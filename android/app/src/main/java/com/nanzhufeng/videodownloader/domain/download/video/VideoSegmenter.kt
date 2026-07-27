package com.nanzhufeng.videodownloader.domain.download.video

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun interface VideoSegmenter {
    suspend fun split(
        source: File,
        destinations: List<File>,
        cancelled: AtomicBoolean,
        onProgress: (Int) -> Unit,
    ): List<File>
}

class AndroidMp4VideoSegmenter : VideoSegmenter {
    override suspend fun split(
        source: File,
        destinations: List<File>,
        cancelled: AtomicBoolean,
        onProgress: (Int) -> Unit,
    ): List<File> = withContext(Dispatchers.IO) {
        require(source.isFile && source.length() > 0L) { "待分段视频不存在或为空" }
        require(destinations.size in 2..MAX_SEGMENTS) {
            "视频分段数量必须在 2 到 $MAX_SEGMENTS 之间"
        }
        val inspection = inspect(source)
        val ranges = VideoSegmentPlanner.plan(
            durationUs = inspection.durationUs,
            segmentCount = destinations.size,
            syncPointsUs = inspection.videoSyncPointsUs,
        )
        try {
            destinations.zip(ranges).forEachIndexed { index, (destination, range) ->
                ensureNotCancelled(cancelled)
                destination.parentFile?.mkdirs()
                destination.delete()
                writeSegment(
                    source = source,
                    destination = destination,
                    range = range,
                    orientationDegrees = inspection.orientationDegrees,
                    cancelled = cancelled,
                    onProgress = { withinSegment ->
                        val overall = (
                            (index.toDouble() + withinSegment.coerceIn(0.0, 1.0)) /
                                destinations.size.toDouble() *
                                100.0
                            ).toInt()
                        onProgress(overall.coerceIn(0, 99))
                    },
                )
                require(isPlayableVideo(destination)) {
                    "第 ${index + 1} 段视频校验失败，请减少分段数量后重试"
                }
            }
            onProgress(100)
            destinations
        } catch (error: Throwable) {
            destinations.forEach { file ->
                if (file.exists() && !file.delete()) file.deleteOnExit()
            }
            throw error
        }
    }

    private fun inspect(source: File): VideoInspection {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(source.absolutePath)
            val trackFormats = (0 until extractor.trackCount).map(extractor::getTrackFormat)
            val videoTrackIndex = trackFormats.indexOfFirst { format ->
                format.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            }
            require(videoTrackIndex >= 0) { "源文件没有可分段的视频轨道" }
            val durationUs = trackFormats
                .mapNotNull { format ->
                    if (format.containsKey(MediaFormat.KEY_DURATION)) {
                        format.getLong(MediaFormat.KEY_DURATION).takeIf { it > 0L }
                    } else {
                        null
                    }
                }
                .maxOrNull()
                ?: readDurationUs(source)
            extractor.selectTrack(videoTrackIndex)
            val syncPoints = buildList {
                while (extractor.sampleTime >= 0L) {
                    if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                        add(extractor.sampleTime)
                    }
                    if (!extractor.advance()) break
                }
            }
            VideoInspection(
                durationUs = durationUs,
                videoSyncPointsUs = syncPoints,
                orientationDegrees = readOrientation(source),
            )
        } finally {
            extractor.release()
        }
    }

    private fun writeSegment(
        source: File,
        destination: File,
        range: VideoSegmentRange,
        orientationDegrees: Int,
        cancelled: AtomicBoolean,
        onProgress: (Double) -> Unit,
    ) {
        val formatExtractor = MediaExtractor()
        val muxer = MediaMuxer(destination.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerStarted = false
        try {
            formatExtractor.setDataSource(source.absolutePath)
            val trackFormats = (0 until formatExtractor.trackCount).map(formatExtractor::getTrackFormat)
            val trackMap = trackFormats.map(muxer::addTrack)
            if (orientationDegrees in setOf(90, 180, 270)) {
                muxer.setOrientationHint(orientationDegrees)
            }
            muxer.start()
            muxerStarted = true
            trackFormats.indices.forEach { sourceTrackIndex ->
                ensureNotCancelled(cancelled)
                copyTrack(
                    source = source,
                    sourceTrackIndex = sourceTrackIndex,
                    destinationTrackIndex = trackMap[sourceTrackIndex],
                    muxer = muxer,
                    range = range,
                    cancelled = cancelled,
                    onProgress = { withinTrack ->
                        onProgress(
                            (sourceTrackIndex.toDouble() + withinTrack) /
                                trackFormats.size.toDouble(),
                        )
                    },
                )
            }
        } finally {
            formatExtractor.release()
            if (muxerStarted) {
                runCatching { muxer.stop() }
            }
            muxer.release()
        }
    }

    private fun copyTrack(
        source: File,
        sourceTrackIndex: Int,
        destinationTrackIndex: Int,
        muxer: MediaMuxer,
        range: VideoSegmentRange,
        cancelled: AtomicBoolean,
        onProgress: (Double) -> Unit,
    ) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(source.absolutePath)
            extractor.selectTrack(sourceTrackIndex)
            extractor.seekTo(range.startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val trackFormat = extractor.getTrackFormat(sourceTrackIndex)
            val requestedBufferBytes = if (trackFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                trackFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            } else {
                DEFAULT_BUFFER_BYTES
            }
            val buffer = ByteBuffer.allocateDirect(
                requestedBufferBytes.coerceIn(DEFAULT_BUFFER_BYTES, MAX_BUFFER_BYTES),
            )
            val info = MediaCodec.BufferInfo()
            var lastWrittenPresentationUs = -1L
            while (extractor.sampleTime >= 0L && extractor.sampleTime < range.endUs) {
                ensureNotCancelled(cancelled)
                val sampleTimeUs = extractor.sampleTime
                if (sampleTimeUs >= range.startUs) {
                    buffer.clear()
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) break
                    val presentationUs = (sampleTimeUs - range.startUs).coerceAtLeast(0L)
                    if (presentationUs >= lastWrittenPresentationUs) {
                        info.set(
                            0,
                            size,
                            presentationUs,
                            extractorFlagsToCodecFlags(extractor.sampleFlags),
                        )
                        muxer.writeSampleData(destinationTrackIndex, buffer, info)
                        lastWrittenPresentationUs = presentationUs
                    }
                }
                val rangeDuration = (range.endUs - range.startUs).coerceAtLeast(1L)
                onProgress(
                    ((sampleTimeUs - range.startUs).coerceAtLeast(0L).toDouble() /
                        rangeDuration.toDouble()).coerceIn(0.0, 1.0),
                )
                if (!extractor.advance()) break
            }
        } finally {
            extractor.release()
        }
    }

    private fun extractorFlagsToCodecFlags(sampleFlags: Int): Int {
        var codecFlags = 0
        if (sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
            codecFlags = codecFlags or MediaCodec.BUFFER_FLAG_KEY_FRAME
        }
        if (sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME != 0) {
            codecFlags = codecFlags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
        }
        return codecFlags
    }

    private fun isPlayableVideo(file: File): Boolean {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            val hasVideo = (0 until extractor.trackCount).any { index ->
                extractor.getTrackFormat(index)
                    .getString(MediaFormat.KEY_MIME)
                    ?.startsWith("video/") == true
            }
            hasVideo && file.length() >= MIN_OUTPUT_BYTES
        } catch (_: Throwable) {
            false
        } finally {
            extractor.release()
        }
    }

    private fun readDurationUs(file: File): Long = MediaMetadataRetriever().use { retriever ->
        retriever.setDataSource(file.absolutePath)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()
            ?.times(1_000L)
            ?.takeIf { it > 0L }
            ?: throw IOException("无法读取视频时长，不能安全分段")
    }

    private fun readOrientation(file: File): Int = runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull()
                ?: 0
        }
    }.getOrDefault(0)

    private fun ensureNotCancelled(cancelled: AtomicBoolean) {
        if (cancelled.get()) throw CancellationException("视频分段已取消")
    }

    private data class VideoInspection(
        val durationUs: Long,
        val videoSyncPointsUs: List<Long>,
        val orientationDegrees: Int,
    )

    private companion object {
        const val MAX_SEGMENTS = 20
        const val DEFAULT_BUFFER_BYTES = 4 * 1024 * 1024
        const val MAX_BUFFER_BYTES = 64 * 1024 * 1024
        const val MIN_OUTPUT_BYTES = 16 * 1024L
    }
}
