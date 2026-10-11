package com.nanzhufeng.videodownloader.probe

import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** Callers constrain official URLs and credentials. No redirects or cross-read metadata cache. */
internal object DiscoveryPageHttp {
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).callTimeout(6, TimeUnit.SECONDS).build()
    private val warmed = LinkedHashMap<String, Long>()
    private val watcher = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "nanfeng-discovery-cancel").apply { isDaemon = true }
    }

    fun prepareRoot(root: String, userAgent: String) {
        val now = android.os.SystemClock.elapsedRealtime()
        synchronized(warmed) {
            val before = warmed[root]
            if (before != null && now - before < 60_000) return
            warmed[root] = now
            if (warmed.size > 16) warmed.remove(warmed.keys.first())
        }
        client.newBuilder().callTimeout(1, TimeUnit.SECONDS).build()
            .newCall(Request.Builder().url(root).header("User-Agent", userAgent).head().build())
            .enqueue(object : Callback {
                override fun onFailure(call: Call, e: java.io.IOException) = Unit
                override fun onResponse(call: Call, response: Response) { response.close() }
            })
    }

    fun <T> read(request: Request, platform: String, maxBytes: Int, parse: (String) -> T?): T? {
        val thread = Thread.currentThread()
        if (thread.isInterrupted) throw CancellationException()
        val start = System.nanoTime()
        val call = client.newCall(request)
        val guard = watcher.scheduleWithFixedDelay({ if (thread.isInterrupted) call.cancel() }, 0, 50, TimeUnit.MILLISECONDS)
        try {
            return call.execute().use { response ->
                if (response.code != 200) {
                    android.util.Log.i("ReadPerformance", "platform=$platform stage=page_status code=${response.code} elapsedMs=${(System.nanoTime()-start)/1_000_000}")
                    return@use null
                }
                val stream = response.body?.byteStream() ?: return@use null
                val buffer = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                var next = 8192
                while (buffer.size() <= maxBytes) {
                    if (thread.isInterrupted) throw CancellationException()
                    val size = stream.read(chunk, 0, minOf(chunk.size, maxBytes + 1 - buffer.size()))
                    if (size < 0) break
                    buffer.write(chunk, 0, size)
                    if (buffer.size() > maxBytes) return@use null
                    if (buffer.size() >= next) {
                        parse(buffer.toString("UTF-8"))?.let {
                            android.util.Log.i("ReadPerformance", "platform=$platform stage=target_page bytes=${buffer.size()} elapsedMs=${(System.nanoTime()-start)/1_000_000}")
                            return@use it
                        }
                        next = buffer.size() + 32 * 1024
                    }
                }
                parse(buffer.toString("UTF-8")).also {
                    if (it != null) android.util.Log.i("ReadPerformance", "platform=$platform stage=target_page bytes=${buffer.size()} elapsedMs=${(System.nanoTime()-start)/1_000_000}")
                }
            }
        } catch (error: Exception) {
            if (thread.isInterrupted || error is CancellationException) throw CancellationException()
            android.util.Log.i("ReadPerformance", "platform=$platform stage=page_error type=${error.javaClass.simpleName} elapsedMs=${(System.nanoTime()-start)/1_000_000}")
            return null
        } finally { guard.cancel(false) }
    }

    /** Extract complete JSON without regex recursion; reject excessive depth and partial prefixes. */
    fun objectAfter(html: String, marker: String, maxChars: Int = 512 * 1024): String? {
        val at = html.indexOf(marker)
        if (at < 0) return null
        var start = at + marker.length
        while (start < html.length && html[start].isWhitespace()) start++
        if (start >= html.length || html[start] != '{') return null
        var quoted = false
        var escaped = false
        var depth = 0
        for (index in start until minOf(html.length, start + maxChars)) {
            val char = html[index]
            if (quoted) {
                if (escaped) escaped = false else if (char == '\\') escaped = true else if (char == '"') quoted = false
            } else when (char) {
                '"' -> quoted = true
                '{', '[' -> { depth++; if (depth > 64) return null }
                '}', ']' -> { depth--; if (depth == 0) return html.substring(start, index + 1) }
            }
        }
        return null
    }
}
