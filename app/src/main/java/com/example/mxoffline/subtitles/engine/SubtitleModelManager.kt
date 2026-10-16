/**
 * Role: Offline speech recognition model manager and file locator.
 * Responsibility: Tracks local Whisper model files and persists custom model paths.
 * Details: Scans VXPlayer storage directories and app files for quantized Whisper binaries.
 */
package com.example.mxoffline.subtitles.engine

import android.content.Context
import android.os.Environment
import java.io.File
import java.util.Locale

object SubtitleModelManager {

    private const val PREFS_NAME = "subtitle_ai_config"
    private const val KEY_MODEL_PATH = "custom_model_path"
    val SUPPORTED_MODEL_NAMES = listOf(
        "ggml-base.bin",
        "whisper-base.bin",
        "whisper-base.onnx",
        "ggml-tiny.bin",
        "whisper-tiny.bin",
        "ggml-model.bin"
    )

    fun getAvailableModels(context: Context): List<File> {
        val found = mutableListOf<File>()
        val searchDirs = listOf(
            File(context.getExternalFilesDir("models") ?: context.filesDir, "models"),
            File(context.filesDir, "models"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "VXPlayer"),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            File(Environment.getExternalStorageDirectory(), "VXPlayer/models")
        )

        for (dir in searchDirs) {
            if (dir.exists() && dir.isDirectory) {
                val files = dir.listFiles() ?: continue
                for (file in files) {
                    if (file.isFile && file.length() > 5_000_000L) { // Min 5MB
                        val lower = file.name.lowercase(Locale.US)
                        val isSupported = SUPPORTED_MODEL_NAMES.contains(lower) ||
                                lower.startsWith("ggml-") ||
                                lower.startsWith("whisper-") ||
                                lower.endsWith(".bin")
                        if (isSupported && found.none { it.name.equals(file.name, ignoreCase = true) }) {
                            found.add(file)
                        }
                    }
                }
            }
        }

        // Include any custom persisted path if present and valid
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val custom = prefs.getString(KEY_MODEL_PATH, null)
        if (!custom.isNullOrBlank()) {
            val f = File(custom)
            if (f.exists() && f.length() > 0 && found.none { it.absolutePath == f.absolutePath }) {
                found.add(0, f)
            }
        }

        return found
    }

    fun getActiveModel(context: Context): File? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val customPath = prefs.getString(KEY_MODEL_PATH, null)
        if (!customPath.isNullOrBlank()) {
            val f = File(customPath)
            if (f.exists() && f.length() > 0) return f
        }

        val available = getAvailableModels(context)
        return available.firstOrNull()
    }

    fun getModelFile(context: Context): File? = getActiveModel(context)

    fun setActiveModel(context: Context, file: File) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODEL_PATH, file.absolutePath)
            .apply()
    }

    fun isModelAvailable(context: Context): Boolean = getActiveModel(context) != null

    fun formatModelDisplayName(file: File): String {
        val name = file.name.lowercase(Locale.US)
        val sizeMb = file.length() / (1024 * 1024)
        val title = when {
            name.contains("base") -> "Whisper Base"
            name.contains("tiny") -> "Whisper Tiny"
            name.contains("small") -> "Whisper Small"
            else -> file.nameWithoutExtension.replaceFirstChar { it.uppercase(Locale.US) }
        }
        return "$title (${file.name} • ${sizeMb}MB)"
    }

    fun getExpectedDirectory(context: Context): File {
        val dir = File(context.getExternalFilesDir("models") ?: context.filesDir, "models")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
}
