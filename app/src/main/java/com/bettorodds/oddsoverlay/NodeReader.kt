package com.bettorodds.oddsoverlay

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Reads odds straight from the app's view tree instead of a screenshot. Each text node carries its
 * own on-screen bounds, so positions are exact and current the instant they change - no OCR, no lag.
 */
object NodeReader {

    /** [hits] are the converted prices with live bounds; [textNodeCount] tells whether the app
     *  exposes readable text at all (zero means it draws to a canvas and we must fall back to OCR). */
    data class Result(val hits: List<PriceHit>, val textNodeCount: Int)

    fun read(root: AccessibilityNodeInfo?, screenWidth: Int, screenHeight: Int): Result {
        if (root == null) return Result(emptyList(), 0)
        val hits = ArrayList<PriceHit>()
        val seen = HashSet<Long>()
        var textNodes = 0
        val scratch = Rect()

        fun add(bounds: Rect, display: String) {
            // Drop anything scrolled off-screen so virtualized rows do not draw stray chips.
            if (bounds.bottom <= 0 || bounds.top >= screenHeight ||
                bounds.right <= 0 || bounds.left >= screenWidth
            ) return
            if (bounds.width() <= 0 || bounds.height() <= 0) return
            // Reject container nodes: a real odds pill is small. Anything spanning a big slice of the
            // screen is a row or list wrapper whose text happens to include a percentage.
            if (bounds.height() > screenHeight / 5 || bounds.width() > screenWidth * 85 / 100) return
            // De-duplicate: the same value in the same spot can appear on more than one node.
            val key = (bounds.centerX() / 8).toLong() shl 32 or (bounds.centerY() / 8).toLong()
            if (!seen.add(key)) return
            hits.add(PriceHit(Rect(bounds), display))
        }

        fun walk(node: AccessibilityNodeInfo?) {
            if (node == null) return
            val text = node.text?.toString()
            if (!text.isNullOrEmpty()) {
                textNodes++
                val matches = OddsConverter.findPercentages(text)
                if (matches.isNotEmpty()) {
                    node.getBoundsInScreen(scratch)
                    val trimmedLen = text.trim().length
                    for (m in matches) {
                        val bounds = if (matches.size == 1 && m.raw.length == trimmedLen) {
                            scratch
                        } else {
                            narrow(scratch, m, text.length)
                        }
                        add(bounds, m.display)
                    }
                }
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                walk(child)
                @Suppress("DEPRECATION")
                try { child.recycle() } catch (_: Exception) {}
            }
        }

        walk(root)
        return Result(hits, textNodes)
    }

    private fun narrow(box: Rect, match: OddsConverter.Match, length: Int): Rect {
        if (length == 0) return Rect(box)
        val perChar = box.width().toFloat() / length
        return Rect(
            box.left + (match.startIndex * perChar).toInt(),
            box.top,
            box.left + (match.endIndex * perChar).toInt(),
            box.bottom
        )
    }
}
