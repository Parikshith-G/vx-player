package com.example.mxoffline.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.Executors

object AppBackupManager {
    const val BACKUP_FILENAME = "vxplayer_backup.json"
    private val executor = Executors.newSingleThreadExecutor()

    fun createBackupJson(context: Context): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("timestamp", System.currentTimeMillis())

        // 1. Settings (Speed, Orientation, Aspect, Time Modes, Decoder, Background Play)
        val settingsPrefs = context.getSharedPreferences("player_settings", Context.MODE_PRIVATE)
        val settingsObj = JSONObject()
        settingsObj.put("playback_speed", settingsPrefs.getFloat("playback_speed", 1.0f).toDouble())
        settingsObj.put("orientation_mode", settingsPrefs.getInt("orientation_mode", 1))
        settingsObj.put("aspect_mode", settingsPrefs.getInt("aspect_mode", 0))
        settingsObj.put("top_time_mode", settingsPrefs.getInt("top_time_mode", 1))
        settingsObj.put("show_remaining_time", settingsPrefs.getBoolean("show_remaining_time", true))
        settingsObj.put("sw_decoder", settingsPrefs.getBoolean("sw_decoder", false))
        settingsObj.put("bg_play", settingsPrefs.getBoolean("bg_play", false))
        settingsObj.put("hold_speed", settingsPrefs.getFloat("hold_speed", 2.0f).toDouble())
        val pinned = settingsPrefs.getStringSet("pinned_buttons", null)
        if (pinned != null) {
            settingsObj.put("pinned_buttons", JSONArray(pinned))
        }
        root.put("settings", settingsObj)

        // 2. Seen videos history
        val seenPrefs = context.getSharedPreferences("player_seen", Context.MODE_PRIVATE)
        val seenObj = JSONObject()
        for ((key, value) in seenPrefs.all) {
            if (value is Long) seenObj.put(key, value)
            else if (value is Number) seenObj.put(key, value.toLong())
        }
        root.put("seen", seenObj)

        // 3. Resume positions
        val resumePrefs = context.getSharedPreferences("player_resume", Context.MODE_PRIVATE)
        val resumeObj = JSONObject()
        for ((key, value) in resumePrefs.all) {
            if (value is Long) resumeObj.put(key, value)
            else if (value is Number) resumeObj.put(key, value.toLong())
        }
        root.put("resume", resumeObj)

        // 4. Library settings (sort preset, etc.)
        val libraryPrefs = context.getSharedPreferences("library_prefs", Context.MODE_PRIVATE)
        val libObj = JSONObject()
        for ((key, value) in libraryPrefs.all) {
            libObj.put(key, value)
        }
        root.put("library", libObj)

        return root.toString(2)
    }

    fun restoreFromJson(context: Context, jsonString: String): Boolean {
        return runCatching {
            val root = JSONObject(jsonString)

            // 1. Restore Settings
            if (root.has("settings")) {
                val settingsObj = root.getJSONObject("settings")
                val editor = context.getSharedPreferences("player_settings", Context.MODE_PRIVATE).edit()
                if (settingsObj.has("playback_speed")) editor.putFloat("playback_speed", settingsObj.getDouble("playback_speed").toFloat())
                if (settingsObj.has("orientation_mode")) editor.putInt("orientation_mode", settingsObj.getInt("orientation_mode"))
                if (settingsObj.has("aspect_mode")) editor.putInt("aspect_mode", settingsObj.getInt("aspect_mode"))
                if (settingsObj.has("top_time_mode")) editor.putInt("top_time_mode", settingsObj.getInt("top_time_mode"))
                if (settingsObj.has("show_remaining_time")) editor.putBoolean("show_remaining_time", settingsObj.getBoolean("show_remaining_time"))
                if (settingsObj.has("sw_decoder")) editor.putBoolean("sw_decoder", settingsObj.getBoolean("sw_decoder"))
                if (settingsObj.has("bg_play")) editor.putBoolean("bg_play", settingsObj.getBoolean("bg_play"))
                if (settingsObj.has("hold_speed")) editor.putFloat("hold_speed", settingsObj.getDouble("hold_speed").toFloat())
                if (settingsObj.has("pinned_buttons")) {
                    val arr = settingsObj.getJSONArray("pinned_buttons")
                    val set = HashSet<String>()
                    for (i in 0 until arr.length()) set.add(arr.getString(i))
                    editor.putStringSet("pinned_buttons", set)
                }
                editor.apply()
            }

            // 2. Restore Seen videos
            if (root.has("seen")) {
                val seenObj = root.getJSONObject("seen")
                val editor = context.getSharedPreferences("player_seen", Context.MODE_PRIVATE).edit()
                val keys = seenObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    editor.putLong(k, seenObj.getLong(k))
                }
                editor.apply()
            }

            // 3. Restore Resume positions
            if (root.has("resume")) {
                val resumeObj = root.getJSONObject("resume")
                val editor = context.getSharedPreferences("player_resume", Context.MODE_PRIVATE).edit()
                val keys = resumeObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    editor.putLong(k, resumeObj.getLong(k))
                }
                editor.apply()
            }

            // 4. Restore Library settings
            if (root.has("library")) {
                val libObj = root.getJSONObject("library")
                val editor = context.getSharedPreferences("library_prefs", Context.MODE_PRIVATE).edit()
                val keys = libObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    when (val v = libObj.get(k)) {
                        is String -> editor.putString(k, v)
                        is Boolean -> editor.putBoolean(k, v)
                        is Int -> editor.putInt(k, v)
                        is Long -> editor.putLong(k, v)
                        is Double -> editor.putFloat(k, v.toFloat())
                    }
                }
                editor.apply()
            }
            true
        }.getOrDefault(false)
    }

    /**
     * Automatically backs up all preferences and seen status to public storage
     * in the background so it survives app uninstallation.
     */
    fun backupToStorageAsync(context: Context, onComplete: ((Boolean) -> Unit)? = null) {
        val appContext = context.applicationContext
        executor.execute {
            var success = false
            runCatching {
                val json = createBackupJson(appContext)

                // 1. Try public Download and Documents directories
                val publicDirs = listOf(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                )
                for (dir in publicDirs) {
                    runCatching {
                        if (!dir.exists()) dir.mkdirs()
                        val file = File(dir, BACKUP_FILENAME)
                        file.writeText(json)
                        success = true
                    }
                }

                // 2. On Android 10+, also write via MediaStore.Downloads
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val mediaStoreSuccess = writeToMediaStoreDownloads(appContext, json)
                    if (mediaStoreSuccess) success = true
                }
            }
            onComplete?.invoke(success)
        }
    }

    private fun writeToMediaStoreDownloads(context: Context, json: String): Boolean {
        return runCatching {
            val resolver = context.contentResolver
            val projection = arrayOf(MediaStore.MediaColumns._ID)
            val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf(BACKUP_FILENAME)

            var existingUri: Uri? = null
            resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    existingUri = Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id.toString())
                }
            }

            val targetUri = existingUri ?: run {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, BACKUP_FILENAME)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            }

            if (targetUri != null) {
                resolver.openOutputStream(targetUri, "rwt")?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                    out.flush()
                }
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }

    /**
     * Checks if backup exists on public storage and restores preferences and seen history.
     */
    fun autoRestoreIfAvailable(context: Context): Boolean {
        val appContext = context.applicationContext
        return runCatching {
            val filesToCheck = listOf(
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), BACKUP_FILENAME),
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), BACKUP_FILENAME)
            )

            var jsonContent: String? = null
            for (file in filesToCheck) {
                if (file.exists() && file.canRead()) {
                    val text = file.readText()
                    if (text.isNotBlank()) {
                        jsonContent = text
                        break
                    }
                }
            }

            // On Android 10+, also check MediaStore Downloads if file not found yet
            if (jsonContent == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching {
                    val resolver = appContext.contentResolver
                    val projection = arrayOf(MediaStore.MediaColumns._ID)
                    val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
                    val selectionArgs = arrayOf(BACKUP_FILENAME)
                    resolver.query(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        projection,
                        selection,
                        selectionArgs,
                        null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                            val uri = Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id.toString())
                            resolver.openInputStream(uri)?.use { stream ->
                                jsonContent = stream.bufferedReader().readText()
                            }
                        }
                    }
                }
            }

            if (!jsonContent.isNullOrBlank()) {
                restoreFromJson(appContext, jsonContent!!)
            } else {
                false
            }
        }.getOrDefault(false)
    }

    fun exportToStream(context: Context, outputStream: OutputStream) {
        val json = createBackupJson(context)
        outputStream.write(json.toByteArray(Charsets.UTF_8))
        outputStream.flush()
    }

    fun importFromStream(context: Context, inputStream: InputStream): Boolean {
        val json = inputStream.bufferedReader().readText()
        return restoreFromJson(context, json)
    }
}
