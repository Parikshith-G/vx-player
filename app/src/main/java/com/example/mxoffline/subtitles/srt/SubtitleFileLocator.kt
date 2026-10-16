/**
 * Role: Subtitle file locator and automatic matching engine.
 * Responsibility: Discovers matching .srt/.vtt subtitle files for local video playback.
 * Details: Searches video directory, public Downloads/VXPlayer/subtitles, and dedicated app subtitle cache.
 */
package com.example.mxoffline.subtitles.srt

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File

object SubtitleFileLocator {

    private val SUPPORTED_EXTS = listOf("srt", "vtt", "ass", "ssa")

    fun findSubtitleForVideo(context: Context, videoUri: Uri, videoName: String, videoId: Long = 0L): Uri? {
        val cleanName = videoName.substringBeforeLast('.')

        // 1. Check same directory as video if real path can be resolved
        val realPath = resolveVideoPath(context, videoUri)
        if (!realPath.isNullOrBlank()) {
            val parent = File(realPath).parentFile
            if (parent != null && parent.exists()) {
                for (ext in SUPPORTED_EXTS) {
                    val subFile = File(parent, "$cleanName.$ext")
                    if (subFile.exists() && subFile.length() > 0) {
                        return Uri.fromFile(subFile)
                    }
                }
            }
        }

        // 2. Check public Downloads/VXPlayer/subtitles folder
        val publicDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "VXPlayer/subtitles"
        )
        if (publicDir.exists() && publicDir.isDirectory) {
            for (ext in SUPPORTED_EXTS) {
                val pubFile = File(publicDir, "$cleanName.$ext")
                if (pubFile.exists() && pubFile.length() > 0) {
                    return Uri.fromFile(pubFile)
                }
            }
        }

        // 3. Check internal / external app subtitles folder
        val subDir = File(context.getExternalFilesDir("subtitles") ?: context.filesDir, "subtitles")
        if (subDir.exists() && subDir.isDirectory) {
            val candidates = listOf(
                File(subDir, "$cleanName.srt"),
                File(subDir, "${cleanName}_${videoId.coerceAtLeast(0L)}.srt"),
                File(subDir, "$cleanName.vtt"),
                File(subDir, "${cleanName}_${videoId.coerceAtLeast(0L)}.vtt")
            )
            for (f in candidates) {
                if (f.exists() && f.length() > 0) {
                    return Uri.fromFile(f)
                }
            }
        }

        return null
    }

    private fun resolveVideoPath(context: Context, videoUri: Uri): String? {
        if (videoUri.scheme == "file") return videoUri.path
        if (videoUri.scheme == "content") {
            runCatching {
                context.contentResolver.query(
                    videoUri,
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
