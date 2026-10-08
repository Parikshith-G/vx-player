/**
 * Role: Playback timeline, seekbar, and progress coordinator.
 * Responsibility: Tracks seekbar dragging, updates elapsed/remaining time, and executes continuous hold-seeking.
 * Details: Automatically marks videos as completed when nearing playback end and persists time mode.
 */
package com.example.mxoffline.player.timeline

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.view.MotionEvent
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import androidx.media3.exoplayer.ExoPlayer
import com.example.mxoffline.player.PlayerHudController
import com.example.mxoffline.util.AppBackupManager
import com.example.mxoffline.util.PreferenceHelper
import com.example.mxoffline.util.TimeFormatter

class PlayerTimelineManager(
    private val context: Context,
    private val settingsPrefs: SharedPreferences,
    private val handler: Handler,
    private val seekBar: SeekBar,
    private val timeView: TextView,
    private val remainingTimeView: TextView,
    private val hudController: PlayerHudController,
    private val onSeekStop: () -> Unit,
    private val onReachEndThreshold: () -> Unit
) {

    var isUserTrackingSeek: Boolean = false
        private set

    var showRemainingTime: Boolean = PreferenceHelper.safeGetBoolean(settingsPrefs, "show_remaining_time", true)
        private set

    init {
        setupSeekBar()
    }

    private fun setupSeekBar() {
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && bar != null) {
                    val d = seekBar.tag as? Long ?: 0L
                    if (d > 0) {
                        val targetMs = (d * progress) / 1000
                        timeView.text = TimeFormatter.formatTime(targetMs)
                    }
                }
            }

            override fun onStartTrackingTouch(bar: SeekBar?) {
                isUserTrackingSeek = true
            }

            override fun onStopTrackingTouch(bar: SeekBar?) {
                isUserTrackingSeek = false
                val d = seekBar.tag as? Long ?: 0L
                if (d > 0 && bar != null) {
                    val targetMs = (d * bar.progress) / 1000
                    onSeekStop()
                }
            }
        })
    }

    fun toggleRemainingTime(): Boolean {
        showRemainingTime = !showRemainingTime
        settingsPrefs.edit().putBoolean("show_remaining_time", showRemainingTime).apply()
        AppBackupManager.backupToStorageAsync(context)
        return showRemainingTime
    }

    fun updateProgress(player: ExoPlayer?) {
        if (player == null) return
        val d = player.duration.coerceAtLeast(0)
        val p = player.currentPosition.coerceAtLeast(0)
        seekBar.tag = d

        if (!isUserTrackingSeek) {
            seekBar.progress = if (d > 0) ((p * 1000) / d).toInt() else 0
            timeView.text = TimeFormatter.formatTime(p)
        }
        seekBar.secondaryProgress = if (d > 0) ((player.bufferedPosition * 1000) / d).toInt() else 0

        if (showRemainingTime) {
            val remaining = (d - p).coerceAtLeast(0)
            remainingTimeView.text = "-${TimeFormatter.formatTime(remaining)}"
        } else {
            remainingTimeView.text = TimeFormatter.formatTime(d)
        }

        if (d > 5_000L && p >= d - 2_000L) {
            onReachEndThreshold()
        }
    }

    fun setupHoldToContinuousSeek(view: View, isForward: Boolean, playerProvider: () -> ExoPlayer?, onAction: () -> Unit) {
        var isHolding = false
        var holdStartPos = 0L

        val seekStepRunnable = object : Runnable {
            override fun run() {
                val player = playerProvider() ?: return
                val step = if (isForward) 2_000L else -2_000L
                val duration = player.duration.coerceAtLeast(0L)
                val current = player.currentPosition.coerceAtLeast(0L)
                val target = (current + step).coerceIn(0L, duration)
                player.seekTo(target)

                val totalDelta = target - holdStartPos
                hudController.showSeek(target, totalDelta, duration)

                if (duration > 0) {
                    seekBar.progress = ((target * 1000) / duration).toInt()
                    timeView.text = TimeFormatter.formatTime(target)
                }
                updateProgress(player)
                onAction()
                handler.postDelayed(this, 75L)
            }
        }

        val holdDetectRunnable = Runnable {
            val player = playerProvider() ?: return@Runnable
            isHolding = true
            holdStartPos = player.currentPosition.coerceAtLeast(0L)
            handler.post(seekStepRunnable)
        }

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isHolding = false
                    handler.postDelayed(holdDetectRunnable, 400L)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(holdDetectRunnable)
                    handler.removeCallbacks(seekStepRunnable)
                    if (!isHolding && event.action == MotionEvent.ACTION_UP) {
                        v.performClick()
                    }
                    onAction()
                    true
                }
                else -> false
            }
        }
    }

    fun syncFromPreferences() {
        showRemainingTime = PreferenceHelper.safeGetBoolean(settingsPrefs, "show_remaining_time", true)
    }
}
