package com.commentblocker.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.commentblocker.app.ui.MainActivity

class CommentBlockerService : AccessibilityService() {

    private lateinit var prefs: SharedPreferences
    private var windowManager: WindowManager? = null
    private var actionBarOverlay: View? = null
    private var commentSheetOverlay: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var pendingCheck: Runnable? = null
    private val CHECK_DELAY_MS = 100L

    private val ACTION_BAR_IDS = setOf(
        "com.instagram.android:id/clips_comment_button",
        "com.instagram.android:id/clips_viewer_right_section",
        "com.instagram.android:id/reel_viewer_comment_button",
        "com.instagram.android:id/reel_viewer_like_button_container",
        "com.instagram.android:id/reel_viewer_right_section",
        "com.instagram.android:id/comment_icon",
        "com.instagram.android:id/comments_count",
        "com.instagram.android:id/like_count"
    )

    private val COMMENT_SHEET_IDS = setOf(
        "com.instagram.android:id/comments_recycler_view",
        "com.instagram.android:id/comments_header_view",
        "com.instagram.android:id/unified_comments_recycler_view",
        "com.instagram.android:id/comment_text",
        "com.instagram.android:id/layout_comment_thread_header",
        "com.instagram.android:id/comments_container"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (!isBlockingEnabled()) { removeAllOverlays(); return }
        val packageName = event.packageName?.toString() ?: return
        if (packageName != "com.instagram.android") { removeAllOverlays(); return }
        pendingCheck?.let { handler.removeCallbacks(it) }
        val check = Runnable { scanAndBlock() }
        pendingCheck = check
        handler.postDelayed(check, CHECK_DELAY_MS)
    }

    private fun scanAndBlock() {
        val root = rootInActiveWindow ?: run { removeAllOverlays(); return }
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels

        val sheetNodes = mutableListOf<AccessibilityNodeInfo>()
        for (id in COMMENT_SHEET_IDS) sheetNodes.addAll(root.findAccessibilityNodeInfosByViewId(id))

        if (sheetNodes.isNotEmpty()) {
            var sheetTop = screenHeight
            for (node in sheetNodes) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                if (rect.height() > 0) sheetTop = minOf(sheetTop, rect.top)
            }
            sheetNodes.forEach { it.recycle() }
            val coverTop = maxOf(0, sheetTop - 60)
            showCommentSheetOverlay(0, coverTop, screenWidth, screenHeight - coverTop)
            showActionBarOverlay(screenWidth, screenHeight)
        } else {
            sheetNodes.forEach { it.recycle() }
            removeCommentSheetOverlay()
            val actionNodes = mutableListOf<AccessibilityNodeInfo>()
            for (id in ACTION_BAR_IDS) actionNodes.addAll(root.findAccessibilityNodeInfosByViewId(id))
            if (actionNodes.isNotEmpty()) {
                actionNodes.forEach { it.recycle() }
                showActionBarOverlay(screenWidth, screenHeight)
            } else {
                actionNodes.forEach { it.recycle() }
                removeAllOverlays()
            }
        }
    }

    private fun showActionBarOverlay(screenWidth: Int, screenHeight: Int) {
        val overlayLeft = (screenWidth * 0.82).toInt()
        val overlayTop = (screenHeight * 0.35).toInt()
        val overlayWidth = screenWidth - overlayLeft
        val overlayHeight = (screenHeight * 0.57).toInt()
        handler.post {
            val existing = actionBarOverlay
            if (existing != null) {
                val params = existing.layoutParams as? WindowManager.LayoutParams
                if (params != null) {
                    params.x = overlayLeft; params.y = overlayTop
                    params.width = overlayWidth; params.height = overlayHeight
                    try { windowManager?.updateViewLayout(existing, params) } catch (_: Exception) {}
                }
                return@post
            }
            val view = View(this)
            view.setBackgroundColor(0xFF0A0A0A.toInt())
            val params = WindowManager.LayoutParams(
                overlayWidth, overlayHeight,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.OPAQUE
            ).apply { gravity = Gravity.TOP or Gravity.START; x = overlayLeft; y = overlayTop }
            try { windowManager?.addView(view, params); actionBarOverlay = view } catch (_: Exception) {}
        }
    }

    private fun showCommentSheetOverlay(x: Int, y: Int, width: Int, height: Int) {
        handler.post {
            val existing = commentSheetOverlay
            if (existing != null) {
                val params = existing.layoutParams as? WindowManager.LayoutParams
                if (params != null) {
                    params.x = x; params.y = y; params.width = width; params.height = height
                    try { windowManager?.updateViewLayout(existing, params) } catch (_: Exception) {}
                }
                return@post
            }
            val view = View(this)
            view.setBackgroundColor(0xFF0A0A0A.toInt())
            val params = WindowManager.LayoutParams(
                width, height,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.OPAQUE
            ).apply { gravity = Gravity.TOP or Gravity.START; this.x = x; this.y = y }
            try { windowManager?.addView(view, params); commentSheetOverlay = view } catch (_: Exception) {}
        }
    }

    private fun removeCommentSheetOverlay() {
        handler.post {
            commentSheetOverlay?.let { try { windowManager?.removeView(it) } catch (_: Exception) {}; commentSheetOverlay = null }
        }
    }

    private fun removeAllOverlays() {
        handler.post {
            actionBarOverlay?.let { try { windowManager?.removeView(it) } catch (_: Exception) {}; actionBarOverlay = null }
            commentSheetOverlay?.let { try { windowManager?.removeView(it) } catch (_: Exception) {}; commentSheetOverlay = null }
        }
    }

    private fun isBlockingEnabled(): Boolean = prefs.getBoolean(MainActivity.KEY_BLOCKING_ENABLED, false)
    override fun onInterrupt() = removeAllOverlays()
    override fun onDestroy() { super.onDestroy(); removeAllOverlays(); handler.removeCallbacksAndMessages(null) }
}
