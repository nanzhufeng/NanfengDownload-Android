package com.nanzhufeng.videodownloader.probe

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.json.JSONObject

/** Discovery only. A public playable target supplies metadata; Worker resolves fresh media separately. */
internal object YouTubePageMetadata {
    const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    const val MAX_BYTES = 3 * 1024 * 1024

    fun workId(url: String): String? {
        val u = url.toHttpUrlOrNull() ?: return null
        if (u.scheme != "https" || u.port != 443 || u.username.isNotBlank() || u.password.isNotBlank()) return null
        val value = when (u.host) {
            "youtu.be" -> u.pathSegments.singleOrNull()
            "youtube.com", "www.youtube.com", "m.youtube.com" -> when {
                u.encodedPath == "/watch" -> u.queryParameterValues("v").singleOrNull()
                u.pathSegments.size == 2 && u.pathSegments[0] == "shorts" -> u.pathSegments[1]
                else -> null
            }
            else -> null
        }
        return value?.takeIf { Regex("[A-Za-z0-9_-]{11}").matches(it) }
    }

    fun prepareConnection(url: String) {
        if (workId(url) != null) DiscoveryPageHttp.prepareRoot("https://www.youtube.com/", USER_AGENT)
    }

    fun read(url: String, cookie: String): YtDlpMediaInfo? {
        val id = workId(url) ?: return null
        // Retain the complete session-aware extractor for authenticated or age-gated works.
        if (cookie.isNotBlank()) return null
        val page = "https://www.youtube.com/watch?v=$id"
        return DiscoveryPageHttp.read(Request.Builder().url(page).header("User-Agent", USER_AGENT)
            .header("Accept", "text/html").build(), "youtube", MAX_BYTES) { parse(it, url) }
    }

    fun parse(html: String, url: String): YtDlpMediaInfo? = runCatching {
        val id = workId(url) ?: return null
        if (html.toByteArray().size > MAX_BYTES) return null
        val encoded = listOf("var ytInitialPlayerResponse =", "ytInitialPlayerResponse=")
            .firstNotNullOfOrNull { DiscoveryPageHttp.objectAfter(html, it) } ?: return null
        val player = JSONObject(encoded)
        val status = player.optJSONObject("playabilityStatus") ?: return null
        if (status.optString("status") != "OK") return null
        val details = player.optJSONObject("videoDetails") ?: return null
        if (details.optString("videoId") != id || details.optBoolean("isLiveContent") ||
            details.optBoolean("isLive") || details.optBoolean("isUpcoming") || details.optBoolean("isPrivate")) return null
        val streaming = player.optJSONObject("streamingData") ?: return null
        if (streaming.has("licenseInfos") || streaming.has("drmFamilies")) return null
        var declaredVideo = false
        for (key in listOf("formats", "adaptiveFormats")) {
            val formats = streaming.optJSONArray(key) ?: continue
            if (formats.length() > 256) return null
            for (index in 0 until formats.length()) {
                val format = formats.optJSONObject(index) ?: continue
                if (format.has("drmFamilies") || format.has("licenseInfos")) return null
                if (format.optInt("itag") > 0 && format.optInt("width") > 0 && format.optInt("height") > 0 &&
                    format.optString("mimeType").startsWith("video/") && format.optString("mimeType").contains("codecs=")) declaredVideo = true
            }
        }
        if (!declaredVideo) return null
        val title = details.optString("title").takeIf(String::isNotBlank) ?: return null
        val author = details.optString("author").takeIf(String::isNotBlank) ?: return null
        val channel = details.optString("channelId").takeIf(String::isNotBlank) ?: return null
        val thumbs = details.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        val thumb = thumbs?.optJSONObject(thumbs.length() - 1)?.optString("url").orEmpty()
        val date = player.optJSONObject("microformat")?.optJSONObject("playerMicroformatRenderer")
            ?.optString("publishDate").orEmpty().take(10).replace("-", "")
        // Do not synthesize a transfer URL from SABR descriptors, ciphered URLs or metadata.
        YtDlpMediaInfo(platform="youtube", id=id, title=title, creator=author, creatorId=channel,
            webpageUrl="https://www.youtube.com/watch?v=$id", uploadDate=date, thumbnail=thumb,
            videoUrl="", audioUrl=null, videoExt="mp4", audioExt=null, headers=emptyMap())
    }.getOrNull()
}
