/**
 * Role: Sealed hierarchy representing all UI item types in the media library list.
 * Responsibility: Models Header, RecentCarousel, Video, Folder, and Saf list entries.
 * Details: Enables type-safe heterogeneous rendering in LibraryAdapter for clean separation of concerns.
 */
package com.example.mxoffline.library

import com.example.mxoffline.model.FolderItem
import com.example.mxoffline.model.SafEntry
import com.example.mxoffline.model.VideoItem

sealed class LibraryListItem {
    data class Header(
        val title: String,
        val count: Int = 0,
        val actionText: String? = null,
        val onActionClick: (() -> Unit)? = null,
        val secondaryActionText: String? = null,
        val onSecondaryActionClick: (() -> Unit)? = null
    ) : LibraryListItem()

    data class RecentCarousel(
        val videos: List<VideoItem>
    ) : LibraryListItem()

    data class Video(
        val item: VideoItem,
        val playlist: List<VideoItem>
    ) : LibraryListItem()

    data class Folder(
        val item: FolderItem
    ) : LibraryListItem()

    data class Saf(
        val item: SafEntry
    ) : LibraryListItem()
}
