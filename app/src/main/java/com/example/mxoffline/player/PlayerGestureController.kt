/**
 * Role: Touch gestures and hardware controls coordinator for PlayerActivity.
 * Responsibility: Detects vertical swipes (volume/brightness), horizontal drags (seek), and hold-to-speed gestures.
 * Details: Implements left-half brightness control, right-half volume boost, and hold-to-speed persistence.
 */
package com.example.mxoffline.player

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.Window
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import com.example.mxoffline.util.UiUtils
import com.example.mxoffline.util.AppBackupManager
import kotlin.math.abs
import kotlin.math.hypot

interface PlayerGestureCallback {
    fun isLocked(): Boolean
    fun onSingleTap()
    fun onSeekBy(deltaMs: Long, isForward: Boolean)
    fun onTogglePlay()
    fun onApplyAudioBoost(boostPercent: Int)
}

class PlayerGestureController(
    private val context: Context,
    private val window: Window,
    private val handler: Handler,
    private val player: ExoPlayer,
    private val audioManager: AudioManager,
    private val hudController: PlayerHudController,
    private val callback: PlayerGestureCallback
) {
    companion object {
        private const val GESTURE_NONE = 0
        private const val GESTURE_SEEK = 1
        private const val GESTURE_BRIGHTNESS = 2
        private const val GESTURE_VOLUME = 3
        private const val GESTURE_HOLD_BOOST = 4
    }

    private var gestureStartX = 0f
    private var gestureStartY = 0f
    private var gestureMode = GESTURE_NONE
    private var startVideoPosition = 0L
    private var targetSeekPosition = 0L
    private var startBrightness = 0.5f
    private var startNormalizedVolume = 0f
    private var currentBoostPercent = 0
    private var lastTapTime = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f
    private var originalSpeedBeforeBoost = 1.0f
    private var wasPlayingBeforeBoost = true
    private var holdStartX = 0f
    private var holdBaseSpeed = 2.0f
    private var currentHoldSpeed = 2.0f
    private var rememberedHoldSpeed: Float = context.getSharedPreferences("player_settings", Context.MODE_PRIVATE)
        .getFloat("hold_speed", 2.0f).coerceIn(0.25f, 8.0f)
    private var pendingSingleTap: Runnable? = null
    private val touchSlop = (ViewConfiguration.get(context).scaledTouchSlop / 2).coerceAtLeast(8)

    private val holdBoostRunnable = Runnable {
        if (gestureMode == GESTURE_NONE && !callback.isLocked()) {
            gestureMode = GESTURE_HOLD_BOOST
            originalSpeedBeforeBoost = player.playbackParameters.speed
            wasPlayingBeforeBoost = player.isPlaying
            holdBaseSpeed = context.getSharedPreferences("player_settings", Context.MODE_PRIVATE)
                .getFloat("hold_speed", 2.0f).coerceIn(0.25f, 8.0f)
            currentHoldSpeed = holdBaseSpeed
            holdStartX = gestureStartX
            player.playbackParameters = PlaybackParameters(currentHoldSpeed, 1.0f)
            if (!wasPlayingBeforeBoost) {
                player.play()
            }
            hudController.showSpeed(currentHoldSpeed)
        }
    }

    fun handleTouchEvent(event: MotionEvent): Boolean {
        if (callback.isLocked()) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    gestureStartX = event.x
                    gestureStartY = event.y
                }
                MotionEvent.ACTION_UP -> {
                    val dx = abs(event.x - gestureStartX)
                    val dy = abs(event.y - gestureStartY)
                    if (dx < touchSlop && dy < touchSlop) {
                        callback.onSingleTap()
                    }
                }
            }
            return true
        }

        val screenWidth = context.resources.displayMetrics.widthPixels.toFloat()
        val screenHeight = context.resources.displayMetrics.heightPixels.toFloat()

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                handler.removeCallbacks(holdBoostRunnable)
                gestureStartX = event.x
                gestureStartY = event.y
                gestureMode = GESTURE_NONE
                startVideoPosition = player.currentPosition
                startBrightness = window.attributes.screenBrightness.takeIf { it >= 0 } ?: 0.5f

                val maxSysVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
                val curSysVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                startNormalizedVolume = curSysVol + (currentBoostPercent / 100f * maxSysVol)

                handler.postDelayed(holdBoostRunnable, 350)
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - gestureStartX
                val dy = event.y - gestureStartY
                val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()

                // In hold boost mode, slide horizontally to adjust speed linearly
                if (gestureMode == GESTURE_HOLD_BOOST) {
                    val slideDx = event.x - holdStartX
                    val stepPx = UiUtils.dp(context, 20).toFloat().coerceAtLeast(1f)
                    val deltaSpeed = (slideDx / stepPx) * 0.1f
                    val rawTargetSpeed = holdBaseSpeed + deltaSpeed

                    // Pin holdStartX if limits are reached to eliminate dead-zone on direction reversal
                    if (rawTargetSpeed > 8.0f) {
                        holdStartX = event.x - ((8.0f - holdBaseSpeed) / 0.1f * stepPx)
                    } else if (rawTargetSpeed < 0.25f) {
                        holdStartX = event.x - ((0.25f - holdBaseSpeed) / 0.1f * stepPx)
                    }

                    val targetSpeed = (holdBaseSpeed + ((event.x - holdStartX) / stepPx) * 0.1f).coerceIn(0.25f, 8.0f)
                    val linearSpeed = (kotlin.math.round(targetSpeed * 10f) / 10f).coerceIn(0.25f, 8.0f)

                    currentHoldSpeed = linearSpeed
                    player.playbackParameters = PlaybackParameters(currentHoldSpeed, 1.0f)
                    hudController.showSpeed(currentHoldSpeed)
                    return true
                }

                if (dist > touchSlop) {
                    handler.removeCallbacks(holdBoostRunnable)

                    if (gestureMode == GESTURE_NONE) {
                        val topExclusionPx = UiUtils.dp(context, 35).toFloat().coerceAtLeast(60f)
                        val bottomExclusionPx = screenHeight - UiUtils.dp(context, 35).toFloat()
                        if (abs(dy) >= abs(dx)) {
                            // If swipe started near top (notification shade) or bottom (system nav bar), ignore brightness & volume swipes
                            if (gestureStartY < topExclusionPx || gestureStartY > bottomExclusionPx) {
                                gestureMode = GESTURE_NONE
                            } else {
                                // Vertical swipe: Left = Brightness, Right = Volume
                                gestureMode = if (gestureStartX < screenWidth / 2f) GESTURE_BRIGHTNESS else GESTURE_VOLUME
                            }
                        } else {
                            // Horizontal swipe: Seek
                            gestureMode = GESTURE_SEEK
                        }
                    }

                    when (gestureMode) {
                        GESTURE_SEEK -> {
                            val duration = player.duration.coerceAtLeast(0)
                            val seekScaleSec = when {
                                duration < 600_000 -> 90L
                                duration < 3600_000 -> 240L
                                else -> 600L
                            }
                            val deltaMs = ((dx / screenWidth) * seekScaleSec * 1000).toLong()
                            targetSeekPosition = (startVideoPosition + deltaMs).coerceIn(0L, duration)
                            hudController.showSeek(targetSeekPosition, deltaMs, duration)
                        }

                        GESTURE_BRIGHTNESS -> {
                            val deltaRatio = -dy / (screenHeight * 0.7f)
                            val newBrightness = (startBrightness + deltaRatio).coerceIn(0.01f, 1.0f)
                            window.attributes = window.attributes.apply { screenBrightness = newBrightness }
                            hudController.showBrightness((newBrightness * 100).toInt())
                        }

                        GESTURE_VOLUME -> {
                            val maxSysVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
                            val totalMax = maxSysVol * 2f
                            val deltaRatio = -dy / (screenHeight * 0.7f)
                            val newTotal = (startNormalizedVolume + deltaRatio * totalMax).coerceIn(0f, totalMax)

                            if (newTotal <= maxSysVol) {
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newTotal.toInt(), 0)
                                currentBoostPercent = 0
                                callback.onApplyAudioBoost(0)
                                val percent = ((newTotal / maxSysVol) * 100).toInt()
                                hudController.showVolume(percent, false)
                            } else {
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxSysVol.toInt(), 0)
                                currentBoostPercent = (((newTotal - maxSysVol) / maxSysVol) * 100).toInt()
                                callback.onApplyAudioBoost(currentBoostPercent)
                                hudController.showVolume(100 + currentBoostPercent, true)
                            }
                        }
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(holdBoostRunnable)

                when (gestureMode) {
                    GESTURE_HOLD_BOOST -> {
                        rememberedHoldSpeed = currentHoldSpeed
                        context.getSharedPreferences("player_settings", Context.MODE_PRIVATE)
                            .edit()
                            .putFloat("hold_speed", rememberedHoldSpeed)
                            .apply()
                        AppBackupManager.backupToStorageAsync(context)
                        player.playbackParameters = PlaybackParameters(originalSpeedBeforeBoost, 1.0f)
                        if (!wasPlayingBeforeBoost) {
                            player.pause()
                        }
                        hudController.hideSpeed()
                    }

                    GESTURE_SEEK -> {
                        player.seekTo(targetSeekPosition)
                        hudController.hideSeek()
                    }

                    GESTURE_BRIGHTNESS -> {
                        hudController.hideBrightness()
                    }

                    GESTURE_VOLUME -> {
                        hudController.hideVolume()
                    }

                    GESTURE_NONE -> {
                        handleTapOrDoubleTap(event.x, event.y, screenWidth)
                    }
                }
                gestureMode = GESTURE_NONE
            }
        }
        return true
    }

    private fun handleTapOrDoubleTap(x: Float, y: Float, screenWidth: Float) {
        val now = System.currentTimeMillis()
        val isDoubleTap = (now - lastTapTime < 320) && (hypot((x - lastTapX).toDouble(), (y - lastTapY).toDouble()) < UiUtils.dp(context, 80))

        if (isDoubleTap) {
            pendingSingleTap?.let { handler.removeCallbacks(it) }
            pendingSingleTap = null
            lastTapTime = 0L

            callback.onTogglePlay()
            hudController.showQuickFeedback(if (player.isPlaying) "Ⅱ" else "▶")
        } else {
            lastTapTime = now
            lastTapX = x
            lastTapY = y
            val single = Runnable {
                pendingSingleTap = null
                callback.onSingleTap()
            }
            pendingSingleTap = single
            handler.postDelayed(single, 220)
        }
    }
}
