package com.example.mxoffline.subtitles

import com.example.mxoffline.subtitles.audio.VoiceActivityDetector
import com.example.mxoffline.subtitles.engine.SubtitleModelManager
import com.example.mxoffline.subtitles.model.SubtitleSegment
import com.example.mxoffline.subtitles.srt.SrtWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

class SubtitleTest {

    @Test
    fun testSrtTimestampFormatting() {
        assertEquals("00:00:00,000", SrtWriter.formatTimestamp(0L))
        assertEquals("00:00:01,500", SrtWriter.formatTimestamp(1500L))
        assertEquals("00:01:05,432", SrtWriter.formatTimestamp(65432L))
        assertEquals("01:23:45,678", SrtWriter.formatTimestamp(5025678L))
    }

    @Test
    fun testSrtSerialization() {
        val segments = listOf(
            SubtitleSegment(startMs = 1000L, endMs = 3500L, text = "Hello world"),
            SubtitleSegment(startMs = 4000L, endMs = 7200L, text = "Offline subtitles in VX Player")
        )
        val srtContent = SrtWriter.buildSrtContent(segments)
        val expected = """
            1
            00:00:01,000 --> 00:00:03,500
            Hello world

            2
            00:00:04,000 --> 00:00:07,200
            Offline subtitles in VX Player

        """.trimIndent()
        assertEquals(expected.trim(), srtContent.trim())
    }

    @Test
    fun testVoiceActivityDetectorOnSilence() {
        val tempFile = File.createTempFile("silence_test", ".pcm")
        tempFile.deleteOnExit()
        FileOutputStream(tempFile).use { it.write(ByteArray(16000 * 2)) }

        val chunks = VoiceActivityDetector.detectSpeechChunks(tempFile)
        assertTrue("Silent audio should yield zero speech chunks", chunks.isEmpty())
        tempFile.delete()
    }

    @Test
    fun testVoiceActivityDetectorOnTone() {
        val tempFile = File.createTempFile("tone_test", ".pcm")
        tempFile.deleteOnExit()
        val pcm = ByteArray(16000 * 2)
        val sampleRate = 16000
        val freq = 440.0
        for (i in 0 until 16000) {
            val sample = (Math.sin(2.0 * Math.PI * freq * i / sampleRate) * 16000.0).toInt().toShort()
            pcm[i * 2] = (sample.toInt() and 0xFF).toByte()
            pcm[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }
        FileOutputStream(tempFile).use { it.write(pcm) }

        val chunks = VoiceActivityDetector.detectSpeechChunks(tempFile)
        assertTrue("Audible tone should trigger speech detection", chunks.isNotEmpty())
        assertTrue("Segment start should be at or near beginning", chunks.first().startMs >= 0L)
        tempFile.delete()
    }

    @Test
    fun testSupportedModelFiles() {
        assertTrue(SubtitleModelManager.SUPPORTED_MODEL_NAMES.contains("whisper-base.bin"))
        assertTrue(SubtitleModelManager.SUPPORTED_MODEL_NAMES.contains("ggml-base.bin"))
        assertTrue(SubtitleModelManager.SUPPORTED_MODEL_NAMES.contains("ggml-tiny.bin"))
    }

    @Test
    fun testModelDownloadUrls() {
        val baseUrl = com.example.mxoffline.subtitles.dialog.SubtitleModelPromptHelper.WHISPER_BASE_URL
        val tinyUrl = com.example.mxoffline.subtitles.dialog.SubtitleModelPromptHelper.WHISPER_TINY_URL
        assertTrue("Base URL should be valid https", baseUrl.startsWith("https://"))
        assertTrue("Base URL should end with ggml-base.bin", baseUrl.endsWith("ggml-base.bin"))
        assertTrue("Tiny URL should be valid https", tinyUrl.startsWith("https://"))
        assertTrue("Tiny URL should end with ggml-tiny.bin", tinyUrl.endsWith("ggml-tiny.bin"))
    }

    @Test
    fun testModelDisplayNameFormatting() {
        val baseFile = File("ggml-base.bin")
        val tinyFile = File("ggml-tiny.bin")
        val baseDisplay = SubtitleModelManager.formatModelDisplayName(baseFile)
        val tinyDisplay = SubtitleModelManager.formatModelDisplayName(tinyFile)
        assertTrue(baseDisplay.contains("Whisper Base"))
        assertTrue(tinyDisplay.contains("Whisper Tiny"))
    }

    @Test
    fun testChunkDurationConstants() {
        assertEquals(5 * 60 * 1000L, com.example.mxoffline.subtitles.audio.AudioExtractor.CHUNK_DURATION_MS)
        assertEquals(5 * 60 * 1000 * 1000L, com.example.mxoffline.subtitles.audio.AudioExtractor.CHUNK_DURATION_US)
    }

    @Test
    fun testModelValidationAndFilter() {
        val validBase = File.createTempFile("ggml-base", ".bin")
        validBase.deleteOnExit()
        val dummyData = ByteArray(5_000_100)
        FileOutputStream(validBase).use { it.write(dummyData) }
        assertTrue("Valid 5MB ggml-base.bin should pass", SubtitleModelManager.isValidModelFile(validBase))

        val crdownloadFile = File(validBase.parentFile, "ggml-base.bin.crdownload")
        crdownloadFile.deleteOnExit()
        validBase.copyTo(crdownloadFile, overwrite = true)
        assertFalse("In-progress .crdownload should be rejected", SubtitleModelManager.isValidModelFile(crdownloadFile))

        val partFile = File(validBase.parentFile, "ggml-base.part")
        partFile.deleteOnExit()
        validBase.copyTo(partFile, overwrite = true)
        assertFalse("In-progress .part file should be rejected", SubtitleModelManager.isValidModelFile(partFile))

        validBase.delete()
        crdownloadFile.delete()
        partFile.delete()
    }
}
