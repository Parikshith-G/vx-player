/**
 * Role: Defensive type-safe SharedPreferences accessor and converter.
 * Responsibility: Protects against ClassCastException caused by type changes across app updates.
 * Details: Safely reads Long, Int, Boolean, Float, and StringSet by inspecting raw preference values.
 */
package com.example.mxoffline.util

import android.content.SharedPreferences

object PreferenceHelper {

    fun safeGetLong(prefs: SharedPreferences, key: String, defValue: Long = 0L): Long {
        return runCatching {
            val v = prefs.all[key] ?: return defValue
            when (v) {
                is Long -> v
                is Number -> v.toLong()
                is String -> v.toLongOrNull() ?: defValue
                is Boolean -> if (v) 1L else 0L
                else -> defValue
            }
        }.getOrDefault(defValue)
    }

    fun safeGetInt(prefs: SharedPreferences, key: String, defValue: Int = 0): Int {
        return runCatching {
            val v = prefs.all[key] ?: return defValue
            when (v) {
                is Int -> v
                is Number -> v.toInt()
                is String -> v.toIntOrNull() ?: defValue
                is Boolean -> if (v) 1 else 0
                else -> defValue
            }
        }.getOrDefault(defValue)
    }

    fun safeGetBoolean(prefs: SharedPreferences, key: String, defValue: Boolean = false): Boolean {
        return runCatching {
            val v = prefs.all[key] ?: return defValue
            when (v) {
                is Boolean -> v
                is Number -> v.toInt() != 0
                is String -> v.toBooleanStrictOrNull() ?: (v.toIntOrNull()?.let { it != 0 } ?: defValue)
                else -> defValue
            }
        }.getOrDefault(defValue)
    }

    fun safeGetFloat(prefs: SharedPreferences, key: String, defValue: Float = 0f): Float {
        return runCatching {
            val v = prefs.all[key] ?: return defValue
            when (v) {
                is Float -> v
                is Number -> v.toFloat()
                is String -> v.toFloatOrNull() ?: defValue
                else -> defValue
            }
        }.getOrDefault(defValue)
    }

    fun safeGetStringSet(prefs: SharedPreferences, key: String, defValue: Set<String>? = null): Set<String>? {
        return runCatching {
            val v = prefs.all[key] ?: return defValue
            if (v is Set<*>) {
                @Suppress("UNCHECKED_CAST")
                v as Set<String>
            } else defValue
        }.getOrDefault(defValue)
    }
}
