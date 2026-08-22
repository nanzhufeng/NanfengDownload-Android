package com.nanzhufeng.videodownloader.feature.settings

import com.nanzhufeng.videodownloader.domain.session.SessionSite
import android.webkit.WebSettings
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionLoginActivityTest {
    @Test
    fun tiktokKeepsItsExistingLoginChallengeCacheWhenThePageIsReopened() {
        assertTrue(loginCacheMode(SessionSite.TIKTOK) == WebSettings.LOAD_DEFAULT)
        assertTrue(loginCacheMode(SessionSite.DOUYIN) == WebSettings.LOAD_NO_CACHE)
    }

    @Test
    fun loginNavigationAllowsOnlyOfficialHttpsHostsAndGoogleSso() {
        assertTrue(SessionSite.TIKTOK.isTrustedLoginUrl("https://accounts.google.com/o/oauth2/auth"))
        assertTrue(SessionSite.TIKTOK.isTrustedLoginUrl("https://m.tiktok.com/login/phone-or-email"))
        assertFalse(SessionSite.TIKTOK.isTrustedLoginUrl("http://www.tiktok.com/login"))
        assertFalse(SessionSite.TIKTOK.isTrustedLoginUrl("https://www.tiktok.com.evil.example/login"))
        assertFalse(SessionSite.TIKTOK.ownsLoginPage("https://accounts.google.com/o/oauth2/auth"))
    }

    @Test
    fun loginWebViewResizesWithTheImeInsteadOfPanningTheOfficialForm() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val loginActivity = manifest.substringAfter("android:name=\".feature.settings.SessionLoginActivity\"")
            .substringBefore("/>")

        assertTrue(loginActivity.contains("android:windowSoftInputMode=\"adjustResize\""))
    }

    @Test
    fun tikTokLoginAssistOnlyStabilizesTheCaretAndAddsAVisibilityToggle() {
        val script = tikTokLoginPageAssistScript()

        assertTrue(script.contains("setSelectionRange"))
        assertTrue(script.contains("显示密码"))
        assertTrue(script.contains("隐藏密码"))
        assertTrue(script.contains("paddingRight = '76px'"))
        assertTrue(script.contains("right:34px"))
        assertFalse(script.contains("console."))
        assertFalse(script.contains("fetch("))
    }

    @Test
    fun loginUsesMobileBrowserIdentityWithoutWebViewMarker() {
        val userAgent = mobileLoginUserAgent(
            "Mozilla/5.0 (Linux; Android 16; PKH120 Build/TEST; wv) " +
                "AppleWebKit/537.36 Version/4.0 Chrome/131.0 Mobile Safari/537.36",
        )

        assertTrue(userAgent.contains("Android 16"))
        assertTrue(userAgent.contains("Mobile Safari"))
        assertFalse(userAgent.contains("; wv"))
        assertFalse(userAgent.contains("Version/4.0"))
        assertFalse(userAgent.contains("X11; Linux"))
    }

    @Test
    fun douyinCallbackWithAuthenticatedCookiesCompletesLoginEvenWhenTicketIsEmpty() {
        assertTrue(
            shouldFinishLoginAfterNavigation(
                site = SessionSite.DOUYIN,
                currentUrl = "https://www.douyin.com/passport/sso/login/callback/?ticket=&next=/",
                cookieHeaders = listOf("passport_csrf_token=probe", "sessionid=real-session"),
                hadAuthenticatedSessionAtStart = false,
            ),
        )
    }

    @Test
    fun callbackWithoutAuthenticatedCookiesDoesNotPretendLoginSucceeded() {
        assertFalse(
            shouldFinishLoginAfterNavigation(
                site = SessionSite.DOUYIN,
                currentUrl = "https://www.douyin.com/passport/sso/login/callback/?ticket=&next=/",
                cookieHeaders = listOf("passport_csrf_token=probe"),
                hadAuthenticatedSessionAtStart = false,
            ),
        )
    }

    @Test
    fun existingSessionDoesNotImmediatelyCloseReLoginScreen() {
        assertFalse(
            shouldFinishLoginAfterNavigation(
                site = SessionSite.DOUYIN,
                currentUrl = "https://www.douyin.com/",
                cookieHeaders = listOf("sessionid=existing-session"),
                hadAuthenticatedSessionAtStart = true,
            ),
        )
    }

    @Test
    fun douyinSsoDoesNotRedirectTheMobileLoginToDesktopCampaign() {
        assertTrue(SessionSite.DOUYIN.loginUrl.startsWith("https://www.douyin.com/login_page?"))
        assertTrue(SessionSite.DOUYIN.loginUrl.contains("service=https%3A%2F%2Fwww.douyin.com%2Fhome"))
    }

    @Test
    fun bilibiliFinishesOnlyAfterAuthenticatedCookieLeavesPassportPage() {
        assertFalse(
            shouldFinishLoginAfterNavigation(
                site = SessionSite.BILIBILI,
                currentUrl = "https://passport.bilibili.com/h5-app/passport/login",
                cookieHeaders = listOf("SESSDATA=account"),
                hadAuthenticatedSessionAtStart = false,
            ),
        )
        assertTrue(
            shouldFinishLoginAfterNavigation(
                site = SessionSite.BILIBILI,
                currentUrl = "https://www.bilibili.com/",
                cookieHeaders = listOf("SESSDATA=account"),
                hadAuthenticatedSessionAtStart = false,
            ),
        )
    }

    @Test
    fun xiaohongshuAnonymousCookieDoesNotReportLoginSuccess() {
        assertTrue(SessionSite.XIAOHONGSHU.loginUrl.endsWith("/explore"))
        assertFalse(SessionSite.XIAOHONGSHU.loginUrl.endsWith("/login"))
        assertFalse(
            shouldFinishLoginAfterNavigation(
                site = SessionSite.XIAOHONGSHU,
                currentUrl = "https://www.xiaohongshu.com/explore",
                cookieHeaders = listOf("a1=anonymous"),
                hadAuthenticatedSessionAtStart = false,
            ),
        )
    }
}
