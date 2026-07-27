package com.nanzhufeng.videodownloader.domain.download.audio

import java.io.File
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

class Mp3AudioTranscoder(
    private val decoder: AndroidPcmDecoder = AndroidPcmDecoder(),
    private val encoder: Mp3Encoder = LameMp3Encoder(),
) : AudioTranscoder {
    override fun transcode(
        source: File,
        destination: File,
        cancelled: AtomicBoolean,
        onProgress: (progressPercent: Int) -> Unit,
    ): File = transcodeSegments(
        source = source,
        destinations = listOf(destination),
        cancelled = cancelled,
        onProgress = onProgress,
    ).single()

    override fun transcodeSegments(
        source: File,
        destinations: List<File>,
        cancelled: AtomicBoolean,
        onProgress: (progressPercent: Int) -> Unit,
    ): List<File> {
        require(source.isFile) { "Audio source does not exist: ${source.absolutePath}" }
        require(destinations.size in 1..MAX_SEGMENTS) {
            "音频分段数量必须在 1 到 $MAX_SEGMENTS 之间"
        }
        require(destinations.distinctBy(File::getAbsolutePath).size == destinations.size) {
            "音频分段输出路径不能重复"
        }
        destinations.forEach { destination ->
            if (destination.exists()) {
                throw IOException("MP3 输出文件已存在，拒绝覆盖：${destination.absolutePath}")
            }
            destination.parentFile?.mkdirs()
        }

        val durationUs = AudioSourceFileValidator.inspect(source).durationUs.orZero()
        if (destinations.size > 1 && durationUs <= 0L) {
            throw IOException("无法读取音频总时长，不能安全地按指定数量分段")
        }
        val partials = destinations.map { destination ->
            File(destination.parentFile, destination.name + ".transcoding.part").also(File::delete)
        }
        var activeSession: Mp3Encoder.Session? = null
        var activeIndex = -1
        var pcmFormat: PcmFormat? = null
        val published = mutableListOf<File>()

        fun finishActiveSession() {
            val session = activeSession ?: return
            session.finish()
            session.close()
            activeSession = null
        }

        try {
            decoder.decode(
                source = source,
                cancelled = cancelled,
                onFormat = { format ->
                    check(pcmFormat == null) { "PCM format was reported more than once" }
                    pcmFormat = format
                },
                onPcm = { samples, frames, presentationTimeUs ->
                    if (cancelled.get()) throw CancellationException("音频转码已取消")
                    val format = pcmFormat
                        ?: throw IOException("收到 PCM 数据前没有解码格式")
                    val segmentIndex = if (destinations.size == 1) {
                        0
                    } else {
                        ((presentationTimeUs.coerceAtMost(durationUs - 1L) * destinations.size) / durationUs)
                            .toInt()
                            .coerceIn(0, destinations.lastIndex)
                    }
                    if (segmentIndex != activeIndex) {
                        finishActiveSession()
                        if (segmentIndex != activeIndex + 1) {
                            throw IOException("音频时间轴跳过了第 ${activeIndex + 2} 段")
                        }
                        activeIndex = segmentIndex
                        activeSession = encoder.open(partials[segmentIndex], format)
                    }
                    activeSession?.encode(samples, frames)
                        ?: throw IOException("MP3 编码器没有正确初始化")
                },
                onProgress = { processedUs, totalUs ->
                    if (totalUs > 0L) {
                        onProgress(
                            ((processedUs.toDouble() / totalUs) * 100.0)
                                .toInt()
                                .coerceIn(0, 99),
                        )
                    }
                },
            )
            if (cancelled.get()) throw CancellationException("音频转码已取消")
            finishActiveSession()
            if (activeIndex != destinations.lastIndex) {
                throw IOException("音频内容不足，无法生成 ${destinations.size} 个有效分段")
            }

            partials.forEach { partial ->
                val validation = Mp3FileValidator.inspect(partial)
                if (!validation.valid) throw Mp3ValidationException(validation)
            }
            partials.zip(destinations).forEach { (partial, destination) ->
                if (!partial.renameTo(destination)) {
                    throw IOException("无法原子发布 MP3 分段文件：${destination.name}")
                }
                published += destination
            }
            onProgress(100)
            return destinations
        } catch (error: Throwable) {
            runCatching { activeSession?.close() }
            partials.forEach(File::delete)
            published.forEach(File::delete)
            throw error
        }
    }

    private fun Long?.orZero(): Long = this ?: 0L

    private companion object {
        const val MAX_SEGMENTS = 20
    }
}
