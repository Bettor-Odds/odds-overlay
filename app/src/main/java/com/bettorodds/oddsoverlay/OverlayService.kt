package com.bettorodds.oddsoverlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OverlayService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val ocr = OcrProcessor()

    private lateinit var windowManager: WindowManager
    private var overlayView: OverlayView? = null

    private var projection: MediaProjection? = null
    private var capture: ScreenCapture? = null
    private var captureThread: HandlerThread? = null

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() = stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val resultData = intent?.projectionData()
        if (resultCode == 0 || resultData == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Android 14 requires the media-projection foreground service to already be running before
        // the projection token is redeemed, so this ordering is load-bearing, not stylistic.
        startForeground(NOTIFICATION_ID, buildNotification())
        beginCapture(resultCode, resultData)
        return START_NOT_STICKY
    }

    private fun beginCapture(resultCode: Int, resultData: Intent) {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val mediaProjection = manager.getMediaProjection(resultCode, resultData) ?: run {
            stopSelf()
            return
        }
        projection = mediaProjection

        val thread = HandlerThread("capture").also { it.start() }
        captureThread = thread
        val handler = Handler(thread.looper)

        // Registering a callback is mandatory before creating a virtual display on Android 14+.
        mediaProjection.registerCallback(projectionCallback, handler)

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val (width, height, densityDpi) = displayMetrics()
        attachOverlay()

        capture = ScreenCapture(
            projection = mediaProjection,
            width = width,
            height = height,
            densityDpi = densityDpi,
            handler = handler,
            onFrameChanged = {
                // Anything drawn against the previous frame is now in the wrong place. Clearing on
                // change means a scroll briefly shows Novig's own percentages instead of dragging
                // stale chips down the screen.
                scope.launch { withContext(Dispatchers.Main) { overlayView?.clear() } }
            },
            onFrameSettled = { bitmap ->
                scope.launch {
                    val hits = ocr.recognize(bitmap)
                    // A pass that finds nothing is usually a transient bad frame - a glare, an
                    // animation, a momentary cover - not a screen without odds. Keep the existing
                    // chips; genuine navigation clears them through onFrameChanged instead.
                    val styled = if (hits.size >= MIN_HITS_TO_DRAW) {
                        ChipStyler.style(bitmap, hits)
                    } else {
                        null
                    }
                    bitmap.recycle()
                    if (styled != null) {
                        withContext(Dispatchers.Main) { overlayView?.show(styled) }
                    }
                }
            }
        ).also { it.start() }
    }

    private fun attachOverlay() {
        val view = OverlayView(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        windowManager.addView(view, params)
        overlayView = view
    }

    /** Physical pixels including system bars, so OCR coordinates and overlay coordinates agree. */
    private fun displayMetrics(): Triple<Int, Int, Int> {
        val densityDpi = resources.configuration.densityDpi
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            Triple(bounds.width(), bounds.height(), densityDpi)
        } else {
            @Suppress("DEPRECATION")
            val metrics = DisplayMetrics().also { windowManager.defaultDisplay.getRealMetrics(it) }
            Triple(metrics.widthPixels, metrics.heightPixels, metrics.densityDpi)
        }
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
        )

        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    null,
                    getString(R.string.notification_stop),
                    stopIntent
                ).build()
            )
            .build()
    }

    override fun onDestroy() {
        capture?.stop()
        projection?.unregisterCallback(projectionCallback)
        projection?.stop()
        overlayView?.let { runCatching { windowManager.removeView(it) } }
        overlayView = null
        captureThread?.quitSafely()
        ocr.close()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "odds_overlay"
        private const val NOTIFICATION_ID = 1
        private const val MIN_HITS_TO_DRAW = 2
        const val ACTION_STOP = "com.bettorodds.oddsoverlay.STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        fun start(context: Context, resultCode: Int, resultData: Intent) {
            val intent = Intent(context, OverlayService::class.java)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, resultData)
            context.startForegroundService(intent)
        }
    }
}

/** Reads the projection [Intent] with the type-safe overload on API 33+, the deprecated one below. */
private fun Intent.projectionData(): Intent? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(OverlayService.EXTRA_RESULT_DATA, Intent::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(OverlayService.EXTRA_RESULT_DATA)
    }
