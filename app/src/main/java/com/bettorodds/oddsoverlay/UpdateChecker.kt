package com.bettorodds.oddsoverlay

import java.net.HttpURLConnection
import java.net.URL

/**
 * Asks GitHub for the latest published version. This is the only network call the app makes - it
 * sends nothing about the screen or the user, just reads a version number.
 */
object UpdateChecker {

    private const val LATEST_RELEASE =
        "https://api.github.com/repos/Bettor-Odds/odds-overlay/releases/latest"
    const val DOWNLOAD_URL =
        "https://github.com/Bettor-Odds/odds-overlay/releases/latest/download/odds-overlay.apk"

    data class Update(val latestVersion: String)

    /** Returns an [Update] if a newer version than [currentVersion] is available, else null. */
    fun check(currentVersion: String): Update? {
        val latest = fetchLatestTag() ?: return null
        return if (isNewer(latest, currentVersion)) Update(latest) else null
    }

    private fun fetchLatestTag(): String? = try {
        val conn = (URL(LATEST_RELEASE).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            connectTimeout = 5000
            readTimeout = 5000
        }
        conn.inputStream.bufferedReader().use { reader ->
            val body = reader.readText()
            Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
        }
    } catch (e: Exception) {
        null
    }

    /** Compares dotted versions ("v0.3.1" vs "0.3.0"), ignoring a leading v. */
    private fun isNewer(candidate: String, current: String): Boolean {
        val a = parts(candidate)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun parts(v: String): List<Int> =
        v.trimStart('v', 'V').split('.').map { it.toIntOrNull() ?: 0 }
}
