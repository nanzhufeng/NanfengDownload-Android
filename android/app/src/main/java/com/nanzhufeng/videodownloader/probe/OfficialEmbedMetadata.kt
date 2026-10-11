package com.nanzhufeng.videodownloader.probe

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.json.JSONObject

/** Public official oEmbed metadata only. No session, cache, invented stream or transfer reuse. */
internal object OfficialEmbedMetadata {
    fun read(url: String): YtDlpMediaInfo? {
        val youtube = YouTubePageMetadata.workId(url)
        val tiktok = TikTokPageVideo.workId(url)
        if (youtube == null && tiktok == null) return null
        val target = if (youtube != null) "https://www.youtube.com/watch?v=$youtube" else url
        val endpoint = (if (youtube != null) "https://www.youtube.com/oembed" else "https://www.tiktok.com/oembed")
            .toHttpUrlOrNull()!!.newBuilder().addQueryParameter("url", target).addQueryParameter("format", "json").build()
        return DiscoveryPageHttp.read(Request.Builder().url(endpoint).header("User-Agent", YouTubePageMetadata.USER_AGENT)
            .header("Accept", "application/json").build(), if (youtube != null) "youtube_oembed" else "tiktok_oembed", 64 * 1024) { parse(it, url) }
    }

    fun parse(body: String, url: String): YtDlpMediaInfo? = runCatching {
        if (body.toByteArray().size > 64 * 1024) return null
        val youtube = YouTubePageMetadata.workId(url)
        val tiktok = TikTokPageVideo.workId(url)
        val id = youtube ?: tiktok ?: return null
        val data = JSONObject(body)
        if (data.optString("type") != "video" || data.optString("version") != "1.0") return null
        val title = data.optString("title").takeIf(String::isNotBlank) ?: return null
        val author = data.optString("author_name").takeIf(String::isNotBlank) ?: return null
        val html = data.optString("html")
        if (html.length > 32 * 1024) return null
        val authorUrl = data.optString("author_url").toHttpUrlOrNull() ?: return null
        if (authorUrl.scheme != "https" || authorUrl.port != 443 || authorUrl.username.isNotBlank() || authorUrl.password.isNotBlank()) return null
        val creatorId: String
        if (youtube != null) {
            if (data.optString("provider_name") != "YouTube" || authorUrl.host !in setOf("www.youtube.com", "youtube.com")) return null
            val iframe = Regex("""<iframe\b[^>]*\bsrc=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(html) ?: return null
            val embed = iframe.groupValues[1].toHttpUrlOrNull() ?: return null
            if (embed.scheme != "https" || embed.host !in setOf("www.youtube.com", "youtube.com") || embed.port != 443 ||
                embed.username.isNotBlank() || embed.password.isNotBlank() || embed.encodedPath != "/embed/$id") return null
            creatorId = authorUrl.pathSegments.takeIf { it.size == 2 && it[0] == "channel" }?.get(1).orEmpty()
        } else {
            if (data.optString("provider_name") != "TikTok" || authorUrl.host !in setOf("www.tiktok.com", "tiktok.com") ||
                data.optString("embed_product_id") != id || data.optString("embed_type") != "video") return null
            val block = Regex("""<blockquote\b[^>]*>""", RegexOption.IGNORE_CASE).find(html)?.value ?: return null
            val attrs = Regex("""([\w-]+)\s*=\s*(["'])(.*?)\2""").findAll(block)
                .associate { it.groupValues[1] to it.groupValues[3] }
            if (attrs["data-video-id"] != id || TikTokPageVideo.workId(attrs["cite"].orEmpty()) != id) return null
            val owner = url.toHttpUrlOrNull()!!.pathSegments.firstOrNull()?.removePrefix("@").orEmpty()
            if (owner.isBlank() || data.optString("author_unique_id") != owner || authorUrl.encodedPath.trim('/') != "@$owner") return null
            creatorId = "" // A mutable handle is not the platform's immutable creator ID.
        }
        YtDlpMediaInfo(platform=if (youtube != null) "youtube" else "tiktok", id=id, title=title, creator=author,
            creatorId=creatorId, webpageUrl=if (youtube != null) "https://www.youtube.com/watch?v=$id" else url,
            uploadDate="", thumbnail=data.optString("thumbnail_url"), videoUrl="", audioUrl=null,
            videoExt="mp4", audioExt=null, headers=emptyMap())
    }.getOrNull()
}
