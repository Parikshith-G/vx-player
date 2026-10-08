/**
 * Role: Subsystem container and dependency aggregator for PlayerActivity.
 * Responsibility: Instantiates and holds references to player controllers, helpers, and managers.
 * Details: Bundles hud, screen, seen, audio boost, header, pip, playlist, quick buttons, timeline, and resume managers.
 */
package com.example.mxoffline.player

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.os.Handler
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.media3.exoplayer.ExoPlayer
import com.example.mxoffline.player.audio.PlayerAudioBoostManager
import com.example.mxoffline.player.buttons.PlayerQuickButtonsManager
import com.example.mxoffline.player.header.PlayerStatusHeaderManager
import com.example.mxoffline.player.pip.PlayerPipManager
import com.example.mxoffline.player.playlist.PlayerPlaylistController
import com.example.mxoffline.player.screen.PlayerScreenController
import com.example.mxoffline.player.seen.PlayerSeenManager
import com.example.mxoffline.player.timeline.PlayerTimelineManager
import com.example.mxoffline.player.ui.PlayerUiViews

class PlayerSubsystems(
    val hud: PlayerHudController,
    val screen: PlayerScreenController,
    val seen: PlayerSeenManager,
    val audioBoost: PlayerAudioBoostManager,
    val header: PlayerStatusHeaderManager,
    val pip: PlayerPipManager,
    val playlist: PlayerPlaylistController,
    val quickButtons: PlayerQuickButtonsManager,
    val timeline: PlayerTimelineManager,
    val resume: PlayerResumeManager
) {
    var gesture: PlayerGestureController? = null
    var dialogs: PlayerDialogHelper? = null

    companion object {
        fun create(
            activity: ComponentActivity,
            ui: PlayerUiViews,
            handler: Handler,
            settingsPrefs: SharedPreferences,
            resumePrefs: SharedPreferences,
            seenPrefs: SharedPreferences,
            uris: ArrayList<String>,
            names: ArrayList<String>,
            initialIndex: Int,
            deleteLauncher: ActivityResultLauncher<IntentSenderRequest>,
            quickButtonsCallbacks: PlayerQuickButtonsManager.Callbacks,
            onPlaylistIndexChanged: () -> Unit,
            onSeekStop: () -> Unit,
            onReachEndThreshold: () -> Unit,
            onPipPlay: () -> Unit,
            onPipPause: () -> Unit,
            onPipPrev: () -> Unit,
            onPipNext: () -> Unit,
            onPipDismiss: () -> Unit
        ): PlayerSubsystems {
            val hud = PlayerHudController(
                handler,
                ui.seekHud, ui.seekIcon, ui.seekTimeText, ui.seekDeltaText, ui.seekProgressBar,
                ui.brightnessHud, ui.brightnessText, ui.brightnessBar,
                ui.volumeHud, ui.volumeIcon, ui.volumeText, ui.volumeBar,
                ui.centerSpeedHud, ui.quickFeedbackHud
            )
            val screen = PlayerScreenController(settingsPrefs)
            val seen = PlayerSeenManager(activity, seenPrefs)
            val audioBoost = PlayerAudioBoostManager()
            val header = PlayerStatusHeaderManager(activity, settingsPrefs, ui.persistentStatusHeader, ui.topTimeStatusView, ui.batteryText, ui.clockText)
            val playlist = PlayerPlaylistController(activity, uris, names, initialIndex) { onPlaylistIndexChanged() }
            val quickButtons = PlayerQuickButtonsManager(activity, settingsPrefs, handler, ui.quickButtonsLayout, hud, deleteLauncher, quickButtonsCallbacks)
            val timeline = PlayerTimelineManager(activity, settingsPrefs, handler, ui.seekBar, ui.timeView, ui.remainingTimeView, hud, onSeekStop, onReachEndThreshold)
            val resume = PlayerResumeManager(resumePrefs, handler, ui.resumeBanner, ui.resumeText)
            val pip = PlayerPipManager(activity, handler, onPipPlay, onPipPause, onPipPrev, onPipNext, onPipDismiss)

            return PlayerSubsystems(hud, screen, seen, audioBoost, header, pip, playlist, quickButtons, timeline, resume)
        }
    }

    fun initPlayerBoundControllers(activity: Context, window: Window, handler: Handler, player: ExoPlayer, audioManager: AudioManager, callback: PlayerGestureCallback) {
        gesture = PlayerGestureController(activity, window, handler, player, audioManager, hud, callback)
        dialogs = PlayerDialogHelper(activity, player)
    }
}
