package com.nanzhufeng.videodownloader.domain.discovery

import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import com.nanzhufeng.videodownloader.core.model.DownloadSourceKind
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset

data class CreatorIdentity(
    val name: String,
    val id: String,
)

data class DiscoveredMedia(
    val sourceUrl: String,
    val platform: DownloadPlatform,
    val mediaId: String,
    val title: String,
    val creator: CreatorIdentity,
    val publishedAt: String,
    val thumbnailUrl: String,
    val defaultResolution: ResolutionPreset,
    val capturedImageUrls: List<String> = emptyList(),
    val capturedImageExpectedCount: Int = 0,
    val capturedImageSourceVersion: Int = 0,
    val legacyMediaId: String = "",
)

sealed interface DiscoveryResult {
    data class Single(val item: DiscoveredMedia) : DiscoveryResult

    data class Batch(
        val items: List<DiscoveredMedia>,
        val errors: List<Pair<String, String>>,
        val sourceKinds: Map<Pair<DownloadPlatform, String>, DownloadSourceKind> = emptyMap(),
    ) : DiscoveryResult

    data class DouyinCaptureRequired(val sourceUrl: String) : DiscoveryResult

    data class WebCaptureRequired(val sourceUrl: String) : DiscoveryResult

    data class Collection(
        val owner: CreatorIdentity,
        val items: List<DiscoveredMedia>,
        val hasMore: Boolean,
        val nextPage: Int?,
    ) : DiscoveryResult

    data class Failure(val message: String) : DiscoveryResult
}
