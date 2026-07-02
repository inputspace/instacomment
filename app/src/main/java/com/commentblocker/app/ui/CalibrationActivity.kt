package com.commentblocker.app.ui

import android.content.Context
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.commentblocker.app.R

/**
 * Full-screen calibration activity.
 * The screen IS the phone screen. User drags the pink circle to where
 * the Instagram comment button appears on their device, then taps Save.
 */
class CalibrationActivity : AppCompatActivity() {

    private lateinit var dragHandle: View
    private lateinit var container: FrameLayout
    private lateinit var stepText: TextView

    private var dotCenterXPct = 0.88f  // default: right side
    private var dotCenterYPct = 0.62f  // default: ~60% down

    private var dX = 0f
    private var dY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load previous calibration if available
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        dotCenterXPct = prefs.getFloat(MainActivity.KEY_CAL_X, 0.88f)
        dotCenterYPct = prefs.getFloat(MainActivity.KEY_CAL_Y, 0.62f)

        setContentView(R.layout.activity_calibration)
        container = findViewById(R.id.calibrationContainer)
        dragHandle = findViewById(R.id.dragHandle)
        stepText = findViewById(R.id.tvStep)

        val btnSave = findViewById<View>(R.id.btnSave)
        val btnCancel = findViewById<View>(R.id.btnCancel)

        btnSave.setOnClickListener { saveAndFinish() }
        btnCancel.setOnClickListener { finish() }

        // Position the dot after layout is measured
        container.post { positionDot() }

        // Make dot draggable
        dragHandle.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dX = v.x - event.rawX
                    dY = v.y - event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val newX = (event.rawX + dX).coerceIn(0f, (container.width - v.width).toFloat())
                    val newY = (event.rawY + dY).coerceIn(0f, (container.height - v.height).toFloat())
                    v.x = newX
                    v.y = newY
                    // Update percentages in real time
                    dotCenterXPct = (newX + v.width / 2f) / container.width
                    dotCenterYPct = (newY + v.height / 2f) / container.height
                    true
                }
                else -> false
            }
        }
    }

    private fun positionDot() {
        val dotSize = dragHandle.width.takeIf { it > 0 } ?: dpToPx(64)
        dragHandle.x = dotCenterXPct * container.width - dotSize / 2f
        dragHandle.y = dotCenterYPct * container.height - dotSize / 2f
    }

    private fun saveAndFinish() {
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat(MainActivity.KEY_CAL_X, dotCenterXPct)
            .putFloat(MainActivity.KEY_CAL_Y, dotCenterYPct)
            .putFloat(MainActivity.KEY_CAL_SIZE, 0.18f)
            .putBoolean(MainActivity.KEY_CALIBRATED, true)
            .apply()
        Toast.makeText(this, "Position saved!", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}
