package com.commentblocker.app.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager
import androidx.appcompat.app.AppCompatActivity
import com.commentblocker.app.R
import com.commentblocker.app.databinding.ActivityMainBinding
import com.commentblocker.app.service.CommentBlockerService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences

    companion object {
        const val PREFS_NAME = "comment_blocker_prefs"
        const val KEY_BLOCKING_ENABLED = "blocking_enabled"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        binding.btnOpenSettings.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        binding.switchBlock.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_BLOCKING_ENABLED, isChecked).apply()
            updateStatus(isChecked)
        }
    }

    override fun onResume() {
        super.onResume()
        val serviceEnabled = isAccessibilityServiceEnabled()
        val blockingEnabled = prefs.getBoolean(KEY_BLOCKING_ENABLED, false)

        binding.switchBlock.isEnabled = serviceEnabled
        binding.switchBlock.isChecked = blockingEnabled && serviceEnabled

        updateStatus(blockingEnabled && serviceEnabled)
    }

    private fun updateStatus(active: Boolean) {
        if (active) {
            binding.tvStatus.text = getString(R.string.status_active)
            binding.statusDot.setBackgroundResource(R.drawable.dot_active)
        } else {
            binding.tvStatus.text = getString(R.string.status_inactive)
            binding.statusDot.setBackgroundResource(R.drawable.dot_inactive)
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(
                    "${packageName}/.service.CommentBlockerService",
                    ignoreCase = true
                )
            ) {
                return true
            }
        }
        return false
    }
}
