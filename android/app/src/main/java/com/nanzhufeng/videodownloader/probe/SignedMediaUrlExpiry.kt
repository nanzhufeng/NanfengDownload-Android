package com.nanzhufeng.videodownloader.probe

import java.net.URI
import java.net.URLDecoder

object SignedMediaUrlExpiry {
    fun isFresh(url: String, nowMillis: Long): Boolean {
        val query = runCatching { URI(url).rawQuery }.getOrNull() ?: return true
        return query.split('&').all { pair ->
            val parts = pair.split('=', limit = 2)
            val name = runCatching { URLDecoder.decode(parts[0], "UTF-8").lowercase() }.getOrNull()
            if (name !in setOf("expire", "expires", "x-expires", "x_expire")) return@all true
            val value = runCatching { URLDecoder.decode(parts.getOrElse(1) { "" }, "UTF-8").toLong() }.getOrNull()
                ?: return@all false
            val expiresMillis = if (value in 1..999_999_999_999L) value * 1000 else value
            expiresMillis > nowMillis
        }
    }
}
