/**
 * Role: Fullscreen offline video player activity with hardware gesture support.
 * Responsibility: Coordinates video playback lifecycle, PiP mode, and connects modular player sub-systems.
 * Details: Implements 100% offline playback with zero network access and automatic preferences persistence.
 */
package com.example.mxoffline

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.TypedValue
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
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
import com.example.mxoffline.util.VideoIdentity

class PlayerActivity : ComponentActivity(), PlayerGestureCallback, PlayerQuickButtonsManager.Callbacks, PlayerMenuHelper.Callback, PlayerEventListener.Callbacks {

    private lateinit var player: ExoPlayer
    private lateinit var ui: PlayerUiViews
    private lateinit var s: PlayerSubsystems
    private val handler = Handler(Looper.getMainLooper())
    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private val resumePrefs by lazy { getSharedPreferences("player_resume", MODE_PRIVATE) }
    private val settingsPrefs by lazy { getSharedPreferences("player_settings", MODE_PRIVATE) }
    private val seenPrefs by lazy { getSharedPreferences("player_seen", MODE_PRIVATE) }

    private var isMuted = false; private var preferSoftwareDecoder = false
    private var backgroundPlayEnabled = false; private var subtitleFontSizeSp = 18f; private var currentPlaybackSpeed = 1.0f
    private var sleepTimerAtEndOfVideo = false
    private var mediaSessionManager: com.example.mxoffline.player.playback.PlayerMediaSessionManager? = null

    private val deleteLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { if (it.resultCode == RESULT_OK) onVideoDeletedSuccess() else Toast.makeText(this, "Delete cancelled", Toast.LENGTH_SHORT).show() }

    private val subtitlePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && ::player.isInitialized) runCatching {
            contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val currentPos = player.currentPosition
            val currentPlayWhenReady = player.playWhenReady
            val currentIdx = s.playlist.index
            val subName = runCatching {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
                }
            }.getOrNull() ?: uri.lastPathSegment.orEmpty()
            val subLower = subName.lowercase()
            val mimeType = when {
                subLower.endsWith(".vtt") || subLower.endsWith(".webvtt") -> androidx.media3.common.MimeTypes.TEXT_VTT
                subLower.endsWith(".ass") || subLower.endsWith(".ssa") -> androidx.media3.common.MimeTypes.TEXT_SSA
                else -> androidx.media3.common.MimeTypes.APPLICATION_SUBRIP
            }
            val newItems = s.playlist.uris.mapIndexed { i, u ->
                if (i == currentIdx) {
                    MediaItem.Builder()
                        .setUri(u)
                        .setSubtitleConfigurations(listOf(
                            MediaItem.SubtitleConfiguration.Builder(uri)
                                .setMimeType(mimeType)
                                .setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT)
                                .build()
                        )).build()
                } else {
                    MediaItem.fromUri(u)
                }
            }
            player.setMediaItems(newItems, currentIdx, currentPos)
            player.playWhenReady = currentPlayWhenReady
            s.hud.showQuickFeedback("External subtitle loaded")
        }.onFailure { Toast.makeText(this, "Failed to load subtitle", Toast.LENGTH_SHORT).show() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.example.mxoffline.util.CrashProtection.install(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); window.statusBarColor = Color.BLACK; window.navigationBarColor = Color.BLACK
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        ui = PlayerUiBuilder.build(this)
        setContentView(ui.root)

        val uris = intent.getStringArrayListExtra("uris") ?: arrayListOf()
        val names = intent.getStringArrayListExtra("names") ?: arrayListOf()
        val rawSizes = intent.getLongArrayExtra("sizes")
        val sizes = if (rawSizes != null) ArrayList(rawSizes.toList()) else arrayListOf<Long>()
        var index = intent.getIntExtra("index", 0).coerceIn(0, (uris.size - 1).coerceAtLeast(0))

        val dataUri = intent.data
        if (uris.isEmpty() && dataUri != null) {
            uris.add(dataUri.toString())
            var size = 0L
            val name = runCatching {
                contentResolver.query(dataUri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                    val nameCol = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeCol = c.getColumnIndex(OpenableColumns.SIZE)
                    if (c.moveToFirst()) {
                        if (sizeCol >= 0 && !c.isNull(sizeCol)) size = c.getLong(sizeCol)
                        if (nameCol >= 0) c.getString(nameCol) else null
                    } else null
                }
            }.getOrNull() ?: dataUri.lastPathSegment ?: "Video"
            names.add(name); sizes.add(size); index = 0
        }

        preferSoftwareDecoder = com.example.mxoffline.util.PreferenceHelper.safeGetBoolean(settingsPrefs, "sw_decoder", false)
        currentPlaybackSpeed = com.example.mxoffline.util.PreferenceHelper.safeGetFloat(settingsPrefs, "playback_speed", 1.0f)
        backgroundPlayEnabled = com.example.mxoffline.util.PreferenceHelper.safeGetBoolean(settingsPrefs, "bg_play", false)

        s = PlayerSubsystems.create(
            this, ui, handler, settingsPrefs, resumePrefs, seenPrefs, uris, names, index, sizes, deleteLauncher, this,
            isPlayerPlaying = { ::player.isInitialized && player.isPlaying },
            isInPip = { Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode },
            onPlaylistIndexChanged = { onPlaylistIndexChanged() },
            onSeekStop = { if (::player.isInitialized && player.duration > 0) player.seekTo((player.duration * ui.seekBar.progress) / 1000); s.controlsLock.scheduleHideControls() },
            onReachEndThreshold = { s.seen.markCurrentVideoAsSeen(s.playlist) },
            onPipPlay = { if (::player.isInitialized) { if (player.playbackState == androidx.media3.common.Player.STATE_IDLE || player.playerError != null) player.prepare(); player.play(); s.pip.updatePipParams(player, ui.playerView) } },
            onPipPause = { if (::player.isInitialized) { player.pause(); s.pip.updatePipParams(player, ui.playerView) } },
            onPipPrev = { if (::player.isInitialized) { player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L)); s.pip.updatePipParams(player, ui.playerView) } },
            onPipNext = { if (::player.isInitialized) { val dur = if (player.duration > 0) player.duration else Long.MAX_VALUE; player.seekTo((player.currentPosition + 10_000L).coerceAtMost(dur)); s.pip.updatePipParams(player, ui.playerView) } },
            onPipDismiss = { if (::player.isInitialized) { s.resume.savePosition(player, s.playlist); player.pause() }; finish() }
        )

        initPlayer(); setupListeners(); s.pip.register(); setupBackNavigation()
        s.controlsLock.hideSystemBars(); s.controlsLock.scheduleHideControls(); handler.post(progressTracker)
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (s.controlsLock.isScreenLocked) s.controlsLock.showLockTemporarily()
                else if (s.controlsLock.controlsVisible) s.controlsLock.toggleControls()
                else finish()
            }
        })
    }

    private fun initPlayer() {
        val renderersFactory = DefaultRenderersFactory(this).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            if (preferSoftwareDecoder) {
                setMediaCodecSelector { mime, sec, tun -> androidx.media3.exoplayer.mediacodec.MediaCodecSelector.DEFAULT.getDecoderInfos(mime, sec, tun).sortedByDescending { it.name.startsWith("c2.android", true) || it.name.startsWith("omx.google", true) } }
            }
        }
        val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
        val p = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(audioAttributes, true)
            .build()
        player = p; ui.playerView.player = p
        s.initPlayerBoundControllers(this, window, handler, p, audioManager, this)
        p.addListener(PlayerEventListener(p, this))
        mediaSessionManager?.release()
        mediaSessionManager = com.example.mxoffline.player.playback.PlayerMediaSessionManager(this, object : com.example.mxoffline.player.playback.PlayerMediaSessionManager.Callbacks {
            override fun onTogglePlay() = togglePlay()
            override fun onSkipNext() { if (::player.isInitialized) { s.seen.markCurrentVideoAsSeen(s.playlist); s.playlist.nextVideo(player) } }
            override fun onSkipPrev() { if (::player.isInitialized) s.playlist.previousVideo(player) }
            override fun isBtDoubleTapEnabled() = this@PlayerActivity.isBtDoubleTapEnabled()
            override fun showHud(message: String) = s.hud.showQuickFeedback(message)
            override fun isPlaying() = ::player.isInitialized && player.isPlaying
            override fun getCurrentPosition() = if (::player.isInitialized) player.currentPosition else 0L
        }).apply { init() }
        if (s.playlist.uris.isNotEmpty()) {
            p.setMediaItems(s.playlist.uris.map { MediaItem.fromUri(it) }, s.playlist.index, 0)
            p.prepare(); p.playbackParameters = PlaybackParameters(currentPlaybackSpeed, 1.0f); p.playWhenReady = true
        }
        ui.titleView.text = s.playlist.getCurrentName()
        s.screen.applyOrientation(this, p, null, false); s.screen.applyAspectRatio(ui.playerView)
        s.quickButtons.renderButtons(); s.seen.updateMarkDoneButtonState(ui.markDoneBtn, s.playlist)
        s.resume.recordWatchTime(s.playlist)
    }

    private fun setupListeners() {
        ui.backButton.setOnClickListener { finish() }
        ui.menuButton.setOnClickListener { PlayerMenuHelper.showHamburgerMenu(this, backgroundPlayEnabled, this) }
        ui.topTimeStatusView.setOnClickListener { s.header.cycleTopTimeMode(s.hud) }
        ui.lockBtn.setOnClickListener { s.controlsLock.lockScreen() }; ui.lockFloatingBtn.setOnClickListener { s.controlsLock.unlockScreen() }
        ui.seekBack5Btn.setOnClickListener { seekBy(-5_000); s.hud.showQuickFeedback("⟲ 5s") }
        s.timeline.setupHoldToContinuousSeek(ui.seekBack5Btn, false, { player }, { s.controlsLock.scheduleHideControls() })
        ui.prevBtn.setOnClickListener { s.playlist.previousVideo(player) }
        s.timeline.setupHoldToContinuousSeek(ui.prevBtn, false, { player }, { s.controlsLock.scheduleHideControls() })
        ui.playPauseBtn.setOnClickListener { togglePlay() }
        ui.nextBtn.setOnClickListener { s.seen.markCurrentVideoAsSeen(s.playlist); s.playlist.nextVideo(player) }
        s.timeline.setupHoldToContinuousSeek(ui.nextBtn, true, { player }, { s.controlsLock.scheduleHideControls() })
        ui.markDoneBtn.setOnClickListener {
            val marked = s.seen.toggleMarkCurrentVideoAsSeen(s.playlist)
            s.seen.updateMarkDoneButtonState(ui.markDoneBtn, s.playlist)
            s.hud.showQuickFeedback(if (marked) "Marked as Seen ✓" else "Removed from Seen")
        }
        ui.seekFwd5Btn.setOnClickListener { seekBy(5_000); s.hud.showQuickFeedback("5s ⟳") }
        s.timeline.setupHoldToContinuousSeek(ui.seekFwd5Btn, true, { player }, { s.controlsLock.scheduleHideControls() })
        ui.skipOpBtn.setOnClickListener { skipForward90s() }
        ui.pipBtn.setOnClickListener { onEnterPip() }
        ui.restartBtn.setOnClickListener { player.seekTo(0); s.resume.clearPosition(s.playlist) }
        val toggleTime: (View) -> Unit = { s.timeline.toggleRemainingTime() }
        ui.timeView.setOnClickListener(toggleTime); ui.remainingTimeView.setOnClickListener(toggleTime)
    }

    private fun onPlaylistIndexChanged() {
        ui.titleView.text = s.playlist.getCurrentName()
        s.seen.updateMarkDoneButtonState(ui.markDoneBtn, s.playlist)
        if (::player.isInitialized) s.pip.updatePipParams(player, ui.playerView)
    }

    override fun onReady() {
        if (::player.isInitialized) {
            s.audioBoost.initAudioEffects(player)
            s.resume.checkAndApplyResume(player, s.playlist)
            mediaSessionManager?.updatePlaybackState(player.isPlaying, player.currentPosition)
        }
    }
    override fun onEnded() {
        if (!::player.isInitialized) return
        s.seen.markCurrentVideoAsSeen(s.playlist); ui.playPauseBtn.text = "▶"
        if (sleepTimerAtEndOfVideo) {
            sleepTimerAtEndOfVideo = false
            player.pause()
            s.resume.clearPosition(s.playlist)
            Toast.makeText(this, "Sleep timer: stopped at end of video", Toast.LENGTH_LONG).show()
            return
        }
        when (s.playlist.repeatMode) {
            PlayerPlaylistController.REPEAT_ALL -> s.playlist.nextVideo(player)
            PlayerPlaylistController.REPEAT_ONE -> { player.seekTo(0); player.prepare(); player.play() }
            else -> if (!s.playlist.nextVideo(player)) { player.pause(); s.resume.clearPosition(s.playlist) }
        }
    }
    override fun onPlayingChanged(isPlaying: Boolean) {
        ui.playPauseBtn.text = if (isPlaying) "Ⅱ" else "▶"
        if (isPlaying) s.controlsLock.scheduleHideControls() else s.controlsLock.cancelHideControls()
        if (::player.isInitialized) {
            s.pip.updatePipParams(player, ui.playerView)
            mediaSessionManager?.updatePlaybackState(isPlaying, player.currentPosition)
        }
    }
    override fun onError(err: androidx.media3.common.PlaybackException) {
        ui.playPauseBtn.text = "▶"; s.hud.showQuickFeedback("Playback error"); Toast.makeText(this, "Cannot play: ${s.playlist.getCurrentName()}", Toast.LENGTH_LONG).show()
    }
    override fun onVideoSizeChanged(videoSize: VideoSize) {
        if (::player.isInitialized) {
            if (s.screen.orientationMode == PlayerScreenController.ORIENTATION_VIDEO) s.screen.applyOrientation(this, player, null, false)
            s.pip.updatePipParams(player, ui.playerView)
        }
    }
    override fun onMediaItemTransition(currentMediaItemIndex: Int) {
        if (currentMediaItemIndex in s.playlist.uris.indices) {
            s.playlist.index = currentMediaItemIndex
            s.resume.recordWatchTime(s.playlist)
            onPlaylistIndexChanged()
        }
    }

    private val progressTracker = object : Runnable {
        override fun run() {
            if (::player.isInitialized) {
                s.timeline.updateProgress(player); s.header.update(player)
                if (player.isPlaying) s.resume.savePosition(player, s.playlist)
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun isLocked(): Boolean = s.controlsLock.isScreenLocked
    override fun onSingleTap() = if (s.controlsLock.isScreenLocked) s.controlsLock.showLockTemporarily() else s.controlsLock.toggleControls()
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
        if (!::player.isInitialized) return
        val cur = player.currentPosition.coerceAtLeast(0L)
        val dur = if (player.duration > 0) player.duration else Long.MAX_VALUE
        player.seekTo((cur + delta).coerceIn(0L, dur))
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus) s.controlsLock.hideSystemBars() }
    override fun onConfigurationChanged(newConfig: Configuration) { super.onConfigurationChanged(newConfig); s.controlsLock.hideSystemBars(); if (::player.isInitialized) s.header.update(player) }
    override fun onResume() { super.onResume(); s.pip.onResumeClean(); s.controlsLock.hideSystemBars(); s.header.setVisible(true); s.controlsLock.setControlsVisible(s.controlsLock.controlsVisible) }
    override fun onPause() { super.onPause(); if (::player.isInitialized) { s.resume.savePosition(player, s.playlist); if (!(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode) && !backgroundPlayEnabled) player.pause() }; AppBackupManager.backupToStorageAsync(this) }
    override fun onStop() { super.onStop(); if (::player.isInitialized && !(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode) && !backgroundPlayEnabled) player.pause() }
    override fun onUserLeaveHint() { super.onUserLeaveHint(); if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && ::player.isInitialized) onEnterPip() }
    override fun onPictureInPictureModeChanged(inPip: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(inPip, newConfig)
        if (::player.isInitialized) s.pip.onPipChanged(inPip, player, ui.playerView, onEntered = { ui.overlayContainer.visibility = View.GONE; s.header.setVisible(false); ui.lockFloatingBtn.visibility = View.GONE }, onExited = { s.controlsLock.hideSystemBars(); s.header.setVisible(true); s.controlsLock.setControlsVisible(true) })
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null); s.pip.onDestroyClean(); s.controlsLock.release()
        mediaSessionManager?.release(); mediaSessionManager = null
        if (::player.isInitialized) { s.resume.savePosition(player, s.playlist); player.pause(); s.audioBoost.release(); ui.playerView.player = null; player.release() }
        super.onDestroy()
    }

    override fun onSpeedClicked(anchor: TextView?) = s.dialogs?.showSpeedDialog(anchor) { sp -> currentPlaybackSpeed = sp; if (::player.isInitialized) player.playbackParameters = PlaybackParameters(sp, 1f); settingsPrefs.edit().putFloat("playback_speed", sp).apply() } ?: Unit
    override fun onOrientationClicked() { if (::player.isInitialized) s.screen.cycleOrientation(this, player, s.hud); s.quickButtons.updateDynamicLabels() }
    override fun onAspectClicked() { s.screen.cycleAspectRatio(this, ui.playerView, s.hud); s.quickButtons.updateDynamicLabels() }
    override fun onPlaylistClicked() = if (::player.isInitialized) s.playlist.showPlaylistDialog(player) else Unit
    override fun onAudioClicked() = s.dialogs?.showAudioTrackDialog(isMuted) { isMuted = !isMuted; if (::player.isInitialized) player.volume = if (isMuted) 0f else 1f; s.hud.showQuickFeedback(if (isMuted) "Muted" else "Unmuted") } ?: Unit
    override fun onSubtitleClicked() = s.dialogs?.showSubtitleDialog(subtitleFontSizeSp, { subtitlePicker.launch(arrayOf("*/*")) }) { fs -> subtitleFontSizeSp = fs; ui.playerView.subtitleView?.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, fs) } ?: Unit
    override fun onDecoderClicked() {
        preferSoftwareDecoder = !preferSoftwareDecoder
        settingsPrefs.edit().putBoolean("sw_decoder", preferSoftwareDecoder).apply()
        s.quickButtons.updateDynamicLabels()
        s.hud.showQuickFeedback(if (preferSoftwareDecoder) "Decoder: SW" else "Decoder: HW")
        var currentPos = 0L
        var shouldPlay = true
        if (::player.isInitialized) {
            currentPos = player.currentPosition
            shouldPlay = player.playWhenReady
            s.resume.savePosition(player, s.playlist)
            player.release()
        }
        s.resume.resetLastCheckedIndex()
        initPlayer()
        if (::player.isInitialized) {
            player.volume = if (isMuted) 0f else 1f
            if (currentPos > 0L) {
                player.seekTo(s.playlist.index, currentPos)
            }
            player.playWhenReady = shouldPlay
        }
    }
    override fun onTimerClicked() = s.dialogs?.showSleepTimerDialog { m ->
        handler.removeCallbacks(sleepTimerRunnable)
        sleepTimerAtEndOfVideo = false
        if (m == -1) {
            sleepTimerAtEndOfVideo = true
            s.hud.showQuickFeedback("Sleep timer: at end of video")
        } else if (m > 0) {
            handler.postDelayed(sleepTimerRunnable, m * 60_000L)
            s.hud.showQuickFeedback("Sleep timer: $m mins")
        } else {
            s.hud.showQuickFeedback("Sleep timer: off")
        }
    } ?: Unit
    private val sleepTimerRunnable = Runnable { if (::player.isInitialized) { player.pause(); Toast.makeText(this, "Sleep timer: stopped", Toast.LENGTH_LONG).show() } }
    override fun onVideoDeletedSuccess() {
        if (!::player.isInitialized) return
        val curUri = s.playlist.getCurrentUri(); val curName = s.playlist.getCurrentName(); val curSize = s.playlist.getCurrentSize()
        if (curUri != null) {
            val sEd = seenPrefs.edit(); val rEd = resumePrefs.edit()
            VideoIdentity.getAllKeysForVideo("seen", curUri, curName, curSize).forEach { sEd.remove(it) }
            VideoIdentity.getAllKeysForVideo("pos", curUri, curName, curSize).forEach { rEd.remove(it) }
            VideoIdentity.getAllKeysForVideo("recent_time", curUri, curName, curSize).forEach { rEd.remove(it) }
            rEd.remove("recent_meta_$curUri")
            val parsedUri = android.net.Uri.parse(curUri)
            runCatching {
                if (DocumentsContract.isDocumentUri(this, parsedUri)) {
                    DocumentsContract.deleteDocument(contentResolver, parsedUri)
                } else {
                    contentResolver.delete(parsedUri, null, null)
                }
            }
            sEd.apply(); rEd.apply(); AppBackupManager.backupToStorageAsync(this)
        }
        val res = s.playlist.removeCurrent()
        if (res.isEmpty) { player.stop(); player.clearMediaItems(); finish() } else { player.removeMediaItem(res.removedIndex); player.seekTo(res.nextIndex, 0L); player.play(); onPlaylistIndexChanged() }
    }
    override fun onSkip80Clicked() = skipForward90s()
    private fun skipForward90s() { seekBy(90_000L); s.hud.showQuickFeedback("⏭ +90s (OP Skipped)"); s.controlsLock.scheduleHideControls() }

    override fun onToggleBtDoubleTap(): Boolean {
        val next = !isBtDoubleTapEnabled()
        settingsPrefs.edit().putBoolean("bt_double_tap_pause", next).apply()
        s.quickButtons.updateDynamicLabels()
        s.hud.showQuickFeedback(if (next) "BT Double Tap: ON" else "BT Double Tap: OFF")
        if (::player.isInitialized) mediaSessionManager?.updatePlaybackState(player.isPlaying, player.currentPosition)
        return next
    }
    override fun isBtDoubleTapEnabled(): Boolean = com.example.mxoffline.util.PreferenceHelper.safeGetBoolean(settingsPrefs, "bt_double_tap_pause", false)

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (mediaSessionManager?.handleMediaKeyEvent(event.keyCode) == true) {
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onButtonInteracted() = s.controlsLock.scheduleHideControls()
    override fun getCurrentUri() = s.playlist.getCurrentUri(); override fun getCurrentName() = s.playlist.getCurrentName()
    override fun getOrientationLabel() = s.screen.getOrientationLabel(); override fun getAspectLabel() = s.screen.getAspectLabel()
    override fun isSoftwareDecoder() = preferSoftwareDecoder
    override fun onCustomizeQuickButtons() = s.quickButtons.showCustomizeDialog()
    override fun onSpeedDialog() = onSpeedClicked(null); override fun onCycleAspectRatio() = onAspectClicked()
    override fun onCycleOrientation() = onOrientationClicked(); override fun onAudioTrackDialog() = onAudioClicked()
    override fun onSubtitleDialog() = onSubtitleClicked(); override fun onToggleDecoder() = onDecoderClicked(); override fun onSleepTimerDialog() = onTimerClicked()
    override fun onToggleBackgroundPlay(): Boolean { backgroundPlayEnabled = !backgroundPlayEnabled; settingsPrefs.edit().putBoolean("bg_play", backgroundPlayEnabled).apply(); s.hud.showQuickFeedback("Background Play: ${if (backgroundPlayEnabled) "On" else "Off"}"); return backgroundPlayEnabled }
    override fun onVideoInfoDialog() = s.dialogs?.showVideoInfoDialog(s.playlist.getCurrentName(), preferSoftwareDecoder) ?: Unit
    override fun onEnterPip() = if (::player.isInitialized) s.pip.enterPip(player, ui.playerView, { ui.overlayContainer.visibility = View.GONE; s.header.setVisible(false); ui.lockFloatingBtn.visibility = View.GONE }, { ui.overlayContainer.visibility = View.VISIBLE; s.header.setVisible(true) }) else Unit
    override fun onRestoreSettings() { if (::player.isInitialized) { s.screen.syncFromPreferences(this, player, ui.playerView); s.header.syncFromPreferences(); s.timeline.syncFromPreferences(); s.quickButtons.renderButtons(); onPlaylistIndexChanged() } }
}
