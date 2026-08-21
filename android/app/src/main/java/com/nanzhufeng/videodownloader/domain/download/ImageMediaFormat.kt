package com.nanzhufeng.videodownloader.domain.download

import java.io.File

/**
 * The resolved image URL is not a reliable source of its file type: several
 * platforms serve GIF/WebP images from extensionless CDN paths. Preserve the
 * downloaded bytes' actual format before publishing them to MediaStore.
 */
internal enum class ImageMediaFormat(
    val extension: String,
    val mimeType: String,
) {
    JPEG("jpg", "image/jpeg"),
    PNG("png", "image/png"),
    GIF("gif", "image/gif"),
    WEBP("webp", "image/webp"),
}

internal fun detectImageMediaFormat(header: ByteArray): ImageMediaFormat? = when {
    header.startsWith(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte())) -> ImageMediaFormat.JPEG
    header.startsWith(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47)) -> ImageMediaFormat.PNG
    header.startsWith("GIF8".toByteArray(Charsets.US_ASCII)) -> ImageMediaFormat.GIF
    header.size >= 12 &&
        header.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF" &&
        header.copyOfRange(8, 12).toString(Charsets.US_ASCII) == "WEBP" -> ImageMediaFormat.WEBP
    else -> null
}

internal fun File.detectImageMediaFormat(): ImageMediaFormat? = inputStream().use { input ->
    val header = ByteArray(32)
    val count = input.read(header)
    if (count <= 0) null else detectImageMediaFormat(header.copyOf(count))
}

internal fun File.withDetectedImageExtension(): File {
    val format = detectImageMediaFormat() ?: return this
    if (extension.equals(format.extension, ignoreCase = true)) return this
    val corrected = File(requireNotNull(parentFile), "$nameWithoutExtension.${format.extension}")
    check(renameTo(corrected)) { "无法按实际图片格式整理下载文件：$name" }
    return corrected
}

private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
    size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
