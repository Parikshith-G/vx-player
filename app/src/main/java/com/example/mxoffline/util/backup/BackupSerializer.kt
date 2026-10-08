/**
 * Role: JSON data serializer and deserializer for VX Player backup files.
 * Responsibility: Converts preferences, seen history, and resume positions to/from JSON structures.
 * Details: Implements additive merging logic to protect user watch history from being wiped out.
 */
package com.example.mxoffline.util.backup

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object BackupSerializer {

    fun createBackupJson(context: Context, extraSeen: Map<String, Long> = emptyMap(), extraResume: Map<String, Long> = emptyMap()): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("timestamp", System.currentTimeMillis())

        val settingsPrefs = context.getSharedPreferences("player_settings", Context.MODE_PRIVATE)
        val settingsObj = JSONObject()
        settingsObj.put("playback_speed", settingsPrefs.getFloat("playback_speed", 1.0f).toDouble())
        settingsObj.put("orientation_mode", settingsPrefs.getInt("orientation_mode", 1))
        settingsObj.put("aspect_mode", settingsPrefs.getInt("aspect_mode", 0))
        settingsObj.put("top_time_mode", settingsPrefs.getInt("top_time_mode", 1))
        settingsObj.put("show_remaining_time", settingsPrefs.getBoolean("show_remaining_time", true))
        settingsObj.put("sw_decoder", settingsPrefs.getBoolean("sw_decoder", false))
        settingsObj.put("bg_play", settingsPrefs.getBoolean("bg_play", false))
        settingsObj.put("hold_speed", settingsPrefs.getFloat("hold_speed", 2.0f).toDouble())
        val pinned = settingsPrefs.getStringSet("pinned_buttons", null)
        if (pinned != null) settingsObj.put("pinned_buttons", JSONArray(pinned))
        root.put("settings", settingsObj)

        val seenPrefs = context.getSharedPreferences("player_seen", Context.MODE_PRIVATE)
        val seenObj = JSONObject()
        for ((k, v) in seenPrefs.all) {
            if (v is Long) seenObj.put(k, v)
            else if (v is Number) seenObj.put(k, v.toLong())
        }
        for ((k, v) in extraSeen) {
            if (!seenObj.has(k)) seenObj.put(k, v)
        }
        root.put("seen", seenObj)

        val resumePrefs = context.getSharedPreferences("player_resume", Context.MODE_PRIVATE)
        val resumeObj = JSONObject()
        for ((k, v) in resumePrefs.all) {
            if (v is Long) resumeObj.put(k, v)
            else if (v is Number) resumeObj.put(k, v.toLong())
        }
        for ((k, v) in extraResume) {
            if (!resumeObj.has(k)) resumeObj.put(k, v)
        }
        root.put("resume", resumeObj)

        val libPrefs = context.getSharedPreferences("library_prefs", Context.MODE_PRIVATE)
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
                if (s.has("playback_speed")) editor.putFloat("playback_speed", s.getDouble("playback_speed").toFloat())
                if (s.has("orientation_mode")) editor.putInt("orientation_mode", s.getInt("orientation_mode"))
                if (s.has("aspect_mode")) editor.putInt("aspect_mode", s.getInt("aspect_mode"))
                if (s.has("top_time_mode")) editor.putInt("top_time_mode", s.getInt("top_time_mode"))
                if (s.has("show_remaining_time")) editor.putBoolean("show_remaining_time", s.getBoolean("show_remaining_time"))
                if (s.has("sw_decoder")) editor.putBoolean("sw_decoder", s.getBoolean("sw_decoder"))
                if (s.has("bg_play")) editor.putBoolean("bg_play", s.getBoolean("bg_play"))
                if (s.has("hold_speed")) editor.putFloat("hold_speed", s.getDouble("hold_speed").toFloat())
                if (s.has("pinned_buttons")) {
                    val arr = s.getJSONArray("pinned_buttons")
                    val set = HashSet<String>()
                    for (i in 0 until arr.length()) set.add(arr.getString(i))
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
                    editor.putLong(k, seenObj.getLong(k))
                }
                editor.apply()
            }

            if (root.has("resume")) {
                val resObj = root.getJSONObject("resume")
                val editor = context.getSharedPreferences("player_resume", Context.MODE_PRIVATE).edit()
                val keys = resObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    editor.putLong(k, resObj.getLong(k))
                }
                editor.apply()
            }

            if (root.has("library")) {
                val libObj = root.getJSONObject("library")
                val editor = context.getSharedPreferences("library_prefs", Context.MODE_PRIVATE).edit()
                val keys = libObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    when (val v = libObj.get(k)) {
                        is String -> editor.putString(k, v)
                        is Boolean -> editor.putBoolean(k, v)
                        is Int -> editor.putInt(k, v)
                        is Long -> editor.putLong(k, v)
                        is Double -> editor.putFloat(k, v.toFloat())
                    }
                }
                editor.apply()
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
                while (keys.hasNext()) { val k = keys.next(); seen[k] = seenObj.getLong(k) }
            }
            if (root.has("resume")) {
                val resObj = root.getJSONObject("resume")
                val keys = resObj.keys()
                while (keys.hasNext()) { val k = keys.next(); resume[k] = resObj.getLong(k) }
            }
        }
        return Pair(seen, resume)
    }
}
