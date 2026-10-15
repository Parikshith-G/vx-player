/**
 * Role: SubRip (.srt) subtitle serializer and file writer.
 * Responsibility: Converts timestamped subtitle segments into standard .srt format and saves to storage.
 * Details: Formats timestamps as HH:mm:ss,SSS and handles directory creation with internal fallback.
 */
package com.example.mxoffline.subtitles.srt

import android.content.Context
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

        // 1. Try saving in the same directory as the video if accessible
        if (video.uri.scheme == "file") {
            val videoPath = video.uri.path
            if (!videoPath.isNullOrBlank()) {
                val parentDir = File(videoPath).parentFile
                if (parentDir != null && parentDir.canWrite()) {
                    val srtFile = File(parentDir, "$cleanName.srt")
                    runCatching {
                        FileOutputStream(srtFile).use { it.write(srtContent.toByteArray(Charsets.UTF_8)) }
                        return srtFile
                    }
                }
            }
        }

        // 2. Fallback to app external or internal subtitles storage
        val subDir = File(context.getExternalFilesDir("subtitles") ?: context.filesDir, "subtitles").apply {
            if (!exists()) mkdirs()
        }
        val targetFile = File(subDir, "${cleanName}_${video.id.coerceAtLeast(0L)}.srt")
        FileOutputStream(targetFile).use { it.write(srtContent.toByteArray(Charsets.UTF_8)) }
        return targetFile
    }
}
