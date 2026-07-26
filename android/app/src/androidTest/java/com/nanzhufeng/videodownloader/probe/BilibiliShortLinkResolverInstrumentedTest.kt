package com.nanzhufeng.videodownloader.probe

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Test

class BilibiliShortLinkResolverInstrumentedTest {
    @Test
    fun trustedDnsPayloadParsesOnAndroidRuntime() {
        val payload = """
            {
              "Answer": [
                {"name": "b23.tv.", "type": 5, "data": "a.w.bilicdn1.com."},
                {"name": "a.w.bilicdn1.com.", "type": 1, "data": "148.153.46.90"}
              ]
            }
        """.trimIndent()

        assertEquals(
            listOf(InetAddress.getByName("148.153.46.90")),
            TrustedBilibiliShortLinkNetwork.parseIpv4Addresses(payload),
        )
    }
}
