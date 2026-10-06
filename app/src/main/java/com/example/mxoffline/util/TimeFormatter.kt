package com.example.mxoffline.util

import java.util.Locale

object TimeFormatter {
    fun formatTime(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val sec = totalSec % 60
        val min = (totalSec / 60) % 60
        val hrs = totalSec / 3600
        return if (hrs > 0) "%d:%02d:%02d".format(hrs, min, sec) else "%02d:%02d".format(min, sec)
    }

    fun formatDuration(ms: Long): String = formatTime(ms)

    fun formatSpeed(speed: Float): String {
        return if (speed % 1f == 0f) {
            "${speed.toInt()}×"
        } else {
            "${String.format(Locale.US, "%.2f", speed).trimEnd('0').trimEnd('.')}×"
        }
    }
}
