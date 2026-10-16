/**
 * Role: Energy-based Voice Activity Detector (VAD) and speech segmenter.
 * Responsibility: Scans 16kHz PCM audio to identify speech intervals with start/end timestamps.
 * Details: Skips pure silence and instrumental background music to accelerate offline inference.
 */
package com.example.mxoffline.subtitles.audio

import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

object VoiceActivityDetector {

    data class SpeechChunk(
        val startMs: Long,
        val endMs: Long,
        val pcmData: ShortArray
    )

    private const val SAMPLE_RATE = 16000
    private const val FRAME_MS = 30
    private const val FRAME_SAMPLES = (SAMPLE_RATE * FRAME_MS) / 1000 // 480 samples
    private const val ENERGY_THRESHOLD = 500.0 // RMS threshold for speech presence
    private const val SILENCE_TOLERANCE_FRAMES = 15 // ~450ms of silence allowed inside a sentence
    private const val MIN_SPEECH_DURATION_MS = 600L
    private const val MAX_SPEECH_DURATION_MS = 12000L
    private const val MAX_CHUNK_SAMPLES = (SAMPLE_RATE * MAX_SPEECH_DURATION_MS / 1000).toInt() // 192,000 samples

    fun detectSpeechChunks(pcmFile: File, isCancelled: () -> Boolean = { false }): List<SpeechChunk> {
        val chunks = mutableListOf<SpeechChunk>()
        if (!pcmFile.exists() || pcmFile.length() < FRAME_SAMPLES * 2) return chunks

        val buffer = ShortArray(FRAME_SAMPLES)
        val byteBuf = ByteArray(FRAME_SAMPLES * 2)

        // Primitive chunk buffer to eliminate boxed object allocations and GC freezes
        val chunkBuffer = ShortArray(MAX_CHUNK_SAMPLES)
        var chunkSampleCount = 0
        var chunkStartSample = 0L
        var silentFrameCount = 0
        var isInSpeech = false
        var currentSampleIndex = 0L

        FileInputStream(pcmFile).use { inStream ->
            while (!isCancelled()) {
                val bytesRead = inStream.read(byteBuf)
                if (bytesRead < FRAME_SAMPLES * 2) break

                ByteBuffer.wrap(byteBuf).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(buffer)
                val rms = calculateRms(buffer)
                val hasSpeech = rms > ENERGY_THRESHOLD

                if (hasSpeech) {
                    if (!isInSpeech) {
                        isInSpeech = true
                        chunkStartSample = currentSampleIndex
                        chunkSampleCount = 0
                    }
                    silentFrameCount = 0
                    if (chunkSampleCount + FRAME_SAMPLES <= MAX_CHUNK_SAMPLES) {
                        System.arraycopy(buffer, 0, chunkBuffer, chunkSampleCount, FRAME_SAMPLES)
                        chunkSampleCount += FRAME_SAMPLES
                    }
                } else if (isInSpeech) {
                    silentFrameCount++
                    if (chunkSampleCount + FRAME_SAMPLES <= MAX_CHUNK_SAMPLES) {
                        System.arraycopy(buffer, 0, chunkBuffer, chunkSampleCount, FRAME_SAMPLES)
                        chunkSampleCount += FRAME_SAMPLES
                    }

                    val chunkDurationMs = (chunkSampleCount * 1000L) / SAMPLE_RATE
                    if (silentFrameCount >= SILENCE_TOLERANCE_FRAMES || chunkDurationMs >= MAX_SPEECH_DURATION_MS) {
                        if (chunkDurationMs >= MIN_SPEECH_DURATION_MS) {
                            val endSample = chunkStartSample + chunkSampleCount
                            val startMs = (chunkStartSample * 1000L) / SAMPLE_RATE
                            val endMs = (endSample * 1000L) / SAMPLE_RATE
                            chunks.add(SpeechChunk(startMs, endMs, chunkBuffer.copyOf(chunkSampleCount)))
                        }
                        isInSpeech = false
                        silentFrameCount = 0
                        chunkSampleCount = 0
                    }
                }
                currentSampleIndex += FRAME_SAMPLES
            }
        }

        // Flush trailing chunk
        if (isInSpeech && chunkSampleCount > 0) {
            val chunkDurationMs = (chunkSampleCount * 1000L) / SAMPLE_RATE
            if (chunkDurationMs >= MIN_SPEECH_DURATION_MS) {
                val startMs = (chunkStartSample * 1000L) / SAMPLE_RATE
                val endMs = startMs + chunkDurationMs
                chunks.add(SpeechChunk(startMs, endMs, chunkBuffer.copyOf(chunkSampleCount)))
            }
        }

        return chunks
    }

    private fun calculateRms(samples: ShortArray): Double {
        var sum = 0.0
        for (s in samples) {
            sum += s.toDouble() * s.toDouble()
        }
        return sqrt(sum / samples.size)
    }
}
