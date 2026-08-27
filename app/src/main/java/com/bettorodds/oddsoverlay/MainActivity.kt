package com.bettorodds.oddsoverlay

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Setup and status. Visited once to enable the service; also surfaces updates - a sideloaded app
 * can't update itself silently, so it downloads the new version in the background and offers a
 * one-tap install.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var enableButton: Button
    private lateinit var updateCard: View
    private lateinit var updateText: TextView
    private lateinit var updateButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        status = findViewById(R.id.status)
        enableButton = findViewById(R.id.enable_button)
        updateCard = findViewById(R.id.update_card)
        updateText = findViewById(R.id.update_text)
        updateButton = findViewById(R.id.update_button)

        enableButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<TextView>(R.id.supported_apps).text =
            getString(R.string.supported_apps, TargetApps.BUILT_IN.values.joinToString(", "))

        checkForUpdate()
    }

    override fun onResume() {
        super.onResume()
        render(isServiceEnabled())
    }

    private fun render(enabled: Boolean) {
        if (enabled) {
            status.setText(R.string.status_on)
            enableButton.setText(R.string.button_manage)
        } else {
            status.setText(R.string.status_off)
            enableButton.setText(R.string.button_enable)
        }
    }

    private fun checkForUpdate() {
        Thread {
            val update = UpdateChecker.check(BuildConfig.VERSION_NAME) ?: return@Thread
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                updateCard.visibility = View.VISIBLE
                updateText.text = getString(R.string.update_preparing, update.latestVersion)
                updateButton.visibility = View.GONE
            }
            // Download in the background so it's ready to install by the time they look.
            val apk = UpdateInstaller.download(this)
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                updateButton.visibility = View.VISIBLE
                if (apk != null) {
                    updateText.text = getString(R.string.update_ready, update.latestVersion)
                    updateButton.setText(R.string.update_install)
                    updateButton.setOnClickListener { UpdateInstaller.install(this, apk) }
                } else {
                    updateText.text = getString(R.string.update_available, update.latestVersion)
                    updateButton.setText(R.string.update_download)
                    updateButton.setOnClickListener {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UpdateChecker.DOWNLOAD_URL)))
                    }
                }
            }
        }.start()
    }

    private fun isServiceEnabled(): Boolean {
        val flat = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val target = "$packageName/${OddsAccessibilityService::class.java.name}"
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(flat)
        return splitter.any { it.equals(target, ignoreCase = true) }
    }
}
