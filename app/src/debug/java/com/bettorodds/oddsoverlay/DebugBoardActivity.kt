package com.bettorodds.oddsoverlay

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * A scrollable board of real text views - stands in for a live app so node reading and scroll
 * tracking can be verified. Debug builds only.
 */
class DebugBoardActivity : Activity() {

    private val games = listOf(
        "Chiefs" to "56.6%", "Bills" to "43.4%",
        "Cowboys" to "40.0%", "Eagles" to "60.0%",
        "49ers" to "72.5%", "Rams" to "27.5%",
        "Ravens" to "48.1%", "Bengals" to "51.9%",
        "Dolphins" to "33.3%", "Jets" to "66.7%",
        "Packers" to "45.0%", "Bears" to "55.0%",
        "Lions" to "62.0%", "Vikings" to "38.0%",
        "Broncos" to "41.5%", "Raiders" to "58.5%",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.parseColor("#0E1116")) }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(40), dp(20), dp(80))
        }
        // repeat the set so there is plenty to scroll through
        repeat(4) { block ->
            column.addView(header("MONEYLINE  -  BLOCK ${block + 1}"))
            games.forEach { (team, pct) -> column.addView(row(team, pct)) }
            column.addView(spacer())
        }
        scroll.addView(column)
        setContentView(scroll)
    }

    private fun header(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(Color.parseColor("#7D8AA0"))
        textSize = 13f
        setPadding(0, dp(20), 0, dp(10))
    }

    private fun row(team: String, pct: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        setBackgroundColor(Color.parseColor("#171C26"))
        addView(TextView(context).apply {
            text = team
            setTextColor(Color.WHITE)
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(context).apply {
            text = pct
            setTextColor(Color.parseColor("#E8EDF5"))
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setBackgroundColor(Color.parseColor("#222B3A"))
            setPadding(dp(20), dp(10), dp(20), dp(10))
        })
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) }
        layoutParams = lp
    }

    private fun spacer() = TextView(this).apply { height = dp(16) }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
