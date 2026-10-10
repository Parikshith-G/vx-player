/**
 * Role: Persistent data backup and restore manager across app uninstalls.
 * Responsibility: Coordinates serialization and public storage inside dedicated VXPlayer folders.
 * Details: Automatically restores preferences on clean installs and safely merges watch history.
 */
package com.example.mxoffline.util

import android.content.Context
import com.example.mxoffline.util.backup.BackupSerializer
import com.example.mxoffline.util.backup.BackupStorageHelper
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.Executors

object AppBackupManager {
    const val BACKUP_DIR_NAME = BackupStorageHelper.BACKUP_DIR_NAME
    const val BACKUP_FILENAME = BackupStorageHelper.BACKUP_FILENAME
    private val executor = Executors.newSingleThreadExecutor()

    fun createBackupJson(context: Context): String {
        return BackupSerializer.createBackupJson(context)
    }

    fun restoreFromJson(context: Context, jsonString: String): Boolean {
        return BackupSerializer.restoreFromJson(context, jsonString)
    }

    /**
     * Automatically backs up preferences and seen history to Downloads/VXPlayer/ and Documents/VXPlayer/
     * without overwriting existing history if the app was newly installed.
     */
    fun backupToStorageAsync(context: Context, onComplete: ((Boolean) -> Unit)? = null) {
        val appContext = context.applicationContext
        executor.execute {
            var success = false
            runCatching {
                val json = BackupSerializer.createBackupJson(appContext)
                val pubSuccess = BackupStorageHelper.writeToPublicStorage(json)
                val msSuccess = BackupStorageHelper.writeToMediaStore(appContext, json)
                success = pubSuccess || msSuccess
            }
            onComplete?.invoke(success)
        }
    }

    /**
     * Checks if backup exists in Downloads/VXPlayer/, Documents/VXPlayer/, or MediaStore and restores it.
     */
    fun autoRestoreIfAvailable(context: Context): Boolean {
        val appContext = context.applicationContext
        return runCatching {
            val jsonContent = BackupStorageHelper.readExistingBackupFromStorage(appContext)
            if (!jsonContent.isNullOrBlank()) {
                restoreFromJson(appContext, jsonContent)
            } else {
                false
            }
        }.getOrDefault(false)
    }

    fun autoRestoreAsync(context: Context, onComplete: ((Boolean) -> Unit)? = null) {
        val appContext = context.applicationContext
        executor.execute {
            val restored = autoRestoreIfAvailable(appContext)
            onComplete?.invoke(restored)
        }
    }

    fun clearBackupData(context: Context) {
        val appContext = context.applicationContext
        executor.execute {
            runCatching {
                val json = BackupSerializer.createBackupJson(appContext)
                BackupStorageHelper.writeToPublicStorage(json)
                BackupStorageHelper.writeToMediaStore(appContext, json)
            }
        }
    }

    fun exportToStream(context: Context, outputStream: OutputStream) {
        val json = BackupSerializer.createBackupJson(context)
        outputStream.write(json.toByteArray(Charsets.UTF_8))
        outputStream.flush()
    }

    fun importFromStream(context: Context, inputStream: InputStream): Boolean {
        val json = inputStream.bufferedReader().readText()
        return restoreFromJson(context, json)
    }
}
