/**
 * Role: Audio decoding and PCM resampling engine for video files.
 * Responsibility: Demuxes audio tracks via MediaExtractor and decodes frames to 16kHz mono PCM.
 * Details: Converts multi-channel audio to mono and resamples to 16,000Hz for speech recognition models.
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
    private const val TIMEOUT_US = 10000L

    /**
     * Extracts and decodes the audio track from [videoUri] into 16kHz 16-bit signed mono PCM.
     * Writes into [outputPcmFile] and reports extraction progress via [onProgress].
     */
    fun extractToPcm(
        context: Context,
        videoUri: Uri,
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

            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return false
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 1L

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            var inputSampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
            var inputChannelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2

            BufferedOutputStream(FileOutputStream(outputPcmFile)).use { outStream ->
                val bufferInfo = MediaCodec.BufferInfo()
                var isExtractorDone = false
                var isDecoderDone = false

                while (!isDecoderDone && !isCancelled()) {
                    if (!isExtractorDone) {
                        val inputBufferId = codec.dequeueInputBuffer(TIMEOUT_US)
                        if (inputBufferId >= 0) {
                            val inputBuffer = codec.getInputBuffer(inputBufferId) ?: continue
                            inputBuffer.clear()
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inputBufferId, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                isExtractorDone = true
                            } else {
                                val sampleTime = extractor.sampleTime
                                codec.queueInputBuffer(inputBufferId, 0, sampleSize, sampleTime, 0)
                                extractor.advance()
                                if (durationUs > 0) onProgress((sampleTime.toFloat() / durationUs).coerceIn(0f, 0.5f))
                            }
                        }
                    }

                    val outputBufferId = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                    if (outputBufferId >= 0) {
                        val outputBuffer = codec.getOutputBuffer(outputBufferId)
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                            val resampled = resampleTo16kMono(
                                outputBuffer,
                                inputSampleRate,
                                inputChannelCount
                            )
                            outStream.write(resampled)
                        }
                        codec.releaseOutputBuffer(outputBufferId, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            isDecoderDone = true
                        }
                    } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
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
