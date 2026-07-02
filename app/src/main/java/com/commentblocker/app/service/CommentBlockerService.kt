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
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val rectsToBlock = mutableListOf<Rect>()
        var commentSheetOpen = false

        val allWindows: List<AccessibilityWindowInfo> = try { windows ?: emptyList() } catch (_: Exception) { emptyList() }

        for (window in allWindows) {
            val root = try { window.root } catch (_: Exception) { null } ?: continue
            if (!commentSheetOpen) {
                for (id in SHEET_IDS) {
                    val nodes = root.findAccessibilityNodeInfosByViewId(id)
                    if (nodes.isNotEmpty()) { commentSheetOpen = true; nodes.forEach { it.recycle() }; break }
                }
            }
            for (id in BUTTON_IDS) {
                val nodes = root.findAccessibilityNodeInfosByViewId(id)
                for (node in nodes) {
                    val rect = Rect()
                    node.getBoundsInScreen(rect)
                    if (rect.width() > 0 && rect.height() > 0 && rectsToBlock.none { it == rect }) rectsToBlock.add(Rect(rect))
                    node.recycle()
                }
            }
            root.recycle()
        }

        val rootFallback = try { rootInActiveWindow } catch (_: Exception) { null }
        if (rootFallback != null) {
            if (!commentSheetOpen) {
                for (id in SHEET_IDS) {
                    val nodes = rootFallback.findAccessibilityNodeInfosByViewId(id)
                    if (nodes.isNotEmpty()) { commentSheetOpen = true; nodes.forEach { it.recycle() }; break }
                }
            }
            for (id in BUTTON_IDS) {
                val nodes = rootFallback.findAccessibilityNodeInfosByViewId(id)
                for (node in nodes) {
                    val rect = Rect()
                    node.getBoundsInScreen(rect)
                    if (rect.width() > 0 && rect.height() > 0 && rectsToBlock.none { it == rect }) rectsToBlock.add(Rect(rect))
                    node.recycle()
                }
            }
            rootFallback.recycle()
        }

        handler.post {
            clearAllOverlays()

            if (commentSheetOpen) {
                val sheetTop = (screenHeight * 0.30).toInt()
                addBlockingOverlay(0, sheetTop, screenWidth, screenHeight - sheetTop, OverlayStyle.SHEET_COVER)
            }

            for (rect in rectsToBlock) {
                val p = 8
                addBlockingOverlay(maxOf(0, rect.left - p), maxOf(0, rect.top - p), rect.width() + p * 2, rect.height() + p * 2, OverlayStyle.DISABLED_BUTTON)
            }

            if (rectsToBlock.isNotEmpty() && !commentSheetOpen) {
                val leftEdge = rectsToBlock.minOf { it.left }
                if (leftEdge > screenWidth * 0.65) {
                    addBlockingOverlay(
                        leftEdge - 16, rectsToBlock.minOf { it.top } - 16,
                        screenWidth - (leftEdge - 16), rectsToBlock.maxOf { it.bottom } - rectsToBlock.minOf { it.top } + 32,
                        OverlayStyle.STRIP_COVER
                    )
                }
            }
        }
    }

    private enum class OverlayStyle { DISABLED_BUTTON, STRIP_COVER, SHEET_COVER }

    private fun addBlockingOverlay(x: Int, y: Int, width: Int, height: Int, style: OverlayStyle) {
        val view = object : View(this) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(canvas: Canvas) {
                when (style) {
                    OverlayStyle.DISABLED_BUTTON -> {
                        paint.color = Color.argb(220, 10, 10, 10)
                        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 24f, 24f, paint)
                        paint.color = Color.argb(180, 80, 80, 80)
                        paint.strokeWidth = 6f
                        paint.style = Paint.Style.STROKE
                        val m = 18f
                        canvas.drawLine(m, m, width - m, height - m, paint)
                        canvas.drawLine(width - m, m, m, height - m, paint)
                        paint.style = Paint.Style.FILL
                    }
                    OverlayStyle.STRIP_COVER, OverlayStyle.SHEET_COVER -> {
                        paint.color = Color.argb(255, 10, 10, 10)
                        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                    }
                }
            }
            override fun onTouchEvent(event: MotionEvent?): Boolean = true
        }
        view.setWillNotDraw(false)
        val params = WindowManager.LayoutParams(
            width, height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            if (style == OverlayStyle.DISABLED_BUTTON) PixelFormat.TRANSLUCENT else PixelFormat.OPAQUE
        ).apply { gravity = Gravity.TOP or Gravity.START; this.x = x; this.y = y }
        try { windowManager?.addView(view, params); activeOverlays.add(view) } catch (_: Exception) {}
    }

    private fun clearAllOverlays() {
        for (view in activeOverlays) { try { windowManager?.removeView(view) } catch (_: Exception) {} }
        activeOverlays.clear()
    }

    private fun isBlockingEnabled(): Boolean = prefs.getBoolean(MainActivity.KEY_BLOCKING_ENABLED, false)
    override fun onInterrupt() = clearAllOverlays()
    override fun onDestroy() { super.onDestroy(); clearAllOverlays(); handler.removeCallbacksAndMessages(null) }
}
