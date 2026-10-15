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

    fun detectSpeechChunks(pcmFile: File, isCancelled: () -> Boolean = { false }): List<SpeechChunk> {
        val chunks = mutableListOf<SpeechChunk>()
        if (!pcmFile.exists() || pcmFile.length() < FRAME_SAMPLES * 2) return chunks

        val totalBytes = pcmFile.length()
        val totalSamples = totalBytes / 2
        val buffer = ShortArray(FRAME_SAMPLES)
        val byteBuf = ByteArray(FRAME_SAMPLES * 2)

        var currentChunkSamples = mutableListOf<Short>()
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
                        currentChunkSamples = mutableListOf()
                    }
                    silentFrameCount = 0
                    currentChunkSamples.addAll(buffer.toList())
                } else if (isInSpeech) {
                    silentFrameCount++
                    currentChunkSamples.addAll(buffer.toList())

                    val chunkDurationMs = (currentChunkSamples.size * 1000L) / SAMPLE_RATE
                    if (silentFrameCount >= SILENCE_TOLERANCE_FRAMES || chunkDurationMs >= MAX_SPEECH_DURATION_MS) {
                        if (chunkDurationMs >= MIN_SPEECH_DURATION_MS) {
                            val endSample = chunkStartSample + currentChunkSamples.size
                            val startMs = (chunkStartSample * 1000L) / SAMPLE_RATE
                            val endMs = (endSample * 1000L) / SAMPLE_RATE
                            chunks.add(SpeechChunk(startMs, endMs, currentChunkSamples.toShortArray()))
                        }
                        isInSpeech = false
                        silentFrameCount = 0
                        currentChunkSamples = mutableListOf()
                    }
                }
                currentSampleIndex += FRAME_SAMPLES
            }
        }

        // Flush trailing chunk
        if (isInSpeech && currentChunkSamples.isNotEmpty()) {
            val chunkDurationMs = (currentChunkSamples.size * 1000L) / SAMPLE_RATE
            if (chunkDurationMs >= MIN_SPEECH_DURATION_MS) {
                val startMs = (chunkStartSample * 1000L) / SAMPLE_RATE
                val endMs = startMs + chunkDurationMs
                chunks.add(SpeechChunk(startMs, endMs, currentChunkSamples.toShortArray()))
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
