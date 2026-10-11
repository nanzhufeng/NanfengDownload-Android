package com.nanzhufeng.videodownloader.probe

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

object DouyinShareVideoClient {
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).callTimeout(2, TimeUnit.SECONDS).build()

    fun prepareConnection(userAgent: String) {
        client.newCall(Request.Builder().url("https://www.iesdouyin.com/")
            .header("User-Agent", userAgent).head().build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = Unit
            override fun onResponse(call: Call, response: Response) { response.close() }
        })
    }

    fun read(pageUrl: String, userAgent: String, cookie: String, done: (DouyinShareVideo.Result?) -> Unit): Call {
        val id = requireNotNull(DouyinCaptureStore.extractWorkId(pageUrl))
        require(pageUrl == "https://www.iesdouyin.com/share/video/$id/")
        val request = Request.Builder().url(pageUrl).header("User-Agent", userAgent)
            .header("Referer", "https://www.douyin.com/").header("Accept", "text/html")
            .apply { if (cookie.isNotBlank()) header("Cookie", cookie) }.build()
        return client.newCall(request).also { call ->
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (!call.isCanceled()) done(null) }
                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use {
                            if (it.code != 200) return@use null
                            val stream = it.body?.byteStream() ?: return@use null
                            val buffer = java.io.ByteArrayOutputStream()
                            val chunk = ByteArray(8192)
                            while (buffer.size() <= DouyinShareVideo.MAX_BYTES) {
                                val n = stream.read(chunk, 0, minOf(chunk.size, DouyinShareVideo.MAX_BYTES + 1 - buffer.size()))
                                if (n < 0) break
                                buffer.write(chunk, 0, n)
                            }
                            if (buffer.size() > DouyinShareVideo.MAX_BYTES) return@use null
                            DouyinShareVideo.parse(buffer.toString("UTF-8"), id, System.currentTimeMillis())
                        }
                    }.getOrNull()
                    if (!call.isCanceled()) done(result)
                }
            })
        }
    }
}
