/**
 * Role: Interactive tour and functionality tutorial coordinator.
 * Responsibility: Guides users through player gestures, tray buttons, and playback controls step by step.
 * Details: Displays an interactive walkthrough using a non-blocking, translucent floating card that dynamically moves away from the highlighted element.
 */
package com.example.mxoffline.player.dialog

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.example.mxoffline.player.ui.PlayerUiViews
import com.example.mxoffline.util.UiUtils

class PlayerFeatureTourHelper(
    private val context: Context,
    private val views: PlayerUiViews,
    private val controlsLock: com.example.mxoffline.player.screen.PlayerControlsLockManager? = null,
    private val isPlayerPlaying: (() -> Boolean)? = null,
    private val pausePlayer: (() -> Unit)? = null,
    private val resumePlayer: (() -> Unit)? = null
) {

    data class TourStep(
        val title: String,
        val targetName: String,
        val description: String,
        val zoneType: TourHighlightOverlay.HighlightType = TourHighlightOverlay.HighlightType.NONE,
        val zoneLabel: String = "",
        val targetViewProvider: ((PlayerUiViews) -> View?)? = null
    )

    private enum class CardPlacement {
        TOP,
        BOTTOM,
        LEFT,
        RIGHT
    }

    private val steps = listOf(
        TourStep(
            title = "Video Title",
            targetName = "Title (Top Header)",
            description = "Shows the active video title with automatic marquee scroll. Tap the title anytime to see complete file details (duration, resolution, codec, decoder).",
            targetViewProvider = { it.titleView }
        ),
        TourStep(
            title = "Feature Guide (Info)",
            targetName = "ⓘ Info Button (Top Header)",
            description = "Opens this interactive walkthrough anytime to discover all gestures and controls.",
            targetViewProvider = { it.helpButton }
        ),
        TourStep(
            title = "Player Settings Menu",
            targetName = "☰ Menu Button (Top Right)",
            description = "Access advanced player settings: playback speed, orientation lock, audio tracks, external subtitles (.srt, .vtt, .ass), HW/SW decoder, and sleep timer.",
            targetViewProvider = { it.menuButton }
        ),
        TourStep(
            title = "Top Quick Tray",
            targetName = "Quick Action Buttons (Top Tray)",
            description = "Scrollable tray for one-tap actions: Speed, Orientation Lock, Fit/Fill/Zoom, In-player Playlist, 4-tap Safe Delete, Audio, Subtitles, HW/SW switcher, and Timer. Fully customizable via Settings.",
            targetViewProvider = { it.quickButtonsLayout }
        ),
        TourStep(
            title = "Top-Left 5s Rewind",
            targetName = "Top-Left 1/4th Corner (Gesture Zone)",
            description = "Double-tap the highlighted top-left corner anytime to instantly rewind 5 seconds (⟲ 5s).",
            zoneType = TourHighlightOverlay.HighlightType.ZONE_TOP_LEFT_CORNER,
            zoneLabel = "⏪ Double-Tap ⟲ 5s"
        ),
        TourStep(
            title = "Top-Right 5s Fast-Forward",
            targetName = "Top-Right 1/4th Corner (Gesture Zone)",
            description = "Double-tap the highlighted top-right corner anytime to fast-forward 5 seconds (5s ⟳).",
            zoneType = TourHighlightOverlay.HighlightType.ZONE_TOP_RIGHT_CORNER,
            zoneLabel = "⏩ Double-Tap 5s ⟳"
        ),
        TourStep(
            title = "Center Double-Tap Play/Pause",
            targetName = "Center Screen (Gesture Zone)",
            description = "Double-tap anywhere in the center to quickly Toggle Play / Pause.",
            zoneType = TourHighlightOverlay.HighlightType.ZONE_CENTER_BOOST,
            zoneLabel = "▶ / Ⅱ Double-Tap"
        ),
        TourStep(
            title = "Hold to Accelerate (Speed Boost)",
            targetName = "Screen Center (Gesture)",
            description = "Touch and hold anywhere on the video to boost speed (2.0× or custom). Slide horizontally while holding to adjust speed from 0.25× up to 8.0×.",
            zoneType = TourHighlightOverlay.HighlightType.ZONE_CENTER_BOOST,
            zoneLabel = "⚡ Hold to Boost Speed"
        ),
        TourStep(
            title = "Brightness Swipe Control",
            targetName = "Left Screen Half (Vertical Swipe)",
            description = "Swipe vertically on the highlighted left side to adjust screen brightness (0% – 100%).",
            zoneType = TourHighlightOverlay.HighlightType.ZONE_LEFT_BRIGHTNESS,
            zoneLabel = "☀ Swipe Brightness"
        ),
        TourStep(
            title = "Volume & 200% Audio Boost",
            targetName = "Right Screen Half (Vertical Swipe)",
            description = "Swipe vertically on the highlighted right side to adjust volume, with up to 200% Audio Boost.",
            zoneType = TourHighlightOverlay.HighlightType.ZONE_RIGHT_VOLUME,
            zoneLabel = "🔊 Swipe Volume (+200%)"
        ),
        TourStep(
            title = "Skip Anime Opening (+90s OP)",
            targetName = "+90s OP Button (Bottom Left, above timeline)",
            description = "Instantly skips 90s opening intros (80s seek duration) with one tap.",
            targetViewProvider = { it.skipOpBtn }
        ),
        TourStep(
            title = "5-Second Seek Buttons",
            targetName = "⟲ 5s / 5s ⟳ (Bottom Actions Bar)",
            description = "Tap to jump 5 seconds backward or forward. Hold down either button for continuous high-speed scrubbing.",
            targetViewProvider = { it.seekBack5Btn }
        ),
        TourStep(
            title = "Touch Screen Lock",
            targetName = "🔒 Lock Button (Bottom Actions Bar)",
            description = "Locks the screen to prevent accidental touches. Tap the screen when locked to reveal the floating unlock button.",
            targetViewProvider = { it.lockBtn }
        ),
        TourStep(
            title = "Picture-in-Picture (PiP)",
            targetName = "⧉ PiP Button (Bottom Actions Bar)",
            description = "Minimizes video into a floating window with Play/Pause and Next/Previous playlist navigation.",
            targetViewProvider = { it.pipBtn }
        ),
        TourStep(
            title = "Mark as Seen",
            targetName = "✔ Mark Done (Bottom Actions Bar)",
            description = "Manually mark or unmark the current video as seen. Completed videos are automatically grouped and ordered by last seen in the library Seen tab.",
            targetViewProvider = { it.markDoneBtn }
        )
    )

    private var currentStepIndex = 0
    private var highlightOverlay: TourHighlightOverlay? = null
    private var cardContainer: LinearLayout? = null
    private var stepBadgeView: TextView? = null
    private var titleTextView: TextView? = null
    private var targetLabelView: TextView? = null
    private var descTextView: TextView? = null
    private var prevButton: Button? = null
    private var nextButton: Button? = null

    private var wasPlayingBeforeTour = false

    val isShowing: Boolean
        get() = cardContainer != null && highlightOverlay != null

    fun showTour(startIndex: Int = 0) {
        currentStepIndex = startIndex.coerceIn(0, steps.size - 1)

        // Lock controls to visible and prevent auto-hide
        controlsLock?.isTourActive = true
        controlsLock?.cancelHideControls()

        // Pause playback so video doesn't run away or trigger background state changes
        wasPlayingBeforeTour = isPlayerPlaying?.invoke() == true
        if (wasPlayingBeforeTour) {
            pausePlayer?.invoke()
        }

        views.overlayContainer.visibility = View.VISIBLE
        attachOverlay()
        attachCard()
        showCurrentStep(animate = false)
    }

    fun dismissTour() {
        controlsLock?.isTourActive = false

        highlightOverlay?.let { views.root.removeView(it) }
        highlightOverlay = null

        cardContainer?.let { views.root.removeView(it) }
        cardContainer = null

        if (wasPlayingBeforeTour) {
            resumePlayer?.invoke()
        }
        controlsLock?.scheduleHideControls()
    }

    private fun attachOverlay() {
        if (highlightOverlay == null) {
            val overlay = TourHighlightOverlay(context)
            views.root.addView(
                overlay,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            highlightOverlay = overlay
        }
    }

    private fun attachCard() {
        if (cardContainer == null) {
            val card = buildCard()
            views.root.addView(card)
            cardContainer = card
        }
    }

    private fun buildCard(): LinearLayout {
        val dp = { v: Int -> UiUtils.dp(context, v) }
        val dpF = { v: Float -> v * context.resources.displayMetrics.density }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(14))
            isClickable = true
            isFocusable = true

            val cardBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpF(16f)
                setColor(0xd8141824.toInt()) // Sleek translucent dark acrylic
                setStroke(dp(1), 0x550099ff.toInt()) // Subtle MX blue stroke
            }
            background = cardBg
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                elevation = dpF(12f)
            }
        }

        // Header row with step badge and close button
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val badge = TextView(context).apply {
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(UiUtils.COLOR_MX_BLUE)
            setPadding(dp(8), dp(3), dp(8), dp(3))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpF(8f)
                setColor(0x330099ff.toInt())
            }
        }
        stepBadgeView = badge
        headerRow.addView(badge, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val spacer = View(context)
        headerRow.addView(spacer, LinearLayout.LayoutParams(0, 0, 1f))

        val closeBtn = TextView(context).apply {
            text = "✕"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xaaeff0f5.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(2), dp(8), dp(2))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpF(10f)
                setColor(0x22ffffff.toInt())
            }
            setOnClickListener { dismissTour() }
        }
        headerRow.addView(closeBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        card.addView(headerRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // Title
        val titleView = TextView(context).apply {
            textSize = 15.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(0, dp(6), 0, dp(2))
        }
        titleTextView = titleView
        card.addView(titleView)

        // Target name
        val targetLabel = TextView(context).apply {
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xff90caf9.toInt())
            setPadding(0, 0, 0, dp(6))
        }
        targetLabelView = targetLabel
        card.addView(targetLabel)

        // Description
        val descView = TextView(context).apply {
            textSize = 13.5f
            setTextColor(0xffdedede.toInt())
            setLineSpacing(dpF(2f), 1.15f)
            setPadding(0, 0, 0, dp(12))
        }
        descTextView = descView
        card.addView(descView)

        // Buttons row
        val buttonsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }

        fun styleBtn(b: Button, bgCol: Int, textCol: Int) = b.apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpF(8f)
                setColor(bgCol)
            }
            setTextColor(textCol)
            typeface = Typeface.DEFAULT_BOLD
            textSize = 12f
            isAllCaps = false
            setPadding(dp(14), 0, dp(14), 0)
        }

        val prevBtn = Button(context).apply {
            text = "‹ Previous"
            styleBtn(this, 0x2affffff.toInt(), Color.WHITE)
            setOnClickListener {
                if (currentStepIndex > 0) {
                    goToStep(currentStepIndex - 1)
                }
            }
        }
        prevButton = prevBtn
        buttonsRow.addView(prevBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)).apply { rightMargin = dp(8) })

        val nextBtn = Button(context).apply {
            text = "Next ›"
            styleBtn(this, UiUtils.COLOR_MX_BLUE, Color.WHITE)
            setOnClickListener {
                if (currentStepIndex < steps.size - 1) {
                    goToStep(currentStepIndex + 1)
                } else {
                    dismissTour()
                }
            }
        }
        nextButton = nextBtn
        buttonsRow.addView(nextBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)))

        card.addView(buttonsRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        return card
    }

    private fun goToStep(index: Int) {
        currentStepIndex = index.coerceIn(0, steps.size - 1)
        val step = steps[currentStepIndex]
        applyHighlight(step)

        val card = cardContainer ?: return
        card.animate().alpha(0f).setDuration(100).withEndAction {
            updateCardContent(step)
            updateCardPosition(step)
            card.animate().alpha(1f).setDuration(150).start()
        }.start()
    }

    private fun showCurrentStep(animate: Boolean) {
        val step = steps[currentStepIndex]
        applyHighlight(step)
        updateCardContent(step)
        updateCardPosition(step)
        if (animate) {
            cardContainer?.alpha = 0f
            cardContainer?.animate()?.alpha(1f)?.setDuration(180)?.start()
        } else {
            cardContainer?.alpha = 1f
        }
    }

    private fun updateCardContent(step: TourStep) {
        stepBadgeView?.text = "STEP ${currentStepIndex + 1} OF ${steps.size}"
        titleTextView?.text = step.title
        targetLabelView?.text = "🎯 ${step.targetName}"
        descTextView?.text = step.description

        val hasPrev = currentStepIndex > 0
        prevButton?.isEnabled = hasPrev
        prevButton?.alpha = if (hasPrev) 1.0f else 0.35f

        val isLast = currentStepIndex == steps.size - 1
        nextButton?.text = if (isLast) "Finish ✓" else "Next ›"
    }

    private fun determinePlacement(step: TourStep): CardPlacement {
        val dp = { v: Int -> UiUtils.dp(context, v) }
        val rootW = views.root.width.takeIf { it > 0 } ?: context.resources.displayMetrics.widthPixels
        val rootH = views.root.height.takeIf { it > 0 } ?: context.resources.displayMetrics.heightPixels
        val isLandscape = rootW > rootH

        return when (step.zoneType) {
            TourHighlightOverlay.HighlightType.ZONE_LEFT_BRIGHTNESS -> {
                // If wide enough (landscape), place card on the right side away from left highlight
                if (isLandscape && rootW >= dp(500)) CardPlacement.RIGHT else CardPlacement.BOTTOM
            }
            TourHighlightOverlay.HighlightType.ZONE_RIGHT_VOLUME -> {
                // If wide enough (landscape), place card on the left side away from right highlight
                if (isLandscape && rootW >= dp(500)) CardPlacement.LEFT else CardPlacement.BOTTOM
            }
            TourHighlightOverlay.HighlightType.ZONE_TOP_LEFT_CORNER,
            TourHighlightOverlay.HighlightType.ZONE_TOP_RIGHT_CORNER,
            TourHighlightOverlay.HighlightType.ZONE_CENTER_BOOST -> {
                CardPlacement.BOTTOM
            }
            TourHighlightOverlay.HighlightType.NONE,
            TourHighlightOverlay.HighlightType.VIEW_TARGET -> {
                val targetView = step.targetViewProvider?.invoke(views)
                if (targetView != null && targetView.isAttachedToWindow && targetView.height > 0) {
                    val loc = IntArray(2)
                    targetView.getLocationInWindow(loc)
                    val rootLoc = IntArray(2)
                    views.root.getLocationInWindow(rootLoc)
                    val targetCenterY = (loc[1] - rootLoc[1]) + (targetView.height / 2f)
                    if (targetCenterY < rootH * 0.5f) CardPlacement.BOTTOM else CardPlacement.TOP
                } else {
                    // Fallback based on known step targets
                    if (currentStepIndex <= 6) CardPlacement.BOTTOM else CardPlacement.TOP
                }
            }
        }
    }

    private fun updateCardPosition(step: TourStep) {
        val card = cardContainer ?: return
        val dp = { v: Int -> UiUtils.dp(context, v) }
        val rootW = views.root.width.takeIf { it > 0 } ?: context.resources.displayMetrics.widthPixels

        val placement = determinePlacement(step)
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        when (placement) {
            CardPlacement.TOP -> {
                params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                params.width = minOf(dp(480), (rootW * 0.86f).toInt())
                params.topMargin = dp(20)
                params.bottomMargin = 0
                params.leftMargin = 0
                params.rightMargin = 0
            }
            CardPlacement.BOTTOM -> {
                params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                params.width = minOf(dp(480), (rootW * 0.86f).toInt())
                params.bottomMargin = dp(18)
                params.topMargin = 0
                params.leftMargin = 0
                params.rightMargin = 0
            }
            CardPlacement.RIGHT -> {
                params.gravity = Gravity.END or Gravity.CENTER_VERTICAL
                params.width = minOf(dp(360), (rootW * 0.46f).toInt())
                params.rightMargin = dp(24)
                params.leftMargin = 0
                params.topMargin = 0
                params.bottomMargin = 0
            }
            CardPlacement.LEFT -> {
                params.gravity = Gravity.START or Gravity.CENTER_VERTICAL
                params.width = minOf(dp(360), (rootW * 0.46f).toInt())
                params.leftMargin = dp(24)
                params.rightMargin = 0
                params.topMargin = 0
                params.bottomMargin = 0
            }
        }

        card.layoutParams = params
    }

    private fun applyHighlight(step: TourStep) {
        views.overlayContainer.visibility = View.VISIBLE
        controlsLock?.cancelHideControls()
        val overlay = highlightOverlay ?: return
        if (step.zoneType != TourHighlightOverlay.HighlightType.NONE) {
            overlay.highlightZone(step.zoneType, step.zoneLabel)
        } else {
            val target = step.targetViewProvider?.invoke(views)
            if (target != null) {
                target.visibility = View.VISIBLE
                target.post {
                    if (target.isAttachedToWindow && target.width > 0 && target.height > 0) {
                        overlay.highlightTargetView(target)
                    } else {
                        target.post {
                            if (target.isAttachedToWindow) {
                                overlay.highlightTargetView(target)
                            }
                        }
                    }
                }
            } else {
                overlay.clearHighlight()
            }
        }
    }
}
