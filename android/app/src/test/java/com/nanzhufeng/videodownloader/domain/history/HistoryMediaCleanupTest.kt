package com.nanzhufeng.videodownloader.domain.history

import com.nanzhufeng.videodownloader.core.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class HistoryMediaCleanupTest {
    @Test fun oneActionInspectsAllRecordsIncludingUnrenderedUnmarkedOnes() = runBlocking {
        val rows = (1..80).map { history("item-$it") }
        val inspected = mutableListOf<String>()
        val deleted = mutableListOf<String>()
        val cleanup = HistoryMediaCleanup(load = { rows }, inspector = {
            inspected += it
            if (it.endsWith("item-80")) LocalMediaAvailability.AVAILABLE else LocalMediaAvailability.MISSING
        }, commit = { plan -> deleted += plan.missing.map { it.taskId }; HistoryCleanupCommit(plan.missing.size) })
        val result = cleanup.run()
        assertEquals(79, result.removed)
        assertEquals(80, result.checked)
        assertEquals(80, inspected.size)
        assertFalse("valid file must remain", "item-80" in deleted)
    }


    @Test fun staleMissingFlagsNeverAuthorizeDeletingReadableOrUncertainFiles() = runBlocking {
        val rows = listOf(history("readable", exists = false), history("permission", exists = false),
            history("offline", exists = false), history("gone", exists = true))
        val result = HistoryMediaCleanup({ rows }, inspector = {
            when {
                it.endsWith("readable") -> LocalMediaAvailability.AVAILABLE
                it.endsWith("permission") -> throw SecurityException("revoked grant")
                it.endsWith("offline") -> LocalMediaAvailability.UNKNOWN
                else -> LocalMediaAvailability.MISSING
            }
        }, commit = { plan ->
            assertEquals(listOf("gone"), plan.missing.map { it.taskId })
            assertEquals(listOf("readable"), plan.readable.map { it.taskId })
            HistoryCleanupCommit(removed = 1, restored = 1)
        }).run()
        assertEquals(HistoryCleanupResult(4, 1, 2, restored = 1), result)
    }

    @Test fun anyReadableSegmentOrImageKeepsTheEntireHistoryAndUnknownNeverMeansMissing() = runBlocking {
        val rows = listOf(history("partial", listOf("gone", "valid")),
            history("uncertain", listOf("gone", "unknown")), history("absent", listOf("gone", "also-gone")))
        val result = HistoryMediaCleanup({ rows }, inspector = {
            when (it) {
                "valid" -> LocalMediaAvailability.AVAILABLE
                "unknown" -> LocalMediaAvailability.UNKNOWN
                else -> LocalMediaAvailability.MISSING
            }
        }, commit = { plan ->
            assertEquals(listOf("absent"), plan.missing.map { it.taskId })
            HistoryCleanupCommit(1)
        }).run()
        assertEquals(HistoryCleanupResult(3, 1, 1), result)
    }

    @Test fun cancellationBeforeTheFullScanFinishesCommitsNothing() = runBlocking {
        var committed = false
        try {
            HistoryMediaCleanup({ listOf(history("one"), history("two")) }, inspector = {
                if (it.endsWith("two")) throw kotlinx.coroutines.CancellationException()
                LocalMediaAvailability.MISSING
            }, commit = { committed = true; HistoryCleanupCommit(1) }).run()
            fail("Cancellation must propagate")
        } catch (_: kotlinx.coroutines.CancellationException) {
            assertFalse(committed)
        }
    }

    @Test fun aNewHistoryAndChangedOutputsAreNotRemovedByAnOlderScan() = runBlocking {
        val before = history("changed")
        val store = mutableListOf(before)
        val result = HistoryMediaCleanup(load = { store.toList() }, inspector = {
            store[0] = before.copy(outputUri = "new-output", outputUris = listOf("new-output"))
            store += history("new")
            LocalMediaAvailability.MISSING
        }, commit = { plan ->
            val eligible = plan.missing.filter { snapshot ->
                store.any { matchesHistoryCleanupSnapshot(it, snapshot) }
            }
            store.removeAll(eligible.toSet())
            HistoryCleanupCommit(eligible.size, changed = plan.missing.size - eligible.size)
        }).run()
        assertEquals(0, result.removed)
        assertEquals(1, result.changed)
        assertEquals(listOf("changed", "new"), store.map { it.taskId })
    }

    @Test fun onlyCompletedHistoriesAreConsideredAndNoOutputReferenceCanBeCleaned() = runBlocking {
        val rows = listOf(history("no-output", emptyList()),
            history("failed", emptyList(), status = DownloadTaskStatus.FAILED))
        val result = HistoryMediaCleanup({ rows }, inspector = { error("No URI to inspect") }, commit = {
            assertEquals(listOf("no-output"), it.missing.map { row -> row.taskId })
            HistoryCleanupCommit(1)
        }).run()
        assertEquals(HistoryCleanupResult(1, 1, 0), result)
    }

    @Test fun snapshotGuardIgnoresOnlyCardAvailabilityAndThumbnailProjection() {
        val before = history("one")
        assertTrue(matchesHistoryCleanupSnapshot(before.copy(fileExists = false, thumbnailUrl = "new"), before))
        assertFalse(matchesHistoryCleanupSnapshot(before.copy(isFavorite = true), before))
        assertFalse(matchesHistoryCleanupSnapshot(before.copy(completedAt = 2), before))
        assertFalse(matchesHistoryCleanupSnapshot(before.copy(fileSize = 200), before))
    }

    @Test fun secondFullCleanupIsIdempotentAndKeepsValidHistory() = runBlocking {
        val rows = mutableListOf(history("gone"), history("valid"))
        val cleanup = HistoryMediaCleanup({ rows.toList() }, inspector = {
            if (it.endsWith("valid")) LocalMediaAvailability.AVAILABLE else LocalMediaAvailability.MISSING
        }, commit = { plan -> rows.removeAll(plan.missing.toSet()); HistoryCleanupCommit(plan.missing.size) })
        assertEquals(1, cleanup.run().removed)
        assertEquals(0, cleanup.run().removed)
        assertEquals(listOf("valid"), rows.map { it.taskId })
    }

    private fun history(id: String, uris: List<String> = listOf("content://media/external/video/media/$id"),
                        exists: Boolean = true, status: DownloadTaskStatus = DownloadTaskStatus.COMPLETED) = DownloadHistory(
        taskId = id, platform = DownloadPlatform.GENERIC, contentId = id,
        originalUrl = "https://example.test/$id", title = id, creator = "fixture",
        resolution = ResolutionPreset.UP_TO_720P, finalStatus = status,
        outputUri = uris.firstOrNull(), outputUris = uris, fileSize = 100, fileExists = exists, completedAt = 1,
    )
}
