/**
 * Role: Data models for offline subtitle extraction and background job queueing.
 * Responsibility: Represents audio transcription segments and batch processing lifecycle states.
 * Details: Encapsulates timestamps, transcribed dialogue lines, progress metrics, and status enums.
 */
package com.example.mxoffline.subtitles.model

import com.example.mxoffline.model.VideoItem

data class SubtitleSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

enum class SubtitleJobStatus {
    QUEUED,
    EXTRACTING_AUDIO,
    TRANSCRIBING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class SubtitleJobItem(
    val video: VideoItem,
    var status: SubtitleJobStatus = SubtitleJobStatus.QUEUED,
    var progress: Int = 0,
    var error: String? = null,
    var outputPath: String? = null
)

data class SubtitleBatchProgress(
    val folderName: String,
    val totalVideos: Int,
    val completedVideos: Int,
    val currentIndex: Int,
    val currentVideoName: String,
    val currentProgress: Int,
    val isPaused: Boolean = false,
    val isFinished: Boolean = false
)
