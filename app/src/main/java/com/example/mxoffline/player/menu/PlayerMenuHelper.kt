/**
 * Role: Main overflow hamburger menu and settings dialog coordinator for the player.
 * Responsibility: Displays options for speed, aspect ratio, orientation, tracks, decoders, and backup.
 * Details: Routes user menu selections to their corresponding player controllers and dialog helpers.
 */
package com.example.mxoffline.player.menu

import android.app.Activity
import android.app.AlertDialog
import android.widget.Toast
import com.example.mxoffline.util.AppBackupManager

object PlayerMenuHelper {

    interface Callback {
        fun onCustomizeQuickButtons()
        fun onSpeedDialog()
        fun onCycleAspectRatio()
        fun onCycleOrientation()
        fun onAudioTrackDialog()
        fun onSubtitleDialog()
        fun onToggleDecoder()
        fun onSleepTimerDialog()
        fun onToggleBackgroundPlay(): Boolean
        fun onVideoInfoDialog()
        fun onEnterPip()
        fun onRestoreSettings()
    }

    fun showHamburgerMenu(activity: Activity, isBgPlayEnabled: Boolean, callback: Callback) {
        val items = arrayOf(
            "⚙ Customize Quick Buttons",
            "Playback Speed",
            "Screen Resize (Fit/Fill/Zoom)",
            "Screen Orientation",
            "Audio Tracks",
            "Subtitles",
            "HW / SW Decoder",
            "Sleep Timer",
            "Background Play: ${if (isBgPlayEnabled) "On" else "Off"}",
            "Video Information",
            "Picture-in-Picture",
            "💾 Backup & Restore (Survives Uninstall)"
        )
        AlertDialog.Builder(activity)
            .setTitle("Player Settings")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> callback.onCustomizeQuickButtons()
                    1 -> callback.onSpeedDialog()
                    2 -> callback.onCycleAspectRatio()
                    3 -> callback.onCycleOrientation()
                    4 -> callback.onAudioTrackDialog()
                    5 -> callback.onSubtitleDialog()
                    6 -> callback.onToggleDecoder()
                    7 -> callback.onSleepTimerDialog()
                    8 -> callback.onToggleBackgroundPlay()
                    9 -> callback.onVideoInfoDialog()
                    10 -> callback.onEnterPip()
                    11 -> showBackupRestoreDialog(activity) { callback.onRestoreSettings() }
                }
            }
            .show()
    }

    fun showBackupRestoreDialog(activity: Activity, onRestore: () -> Unit) {
        val options = arrayOf(
            "💾 Backup All Settings & History Now",
            "📥 Restore from Storage Backup"
        )
        AlertDialog.Builder(activity)
            .setTitle("Persistent Backup & Restore")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> AppBackupManager.backupToStorageAsync(activity) { success ->
                        activity.runOnUiThread {
                            Toast.makeText(
                                activity,
                                if (success) "Backed up to Downloads/${AppBackupManager.BACKUP_FILENAME}" else "Could not write backup file",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                    1 -> {
                        if (AppBackupManager.autoRestoreIfAvailable(activity)) {
                            onRestore()
                            Toast.makeText(activity, "Settings & Seen history restored!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(activity, "No backup file found in Downloads or Documents", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }
}
