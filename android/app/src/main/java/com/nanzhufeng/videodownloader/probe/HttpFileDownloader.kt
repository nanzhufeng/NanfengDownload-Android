package com.nanzhufeng.videodownloader.probe

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

data class DirectDownloadRequest(
    val url: String,
    val headers: Map<String, String>,
    val target: File,
)

class HttpFileDownloader {
    fun download(
        request: DirectDownloadRequest,
        cancelled: AtomicBoolean,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): File {
        val partial = File(request.target.parentFile, request.target.name + ".part")
        partial.parentFile?.mkdirs()
        val existing = partial.takeIf(File::exists)?.length() ?: 0L
        val connection = URL(request.url).openConnection() as HttpURLConnection
        request.headers.forEach(connection::setRequestProperty)
        if (existing > 0L) {
            connection.setRequestProperty("Range", "bytes=$existing-")
        }
        connection.connectTimeout = 20_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true

        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IOException("下载请求失败：HTTP $responseCode")
            }
            val append = existing > 0L && responseCode == HttpURLConnection.HTTP_PARTIAL
            val start = if (append) existing else 0L
            if (!append && partial.exists()) {
                partial.delete()
            }
            val bodyLength = connection.contentLengthLong.coerceAtLeast(0L)
            val total = if (bodyLength > 0L) start + bodyLength else 0L
            var downloaded = start

            connection.inputStream.use { input ->
                FileOutputStream(partial, append).buffered().use { output ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        if (cancelled.get()) {
                            throw CancellationException("下载已取消")
                        }
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        onProgress(downloaded, total)
                    }
                }
            }

            if (!partial.renameTo(request.target)) {
                partial.copyTo(request.target, overwrite = true)
                partial.delete()
            }
            return request.target
        } finally {
            connection.disconnect()
        }
    }
}

object MediaFileValidator {
    fun isLikelyMedia(file: File): Boolean {
        if (!file.isFile || file.length() < 64 * 1024) return false
        val header = ByteArray(64)
        val count = file.inputStream().use { it.read(header) }
        if (count <= 0) return false
        val text = header.copyOf(count).toString(Charsets.ISO_8859_1)
        return "ftyp" in text || "webm" in text || text.startsWith("ID3")
    }
}
