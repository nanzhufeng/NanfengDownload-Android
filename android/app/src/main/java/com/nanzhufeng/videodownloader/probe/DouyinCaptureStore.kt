package com.nanzhufeng.videodownloader.probe

import java.util.concurrent.atomic.AtomicReference
import java.net.URI

object DouyinCaptureStore {
    data class CapturedMedia(
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

    fun capture(pageUrl: String, requestUrl: String): CapturedMedia? {
        val expected = targetWorkId.get() ?: return null
        val pageWorkId = extractWorkId(pageUrl) ?: return null
        if (pageWorkId == expected && isMediaUrl(requestUrl)) {
            captured.compareAndSet(null, CapturedMedia(requestUrl, pageUrl))
        }
        return captured.get()
    }

    internal fun extractWorkId(url: String): String? =
        workIdPattern.find(url)?.groupValues?.get(1)

    internal fun isMediaUrl(url: String): Boolean {
        val lower = url.lowercase()
        val host = runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")
        val trustedMediaHost = host == "douyin.com" || host.endsWith(".douyin.com") ||
            host == "iesdouyin.com" || host.endsWith(".iesdouyin.com") ||
            host == "douyinvod.com" || host.endsWith(".douyinvod.com") ||
            host == "bytecdn.cn" || host.endsWith(".bytecdn.cn") ||
            host == "byteimg.com" || host.endsWith(".byteimg.com") ||
            host == "ibytedtos.com" || host.endsWith(".ibytedtos.com") ||
            host == "amemv.com" || host.endsWith(".amemv.com") ||
            host == "snssdk.com" || host.endsWith(".snssdk.com")
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
            trustedMediaHost &&
            mediaPath &&
            !image &&
            "douyin.com/video/" !in lower
    }
}
