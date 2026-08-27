package com.bettorodds.oddsoverlay

import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * Walks the three grants the overlay needs - notifications, draw-over-other-apps, and screen
 * capture - then hands the projection token to [OverlayService].
 */
class MainActivity : AppCompatActivity() {

    private lateinit var statusView: TextView
    private lateinit var startButton: Button

    private val requestNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { requestOverlayPermission() }

    private val requestProjection = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null) {
            OverlayService.start(this, result.resultCode, data)
            statusView.setText(R.string.status_running)
            moveTaskToBack(true)
        } else {
            statusView.setText(R.string.status_capture_declined)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusView = findViewById(R.id.status)
        startButton = findViewById(R.id.start_button)
        startButton.setOnClickListener { requestNotificationsThenStart() }

        findViewById<Button>(R.id.stop_button).setOnClickListener {
            startService(Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_STOP))
            statusView.setText(R.string.status_stopped)
        }
    }

    override fun onResume() {
        super.onResume()
        if (pendingOverlayGrant && Settings.canDrawOverlays(this)) {
            pendingOverlayGrant = false
            requestScreenCapture()
        }
    }

    private var pendingOverlayGrant = false

    private fun requestNotificationsThenStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            requestOverlayPermission()
        }
    }

    private fun requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            requestScreenCapture()
            return
        }
        pendingOverlayGrant = true
        statusView.setText(R.string.status_needs_overlay)
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
        )
    }

    private fun requestScreenCapture() {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        requestProjection.launch(manager.createScreenCaptureIntent())
    }
}
