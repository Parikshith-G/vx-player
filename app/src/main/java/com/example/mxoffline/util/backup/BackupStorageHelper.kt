/**
 * Role: Public storage and MediaStore file I/O coordinator for backups.
 * Responsibility: Manages dedicated VXPlayer folder creation and reads/writes backup files.
 * Details: Handles scoped storage relative paths, multiple public directories, and MediaStore downloads.
 */
package com.example.mxoffline.util.backup

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

object BackupStorageHelper {
    const val BACKUP_DIR_NAME = "VXPlayer"
    const val BACKUP_FILENAME = "vxplayer_backup.json"

    fun writeToPublicStorage(json: String): Boolean {
        var anySuccess = false
        val baseDirs = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        )
        for (base in baseDirs) {
            runCatching {
                val subDir = File(base, BACKUP_DIR_NAME)
                if (!subDir.exists()) subDir.mkdirs()
                val targetFile = File(subDir, BACKUP_FILENAME)
                targetFile.writeText(json)
                anySuccess = true
            }
        }
        return anySuccess
    }

    fun writeToMediaStore(context: Context, json: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
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
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$BACKUP_DIR_NAME/")
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

    fun readExistingBackupFromStorage(context: Context): String? {
        val filesToCheck = listOf(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "$BACKUP_DIR_NAME/$BACKUP_FILENAME"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "$BACKUP_DIR_NAME/$BACKUP_FILENAME"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "$BACKUP_DIR_NAME/$BACKUP_FILENAME"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), BACKUP_FILENAME),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), BACKUP_FILENAME)
        )

        for (file in filesToCheck) {
            runCatching {
                if (file.exists() && file.canRead()) {
                    val text = file.readText()
                    if (text.isNotBlank()) return text
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val resolver = context.contentResolver
                val projection = arrayOf(MediaStore.MediaColumns._ID)
                val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE 'vxplayer_backup%.json'"
                val sortOrder = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
                resolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, selection, null, sortOrder)?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val uri = Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id.toString())
                        val content = resolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
                        if (!content.isNullOrBlank()) return content
                    }
                }
            }
        }
        return null
    }
}
