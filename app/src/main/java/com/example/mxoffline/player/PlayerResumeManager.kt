package com.example.mxoffline.player

import android.content.SharedPreferences
import android.os.Handler
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.exoplayer.ExoPlayer
import com.example.mxoffline.util.TimeFormatter

class PlayerResumeManager(
    private val prefs: SharedPreferences,
    private val handler: Handler,
    private val resumeBanner: LinearLayout,
    private val resumeText: TextView
) {
    private var lastCheckedIndex = -1
    private val hideBannerRunnable = Runnable { resumeBanner.visibility = View.GONE }

    fun checkAndApplyResume(player: ExoPlayer, uris: List<String>, index: Int) {
        val currentIdx = player.currentMediaItemIndex
        if (lastCheckedIndex == currentIdx) return
        lastCheckedIndex = currentIdx

        val uri = uris.getOrNull(currentIdx) ?: return
        val saved = prefs.getLong("pos_$uri", 0L)
        val duration = player.duration
        if (saved > 3000L && duration > 10000L && saved < duration - 5000L) {
            player.seekTo(saved)
            resumeText.text = "Resumed from ${TimeFormatter.formatTime(saved)}"
            resumeBanner.visibility = View.VISIBLE
            handler.removeCallbacks(hideBannerRunnable)
            handler.postDelayed(hideBannerRunnable, 4500)
        }
    }

    fun savePosition(player: ExoPlayer, uris: List<String>, index: Int) {
        val uri = uris.getOrNull(index) ?: return
        if (player.duration > 0) {
            val pos = player.currentPosition
            if (pos > 3000L && pos < player.duration - 3000L) {
                prefs.edit().putLong("pos_$uri", pos).apply()
            }
        }
    }

    fun clearPosition(uris: List<String>, index: Int) {
        val uri = uris.getOrNull(index) ?: return
        prefs.edit().remove("pos_$uri").apply()
        handler.removeCallbacks(hideBannerRunnable)
        resumeBanner.visibility = View.GONE
    }
}
