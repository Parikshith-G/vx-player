/**
 * Role: Audio hardware boost controller.
 * Responsibility: Manages android.media.audiofx.LoudnessEnhancer to provide up to 200% volume boost.
 * Details: Attaches to ExoPlayer audio session IDs, calculates millibel gains, and safely releases resources.
 */
package com.example.mxoffline.player.audio

import android.media.audiofx.LoudnessEnhancer
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer

class PlayerAudioBoostManager {

    private var loudnessEnhancer: LoudnessEnhancer? = null
    var currentBoostPercent: Int = 0
        private set

    fun initAudioEffects(player: ExoPlayer) {
        val sessionId = player.audioSessionId
        if (sessionId != C.AUDIO_SESSION_ID_UNSET) {
            runCatching {
                loudnessEnhancer?.release()
                loudnessEnhancer = LoudnessEnhancer(sessionId).apply { enabled = true }
                applyBoost(currentBoostPercent, player)
            }
        }
    }

    fun applyBoost(boostPercent: Int, player: ExoPlayer) {
        currentBoostPercent = boostPercent.coerceIn(0, 100)
        runCatching {
            if (loudnessEnhancer == null && player.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                loudnessEnhancer = LoudnessEnhancer(player.audioSessionId).apply { enabled = true }
            }
            if (currentBoostPercent > 0) {
                loudnessEnhancer?.enabled = true
                val gainMb = (currentBoostPercent / 100.0 * 1800).toInt()
                loudnessEnhancer?.setTargetGain(gainMb)
            } else {
                loudnessEnhancer?.setTargetGain(0)
                loudnessEnhancer?.enabled = false
            }
        }
    }

    fun release() {
        runCatching { loudnessEnhancer?.release() }
        loudnessEnhancer = null
    }
}
