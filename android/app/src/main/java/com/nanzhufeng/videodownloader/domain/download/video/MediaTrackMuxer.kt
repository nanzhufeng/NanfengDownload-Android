package com.nanzhufeng.videodownloader.domain.download.video

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun interface MediaTrackMuxer {
    suspend fun merge(
        video: File,
        audio: File,
        output: File,
        cancelled: AtomicBoolean,
        onProgress: (Int) -> Unit,
    ): File
}

/**
 * Copies already-compressed video and audio samples directly into one MP4 container.
 *
 * No decoder, encoder or Transformer pipeline is involved. Samples from both inputs
 * are interleaved by presentation timestamp so large videos remain a streaming,
 * constant-memory operation.
 */
class AndroidMp4TrackMuxer : MediaTrackMuxer {
    override suspend fun merge(
        video: File,
        audio: File,
        output: File,
        cancelled: AtomicBoolean,
        onProgress: (Int) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        require(video.isFile && video.length() > 0L) { "待合并的视频流不存在或为空" }
        require(audio.isFile && audio.length() > 0L) { "待合并的音频流不存在或为空" }
        output.parentFile?.mkdirs()
        output.delete()

        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        try {
            videoExtractor.setDataSource(video.absolutePath)
            audioExtractor.setDataSource(audio.absolutePath)
            val videoTrack = findTrack(videoExtractor, "video/")
            val audioTrack = findTrack(audioExtractor, "audio/")
            val videoFormat = videoExtractor.getTrackFormat(videoTrack)
            val audioFormat = audioExtractor.getTrackFormat(audioTrack)
            requireMp4Compatible(videoFormat, "视频")
            requireMp4Compatible(audioFormat, "音频")

            videoExtractor.selectTrack(videoTrack)
            audioExtractor.selectTrack(audioTrack)
            videoExtractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            audioExtractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            val outputMuxer = MediaMuxer(
                output.absolutePath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
            )
            muxer = outputMuxer
            val outputVideoTrack = outputMuxer.addTrack(videoFormat)
            val outputAudioTrack = outputMuxer.addTrack(audioFormat)
            readOrientation(video).takeIf { it in setOf(90, 180, 270) }
                ?.let(outputMuxer::setOrientationHint)
            outputMuxer.start()
            muxerStarted = true

            val buffer = ByteBuffer.allocateDirect(
                maxOf(
                    recommendedBufferBytes(videoFormat),
                    recommendedBufferBytes(audioFormat),
                ),
            )
            val info = MediaCodec.BufferInfo()
            val videoDurationUs = durationUs(videoFormat)
            val audioDurationUs = durationUs(audioFormat)
            var videoTimeUs = videoExtractor.sampleTime
            var audioTimeUs = audioExtractor.sampleTime
            require(videoTimeUs >= 0L) { "视频流没有可合并的媒体帧" }
            require(audioTimeUs >= 0L) { "音频流没有可合并的媒体帧" }
            var lastProgress = -1

            while (videoTimeUs >= 0L || audioTimeUs >= 0L) {
                ensureNotCancelled(cancelled)
                val copyVideo = videoTimeUs >= 0L &&
                    (audioTimeUs < 0L || videoTimeUs <= audioTimeUs)
                if (copyVideo) {
                    copyCurrentSample(
                        extractor = videoExtractor,
                        destinationTrack = outputVideoTrack,
                        muxer = outputMuxer,
                        buffer = buffer,
                        info = info,
                    )
                    videoTimeUs = videoExtractor.sampleTime
                } else {
                    copyCurrentSample(
                        extractor = audioExtractor,
                        destinationTrack = outputAudioTrack,
                        muxer = outputMuxer,
                        buffer = buffer,
                        info = info,
                    )
                    audioTimeUs = audioExtractor.sampleTime
                }
                val progress = mergeProgress(
                    videoTimeUs = videoTimeUs,
                    videoDurationUs = videoDurationUs,
                    audioTimeUs = audioTimeUs,
                    audioDurationUs = audioDurationUs,
                )
                if (progress != lastProgress) {
                    onProgress(progress)
                    lastProgress = progress
                }
            }

            outputMuxer.stop()
            muxerStarted = false
            outputMuxer.release()
            muxer = null
            require(output.isFile && output.length() > 0L) { "音视频合并没有生成有效文件" }
            onProgress(100)
            output
        } catch (error: Throwable) {
            if (muxerStarted) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            muxer = null
            if (output.exists() && !output.delete()) output.deleteOnExit()
            throw error
        } finally {
            videoExtractor.release()
            audioExtractor.release()
            runCatching { muxer?.release() }
        }
    }

    private fun findTrack(extractor: MediaExtractor, prefix: String): Int =
        (0 until extractor.trackCount).firstOrNull { index ->
            extractor.getTrackFormat(index)
                .getString(MediaFormat.KEY_MIME)
                ?.startsWith(prefix) == true
        } ?: error("媒体文件缺少${if (prefix == "video/") "视频" else "音频"}轨道")

    private fun requireMp4Compatible(format: MediaFormat, label: String) {
        val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
        val supported = when (label) {
            "视频" -> mime in SUPPORTED_VIDEO_MIMES
            else -> mime in SUPPORTED_AUDIO_MIMES
        }
        require(supported) { "$label 格式 $mime 无法直接封装为 MP4，请重新选择兼容格式" }
    }

    private fun copyCurrentSample(
        extractor: MediaExtractor,
        destinationTrack: Int,
        muxer: MediaMuxer,
        buffer: ByteBuffer,
        info: MediaCodec.BufferInfo,
    ) {
        buffer.clear()
        val size = extractor.readSampleData(buffer, 0)
        if (size < 0) return
        info.set(
            0,
            size,
            extractor.sampleTime.coerceAtLeast(0L),
            extractorFlagsToCodecFlags(extractor.sampleFlags),
        )
        muxer.writeSampleData(destinationTrack, buffer, info)
        extractor.advance()
    }

    private fun recommendedBufferBytes(format: MediaFormat): Int {
        val requested = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
        } else {
            DEFAULT_BUFFER_BYTES
        }
        return requested.coerceIn(DEFAULT_BUFFER_BYTES, MAX_BUFFER_BYTES)
    }

    private fun durationUs(format: MediaFormat): Long =
        if (format.containsKey(MediaFormat.KEY_DURATION)) {
            format.getLong(MediaFormat.KEY_DURATION).coerceAtLeast(1L)
        } else {
            1L
        }

    private fun mergeProgress(
        videoTimeUs: Long,
        videoDurationUs: Long,
        audioTimeUs: Long,
        audioDurationUs: Long,
    ): Int {
        fun fraction(timeUs: Long, durationUs: Long): Double =
            if (timeUs < 0L) 1.0 else timeUs.toDouble().div(durationUs).coerceIn(0.0, 1.0)
        return (
            (fraction(videoTimeUs, videoDurationUs) + fraction(audioTimeUs, audioDurationUs)) /
                2.0 *
                100.0
            ).toInt().coerceIn(0, 99)
    }

    private fun readOrientation(file: File): Int = runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull()
                ?: 0
        }
    }.getOrDefault(0)

    private fun extractorFlagsToCodecFlags(sampleFlags: Int): Int {
        var flags = 0
        if (sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
            flags = flags or MediaCodec.BUFFER_FLAG_KEY_FRAME
        }
        if (sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME != 0) {
            flags = flags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
        }
        return flags
    }

    private fun ensureNotCancelled(cancelled: AtomicBoolean) {
        if (cancelled.get()) throw CancellationException("音视频合并已取消")
    }

    private companion object {
        const val DEFAULT_BUFFER_BYTES = 4 * 1024 * 1024
        const val MAX_BUFFER_BYTES = 64 * 1024 * 1024
        val SUPPORTED_VIDEO_MIMES = setOf(
            "video/avc",
            "video/hevc",
            "video/mp4v-es",
            "video/3gpp",
            "video/av01",
        )
        val SUPPORTED_AUDIO_MIMES = setOf(
            "audio/mp4a-latm",
            "audio/mpeg",
            "audio/3gpp",
            "audio/amr-wb",
        )
    }
}
