/**
 * Role: Subtitle appearance and vertical positioning dialog helper.
 * Responsibility: Provides fine-grained font size slider, vertical position offset slider, steppers, and presets.
 * Details: Applies changes live to SubtitleView so users see immediate feedback on video playback.
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
import com.example.mxoffline.util.UiUtils
import kotlin.math.abs

object SubtitleStyleDialogHelper {

    fun show(
        context: Context,
        currentFontSizeSp: Float,
        currentBottomPaddingFraction: Float,
        onSubtitleStyleChanged: (fontSizeSp: Float, bottomPaddingFraction: Float) -> Unit,
        onResetToDefaults: () -> Unit
    ) {
        val dp = { v: Int -> UiUtils.dp(context, v) }
        var activeFontSize = currentFontSizeSp.coerceIn(10f, 50f)
        var activePaddingFraction = currentBottomPaddingFraction.coerceIn(0.0f, 0.80f)

        val dialogView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(14), dp(20), dp(12))
        }

        // --- Live Preview Box ---
        val previewCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = UiUtils.rounded(0xff181a22.toInt(), 12, context)
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val previewLabel = TextView(context).apply {
            text = "LIVE PREVIEW"
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xff8899aa.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(6))
        }
        val previewText = TextView(context).apply {
            text = "Sample Subtitle Text"
            textSize = activeFontSize
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setShadowLayer(4f, 0f, 0f, Color.BLACK)
        }
        previewCard.addView(previewLabel)
        previewCard.addView(previewText)
        dialogView.addView(previewCard)

        // Function to update preview and player live
        fun notifyChange() {
            previewText.textSize = activeFontSize
            onSubtitleStyleChanged(activeFontSize, activePaddingFraction)
        }

        // --- Section 1: Font Size ---
        val minFontSize = 10f
        val maxFontSize = 50f
        val sizeSteps = (maxFontSize - minFontSize).toInt() // 40 steps, 1sp each

        val sizeHeaderRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(14), 0, dp(4))
        }
        val sizeTitle = TextView(context).apply {
            text = "Text Size"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        val sizeValueText = TextView(context).apply {
            text = "${activeFontSize.toInt()} sp"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(UiUtils.COLOR_MX_BLUE)
        }
        sizeHeaderRow.addView(sizeTitle)
        sizeHeaderRow.addView(sizeValueText)
        dialogView.addView(sizeHeaderRow)

        val sizeSliderRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val sizeSeekBar = SeekBar(context).apply {
            max = sizeSteps
            progress = (activeFontSize - minFontSize).toInt().coerceIn(0, sizeSteps)
            progressTintList = android.content.res.ColorStateList.valueOf(UiUtils.COLOR_MX_BLUE)
            thumbTintList = android.content.res.ColorStateList.valueOf(UiUtils.COLOR_MX_BLUE)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
                leftMargin = dp(6)
                rightMargin = dp(6)
            }
        }

        fun applyFontSize(newSize: Float) {
            activeFontSize = newSize.coerceIn(minFontSize, maxFontSize)
            sizeValueText.text = "${activeFontSize.toInt()} sp"
            sizeSeekBar.progress = (activeFontSize - minFontSize).toInt()
            notifyChange()
        }

        val minusSizeBtn = TextView(context).apply {
            text = "−"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = UiUtils.rounded(0xff2b2f3a.toInt(), 8, context)
            setPadding(dp(12), dp(4), dp(12), dp(4))
            setOnClickListener { applyFontSize(activeFontSize - 1f) }
        }

        val plusSizeBtn = TextView(context).apply {
            text = "+"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = UiUtils.rounded(0xff2b2f3a.toInt(), 8, context)
            setPadding(dp(12), dp(4), dp(12), dp(4))
            setOnClickListener { applyFontSize(activeFontSize + 1f) }
        }

        sizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val s = minFontSize + progress
                    activeFontSize = s
                    sizeValueText.text = "${activeFontSize.toInt()} sp"
                    notifyChange()
                }
            }
            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })

        sizeSliderRow.addView(minusSizeBtn)
        sizeSliderRow.addView(sizeSeekBar)
        sizeSliderRow.addView(plusSizeBtn)
        dialogView.addView(sizeSliderRow)

        // Size presets
        val sizePresetsScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, dp(8), 0, dp(6))
        }
        val sizePresetsRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val sizePresets = listOf(12f, 14f, 16f, 18f, 20f, 24f, 28f, 34f, 42f)
        for (preset in sizePresets) {
            val chip = TextView(context).apply {
                val isDefault = preset == 18f
                text = "${preset.toInt()}sp${if (isDefault) " ★" else ""}"
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = UiUtils.rounded(
                    if (abs(activeFontSize - preset) < 0.5f) UiUtils.COLOR_MX_BLUE else 0xff252934.toInt(),
                    8, context
                )
                setPadding(dp(10), dp(6), dp(10), dp(6))
                setOnClickListener { applyFontSize(preset) }
            }
            sizePresetsRow.addView(chip, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(6) })
        }
        sizePresetsScroll.addView(sizePresetsRow)
        dialogView.addView(sizePresetsScroll)

        // --- Section 2: Vertical Position (Move Up / Down) ---
        val positionHeaderRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(14), 0, dp(4))
        }
        val positionTitle = TextView(context).apply {
            text = "Vertical Position"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }

        fun formatPositionLabel(fraction: Float): String {
            val pct = (fraction * 100).toInt()
            val desc = when {
                pct <= 10 -> "Bottom"
                pct <= 25 -> "Low"
                pct <= 50 -> "Middle"
                else -> "Top"
            }
            return "$desc ($pct%)"
        }

        val positionValueText = TextView(context).apply {
            text = formatPositionLabel(activePaddingFraction)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(UiUtils.COLOR_MX_BLUE)
        }
        positionHeaderRow.addView(positionTitle)
        positionHeaderRow.addView(positionValueText)
        dialogView.addView(positionHeaderRow)

        val positionSliderRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val positionSeekBar = SeekBar(context).apply {
            max = 80 // 0% to 80% bottom padding
            progress = (activePaddingFraction * 100).toInt().coerceIn(0, 80)
            progressTintList = android.content.res.ColorStateList.valueOf(UiUtils.COLOR_MX_BLUE)
            thumbTintList = android.content.res.ColorStateList.valueOf(UiUtils.COLOR_MX_BLUE)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
                leftMargin = dp(6)
                rightMargin = dp(6)
            }
        }

        fun applyPaddingFraction(newFraction: Float) {
            activePaddingFraction = (kotlin.math.round(newFraction.coerceIn(0.0f, 0.80f) * 100f) / 100f)
            positionValueText.text = formatPositionLabel(activePaddingFraction)
            positionSeekBar.progress = (activePaddingFraction * 100).toInt()
            notifyChange()
        }

        val downBtn = TextView(context).apply {
            text = "▼ Down"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = UiUtils.rounded(0xff2b2f3a.toInt(), 8, context)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener { applyPaddingFraction(activePaddingFraction - 0.02f) }
        }

        val upBtn = TextView(context).apply {
            text = "▲ Up"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = UiUtils.rounded(0xff2b2f3a.toInt(), 8, context)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener { applyPaddingFraction(activePaddingFraction + 0.02f) }
        }

        positionSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    activePaddingFraction = progress / 100f
                    positionValueText.text = formatPositionLabel(activePaddingFraction)
                    notifyChange()
                }
            }
            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })

        positionSliderRow.addView(downBtn)
        positionSliderRow.addView(positionSeekBar)
        positionSliderRow.addView(upBtn)
        dialogView.addView(positionSliderRow)

        // Position presets
        val positionPresetsScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, dp(8), 0, dp(6))
        }
        val positionPresetsRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val positionPresets = listOf(
            0.02f to "Bottom (2%)",
            0.08f to "Standard (8%) ★",
            0.18f to "Low (18%)",
            0.32f to "Mid-Low (32%)",
            0.50f to "Center (50%)",
            0.70f to "Top (70%)"
        )
        for ((presetVal, label) in positionPresets) {
            val chip = TextView(context).apply {
                text = label
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = UiUtils.rounded(
                    if (abs(activePaddingFraction - presetVal) < 0.02f) UiUtils.COLOR_MX_BLUE else 0xff252934.toInt(),
                    8, context
                )
                setPadding(dp(10), dp(6), dp(10), dp(6))
                setOnClickListener { applyPaddingFraction(presetVal) }
            }
            positionPresetsRow.addView(chip, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(6) })
        }
        positionPresetsScroll.addView(positionPresetsRow)
        dialogView.addView(positionPresetsScroll)

        // --- Action Buttons ---
        val actionsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
        }
        val resetBtn = TextView(context).apply {
            text = "Reset to Default (18sp, 8%)"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(UiUtils.COLOR_MX_BLUE)
            background = UiUtils.rounded(0xff1f232d.toInt(), 10, context)
            setPadding(dp(16), dp(8), dp(16), dp(8))
            setOnClickListener {
                applyFontSize(18f)
                applyPaddingFraction(0.08f)
                onResetToDefaults()
            }
        }
        actionsRow.addView(resetBtn)
        dialogView.addView(actionsRow)

        AlertDialog.Builder(context)
            .setTitle("Subtitle Size & Position")
            .setView(dialogView)
            .setPositiveButton("Done", null)
            .show()
    }
}
