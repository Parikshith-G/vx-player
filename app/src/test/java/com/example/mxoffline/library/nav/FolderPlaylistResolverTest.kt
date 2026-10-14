package com.example.mxoffline.library.nav

import android.net.FakeUri
import com.example.mxoffline.model.FolderItem
import com.example.mxoffline.model.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Test

class FolderPlaylistResolverTest {

    @Test
    fun testResolvesFolderVideosInNaturalOrder() {
        val dummyUri1 = FakeUri("video1")
        val dummyUri2 = FakeUri("video2")
        val dummyUri3 = FakeUri("video3")

        val video1 = VideoItem(id = 1L, name = "Episode 1.mp4", uri = dummyUri1, folderName = "Anime")
        val video2 = VideoItem(id = 2L, name = "Episode 2.mp4", uri = dummyUri2, folderName = "Anime")
        val video3 = VideoItem(id = 3L, name = "Movie.mp4", uri = dummyUri3, folderName = "Downloads")

        val allVideos = listOf(video3, video2, video1)
        val animeFolder = FolderItem(name = "Anime", videoCount = 2, latestVideoUri = dummyUri2, latestVideoId = 2L, videos = listOf(video2, video1))
        val downloadsFolder = FolderItem(name = "Downloads", videoCount = 1, latestVideoUri = dummyUri3, latestVideoId = 3L, videos = listOf(video3))
        val folders = listOf(animeFolder, downloadsFolder)

        // Resolve when playing video1 from recents (sortMode = 0: Name A-Z natural order)
        val (playlist, index) = FolderPlaylistResolver.resolve(
            video = video1,
            allDeviceVideos = allVideos,
            deviceFolders = folders,
            sortMode = 0
        )

        // Playlist should be Anime folder videos sorted: Episode 1, Episode 2
        assertEquals(2, playlist.size)
        assertEquals("Episode 1.mp4", playlist[0].name)
        assertEquals("Episode 2.mp4", playlist[1].name)
        assertEquals(0, index)

        // Resolve when playing video2 from recents
        val (playlist2, index2) = FolderPlaylistResolver.resolve(
            video = video2,
            allDeviceVideos = allVideos,
            deviceFolders = folders,
            sortMode = 0
        )
        assertEquals(2, playlist2.size)
        assertEquals(1, index2)
    }

    @Test
    fun testFallbackToSingleVideoWhenNoFolderMatches() {
        val standaloneVideo = VideoItem(id = 99L, name = "Standalone.mp4", uri = FakeUri("standalone"), folderName = "")
        val (playlist, index) = FolderPlaylistResolver.resolve(
            video = standaloneVideo,
            allDeviceVideos = emptyList(),
            deviceFolders = emptyList(),
            sortMode = 0
        )

        assertEquals(1, playlist.size)
        assertEquals("Standalone.mp4", playlist[0].name)
        assertEquals(0, index)
    }
}
