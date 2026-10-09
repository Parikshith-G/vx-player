/**
 * Role: Unit test suite for NaturalOrderComparator.
 * Responsibility: Validates natural alphanumeric ordering for video filenames and playlists.
 * Details: Verifies user-reported bug cases and general alphanumeric comparison logic.
 */
package com.example.mxoffline.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NaturalOrderComparatorTest {

    @Test
    fun testUserReportedAnimeSorting() {
        val input = listOf("animename_1000", "animename_2")
        val sorted = input.sortedWith(NaturalOrderComparator)
        assertEquals(listOf("animename_2", "animename_1000"), sorted)
    }

    @Test
    fun testAnimeEpisodeNumbers() {
        val input = listOf(
            "Episode 100",
            "Episode 2",
            "Episode 1",
            "Episode 1000",
            "Episode 20",
            "Episode 10"
        )
        val expected = listOf(
            "Episode 1",
            "Episode 2",
            "Episode 10",
            "Episode 20",
            "Episode 100",
            "Episode 1000"
        )
        assertEquals(expected, input.sortedWith(NaturalOrderComparator))
    }

    @Test
    fun testLeadingZerosAndTieBreakers() {
        val input = listOf("002", "10", "01", "2", "000")
        val sorted = input.sortedWith(NaturalOrderComparator)
        assertEquals(listOf("000", "01", "002", "2", "10"), sorted)
    }

    @Test
    fun testReleaseGroupFilenames() {
        val input = listOf(
            "[Sub] Naruto - 100 [1080p].mkv",
            "[Sub] Naruto - 2 [1080p].mkv",
            "[Sub] Naruto - 03 [1080p].mkv",
            "[Sub] Naruto - 1000 [1080p].mkv"
        )
        val expected = listOf(
            "[Sub] Naruto - 2 [1080p].mkv",
            "[Sub] Naruto - 03 [1080p].mkv",
            "[Sub] Naruto - 100 [1080p].mkv",
            "[Sub] Naruto - 1000 [1080p].mkv"
        )
        assertEquals(expected, input.sortedWith(NaturalOrderComparator))
    }

    @Test
    fun testCaseInsensitiveAndContract() {
        assertTrue(NaturalOrderComparator.compare("Anime 2", "anime 10") < 0)
        assertTrue(NaturalOrderComparator.compare("anime 10", "Anime 2") > 0)
        assertEquals(0, NaturalOrderComparator.compare("test", "test"))
        assertTrue(NaturalOrderComparator.compare(null, "test") < 0)
        assertTrue(NaturalOrderComparator.compare("test", null) > 0)
    }
}
