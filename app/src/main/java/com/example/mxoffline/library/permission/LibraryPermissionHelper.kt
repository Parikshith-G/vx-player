/**
 * Role: Android runtime storage permission checker and resolver.
 * Responsibility: Checks READ_MEDIA_VIDEO and READ_EXTERNAL_STORAGE permissions across Android versions.
 * Details: Provides backward-compatible permission strings for Android 13+ (Tiramisu) and legacy storage.
 */
package com.example.mxoffline.library.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object LibraryPermissionHelper {

    fun getRequiredPermission(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    fun hasPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, getRequiredPermission()) == PackageManager.PERMISSION_GRANTED
    }
}
