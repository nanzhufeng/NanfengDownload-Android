package com.nanzhufeng.videodownloader.probe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DouyinShortLinkResolverTest {
    @Test fun relativeRedirectWorksAndRedirectLoopIsBounded() {
        var requests = 0
        val loop = DouyinShortLinkResolver { requests++; ShortLinkResponse(302, "/again/") }
        assertThrows(IllegalStateException::class.java) { loop.resolve("https://v.douyin.com/sample/") }
        assertEquals(3, requests)
        val relative = DouyinShortLinkResolver { ShortLinkResponse(302, "/video/7684884186910329202") }
        assertEquals("https://www.douyin.com/video/7684884186910329202", relative.resolve("https://v.douyin.com/sample/"))
    }

    @Test fun httpCredentialsAndUnknownPortAreRejectedBeforeFetchingTarget() {
        listOf("http://www.douyin.com/video/7684884186910329202", "https://account:secret@www.douyin.com/video/7684884186910329202", "https://www.douyin.com:8443/video/7684884186910329202").forEach { target ->
            var requests = 0
            val resolver = DouyinShortLinkResolver { requests++; ShortLinkResponse(302, target) }
            assertThrows(IllegalArgumentException::class.java) { resolver.resolve("https://v.douyin.com/sample/") }
            assertEquals(1, requests)
        }
    }
    @Test fun shortShareStopsAtOfficialWorkRedirectWithoutFetchingItsPage() {
        var requests = 0
        val resolver = DouyinShortLinkResolver { url ->
            assertEquals("https://v.douyin.com/sample/", url)
            requests++
            ShortLinkResponse(302, "https://www.iesdouyin.com/share/video/7684884186910329202/?tracking=1")
        }
        assertEquals("https://www.douyin.com/video/7684884186910329202", resolver.resolve("https://v.douyin.com/sample/"))
        assertEquals(1, requests)
    }

    @Test fun noteRedirectPreservesCompleteGalleryRoute() {
        val resolver = DouyinShortLinkResolver { ShortLinkResponse(302, "https://www.douyin.com/note/7676041925425736777") }
        assertEquals("https://www.douyin.com/note/7676041925425736777", resolver.resolve("https://v.douyin.com/sample/"))
    }

    @Test fun untrustedRedirectIsRejectedBeforeRequestingIt() {
        var requests = 0
        val resolver = DouyinShortLinkResolver { requests++; ShortLinkResponse(302, "https://douyin.com.evil.example/video/7684884186910329202") }
        assertThrows(IllegalArgumentException::class.java) { resolver.resolve("https://v.douyin.com/sample/") }
        assertEquals(1, requests)
    }

    @Test fun directAndUnrelatedLinksDoNotStartShareNetwork() {
        val resolver = DouyinShortLinkResolver { error("unexpected network") }
        val direct = "https://www.douyin.com/video/7684884186910329202"
        assertEquals(direct, resolver.resolve(direct))
        assertEquals("https://example.test/", resolver.resolve("https://example.test/"))
    }
}
