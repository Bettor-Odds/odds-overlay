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

    fun read(root: AccessibilityNodeInfo?): Result {
        if (root == null) return Result(emptyList(), 0)
        val hits = ArrayList<PriceHit>()
        var textNodes = 0
        val scratch = Rect()

        fun walk(node: AccessibilityNodeInfo?) {
            if (node == null) return
            val text = node.text?.toString()
            if (!text.isNullOrEmpty()) {
                textNodes++
                val matches = OddsConverter.findPercentages(text)
                if (matches.isNotEmpty()) {
                    node.getBoundsInScreen(scratch)
                    if (!scratch.isEmpty) {
                        val trimmedLen = text.trim().length
                        for (m in matches) {
                            // If the node is exactly the percentage, its bounds are the chip. If it
                            // carries a label too, apportion the bounds by character offset.
                            val bounds = if (matches.size == 1 && m.raw.length == trimmedLen) {
                                Rect(scratch)
                            } else {
                                narrow(scratch, m, text.length)
                            }
                            hits.add(PriceHit(bounds, m.display))
                        }
                    }
                }
            }
            for (i in 0 until node.childCount) walk(node.getChild(i))
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
