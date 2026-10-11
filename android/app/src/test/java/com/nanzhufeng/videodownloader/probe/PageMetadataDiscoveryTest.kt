package com.nanzhufeng.videodownloader.probe

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PageMetadataDiscoveryTest {
    private val youtube = "https://www.youtube.com/watch?v=fixture1234"
    private fun youtubePage(id: String = "fixture1234", status: String = "OK", live: Boolean = false) = """
        <script>var ytInitialPlayerResponse = {"playabilityStatus":{"status":"$status"},
        "videoDetails":{"videoId":"$id","title":"Synthetic title","author":"Fixture author","channelId":"UCfixture","isLiveContent":$live},
        "streamingData":{"adaptiveFormats":[{"itag":137,"width":1920,"height":1080,"mimeType":"video/mp4; codecs=avc1"}]}};</script>
    """.trimIndent()

    @Test fun publicPlayableYoutubeMetadataDoesNotInventATransferUrl() {
        val result = requireNotNull(YouTubePageMetadata.parse(youtubePage(), youtube))
        assertEquals("fixture1234", result.id)
        assertEquals("Synthetic title", result.title)
        assertEquals("Fixture author", result.creator)
        assertEquals("", result.videoUrl)
        assertTrue(result.headers.isEmpty())
    }
    @Test fun youtubeRejectsForeignPrivateUnplayableAndLiveWorks() {
        assertNull(YouTubePageMetadata.parse(youtubePage(id="other123456"), youtube))
        assertNull(YouTubePageMetadata.parse(youtubePage(status="LOGIN_REQUIRED"), youtube))
        assertNull(YouTubePageMetadata.parse(youtubePage(status="UNPLAYABLE"), youtube))
        assertNull(YouTubePageMetadata.parse(youtubePage(live=true), youtube))
        assertNull(YouTubePageMetadata.parse(youtubePage().replace("\"streamingData\":{", "\"streamingData\":{\"drmFamilies\":[\"widevine\"],"), youtube))
    }
    @Test fun youtubeRequiresDeclaredVideoAndAnUnambiguousOfficialWork() {
        assertNull(YouTubePageMetadata.parse(youtubePage().replace("video/mp4", "audio/mp4"), youtube))
        for (url in listOf(youtube+"&v=other123456", youtube.replace("www.youtube.com", "youtube.com.evil.test"),
            youtube.replace("https:", "http:"), youtube.replace("www.youtube.com", "www.youtube.com:8443"))) {
            assertNull(YouTubePageMetadata.parse(youtubePage(), url))
        }
        assertEquals("fixture1234", YouTubePageMetadata.workId("https://youtu.be/fixture1234"))
        assertEquals("fixture1234", YouTubePageMetadata.workId("https://www.youtube.com/shorts/fixture1234"))
    }
    @Test fun prefixJsonReaderHandlesStringsPartialDataAndExcessiveDepth() {
        assertEquals("{\"text\":\"brace } escaped \\\"\",\"v\":{}}", DiscoveryPageHttp.objectAfter("player={\"text\":\"brace } escaped \\\"\",\"v\":{}};rest", "player="))
        assertNull(DiscoveryPageHttp.objectAfter("player={\"partial\":{", "player="))
        assertNull(DiscoveryPageHttp.objectAfter("player="+"{".repeat(65)+"}".repeat(65), "player="))
    }

}
