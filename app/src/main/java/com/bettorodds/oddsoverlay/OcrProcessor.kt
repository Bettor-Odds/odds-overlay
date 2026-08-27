package com.bettorodds.oddsoverlay

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** A converted price and the screen rectangle its percentage occupied. */
data class PriceHit(val bounds: Rect, val display: String)

class OcrProcessor {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(bitmap: Bitmap): List<PriceHit> = suspendCoroutine { continuation ->
        val input = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(input)
            .addOnSuccessListener { text ->
                val hits = text.textBlocks
                    .asSequence()
                    .flatMap { it.lines.asSequence() }
                    .flatMap { it.elements.asSequence() }
                    .flatMap { element ->
                        val box = element.boundingBox
                        if (box == null) {
                            emptySequence()
                        } else {
                            OddsConverter.findPercentages(element.text)
                                .asSequence()
                                .map { PriceHit(box.narrowTo(it, element.text.length), it.display) }
                        }
                    }
                    .toList()
                continuation.resume(hits)
            }
            .addOnFailureListener { continuation.resume(emptyList()) }
    }

    fun close() = recognizer.close()

    /**
     * ML Kit boxes an entire element, which may carry a label alongside the price ("Chiefs 43.4%").
     * Latin text in these apps is close enough to monospaced at this scale that apportioning the
     * box by character offset lands the chip on the number rather than the whole element.
     */
    private fun Rect.narrowTo(match: OddsConverter.Match, elementLength: Int): Rect {
        if (elementLength == 0 || match.endIndex - match.startIndex == elementLength) return Rect(this)
        val perChar = width().toFloat() / elementLength
        return Rect(
            left + (match.startIndex * perChar).toInt(),
            top,
            left + (match.endIndex * perChar).toInt(),
            bottom
        )
    }
}
