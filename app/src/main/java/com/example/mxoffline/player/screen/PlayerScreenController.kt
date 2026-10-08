/**
 * Role: Screen orientation and aspect ratio controller.
 * Responsibility: Enforces user screen rotation lock modes and ExoPlayer video scaling factors.
 * Details: Persists orientation and aspect settings across app sessions and applies video aspect ratios.
 */
package com.example.mxoffline.player.screen

import android.app.Activity
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.mxoffline.player.PlayerHudController
import com.example.mxoffline.util.AppBackupManager
import com.example.mxoffline.util.PreferenceHelper

class PlayerScreenController(
    private val settingsPrefs: SharedPreferences
) {

    companion object {
        const val ORIENTATION_AUTO = 0
        const val ORIENTATION_LANDSCAPE = 1
        const val ORIENTATION_PORTRAIT = 2
        const val ORIENTATION_VIDEO = 3

        const val ASPECT_FIT = 0
        const val ASPECT_FILL = 1
        const val ASPECT_ZOOM = 2
    }

    var orientationMode: Int = PreferenceHelper.safeGetInt(settingsPrefs, "orientation_mode", ORIENTATION_LANDSCAPE)
        private set

    var aspectModeIndex: Int = PreferenceHelper.safeGetInt(settingsPrefs, "aspect_mode", ASPECT_FIT)
        private set

    fun getOrientationLabel(): String {
        return when (orientationMode) {
            ORIENTATION_LANDSCAPE -> "Land"
            ORIENTATION_PORTRAIT -> "Port"
            ORIENTATION_VIDEO -> "Video"
            else -> "Auto"
        }
    }

    fun applyOrientation(activity: Activity, player: ExoPlayer?, hud: PlayerHudController?, showFeedback: Boolean = true) {
        when (orientationMode) {
            ORIENTATION_AUTO -> {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
                if (showFeedback) hud?.showQuickFeedback("Orientation: Auto Sensor")
            }
            ORIENTATION_LANDSCAPE -> {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                if (showFeedback) hud?.showQuickFeedback("Orientation: Landscape Locked")
            }
            ORIENTATION_PORTRAIT -> {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                if (showFeedback) hud?.showQuickFeedback("Orientation: Portrait Locked")
            }
            ORIENTATION_VIDEO -> {
                val format = player?.videoFormat
                val size = player?.videoSize
                val w = if (size != null && size.width > 0) size.width else (format?.width ?: 0)
                val h = if (size != null && size.height > 0) size.height else (format?.height ?: 0)
                if (w > 0 && h > 0) {
                    if (w >= h) {
                        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        if (showFeedback) hud?.showQuickFeedback("Orientation: Match Video (Land)")
                    } else {
                        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                        if (showFeedback) hud?.showQuickFeedback("Orientation: Match Video (Port)")
                    }
                } else {
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    if (showFeedback) hud?.showQuickFeedback("Orientation: Match Video (Default Land)")
                }
            }
        }
    }

    fun cycleOrientation(activity: Activity, player: ExoPlayer?, hud: PlayerHudController?): String {
        orientationMode = (orientationMode + 1) % 4
        settingsPrefs.edit().putInt("orientation_mode", orientationMode).apply()
        AppBackupManager.backupToStorageAsync(activity)
        applyOrientation(activity, player, hud, showFeedback = true)
        return getOrientationLabel()
    }

    fun getAspectLabel(): String {
        val modes = listOf("Fit", "Fill", "Zoom")
        return modes.getOrElse(aspectModeIndex) { "Fit" }
    }

    fun applyAspectRatio(playerView: PlayerView) {
        when (aspectModeIndex) {
            ASPECT_FIT -> playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            ASPECT_FILL -> playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            ASPECT_ZOOM -> playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
            else -> playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
    }

    fun cycleAspectRatio(activity: Activity, playerView: PlayerView, hud: PlayerHudController?): String {
        val modes = listOf(
            "Fit" to AspectRatioFrameLayout.RESIZE_MODE_FIT,
            "Fill" to AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
            "Zoom" to AspectRatioFrameLayout.RESIZE_MODE_FILL
        )
        aspectModeIndex = (aspectModeIndex + 1) % modes.size
        playerView.resizeMode = modes[aspectModeIndex].second
        settingsPrefs.edit().putInt("aspect_mode", aspectModeIndex).apply()
        AppBackupManager.backupToStorageAsync(activity)
        hud?.showQuickFeedback("Screen: ${modes[aspectModeIndex].first}")
        return modes[aspectModeIndex].first
    }

    fun syncFromPreferences(activity: Activity, player: ExoPlayer?, playerView: PlayerView) {
        orientationMode = PreferenceHelper.safeGetInt(settingsPrefs, "orientation_mode", ORIENTATION_LANDSCAPE)
        aspectModeIndex = PreferenceHelper.safeGetInt(settingsPrefs, "aspect_mode", ASPECT_FIT)
        applyOrientation(activity, player, null, showFeedback = false)
        applyAspectRatio(playerView)
    }
}
