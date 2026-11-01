package net.fireassistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.net.Uri
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Swipes up on the lock screen, reports which Home Assistant dashboard is showing, and taps "Install" in the system package installer while an
 * update started by /api/update or the app's update button is pending (see [armInstall]). Fire OS blocks keyguard dismissal for third-party
 * apps, so a swipe is the only way past the "Special Offers" (ad-supported) lock screen.
 */
class GestureService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // A tap inside Home Assistant may have navigated away from the dashboard we opened.
        if (event?.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED &&
            event.packageName?.toString() in Actions.HA_PACKAGES
        ) {
            openedDashboard = null
        }
        if (System.currentTimeMillis() > installArmedUntil) return
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() !in INSTALLER_PACKAGES) return
        val button = INSTALL_BUTTON_IDS.firstNotNullOfOrNull { id ->
            root.findAccessibilityNodeInfosByViewId(id).firstOrNull { it.isClickable }
        } ?: root.findAccessibilityNodeInfosByText("Install").firstOrNull { it.isClickable && it.isEnabled }
        if (button != null && button.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            installArmedUntil = 0
        }
    }

    override fun onInterrupt() = Unit

    private fun swipeUp(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val m = resources.displayMetrics
        val x = m.widthPixels / 2f
        val path = Path().apply {
            // Start at the very bottom edge, below any lock-screen ad button: a swipe that starts
            // on an ad can register as a tap and open an Appstore page.
            moveTo(x, m.heightPixels * 0.97f)
            lineTo(x, m.heightPixels * 0.30f)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 350))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    companion object {
        @Volatile
        var instance: GestureService? = null
            private set

        val isRunning get() = instance != null

        private val INSTALLER_PACKAGES = setOf("com.android.packageinstaller", "com.google.android.packageinstaller")
        private val INSTALL_BUTTON_IDS = listOf(
            "com.android.packageinstaller:id/ok_button",
            "com.google.android.packageinstaller:id/ok_button",
            "android:id/button1",
        )

        @Volatile
        private var installArmedUntil = 0L

        /** Allow the next installer confirmation dialog (within 3 minutes) to be tapped automatically. */
        fun armInstall() {
            installArmedUntil = System.currentTimeMillis() + 3 * 60_000
        }

        fun swipeUnlock(): Boolean = instance?.swipeUp() ?: false

        /**
         * Dashboard path last opened through the API, cleared when someone taps inside Home
         * Assistant. Fallback for [currentDashboard] when the WebView doesn't expose its URL.
         */
        @Volatile
        var openedDashboard: String? = null

        /** Package of the app in front, or null if unknown (service off, nothing focused). */
        fun foregroundPackage(): String? = try {
            instance?.rootInActiveWindow?.packageName?.toString()
        } catch (e: Exception) {
            null
        }

        /**
         * Dashboard path (e.g. `lovelace/kitchen`) Home Assistant is showing, read from the URL
         * that Chromium's WebView puts in the "url" extra of the page's accessibility node.
         * Falls back to [openedDashboard]; null if unknown.
         */
        fun currentDashboard(): String? {
            val url = try {
                instance?.rootInActiveWindow?.let(::findPageUrl)
            } catch (e: Exception) {
                null
            }
            return if (url != null) Uri.parse(url).path.orEmpty().trim('/') else openedDashboard
        }

        /** Breadth-first so the page node is found before any link inside it (links carry "url" too). */
        private fun findPageUrl(root: AccessibilityNodeInfo): String? {
            val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
            var visited = 0
            while (queue.isNotEmpty() && visited++ < MAX_NODES) {
                val node = queue.removeFirst()
                val url = node.extras?.getCharSequence("url")?.toString()
                if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) return url
                for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
            }
            return null
        }

        private const val MAX_NODES = 300
    }
}
