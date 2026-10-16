/**
 * Role: Audio decoding and PCM resampling engine for video files.
 * Responsibility: Demuxes audio tracks via MediaExtractor and decodes frames to 16kHz mono PCM in 5-minute chunks.
 * Details: Converts multi-channel audio to mono and resamples to 16,000Hz with timestamp-windowed chunk extraction.
 */
package com.example.mxoffline.subtitles.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioExtractor {

    private const val TARGET_SAMPLE_RATE = 16000
    private const val TIMEOUT_US = 5000L
    const val CHUNK_DURATION_MS = 5 * 60 * 1000L // 5 minutes chunk window
    const val CHUNK_DURATION_US = CHUNK_DURATION_MS * 1000L

    fun getAudioDurationUs(context: Context, videoUri: Uri): Long {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, videoUri, null)
            val trackIndex = selectAudioTrack(extractor) ?: return 0L
            val format = extractor.getTrackFormat(trackIndex)
            if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
        } catch (e: Exception) {
            0L
        } finally {
            runCatching { extractor.release() }
        }
    }

    /**
     * Extracts and decodes audio for a specific time window [startUs] to [endUs] into 16kHz mono PCM.
     */
    fun extractChunkToPcm(
        context: Context,
        videoUri: Uri,
        startUs: Long,
        endUs: Long,
        outputPcmFile: File,
        onProgress: (Float) -> Unit,
        isCancelled: () -> Boolean
    ): Boolean {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null

        return try {
            extractor.setDataSource(context, videoUri, null)
            val trackIndex = selectAudioTrack(extractor) ?: return false
            extractor.selectTrack(trackIndex)

            if (startUs > 0) {
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            }

            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return false

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            var inputSampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
            var inputChannelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2

            BufferedOutputStream(FileOutputStream(outputPcmFile), 65536).use { outStream ->
                val bufferInfo = MediaCodec.BufferInfo()
                var isExtractorDone = false
                var isDecoderDone = false
                val chunkSpanUs = (endUs - startUs).coerceAtLeast(1L)

                while (!isDecoderDone && !isCancelled()) {
                    if (!isExtractorDone) {
                        val inputBufferId = codec.dequeueInputBuffer(TIMEOUT_US)
                        if (inputBufferId >= 0) {
                            val inputBuffer = codec.getInputBuffer(inputBufferId)
                            if (inputBuffer != null) {
                                inputBuffer.clear()
                                val sampleSize = extractor.readSampleData(inputBuffer, 0)
                                val sampleTime = extractor.sampleTime

                                if (sampleSize < 0 || (endUs < Long.MAX_VALUE && sampleTime >= endUs)) {
                                    codec.queueInputBuffer(inputBufferId, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                    isExtractorDone = true
                                } else {
                                    codec.queueInputBuffer(inputBufferId, 0, sampleSize, sampleTime, 0)
                                    extractor.advance()
                                    val frac = ((sampleTime - startUs).toFloat() / chunkSpanUs).coerceIn(0f, 1f)
                                    onProgress(frac)
                                }
                            }
                        }
                    }

                    // Drain all pending decoded output buffers
                    var outputBufferId = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                    while (outputBufferId >= 0 && !isCancelled()) {
                        val outputBuffer = codec.getOutputBuffer(outputBufferId)
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            if (bufferInfo.presentationTimeUs >= (startUs - 200_000L)) {
                                outputBuffer.position(bufferInfo.offset)
                                outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                                val resampled = resampleTo16kMono(
                                    outputBuffer,
                                    inputSampleRate,
                                    inputChannelCount
                                )
                                if (resampled.isNotEmpty()) {
                                    outStream.write(resampled)
                                }
                            }
                        }
                        codec.releaseOutputBuffer(outputBufferId, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            isDecoderDone = true
                            break
                        }
                        outputBufferId = codec.dequeueOutputBuffer(bufferInfo, 0L)
                    }

                    if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val newFormat = codec.outputFormat
                        inputSampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        inputChannelCount = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                }
            }
            !isCancelled() && outputPcmFile.exists() && outputPcmFile.length() > 0
        } catch (e: Exception) {
            outputPcmFile.delete()
            false
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    fun extractToPcm(
        context: Context,
        videoUri: Uri,
        outputPcmFile: File,
        onProgress: (Float) -> Unit,
        isCancelled: () -> Boolean
    ): Boolean = extractChunkToPcm(context, videoUri, 0L, Long.MAX_VALUE, outputPcmFile, onProgress, isCancelled)

    private fun selectAudioTrack(extractor: MediaExtractor): Int? {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/", ignoreCase = true)) return i
        }
        return null
    }

    private fun resampleTo16kMono(
        buffer: ByteBuffer,
        inputSampleRate: Int,
        channelCount: Int
    ): ByteArray {
        val shortBuffer = buffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val totalFrames = shortBuffer.remaining() / channelCount.coerceAtLeast(1)
        if (totalFrames <= 0) return ByteArray(0)

        // Downmix multi-channel to mono short array
        val monoSamples = ShortArray(totalFrames)
        for (f in 0 until totalFrames) {
            var sum = 0
            for (c in 0 until channelCount) {
                if (shortBuffer.hasRemaining()) sum += shortBuffer.get()
            }
            monoSamples[f] = (sum / channelCount.coerceAtLeast(1)).toShort()
        }

        if (inputSampleRate == TARGET_SAMPLE_RATE) {
            val bytes = ByteArray(totalFrames * 2)
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(monoSamples)
            return bytes
        }

        // Resample mono samples to 16kHz using linear interpolation
        val targetFrames = ((totalFrames.toLong() * TARGET_SAMPLE_RATE) / inputSampleRate).toInt()
        val resampledSamples = ShortArray(targetFrames)
        val ratio = inputSampleRate.toDouble() / TARGET_SAMPLE_RATE.toDouble()

        for (i in 0 until targetFrames) {
            val srcIndex = i * ratio
            val index0 = srcIndex.toInt()
            val index1 = (index0 + 1).coerceAtMost(totalFrames - 1)
            val frac = srcIndex - index0
            val sample = ((1.0 - frac) * monoSamples[index0] + frac * monoSamples[index1]).toInt()
            resampledSamples[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        val outBytes = ByteArray(targetFrames * 2)
        ByteBuffer.wrap(outBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(resampledSamples)
        return outBytes
    }
}
