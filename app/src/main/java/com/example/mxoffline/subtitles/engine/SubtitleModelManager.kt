/**
 * Role: Offline speech recognition model manager and file locator.
 * Responsibility: Tracks local Whisper model files and persists custom model paths.
 * Details: Scans VXPlayer storage directories, public Downloads, and app files for Whisper binaries.
 */
package com.example.mxoffline.subtitles.engine

import android.content.Context
import android.os.Build
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

    fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    fun isValidModelFile(file: File): Boolean {
        if (!file.isFile || file.length() < 5_000_000L) return false
        val lower = file.name.lowercase(Locale.US)
        if (lower.endsWith(".crdownload") || lower.endsWith(".part") ||
            lower.endsWith(".tmp") || lower.endsWith(".temp") || lower.endsWith(".download")) {
            return false
        }
        return SUPPORTED_MODEL_NAMES.contains(lower) ||
                lower.startsWith("ggml-") ||
                lower.startsWith("whisper-") ||
                lower.endsWith(".bin") ||
                lower.endsWith(".onnx")
    }

    fun getCandidateDirectories(context: Context): List<File> {
        val dirs = LinkedHashSet<File>()

        // 1. App-specific storage (Always accessible regardless of Scoped Storage)
        context.getExternalFilesDir("models")?.let {
            dirs.add(it)
            dirs.add(File(it, "models"))
        }
        context.getExternalFilesDir(null)?.let {
            dirs.add(it)
            dirs.add(File(it, "models"))
            dirs.add(File(it, "vxplayer"))
            dirs.add(File(it, "VXPlayer"))
        }
        dirs.add(File(context.filesDir, "models"))
        dirs.add(context.filesDir)

        // 2. Public downloads & storage directories (All casing and plural variations)
        val extStorage = Environment.getExternalStorageDirectory()
        val pubDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (pubDownloads != null) {
            dirs.add(pubDownloads)
            dirs.add(File(pubDownloads, "vxplayer"))
            dirs.add(File(pubDownloads, "VXPlayer"))
            dirs.add(File(pubDownloads, "models"))
        }

        val dlFolders = listOf("Download", "Downloads", "download", "downloads")
        val appFolders = listOf("vxplayer", "VXPlayer", "vx-player", "models")

        for (dl in dlFolders) {
            val dlDir = File(extStorage, dl)
            dirs.add(dlDir)
            for (app in appFolders) {
                dirs.add(File(dlDir, app))
                dirs.add(File(dlDir, "$app/models"))
            }
        }

        // Direct storage root variations
        for (app in appFolders) {
            dirs.add(File(extStorage, app))
            dirs.add(File(extStorage, "$app/models"))
        }

        return dirs.toList()
    }

    fun getAvailableModels(context: Context): List<File> {
        val found = mutableListOf<File>()
        val searchDirs = getCandidateDirectories(context)

        for (dir in searchDirs) {
            if (dir.exists() && dir.isDirectory) {
                val files = dir.listFiles() ?: continue
                for (file in files) {
                    if (isValidModelFile(file) && found.none { it.name.equals(file.name, ignoreCase = true) }) {
                        found.add(file)
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
            name.contains("medium") -> "Whisper Medium"
            name.contains("large") -> "Whisper Large"
            else -> file.nameWithoutExtension.replaceFirstChar { it.uppercase(Locale.US) }
        }
        return "$title (${file.name} • ${sizeMb}MB)"
    }

    fun getExpectedDirectory(context: Context): File {
        val dir = context.getExternalFilesDir("models") ?: File(context.filesDir, "models")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
}
