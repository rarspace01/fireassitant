package net.fireassistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class Release(val tag: String, val build: Int, val apkUrl: String)

/**
 * Self-update from GitHub releases. Release tags are `v1.0.<run number>` and the run number is
 * also the APK's versionCode, so "newer" is simply a higher number than ours.
 * Only works over installs signed with the same key (i.e. CI release builds).
 */
object Updater {
    private const val LATEST_URL = "https://api.github.com/repos/rarspace01/fireassitant/releases/latest"

    /** Blocking; call off the main thread. Returns null if the latest release has no usable APK. */
    fun latest(): Release? {
        val json = JSONObject(get(LATEST_URL).use { it.readBytes().decodeToString() })
        val tag = json.getString("tag_name")
        val build = tag.substringAfterLast('.').toIntOrNull() ?: return null
        val assets = json.getJSONArray("assets")
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            if (asset.getString("name").endsWith(".apk")) {
                return Release(tag, build, asset.getString("browser_download_url"))
            }
        }
        return null
    }

    @Suppress("DEPRECATION")
    fun installedBuild(ctx: Context): Int = ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionCode

    /** Blocking; call off the main thread. */
    fun download(ctx: Context, release: Release): File {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        val file = File(dir, "update.apk")
        get(release.apkUrl).use { input -> file.outputStream().use { input.copyTo(it) } }
        return file
    }

    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun get(url: String) = (URL(url).openConnection() as HttpURLConnection).run {
        connectTimeout = 10_000
        readTimeout = 30_000
        setRequestProperty("User-Agent", "fireassistant")
        setRequestProperty("Accept", "application/vnd.github+json")
        if (responseCode != 200) throw java.io.IOException("HTTP $responseCode")
        inputStream
    }
}
