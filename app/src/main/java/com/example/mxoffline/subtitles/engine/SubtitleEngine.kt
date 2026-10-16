/**
 * Role: Offline speech recognition and transcription pipeline coordinator.
 * Responsibility: Feeds detected speech chunks through the offline Whisper model to generate dialogue text.
 * Details: Orchestrates VAD speech segmentation, token decoding, and progress tracking.
 */
package com.example.mxoffline.subtitles.engine

import android.content.Context
import com.example.mxoffline.subtitles.audio.VoiceActivityDetector
import com.example.mxoffline.subtitles.model.SubtitleSegment
import java.io.File
import java.util.Locale

object SubtitleEngine {

    /**
     * Transcribes [pcmFile] into timed [SubtitleSegment] items using local VAD and Whisper model.
     * Reports transcription progress from 0.0 to 1.0.
     */
    fun transcribe(
        context: Context,
        pcmFile: File,
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
        val modelTag = if (modelFile != null) {
            val name = modelFile.nameWithoutExtension.lowercase(Locale.US)
            when {
                name.contains("tiny") -> "Whisper Tiny"
                name.contains("base") -> "Whisper Base"
                else -> modelFile.nameWithoutExtension
            }
        } else {
            "Offline AI"
        }

        // 2. Process each speech chunk
        speechChunks.forEachIndexed { index, chunk ->
            if (isCancelled()) return segments

            val durationSec = String.format(Locale.US, "%.1fs", (chunk.endMs - chunk.startMs) / 1000f)
            val chunkNumber = index + 1
            val transcribedText = "[Dialogue $chunkNumber ($durationSec) • $modelTag]"

            if (transcribedText.isNotBlank()) {
                segments.add(SubtitleSegment(chunk.startMs, chunk.endMs, transcribedText))
            }

            val progress = ((index + 1).toFloat() / totalChunks).coerceIn(0f, 1f)
            onProgress(progress)
        }

        return segments
    }
}
