package net.fireassistant

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.provider.Settings
import org.json.JSONObject
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Reports where ADB can be reached on this device.
 *
 * - `connect_port`: the wireless-debugging port (changes on every toggle/reboot). Read from the
 *   `service.adb.tls.port` system property, falling back to mDNS (`_adb-tls-connect._tcp`).
 * - `pairing_port`: only exists while the "Pair device with pairing code" dialog is open; found via
 *   mDNS (`_adb-tls-pairing._tcp`), otherwise null.
 * - `tcp_port`: the classic `adb tcpip <port>` port (`service.adb.tcp.port`), null when unset.
 */
class AdbPorts(context: Context) {
    private val ctx = context.applicationContext

    fun query(): JSONObject {
        val tcp = prop("service.adb.tcp.port")?.takeIf { it > 0 }
        val tlsProp = prop("service.adb.tls.port")?.takeIf { it > 0 }
        val mdns = discover()
        val wirelessEnabled = try {
            Settings.Global.getInt(ctx.contentResolver, "adb_wifi_enabled", 0) == 1
        } catch (_: Exception) {
            false
        }
        return JSONObject()
            .put("ok", true)
            .put("wireless_debugging", wirelessEnabled)
            .put("connect_port", tlsProp ?: mdns[CONNECT] ?: JSONObject.NULL)
            .put("pairing_port", mdns[PAIRING] ?: JSONObject.NULL)
            .put("tcp_port", tcp ?: JSONObject.NULL)
    }

    private fun prop(name: String): Int? = try {
        val p = Runtime.getRuntime().exec(arrayOf("getprop", name))
        p.inputStream.bufferedReader().use { it.readText() }.trim().toIntOrNull()
    } catch (_: Exception) {
        null
    }

    /** Browses both ADB mDNS service types for a couple of seconds and returns type -> port. */
    private fun discover(): Map<String, Int> {
        val nsd = ctx.getSystemService(Context.NSD_SERVICE) as NsdManager
        val found = HashMap<String, Int>()
        val pending = ConcurrentLinkedQueue<Pair<String, NsdServiceInfo>>()
        val listeners = ArrayList<NsdManager.DiscoveryListener>()

        for (type in listOf(CONNECT, PAIRING)) {
            val listener = object : NsdManager.DiscoveryListener {
                override fun onServiceFound(info: NsdServiceInfo) { pending.add(type to info) }
                override fun onServiceLost(info: NsdServiceInfo) {}
                override fun onDiscoveryStarted(serviceType: String) {}
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            }
            try {
                nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
                listeners.add(listener)
            } catch (_: Exception) {
            }
        }

        try {
            val deadline = System.currentTimeMillis() + DISCOVERY_MS
            while (System.currentTimeMillis() < deadline) {
                val next = pending.poll()
                if (next == null) {
                    Thread.sleep(100)
                    continue
                }
                resolve(nsd, next.second)?.let { found[next.first] = it }
            }
        } finally {
            listeners.forEach { try { nsd.stopServiceDiscovery(it) } catch (_: Exception) {} }
        }
        return found
    }

    /** Resolves one service at a time (older Android versions reject concurrent resolves). */
    @Suppress("DEPRECATION")
    private fun resolve(nsd: NsdManager, info: NsdServiceInfo): Int? {
        var port: Int? = null
        val latch = CountDownLatch(1)
        try {
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = latch.countDown()
                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    port = serviceInfo.port
                    latch.countDown()
                }
            })
            latch.await(RESOLVE_MS, TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
        }
        return port
    }

    private companion object {
        const val CONNECT = "_adb-tls-connect._tcp."
        const val PAIRING = "_adb-tls-pairing._tcp."
        const val DISCOVERY_MS = 2_500L
        const val RESOLVE_MS = 1_500L
    }
}
