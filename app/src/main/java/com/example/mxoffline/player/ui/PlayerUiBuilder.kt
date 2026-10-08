/**
 * Role: Programmatic layout builder and view hierarchy constructor for PlayerActivity.
 * Responsibility: Assembles player surface, persistent top status headers, HUD overlays, and MX-style action bar.
 * Details: Ensures topTimeStatusView remains at top-left and bottom bar features exact MX Player action buttons.
 */
package com.example.mxoffline.player.ui

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.media3.ui.PlayerView
import com.example.mxoffline.util.UiUtils

class PlayerUiViews(
    val root: FrameLayout,
    val playerView: PlayerView,
    val overlayContainer: FrameLayout,
    val persistentStatusHeader: LinearLayout,
    val topTimeStatusView: TextView,
    val batteryText: TextView,
    val clockText: TextView,
    val lockFloatingBtn: TextView,
    val resumeBanner: LinearLayout,
    val resumeText: TextView,
    val restartBtn: TextView,
    val titleView: TextView,
    val backButton: TextView,
    val menuButton: TextView,
    val quickButtonsLayout: LinearLayout,
    val timeView: TextView,
    val remainingTimeView: TextView,
    val seekBar: SeekBar,
    val lockBtn: TextView,
    val seekBack5Btn: TextView,
    val prevBtn: TextView,
    val playPauseBtn: TextView,
    val nextBtn: TextView,
    val markDoneBtn: TextView,
    val seekFwd5Btn: TextView,
    val pipBtn: TextView,
    val seekHud: LinearLayout,
    val seekIcon: TextView,
    val seekTimeText: TextView,
    val seekDeltaText: TextView,
    val seekProgressBar: ProgressBar,
    val brightnessHud: LinearLayout,
    val brightnessText: TextView,
    val brightnessBar: ProgressBar,
    val volumeHud: LinearLayout,
    val volumeIcon: TextView,
    val volumeText: TextView,
    val volumeBar: ProgressBar,
    val centerSpeedHud: TextView,
    val quickFeedbackHud: TextView
)

object PlayerUiBuilder {

    fun build(activity: Activity): PlayerUiViews {
        val dp = { v: Int -> UiUtils.dp(activity, v) }
        val root = FrameLayout(activity).apply { setBackgroundColor(Color.BLACK) }

        val playerView = PlayerView(activity).apply {
            useController = false; setBackgroundColor(Color.BLACK)
        }
        root.addView(playerView, FrameLayout.LayoutParams(-1, -1))

        // Seek HUD
        val seekHud = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(22), dp(14), dp(22), dp(14))
            background = UiUtils.rounded(0xcc101217.toInt(), 20, activity); visibility = View.GONE
        }
        val seekIcon = TextView(activity).apply { text = "⏩"; textSize = 28f; gravity = Gravity.CENTER; setTextColor(0xffffc400.toInt()) }
        val seekTimeText = TextView(activity).apply { textSize = 24f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(Color.WHITE) }
        val seekDeltaText = TextView(activity).apply { textSize = 14f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(0xffffc400.toInt()) }
        val seekProgressBar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000; progressTintList = ColorStateList.valueOf(0xffffc400.toInt())
        }
        seekHud.addView(seekIcon); seekHud.addView(seekTimeText); seekHud.addView(seekDeltaText)
        seekHud.addView(seekProgressBar, LinearLayout.LayoutParams(dp(140), dp(6)).apply { topMargin = dp(8) })
        root.addView(seekHud, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))

        // Brightness HUD
        val brightnessHud = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(12), dp(16), dp(12), dp(16))
            background = UiUtils.rounded(0xbb000000.toInt(), 16, activity); visibility = View.GONE
        }
        val brightnessText = TextView(activity).apply { text = "100%"; textSize = 13f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(Color.WHITE) }
        val brightnessBar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progress = 100; progressTintList = ColorStateList.valueOf(0xffffc400.toInt())
        }
        brightnessHud.addView(TextView(activity).apply { text = "☀"; textSize = 18f; gravity = Gravity.CENTER; setTextColor(Color.WHITE) })
        brightnessHud.addView(brightnessText)
        brightnessHud.addView(brightnessBar, LinearLayout.LayoutParams(dp(70), dp(6)))
        root.addView(brightnessHud, FrameLayout.LayoutParams(-2, -2, Gravity.START or Gravity.CENTER_VERTICAL).apply { leftMargin = dp(28) })

        // Volume HUD
        val volumeHud = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(12), dp(16), dp(12), dp(16))
            background = UiUtils.rounded(0xbb000000.toInt(), 16, activity); visibility = View.GONE
        }
        val volumeIcon = TextView(activity).apply { text = "🔊"; textSize = 18f; gravity = Gravity.CENTER; setTextColor(Color.WHITE) }
        val volumeText = TextView(activity).apply { text = "100%"; textSize = 13f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(Color.WHITE) }
        val volumeBar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 200; progress = 100; progressTintList = ColorStateList.valueOf(0xffffc400.toInt())
        }
        volumeHud.addView(volumeIcon); volumeHud.addView(volumeText)
        volumeHud.addView(volumeBar, LinearLayout.LayoutParams(dp(70), dp(6)))
        root.addView(volumeHud, FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.CENTER_VERTICAL).apply { rightMargin = dp(28) })

        val quickFeedbackHud = TextView(activity).apply {
            textSize = 20f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = UiUtils.rounded(0xbb000000.toInt(), 24, activity); setPadding(dp(22), dp(12), dp(22), dp(12)); visibility = View.GONE
        }
        root.addView(quickFeedbackHud, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))

        val centerSpeedHud = TextView(activity).apply {
            textSize = 16f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(0xffffc400.toInt())
            background = UiUtils.rounded(0xdd12151c.toInt(), 16, activity); setPadding(dp(18), dp(8), dp(18), dp(8)); visibility = View.GONE
        }
        root.addView(centerSpeedHud, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(28) })

        // Floating Unlock Button
        val lockFloatingBtn = TextView(activity).apply {
            text = "🔓"; textSize = 22f; gravity = Gravity.CENTER; includeFontPadding = false
            background = GradientDrawable().apply {
                setColor(0xee222631.toInt()); cornerRadius = dp(24).toFloat(); setStroke(dp(1), 0x55ffffff.toInt())
            }
            visibility = View.GONE
        }
        root.addView(lockFloatingBtn, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP or Gravity.START).apply {
            topMargin = dp(42); leftMargin = dp(20)
        })

        // Resume Banner
        val resumeBanner = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(10), dp(16), dp(10))
            background = UiUtils.rounded(0xee1a1d26.toInt(), 20, activity); visibility = View.GONE
        }
        val resumeText = TextView(activity).apply { text = "Resumed from 00:00"; textSize = 13f; setTextColor(0xfff0f1f3.toInt()) }
        val restartBtn = TextView(activity).apply {
            text = "Restart"; textSize = 13f; typeface = Typeface.DEFAULT_BOLD; setTextColor(0xffffc400.toInt()); setPadding(dp(14), 0, 0, 0)
        }
        resumeBanner.addView(resumeText); resumeBanner.addView(restartBtn)
        root.addView(resumeBanner, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(110) })

        // Controls Overlay
        val overlayContainer = FrameLayout(activity).apply { setBackgroundColor(Color.TRANSPARENT) }

        // Persistent Top Status Header: topTimeStatusView on left, spacer, battery & clock on right
        val persistentStatusHeader = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(20), 0, dp(20), 0)
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x55000000.toInt(), Color.TRANSPARENT))
        }
        val topTimeStatusView = TextView(activity).apply {
            text = "00:00 / 00:00"; textSize = 12f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
            setSingleLine(true); includeFontPadding = false; setShadowLayer(4f, 1f, 1f, 0xdd000000.toInt()); setPadding(dp(4), dp(2), dp(8), dp(2))
        }
        val batteryText = TextView(activity).apply {
            textSize = 12f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
            setSingleLine(true); includeFontPadding = false; setShadowLayer(4f, 1f, 1f, 0xdd000000.toInt()); setPadding(dp(4), dp(2), dp(6), dp(2))
            gravity = Gravity.CENTER_VERTICAL
        }
        val clockText = TextView(activity).apply {
            textSize = 12f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
            setSingleLine(true); includeFontPadding = false; setShadowLayer(4f, 1f, 1f, 0xdd000000.toInt()); setPadding(dp(4), dp(2), dp(4), dp(2))
            gravity = Gravity.CENTER_VERTICAL
        }
        persistentStatusHeader.addView(topTimeStatusView, LinearLayout.LayoutParams(-2, -2))
        persistentStatusHeader.addView(View(activity), LinearLayout.LayoutParams(0, dp(1), 1f))
        persistentStatusHeader.addView(batteryText, LinearLayout.LayoutParams(-2, -2))
        persistentStatusHeader.addView(clockText, LinearLayout.LayoutParams(-2, -2))
        root.addView(persistentStatusHeader, FrameLayout.LayoutParams(-1, dp(32), Gravity.TOP))

        // Top Bar
        val topBar = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xcc000000.toInt(), Color.TRANSPARENT))
            setPadding(dp(16), dp(32), dp(16), dp(14))
        }
        val topTitleRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val backButton = TextView(activity).apply { text = "‹"; textSize = 28f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(Color.WHITE) }
        val titleView = TextView(activity).apply {
            textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(dp(12), 0, dp(12), 0)
        }
        val menuButton = TextView(activity).apply { text = "☰"; textSize = 22f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(Color.WHITE) }
        topTitleRow.addView(backButton, LinearLayout.LayoutParams(dp(44), dp(44)))
        topTitleRow.addView(titleView, LinearLayout.LayoutParams(0, -2, 1f))
        topTitleRow.addView(menuButton, LinearLayout.LayoutParams(dp(44), dp(44)))
        topBar.addView(topTitleRow)

        val quickButtonsScroll = HorizontalScrollView(activity).apply { isHorizontalScrollBarEnabled = false; clipToPadding = false }
        val quickButtonsLayout = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(6), 0, 0) }
        quickButtonsScroll.addView(quickButtonsLayout, FrameLayout.LayoutParams(-2, -2))
        topBar.addView(quickButtonsScroll, LinearLayout.LayoutParams(-1, -2))
        overlayContainer.addView(topBar, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        // Bottom Bar (Exact MX Player sequence)
        val bottomBar = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(0xee000000.toInt(), 0x77000000.toInt(), Color.TRANSPARENT))
            setPadding(dp(14), dp(8), dp(14), dp(12))
        }
        val timeRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val timeView = TextView(activity).apply { text = "00:00"; textSize = 13f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE); setPadding(0, 0, dp(8), 0) }
        val seekBar = SeekBar(activity).apply {
            max = 1000; progressTintList = ColorStateList.valueOf(0xffffc400.toInt()); thumbTintList = ColorStateList.valueOf(0xffffc400.toInt())
        }
        val remainingTimeView = TextView(activity).apply { text = "00:00"; textSize = 13f; typeface = Typeface.DEFAULT_BOLD; setTextColor(0xffbbbec6.toInt()); setPadding(dp(8), 0, 0, 0) }
        timeRow.addView(timeView); timeRow.addView(seekBar, LinearLayout.LayoutParams(0, dp(40), 1f)); timeRow.addView(remainingTimeView)
        bottomBar.addView(timeRow)

        // Actions Row (Exact MX Player sequence: Lock, 5s Seek Back, Prev, Play/Pause, Next, Mark Done, 5s Seek Fwd, PiP)
        val actionsRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        fun actionBtn(text: String, sizeSp: Float = 14f) = TextView(activity).apply {
            this.text = text; textSize = sizeSp; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setTextColor(Color.WHITE); setSingleLine(true); includeFontPadding = false
        }
        val lockBtn = actionBtn("🔒")
        val seekBack5Btn = actionBtn("⟲ 5s")
        val prevBtn = actionBtn("⏮")
        val playPauseBtn = actionBtn("Ⅱ", 20f).apply { setTextColor(0xffffc400.toInt()) }
        val nextBtn = actionBtn("⏭")
        val markDoneBtn = actionBtn("✔", 20f).apply { setTextColor(0xff00e676.toInt()) }
        val seekFwd5Btn = actionBtn("5s ⟳")
        val pipBtn = actionBtn("⧉").apply { if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) visibility = View.GONE }

        val actionLp = { LinearLayout.LayoutParams(0, dp(44), 1f) }
        actionsRow.addView(lockBtn, actionLp())
        actionsRow.addView(seekBack5Btn, actionLp())
        actionsRow.addView(prevBtn, actionLp())
        actionsRow.addView(playPauseBtn, actionLp())
        actionsRow.addView(nextBtn, actionLp())
        actionsRow.addView(markDoneBtn, actionLp())
        actionsRow.addView(seekFwd5Btn, actionLp())
        actionsRow.addView(pipBtn, actionLp())
        bottomBar.addView(actionsRow)

        overlayContainer.addView(bottomBar, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        root.addView(overlayContainer, FrameLayout.LayoutParams(-1, -1))

        return PlayerUiViews(
            root, playerView, overlayContainer, persistentStatusHeader, topTimeStatusView,
            batteryText, clockText, lockFloatingBtn, resumeBanner, resumeText, restartBtn,
            titleView, backButton, menuButton, quickButtonsLayout, timeView, remainingTimeView,
            seekBar, lockBtn, seekBack5Btn, prevBtn, playPauseBtn, nextBtn, markDoneBtn, seekFwd5Btn, pipBtn,
            seekHud, seekIcon, seekTimeText, seekDeltaText, seekProgressBar,
            brightnessHud, brightnessText, brightnessBar, volumeHud, volumeIcon, volumeText, volumeBar,
            centerSpeedHud, quickFeedbackHud
        )
    }
}
