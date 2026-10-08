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
import android.graphics.Rect
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
import android.app.RecoverableSecurityException
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.result.IntentSenderRequest
import java.io.File
import android.util.Rational
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.app.AlertDialog
import android.os.BatteryManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
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
    private lateinit var bottomBar: LinearLayout

    private lateinit var titleView: TextView
    private lateinit var timeView: TextView
    private lateinit var remainingTimeView: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var lockFloatingBtn: TextView
    private lateinit var playPauseBottomBtn: TextView
    private lateinit var markDoneBtn: TextView
    private lateinit var resumeBanner: LinearLayout
    private lateinit var resumeText: TextView

    // Top Header & Status info
    private lateinit var persistentStatusHeader: LinearLayout
    private lateinit var batteryText: TextView
    private lateinit var clockText: TextView
    private lateinit var topTimeStatusView: TextView
    private var topTimeStatusMode = 1 // 1: "00:00 / -00:00" (time done / time remaining), 0: "00:00 / 00:00" (time done / total time)
    private lateinit var quickButtonsLayout: LinearLayout
    private var speedCircularBtn: TextView? = null
    private var decoderCircularBtn: TextView? = null
    private var aspectCircularBtn: TextView? = null
    private var orientationCircularBtn: TextView? = null
    private var deleteCircularBtn: TextView? = null
    private var deleteTapCount = 0
    private val deleteResetRunnable = Runnable { resetDeleteTaps() }
    private var pendingDeleteIndex: Int = -1

    private val deleteIntentLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && pendingDeleteIndex in uris.indices) {
            onVideoDeletedSuccess(pendingDeleteIndex)
        } else {
            Toast.makeText(this, "Delete cancelled", Toast.LENGTH_SHORT).show()
        }
        pendingDeleteIndex = -1
    }

    private var currentPlaybackSpeed = 1.0f

    private val allQuickButtons = listOf(
        "speed" to "Playback Speed",
        "orientation" to "Orientation Lock",
        "aspect" to "Fit / Aspect Ratio",
        "playlist" to "In-Player Playlist",
        "delete" to "Delete Video (4 Taps)",
        "audio" to "Audio Tracks",
        "subtitle" to "Subtitles",
        "decoder" to "HW / SW Decoder",
        "timer" to "Sleep Timer"
    )

    companion object {
        private const val ACTION_PIP_PLAY = "com.example.mxoffline.PIP_PLAY"
        private const val ACTION_PIP_PAUSE = "com.example.mxoffline.PIP_PAUSE"
        private const val ACTION_PIP_PREV = "com.example.mxoffline.PIP_PREV"
        private const val ACTION_PIP_NEXT = "com.example.mxoffline.PIP_NEXT"
    }

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!::player.isInitialized) return
            when (intent?.action) {
                ACTION_PIP_PLAY -> {
                    if (player.playerError != null || player.playbackState == Player.STATE_IDLE) {
                        player.prepare()
                    }
                    player.play()
                    updatePipParams()
                }
                ACTION_PIP_PAUSE -> {
                    player.pause()
                    updatePipParams()
                }
                ACTION_PIP_PREV -> {
                    previousVideo()
                    updatePipParams()
                }
                ACTION_PIP_NEXT -> {
                    nextVideo()
                    updatePipParams()
                }
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private val resumePrefs by lazy { getSharedPreferences("player_resume", MODE_PRIVATE) }
    private val settingsPrefs by lazy { getSharedPreferences("player_settings", MODE_PRIVATE) }
    private val seenPrefs by lazy { getSharedPreferences("player_seen", MODE_PRIVATE) }

    private var uris = ArrayList<String>()
    private var names = ArrayList<String>()
    private var index = 0

    // State
    private var controlsVisible = true
    private var isScreenLocked = false
    private var isMuted = false
    private var showRemainingTime = true
    private var currentAspectModeIndex = 0
    private var currentOrientationMode = 1
    private var repeatMode = 0
    private var preferSoftwareDecoder = false
    private var backgroundPlayEnabled = false
    private var enteredPipMode = false
    private var pipDismissRunnable: Runnable? = null
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        parseIntentData()
        backgroundPlayEnabled = settingsPrefs.getBoolean("bg_play", false)
        preferSoftwareDecoder = settingsPrefs.getBoolean("sw_decoder", false)
        currentPlaybackSpeed = settingsPrefs.getFloat("playback_speed", 1.0f)
        currentOrientationMode = settingsPrefs.getInt("orientation_mode", 1)
        currentAspectModeIndex = settingsPrefs.getInt("aspect_mode", 0)
        showRemainingTime = settingsPrefs.getBoolean("show_remaining_time", true)
        topTimeStatusMode = settingsPrefs.getInt("top_time_mode", 1)

        buildUi()
        initPlayer()
        initComponents()
        registerPipReceiver()
        setupBackNavigation()
        hideSystemBars()
        scheduleHideControls()
        handler.post(progressTracker)
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isScreenLocked) {
                    showLockTemporarily()
                } else if (controlsVisible) {
                    toggleControls()
                } else {
                    finish()
                }
            }
        })
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        hideSystemBars()
        updateStatusHeader()
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

        val aspectModes = listOf(
            AspectRatioFrameLayout.RESIZE_MODE_FIT,
            AspectRatioFrameLayout.RESIZE_MODE_FILL,
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        )
        val initialResizeMode = aspectModes.getOrElse(currentAspectModeIndex) { AspectRatioFrameLayout.RESIZE_MODE_FIT }

        playerView = PlayerView(this).apply {
            useController = false
            resizeMode = initialResizeMode
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

        // Floating Unlock Button (At the top-left, not middle of screen)
        lockFloatingBtn = TextView(this).apply {
            text = "🔒"; textSize = 22f; gravity = Gravity.CENTER
            includeFontPadding = false
            background = GradientDrawable().apply {
                setColor(0xee222631.toInt())
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), 0x55ffffff.toInt())
            }
            visibility = View.GONE
            setOnClickListener { unlockScreen() }
        }

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

        // Persistent Top Status Header (Always visible: Time done/remaining on left, Battery & Clock on right)
        persistentStatusHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), 0, dp(20), 0)
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x55000000.toInt(), Color.TRANSPARENT)
            )
        }

        topTimeStatusView = TextView(this).apply {
            text = "00:00 / 00:00"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setSingleLine(true)
            includeFontPadding = false
            setShadowLayer(4f, 1f, 1f, 0xdd000000.toInt())
            setPadding(dp(4), dp(2), dp(8), dp(2))
            setOnClickListener {
                topTimeStatusMode = (topTimeStatusMode + 1) % 2
                settingsPrefs.edit().putInt("top_time_mode", topTimeStatusMode).apply()
                updateStatusHeader()
                hudController.showQuickFeedback(if (topTimeStatusMode == 1) "Time: Done / Remaining" else "Time: Done / Total")
            }
        }

        val statusSpacer = View(this)

        batteryText = TextView(this).apply {
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setSingleLine(true)
            includeFontPadding = false
            setShadowLayer(4f, 1f, 1f, 0xdd000000.toInt())
            setPadding(dp(4), dp(2), dp(6), dp(2))
            gravity = Gravity.CENTER_VERTICAL
        }

        clockText = TextView(this).apply {
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setSingleLine(true)
            includeFontPadding = false
            setShadowLayer(4f, 1f, 1f, 0xdd000000.toInt())
            setPadding(dp(4), dp(2), dp(4), dp(2))
            gravity = Gravity.CENTER_VERTICAL
        }

        persistentStatusHeader.addView(statusSpacer, LinearLayout.LayoutParams(0, dp(1), 1f))
        persistentStatusHeader.addView(topTimeStatusView, LinearLayout.LayoutParams(-2, -2))
        persistentStatusHeader.addView(batteryText, LinearLayout.LayoutParams(-2, -2))
        persistentStatusHeader.addView(clockText, LinearLayout.LayoutParams(-2, -2))

        // Top Bar (Controls: Back, Title, Hamburger menu, and Circular quick action buttons)
        topBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(32), dp(12), dp(6))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xee000000.toInt(), 0x77000000.toInt(), Color.TRANSPARENT)
            )
        }

        // Top Controls Row: [‹] [Title] [☰]
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val backBtn = iconButton("‹", 26f) { finish() }

        titleView = TextView(this).apply {
            text = "Video"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(8), 0, dp(8), 0)
        }

        val hamburgerBtn = iconButton("☰", 22f) { showHamburgerMenu() }

        headerRow.addView(backBtn, LinearLayout.LayoutParams(dp(40), dp(40)))
        headerRow.addView(titleView, LinearLayout.LayoutParams(0, -2, 1f))
        headerRow.addView(hamburgerBtn, LinearLayout.LayoutParams(dp(40), dp(40)))
        topBar.addView(headerRow, LinearLayout.LayoutParams(-1, -2))

        // Top Circular Quick Action Buttons Row
        val quickButtonsScrollView = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setPadding(dp(2), dp(6), dp(2), dp(2))
        }
        quickButtonsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        quickButtonsScrollView.addView(quickButtonsLayout, FrameLayout.LayoutParams(-2, -2))
        topBar.addView(quickButtonsScrollView, LinearLayout.LayoutParams(-1, -2))
        renderTopCircularButtons()

        overlayContainer.addView(topBar, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        // Center Controls: Single play/pause button
        // Bottom Bar
        bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(12))
            background = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(0xee000000.toInt(), 0x77000000.toInt(), Color.TRANSPARENT)
            )
        }

        val timelineRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        timeView = TextView(this).apply {
            text = "00:00"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setSingleLine(true)
            includeFontPadding = false
            setTextColor(Color.WHITE)
            setPadding(0, 0, dp(8), 0)
            setOnClickListener {
                showRemainingTime = !showRemainingTime
                settingsPrefs.edit().putBoolean("show_remaining_time", showRemainingTime).apply()
                updateTimeDisplay()
                hudController.showQuickFeedback(if (showRemainingTime) "Time: Remaining (-)" else "Time: Total")
            }
        }
        seekBar = SeekBar(this).apply {
            max = 1000
            progressTintList = ColorStateList.valueOf(0xffffc400.toInt())
            thumbTintList = ColorStateList.valueOf(0xffffc400.toInt())
        }
        remainingTimeView = TextView(this).apply {
            text = "00:00"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setSingleLine(true)
            includeFontPadding = false
            setTextColor(0xffbbbec6.toInt())
            setPadding(dp(8), 0, 0, 0)
            setOnClickListener {
                showRemainingTime = !showRemainingTime
                settingsPrefs.edit().putBoolean("show_remaining_time", showRemainingTime).apply()
                updateTimeDisplay()
                hudController.showQuickFeedback(if (showRemainingTime) "Time: Remaining (-)" else "Time: Total")
            }
        }
        timelineRow.addView(timeView)
        timelineRow.addView(seekBar, LinearLayout.LayoutParams(0, dp(40), 1f))
        timelineRow.addView(remainingTimeView)
        bottomBar.addView(timelineRow)

        // Actions Row (Exact MX Player sequence):
        // 1. Screen Lock, 2. 5s Seek Back, 3. Prev Video, 4. Play/Pause, 5. Next Video, 6. 5s Seek Forward, 7. PiP
        val actionsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val lockBtn = actionTextButton("🔒") { lockScreen() }
        val seekBack5Btn = actionTextButton("⟲ 5s") {}
        setupHoldToContinuousSeek(seekBack5Btn, isForward = false) {
            seekBy(-5_000)
            hudController.showQuickFeedback("⟲ 5s")
        }
        val prevBtn = actionTextButton("⏮") {}
        setupHoldToContinuousSeek(prevBtn, isForward = false) {
            previousVideo()
        }
        playPauseBottomBtn = actionTextButton("Ⅱ") { togglePlay() }.apply {
            textSize = 20f
            setTextColor(0xffffc400.toInt())
        }
        val nextBtn = actionTextButton("⏭") {}
        setupHoldToContinuousSeek(nextBtn, isForward = true) {
            nextVideo()
        }
        markDoneBtn = actionTextButton("✔") {
            toggleMarkCurrentVideoAsSeen()
        }.apply {
            textSize = 20f
            setTextColor(0xff00e676.toInt())
        }
        val seekFwd5Btn = actionTextButton("5s ⟳") {}
        setupHoldToContinuousSeek(seekFwd5Btn, isForward = true) {
            seekBy(5_000)
            hudController.showQuickFeedback("5s ⟳")
        }
        val pipBtn = actionTextButton("⧉") { enterPipMode() }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) pipBtn.visibility = View.GONE

        val actionLp = { LinearLayout.LayoutParams(0, dp(44), 1f) }
        actionsRow.addView(lockBtn, actionLp())
        actionsRow.addView(seekBack5Btn, actionLp())
        actionsRow.addView(prevBtn, actionLp())
        actionsRow.addView(playPauseBottomBtn, actionLp())
        actionsRow.addView(nextBtn, actionLp())
        actionsRow.addView(markDoneBtn, actionLp())
        actionsRow.addView(seekFwd5Btn, actionLp())
        actionsRow.addView(pipBtn, actionLp())
        bottomBar.addView(actionsRow)

        overlayContainer.addView(bottomBar, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        root.addView(overlayContainer, FrameLayout.LayoutParams(-1, -1))
        root.addView(persistentStatusHeader, FrameLayout.LayoutParams(-1, dp(32), Gravity.TOP))
        root.addView(
            lockFloatingBtn,
            FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP or Gravity.START).apply {
                topMargin = dp(42)
                leftMargin = dp(20)
            }
        )

        // Set touch listeners - only playerView handles video gestures so all buttons remain clickable
        playerView.setOnTouchListener { _, event -> gestureController.handleTouchEvent(event) }
        topBar.setOnClickListener { scheduleHideControls() }
        bottomBar.setOnClickListener { scheduleHideControls() }

        updateMarkDoneButtonState()
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
        this.text = text
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setSingleLine(true)
        includeFontPadding = false
        setOnClickListener { onClick(); scheduleHideControls() }
    }

    private fun setupHoldToContinuousSeek(view: View, isForward: Boolean, onTap: () -> Unit) {
        var isHolding = false
        var holdStartPos = 0L

        val seekStepRunnable = object : Runnable {
            override fun run() {
                if (!::player.isInitialized) return
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
                updateTimeDisplay()
                updateStatusHeader()

                handler.removeCallbacks(hideControlsRunnable)
                handler.postDelayed(this, 75L)
            }
        }

        val holdDetectRunnable = Runnable {
            if (!::player.isInitialized) return@Runnable
            isHolding = true
            holdStartPos = player.currentPosition.coerceAtLeast(0L)
            handler.removeCallbacks(hideControlsRunnable)
            handler.post(seekStepRunnable)
        }

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isHolding = false
                    v.alpha = 0.55f
                    handler.removeCallbacks(hideControlsRunnable)
                    handler.postDelayed(holdDetectRunnable, 280L)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    v.alpha = 1.0f
                    handler.removeCallbacks(holdDetectRunnable)
                    handler.removeCallbacks(seekStepRunnable)
                    if (isHolding) {
                        isHolding = false
                        hudController.hideSeek(400)
                        scheduleHideControls()
                    } else {
                        v.performClick()
                        onTap()
                        scheduleHideControls()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.alpha = 1.0f
                    handler.removeCallbacks(holdDetectRunnable)
                    handler.removeCallbacks(seekStepRunnable)
                    if (isHolding) {
                        isHolding = false
                        hudController.hideSeek(400)
                    }
                    scheduleHideControls()
                    true
                }
                else -> false
            }
        }
    }

    private fun quickCircularButton(text: String, onClick: () -> Unit) = TextView(this).apply {
        this.text = text
        textSize = 12f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setSingleLine(true)
        includeFontPadding = false
        background = UiUtils.rounded(0x44ffffff.toInt(), 17, this@PlayerActivity)
        setPadding(UiUtils.dp(this@PlayerActivity, 12), UiUtils.dp(this@PlayerActivity, 6), UiUtils.dp(this@PlayerActivity, 12), UiUtils.dp(this@PlayerActivity, 6))
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, UiUtils.dp(this@PlayerActivity, 34)).apply {
            rightMargin = UiUtils.dp(this@PlayerActivity, 8)
        }
        layoutParams = lp
        setOnClickListener {
            onClick()
            scheduleHideControls()
        }
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
                        markCurrentVideoAsSeen()
                        playPauseBottomBtn.text = "▶"
                        when (repeatMode) {
                            1 -> nextVideo()
                            2 -> { p.seekTo(0); p.prepare(); p.play() }
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
                    playPauseBottomBtn.text = symbol
                    if (isPlaying) scheduleHideControls() else handler.removeCallbacks(hideControlsRunnable)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode) {
                        updatePipParams()
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    playPauseBottomBtn.text = "▶"
                    val errorName = names.getOrNull(index) ?: "Video"
                    hudController.showQuickFeedback("Playback error")
                    Toast.makeText(
                        this@PlayerActivity,
                        "Cannot play: $errorName (corrupted or unsupported format)",
                        Toast.LENGTH_LONG
                    ).show()
                }

                override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                    if (currentOrientationMode == 3) {
                        applyOrientation(showFeedback = false)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        updatePipParams()
                    }
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    val currentIdx = p.currentMediaItemIndex
                    if (currentIdx in uris.indices) {
                        index = currentIdx
                        updateTitle()
                        markCurrentVideoAsSeen()
                        updateMarkDoneButtonState()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            updatePipParams()
                        }
                    }
                }
            })

            if (uris.isNotEmpty()) {
                p.setMediaItems(uris.map { MediaItem.fromUri(it) }, index, 0)
                p.prepare()
                p.playbackParameters = PlaybackParameters(currentPlaybackSpeed, 1.0f)
                p.playWhenReady = true
                markCurrentVideoAsSeen()
            }
        }

        updateTitle()
        applyOrientation(showFeedback = false)
        markCurrentVideoAsSeen()
        updateMarkDoneButtonState()
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

    private fun isCurrentVideoSeen(): Boolean {
        if (uris.isEmpty() || index !in uris.indices) return false
        val currentUri = uris[index]
        return seenPrefs.contains("seen_$currentUri")
    }

    private fun updateMarkDoneButtonState() {
        if (!::markDoneBtn.isInitialized) return
        val isSeen = isCurrentVideoSeen()
        markDoneBtn.setTextColor(0xff00e676.toInt())
        markDoneBtn.alpha = if (isSeen) 1.0f else 0.45f
    }

    private fun toggleMarkCurrentVideoAsSeen() {
        if (uris.isEmpty() || index !in uris.indices) return
        val currentUri = uris[index]
        val key = "seen_$currentUri"
        if (seenPrefs.contains(key)) {
            seenPrefs.edit().remove(key).apply()
            updateMarkDoneButtonState()
            hudController.showQuickFeedback("Removed from Seen")
        } else {
            seenPrefs.edit().putLong(key, System.currentTimeMillis()).apply()
            updateMarkDoneButtonState()
            hudController.showQuickFeedback("Marked as Seen ✓")
        }
    }

    private fun markCurrentVideoAsSeen() {
        if (uris.isEmpty() || index !in uris.indices) return
        val currentUri = uris[index]
        seenPrefs.edit().putLong("seen_$currentUri", System.currentTimeMillis()).apply()
        updateMarkDoneButtonState()
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
                updateStatusHeader()

                if (d > 5_000L && p >= d - 2_000L) {
                    markCurrentVideoAsSeen()
                }
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
        if (isScreenLocked) {
            showLockTemporarily()
        } else {
            toggleControls()
        }
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
            player.seekTo(0L)
            player.prepare()
            player.play()
            return
        }
        if (player.playerError != null || player.playbackState == Player.STATE_IDLE) {
            player.prepare()
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
            player.seekTo(index, 0L)
            player.prepare()
            player.play()
            updateTitle()
            markCurrentVideoAsSeen()
            updateMarkDoneButtonState()
        } else if (repeatMode == 1) {
            index = 0
            player.seekTo(0, 0L)
            player.prepare()
            player.play()
            updateTitle()
            markCurrentVideoAsSeen()
            updateMarkDoneButtonState()
        } else {
            Toast.makeText(this, "End of playlist", Toast.LENGTH_SHORT).show()
        }
    }

    private fun previousVideo() {
        if (!::player.isInitialized || uris.isEmpty()) return
        if (player.currentPosition > 3500 && player.playbackState != Player.STATE_IDLE && player.playerError == null) {
            player.seekTo(0L)
            player.prepare()
            player.play()
        } else if (index > 0) {
            index--
            player.seekTo(index, 0L)
            player.prepare()
            player.play()
            updateTitle()
            markCurrentVideoAsSeen()
            updateMarkDoneButtonState()
        } else {
            player.seekTo(0L)
            player.prepare()
            player.play()
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

    private val hideLockRunnable = Runnable {
        lockFloatingBtn.visibility = View.GONE
    }

    private fun showLockTemporarily() {
        handler.removeCallbacks(hideLockRunnable)
        lockFloatingBtn.visibility = View.VISIBLE
        handler.postDelayed(hideLockRunnable, 3000L)
    }

    private fun lockScreen() {
        isScreenLocked = true
        controlsVisible = false
        overlayContainer.visibility = View.GONE
        showLockTemporarily()
    }

    private fun unlockScreen() {
        handler.removeCallbacks(hideLockRunnable)
        isScreenLocked = false
        lockFloatingBtn.visibility = View.GONE
        controlsVisible = true
        overlayContainer.visibility = View.VISIBLE
        scheduleHideControls()
    }

    private fun cycleResizeMode() {
        val modes = listOf(
            "Fit" to AspectRatioFrameLayout.RESIZE_MODE_FIT,
            "Fill" to AspectRatioFrameLayout.RESIZE_MODE_FILL,
            "Zoom" to AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        )
        currentAspectModeIndex = (currentAspectModeIndex + 1) % modes.size
        val (label, mode) = modes[currentAspectModeIndex]
        settingsPrefs.edit().putInt("aspect_mode", currentAspectModeIndex).apply()
        playerView.resizeMode = mode
        aspectCircularBtn?.text = "📐 $label"
        hudController.showQuickFeedback("Screen: $label")
    }

    private fun cycleOrientation() {
        currentOrientationMode = (currentOrientationMode + 1) % 4
        settingsPrefs.edit().putInt("orientation_mode", currentOrientationMode).apply()
        applyOrientation(showFeedback = true)
    }

    private fun applyOrientation(showFeedback: Boolean = true) {
        when (currentOrientationMode) {
            0 -> {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
                if (showFeedback) hudController.showQuickFeedback("Orientation: Auto")
                orientationCircularBtn?.text = "🔄 Auto"
            }
            1 -> {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                if (showFeedback) hudController.showQuickFeedback("Orientation: Landscape")
                orientationCircularBtn?.text = "🔄 Land"
            }
            2 -> {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                if (showFeedback) hudController.showQuickFeedback("Orientation: Portrait")
                orientationCircularBtn?.text = "🔄 Port"
            }
            3 -> {
                val size = if (::player.isInitialized) player.videoSize else null
                val format = if (::player.isInitialized) player.videoFormat else null
                val width = if (size != null && size.width > 0) size.width else (format?.width ?: 0)
                val height = if (size != null && size.height > 0) size.height else (format?.height ?: 0)
                val isWide = width > height
                requestedOrientation = if (isWide) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                if (showFeedback) hudController.showQuickFeedback("Orientation: Match Video")
                orientationCircularBtn?.text = "🔄 Video"
            }
        }
    }

    private fun toggleDecoderMode() {
        preferSoftwareDecoder = !preferSoftwareDecoder
        settingsPrefs.edit().putBoolean("sw_decoder", preferSoftwareDecoder).apply()
        val decLabel = if (preferSoftwareDecoder) "SW" else "HW"
        decoderCircularBtn?.text = decLabel
        Toast.makeText(this, "Decoder set to $decLabel. Restarting playback...", Toast.LENGTH_SHORT).show()
        val pos = player.currentPosition
        player.release()
        initPlayer()
        initComponents()
        player.seekTo(index, pos)
    }

    private fun setPlaybackSpeed(speed: Float) {
        currentPlaybackSpeed = speed
        settingsPrefs.edit().putFloat("playback_speed", speed).apply()
        if (::player.isInitialized) {
            player.playbackParameters = PlaybackParameters(speed, 1.0f)
        }
        speedCircularBtn?.text = TimeFormatter.formatSpeed(speed)
        hudController.showQuickFeedback("${TimeFormatter.formatSpeed(speed)} Speed")
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
                player.seekTo(index, 0L)
                player.prepare()
                player.play()
                updateTitle()
                markCurrentVideoAsSeen()
                updateMarkDoneButtonState()
            },
            onCycleRepeatMode = {
                repeatMode = (repeatMode + 1) % 3
                val label = when (repeatMode) { 1 -> "Repeat All"; 2 -> "Repeat One"; else -> "Off" }
                hudController.showQuickFeedback("Loop: $label")
            }
        )
    }

    private fun renderTopCircularButtons() {
        if (!::quickButtonsLayout.isInitialized) return
        quickButtonsLayout.removeAllViews()
        speedCircularBtn = null
        decoderCircularBtn = null
        aspectCircularBtn = null
        orientationCircularBtn = null

        val rawSaved = settingsPrefs.getStringSet("top_quick_buttons", null)
        val savedKeys = if (rawSaved == null) {
            setOf("speed", "orientation", "aspect", "playlist", "delete")
        } else if (!settingsPrefs.getBoolean("has_initialized_delete_btn", false)) {
            val updated = rawSaved.toMutableSet().apply { add("delete") }
            settingsPrefs.edit()
                .putStringSet("top_quick_buttons", updated)
                .putBoolean("has_initialized_delete_btn", true)
                .apply()
            updated
        } else {
            rawSaved
        }

        for ((key, _) in allQuickButtons) {
            if (!savedKeys.contains(key)) continue
            when (key) {
                "speed" -> {
                    val btn = quickCircularButton(TimeFormatter.formatSpeed(currentPlaybackSpeed)) {
                        dialogHelper.showSpeedDialog(speedCircularBtn) { s -> setPlaybackSpeed(s) }
                    }
                    speedCircularBtn = btn
                    quickButtonsLayout.addView(btn)
                }
                "orientation" -> {
                    val label = when (currentOrientationMode) { 1 -> "Land"; 2 -> "Port"; 3 -> "Video"; else -> "Auto" }
                    val btn = quickCircularButton("🔄 $label") {
                        cycleOrientation()
                    }
                    orientationCircularBtn = btn
                    quickButtonsLayout.addView(btn)
                }
                "aspect" -> {
                    val modes = listOf("Fit", "Fill", "Zoom")
                    val label = modes.getOrElse(currentAspectModeIndex) { "Fit" }
                    val btn = quickCircularButton("📐 $label") {
                        cycleResizeMode()
                    }
                    aspectCircularBtn = btn
                    quickButtonsLayout.addView(btn)
                }
                "playlist" -> {
                    val btn = quickCircularButton("📑 List") {
                        showPlaylist()
                    }
                    quickButtonsLayout.addView(btn)
                }
                "delete" -> {
                    val btn = quickCircularButton("🗑 Del") {
                        handleDeleteButtonTap()
                    }
                    deleteCircularBtn = btn
                    quickButtonsLayout.addView(btn)
                }
                "audio" -> {
                    val btn = quickCircularButton("🎵 Audio") {
                        dialogHelper.showAudioTrackDialog(isMuted) {
                            isMuted = !isMuted
                            player.volume = if (isMuted) 0f else 1f
                            hudController.showQuickFeedback(if (isMuted) "Muted" else "Unmuted")
                        }
                    }
                    quickButtonsLayout.addView(btn)
                }
                "subtitle" -> {
                    val btn = quickCircularButton("💬 Sub") {
                        dialogHelper.showSubtitleDialog(
                            currentFontSizeSp = subtitleFontSizeSp,
                            onExternalSubtitleClicked = { subtitlePicker.launch(arrayOf("*/*")) },
                            onFontSizeChanged = { size ->
                                subtitleFontSizeSp = size
                                playerView.subtitleView?.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, subtitleFontSizeSp)
                            }
                        )
                    }
                    quickButtonsLayout.addView(btn)
                }
                "decoder" -> {
                    val btn = quickCircularButton(if (preferSoftwareDecoder) "SW" else "HW") {
                        toggleDecoderMode()
                    }
                    decoderCircularBtn = btn
                    quickButtonsLayout.addView(btn)
                }
                "timer" -> {
                    val btn = quickCircularButton("⏱ Timer") {
                        dialogHelper.showSleepTimerDialog { min ->
                            handler.removeCallbacks(sleepTimerRunnable)
                            if (min > 0) {
                                handler.postDelayed(sleepTimerRunnable, min * 60 * 1000L)
                                hudController.showQuickFeedback("Sleep timer set for $min mins")
                            } else {
                                hudController.showQuickFeedback("Sleep timer disabled")
                            }
                        }
                    }
                    quickButtonsLayout.addView(btn)
                }
            }
        }
    }

    private fun showTopButtonsCustomizeDialog() {
        val savedKeys = settingsPrefs.getStringSet("top_quick_buttons", null)
            ?: setOf("speed", "orientation", "aspect", "playlist", "delete")
        val checkedItems = BooleanArray(allQuickButtons.size) { i ->
            savedKeys.contains(allQuickButtons[i].first)
        }
        val titles = allQuickButtons.map { it.second }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Quick Action Buttons")
            .setMultiChoiceItems(titles, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton("Save") { _, _ ->
                val newSelected = mutableSetOf<String>()
                for (i in checkedItems.indices) {
                    if (checkedItems[i]) {
                        newSelected.add(allQuickButtons[i].first)
                    }
                }
                settingsPrefs.edit().putStringSet("top_quick_buttons", newSelected).apply()
                renderTopCircularButtons()
                hudController.showQuickFeedback("Quick buttons updated")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun handleDeleteButtonTap() {
        deleteTapCount++
        handler.removeCallbacks(deleteResetRunnable)

        when (deleteTapCount) {
            1 -> {
                deleteCircularBtn?.text = "🗑 3 more"
                deleteCircularBtn?.setTextColor(0xffff7777.toInt())
                hudController.showQuickFeedback("Tap 3 more times to delete")
                handler.postDelayed(deleteResetRunnable, 2500)
            }
            2 -> {
                deleteCircularBtn?.text = "🗑 2 more"
                deleteCircularBtn?.setTextColor(0xffff5555.toInt())
                hudController.showQuickFeedback("Tap 2 more times to delete")
                handler.postDelayed(deleteResetRunnable, 2500)
            }
            3 -> {
                deleteCircularBtn?.text = "🗑 1 more!"
                deleteCircularBtn?.setTextColor(0xffff2222.toInt())
                hudController.showQuickFeedback("Tap 1 more time to delete!")
                handler.postDelayed(deleteResetRunnable, 2500)
            }
            4 -> {
                resetDeleteTaps()
                deleteCurrentVideo()
            }
            else -> {
                resetDeleteTaps()
            }
        }
    }

    private fun resetDeleteTaps() {
        deleteTapCount = 0
        handler.removeCallbacks(deleteResetRunnable)
        deleteCircularBtn?.text = "🗑 Del"
        deleteCircularBtn?.setTextColor(Color.WHITE)
    }

    private fun deleteCurrentVideo() {
        if (!::player.isInitialized || uris.isEmpty() || index !in uris.indices) return
        val currentUriStr = uris[index]
        val currentUri = Uri.parse(currentUriStr)
        val currentName = names.getOrNull(index) ?: "Video"
        pendingDeleteIndex = index

        var successfullyDeleted = false

        if (currentUri.scheme == "file") {
            runCatching {
                val f = File(currentUri.path ?: "")
                if (f.exists()) {
                    successfullyDeleted = f.delete()
                }
            }
        }

        if (!successfullyDeleted && DocumentsContract.isDocumentUri(this, currentUri)) {
            runCatching {
                successfullyDeleted = DocumentsContract.deleteDocument(contentResolver, currentUri)
            }
        }

        if (!successfullyDeleted) {
            try {
                val rows = contentResolver.delete(currentUri, null, null)
                if (rows > 0) {
                    successfullyDeleted = true
                }
            } catch (e: SecurityException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                    val intentSender = e.userAction.actionIntent.intentSender
                    deleteIntentLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                    return
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    runCatching {
                        val pi = MediaStore.createDeleteRequest(contentResolver, listOf(currentUri))
                        deleteIntentLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                        return
                    }
                }
            } catch (e: Exception) {
                // Fallback below
            }
        }

        if (!successfullyDeleted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && currentUri.scheme == "content") {
            val launched = runCatching {
                val pi = MediaStore.createDeleteRequest(contentResolver, listOf(currentUri))
                deleteIntentLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                true
            }.getOrDefault(false)
            if (launched) return
        }

        if (successfullyDeleted) {
            onVideoDeletedSuccess(index)
        } else {
            val fileDeleted = runCatching {
                contentResolver.query(currentUri, arrayOf(MediaStore.Video.Media.DATA), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val col = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                        if (col >= 0) {
                            val path = cursor.getString(col)
                            if (!path.isNullOrEmpty()) {
                                val f = File(path)
                                if (f.exists() && f.delete()) {
                                    runCatching { contentResolver.delete(currentUri, null, null) }
                                    true
                                } else false
                            } else false
                        } else false
                    } else false
                } ?: false
            }.getOrDefault(false)

            if (fileDeleted) {
                onVideoDeletedSuccess(index)
            } else {
                Toast.makeText(this, "Could not delete $currentName from storage, removing from playlist", Toast.LENGTH_SHORT).show()
                onVideoDeletedSuccess(index)
            }
        }
    }

    private fun onVideoDeletedSuccess(targetIndex: Int) {
        if (uris.isEmpty() || targetIndex !in uris.indices) return
        val deletedUri = uris[targetIndex]
        val deletedName = names.getOrNull(targetIndex) ?: "Video"

        seenPrefs.edit().remove("seen_$deletedUri").apply()
        resumeManager.clearPosition(uris, targetIndex)

        if (uris.size <= 1) {
            uris.clear()
            names.clear()
            player.stop()
            player.clearMediaItems()
            Toast.makeText(this, "Deleted: $deletedName", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        player.removeMediaItem(targetIndex)
        uris.removeAt(targetIndex)
        names.removeAt(targetIndex)

        val nextIndex = if (targetIndex < uris.size) targetIndex else 0
        index = nextIndex

        player.seekTo(index, 0)
        player.play()
        updateTitle()
        updateMarkDoneButtonState()
        hudController.showQuickFeedback("Deleted: $deletedName")
    }

    private fun showHamburgerMenu() {
        val items = arrayOf(
            "⚙ Customize Quick Buttons",
            "Playback Speed",
            "Screen Resize (Fit/Fill/Zoom)",
            "Screen Orientation",
            "Audio Tracks",
            "Subtitles",
            "HW / SW Decoder",
            "Sleep Timer",
            "Background Play: ${if (backgroundPlayEnabled) "On" else "Off"}",
            "Video Information",
            "Picture-in-Picture"
        )
        AlertDialog.Builder(this)
            .setTitle("Player Settings")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showTopButtonsCustomizeDialog()
                    1 -> dialogHelper.showSpeedDialog(speedCircularBtn) { s -> setPlaybackSpeed(s) }
                    2 -> cycleResizeMode()
                    3 -> cycleOrientation()
                    4 -> dialogHelper.showAudioTrackDialog(isMuted) {
                        isMuted = !isMuted
                        player.volume = if (isMuted) 0f else 1f
                        hudController.showQuickFeedback(if (isMuted) "Muted" else "Unmuted")
                    }
                    5 -> dialogHelper.showSubtitleDialog(
                        currentFontSizeSp = subtitleFontSizeSp,
                        onExternalSubtitleClicked = { subtitlePicker.launch(arrayOf("*/*")) },
                        onFontSizeChanged = { size ->
                            subtitleFontSizeSp = size
                            playerView.subtitleView?.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, subtitleFontSizeSp)
                        }
                    )
                    6 -> toggleDecoderMode()
                    7 -> dialogHelper.showSleepTimerDialog { min ->
                        handler.removeCallbacks(sleepTimerRunnable)
                        if (min > 0) {
                            handler.postDelayed(sleepTimerRunnable, min * 60 * 1000L)
                            hudController.showQuickFeedback("Sleep timer set for $min mins")
                        } else {
                            hudController.showQuickFeedback("Sleep timer disabled")
                        }
                    }
                    8 -> {
                        backgroundPlayEnabled = !backgroundPlayEnabled
                        settingsPrefs.edit().putBoolean("bg_play", backgroundPlayEnabled).apply()
                        hudController.showQuickFeedback("Background Play: ${if (backgroundPlayEnabled) "On" else "Off"}")
                    }
                    9 -> dialogHelper.showVideoInfoDialog(names.getOrNull(index) ?: "Video", preferSoftwareDecoder)
                    10 -> enterPipMode()
                }
            }
            .show()
    }

    private fun showMoreOptionsMenu() {
        showHamburgerMenu()
    }

    private val clockFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    private fun updateStatusHeader() {
        if (!::batteryText.isInitialized || !::clockText.isInitialized || !::topTimeStatusView.isInitialized) return
        clockText.text = clockFormat.format(Date())
        val bat = getBatteryPercentage()
        batteryText.text = if (bat >= 0) "$bat%" else ""

        if (::player.isInitialized) {
            val d = player.duration.coerceAtLeast(0)
            val p = player.currentPosition.coerceAtLeast(0)
            val elapsed = TimeFormatter.formatTime(p)
            topTimeStatusView.text = if (topTimeStatusMode == 0) {
                "$elapsed / ${TimeFormatter.formatTime(d)}"
            } else {
                val rem = (d - p).coerceAtLeast(0)
                "$elapsed / -${TimeFormatter.formatTime(rem)}"
            }
        }
    }

    private fun getBatteryPercentage(): Int {
        val bm = getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val batLevel = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        if (batLevel in 0..100) return batLevel

        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100 / scale) else -1
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
                addAction(ACTION_PIP_PREV)
                addAction(ACTION_PIP_NEXT)
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
        val vSize = if (::player.isInitialized) player.videoSize else null
        val vFormat = if (::player.isInitialized) player.videoFormat else null
        val width = if (vSize != null && vSize.width > 0) vSize.width else (vFormat?.width ?: 0)
        val height = if (vSize != null && vSize.height > 0) vSize.height else (vFormat?.height ?: 0)

        val rational = if (width > 0 && height > 0) {
            val ratio = width.toFloat() / height.toFloat()
            if (ratio in 0.42f..2.38f) {
                Rational(width, height)
            } else if (ratio < 0.42f) {
                Rational(42, 100)
            } else {
                Rational(238, 100)
            }
        } else {
            Rational(16, 9)
        }

        val builder = PictureInPictureParams.Builder().setAspectRatio(rational)

        // Set sourceRectHint so transition is smooth and matches MX Player
        val surfaceView = playerView.videoSurfaceView ?: playerView
        val sourceRect = Rect()
        surfaceView.getGlobalVisibleRect(sourceRect)
        if (sourceRect.width() > 0 && sourceRect.height() > 0) {
            builder.setSourceRectHint(sourceRect)
        }

        val isPlaying = ::player.isInitialized && player.isPlaying
        val actions = ArrayList<RemoteAction>()

        // 1. Previous Video
        val prevIntent = PendingIntent.getBroadcast(
            this, 1, Intent(ACTION_PIP_PREV).apply { `package` = packageName },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        actions.add(
            RemoteAction(
                Icon.createWithResource(this, android.R.drawable.ic_media_previous),
                "Previous",
                "Previous video",
                prevIntent
            )
        )

        // 2. Play / Pause
        if (isPlaying) {
            val pauseIntent = PendingIntent.getBroadcast(
                this, 2, Intent(ACTION_PIP_PAUSE).apply { `package` = packageName },
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
                this, 3, Intent(ACTION_PIP_PLAY).apply { `package` = packageName },
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

        // 3. Next Video
        val nextIntent = PendingIntent.getBroadcast(
            this, 4, Intent(ACTION_PIP_NEXT).apply { `package` = packageName },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        actions.add(
            RemoteAction(
                Icon.createWithResource(this, android.R.drawable.ic_media_next),
                "Next",
                "Next video",
                nextIntent
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
            if (::persistentStatusHeader.isInitialized) persistentStatusHeader.visibility = View.GONE
            if (::lockFloatingBtn.isInitialized) lockFloatingBtn.visibility = View.GONE
            if (::player.isInitialized && !player.isPlaying && player.playbackState == Player.STATE_READY) {
                player.play()
            }
            val params = buildPipParams()
            if (params != null) {
                enteredPipMode = true
                enterPictureInPictureMode(params)
            }
        }.onFailure {
            enteredPipMode = false
            overlayContainer.visibility = View.VISIBLE
            if (::persistentStatusHeader.isInitialized) persistentStatusHeader.visibility = View.VISIBLE
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
        pipDismissRunnable?.let { handler.removeCallbacks(it) }
        pipDismissRunnable = null
        enteredPipMode = false
        hideSystemBars()
        if (::persistentStatusHeader.isInitialized) persistentStatusHeader.visibility = View.VISIBLE
        if (::lockFloatingBtn.isInitialized && isScreenLocked) lockFloatingBtn.visibility = View.VISIBLE
        overlayContainer.visibility = if (controlsVisible) View.VISIBLE else View.GONE
        scheduleHideControls()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
            if (!inPip) {
                if (!backgroundPlayEnabled) {
                    player.pause()
                }
                if (enteredPipMode && !lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                    enteredPipMode = false
                    finish()
                }
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
        pipDismissRunnable?.let { handler.removeCallbacks(it) }
        pipDismissRunnable = null

        if (isInPip) {
            enteredPipMode = true
            overlayContainer.visibility = View.GONE
            if (::persistentStatusHeader.isInitialized) persistentStatusHeader.visibility = View.GONE
            if (::lockFloatingBtn.isInitialized) lockFloatingBtn.visibility = View.GONE
            if (::player.isInitialized && !player.isPlaying && player.playbackState == Player.STATE_READY) {
                player.play()
            }
            updatePipParams()
        } else {
            hideSystemBars()
            if (::persistentStatusHeader.isInitialized) persistentStatusHeader.visibility = View.VISIBLE
            overlayContainer.visibility = View.VISIBLE
            controlsVisible = true
            scheduleHideControls()

            val dismissTask = Runnable {
                if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) && !isFinishing) {
                    enteredPipMode = false
                    if (::player.isInitialized) {
                        resumeManager.savePosition(player, uris, index)
                        player.pause()
                    }
                    finish()
                }
            }
            pipDismissRunnable = dismissTask
            handler.postDelayed(dismissTask, 1500)
        }
    }

    override fun onDestroy() {
        pipDismissRunnable?.let { handler.removeCallbacks(it) }
        pipDismissRunnable = null
        handler.removeCallbacksAndMessages(null)
        unregisterPipReceiver()
        if (::player.isInitialized) {
            resumeManager.savePosition(player, uris, index)
            player.pause()
            runCatching { loudnessEnhancer?.release() }
            playerView.player = null
            player.release()
        }
        super.onDestroy()
    }
}
