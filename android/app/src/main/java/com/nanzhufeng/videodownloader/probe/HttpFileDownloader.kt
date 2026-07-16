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
    private const val MIN_MP3_BYTES = 1024L
    private const val MIN_CONTAINER_BYTES = 64L * 1024L

    fun isLikelyMedia(file: File): Boolean {
        if (!file.isFile) return false
        return file.inputStream().use { input ->
            isLikelyMedia(input, file.length())
        }
    }

    fun isLikelyMedia(input: InputStream, length: Long): Boolean {
        val header = ByteArray(64)
        val count = input.read(header)
        if (count <= 0) return false
        val bytes = header.copyOf(count)
        val text = bytes.toString(Charsets.ISO_8859_1)
        val trimmedText = text.trimStart('\u0000', ' ', '\t', '\r', '\n')
        if (
            trimmedText.startsWith("<", ignoreCase = true) ||
            trimmedText.startsWith("{") ||
            trimmedText.startsWith("[")
        ) {
            return false
        }

        val isMp3 = text.startsWith("ID3") || hasMpegAudioFrameSync(bytes)
        if (isMp3) return length >= MIN_MP3_BYTES

        val isIsoBmff = "ftyp" in text
        val isWebM = bytes.startsWith(byteArrayOf(0x1a, 0x45, 0xdf.toByte(), 0xa3.toByte())) ||
            "webm" in text.lowercase()
        return (isIsoBmff || isWebM) && length >= MIN_CONTAINER_BYTES
    }

    private fun hasMpegAudioFrameSync(header: ByteArray): Boolean {
        if (header.size < 2) return false
        val first = header[0].toInt() and 0xff
        val second = header[1].toInt() and 0xff
        val versionBits = (second ushr 3) and 0x03
        val layerBits = (second ushr 1) and 0x03
        return first == 0xff &&
            second and 0xe0 == 0xe0 &&
            versionBits != 0x01 &&
            layerBits != 0x00
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
}
