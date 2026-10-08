/**
 * Role: Batch media manager and history clearing coordinator.
 * Responsibility: Executes bulk deletion of videos and manages recent/seen history cleanup.
 * Details: Integrates MediaStore scoped-storage delete requests, DocumentsContract SAF, and SharedPreferences.
 */
package com.example.mxoffline.library.actions

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.example.mxoffline.model.VideoItem
import com.example.mxoffline.util.AppBackupManager
import com.example.mxoffline.util.FileSizeFormatter
import java.io.File
import java.util.concurrent.Executor

class LibraryBatchActionManager(
    private val activity: ComponentActivity,
    private val resumePrefs: SharedPreferences,
    private val seenPrefs: SharedPreferences,
    private val executor: Executor,
    private val mainHandler: Handler,
    private val batchDeleteLauncher: ActivityResultLauncher<IntentSenderRequest>,
    private val onDataChanged: () -> Unit
) {

    var pendingBatchDeleteVideos = listOf<VideoItem>()
        private set

    fun promptClearRecent() {
        AlertDialog.Builder(activity)
            .setTitle("Clear Recent History")
            .setMessage("Do you want to clear your recently watched playback history?")
            .setPositiveButton("Clear") { _, _ ->
                val editor = resumePrefs.edit()
                for (key in resumePrefs.all.keys) {
                    if (key.startsWith("pos_")) {
                        editor.remove(key)
                    }
                }
                editor.apply()
                onDataChanged()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun promptClearSeenHistory() {
        AlertDialog.Builder(activity)
            .setTitle("Clear Seen History")
            .setMessage("Remove all completed videos from the Seen list? Files on storage will NOT be deleted.")
            .setPositiveButton("Clear") { _, _ ->
                val editor = seenPrefs.edit()
                for (key in seenPrefs.all.keys) {
                    if (key.startsWith("seen_")) {
                        editor.remove(key)
                    }
                }
                editor.apply()
                AppBackupManager.clearBackupData(activity)
                onDataChanged()
                Toast.makeText(activity, "Seen history cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun promptDeleteAllSeen(videos: List<VideoItem>) {
        if (videos.isEmpty()) return
        val count = videos.size
        val totalBytes = videos.sumOf { it.sizeBytes }
        val sizeFormatted = FileSizeFormatter.formatSize(totalBytes)

        AlertDialog.Builder(activity)
            .setTitle("Delete All Seen Videos")
            .setMessage("Permanently delete $count completed video(s) ($sizeFormatted) from device storage? This cannot be undone.")
            .setPositiveButton("Delete All") { _, _ ->
                performBatchDelete(videos)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun performBatchDelete(videos: List<VideoItem>) {
        if (videos.isEmpty()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val contentUris = videos.map { it.uri }.filter { it.scheme == "content" }
            if (contentUris.isNotEmpty()) {
                val launched = runCatching {
                    val pi = MediaStore.createDeleteRequest(activity.contentResolver, contentUris)
                    pendingBatchDeleteVideos = videos
                    batchDeleteLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                    true
                }.getOrDefault(false)
                if (launched) return
            }
        }

        executor.execute {
            var deletedCount = 0
            val seenEditor = seenPrefs.edit()
            val resumeEditor = resumePrefs.edit()
            for (video in videos) {
                var success = false
                if (video.uri.scheme == "file") {
                    runCatching {
                        val f = File(video.uri.path ?: "")
                        if (f.exists()) success = f.delete()
                    }
                } else if (DocumentsContract.isDocumentUri(activity, video.uri)) {
                    runCatching {
                        success = DocumentsContract.deleteDocument(activity.contentResolver, video.uri)
                    }
                }
                if (!success) {
                    try {
                        val rows = activity.contentResolver.delete(video.uri, null, null)
                        if (rows > 0) success = true
                    } catch (e: Exception) {
                        // Ignored
                    }
                }
                if (success) {
                    deletedCount++
                    for (k in com.example.mxoffline.util.VideoIdentity.getAllKeys("seen", video)) seenEditor.remove(k)
                    for (k in com.example.mxoffline.util.VideoIdentity.getAllKeys("pos", video)) resumeEditor.remove(k)
                }
            }
            seenEditor.apply()
            resumeEditor.apply()
            AppBackupManager.clearBackupData(activity)

            mainHandler.post {
                onDataChanged()
                Toast.makeText(activity, "Deleted $deletedCount of ${videos.size} seen video(s)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun onBatchDeleteSuccess(videos: List<VideoItem>) {
        val seenEditor = seenPrefs.edit()
        val resumeEditor = resumePrefs.edit()
        for (video in videos) {
            for (k in com.example.mxoffline.util.VideoIdentity.getAllKeys("seen", video)) seenEditor.remove(k)
            for (k in com.example.mxoffline.util.VideoIdentity.getAllKeys("pos", video)) resumeEditor.remove(k)
        }
        seenEditor.apply()
        resumeEditor.apply()
        AppBackupManager.clearBackupData(activity)
        onDataChanged()
        Toast.makeText(activity, "Deleted ${videos.size} seen video(s)", Toast.LENGTH_SHORT).show()
        pendingBatchDeleteVideos = emptyList()
    }

    fun onBatchDeleteCancelled() {
        Toast.makeText(activity, "Batch delete cancelled", Toast.LENGTH_SHORT).show()
        pendingBatchDeleteVideos = emptyList()
    }
}
