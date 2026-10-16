/**
 * Role: SubRip (.srt) subtitle serializer and file writer.
 * Responsibility: Converts timestamped subtitle segments into standard .srt format and saves to storage.
 * Details: Saves alongside the source video and writes to public Downloads/VXPlayer/subtitles for file manager access.
 */
package com.example.mxoffline.subtitles.srt

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.provider.MediaStore
import com.example.mxoffline.model.VideoItem
import com.example.mxoffline.subtitles.model.SubtitleSegment
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

object SrtWriter {

    fun formatTimestamp(millis: Long): String {
        val totalSec = millis / 1000
        val ms = millis % 1000
        val hours = totalSec / 3600
        val minutes = (totalSec % 3600) / 60
        val seconds = totalSec % 60
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, ms)
    }

    fun buildSrtContent(segments: List<SubtitleSegment>): String {
        val sb = StringBuilder()
        segments.forEachIndexed { index, seg ->
            sb.append(index + 1).append("\n")
            sb.append(formatTimestamp(seg.startMs)).append(" --> ").append(formatTimestamp(seg.endMs)).append("\n")
            sb.append(seg.text.trim()).append("\n\n")
        }
        return sb.toString()
    }

    fun saveSubtitles(
        context: Context,
        video: VideoItem,
        segments: List<SubtitleSegment>
    ): File {
        val srtContent = buildSrtContent(segments)
        val cleanName = video.name.substringBeforeLast('.')
        var primaryFile: File? = null

        // 1. Try saving alongside the video file in the same directory
        val realVideoPath = resolveVideoPath(context, video)
        if (!realVideoPath.isNullOrBlank()) {
            val videoParentDir = File(realVideoPath).parentFile
            if (videoParentDir != null && (videoParentDir.canWrite() || videoParentDir.exists())) {
                val srtInVideoDir = File(videoParentDir, "$cleanName.srt")
                val writeSuccess = runCatching {
                    FileOutputStream(srtInVideoDir).use { it.write(srtContent.toByteArray(Charsets.UTF_8)) }
                    MediaScannerConnection.scanFile(context, arrayOf(srtInVideoDir.absolutePath), null, null)
                    true
                }.getOrDefault(false)

                if (writeSuccess && srtInVideoDir.exists() && srtInVideoDir.length() > 0) {
                    primaryFile = srtInVideoDir
                }
            }
        }

        // 2. Also write a copy to public Downloads/VXPlayer/subtitles for easy user file manager access
        runCatching {
            val publicDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "VXPlayer/subtitles"
            ).apply { if (!exists()) mkdirs() }
            val publicSrt = File(publicDir, "$cleanName.srt")
            FileOutputStream(publicSrt).use { it.write(srtContent.toByteArray(Charsets.UTF_8)) }
            MediaScannerConnection.scanFile(context, arrayOf(publicSrt.absolutePath), null, null)
            if (primaryFile == null) {
                primaryFile = publicSrt
            }
        }

        // 3. Fallback to app subtitles directory if public storage is unavailable
        if (primaryFile == null) {
            val subDir = File(context.getExternalFilesDir("subtitles") ?: context.filesDir, "subtitles").apply {
                if (!exists()) mkdirs()
            }
            val targetFile = File(subDir, "$cleanName.srt")
            FileOutputStream(targetFile).use { it.write(srtContent.toByteArray(Charsets.UTF_8)) }
            primaryFile = targetFile
        }

        return primaryFile
    }

    private fun resolveVideoPath(context: Context, video: VideoItem): String? {
        if (video.uri.scheme == "file") {
            return video.uri.path
        }
        if (video.uri.scheme == "content") {
            runCatching {
                context.contentResolver.query(
                    video.uri,
                    arrayOf(MediaStore.Video.Media.DATA),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    val col = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                    if (cursor.moveToFirst() && col >= 0) {
                        return cursor.getString(col)
                    }
                }
            }
        }
        return null
    }
}
