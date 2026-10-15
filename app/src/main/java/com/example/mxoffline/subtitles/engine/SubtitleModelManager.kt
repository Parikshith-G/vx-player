/**
 * Role: Offline speech recognition model manager and file locator.
 * Responsibility: Tracks local Whisper model files and persists custom model paths.
 * Details: Scans VXPlayer storage directories and app files for quantized Whisper binaries.
 */
package com.example.mxoffline.subtitles.engine

import android.content.Context
import android.net.Uri
import android.os.Environment
import java.io.File

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

    fun getModelFile(context: Context): File? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val customPath = prefs.getString(KEY_MODEL_PATH, null)
        if (!customPath.isNullOrBlank()) {
            val f = File(customPath)
            if (f.exists() && f.length() > 0) return f
        }

        val searchDirs = listOf(
            File(context.getExternalFilesDir("models") ?: context.filesDir, "models"),
            File(context.filesDir, "models"),
            File(Environment.getExternalStorageDirectory(), "VXPlayer/models"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "VXPlayer"),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        )

        for (dir in searchDirs) {
            if (dir.exists() && dir.isDirectory) {
                for (name in SUPPORTED_MODEL_NAMES) {
                    val candidate = File(dir, name)
                    if (candidate.exists() && candidate.length() > 10_000_000L) { // At least 10MB
                        return candidate
                    }
                }
            }
        }
        return null
    }

    fun setCustomModelPath(context: Context, path: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODEL_PATH, path)
            .apply()
    }

    fun isModelAvailable(context: Context): Boolean {
        return getModelFile(context) != null
    }

    fun getExpectedDirectory(context: Context): File {
        val dir = File(context.getExternalFilesDir("models") ?: context.filesDir, "models")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
}
