package com.nanzhufeng.videodownloader.domain.download

import com.nanzhufeng.videodownloader.probe.DirectDownloadRequest
import com.nanzhufeng.videodownloader.probe.headersFor
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

fun interface MediaLengthProbe {
    fun contentLength(request: DirectDownloadRequest): Long
}

/** A one-byte range request is accepted by the same signed media endpoints as the download. */
class HttpMediaLengthProbe(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS)
        .build(),
) : MediaLengthProbe {
    override fun contentLength(request: DirectDownloadRequest): Long = runCatching {
        val url = request.url.toHttpUrlOrNull() ?: return@runCatching 0L
        val httpRequest = Request.Builder()
            .url(url)
            .apply {
                request.headersFor(url).forEach { (name, value) -> header(name, value) }
            }
            .header("Range", "bytes=0-0")
            .get()
            .build()
        client.newCall(httpRequest).execute().use { response ->
            if (!response.isSuccessful) return@use 0L
            response.header("Content-Range")
                ?.substringAfterLast('/', "")
                ?.toLongOrNull()
                ?.takeIf { it > 0L }
                ?: response.body?.contentLength()?.takeIf { it > 0L }
                ?: 0L
        }
    }.getOrDefault(0L)
}
