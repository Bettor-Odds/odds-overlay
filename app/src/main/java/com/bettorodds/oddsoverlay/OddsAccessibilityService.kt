package com.bettorodds.oddsoverlay

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.HardwareBuffer
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
 * Watches which app is in front and, inside a target app, reads the screen and paints American odds
 * over its percentages.
 *
 * This replaces the screen-capture approach entirely. An accessibility service gets a real-time
 * event the moment a target app comes forward, reads the screen itself through the screenshot API
 * with no per-session consent, and gets another event the moment the user leaves - so it converts
 * only inside the target app and does nothing otherwise.
 */
class OddsAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val ocr = OcrProcessor()

    private lateinit var windowManager: WindowManager
    private var overlayView: OverlayView? = null
    private var prompt: ActivationPrompt? = null

    private var currentTarget: String? = null
    private var armed = false
    private var lastShotAt = 0L
    private var shotInFlight = false

    override fun onServiceConnected() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return
            // Our own windows never count as a target - except the debug board, which stands in for
            // a real target app so the pipeline can be verified without one.
            val isDebugBoard = BuildConfig.DEBUG &&
                event.className == "$packageName.DebugBoardActivity"
            if (pkg == packageName && !isDebugBoard) return
            onForegroundApp(pkg, isDebugBoard)
        } else if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            if (armed && event.packageName == currentTarget) requestShot()
        }
    }

    override fun onInterrupt() {}

    private fun onForegroundApp(pkg: String, isDebugBoard: Boolean) {
        if (isDebugBoard || TargetApps.isTarget(this, pkg)) {
            if (pkg == currentTarget) return
            currentTarget = pkg
            // The debug board arms immediately; real apps show the prompt unless set to auto-on.
            if (isDebugBoard || TargetApps.isAutoOn(this, pkg)) arm() else showPrompt(pkg)
        } else if (currentTarget != null) {
            leaveTarget()
        }
    }

    /** Begin converting for the current visit. */
    private fun arm() {
        removePrompt()
        armed = true
        attachOverlay()
        requestShot()
    }

    private fun leaveTarget() {
        currentTarget = null
        armed = false
        removePrompt()
        removeOverlay()
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

    private fun requestShot() {
        val now = SystemClock.elapsedRealtime()
        if (shotInFlight || now - lastShotAt < SHOT_INTERVAL_MS) return
        shotInFlight = true
        lastShotAt = now
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    val bitmap = result.hardwareBuffer.use { buffer ->
                        Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                            ?.copy(Bitmap.Config.ARGB_8888, false)
                    }
                    shotInFlight = false
                    if (bitmap != null && armed) convert(bitmap) else bitmap?.recycle()
                }

                override fun onFailure(errorCode: Int) {
                    shotInFlight = false
                }
            }
        )
    }

    private fun convert(bitmap: Bitmap) {
        scope.launch {
            val hits = ocr.recognize(bitmap)
            // The chips read as "-130", never "43.4%", so they never re-trigger detection. A pass
            // that finds nothing is a covered or transient frame - keep the last good chips.
            val styled = if (hits.size >= MIN_HITS_TO_DRAW) ChipStyler.style(bitmap, hits) else null
            bitmap.recycle()
            if (styled != null) withContext(Dispatchers.Main) { overlayView?.show(styled) }
        }
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
        // takeScreenshot is rate-limited by the platform; stay above the floor and re-read on the
        // content-changed events a live board fires anyway.
        const val SHOT_INTERVAL_MS = 1000L
    }
}
