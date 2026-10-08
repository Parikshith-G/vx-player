/**
 * Role: Presentation and list aggregation coordinator for Library screen.
 * Responsibility: Builds display lists and result counter labels for each tab.
 * Details: Applies search filters, sorts items, and generates Header, Carousel, Video, and Folder items.
 */
package com.example.mxoffline.library.display

import android.content.SharedPreferences
import com.example.mxoffline.library.LibraryListItem
import com.example.mxoffline.library.actions.LibraryBatchActionManager
import com.example.mxoffline.library.sort.LibrarySortManager
import com.example.mxoffline.model.FolderItem
import com.example.mxoffline.model.SafEntry
import com.example.mxoffline.model.VideoItem
import com.example.mxoffline.util.PreferenceHelper

data class DisplayResult(
    val items: List<LibraryListItem>,
    val resultText: String,
    val emptyText: String
)

class LibraryDisplayCoordinator(
    private val resumePrefs: SharedPreferences,
    private val seenPrefs: SharedPreferences,
    private val actionManager: LibraryBatchActionManager
) {

    fun getSavedResumePosition(video: VideoItem): Long {
        val keys = com.example.mxoffline.util.VideoIdentity.getAllKeys("pos", video)
        var maxPos = 0L
        for (k in keys) {
            val p = PreferenceHelper.safeGetLong(resumePrefs, k, 0L)
            if (p > maxPos) maxPos = p
        }
        return maxPos
    }

    fun getRecentVideos(allVideos: List<VideoItem>): List<VideoItem> {
        return allVideos.filter {
            getSavedResumePosition(it) > 3000L
        }.sortedByDescending { getSavedResumePosition(it) }
    }

    fun isVideoSeen(video: VideoItem): Boolean {
        val keys = com.example.mxoffline.util.VideoIdentity.getAllKeys("seen", video)
        for (k in keys) {
            if (seenPrefs.contains(k)) return true
        }
        return false
    }

    fun getSeenTimestamp(video: VideoItem): Long {
        val keys = com.example.mxoffline.util.VideoIdentity.getAllKeys("seen", video)
        var maxTime = 0L
        for (k in keys) {
            val t = PreferenceHelper.safeGetLong(seenPrefs, k, 0L)
            if (t > maxTime) maxTime = t
        }
        return maxTime
    }

    fun getSeenVideos(allVideos: List<VideoItem>): List<VideoItem> {
        return allVideos.filter {
            isVideoSeen(it)
        }.sortedByDescending { getSeenTimestamp(it) }
    }

    fun buildFoldersTab(
        allVideos: List<VideoItem>,
        deviceFolders: List<FolderItem>,
        searchQuery: String,
        sortMode: Int
    ): DisplayResult {
        val recent = getRecentVideos(allVideos).filter { searchQuery.isBlank() || it.name.contains(searchQuery, true) }
        val folders = LibrarySortManager.sortFolders(
            deviceFolders.filter { searchQuery.isBlank() || it.name.contains(searchQuery, true) },
            sortMode
        )
        val list = mutableListOf<LibraryListItem>()
        if (recent.isNotEmpty()) {
            list.add(LibraryListItem.Header("RECENTLY PLAYED", recent.size, actionText = "Clear", onActionClick = { actionManager.promptClearRecent() }))
            list.add(LibraryListItem.RecentCarousel(recent))
        }
        if (folders.isNotEmpty()) {
            if (recent.isNotEmpty()) list.add(LibraryListItem.Header("FOLDERS", folders.size))
            folders.forEach { list.add(LibraryListItem.Folder(it)) }
        }
        val countText = "${if (recent.isNotEmpty()) "${recent.size} recent · " else ""}${folders.size} folders"
        val emptyText = if (recent.isEmpty() && folders.isEmpty()) {
            if (searchQuery.isNotBlank()) "No matching folders found" else "No video folders found"
        } else ""
        return DisplayResult(list, countText, emptyText)
    }

    fun buildAllVideosTab(allVideos: List<VideoItem>, searchQuery: String, sortMode: Int): DisplayResult {
        val filtered = allVideos.filter { searchQuery.isBlank() || it.name.contains(searchQuery, true) }
        val sorted = LibrarySortManager.sortVideos(filtered, sortMode)
        val list = sorted.map { LibraryListItem.Video(it, sorted) }
        val countText = "${sorted.size} videos"
        val emptyText = if (sorted.isEmpty()) "No videos found" else ""
        return DisplayResult(list, countText, emptyText)
    }

    fun buildFolderVideosTab(folderVideos: List<VideoItem>, folderName: String, searchQuery: String, sortMode: Int): DisplayResult {
        val filtered = folderVideos.filter { searchQuery.isBlank() || it.name.contains(searchQuery, true) }
        val sorted = LibrarySortManager.sortVideos(filtered, sortMode)
        val list = sorted.map { LibraryListItem.Video(it, sorted) }
        val countText = "${sorted.size} videos in $folderName"
        val emptyText = if (sorted.isEmpty()) "Folder is empty" else ""
        return DisplayResult(list, countText, emptyText)
    }

    fun buildSafTab(safEntries: List<SafEntry>, searchQuery: String, sortMode: Int): DisplayResult {
        val filtered = safEntries.filter { searchQuery.isBlank() || it.name.contains(searchQuery, true) }
        val sorted = LibrarySortManager.sortSafEntries(filtered, sortMode)
        val list = sorted.map { LibraryListItem.Saf(it) }
        val countText = "${sorted.size} items"
        val emptyText = if (sorted.isEmpty()) "This folder is empty" else ""
        return DisplayResult(list, countText, emptyText)
    }

    fun buildSeenTab(allVideos: List<VideoItem>, searchQuery: String, sortMode: Int): DisplayResult {
        val seen = getSeenVideos(allVideos).filter { searchQuery.isBlank() || it.name.contains(searchQuery, true) }
        val sorted = LibrarySortManager.sortVideos(seen, sortMode)
        val list = mutableListOf<LibraryListItem>()
        if (sorted.isNotEmpty()) {
            list.add(
                LibraryListItem.Header(
                    "SEEN VIDEOS",
                    sorted.size,
                    actionText = "🗑 Delete All Seen",
                    onActionClick = { actionManager.promptDeleteAllSeen(sorted) },
                    secondaryActionText = "Clear History",
                    onSecondaryActionClick = { actionManager.promptClearSeenHistory() }
                )
            )
            sorted.forEach { list.add(LibraryListItem.Video(it, sorted)) }
        }
        val countText = "${sorted.size} seen videos"
        val emptyText = if (sorted.isEmpty()) {
            if (searchQuery.isNotBlank()) "No matching seen videos found"
            else "No completed videos yet.\n\nVideos you watch to the end will appear here automatically."
        } else ""
        return DisplayResult(list, countText, emptyText)
    }
}
