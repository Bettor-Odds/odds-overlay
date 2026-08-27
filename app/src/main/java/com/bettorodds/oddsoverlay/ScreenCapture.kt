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

    private var lastSignature: Long = 0L
    private var stableSamples = 0
    private var lastSampleAt = 0L
    private var emittedForCurrentScreen = false

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

            val signature = signatureOf(image)
            if (signature != lastSignature) {
                lastSignature = signature
                stableSamples = 0
                if (emittedForCurrentScreen) {
                    emittedForCurrentScreen = false
                    onFrameChanged()
                }
                return
            }

            stableSamples++
            if (stableSamples < REQUIRED_STABLE_SAMPLES || emittedForCurrentScreen) return

            emittedForCurrentScreen = true
            onFrameSettled(image.toBitmap())
        } finally {
            image.close()
        }
    }

    /**
     * Coarse fingerprint of the frame. Sampling a grid rather than hashing every pixel keeps this
     * cheap enough to run on the image-available callback without dropping frames.
     */
    private fun signatureOf(image: Image): Long {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride

        var signature = 1125899906842597L
        val rowStep = (image.height / SIGNATURE_ROWS).coerceAtLeast(1)
        val colStep = (image.width / SIGNATURE_COLS).coerceAtLeast(1)

        var y = 0
        while (y < image.height) {
            var x = 0
            while (x < image.width) {
                val offset = y * rowStride + x * pixelStride
                if (offset + 3 < buffer.limit()) {
                    signature = 31 * signature + buffer.get(offset)
                    signature = 31 * signature + buffer.get(offset + 1)
                    signature = 31 * signature + buffer.get(offset + 2)
                }
                x += colStep
            }
            y += rowStep
        }
        return signature
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
        const val SAMPLE_INTERVAL_MS = 250L
        const val REQUIRED_STABLE_SAMPLES = 2
        const val SIGNATURE_ROWS = 32
        const val SIGNATURE_COLS = 16
    }
}
