/**
 * Role: Interactive tour and functionality tutorial coordinator.
 * Responsibility: Guides users through player gestures, tray buttons, and playback controls step by step.
 * Details: Displays an interactive walkthrough dialog with highlight badges, pulsing zone illumination, and Previous/Next navigation.
 */
package com.example.mxoffline.player.dialog

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
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
    private val views: PlayerUiViews
) {

    data class TourStep(
        val title: String,
        val targetName: String,
        val description: String,
        val zoneType: TourHighlightOverlay.HighlightType = TourHighlightOverlay.HighlightType.NONE,
        val zoneLabel: String = "",
        val targetViewProvider: ((PlayerUiViews) -> View?)? = null
    )

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
    private var currentDialog: AlertDialog? = null

    fun showTour(startIndex: Int = 0) {
        currentStepIndex = startIndex.coerceIn(0, steps.size - 1)
        attachOverlay()
        renderDialog()
    }

    private fun attachOverlay() {
        if (highlightOverlay == null) {
            val overlay = TourHighlightOverlay(context)
            views.root.addView(overlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            highlightOverlay = overlay
        }
    }

    private fun detachOverlay() {
        highlightOverlay?.let {
            views.root.removeView(it)
        }
        highlightOverlay = null
    }

    private fun renderDialog() {
        val dp = { v: Int -> UiUtils.dp(context, v) }
        val step = steps[currentStepIndex]

        applyHighlight(step)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
            setBackgroundColor(0xff1e222e.toInt())
        }

        val stepBadge = TextView(context).apply {
            text = "STEP ${currentStepIndex + 1} OF ${steps.size}"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xffffc400.toInt())
            setPadding(0, 0, 0, dp(4))
        }
        root.addView(stepBadge)

        val targetLabel = TextView(context).apply {
            text = "🎯 ${step.targetName}"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xff90caf9.toInt())
            setPadding(0, 0, 0, dp(8))
        }
        root.addView(targetLabel)

        val desc = TextView(context).apply {
            text = step.description
            textSize = 14f
            setTextColor(0xffe0e0e0.toInt())
            setLineSpacing(dp(2).toFloat(), 1.15f)
            setPadding(0, 0, 0, dp(16))
        }
        root.addView(desc)

        val buttonsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }

        fun styleBtn(b: Button, bgCol: Int, textCol: Int) = b.apply {
            setBackgroundColor(bgCol)
            setTextColor(textCol)
            typeface = Typeface.DEFAULT_BOLD
            textSize = 12f
            isAllCaps = false
            setPadding(dp(12), dp(6), dp(12), dp(6))
        }

        val prevBtn = Button(context).apply {
            text = "‹ Previous"
            isEnabled = currentStepIndex > 0
            alpha = if (isEnabled) 1.0f else 0.4f
            styleBtn(this, 0x33ffffff.toInt(), Color.WHITE)
            setOnClickListener {
                if (currentStepIndex > 0) {
                    currentStepIndex--
                    dismissCurrentDialog()
                    renderDialog()
                }
            }
        }
        buttonsRow.addView(prevBtn, LinearLayout.LayoutParams(-2, dp(38)).apply { rightMargin = dp(8) })

        val nextBtn = Button(context).apply {
            text = if (currentStepIndex < steps.size - 1) "Next ›" else "Finish ✓"
            styleBtn(this, 0xffffc400.toInt(), Color.BLACK)
            setOnClickListener {
                if (currentStepIndex < steps.size - 1) {
                    currentStepIndex++
                    dismissCurrentDialog()
                    renderDialog()
                } else {
                    dismissCurrentDialog()
                    detachOverlay()
                }
            }
        }
        buttonsRow.addView(nextBtn, LinearLayout.LayoutParams(-2, dp(38)))

        root.addView(buttonsRow)

        val dialog = AlertDialog.Builder(context)
            .setTitle(step.title)
            .setView(root)
            .setOnDismissListener {
                detachOverlay()
            }
            .create()

        currentDialog = dialog
        dialog.show()
    }

    private fun dismissCurrentDialog() {
        currentDialog?.setOnDismissListener(null)
        currentDialog?.dismiss()
        currentDialog = null
    }

    private fun applyHighlight(step: TourStep) {
        val overlay = highlightOverlay ?: return
        if (step.zoneType != TourHighlightOverlay.HighlightType.NONE) {
            overlay.highlightZone(step.zoneType, step.zoneLabel)
        } else {
            val target = step.targetViewProvider?.invoke(views)
            if (target != null) {
                target.post {
                    overlay.highlightTargetView(target)
                }
            } else {
                overlay.clearHighlight()
            }
        }
    }
}
