package com.nanzhufeng.videodownloader.probe

enum class Platform {
    YOUTUBE,
    DOUYIN,
}

enum class SourceKind {
    SINGLE_VIDEO,
    CHANNEL_OR_PLAYLIST,
    UNKNOWN_DOUYIN_SHARE,
}

data class ClassifiedSource(
    val platform: Platform,
    val kind: SourceKind,
    val url: String,
)

data class ProbeResult(
    val success: Boolean,
    val stage: String,
    val message: String,
    val details: Map<String, String> = emptyMap(),
)
