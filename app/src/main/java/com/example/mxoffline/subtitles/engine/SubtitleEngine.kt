/**
 * Role: Offline speech recognition and transcription pipeline coordinator.
 * Responsibility: Feeds detected speech chunks through the offline Whisper model to generate dialogue text.
 * Details: Orchestrates VAD speech segmentation, token decoding, and progress tracking with chunk time offsets.
 */
package com.example.mxoffline.subtitles.engine

import android.content.Context
import com.example.mxoffline.subtitles.audio.VoiceActivityDetector
import com.example.mxoffline.subtitles.model.SubtitleSegment
import com.whispercpp.whisper.WhisperContext
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Locale

object SubtitleEngine {

    /**
     * Transcribes [pcmFile] into timed [SubtitleSegment] items using local VAD and Whisper model.
     * [timeOffsetMs] is added to segment timestamps for synchronized chunk stitching.
     * Reports transcription progress from 0.0 to 1.0.
     */
    fun transcribe(
        context: Context,
        pcmFile: File,
        timeOffsetMs: Long = 0L,
        onProgress: (Float) -> Unit,
        isCancelled: () -> Boolean
    ): List<SubtitleSegment> {
        val segments = mutableListOf<SubtitleSegment>()
        if (isCancelled() || !pcmFile.exists()) return segments

        // 1. Detect speech chunks using VAD (Voice Activity Detection)
        val speechChunks = VoiceActivityDetector.detectSpeechChunks(pcmFile, isCancelled)
        if (speechChunks.isEmpty() || isCancelled()) return segments

        val totalChunks = speechChunks.size
        val modelFile = SubtitleModelManager.getActiveModel(context)

        // Initialize Whisper native context
        var whisperContext: WhisperContext? = null
        if (modelFile != null && modelFile.exists() && modelFile.length() > 5_000_000L) {
            runCatching {
                whisperContext = WhisperContext.createContextFromFile(modelFile.absolutePath)
            }
        }

        try {
            // 2. Process each speech chunk through Whisper neural network
            speechChunks.forEachIndexed { index, chunk ->
                if (isCancelled()) return segments

                var transcribedText = ""
                val ctx = whisperContext
                if (ctx != null && chunk.pcmData.isNotEmpty()) {
                    runCatching {
                        val floatSamples = FloatArray(chunk.pcmData.size) { i ->
                            (chunk.pcmData[i] / 32768.0f).coerceIn(-1.0f, 1.0f)
                        }
                        runBlocking {
                            transcribedText = ctx.transcribeData(floatSamples, printTimestamp = false).trim()
                        }
                    }
                }

                if (transcribedText.isBlank()) {
                    val durationSec = String.format(Locale.US, "%.1fs", (chunk.endMs - chunk.startMs) / 1000f)
                    val chunkNumber = index + 1
                    transcribedText = "[Dialogue $chunkNumber ($durationSec)]"
                }

                val globalStartMs = timeOffsetMs + chunk.startMs
                val globalEndMs = timeOffsetMs + chunk.endMs

                segments.add(SubtitleSegment(globalStartMs, globalEndMs, transcribedText))

                val progress = ((index + 1).toFloat() / totalChunks).coerceIn(0f, 1f)
                onProgress(progress)
            }
        } finally {
            whisperContext?.let { ctx ->
                runCatching {
                    runBlocking { ctx.release() }
                }
            }
        }

        return segments
    }
}
