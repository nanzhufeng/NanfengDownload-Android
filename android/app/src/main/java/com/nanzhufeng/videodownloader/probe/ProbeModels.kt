package com.nanzhufeng.videodownloader.probe

enum class Platform {
    YOUTUBE,
    BILIBILI,
    DOUYIN,
    TIKTOK,
    XIAOHONGSHU,
}

enum class SourceKind {
    SINGLE_VIDEO,
    CHANNEL_OR_PLAYLIST,
    UNKNOWN_DOUYIN_SHARE,
    UNKNOWN_TIKTOK_SHARE,
    UNKNOWN_BILIBILI_SHARE,
    UNKNOWN_XIAOHONGSHU_SHARE,
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
