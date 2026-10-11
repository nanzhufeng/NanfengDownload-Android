package com.nanzhufeng.videodownloader.probe

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class OfficialEmbedMetadataTest {
    private val youtube = "https://www.youtube.com/watch?v=fixture1234"
    private val tiktok = "https://www.tiktok.com/@fixture/video/1234567890123456789"
    private fun youtubeData() = JSONObject().put("type","video").put("version","1.0").put("provider_name","YouTube")
        .put("title","Fixture title").put("author_name","Fixture author").put("author_url","https://www.youtube.com/@fixture")
        .put("html","""<iframe src="https://www.youtube.com/embed/fixture1234?feature=oembed"></iframe>""")
    private fun tiktokData() = JSONObject().put("type","video").put("version","1.0").put("provider_name","TikTok")
        .put("title","Fixture title").put("author_name","Fixture author").put("author_url","https://www.tiktok.com/@fixture")
        .put("author_unique_id","fixture").put("embed_product_id","1234567890123456789").put("embed_type","video")
        .put("html","""<blockquote cite="https://www.tiktok.com/@fixture/video/1234567890123456789" data-video-id="1234567890123456789"></blockquote>""")
    @Test fun officialYoutubeEmbedReadsOnlyMetadataWithoutInventingAStreamOrCreatorId() {
        val info = requireNotNull(OfficialEmbedMetadata.parse(youtubeData().toString(),youtube))
        assertEquals("fixture1234",info.id);assertEquals("Fixture title",info.title)
        assertEquals("",info.videoUrl);assertEquals("",info.creatorId);assertTrue(info.headers.isEmpty())
    }
    @Test fun officialTiktokEmbedRequiresTheExactWorkAndAccount() {
        val info = requireNotNull(OfficialEmbedMetadata.parse(tiktokData().toString(),tiktok))
        assertEquals("1234567890123456789",info.id);assertEquals("",info.videoUrl)
        assertNull(OfficialEmbedMetadata.parse(tiktokData().put("author_unique_id","other").toString(),tiktok))
        assertNull(OfficialEmbedMetadata.parse(tiktokData().put("embed_product_id","other").toString(),tiktok))
        assertNull(OfficialEmbedMetadata.parse(tiktokData().put("html","<blockquote data-video-id='1234567890123456789' cite='https://www.tiktok.com/@fixture/video/9999999999999999999'></blockquote>").toString(),tiktok))
    }
    @Test fun youtubeRejectsForeignIframeProviderAndCredentialBearingUrls() {
        for (html in listOf("<iframe src='https://youtube.com.evil.test/embed/fixture1234'></iframe>",
            "<iframe src='https://www.youtube.com/embed/other123456'></iframe>",
            "<iframe src='https://user:password@www.youtube.com/embed/fixture1234'></iframe>")) {
            assertNull(OfficialEmbedMetadata.parse(youtubeData().put("html",html).toString(),youtube))
        }
        assertNull(OfficialEmbedMetadata.parse(youtubeData().put("provider_name","Other").toString(),youtube))
    }
    @Test fun missingErrorAndOversizedMetadataDoNotBecomeSuccess() {
        assertNull(OfficialEmbedMetadata.parse("{\"code\":404}",tiktok))
        assertNull(OfficialEmbedMetadata.parse(youtubeData().put("type","rich").toString(),youtube))
        assertNull(OfficialEmbedMetadata.parse(youtubeData().put("title", "x".repeat(70*1024)).toString(),youtube))
        assertNull(OfficialEmbedMetadata.parse(youtubeData().toString(),youtube.replace("www.youtube.com","youtube.com.evil.test")))
    }
}
