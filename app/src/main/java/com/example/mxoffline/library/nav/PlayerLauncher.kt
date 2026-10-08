/**
 * Role: Activity launcher for video playback.
 * Responsibility: Packs playlists and target indexes into intents and launches PlayerActivity.
 * Details: Handles empty list checks and serializing URI and name lists for intent transfer.
 */
package com.example.mxoffline.library.nav

import android.content.Context
import android.content.Intent
import com.example.mxoffline.PlayerActivity
import com.example.mxoffline.model.VideoItem

object PlayerLauncher {
    fun start(context: Context, videos: List<VideoItem>, index: Int) {
        if (videos.isEmpty()) return
        context.startActivity(
            Intent(context, PlayerActivity::class.java)
                .putExtra("uris", ArrayList(videos.map { it.uri.toString() }))
                .putExtra("names", ArrayList(videos.map { it.name }))
                .putExtra("index", index)
        )
    }
}
