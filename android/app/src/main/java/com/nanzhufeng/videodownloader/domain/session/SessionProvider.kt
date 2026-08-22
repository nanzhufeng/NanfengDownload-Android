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
        // This is Douyin's actual login-page entry. The SSO root with a www root
        // service target redirects mobile WebViews to the desktop-client campaign.
        "https://www.douyin.com/login_page?service=https%3A%2F%2Fwww.douyin.com%2Fhome",
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
    BILIBILI(
        "哔哩哔哩",
        "https://passport.bilibili.com/h5-app/passport/login",
        "https://www.bilibili.com/",
        ".bilibili.com",
        listOf(
            "https://www.bilibili.com/",
            "https://api.bilibili.com/",
            "https://passport.bilibili.com/",
            "https://space.bilibili.com/",
            "https://b23.tv/",
        ),
        setOf("sessdata"),
    ),
    TIKTOK(
        "TikTok",
        "https://m.tiktok.com/login/phone-or-email",
        "https://www.tiktok.com/",
        ".tiktok.com",
        listOf(
            "https://www.tiktok.com/",
            "https://www.tiktok.com/login",
            "https://m.tiktok.com/",
        ),
        setOf("sessionid", "sessionid_ss", "sid_guard", "sid_tt", "uid_tt", "uid_tt_ss"),
    ),
    XIAOHONGSHU(
        "小红书",
        "https://www.xiaohongshu.com/explore",
        "https://www.xiaohongshu.com/explore",
        ".xiaohongshu.com",
        listOf(
            "https://www.xiaohongshu.com/",
            "https://edith.xiaohongshu.com/",
            "https://www.rednote.com/",
            "https://xhslink.com/",
            "https://xhslink.cn/",
        ),
        setOf("web_session"),
    ),
    ;

    companion object {
        fun fromUrl(url: String): SessionSite? {
            val host = runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")
            return when {
                host.matchesDomain("douyin.com") -> DOUYIN
                host.matchesDomain("youtube.com") || host.matchesDomain("youtu.be") -> YOUTUBE
                host.matchesDomain("tiktok.com") -> TIKTOK
                host.matchesDomain("bilibili.com") || host.matchesDomain("b23.tv") -> BILIBILI
                host.matchesDomain("xiaohongshu.com") ||
                    host.matchesDomain("rednote.com") ||
                    host.matchesDomain("xhslink.com") ||
                    host.matchesDomain("xhslink.cn") -> XIAOHONGSHU
                else -> null
            }
        }

        private fun String.matchesDomain(domain: String): Boolean =
            this == domain || endsWith(".$domain")
    }

    fun isTrustedLoginUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        val host = uri.host.orEmpty().lowercase()
        return when (this) {
            DOUYIN -> host.matchesDomain("douyin.com") || host.matchesDomain("iesdouyin.com")
            YOUTUBE -> host.matchesDomain("youtube.com") || host.matchesDomain("google.com")
            BILIBILI -> host.matchesDomain("bilibili.com") || host == "b23.tv"
            TIKTOK -> host.matchesDomain("tiktok.com") || host.matchesDomain("google.com")
            XIAOHONGSHU -> host.matchesDomain("xiaohongshu.com") || host.matchesDomain("rednote.com")
        }
    }

    fun ownsLoginPage(url: String): Boolean {
        val host = runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")
        return when (this) {
            DOUYIN -> host.matchesDomain("douyin.com")
            YOUTUBE -> host.matchesDomain("youtube.com")
            BILIBILI -> host.matchesDomain("bilibili.com")
            TIKTOK -> host.matchesDomain("tiktok.com")
            XIAOHONGSHU -> host.matchesDomain("xiaohongshu.com") || host.matchesDomain("rednote.com")
        }
    }

    val requiresThirdPartyCookies: Boolean
        get() = this == TIKTOK
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

    suspend fun exportCookies(site: SessionSite, destinationUri: String): Result<Int>

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
    override suspend fun exportCookies(site: SessionSite, destinationUri: String) = Result.failure<Int>(
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
    private val sessionCookieFile: (SessionSite, String) -> String? = { _, _ -> null },
) {
    fun accessFor(url: String): SessionAccess = when (SessionSite.fromUrl(url)) {
        SessionSite.DOUYIN -> scopedAccess(SessionSite.DOUYIN, url)
        SessionSite.TIKTOK -> scopedAccess(SessionSite.TIKTOK, url)
        SessionSite.BILIBILI -> scopedAccess(SessionSite.BILIBILI, url)
        SessionSite.XIAOHONGSHU -> scopedAccess(SessionSite.XIAOHONGSHU, url)
        SessionSite.YOUTUBE -> SessionAccess(cookieFilePath = youtubeCookieFile())
        null -> SessionAccess()
    }

    private fun scopedAccess(site: SessionSite, url: String): SessionAccess {
        val cookies = cookieLookup(site, url)
        return SessionAccess(
            cookieHeader = cookies,
            cookieFilePath = sessionCookieFile(site, cookies),
        )
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
