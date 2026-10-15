/**
 * Role: Missing AI model prompt dialog coordinator.
 * Responsibility: Detects missing offline speech models and displays interactive download guidance.
 * Details: Provides direct links to standard Whisper GGML binaries and handles fallback clipboard copy.
 */
package com.example.mxoffline.subtitles.dialog

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.example.mxoffline.subtitles.engine.SubtitleModelManager

object SubtitleModelPromptHelper {

    const val WHISPER_BASE_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin"
    const val WHISPER_TINY_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin"

    fun checkModelOrPrompt(
        activity: Activity,
        onReady: () -> Unit
    ) {
        if (SubtitleModelManager.isModelAvailable(activity)) {
            onReady()
            return
        }
        showModelRequiredDialog(activity)
    }

    fun showModelRequiredDialog(activity: Activity) {
        val msg = """
            To generate subtitles 100% offline with zero cloud dependency, VX Player requires a local Whisper speech recognition model.

            Recommended Models:
            • Whisper Base (ggml-base.bin ~140 MB) - High accuracy multilingual
            • Whisper Tiny (ggml-tiny.bin ~75 MB) - Ultra-fast, lower RAM usage

            Tap 'Download Base Model' to download. Once downloaded, place the file in your 'Downloads/VXPlayer' folder, then tap Generate again.
        """.trimIndent()

        AlertDialog.Builder(activity)
            .setTitle("✨ AI Subtitle Model Needed")
            .setMessage(msg)
            .setPositiveButton("Download Base Model (~140MB)") { _, _ ->
                openDownloadLink(activity, WHISPER_BASE_URL)
            }
            .setNeutralButton("Download Tiny (~75MB)") { _, _ ->
                openDownloadLink(activity, WHISPER_TINY_URL)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openDownloadLink(activity: Activity, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        runCatching {
            activity.startActivity(intent)
            Toast.makeText(activity, "Opening model download link...", Toast.LENGTH_SHORT).show()
        }.onFailure {
            val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText("Whisper Model Link", url))
            Toast.makeText(activity, "Download link copied to clipboard!", Toast.LENGTH_LONG).show()
        }
    }
}
