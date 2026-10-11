package com.nanzhufeng.videodownloader.domain.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionAccessPolicyTest {
    @Test fun nativeHeaderReadDoesNotWriteCookieFilesOrSendToOtherOrigins() {
        var files = 0
        val policy = SessionAccessPolicy(cookieLookup = { _, _ -> "ttwid=test-visitor" },
            youtubeCookieFile = { null }, sessionCookieFile = { _, _ -> files++; "private-file" })
        assertEquals("ttwid=test-visitor", policy.cookieHeaderFor("https://www.iesdouyin.com/share/video/123/"))
        for (url in listOf("http://www.iesdouyin.com/", "https://evil.example/", "https://www.iesdouyin.com.evil.example/",
                "https://user@www.iesdouyin.com/", "https://www.iesdouyin.com:444/", "https://www.youtube.com/")) {
            assertEquals("", policy.cookieHeaderFor(url))
        }
        assertEquals(0, files)
    }
    @Test fun officialIesShareUsesTheExistingDouyinSessionScope() {
        val policy = SessionAccessPolicy(cookieLookup = { site, _ ->
            if (site == SessionSite.DOUYIN) "ttwid=test-visitor" else ""
        }, youtubeCookieFile = { null })
        assertEquals("ttwid=test-visitor", policy.accessFor("https://www.iesdouyin.com/share/video/123/").cookieHeader)
        assertEquals("", policy.accessFor("http://www.iesdouyin.com/share/video/123/").cookieHeader)
        assertEquals("", policy.accessFor("https://www.iesdouyin.com.evil.example/share/video/123/").cookieHeader)
    }
    @Test
    fun douyinAndTikTokUseOnlyTheirOwnWebViewCookies() {
        val cookies = mapOf(
            SessionSite.DOUYIN to "sessionid=douyin-session",
            SessionSite.TIKTOK to "sessionid=tiktok-session",
            SessionSite.BILIBILI to "SESSDATA=bilibili-session",
            SessionSite.XIAOHONGSHU to "web_session=xiaohongshu-session",
        )
        val policy = SessionAccessPolicy(
            cookieLookup = { site, _ -> cookies[site].orEmpty() },
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
        assertEquals(
            "SESSDATA=bilibili-session",
            policy.accessFor("https://www.bilibili.com/video/BV1bK411W797").cookieHeader,
        )
        assertEquals(
            "web_session=xiaohongshu-session",
            policy.accessFor("https://www.rednote.com/explore/69ce30d3000000002100791c").cookieHeader,
        )
        assertEquals(
            "web_session=xiaohongshu-session",
            policy.accessFor("https://xhslink.cn/o/7i6agytmp2s").cookieHeader,
        )
        assertNull(policy.accessFor("https://www.douyin.com/video/1").cookieFilePath)
    }

    @Test
    fun youtubeUsesImportedCookieFileWithoutLeakingOtherCookies() {
        val policy = SessionAccessPolicy(
            cookieLookup = { _, _ -> "should-not-leak" },
            youtubeCookieFile = { "C:/private/youtube-cookies.txt" },
        )

        val access = policy.accessFor("https://www.youtube.com/watch?v=one")

        assertEquals("", access.cookieHeader)
        assertEquals("C:/private/youtube-cookies.txt", access.cookieFilePath)
    }

    @Test
    fun webViewCookiesBecomeAPlatformScopedCookieJarForYtDlp() {
        val generatedFor = mutableListOf<SessionSite>()
        val policy = SessionAccessPolicy(
            cookieLookup = { site, _ -> "sessionid=${site.name.lowercase()}" },
            youtubeCookieFile = { null },
            sessionCookieFile = { site, _ ->
                generatedFor += site
                "/private/${site.name.lowercase()}-cookies.txt"
            },
        )

        val tiktok = policy.accessFor("https://www.tiktok.com/@creator/video/2")
        val bilibili = policy.accessFor("https://www.bilibili.com/video/BV1bK411W797")

        assertEquals("sessionid=tiktok", tiktok.cookieHeader)
        assertEquals("/private/tiktok-cookies.txt", tiktok.cookieFilePath)
        assertEquals("/private/bilibili-cookies.txt", bilibili.cookieFilePath)
        assertEquals(listOf(SessionSite.TIKTOK, SessionSite.BILIBILI), generatedFor)
    }

    @Test
    fun unrelatedUrlGetsNoAuthorizationMaterial() {
        val policy = SessionAccessPolicy(
            cookieLookup = { _, _ -> "secret" },
            youtubeCookieFile = { "C:/private/youtube-cookies.txt" },
        )

        assertEquals(SessionAccess(), policy.accessFor("https://example.com/video"))
    }

    @Test
    fun anonymousCookiesAreSavedButNotReportedAsAuthenticated() {
        val header = "ttwid=anonymous; msToken=temporary"

        assertFalse(classifyAuthenticatedSession(SessionSite.DOUYIN, header))
        assertFalse(classifyAuthenticatedSession(SessionSite.TIKTOK, header))
    }

    @Test
    fun accountCookiesAreReportedAsAuthenticated() {
        assertTrue(
            classifyAuthenticatedSession(
                SessionSite.DOUYIN,
                "ttwid=anonymous; sessionid=account-session",
            ),
        )
        assertTrue(classifyAuthenticatedSession(SessionSite.TIKTOK, "sessionid_ss=account-session"))
        assertTrue(classifyAuthenticatedSession(SessionSite.BILIBILI, "SESSDATA=account-session"))
        assertTrue(classifyAuthenticatedSession(SessionSite.XIAOHONGSHU, "web_session=account-session"))
        assertFalse(classifyAuthenticatedSession(SessionSite.BILIBILI, "buvid3=anonymous"))
        assertFalse(classifyAuthenticatedSession(SessionSite.XIAOHONGSHU, "a1=anonymous"))
    }

    @Test
    fun cookieHeadersMergePlatformScopesWithoutDuplicatingNames() {
        assertEquals(
            "ttwid=short-link; sessionid=account; msToken=web",
            mergeCookieHeaders(
                listOf(
                    "ttwid=short-link; sessionid=account",
                    "ttwid=web; msToken=web",
                ),
            ),
        )
    }

    @Test
    fun cookieDeletionCoversParentHostAndDotPrefixedHostScopes() {
        assertEquals(
            setOf(
                ".douyin.com",
                "v.douyin.com",
                ".v.douyin.com",
                "www.douyin.com",
                ".www.douyin.com",
                "www.iesdouyin.com",
                ".www.iesdouyin.com",
                "passport.douyin.com",
                ".passport.douyin.com",
                "sso.douyin.com",
                ".sso.douyin.com",
            ),
            cookieDeletionDomains(SessionSite.DOUYIN),
        )
    }

    @Test
    fun cookieAssignmentsPreserveValuesContainingEqualsSigns() {
        assertEquals(
            listOf("sessionid=abc==", "msToken=one"),
            cookieAssignments("sessionid=abc==; malformed; msToken=one"),
        )
    }
}
