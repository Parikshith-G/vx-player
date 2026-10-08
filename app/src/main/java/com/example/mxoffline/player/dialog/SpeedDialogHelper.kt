/**
 * Role: Playback speed customization slider and presets dialog.
 * Responsibility: Displays fine-grained speed slider (0.25x - 8.0x) and preset chips.
 * Details: Applies playback speed changes immediately to ExoPlayer and notifies listener.
 */
package com.example.mxoffline.player.dialog

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import com.example.mxoffline.util.TimeFormatter
import com.example.mxoffline.util.UiUtils
import kotlin.math.abs

object SpeedDialogHelper {

    fun show(
        context: Context,
        player: ExoPlayer,
        speedButton: TextView? = null,
        onSpeedChanged: (Float) -> Unit
    ) {
        val dp = { v: Int -> UiUtils.dp(context, v) }
        val dialogView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(12))
        }

        val speedValueText = TextView(context).apply {
            val cur = player.playbackParameters.speed
            text = "${TimeFormatter.formatSpeed(cur)} Speed"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(0xffffc400.toInt())
            setPadding(0, 0, 0, dp(14))
        }
        dialogView.addView(speedValueText)

        val minSpeed = 0.25f
        val maxSpeed = 8.0f
        val step = 0.05f
        val totalSteps = ((maxSpeed - minSpeed) / step).toInt()

        fun speedToProgress(s: Float): Int {
            return (((s.coerceIn(minSpeed, maxSpeed) - minSpeed) / step) + 0.5f).toInt().coerceIn(0, totalSteps)
        }

        fun progressToSpeed(p: Int): Float {
            val s = minSpeed + p * step
            return (kotlin.math.round(s * 100f) / 100f).coerceIn(minSpeed, maxSpeed)
        }

        val sliderRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val speedSeekBar = SeekBar(context).apply {
            max = totalSteps
            progress = speedToProgress(player.playbackParameters.speed)
            progressTintList = android.content.res.ColorStateList.valueOf(0xffffc400.toInt())
            thumbTintList = android.content.res.ColorStateList.valueOf(0xffffc400.toInt())
        }

        fun applySpeed(s: Float) {
            val clamped = (kotlin.math.round(s * 100f) / 100f).coerceIn(minSpeed, maxSpeed)
            player.playbackParameters = PlaybackParameters(clamped, 1.0f)
            val label = TimeFormatter.formatSpeed(clamped)
            speedValueText.text = "$label Speed"
            speedButton?.text = label
            speedSeekBar.progress = speedToProgress(clamped)
            onSpeedChanged(clamped)
        }

        val minusBtn = TextView(context).apply {
            text = "−0.1"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = UiUtils.rounded(0xff2b2f3a.toInt(), 10, context)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setOnClickListener { applySpeed(player.playbackParameters.speed - 0.1f) }
        }

        val plusBtn = TextView(context).apply {
            text = "+0.1"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = UiUtils.rounded(0xff2b2f3a.toInt(), 10, context)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setOnClickListener { applySpeed(player.playbackParameters.speed + 0.1f) }
        }

        sliderRow.addView(minusBtn)
        sliderRow.addView(speedSeekBar, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
            leftMargin = dp(6)
            rightMargin = dp(6)
        })
        sliderRow.addView(plusBtn)
        dialogView.addView(sliderRow)

        speedSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val s = progressToSpeed(progress)
                    player.playbackParameters = PlaybackParameters(s, 1.0f)
                    val label = TimeFormatter.formatSpeed(s)
                    speedValueText.text = "$label Speed"
                    speedButton?.text = label
                    onSpeedChanged(s)
                }
            }
            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })

        val presetsScrollView = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, dp(14), 0, dp(6))
        }
        val presetsRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val presetValues = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 2.5f, 3.0f, 4.0f, 5.0f, 6.0f, 8.0f)
        for (preset in presetValues) {
            val chip = TextView(context).apply {
                val label = if (preset % 1f == 0f) "${preset.toInt()}×" else "${preset}×"
                text = label
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(if (abs(player.playbackParameters.speed - preset) < 0.05f) 0xff101114.toInt() else Color.WHITE)
                background = UiUtils.rounded(
                    if (abs(player.playbackParameters.speed - preset) < 0.05f) 0xffffc400.toInt() else 0xff252934.toInt(),
                    10, context
                )
                setPadding(dp(12), dp(8), dp(12), dp(8))
                setOnClickListener { applySpeed(preset) }
            }
            presetsRow.addView(chip, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(8) })
        }
        presetsScrollView.addView(presetsRow)
        dialogView.addView(presetsScrollView)

        val resetRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        }
        val resetBtn = TextView(context).apply {
            text = "Reset to 1.0×"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(0xffffc400.toInt())
            background = UiUtils.rounded(0xff1f232d.toInt(), 10, context)
            setPadding(dp(16), dp(8), dp(16), dp(8))
            setOnClickListener { applySpeed(1.0f) }
        }
        resetRow.addView(resetBtn)
        dialogView.addView(resetRow)

        AlertDialog.Builder(context)
            .setTitle("Playback Speed")
            .setView(dialogView)
            .setPositiveButton("Done", null)
            .show()
    }
}
