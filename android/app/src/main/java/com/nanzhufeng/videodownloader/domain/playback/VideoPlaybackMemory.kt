package com.nanzhufeng.videodownloader.domain.playback

import java.security.MessageDigest
import java.util.Locale

interface VideoPositionStorage {
    fun read(key: String): Long
    fun write(key: String, positionMillis: Long, flush: Boolean): Boolean
}

/** Per-output presentation state; task/history/output facts remain in Room. */
class VideoPlaybackMemory(private val storage: VideoPositionStorage) {
    fun positionFor(videoUri: String): Long = runCatching {
        storage.read(key(videoUri)).coerceAtLeast(0L)
    }.getOrDefault(0L)

    fun save(videoUri: String, positionMillis: Long, durationMillis: Long, ended: Boolean, flush: Boolean = false): Boolean {
        val position = when {
            ended -> 0L
            durationMillis > 0L -> positionMillis.coerceIn(0L, durationMillis)
            else -> positionMillis.coerceAtLeast(0L)
        }
        return runCatching { storage.write(key(videoUri), position, flush) }.getOrDefault(false)
    }

    private fun key(videoUri: String): String = MessageDigest.getInstance("SHA-256")
        .digest(videoUri.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 255) }
}
