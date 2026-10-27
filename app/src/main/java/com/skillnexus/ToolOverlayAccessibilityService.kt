package com.skillnexus

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.TextView

/**
 * Best-effort visual indicator for activity visible in selected AI apps.
 * Android does not expose those apps' internal tool-call events to third parties.
 * Labels are heuristic and must not be read as confirmed tool calls.
 */
class ToolOverlayAccessibilityService : AccessibilityService() {
    private lateinit var windowManager: WindowManager
    private var overlay: SkillOverlayView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var lastSignal = ""
    private var serviceReady = false

    private val monitoredPackages = setOf(
        "com.anthropic.claude",
        "com.openai.chatgpt",
        "com.facebook.stella",
        "com.meta.ai"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        serviceReady = true
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 500
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (!serviceReady) return

        val packageName = event.packageName?.toString() ?: return

        // Ignore this app's own overlay and other apps' incidental content events.
        // Keyboard/IME events are common while chatting; hiding on every unrelated
        // event caused the overlay to repeatedly disappear and reappear.
        if (packageName == applicationContext.packageName) return
        if (packageName !in monitoredPackages) {
            // Content-change events from keyboards and system UI are frequent.
            // Only a real window transition should hide the overlay; never use an
            // inactivity timer, because streaming responses produce event gaps.
            val systemPackages = setOf(
                "com.android.systemui",
                "com.google.android.inputmethod.latin",
                "com.samsung.android.honeyboard",
                "com.android.inputmethod.latin",
                "com.google.android.inputmethod.latin.beta"
            )
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                packageName !in systemPackages
            ) {
                hideOverlay()
            }
            return
        }

        // Ignore event types that are unlikely to provide useful UI activity.
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
        ) return

        val text = buildString {
            event.text?.forEach { append(it).append(' ') }
            event.contentDescription?.let { append(it) }
        }.lowercase()

        val skill = when {
            listOf("searching the web", "web search", "browser", "website", "검색 중", "웹 검색").any(text::contains) -> "BROWSER"
            listOf("python", "terminal", "command line", "shell", "코드 실행", "터미널").any(text::contains) -> "CODE / SHELL"
            listOf("image", "photo", "vision", "이미지", "사진").any(text::contains) -> "VISION"
            listOf("research", "sources", "citations", "조사", "출처").any(text::contains) -> "RESEARCH"
            listOf("writing code", "generating code", "코드 작성").any(text::contains) -> "CODING"
            else -> "AI ACTIVITY"
        }
        showOverlay(skill)
    }

    override fun onInterrupt() {
        hideOverlay()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        hideOverlay()
        serviceReady = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        hideOverlay()
        serviceReady = false
        super.onDestroy()
    }

    private fun showOverlay(skill: String) {
        // All view/window operations are serialized on the main thread.
        handler.post {
            if (!serviceReady || !::windowManager.isInitialized) return@post

            if (overlay == null) {
                val view = SkillOverlayView()
                val height = (resources.displayMetrics.heightPixels * 0.33f).toInt()
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    height,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP
                }
                try {
                    windowManager.addView(view, params)
                    overlay = view
                } catch (_: Exception) {
                    // Keep the service alive; a later eligible event can retry.
                    return@post
                }
            }

            // Avoid unnecessary redraws when the inferred label has not changed.
            if (lastSignal != skill) {
                lastSignal = skill
                overlay?.setSkill(skill)
            }

            // Keep the same overlay attached while the monitored app is active.
            // A timeout here made it disappear during quiet periods and reappear
            // on the next event, which looked like rapid flickering.
        }
    }

    private fun hideOverlay() {
        handler.post {
            removeOverlayNow()
        }
    }

    private fun removeOverlayNow() {
        val current = overlay ?: return
        overlay = null
        lastSignal = ""
        try {
            if (::windowManager.isInitialized) windowManager.removeView(current)
        } catch (_: Exception) {
            // The system may already have detached the accessibility overlay.
        }
    }

    private inner class SkillOverlayView : FrameLayout(this@ToolOverlayAccessibilityService) {
        private val label: TextView
        private val visual = SkillNetworkView(this@ToolOverlayAccessibilityService)

        init {
            setBackgroundColor(Color.TRANSPARENT)
            val panel = FrameLayout(context).apply {
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.argb(238, 8, 17, 36))
                    cornerRadius = 42f
                    setStroke(2, Color.argb(180, 45, 212, 255))
                }
                elevation = 18f
            }
            addView(panel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
                setMargins(18, 14, 18, 14)
            })
            label = TextView(context).apply {
                text = "SKILL NEXUS  •  POSSIBLE ACTIVITY"
                setTextColor(Color.WHITE)
                textSize = 12f
                gravity = Gravity.CENTER
                letterSpacing = 0.08f
            }
            addView(label, LayoutParams(LayoutParams.MATCH_PARENT, 42).apply {
                gravity = Gravity.TOP
            })
            addView(visual, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            val note = TextView(context).apply {
                text = "HEURISTIC DETECTION · NOT A CONFIRMED TOOL CALL"
                setTextColor(Color.rgb(160, 190, 220))
                textSize = 9f
                gravity = Gravity.CENTER
                letterSpacing = 0.04f
            }
            addView(note, LayoutParams(LayoutParams.MATCH_PARENT, 28).apply {
                gravity = Gravity.BOTTOM
            })
        }

        fun setSkill(skill: String) {
            label.text = "SKILL NEXUS  •  $skill"
            visual.setSkill(skill)
        }
    }

    private class SkillNetworkView(context: android.content.Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var skill = "AI ACTIVITY"
        private var phase = 0f
        private val frame = object : Runnable {
            override fun run() {
                if (!isAttachedToWindow) return
                phase = (phase + 0.08f) % 1f
                invalidate()
                postDelayed(this, 40L)
            }
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            removeCallbacks(frame)
            post(frame)
        }

        override fun onDetachedFromWindow() {
            removeCallbacks(frame)
            super.onDetachedFromWindow()
        }

        fun setSkill(value: String) {
            if (skill == value) return
            skill = value
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val radius = (width.coerceAtMost(height) * 0.27f)
            val nodes = listOf(
                Pair(cx, cy - radius),
                Pair(cx + radius * 0.95f, cy),
                Pair(cx, cy + radius),
                Pair(cx - radius * 0.95f, cy)
            )
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.5f
            paint.color = Color.argb(130, 40, 190, 255)
            nodes.forEach { canvas.drawLine(cx, cy, it.first, it.second, paint) }
            paint.color = Color.argb(170, 30, 255, 210)
            paint.strokeWidth = 4f
            val target = when (skill) {
                "BROWSER" -> 0
                "CODE / SHELL", "CODING" -> 1
                "VISION" -> 2
                "RESEARCH" -> 3
                else -> (phase * nodes.size).toInt().coerceIn(0, nodes.lastIndex)
            }
            val node = nodes[target]
            canvas.drawLine(cx, cy, cx + (node.first - cx) * phase, cy + (node.second - cy) * phase, paint)
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(50, 230, 255)
            canvas.drawCircle(cx, cy, 18f, paint)
            nodes.forEachIndexed { index, p ->
                paint.color = if (index == target) Color.rgb(74, 255, 201) else Color.rgb(40, 130, 210)
                canvas.drawCircle(p.first, p.second, if (index == target) 13f else 9f, paint)
            }
            paint.color = Color.WHITE
            paint.textSize = 11f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("AI", cx, cy + 4f, paint)
            paint.textSize = 10f
            canvas.drawText("BROWSER", nodes[0].first, nodes[0].second - 17f, paint)
            canvas.drawText("CODE", nodes[1].first, nodes[1].second - 17f, paint)
            canvas.drawText("VISION", nodes[2].first, nodes[2].second + 25f, paint)
            canvas.drawText("RESEARCH", nodes[3].first, nodes[3].second - 17f, paint)
        }
    }
}
