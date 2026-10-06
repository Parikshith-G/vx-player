package com.example.mxoffline.library

import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
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

    private var mode = TYPE_FOLDER
    private var videoItems = listOf<VideoItem>()
    private var folderItems = listOf<FolderItem>()
    private var safItems = listOf<SafEntry>()

    companion object {
        const val TYPE_VIDEO = 0
        const val TYPE_FOLDER = 1
        const val TYPE_SAF = 2
    }

    fun submitVideos(videos: List<VideoItem>) {
        mode = TYPE_VIDEO
        videoItems = videos
        notifyDataSetChanged()
    }

    fun submitFolders(folders: List<FolderItem>) {
        mode = TYPE_FOLDER
        folderItems = folders
        notifyDataSetChanged()
    }

    fun submitSaf(entries: List<SafEntry>) {
        mode = TYPE_SAF
        safItems = entries
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = mode

    override fun getItemCount(): Int = when (mode) {
        TYPE_VIDEO -> videoItems.size
        TYPE_FOLDER -> folderItems.size
        TYPE_SAF -> safItems.size
        else -> 0
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val ctx = parent.context
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

        return MediaHolder(card, thumbImage, folderIconText, durationBadge, progressBar, title, subtitle)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val h = holder as MediaHolder
        val ctx = h.itemView.context

        when (mode) {
            TYPE_VIDEO -> {
                val item = videoItems[position]
                h.title.text = item.name
                h.folderIcon.visibility = View.GONE
                h.thumb.visibility = View.VISIBLE

                ThumbnailLoader.load(ctx, item.uri, item.id, h.thumb)

                if (item.durationMs > 0) {
                    h.duration.text = TimeFormatter.formatDuration(item.durationMs)
                    h.duration.visibility = View.VISIBLE
                } else {
                    h.duration.visibility = View.GONE
                }

                val sizeStr = FileSizeFormatter.formatSize(item.sizeBytes)
                val resBadge = if (item.resolution.isNotBlank()) " · ${item.resolution}" else ""
                h.subtitle.text = "$sizeStr$resBadge"

                val savedPos = resumePrefs.getLong("pos_${item.uri}", 0L)
                if (savedPos > 3000L && item.durationMs > 0) {
                    h.progressBar.progress = ((savedPos * 1000) / item.durationMs).toInt()
                    h.progressBar.visibility = View.VISIBLE
                } else {
                    h.progressBar.visibility = View.GONE
                }

                h.itemView.setOnClickListener { onVideoClick(item, videoItems) }
            }

            TYPE_FOLDER -> {
                val folder = folderItems[position]
                h.title.text = folder.name
                h.subtitle.text = "${folder.videoCount} videos"
                h.duration.visibility = View.GONE
                h.progressBar.visibility = View.GONE

                if (folder.latestVideoUri != null) {
                    h.folderIcon.visibility = View.GONE
                    h.thumb.visibility = View.VISIBLE
                    ThumbnailLoader.load(ctx, folder.latestVideoUri, folder.latestVideoId, h.thumb)
                } else {
                    h.thumb.visibility = View.GONE
                    h.folderIcon.text = "📁"
                    h.folderIcon.visibility = View.VISIBLE
                }

                h.itemView.setOnClickListener { onFolderClick(folder) }
            }

            TYPE_SAF -> {
                val entry = safItems[position]
                h.title.text = entry.name
                h.duration.visibility = View.GONE
                h.progressBar.visibility = View.GONE

                if (entry.isDirectory) {
                    h.thumb.visibility = View.GONE
                    h.folderIcon.text = "📁"
                    h.folderIcon.visibility = View.VISIBLE
                    h.subtitle.text = "FOLDER"
                } else {
                    h.folderIcon.visibility = View.GONE
                    h.thumb.visibility = View.VISIBLE
                    h.subtitle.text = FileSizeFormatter.formatSize(entry.sizeBytes)
                }

                h.itemView.setOnClickListener { onSafClick(entry) }
            }
        }
    }

    class MediaHolder(
        view: View,
        val thumb: ImageView,
        val folderIcon: TextView,
        val duration: TextView,
        val progressBar: ProgressBar,
        val title: TextView,
        val subtitle: TextView
    ) : RecyclerView.ViewHolder(view)
}
