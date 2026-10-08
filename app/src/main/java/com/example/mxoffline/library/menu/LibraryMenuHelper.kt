/**
 * Role: Context and overflow menu dialog provider.
 * Responsibility: Displays backup, restore, file export/import, and about dialogs.
 * Details: Connects backup/restore actions to AppBackupManager and provides user feedback toasts.
 */
package com.example.mxoffline.library.menu

import android.app.Activity
import android.app.AlertDialog
import android.widget.Toast
import com.example.mxoffline.util.AppBackupManager

object LibraryMenuHelper {

    fun showMoreMenu(
        activity: Activity,
        onExport: (() -> Unit)? = null,
        onImport: (() -> Unit)? = null,
        onRestoreSuccess: () -> Unit
    ) {
        val options = arrayOf(
            "💾 Backup Data to Storage (Survives Uninstall)",
            "📥 Restore Data from Storage",
            "📤 Export Backup File (Choose Location)...",
            "📂 Import Backup File (Choose File)...",
            "ℹ About VX Player"
        )
        AlertDialog.Builder(activity)
            .setTitle("Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> AppBackupManager.backupToStorageAsync(activity) { ok ->
                        activity.runOnUiThread {
                            val path = "Downloads/${AppBackupManager.BACKUP_DIR_NAME}/${AppBackupManager.BACKUP_FILENAME}"
                            Toast.makeText(
                                activity,
                                if (ok) "Backed up to $path" else "Could not write backup file",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    1 -> {
                        if (AppBackupManager.autoRestoreIfAvailable(activity)) {
                            onRestoreSuccess()
                            Toast.makeText(activity, "Preferences & Seen history restored!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(activity, "No backup found in Downloads/${AppBackupManager.BACKUP_DIR_NAME}/", Toast.LENGTH_SHORT).show()
                        }
                    }
                    2 -> onExport?.invoke() ?: Toast.makeText(activity, "Export unavailable", Toast.LENGTH_SHORT).show()
                    3 -> onImport?.invoke() ?: Toast.makeText(activity, "Import unavailable", Toast.LENGTH_SHORT).show()
                    4 -> AlertDialog.Builder(activity)
                        .setTitle("VX Player")
                        .setMessage("Version 1.0\n100% Offline & Private.\nAll data is kept on your device and backed up to Downloads/${AppBackupManager.BACKUP_DIR_NAME}/${AppBackupManager.BACKUP_FILENAME}.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun showAccessError(activity: Activity, error: Throwable, onChooseFolder: () -> Unit) {
        AlertDialog.Builder(activity)
            .setTitle("Cannot open folder")
            .setMessage(error.localizedMessage ?: "Folder access may have been removed.")
            .setPositiveButton("Choose Folder") { _, _ -> onChooseFolder() }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
