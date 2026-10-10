/**
 * Role: Playback position persistence and resume banner controller.
 * Responsibility: Stores playback timestamps in SharedPreferences and presents resume banner on playback start.
 * Details: Prompts user with "Resumed from 00:00" and a Restart button when starting videos with saved positions.
 */
package com.example.mxoffline.player

import android.content.SharedPreferences
import android.os.Handler
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.exoplayer.ExoPlayer
import com.example.mxoffline.util.PreferenceHelper
import com.example.mxoffline.util.TimeFormatter
import com.example.mxoffline.util.VideoIdentity

class PlayerResumeManager(
    private val prefs: SharedPreferences,
    private val handler: Handler,
    private val resumeBanner: LinearLayout,
    private val resumeText: TextView
) {
    private var lastCheckedIndex = -1
    private val hideBannerRunnable = Runnable { resumeBanner.visibility = View.GONE }

    fun getSavedPosition(uri: String, name: String = "", size: Long = 0L): Long {
        val keys = VideoIdentity.getAllKeysForVideo("pos", uri, name, size)
        var maxPos = 0L
        for (k in keys) {
            val p = PreferenceHelper.safeGetLong(prefs, k, 0L)
            if (p > maxPos) maxPos = p
        }
        return maxPos
    }

    fun checkAndApplyResume(player: ExoPlayer, uris: List<String>, index: Int, names: List<String> = emptyList(), sizes: List<Long> = emptyList()) {
        val currentIdx = player.currentMediaItemIndex
        if (lastCheckedIndex == currentIdx) return
        lastCheckedIndex = currentIdx

        val uri = uris.getOrNull(currentIdx) ?: return
        val name = names.getOrNull(currentIdx) ?: ""
        val size = sizes.getOrNull(currentIdx) ?: 0L
        val saved = getSavedPosition(uri, name, size)
        val duration = player.duration
        if (saved > 3000L && duration > 10000L && saved < duration - 5000L) {
            player.seekTo(saved)
            resumeText.text = "Resumed from ${TimeFormatter.formatTime(saved)}"
            resumeBanner.visibility = View.VISIBLE
            handler.removeCallbacks(hideBannerRunnable)
            handler.postDelayed(hideBannerRunnable, 4500)
        }
    }

    fun recordWatchTime(uris: List<String>, index: Int, names: List<String> = emptyList(), sizes: List<Long> = emptyList()) {
        val uri = uris.getOrNull(index) ?: return
        val name = names.getOrNull(index) ?: ""
        val size = sizes.getOrNull(index) ?: 0L
        val editor = prefs.edit()
        val timeKeys = VideoIdentity.getAllKeysForVideo("recent_time", uri, name, size)
        val now = System.currentTimeMillis()
        for (k in timeKeys) editor.putLong(k, now)
        editor.apply()
    }

    fun savePosition(player: ExoPlayer, uris: List<String>, index: Int, names: List<String> = emptyList(), sizes: List<Long> = emptyList()) {
        val uri = uris.getOrNull(index) ?: return
        val name = names.getOrNull(index) ?: ""
        val size = sizes.getOrNull(index) ?: 0L
        val editor = prefs.edit()
        val timeKeys = VideoIdentity.getAllKeysForVideo("recent_time", uri, name, size)
        val now = System.currentTimeMillis()
        for (k in timeKeys) editor.putLong(k, now)

        if (player.duration > 0) {
            val pos = player.currentPosition
            if (pos > 3000L && pos < player.duration - 3000L) {
                val keys = VideoIdentity.getAllKeysForVideo("pos", uri, name, size)
                for (k in keys) editor.putLong(k, pos)
            }
        }
        editor.apply()
    }

    fun clearPosition(uris: List<String>, index: Int, names: List<String> = emptyList(), sizes: List<Long> = emptyList()) {
        val uri = uris.getOrNull(index) ?: return
        val name = names.getOrNull(index) ?: ""
        val size = sizes.getOrNull(index) ?: 0L
        val editor = prefs.edit()
        val keys = VideoIdentity.getAllKeysForVideo("pos", uri, name, size)
        for (k in keys) editor.remove(k)
        editor.apply()
        handler.removeCallbacks(hideBannerRunnable)
        resumeBanner.visibility = View.GONE
    }

    fun recordWatchTime(p: com.example.mxoffline.player.playlist.PlayerPlaylistController) = recordWatchTime(p.uris, p.index, p.names, p.sizes)
    fun checkAndApplyResume(player: ExoPlayer, p: com.example.mxoffline.player.playlist.PlayerPlaylistController) = checkAndApplyResume(player, p.uris, p.index, p.names, p.sizes)
    fun savePosition(player: ExoPlayer, p: com.example.mxoffline.player.playlist.PlayerPlaylistController) = savePosition(player, p.uris, p.index, p.names, p.sizes)
    fun clearPosition(p: com.example.mxoffline.player.playlist.PlayerPlaylistController) = clearPosition(p.uris, p.index, p.names, p.sizes)
}
