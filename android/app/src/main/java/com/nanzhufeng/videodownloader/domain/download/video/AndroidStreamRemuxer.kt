package com.nanzhufeng.videodownloader.domain.download.video

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.InAppMp4Muxer
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

/** Recontainers TS/fMP4 samples as a standalone MP4; never publishes raw fragments as .mp4. */
object AndroidStreamRemuxer {
    fun remux(source: File, target: File, cancelled: AtomicBoolean): File {
        val partial = File(target.parentFile, target.name + ".mux.part")
        val extractor = MediaExtractor()
        var muxer: SampleMp4Muxer? = null
        var started = false
        try {
            extractor.setDataSource(source.absolutePath)
            val output = SampleMp4Muxer(partial.absolutePath)
            muxer = output
            val tracks = mutableMapOf<Int, Int>()
            val trackMimes = mutableMapOf<Int, String>()
            val trackSampleRates = mutableMapOf<Int, Int>()
            val trackEnds = mutableMapOf<Int, TrackEndTime>()
            var capacity = 4 * 1024 * 1024
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (!mime.startsWith("video/") && !mime.startsWith("audio/")) continue
                tracks[index] = output.addTrack(format)
                trackMimes[index] = mime
                if (mime == "audio/mp4a-latm" && format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                    trackSampleRates[index] = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                }
                val frameRate = if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) format.getInteger(MediaFormat.KEY_FRAME_RATE) else 0
                val fallbackStep = if (trackSampleRates[index] != null) 1_024_000_000L / requireNotNull(trackSampleRates[index])
                    else if (frameRate > 0) 1_000_000L / frameRate else 33_333L
                trackEnds[index] = TrackEndTime(fallbackStep)
                extractor.selectTrack(index)
                if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    capacity = maxOf(capacity, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
                }
            }
            require(tracks.isNotEmpty()) { "分段文件没有可封装的音视频轨道" }
            require(capacity <= 32 * 1024 * 1024) { "媒体轨道要求的缓冲区过大，无法安全封装" }
            val audioLeads = trackSampleRates.mapValues { (track, sampleRate) ->
                probeInitialAacLead(source, track, sampleRate, capacity, cancelled)
            }
            val commonShiftUs = audioLeads.values.maxOrNull() ?: 0L
            val audioClocks = trackSampleRates.mapValues { (track, sampleRate) ->
                AacSampleTimeline(sampleRate, audioLeads[track] ?: 0L)
            }
            output.start()
            started = true
            var buffer = ByteBuffer.allocateDirect(capacity)
            val info = MediaCodec.BufferInfo()
            val written = mutableSetOf<Int>()
            val lastWrittenTimeUs = mutableMapOf<Int, Long>()
            val timeOriginUs = extractor.sampleTime.coerceAtLeast(0L)
            while (extractor.sampleTrackIndex >= 0) {
                if (cancelled.get() || Thread.currentThread().isInterrupted) throw CancellationException("封装已取消")
                val sampleSize = extractor.sampleSize
                require(sampleSize in 0..(32L * 1024 * 1024)) { "媒体帧过大，无法安全封装" }
                if (sampleSize > buffer.capacity()) buffer = ByteBuffer.allocateDirect(sampleSize.toInt())
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                val track = extractor.sampleTrackIndex
                require(extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED == 0) {
                    "媒体轨道受加密保护，不能保存为普通 MP4"
                }
                val layouts = AacAdtsSampleLayout.forSample(requireNotNull(trackMimes[track]), buffer, size)
                val sampleRate = trackSampleRates[track] ?: 0
                require(layouts.size == 1 || sampleRate > 0) { "多帧 AAC 缺少采样率，不能安全封装" }
                val sampleTimeUs = (extractor.sampleTime - timeOriginUs).coerceAtLeast(0L)
                val frameTimes = audioClocks[track]?.times(
                    sampleTimeUs + commonShiftUs - (audioLeads[track] ?: 0L), layouts.size,
                ) ?: listOf(sampleTimeUs + commonShiftUs)
                val sampleFlags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                    MediaCodec.BUFFER_FLAG_KEY_FRAME
                } else 0
                layouts.forEachIndexed { frameIndex, layout ->
                    info.set(layout.offset, layout.size, frameTimes[frameIndex], sampleFlags)
                    try {
                        output.writeSampleData(requireNotNull(tracks[track]), buffer, info)
                    } catch (error: Exception) {
                        throw IllegalStateException(
                            "媒体封装失败：轨道=$track，类型=${trackMimes[track]}，采样率=$sampleRate，" +
                                "原始时间=${extractor.sampleTime}，起点=$timeOriginUs，" +
                                "帧=$frameIndex/${layouts.size}，时间=${info.presentationTimeUs}，" +
                                "前帧=${lastWrittenTimeUs[track]}；${error.message}", error,
                        )
                    }
                    requireNotNull(trackEnds[track]).record(info.presentationTimeUs)
                    lastWrittenTimeUs[track] = info.presentationTimeUs
                }
                written.add(track)
                extractor.advance()
            }
            require(written == tracks.keys) { "分段文件轨道不完整，未发布" }
            // Explicit EOS duration prevents the native MP4 writer from using
            // a tiny/negative decode-order delta as the final B-frame duration.
            tracks.forEach { (inputTrack, outputTrack) ->
                info.set(0, 0, requireNotNull(trackEnds[inputTrack]).endUs(), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                output.writeSampleData(outputTrack, buffer, info)
            }
            output.stop()
            started = false
            output.release()
            muxer = null
            require(partial.isFile && partial.length() > 0) { "媒体封装结果为空" }
            if (cancelled.get()) throw CancellationException("封装已取消")
            check(!target.exists() || target.delete()) { "无法替换本任务的媒体缓存" }
            check(partial.renameTo(target)) { "无法保存封装后的媒体文件" }
            return target
        } finally {
            if (started) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            extractor.release()
            partial.delete()
        }
    }

    private fun probeInitialAacLead(
        source: File, track: Int, sampleRate: Int, capacity: Int, cancelled: AtomicBoolean,
    ): Long {
        val probe = MediaExtractor()
        try {
            if (cancelled.get()) throw CancellationException("封装已取消")
            probe.setDataSource(source.absolutePath)
            probe.selectTrack(track)
            val firstTimeUs = probe.sampleTime
            if (firstTimeUs != 0L) return 0L
            var frames = 0
            var buffer = ByteBuffer.allocateDirect(capacity)
            // Stagefright may clamp several separate negative-PTS access units to zero.
            // Count the whole zero prefix and anchor it to the first positive timestamp.
            repeat(256) {
                if (cancelled.get() || Thread.currentThread().isInterrupted) throw CancellationException("封装已取消")
                val timestamp = probe.sampleTime
                if (timestamp > 0L) return initialAacLeadUs(firstTimeUs, timestamp, frames, sampleRate)
                if (timestamp != 0L || frames * 1_024_000_000L / sampleRate > 1_000_000L) return 0L
                val sampleSize = probe.sampleSize
                require(sampleSize in 0..(32L * 1024 * 1024)) { "媒体帧过大，无法安全封装" }
                if (sampleSize > buffer.capacity()) buffer = ByteBuffer.allocateDirect(sampleSize.toInt())
                buffer.clear()
                val size = probe.readSampleData(buffer, 0)
                if (size < 0) return 0L
                frames += AacAdtsSampleLayout.forSample("audio/mp4a-latm", buffer, size).size
                if (!probe.advance()) return 0L
            }
            return 0L
        } finally {
            probe.release()
        }
    }
}


/** Reuses the same compressed-sample MP4 writer as independent-track downloads. */
@androidx.annotation.OptIn(UnstableApi::class)
private class SampleMp4Muxer(path: String) {
    private val muxer = InAppMp4Muxer.Factory().create(path)
    private val timescales = mutableMapOf<Int, Int>()
    private var closed = false

    fun addTrack(source: MediaFormat): Int {
        val mime = requireNotNull(source.getString(MediaFormat.KEY_MIME))
        val format = Format.Builder().setSampleMimeType(mime)
        val initialization = mutableListOf<ByteArray>()
        for (index in 0..15) {
            val key = "csd-$index"
            if (!source.containsKey(key)) break
            val data = requireNotNull(source.getByteBuffer(key)).duplicate()
            require(data.remaining() <= 1024 * 1024) { "媒体配置过大，未发布" }
            initialization.add(ByteArray(data.remaining()).also(data::get))
        }
        format.setInitializationData(initialization)
        if (mime.startsWith("video/")) {
            if (source.containsKey(MediaFormat.KEY_WIDTH)) format.setWidth(source.getInteger(MediaFormat.KEY_WIDTH))
            if (source.containsKey(MediaFormat.KEY_HEIGHT)) format.setHeight(source.getInteger(MediaFormat.KEY_HEIGHT))
            if (source.containsKey(MediaFormat.KEY_FRAME_RATE)) format.setFrameRate(source.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat())
        } else {
            if (source.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.setChannelCount(source.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
            if (source.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.setSampleRate(source.getInteger(MediaFormat.KEY_SAMPLE_RATE))
        }
        return muxer.addTrack(format.build()).also { timescales[it] = if (mime.startsWith("audio/")) 48_000 else 90_000 }
    }

    fun start() = Unit

    fun writeSampleData(track: Int, buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        val adjusted = MediaCodec.BufferInfo().apply {
            set(0, info.size, muxTimestampUs(info.presentationTimeUs, timescales.getValue(track)), info.flags)
        }
        val sample = compressedSampleBuffer(buffer, info.offset, info.size)
        muxer.writeSampleData(track, sample, adjusted)
    }

    fun stop() { if (!closed) { muxer.close(); closed = true } }
    fun release() = stop()
}
