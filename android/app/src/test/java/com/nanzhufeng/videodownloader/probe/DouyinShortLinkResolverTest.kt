package com.nanzhufeng.videodownloader.probe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DouyinShortLinkResolverTest {
    @Test fun httpAccessRejectionIsNotRetried() {
        var requests = 0
        val resolver = DouyinShortLinkResolver { requests++; ShortLinkResponse(403, null) }
        assertThrows(IllegalArgumentException::class.java) { resolver.resolve("https://v.douyin.com/sample/") }
        assertEquals(1, requests)
    }

    @Test fun recoveryBudgetIsSharedAcrossRedirectHops() {
        var requests = 0
        val resolver = DouyinShortLinkResolver {
            when (++requests) {
                1 -> throw java.net.SocketException("reset")
                2 -> ShortLinkResponse(302, "/another-hop/")
                else -> throw java.net.SocketException("reset again")
            }
        }
        assertThrows(DouyinShortLinkReadException::class.java) { resolver.resolve("https://v.douyin.com/sample/") }
        assertEquals(3, requests)
    }

    @Test fun transientConnectionFailureRetriesOnceAndPreservesNoteIdentity() {
        var requests = 0
        val resolver = DouyinShortLinkResolver {
            if (++requests == 1) throw java.net.SocketException("Connection reset")
            ShortLinkResponse(302, "https://www.iesdouyin.com/share/note/7692742276019207507/?tracking=1")
        }
        assertEquals("https://www.douyin.com/note/7692742276019207507", resolver.resolve("https://v.douyin.com/sample/"))
        assertEquals(2, requests)
    }

    @Test fun persistentConnectionFailureIsBoundedAndReportsItsStage() {
        var requests = 0
        val resolver = DouyinShortLinkResolver { requests++; throw java.net.UnknownHostException("private host details") }
        val failure = assertThrows(java.io.IOException::class.java) { resolver.resolve("https://v.douyin.com/sample/") }
        assertEquals(2, requests)
        org.junit.Assert.assertTrue(failure.message.orEmpty().contains("抖音短链接"))
        org.junit.Assert.assertFalse(failure.message.orEmpty().contains("private host details"))
    }

    @Test fun tlsFailureAndCancellationAreNeverRetried() {
        var requests = 0
        val tls = DouyinShortLinkResolver { requests++; throw javax.net.ssl.SSLException("certificate") }
        assertThrows(javax.net.ssl.SSLException::class.java) { tls.resolve("https://v.douyin.com/sample/") }
        assertEquals(1, requests)
        requests = 0
        val cancelled = DouyinShortLinkResolver { requests++; Thread.currentThread().interrupt(); throw java.io.IOException("cancelled") }
        try {
            assertThrows(java.io.IOException::class.java) { cancelled.resolve("https://v.douyin.com/sample/") }
            assertEquals(1, requests)
        } finally { Thread.interrupted() }
    }

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
