/**
 * Role: Resolves the actual folder/directory playlist for a given media file.
 * Responsibility: Maps a video to its containing folder's video list with natural sorting.
 * Details: Ensures playback navigation (next/prev and in-player playlist) traverses the file explorer folder.
 */
package com.example.mxoffline.library.nav

import com.example.mxoffline.library.sort.LibrarySortManager
import com.example.mxoffline.model.FolderItem
import com.example.mxoffline.model.SafEntry
import com.example.mxoffline.model.VideoItem

object FolderPlaylistResolver {

    /**
     * Resolves the containing folder's playlist and video index for [video].
     * If the video belongs to a device folder or active SAF directory, returns that folder's
     * sorted video playlist and target index; otherwise falls back to a single-item playlist.
     */
    fun resolve(
        video: VideoItem,
        allDeviceVideos: List<VideoItem>,
        deviceFolders: List<FolderItem>,
        sortMode: Int,
        navigator: LibraryFolderNavigator? = null,
        safEntries: List<SafEntry> = emptyList()
    ): Pair<List<VideoItem>, Int> {
        // 1. Find matching video in device library
        val matchedVideo = allDeviceVideos.find {
            it.uri == video.uri ||
                (it.id != 0L && it.id == video.id) ||
                (it.name == video.name && it.sizeBytes > 0L && it.sizeBytes == video.sizeBytes) ||
                it.name == video.name
        } ?: video

        // 2. Identify containing folder among device folders or all device videos
        val folderVideos = if (matchedVideo.folderName.isNotBlank()) {
            val list = allDeviceVideos.filter { it.folderName.equals(matchedVideo.folderName, ignoreCase = true) }
            if (list.isNotEmpty()) list else {
                deviceFolders.find { f -> f.name.equals(matchedVideo.folderName, ignoreCase = true) }?.videos ?: emptyList()
            }
        } else {
            deviceFolders.find { f ->
                f.videos.any { it.uri == matchedVideo.uri || (it.id != 0L && it.id == matchedVideo.id) || it.name == matchedVideo.name }
            }?.videos ?: emptyList()
        }

        if (folderVideos.isNotEmpty()) {
            val sorted = LibrarySortManager.sortVideos(folderVideos, sortMode)
            val index = sorted.indexOfFirst {
                it.uri == matchedVideo.uri || (it.id != 0L && it.id == matchedVideo.id) || it.name == matchedVideo.name
            }.coerceAtLeast(0)
            return Pair(sorted, index)
        }

        // 3. Check if matching entry exists in currently active SAF folder
        if (navigator?.treeUri != null && safEntries.isNotEmpty()) {
            val safVideos = navigator.buildSafVideoList(LibrarySortManager.sortSafEntries(safEntries, sortMode))
            val safIndex = safVideos.indexOfFirst { it.uri == video.uri || it.name == video.name }
            if (safIndex >= 0) {
                return Pair(safVideos, safIndex)
            }
        }

        // 4. Standalone fallback: single video playlist
        return Pair(listOf(matchedVideo), 0)
    }
}
