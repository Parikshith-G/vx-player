/**
 * Role: Top status bar and diagnostic display controller.
 * Responsibility: Updates clock time, battery level percentage, and elapsed/remaining video timers.
 * Details: Positioned at top-left of the persistent status header and toggles done/remaining vs done/total time.
 */
package com.example.mxoffline.player.header

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.BatteryManager
import android.view.View
import android.widget.TextView
import androidx.media3.exoplayer.ExoPlayer
import com.example.mxoffline.player.PlayerHudController
import com.example.mxoffline.util.AppBackupManager
import com.example.mxoffline.util.TimeFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PlayerStatusHeaderManager(
    private val context: Context,
    private val settingsPrefs: SharedPreferences,
    private val headerView: View,
    private val topTimeStatusView: TextView,
    private val batteryText: TextView,
    private val clockText: TextView
) {

    private val clockFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    var topTimeMode: Int = settingsPrefs.getInt("top_time_mode", 1) // 1: done / remaining, 0: done / total
        private set

    fun cycleTopTimeMode(hud: PlayerHudController?): String {
        topTimeMode = (topTimeMode + 1) % 2
        settingsPrefs.edit().putInt("top_time_mode", topTimeMode).apply()
        AppBackupManager.backupToStorageAsync(context)
        val label = if (topTimeMode == 1) "Time: Done / Remaining" else "Time: Done / Total"
        hud?.showQuickFeedback(label)
        return label
    }

    fun update(player: ExoPlayer?) {
        clockText.text = clockFormat.format(Date())
        val bat = getBatteryPercentage()
        batteryText.text = if (bat >= 0) "$bat%" else ""

        if (player != null) {
            val d = player.duration.coerceAtLeast(0)
            val p = player.currentPosition.coerceAtLeast(0)
            val elapsed = TimeFormatter.formatTime(p)
            topTimeStatusView.text = if (topTimeMode == 0) {
                "$elapsed / ${TimeFormatter.formatTime(d)}"
            } else {
                val rem = (d - p).coerceAtLeast(0)
                "$elapsed / -${TimeFormatter.formatTime(rem)}"
            }
        }
    }

    fun setVisible(visible: Boolean) {
        headerView.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun syncFromPreferences() {
        topTimeMode = settingsPrefs.getInt("top_time_mode", 1)
    }

    private fun getBatteryPercentage(): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val batLevel = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        if (batLevel in 0..100) return batLevel

        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100 / scale) else -1
    }
}
