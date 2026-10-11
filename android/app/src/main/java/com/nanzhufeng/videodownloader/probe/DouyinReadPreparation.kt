package com.nanzhufeng.videodownloader.probe

import android.content.Context
import android.webkit.WebSettings
import java.net.URI
import java.util.concurrent.atomic.AtomicLong

/** Prepare transport, never pre-create a task or substitute cached metadata for a source. */
object DouyinReadPreparation {
    private val preparedAt = AtomicLong(0)

    suspend fun prepare(context: Context, input: String) {
        if (!isEligible(input)) return
        prepareTransport(context)
    }

    suspend fun prepareTransport(context: Context) {
        val now = android.os.SystemClock.elapsedRealtime()
        val before = preparedAt.get()
        if (before != 0L && now - before < 60_000L) return
        if (!preparedAt.compareAndSet(before, now)) return
        val userAgent = DouyinProbeActivity.browserUserAgent(WebSettings.getDefaultUserAgent(context), false)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
            DouyinShortLinkResolver.prepareConnection()
            DouyinShareVideoClient.prepareConnection(userAgent)
        }
    }

    fun isEligible(input: String): Boolean = UrlClassifier.extractUrls(input).any { url ->
        val uri = runCatching { URI(url) }.getOrNull() ?: return@any false
        if (uri.scheme != "https" || uri.userInfo != null || uri.port !in setOf(-1, 443)) return@any false
        (uri.host == "v.douyin.com" && Regex("^/[A-Za-z0-9_-]{2,}/?$").matches(uri.path.orEmpty())) ||
            (DouyinCaptureStore.extractWorkId(url) != null && !uri.path.contains("/note/"))
    }
}
