package com.nanzhufeng.videodownloader.domain.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionAccessPolicyTest {
    @Test
    fun douyinAndTikTokUseOnlyTheirOwnWebViewCookies() {
        val cookies = mapOf(
            SessionSite.DOUYIN to "sessionid=douyin-session",
            SessionSite.TIKTOK to "sessionid=tiktok-session",
        )
        val policy = SessionAccessPolicy(
            cookieLookup = { site -> cookies[site].orEmpty() },
            youtubeCookieFile = { "C:/private/youtube-cookies.txt" },
        )

        assertEquals(
            "sessionid=douyin-session",
            policy.accessFor("https://www.douyin.com/video/1").cookieHeader,
        )
        assertEquals(
            "sessionid=tiktok-session",
            policy.accessFor("https://www.tiktok.com/@creator/video/2").cookieHeader,
        )
        assertNull(policy.accessFor("https://www.douyin.com/video/1").cookieFilePath)
    }

    @Test
    fun youtubeUsesImportedCookieFileWithoutLeakingOtherCookies() {
        val policy = SessionAccessPolicy(
            cookieLookup = { "should-not-leak" },
            youtubeCookieFile = { "C:/private/youtube-cookies.txt" },
        )

        val access = policy.accessFor("https://www.youtube.com/watch?v=one")

        assertEquals("", access.cookieHeader)
        assertEquals("C:/private/youtube-cookies.txt", access.cookieFilePath)
    }

    @Test
    fun unrelatedUrlGetsNoAuthorizationMaterial() {
        val policy = SessionAccessPolicy(
            cookieLookup = { "secret" },
            youtubeCookieFile = { "C:/private/youtube-cookies.txt" },
        )

        assertEquals(SessionAccess(), policy.accessFor("https://example.com/video"))
    }
}
