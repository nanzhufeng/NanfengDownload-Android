package com.nanzhufeng.videodownloader.domain.download

import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import com.nanzhufeng.videodownloader.core.model.MediaItem
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset

class OutputFilePolicy {
    fun relativePath(media: MediaItem, resolution: ResolutionPreset): String {
        val extension = if (resolution == ResolutionPreset.AUDIO_MP3) "mp3" else "mp4"
        val platform = when (media.platform) {
            DownloadPlatform.DOUYIN -> "抖音"
            DownloadPlatform.YOUTUBE -> "YouTube"
            DownloadPlatform.TIKTOK -> "TikTok"
        }
        val creator = sanitize(media.creator.ifBlank { "未知作者" })
        val title = sanitize(media.title.ifBlank { "未知标题" })
        val publishDate = normalizeDate(media.publishDate)
        val root = if (resolution == ResolutionPreset.AUDIO_MP3) "Music" else "Movies"
        return "$root/南烛枫视频下载器/$platform/$creator/$publishDate $title.$extension"
    }

    fun displayName(media: MediaItem, resolution: ResolutionPreset): String =
        relativePath(media, resolution).substringAfterLast('/')

    fun relativeDirectory(media: MediaItem): String {
        val path = relativePath(media, ResolutionPreset.UP_TO_720P)
        return path.substringBeforeLast('/')
    }

    private fun normalizeDate(value: String): String {
        val digits = value.filter(Char::isDigit)
        return if (digits.length >= 8) {
            "${digits.substring(0, 4)}-${digits.substring(4, 6)}-${digits.substring(6, 8)}"
        } else {
            "未知日期"
        }
    }

    private fun sanitize(value: String): String = value
        .trim()
        .replace(INVALID_PATH_CHARS, "_")
        .replace(WHITESPACE, " ")
        .trim(' ', '.')
        .ifBlank { "未命名" }
        .take(MAX_SEGMENT_LENGTH)

    private companion object {
        val INVALID_PATH_CHARS = Regex("[\\\\/:*?\"<>|]")
        val WHITESPACE = Regex("\\s+")
        const val MAX_SEGMENT_LENGTH = 120
    }
}
