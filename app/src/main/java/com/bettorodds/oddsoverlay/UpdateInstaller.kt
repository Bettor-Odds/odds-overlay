package com.bettorodds.oddsoverlay

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads the newest APK and hands it to the system installer. As close to auto as a sideloaded
 * app is allowed: the download is silent, then Android asks the user to confirm the install once.
 */
object UpdateInstaller {

    /** Downloads the latest APK to the cache. Returns the file, or null on failure. */
    fun download(context: Context): File? {
        val target = File(context.cacheDir, "odds-converter-update.apk")
        var url = UpdateChecker.DOWNLOAD_URL
        try {
            var redirects = 0
            while (redirects < 5) {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 15000
                    readTimeout = 30000
                }
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location") ?: return null
                    conn.disconnect()
                    url = location
                    redirects++
                    continue
                }
                if (code != HttpURLConnection.HTTP_OK) return null
                conn.inputStream.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                conn.disconnect()
                return if (target.length() > 0) target else null
            }
            return null
        } catch (e: Exception) {
            return null
        }
    }

    /** Launches the system installer for an already-downloaded APK. */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
