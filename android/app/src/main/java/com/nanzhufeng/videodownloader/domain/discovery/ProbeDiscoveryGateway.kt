package com.nanzhufeng.videodownloader.domain.discovery

import com.nanzhufeng.videodownloader.probe.ClassifiedSource
import com.nanzhufeng.videodownloader.probe.BilibiliShortLinkResolver
import com.nanzhufeng.videodownloader.probe.DouyinShortLinkResolver
import com.nanzhufeng.videodownloader.probe.Platform
import com.nanzhufeng.videodownloader.probe.SourceKind
import com.nanzhufeng.videodownloader.probe.CreatorCatalog
import com.nanzhufeng.videodownloader.probe.ResolvedSource
import com.nanzhufeng.videodownloader.probe.ShortLinkResolver
import com.nanzhufeng.videodownloader.probe.UrlClassifier
import com.nanzhufeng.videodownloader.probe.YtDlpMediaInfo
import com.nanzhufeng.videodownloader.probe.YtDlpProbe
import com.nanzhufeng.videodownloader.domain.session.NoOpSessionProvider
import com.nanzhufeng.videodownloader.domain.session.SessionProvider

interface ProbeDiscoveryGateway {
    fun prepare() = Unit
    fun classify(input: String): ClassifiedSource

    fun resolve(url: String): ResolvedSource

    fun extractSingle(url: String): YtDlpMediaInfo

    fun extractCreator(url: String, start: Int, pageSize: Int): CreatorCatalog

    fun extractWebCollection(url: String, start: Int, pageSize: Int): CreatorCatalog =
        throw UnsupportedOperationException("通用合集提取器未接入")
}

class ChaquopyProbeDiscoveryGateway(
    private val probe: YtDlpProbe = YtDlpProbe(),
    private val sessions: SessionProvider = NoOpSessionProvider,
    private val shortLinks: ShortLinkResolver = BilibiliShortLinkResolver(),
    private val douyinShortLinks: ShortLinkResolver = DouyinShortLinkResolver(),
) : ProbeDiscoveryGateway {
    override fun prepare() = probe.prepare()
    override fun classify(input: String): ClassifiedSource = UrlClassifier.extractAndClassify(input)

    override fun resolve(url: String): ResolvedSource {
        val douyinUrl = douyinShortLinks.resolve(url)
        if (douyinUrl != url) {
            val source = UrlClassifier.extractAndClassify(douyinUrl)
            if (source.platform == Platform.DOUYIN && source.kind == SourceKind.SINGLE_VIDEO) {
                return ResolvedSource(source.kind, source.url)
            }
        }
        val resolvedUrl = shortLinks.resolve(url)
        return probe.resolveSource(resolvedUrl, sessions.accessFor(resolvedUrl))
    }

    override fun extractSingle(url: String): YtDlpMediaInfo =
        probe.readSingle(url, access = sessions.accessFor(url))

    override fun extractCreator(url: String, start: Int, pageSize: Int): CreatorCatalog =
        probe.extractCreator(url, start, pageSize, sessions.accessFor(url))

    override fun extractWebCollection(url: String, start: Int, pageSize: Int): CreatorCatalog =
        probe.extractWebCollection(url, start, pageSize)
}
