package com.bettorodds.oddsoverlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.view.View

/**
 * Paints converted prices over the percentages underneath.
 *
 * The window hosting this view is not touchable, so everything drawn here is inert - taps pass
 * straight through to the app being covered.
 */
class OverlayView(context: Context) : View(context) {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val textBounds = Rect()
    private val locationOnScreen = IntArray(2)

    private var hits: List<StyledHit> = emptyList()

    fun show(newHits: List<StyledHit>) {
        hits = newHits
        invalidate()
    }

    fun clear() {
        if (hits.isEmpty()) return
        hits = emptyList()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Hit bounds are in capture space, whose origin is the physical top-left of the screen.
        // The overlay window's own top-left can sit below the status bar, so translate the canvas by
        // the view's actual on-screen position to put chips back where their percentages are.
        getLocationOnScreen(locationOnScreen)
        canvas.translate(-locationOnScreen[0].toFloat(), -locationOnScreen[1].toFloat())
        for (hit in hits) {
            drawChip(canvas, hit)
        }
    }

    private fun drawChip(canvas: Canvas, hit: StyledHit) {
        val bounds = hit.bounds
        // Bleed past the original glyphs so no antialiased edges of the percentage survive.
        val bleedX = bounds.width() * HORIZONTAL_BLEED
        val bleedY = bounds.height() * VERTICAL_BLEED

        fillPaint.color = hit.backgroundColor
        canvas.drawRect(
            bounds.left - bleedX,
            bounds.top - bleedY,
            bounds.right + bleedX,
            bounds.bottom + bleedY,
            fillPaint
        )

        textPaint.color = hit.textColor
        textPaint.textSize = fittedTextSize(hit.display, bounds)
        textPaint.getTextBounds(hit.display, 0, hit.display.length, textBounds)

        canvas.drawText(
            hit.display,
            bounds.exactCenterX(),
            bounds.exactCenterY() + textBounds.height() / 2f,
            textPaint
        )
    }

    /**
     * "+130" is wider than "43.4%" is tall, so sizing by height alone overflows the space the
     * original number occupied. Start from the box height and shrink until the string fits.
     */
    private fun fittedTextSize(text: String, bounds: Rect): Float {
        var size = bounds.height() * INITIAL_TEXT_SCALE
        val maxWidth = bounds.width() * (1f + 2f * HORIZONTAL_BLEED)
        while (size > MIN_TEXT_SIZE) {
            textPaint.textSize = size
            if (textPaint.measureText(text) <= maxWidth) break
            size -= TEXT_SIZE_STEP
        }
        return size
    }

    private companion object {
        const val HORIZONTAL_BLEED = 0.16f
        const val VERTICAL_BLEED = 0.20f
        const val INITIAL_TEXT_SCALE = 0.82f
        const val MIN_TEXT_SIZE = 8f
        const val TEXT_SIZE_STEP = 0.5f
    }
}
