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
import com.example.mxoffline.util.VideoIdentity

class PlayerSeenManager(
    private val context: Context,
    private val seenPrefs: SharedPreferences
) {

    fun isSeen(uriString: String?, name: String? = null, size: Long = 0L): Boolean {
        if (uriString.isNullOrEmpty() && name.isNullOrEmpty()) return false
        if (!uriString.isNullOrEmpty() && seenPrefs.contains(VideoIdentity.getUriKey("seen", uriString))) return true
        if (!name.isNullOrEmpty()) {
            if (size > 0 && seenPrefs.contains(VideoIdentity.getMetaKey("seen", name, size))) return true
            if (seenPrefs.contains(VideoIdentity.getNameKey("seen", name))) return true
        }
        return false
    }

    fun isCurrentVideoSeen(uris: List<String>, index: Int, names: List<String> = emptyList(), sizes: List<Long> = emptyList()): Boolean {
        if (uris.isEmpty() || index !in uris.indices) return false
        val uri = uris[index]
        val name = names.getOrNull(index)
        val size = sizes.getOrNull(index) ?: 0L
        return isSeen(uri, name, size)
    }

    fun markCurrentVideoAsSeen(uris: List<String>, index: Int, names: List<String> = emptyList(), sizes: List<Long> = emptyList()) {
        if (uris.isEmpty() || index !in uris.indices) return
        val currentUri = uris[index]
        val name = names.getOrNull(index) ?: ""
        val size = sizes.getOrNull(index) ?: 0L
        val now = System.currentTimeMillis()
        val editor = seenPrefs.edit()
        val keys = VideoIdentity.getAllKeysForVideo("seen", currentUri, name, size)
        for (k in keys) editor.putLong(k, now)
        editor.apply()
        AppBackupManager.backupToStorageAsync(context)
    }

    fun toggleMarkCurrentVideoAsSeen(uris: List<String>, index: Int, names: List<String> = emptyList(), sizes: List<Long> = emptyList()): Boolean {
        if (uris.isEmpty() || index !in uris.indices) return false
        val currentUri = uris[index]
        val name = names.getOrNull(index) ?: ""
        val size = sizes.getOrNull(index) ?: 0L
        val currentlySeen = isSeen(currentUri, name, size)
        val editor = seenPrefs.edit()
        val keys = VideoIdentity.getAllKeysForVideo("seen", currentUri, name, size)
        val nowSeen = if (currentlySeen) {
            for (k in keys) editor.remove(k)
            false
        } else {
            val now = System.currentTimeMillis()
            for (k in keys) editor.putLong(k, now)
            true
        }
        editor.apply()
        AppBackupManager.backupToStorageAsync(context)
        return nowSeen
    }

    fun updateMarkDoneButtonState(button: TextView?, uris: List<String>, index: Int, names: List<String> = emptyList(), sizes: List<Long> = emptyList()) {
        if (button == null) return
        val seen = isCurrentVideoSeen(uris, index, names, sizes)
        button.setTextColor(0xff00e676.toInt())
        button.alpha = if (seen) 1.0f else 0.45f
    }

    fun markCurrentVideoAsSeen(p: com.example.mxoffline.player.playlist.PlayerPlaylistController) = markCurrentVideoAsSeen(p.uris, p.index, p.names, p.sizes)
    fun toggleMarkCurrentVideoAsSeen(p: com.example.mxoffline.player.playlist.PlayerPlaylistController) = toggleMarkCurrentVideoAsSeen(p.uris, p.index, p.names, p.sizes)
    fun updateMarkDoneButtonState(button: TextView?, p: com.example.mxoffline.player.playlist.PlayerPlaylistController) = updateMarkDoneButtonState(button, p.uris, p.index, p.names, p.sizes)
}
