package com.nanzhufeng.videodownloader.probe

import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
import java.net.URI
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject

/** Official, target-scoped SSR; never accept recommendation or login-page data. */
internal object TikTokPageVideo {
    const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    const val MAX_BYTES = 2 * 1024 * 1024

    fun prepareConnection(url: String) {
        if (workId(url) != null) DiscoveryPageHttp.prepareRoot("https://www.tiktok.com/", USER_AGENT)
    }

    fun readMetadata(url: String, cookie: String): YtDlpMediaInfo? {
        if (workId(url) == null) return null
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT)
            .header("Accept", "text/html").header("Referer", "https://www.tiktok.com/")
            .apply { if (cookie.isNotBlank()) header("Cookie", cookie) }.build()
        return DiscoveryPageHttp.read(request, "tiktok", MAX_BYTES) { parse(it, url, ResolutionPreset.UP_TO_720P) }
    }
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).callTimeout(8, TimeUnit.SECONDS).build()

    fun workId(url: String): String? = runCatching {
        val u = URI(url)
        if (u.scheme != "https" || u.host !in setOf("www.tiktok.com", "tiktok.com") ||
            u.userInfo != null || u.port !in setOf(-1, 443)) return null
        Regex("^/@[^/]+/video/(\\d+)/?$").matchEntire(u.path)?.groupValues?.get(1)
    }.getOrNull()

    fun read(url: String, resolution: ResolutionPreset, cookie: String): YtDlpMediaInfo? {
        if (workId(url) == null) return null
        if (Thread.currentThread().isInterrupted) throw java.util.concurrent.CancellationException()
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT)
            .header("Accept", "text/html").header("Referer", "https://www.tiktok.com/")
            .apply { if (cookie.isNotBlank()) header("Cookie", cookie) }.build()
        val result = runCatching {
            client.newCall(request).execute().use { response ->
                if (response.code != 200) {
                    android.util.Log.i("TikTokVideoSource", "native page status=${response.code}")
                    return@use null
                }
                val source = response.body?.source() ?: return@use null
                source.request(MAX_BYTES.toLong() + 1)
                if (source.buffer.size > MAX_BYTES) return@use null
                val html = source.readUtf8()
                parse(html, url, resolution)?.let { bindMediaCookies(it, Cookie.parseAll(response.request.url, response.headers)) }.also {
                    android.util.Log.i("TikTokVideoSource", "native target page bytes=${html.toByteArray().size} valid=${it != null}")
                }
            }
        }.getOrNull()
        if (Thread.currentThread().isInterrupted) throw java.util.concurrent.CancellationException()
        return result
    }

    fun bindMediaCookies(info: YtDlpMediaInfo, cookies: List<Cookie>, nowMillis: Long = System.currentTimeMillis()): YtDlpMediaInfo {
        val target = info.videoUrl.toHttpUrl()
        val scoped = cookies.filter { it.expiresAt > nowMillis && it.matches(target) }
            .joinToString("; ") { "${it.name}=${it.value}" }
        return info.copy(videoCookieHeader = scoped)
    }

    fun parse(html: String, url: String, resolution: ResolutionPreset): YtDlpMediaInfo? = runCatching {
        val id = workId(url) ?: return null
        if (html.toByteArray().size > MAX_BYTES) return null
        val script = Regex("""<script\b[^>]*\bid=["']__UNIVERSAL_DATA_FOR_REHYDRATION__["'][^>]*>(.*?)</script\s*>""", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1) ?: return null
        val scope = JSONObject(script).getJSONObject("__DEFAULT_SCOPE__")
        val detail = scope.optJSONObject("webapp.video-detail")
            ?: scope.optJSONObject("webapp.reflow.video.detail") ?: return null
        if (!detail.has("statusCode") || detail.optInt("statusCode", -1) != 0) return null
        val item = detail.getJSONObject("itemInfo").getJSONObject("itemStruct")
        if (item.optString("id") != id) return null
        val video = item.getJSONObject("video")
        val candidates = mutableListOf<Triple<String, Int, Long>>()
        fun add(address: String, shortEdge: Int, bitrate: Long) {
            val u = runCatching { URI(address) }.getOrNull() ?: return
            val host = u.host.orEmpty().lowercase()
            if (u.path.orEmpty().endsWith("/media-video-hvc1/")) return
            if (u.scheme != "https" || u.userInfo != null || u.port !in setOf(-1, 443) ||
                !listOf("tiktok.com", "tiktokcdn.com", "tiktokcdn-us.com", "tiktokv.com", "byteoversea.com", "ibytedtos.com").any { host == it || host.endsWith(".$it") } ||
                !SignedMediaUrlExpiry.isFresh(address, System.currentTimeMillis())) return
            candidates += Triple(address, shortEdge, bitrate)
        }
        val rates = video.optJSONArray("bitrateInfo")
        for (i in 0 until (rates?.length() ?: 0)) {
            val rate = rates?.optJSONObject(i) ?: continue
            val play = rate.optJSONObject("PlayAddr") ?: continue
            if (play.optString("UrlKey").contains("bytevc2", ignoreCase = true)) continue
            val edge = minOf(play.optInt("Width", 0), play.optInt("Height", 0))
            val urls = play.optJSONArray("UrlList") ?: continue
            for (j in 0 until urls.length()) add(urls.optString(j), edge, rate.optLong("Bitrate", 0))
        }
        add(video.optString("playAddr"), minOf(video.optInt("width", 0), video.optInt("height", 0)), video.optLong("bitrate", 0))
        val cap = when (resolution) {
            ResolutionPreset.UP_TO_360P -> 360
            ResolutionPreset.UP_TO_1080P -> 1080
            ResolutionPreset.BEST -> Int.MAX_VALUE
            else -> 720
        }
        val selected = candidates.filter { it.second in 1..cap }.maxWithOrNull(compareBy({ it.second }, { it.third }))
            ?: candidates.minWithOrNull(compareBy<Triple<String, Int, Long>> { it.second }.thenByDescending { it.third }) ?: return null
        val author = item.optJSONObject("author") ?: JSONObject()
        val created = item.optLong("createTime", 0)
        YtDlpMediaInfo(
            platform = "tiktok", id = id, title = item.optString("desc", "TikTok 视频"),
            creator = author.optString("nickname", "TikTok 用户"), creatorId = author.optString("id"),
            webpageUrl = url, uploadDate = if (created > 0) DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC).format(Instant.ofEpochSecond(created)) else "",
            thumbnail = video.optString("cover"), videoUrl = selected.first, audioUrl = null,
            videoExt = "mp4", audioExt = null, headers = mapOf("User-Agent" to USER_AGENT, "Referer" to url),
            audioFromVideoSource = resolution == ResolutionPreset.AUDIO_MP3,
        )
    }.getOrNull()
}
