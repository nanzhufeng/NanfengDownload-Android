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
            hasMore = false,
            nextStart = 0,
        )

        assertEquals(listOf("1"), catalog.selectedEntries().map(CreatorVideoEntry::id))
    }

    @Test
    fun appendPageDeduplicatesAcrossPagesAndAdvancesCursor() {
        val first = CreatorVideoEntry("1", "one", "target", "target", "url-1", "", "")
        val duplicate = first.copy(title = "duplicate")
        val second = CreatorVideoEntry("2", "two", "target", "target", "url-2", "", "")
        val catalog = CreatorCatalog("target", "target", listOf(first), 0, 0, true, 51)
        val nextPage = CreatorCatalog("target", "target", listOf(duplicate, second), 0, 1, true, 101)

        val merged = catalog.append(nextPage)

        assertEquals(listOf("1", "2"), merged.entries.map(CreatorVideoEntry::id))
        assertEquals(1, merged.duplicateCount)
        assertEquals(1, merged.foreignCount)
        assertEquals(101, merged.nextStart)
    }
}
