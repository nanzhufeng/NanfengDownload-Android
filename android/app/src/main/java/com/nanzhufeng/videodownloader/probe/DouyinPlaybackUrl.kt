package com.nanzhufeng.videodownloader.probe

import java.net.URI
import java.net.URLDecoder

object DouyinPlaybackUrl {
    fun normalize(value: String): String {
        val uri = runCatching { URI(value) }.getOrNull() ?: return value
        if (uri.scheme != "https" || uri.userInfo != null || uri.port !in setOf(-1, 443) ||
            uri.host !in setOf("aweme.snssdk.com", "www.iesdouyin.com", "www.douyin.com") ||
            uri.rawPath != "/aweme/v1/playwm/") return value
        val ids = uri.rawQuery.orEmpty().split('&').filter { it.substringBefore('=') == "video_id" }
        if (ids.size != 1) return value
        val id = runCatching { URLDecoder.decode(ids.single().substringAfter('=', ""), "UTF-8") }.getOrNull() ?: return value
        if (!id.matches(Regex("[A-Za-z0-9_-]{1,200}"))) return value
        return value.replaceFirst("/aweme/v1/playwm/", "/aweme/v1/play/")
    }
}
