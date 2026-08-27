package com.bettorodds.oddsoverlay

import android.content.Context

/**
 * The apps the overlay activates inside. A built-in list of prediction-market apps covers the known
 * ones out of the box; the user's own additions are stored so a book that switches to percentages
 * later can be added without shipping a new build.
 */
object TargetApps {

    private const val PREFS = "target_apps"
    private const val KEY_ENABLED = "enabled_packages"
    private const val KEY_AUTO = "auto_on_packages"

    val BUILT_IN = linkedMapOf(
        "us.novig.app" to "Novig",
        "com.kalshi.kalshi" to "Kalshi",
        "com.polymarket.polymarket" to "Polymarket",
        "com.prizepicks.android" to "PrizePicks",
    )

    fun isTarget(context: Context, pkg: String): Boolean =
        pkg in BUILT_IN || pkg in userEnabled(context)

    /** Packages the user turned on beyond the built-ins. */
    fun userEnabled(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_ENABLED, emptySet()) ?: emptySet()

    fun setUserEnabled(context: Context, pkg: String, enabled: Boolean) {
        val current = userEnabled(context).toMutableSet()
        if (enabled) current.add(pkg) else current.remove(pkg)
        prefs(context).edit().putStringSet(KEY_ENABLED, current).apply()
    }

    /** Packages that skip the "turn on?" prompt and convert the moment they open. */
    fun isAutoOn(context: Context, pkg: String): Boolean =
        pkg in (prefs(context).getStringSet(KEY_AUTO, emptySet()) ?: emptySet())

    fun setAutoOn(context: Context, pkg: String, auto: Boolean) {
        val current = (prefs(context).getStringSet(KEY_AUTO, emptySet()) ?: emptySet()).toMutableSet()
        if (auto) current.add(pkg) else current.remove(pkg)
        prefs(context).edit().putStringSet(KEY_AUTO, current).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
