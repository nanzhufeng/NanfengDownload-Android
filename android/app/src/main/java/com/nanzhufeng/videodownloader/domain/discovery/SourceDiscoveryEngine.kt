package com.nanzhufeng.videodownloader.domain.discovery

interface SourceDiscoveryEngine {
    suspend fun read(input: String, page: Int = 1): DiscoveryResult
}
