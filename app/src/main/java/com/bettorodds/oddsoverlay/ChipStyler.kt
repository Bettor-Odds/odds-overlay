package com.bettorodds.oddsoverlay

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect

/** A converted price with colors sampled from the app underneath it. */
data class StyledHit(
    val bounds: Rect,
    val display: String,
    val backgroundColor: Int,
    val textColor: Int
)

/**
 * Picks each chip's colors from the pixels it will cover.
 *
 * A chip painted a fixed color reads as something stuck on top of the app. Sampling the background
 * immediately around the number lets the replacement sit where the original was, and works whether
 * the host app is in light or dark mode without knowing anything about its theme.
 */
object ChipStyler {

    private const val LUMINANCE_THRESHOLD = 0.5

    fun style(bitmap: Bitmap, hits: List<PriceHit>): List<StyledHit> = hits.mapNotNull { hit ->
        val bounds = hit.bounds
        if (bounds.width() <= 0 || bounds.height() <= 0) return@mapNotNull null
        if (bounds.left < 0 || bounds.top < 0 ||
            bounds.right > bitmap.width || bounds.bottom > bitmap.height
        ) return@mapNotNull null

        val background = sampleBackground(bitmap, bounds)
        StyledHit(
            bounds = bounds,
            display = hit.display,
            backgroundColor = background,
            textColor = if (luminance(background) > LUMINANCE_THRESHOLD) Color.BLACK else Color.WHITE
        )
    }

    /**
     * Reads the corners just outside the glyphs. The centre of the box is text, so sampling it
     * would return the foreground color and produce a chip that hides its own number.
     */
    private fun sampleBackground(bitmap: Bitmap, bounds: Rect): Int {
        val insetY = (bounds.height() * 0.15f).toInt().coerceAtLeast(1)
        val points = listOf(
            bounds.left to bounds.top + insetY,
            bounds.right - 1 to bounds.top + insetY,
            bounds.left to bounds.bottom - insetY - 1,
            bounds.right - 1 to bounds.bottom - insetY - 1
        )

        var red = 0
        var green = 0
        var blue = 0
        var counted = 0
        for ((x, y) in points) {
            if (x !in 0 until bitmap.width || y !in 0 until bitmap.height) continue
            val pixel = bitmap.getPixel(x, y)
            red += Color.red(pixel)
            green += Color.green(pixel)
            blue += Color.blue(pixel)
            counted++
        }

        if (counted == 0) return Color.BLACK
        return Color.rgb(red / counted, green / counted, blue / counted)
    }

    private fun luminance(color: Int): Double =
        (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0
}
