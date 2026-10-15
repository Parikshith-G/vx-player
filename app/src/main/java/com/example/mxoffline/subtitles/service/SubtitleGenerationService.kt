/**
 * Role: Unkillable Android Foreground Service for batch offline subtitle extraction.
 * Responsibility: Executes sequential audio decoding and speech transcription in background thread.
 * Details: Acquires Partial WakeLock, updates sticky notification, and persists .srt files to storage.
 */
package com.example.mxoffline.subtitles.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.example.mxoffline.model.VideoItem
import com.example.mxoffline.subtitles.audio.AudioExtractor
import com.example.mxoffline.subtitles.engine.SubtitleEngine
import com.example.mxoffline.subtitles.manager.SubtitleGenerationManager
import com.example.mxoffline.subtitles.model.SubtitleBatchProgress
import com.example.mxoffline.subtitles.srt.SrtWriter
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class SubtitleGenerationService : Service() {

    private val executor = Executors.newSingleThreadExecutor()
    private val isCancelled = AtomicBoolean(false)
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var notificationHelper: SubtitleNotificationHelper

    companion object {
        const val ACTION_START = "com.example.mxoffline.subtitles.ACTION_START"
    }

    override fun onCreate() {
        super.onCreate()
        notificationHelper = SubtitleNotificationHelper(this)
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "vxplayer:subtitle_gen").apply {
            setReferenceCounted(false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == SubtitleNotificationHelper.ACTION_CANCEL) {
            cancelGeneration()
            return START_NOT_STICKY
        }

        if (action == ACTION_START) {
            isCancelled.set(false)
            val queue = SubtitleGenerationManager.currentQueue
            val folderName = SubtitleGenerationManager.currentFolderName
            if (queue.isEmpty()) {
                stopSelf()
                return START_NOT_STICKY
            }

            val initialProgress = SubtitleBatchProgress(
                folderName = folderName,
                totalVideos = queue.size,
                completedVideos = 0,
                currentIndex = 0,
                currentVideoName = queue.first().name,
                currentProgress = 0
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    SubtitleNotificationHelper.NOTIFICATION_ID,
                    notificationHelper.buildProgressNotification(initialProgress),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(
                    SubtitleNotificationHelper.NOTIFICATION_ID,
                    notificationHelper.buildProgressNotification(initialProgress)
                )
            }

            wakeLock?.acquire(60 * 60 * 1000L) // 1 hour max safe guard
            startProcessing(queue, folderName)
        }

        return START_NOT_STICKY
    }

    private fun startProcessing(videos: List<VideoItem>, folderName: String) {
        executor.execute {
            var completedCount = 0
            val total = videos.size

            for (index in videos.indices) {
                if (isCancelled.get()) break
                val video = videos[index]

                val pcmFile = File(cacheDir, "sub_extract_${System.currentTimeMillis()}.pcm")
                try {
                    // Stage 1: Audio Extraction (0% -> 50%)
                    AudioExtractor.extractToPcm(
                        context = this,
                        videoUri = video.uri,
                        outputPcmFile = pcmFile,
                        onProgress = { extProgress ->
                            val percent = (extProgress * 50).toInt()
                            updateProgress(folderName, total, completedCount, index, video.name, percent)
                        },
                        isCancelled = { isCancelled.get() }
                    )

                    if (isCancelled.get()) break

                    // Stage 2: Transcription (50% -> 100%)
                    val segments = SubtitleEngine.transcribe(
                        context = this,
                        pcmFile = pcmFile,
                        onProgress = { transProgress ->
                            val percent = 50 + (transProgress * 50).toInt()
                            updateProgress(folderName, total, completedCount, index, video.name, percent)
                        },
                        isCancelled = { isCancelled.get() }
                    )

                    if (isCancelled.get()) break

                    if (segments.isNotEmpty()) {
                        SrtWriter.saveSubtitles(this, video, segments)
                    }
                    completedCount++
                } catch (e: Exception) {
                    // Continue to next video if one fails
                } finally {
                    pcmFile.delete()
                }
            }

            wakeLock?.let { if (it.isHeld) it.release() }
            if (!isCancelled.get()) {
                notificationHelper.notifyCompleted(completedCount, folderName)
                SubtitleGenerationManager.dispatchCompleted(folderName, completedCount)
            } else {
                SubtitleGenerationManager.dispatchCancelled()
            }

            stopForeground(false)
            stopSelf()
        }
    }

    private fun updateProgress(
        folderName: String,
        total: Int,
        completed: Int,
        index: Int,
        name: String,
        percent: Int
    ) {
        val progress = SubtitleBatchProgress(
            folderName = folderName,
            totalVideos = total,
            completedVideos = completed,
            currentIndex = index,
            currentVideoName = name,
            currentProgress = percent
        )
        notificationHelper.updateProgress(progress)
        SubtitleGenerationManager.dispatchProgress(progress)
    }

    private fun cancelGeneration() {
        isCancelled.set(true)
        wakeLock?.let { if (it.isHeld) it.release() }
        stopForeground(true)
        SubtitleGenerationManager.dispatchCancelled()
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        isCancelled.set(true)
        wakeLock?.let { if (it.isHeld) it.release() }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
