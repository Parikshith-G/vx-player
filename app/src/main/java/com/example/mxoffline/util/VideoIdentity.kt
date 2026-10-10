/**
 * Role: Canonical key generator for cross-install video identity.
 * Responsibility: Builds robust lookup keys using URIs, file names, and byte sizes.
 * Details: Ensures seen history and resume positions survive MediaStore re-indexing and app uninstalls.
 */
package com.example.mxoffline.util

import com.example.mxoffline.model.VideoItem

object VideoIdentity {

    fun getUriKey(prefix: String, uriString: String): String = "${prefix}_$uriString"

    fun getNameKey(prefix: String, fileName: String): String = "${prefix}_name_$fileName"

    fun getMetaKey(prefix: String, fileName: String, sizeBytes: Long): String {
        return if (sizeBytes > 0) "${prefix}_meta_${fileName}_$sizeBytes" else getNameKey(prefix, fileName)
    }

    fun getAllKeysForVideo(prefix: String, uriString: String, name: String, sizeBytes: Long = 0L): List<String> {
        val keys = mutableListOf<String>()
        if (uriString.isNotBlank()) keys.add(getUriKey(prefix, uriString))
        if (name.isNotBlank() && sizeBytes > 0) {
            keys.add(getMetaKey(prefix, name, sizeBytes))
        }
        return keys
    }

    fun getAllKeys(prefix: String, video: VideoItem): List<String> {
        return getAllKeysForVideo(prefix, video.uri.toString(), video.name, video.sizeBytes)
    }
}
