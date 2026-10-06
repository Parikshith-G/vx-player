package com.example.mxoffline.player

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.ExoPlayer
import com.example.mxoffline.util.TimeFormatter
import com.example.mxoffline.util.UiUtils
import kotlin.math.abs

class PlayerDialogHelper(
    private val context: Context,
    private val player: ExoPlayer
) {
    fun showSpeedDialog(
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

        // Horizontal Quick Presets
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

        // Reset to 1.0x
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
            setPadding(dp(14), dp(8), dp(14), dp(8))
            setOnClickListener { applySpeed(1.0f) }
        }
        resetRow.addView(resetBtn)
        dialogView.addView(resetRow)

        AlertDialog.Builder(context)
            .setTitle("Playback Speed (0.25× – 8.0×)")
            .setView(dialogView)
            .setPositiveButton("Done", null)
            .show()
    }

    fun showSubtitleDialog(
        currentFontSizeSp: Float,
        onExternalSubtitleClicked: () -> Unit,
        onFontSizeChanged: (Float) -> Unit
    ) {
        val tracks = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
        val options = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        options.add("Disable Subtitles")
        actions.add {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            Toast.makeText(context, "Subtitles disabled", Toast.LENGTH_SHORT).show()
        }

        tracks.forEachIndexed { _, group ->
            for (trackIdx in 0 until group.length) {
                val format = group.getTrackFormat(trackIdx)
                val isSelected = group.isTrackSelected(trackIdx)
                val label = (format.label ?: format.language ?: "Track ${trackIdx + 1}") + if (isSelected) " ✓" else ""
                options.add(label)
                actions.add {
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIdx))
                        .build()
                    Toast.makeText(context, "Selected subtitle: ${format.label ?: "Track"}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        options.add("＋ Open external subtitle (.srt, .vtt)...")
        actions.add { onExternalSubtitleClicked() }

        options.add("Subtitle Font Size (${currentFontSizeSp.toInt()}sp)")
        actions.add {
            val sizes = arrayOf("Small (14sp)", "Normal (18sp)", "Large (22sp)", "Huge (28sp)")
            val values = arrayOf(14f, 18f, 22f, 28f)
            AlertDialog.Builder(context)
                .setTitle("Subtitle Font Size")
                .setItems(sizes) { _, which -> onFontSizeChanged(values[which]) }
                .show()
        }

        AlertDialog.Builder(context)
            .setTitle("Subtitles")
            .setItems(options.toTypedArray()) { _, which -> actions[which].invoke() }
            .show()
    }

    fun showAudioTrackDialog(isMuted: Boolean, onToggleMute: () -> Unit) {
        val audioGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        val options = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        audioGroups.forEachIndexed { _, group ->
            for (trackIdx in 0 until group.length) {
                val format = group.getTrackFormat(trackIdx)
                val isSelected = group.isTrackSelected(trackIdx)
                val channels = if (format.channelCount > 2) "${format.channelCount}ch" else "Stereo"
                val label = "${format.label ?: format.language ?: "Track ${trackIdx + 1}"} ($channels)${if (isSelected) " ✓" else ""}"
                options.add(label)
                actions.add {
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIdx))
                        .build()
                }
            }
        }

        options.add(if (isMuted) "Unmute Audio" else "Mute Audio")
        actions.add { onToggleMute() }

        AlertDialog.Builder(context)
            .setTitle("Audio Tracks")
            .setItems(options.toTypedArray()) { _, which -> actions[which].invoke() }
            .show()
    }

    fun showPlaylistDialog(
        names: List<String>,
        currentIndex: Int,
        repeatMode: Int,
        onVideoSelected: (Int) -> Unit,
        onCycleRepeatMode: () -> Unit
    ) {
        val items = names.mapIndexed { idx, name ->
            if (idx == currentIndex) "▶  $name  (Now Playing)" else name
        }.toTypedArray()

        val repeatLabel = when (repeatMode) {
            1 -> "Repeat All"
            2 -> "Repeat One"
            else -> "Off"
        }

        AlertDialog.Builder(context)
            .setTitle("Playlist (${names.size} videos)")
            .setItems(items) { _, which ->
                if (which != currentIndex) onVideoSelected(which)
            }
            .setPositiveButton("Loop Mode: $repeatLabel") { _, _ -> onCycleRepeatMode() }
            .show()
    }

    fun showSleepTimerDialog(onTimerSet: (Int) -> Unit) {
        val timers = arrayOf("Off", "15 minutes", "30 minutes", "45 minutes", "60 minutes")
        val minutes = arrayOf(0, 15, 30, 45, 60)
        AlertDialog.Builder(context)
            .setTitle("Sleep Timer")
            .setItems(timers) { _, which -> onTimerSet(minutes[which]) }
            .show()
    }

    fun showVideoInfoDialog(fileName: String, isSoftwareDecoder: Boolean) {
        val format = player.videoFormat
        val res = if (format != null) "${format.width} × ${format.height}" else "Unknown"
        val codec = format?.sampleMimeType ?: "Standard Codec"
        val fps = if (format != null && format.frameRate > 0) "${format.frameRate.toInt()} fps" else ""
        val curDuration = TimeFormatter.formatTime(player.duration.coerceAtLeast(0))

        val info = """
            File: $fileName
            Duration: $curDuration
            Resolution: $res $fps
            Video Codec: $codec
            Decoder: ${if (isSoftwareDecoder) "Software (SW)" else "Hardware (HW)"}
        """.trimIndent()

        AlertDialog.Builder(context)
            .setTitle("Video Details")
            .setMessage(info)
            .setPositiveButton("OK", null)
            .show()
    }
}
