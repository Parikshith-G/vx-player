/**
 * Role: Missing AI model prompt dialog, file picker coordinator, and model importer.
 * Responsibility: Detects missing offline speech models, offers system file picker import, and download guidance.
 * Details: Seamlessly coordinates SAF document picking, All Files Access settings, and multi-model switching.
 */
package com.example.mxoffline.subtitles.dialog

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Toast
import com.example.mxoffline.subtitles.engine.SubtitleModelManager
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

object SubtitleModelPromptHelper {

    const val WHISPER_BASE_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin"
    const val WHISPER_TINY_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin"

    @Volatile
    private var pendingOnReady: (() -> Unit)? = null

    fun clearPendingCallback() {
        pendingOnReady = null
    }

    fun checkModelOrPrompt(
        activity: Activity,
        onReady: () -> Unit
    ) {
        if (SubtitleModelManager.isModelAvailable(activity)) {
            onReady()
            return
        }
        pendingOnReady = onReady
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
            .setNeutralButton("Import / Add...") { _, _ ->
                showModelRequiredDialog(activity)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun showModelRequiredDialog(activity: Activity) {
        val options = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        options.add("📂 Select Model File (Already downloaded)")
        actions.add { launchModelPicker(activity) }

        options.add("🌐 Download Whisper Base (~140 MB)")
        actions.add { openDownloadLink(activity, WHISPER_BASE_URL) }

        options.add("🌐 Download Whisper Tiny (~75 MB)")
        actions.add { openDownloadLink(activity, WHISPER_TINY_URL) }

        if (!SubtitleModelManager.hasAllFilesAccess()) {
            options.add("🔑 Grant Storage Access (Auto-detect in Downloads)")
            actions.add { requestAllFilesAccess(activity) }
        }

        AlertDialog.Builder(activity)
            .setTitle("✨ AI Subtitle Model")
            .setItems(options.toTypedArray()) { _, which ->
                actions.getOrNull(which)?.invoke()
            }
            .setNegativeButton("Cancel") { _, _ ->
                clearPendingCallback()
            }
            .show()
    }

    fun launchModelPicker(activity: Activity) {
        val intent = Intent(activity, ModelPickerActivity::class.java)
        activity.startActivity(intent)
    }

    fun requestAllFilesAccess(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                activity.startActivity(intent)
            }.recoverCatching {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                activity.startActivity(intent)
            }
        }
    }

    fun importModelFromUri(
        context: Context,
        uri: Uri,
        onComplete: (File?) -> Unit
    ) {
        val executor = Executors.newSingleThreadExecutor()
        val handler = Handler(Looper.getMainLooper())

        executor.execute {
            var resultFile: File? = null
            runCatching {
                var fileName = "ggml-base.bin"
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    val col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && col >= 0) {
                        val n = cursor.getString(col)
                        if (!n.isNullOrBlank()) fileName = n
                    }
                }

                val modelsDir = SubtitleModelManager.getExpectedDirectory(context)
                val destFile = File(modelsDir, fileName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output, bufferSize = 64 * 1024)
                    }
                }

                if (destFile.exists() && SubtitleModelManager.isValidModelFile(destFile)) {
                    SubtitleModelManager.setActiveModel(context, destFile)
                    resultFile = destFile
                }
            }

            handler.post {
                val f = resultFile
                if (f != null) {
                    val callback = pendingOnReady
                    pendingOnReady = null
                    callback?.invoke()
                }
                onComplete(f)
            }
        }
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
