/**
 * Role: Custom overlay view for highlighting interactive tutorial areas on top of the player.
 * Responsibility: Renders targeted circular/rounded cutouts for buttons and highlighted screen zones (top corners, center, left/right swipes).
 * Details: Uses PorterDuffClear on semi-transparent scrim with pulsing animated glowing borders.
 */
package com.example.mxoffline.player.dialog

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

class TourHighlightOverlay(context: Context) : View(context) {

    enum class HighlightType {
        NONE,
        VIEW_TARGET,
        ZONE_TOP_LEFT_CORNER,
        ZONE_TOP_RIGHT_CORNER,
        ZONE_CENTER_BOOST,
        ZONE_LEFT_BRIGHTNESS,
        ZONE_RIGHT_VOLUME
    }

    private var currentType: HighlightType = HighlightType.NONE
    private val targetRect = RectF()
    private var cornerRadiusPx = 24f
    private var zoneLabel = ""

    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x88000000.toInt()
    }

    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = com.example.mxoffline.util.UiUtils.COLOR_MX_BLUE
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 34f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
        setShadowLayer(8f, 2f, 2f, 0xdd000000.toInt())
    }

    private val pulseAnimator = ValueAnimator.ofFloat(0.5f, 1.0f).apply {
        duration = 900
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener {
            val alphaVal = (it.animatedValue as Float * 255).toInt()
            borderPaint.alpha = alphaVal
            invalidate()
        }
    }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        isClickable = true
        isFocusable = false
    }

    fun highlightTargetView(view: View, paddingPx: Float = 16f, cornerRadius: Float = 24f) {
        val loc = IntArray(2)
        view.getLocationInWindow(loc)
        val myLoc = IntArray(2)
        getLocationInWindow(myLoc)

        val x = (loc[0] - myLoc[0]).toFloat()
        val y = (loc[1] - myLoc[1]).toFloat()

        targetRect.set(
            x - paddingPx,
            y - paddingPx,
            x + view.width + paddingPx,
            y + view.height + paddingPx
        )
        this.cornerRadiusPx = cornerRadius
        this.zoneLabel = ""
        this.currentType = HighlightType.VIEW_TARGET
        startPulse()
    }

    fun highlightZone(type: HighlightType, label: String) {
        this.currentType = type
        this.zoneLabel = label
        this.cornerRadiusPx = 32f
        if (width > 0 && height > 0) {
            updateZoneRect(width.toFloat(), height.toFloat())
        }
        startPulse()
    }

    fun getTargetRect(): RectF = RectF(targetRect)

    fun clearHighlight() {
        pulseAnimator.cancel()
        currentType = HighlightType.NONE
        zoneLabel = ""
        targetRect.setEmpty()
        visibility = GONE
        invalidate()
    }

    private fun startPulse() {
        visibility = VISIBLE
        if (!pulseAnimator.isRunning) {
            pulseAnimator.start()
        }
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0 && currentType != HighlightType.VIEW_TARGET && currentType != HighlightType.NONE) {
            updateZoneRect(w.toFloat(), h.toFloat())
        }
    }

    private fun updateZoneRect(w: Float, h: Float) {
        when (currentType) {
            HighlightType.ZONE_TOP_LEFT_CORNER -> {
                targetRect.set(16f, 16f, w * 0.35f, h * 0.28f)
            }
            HighlightType.ZONE_TOP_RIGHT_CORNER -> {
                targetRect.set(w * 0.65f, 16f, w - 16f, h * 0.28f)
            }
            HighlightType.ZONE_CENTER_BOOST -> {
                val left = w * 0.25f
                val top = h * 0.28f
                targetRect.set(left, top, w * 0.75f, top + h * 0.44f)
            }
            HighlightType.ZONE_LEFT_BRIGHTNESS -> {
                targetRect.set(24f, h * 0.15f, w * 0.40f, h * 0.82f)
            }
            HighlightType.ZONE_RIGHT_VOLUME -> {
                targetRect.set(w * 0.60f, h * 0.15f, w - 24f, h * 0.82f)
            }
            HighlightType.VIEW_TARGET, HighlightType.NONE -> {
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (currentType == HighlightType.NONE) return

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        // 1. Draw overall dim scrim
        canvas.drawRect(0f, 0f, w, h, scrimPaint)

        // 2. Compute zone rects if not a direct view target
        if (currentType != HighlightType.VIEW_TARGET) {
            updateZoneRect(w, h)
        }

        if (!targetRect.isEmpty) {
            // Cut hole in scrim
            canvas.drawRoundRect(targetRect, cornerRadiusPx, cornerRadiusPx, clearPaint)
            // Draw glowing border
            canvas.drawRoundRect(targetRect, cornerRadiusPx, cornerRadiusPx, borderPaint)

            // Draw informative banner text inside or above zone
            if (zoneLabel.isNotEmpty()) {
                val cx = targetRect.centerX()
                val cy = targetRect.centerY() + 12f
                canvas.drawText(zoneLabel, cx, cy, labelPaint)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pulseAnimator.cancel()
    }
}
