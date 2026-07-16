package com.nanzhufeng.videodownloader.domain.discovery

import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
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
)

sealed interface DiscoveryResult {
    data class Single(val item: DiscoveredMedia) : DiscoveryResult

    data class Collection(
        val owner: CreatorIdentity,
        val items: List<DiscoveredMedia>,
        val hasMore: Boolean,
    ) : DiscoveryResult

    data class Failure(val message: String) : DiscoveryResult
}
