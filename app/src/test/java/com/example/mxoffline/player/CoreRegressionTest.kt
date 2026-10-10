package com.example.mxoffline.player

import android.content.Context
import com.example.mxoffline.player.playlist.PlayerPlaylistController
import com.example.mxoffline.player.screen.PlayerScreenController
import com.example.mxoffline.util.VideoIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreRegressionTest {

    @Test
    fun testPlaylistRemoveCurrentLastItemIndex() {
        val uris = arrayListOf("uri1", "uri2", "uri3")
        val names = arrayListOf("Video 1", "Video 2", "Video 3")
        val sizes = arrayListOf(100L, 200L, 300L)
        val controller = PlayerPlaylistController(
            context = null,
            uris = uris,
            names = names,
            initialIndex = 2, // Last item
            sizes = sizes,
            onVideoSelected = {}
        )

        val result = controller.removeCurrent()
        // Removed item should be index 2, and new index wrapped/set to 0
        assertEquals(2, result.removedIndex)
        assertEquals(0, result.nextIndex)
        assertFalse(result.isEmpty)
        assertEquals(2, uris.size)
    }

    @Test
    fun testPlaylistRemoveCurrentMiddleItem() {
        val uris = arrayListOf("uri1", "uri2", "uri3")
        val names = arrayListOf("Video 1", "Video 2", "Video 3")
        val sizes = arrayListOf(100L, 200L, 300L)
        val controller = PlayerPlaylistController(
            context = null,
            uris = uris,
            names = names,
            initialIndex = 1, // Middle item
            sizes = sizes,
            onVideoSelected = {}
        )

        val result = controller.removeCurrent()
        assertEquals(1, result.removedIndex)
        assertEquals(1, result.nextIndex)
        assertFalse(result.isEmpty)
        assertEquals(2, uris.size)
    }

    @Test
    fun testVideoIdentityNoNameCollision() {
        val uri1 = "content://media/external/video/media/101"
        val uri2 = "content://media/external/video/media/102"
        val sameName = "Episode 01.mp4"

        val keys1 = VideoIdentity.getAllKeysForVideo("seen", uri1, sameName, 1000L)
        val keys2 = VideoIdentity.getAllKeysForVideo("seen", uri2, sameName, 2000L)

        // Ensure keys do not overlap between different files with different sizes/URIs
        assertTrue(keys1.intersect(keys2.toSet()).isEmpty())
        assertFalse(keys1.contains("seen_name_$sameName"))
    }
}
