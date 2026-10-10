/**
 * Role: Local storage and ContentResolver media scanner.
 * Responsibility: Queries device MediaStore and SAF trees for video files and aggregates folders.
 * Details: Extracts metadata including resolutions, durations, file sizes, and folder groupings.
 */
package com.example.mxoffline.library

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.File
import com.example.mxoffline.model.FolderItem
import com.example.mxoffline.model.SafEntry
import com.example.mxoffline.model.VideoItem

object MediaScanner {
    val VIDEO_EXTENSIONS = setOf(
        "mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v", "ts", "flv", "wmv", "vob", "ogv", "mpg", "mpeg", "rmvb", "asf"
    )

    private const val DIRECTORY_MIME_TYPE = "vnd.android.document/directory"

    fun isVideo(name: String, mime: String? = null): Boolean {
        if (mime?.startsWith("video/", true) == true) return true
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in VIDEO_EXTENSIONS
    }

    fun scanDeviceVideos(contentResolver: ContentResolver): List<VideoItem> {
        val items = mutableListOf<VideoItem>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_MODIFIED,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Video.Media.BUCKET_DISPLAY_NAME else MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DATA
        )
        val sortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
        val selection = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                "${MediaStore.MediaColumns.IS_TRASHED} = 0 AND ${MediaStore.Video.Media.SIZE} > 0"
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                "${MediaStore.MediaColumns.IS_PENDING} = 0 AND ${MediaStore.Video.Media.SIZE} > 0"
            }
            else -> "${MediaStore.Video.Media.SIZE} > 0"
        }

        runCatching {
            contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                val dateCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)
                val bucketCol = cursor.getColumnIndex(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Video.Media.BUCKET_DISPLAY_NAME else MediaStore.Video.Media.DATA
                )
                val widthCol = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
                val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)

                while (cursor.moveToNext()) {
                    if (dataCol >= 0 && !cursor.isNull(dataCol)) {
                        val path = cursor.getString(dataCol)
                        if (!path.isNullOrBlank()) {
                            val f = File(path)
                            if (!f.exists() || f.length() == 0L) continue
                        }
                    }
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Video"
                    val duration = if (durCol >= 0 && !cursor.isNull(durCol)) cursor.getLong(durCol) else 0L
                    val size = if (sizeCol >= 0 && !cursor.isNull(sizeCol)) cursor.getLong(sizeCol) else 0L
                    val date = if (dateCol >= 0 && !cursor.isNull(dateCol)) cursor.getLong(dateCol) * 1000L else 0L

                    var folder = "Videos"
                    if (bucketCol >= 0 && !cursor.isNull(bucketCol)) {
                        val bucketVal = cursor.getString(bucketCol)
                        folder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            bucketVal ?: "Videos"
                        } else {
                            bucketVal?.substringBeforeLast('/')?.substringAfterLast('/') ?: "Videos"
                        }
                    }

                    val width = if (widthCol >= 0) cursor.getInt(widthCol) else 0
                    val height = if (heightCol >= 0) cursor.getInt(heightCol) else 0
                    val resolution = when {
                        width >= 3840 || height >= 2160 -> "4K"
                        width >= 1920 || height >= 1080 -> "1080p"
                        width >= 1280 || height >= 720 -> "720p"
                        else -> ""
                    }

                    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    items.add(VideoItem(id, name, uri, duration, size, date, folder, resolution))
                }
            }
        }
        return items
    }

    fun groupIntoFolders(videos: List<VideoItem>): List<FolderItem> {
        return videos.groupBy { it.folderName }
            .map { (name, list) ->
                val latest = list.firstOrNull()
                FolderItem(
                    name = name,
                    videoCount = list.size,
                    latestVideoUri = latest?.uri,
                    latestVideoId = latest?.id ?: 0L,
                    videos = list
                )
            }
            .sortedByDescending { it.videoCount }
    }

    fun querySafFolder(contentResolver: ContentResolver, tree: Uri, docId: String): List<SafEntry> {
        val list = mutableListOf<SafEntry>()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            ),
            null, null, null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            val modCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

            while (cursor.moveToNext()) {
                val id = cursor.getString(idCol) ?: continue
                val name = cursor.getString(nameCol) ?: "Untitled"
                val mime = cursor.getString(mimeCol)
                val isDir = mime == DIRECTORY_MIME_TYPE

                if (isDir || isVideo(name, mime)) {
                    val size = if (sizeCol >= 0 && !cursor.isNull(sizeCol)) cursor.getLong(sizeCol) else null
                    val mod = if (modCol >= 0 && !cursor.isNull(modCol)) cursor.getLong(modCol) else null
                    list.add(SafEntry(name, id, isDir, size, mod))
                }
            }
        }
        return list
    }
}
