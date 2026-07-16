package com.nanzhufeng.videodownloader.domain.session

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import com.nanzhufeng.videodownloader.feature.settings.SessionLoginActivity
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class WebViewSessionProvider(context: Context) : SessionProvider {
    private val applicationContext = context.applicationContext
    private val cookieManager = CookieManager.getInstance().apply { setAcceptCookie(true) }
    private val sessionDirectory = File(applicationContext.filesDir, "sessions").apply { mkdirs() }
    private val youtubeCookieFile = File(sessionDirectory, "youtube-cookies.txt")
    private val accessPolicy = SessionAccessPolicy(
        cookieLookup = { site -> cookieManager.getCookie(site.cookieProbeUrl).orEmpty() },
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

    override fun clear(site: SessionSite) {
        if (site == SessionSite.YOUTUBE) {
            youtubeCookieFile.delete()
        } else {
            val cookieNames = cookieManager.getCookie(site.cookieProbeUrl)
                .orEmpty()
                .split(';')
                .mapNotNull { cookie -> cookie.substringBefore('=').trim().takeIf(String::isNotBlank) }
            cookieNames.forEach { name ->
                cookieManager.setCookie(
                    site.cookieProbeUrl,
                    "$name=; Max-Age=0; Path=/; SameSite=Lax",
                )
            }
            cookieManager.flush()
        }
        refresh()
    }

    override fun refresh() {
        mutableStates.value = buildStates()
    }

    private fun buildStates(): List<SiteSessionState> = SessionSite.entries.map { site ->
        val saved = when (site) {
            SessionSite.YOUTUBE -> youtubeCookieFile.isFile && youtubeCookieFile.length() > 0L
            else -> cookieManager.getCookie(site.cookieProbeUrl).orEmpty().isNotBlank()
        }
        SiteSessionState(
            site = site,
            hasSavedSession = saved,
            summary = if (saved) "会话已保存，可在下次启动继续使用" else "未保存登录会话",
        )
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
