package com.bettorodds.oddsoverlay

import android.app.Activity
import android.os.Bundle
import android.widget.ImageView

/**
 * A perfectly static full-screen percentage board, for verifying the overlay without depending on a
 * live app. Debug builds only. Launch with:
 *   adb shell am start -n com.bettorodds.oddsoverlay/.DebugBoardActivity
 */
class DebugBoardActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val image = ImageView(this).apply {
            setImageResource(R.drawable.test_board)
            scaleType = ImageView.ScaleType.FIT_XY
        }
        setContentView(image)
    }
}
