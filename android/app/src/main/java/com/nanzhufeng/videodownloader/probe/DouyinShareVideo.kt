package com.nanzhufeng.videodownloader.probe

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Only the requested work's declared playback address may bypass the browser. */
object DouyinShareVideo {
    data class Result(val mediaUrl: String, val title: String, val creator: String, val thumbnail: String)

    fun parse(html: String, workId: String, nowMillis: Long): Result? {
        if (html.length > MAX_BYTES || !workId.matches(Regex("\\d+"))) return null
        val start = Regex("window\\._ROUTER_DATA\\s*=\\s*").find(html)?.range?.last?.plus(1) ?: return null
        val root = runCatching { JSONTokener(html.substring(start)).nextValue() as? JSONObject }.getOrNull() ?: return null
        if (root.has("status_code") && root.optInt("status_code", -1) != 0) return null
        var budget = 2000
        fun walk(node: Any?, depth: Int): Result? {
            if (depth > 12 || budget-- <= 0) return null
            if (node is JSONObject) {
                if (node.optString("aweme_id", node.optString("awemeId")) == workId) {
                    val video = node.optJSONObject("video")
                    val urls = video?.optJSONObject("play_addr")?.optJSONArray("url_list")
                        ?: video?.optJSONObject("playAddr")?.optJSONArray("urlList")
                    if (urls != null) for (i in 0 until urls.length()) {
                        val source = urls.optString(i)
                        if (DouyinCaptureStore.isMediaUrl(source) && SignedMediaUrlExpiry.isFresh(source, nowMillis)) {
                            return Result(source, node.optString("desc"), node.optJSONObject("author")?.optString("nickname").orEmpty(),
                                video.optJSONObject("cover")?.optJSONArray("url_list")?.optString(0).orEmpty())
                        }
                    }
                }
                val keys = node.keys()
                while (keys.hasNext()) walk(node.opt(keys.next()), depth + 1)?.let { return it }
            } else if (node is JSONArray) for (i in 0 until node.length()) walk(node.opt(i), depth + 1)?.let { return it }
            return null
        }
        return walk(root, 0)
    }

    const val MAX_BYTES = 512_000
}
