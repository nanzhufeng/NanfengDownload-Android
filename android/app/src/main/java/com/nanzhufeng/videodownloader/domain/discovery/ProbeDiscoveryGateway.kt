package com.nanzhufeng.videodownloader.domain.discovery

import com.nanzhufeng.videodownloader.probe.ClassifiedSource
import com.nanzhufeng.videodownloader.probe.CreatorCatalog
import com.nanzhufeng.videodownloader.probe.ResolvedSource
import com.nanzhufeng.videodownloader.probe.UrlClassifier
import com.nanzhufeng.videodownloader.probe.YtDlpMediaInfo
import com.nanzhufeng.videodownloader.probe.YtDlpProbe
import com.nanzhufeng.videodownloader.domain.session.NoOpSessionProvider
import com.nanzhufeng.videodownloader.domain.session.SessionProvider

interface ProbeDiscoveryGateway {
    fun classify(input: String): ClassifiedSource

    fun resolve(url: String): ResolvedSource

    fun extractSingle(url: String): YtDlpMediaInfo

    fun extractCreator(url: String, start: Int, pageSize: Int): CreatorCatalog
}

class ChaquopyProbeDiscoveryGateway(
    private val probe: YtDlpProbe = YtDlpProbe(),
    private val sessions: SessionProvider = NoOpSessionProvider,
) : ProbeDiscoveryGateway {
    override fun classify(input: String): ClassifiedSource = UrlClassifier.extractAndClassify(input)

    override fun resolve(url: String): ResolvedSource = probe.resolveSource(url, sessions.accessFor(url))

    override fun extractSingle(url: String): YtDlpMediaInfo =
        probe.extractSingle(url, access = sessions.accessFor(url))

    override fun extractCreator(url: String, start: Int, pageSize: Int): CreatorCatalog =
        probe.extractCreator(url, start, pageSize, sessions.accessFor(url))
}
