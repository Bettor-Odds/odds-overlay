package com.bettorodds.oddsoverlay

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Reads odds straight from the app's view tree instead of a screenshot. Each text node carries its
 * own on-screen bounds, so positions are exact and current the instant they change - no OCR, no lag.
 *
 * Whole off-screen branches are pruned before descending into them, so a long board costs only its
 * visible rows rather than a full-tree walk - that is what keeps reads fast on big apps.
 */
object NodeReader {

    data class Result(val hits: List<PriceHit>, val textNodeCount: Int)

    fun read(root: AccessibilityNodeInfo?, screenWidth: Int, screenHeight: Int): Result {
        if (root == null) return Result(emptyList(), 0)
        val hits = ArrayList<PriceHit>()
        val seen = HashSet<Long>()
        var textNodes = 0

        fun add(bounds: Rect, display: String) {
            if (bounds.width() <= 0 || bounds.height() <= 0) return
            // A real odds pill is small; anything spanning a big slice of the screen is a container.
            if (bounds.height() > screenHeight / 5 || bounds.width() > screenWidth * 85 / 100) return
            val key = (bounds.centerX() / 8).toLong() shl 32 or (bounds.centerY() / 8).toLong()
            if (!seen.add(key)) return
            hits.add(PriceHit(Rect(bounds), display))
        }

        fun walk(node: AccessibilityNodeInfo?) {
            if (node == null) return
            val b = Rect()
            node.getBoundsInScreen(b)
            // Prune: a node with a real box entirely off-screen has no visible descendants worth
            // walking. Empty boxes (some containers report none) are kept so we still descend.
            val offScreen = !b.isEmpty &&
                (b.bottom <= 0 || b.top >= screenHeight || b.right <= 0 || b.left >= screenWidth)
            if (offScreen) return

            // Only the leaf that actually holds the text - a parent button echoes its child's
            // "43.4%" but reports the whole selection's bounds, which would cover the entire cell.
            val text = node.text?.toString()
            if (!text.isNullOrEmpty() && node.childCount == 0) {
                textNodes++
                val matches = OddsConverter.findPercentages(text)
                if (matches.isNotEmpty() && !b.isEmpty) {
                    val trimmedLen = text.trim().length
                    for (m in matches) {
                        val bounds = if (matches.size == 1 && m.raw.length == trimmedLen) b
                        else narrow(b, m, text.length)
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
