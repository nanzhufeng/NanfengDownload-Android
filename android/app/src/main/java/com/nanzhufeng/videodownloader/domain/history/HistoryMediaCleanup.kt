package com.nanzhufeng.videodownloader.domain.history

import com.nanzhufeng.videodownloader.core.model.DownloadHistory
import com.nanzhufeng.videodownloader.core.model.DownloadTaskStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

enum class LocalMediaAvailability { AVAILABLE, MISSING, UNKNOWN }

fun interface LocalMediaInspector {
    suspend fun inspect(uri: String): LocalMediaAvailability
    suspend fun forCleanup(uris: Collection<String>): LocalMediaInspector = this
    suspend fun isSnapshotStillValid(): Boolean = true
}

data class HistoryCleanupResult(
    val checked: Int, val removed: Int, val uncertain: Int, val changed: Int = 0, val restored: Int = 0,
)

internal data class HistoryCleanupPlan(val missing: List<DownloadHistory>, val readable: List<DownloadHistory>)
internal data class HistoryCleanupCommit(val removed: Int, val changed: Int = 0, val restored: Int = 0)

/** Scans a complete snapshot before committing, independent of rendered cards and persisted flags. */
internal class HistoryMediaCleanup(
    private val load: suspend () -> List<DownloadHistory>,
    private val inspector: LocalMediaInspector,
    private val commit: suspend (HistoryCleanupPlan) -> HistoryCleanupCommit,
) {
    suspend fun run(): HistoryCleanupResult {
        val rows = load().filter { it.finalStatus == DownloadTaskStatus.COMPLETED }.distinctBy { it.taskId }
        val scan = inspector.forCleanup(rows.flatMap { it.outputUris + listOfNotNull(it.outputUri) }
            .filter(String::isNotBlank).distinct())
        val missing = mutableListOf<DownloadHistory>()
        val readable = mutableListOf<DownloadHistory>()
        var uncertain = 0
        for (row in rows) {
            currentCoroutineContext().ensureActive()
            val uris = (row.outputUris + listOfNotNull(row.outputUri)).filter(String::isNotBlank).distinct()
            var available = false
            var unknown = false
            for (uri in uris) {
                currentCoroutineContext().ensureActive()
                val availability = try {
                    scan.inspect(uri)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    LocalMediaAvailability.UNKNOWN
                }
                if (availability == LocalMediaAvailability.AVAILABLE) { available = true; break }
                if (availability == LocalMediaAvailability.UNKNOWN) unknown = true
            }
            when {
                available -> if (!row.fileExists) readable += row
                unknown -> uncertain++
                else -> missing += row
            }
        }
        currentCoroutineContext().ensureActive()
        if (!scan.isSnapshotStillValid()) return HistoryCleanupResult(rows.size, 0, uncertain + missing.size)
        currentCoroutineContext().ensureActive()
        val applied = commit(HistoryCleanupPlan(missing, readable))
        return HistoryCleanupResult(rows.size, applied.removed, uncertain, applied.changed, applied.restored)
    }
}

/** Card-side availability updates are harmless; a changed output, favorite or history identity is not. */
internal fun matchesHistoryCleanupSnapshot(current: DownloadHistory, snapshot: DownloadHistory): Boolean =
    current.copy(fileExists = snapshot.fileExists, thumbnailUrl = snapshot.thumbnailUrl) == snapshot
