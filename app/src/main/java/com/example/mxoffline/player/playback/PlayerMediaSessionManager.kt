/**
 * Role: Android MediaSession manager for Bluetooth earbud and headset media controls.
 * Responsibility: Handles Bluetooth AVRCP media button events (play, pause, next, prev, double-tap).
 * Details: Intercepts single and double-tap gestures, routes next/prev, and keeps system playback state in sync.
 */
package com.example.mxoffline.player.playback

import android.content.Context
import android.content.Intent
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent

class PlayerMediaSessionManager(
    private val context: Context,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun onTogglePlay()
        fun onSkipNext()
        fun onSkipPrev()
        fun isBtDoubleTapEnabled(): Boolean
        fun showHud(message: String)
        fun isPlaying(): Boolean
        fun getCurrentPosition(): Long
    }

    private var mediaSession: MediaSession? = null
    private var lastBtTapTime = 0L

    fun init() {
        release()
        try {
            mediaSession = MediaSession(context, "VXPlayerSession").apply {
                setFlags(
                    MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
                )
                setCallback(object : MediaSession.Callback() {
                    override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                        val event = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                        }
                        if (event != null && event.action == KeyEvent.ACTION_DOWN) {
                            if (handleMediaKeyEvent(event.keyCode)) {
                                return true
                            }
                        }
                        return super.onMediaButtonEvent(mediaButtonIntent)
                    }

                    override fun onPlay() {
                        callbacks.onTogglePlay()
                    }

                    override fun onPause() {
                        callbacks.onTogglePlay()
                    }

                    override fun onSkipToNext() {
                        handleSkipNext()
                    }

                    override fun onSkipToPrevious() {
                        callbacks.onSkipPrev()
                    }

                    override fun onFastForward() {
                        handleSkipNext()
                    }

                    override fun onRewind() {
                        callbacks.onSkipPrev()
                    }
                })
                isActive = true
            }
            updatePlaybackState(callbacks.isPlaying(), callbacks.getCurrentPosition())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun handleMediaKeyEvent(keyCode: Int): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                handleSkipNext()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                callbacks.onSkipPrev()
                return true
            }
            KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                handlePlayPauseKey()
                return true
            }
        }
        return false
    }

    fun handleSkipNext() {
        val now = SystemClock.uptimeMillis()
        if (callbacks.isBtDoubleTapEnabled()) {
            if (now - lastBtTapTime > 350) {
                lastBtTapTime = now
                callbacks.onTogglePlay()
                val isPlaying = callbacks.isPlaying()
                callbacks.showHud(if (isPlaying) "Ⅱ Pause (BT 2×)" else "▶ Play (BT 2×)")
            }
        } else {
            callbacks.onSkipNext()
        }
    }

    private fun handlePlayPauseKey() {
        val now = SystemClock.uptimeMillis()
        if (callbacks.isBtDoubleTapEnabled()) {
            // Earbuds sending two rapid play/pause clicks: swallow second click within 500ms
            if (now - lastBtTapTime <= 500) {
                lastBtTapTime = 0L
                callbacks.showHud(if (callbacks.isPlaying()) "▶ Play (BT 2×)" else "Ⅱ Pause (BT 2×)")
                return
            }
            lastBtTapTime = now
            callbacks.onTogglePlay()
            callbacks.showHud(if (callbacks.isPlaying()) "▶ Play (BT 2×)" else "Ⅱ Pause (BT 2×)")
        } else {
            callbacks.onTogglePlay()
        }
    }

    fun updatePlaybackState(isPlaying: Boolean, currentPositionMs: Long) {
        val session = mediaSession ?: return
        try {
            val state = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
            val actions = PlaybackState.ACTION_PLAY or
                    PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_PLAY_PAUSE or
                    PlaybackState.ACTION_SKIP_TO_NEXT or
                    PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackState.ACTION_FAST_FORWARD or
                    PlaybackState.ACTION_REWIND
            val playbackState = PlaybackState.Builder()
                .setActions(actions)
                .setState(state, currentPositionMs.coerceAtLeast(0L), 1.0f)
                .build()
            session.setPlaybackState(playbackState)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun release() {
        try {
            mediaSession?.isActive = false
            mediaSession?.release()
            mediaSession = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
