package com.commentblocker.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.commentblocker.app.ui.MainActivity

class CommentBlockerService : AccessibilityService() {

    private lateinit var prefs: SharedPreferences
    private var windowManager: WindowManager? = null
    private val activeOverlays = mutableListOf<View>()
    private val handler = Handler(Looper.getMainLooper())
    private var pendingCheck: Runnable? = null
    private val CHECK_DELAY_MS = 80L

    // Pinkish-white overlay color
    private val OVERLAY_COLOR = Color.argb(245, 255, 200, 210)
    private val SHEET_COLOR  = Color.argb(255, 255, 210, 218)

    private val BUTTON_IDS = listOf(
        "com.instagram.android:id/clips_comment_button",
        "com.instagram.android:id/reel_viewer_comment_button",
        "com.instagram.android:id/comment_icon",
        "com.instagram.android:id/clips_viewer_right_section",
        "com.instagram.android:id/reel_viewer_right_section",
        "com.instagram.android:id/reel_viewer_like_button_container",
        "com.instagram.android:id/comments_count",
        "com.instagram.android:id/like_count",
        "com.instagram.android:id/share_to_button",
        "com.instagram.android:id/row_feed_button_comment"
    )

    private val SHEET_IDS = listOf(
        "com.instagram.android:id/comments_recycler_view",
        "com.instagram.android:id/unified_comments_recycler_view",
        "com.instagram.android:id/comments_header_view",
        "com.instagram.android:id/layout_comment_thread_header",
        "com.instagram.android:id/comments_container",
        "com.instagram.android:id/comment_text",
        "com.instagram.android:id/comment_list_container"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (!isBlockingEnabled()) { clearAllOverlays(); return }
        val pkg = event.packageName?.toString() ?: return
        if (pkg != "com.instagram.android") { clearAllOverlays(); return }
        pendingCheck?.let { handler.removeCallbacks(it) }
        val job = Runnable { scanAndApply() }
        pendingCheck = job
        handler.postDelayed(job, CHECK_DELAY_MS)
    }

    private fun scanAndApply() {
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels

        val rectsToBlock = mutableListOf<Rect>()
        var commentSheetOpen = false

        // Scan ALL windows (comment sheet opens in its own window layer)
        val allWindows: List<AccessibilityWindowInfo> =
            try { windows ?: emptyList() } catch (_: Exception) { emptyList() }

        for (window in allWindows) {
            val root = try { window.root } catch (_: Exception) { null } ?: continue
            if (!commentSheetOpen) commentSheetOpen = hasSheetNode(root)
            collectButtonRects(root, rectsToBlock)
            root.recycle()
        }

        // Fallback to active window
        val rootFb = try { rootInActiveWindow } catch (_: Exception) { null }
        if (rootFb != null) {
            if (!commentSheetOpen) commentSheetOpen = hasSheetNode(rootFb)
            collectButtonRects(rootFb, rectsToBlock)
            rootFb.recycle()
        }

        handler.post {
            clearAllOverlays()

            if (commentSheetOpen) {
                // Cover the entire bottom sheet area
                val sheetTop = (screenH * 0.28).toInt()
                addOverlay(0, sheetTop, screenW, screenH - sheetTop, isSheet = true)
                return@post
            }

            val isCalibrated = prefs.getBoolean(MainActivity.KEY_CALIBRATED, false)

            if (isCalibrated) {
                // Use the user's calibrated position — precise, device-specific
                val xPct = prefs.getFloat(MainActivity.KEY_CAL_X, 0.88f)
                val yPct = prefs.getFloat(MainActivity.KEY_CAL_Y, 0.60f)
                val sizePct = prefs.getFloat(MainActivity.KEY_CAL_SIZE, 0.12f)
                val size = (screenW * sizePct).toInt()
                val cx = (screenW * xPct).toInt()
                val cy = (screenH * yPct).toInt()
                addOverlay(cx - size / 2, cy - size / 2, size, size, isSheet = false)
            } else if (rectsToBlock.isNotEmpty()) {
                // Auto-detected — merge overlapping rects and block each cluster
                val merged = mergeRects(rectsToBlock)
                for (rect in merged) {
                    val p = 10
                    addOverlay(
                        maxOf(0, rect.left - p), maxOf(0, rect.top - p),
                        rect.width() + p * 2, rect.height() + p * 2,
                        isSheet = false
                    )
                }
                // Safety strip over the whole right column
                val leftEdge = merged.minOf { it.left }
                if (leftEdge > screenW * 0.70) {
                    addOverlay(
                        leftEdge - 8,
                        merged.minOf { it.top } - 8,
                        screenW - leftEdge + 8,
                        merged.maxOf { it.bottom } - merged.minOf { it.top } + 16,
                        isSheet = false
                    )
                }
            }
        }
    }

    private fun hasSheetNode(root: AccessibilityNodeInfo): Boolean {
        for (id in SHEET_IDS) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (nodes.isNotEmpty()) { nodes.forEach { it.recycle() }; return true }
        }
        return false
    }

    private fun collectButtonRects(root: AccessibilityNodeInfo, out: MutableList<Rect>) {
        for (id in BUTTON_IDS) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            for (node in nodes) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                if (rect.width() > 0 && rect.height() > 0 && out.none { it == rect }) out.add(Rect(rect))
                node.recycle()
            }
        }
    }

    /** Simple union-based rect merge to avoid stacking overlays on the same button */
    private fun mergeRects(rects: List<Rect>): List<Rect> {
        val merged = mutableListOf<Rect>()
        for (r in rects) {
            val existing = merged.firstOrNull { it.intersects(r.left, r.top, r.right, r.bottom) }
            if (existing != null) existing.union(r)
            else merged.add(Rect(r))
        }
        return merged
    }

    private fun addOverlay(x: Int, y: Int, width: Int, height: Int, isSheet: Boolean) {
        if (width <= 0 || height <= 0) return
        val overlayColor = if (isSheet) SHEET_COLOR else OVERLAY_COLOR

        val view = object : View(this) {
            private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = overlayColor
                style = Paint.Style.FILL
            }
            private val xPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(160, 180, 60, 80)
                strokeWidth = 5f
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
            }
            private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(180, 150, 50, 70)
                textAlign = Paint.Align.CENTER
                textSize = 28f
            }

            override fun onDraw(canvas: Canvas) {
                if (isSheet) {
                    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
                    // Centered label
                    textPaint.textSize = 42f
                    canvas.drawText("Comments hidden", width / 2f, height / 2f, textPaint)
                } else {
                    canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 32f, 32f, bgPaint)
                    // X mark — indicates disabled
                    val m = minOf(width, height) * 0.28f
                    canvas.drawLine(m, m, width - m, height - m, xPaint)
                    canvas.drawLine(width - m, m, m, height - m, xPaint)
                }
            }
        }

        view.setWillNotDraw(false)
        view.isClickable = true
        view.isLongClickable = true
        // Consume ALL touch events — nothing passes through to Instagram
        view.setOnTouchListener { _, _ -> true }

        val params = WindowManager.LayoutParams(
            width, height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // No FLAG_NOT_TOUCHABLE → overlay intercepts and swallows touches
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }

        try {
            windowManager?.addView(view, params)
            activeOverlays.add(view)
        } catch (_: Exception) {}
    }

    private fun clearAllOverlays() {
        for (v in activeOverlays) { try { windowManager?.removeView(v) } catch (_: Exception) {} }
        activeOverlays.clear()
    }

    private fun isBlockingEnabled() = prefs.getBoolean(MainActivity.KEY_BLOCKING_ENABLED, false)

    override fun onInterrupt() = clearAllOverlays()
    override fun onDestroy() {
        super.onDestroy()
        clearAllOverlays()
        handler.removeCallbacksAndMessages(null)
    }
}
