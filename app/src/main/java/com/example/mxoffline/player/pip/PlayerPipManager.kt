/**
 * Role: Picture-in-Picture lifecycle and remote control coordinator.
 * Responsibility: Manages PiP parameters, source bounds, system broadcast receiver, and exit listeners.
 * Details: Implements MX Player style aspect clamping, auto-enter on Android 12+, and halts audio on window close.
 */
package com.example.mxoffline.player.pip

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.util.Rational
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class PlayerPipManager(
    private val activity: Activity,
    private val handler: Handler,
    private val onPlayRequested: () -> Unit,
    private val onPauseRequested: () -> Unit,
    private val onPrevRequested: () -> Unit,
    private val onNextRequested: () -> Unit,
    private val onDismissClosed: () -> Unit
) {

    companion object {
        const val ACTION_PIP_PLAY = "com.example.mxoffline.PIP_PLAY"
        const val ACTION_PIP_PAUSE = "com.example.mxoffline.PIP_PAUSE"
        const val ACTION_PIP_PREV = "com.example.mxoffline.PIP_PREV"
        const val ACTION_PIP_NEXT = "com.example.mxoffline.PIP_NEXT"
    }

    var enteredPipMode = false
    private var pipDismissRunnable: Runnable? = null

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_PIP_PLAY -> onPlayRequested()
                ACTION_PIP_PAUSE -> onPauseRequested()
                ACTION_PIP_PREV -> onPrevRequested()
                ACTION_PIP_NEXT -> onNextRequested()
            }
        }
    }

    fun register() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val filter = IntentFilter().apply {
                addAction(ACTION_PIP_PLAY)
                addAction(ACTION_PIP_PAUSE)
                addAction(ACTION_PIP_PREV)
                addAction(ACTION_PIP_NEXT)
            }
            ContextCompat.registerReceiver(activity, pipReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }
    }

    fun unregister() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { activity.unregisterReceiver(pipReceiver) }
        }
    }

    fun buildPipParams(player: ExoPlayer?, playerView: PlayerView?): PictureInPictureParams? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val vSize = player?.videoSize
        val vFormat = player?.videoFormat
        val width = if (vSize != null && vSize.width > 0) vSize.width else (vFormat?.width ?: 0)
        val height = if (vSize != null && vSize.height > 0) vSize.height else (vFormat?.height ?: 0)

        val rational = if (width > 0 && height > 0) {
            val ratio = width.toFloat() / height.toFloat()
            if (ratio in 0.42f..2.38f) Rational(width, height)
            else if (ratio < 0.42f) Rational(42, 100)
            else Rational(238, 100)
        } else Rational(16, 9)

        val builder = PictureInPictureParams.Builder().setAspectRatio(rational)

        if (playerView != null) {
            val surfaceView = playerView.videoSurfaceView ?: playerView
            val sourceRect = Rect()
            surfaceView.getGlobalVisibleRect(sourceRect)
            if (sourceRect.width() > 0 && sourceRect.height() > 0) {
                builder.setSourceRectHint(sourceRect)
            }
        }

        val isPlaying = player?.isPlaying == true
        val actions = ArrayList<RemoteAction>()

        val prevIntent = PendingIntent.getBroadcast(
            activity, 1, Intent(ACTION_PIP_PREV).apply { `package` = activity.packageName },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        actions.add(RemoteAction(Icon.createWithResource(activity, android.R.drawable.ic_media_previous), "Rewind 10s", "Rewind 10 seconds", prevIntent))

        val playPauseIntent = PendingIntent.getBroadcast(
            activity, if (isPlaying) 2 else 3,
            Intent(if (isPlaying) ACTION_PIP_PAUSE else ACTION_PIP_PLAY).apply { `package` = activity.packageName },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        actions.add(RemoteAction(Icon.createWithResource(activity, playPauseIcon), if (isPlaying) "Pause" else "Play", "Playback toggle", playPauseIntent))

        val nextIntent = PendingIntent.getBroadcast(
            activity, 4, Intent(ACTION_PIP_NEXT).apply { `package` = activity.packageName },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        actions.add(RemoteAction(Icon.createWithResource(activity, android.R.drawable.ic_media_next), "Forward 10s", "Forward 10 seconds", nextIntent))

        builder.setActions(actions)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(true)
            builder.setSeamlessResizeEnabled(true)
        }

        return builder.build()
    }

    fun updatePipParams(player: ExoPlayer?, playerView: PlayerView?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                val params = buildPipParams(player, playerView) ?: return
                activity.setPictureInPictureParams(params)
            }
        }
    }

    fun enterPip(player: ExoPlayer?, playerView: PlayerView?, hideOverlays: () -> Unit, showOverlays: () -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Toast.makeText(activity, "PiP requires Android 8.0+", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            hideOverlays()
            val params = buildPipParams(player, playerView)
            if (params != null) {
                enteredPipMode = true
                activity.enterPictureInPictureMode(params)
            }
        }.onFailure {
            enteredPipMode = false
            showOverlays()
            Toast.makeText(activity, "Cannot enter PiP: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun onPipChanged(isInPip: Boolean, player: ExoPlayer?, playerView: PlayerView?, onEntered: () -> Unit, onExited: () -> Unit) {
        pipDismissRunnable?.let { handler.removeCallbacks(it) }
        pipDismissRunnable = null

        if (isInPip) {
            enteredPipMode = true
            onEntered()
            updatePipParams(player, playerView)
        } else {
            onExited()
            val dismissTask = Runnable {
                val lifecycleOwner = activity as? androidx.lifecycle.LifecycleOwner
                if (lifecycleOwner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != true && !activity.isFinishing) {
                    enteredPipMode = false
                    onDismissClosed()
                }
            }
            pipDismissRunnable = dismissTask
            handler.postDelayed(dismissTask, 1500)
        }
    }

    fun onResumeClean() {
        pipDismissRunnable?.let { handler.removeCallbacks(it) }
        pipDismissRunnable = null
        enteredPipMode = false
    }

    fun onDestroyClean() {
        pipDismissRunnable?.let { handler.removeCallbacks(it) }
        pipDismissRunnable = null
        unregister()
    }
}
