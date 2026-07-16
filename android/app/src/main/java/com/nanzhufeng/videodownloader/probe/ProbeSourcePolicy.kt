package com.nanzhufeng.videodownloader.probe

object ProbeSourcePolicy {
    fun requireSingle(
        source: ClassifiedSource,
        resolved: ResolvedSource? = null,
    ): ClassifiedSource {
        require(source.platform == Platform.YOUTUBE || source.platform == Platform.TIKTOK) {
            "这里只接受 YouTube 或 TikTok 单视频链接"
        }
        val finalSource = withResolvedSource(source, resolved)
        require(finalSource.kind == SourceKind.SINGLE_VIDEO) {
            "该链接是作者主页或频道，不会作为单视频下载"
        }
        return finalSource
    }

    fun requireTiktokCreator(
        source: ClassifiedSource,
        resolved: ResolvedSource? = null,
    ): ClassifiedSource {
        require(source.platform == Platform.TIKTOK) {
            "这里只接受 TikTok 作者主页链接"
        }
        val finalSource = withResolvedSource(source, resolved)
        require(finalSource.kind == SourceKind.CHANNEL_OR_PLAYLIST) {
            "该链接是 TikTok 单视频，不是作者主页"
        }
        return finalSource
    }

    private fun withResolvedSource(
        source: ClassifiedSource,
        resolved: ResolvedSource?,
    ): ClassifiedSource {
        if (source.kind != SourceKind.UNKNOWN_TIKTOK_SHARE) return source
        requireNotNull(resolved) { "TikTok 短链接尚未解析最终类型" }
        return source.copy(
            kind = resolved.kind,
            url = resolved.url.ifBlank { source.url },
        )
    }
}
