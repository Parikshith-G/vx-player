package com.example.mxoffline.model

import android.net.Uri

data class VideoItem(
    val id: Long = 0L,
    val name: String,
    val uri: Uri,
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val dateModified: Long = 0L,
    val folderName: String = "",
    val resolution: String = ""
)

data class FolderItem(
    val name: String,
    val videoCount: Int,
    val latestVideoUri: Uri?,
    val latestVideoId: Long = 0L,
    val videos: List<VideoItem>
)

data class SafEntry(
    val name: String,
    val documentId: String,
    val isDirectory: Boolean,
    val sizeBytes: Long? = null,
    val modified: Long? = null
)
