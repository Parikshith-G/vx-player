/**
 * Role: Asynchronous video thumbnail loader and memory cache.
 * Responsibility: Decodes video frame previews and caches bitmaps in an in-memory LruCache.
 * Details: Uses MediaStore thumbnail APIs and MediaMetadataRetriever fallback without network calls.
 */
package com.example.mxoffline.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import android.widget.ImageView
import java.util.concurrent.Executors

object ThumbnailLoader {
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = (maxMemory / 8).coerceAtLeast(1024)

    private val cache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    private val executor = Executors.newFixedThreadPool(4)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun load(context: Context, uri: Uri, id: Long, imageView: ImageView) {
        val key = uri.toString()
        imageView.tag = key
        val cached = cache.get(key)
        if (cached != null) {
            imageView.setImageBitmap(cached)
            return
        }

        imageView.setImageDrawable(null)

        executor.execute {
            var bitmap: Bitmap? = null

            // Method 1: Android 10+ ContentResolver thumbnail
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    bitmap = context.contentResolver.loadThumbnail(uri, Size(320, 240), null)
                } catch (_: Exception) {
                }
            }

            // Method 2: Legacy MediaStore thumbnail
            if (bitmap == null && id > 0) {
                try {
                    @Suppress("DEPRECATION")
                    bitmap = MediaStore.Video.Thumbnails.getThumbnail(
                        context.contentResolver,
                        id,
                        MediaStore.Video.Thumbnails.MINI_KIND,
                        null
                    )
                } catch (_: Exception) {
                }
            }

            // Method 3: MediaMetadataRetriever fallback (works for SAF URIs, file URIs, custom schemes)
            if (bitmap == null) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    bitmap = retriever.getFrameAtTime(1_500_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } catch (_: Exception) {
                } finally {
                    try {
                        retriever.release()
                    } catch (_: Exception) {
                    }
                }
            }

            if (bitmap != null) {
                cache.put(key, bitmap)
                mainHandler.post {
                    if (imageView.tag == key) {
                        imageView.setImageBitmap(bitmap)
                    }
                }
            }
        }
    }
}
