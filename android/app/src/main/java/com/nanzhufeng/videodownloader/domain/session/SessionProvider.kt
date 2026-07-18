package com.nanzhufeng.videodownloader.domain.session

import java.net.URI
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class SessionSite(
    val label: String,
    val loginUrl: String,
    val cookieProbeUrl: String,
    val cookieDomain: String,
    val cookieScopeUrls: List<String>,
    val authenticationCookieNames: Set<String>,
) {
    DOUYIN(
        "抖音",
        "https://sso.douyin.com/login/?service=https%3A%2F%2Fwww.douyin.com%2F",
        "https://www.douyin.com/",
        ".douyin.com",
        listOf(
            "https://v.douyin.com/",
            "https://www.douyin.com/",
            "https://www.iesdouyin.com/",
            "https://passport.douyin.com/",
            "https://sso.douyin.com/",
        ),
        setOf("sessionid", "sessionid_ss", "sid_guard", "sid_tt", "uid_tt", "uid_tt_ss"),
    ),
    YOUTUBE(
        "YouTube",
        "https://www.youtube.com/",
        "https://www.youtube.com/",
        ".youtube.com",
        listOf("https://www.youtube.com/"),
        emptySet(),
    ),
    TIKTOK(
        "TikTok",
        "https://www.tiktok.com/login",
        "https://www.tiktok.com/",
        ".tiktok.com",
        listOf(
            "https://www.tiktok.com/",
            "https://www.tiktok.com/login",
            "https://m.tiktok.com/",
        ),
        setOf("sessionid", "sessionid_ss", "sid_guard", "sid_tt", "uid_tt", "uid_tt_ss"),
    ),
    ;

    companion object {
        fun fromUrl(url: String): SessionSite? {
            val host = runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")
            return when {
                host.matchesDomain("douyin.com") -> DOUYIN
                host.matchesDomain("youtube.com") || host.matchesDomain("youtu.be") -> YOUTUBE
                host.matchesDomain("tiktok.com") -> TIKTOK
                else -> null
            }
        }

        private fun String.matchesDomain(domain: String): Boolean =
            this == domain || endsWith(".$domain")
    }
}

data class SessionAccess(
    val cookieHeader: String = "",
    val cookieFilePath: String? = null,
)

data class SiteSessionState(
    val site: SessionSite,
    val hasSavedSession: Boolean,
    val summary: String,
    val isAuthenticated: Boolean = false,
)

interface SessionProvider {
    val states: StateFlow<List<SiteSessionState>>

    fun accessFor(url: String): SessionAccess

    fun openLogin(site: SessionSite)

    suspend fun importYoutubeCookies(sourceUri: String): Result<Unit>

    suspend fun exportCookies(destinationUri: String): Result<Int>

    suspend fun clear(site: SessionSite): Result<Unit>

    fun refresh()
}

object NoOpSessionProvider : SessionProvider {
    override val states: StateFlow<List<SiteSessionState>> = MutableStateFlow(
        SessionSite.entries.map { site -> SiteSessionState(site, false, "未保存会话") },
    )

    override fun accessFor(url: String) = SessionAccess()
    override fun openLogin(site: SessionSite) = Unit
    override suspend fun importYoutubeCookies(sourceUri: String) = Result.failure<Unit>(
        IllegalStateException("当前环境不支持导入登录信息"),
    )
    override suspend fun exportCookies(destinationUri: String) = Result.failure<Int>(
        IllegalStateException("当前环境不支持导出登录信息"),
    )
    override suspend fun clear(site: SessionSite) = Result.failure<Unit>(
        IllegalStateException("当前环境不支持清除登录信息"),
    )
    override fun refresh() = Unit
}

internal fun cookieDeletionDomains(site: SessionSite): Set<String> = buildSet {
    add(site.cookieDomain)
    site.cookieScopeUrls.forEach { url ->
        val host = runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")
        if (host.isNotBlank()) {
            add(host)
            add(".$host")
        }
    }
}

internal fun cookieAssignments(header: String): List<String> = header
    .split(';')
    .map(String::trim)
    .filter { cookie -> cookie.indexOf('=') > 0 }

class SessionAccessPolicy(
    private val cookieLookup: (SessionSite, String) -> String,
    private val youtubeCookieFile: () -> String?,
) {
    fun accessFor(url: String): SessionAccess = when (SessionSite.fromUrl(url)) {
        SessionSite.DOUYIN -> SessionAccess(cookieHeader = cookieLookup(SessionSite.DOUYIN, url))
        SessionSite.TIKTOK -> SessionAccess(cookieHeader = cookieLookup(SessionSite.TIKTOK, url))
        SessionSite.YOUTUBE -> SessionAccess(cookieFilePath = youtubeCookieFile())
        null -> SessionAccess()
    }
}

internal fun classifyAuthenticatedSession(site: SessionSite, cookieHeader: String): Boolean {
    if (site.authenticationCookieNames.isEmpty()) return false
    return cookieNames(cookieHeader).any(site.authenticationCookieNames::contains)
}

internal fun mergeCookieHeaders(headers: List<String>): String {
    val cookies = linkedMapOf<String, String>()
    headers.forEach { header ->
        header.split(';').forEach { rawCookie ->
            val cookie = rawCookie.trim()
            val separator = cookie.indexOf('=')
            if (separator <= 0) return@forEach
            val name = cookie.substring(0, separator).trim()
            if (name.isBlank() || name in cookies) return@forEach
            cookies[name] = cookie.substring(separator + 1)
        }
    }
    return cookies.entries.joinToString("; ") { (name, value) -> "$name=$value" }
}

internal fun cookieNames(header: String): Set<String> = header
    .split(';')
    .mapNotNull { cookie ->
        cookie.substringBefore('=').trim().lowercase().takeIf(String::isNotBlank)
    }
    .toSet()
