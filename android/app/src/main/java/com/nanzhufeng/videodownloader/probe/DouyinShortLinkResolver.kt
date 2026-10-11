package com.nanzhufeng.videodownloader.probe

import java.net.URI
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

data class ShortLinkResponse(val status: Int, val location: String?)

/** Resolve public share identity from headers; never fetch the final work's HTML here. */
class DouyinShortLinkResolver(
    private val request: (String) -> ShortLinkResponse = { url ->
        client.newCall(Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 15; Mobile)").get().build())
            .execute().use { ShortLinkResponse(it.code, it.header("Location")) }
    },
) : ShortLinkResolver {
    override fun resolve(url: String): String {
        val source = URI(url)
        if (!source.scheme.equals("https", true) || !source.host.equals("v.douyin.com", true)) return url
        var current = source
        repeat(3) {
            check(!Thread.currentThread().isInterrupted) { "已取消抖音短链读取" }
            require(isOfficial(current)) { "抖音短链接跳转到了非官方安全地址，已拒绝读取" }
            val work = Regex("^/(?:share/)?(video|note)/(\\d+)/?$").matchEntire(current.path.orEmpty())
            if (work != null) return "https://www.douyin.com/${work.groupValues[1]}/${work.groupValues[2]}"
            val response = request(current.toString())
            check(!Thread.currentThread().isInterrupted) { "已取消抖音短链读取" }
            if (response.status in 200..299) return current.toString()
            require(response.status in setOf(301, 302, 303, 307, 308) && !response.location.isNullOrBlank()) {
                "抖音短链接未返回有效目标地址（HTTP ${response.status}）"
            }
            current = current.resolve(response.location)
        }
        require(isOfficial(current)) { "抖音短链接跳转到了非官方安全地址，已拒绝读取" }
        val work = Regex("^/(?:share/)?(video|note)/(\\d+)/?$").matchEntire(current.path.orEmpty())
        if (work != null) return "https://www.douyin.com/${work.groupValues[1]}/${work.groupValues[2]}"
        error("抖音短链接跳转次数过多，请重新复制作品链接")
    }

    private fun isOfficial(uri: URI): Boolean {
        val host = uri.host.orEmpty().lowercase()
        return uri.scheme.equals("https", true) && uri.userInfo == null && uri.port in setOf(-1, 443) &&
            (host == "douyin.com" || host.endsWith(".douyin.com") ||
                host == "iesdouyin.com" || host.endsWith(".iesdouyin.com"))
    }

    companion object {
        private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false).connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS).callTimeout(6, TimeUnit.SECONDS).build()

        fun prepareConnection() {
            client.newCall(Request.Builder().url("https://v.douyin.com/").head().build())
                .enqueue(object : okhttp3.Callback {
                    override fun onFailure(call: okhttp3.Call, e: java.io.IOException) = Unit
                    override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) { response.close() }
                })
        }
    }
}
