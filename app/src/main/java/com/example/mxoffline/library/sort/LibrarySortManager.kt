/**
 * Role: Media sorting algorithms and dialog coordinator.
 * Responsibility: Sorts folders, videos, and SAF file items according to user preferences.
 * Details: Supports sorting by name (A-Z), date modified, file size, and video duration.
 */
package com.example.mxoffline.library.sort

import android.app.AlertDialog
import android.content.Context
import com.example.mxoffline.model.FolderItem
import com.example.mxoffline.model.SafEntry
import com.example.mxoffline.model.VideoItem

object LibrarySortManager {

    val SORT_OPTIONS = arrayOf(
        "Sort by Name (A-Z)",
        "Sort by Date (Newest)",
        "Sort by Size (Largest)",
        "Sort by Duration (Longest)"
    )

    fun sortFolders(list: List<FolderItem>, sortMode: Int): List<FolderItem> {
        return when (sortMode) {
            1 -> list.sortedWith(
                compareByDescending<FolderItem> { folder -> folder.videos.maxOfOrNull { it.dateModified } ?: 0L }
                    .thenBy { it.name.lowercase() }
            )
            2 -> list.sortedWith(
                compareByDescending<FolderItem> { folder -> folder.videos.sumOf { it.sizeBytes } }
                    .thenBy { it.name.lowercase() }
            )
            3 -> list.sortedWith(
                compareByDescending<FolderItem> { folder -> folder.videos.sumOf { it.durationMs } }
                    .thenBy { it.name.lowercase() }
            )
            else -> list.sortedBy { it.name.lowercase() }
        }
    }

    fun sortVideos(list: List<VideoItem>, sortMode: Int): List<VideoItem> {
        return when (sortMode) {
            1 -> list.sortedWith(
                compareByDescending<VideoItem> { it.dateModified }
                    .thenBy { it.name.lowercase() }
            )
            2 -> list.sortedWith(
                compareByDescending<VideoItem> { it.sizeBytes }
                    .thenBy { it.name.lowercase() }
            )
            3 -> list.sortedWith(
                compareByDescending<VideoItem> { it.durationMs }
                    .thenBy { it.name.lowercase() }
            )
            else -> list.sortedBy { it.name.lowercase() }
        }
    }

    fun sortSafEntries(list: List<SafEntry>, sortMode: Int): List<SafEntry> {
        return when (sortMode) {
            1 -> list.sortedWith(
                compareBy<SafEntry> { !it.isDirectory }
                    .thenByDescending { it.modified ?: 0L }
                    .thenBy { it.name.lowercase() }
            )
            2 -> list.sortedWith(
                compareBy<SafEntry> { !it.isDirectory }
                    .thenByDescending { it.sizeBytes ?: 0L }
                    .thenBy { it.name.lowercase() }
            )
            else -> list.sortedWith(
                compareBy<SafEntry> { !it.isDirectory }
                    .thenBy { it.name.lowercase() }
            )
        }
    }

    fun showSortDialog(context: Context, currentMode: Int, onSelect: (Int) -> Unit) {
        AlertDialog.Builder(context)
            .setTitle("Sort Folders & Videos")
            .setSingleChoiceItems(SORT_OPTIONS, currentMode) { dialog, which ->
                onSelect(which)
                dialog.dismiss()
            }
            .show()
    }
}
