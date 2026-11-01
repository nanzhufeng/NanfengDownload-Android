package com.nanzhufeng.videodownloader.domain.history

import com.nanzhufeng.videodownloader.core.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files

class LocalDocumentAbsenceTest {
    @Test fun cleanupRemovesDeletedSubdirectoryWhileAuthorizedRootStillExists() = runBlocking {
        val root = Files.createTempDirectory("saf-child-absence").toFile()
        try {
            val keep = File(root, "keep.mp4").apply { writeText("keep unchanged") }
            val removedDirectory = File(root, "platform").apply { mkdir() }
            File(removedDirectory, "gone.mp4").writeText("deleted fixture")
            assertTrue(removedDirectory.deleteRecursively())
            val rows = mutableListOf(history("gone"), history("keep"))
            val result = HistoryMediaCleanup({ rows.toList() }, inspector = { uri ->
                if (uri == "keep") LocalMediaAvailability.AVAILABLE else confirmLocalDocumentAbsence(
                    mountedAndGranted = true,
                    queryExists = { throw IllegalArgumentException("Not a descendant after parent deletion") },
                    findDocumentPath = { throw IllegalArgumentException("Rejected before locating document") },
                    queryAncestorExists = {
                        // A complete listing of the still-authorized root independently proves absence.
                        requireNotNull(root.listFiles()).any { it.name == "platform" }
                    },
                )
            }, commit = { plan ->
                rows.removeAll(plan.missing.toSet())
                HistoryCleanupCommit(plan.missing.size)
            }).run()
            assertEquals(1, result.removed)
            assertEquals(0, result.uncertain)
            assertEquals(listOf("keep"), rows.map { it.taskId })
            assertEquals("keep unchanged", keep.readText())
        } finally { root.deleteRecursively() }
    }

    @Test fun cleanupRemovesDeletedSafDocumentEvenWhenProviderReturnsNullCursor() = runBlocking {
        val directory = Files.createTempDirectory("saf-absence").toFile()
        try {
            val valid = File(directory, "valid.mp4").apply { writeText("valid fixture") }
            val deleted = File(directory, "deleted.mp4").apply { writeText("old fixture") }
            assertTrue(deleted.delete())
            val rows = mutableListOf(history("gone"), history("keep"))
            val result = HistoryMediaCleanup({ rows.toList() }, inspector = { uri ->
                if (uri == "keep") LocalMediaAvailability.AVAILABLE else confirmLocalDocumentAbsence(
                    mountedAndGranted = true,
                    queryExists = { null }, // Actual DocumentsProvider behavior after a missing-file exception.
                    findDocumentPath = { deleted.inputStream().use { it.read() }; Unit },
                )
            }, commit = { plan ->
                rows.removeAll(plan.missing.toSet())
                HistoryCleanupCommit(plan.missing.size)
            }).run()
            assertEquals(1, result.removed)
            assertEquals(listOf("keep"), rows.map { it.taskId })
            assertEquals("valid fixture", valid.readText())
        } finally { directory.deleteRecursively() }
    }

    @Test fun revokedGrantOrUnmountedStorageDoesNotProbeOrDelete() {
        assertEquals(LocalMediaAvailability.UNKNOWN, confirmLocalDocumentAbsence(false,
            queryExists = { error("Must not query an unauthorized/offline tree") },
            findDocumentPath = { error("Must not locate an unauthorized/offline tree") }))
    }

    @Test fun ancestorEvidenceMustBeCompleteAndSecurityFailuresNeverDelete() {
        for (listing in listOf<() -> Boolean?>({ true }, { null },
            { throw SecurityException() }, { throw java.io.IOException() })) {
            assertEquals(LocalMediaAvailability.UNKNOWN, confirmLocalDocumentAbsence(true,
                queryExists = { null }, findDocumentPath = { throw IllegalArgumentException() },
                queryAncestorExists = listing))
        }
        assertEquals(LocalMediaAvailability.UNKNOWN, confirmLocalDocumentAbsence(true,
            queryExists = { null }, findDocumentPath = { throw SecurityException() },
            queryAncestorExists = { null }))
        assertEquals(LocalMediaAvailability.MISSING, confirmLocalDocumentAbsence(true,
            queryExists = { throw SecurityException("Missing descendant rejected") },
            findDocumentPath = { throw SecurityException("Missing descendant rejected") },
            queryAncestorExists = { false }))
    }

    @Test fun onlyExactDescendantsMayBeResolvedWithinThePersistedGrant() {
        assertEquals(listOf("primary:Download/Allowed", "primary:Download/Allowed/platform",
            "primary:Download/Allowed/platform/gone.mp4"),
            localDocumentAncestry("primary:Download/Allowed", "primary:Download/Allowed/platform/gone.mp4"))
        assertEquals(listOf("primary:", "primary:Movies", "primary:Movies/file.mp4"),
            localDocumentAncestry("primary:", "primary:Movies/file.mp4"))
        for (target in listOf("primary:Download/AllowedElsewhere/file", "other:Download/Allowed/file",
            "primary:Download/Allowed/../file", "primary:Download/Allowed//file", "invalid")) {
            assertNull(localDocumentAncestry("primary:Download/Allowed", target))
        }
    }

    @Test fun explicitEmptyQueryIsMissingAndDoesNotNeedThePathCall() {
        assertEquals(LocalMediaAvailability.MISSING, confirmLocalDocumentAbsence(true,
            queryExists = { false }, findDocumentPath = { error("Absence already confirmed") }))
    }

    @Test fun nullOrUnsupportedPathAndSecurityFailureRemainUnknown() {
        for (probe in listOf<() -> Unit>({}, { throw UnsupportedOperationException() },
            { throw SecurityException("revoked") }, { throw java.io.IOException("temporary") })) {
            assertEquals(LocalMediaAvailability.UNKNOWN,
                confirmLocalDocumentAbsence(true, queryExists = { null }, findDocumentPath = probe))
        }
    }

    @Test fun staleMetadataCannotHideTypedMissingDocumentAndExistingPathCannotAuthorizeDeletion() {
        assertEquals(LocalMediaAvailability.MISSING, confirmLocalDocumentAbsence(true,
            queryExists = { true }, findDocumentPath = { throw FileNotFoundException() }))
        assertEquals(LocalMediaAvailability.UNKNOWN, confirmLocalDocumentAbsence(true,
            queryExists = { true }, findDocumentPath = {}))
    }

    @Test fun cancellationIsNeverConvertedIntoAbsence() {
        try {
            confirmLocalDocumentAbsence(true, queryExists = { null },
                findDocumentPath = { throw kotlinx.coroutines.CancellationException() })
            fail("Cancellation must propagate")
        } catch (_: kotlinx.coroutines.CancellationException) { }
    }

    @Test fun rejectedDescendantQueryStillUsesTypedAbsenceProof() {
        assertEquals(LocalMediaAvailability.MISSING, confirmLocalDocumentAbsence(true,
            queryExists = { throw IllegalArgumentException("Provider rejected descendant") },
            findDocumentPath = { throw FileNotFoundException() }))
        assertEquals(LocalMediaAvailability.UNKNOWN, confirmLocalDocumentAbsence(true,
            queryExists = { throw IllegalArgumentException("Provider rejected descendant") },
            findDocumentPath = { throw IllegalArgumentException("Still inconclusive") }))
    }

    private fun history(id: String) = DownloadHistory(
        taskId = id, platform = DownloadPlatform.GENERIC, contentId = id,
        originalUrl = "https://example.test/$id", title = id, creator = "fixture",
        resolution = ResolutionPreset.UP_TO_720P, finalStatus = DownloadTaskStatus.COMPLETED,
        outputUri = id, outputUris = listOf(id), fileSize = 100, fileExists = true, completedAt = 1,
    )
}
