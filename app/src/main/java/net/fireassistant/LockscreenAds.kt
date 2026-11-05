package net.fireassistant

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log

/**
 * Keeps Special Offers ads off the lock screen, the way Fire Toolbox does it on Fire OS 8 (its
 * "[FTB] Lockscreen Ads Remover" Automate flow): Fire OS reads the `LOCKSCREEN_AD_ENABLED` setting
 * to decide whether the lock screen shows an ad, and Amazon turns it back on, so it is reset to 0
 * every time the screen goes off, before the lock screen is drawn again.
 *
 * Writing it needs WRITE_SECURE_SETTINGS, which only ADB can grant:
 * `adb shell pm grant net.fireassistant android.permission.WRITE_SECURE_SETTINGS`
 */
object LockscreenAds {
    private const val TAG = "FireAssistant"
    private const val KEY = "LOCKSCREEN_AD_ENABLED"
    const val GRANT_COMMAND = "adb shell pm grant net.fireassistant android.permission.WRITE_SECURE_SETTINGS"

    fun canBlock(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /**
     * Sets the flag to 0. Fire Toolbox's flow doesn't say which settings table it uses, and older
     * guides use both, so both are written. Returns false if neither write worked.
     */
    fun block(ctx: Context): Boolean {
        if (!canBlock(ctx)) return false
        val cr = ctx.contentResolver
        val global = write { Settings.Global.putString(cr, KEY, "0") }
        val secure = write { Settings.Secure.putString(cr, KEY, "0") }
        return global || secure
    }

    /** Current value, e.g. "global=1, secure=0"; "unset" where the key doesn't exist. */
    fun state(ctx: Context): String {
        val cr = ctx.contentResolver
        val global = read { Settings.Global.getString(cr, KEY) }
        val secure = read { Settings.Secure.getString(cr, KEY) }
        return "global=$global, secure=$secure"
    }

    /** Re-blocks the ads whenever the screen turns off. Register from a running service. */
    fun screenOffReceiver(): Pair<BroadcastReceiver, IntentFilter> = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            block(context)
        }
    } to IntentFilter(Intent.ACTION_SCREEN_OFF)

    private fun write(op: () -> Boolean): Boolean = try {
        op()
    } catch (e: Exception) {
        Log.w(TAG, "could not write $KEY", e)
        false
    }

    private fun read(op: () -> String?): String = try {
        op() ?: "unset"
    } catch (e: Exception) {
        "unreadable"
    }
}
