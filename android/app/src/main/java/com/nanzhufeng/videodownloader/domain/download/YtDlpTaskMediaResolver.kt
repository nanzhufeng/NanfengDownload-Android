package com.nanzhufeng.videodownloader.domain.download

import com.nanzhufeng.videodownloader.core.model.MediaItem
import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
import com.nanzhufeng.videodownloader.probe.YtDlpProbe
import com.nanzhufeng.videodownloader.probe.DouyinCapturedMediaSource
import com.nanzhufeng.videodownloader.probe.NoOpDouyinCapturedMediaSource
import com.nanzhufeng.videodownloader.domain.session.NoOpSessionProvider
import com.nanzhufeng.videodownloader.domain.session.SessionAccess
import com.nanzhufeng.videodownloader.domain.session.SessionProvider
import com.nanzhufeng.videodownloader.probe.YtDlpMediaInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class YtDlpTaskMediaResolver(
    private val probe: YtDlpProbe = YtDlpProbe(),
    private val sessions: SessionProvider = NoOpSessionProvider,
    private val douyinCaptures: DouyinCapturedMediaSource = NoOpDouyinCapturedMediaSource,
    private val singleExtractor: (String, ResolutionPreset, SessionAccess) -> YtDlpMediaInfo =
        probe::extractSingle,
) : TaskMediaResolver {
    override suspend fun resolve(
        media: MediaItem,
        resolution: ResolutionPreset,
    ): ResolvedMedia = withContext(Dispatchers.IO) {
        val info = try {
            singleExtractor(
                media.originalUrl,
                resolution,
                sessions.accessFor(media.originalUrl),
            )
        } catch (error: Exception) {
            if (media.platform != DownloadPlatform.DOUYIN) throw error
            val captured = douyinCaptures.find(media.contentId) ?: throw error
            val access = sessions.accessFor(captured.pageUrl)
            return@withContext ResolvedMedia(
                videoUrl = captured.mediaUrl,
                audioUrl = null,
                videoExtension = "mp4",
                videoSizeBytes = 0L,
                audioExtension = null,
                headers = buildMap {
                    put("Referer", captured.pageUrl)
                    put("User-Agent", DOUYIN_MOBILE_USER_AGENT)
                    if (access.cookieHeader.isNotBlank()) put("Cookie", access.cookieHeader)
                },
            )
        }
        require(info.id == media.contentId) {
            "解析结果与目标作品不一致：${info.id} != ${media.contentId}"
        }
        ResolvedMedia(
            videoUrl = info.videoUrl,
            audioUrl = info.audioUrl,
            videoExtension = info.videoExt,
            videoSizeBytes = info.videoSizeBytes,
            audioExtension = info.audioExt,
            headers = info.headers,
            audioFromVideoSource = info.audioFromVideoSource,
        )
    }

    private companion object {
        const val DOUYIN_MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16; Mobile) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/138.0 Mobile Safari/537.36"
    }
}
