package com.bettorodds.oddsoverlay

import android.graphics.Bitmap
import android.graphics.Color

/** How chips are painted when positions come from nodes (no per-chip pixel to sample). */
data class ChipStyle(val backgroundColor: Int, val textColor: Int)

/**
 * Decides one chip style for the whole screen from a single screenshot, sampled occasionally rather
 * than per frame. Reading positions from nodes is what has to be fast; the color changes slowly, so
 * it is fine to refresh it only when the app or page changes.
 */
object ThemeSampler {

    val DARK = ChipStyle(Color.parseColor("#243044"), Color.WHITE)
    val LIGHT = ChipStyle(Color.parseColor("#E4E9F1"), Color.BLACK)

    fun sample(bitmap: Bitmap): ChipStyle {
        val cols = 24
        val rows = 48
        val stepX = (bitmap.width / cols).coerceAtLeast(1)
        val stepY = (bitmap.height / rows).coerceAtLeast(1)

        var luminanceSum = 0.0
        var count = 0
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val p = bitmap.getPixel(x, y)
                luminanceSum += (0.299 * Color.red(p) + 0.587 * Color.green(p) + 0.114 * Color.blue(p)) / 255.0
                count++
                x += stepX
            }
            y += stepY
        }

        val avg = if (count == 0) 0.0 else luminanceSum / count
        return if (avg < 0.5) DARK else LIGHT
    }
}
