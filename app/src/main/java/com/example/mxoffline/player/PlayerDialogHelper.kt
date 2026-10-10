/**
 * Role: Audio tracks, subtitles, sleep timer, and video info dialog coordinator.
 * Responsibility: Displays modal configuration dialogs for media playback tracks and timers.
 * Details: Applies track selection overrides and font size scaling dynamically to ExoPlayer.
 */
package com.example.mxoffline.player

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.ExoPlayer
import com.example.mxoffline.player.dialog.SpeedDialogHelper
import com.example.mxoffline.util.TimeFormatter
import com.example.mxoffline.util.UiUtils

class PlayerDialogHelper(
    private val context: Context,
    private val player: ExoPlayer
) {
    fun showSpeedDialog(
        speedButton: TextView? = null,
        onSpeedChanged: (Float) -> Unit
    ) {
        SpeedDialogHelper.show(context, player, speedButton, onSpeedChanged)
    }

    fun showAudioTrackDialog(
        isMuted: Boolean,
        onMuteToggled: () -> Unit
    ) {
        val tracks = player.currentTracks
        val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }

        val items = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        items.add(if (isMuted) "Unmute Audio" else "Mute Audio")
        actions.add { onMuteToggled() }

        for (group in audioGroups) {
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                val lang = format.language?.uppercase() ?: "Unknown"
                val label = format.label ?: "Track ${items.size}"
                val isSelected = group.isTrackSelected(i)
                val indicator = if (isSelected && !isMuted) "✓ " else "   "

                items.add("$indicator$label ($lang)")
                actions.add {
                    val override = TrackSelectionOverride(group.mediaTrackGroup, listOf(i))
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setOverrideForType(override)
                        .build()
                }
            }
        }

        AlertDialog.Builder(context)
            .setTitle("Audio Tracks")
            .setItems(items.toTypedArray()) { _, which -> actions[which].invoke() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun showSubtitleDialog(
        currentFontSizeSp: Float,
        onExternalSubtitleClicked: () -> Unit,
        onFontSizeChanged: (Float) -> Unit
    ) {
        val tracks = player.currentTracks
        val textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }

        val items = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        items.add("Load External Subtitle File (.srt, .vtt)")
        actions.add { onExternalSubtitleClicked() }

        items.add("Disable Subtitles (Off)")
        actions.add {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        }

        for (group in textGroups) {
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                val lang = format.language?.uppercase() ?: "Unknown"
                val label = format.label ?: "Subtitle ${items.size - 1}"
                val isSelected = group.isTrackSelected(i)
                val indicator = if (isSelected) "✓ " else "   "

                items.add("$indicator$label ($lang)")
                actions.add {
                    val override = TrackSelectionOverride(group.mediaTrackGroup, listOf(i))
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setOverrideForType(override)
                        .build()
                }
            }
        }

        val fontSizes = listOf(14f, 16f, 18f, 20f, 24f, 28f)
        for (fs in fontSizes) {
            val isCurrent = currentFontSizeSp == fs
            items.add("${if (isCurrent) "✓ " else "   "}Subtitle Size: ${fs.toInt()}sp")
            actions.add { onFontSizeChanged(fs) }
        }

        AlertDialog.Builder(context)
            .setTitle("Subtitles & Captions")
            .setItems(items.toTypedArray()) { _, which -> actions[which].invoke() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun showSleepTimerDialog(onTimerSet: (Int) -> Unit) {
        val options = arrayOf("Turn Off Timer", "10 minutes", "15 minutes", "30 minutes", "45 minutes", "60 minutes", "At end of video")
        val minutes = arrayOf(0, 10, 15, 30, 45, 60, -1)

        AlertDialog.Builder(context)
            .setTitle("Sleep Timer")
            .setItems(options) { _, which -> onTimerSet(minutes[which]) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun showVideoInfoDialog(title: String, isSoftwareDecoder: Boolean) {
        val format = player.videoFormat
        val res = if (format != null && format.width > 0) "${format.width}x${format.height}" else "Unknown"
        val codec = format?.sampleMimeType ?: "Unknown"
        val dur = TimeFormatter.formatDuration(player.duration.coerceAtLeast(0))
        val decoder = if (isSoftwareDecoder) "Software (SW)" else "Hardware (HW)"

        AlertDialog.Builder(context)
            .setTitle("Video Details")
            .setMessage("Name: $title\nDuration: $dur\nResolution: $res\nCodec: $codec\nDecoder: $decoder")
            .setPositiveButton("Close", null)
            .show()
    }
}
