package com.nanzhufeng.videodownloader.data.repository

import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import com.nanzhufeng.videodownloader.core.model.DownloadSourceKind
import com.nanzhufeng.videodownloader.core.model.MediaItem
import com.nanzhufeng.videodownloader.probe.DouyinCaptureStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomDownloadRepositoryTest {
    @Test
    fun completeGalleryRequeuesLegacyItemWithoutCapturedImages() {
        assertTrue(
            isCapturedGallerySourceUpgrade(
                existingUrls = emptyList(),
                existingExpectedCount = 0,
                existingSourceVersion = 0,
                incoming = media(cleanGallery(14)),
            ),
        )
    }

    @Test
    fun completeGalleryRequeuesLegacySingleSlide() {
        assertTrue(
            isCapturedGallerySourceUpgrade(
                existingUrls = cleanGallery(1),
                existingExpectedCount = 0,
                existingSourceVersion = 0,
                incoming = media(cleanGallery(14)),
            ),
        )
    }

    @Test
    fun cleanGalleryReplacesWatermarkedGallery() {
        assertTrue(
            isCapturedGallerySourceUpgrade(
                existingUrls = listOf(
                    "https://p3-sign.douyinpic.com/tos/image-1~tplv-dy-water-v2.webp?x-signature=old",
                ),
                existingExpectedCount = 1,
                existingSourceVersion = DouyinCaptureStore.STRUCTURED_GALLERY_SOURCE_VERSION,
                incoming = media(listOf(
                    "https://p9-sign.douyinpic.com/tos/image-1~tplv-dy-aweme-images.webp?x-signature=new",
                )),
            ),
        )
    }

    @Test
    fun renewedSignatureForSameCleanGalleryRemainsDuplicate() {
        assertFalse(
            isCapturedGallerySourceUpgrade(
                existingUrls = listOf(
                    "https://p3-sign.douyinpic.com/tos/image-1~tplv-dy-aweme-images.webp?x-signature=old",
                ),
                existingExpectedCount = 1,
                existingSourceVersion = DouyinCaptureStore.STRUCTURED_GALLERY_SOURCE_VERSION,
                incoming = media(listOf(
                    "https://p9-sign.douyinpic.com/tos/image-1~tplv-dy-aweme-images.webp?x-signature=new",
                )),
            ),
        )
    }

    @Test
    fun unverifiedIncomingGalleryNeverRequeuesAnything() {
        assertFalse(
            isCapturedGallerySourceUpgrade(
                existingUrls = emptyList(),
                existingExpectedCount = 0,
                existingSourceVersion = 0,
                incoming = media(cleanGallery(14)).copy(capturedImageSourceVersion = 0),
            ),
        )
    }

    @Test
    fun legacyTwoFileHistoryMustBeReplacedByVerifiedFourteenImageGallery() {
        assertTrue(
            needsVerifiedGalleryRedownload(
                completedExpectedCount = 0,
                completedSourceVersion = 0,
                completedOutputCount = 2,
                incoming = media(cleanGallery(14)),
            ),
        )
    }

    @Test
    fun certifiedFourteenFileHistoryRemainsADuplicate() {
        assertFalse(
            needsVerifiedGalleryRedownload(
                completedExpectedCount = 14,
                completedSourceVersion = DouyinCaptureStore.STRUCTURED_GALLERY_SOURCE_VERSION,
                completedOutputCount = 14,
                incoming = media(cleanGallery(14)),
            ),
        )
    }

    @Test
    fun legacyTwoFileHistoryMustBeReplacedEvenWhenExpectedCountIsAlsoTwo() {
        assertTrue(
            needsVerifiedGalleryRedownload(
                completedExpectedCount = 0,
                completedSourceVersion = 0,
                completedOutputCount = 2,
                incoming = media(cleanGallery(2)),
            ),
        )
    }

    private fun cleanGallery(count: Int): List<String> = List(count) { index ->
        "https://p3-sign.douyinpic.com/tos/image-$index~tplv-dy-aweme-images.webp?signature=$index"
    }

    private fun media(urls: List<String>) = MediaItem(
        mediaKey = "DOUYIN:7670887343922973155",
        platform = DownloadPlatform.DOUYIN,
        contentId = "7670887343922973155",
        originalUrl = "https://www.douyin.com/note/7670887343922973155",
        sourceKind = DownloadSourceKind.SINGLE_VIDEO,
        title = "图文",
        creator = "作者",
        creatorId = "",
        publishDate = "",
        thumbnailUrl = "",
        capturedImageUrls = urls,
        capturedImageExpectedCount = urls.size,
        capturedImageSourceVersion = DouyinCaptureStore.STRUCTURED_GALLERY_SOURCE_VERSION,
    )

}
