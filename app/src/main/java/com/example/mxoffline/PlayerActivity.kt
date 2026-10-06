package com.example.mxoffline

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Rational
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.mxoffline.player.PlayerDialogHelper
import com.example.mxoffline.player.PlayerGestureCallback
import com.example.mxoffline.player.PlayerGestureController
import com.example.mxoffline.player.PlayerHudController
import com.example.mxoffline.player.PlayerResumeManager
import com.example.mxoffline.util.TimeFormatter
import com.example.mxoffline.util.UiUtils

class PlayerActivity : ComponentActivity(), PlayerGestureCallback {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var overlayContainer: FrameLayout
    private lateinit var topBar: LinearLayout
    private lateinit var centerControls: LinearLayout
    private lateinit var bottomBar: LinearLayout

    private lateinit var titleView: TextView
    private lateinit var timeView: TextView
    private lateinit var remainingTimeView: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var speedButton: TextView
    private lateinit var resizeButton: TextView
    private lateinit var orientationButton: TextView
    private lateinit var decoderBadge: TextView
    private lateinit var lockFloatingBtn: TextView
    private lateinit var playPauseCenterBtn: TextView
    private lateinit var playPauseBottomBtn: TextView
    private lateinit var resumeBanner: LinearLayout
    private lateinit var resumeText: TextView

    companion object {
        private const val ACTION_PIP_PLAY = "com.example.mxoffline.PIP_PLAY"
        private const val ACTION_PIP_PAUSE = "com.example.mxoffline.PIP_PAUSE"
        private const val ACTION_PIP_REWIND = "com.example.mxoffline.PIP_REWIND"
        private const val ACTION_PIP_FORWARD = "com.example.mxoffline.PIP_FORWARD"
    }

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!::player.isInitialized) return
            when (intent?.action) {
                ACTION_PIP_PLAY -> {
                    player.play()
                    updatePipParams()
                }
                ACTION_PIP_PAUSE -> {
                    player.pause()
                    updatePipParams()
                }
                ACTION_PIP_REWIND -> {
                    val pos = (player.currentPosition - 10000L).coerceAtLeast(0L)
                    player.seekTo(pos)
                }
                ACTION_PIP_FORWARD -> {
                    val duration = player.duration
                    val target = player.currentPosition + 10000L
                    player.seekTo(if (duration > 0) target.coerceAtMost(duration) else target)
                }
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private val resumePrefs by lazy { getSharedPreferences("player_resume", MODE_PRIVATE) }
    private val settingsPrefs by lazy { getSharedPreferences("player_settings", MODE_PRIVATE) }

    private var uris = ArrayList<String>()
    private var names = ArrayList<String>()
    private var index = 0

    // State
    private var controlsVisible = true
    private var isScreenLocked = false
    private var isMuted = false
    private var showRemainingTime = false
    private var currentAspectModeIndex = 0
    private var currentOrientationMode = 0
    private var repeatMode = 0
    private var preferSoftwareDecoder = false
    private var backgroundPlayEnabled = false
    private var subtitleFontSizeSp = 18f
    private var isUserTrackingSeek = false
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var currentBoostPercent = 0

    // Modular Controllers
    private lateinit var resumeManager: PlayerResumeManager
    private lateinit var hudController: PlayerHudController
    private lateinit var gestureController: PlayerGestureController
    private lateinit var dialogHelper: PlayerDialogHelper

    private val subtitlePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                loadExternalSubtitle(uri)
            }.onFailure {
                Toast.makeText(this, "Could not load subtitle: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        parseIntentData()
        backgroundPlayEnabled = settingsPrefs.getBoolean("bg_play", false)
        preferSoftwareDecoder = settingsPrefs.getBoolean("sw_decoder", false)

        buildUi()
        initPlayer()
        initComponents()
        registerPipReceiver()
        hideSystemBars()
        scheduleHideControls()
        handler.post(progressTracker)
    }

    private fun parseIntentData() {
        uris = intent.getStringArrayListExtra("uris") ?: arrayListOf()
        names = intent.getStringArrayListExtra("names") ?: arrayListOf()
        index = intent.getIntExtra("index", 0).coerceIn(0, (uris.size - 1).coerceAtLeast(0))

        val dataUri = intent.data
        if (uris.isEmpty() && dataUri != null) {
            uris.add(dataUri.toString())
            val displayName = queryDisplayName(dataUri) ?: dataUri.lastPathSegment ?: "Video"
            names.add(displayName)
            index = 0
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && col >= 0) cursor.getString(col) else null
            }
        }.getOrNull()
    }

    private fun buildUi() {
        val dp = { v: Int -> UiUtils.dp(this, v) }
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        playerView = PlayerView(this).apply {
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            subtitleView?.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, subtitleFontSizeSp)
        }
        root.addView(playerView, FrameLayout.LayoutParams(-1, -1))

        // Center Seek HUD
        val seekHud = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(16), dp(20), dp(16))
            background = UiUtils.rounded(0xdd12151c.toInt(), 16, this@PlayerActivity)
            visibility = View.GONE
        }
        val seekIcon = TextView(this).apply { text = "⏩"; textSize = 28f; gravity = Gravity.CENTER; setTextColor(0xffffc400.toInt()) }
        val seekTimeText = TextView(this).apply { text = "00:00"; textSize = 24f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(Color.WHITE) }
        val seekDeltaText = TextView(this).apply { text = "[+00:00]"; textSize = 14f; gravity = Gravity.CENTER; setTextColor(0xffffc400.toInt()); setPadding(0, dp(2), 0, dp(8)) }
        val seekProgressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progressTintList = ColorStateList.valueOf(0xffffc400.toInt())
        }
        seekHud.addView(seekIcon)
        seekHud.addView(seekTimeText)
        seekHud.addView(seekDeltaText)
        seekHud.addView(seekProgressBar, LinearLayout.LayoutParams(dp(180), dp(8)))
        root.addView(seekHud, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))

        // Left Brightness HUD
        val brightnessHud = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = UiUtils.rounded(0xdd12151c.toInt(), 16, this@PlayerActivity)
            visibility = View.GONE
        }
        brightnessHud.addView(TextView(this).apply { text = "☀️"; textSize = 22f; gravity = Gravity.CENTER; setTextColor(0xffffc400.toInt()) })
        val brightnessText = TextView(this).apply { text = "50%"; textSize = 13f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(Color.WHITE); setPadding(0, dp(4), 0, dp(6)) }
        val brightnessBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progress = 50; progressTintList = ColorStateList.valueOf(0xffffc400.toInt())
        }
        brightnessHud.addView(brightnessText)
        brightnessHud.addView(brightnessBar, LinearLayout.LayoutParams(dp(70), dp(6)))
        root.addView(brightnessHud, FrameLayout.LayoutParams(-2, -2, Gravity.START or Gravity.CENTER_VERTICAL).apply { leftMargin = dp(28) })

        // Right Volume HUD
        val volumeHud = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = UiUtils.rounded(0xdd12151c.toInt(), 16, this@PlayerActivity)
            visibility = View.GONE
        }
        val volumeIcon = TextView(this).apply { text = "🔊"; textSize = 22f; gravity = Gravity.CENTER; setTextColor(0xffffc400.toInt()) }
        val volumeText = TextView(this).apply { text = "100%"; textSize = 13f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(Color.WHITE); setPadding(0, dp(4), 0, dp(6)) }
        val volumeBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 200; progress = 100; progressTintList = ColorStateList.valueOf(0xffffc400.toInt())
        }
        volumeHud.addView(volumeIcon)
        volumeHud.addView(volumeText)
        volumeHud.addView(volumeBar, LinearLayout.LayoutParams(dp(70), dp(6)))
        root.addView(volumeHud, FrameLayout.LayoutParams(-2, -2, Gravity.END or Gravity.CENTER_VERTICAL).apply { rightMargin = dp(28) })

        // Feedback and Speed HUDs
        val quickFeedbackHud = TextView(this).apply {
            textSize = 20f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = UiUtils.rounded(0xbb000000.toInt(), 24, this@PlayerActivity); setPadding(dp(22), dp(12), dp(22), dp(12)); visibility = View.GONE
        }
        root.addView(quickFeedbackHud, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))

        val centerSpeedHud = TextView(this).apply {
            textSize = 16f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(0xffffc400.toInt())
            background = UiUtils.rounded(0xdd12151c.toInt(), 16, this@PlayerActivity); setPadding(dp(18), dp(8), dp(18), dp(8)); visibility = View.GONE
        }
        root.addView(centerSpeedHud, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(28) })

        // Floating Unlock Button
        lockFloatingBtn = TextView(this).apply {
            text = "🔒"; textSize = 24f; gravity = Gravity.CENTER
            background = UiUtils.rounded(0xee222631.toInt(), 28, this@PlayerActivity); visibility = View.GONE
            setOnClickListener { unlockScreen() }
        }
        root.addView(lockFloatingBtn, FrameLayout.LayoutParams(dp(56), dp(56), Gravity.START or Gravity.CENTER_VERTICAL).apply { leftMargin = dp(24) })

        // Resume Banner
        resumeBanner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(10), dp(16), dp(10))
            background = UiUtils.rounded(0xee1a1d26.toInt(), 20, this@PlayerActivity); visibility = View.GONE
        }
        resumeText = TextView(this).apply { text = "Resumed from 00:00"; textSize = 13f; setTextColor(0xfff0f1f3.toInt()) }
        val restartBtn = TextView(this).apply {
            text = "Restart"; textSize = 13f; typeface = Typeface.DEFAULT_BOLD; setTextColor(0xffffc400.toInt()); setPadding(dp(14), 0, 0, 0)
            setOnClickListener {
                player.seekTo(0)
                resumeManager.clearPosition(uris, index)
            }
        }
        resumeBanner.addView(resumeText)
        resumeBanner.addView(restartBtn)
        root.addView(resumeBanner, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(110) })

        // Controls Overlay
        overlayContainer = FrameLayout(this).apply { setBackgroundColor(Color.TRANSPARENT) }

        // Top Bar
        topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xdd000000.toInt(), 0x77000000.toInt(), Color.TRANSPARENT))
        }
        val backBtn = iconButton("‹", 26f) { finish() }
        titleView = TextView(this).apply {
            text = "Video"; textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE); maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(dp(8), 0, dp(8), 0)
        }
        decoderBadge = TextView(this).apply {
            text = if (preferSoftwareDecoder) "SW" else "HW"; textSize = 12f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setTextColor(0xffffc400.toInt()); background = UiUtils.rounded(0x33ffc400.toInt(), 8, this@PlayerActivity); setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnClickListener { toggleDecoderMode() }
        }
        val audioTrackBtn = iconButton("🎵", 17f) {
            dialogHelper.showAudioTrackDialog(isMuted) {
                isMuted = !isMuted
                player.volume = if (isMuted) 0f else 1f
                hudController.showQuickFeedback(if (isMuted) "Muted" else "Unmuted")
            }
        }
        val subtitleBtn = iconButton("💬", 17f) {
            dialogHelper.showSubtitleDialog(
                currentFontSizeSp = subtitleFontSizeSp,
                onExternalSubtitleClicked = { subtitlePicker.launch(arrayOf("*/*")) },
                onFontSizeChanged = { size ->
                    subtitleFontSizeSp = size
                    playerView.subtitleView?.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, subtitleFontSizeSp)
                }
            )
        }
        val moreMenuBtn = iconButton("⋮", 22f) { showMoreOptionsMenu() }

        topBar.addView(backBtn, LinearLayout.LayoutParams(dp(44), dp(44)))
        topBar.addView(titleView, LinearLayout.LayoutParams(0, -2, 1f))
        topBar.addView(decoderBadge, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(6) })
        topBar.addView(audioTrackBtn, LinearLayout.LayoutParams(dp(44), dp(44)))
        topBar.addView(subtitleBtn, LinearLayout.LayoutParams(dp(44), dp(44)))
        topBar.addView(moreMenuBtn, LinearLayout.LayoutParams(dp(44), dp(44)))
        overlayContainer.addView(topBar, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        // Center Controls
        centerControls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val rewind10Btn = roundButton("−10", 15f, dp(58), 0x44ffffff.toInt()) {
            seekBy(-10_000)
            hudController.showQuickFeedback("⟲ 10s")
        }
        playPauseCenterBtn = roundButton("Ⅱ", 24f, dp(72), 0xffffc400.toInt(), textColor = 0xff101114.toInt()) { togglePlay() }
        val forward10Btn = roundButton("+10", 15f, dp(58), 0x44ffffff.toInt()) {
            seekBy(10_000)
            hudController.showQuickFeedback("10s ⟳")
        }
        centerControls.addView(rewind10Btn, LinearLayout.LayoutParams(dp(62), dp(62)).apply { rightMargin = dp(24) })
        centerControls.addView(playPauseCenterBtn, LinearLayout.LayoutParams(dp(72), dp(72)))
        centerControls.addView(forward10Btn, LinearLayout.LayoutParams(dp(62), dp(62)).apply { leftMargin = dp(24) })
        overlayContainer.addView(centerControls, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))

        // Bottom Bar
        bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(0xee000000.toInt(), 0x77000000.toInt(), Color.TRANSPARENT))
        }

        val timelineRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        timeView = TextView(this).apply { text = "00:00"; textSize = 13f; setTextColor(Color.WHITE); setPadding(0, 0, dp(8), 0) }
        seekBar = SeekBar(this).apply {
            max = 1000; progressTintList = ColorStateList.valueOf(0xffffc400.toInt()); thumbTintList = ColorStateList.valueOf(0xffffc400.toInt())
        }
        remainingTimeView = TextView(this).apply {
            text = "00:00"; textSize = 13f; setTextColor(0xffbbbec6.toInt()); setPadding(dp(8), 0, 0, 0)
            setOnClickListener { showRemainingTime = !showRemainingTime; updateTimeDisplay() }
        }
        timelineRow.addView(timeView)
        timelineRow.addView(seekBar, LinearLayout.LayoutParams(0, dp(40), 1f))
        timelineRow.addView(remainingTimeView)
        bottomBar.addView(timelineRow)

        val actionsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val lockBtn = actionTextButton("🔒") { lockScreen() }
        val prevBtn = actionTextButton("|‹") { previousVideo() }
        playPauseBottomBtn = actionTextButton("Ⅱ") { togglePlay() }
        val nextBtn = actionTextButton("›|") { nextVideo() }
        speedButton = actionTextButton("1.0×") {
            dialogHelper.showSpeedDialog(speedButton) { speed ->
                speedButton.text = TimeFormatter.formatSpeed(speed)
            }
        }
        resizeButton = actionTextButton("Fit") { cycleResizeMode() }
        orientationButton = actionTextButton("🔄") { cycleOrientation() }
        val playlistBtn = actionTextButton("📋") { showPlaylist() }
        val pipBtn = actionTextButton("PiP") { enterPipMode() }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) pipBtn.visibility = View.GONE

        val actionLp = { LinearLayout.LayoutParams(0, dp(44), 1f) }
        actionsRow.addView(lockBtn, actionLp())
        actionsRow.addView(prevBtn, actionLp())
        actionsRow.addView(playPauseBottomBtn, actionLp())
        actionsRow.addView(nextBtn, actionLp())
        actionsRow.addView(speedButton, actionLp())
        actionsRow.addView(resizeButton, actionLp())
        actionsRow.addView(orientationButton, actionLp())
        actionsRow.addView(playlistBtn, actionLp())
        actionsRow.addView(pipBtn, actionLp())
        bottomBar.addView(actionsRow)

        overlayContainer.addView(bottomBar, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        root.addView(overlayContainer, FrameLayout.LayoutParams(-1, -1))

        // Set touch listeners
        playerView.setOnTouchListener { _, event -> gestureController.handleTouchEvent(event) }
        overlayContainer.setOnTouchListener { _, event -> gestureController.handleTouchEvent(event) }
        root.setOnTouchListener { _, event -> gestureController.handleTouchEvent(event) }

        setContentView(root)

        // Initialize HUD Controller
        hudController = PlayerHudController(
            handler = handler,
            seekHud = seekHud,
            seekIcon = seekIcon,
            seekTimeText = seekTimeText,
            seekDeltaText = seekDeltaText,
            seekProgressBar = seekProgressBar,
            brightnessHud = brightnessHud,
            brightnessText = brightnessText,
            brightnessBar = brightnessBar,
            volumeHud = volumeHud,
            volumeIcon = volumeIcon,
            volumeText = volumeText,
            volumeBar = volumeBar,
            centerSpeedHud = centerSpeedHud,
            quickFeedbackHud = quickFeedbackHud
        )

        // Initialize Resume Manager
        resumeManager = PlayerResumeManager(resumePrefs, handler, resumeBanner, resumeText)

        // SeekBar Tracking
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && ::player.isInitialized && player.duration > 0) {
                    val targetMs = (player.duration * progress) / 1000
                    timeView.text = TimeFormatter.formatTime(targetMs)
                }
            }
            override fun onStartTrackingTouch(bar: SeekBar?) {
                isUserTrackingSeek = true
                handler.removeCallbacks(hideControlsRunnable)
            }
            override fun onStopTrackingTouch(bar: SeekBar?) {
                isUserTrackingSeek = false
                if (::player.isInitialized && player.duration > 0 && bar != null) {
                    val targetMs = (player.duration * bar.progress) / 1000
                    player.seekTo(targetMs)
                }
                scheduleHideControls()
            }
        })
    }

    private fun initComponents() {
        gestureController = PlayerGestureController(
            context = this,
            window = window,
            handler = handler,
            player = player,
            audioManager = audioManager,
            hudController = hudController,
            callback = this
        )
        dialogHelper = PlayerDialogHelper(this, player)
    }

    private fun iconButton(text: String, sizeSp: Float, onClick: () -> Unit) = TextView(this).apply {
        this.text = text; textSize = sizeSp; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
        setOnClickListener { onClick(); scheduleHideControls() }
    }

    private fun roundButton(text: String, sizeSp: Float, sizeDp: Int, bgColor: Int, textColor: Int = Color.WHITE, onClick: () -> Unit) = TextView(this).apply {
        this.text = text; textSize = sizeSp; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        setTextColor(textColor); background = UiUtils.rounded(bgColor, sizeDp / 2, this@PlayerActivity)
        setOnClickListener { onClick(); scheduleHideControls() }
    }

    private fun actionTextButton(text: String, onClick: () -> Unit) = TextView(this).apply {
        this.text = text; textSize = 14f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
        setOnClickListener { onClick(); scheduleHideControls() }
    }

    // Player Lifecycle
    private fun initPlayer() {
        val renderersFactory = DefaultRenderersFactory(this).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            if (preferSoftwareDecoder) {
                setMediaCodecSelector { mimeType, requiresSecure, requiresTunneling ->
                    val decoders = androidx.media3.exoplayer.mediacodec.MediaCodecSelector.DEFAULT
                        .getDecoderInfos(mimeType, requiresSecure, requiresTunneling)
                    decoders.sortedByDescending { it.name.startsWith("c2.android", true) || it.name.startsWith("omx.google", true) }
                }
            }
        }

        player = ExoPlayer.Builder(this, renderersFactory).build().also { p ->
            playerView.player = p
            p.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        initAudioEffects()
                        resumeManager.checkAndApplyResume(p, uris, index)
                    } else if (state == Player.STATE_ENDED) {
                        playPauseCenterBtn.text = "▶"
                        playPauseBottomBtn.text = "▶"
                        when (repeatMode) {
                            1 -> nextVideo()
                            2 -> { p.seekTo(0); p.play() }
                            else -> {
                                if (index < uris.lastIndex) nextVideo()
                                else {
                                    p.pause()
                                    resumeManager.clearPosition(uris, index)
                                }
                            }
                        }
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    val symbol = if (isPlaying) "Ⅱ" else "▶"
                    playPauseCenterBtn.text = symbol
                    playPauseBottomBtn.text = symbol
                    if (isPlaying) scheduleHideControls() else handler.removeCallbacks(hideControlsRunnable)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode) {
                        updatePipParams()
                    }
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    val currentIdx = p.currentMediaItemIndex
                    if (currentIdx in uris.indices) {
                        index = currentIdx
                        updateTitle()
                    }
                }
            })

            if (uris.isNotEmpty()) {
                p.setMediaItems(uris.map { MediaItem.fromUri(it) }, index, 0)
                p.prepare()
                p.playWhenReady = true
            }
        }

        updateTitle()
        applyOrientation()
    }

    private fun initAudioEffects() {
        val sessionId = player.audioSessionId
        if (sessionId != C.AUDIO_SESSION_ID_UNSET) {
            runCatching {
                loudnessEnhancer?.release()
                loudnessEnhancer = LoudnessEnhancer(sessionId).apply { enabled = true }
                onApplyAudioBoost(currentBoostPercent)
            }
        }
    }

    override fun onApplyAudioBoost(boostPercent: Int) {
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

    private fun updateTitle() {
        titleView.text = names.getOrNull(index) ?: "Video"
    }

    private val progressTracker = object : Runnable {
        override fun run() {
            if (::player.isInitialized) {
                val d = player.duration.coerceAtLeast(0)
                val p = player.currentPosition.coerceAtLeast(0)
                if (!isUserTrackingSeek) {
                    seekBar.progress = if (d > 0) ((p * 1000) / d).toInt() else 0
                    timeView.text = TimeFormatter.formatTime(p)
                }
                seekBar.secondaryProgress = if (d > 0) ((player.bufferedPosition * 1000) / d).toInt() else 0
                updateTimeDisplay()

                if (player.isPlaying) resumeManager.savePosition(player, uris, index)
                handler.postDelayed(this, 1000)
            }
        }
    }

    private fun updateTimeDisplay() {
        if (!::player.isInitialized) return
        val d = player.duration.coerceAtLeast(0)
        val p = player.currentPosition.coerceAtLeast(0)
        if (showRemainingTime) {
            val remaining = (d - p).coerceAtLeast(0)
            remainingTimeView.text = "-${TimeFormatter.formatTime(remaining)}"
        } else {
            remainingTimeView.text = TimeFormatter.formatTime(d)
        }
    }

    // PlayerGestureCallback Implementations
    override fun isLocked(): Boolean = isScreenLocked

    override fun onSingleTap() {
        toggleControls()
    }

    override fun onSeekBy(deltaMs: Long, isForward: Boolean) {
        seekBy(deltaMs)
    }

    override fun onTogglePlay() {
        togglePlay()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureController.handleTouchEvent(event) || super.onTouchEvent(event)
    }

    // Control Actions
    private fun togglePlay() {
        if (!::player.isInitialized) return
        if (player.playbackState == Player.STATE_ENDED) {
            player.seekTo(0)
            player.play()
            return
        }
        if (player.playWhenReady) {
            player.pause()
        } else {
            player.play()
        }
    }

    private fun seekBy(deltaMs: Long) {
        if (!::player.isInitialized) return
        val target = (player.currentPosition + deltaMs).coerceIn(0L, player.duration.coerceAtLeast(0))
        player.seekTo(target)
    }

    private fun nextVideo() {
        if (!::player.isInitialized || uris.isEmpty()) return
        if (index < uris.lastIndex) {
            index++
            player.seekTo(index, 0)
            updateTitle()
        } else if (repeatMode == 1) {
            index = 0
            player.seekTo(0, 0)
            updateTitle()
        } else {
            Toast.makeText(this, "End of playlist", Toast.LENGTH_SHORT).show()
        }
    }

    private fun previousVideo() {
        if (!::player.isInitialized || uris.isEmpty()) return
        if (player.currentPosition > 3500) {
            player.seekTo(0)
        } else if (index > 0) {
            index--
            player.seekTo(index, 0)
            updateTitle()
        }
    }

    private fun toggleControls() {
        controlsVisible = !controlsVisible
        overlayContainer.visibility = if (controlsVisible) View.VISIBLE else View.GONE
        if (controlsVisible) {
            hideSystemBars()
            scheduleHideControls()
        } else {
            handler.removeCallbacks(hideControlsRunnable)
        }
    }

    private val hideControlsRunnable = Runnable {
        if (::player.isInitialized && player.isPlaying && controlsVisible && !isScreenLocked) {
            overlayContainer.visibility = View.GONE
            controlsVisible = false
        }
    }

    private fun scheduleHideControls() {
        handler.removeCallbacks(hideControlsRunnable)
        handler.postDelayed(hideControlsRunnable, 4500)
    }

    private fun lockScreen() {
        isScreenLocked = true
        controlsVisible = false
        overlayContainer.visibility = View.GONE
        lockFloatingBtn.visibility = View.VISIBLE
        hudController.showQuickFeedback("Screen Locked")
    }

    private fun unlockScreen() {
        isScreenLocked = false
        lockFloatingBtn.visibility = View.GONE
        controlsVisible = true
        overlayContainer.visibility = View.VISIBLE
        scheduleHideControls()
        hudController.showQuickFeedback("Screen Unlocked")
    }

    private fun cycleResizeMode() {
        val modes = listOf(
            "Fit" to AspectRatioFrameLayout.RESIZE_MODE_FIT,
            "Fill" to AspectRatioFrameLayout.RESIZE_MODE_FILL,
            "Zoom" to AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        )
        currentAspectModeIndex = (currentAspectModeIndex + 1) % modes.size
        val (label, mode) = modes[currentAspectModeIndex]
        playerView.resizeMode = mode
        resizeButton.text = label
        hudController.showQuickFeedback("Screen: $label")
    }

    private fun cycleOrientation() {
        currentOrientationMode = (currentOrientationMode + 1) % 4
        applyOrientation()
    }

    private fun applyOrientation() {
        when (currentOrientationMode) {
            0 -> {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
                hudController.showQuickFeedback("Orientation: Auto")
            }
            1 -> {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                hudController.showQuickFeedback("Orientation: Landscape")
            }
            2 -> {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                hudController.showQuickFeedback("Orientation: Portrait")
            }
            3 -> {
                val format = player.videoFormat
                val isWide = format != null && format.width > format.height
                requestedOrientation = if (isWide) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                hudController.showQuickFeedback("Orientation: Match Video")
            }
        }
    }

    private fun toggleDecoderMode() {
        preferSoftwareDecoder = !preferSoftwareDecoder
        settingsPrefs.edit().putBoolean("sw_decoder", preferSoftwareDecoder).apply()
        decoderBadge.text = if (preferSoftwareDecoder) "SW" else "HW"
        Toast.makeText(this, "Decoder set to ${decoderBadge.text}. Restarting playback...", Toast.LENGTH_SHORT).show()
        val pos = player.currentPosition
        player.release()
        initPlayer()
        initComponents()
        player.seekTo(index, pos)
    }

    private fun loadExternalSubtitle(uri: Uri) {
        val displayName = queryDisplayName(uri) ?: "sub.srt"
        val mimeType = when {
            displayName.endsWith(".vtt", true) -> MimeTypes.TEXT_VTT
            displayName.endsWith(".ssa", true) || displayName.endsWith(".ass", true) -> MimeTypes.TEXT_SSA
            else -> MimeTypes.APPLICATION_SUBRIP
        }
        val subConfig = MediaItem.SubtitleConfiguration.Builder(uri)
            .setMimeType(mimeType)
            .setLanguage("en")
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()

        val currentItem = player.currentMediaItem ?: return
        val currentSubs = currentItem.localConfiguration?.subtitleConfigurations.orEmpty()
        val newItem = currentItem.buildUpon()
            .setSubtitleConfigurations(currentSubs + subConfig)
            .build()

        val pos = player.currentPosition
        val isPlaying = player.playWhenReady
        player.setMediaItem(newItem, pos)
        player.playWhenReady = isPlaying
        Toast.makeText(this, "Subtitle added: $displayName", Toast.LENGTH_SHORT).show()
    }

    private fun showPlaylist() {
        dialogHelper.showPlaylistDialog(
            names = names,
            currentIndex = index,
            repeatMode = repeatMode,
            onVideoSelected = { which ->
                index = which
                player.seekTo(index, 0)
                updateTitle()
            },
            onCycleRepeatMode = {
                repeatMode = (repeatMode + 1) % 3
                val label = when (repeatMode) { 1 -> "Repeat All"; 2 -> "Repeat One"; else -> "Off" }
                hudController.showQuickFeedback("Loop: $label")
            }
        )
    }

    private fun showMoreOptionsMenu() {
        val options = arrayOf(
            "Playback Speed",
            "Screen Resize (Fit/Fill/Zoom)",
            "Screen Orientation",
            "Sleep Timer",
            "Background Play: ${if (backgroundPlayEnabled) "On" else "Off"}",
            "Video Information",
            "Picture-in-Picture"
        )
        android.app.AlertDialog.Builder(this)
            .setTitle("Player Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> dialogHelper.showSpeedDialog(speedButton) { s -> speedButton.text = TimeFormatter.formatSpeed(s) }
                    1 -> cycleResizeMode()
                    2 -> cycleOrientation()
                    3 -> dialogHelper.showSleepTimerDialog { min ->
                        handler.removeCallbacks(sleepTimerRunnable)
                        if (min > 0) {
                            handler.postDelayed(sleepTimerRunnable, min * 60 * 1000L)
                            hudController.showQuickFeedback("Sleep timer set for $min mins")
                        } else {
                            hudController.showQuickFeedback("Sleep timer disabled")
                        }
                    }
                    4 -> {
                        backgroundPlayEnabled = !backgroundPlayEnabled
                        settingsPrefs.edit().putBoolean("bg_play", backgroundPlayEnabled).apply()
                        hudController.showQuickFeedback("Background Play: ${if (backgroundPlayEnabled) "On" else "Off"}")
                    }
                    5 -> dialogHelper.showVideoInfoDialog(names.getOrNull(index) ?: "Video", preferSoftwareDecoder)
                    6 -> enterPipMode()
                }
            }
            .show()
    }

    private val sleepTimerRunnable = Runnable {
        if (::player.isInitialized) {
            player.pause()
            Toast.makeText(this, "Sleep timer: Playback stopped", Toast.LENGTH_LONG).show()
        }
    }

    private fun registerPipReceiver() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val filter = IntentFilter().apply {
                addAction(ACTION_PIP_PLAY)
                addAction(ACTION_PIP_PAUSE)
                addAction(ACTION_PIP_REWIND)
                addAction(ACTION_PIP_FORWARD)
            }
            ContextCompat.registerReceiver(this, pipReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }
    }

    private fun unregisterPipReceiver() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { unregisterReceiver(pipReceiver) }
        }
    }

    private fun buildPipParams(): PictureInPictureParams? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val format = if (::player.isInitialized) player.videoFormat else null
        val rational = if (format != null && format.width > 0 && format.height > 0) {
            val ratio = format.width.toFloat() / format.height.toFloat()
            if (ratio in 0.42f..2.38f) Rational(format.width, format.height) else Rational(16, 9)
        } else Rational(16, 9)

        val builder = PictureInPictureParams.Builder().setAspectRatio(rational)

        val isPlaying = ::player.isInitialized && player.isPlaying
        val actions = ArrayList<RemoteAction>()

        // 1. Rewind 10s
        val rewindIntent = PendingIntent.getBroadcast(
            this, 1, Intent(ACTION_PIP_REWIND).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        actions.add(
            RemoteAction(
                Icon.createWithResource(this, android.R.drawable.ic_media_rew),
                "Rewind",
                "Rewind 10 seconds",
                rewindIntent
            )
        )

        // 2. Play / Pause
        if (isPlaying) {
            val pauseIntent = PendingIntent.getBroadcast(
                this, 2, Intent(ACTION_PIP_PAUSE).setPackage(packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            actions.add(
                RemoteAction(
                    Icon.createWithResource(this, android.R.drawable.ic_media_pause),
                    "Pause",
                    "Pause playback",
                    pauseIntent
                )
            )
        } else {
            val playIntent = PendingIntent.getBroadcast(
                this, 3, Intent(ACTION_PIP_PLAY).setPackage(packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            actions.add(
                RemoteAction(
                    Icon.createWithResource(this, android.R.drawable.ic_media_play),
                    "Play",
                    "Play video",
                    playIntent
                )
            )
        }

        // 3. Forward 10s
        val forwardIntent = PendingIntent.getBroadcast(
            this, 4, Intent(ACTION_PIP_FORWARD).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        actions.add(
            RemoteAction(
                Icon.createWithResource(this, android.R.drawable.ic_media_ff),
                "Forward",
                "Forward 10 seconds",
                forwardIntent
            )
        )

        builder.setActions(actions)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(true)
            builder.setSeamlessResizeEnabled(true)
        }

        return builder.build()
    }

    private fun updatePipParams() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                val params = buildPipParams() ?: return
                setPictureInPictureParams(params)
            }
        }
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Toast.makeText(this, "PiP requires Android 8.0+", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            overlayContainer.visibility = View.GONE
            if (::player.isInitialized && !player.isPlaying && player.playbackState == Player.STATE_READY) {
                player.play()
            }
            val params = buildPipParams()
            if (params != null) {
                enterPictureInPictureMode(params)
            }
        }.onFailure {
            overlayContainer.visibility = View.VISIBLE
            Toast.makeText(this, "Cannot enter PiP: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode) return
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode) {
            updatePipParams()
        }
    }

    override fun onPause() {
        super.onPause()
        if (::player.isInitialized) {
            resumeManager.savePosition(player, uris, index)
            val inPip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode
            if (!inPip && !backgroundPlayEnabled) {
                player.pause()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (::player.isInitialized) {
            resumeManager.savePosition(player, uris, index)
            val inPip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode
            if (!inPip && !backgroundPlayEnabled) {
                player.pause()
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && ::player.isInitialized) {
            enterPipMode()
        }
    }

    override fun onPictureInPictureModeChanged(isInPip: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPip, newConfig)
        if (isInPip) {
            overlayContainer.visibility = View.GONE
            if (::player.isInitialized && !player.isPlaying && player.playbackState == Player.STATE_READY) {
                player.play()
            }
            updatePipParams()
        } else {
            hideSystemBars()
            if (controlsVisible) overlayContainer.visibility = View.VISIBLE
            scheduleHideControls()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        unregisterPipReceiver()
        if (::player.isInitialized) {
            resumeManager.savePosition(player, uris, index)
            runCatching { loudnessEnhancer?.release() }
            playerView.player = null
            player.release()
        }
        super.onDestroy()
    }
}
