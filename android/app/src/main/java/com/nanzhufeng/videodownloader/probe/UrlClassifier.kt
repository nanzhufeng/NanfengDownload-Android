package com.nanzhufeng.videodownloader.probe

import java.net.URI

object UrlClassifier {
    private val urlRegex = Regex("https?://[^\\s]+")

    fun extractAndClassify(text: String): ClassifiedSource {
        val raw = urlRegex.find(text)?.value
            ?.trimEnd('。', '，', ',', '.', ')', '）', ']', '】')
            ?: throw IllegalArgumentException("没有找到支持平台的视频链接")
        val uri = runCatching { URI(raw) }
            .getOrElse { throw IllegalArgumentException("链接格式无效，请重新复制官方分享链接") }
        val host = uri.host.orEmpty().lowercase()
        val path = uri.path.orEmpty().lowercase()

        return when {
            host.matchesDomain("youtu.be") && path.length > 1 ->
                ClassifiedSource(Platform.YOUTUBE, SourceKind.SINGLE_VIDEO, raw)

            host.matchesDomain("youtube.com") &&
                (path == "/watch" || path.startsWith("/live/") || path.startsWith("/shorts/")) ->
                ClassifiedSource(Platform.YOUTUBE, SourceKind.SINGLE_VIDEO, raw)

            host.matchesDomain("youtube.com") &&
                (path == "/playlist" || path.startsWith("/@") ||
                    path.startsWith("/channel/") || path.startsWith("/c/")) ->
                ClassifiedSource(Platform.YOUTUBE, SourceKind.CHANNEL_OR_PLAYLIST, raw)

            host.matchesDomain("douyin.com") && path.startsWith("/video/") ->
                ClassifiedSource(Platform.DOUYIN, SourceKind.SINGLE_VIDEO, raw)

            host.matchesDomain("douyin.com") && path.startsWith("/user/") ->
                ClassifiedSource(Platform.DOUYIN, SourceKind.CHANNEL_OR_PLAYLIST, raw)

            host == "v.douyin.com" ->
                ClassifiedSource(Platform.DOUYIN, SourceKind.UNKNOWN_DOUYIN_SHARE, raw)

            host.matchesDomain("tiktok.com") && path.startsWith("/@") && "/video/" in path ->
                ClassifiedSource(Platform.TIKTOK, SourceKind.SINGLE_VIDEO, raw)

            host.matchesDomain("tiktok.com") &&
                Regex("^/@[^/]+/?$").matches(path) ->
                ClassifiedSource(Platform.TIKTOK, SourceKind.CHANNEL_OR_PLAYLIST, raw)

            host == "vm.tiktok.com" || host == "vt.tiktok.com" ->
                ClassifiedSource(Platform.TIKTOK, SourceKind.UNKNOWN_TIKTOK_SHARE, raw)

            host.matchesDomain("bilibili.com") &&
                (path.startsWith("/video/av") || path.startsWith("/video/bv") ||
                    path.startsWith("/bangumi/play/")) ->
                ClassifiedSource(Platform.BILIBILI, SourceKind.SINGLE_VIDEO, raw)

            host == "space.bilibili.com" &&
                path.trim('/').substringBefore('/').let { it.isNotBlank() && it.all(Char::isDigit) } ->
                throw IllegalArgumentException(
                    "哔哩哔哩当前只支持单个视频，暂不支持从 UP 主页批量读取。" +
                        "请打开具体视频后复制分享链接重试",
                )

            host == "b23.tv" ->
                ClassifiedSource(Platform.BILIBILI, SourceKind.UNKNOWN_BILIBILI_SHARE, raw)

            (host.matchesDomain("xiaohongshu.com") || host.matchesDomain("rednote.com")) &&
                (path.startsWith("/explore/") || path.startsWith("/discovery/item/")) ->
                ClassifiedSource(Platform.XIAOHONGSHU, SourceKind.SINGLE_VIDEO, raw)

            host.matchesDomain("xhslink.com") ->
                ClassifiedSource(Platform.XIAOHONGSHU, SourceKind.UNKNOWN_XIAOHONGSHU_SHARE, raw)

            else -> throw IllegalArgumentException(
                "只支持抖音、YouTube、TikTok、哔哩哔哩和小红书视频链接",
            )
        }
    }

    private fun String.matchesDomain(domain: String): Boolean =
        this == domain || endsWith(".$domain")
}
