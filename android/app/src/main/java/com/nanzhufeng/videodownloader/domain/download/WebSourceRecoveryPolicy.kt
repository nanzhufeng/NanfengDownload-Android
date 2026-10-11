package com.nanzhufeng.videodownloader.domain.download

import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import com.nanzhufeng.videodownloader.core.model.DownloadTaskStatus
import com.nanzhufeng.videodownloader.core.model.MediaItem
import com.nanzhufeng.videodownloader.core.model.QueuedDownload
import com.nanzhufeng.videodownloader.probe.HttpDownloadException
import java.io.IOException

/** A confirmed WebView source is temporary; retrying its identical rejected URL cannot refresh it. */
object WebSourceRecoveryPolicy {
    private val expiredStatuses = setOf(401, 403, 404, 410, 416)
    private val storedHttpFailure = Regex("\\bHTTP\\s*(401|403|404|410|416)\\b", RegexOption.IGNORE_CASE)

    fun mustRecapture(media: MediaItem, error: Throwable): Boolean =
        media.hasConfirmedWebSource() && generateSequence(error) { it.cause }
            .any { it is HttpDownloadException && it.statusCode in expiredStatuses }

    fun offersRecapture(queued: QueuedDownload): Boolean =
        queued.task.status == DownloadTaskStatus.FAILED &&
            queued.media.hasConfirmedWebSource() &&
            queued.task.errorSummary.orEmpty().let { summary ->
                summary.contains("重新读取播放源") || storedHttpFailure.containsMatchIn(summary)
            }

    private fun MediaItem.hasConfirmedWebSource(): Boolean =
        platform in setOf(DownloadPlatform.GENERIC, DownloadPlatform.PORNHUB) &&
            capturedVideoSourceVersion == 1 && capturedVideoUrl.isNotBlank() &&
            capturedVideoReferer.isNotBlank() && originalUrl.isNotBlank()
}

class WebSourceRefreshRequiredException(cause: Throwable) : IOException(
    "网页临时播放源已失效。解决办法：请点击“重新读取播放源”，在原作品页播放目标视频后确认。",
    cause,
)
