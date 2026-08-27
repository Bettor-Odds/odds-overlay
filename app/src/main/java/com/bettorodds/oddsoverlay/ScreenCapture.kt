package com.bettorodds.oddsoverlay

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.util.Log

/**
 * Mirrors the display into an [ImageReader] and hands out bitmaps only when the screen has
 * materially changed.
 *
 * Running OCR on every frame is the difference between a tool people keep installed and one they
 * uninstall for eating the battery. Novig's board is static between scrolls, so frames are sampled
 * at [SAMPLE_INTERVAL_MS] and compared against a coarse signature; identical frames never reach the
 * recognizer.
 */
class ScreenCapture(
    private val projection: MediaProjection,
    private val width: Int,
    private val height: Int,
    private val densityDpi: Int,
    private val handler: Handler,
    private val onFrameChanged: () -> Unit,
    private val onFrameSettled: (Bitmap) -> Unit
) {

    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null

    private var lastSampleAt = 0L
    private var lastSampleHash = 0L
    private var lastSampleSum = 0L
    private var lastOcrHash = 0L
    private var lastOcrAt = 0L
    private var trailingScheduled = false

    fun start() {
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, MAX_IMAGES)
        reader.setOnImageAvailableListener({ onImageAvailable(it) }, handler)
        imageReader = reader

        virtualDisplay = projection.createVirtualDisplay(
            VIRTUAL_DISPLAY_NAME,
            width,
            height,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            handler
        )
    }

    fun stop() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.setOnImageAvailableListener(null, null)
        imageReader?.close()
        imageReader = null
    }

    private fun onImageAvailable(reader: ImageReader) {
        val image = try {
            reader.acquireLatestImage()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "acquireLatestImage failed", e)
            null
        } ?: return

        try {
            val now = System.currentTimeMillis()
            if (now - lastSampleAt < SAMPLE_INTERVAL_MS) return
            lastSampleAt = now

            val (hash, sum) = fingerprint(image)

            // Magnitude of change from the previous sample. A big jump means a scroll or a new page,
            // so stale chips are now in the wrong place and are hidden until the next conversion. A
            // tiny jump - one odds value ticking - is left alone so a live board does not strobe.
            val changeRatio = kotlin.math.abs(sum - lastSampleSum).toDouble() / (lastSampleSum + 1)
            lastSampleSum = sum
            if (hash != lastSampleHash && changeRatio > BIG_CHANGE_RATIO) onFrameChanged()
            lastSampleHash = hash

            if (hash == lastOcrHash) return

            // Convert whenever the screen differs from what was last converted, throttled so a
            // constantly-animating board costs at most one recognition per interval. Waiting for the
            // screen to go perfectly still never fires on apps that animate.
            val waited = now - lastOcrAt
            if (waited >= MIN_OCR_INTERVAL_MS) {
                recognizeCurrent(hash, now, image)
            } else {
                // The changed frame fell inside the throttle window. Schedule a trailing pass so the
                // latest content is still picked up even if the screen now goes static and stops
                // delivering frames - the case where chips would otherwise be left cleared.
                scheduleTrailingRecognition(MIN_OCR_INTERVAL_MS - waited)
            }
        } finally {
            image.close()
        }
    }

    private fun recognizeCurrent(hash: Long, now: Long, image: Image) {
        lastOcrHash = hash
        lastOcrAt = now
        onFrameSettled(image.toBitmap())
    }

    private fun scheduleTrailingRecognition(delayMs: Long) {
        if (trailingScheduled) return
        trailingScheduled = true
        handler.postDelayed({
            trailingScheduled = false
            val image = try {
                imageReader?.acquireLatestImage()
            } catch (e: IllegalStateException) {
                null
            } ?: return@postDelayed
            try {
                val (hash, _) = fingerprint(image)
                if (hash != lastOcrHash) {
                    recognizeCurrent(hash, System.currentTimeMillis(), image)
                }
            } finally {
                image.close()
            }
        }, delayMs)
    }

    /**
     * Coarse fingerprint of the frame: an order-sensitive [hash] for equality and a plain [sum] of
     * the sampled channel for measuring how big a change is. Sampling a grid rather than every pixel
     * keeps this cheap enough to run on the image-available callback without dropping frames.
     */
    private fun fingerprint(image: Image): Pair<Long, Long> {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride

        var hash = 1125899906842597L
        var sum = 0L
        val rowStep = (image.height / SIGNATURE_ROWS).coerceAtLeast(1)
        val colStep = (image.width / SIGNATURE_COLS).coerceAtLeast(1)

        var y = 0
        while (y < image.height) {
            var x = 0
            while (x < image.width) {
                val offset = y * rowStride + x * pixelStride
                if (offset + 3 < buffer.limit()) {
                    val r = buffer.get(offset).toInt() and 0xFF
                    val g = buffer.get(offset + 1).toInt() and 0xFF
                    val b = buffer.get(offset + 2).toInt() and 0xFF
                    hash = 31 * hash + r
                    hash = 31 * hash + g
                    hash = 31 * hash + b
                    sum += r + g + b
                }
                x += colStep
            }
            y += rowStep
        }
        return hash to sum
    }

    private fun Image.toBitmap(): Bitmap {
        val plane = planes[0]
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        // Rows are padded to a hardware-friendly stride, so the backing bitmap is wider than the
        // display and has to be cropped back down.
        val rowPadding = rowStride - pixelStride * width
        val paddedWidth = width + rowPadding / pixelStride

        val padded = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
        padded.copyPixelsFromBuffer(plane.buffer)

        return if (rowPadding == 0) {
            padded
        } else {
            Bitmap.createBitmap(padded, 0, 0, width, height).also { padded.recycle() }
        }
    }

    private companion object {
        const val TAG = "ScreenCapture"
        const val VIRTUAL_DISPLAY_NAME = "odds-overlay"
        const val MAX_IMAGES = 2
        const val SAMPLE_INTERVAL_MS = 200L
        const val MIN_OCR_INTERVAL_MS = 600L
        const val BIG_CHANGE_RATIO = 0.02
        const val SIGNATURE_ROWS = 32
        const val SIGNATURE_COLS = 16
    }
}
