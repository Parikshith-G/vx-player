/**
 * Role: Real-time visual progress card for offline subtitle generation.
 * Responsibility: Displays batch status, current video name, percentage, stage, and cancellation button.
 * Details: Observes SubtitleGenerationManager events and automatically animates completion/cancellation.
 */
package com.example.mxoffline.subtitles.ui

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.example.mxoffline.subtitles.manager.SubtitleGenerationManager
import com.example.mxoffline.subtitles.model.SubtitleBatchProgress
import com.example.mxoffline.util.UiUtils

class SubtitleProgressCard(
    private val activity: Activity,
    private val parentContainer: ViewGroup
) : SubtitleGenerationManager.Listener {

    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var cardView: LinearLayout
    private lateinit var titleText: TextView
    private lateinit var detailText: TextView
    private lateinit var percentText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var cancelBtn: TextView

    fun bind() {
        val dp = { v: Int -> UiUtils.dp(activity, v) }
        cardView = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = UiUtils.rounded(0xfa1d212c.toInt(), 16, activity)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            visibility = if (SubtitleGenerationManager.isRunning) View.VISIBLE else View.GONE
        }

        val headerRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleText = TextView(activity).apply {
            text = "Generating Subtitles (AI)"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            val d = androidx.core.content.ContextCompat.getDrawable(activity, com.example.mxoffline.R.drawable.ic_subtitles)?.mutate()?.apply {
                val sz = dp(20); setBounds(0, 0, sz, sz); setTint(Color.WHITE)
            }
            setCompoundDrawables(d, null, null, null)
            compoundDrawablePadding = dp(8)
        }
        cancelBtn = TextView(activity).apply {
            text = "Cancel"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xffff5252.toInt())
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnClickListener { SubtitleGenerationManager.cancel(activity) }
        }
        headerRow.addView(titleText, LinearLayout.LayoutParams(0, -2, 1f))
        headerRow.addView(cancelBtn)
        cardView.addView(headerRow)

        detailText = TextView(activity).apply {
            text = "Preparing..."
            textSize = 12f
            setTextColor(0xffaab0bc.toInt())
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            setPadding(0, dp(4), 0, dp(4))
        }
        cardView.addView(detailText)

        val progressRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        progressBar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            progressTintList = ColorStateList.valueOf(UiUtils.COLOR_ACCENT_BLUE)
        }
        percentText = TextView(activity).apply {
            text = "0%"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(UiUtils.COLOR_ACCENT_BLUE)
            setPadding(dp(8), 0, 0, 0)
        }
        progressRow.addView(progressBar, LinearLayout.LayoutParams(0, dp(6), 1f))
        progressRow.addView(percentText)
        cardView.addView(progressRow)

        val lp = if (parentContainer is FrameLayout) {
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            ).apply { setMargins(dp(16), 0, dp(16), dp(16)) }
        } else {
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(16), dp(8), dp(16), dp(8)) }
        }
        parentContainer.addView(cardView, lp)
        SubtitleGenerationManager.addListener(this)
    }

    private var actualTargetPercent = 0
    private var visualPercent = 0
    private var isInterpolating = false

    private val interpolationRunnable = object : Runnable {
        override fun run() {
            if (!SubtitleGenerationManager.isRunning) {
                isInterpolating = false
                return
            }
            if (visualPercent < actualTargetPercent) {
                visualPercent++
                applyVisualProgress(visualPercent)
                mainHandler.postDelayed(this, 120L)
            } else if (visualPercent < 98) {
                // Optimistic continuous crawl: gently advance 1% every 3s while waiting for next chunk
                visualPercent++
                applyVisualProgress(visualPercent)
                mainHandler.postDelayed(this, 3000L)
            } else {
                mainHandler.postDelayed(this, 1000L)
            }
        }
    }

    private fun startInterpolationIfNeeded() {
        if (!isInterpolating) {
            isInterpolating = true
            mainHandler.post(interpolationRunnable)
        }
    }

    private fun applyVisualProgress(percent: Int) {
        progressBar.progress = percent.coerceIn(0, 100)
        percentText.text = "$percent%"
    }

    fun unbind() {
        mainHandler.removeCallbacks(interpolationRunnable)
        isInterpolating = false
        SubtitleGenerationManager.removeListener(this)
    }

    override fun onProgress(progress: SubtitleBatchProgress) {
        mainHandler.post {
            cardView.visibility = View.VISIBLE
            val stage = if (progress.currentProgress < 50) "Extracting audio" else "Transcribing speech"
            detailText.text = "${progress.folderName} (${progress.currentIndex + 1}/${progress.totalVideos}) • ${progress.currentVideoName}"
            titleText.text = "✨ Generating Subtitles: $stage"

            actualTargetPercent = progress.currentProgress
            if (visualPercent < actualTargetPercent) {
                startInterpolationIfNeeded()
            } else {
                visualPercent = actualTargetPercent
                applyVisualProgress(visualPercent)
                startInterpolationIfNeeded()
            }
        }
    }

    override fun onCompleted(folderName: String, totalCompleted: Int) {
        mainHandler.post {
            mainHandler.removeCallbacks(interpolationRunnable)
            isInterpolating = false
            visualPercent = 100
            actualTargetPercent = 100
            applyVisualProgress(100)
            titleText.text = "✨ Subtitles Generated!"
            detailText.text = "$totalCompleted video(s) completed in $folderName"
            cancelBtn.visibility = View.GONE
            mainHandler.postDelayed({
                cardView.visibility = View.GONE
                cancelBtn.visibility = View.VISIBLE
            }, 3000L)
        }
    }

    override fun onCancelled() {
        mainHandler.post {
            mainHandler.removeCallbacks(interpolationRunnable)
            isInterpolating = false
            titleText.text = "Subtitle Generation Cancelled"
            detailText.text = "Process stopped by user"
            mainHandler.postDelayed({ cardView.visibility = View.GONE }, 1500L)
        }
    }
}
