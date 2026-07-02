package com.commentblocker.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.commentblocker.app.ui.MainActivity

/**
 * Accessibility service that monitors Instagram and draws an opaque overlay
 * over the comment section area when a Reel is detected in the feed.
 *
 * Strategy:
 *  - Listen for window/content-change events from com.instagram.android
 *  - Scan the accessibility node tree for comment-related containers
 *  - If found, measure their screen position and cover them with a Window overlay
 *  - If not found (e.g. user navigated away), remove the overlay
 */
class CommentBlockerService : AccessibilityService() {

    private lateinit var prefs: SharedPreferences
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private val handler = Handler(Looper.getMainLooper())

    // Debounce rapid events
    private var pendingCheck: Runnable? = null
    private val CHECK_DELAY_MS = 120L

    // Known accessibility view IDs and content descriptions used by Instagram
    // for the comment section / comment button row on Reels.
    private val COMMENT_VIEW_IDS = setOf(
        "com.instagram.android:id/clips_comment_button",
        "com.instagram.android:id/row_comment_container",
        "com.instagram.android:id/comment_icon",
        "com.instagram.android:id/comments_count",
        "com.instagram.android:id/like_count",
        "com.instagram.android:id/action_bar_container",
        "com.instagram.android:id/reel_viewer_comment_button",
        "com.instagram.android:id/reel_viewer_like_button_container",
        "com.instagram.android:id/clips_viewer_right_section"
    )

    // Container IDs that wrap the entire right-side action row on Reels
    private val ACTION_ROW_IDS = setOf(
        "com.instagram.android:id/clips_viewer_right_section",
        "com.instagram.android:id/reel_viewer_right_section",
        "com.instagram.android:id/tab_switcher_container"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (!isBlockingEnabled()) {
            removeOverlay()
            return
        }

        val packageName = event.packageName?.toString() ?: return
        if (packageName != "com.instagram.android") {
            removeOverlay()
            return
        }

        // Debounce rapid events
        pendingCheck?.let { handler.removeCallbacks(it) }
        val check = Runnable { scanAndBlock(event.source) }
        pendingCheck = check
        handler.postDelayed(check, CHECK_DELAY_MS)
    }

    private fun scanAndBlock(rootNode: AccessibilityNodeInfo?) {
        val root = rootNode ?: rootInActiveWindow ?: run {
            removeOverlay()
            return
        }

        // Try to find comment-related nodes
        val commentNodes = mutableListOf<AccessibilityNodeInfo>()

        for (id in COMMENT_VIEW_IDS) {
            val found = root.findAccessibilityNodeInfosByViewId(id)
            commentNodes.addAll(found)
        }

        if (commentNodes.isEmpty()) {
            // Also try searching by content description (fallback)
            val byDesc = findNodesByContentDescription(root, "Comment")
            commentNodes.addAll(byDesc)
        }

        if (commentNodes.isNotEmpty()) {
            // Find the bounding rect of the entire action area to cover
            showOverlayForNodes(commentNodes)
        } else {
            removeOverlay()
        }

        commentNodes.forEach { it.recycle() }
    }

    private fun findNodesByContentDescription(
        node: AccessibilityNodeInfo,
        keyword: String
    ): List<AccessibilityNodeInfo> {
        val results = mutableListOf<AccessibilityNodeInfo>()
        val desc = node.contentDescription?.toString() ?: ""
        if (desc.contains(keyword, ignoreCase = true)) {
            results.add(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            results.addAll(findNodesByContentDescription(child, keyword))
        }
        return results
    }

    private fun showOverlayForNodes(nodes: List<AccessibilityNodeInfo>) {
        // Compute bounding rect that covers all found nodes
        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = Int.MIN_VALUE
        var bottom = Int.MIN_VALUE

        for (node in nodes) {
            val rect = android.graphics.Rect()
            node.getBoundsInScreen(rect)
            if (rect.width() > 0 && rect.height() > 0) {
                left = minOf(left, rect.left)
                top = minOf(top, rect.top)
                right = maxOf(right, rect.right)
                bottom = maxOf(bottom, rect.bottom)
            }
        }

        if (left == Int.MAX_VALUE) {
            removeOverlay()
            return
        }

        // Expand the overlay to cover a generous area around the comment section
        // The comment section on Reels is typically the right-side action bar
        // We expand it to cover the full right portion of the screen
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels

        // Cover right 15% of screen height range where action buttons appear
        // (roughly bottom 55% to bottom 10% of screen)
        val overlayTop = (screenHeight * 0.38).toInt()
        val overlayBottom = (screenHeight * 0.92).toInt()
        val overlayLeft = (screenWidth * 0.72).toInt()
        val overlayWidth = screenWidth - overlayLeft
        val overlayHeight = overlayBottom - overlayTop

        handler.post {
            if (overlayView != null) {
                // Update existing overlay position
                val params = overlayView!!.layoutParams as? WindowManager.LayoutParams
                if (params != null) {
                    params.x = overlayLeft
                    params.y = overlayTop
                    params.width = overlayWidth
                    params.height = overlayHeight
                    try {
                        windowManager?.updateViewLayout(overlayView, params)
                    } catch (_: Exception) {}
                }
                return@post
            }

            // Create new overlay
            val view = View(this)
            view.setBackgroundColor(0xFF0A0A0A.toInt()) // near-black, same as Reels bg

            val params = WindowManager.LayoutParams(
                overlayWidth,
                overlayHeight,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.OPAQUE
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = overlayLeft
                y = overlayTop
            }

            try {
                windowManager?.addView(view, params)
                overlayView = view
            } catch (_: Exception) {}
        }
    }

    private fun removeOverlay() {
        handler.post {
            overlayView?.let {
                try {
                    windowManager?.removeView(it)
                } catch (_: Exception) {}
                overlayView = null
            }
        }
    }

    private fun isBlockingEnabled(): Boolean {
        return prefs.getBoolean(MainActivity.KEY_BLOCKING_ENABLED, false)
    }

    override fun onInterrupt() {
        removeOverlay()
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
        handler.removeCallbacksAndMessages(null)
    }
}
