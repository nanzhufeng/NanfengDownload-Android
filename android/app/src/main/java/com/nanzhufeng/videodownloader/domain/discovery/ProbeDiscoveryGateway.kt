package com.nanzhufeng.videodownloader.domain.discovery

import com.nanzhufeng.videodownloader.probe.ClassifiedSource
import com.nanzhufeng.videodownloader.probe.BilibiliShortLinkResolver
import com.nanzhufeng.videodownloader.probe.CreatorCatalog
import com.nanzhufeng.videodownloader.probe.DouyinGalleryInfo
import com.nanzhufeng.videodownloader.probe.ResolvedSource
import com.nanzhufeng.videodownloader.probe.ShortLinkResolver
import com.nanzhufeng.videodownloader.probe.UrlClassifier
import com.nanzhufeng.videodownloader.probe.YtDlpMediaInfo
import com.nanzhufeng.videodownloader.probe.YtDlpProbe
import com.nanzhufeng.videodownloader.domain.session.NoOpSessionProvider
import com.nanzhufeng.videodownloader.domain.session.SessionProvider

interface ProbeDiscoveryGateway {
    fun classify(input: String): ClassifiedSource

    fun resolve(url: String): ResolvedSource

    fun extractSingle(url: String): YtDlpMediaInfo

    fun extractDouyinGallery(url: String): DouyinGalleryInfo? = null

    fun extractCreator(url: String, start: Int, pageSize: Int): CreatorCatalog
}

class ChaquopyProbeDiscoveryGateway(
    private val probe: YtDlpProbe = YtDlpProbe(),
    private val sessions: SessionProvider = NoOpSessionProvider,
    private val shortLinks: ShortLinkResolver = BilibiliShortLinkResolver(),
) : ProbeDiscoveryGateway {
    override fun classify(input: String): ClassifiedSource = UrlClassifier.extractAndClassify(input)

    override fun resolve(url: String): ResolvedSource {
        val resolvedUrl = shortLinks.resolve(url)
        return probe.resolveSource(resolvedUrl, sessions.accessFor(resolvedUrl))
    }

    override fun extractSingle(url: String): YtDlpMediaInfo =
        probe.extractSingle(url, access = sessions.accessFor(url))

    override fun extractDouyinGallery(url: String): DouyinGalleryInfo? =
        probe.extractDouyinGallery(url, access = sessions.accessFor(url))

    override fun extractCreator(url: String, start: Int, pageSize: Int): CreatorCatalog =
        probe.extractCreator(url, start, pageSize, sessions.accessFor(url))
}
