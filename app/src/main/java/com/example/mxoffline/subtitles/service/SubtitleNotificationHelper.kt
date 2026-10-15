/**
 * Role: Notification builder and channel coordinator for background subtitle generation.
 * Responsibility: Manages foreground service notification channel and real-time progress updates.
 * Details: Displays progress bar, active video title, queue counter, and pause/cancel actions.
 */
package com.example.mxoffline.subtitles.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.mxoffline.MainActivity
import com.example.mxoffline.subtitles.model.SubtitleBatchProgress

class SubtitleNotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "vxplayer_subtitle_gen"
        const val NOTIFICATION_ID = 2024
        const val ACTION_CANCEL = "com.example.mxoffline.subtitles.ACTION_CANCEL"
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannel()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Subtitle Generation",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of offline subtitle extraction and generation"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun buildProgressNotification(progress: SubtitleBatchProgress): Notification {
        val openIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = PendingIntent.getService(
            context, 1,
            Intent(context, SubtitleGenerationService::class.java).apply { action = ACTION_CANCEL },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (progress.totalVideos > 1) {
            "Subtitles: ${progress.folderName} (${progress.currentIndex + 1}/${progress.totalVideos})"
        } else {
            "Generating Subtitles"
        }

        val content = "${progress.currentVideoName} · ${progress.currentProgress}%"

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress.currentProgress, false)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelIntent)
            .build()
    }

    fun buildCompleteNotification(count: Int, folderName: String): Notification {
        val openIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Subtitles Generated")
            .setContentText("Completed $count video(s) in $folderName")
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .build()
    }

    fun updateProgress(progress: SubtitleBatchProgress) {
        notificationManager.notify(NOTIFICATION_ID, buildProgressNotification(progress))
    }

    fun notifyCompleted(count: Int, folderName: String) {
        notificationManager.notify(NOTIFICATION_ID, buildCompleteNotification(count, folderName))
    }
}
