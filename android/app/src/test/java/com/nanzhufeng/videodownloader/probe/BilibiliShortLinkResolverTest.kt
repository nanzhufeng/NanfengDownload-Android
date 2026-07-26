package com.nanzhufeng.videodownloader.probe

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BilibiliShortLinkResolverTest {
    @Test
    fun `official b23 short link resolves to official video url`() {
        val resolver = BilibiliShortLinkResolver {
            "https://www.bilibili.com/video/BV1zv5561EtZ"
        }

        assertEquals(
            "https://www.bilibili.com/video/BV1zv5561EtZ",
            resolver.resolve("https://b23.tv/kdX9kKW"),
        )
    }

    @Test
    fun `non b23 urls do not use trusted short link network`() {
        val resolver = BilibiliShortLinkResolver {
            error("不应请求网络")
        }

        assertEquals(
            "https://www.bilibili.com/video/BV1zv5561EtZ",
            resolver.resolve("https://www.bilibili.com/video/BV1zv5561EtZ"),
        )
    }

    @Test
    fun `short link redirecting outside bilibili is rejected`() {
        val resolver = BilibiliShortLinkResolver { "https://www.baidu.com/" }

        assertThrows(IllegalArgumentException::class.java) {
            resolver.resolve("https://b23.tv/kdX9kKW")
        }
    }

    @Test
    fun `trusted dns parser keeps only ipv4 answers`() {
        val payload = """
            {
              "Status": 0,
              "Answer": [
                {"name": "b23.tv.", "type": 5, "data": "a.w.bilicdn1.com."},
                {"name": "a.w.bilicdn1.com.", "type": 1, "data": "148.153.46.90"},
                {"name": "a.w.bilicdn1.com.", "type": 28, "data": "2408::1"},
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
