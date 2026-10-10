/**
 * Role: Quick action circular button bar and safe deletion coordinator.
 * Responsibility: Renders customizable top circular action buttons and executes 4-tap safety video deletion.
 * Details: Integrates MediaStore scoped-storage delete requests, DocumentsContract SAF, and button customization dialogs.
 */
package com.example.mxoffline.player.buttons

import android.app.AlertDialog
import android.app.RecoverableSecurityException
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.example.mxoffline.player.PlayerHudController
import com.example.mxoffline.util.UiUtils
import java.io.File

class PlayerQuickButtonsManager(
    private val activity: ComponentActivity,
    private val settingsPrefs: SharedPreferences,
    private val handler: Handler,
    private val quickButtonsLayout: LinearLayout,
    private val hudController: PlayerHudController,
    private val deleteLauncher: ActivityResultLauncher<IntentSenderRequest>,
    private val callbacks: Callbacks
) {

    interface Callbacks {
        fun onSpeedClicked(anchor: TextView?)
        fun onSkip80Clicked()
        fun onOrientationClicked()
        fun onAspectClicked()
        fun onPlaylistClicked()
        fun onAudioClicked()
        fun onSubtitleClicked()
        fun onDecoderClicked()
        fun onTimerClicked()
        fun onVideoDeletedSuccess()
        fun onButtonInteracted()
        fun getCurrentUri(): String?
        fun getCurrentName(): String
        fun getCurrentSize(): Long = 0L
        fun getOrientationLabel(): String
        fun getAspectLabel(): String
        fun isSoftwareDecoder(): Boolean
    }

    val allQuickButtons = listOf(
        "speed" to "Playback Speed",
        "skip80" to "Skip 80s (Anime OP)",
        "orientation" to "Orientation Lock",
        "aspect" to "Fit / Aspect Ratio",
        "playlist" to "In-Player Playlist",
        "delete" to "Delete Video (4 Taps)",
        "audio" to "Audio Tracks",
        "subtitle" to "Subtitles",
        "decoder" to "HW / SW Decoder",
        "timer" to "Sleep Timer"
    )

    private var speedCircularBtn: TextView? = null
    private var decoderCircularBtn: TextView? = null
    private var aspectCircularBtn: TextView? = null
    private var orientationCircularBtn: TextView? = null
    private var deleteCircularBtn: TextView? = null

    private var deleteTapCount = 0
    private val deleteResetRunnable = Runnable { resetDeleteTaps() }

    fun renderButtons() {
        val dp = { v: Int -> UiUtils.dp(activity, v) }
        quickButtonsLayout.removeAllViews()

        val savedKeys = settingsPrefs.getStringSet("top_quick_buttons", null)
            ?: setOf("speed", "skip80", "orientation", "aspect", "playlist", "delete")

        fun quickBtn(text: String, onClick: () -> Unit) = TextView(activity).apply {
            this.text = text
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = UiUtils.rounded(0xdd1e222e.toInt(), 17, activity)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            val lp = LinearLayout.LayoutParams(-2, dp(34)).apply { rightMargin = dp(8) }
            layoutParams = lp
            setOnClickListener {
                onClick()
                callbacks.onButtonInteracted()
            }
        }

        allQuickButtons.forEach { (key, _) ->
            if (savedKeys.contains(key)) {
                when (key) {
                    "speed" -> {
                        val btn = quickBtn("Speed") { callbacks.onSpeedClicked(speedCircularBtn) }
                        speedCircularBtn = btn
                        quickButtonsLayout.addView(btn)
                    }
                    "skip80" -> quickButtonsLayout.addView(quickBtn("80s OP") { callbacks.onSkip80Clicked() })
                    "orientation" -> {
                        val btn = quickBtn(callbacks.getOrientationLabel()) { callbacks.onOrientationClicked() }
                        orientationCircularBtn = btn
                        quickButtonsLayout.addView(btn)
                    }
                    "aspect" -> {
                        val btn = quickBtn(callbacks.getAspectLabel()) { callbacks.onAspectClicked() }
                        aspectCircularBtn = btn
                        quickButtonsLayout.addView(btn)
                    }
                    "playlist" -> {
                        val btn = quickBtn("List") { callbacks.onPlaylistClicked() }
                        quickButtonsLayout.addView(btn)
                    }
                    "delete" -> {
                        val btn = quickBtn("Del") { handleDeleteButtonTap() }
                        deleteCircularBtn = btn
                        quickButtonsLayout.addView(btn)
                    }
                    "audio" -> quickButtonsLayout.addView(quickBtn("Audio") { callbacks.onAudioClicked() })
                    "subtitle" -> quickButtonsLayout.addView(quickBtn("Sub") { callbacks.onSubtitleClicked() })
                    "decoder" -> {
                        val btn = quickBtn(if (callbacks.isSoftwareDecoder()) "SW" else "HW") { callbacks.onDecoderClicked() }
                        decoderCircularBtn = btn
                        quickButtonsLayout.addView(btn)
                    }
                    "timer" -> quickButtonsLayout.addView(quickBtn("Timer") { callbacks.onTimerClicked() })
                }
            }
        }
    }

    fun updateDynamicLabels() {
        orientationCircularBtn?.text = callbacks.getOrientationLabel()
        aspectCircularBtn?.text = callbacks.getAspectLabel()
        decoderCircularBtn?.text = if (callbacks.isSoftwareDecoder()) "SW" else "HW"
    }

    private fun handleDeleteButtonTap() {
        deleteTapCount++
        handler.removeCallbacks(deleteResetRunnable)

        when (deleteTapCount) {
            1 -> {
                deleteCircularBtn?.text = "3 more"
                deleteCircularBtn?.setTextColor(0xffff7777.toInt())
                hudController.showQuickFeedback("Tap 3 more times to delete")
                handler.postDelayed(deleteResetRunnable, 2500)
            }
            2 -> {
                deleteCircularBtn?.text = "2 more"
                deleteCircularBtn?.setTextColor(0xffff5555.toInt())
                hudController.showQuickFeedback("Tap 2 more times to delete")
                handler.postDelayed(deleteResetRunnable, 2500)
            }
            3 -> {
                deleteCircularBtn?.text = "1 more!"
                deleteCircularBtn?.setTextColor(0xffff2222.toInt())
                hudController.showQuickFeedback("Tap 1 more time to delete!")
                handler.postDelayed(deleteResetRunnable, 2500)
            }
            4 -> {
                resetDeleteTaps()
                deleteCurrentVideo()
            }
            else -> resetDeleteTaps()
        }
    }

    fun resetDeleteTaps() {
        deleteTapCount = 0
        handler.removeCallbacks(deleteResetRunnable)
        deleteCircularBtn?.text = "Del"
        deleteCircularBtn?.setTextColor(Color.WHITE)
    }

    fun deleteCurrentVideo() {
        val uriStr = callbacks.getCurrentUri() ?: return
        val currentUri = Uri.parse(uriStr)
        val currentName = callbacks.getCurrentName()
        var successfullyDeleted = false

        if (currentUri.scheme == "file") {
            runCatching {
                val f = File(currentUri.path ?: "")
                if (f.exists()) successfullyDeleted = f.delete()
            }
        }

        if (!successfullyDeleted && DocumentsContract.isDocumentUri(activity, currentUri)) {
            runCatching {
                successfullyDeleted = DocumentsContract.deleteDocument(activity.contentResolver, currentUri)
            }
        }

        if (!successfullyDeleted) {
            try {
                val rows = activity.contentResolver.delete(currentUri, null, null)
                if (rows > 0) successfullyDeleted = true
            } catch (e: SecurityException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                    val intentSender = e.userAction.actionIntent.intentSender
                    deleteLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                    return
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    runCatching {
                        val pi = MediaStore.createDeleteRequest(activity.contentResolver, listOf(currentUri))
                        deleteLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                        return
                    }
                }
            } catch (e: Exception) {
                // Fallback
            }
        }

        if (!successfullyDeleted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && currentUri.scheme == "content") {
            val launched = runCatching {
                val pi = MediaStore.createDeleteRequest(activity.contentResolver, listOf(currentUri))
                deleteLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                true
            }.getOrDefault(false)
            if (launched) return
        }

        if (successfullyDeleted) {
            callbacks.onVideoDeletedSuccess()
        } else {
            Toast.makeText(activity, "Could not delete $currentName from storage, removing from playlist", Toast.LENGTH_SHORT).show()
            callbacks.onVideoDeletedSuccess()
        }
    }

    fun showCustomizeDialog() {
        val savedKeys = settingsPrefs.getStringSet("top_quick_buttons", null)
            ?: setOf("speed", "orientation", "aspect", "playlist", "delete")
        val checkedItems = BooleanArray(allQuickButtons.size) { i -> savedKeys.contains(allQuickButtons[i].first) }
        val titles = allQuickButtons.map { it.second }.toTypedArray()

        AlertDialog.Builder(activity)
            .setTitle("Quick Action Buttons")
            .setMultiChoiceItems(titles, checkedItems) { _, which, isChecked -> checkedItems[which] = isChecked }
            .setPositiveButton("Save") { _, _ ->
                val newSelected = mutableSetOf<String>()
                for (i in checkedItems.indices) {
                    if (checkedItems[i]) newSelected.add(allQuickButtons[i].first)
                }
                settingsPrefs.edit().putStringSet("top_quick_buttons", newSelected).apply()
                renderButtons()
                hudController.showQuickFeedback("Quick buttons updated")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
