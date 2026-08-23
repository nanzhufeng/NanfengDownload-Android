package com.nanzhufeng.videodownloader.probe

import com.chaquo.python.Python
import com.nanzhufeng.videodownloader.domain.session.SessionAccess
import org.json.JSONObject

data class RuntimeInfo(
    val python: String,
    val ytDlp: String,
)

data class YtDlpImageItem(
    val url: String,
    val motionUrl: String? = null,
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
    val videoSizeBytes: Long = 0L,
    val audioExt: String?,
    val headers: Map<String, String>,
    val videoCookieHeader: String = "",
    val audioCookieHeader: String = "",
    val audioFromVideoSource: Boolean = false,
    val imageUrls: List<String> = emptyList(),
    val imageItems: List<YtDlpImageItem> = emptyList(),
)

data class ResolvedSource(
    val kind: SourceKind,
    val url: String,
)

data class CreatorVideoEntry(
    val id: String,
    val title: String,
    val creator: String,
    val creatorId: String,
    val webpageUrl: String,
    val uploadDate: String,
    val thumbnail: String,
    val selected: Boolean = true,
)

data class CreatorCatalog(
    val creator: String,
    val creatorId: String,
    val entries: List<CreatorVideoEntry>,
    val duplicateCount: Int,
    val foreignCount: Int,
    val hasMore: Boolean,
    val nextStart: Int,
) {
    fun selectedEntries(): List<CreatorVideoEntry> = entries.filter(CreatorVideoEntry::selected)

    fun append(page: CreatorCatalog): CreatorCatalog {
        require(creatorId.isBlank() || page.creatorId.isBlank() || creatorId == page.creatorId) {
            "TikTok 作者分页身份不一致"
        }
        val combined = entries + page.entries
        val unique = combined.distinctBy(CreatorVideoEntry::id)
        return copy(
            entries = unique,
            duplicateCount = duplicateCount + page.duplicateCount + (combined.size - unique.size),
            foreignCount = foreignCount + page.foreignCount,
            hasMore = page.hasMore,
            nextStart = page.nextStart,
        )
    }
}

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

    fun resolveSource(url: String, access: SessionAccess = SessionAccess()): ResolvedSource {
        val json = JSONObject(
            module.callAttr(
                "resolve_source",
                url,
                access.cookieHeader,
                access.cookieFilePath.orEmpty(),
            ).toString(),
        )
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

    fun extractSingle(
        url: String,
        resolution: com.nanzhufeng.videodownloader.core.model.ResolutionPreset =
            com.nanzhufeng.videodownloader.core.model.ResolutionPreset.UP_TO_720P,
        access: SessionAccess = SessionAccess(),
    ): YtDlpMediaInfo {
        val json = JSONObject(
            module.callAttr(
                "extract_single",
                url,
                resolution.name,
                access.cookieHeader,
                access.cookieFilePath.orEmpty(),
            ).toString(),
        )
        val headersJson = json.getJSONObject("headers")
        val headers = headersJson.keys().asSequence().associateWith(headersJson::getString)
        val imageUrls = json.optJSONArray("image_urls")?.let { images ->
            buildList {
                for (index in 0 until images.length()) {
                    images.optString(index).takeIf(String::isNotBlank)?.let(::add)
                }
            }
        }.orEmpty()
        val imageItems = json.optJSONArray("image_items")?.let { images ->
            buildList {
                for (index in 0 until images.length()) {
                    val image = images.optJSONObject(index) ?: continue
                    val url = image.optString("url").takeIf(String::isNotBlank) ?: continue
                    add(YtDlpImageItem(url, image.optString("motion_url").takeIf(String::isNotBlank)))
                }
            }
        }.orEmpty()
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
            videoSizeBytes = json.optLong("video_size_bytes", 0L).coerceAtLeast(0L),
            audioExt = json.getString("audio_ext").ifBlank { null },
            videoCookieHeader = json.optString("video_cookie_header"),
            audioCookieHeader = json.optString("audio_cookie_header"),
            audioFromVideoSource = json.optBoolean("audio_from_video_source", false),
            imageUrls = imageUrls,
            imageItems = imageItems,
            headers = headers,
        )
    }

    fun extractCreator(
        url: String,
        start: Int = 1,
        pageSize: Int = 50,
        access: SessionAccess = SessionAccess(),
    ): CreatorCatalog {
        val json = JSONObject(
            module.callAttr(
                "extract_creator",
                url,
                start,
                pageSize,
                access.cookieHeader,
                access.cookieFilePath.orEmpty(),
            ).toString(),
        )
        val items = json.getJSONArray("entries")
        val entries = buildList {
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                val webpageUrl = item.getString("webpage_url")
                if (webpageUrl.isBlank()) continue
                add(
                    CreatorVideoEntry(
                        id = item.getString("id"),
                        title = item.getString("title"),
                        creator = item.getString("creator"),
                        creatorId = item.getString("creator_id"),
                        webpageUrl = webpageUrl,
                        uploadDate = item.getString("upload_date"),
                        thumbnail = item.getString("thumbnail"),
                    ),
                )
            }
        }
        return CreatorCatalog(
            creator = json.getString("creator"),
            creatorId = json.getString("creator_id"),
            entries = entries,
            duplicateCount = json.getInt("duplicate_count"),
            foreignCount = json.getInt("foreign_count"),
            hasMore = json.getBoolean("has_more"),
            nextStart = json.getInt("next_start"),
        )
    }
}
