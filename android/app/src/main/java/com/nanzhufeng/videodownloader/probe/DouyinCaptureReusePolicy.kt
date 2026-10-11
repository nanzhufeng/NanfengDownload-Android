package com.nanzhufeng.videodownloader.probe

import java.net.URI

/** Short-lived reuse of a target-page result; transfer validation still happens at download. */
object DouyinCaptureReusePolicy {
    fun canReuse(media: DouyinCapturedMedia, sourceUrl: String, nowMillis: Long, sessionFingerprint: String = ""): Boolean {
        if (media.capturedAtMillis <= 0 || nowMillis < media.capturedAtMillis ||
            nowMillis - media.capturedAtMillis > 120_000L
        ) return false
        if (!DouyinCaptureContext.isSafeUserAgent(media.captureUserAgent) ||
            media.sessionFingerprint.isBlank() || media.sessionFingerprint != sessionFingerprint
        ) return false
        if (!isOfficialWorkUrl(sourceUrl) || !isOfficialWorkUrl(media.pageUrl)) return false
        if (DouyinCaptureStore.extractWorkId(sourceUrl) != media.workId ||
            DouyinCaptureStore.extractWorkId(media.pageUrl) != media.workId
        ) return false
        val sourceIsNote = URI(sourceUrl).path.contains("/note/")
        if (sourceIsNote != URI(media.pageUrl).path.contains("/note/")) return false
        if (sourceIsNote) return media.mediaUrl.isBlank() && DouyinCaptureStore.isVerifiedImageGallery(
            media.imageUrls, media.imageExpectedCount, media.imageSourceVersion,
        ) && media.imageUrls.all { SignedMediaUrlExpiry.isFresh(it, nowMillis) }
        return DouyinCaptureStore.isMediaUrl(media.mediaUrl) && SignedMediaUrlExpiry.isFresh(media.mediaUrl, nowMillis)
    }

    private fun isOfficialWorkUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val host = uri.host.orEmpty().lowercase()
        return uri.scheme.equals("https", true) && uri.userInfo == null &&
            uri.port in setOf(-1, 443) &&
            (host == "douyin.com" || host.endsWith(".douyin.com") ||
                host == "iesdouyin.com" || host.endsWith(".iesdouyin.com")) &&
            Regex("^/(?:share/)?(?:video|note)/\\d+/?$").matches(uri.path.orEmpty())
    }
}
