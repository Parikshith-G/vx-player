/**
 * Role: Android MediaSession manager for Bluetooth earbud and headset media controls.
 * Responsibility: Handles Bluetooth AVRCP media button events (play, pause, next, prev, double-tap).
 * Details: Accurately differentiates single-tap from double-tap gestures and honors the BT Double-Tap toggle.
 */
package com.example.mxoffline.player.playback

import android.content.Context
import android.content.Intent
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
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
    private var lastSkipTime = 0L
    private val mainHandler = Handler(Looper.getMainLooper())
    private var tapCount = 0

    private val singleTapRunnable = Runnable {
        tapCount = 0
        // Standard single tap: normal play/pause
        callbacks.onTogglePlay()
    }

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
                        if (event != null) {
                            if (event.action == KeyEvent.ACTION_DOWN) {
                                if (handleMediaKeyEvent(event.keyCode)) {
                                    return true
                                }
                            } else if (event.action == KeyEvent.ACTION_UP) {
                                when (event.keyCode) {
                                    KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                                    KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_REWIND,
                                    KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                                    KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> return true
                                }
                            }
                        }
                        return super.onMediaButtonEvent(mediaButtonIntent)
                    }

                    override fun onPlay() {
                        handlePlayPauseKey()
                    }

                    override fun onPause() {
                        handlePlayPauseKey()
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
            if (now - lastSkipTime > 350) {
                lastSkipTime = now
                callbacks.onTogglePlay()
                val isPlaying = callbacks.isPlaying()
                callbacks.showHud(if (isPlaying) "Ⅱ Pause (BT 2×)" else "▶ Play (BT 2×)")
            }
        } else {
            // When BT double tap toggle is OFF:
            // Double tap behaves as standard track skip, NOT play/pause!
            callbacks.onSkipNext()
        }
    }

    private fun handlePlayPauseKey() {
        tapCount++
        if (tapCount == 1) {
            mainHandler.removeCallbacks(singleTapRunnable)
            mainHandler.postDelayed(singleTapRunnable, 320)
        } else if (tapCount >= 2) {
            mainHandler.removeCallbacks(singleTapRunnable)
            tapCount = 0
            if (callbacks.isBtDoubleTapEnabled()) {
                // When BT double tap toggle is ON:
                // Double tap triggers Play/Pause!
                callbacks.onTogglePlay()
                val isPlaying = callbacks.isPlaying()
                callbacks.showHud(if (isPlaying) "Ⅱ Pause (BT 2×)" else "▶ Play (BT 2×)")
            } else {
                // When BT double tap toggle is OFF:
                // Double tap does NOT pause and play! Skips track instead.
                callbacks.onSkipNext()
            }
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
            mainHandler.removeCallbacksAndMessages(null)
            mediaSession?.isActive = false
            mediaSession?.release()
            mediaSession = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
