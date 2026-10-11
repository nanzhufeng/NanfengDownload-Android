package com.nanzhufeng.videodownloader.probe

import android.webkit.CookieManager
import com.nanzhufeng.videodownloader.domain.session.SessionSite
import java.security.MessageDigest

object DouyinCaptureContext {
    fun currentSessionFingerprint(): String = sessionFingerprint(
        SessionSite.DOUYIN.cookieScopeUrls.map { CookieManager.getInstance().getCookie(it).orEmpty() },
    )

    fun sessionFingerprint(headers: List<String>): String {
        val names = SessionSite.DOUYIN.authenticationCookieNames
        val pairs = headers.flatMap { it.split(';') }.mapNotNull {
            val pair = it.trim()
            val separator = pair.indexOf('=')
            if (separator <= 0) return@mapNotNull null
            val name = pair.substring(0, separator).lowercase()
            if (name !in names) null else name + pair.substring(separator)
        }.distinct()
        // sid_guard contains a renewal timestamp/TTL. With a stable session
        // identifier it must not make the same account look like a new login.
        val primary = pairs.filter { it.substringBefore('=') in setOf("sessionid", "sessionid_ss", "sid_tt") }
        val stable = if (primary.isNotEmpty()) primary else pairs.map { pair ->
            if (!pair.startsWith("sid_guard=")) pair else "sid_guard=" +
                runCatching { java.net.URLDecoder.decode(pair.substringAfter('='), "UTF-8").substringBefore('|') }
                    .getOrDefault(pair.substringAfter('='))
        }
        val authentication = stable.sorted().joinToString("\n")
        return MessageDigest.getInstance("SHA-256").digest(authentication.toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    fun isSafeUserAgent(value: String): Boolean =
        value.isNotBlank() && value.length <= 1024 && value.all { it.code in 32..126 }
}
