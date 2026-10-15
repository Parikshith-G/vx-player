/**
 * Role: RecyclerView adapter for the offline media library.
 * Responsibility: Coordinates list submission, view type creation, and model binding.
 * Details: Dispatches videos, folders, SAF entries, carousels, and section headers.
 */
package com.example.mxoffline.library.adapter

import android.content.SharedPreferences
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mxoffline.library.LibraryListItem
import com.example.mxoffline.model.FolderItem
import com.example.mxoffline.model.SafEntry
import com.example.mxoffline.model.VideoItem
import com.example.mxoffline.util.FileSizeFormatter
import com.example.mxoffline.util.ThumbnailLoader
import com.example.mxoffline.util.TimeFormatter
import com.example.mxoffline.util.UiUtils

class LibraryAdapter(
    private val onVideoClick: (VideoItem, List<VideoItem>) -> Unit,
    private val onFolderClick: (FolderItem) -> Unit,
    private val onSafClick: (SafEntry) -> Unit,
    private val resumePrefs: SharedPreferences,
    private val onVideoLongClick: ((VideoItem, List<VideoItem>) -> Unit)? = null,
    private val getSafTreeUri: (() -> android.net.Uri?)? = null,
    private val onRecentVideoClick: ((VideoItem) -> Unit)? = null,
    private val onRecentVideoLongClick: ((VideoItem) -> Unit)? = null,
    private val onFolderLongClick: ((FolderItem) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val VIEW_TYPE_HEADER = 0
        const val VIEW_TYPE_VIDEO = 1
        const val VIEW_TYPE_FOLDER = 2
        const val VIEW_TYPE_SAF = 3
        const val VIEW_TYPE_RECENT_CAROUSEL = 4
    }

    private var items = listOf<LibraryListItem>()

    fun submitItems(newItems: List<LibraryListItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun submitVideos(videos: List<VideoItem>) {
        items = videos.map { LibraryListItem.Video(it, videos) }
        notifyDataSetChanged()
    }

    fun submitFolders(folders: List<FolderItem>) {
        items = folders.map { LibraryListItem.Folder(it) }
        notifyDataSetChanged()
    }

    fun submitSaf(entries: List<SafEntry>) {
        items = entries.map { LibraryListItem.Saf(it) }
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is LibraryListItem.Header -> VIEW_TYPE_HEADER
            is LibraryListItem.RecentCarousel -> VIEW_TYPE_RECENT_CAROUSEL
            is LibraryListItem.Video -> VIEW_TYPE_VIDEO
            is LibraryListItem.Folder -> VIEW_TYPE_FOLDER
            is LibraryListItem.Saf -> VIEW_TYPE_SAF
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val ctx = parent.context
        fun dp(v: Int) = UiUtils.dp(ctx, v)

        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(4), dp(10), dp(4), dp(6))
                    layoutParams = RecyclerView.LayoutParams(-1, -2)
                }
                val title = TextView(ctx).apply {
                    textSize = 12f; letterSpacing = 0.08f; typeface = Typeface.DEFAULT_BOLD
                    setTextColor(UiUtils.COLOR_ACCENT_BLUE)
                }
                row.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
                val secondaryBtn = TextView(ctx).apply {
                    textSize = 12f; typeface = Typeface.DEFAULT_BOLD; setTextColor(0xff9ea4b2.toInt())
                    setPadding(dp(8), dp(4), dp(8), dp(4)); visibility = View.GONE
                }
                row.addView(secondaryBtn)
                val actionBtn = TextView(ctx).apply {
                    textSize = 12f; typeface = Typeface.DEFAULT_BOLD; setTextColor(UiUtils.COLOR_ACCENT_BLUE)
                    setPadding(dp(8), dp(4), dp(8), dp(4)); visibility = View.GONE
                }
                row.addView(actionBtn)
                HeaderHolder(row, title, secondaryBtn, actionBtn)
            }

            VIEW_TYPE_RECENT_CAROUSEL -> {
                val hRecycler = RecyclerView(ctx).apply {
                    layoutManager = LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false)
                    clipToPadding = false
                    setPadding(dp(2), 0, dp(2), dp(8))
                    layoutParams = RecyclerView.LayoutParams(-1, -2)
                }
                RecentCarouselHolder(hRecycler, onVideoClick, resumePrefs, onRecentVideoClick, onRecentVideoLongClick)
            }

            VIEW_TYPE_VIDEO -> VideoHolder(MediaCardViews.create(ctx))
            VIEW_TYPE_FOLDER -> FolderHolder(MediaCardViews.create(ctx))
            VIEW_TYPE_SAF -> SafHolder(MediaCardViews.create(ctx))
            else -> throw IllegalArgumentException("Unknown viewType: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        val ctx = holder.itemView.context

        when (holder) {
            is HeaderHolder -> {
                val header = item as LibraryListItem.Header
                holder.title.text = if (header.count > 0) "${header.title} (${header.count})" else header.title

                if (header.secondaryActionText != null && header.onSecondaryActionClick != null) {
                    holder.secondaryBtn.text = header.secondaryActionText
                    holder.secondaryBtn.visibility = View.VISIBLE
                    holder.secondaryBtn.setOnClickListener { header.onSecondaryActionClick.invoke() }
                } else {
                    holder.secondaryBtn.visibility = View.GONE
                }

                if (header.actionText != null && header.onActionClick != null) {
                    holder.actionBtn.text = header.actionText
                    holder.actionBtn.visibility = View.VISIBLE
                    if (header.actionText.contains("Delete", ignoreCase = true)) {
                        holder.actionBtn.setTextColor(0xffff5252.toInt())
                    } else {
                        holder.actionBtn.setTextColor(UiUtils.COLOR_ACCENT_BLUE)
                    }
                    holder.actionBtn.setOnClickListener { header.onActionClick.invoke() }
                } else {
                    holder.actionBtn.visibility = View.GONE
                }
            }

            is RecentCarouselHolder -> {
                val carousel = item as LibraryListItem.RecentCarousel
                holder.bind(carousel.videos)
            }

            is VideoHolder -> {
                val videoItem = (item as LibraryListItem.Video)
                val video = videoItem.item
                holder.views.title.text = video.name
                holder.views.folderIcon.visibility = View.GONE
                holder.views.thumb.visibility = View.VISIBLE

                ThumbnailLoader.load(ctx, video.uri, video.id, holder.views.thumb)

                if (video.durationMs > 0) {
                    holder.views.duration.text = TimeFormatter.formatDuration(video.durationMs)
                    holder.views.duration.visibility = View.VISIBLE
                } else {
                    holder.views.duration.visibility = View.GONE
                }

                val sizeStr = FileSizeFormatter.formatSize(video.sizeBytes)
                val resBadge = if (video.resolution.isNotBlank()) " · ${video.resolution}" else ""
                holder.views.subtitle.text = "$sizeStr$resBadge"

                val savedPos = com.example.mxoffline.util.VideoIdentity.getAllKeys("pos", video).maxOfOrNull { com.example.mxoffline.util.PreferenceHelper.safeGetLong(resumePrefs, it, 0L) } ?: 0L
                if (savedPos > 3000L && video.durationMs > 0) {
                    holder.views.progressBar.progress = ((savedPos * 1000) / video.durationMs).toInt()
                    holder.views.progressBar.visibility = View.VISIBLE
                } else {
                    holder.views.progressBar.visibility = View.GONE
                }

                val recentTime = com.example.mxoffline.util.VideoIdentity.getAllKeys("recent_time", video).maxOfOrNull { com.example.mxoffline.util.PreferenceHelper.safeGetLong(resumePrefs, it, 0L) } ?: 0L
                val lastWatchedUri = resumePrefs.getString("last_watched_uri", null)
                val lastWatchedName = resumePrefs.getString("last_watched_name", null)
                val lastWatchedTime = resumePrefs.getLong("last_watched_time", 0L)

                val isLastWatched = (lastWatchedUri != null && (video.uri.toString() == lastWatchedUri || (lastWatchedName != null && lastWatchedName.isNotBlank() && video.name == lastWatchedName))) ||
                        (lastWatchedTime > 0L && recentTime == lastWatchedTime)
                val isWatching = savedPos > 3000L

                if (isLastWatched || isWatching) {
                    holder.views.title.setTextColor(UiUtils.COLOR_ACCENT_BLUE)
                } else {
                    holder.views.title.setTextColor(0xfff0f1f4.toInt())
                }

                holder.itemView.setOnClickListener { onVideoClick(video, videoItem.playlist) }
                holder.itemView.setOnLongClickListener { onVideoLongClick?.invoke(video, videoItem.playlist); true }
            }

            is FolderHolder -> {
                val folder = (item as LibraryListItem.Folder).item
                holder.views.title.text = folder.name
                holder.views.title.setTextColor(0xfff0f1f4.toInt())
                holder.views.subtitle.text = "${folder.videoCount} videos"
                holder.views.duration.visibility = View.GONE
                holder.views.progressBar.visibility = View.GONE

                if (folder.latestVideoUri != null) {
                    holder.views.folderIcon.visibility = View.GONE
                    holder.views.thumb.visibility = View.VISIBLE
                    ThumbnailLoader.load(ctx, folder.latestVideoUri, folder.latestVideoId, holder.views.thumb)
                } else {
                    holder.views.thumb.visibility = View.GONE
                    holder.views.folderIcon.text = "DIR"
                    holder.views.folderIcon.visibility = View.VISIBLE
                }

                holder.itemView.setOnClickListener { onFolderClick(folder) }
                holder.itemView.setOnLongClickListener { onFolderLongClick?.invoke(folder); true }
            }

            is SafHolder -> {
                val entry = (item as LibraryListItem.Saf).item
                holder.views.title.text = entry.name
                holder.views.duration.visibility = View.GONE
                holder.views.progressBar.visibility = View.GONE

                if (entry.isDirectory) {
                    holder.views.title.setTextColor(0xfff0f1f4.toInt())
                    holder.views.thumb.visibility = View.GONE
                    holder.views.folderIcon.text = "DIR"
                    holder.views.folderIcon.visibility = View.VISIBLE
                    holder.views.subtitle.text = "FOLDER"
                } else {
                    holder.views.folderIcon.visibility = View.GONE
                    holder.views.thumb.visibility = View.VISIBLE
                    holder.views.subtitle.text = FileSizeFormatter.formatSize(entry.sizeBytes)
                    val tree = getSafTreeUri?.invoke()
                    val docUri = if (tree != null) android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, entry.documentId) else null
                    if (docUri != null) {
                        ThumbnailLoader.load(ctx, docUri, 0L, holder.views.thumb)
                    } else {
                        holder.views.thumb.setImageDrawable(null)
                    }

                    val uriStr = docUri?.toString().orEmpty()
                    val entrySize = entry.sizeBytes ?: 0L
                    val savedPos = if (uriStr.isNotBlank()) {
                        com.example.mxoffline.util.VideoIdentity.getAllKeysForVideo("pos", uriStr, entry.name, entrySize).maxOfOrNull {
                            com.example.mxoffline.util.PreferenceHelper.safeGetLong(resumePrefs, it, 0L)
                        } ?: 0L
                    } else 0L

                    val recentTime = if (uriStr.isNotBlank()) {
                        com.example.mxoffline.util.VideoIdentity.getAllKeysForVideo("recent_time", uriStr, entry.name, entrySize).maxOfOrNull {
                            com.example.mxoffline.util.PreferenceHelper.safeGetLong(resumePrefs, it, 0L)
                        } ?: 0L
                    } else 0L

                    val lastWatchedUri = resumePrefs.getString("last_watched_uri", null)
                    val lastWatchedName = resumePrefs.getString("last_watched_name", null)
                    val lastWatchedTime = resumePrefs.getLong("last_watched_time", 0L)

                    val isLastWatched = (lastWatchedUri != null && (uriStr == lastWatchedUri || (lastWatchedName != null && lastWatchedName.isNotBlank() && entry.name == lastWatchedName))) ||
                            (lastWatchedTime > 0L && recentTime == lastWatchedTime)
                    val isWatching = savedPos > 3000L

                    if (isLastWatched || isWatching) {
                        holder.views.title.setTextColor(UiUtils.COLOR_ACCENT_BLUE)
                    } else {
                        holder.views.title.setTextColor(0xfff0f1f4.toInt())
                    }
                }

                holder.itemView.setOnClickListener { onSafClick(entry) }
            }
        }
    }
}
