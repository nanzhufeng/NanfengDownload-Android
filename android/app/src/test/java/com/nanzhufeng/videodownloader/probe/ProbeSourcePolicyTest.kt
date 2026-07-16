package com.nanzhufeng.videodownloader.probe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProbeSourcePolicyTest {
    @Test
    fun tiktokCreatorIsRejectedBySingleVideoEntry() {
        val source = ClassifiedSource(
            platform = Platform.TIKTOK,
            kind = SourceKind.CHANNEL_OR_PLAYLIST,
            url = "https://www.tiktok.com/@creator",
        )

        assertThrows(IllegalArgumentException::class.java) {
            ProbeSourcePolicy.requireSingle(source)
        }
    }

    @Test
    fun resolvedShortCreatorUsesCanonicalProfileUrl() {
        val source = ClassifiedSource(
            platform = Platform.TIKTOK,
            kind = SourceKind.UNKNOWN_TIKTOK_SHARE,
            url = "https://vt.tiktok.com/ZSMock123/",
        )
        val resolved = ResolvedSource(
            kind = SourceKind.CHANNEL_OR_PLAYLIST,
            url = "https://www.tiktok.com/@creator",
        )

        val result = ProbeSourcePolicy.requireTiktokCreator(source, resolved)

        assertEquals(SourceKind.CHANNEL_OR_PLAYLIST, result.kind)
        assertEquals("https://www.tiktok.com/@creator", result.url)
    }
}
