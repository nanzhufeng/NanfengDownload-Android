package com.nanzhufeng.videodownloader.domain.download

import com.nanzhufeng.videodownloader.core.model.MediaItem
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
import com.nanzhufeng.videodownloader.probe.YtDlpProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class YtDlpTaskMediaResolver(
    private val probe: YtDlpProbe = YtDlpProbe(),
) : TaskMediaResolver {
    override suspend fun resolve(
        media: MediaItem,
        resolution: ResolutionPreset,
    ): ResolvedMedia = withContext(Dispatchers.IO) {
        val info = probe.extractSingle(media.originalUrl, resolution)
        require(info.id == media.contentId) {
            "解析结果与目标作品不一致：${info.id} != ${media.contentId}"
        }
        ResolvedMedia(
            videoUrl = info.videoUrl,
            audioUrl = info.audioUrl,
            videoExtension = info.videoExt,
            audioExtension = info.audioExt,
            headers = info.headers,
        )
    }
}
