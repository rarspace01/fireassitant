package net.fireassistant

import android.content.Context
import java.security.SecureRandom

object Prefs {
    const val PORT = 8765

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences("fireassistant", Context.MODE_PRIVATE)

    /** API token, generated on first use. Clients send it as `Authorization: Bearer <token>` or `?token=`. */
    fun token(ctx: Context): String {
        val p = prefs(ctx)
        p.getString("token", null)?.let { return it }
        val bytes = ByteArray(18).also { SecureRandom().nextBytes(it) }
        val token = bytes.joinToString("") { "%02x".format(it) }
        p.edit().putString("token", token).apply()
        return token
    }

    fun regenerateToken(ctx: Context): String {
        prefs(ctx).edit().remove("token").apply()
        return token(ctx)
    }
}
