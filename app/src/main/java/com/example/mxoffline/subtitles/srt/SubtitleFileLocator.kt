/**
 * Role: Subtitle file locator and automatic matching engine.
 * Responsibility: Discovers matching .srt/.vtt subtitle files for local video playback.
 * Details: Searches parent directories, SAF document trees, and dedicated app subtitle cache.
 */
package com.example.mxoffline.subtitles.srt

import android.content.Context
import android.net.Uri
import java.io.File

object SubtitleFileLocator {

    fun findSubtitleForVideo(context: Context, videoUri: Uri, videoName: String, videoId: Long = 0L): Uri? {
        val cleanName = videoName.substringBeforeLast('.')

        // 1. Check same directory if file scheme
        if (videoUri.scheme == "file") {
            val videoPath = videoUri.path
            if (!videoPath.isNullOrBlank()) {
                val parent = File(videoPath).parentFile
                if (parent != null && parent.exists()) {
                    for (ext in listOf("srt", "vtt", "ass", "ssa")) {
                        val subFile = File(parent, "$cleanName.$ext")
                        if (subFile.exists() && subFile.length() > 0) {
                            return Uri.fromFile(subFile)
                        }
                    }
                }
            }
        }

        // 2. Check internal / external app subtitles folder
        val subDir = File(context.getExternalFilesDir("subtitles") ?: context.filesDir, "subtitles")
        if (subDir.exists() && subDir.isDirectory) {
            val candidates = listOf(
                File(subDir, "${cleanName}_${videoId.coerceAtLeast(0L)}.srt"),
                File(subDir, "$cleanName.srt"),
                File(subDir, "${cleanName}_${videoId.coerceAtLeast(0L)}.vtt"),
                File(subDir, "$cleanName.vtt")
            )
            for (f in candidates) {
                if (f.exists() && f.length() > 0) {
                    return Uri.fromFile(f)
                }
            }
        }

        return null
    }
}
