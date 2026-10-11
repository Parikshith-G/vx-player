/**
 * Role: Overlay controls visibility, screen lock state, and system bars coordinator.
 * Responsibility: Manages overlay auto-hide scheduling, screen lock state, floating unlock button, and transient system bars.
 * Details: Suppresses controls when screen is locked and hides system bars via WindowInsetsControllerCompat.
 */
package com.example.mxoffline.player.screen

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.view.View
import android.view.Window
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class PlayerControlsLockManager(
    private val activity: Activity,
    private val window: Window,
    private val handler: Handler,
    private val overlayContainer: View,
    private val lockFloatingBtn: View,
    private val isPlayerPlaying: () -> Boolean,
    private val isInPip: () -> Boolean
) {
    var controlsVisible: Boolean = true
        private set
    var isScreenLocked: Boolean = false
        private set
    var isTourActive: Boolean = false
        set(value) {
            field = value
            if (value) {
                handler.removeCallbacks(hideControlsRunnable)
                overlayContainer.visibility = View.VISIBLE
                controlsVisible = true
            }
        }

    private val hideControlsRunnable = Runnable {
        if (!isTourActive && isPlayerPlaying() && controlsVisible && !isScreenLocked) {
            overlayContainer.visibility = View.GONE
            controlsVisible = false
        }
    }

    private val hideLockRunnable = Runnable { lockFloatingBtn.visibility = View.GONE }

    fun scheduleHideControls(delayMs: Long = 4500L) {
        if (isTourActive) return
        handler.removeCallbacks(hideControlsRunnable)
        handler.postDelayed(hideControlsRunnable, delayMs)
    }

    fun cancelHideControls() {
        handler.removeCallbacks(hideControlsRunnable)
    }

    fun toggleControls() {
        if (isTourActive) {
            overlayContainer.visibility = View.VISIBLE
            controlsVisible = true
            return
        }
        controlsVisible = !controlsVisible
        overlayContainer.visibility = if (controlsVisible) View.VISIBLE else View.GONE
        if (controlsVisible) {
            hideSystemBars()
            scheduleHideControls()
        } else {
            handler.removeCallbacks(hideControlsRunnable)
        }
    }

    fun setControlsVisible(visible: Boolean) {
        if (isTourActive) {
            overlayContainer.visibility = View.VISIBLE
            controlsVisible = true
            cancelHideControls()
            return
        }
        controlsVisible = visible
        overlayContainer.visibility = if (visible) View.VISIBLE else View.GONE
        if (visible) scheduleHideControls() else handler.removeCallbacks(hideControlsRunnable)
    }

    fun lockScreen() {
        if (isTourActive) return
        isScreenLocked = true
        controlsVisible = false
        overlayContainer.visibility = View.GONE
        showLockTemporarily()
    }

    fun unlockScreen() {
        isScreenLocked = false
        lockFloatingBtn.visibility = View.GONE
        controlsVisible = true
        overlayContainer.visibility = View.VISIBLE
        scheduleHideControls()
    }

    fun showLockTemporarily() {
        lockFloatingBtn.visibility = View.VISIBLE
        handler.removeCallbacks(hideLockRunnable)
        handler.postDelayed(hideLockRunnable, 3000L)
    }

    fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPip()) return
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.systemBars())
    }

    fun release() {
        handler.removeCallbacks(hideControlsRunnable)
        handler.removeCallbacks(hideLockRunnable)
    }
}
