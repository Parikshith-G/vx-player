/**
 * Role: View container and factory for media library card items.
 * Responsibility: Constructs and holds references to views for video, folder, and SAF entries.
 * Details: Creates consistent rounded card rows with thumbnails, titles, badges, and progress bars.
 */
package com.example.mxoffline.library.adapter

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.mxoffline.util.UiUtils

class MediaCardViews(
    val root: View,
    val thumb: ImageView,
    val folderIcon: TextView,
    val duration: TextView,
    val progressBar: ProgressBar,
    val title: TextView,
    val subtitle: TextView
) {
    companion object {
        fun create(ctx: Context): MediaCardViews {
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
    }
}
