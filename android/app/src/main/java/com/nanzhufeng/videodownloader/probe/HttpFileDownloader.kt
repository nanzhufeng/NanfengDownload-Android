package com.nanzhufeng.videodownloader.probe

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

data class DirectDownloadRequest(
    val url: String,
    val headers: Map<String, String>,
    val target: File,
)

class HttpFileDownloader(
    private val maxAttempts: Int = 3,
    private val retryDelayMillis: Long = 400L,
) {
    fun download(
        request: DirectDownloadRequest,
        cancelled: AtomicBoolean,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): File {
        val partial = File(request.target.parentFile, request.target.name + ".part")
        partial.parentFile?.mkdirs()
        var lastError: IOException? = null

        repeat(maxAttempts) { attempt ->
            if (cancelled.get()) throw CancellationException("下载已取消")
            try {
                downloadAttempt(request, partial, cancelled, onProgress)
                publish(partial, request.target)
                return request.target
            } catch (error: CancellationException) {
                throw error
            } catch (error: HttpStatusException) {
                throw error
            } catch (error: IOException) {
                lastError = error
                if (attempt == maxAttempts - 1) throw error
                if (retryDelayMillis > 0L) {
                    Thread.sleep(retryDelayMillis * (attempt + 1))
                }
            }
        }
        throw lastError ?: IOException("下载失败，且没有错误详情")
    }

    private fun downloadAttempt(
        request: DirectDownloadRequest,
        partial: File,
        cancelled: AtomicBoolean,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ) {
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
                throw HttpStatusException(responseCode)
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
            if (total > 0L && downloaded < total) {
                throw IOException("下载连接提前结束：$downloaded/$total 字节")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun publish(partial: File, target: File) {
        if (!partial.renameTo(target)) {
            partial.copyTo(target, overwrite = true)
            partial.delete()
        }
    }

    private class HttpStatusException(code: Int) : IOException("下载请求失败：HTTP $code")
}

object MediaFileValidator {
    fun isLikelyMedia(file: File): Boolean {
        if (!file.isFile) return false
        return file.inputStream().use { input ->
            isLikelyMedia(input, file.length())
        }
    }

    fun isLikelyMedia(input: InputStream, length: Long): Boolean {
        if (length < 64 * 1024) return false
        val header = ByteArray(64)
        val count = input.read(header)
        if (count <= 0) return false
        val text = header.copyOf(count).toString(Charsets.ISO_8859_1)
        return "ftyp" in text || "webm" in text || text.startsWith("ID3")
    }
}
