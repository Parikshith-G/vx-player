/**
 * Role: Horizontal recent playback carousel adapter and holder.
 * Responsibility: Renders thumbnail cards for recently played videos with progress overlays.
 * Details: Displays video duration badge and watch progress bars in a horizontal scrolling row.
 */
package com.example.mxoffline.library.adapter

import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.mxoffline.model.VideoItem
import com.example.mxoffline.util.ThumbnailLoader
import com.example.mxoffline.util.TimeFormatter
import com.example.mxoffline.util.UiUtils

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
