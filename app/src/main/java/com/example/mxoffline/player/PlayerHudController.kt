package com.example.mxoffline.player

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Handler
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.example.mxoffline.util.TimeFormatter
import java.util.Locale
import kotlin.math.abs

class PlayerHudController(
    private val handler: Handler,
    private val seekHud: LinearLayout,
    private val seekIcon: TextView,
    private val seekTimeText: TextView,
    private val seekDeltaText: TextView,
    private val seekProgressBar: ProgressBar,
    private val brightnessHud: LinearLayout,
    private val brightnessText: TextView,
    private val brightnessBar: ProgressBar,
    private val volumeHud: LinearLayout,
    private val volumeIcon: TextView,
    private val volumeText: TextView,
    private val volumeBar: ProgressBar,
    private val centerSpeedHud: TextView,
    private val quickFeedbackHud: TextView
) {
    private val hideSeekRunnable = Runnable { seekHud.visibility = View.GONE }
    private val hideBrightnessRunnable = Runnable { brightnessHud.visibility = View.GONE }
    private val hideVolumeRunnable = Runnable { volumeHud.visibility = View.GONE }

    fun showSeek(targetMs: Long, deltaMs: Long, durationMs: Long) {
        handler.removeCallbacks(hideSeekRunnable)
        seekIcon.text = if (deltaMs >= 0) "⏩" else "⏪"
        seekTimeText.text = TimeFormatter.formatTime(targetMs)
        val prefix = if (deltaMs >= 0) "+" else "-"
        seekDeltaText.text = "[$prefix${TimeFormatter.formatTime(abs(deltaMs))}]"
        seekProgressBar.progress = if (durationMs > 0) ((targetMs * 1000) / durationMs).toInt() else 0
        seekHud.visibility = View.VISIBLE
    }

    fun hideSeek(delayMs: Long = 300) {
        handler.removeCallbacks(hideSeekRunnable)
        handler.postDelayed(hideSeekRunnable, delayMs)
    }

    fun showBrightness(percent: Int) {
        handler.removeCallbacks(hideBrightnessRunnable)
        brightnessText.text = "$percent%"
        brightnessBar.progress = percent
        brightnessHud.visibility = View.VISIBLE
    }

    fun hideBrightness(delayMs: Long = 600) {
        handler.removeCallbacks(hideBrightnessRunnable)
        handler.postDelayed(hideBrightnessRunnable, delayMs)
    }

    fun showVolume(percent: Int, isBoost: Boolean) {
        handler.removeCallbacks(hideVolumeRunnable)
        if (isBoost) {
            volumeIcon.text = "🚀"
            volumeText.text = "$percent% BOOST"
            volumeText.setTextColor(0xffffc400.toInt())
            volumeBar.progress = percent
            volumeBar.progressTintList = ColorStateList.valueOf(0xffff5500.toInt())
        } else {
            volumeIcon.text = if (percent == 0) "🔇" else "🔊"
            volumeText.text = "$percent%"
            volumeText.setTextColor(Color.WHITE)
            volumeBar.progress = percent
            volumeBar.progressTintList = ColorStateList.valueOf(0xffffc400.toInt())
        }
        volumeHud.visibility = View.VISIBLE
    }

    fun hideVolume(delayMs: Long = 600) {
        handler.removeCallbacks(hideVolumeRunnable)
        handler.postDelayed(hideVolumeRunnable, delayMs)
    }

    fun showSpeed(speed: Float) {
        centerSpeedHud.text = "▶▶ ${String.format(Locale.US, "%.1f", speed)}× Speed"
        centerSpeedHud.visibility = View.VISIBLE
    }

    fun hideSpeed() {
        centerSpeedHud.visibility = View.GONE
    }

    fun showQuickFeedback(msg: String) {
        quickFeedbackHud.text = msg
        quickFeedbackHud.alpha = 1f
        quickFeedbackHud.visibility = View.VISIBLE
        quickFeedbackHud.animate().alpha(0f).setDuration(600).withEndAction {
            quickFeedbackHud.visibility = View.GONE
        }.start()
    }
}
