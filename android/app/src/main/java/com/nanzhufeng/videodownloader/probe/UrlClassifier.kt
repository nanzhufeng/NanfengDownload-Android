package com.nanzhufeng.videodownloader.probe

object UrlClassifier {
    private val urlRegex = Regex("https?://[^\\s]+")

    fun extractAndClassify(text: String): ClassifiedSource {
        val raw = urlRegex.find(text)?.value
            ?.trimEnd('。', '，', ',', '.', ')', '）', ']', '】')
            ?: throw IllegalArgumentException("没有找到抖音、YouTube 或 TikTok 链接")
        val lower = raw.lowercase()

        return when {
            "youtube.com/watch" in lower ||
                "youtube.com/live/" in lower ||
                "youtu.be/" in lower ->
                ClassifiedSource(Platform.YOUTUBE, SourceKind.SINGLE_VIDEO, raw)

            "youtube.com/playlist" in lower ||
                "youtube.com/@" in lower ||
                "youtube.com/channel/" in lower ||
                "youtube.com/c/" in lower ->
                ClassifiedSource(Platform.YOUTUBE, SourceKind.CHANNEL_OR_PLAYLIST, raw)

            "douyin.com/video/" in lower ->
                ClassifiedSource(Platform.DOUYIN, SourceKind.SINGLE_VIDEO, raw)

            "douyin.com/user/" in lower ->
                ClassifiedSource(Platform.DOUYIN, SourceKind.CHANNEL_OR_PLAYLIST, raw)

            "v.douyin.com/" in lower ->
                ClassifiedSource(Platform.DOUYIN, SourceKind.UNKNOWN_DOUYIN_SHARE, raw)

            "tiktok.com/@" in lower && "/video/" in lower ->
                ClassifiedSource(Platform.TIKTOK, SourceKind.SINGLE_VIDEO, raw)

            Regex("https?://(?:www\\.)?tiktok\\.com/@[^/?#]+/?(?:[?#].*)?$").matches(lower) ->
                ClassifiedSource(Platform.TIKTOK, SourceKind.CHANNEL_OR_PLAYLIST, raw)

            "vm.tiktok.com/" in lower || "vt.tiktok.com/" in lower ->
                ClassifiedSource(Platform.TIKTOK, SourceKind.UNKNOWN_TIKTOK_SHARE, raw)

            else -> throw IllegalArgumentException("只支持抖音、YouTube 和 TikTok 链接")
        }
    }
}
