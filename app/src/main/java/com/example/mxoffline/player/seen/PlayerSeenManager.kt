/**
 * Role: Seen video state tracker and persistence controller.
 * Responsibility: Records completed and watched videos in SharedPreferences and manages button state.
 * Details: Coordinates automatic backup of seen status across app reinstalls and updates UI checkmarks.
 */
package com.example.mxoffline.player.seen

import android.content.Context
import android.content.SharedPreferences
import android.widget.TextView
import com.example.mxoffline.util.AppBackupManager

class PlayerSeenManager(
    private val context: Context,
    private val seenPrefs: SharedPreferences
) {

    fun isSeen(uriString: String?): Boolean {
        if (uriString.isNullOrEmpty()) return false
        return seenPrefs.contains("seen_$uriString")
    }

    fun isCurrentVideoSeen(uris: List<String>, index: Int): Boolean {
        if (uris.isEmpty() || index !in uris.indices) return false
        return isSeen(uris[index])
    }

    fun markCurrentVideoAsSeen(uris: List<String>, index: Int) {
        if (uris.isEmpty() || index !in uris.indices) return
        val currentUri = uris[index]
        seenPrefs.edit().putLong("seen_$currentUri", System.currentTimeMillis()).apply()
        AppBackupManager.backupToStorageAsync(context)
    }

    fun toggleMarkCurrentVideoAsSeen(uris: List<String>, index: Int): Boolean {
        if (uris.isEmpty() || index !in uris.indices) return false
        val currentUri = uris[index]
        val key = "seen_$currentUri"
        val nowSeen = if (seenPrefs.contains(key)) {
            seenPrefs.edit().remove(key).apply()
            false
        } else {
            seenPrefs.edit().putLong(key, System.currentTimeMillis()).apply()
            true
        }
        AppBackupManager.backupToStorageAsync(context)
        return nowSeen
    }

    fun updateMarkDoneButtonState(button: TextView?, uris: List<String>, index: Int) {
        if (button == null) return
        val seen = isCurrentVideoSeen(uris, index)
        button.setTextColor(0xff00e676.toInt())
        button.alpha = if (seen) 1.0f else 0.45f
    }
}
