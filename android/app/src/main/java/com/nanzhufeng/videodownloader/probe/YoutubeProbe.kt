package com.nanzhufeng.videodownloader.probe

import com.chaquo.python.Python
import org.json.JSONObject

data class RuntimeInfo(
    val python: String,
    val ytDlp: String,
)

data class YoutubeMediaInfo(
    val id: String,
    val title: String,
    val creator: String,
    val videoUrl: String,
    val audioUrl: String?,
    val headers: Map<String, String>,
)

class YoutubeProbe {
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

    fun extractSingle(url: String): YoutubeMediaInfo {
        val json = JSONObject(module.callAttr("extract_single", url).toString())
        val headersJson = json.getJSONObject("headers")
        val headers = headersJson.keys().asSequence().associateWith(headersJson::getString)
        return YoutubeMediaInfo(
            id = json.getString("id"),
            title = json.getString("title"),
            creator = json.getString("creator"),
            videoUrl = json.getString("video_url"),
            audioUrl = json.getString("audio_url").ifBlank { null },
            headers = headers,
        )
    }
}
