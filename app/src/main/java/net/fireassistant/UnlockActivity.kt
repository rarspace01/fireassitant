package net.fireassistant

import android.app.Activity
import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager

/**
 * Transparent activity shown over the lock screen. Asks the system to dismiss the keyguard and,
 * if the (ad-supported) lock screen is still up afterwards, falls back to an accessibility swipe.
 * Only works for swipe lock screens; a PIN/password/pattern can't be bypassed.
 */
class UnlockActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var keyguard: KeyguardManager

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        keyguard = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = finish()
                override fun onDismissError() = swipeIfStillLocked()
                override fun onDismissCancelled() = swipeIfStillLocked()
            })
        }
        // FLAG_DISMISS_KEYGUARD (pre-O) and custom ad lock screens may ignore the above; check again.
        handler.postDelayed({ swipeIfStillLocked() }, 1_200)
        handler.postDelayed({ finish() }, 4_000)
    }

    private fun swipeIfStillLocked() {
        if (!keyguard.isKeyguardLocked) {
            finish()
            return
        }
        GestureService.swipeUnlock()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
