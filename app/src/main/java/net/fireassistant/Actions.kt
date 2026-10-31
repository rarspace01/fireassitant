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

class ActionResult(val ok: Boolean, val message: String, val httpStatus: Int = if (ok) 200 else 500)

class Actions(context: Context) {
    private val ctx = context.applicationContext

    fun wake(): ActionResult {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
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
        wake()
        val keyguard = ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({ GestureService.swipeUnlock() }, 500)
        handler.postDelayed({ if (keyguard.isKeyguardLocked) GestureService.swipeUnlock() }, 1_500)
        return ActionResult(true, "unlock requested")
    }

    fun openHomeAssistant(): ActionResult {
        val pkg = haPackage() ?: return notInstalled()
        val intent = ctx.packageManager.getLaunchIntentForPackage(pkg)
            ?: return ActionResult(false, "cannot launch $pkg", 404)
        return start(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), "opened Home Assistant")
    }

    /** [path] is a dashboard path as in the HA URL, e.g. `lovelace/kitchen` or `dashboard-tablet/0`. */
    fun openDashboard(path: String): ActionResult {
        val clean = path.trim().trim('/')
        if (clean.isEmpty()) return ActionResult(false, "missing 'path' parameter", 400)
        val pkg = haPackage() ?: return notInstalled()
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("homeassistant://navigate/$clean"))
            .setPackage(pkg)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return start(intent, "opened dashboard $clean")
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
        val HA_PACKAGES = listOf(
            "io.homeassistant.companion.android",
            "io.homeassistant.companion.android.minimal",
        )

        fun adminComponent(ctx: Context) = ComponentName(ctx, AdminReceiver::class.java)
    }
}
