package com.nanzhufeng.videodownloader.feature.settings

import com.nanzhufeng.videodownloader.domain.session.SessionSite
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionLoginActivityTest {
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
    fun douyinSsoTargetsTheRegisteredSiteRootInsteadOfTheBrokenLoginPath() {
        assertTrue(SessionSite.DOUYIN.loginUrl.contains("service=https%3A%2F%2Fwww.douyin.com%2F"))
        assertFalse(SessionSite.DOUYIN.loginUrl.contains("www.douyin.com%2Flogin"))
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
