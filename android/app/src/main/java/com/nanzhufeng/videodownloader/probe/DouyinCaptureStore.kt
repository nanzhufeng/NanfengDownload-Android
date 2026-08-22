package com.nanzhufeng.videodownloader.probe

import java.util.concurrent.atomic.AtomicReference
import java.net.URI

object DouyinCaptureStore {
    data class CapturedMedia(
        val mediaUrl: String,
        val pageUrl: String,
        val imageUrls: List<String> = emptyList(),
        val imageExpectedCount: Int = 0,
        val imageSourceVersion: Int = 0,
    )

    private val captured = AtomicReference<CapturedMedia?>(null)
    private val capturedImages = AtomicReference<List<String>>(emptyList())
    private val targetWorkId = AtomicReference<String?>(null)
    // 抖音会把实况图/动态图片作品放在 `/note/<id>`，但页面实际播放的
    // 仍是受签名保护的 MP4 流。`video` 与 `note` 必须使用同一作品 ID
    // 边界，避免把相邻推荐流误捕获为当前下载任务。
    private val workIdPattern = Regex("/(?:video|note)/(\\d+)")

    val latestMediaUrl: String?
        get() = captured.get()?.mediaUrl

    val latestPageUrl: String?
        get() = captured.get()?.pageUrl

    /**
     * Network interception may see a note image before the WebView exposes it
     * in the DOM.  Keep this private-session snapshot available to the probe,
     * but never treat it as a completed capture by itself: a note can still
     * declare a video element after its preview image has arrived.
     */
    internal fun capturedImageUrls(): List<String> = capturedImages.get()

    fun begin(sourceUrl: String) {
        captured.set(null)
        capturedImages.set(emptyList())
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

    fun captureImage(pageUrl: String, requestUrl: String): List<String> {
        val expected = targetWorkId.get() ?: return emptyList()
        val pageWorkId = extractWorkId(pageUrl) ?: return emptyList()
        if (pageWorkId != expected || !isImageUrl(requestUrl)) return emptyList()
        while (true) {
            val current = capturedImages.get()
            if (requestUrl in current) return current
            val updated = current + requestUrl
            if (capturedImages.compareAndSet(current, updated)) return updated
        }
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

    internal fun isImageUrl(url: String): Boolean {
        val lower = url.lowercase()
        val host = runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")
        val trustedImageHost = host == "douyinpic.com" || host.endsWith(".douyinpic.com") ||
            host == "byteimg.com" || host.endsWith(".byteimg.com") ||
            host == "ibytedtos.com" || host.endsWith(".ibytedtos.com") ||
            host == "amemv.com" || host.endsWith(".amemv.com")
        val imagePath = lower.contains("aweme_images") ||
            lower.contains("/image/") ||
            lower.contains("tos-cn-i") ||
            lower.contains(".jpg") || lower.contains(".jpeg") ||
            lower.contains(".png") || lower.contains(".webp")
        return lower.startsWith("https://") && trustedImageHost && imagePath && !isMediaUrl(url)
    }

    internal fun isVerifiedImageGallery(
        imageUrls: List<String>,
        expectedCount: Int,
        sourceVersion: Int,
    ): Boolean = sourceVersion == STRUCTURED_GALLERY_SOURCE_VERSION &&
        expectedCount > 0 &&
        imageUrls.size == expectedCount &&
        imageUrls.distinct().size == expectedCount &&
        imageUrls.all { url ->
            val lower = url.lowercase()
            isImageUrl(url) &&
                "tplv-dy-aweme-images" in lower &&
                "tplv-dy-water" !in lower
        }

    const val STRUCTURED_GALLERY_SOURCE_VERSION = 1
}
