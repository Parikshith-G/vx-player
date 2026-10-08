/**
 * Role: JSON data serializer and deserializer for VX Player backup files.
 * Responsibility: Converts preferences, seen history, and resume positions to/from JSON structures.
 * Details: Implements additive merging logic to protect user watch history from being wiped out.
 */
package com.example.mxoffline.util.backup

import android.content.Context
import com.example.mxoffline.util.PreferenceHelper
import org.json.JSONArray
import org.json.JSONObject

object BackupSerializer {

    fun createBackupJson(context: Context, extraSeen: Map<String, Long> = emptyMap(), extraResume: Map<String, Long> = emptyMap()): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("timestamp", System.currentTimeMillis())

        val settingsPrefs = context.getSharedPreferences("player_settings", Context.MODE_PRIVATE)
        val settingsObj = JSONObject()
        settingsObj.put("playback_speed", PreferenceHelper.safeGetFloat(settingsPrefs, "playback_speed", 1.0f).toDouble())
        settingsObj.put("orientation_mode", PreferenceHelper.safeGetInt(settingsPrefs, "orientation_mode", 1))
        settingsObj.put("aspect_mode", PreferenceHelper.safeGetInt(settingsPrefs, "aspect_mode", 0))
        settingsObj.put("top_time_mode", PreferenceHelper.safeGetInt(settingsPrefs, "top_time_mode", 1))
        settingsObj.put("show_remaining_time", PreferenceHelper.safeGetBoolean(settingsPrefs, "show_remaining_time", true))
        settingsObj.put("sw_decoder", PreferenceHelper.safeGetBoolean(settingsPrefs, "sw_decoder", false))
        settingsObj.put("bg_play", PreferenceHelper.safeGetBoolean(settingsPrefs, "bg_play", false))
        settingsObj.put("hold_speed", PreferenceHelper.safeGetFloat(settingsPrefs, "hold_speed", 2.0f).toDouble())
        val quickBtns = PreferenceHelper.safeGetStringSet(settingsPrefs, "top_quick_buttons", null)
            ?: PreferenceHelper.safeGetStringSet(settingsPrefs, "pinned_buttons", null)
        if (quickBtns != null) {
            settingsObj.put("top_quick_buttons", JSONArray(quickBtns))
            settingsObj.put("pinned_buttons", JSONArray(quickBtns))
        }
        root.put("settings", settingsObj)

        val seenPrefs = context.getSharedPreferences("player_seen", Context.MODE_PRIVATE)
        val seenObj = JSONObject()
        for ((k, v) in seenPrefs.all) {
            val num = when (v) {
                is Long -> v
                is Number -> v.toLong()
                is String -> v.toLongOrNull()
                is Boolean -> if (v) System.currentTimeMillis() else null
                else -> null
            }
            if (num != null) seenObj.put(k, num)
        }
        for ((k, v) in extraSeen) {
            if (!seenObj.has(k)) seenObj.put(k, v)
        }
        root.put("seen", seenObj)

        val resumePrefs = context.getSharedPreferences("player_resume", Context.MODE_PRIVATE)
        val resumeObj = JSONObject()
        for ((k, v) in resumePrefs.all) {
            val num = when (v) {
                is Long -> v
                is Number -> v.toLong()
                is String -> v.toLongOrNull()
                else -> null
            }
            if (num != null) resumeObj.put(k, num)
        }
        for ((k, v) in extraResume) {
            if (!resumeObj.has(k)) resumeObj.put(k, v)
        }
        root.put("resume", resumeObj)

        val libPrefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)
        val libObj = JSONObject()
        for ((k, v) in libPrefs.all) libObj.put(k, v)
        root.put("library", libObj)

        return root.toString(2)
    }

    fun restoreFromJson(context: Context, jsonString: String): Boolean {
        return runCatching {
            val root = JSONObject(jsonString)

            if (root.has("settings")) {
                val s = root.getJSONObject("settings")
                val editor = context.getSharedPreferences("player_settings", Context.MODE_PRIVATE).edit()
                if (s.has("playback_speed")) editor.putFloat("playback_speed", s.optDouble("playback_speed", 1.0).toFloat())
                if (s.has("orientation_mode")) editor.putInt("orientation_mode", s.optInt("orientation_mode", 1))
                if (s.has("aspect_mode")) editor.putInt("aspect_mode", s.optInt("aspect_mode", 0))
                if (s.has("top_time_mode")) editor.putInt("top_time_mode", s.optInt("top_time_mode", 1))
                if (s.has("show_remaining_time")) {
                    val rem = s.optBoolean("show_remaining_time", true)
                    editor.putBoolean("show_remaining_time", rem)
                    editor.putInt("show_remaining_time", if (rem) 1 else 0)
                }
                if (s.has("sw_decoder")) editor.putBoolean("sw_decoder", s.optBoolean("sw_decoder", false))
                if (s.has("bg_play")) editor.putBoolean("bg_play", s.optBoolean("bg_play", false))
                if (s.has("hold_speed")) editor.putFloat("hold_speed", s.optDouble("hold_speed", 2.0).toFloat())
                val btnKey = when {
                    s.has("top_quick_buttons") -> "top_quick_buttons"
                    s.has("pinned_buttons") -> "pinned_buttons"
                    else -> null
                }
                if (btnKey != null) {
                    val arr = s.getJSONArray(btnKey)
                    val set = HashSet<String>()
                    for (i in 0 until arr.length()) set.add(arr.getString(i))
                    editor.putStringSet("top_quick_buttons", set)
                    editor.putStringSet("pinned_buttons", set)
                }
                editor.apply()
            }

            if (root.has("seen")) {
                val seenObj = root.getJSONObject("seen")
                val editor = context.getSharedPreferences("player_seen", Context.MODE_PRIVATE).edit()
                val keys = seenObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = seenObj.optLong(k, 0L).let { if (it > 0) it else (if (seenObj.optBoolean(k, false)) System.currentTimeMillis() else 0L) }
                    if (v > 0L) editor.putLong(k, v)
                }
                editor.apply()
            }

            if (root.has("resume")) {
                val resObj = root.getJSONObject("resume")
                val editor = context.getSharedPreferences("player_resume", Context.MODE_PRIVATE).edit()
                val keys = resObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = resObj.optLong(k, 0L)
                    if (v > 0L) editor.putLong(k, v)
                }
                editor.apply()
            }

            if (root.has("library")) {
                val libObj = root.getJSONObject("library")
                val editor = context.getSharedPreferences("library", Context.MODE_PRIVATE).edit()
                val legacyEditor = context.getSharedPreferences("library_prefs", Context.MODE_PRIVATE).edit()
                val keys = libObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    when (val v = libObj.get(k)) {
                        is String -> { editor.putString(k, v); legacyEditor.putString(k, v) }
                        is Boolean -> { editor.putBoolean(k, v); legacyEditor.putBoolean(k, v) }
                        is Int -> { editor.putInt(k, v); legacyEditor.putInt(k, v) }
                        is Long -> { editor.putLong(k, v); legacyEditor.putLong(k, v) }
                        is Double -> { editor.putFloat(k, v.toFloat()); legacyEditor.putFloat(k, v.toFloat()) }
                    }
                }
                editor.apply()
                legacyEditor.apply()
            }
            true
        }.getOrDefault(false)
    }

    fun extractHistoryMaps(jsonString: String): Pair<Map<String, Long>, Map<String, Long>> {
        val seen = mutableMapOf<String, Long>()
        val resume = mutableMapOf<String, Long>()
        runCatching {
            val root = JSONObject(jsonString)
            if (root.has("seen")) {
                val seenObj = root.getJSONObject("seen")
                val keys = seenObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = seenObj.optLong(k, 0L)
                    if (v > 0L) seen[k] = v
                }
            }
            if (root.has("resume")) {
                val resObj = root.getJSONObject("resume")
                val keys = resObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = resObj.optLong(k, 0L)
                    if (v > 0L) resume[k] = v
                }
            }
        }
        return Pair(seen, resume)
    }
}
