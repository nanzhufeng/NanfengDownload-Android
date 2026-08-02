package com.nanzhufeng.videodownloader.probe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DouyinProbeActivityTest {
    @Test
    fun iesDouyinSharePageIsCanonicalizedToAuthenticatedVideoPage() {
        assertEquals(
            "https://www.douyin.com/video/7669248142533973995",
            DouyinProbeActivity.canonicalDouyinVideoUrl(
                "https://www.iesdouyin.com/share/video/7669248142533973995/?region=CN",
            ),
        )
    }

    @Test
    fun unrelatedHostCannotTriggerCanonicalRedirect() {
        assertNull(
            DouyinProbeActivity.canonicalDouyinVideoUrl(
                "https://www.iesdouyin.com.attacker.example/share/video/7669248142533973995/",
            ),
        )
    }

    @Test
    fun mobileBrowserUserAgentKeepsTheInstalledEngineVersion() {
        assertEquals(
            "Mozilla/5.0 (Linux; Android 16; PKH120 Build/V) " +
                "AppleWebKit/537.36 Chrome/150.0.0.0 Mobile Safari/537.36",
            DouyinProbeActivity.mobileBrowserUserAgent(
                "Mozilla/5.0 (Linux; Android 16; PKH120 Build/V; wv) " +
                    "AppleWebKit/537.36 Version/4.0 Chrome/150.0.0.0 Mobile Safari/537.36",
            ),
        )
    }
}
