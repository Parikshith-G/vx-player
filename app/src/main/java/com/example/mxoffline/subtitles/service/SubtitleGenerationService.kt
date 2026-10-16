/**
 * Role: Unkillable Android Foreground Service for batch offline subtitle extraction.
 * Responsibility: Executes sequential 5-minute chunked audio decoding and speech transcription in background thread.
 * Details: Acquires Partial WakeLock, updates sticky notification, and progressively saves .srt files to storage.
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
import com.example.mxoffline.subtitles.model.SubtitleSegment
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
            if (!com.example.mxoffline.subtitles.engine.SubtitleModelManager.isModelAvailable(this)) {
                wakeLock?.let { if (it.isHeld) it.release() }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE) else @Suppress("DEPRECATION") stopForeground(true)
                SubtitleGenerationManager.dispatchCancelled()
                stopSelf()
                return@execute
            }
            var completedCount = 0
            val total = videos.size

            for (index in videos.indices) {
                if (isCancelled.get()) break
                val video = videos[index]

                val durationUs = AudioExtractor.getAudioDurationUs(this, video.uri)
                val totalVideoMs = durationUs / 1000L
                val chunkDurationMs = AudioExtractor.CHUNK_DURATION_MS
                val totalChunks = if (totalVideoMs > 0) {
                    ((totalVideoMs + chunkDurationMs - 1) / chunkDurationMs).toInt().coerceAtLeast(1)
                } else {
                    1
                }

                val accumulatedSegments = mutableListOf<SubtitleSegment>()

                for (chunkIndex in 0 until totalChunks) {
                    if (isCancelled.get()) break

                    val startMs = chunkIndex * chunkDurationMs
                    val endMs = ((chunkIndex + 1) * chunkDurationMs).coerceAtMost(if (totalVideoMs > 0) totalVideoMs else Long.MAX_VALUE)
                    val startUs = startMs * 1000L
                    val endUs = endMs * 1000L

                    val chunkPcm = File(cacheDir, "sub_chunk_${index}_${chunkIndex}_${System.currentTimeMillis()}.pcm")
                    try {
                        // Stage 1: Extract 5-minute chunk audio
                        val extOk = AudioExtractor.extractChunkToPcm(
                            context = this,
                            videoUri = video.uri,
                            startUs = startUs,
                            endUs = endUs,
                            outputPcmFile = chunkPcm,
                            onProgress = { extFrac ->
                                val chunkWeight = 100f / totalChunks
                                val overallPercent = ((chunkIndex * chunkWeight) + (extFrac * 0.5f * chunkWeight)).toInt()
                                updateProgress(folderName, total, completedCount, index, video.name, overallPercent)
                            },
                            isCancelled = { isCancelled.get() }
                        )

                        if (isCancelled.get()) break
                        if (!extOk || !chunkPcm.exists() || chunkPcm.length() == 0L) continue

                        // Stage 2: Transcribe 5-minute chunk with time offset
                        val chunkSegments = SubtitleEngine.transcribe(
                            context = this,
                            pcmFile = chunkPcm,
                            timeOffsetMs = startMs,
                            onProgress = { transFrac ->
                                val chunkWeight = 100f / totalChunks
                                val overallPercent = ((chunkIndex * chunkWeight) + ((0.5f + transFrac * 0.5f) * chunkWeight)).toInt()
                                updateProgress(folderName, total, completedCount, index, video.name, overallPercent)
                            },
                            isCancelled = { isCancelled.get() }
                        )

                        if (isCancelled.get()) break

                        if (chunkSegments.isNotEmpty()) {
                            accumulatedSegments.addAll(chunkSegments)
                            // Progressively save accumulated subtitles so far
                            SrtWriter.saveSubtitles(this, video, accumulatedSegments)
                        }
                    } catch (e: Exception) {
                        // Skip damaged chunk and continue
                    } finally {
                        chunkPcm.delete()
                    }
                }

                if (!isCancelled.get() && accumulatedSegments.isNotEmpty()) {
                    SrtWriter.saveSubtitles(this, video, accumulatedSegments)
                    completedCount++
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
