/**
 * Role: Fullscreen offline video player activity with hardware gesture support.
 * Responsibility: Coordinates video playback lifecycle, PiP mode, and connects modular player sub-systems.
 * Details: Implements 100% offline playback with zero network access and automatic preferences persistence.
 */
package com.example.mxoffline

import android.content.Context
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import com.example.mxoffline.player.*
import com.example.mxoffline.player.buttons.PlayerQuickButtonsManager
import com.example.mxoffline.player.menu.PlayerMenuHelper
import com.example.mxoffline.player.playback.PlayerEventListener
import com.example.mxoffline.player.playlist.PlayerPlaylistController
import com.example.mxoffline.player.screen.PlayerScreenController
import com.example.mxoffline.player.ui.PlayerUiBuilder
import com.example.mxoffline.player.ui.PlayerUiViews
import com.example.mxoffline.util.AppBackupManager

class PlayerActivity : ComponentActivity(), PlayerGestureCallback, PlayerQuickButtonsManager.Callbacks, PlayerMenuHelper.Callback, PlayerEventListener.Callbacks {

    private lateinit var player: ExoPlayer
    private lateinit var ui: PlayerUiViews
    private lateinit var s: PlayerSubsystems
    private val handler = Handler(Looper.getMainLooper())
    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private val resumePrefs by lazy { getSharedPreferences("player_resume", MODE_PRIVATE) }
    private val settingsPrefs by lazy { getSharedPreferences("player_settings", MODE_PRIVATE) }
    private val seenPrefs by lazy { getSharedPreferences("player_seen", MODE_PRIVATE) }

    private var controlsVisible = true
    private var isScreenLocked = false
    private var isMuted = false
    private var preferSoftwareDecoder = false
    private var backgroundPlayEnabled = false
    private var subtitleFontSizeSp = 18f
    private var currentPlaybackSpeed = 1.0f

    private val deleteLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == RESULT_OK) onVideoDeletedSuccess() else Toast.makeText(this, "Delete cancelled", Toast.LENGTH_SHORT).show()
    }

    private val subtitlePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                player.setMediaItem(MediaItem.Builder().setUri(s.playlist.getCurrentUri() ?: return@runCatching).setSubtitleConfigurations(listOf(MediaItem.SubtitleConfiguration.Builder(uri).setMimeType(androidx.media3.common.MimeTypes.APPLICATION_SUBRIP).setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT).build())).build())
                s.hud.showQuickFeedback("External subtitle loaded")
            }.onFailure { Toast.makeText(this, "Failed to load subtitle", Toast.LENGTH_SHORT).show() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppBackupManager.autoRestoreIfAvailable(this)
        ui = PlayerUiBuilder.build(this)
        setContentView(ui.root)

        val uris = intent.getStringArrayListExtra("uris") ?: arrayListOf()
        val names = intent.getStringArrayListExtra("names") ?: arrayListOf()
        val index = intent.getIntExtra("index", 0)

        currentPlaybackSpeed = settingsPrefs.getFloat("playback_speed", 1.0f)
        backgroundPlayEnabled = settingsPrefs.getBoolean("bg_play", false)

        s = PlayerSubsystems.create(
            this, ui, handler, settingsPrefs, resumePrefs, seenPrefs, uris, names, index, deleteLauncher, this,
            onPlaylistIndexChanged = { onPlaylistIndexChanged() },
            onSeekStop = { if (::player.isInitialized && player.duration > 0) player.seekTo((player.duration * ui.seekBar.progress) / 1000); scheduleHideControls() },
            onReachEndThreshold = { s.seen.markCurrentVideoAsSeen(s.playlist.uris, s.playlist.index) },
            onPipPlay = { if (player.playbackState == androidx.media3.common.Player.STATE_IDLE || player.playerError != null) player.prepare(); player.play(); s.pip.updatePipParams(player, ui.playerView) },
            onPipPause = { player.pause(); s.pip.updatePipParams(player, ui.playerView) },
            onPipPrev = { s.playlist.previousVideo(player); s.pip.updatePipParams(player, ui.playerView) },
            onPipNext = { s.playlist.nextVideo(player); s.pip.updatePipParams(player, ui.playerView) },
            onPipDismiss = { if (::player.isInitialized) { s.resume.savePosition(player, s.playlist.uris, s.playlist.index); player.pause() }; finish() }
        )

        initPlayer()
        setupListeners()
        s.pip.register()
        hideSystemBars()
        scheduleHideControls()
        handler.post(progressTracker)
    }

    private fun initPlayer() {
        val renderersFactory = DefaultRenderersFactory(this).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            if (preferSoftwareDecoder) {
                setMediaCodecSelector { mime, sec, tun -> androidx.media3.exoplayer.mediacodec.MediaCodecSelector.DEFAULT.getDecoderInfos(mime, sec, tun).sortedByDescending { it.name.startsWith("c2.android", true) || it.name.startsWith("omx.google", true) } }
            }
        }
        player = ExoPlayer.Builder(this, renderersFactory).build().also { p ->
            ui.playerView.player = p
            p.addListener(PlayerEventListener(p, this))
            if (s.playlist.uris.isNotEmpty()) {
                p.setMediaItems(s.playlist.uris.map { MediaItem.fromUri(it) }, s.playlist.index, 0)
                p.prepare()
                p.playbackParameters = PlaybackParameters(currentPlaybackSpeed, 1.0f)
                p.playWhenReady = true
                s.seen.markCurrentVideoAsSeen(s.playlist.uris, s.playlist.index)
            }
        }
        s.initPlayerBoundControllers(this, window, handler, player, audioManager, this)
        ui.titleView.text = s.playlist.getCurrentName()
        s.screen.applyOrientation(this, player, null, false)
        s.screen.applyAspectRatio(ui.playerView)
        s.quickButtons.renderButtons()
        s.seen.updateMarkDoneButtonState(ui.markDoneBtn, s.playlist.uris, s.playlist.index)
    }

    private fun setupListeners() {
        ui.backButton.setOnClickListener { finish() }
        ui.menuButton.setOnClickListener { PlayerMenuHelper.showHamburgerMenu(this, backgroundPlayEnabled, this) }
        ui.topTimeStatusView.setOnClickListener { s.header.cycleTopTimeMode(s.hud) }
        ui.lockBtn.setOnClickListener { lockScreen() }
        ui.lockFloatingBtn.setOnClickListener { unlockScreen() }
        ui.repeatBtn.setOnClickListener { s.hud.showQuickFeedback(s.playlist.cycleRepeatMode()) }
        ui.playPauseBtn.setOnClickListener { togglePlay() }
        ui.rewindBtn.setOnClickListener { seekBy(-10_000) }
        ui.forwardBtn.setOnClickListener { seekBy(10_000) }
        ui.nextBtn.setOnClickListener { s.playlist.nextVideo(player) }
        ui.markDoneBtn.setOnClickListener {
            val marked = s.seen.toggleMarkCurrentVideoAsSeen(s.playlist.uris, s.playlist.index)
            s.seen.updateMarkDoneButtonState(ui.markDoneBtn, s.playlist.uris, s.playlist.index)
            s.hud.showQuickFeedback(if (marked) "Marked as Seen ✓" else "Removed from Seen")
        }
        ui.restartBtn.setOnClickListener { player.seekTo(0); s.resume.clearPosition(s.playlist.uris, s.playlist.index) }
        val toggleTime: (View) -> Unit = { s.timeline.toggleRemainingTime() }
        ui.timeView.setOnClickListener(toggleTime)
        ui.remainingTimeView.setOnClickListener(toggleTime)
        s.timeline.setupHoldToContinuousSeek(ui.rewindBtn, false, { player }, { scheduleHideControls() })
        s.timeline.setupHoldToContinuousSeek(ui.forwardBtn, true, { player }, { scheduleHideControls() })
    }

    private fun onPlaylistIndexChanged() {
        ui.titleView.text = s.playlist.getCurrentName()
        s.seen.markCurrentVideoAsSeen(s.playlist.uris, s.playlist.index)
        s.seen.updateMarkDoneButtonState(ui.markDoneBtn, s.playlist.uris, s.playlist.index)
        s.pip.updatePipParams(player, ui.playerView)
    }

    override fun onReady() { s.audioBoost.initAudioEffects(player); s.resume.checkAndApplyResume(player, s.playlist.uris, s.playlist.index) }
    override fun onEnded() {
        s.seen.markCurrentVideoAsSeen(s.playlist.uris, s.playlist.index); ui.playPauseBtn.text = "▶"
        when (s.playlist.repeatMode) {
            PlayerPlaylistController.REPEAT_ALL -> s.playlist.nextVideo(player)
            PlayerPlaylistController.REPEAT_ONE -> { player.seekTo(0); player.prepare(); player.play() }
            else -> if (!s.playlist.nextVideo(player)) { player.pause(); s.resume.clearPosition(s.playlist.uris, s.playlist.index) }
        }
    }
    override fun onPlayingChanged(isPlaying: Boolean) {
        ui.playPauseBtn.text = if (isPlaying) "Ⅱ" else "▶"
        if (isPlaying) scheduleHideControls() else handler.removeCallbacks(hideControlsRunnable)
        s.pip.updatePipParams(player, ui.playerView)
    }
    override fun onError(err: androidx.media3.common.PlaybackException) {
        ui.playPauseBtn.text = "▶"; s.hud.showQuickFeedback("Playback error"); Toast.makeText(this, "Cannot play: ${s.playlist.getCurrentName()}", Toast.LENGTH_LONG).show()
    }
    override fun onVideoSizeChanged(videoSize: VideoSize) {
        if (s.screen.orientationMode == PlayerScreenController.ORIENTATION_VIDEO) s.screen.applyOrientation(this, player, null, false)
        s.pip.updatePipParams(player, ui.playerView)
    }
    override fun onMediaItemTransition(currentMediaItemIndex: Int) {
        if (currentMediaItemIndex in s.playlist.uris.indices) { s.playlist.index = currentMediaItemIndex; onPlaylistIndexChanged() }
    }

    private val progressTracker = object : Runnable {
        override fun run() {
            if (::player.isInitialized) {
                s.timeline.updateProgress(player); s.header.update(player)
                if (player.isPlaying) s.resume.savePosition(player, s.playlist.uris, s.playlist.index)
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun isLocked(): Boolean = isScreenLocked
    override fun onSingleTap() = if (isScreenLocked) showLockTemporarily() else toggleControls()
    override fun onSeekBy(deltaMs: Long, isForward: Boolean) = seekBy(deltaMs)
    override fun onTogglePlay() = togglePlay()
    override fun onTouchEvent(ev: MotionEvent): Boolean = s.gesture?.handleTouchEvent(ev) == true || super.onTouchEvent(ev)
    override fun onApplyAudioBoost(boost: Int) = s.audioBoost.applyBoost(boost, player)

    private fun togglePlay() {
        if (!::player.isInitialized) return
        if (player.playbackState == androidx.media3.common.Player.STATE_ENDED) { player.seekTo(0L); player.prepare(); player.play(); return }
        if (player.playerError != null || player.playbackState == androidx.media3.common.Player.STATE_IDLE) { player.prepare(); player.play(); return }
        if (player.playWhenReady) player.pause() else player.play()
    }

    private fun seekBy(delta: Long) {
        if (::player.isInitialized) player.seekTo((player.currentPosition + delta).coerceIn(0L, player.duration.coerceAtLeast(0)))
    }

    private fun toggleControls() {
        controlsVisible = !controlsVisible; ui.overlayContainer.visibility = if (controlsVisible) View.VISIBLE else View.GONE
        if (controlsVisible) { hideSystemBars(); scheduleHideControls() } else handler.removeCallbacks(hideControlsRunnable)
    }

    private val hideControlsRunnable = Runnable {
        if (::player.isInitialized && player.isPlaying && controlsVisible && !isScreenLocked) {
            ui.overlayContainer.visibility = View.GONE; controlsVisible = false
        }
    }

    private fun scheduleHideControls() { handler.removeCallbacks(hideControlsRunnable); handler.postDelayed(hideControlsRunnable, 4500) }
    private fun lockScreen() { isScreenLocked = true; controlsVisible = false; ui.overlayContainer.visibility = View.GONE; showLockTemporarily() }
    private fun unlockScreen() { isScreenLocked = false; ui.lockFloatingBtn.visibility = View.GONE; controlsVisible = true; ui.overlayContainer.visibility = View.VISIBLE; scheduleHideControls() }
    private fun showLockTemporarily() { ui.lockFloatingBtn.visibility = View.VISIBLE; handler.removeCallbacks(hideLockRunnable); handler.postDelayed(hideLockRunnable, 3000L) }
    private val hideLockRunnable = Runnable { ui.lockFloatingBtn.visibility = View.GONE }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode) return
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus) hideSystemBars() }
    override fun onResume() { super.onResume(); s.pip.onResumeClean(); hideSystemBars(); s.header.setVisible(true); ui.overlayContainer.visibility = if (controlsVisible) View.VISIBLE else View.GONE; scheduleHideControls() }
    override fun onPause() { super.onPause(); if (::player.isInitialized) { s.resume.savePosition(player, s.playlist.uris, s.playlist.index); if (!(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode) && !backgroundPlayEnabled) player.pause() }; AppBackupManager.backupToStorageAsync(this) }
    override fun onStop() { super.onStop(); if (::player.isInitialized && !(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode) && !backgroundPlayEnabled) player.pause() }
    override fun onUserLeaveHint() { super.onUserLeaveHint(); if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && ::player.isInitialized) onEnterPip() }
    override fun onPictureInPictureModeChanged(inPip: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(inPip, newConfig)
        s.pip.onPipChanged(inPip, player, ui.playerView, onEntered = { ui.overlayContainer.visibility = View.GONE; s.header.setVisible(false); ui.lockFloatingBtn.visibility = View.GONE }, onExited = { hideSystemBars(); s.header.setVisible(true); ui.overlayContainer.visibility = View.VISIBLE; scheduleHideControls() })
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null); s.pip.onDestroyClean()
        if (::player.isInitialized) { s.resume.savePosition(player, s.playlist.uris, s.playlist.index); player.pause(); s.audioBoost.release(); ui.playerView.player = null; player.release() }
        super.onDestroy()
    }

    override fun onSpeedClicked(anchor: TextView?) = s.dialogs?.showSpeedDialog(anchor) { sp -> currentPlaybackSpeed = sp; player.playbackParameters = PlaybackParameters(sp, 1f); settingsPrefs.edit().putFloat("playback_speed", sp).apply() } ?: Unit
    override fun onOrientationClicked() { s.screen.cycleOrientation(this, player, s.hud); s.quickButtons.updateDynamicLabels() }
    override fun onAspectClicked() { s.screen.cycleAspectRatio(this, ui.playerView, s.hud); s.quickButtons.updateDynamicLabels() }
    override fun onPlaylistClicked() = s.playlist.showPlaylistDialog(player)
    override fun onAudioClicked() = s.dialogs?.showAudioTrackDialog(isMuted) { isMuted = !isMuted; player.volume = if (isMuted) 0f else 1f; s.hud.showQuickFeedback(if (isMuted) "Muted" else "Unmuted") } ?: Unit
    override fun onSubtitleClicked() = s.dialogs?.showSubtitleDialog(subtitleFontSizeSp, { subtitlePicker.launch(arrayOf("*/*")) }) { fs -> subtitleFontSizeSp = fs; ui.playerView.subtitleView?.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, fs) } ?: Unit
    override fun onDecoderClicked() { preferSoftwareDecoder = !preferSoftwareDecoder; s.quickButtons.updateDynamicLabels(); s.hud.showQuickFeedback(if (preferSoftwareDecoder) "Decoder: SW" else "Decoder: HW"); initPlayer() }
    override fun onTimerClicked() = s.dialogs?.showSleepTimerDialog { m -> handler.removeCallbacks(sleepTimerRunnable); if (m > 0) { handler.postDelayed(sleepTimerRunnable, m * 60_000L); s.hud.showQuickFeedback("Sleep timer: $m mins") } } ?: Unit
    private val sleepTimerRunnable = Runnable { if (::player.isInitialized) { player.pause(); Toast.makeText(this, "Sleep timer: stopped", Toast.LENGTH_LONG).show() } }
    override fun onVideoDeletedSuccess() {
        val (newIdx, empty) = s.playlist.removeCurrent()
        if (empty) { player.stop(); player.clearMediaItems(); finish() }
        else { player.removeMediaItem(newIdx); player.seekTo(newIdx, 0L); player.play(); onPlaylistIndexChanged() }
    }
    override fun onButtonInteracted() = scheduleHideControls()
    override fun getCurrentUri() = s.playlist.getCurrentUri()
    override fun getCurrentName() = s.playlist.getCurrentName()
    override fun getOrientationLabel() = s.screen.getOrientationLabel()
    override fun getAspectLabel() = s.screen.getAspectLabel()
    override fun isSoftwareDecoder() = preferSoftwareDecoder

    override fun onCustomizeQuickButtons() = s.quickButtons.showCustomizeDialog()
    override fun onSpeedDialog() = onSpeedClicked(null)
    override fun onCycleAspectRatio() = onAspectClicked()
    override fun onCycleOrientation() = onOrientationClicked()
    override fun onAudioTrackDialog() = onAudioClicked()
    override fun onSubtitleDialog() = onSubtitleClicked()
    override fun onToggleDecoder() = onDecoderClicked()
    override fun onSleepTimerDialog() = onTimerClicked()
    override fun onToggleBackgroundPlay(): Boolean { backgroundPlayEnabled = !backgroundPlayEnabled; settingsPrefs.edit().putBoolean("bg_play", backgroundPlayEnabled).apply(); s.hud.showQuickFeedback("Background Play: ${if (backgroundPlayEnabled) "On" else "Off"}"); return backgroundPlayEnabled }
    override fun onVideoInfoDialog() = s.dialogs?.showVideoInfoDialog(s.playlist.getCurrentName(), preferSoftwareDecoder) ?: Unit
    override fun onEnterPip() = s.pip.enterPip(player, ui.playerView, { ui.overlayContainer.visibility = View.GONE; s.header.setVisible(false); ui.lockFloatingBtn.visibility = View.GONE }, { ui.overlayContainer.visibility = View.VISIBLE; s.header.setVisible(true) })
    override fun onRestoreSettings() { s.screen.syncFromPreferences(this, player, ui.playerView); s.header.syncFromPreferences(); s.timeline.syncFromPreferences(); s.quickButtons.renderButtons(); onPlaylistIndexChanged() }
}
