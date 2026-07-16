package com.nanzhufeng.videodownloader.probe

import org.junit.Assert.assertEquals
import org.junit.Test

class CreatorCatalogTest {
    @Test
    fun selectedEntriesExcludeUncheckedItems() {
        val selected = CreatorVideoEntry(
            id = "1",
            title = "one",
            creator = "target",
            creatorId = "target",
            webpageUrl = "https://www.tiktok.com/@target/video/1",
            uploadDate = "",
            thumbnail = "",
        )
        val unchecked = CreatorVideoEntry(
            id = "2",
            title = "two",
            creator = "target",
            creatorId = "target",
            webpageUrl = "https://www.tiktok.com/@target/video/2",
            uploadDate = "",
            thumbnail = "",
            selected = false,
        )
        val catalog = CreatorCatalog(
            creator = "target",
            creatorId = "target",
            entries = listOf(selected, unchecked),
            duplicateCount = 0,
            foreignCount = 0,
        )

        assertEquals(listOf("1"), catalog.selectedEntries().map(CreatorVideoEntry::id))
    }
}
