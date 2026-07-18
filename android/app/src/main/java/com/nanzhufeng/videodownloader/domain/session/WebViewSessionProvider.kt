package com.nanzhufeng.videodownloader.domain.session

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import com.nanzhufeng.videodownloader.feature.settings.SessionLoginActivity
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class WebViewSessionProvider(context: Context) : SessionProvider {
    private val applicationContext = context.applicationContext
    private val cookieManager = CookieManager.getInstance().apply { setAcceptCookie(true) }
    private val sessionDirectory = File(applicationContext.filesDir, "sessions").apply { mkdirs() }
    private val youtubeCookieFile = File(sessionDirectory, "youtube-cookies.txt")
    private val accessPolicy = SessionAccessPolicy(
        cookieLookup = ::cookiesForSite,
        youtubeCookieFile = { youtubeCookieFile.takeIf(File::isFile)?.absolutePath },
    )
    private val mutableStates = MutableStateFlow(buildStates())
    override val states: StateFlow<List<SiteSessionState>> = mutableStates.asStateFlow()

    override fun accessFor(url: String): SessionAccess = accessPolicy.accessFor(url)

    override fun openLogin(site: SessionSite) {
        require(site != SessionSite.YOUTUBE) { "YouTube 登录信息请通过 cookies.txt 导入" }
        applicationContext.startActivity(
            Intent(applicationContext, SessionLoginActivity::class.java).apply {
                putExtra(SessionLoginActivity.EXTRA_SITE, site.name)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }

    override suspend fun importYoutubeCookies(sourceUri: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = requireNotNull(
                applicationContext.contentResolver.openInputStream(Uri.parse(sourceUri)),
            ) { "无法读取所选 cookies.txt" }.use(::readLimited)
            require(isValidNetscapeCookieFile(bytes)) {
                "所选文件不是有效的 Netscape cookies.txt"
            }
            val temporary = File(sessionDirectory, "youtube-cookies.tmp")
            temporary.writeBytes(bytes)
            if (youtubeCookieFile.exists()) youtubeCookieFile.delete()
            require(temporary.renameTo(youtubeCookieFile)) { "无法保存 YouTube 登录信息" }
            refresh()
        }
    }

    override suspend fun exportCookies(destinationUri: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val sections = buildList {
                if (youtubeCookieFile.isFile && youtubeCookieFile.length() > 0L) {
                    add(
                        youtubeCookieFile.readLines()
                            .filter { line -> line.isNotBlank() && !line.startsWith("# Netscape") }
                            .joinToString("\n"),
                    )
                }
                listOf(
                    SessionSite.DOUYIN to ".douyin.com",
                    SessionSite.TIKTOK to ".tiktok.com",
                ).forEach { (site, domain) ->
                    cookieManager.getCookie(site.cookieProbeUrl)
                        .orEmpty()
                        .split(';')
                        .map(String::trim)
                        .filter(String::isNotBlank)
                        .mapNotNull { cookie ->
                            val separator = cookie.indexOf('=')
                            if (separator <= 0) return@mapNotNull null
                            val name = cookie.substring(0, separator).trim()
                            val value = cookie.substring(separator + 1)
                            "$domain\tTRUE\t/\tTRUE\t0\t$name\t$value"
                        }
                        .takeIf(List<String>::isNotEmpty)
                        ?.joinToString("\n")
                        ?.let(::add)
                }
            }.filter(String::isNotBlank)
            require(sections.isNotEmpty()) { "当前没有可导出的登录会话" }
            val output = buildString {
                appendLine("# Netscape HTTP Cookie File")
                appendLine("# Exported by Nanzhufeng Video Downloader")
                append(sections.joinToString("\n"))
                appendLine()
            }
            requireNotNull(
                applicationContext.contentResolver.openOutputStream(Uri.parse(destinationUri), "w"),
            ) { "无法打开所选导出文件" }.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(output)
            }
            sections.size
        }
    }

    override suspend fun clear(site: SessionSite): Result<Unit> = runCatching {
        if (site == SessionSite.YOUTUBE) {
            withContext(Dispatchers.IO) {
                if (youtubeCookieFile.exists()) {
                    require(youtubeCookieFile.delete()) { "无法清除 YouTube cookies.txt" }
                }
            }
        } else {
            withContext(Dispatchers.Main.immediate) { clearWebViewCookies(site) }
        }
        refresh()
    }

    override fun refresh() {
        mutableStates.value = buildStates()
    }

    private fun buildStates(): List<SiteSessionState> = SessionSite.entries.map { site ->
        val cookieHeader = if (site == SessionSite.YOUTUBE) "" else cookiesForSite(site, site.cookieProbeUrl)
        val saved = if (site == SessionSite.YOUTUBE) {
            youtubeCookieFile.isFile && youtubeCookieFile.length() > 0L
        } else {
            cookieHeader.isNotBlank()
        }
        val authenticated = saved && classifyAuthenticatedSession(site, cookieHeader)
        SiteSessionState(
            site = site,
            hasSavedSession = saved,
            summary = when {
                site == SessionSite.YOUTUBE && saved -> "cookies.txt 已导入"
                authenticated -> "已检测到登录 Cookie，使用时仍会验证"
                saved -> "已保存网页会话，尚未确认登录"
                else -> "未保存登录会话"
            },
            isAuthenticated = authenticated,
        )
    }

    private fun cookiesForSite(site: SessionSite, preferredUrl: String): String {
        val urls = buildList {
            add(preferredUrl)
            addAll(site.cookieScopeUrls)
        }.distinct()
        return mergeCookieHeaders(urls.map { url -> cookieManager.getCookie(url).orEmpty() })
    }

    private suspend fun clearWebViewCookies(site: SessionSite) {
        val preservedScopes = SessionSite.entries
            .filter { candidate -> candidate != site && candidate != SessionSite.YOUTUBE }
            .flatMap { candidate ->
                candidate.cookieScopeUrls.mapNotNull { url ->
                    cookieManager.getCookie(url).orEmpty()
                        .takeIf(String::isNotBlank)
                        ?.let { header -> PreservedCookieScope(candidate, url, header) }
                }
            }
        val names = site.cookieScopeUrls
            .flatMap { url -> cookieNames(cookieManager.getCookie(url).orEmpty()) }
            .toSet()
        names.forEach { name ->
            site.cookieScopeUrls.forEach { url ->
                expireCookie(url, "$name=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/")
                cookieDeletionDomains(site).forEach { domain ->
                    expireCookie(
                        url,
                        "$name=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; " +
                            "Domain=$domain; Path=/",
                    )
                }
            }
        }
        cookieManager.flush()
        if (cookiesForSite(site, site.cookieProbeUrl).isNotBlank()) {
            removeAllCookies()
            preservedScopes.forEach { scope ->
                cookieAssignments(scope.header).forEach { assignment ->
                    setCookie(
                        scope.url,
                        "$assignment; Path=/; Secure; SameSite=None",
                    )
                }
            }
            cookieManager.flush()
            preservedScopes.map(PreservedCookieScope::site).distinct().forEach { preservedSite ->
                require(cookiesForSite(preservedSite, preservedSite.cookieProbeUrl).isNotBlank()) {
                    "${preservedSite.label} 会话未能恢复，请重新登录"
                }
            }
        }
        require(cookiesForSite(site, site.cookieProbeUrl).isBlank()) {
            "${site.label} 会话未能完全清除，请关闭登录页后重试"
        }
    }

    private suspend fun expireCookie(url: String, value: String) =
        suspendCancellableCoroutine { continuation ->
            cookieManager.setCookie(url, value) {
                if (continuation.isActive) continuation.resume(Unit)
            }
        }

    private suspend fun setCookie(url: String, value: String) =
        suspendCancellableCoroutine { continuation ->
            cookieManager.setCookie(url, value) {
                if (continuation.isActive) continuation.resume(Unit)
            }
        }

    private suspend fun removeAllCookies() =
        suspendCancellableCoroutine { continuation ->
            cookieManager.removeAllCookies {
                if (continuation.isActive) continuation.resume(Unit)
            }
        }

    private fun readLimited(input: java.io.InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= MAX_COOKIE_FILE_BYTES) { "cookies.txt 文件过大" }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private data class PreservedCookieScope(
        val site: SessionSite,
        val url: String,
        val header: String,
    )

    private fun isValidNetscapeCookieFile(bytes: ByteArray): Boolean {
        val text = bytes.toString(Charsets.UTF_8)
        return text.lineSequence().any { rawLine ->
            val line = rawLine.removePrefix("#HttpOnly_").trim()
            !line.startsWith('#') && line.split('\t').size >= 7 &&
                (line.contains("youtube.com", ignoreCase = true) ||
                    line.contains("google.com", ignoreCase = true))
        }
    }

    private companion object {
        const val MAX_COOKIE_FILE_BYTES = 5 * 1024 * 1024
    }
}
