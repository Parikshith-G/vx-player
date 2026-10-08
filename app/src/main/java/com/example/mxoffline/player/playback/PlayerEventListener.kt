/**
 * Role: ExoPlayer playback state and lifecycle listener.
 * Responsibility: Listens to track transitions, completion, buffer state changes, and decoding errors.
 * Details: Triggers auto-advance, PiP updates, resume points, and audio boost synchronization.
 */
package com.example.mxoffline.player.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer

class PlayerEventListener(
    private val player: ExoPlayer,
    private val callbacks: Callbacks
) : Player.Listener {

    interface Callbacks {
        fun onReady()
        fun onEnded()
        fun onPlayingChanged(isPlaying: Boolean)
        fun onError(err: PlaybackException)
        fun onVideoSizeChanged(videoSize: VideoSize)
        fun onMediaItemTransition(currentMediaItemIndex: Int)
    }

    override fun onPlaybackStateChanged(state: Int) {
        if (state == Player.STATE_READY) {
            callbacks.onReady()
        } else if (state == Player.STATE_ENDED) {
            callbacks.onEnded()
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        callbacks.onPlayingChanged(isPlaying)
    }

    override fun onPlayerError(error: PlaybackException) {
        callbacks.onError(error)
    }

    override fun onVideoSizeChanged(videoSize: VideoSize) {
        callbacks.onVideoSizeChanged(videoSize)
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        callbacks.onMediaItemTransition(player.currentMediaItemIndex)
    }
}
