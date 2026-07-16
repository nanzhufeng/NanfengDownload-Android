package com.nanzhufeng.videodownloader.probe

import java.util.concurrent.atomic.AtomicReference
import java.net.URI

object DouyinCaptureStore {
    private data class CapturedMedia(
        val mediaUrl: String,
        val pageUrl: String,
    )

    private val captured = AtomicReference<CapturedMedia?>(null)
    private val targetWorkId = AtomicReference<String?>(null)
    private val workIdPattern = Regex("/video/(\\d+)")

    val latestMediaUrl: String?
        get() = captured.get()?.mediaUrl

    val latestPageUrl: String?
        get() = captured.get()?.pageUrl

    fun begin(sourceUrl: String) {
        captured.set(null)
        targetWorkId.set(extractWorkId(sourceUrl))
    }

    fun updatePage(pageUrl: String) {
        val pageWorkId = extractWorkId(pageUrl) ?: return
        targetWorkId.compareAndSet(null, pageWorkId)
    }

    fun capture(pageUrl: String, requestUrl: String) {
        val expected = targetWorkId.get() ?: return
        val pageWorkId = extractWorkId(pageUrl) ?: return
        if (pageWorkId == expected && isMediaUrl(requestUrl)) {
            captured.compareAndSet(null, CapturedMedia(requestUrl, pageUrl))
        }
    }

    private fun extractWorkId(url: String): String? =
        workIdPattern.find(url)?.groupValues?.get(1)

    private fun isMediaUrl(url: String): Boolean {
        val lower = url.lowercase()
        val host = runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")
        val douyinVideoCdn = (host == "douyinvod.com" || host.endsWith(".douyinvod.com")) &&
            ("/tos-" in lower || "video_mp4" in lower)
        val mediaPath = "/video/tos/" in lower ||
            "/play/" in lower ||
            "/aweme/v1/play" in lower ||
            ".mp4" in lower ||
            douyinVideoCdn
        val image = lower.endsWith(".webp") ||
            lower.endsWith(".jpg") ||
            lower.endsWith(".jpeg") ||
            lower.endsWith(".png")
        return lower.startsWith("https://") &&
            mediaPath &&
            !image &&
            "douyin.com/video/" !in lower
    }
}
