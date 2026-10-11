package com.nanzhufeng.videodownloader.probe

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PornhubApiMetadataTest {
    private val url="https://cn.pornhub.com/view_video.php?viewkey=fixture123"
    private fun video()=JSONObject().put("video_id","fixture123").put("url","https://www.pornhub.com/view_video.php?viewkey=fixture123")
        .put("title","Fixture &amp; 中文").put("duration","01:23").put("publish_date","2026-10-01 12:30:00")
    private fun body(v:JSONObject=video())=JSONObject().put("video",v).toString()
    @Test fun readsExactOfficialMetadataWithoutInventingAStreamOrUploader() {
        val info=requireNotNull(PornhubApiMetadata.parse(body(),url))
        assertEquals("fixture123",info.id);assertEquals("Fixture & 中文",info.title)
        assertEquals("20261001",info.uploadDate);assertEquals("Pornhub",info.creator)
        assertEquals("",info.creatorId);assertEquals("",info.videoUrl);assertEquals(url,info.webpageUrl)
    }
    @Test fun rejectsForeignIdsUrlsAndPrivateDeletedOrLiveFlags() {
        assertNull(PornhubApiMetadata.parse(body(video().put("video_id","other")),url))
        assertNull(PornhubApiMetadata.parse(body(video().put("url","https://pornhub.com.evil.test/view_video.php?viewkey=fixture123")),url))
        for (flag in listOf("is_private","is_deleted","is_live","drm")) assertNull(PornhubApiMetadata.parse(body(video().put(flag,true)),url))
    }
    @Test fun rejectsErrorsEmptyOrOversizedMetadataAndZeroDuration() {
        assertNull(PornhubApiMetadata.parse("{\"error\":\"fixture\"}",url))
        assertNull(PornhubApiMetadata.parse(body(video().put("duration","00:00")),url))
        assertNull(PornhubApiMetadata.parse(body(video().put("title","")),url))
        assertNull(PornhubApiMetadata.parse(body(video().put("title","x".repeat(300*1024))),url))
        assertNull(PornhubApiMetadata.parse(body(),url+"&viewkey=other"))
    }
    @Test fun preservesTheRequestedCnRegionAndNeverRoutesSpoofedUrls() {
        assertEquals("cn.pornhub.com",requireNotNull(PornhubApiMetadata.endpoint(url)).host)
        assertEquals("fixture123",requireNotNull(PornhubApiMetadata.endpoint(url)).queryParameter("id"))
        assertEquals("www.pornhub.com",requireNotNull(PornhubApiMetadata.endpoint(url.replace("cn.","www."))).host)
        assertNull(PornhubApiMetadata.endpoint(url.replace("cn.pornhub.com","pornhub.com.evil.test")))
    }
}
