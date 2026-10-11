package com.nanzhufeng.videodownloader.probe

import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

/** Discovery only: read the exact primary player's declarations, not every rendition.
 * Download resolution keeps its existing full extractor and media validation.
 */
internal object PornhubPageVideo {
    const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    const val MAX_BYTES = 3 * 1024 * 1024
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).callTimeout(4, TimeUnit.SECONDS).build()
    private val warmed = LinkedHashMap<String, Long>()
    private val watcher = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "nanfeng-read-cancel").apply { isDaemon = true }
    }

    fun workId(url: String): String? {
        val u = url.toHttpUrlOrNull() ?: return null
        if (u.scheme != "https" || !official(u.host) || u.port != 443 ||
            u.username.isNotEmpty() || u.password.isNotEmpty() || u.encodedPath != "/view_video.php") return null
        val ids = u.queryParameterValues("viewkey")
        return ids.singleOrNull()?.takeIf { Regex("[A-Za-z0-9]{2,64}").matches(it) }
    }

    private fun official(host: String) = host == "pornhub.com" || host.endsWith(".pornhub.com")

    fun prepareConnection(url: String) {
        if (workId(url) == null) return
        val host = requireNotNull(url.toHttpUrlOrNull()).host
        val now = android.os.SystemClock.elapsedRealtime()
        synchronized(warmed) {
            val before = warmed[host]
            if (before != null && now - before < 60_000L) return
            warmed[host] = now
            if (warmed.size > 16) warmed.remove(warmed.keys.first())
        }
        val warmClient = client.newBuilder().callTimeout(1, TimeUnit.SECONDS).build()
        warmClient.newCall(Request.Builder().url("https://$host/").header("User-Agent", USER_AGENT).head().build())
            .enqueue(object : Callback {
                override fun onFailure(call: Call, e: java.io.IOException) = Unit
                override fun onResponse(call: Call, response: Response) { response.close() }
            })
    }

    fun read(url: String, resolution: ResolutionPreset): YtDlpMediaInfo? {
        if (workId(url) == null) return null
        val thread = Thread.currentThread()
        if (thread.isInterrupted) throw CancellationException()
        val started = System.nanoTime()
        val call = client.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT)
            .header("Accept", "text/html").build())
        val guard = watcher.scheduleWithFixedDelay({ if (thread.isInterrupted) call.cancel() }, 0, 50, TimeUnit.MILLISECONDS)
        try {
            return call.execute().use { response ->
                if (response.code != 200) {
                    android.util.Log.i("ReadPerformance", "platform=pornhub stage=page_status code=${response.code}")
                    return@use null
                }
                val stream = response.body?.byteStream() ?: return@use null
                val buffer = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                var nextParse = 8192
                while (buffer.size() <= MAX_BYTES) {
                    if (thread.isInterrupted) throw CancellationException()
                    val count = stream.read(chunk, 0, minOf(chunk.size, MAX_BYTES + 1 - buffer.size()))
                    if (count < 0) break
                    buffer.write(chunk, 0, count)
                    if (buffer.size() > MAX_BYTES) return@use null
                    if (buffer.size() >= nextParse) {
                        parse(buffer.toString("UTF-8"), url, resolution)?.let {
                            android.util.Log.i("ReadPerformance", "platform=pornhub stage=target_page bytes=${buffer.size()} elapsedMs=${(System.nanoTime()-started)/1_000_000}")
                            return@use it
                        }
                        nextParse = buffer.size() + 32 * 1024
                    }
                }
                parse(buffer.toString("UTF-8"), url, resolution).also {
                    android.util.Log.i("ReadPerformance", "platform=pornhub stage=page_end bytes=${buffer.size()} valid=${it != null} elapsedMs=${(System.nanoTime()-started)/1_000_000}")
                }
            }
        } catch (error: Exception) {
            if (thread.isInterrupted || error is CancellationException) throw CancellationException()
            android.util.Log.i("ReadPerformance", "platform=pornhub stage=page_error type=${error.javaClass.simpleName} elapsedMs=${(System.nanoTime()-started)/1_000_000}")
            return null
        } finally { guard.cancel(false) }
    }

    fun parse(html: String, url: String, resolution: ResolutionPreset): YtDlpMediaInfo? = runCatching {
        val id = workId(url) ?: return null
        if (html.toByteArray().size > MAX_BYTES) return null
        val metadata = Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(html).map(::attributes).toList()
        val canonical = metadata.firstOrNull { it["property"] == "og:url" }?.get("content") ?: return null
        if (workId(decode(canonical)) != id) return null
        if (Regex("""<[^>]+(?:id=["']lockedPlayer["']|class=["'][^"']*\b(?:geoBlocked|noVideo|removed)\b)""", RegexOption.IGNORE_CASE).containsMatchIn(html)) return null
        val assignment = Regex("""var\s+flashvars_\d+\s*=\s*(\{.*?\});""", RegexOption.DOT_MATCHES_ALL).find(html) ?: return null
        val encoded = assignment.groupValues[1]
        if (!boundedJson(encoded)) return null
        val player = JSONObject(encoded)
        val embed = Regex("""https://[^\s"'<>\\]+/embed/([A-Za-z0-9]+)""").find(player.optString("embedCode")) ?: return null
        val embedUrl = embed.value.toHttpUrlOrNull() ?: return null
        if (!official(embedUrl.host) || embedUrl.port != 443 || embedUrl.username.isNotBlank() || embedUrl.password.isNotBlank() || embed.groupValues[1] != id) return null
        if (player.optString("video_unavailable") !in setOf("", "false", "0")) return null
        val definitions = player.optJSONArray("mediaDefinitions") ?: return null
        if (definitions.length() > 64) return null
        val formats = buildList {
            for (index in 0 until definitions.length()) {
                val item = definitions.optJSONObject(index) ?: continue
                val media = item.optString("videoUrl").toHttpUrlOrNull() ?: continue
                if (media.scheme != "https" || media.port != 443 || media.username.isNotBlank() || media.password.isNotBlank() ||
                    !(media.host == "phncdn.com" || media.host.endsWith(".phncdn.com")) ||
                    !SignedMediaUrlExpiry.isFresh(media.toString(), System.currentTimeMillis())) continue
                val height = item.optString("quality").toIntOrNull() ?: continue
                if (height <= 0 || item.optString("format") !in setOf("hls", "mp4") ||
                    !(media.encodedPath.endsWith(".m3u8") || media.encodedPath.endsWith(".mp4"))) continue
                add(height to media.toString())
            }
        }
        val cap = when (resolution) { ResolutionPreset.BEST -> Int.MAX_VALUE; ResolutionPreset.UP_TO_1080P -> 1080; ResolutionPreset.UP_TO_360P -> 360; else -> 720 }
        val chosen = formats.filter { it.first <= cap }.maxByOrNull { it.first } ?: formats.minByOrNull { it.first } ?: return null
        val title = metadata.firstOrNull { it["name"] == "twitter:title" }?.get("content")
            ?: metadata.firstOrNull { it["property"] == "og:title" }?.get("content") ?: return null
        val details = Regex("""<script\b[^>]*type=["']application/ld\+json["'][^>]*>(.*?)</script\s*>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(html).mapNotNull { if (!boundedJson(it.groupValues[1])) null else runCatching { JSONObject(it.groupValues[1]) }.getOrNull() }
            .filter { it.optString("@type") == "VideoObject" }.toList().singleOrNull()
        val author = details?.opt("author")
        val creator = if (author is JSONObject) author.optString("name") else (author as? String).orEmpty()
        val upload = details?.optString("uploadDate").orEmpty().take(10).replace("-", "")
        YtDlpMediaInfo(platform="pornhub", id=id, title=decode(title), creator=decode(creator).ifBlank { "Pornhub" }, creatorId="",
            webpageUrl=url, uploadDate=upload, thumbnail=player.optString("image_url"), videoUrl=chosen.second,
            audioUrl=null, videoExt="mp4", audioExt=null, headers=mapOf("User-Agent" to USER_AGENT, "Referer" to url))
    }.getOrElse {
        android.util.Log.i("ReadPerformance", "platform=pornhub stage=parse_error type=${it.javaClass.simpleName}")
        null
    }

    private fun attributes(match: MatchResult): Map<String, String> =
        Regex("""([\w:-]+)\s*=\s*(["'])(.*?)\2""", RegexOption.DOT_MATCHES_ALL).findAll(match.value)
            .associate { it.groupValues[1].lowercase() to it.groupValues[3] }

    private fun boundedJson(value: String): Boolean {
        if (value.length > 128 * 1024) return false
        var quoted = false
        var escaped = false
        var depth = 0
        var nodes = 0
        for (char in value) {
            if (quoted) {
                if (escaped) escaped = false else if (char == '\\') escaped = true else if (char == '"') quoted = false
            } else when (char) {
                '"' -> quoted = true
                '{', '[' -> { depth++; nodes++; if (depth > 32 || nodes > 4096) return false }
                '}', ']' -> depth--
            }
        }
        return depth == 0 && !quoted
    }

    internal fun decode(value: String): String {
        val basic = value.replace("&quot;", "\"").replace("&#039;", "'").replace("&apos;", "'")
            .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ").replace("&amp;", "&")
        return Regex("&#(x[0-9a-fA-F]+|[0-9]+);").replace(basic) { match ->
            val raw = match.groupValues[1]
            val code = if (raw.startsWith("x")) raw.drop(1).toIntOrNull(16) else raw.toIntOrNull()
            if (code != null && Character.isValidCodePoint(code)) String(Character.toChars(code)) else match.value
        }
    }
}
