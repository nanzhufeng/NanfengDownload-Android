package com.nanzhufeng.videodownloader.domain.discovery

interface SourceDiscoveryEngine {
    fun prepare() = Unit
    suspend fun read(input: String, page: Int = 1): DiscoveryResult
}
