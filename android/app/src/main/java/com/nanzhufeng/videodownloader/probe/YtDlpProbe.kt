package com.nanzhufeng.videodownloader.probe

import com.chaquo.python.Python
import org.json.JSONObject

data class RuntimeInfo(
    val python: String,
    val ytDlp: String,
)

data class YtDlpMediaInfo(
    val platform: String,
    val id: String,
    val title: String,
    val creator: String,
    val creatorId: String,
    val webpageUrl: String,
    val uploadDate: String,
    val thumbnail: String,
    val videoUrl: String,
    val audioUrl: String?,
    val videoExt: String,
    val audioExt: String?,
    val headers: Map<String, String>,
)

data class ResolvedSource(
    val kind: SourceKind,
    val url: String,
)

class YtDlpProbe {
    private val module by lazy {
        Python.getInstance().getModule("nanzhufeng_probe.youtube_probe")
    }

    fun runtimeInfo(): RuntimeInfo {
        val json = JSONObject(module.callAttr("runtime_info").toString())
        return RuntimeInfo(
            python = json.getString("python"),
            ytDlp = json.getString("yt_dlp"),
        )
    }

    fun resolveSource(url: String): ResolvedSource {
        val json = JSONObject(module.callAttr("resolve_source", url).toString())
        val kind = when (json.getString("kind")) {
            "single" -> SourceKind.SINGLE_VIDEO
            "creator" -> SourceKind.CHANNEL_OR_PLAYLIST
            else -> error("yt-dlp 返回了未知来源类型")
        }
        return ResolvedSource(
            kind = kind,
            url = json.getString("url"),
        )
    }

    fun extractSingle(url: String): YtDlpMediaInfo {
        val json = JSONObject(module.callAttr("extract_single", url).toString())
        val headersJson = json.getJSONObject("headers")
        val headers = headersJson.keys().asSequence().associateWith(headersJson::getString)
        return YtDlpMediaInfo(
            platform = json.getString("platform"),
            id = json.getString("id"),
            title = json.getString("title"),
            creator = json.getString("creator"),
            creatorId = json.getString("creator_id"),
            webpageUrl = json.getString("webpage_url"),
            uploadDate = json.getString("upload_date"),
            thumbnail = json.getString("thumbnail"),
            videoUrl = json.getString("video_url"),
            audioUrl = json.getString("audio_url").ifBlank { null },
            videoExt = json.getString("video_ext"),
            audioExt = json.getString("audio_ext").ifBlank { null },
            headers = headers,
        )
    }
}
