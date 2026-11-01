package net.fireassistant

import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

class ActionResult(val ok: Boolean, val message: String, val httpStatus: Int = if (ok) 200 else 500)

class Actions(context: Context) {
    private val ctx = context.applicationContext

    private val power get() = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val keyguard get() = ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

    private fun isScreenOn() = power.isInteractive
    private fun isUnlocked() = isScreenOn() && !keyguard.isKeyguardLocked

    fun wake(): ActionResult {
        if (isScreenOn()) return ActionResult(true, "screen already on")
        val pm = power
        @Suppress("DEPRECATION")
        val lock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            "fireassistant:wake",
        )
        lock.acquire(5_000)
        return ActionResult(true, "screen woken")
    }

    fun sleep(): ActionResult {
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (!dpm.isAdminActive(adminComponent(ctx))) {
            return ActionResult(false, "device admin not enabled; open the app and grant it", 409)
        }
        dpm.lockNow()
        return ActionResult(true, "screen off")
    }

    /**
     * Fire OS blocks apps from dismissing the keyguard (needs an Amazon-only permission), so
     * the only route is an accessibility swipe once the screen is on. Retried once because the
     * lock screen can take a moment to become interactive.
     */
    fun unlock(): ActionResult {
        if (!GestureService.isRunning) {
            return ActionResult(false, "accessibility service not enabled; open the app and enable it", 409)
        }
        if (isUnlocked()) return ActionResult(true, "already unlocked")
        wake()
        val handler = Handler(Looper.getMainLooper())
        // Give the lock screen time to become interactive, and only swipe while it is showing.
        handler.postDelayed({ if (keyguard.isKeyguardLocked) GestureService.swipeUnlock() }, 1_000)
        handler.postDelayed({ if (keyguard.isKeyguardLocked) GestureService.swipeUnlock() }, 2_500)
        return ActionResult(true, "unlock requested")
    }

    /**
     * Installs the newest GitHub release without any tapping: unlocks the screen, downloads the APK,
     * opens the system installer and lets [GestureService] confirm it. Returns before the install
     * starts because the update kills this process.
     */
    fun update(): ActionResult {
        if (!GestureService.isRunning) {
            return ActionResult(false, "accessibility service not enabled; open the app and enable it", 409)
        }
        if (!canStartFromBackground()) {
            return ActionResult(false, "'Display over other apps' permission not granted; open the app and grant it", 409)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ctx.packageManager.canRequestPackageInstalls()) {
            return ActionResult(false, "installing from this app is not allowed; tap 'Check for updates' in the app once", 409)
        }
        val latest = try {
            Updater.latest()
        } catch (e: Exception) {
            return ActionResult(false, "update check failed: ${e.message}", 502)
        } ?: return ActionResult(false, "no release found", 404)
        if (latest.build <= Updater.installedBuild(ctx)) return ActionResult(true, "up to date (${latest.tag})")
        if (!updating.compareAndSet(false, true)) return ActionResult(true, "update already in progress")

        Thread {
            try {
                unlock()
                val apk = Updater.download(ctx, latest)
                // Open the installer only once the lock screen is gone, so the swipes can't hit its dialog.
                waitUntilUnlocked(10_000)
                GestureService.armInstall()
                Updater.install(ctx, apk)
            } catch (e: Exception) {
                Log.e(TAG, "update failed", e)
            } finally {
                updating.set(false)
            }
        }.start()
        return ActionResult(true, "updating to ${latest.tag}")
    }

    fun openHomeAssistant(): ActionResult {
        val pkg = haPackage() ?: return notInstalled()
        if (isHomeAssistantInFront()) return ActionResult(true, "Home Assistant already open")
        val intent = ctx.packageManager.getLaunchIntentForPackage(pkg)
            ?: return ActionResult(false, "cannot launch $pkg", 404)
        return start(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), "opened Home Assistant")
    }

    /**
     * Shows a dashboard, doing only the steps still missing: wakes the screen if it is off, swipes
     * the lock screen away if it is showing, and navigates only if Home Assistant isn't already in
     * front on that dashboard (the deep link opens Home Assistant if needed). Skipping the
     * navigation avoids reloading a dashboard that is already on screen.
     *
     * [path] is a dashboard path as in the HA URL, e.g. `lovelace/kitchen` or `dashboard-tablet/0`.
     */
    fun openDashboard(path: String): ActionResult {
        val clean = path.trim().trim('/')
        if (clean.isEmpty()) return ActionResult(false, "missing 'path' parameter", 400)
        val pkg = haPackage() ?: return notInstalled()

        val done = mutableListOf<String>()
        if (!isScreenOn()) {
            wake()
            done += "screen woken"
        }
        if (!isUnlocked() && GestureService.isRunning) {
            // Wait for the swipe so we can see what is behind the lock screen; if it doesn't
            // work out, navigate anyway and the dashboard is there once the tablet is unlocked.
            unlock()
            if (waitUntilUnlocked(5_000)) done += "unlocked"
        }

        if (isHomeAssistantInFront() && GestureService.currentDashboard() == clean) {
            return ActionResult(true, (done + "dashboard $clean already shown").joinToString(", "))
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("homeassistant://navigate/$clean"))
            .setPackage(pkg)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val result = start(intent, "opened dashboard $clean")
        if (!result.ok) return result
        GestureService.openedDashboard = clean
        return ActionResult(true, (done + result.message).joinToString(", "))
    }

    /** Only meaningful when unlocked: behind the lock screen the front app can't be seen. */
    private fun isHomeAssistantInFront() =
        isUnlocked() && GestureService.foregroundPackage() in HA_PACKAGES

    /** Polls the lock screen; true once it is gone and the screen is on. */
    private fun waitUntilUnlocked(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!isUnlocked() && System.currentTimeMillis() < deadline) Thread.sleep(250)
        return isUnlocked()
    }

    private fun start(intent: Intent, okMessage: String): ActionResult {
        if (!canStartFromBackground()) {
            return ActionResult(false, "'Display over other apps' permission not granted; open the app and grant it", 409)
        }
        return try {
            ctx.startActivity(intent)
            ActionResult(true, okMessage)
        } catch (e: Exception) {
            ActionResult(false, "failed to start: ${e.message}")
        }
    }

    private fun haPackage(): String? = HA_PACKAGES.firstOrNull {
        try {
            ctx.packageManager.getPackageInfo(it, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun notInstalled() = ActionResult(false, "Home Assistant companion app is not installed", 404)

    /** Android 10+ blocks background activity starts unless the app has an overlay permission. */
    private fun canStartFromBackground() =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || Settings.canDrawOverlays(ctx)

    companion object {
        private const val TAG = "FireAssistant"
        private val updating = AtomicBoolean(false)

        val HA_PACKAGES = listOf(
            "io.homeassistant.companion.android",
            "io.homeassistant.companion.android.minimal",
        )

        fun adminComponent(ctx: Context) = ComponentName(ctx, AdminReceiver::class.java)
    }
}
