package com.commentblocker.app.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.commentblocker.app.R
import com.commentblocker.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences

    companion object {
        const val PREFS_NAME = "comment_blocker_prefs"
        const val KEY_BLOCKING_ENABLED = "blocking_enabled"
        const val KEY_CALIBRATED = "calibrated"
        const val KEY_CAL_X = "cal_x"
        const val KEY_CAL_Y = "cal_y"
        const val KEY_CAL_SIZE = "cal_size"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Toggle — just save state immediately, no gate
        binding.switchBlock.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_BLOCKING_ENABLED, isChecked).commit()
            updateStatusBadge(isChecked)
        }

        binding.btnOpenSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.btnCalibrate.setOnClickListener {
            startActivity(Intent(this, CalibrationActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        val serviceEnabled = isAccessibilityServiceEnabled()
        val blockingEnabled = prefs.getBoolean(KEY_BLOCKING_ENABLED, false)
        val calibrated = prefs.getBoolean(KEY_CALIBRATED, false)

        // Show/hide service warning banner
        binding.bannerServiceOff.visibility = if (serviceEnabled) View.GONE else View.VISIBLE

        // Toggle reflects saved state — always interactive
        binding.switchBlock.isChecked = blockingEnabled
        updateStatusBadge(blockingEnabled && serviceEnabled)

        // Show calibration status
        binding.tvCalStatus.text = if (calibrated)
            "✓ Position saved — tap to adjust"
        else
            "Not set — tap to locate the comment button"
    }

    private fun updateStatusBadge(active: Boolean) {
        if (active) {
            binding.tvStatus.text = getString(R.string.status_active)
            binding.statusDot.setBackgroundResource(R.drawable.dot_active)
        } else {
            binding.tvStatus.text = getString(R.string.status_inactive)
            binding.statusDot.setBackgroundResource(R.drawable.dot_inactive)
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            if (splitter.next().equals(
                    "${packageName}/.service.CommentBlockerService",
                    ignoreCase = true
                )
            ) return true
        }
        return false
    }
}
