package com.nanzhufeng.videodownloader.probe

import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import java.net.URI
import java.security.MessageDigest

fun canInspectWebPlayer(url: String): Boolean = runCatching {
    val source = UrlClassifier.extractAndClassify(url)
    (source.platform == Platform.GENERIC && source.kind == SourceKind.UNKNOWN_WEB_PAGE) ||
        (source.platform == Platform.PORNHUB && source.kind == SourceKind.SINGLE_VIDEO)
}.getOrDefault(false)

data class GenericWebCapture(
    val pageUrl: String,
    val referer: String,
    val mediaUrl: String,
    val title: String,
) {
    fun validate(): GenericWebCapture {
        require(canInspectWebPlayer(pageUrl)) { "只能读取当前已识别的目标作品网页" }
        listOf(pageUrl, referer, mediaUrl).forEach { raw ->
            val uri = URI(raw)
            require(uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null) {
                "播放器未返回可下载的 HTTP/HTTPS 媒体地址"
            }
        }
        require(!URI(mediaUrl).path.orEmpty().lowercase().endsWith(".mpd")) {
            "该播放器只提供 DASH 清单，当前网页捕获不能确认完整分片"
        }
        return this
    }

    val platform: DownloadPlatform
        get() = if (UrlClassifier.extractAndClassify(pageUrl).platform == Platform.PORNHUB) {
            DownloadPlatform.PORNHUB
        } else DownloadPlatform.GENERIC

    val mediaId: String
        get() {
            if (platform == DownloadPlatform.PORNHUB) {
                return requireNotNull(
                    Regex("(?:^|&)viewkey=([A-Za-z0-9]+)(?:&|$)")
                        .find(URI(pageUrl).rawQuery.orEmpty())?.groupValues?.get(1),
                ) { "Pornhub 目标作品缺少 viewkey" }
            }
            if (YinghuaEpisodeParser.isEpisodeUrl(pageUrl)) {
                return genericCollectionMediaId(pageUrl)
            }
            val host = URI(pageUrl).host.orEmpty().lowercase()
            val bytes = MessageDigest.getInstance("SHA-256")
                .digest("$host\n$pageUrl".toByteArray(Charsets.UTF_8))
            return "capture:" + bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        }
}

data class GenericWebEpisode(val url: String, val title: String)

fun genericCollectionMediaId(url: String): String {
    val host = URI(url).host.orEmpty().lowercase()
    require(host.isNotBlank()) { "剧集链接缺少来源域名" }
    val bytes = MessageDigest.getInstance("SHA-256")
        .digest("$host\n$url".toByteArray(Charsets.UTF_8))
    return "collection:" + bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

object YinghuaEpisodeParser {
    private val workPattern = Regex("/vod(?:-detail|-play)?/(\\d+)")
    private val episodePattern = Regex("/vod-play/(\\d+)/ep(\\d+)\\.html")

    fun isEpisodeUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme in setOf("http", "https") &&
            uri.host.orEmpty().lowercase() in setOf("yhdm.one", "www.yhdm.one") &&
            uri.userInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
            episodePattern.matchEntire(uri.path.orEmpty()) != null
    }.getOrDefault(false)

    fun parse(pageUrl: String, anchors: List<String>): List<GenericWebEpisode> {
        val page = URI(pageUrl)
        require(page.host.orEmpty().lowercase() in setOf("yhdm.one", "www.yhdm.one")) {
            "该剧集规则只适用于樱花动漫作品页"
        }
        val workId = workPattern.find(page.path.orEmpty())?.groupValues?.get(1)
        val episodes = linkedMapOf<String, Pair<String, Int>>()
        anchors.forEach { href ->
            val target = runCatching { page.resolve(href) }.getOrNull() ?: return@forEach
            val match = episodePattern.matchEntire(target.path.orEmpty()) ?: return@forEach
            if (!target.scheme.equals("https", true) && !target.scheme.equals("http", true)) return@forEach
            if (target.host != page.host || target.rawQuery != null || target.userInfo != null) return@forEach
            if (workId != null && match.groupValues[1] != workId) return@forEach
            episodes.putIfAbsent(target.toString(), match.groupValues[1] to match.groupValues[2].toInt())
        }
        require(episodes.values.map { it.first }.distinct().size <= 1) {
            "页面包含多个作品，请打开具体作品的合集页"
        }
        return episodes.entries.sortedBy { it.value.second }
            .map { GenericWebEpisode(it.key, "第${it.value.second}集") }
    }
}
