package com.bettorodds.oddsoverlay

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Watches the foreground app and, inside a target app, paints American odds over its percentages.
 *
 * Reads odds from the view tree (fast, exact, live) and only falls back to a screenshot + OCR when
 * the app exposes no readable text. Reads on a per-frame cadence while scrolling and a lazy cadence
 * when the board is still, and disarms after a spell of inactivity or when the user leaves the app.
 */
class OddsAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val ocr = OcrProcessor()
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var windowManager: WindowManager
    private var overlayView: OverlayView? = null
    private var prompt: ActivationPrompt? = null

    private var screenWidth = 0
    private var screenHeight = 0

    private var currentTarget: String? = null
    private var armed = false
    private var activatedThisVisit = false
    private var chipStyle: ChipStyle = ThemeSampler.DARK

    private var readPending = false
    private var pendingFast = false
    private var lastOcrAt = 0L
    private var ocrInFlight = false

    private val readRunnable = Runnable { readPending = false; readNodes() }
    private val disarmForIdle = Runnable { if (armed) disarm(keepVisit = true) }

    override fun onServiceConnected() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val bounds = windowManager.currentWindowMetrics.bounds
        screenWidth = bounds.width()
        screenHeight = bounds.height()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val pkg = event.packageName?.toString() ?: return
                val isDebugBoard = BuildConfig.DEBUG &&
                    event.className == "$packageName.DebugBoardActivity"
                if (pkg == packageName && !isDebugBoard) return
                onForegroundApp(pkg, isDebugBoard)
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (event.packageName == currentTarget) onTargetActivity(fast = false)
            }

            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                if (event.packageName != currentTarget) return
                if (armed) {
                    val dy = event.scrollDeltaY
                    val dx = event.scrollDeltaX
                    if (dy != 0 || dx != 0) overlayView?.nudge(-dx.toFloat(), -dy.toFloat())
                }
                onTargetActivity(fast = true)   // re-arms if idle-disarmed
            }
        }
    }

    override fun onInterrupt() {}

    private fun onForegroundApp(pkg: String, isDebugBoard: Boolean) {
        if (isDebugBoard || TargetApps.isTarget(this, pkg)) {
            if (pkg == currentTarget) return
            disarm(keepVisit = false)           // clear the previous app's chips/prompt
            removePrompt()
            currentTarget = pkg
            if (isDebugBoard || TargetApps.isAutoOn(this, pkg)) arm() else showPrompt(pkg)
        } else if (currentTarget != null) {
            leaveTarget()
        }
    }

    /** Any interaction inside the target: keep the overlay alive and push out the idle timer. */
    private fun onTargetActivity(fast: Boolean) {
        if (!armed && activatedThisVisit) arm()      // re-arm after an idle disarm
        if (!armed) return
        resetIdleTimer()
        scheduleRead(fast)
    }

    private fun arm() {
        removePrompt()
        armed = true
        activatedThisVisit = true
        attachOverlay()
        refreshTheme()
        resetIdleTimer()
        readNodes()            // paint on the first frame
        scheduleRead(fast = true)
    }

    private fun disarm(keepVisit: Boolean) {
        armed = false
        if (!keepVisit) activatedThisVisit = false
        handler.removeCallbacks(disarmForIdle)
        handler.removeCallbacks(readRunnable)
        readPending = false
        removeOverlay()
    }

    private fun currentScreenBounds() {
        val bounds = windowManager.currentWindowMetrics.bounds
        screenWidth = bounds.width()
        screenHeight = bounds.height()
    }

    private fun leaveTarget() {
        currentTarget = null
        disarm(keepVisit = false)
        removePrompt()
    }

    private fun resetIdleTimer() {
        handler.removeCallbacks(disarmForIdle)
        handler.postDelayed(disarmForIdle, IDLE_TIMEOUT_MS)
    }

    /** Fast reads track scrolling; slow reads keep a still board fresh without burning battery. */
    private fun scheduleRead(fast: Boolean) {
        if (readPending) {
            if (fast && !pendingFast) {              // upgrade a lazy read to a fast one
                handler.removeCallbacks(readRunnable)
                pendingFast = true
                handler.postDelayed(readRunnable, FAST_READ_MS)
            }
            return
        }
        readPending = true
        pendingFast = fast
        handler.postDelayed(readRunnable, if (fast) FAST_READ_MS else SLOW_READ_MS)
    }

    private fun readNodes() {
        if (!armed) return
        currentScreenBounds()
        val root = try {
            rootInActiveWindow
        } catch (e: Exception) {
            null
        } ?: return
        val result = NodeReader.read(root, screenWidth, screenHeight)
        when {
            result.hits.size >= MIN_HITS_TO_DRAW -> {
                val styled = result.hits.map {
                    StyledHit(it.bounds, it.display, chipStyle.backgroundColor, chipStyle.textColor)
                }
                overlayView?.show(styled)
            }
            result.textNodeCount > 0 -> overlayView?.clear()
            else -> requestOcrFallback()
        }
    }

    private fun refreshTheme() {
        takeScreenshotBitmap { bitmap ->
            chipStyle = ThemeSampler.sample(bitmap)
            bitmap.recycle()
        }
    }

    private fun requestOcrFallback() {
        val now = SystemClock.elapsedRealtime()
        if (ocrInFlight || now - lastOcrAt < OCR_INTERVAL_MS) return
        ocrInFlight = true
        lastOcrAt = now
        takeScreenshotBitmap { bitmap ->
            scope.launch {
                val hits = ocr.recognize(bitmap)
                val styled = if (hits.size >= MIN_HITS_TO_DRAW) ChipStyler.style(bitmap, hits) else null
                bitmap.recycle()
                ocrInFlight = false
                if (styled != null && armed) withContext(Dispatchers.Main) { overlayView?.show(styled) }
            }
        }
    }

    private inline fun takeScreenshotBitmap(crossinline onBitmap: (Bitmap) -> Unit) {
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    val bitmap = result.hardwareBuffer.use { buffer ->
                        Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                            ?.copy(Bitmap.Config.ARGB_8888, false)
                    }
                    if (bitmap != null) onBitmap(bitmap) else ocrInFlight = false
                }

                override fun onFailure(errorCode: Int) {
                    ocrInFlight = false
                }
            }
        )
    }

    private fun showPrompt(pkg: String) {
        removePrompt()
        val name = TargetApps.BUILT_IN[pkg] ?: appLabel(pkg)
        prompt = ActivationPrompt(this, windowManager, name,
            onTurnOn = { arm() },
            onAlways = { TargetApps.setAutoOn(this, pkg, true); arm() },
            onDismiss = { removePrompt() }
        ).also { it.show() }
    }

    private fun attachOverlay() {
        if (overlayView != null) return
        val view = OverlayView(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        windowManager.addView(view, params)
        overlayView = view
    }

    private fun removeOverlay() {
        overlayView?.let { runCatching { windowManager.removeView(it) } }
        overlayView = null
    }

    private fun removePrompt() {
        prompt?.remove()
        prompt = null
    }

    private fun appLabel(pkg: String): String = try {
        val pm = packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }

    override fun onDestroy() {
        leaveTarget()
        ocr.close()
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val MIN_HITS_TO_DRAW = 2
        const val FAST_READ_MS = 16L
        const val SLOW_READ_MS = 150L
        const val OCR_INTERVAL_MS = 1000L
        const val IDLE_TIMEOUT_MS = 3 * 60 * 1000L
    }
}
