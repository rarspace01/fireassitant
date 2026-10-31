package net.fireassistant

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.Formatter
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Setup screen: shows the API URL/token and walks through the three permissions. */
class MainActivity : Activity() {
    private lateinit var info: TextView
    private lateinit var adminStatus: TextView
    private lateinit var overlayStatus: TextView
    private lateinit var a11yStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }
        info = text(root, 16f)

        adminStatus = text(root, 16f)
        button(root, "Enable device admin (screen off)") {
            startActivity(
                Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, Actions.adminComponent(this)),
            )
        }
        overlayStatus = text(root, 16f)
        button(root, "Allow display over other apps (open HA)") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        a11yStatus = text(root, 16f)
        button(root, "Enable accessibility service (needed for unlock)") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        button(root, "Regenerate token") {
            Prefs.regenerateToken(this)
            ApiService.stop(this)
            ApiService.start(this)
            refresh()
        }
        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        ApiService.start(this)
        refresh()
    }

    private fun refresh() {
        @Suppress("DEPRECATION")
        val ip = Formatter.formatIpAddress(
            (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).connectionInfo.ipAddress,
        )
        val base = "http://$ip:${Prefs.PORT}"
        val token = Prefs.token(this)
        info.text = "Fire Assistant API\n\n$base\nToken: $token\n\n" +
            "curl -H 'Authorization: Bearer $token' $base/api/wake\n\n" +
            "Endpoints: /api/wake, /api/sleep, /api/unlock, /api/ha, /api/ha/dashboard?path=lovelace/kitchen\n"

        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminStatus.text = status("Device admin", dpm.isAdminActive(Actions.adminComponent(this)))
        overlayStatus.text = status(
            "Display over other apps",
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this),
        )
        a11yStatus.text = status("Accessibility service", GestureService.isRunning)
    }

    private fun status(name: String, on: Boolean) = "\n$name: ${if (on) "OK" else "NOT GRANTED"}"

    private fun text(parent: LinearLayout, size: Float) =
        TextView(this).apply { textSize = size; parent.addView(this) }

    private fun button(parent: LinearLayout, label: String, onClick: () -> Unit) {
        parent.addView(Button(this).apply {
            text = label
            setOnClickListener { _: View -> onClick() }
        })
    }
}
