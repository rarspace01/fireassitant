package net.fireassistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Starts the API server after boot and after the app itself is updated (the update kills it). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            ApiService.start(context)
        }
    }
}
