package com.nanzhufeng.videodownloader.probe

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Official public metadata only; does not claim a rendition, uploader or completed download. */
internal object PornhubApiMetadata {
    fun endpoint(url: String): okhttp3.HttpUrl? {
        val id = PornhubPageVideo.workId(url) ?: return null
        val host = url.toHttpUrlOrNull()?.host.takeIf { it == "cn.pornhub.com" } ?: "www.pornhub.com"
        return "https://$host/webmasters/video_by_id".toHttpUrl().newBuilder().addQueryParameter("id",id).build()
    }
    fun prepareConnection(url: String) {
        val endpoint = endpoint(url) ?: return
        DiscoveryPageHttp.prepareRoot("https://${endpoint.host}/", PornhubPageVideo.USER_AGENT)
    }
    fun read(url: String): YtDlpMediaInfo? {
        val endpoint = endpoint(url) ?: return null
        return DiscoveryPageHttp.read(Request.Builder().url(endpoint).header("User-Agent",PornhubPageVideo.USER_AGENT)
            .header("Accept","application/json").build(), "pornhub_api", 256 * 1024) { parse(it,url) }
    }
    fun parse(body: String, url: String): YtDlpMediaInfo? = runCatching {
        val id = PornhubPageVideo.workId(url) ?: return null
        if (body.toByteArray().size > 256 * 1024) return null
        val root = JSONObject(body)
        if (root.has("error") || root.has("errors")) return null
        val video = root.optJSONObject("video") ?: return null
        if (video.optString("video_id") != id || PornhubPageVideo.workId(video.optString("url")) != id) return null
        if (listOf("is_private", "is_deleted", "is_live", "drm").any { video.optBoolean(it) || video.optInt(it) != 0 }) return null
        val duration = video.optString("duration")
        if (!Regex("[0-9]{1,4}:[0-9]{2}(?::[0-9]{2})?").matches(duration) || duration.filter(Char::isDigit).all { it == '0' }) return null
        val title = PornhubPageVideo.decode(video.optString("title")).takeIf(String::isNotBlank) ?: return null
        val date = runCatching { LocalDate.parse(video.optString("publish_date").take(10)).format(DateTimeFormatter.BASIC_ISO_DATE) }.getOrDefault("")
        // The API supplies no uploader identity. Keep the publisher label; never infer it from performers/tags.
        YtDlpMediaInfo(platform="pornhub", id=id, title=title, creator="Pornhub", creatorId="", webpageUrl=url,
            uploadDate=date, thumbnail=video.optString("default_thumb"), videoUrl="", audioUrl=null,
            videoExt="mp4", audioExt=null, headers=emptyMap())
    }.getOrNull()
}
