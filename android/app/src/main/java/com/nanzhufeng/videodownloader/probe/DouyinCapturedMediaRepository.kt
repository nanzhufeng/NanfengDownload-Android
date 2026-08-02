package com.nanzhufeng.videodownloader.probe

import android.content.Context
import org.json.JSONObject

data class DouyinCapturedMedia(
    val workId: String,
    val pageUrl: String,
    val mediaUrl: String,
    val title: String,
    val creator: String,
    val thumbnailUrl: String,
    val capturedAtMillis: Long,
)

fun interface DouyinCapturedMediaSource {
    fun find(workId: String): DouyinCapturedMedia?
}

object NoOpDouyinCapturedMediaSource : DouyinCapturedMediaSource {
    override fun find(workId: String): DouyinCapturedMedia? = null
}

class DouyinCapturedMediaRepository(
    context: Context,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    preferencesName: String = PREFERENCES_NAME,
) : DouyinCapturedMediaSource {
    private val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    fun save(media: DouyinCapturedMedia) {
        require(isValid(media)) { "抖音捕获结果未通过安全校验" }
        preferences.edit().putString(media.workId, media.toJson().toString()).apply()
    }

    override fun find(workId: String): DouyinCapturedMedia? {
        val payload = preferences.getString(workId, null) ?: return null
        val media = runCatching { JSONObject(payload).toCapturedMedia() }.getOrNull()
        if (media == null || !isValid(media) || nowMillis() - media.capturedAtMillis > MAX_CAPTURE_AGE_MILLIS) {
            preferences.edit().remove(workId).apply()
            return null
        }
        return media
    }

    private fun isValid(media: DouyinCapturedMedia): Boolean =
        media.workId.matches(WORK_ID_PATTERN) &&
            DouyinCaptureStore.extractWorkId(media.pageUrl) == media.workId &&
            DouyinCaptureStore.isMediaUrl(media.mediaUrl)

    private fun DouyinCapturedMedia.toJson() = JSONObject()
        .put("work_id", workId)
        .put("page_url", pageUrl)
        .put("media_url", mediaUrl)
        .put("title", title)
        .put("creator", creator)
        .put("thumbnail_url", thumbnailUrl)
        .put("captured_at_millis", capturedAtMillis)

    private fun JSONObject.toCapturedMedia() = DouyinCapturedMedia(
        workId = getString("work_id"),
        pageUrl = getString("page_url"),
        mediaUrl = getString("media_url"),
        title = optString("title"),
        creator = optString("creator"),
        thumbnailUrl = optString("thumbnail_url"),
        capturedAtMillis = getLong("captured_at_millis"),
    )

    private companion object {
        const val PREFERENCES_NAME = "douyin_captured_media"
        const val MAX_CAPTURE_AGE_MILLIS = 6 * 60 * 60 * 1_000L
        val WORK_ID_PATTERN = Regex("\\d{10,24}")
    }
}
