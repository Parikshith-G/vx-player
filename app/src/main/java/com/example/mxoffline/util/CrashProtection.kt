/**
 * Role: Global uncaught exception interceptor and crash diagnostic recorder.
 * Responsibility: Traps unhandled crashes, writes stack traces to storage, and notifies user upon app restart.
 * Details: Enables immediate pin-pointing of crashes without requiring adb logcat connection.
 */
package com.example.mxoffline.util

import android.app.Activity
import android.content.Context
import android.os.Environment
import android.util.Log
import android.widget.Toast
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

object CrashProtection {
    private const val TAG = "VXPlayerCrash"
    private const val CRASH_FILE_NAME = "crash_latest.txt"
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val appContext = context.applicationContext
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                Log.e(TAG, "FATAL CRASH on thread ${thread.name}: ${throwable.message}", throwable)
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val stackTrace = sw.toString()

                val info = buildString {
                    append("Timestamp: ").append(System.currentTimeMillis()).append("\n")
                    append("Thread: ").append(thread.name).append("\n")
                    append("Exception: ").append(throwable::class.java.name).append(": ").append(throwable.message).append("\n\n")
                    append(stackTrace)
                }

                // 1. Save to app private storage
                val privateFile = File(appContext.filesDir, CRASH_FILE_NAME)
                privateFile.writeText(info)

                // 2. Try saving to external public VXPlayer folder if possible
                val pubDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "VXPlayer")
                if (!pubDir.exists()) pubDir.mkdirs()
                File(pubDir, "crash.log").writeText(info)
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun checkAndNotifyCrash(activity: Activity) {
        val file = File(activity.filesDir, CRASH_FILE_NAME)
        if (file.exists() && file.canRead()) {
            runCatching {
                val lines = file.readLines()
                val summary = lines.find { it.startsWith("Exception:") }?.removePrefix("Exception:")?.trim() ?: "Crash detected"
                Toast.makeText(activity, "Previous crash: $summary", Toast.LENGTH_LONG).show()
                val archivedFile = File(activity.filesDir, "crash_previous.txt")
                file.copyTo(archivedFile, overwrite = true)
                file.delete()
            }
        }
    }
}
