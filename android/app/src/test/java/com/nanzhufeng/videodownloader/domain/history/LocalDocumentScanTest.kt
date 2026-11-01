package com.nanzhufeng.videodownloader.domain.history

import com.nanzhufeng.videodownloader.core.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class LocalDocumentScanTest {
    @Test fun reappearingDocumentInvalidatesDeletionAndNextOperationGetsFreshData() {
        val reference = LocalDocumentReference("primary:root", "primary:root/file.mp4")
        val visible = linkedSetOf<String>()
        fun scan() = LocalDocumentScan(listOf(reference)) { _, _, _ -> visible }
        val first = scan()
        assertEquals(false, first.exists(reference))
        visible += reference.documentId
        assertFalse(first.validate())
        assertEquals(true, scan().exists(reference))
    }

    @Test fun unknownOrDeniedListingIsNeverAbsenceAndOutOfTreeTargetsAreNotQueried() {
        val reference = LocalDocumentReference("primary:root", "primary:root/file.mp4")
        for (query in listOf<(String, String, Set<String>) -> Set<String>?>(
            { _, _, _ -> null }, { _, _, _ -> throw SecurityException() })) {
            val scan = LocalDocumentScan(listOf(reference), query)
            assertNull(scan.exists(reference))
            assertTrue(scan.validate())
        }
        val scan = LocalDocumentScan(listOf(reference)) { _, _, _ -> error("Outside grant") }
        assertNull(scan.exists(LocalDocumentReference("primary:root", "primary:elsewhere/file.mp4")))
    }

    @Test fun cancellationDuringFinalValidationIsPropagated() {
        val reference = LocalDocumentReference("primary:root", "primary:root/file.mp4")
        var cancelled = false
        val scan = LocalDocumentScan(listOf(reference)) { _, _, _ ->
            if (cancelled) throw kotlinx.coroutines.CancellationException()
            emptySet()
        }
        assertEquals(false, scan.exists(reference))
        cancelled = true
        try { scan.validate(); fail("Cancellation must propagate") }
        catch (_: kotlinx.coroutines.CancellationException) { }
    }

    @Test fun seventySevenMissingRecordsShareParentChecksAndPreserveValidOutput() = runBlocking {
        val root = Files.createTempDirectory("local-scan").toFile()
        try {
            File(root, "Download").mkdir()
            val keep = File(root, "keep.mp4").apply { writeText("valid fixture") }
            val refs = (1..77).associate { "gone-$it" to LocalDocumentReference("primary:NF158", "primary:NF158/Download/Site/$it.mp4") }
            val rows = (refs.keys + "keep").map { history(it) }.toMutableList()
            var queries = 0
            val inspector = object : LocalMediaInspector {
                override suspend fun inspect(uri: String): LocalMediaAvailability = error("Use the operation's scope")
                override suspend fun forCleanup(uris: Collection<String>): LocalMediaInspector {
                    val scan = LocalDocumentScan(refs.values) { _, parent, wanted ->
                        queries++
                        Thread.sleep(2) // Controlled provider latency, never a phone timing claim.
                        val directory = when (parent) {
                            "primary:NF158" -> root
                            "primary:NF158/Download" -> File(root, "Download")
                            else -> error("Unexpected directory")
                        }
                        requireNotNull(directory.listFiles()).map { "$parent/${it.name}" }.filter { it in wanted }.toSet()
                    }
                    return object : LocalMediaInspector {
                        override suspend fun inspect(uri: String): LocalMediaAvailability =
                            if (uri == "keep") { keep.inputStream().use { it.read() }; LocalMediaAvailability.AVAILABLE }
                            else if (scan.exists(refs.getValue(uri)) == false) LocalMediaAvailability.MISSING
                            else LocalMediaAvailability.UNKNOWN
                        override suspend fun isSnapshotStillValid() = scan.validate()
                    }
                }
            }
            val start = System.nanoTime()
            val result = HistoryMediaCleanup({ rows.toList() }, inspector) { plan ->
                rows.removeAll(plan.missing.toSet()); HistoryCleanupCommit(plan.missing.size)
            }.run()
            val elapsed = (System.nanoTime() - start) / 1_000_000
            println("CLEANUP_MODEL checked=${result.checked} removed=${result.removed} queries=$queries elapsedMs=$elapsed latencyPerQueryMs=2")
            assertEquals(77, result.removed)
            assertEquals(listOf("keep"), rows.map { it.taskId })
            assertEquals("valid fixture", keep.readText())
            assertTrue("77 records under the same missing directory must not re-list the same parents: $queries", queries <= 3)
        } finally { root.deleteRecursively() }
    }

    private fun history(id: String) = DownloadHistory(
        taskId = id, platform = DownloadPlatform.GENERIC, contentId = id,
        originalUrl = "https://example.test/$id", title = id, creator = "fixture",
        resolution = ResolutionPreset.UP_TO_720P, finalStatus = DownloadTaskStatus.COMPLETED,
        outputUri = id, outputUris = listOf(id), fileSize = 100, fileExists = true, completedAt = 1,
    )
}
