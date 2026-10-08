/**
 * Role: Playlist navigation, ordering, and repeat mode coordinator.
 * Responsibility: Manages video list queues, previous/next transitions, and repeat modes.
 * Details: Displays playlist selection dialog and navigates tracks seamlessly without playback errors.
 */
package com.example.mxoffline.player.playlist

import android.app.AlertDialog
import android.content.Context
import android.widget.Toast
import androidx.media3.exoplayer.ExoPlayer

class PlayerPlaylistController(
    private val context: Context,
    val uris: ArrayList<String>,
    val names: ArrayList<String>,
    initialIndex: Int,
    val sizes: ArrayList<Long> = arrayListOf(),
    private val onVideoSelected: (Int) -> Unit
) {

    var index: Int = initialIndex.coerceIn(0, (uris.size - 1).coerceAtLeast(0))

    companion object {
        const val REPEAT_OFF = 0
        const val REPEAT_ALL = 1
        const val REPEAT_ONE = 2
    }

    var repeatMode: Int = REPEAT_OFF
        private set

    fun cycleRepeatMode(): String {
        repeatMode = (repeatMode + 1) % 3
        return when (repeatMode) {
            REPEAT_ALL -> "Repeat: All"
            REPEAT_ONE -> "Repeat: One"
            else -> "Repeat: Off"
        }
    }

    fun getCurrentUri(): String? = uris.getOrNull(index)
    fun getCurrentName(): String = names.getOrNull(index) ?: "Video"
    fun getCurrentSize(): Long = sizes.getOrNull(index) ?: 0L

    fun nextVideo(player: ExoPlayer?): Boolean {
        if (player == null || uris.isEmpty()) return false
        if (index < uris.lastIndex) {
            index++
            player.seekTo(index, 0L)
            player.prepare()
            player.play()
            onVideoSelected(index)
            return true
        } else if (repeatMode == REPEAT_ALL) {
            index = 0
            player.seekTo(0, 0L)
            player.prepare()
            player.play()
            onVideoSelected(index)
            return true
        } else {
            Toast.makeText(context, "End of playlist", Toast.LENGTH_SHORT).show()
            return false
        }
    }

    fun previousVideo(player: ExoPlayer?): Boolean {
        if (player == null || uris.isEmpty()) return false
        if (player.currentPosition > 3500 && player.playbackState != androidx.media3.common.Player.STATE_IDLE && player.playerError == null) {
            player.seekTo(0L)
            player.prepare()
            player.play()
            return true
        } else if (index > 0) {
            index--
            player.seekTo(index, 0L)
            player.prepare()
            player.play()
            onVideoSelected(index)
            return true
        } else {
            player.seekTo(0L)
            player.prepare()
            player.play()
            return true
        }
    }

    fun showPlaylistDialog(player: ExoPlayer?) {
        if (names.isEmpty()) return
        val itemsWithIndicator = names.mapIndexed { idx, name ->
            if (idx == index) "▶  $name" else "     $name"
        }.toTypedArray()

        AlertDialog.Builder(context)
            .setTitle("Playlist (${index + 1}/${names.size})")
            .setItems(itemsWithIndicator) { _, which ->
                if (which in uris.indices) {
                    index = which
                    player?.seekTo(index, 0L)
                    player?.prepare()
                    player?.play()
                    onVideoSelected(index)
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    fun removeCurrent(): Pair<Int, Boolean> {
        if (uris.isEmpty() || index !in uris.indices) return index to true
        uris.removeAt(index)
        names.removeAt(index)
        if (sizes.size > index) sizes.removeAt(index)
        if (uris.isEmpty()) return 0 to true
        if (index >= uris.size) index = 0
        return index to false
    }
}
