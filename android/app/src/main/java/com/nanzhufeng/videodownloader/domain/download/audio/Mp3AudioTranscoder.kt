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
    ): File {
        require(source.isFile) { "Audio source does not exist: ${source.absolutePath}" }
        if (destination.exists()) {
            throw IOException("MP3 输出文件已存在，拒绝覆盖：${destination.absolutePath}")
        }
        destination.parentFile?.mkdirs()
        val partial = File(destination.parentFile, destination.name + ".transcoding.part")
        partial.delete()
        var session: Mp3Encoder.Session? = null

        try {
            decoder.decode(
                source = source,
                cancelled = cancelled,
                onFormat = { format ->
                    check(session == null) { "PCM format was reported more than once" }
                    session = encoder.open(partial, format)
                },
                onPcm = { samples, frames ->
                    if (cancelled.get()) throw CancellationException("音频转码已取消")
                    val activeSession = session
                        ?: throw IOException("收到 PCM 数据前没有解码格式")
                    activeSession.encode(samples, frames)
                },
            )
            if (cancelled.get()) throw CancellationException("音频转码已取消")
            val activeSession = session ?: throw IOException("音频解码器没有输出 PCM 数据")
            activeSession.finish()
            activeSession.close()
            session = null

            if (!Mp3FileValidator.isValid(partial)) {
                throw IOException("LAME 输出未通过 MP3 内容校验")
            }
            if (!partial.renameTo(destination)) {
                throw IOException("无法原子发布 MP3 临时文件")
            }
            return destination
        } catch (error: Throwable) {
            session?.close()
            partial.delete()
            throw error
        }
    }
}
