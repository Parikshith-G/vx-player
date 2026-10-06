package com.example.mxoffline.library

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
    private val resumePrefs: SharedPreferences
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
                    textSize = 12f
                    letterSpacing = 0.08f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(0xffffc400.toInt())
                }
                row.addView(title, LinearLayout.LayoutParams(0, -2, 1f))

                val secondaryBtn = TextView(ctx).apply {
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(0xff9ea4b2.toInt())
                    setPadding(dp(8), dp(4), dp(8), dp(4))
                    visibility = View.GONE
                }
                row.addView(secondaryBtn)

                val actionBtn = TextView(ctx).apply {
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(0xffffc400.toInt())
                    setPadding(dp(8), dp(4), dp(8), dp(4))
                    visibility = View.GONE
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
                RecentCarouselHolder(hRecycler, onVideoClick, resumePrefs)
            }

            VIEW_TYPE_VIDEO -> {
                val card = createMediaCard(ctx)
                VideoHolder(card)
            }

            VIEW_TYPE_FOLDER -> {
                val card = createMediaCard(ctx)
                FolderHolder(card)
            }

            VIEW_TYPE_SAF -> {
                val card = createMediaCard(ctx)
                SafHolder(card)
            }

            else -> throw IllegalArgumentException("Unknown viewType: $viewType")
        }
    }

    private fun createMediaCard(ctx: Context): MediaCardViews {
        fun dp(v: Int) = UiUtils.dp(ctx, v)

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = UiUtils.rounded(0xff181b24.toInt(), 14, ctx)
            layoutParams = RecyclerView.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
        }

        // Thumbnail / Icon Frame
        val thumbFrame = FrameLayout(ctx)
        val thumbImage = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = UiUtils.rounded(0xff232734.toInt(), 10, ctx)
            clipToOutline = true
        }
        val durationBadge = TextView(ctx).apply {
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = UiUtils.rounded(0xdd000000.toInt(), 4, ctx)
            setPadding(dp(4), dp(1), dp(4), dp(1))
            visibility = View.GONE
        }
        val progressBar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progressTintList = android.content.res.ColorStateList.valueOf(0xffffc400.toInt())
            visibility = View.GONE
        }
        val folderIconText = TextView(ctx).apply {
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(0xffffc400.toInt())
            visibility = View.GONE
        }

        thumbFrame.addView(thumbImage, FrameLayout.LayoutParams(-1, -1))
        thumbFrame.addView(folderIconText, FrameLayout.LayoutParams(-1, -1))
        thumbFrame.addView(durationBadge, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply {
            bottomMargin = dp(4); rightMargin = dp(4)
        })
        thumbFrame.addView(progressBar, FrameLayout.LayoutParams(-1, dp(4), Gravity.BOTTOM))
        card.addView(thumbFrame, LinearLayout.LayoutParams(dp(88), dp(58)))

        // Details
        val details = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(6), 0)
        }
        val title = TextView(ctx).apply {
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xfff0f1f4.toInt())
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val subtitle = TextView(ctx).apply {
            textSize = 12f
            setTextColor(0xff8c92a2.toInt())
            maxLines = 1
            setPadding(0, dp(3), 0, 0)
        }
        details.addView(title)
        details.addView(subtitle)
        card.addView(details, LinearLayout.LayoutParams(0, -2, 1f))

        val arrow = TextView(ctx).apply {
            text = "›"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(0xff5e6475.toInt())
        }
        card.addView(arrow, LinearLayout.LayoutParams(dp(24), -1))

        return MediaCardViews(card, thumbImage, folderIconText, durationBadge, progressBar, title, subtitle)
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

                val savedPos = resumePrefs.getLong("pos_${video.uri}", 0L)
                if (savedPos > 3000L && video.durationMs > 0) {
                    holder.views.progressBar.progress = ((savedPos * 1000) / video.durationMs).toInt()
                    holder.views.progressBar.visibility = View.VISIBLE
                } else {
                    holder.views.progressBar.visibility = View.GONE
                }

                holder.itemView.setOnClickListener { onVideoClick(video, videoItem.playlist) }
            }

            is FolderHolder -> {
                val folder = (item as LibraryListItem.Folder).item
                holder.views.title.text = folder.name
                holder.views.subtitle.text = "${folder.videoCount} videos"
                holder.views.duration.visibility = View.GONE
                holder.views.progressBar.visibility = View.GONE

                if (folder.latestVideoUri != null) {
                    holder.views.folderIcon.visibility = View.GONE
                    holder.views.thumb.visibility = View.VISIBLE
                    ThumbnailLoader.load(ctx, folder.latestVideoUri, folder.latestVideoId, holder.views.thumb)
                } else {
                    holder.views.thumb.visibility = View.GONE
                    holder.views.folderIcon.text = "📁"
                    holder.views.folderIcon.visibility = View.VISIBLE
                }

                holder.itemView.setOnClickListener { onFolderClick(folder) }
            }

            is SafHolder -> {
                val entry = (item as LibraryListItem.Saf).item
                holder.views.title.text = entry.name
                holder.views.duration.visibility = View.GONE
                holder.views.progressBar.visibility = View.GONE

                if (entry.isDirectory) {
                    holder.views.thumb.visibility = View.GONE
                    holder.views.folderIcon.text = "📁"
                    holder.views.folderIcon.visibility = View.VISIBLE
                    holder.views.subtitle.text = "FOLDER"
                } else {
                    holder.views.folderIcon.visibility = View.GONE
                    holder.views.thumb.visibility = View.VISIBLE
                    holder.views.subtitle.text = FileSizeFormatter.formatSize(entry.sizeBytes)
                }

                holder.itemView.setOnClickListener { onSafClick(entry) }
            }
        }
    }

    class HeaderHolder(
        view: View,
        val title: TextView,
        val secondaryBtn: TextView,
        val actionBtn: TextView
    ) : RecyclerView.ViewHolder(view)

    class RecentCarouselHolder(
        val recyclerView: RecyclerView,
        private val onVideoClick: (VideoItem, List<VideoItem>) -> Unit,
        private val resumePrefs: SharedPreferences
    ) : RecyclerView.ViewHolder(recyclerView) {
        fun bind(videos: List<VideoItem>) {
            recyclerView.adapter = RecentThumbAdapter(videos, onVideoClick, resumePrefs)
        }
    }

    class RecentThumbAdapter(
        private val videos: List<VideoItem>,
        private val onVideoClick: (VideoItem, List<VideoItem>) -> Unit,
        private val resumePrefs: SharedPreferences
    ) : RecyclerView.Adapter<RecentThumbHolder>() {

        override fun getItemCount(): Int = videos.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecentThumbHolder {
            val ctx = parent.context
            fun dp(v: Int) = UiUtils.dp(ctx, v)

            val thumbFrame = FrameLayout(ctx).apply {
                background = UiUtils.rounded(0xff1c202b.toInt(), 12, ctx)
                clipToOutline = true
                layoutParams = RecyclerView.LayoutParams(dp(136), dp(82)).apply {
                    rightMargin = dp(10)
                }
            }

            val thumbImage = ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
            }

            val durationBadge = TextView(ctx).apply {
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                background = UiUtils.rounded(0xdd000000.toInt(), 4, ctx)
                setPadding(dp(4), dp(1), dp(4), dp(1))
                visibility = View.GONE
            }

            val progressBar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 1000
                progressTintList = android.content.res.ColorStateList.valueOf(0xffffc400.toInt())
                visibility = View.GONE
            }

            thumbFrame.addView(thumbImage, FrameLayout.LayoutParams(-1, -1))
            thumbFrame.addView(durationBadge, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply {
                bottomMargin = dp(6); rightMargin = dp(6)
            })
            thumbFrame.addView(progressBar, FrameLayout.LayoutParams(-1, dp(4), Gravity.BOTTOM))

            return RecentThumbHolder(thumbFrame, thumbImage, durationBadge, progressBar)
        }

        override fun onBindViewHolder(holder: RecentThumbHolder, position: Int) {
            val video = videos[position]
            val ctx = holder.itemView.context

            ThumbnailLoader.load(ctx, video.uri, video.id, holder.thumb)

            if (video.durationMs > 0) {
                holder.duration.text = TimeFormatter.formatDuration(video.durationMs)
                holder.duration.visibility = View.VISIBLE
            } else {
                holder.duration.visibility = View.GONE
            }

            val savedPos = resumePrefs.getLong("pos_${video.uri}", 0L)
            if (savedPos > 3000L && video.durationMs > 0) {
                holder.progressBar.progress = ((savedPos * 1000) / video.durationMs).toInt()
                holder.progressBar.visibility = View.VISIBLE
            } else {
                holder.progressBar.visibility = View.GONE
            }

            holder.itemView.setOnClickListener { onVideoClick(video, videos) }
        }
    }

    class RecentThumbHolder(
        view: View,
        val thumb: ImageView,
        val duration: TextView,
        val progressBar: ProgressBar
    ) : RecyclerView.ViewHolder(view)

    class VideoHolder(val views: MediaCardViews) : RecyclerView.ViewHolder(views.root)
    class FolderHolder(val views: MediaCardViews) : RecyclerView.ViewHolder(views.root)
    class SafHolder(val views: MediaCardViews) : RecyclerView.ViewHolder(views.root)

    class MediaCardViews(
        val root: View,
        val thumb: ImageView,
        val folderIcon: TextView,
        val duration: TextView,
        val progressBar: ProgressBar,
        val title: TextView,
        val subtitle: TextView
    )
}
