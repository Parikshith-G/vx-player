/**
 * Role: Touch gestures and hardware controls coordinator for PlayerActivity.
 * Responsibility: Detects vertical swipes (volume/brightness), tap controls, and hold-to-speed gestures.
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
    fun onAdjustSubtitlePosition(deltaFraction: Float) {}
    fun onAdjustSubtitleSize(deltaSp: Float) {}
    fun onSubtitleGestureEnded() {}
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
        private const val GESTURE_SWIPE_IGNORED = 1
        private const val GESTURE_BRIGHTNESS = 2
        private const val GESTURE_VOLUME = 3
        private const val GESTURE_HOLD_BOOST = 4
        private const val GESTURE_TWO_FINGER = 5
    }

    private var gestureStartX = 0f
    private var gestureStartY = 0f
    private var gestureMode = GESTURE_NONE
    private var startBrightness = 0.5f
    private var startNormalizedVolume = 0f
    private var currentBoostPercent = 0
    private var lastTapUpTime = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f
    private var inDoubleTapSeek = false
    private var accumulatedSeekDelta = 0L
    private var originalSpeedBeforeBoost = 1.0f
    private var wasPlayingBeforeBoost = true
    private var holdStartX = 0f
    private var holdBaseSpeed = 2.0f
    private var currentHoldSpeed = 2.0f
    private var rememberedHoldSpeed: Float = context.getSharedPreferences("player_settings", Context.MODE_PRIVATE)
        .getFloat("hold_speed", 2.0f).coerceIn(0.25f, 8.0f)
    private var pendingSingleTap: Runnable? = null
    private val touchSlop = (ViewConfiguration.get(context).scaledTouchSlop / 2).coerceAtLeast(8)

    private var twoFingerActive = false
    private var twoFingerMode = 0 // 0 = NONE, 1 = SWIPE_Y (move), 2 = PINCH (size)
    private var twoFingerStartMidY = 0f
    private var twoFingerLastMidY = 0f
    private var twoFingerStartDistance = 0f
    private var twoFingerLastDistance = 0f

    private val resetSeekRunnable = Runnable {
        inDoubleTapSeek = false
        accumulatedSeekDelta = 0L
        lastTapUpTime = 0L
    }

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

        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) {
                    handler.removeCallbacks(holdBoostRunnable)
                    pendingSingleTap?.let { handler.removeCallbacks(it) }
                    pendingSingleTap = null
                    if (gestureMode == GESTURE_HOLD_BOOST) {
                        player.playbackParameters = PlaybackParameters(originalSpeedBeforeBoost, 1.0f)
                        hudController.hideSpeed()
                        if (!wasPlayingBeforeBoost) player.pause()
                    }
                    gestureMode = GESTURE_TWO_FINGER
                    twoFingerActive = true
                    twoFingerMode = 0
                    twoFingerStartMidY = (event.getY(0) + event.getY(1)) / 2f
                    twoFingerLastMidY = twoFingerStartMidY
                    val dx = (event.getX(0) - event.getX(1)).toDouble()
                    val dy = (event.getY(0) - event.getY(1)).toDouble()
                    twoFingerStartDistance = hypot(dx, dy).toFloat().coerceAtLeast(10f)
                    twoFingerLastDistance = twoFingerStartDistance
                    return true
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (twoFingerActive) {
                    if (twoFingerMode != 0) {
                        callback.onSubtitleGestureEnded()
                    }
                    twoFingerActive = false
                    twoFingerMode = 0
                    gestureMode = GESTURE_SWIPE_IGNORED
                    return true
                }
            }

            MotionEvent.ACTION_DOWN -> {
                handler.removeCallbacks(holdBoostRunnable)
                val now = System.currentTimeMillis()
                val isCandidateMultiTap = (now - lastTapUpTime < 380) &&
                        (hypot((event.x - lastTapX).toDouble(), (event.y - lastTapY).toDouble()) < UiUtils.dp(context, 80))
                if (isCandidateMultiTap || inDoubleTapSeek) {
                    pendingSingleTap?.let { handler.removeCallbacks(it) }
                    pendingSingleTap = null
                }
                gestureStartX = event.x
                gestureStartY = event.y
                gestureMode = GESTURE_NONE
                startBrightness = window.attributes.screenBrightness.takeIf { it >= 0 } ?: 0.5f

                val maxSysVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
                val curSysVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                startNormalizedVolume = curSysVol + (currentBoostPercent / 100f * maxSysVol)

                handler.postDelayed(holdBoostRunnable, 350)
            }

            MotionEvent.ACTION_MOVE -> {
                if (twoFingerActive && event.pointerCount >= 2) {
                    val curMidY = (event.getY(0) + event.getY(1)) / 2f
                    val dx = (event.getX(0) - event.getX(1)).toDouble()
                    val dy = (event.getY(0) - event.getY(1)).toDouble()
                    val curDist = hypot(dx, dy).toFloat()
                    val totalDy = curMidY - twoFingerStartMidY
                    val totalDistChange = curDist - twoFingerStartDistance

                    if (twoFingerMode == 0) {
                        if (abs(totalDistChange) > UiUtils.dp(context, 16) && abs(totalDistChange) > abs(totalDy)) {
                            twoFingerMode = 2 // PINCH: Resize
                            twoFingerLastDistance = curDist
                        } else if (abs(totalDy) > UiUtils.dp(context, 10)) {
                            twoFingerMode = 1 // SWIPE_Y: Move
                            twoFingerLastMidY = curMidY
                        }
                    }

                    when (twoFingerMode) {
                        1 -> {
                            val stepDy = curMidY - twoFingerLastMidY
                            val deltaFraction = -stepDy / (screenHeight * 0.7f)
                            callback.onAdjustSubtitlePosition(deltaFraction)
                            twoFingerLastMidY = curMidY
                        }
                        2 -> {
                            val stepDist = curDist - twoFingerLastDistance
                            val stepPx = UiUtils.dp(context, 15).toFloat().coerceAtLeast(1f)
                            val deltaSp = (stepDist / stepPx) * 1.5f
                            callback.onAdjustSubtitleSize(deltaSp)
                            twoFingerLastDistance = curDist
                        }
                    }
                    return true
                }

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
                                gestureMode = GESTURE_SWIPE_IGNORED
                            } else {
                                // Vertical swipe: Left = Brightness, Right = Volume
                                gestureMode = if (gestureStartX < screenWidth / 2f) GESTURE_BRIGHTNESS else GESTURE_VOLUME
                            }
                        } else {
                            // Horizontal swipe: seeking disabled; user seeks via seekbar or buttons
                            gestureMode = GESTURE_SWIPE_IGNORED
                        }
                    }

                    when (gestureMode) {
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

                if (twoFingerActive) {
                    if (twoFingerMode != 0) {
                        callback.onSubtitleGestureEnded()
                    }
                    twoFingerActive = false
                    twoFingerMode = 0
                    gestureMode = GESTURE_NONE
                    return true
                }

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

                    GESTURE_BRIGHTNESS -> {
                        hudController.hideBrightness()
                    }

                    GESTURE_VOLUME -> {
                        hudController.hideVolume()
                    }

                    GESTURE_NONE -> {
                        handleTapOrDoubleTap(event.x, event.y, screenWidth, screenHeight)
                    }

                    GESTURE_SWIPE_IGNORED, GESTURE_TWO_FINGER -> {
                        // Multi-touch or ignored swipe ended; do not trigger taps
                    }
                }
                gestureMode = GESTURE_NONE
            }
        }
        return true
    }

    private fun handleTapOrDoubleTap(x: Float, y: Float, screenWidth: Float, screenHeight: Float) {
        val now = System.currentTimeMillis()
        val isNearLastTap = hypot((x - lastTapX).toDouble(), (y - lastTapY).toDouble()) < UiUtils.dp(context, 80)
        val isDoubleTap = (now - lastTapUpTime < 380) && isNearLastTap

        if (isDoubleTap || (inDoubleTapSeek && (now - lastTapUpTime < 550) && isNearLastTap)) {
            pendingSingleTap?.let { handler.removeCallbacks(it) }
            pendingSingleTap = null
            handler.removeCallbacks(resetSeekRunnable)

            lastTapUpTime = now
            lastTapX = x
            lastTapY = y

            val isTopQuarter = y <= (screenHeight * 0.25f)
            val isLeftThird = x <= (screenWidth * 0.35f)
            val isRightThird = x >= (screenWidth * 0.65f)

            if (isTopQuarter && isLeftThird) {
                // Top-left corner double-tap: Seek backward 5s
                inDoubleTapSeek = true
                accumulatedSeekDelta = if (accumulatedSeekDelta < 0) accumulatedSeekDelta - 5_000L else -5_000L
                callback.onSeekBy(-5_000L, false)
                val sec = abs(accumulatedSeekDelta) / 1000
                hudController.showQuickFeedback("⟲ ${sec}s")
                handler.postDelayed(resetSeekRunnable, 600)
            } else if (isTopQuarter && isRightThird) {
                // Top-right corner double-tap: Seek forward 5s
                inDoubleTapSeek = true
                accumulatedSeekDelta = if (accumulatedSeekDelta > 0) accumulatedSeekDelta + 5_000L else 5_000L
                callback.onSeekBy(5_000L, true)
                val sec = abs(accumulatedSeekDelta) / 1000
                hudController.showQuickFeedback("${sec}s ⟳")
                handler.postDelayed(resetSeekRunnable, 600)
            } else {
                // Center / general double-tap: Toggle Play / Pause
                inDoubleTapSeek = false
                accumulatedSeekDelta = 0L
                lastTapUpTime = 0L
                callback.onTogglePlay()
                hudController.showQuickFeedback(if (player.isPlaying) "Ⅱ" else "▶")
            }
        } else {
            inDoubleTapSeek = false
            accumulatedSeekDelta = 0L
            lastTapUpTime = now
            lastTapX = x
            lastTapY = y
            val single = Runnable {
                pendingSingleTap = null
                if (!inDoubleTapSeek) {
                    callback.onSingleTap()
                }
            }
            pendingSingleTap = single
            handler.postDelayed(single, 320)
        }
    }
}
