package com.nanzhufeng.videodownloader.domain.download

import org.junit.Assert.assertEquals
import org.junit.Test

class GalleryOverallProgressTest {
    @Test
    fun oneFinishedImageOnlyAdvancesTheWholeGalleryByItsShare() {
        val progress = GalleryOverallProgress(listOf(100L, 300L))

        assertEquals(400L, progress.totalBytes)
        assertEquals(100L, progress.update(100L, 20L).downloadedBytes)
        assertEquals(400L, progress.update(100L, 20L).totalBytes)

        progress.complete(100L)
        assertEquals(400L, progress.update(300L, 20L).downloadedBytes)
    }
}
