package com.bettorodds.oddsoverlay

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Setup and status. The whole point of the accessibility approach is that this screen is visited
 * once: enable the service, then never open the app again - opening a target app is all it takes.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var enableButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        status = findViewById(R.id.status)
        enableButton = findViewById(R.id.enable_button)
        enableButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<TextView>(R.id.supported_apps).text =
            getString(R.string.supported_apps, TargetApps.BUILT_IN.values.joinToString(", "))
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
