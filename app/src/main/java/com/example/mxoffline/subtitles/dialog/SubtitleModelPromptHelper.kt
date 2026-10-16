/**
 * Role: Missing AI model prompt dialog and model selection coordinator.
 * Responsibility: Detects missing offline speech models and displays interactive download guidance & model switcher.
 * Details: Provides direct links to standard Whisper GGML binaries, radio selection for installed models, and clipboard fallback.
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
import java.io.File

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

    fun showModelSelectionDialog(
        activity: Activity,
        onModelSelected: ((File) -> Unit)? = null
    ) {
        val models = SubtitleModelManager.getAvailableModels(activity)
        if (models.isEmpty()) {
            showModelRequiredDialog(activity)
            return
        }

        val active = SubtitleModelManager.getActiveModel(activity)
        var selectedIndex = models.indexOfFirst { it.absolutePath == active?.absolutePath }.coerceAtLeast(0)
        val items = models.map { SubtitleModelManager.formatModelDisplayName(it) }.toTypedArray()

        AlertDialog.Builder(activity)
            .setTitle("Select AI Subtitle Model")
            .setSingleChoiceItems(items, selectedIndex) { _, which ->
                selectedIndex = which
            }
            .setPositiveButton("Use Selected") { _, _ ->
                if (selectedIndex in models.indices) {
                    val chosen = models[selectedIndex]
                    SubtitleModelManager.setActiveModel(activity, chosen)
                    onModelSelected?.invoke(chosen)
                    Toast.makeText(
                        activity,
                        "Active model: ${chosen.name}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNeutralButton("Download Models") { _, _ ->
                showModelRequiredDialog(activity)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun showModelRequiredDialog(activity: Activity) {
        val msg = """
            To generate subtitles 100% offline with zero cloud dependency, VX Player requires a local Whisper speech recognition model.

            Available Models:
            • Whisper Base (ggml-base.bin ~140 MB) - Higher accuracy multilingual
            • Whisper Tiny (ggml-tiny.bin ~75 MB) - Ultra-fast, lower RAM usage

            Tap a button below to download the model file. Once downloaded, place the file in 'Downloads/VXPlayer' or 'Downloads' on your storage.
        """.trimIndent()

        AlertDialog.Builder(activity)
            .setTitle("✨ AI Subtitle Model")
            .setMessage(msg)
            .setPositiveButton("Download Base (~140MB)") { _, _ ->
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
