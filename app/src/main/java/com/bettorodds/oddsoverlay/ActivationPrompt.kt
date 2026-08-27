package com.bettorodds.oddsoverlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The "Show American odds?" card that slides up when a target app opens. A touchable accessibility
 * overlay - unlike the odds chips, this one takes taps.
 */
class ActivationPrompt(
    private val context: Context,
    private val windowManager: WindowManager,
    private val appName: String,
    private val onTurnOn: () -> Unit,
    private val onAlways: () -> Unit,
    private val onDismiss: () -> Unit
) {
    private var view: View? = null

    fun show() {
        if (view != null) return
        val root = buildView()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            y = dp(24)
        }
        windowManager.addView(root, params)
        view = root
    }

    fun remove() {
        view?.let { runCatching { windowManager.removeView(it) } }
        view = null
    }

    private fun buildView(): View {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(Color.parseColor("#171C26"))
            }
        }
        val margin = dp(16)

        val title = TextView(context).apply {
            text = context.getString(R.string.prompt_title, appName)
            setTextColor(Color.WHITE)
            textSize = 17f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        val turnOn = Button(context).apply {
            text = context.getString(R.string.prompt_turn_on)
            setOnClickListener { remove(); onTurnOn() }
        }
        val always = TextView(context).apply {
            text = context.getString(R.string.prompt_always)
            setTextColor(Color.parseColor("#39D98A"))
            textSize = 14f
            setPadding(0, dp(12), 0, 0)
            setOnClickListener { remove(); onAlways() }
        }
        val dismiss = TextView(context).apply {
            text = context.getString(R.string.prompt_not_now)
            setTextColor(Color.parseColor("#9FB0CC"))
            textSize = 14f
            setPadding(0, dp(10), 0, 0)
            setOnClickListener { remove(); onDismiss() }
        }

        card.addView(title)
        card.addView(turnOn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(14) })
        card.addView(always)
        card.addView(dismiss)

        val wrapper = LinearLayout(context).apply {
            setPadding(margin, 0, margin, 0)
            addView(card, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
        return wrapper
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
