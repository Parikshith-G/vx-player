/**
 * Role: Subtitle generation singleton manager and lifecycle coordinator.
 * Responsibility: Enqueues videos, launches SubtitleGenerationService, and dispatches status events.
 * Details: Connects library UI and player dialogs to background foreground transcription tasks.
 */
package com.example.mxoffline.subtitles.manager

import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import com.example.mxoffline.model.VideoItem
import com.example.mxoffline.subtitles.model.SubtitleBatchProgress
import com.example.mxoffline.subtitles.service.SubtitleGenerationService
import java.util.concurrent.CopyOnWriteArrayList

object SubtitleGenerationManager {

    interface Listener {
        fun onProgress(progress: SubtitleBatchProgress)
        fun onCompleted(folderName: String, totalCompleted: Int)
        fun onCancelled()
    }

    var currentQueue: List<VideoItem> = emptyList()
        private set
    var currentFolderName: String = ""
        private set
    var isRunning: Boolean = false
        private set

    private val listeners = CopyOnWriteArrayList<Listener>()

    fun addListener(listener: Listener) {
        if (!listeners.contains(listener)) listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    fun startFolderBatch(context: Context, folderName: String, videos: List<VideoItem>) {
        if (videos.isEmpty()) {
            Toast.makeText(context, "Folder contains no videos", Toast.LENGTH_SHORT).show()
            return
        }
        if (isRunning) {
            Toast.makeText(context, "Subtitle generation already in progress", Toast.LENGTH_SHORT).show()
            return
        }

        currentQueue = videos
        currentFolderName = folderName
        isRunning = true

        val intent = Intent(context, SubtitleGenerationService::class.java).apply {
            action = SubtitleGenerationService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        Toast.makeText(context, "Starting subtitle generation for ${videos.size} video(s)", Toast.LENGTH_SHORT).show()
    }

    fun startSingleVideo(context: Context, video: VideoItem) {
        val folder = if (video.folderName.isNotBlank()) video.folderName else "Video"
        startFolderBatch(context, folder, listOf(video))
    }

    fun cancel(context: Context) {
        if (!isRunning) return
        val intent = Intent(context, SubtitleGenerationService::class.java).apply {
            action = com.example.mxoffline.subtitles.service.SubtitleNotificationHelper.ACTION_CANCEL
        }
        context.startService(intent)
    }

    fun dispatchProgress(progress: SubtitleBatchProgress) {
        isRunning = true
        listeners.forEach { it.onProgress(progress) }
    }

    fun dispatchCompleted(folderName: String, totalCompleted: Int) {
        isRunning = false
        currentQueue = emptyList()
        currentFolderName = ""
        listeners.forEach { it.onCompleted(folderName, totalCompleted) }
    }

    fun dispatchCancelled() {
        isRunning = false
        currentQueue = emptyList()
        currentFolderName = ""
        listeners.forEach { it.onCancelled() }
    }
}
