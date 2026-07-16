package com.nanzhufeng.videodownloader.domain.session

import java.net.URI
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class SessionSite(
    val label: String,
    val loginUrl: String,
    val cookieProbeUrl: String,
) {
    DOUYIN("抖音", "https://www.douyin.com/", "https://www.douyin.com/"),
    YOUTUBE("YouTube", "https://www.youtube.com/", "https://www.youtube.com/"),
    TIKTOK("TikTok", "https://www.tiktok.com/login", "https://www.tiktok.com/"),
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
)

interface SessionProvider {
    val states: StateFlow<List<SiteSessionState>>

    fun accessFor(url: String): SessionAccess

    fun openLogin(site: SessionSite)

    suspend fun importYoutubeCookies(sourceUri: String): Result<Unit>

    fun clear(site: SessionSite)

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
    override fun clear(site: SessionSite) = Unit
    override fun refresh() = Unit
}

class SessionAccessPolicy(
    private val cookieLookup: (SessionSite) -> String,
    private val youtubeCookieFile: () -> String?,
) {
    fun accessFor(url: String): SessionAccess = when (SessionSite.fromUrl(url)) {
        SessionSite.DOUYIN -> SessionAccess(cookieHeader = cookieLookup(SessionSite.DOUYIN))
        SessionSite.TIKTOK -> SessionAccess(cookieHeader = cookieLookup(SessionSite.TIKTOK))
        SessionSite.YOUTUBE -> SessionAccess(cookieFilePath = youtubeCookieFile())
        null -> SessionAccess()
    }
}
