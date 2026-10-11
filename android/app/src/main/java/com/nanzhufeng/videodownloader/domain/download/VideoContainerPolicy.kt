package com.nanzhufeng.videodownloader.domain.download

/** A complete WebM file is valid media; its VP8/Vorbis tracks cannot be relabelled as MP4. */
object VideoContainerPolicy {
    fun requiresDirectMp4Remux(extension: String): Boolean =
        extension.lowercase() !in setOf("mp4", "m4v", "webm")

    fun outputMime(extension: String, transcodedToMp4: Boolean, segmented: Boolean): String {
        val isWebm = extension.equals("webm", ignoreCase = true) && !transcodedToMp4
        require(!isWebm || !segmented) {
            "WebM 原格式暂不支持拆分成多个 MP4；请将同时下载改为 1 个任务"
        }
        return if (isWebm) "video/webm" else "video/mp4"
    }
}
