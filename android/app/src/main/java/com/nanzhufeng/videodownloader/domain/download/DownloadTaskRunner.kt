package com.nanzhufeng.videodownloader.domain.download

import com.nanzhufeng.videodownloader.core.model.DownloadHistory
import com.nanzhufeng.videodownloader.core.model.DownloadTaskStatus
import com.nanzhufeng.videodownloader.core.model.MediaItem
import com.nanzhufeng.videodownloader.core.model.QueuedDownload
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
import com.nanzhufeng.videodownloader.data.repository.DownloadRepository
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.CancellationException
import javax.net.ssl.SSLException

data class ResolvedMedia(
    val videoUrl: String,
    val audioUrl: String?,
    val videoExtension: String,
    val audioExtension: String?,
    val headers: Map<String, String>,
)

data class PreparedMedia(
    val file: File,
    val mimeType: String,
)

data class StoredMedia(
    val uri: String,
    val fileSize: Long,
)

interface TaskMediaResolver {
    suspend fun resolve(media: MediaItem, resolution: ResolutionPreset): ResolvedMedia
}

interface MediaTransfer {
    suspend fun download(
        task: QueuedDownload,
        source: ResolvedMedia,
        onProgress: suspend (
            downloadedBytes: Long,
            totalBytes: Long,
            speedBytesPerSecond: Long,
            remainingSeconds: Long?,
        ) -> Unit,
    ): PreparedMedia
}

interface DownloadOutputStore {
    suspend fun findExisting(media: MediaItem, resolution: ResolutionPreset): StoredMedia?

    suspend fun uriExists(uri: String): Boolean

    suspend fun publish(
        media: MediaItem,
        resolution: ResolutionPreset,
        prepared: PreparedMedia,
    ): StoredMedia
}

sealed interface TaskRunResult {
    data object Idle : TaskRunResult
    data object Completed : TaskRunResult
    data object Skipped : TaskRunResult
    data object WaitingForNetwork : TaskRunResult
    data object Failed : TaskRunResult
}

class DownloadTaskRunner(
    private val repository: DownloadRepository,
    private val resolver: TaskMediaResolver,
    private val transfer: MediaTransfer,
    private val outputStore: DownloadOutputStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun runNext(): TaskRunResult {
        val queued = repository.nextSelectedRunnable() ?: return TaskRunResult.Idle
        val completed = repository.findCompleted(
            queued.media.platform,
            queued.media.contentId,
            queued.task.resolution,
        )
        val existing = when {
            completed?.outputUri != null && outputStore.uriExists(completed.outputUri) ->
                StoredMedia(completed.outputUri, completed.fileSize)

            else -> outputStore.findExisting(queued.media, queued.task.resolution)
        }
        if (existing != null) {
            repository.transition(queued.task.taskId, DownloadTaskStatus.SKIPPED)
            repository.archiveTerminal(queued.toHistory(DownloadTaskStatus.SKIPPED, existing))
            return TaskRunResult.Skipped
        }

        var status = queued.task.status
        try {
            repository.transition(queued.task.taskId, DownloadTaskStatus.PARSING)
            status = DownloadTaskStatus.PARSING
            val source = resolver.resolve(queued.media, queued.task.resolution)

            repository.transition(queued.task.taskId, DownloadTaskStatus.DOWNLOADING)
            status = DownloadTaskStatus.DOWNLOADING
            val prepared = transfer.download(queued, source) { downloaded, total, speed, remaining ->
                repository.updateTransfer(
                    taskId = queued.task.taskId,
                    downloadedBytes = downloaded,
                    totalBytes = total,
                    speedBytesPerSecond = speed,
                    remainingSeconds = remaining,
                )
            }

            repository.transition(queued.task.taskId, DownloadTaskStatus.VALIDATING)
            status = DownloadTaskStatus.VALIDATING
            val stored = outputStore.publish(queued.media, queued.task.resolution, prepared)
            repository.transition(queued.task.taskId, DownloadTaskStatus.COMPLETED)
            repository.archiveTerminal(queued.toHistory(DownloadTaskStatus.COMPLETED, stored))
            return TaskRunResult.Completed
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val terminal = if (NetworkErrorClassifier.isRetryable(error)) {
                DownloadTaskStatus.WAITING_NETWORK
            } else {
                DownloadTaskStatus.FAILED
            }
            if (status != terminal) {
                repository.transition(queued.task.taskId, terminal)
            }
            if (terminal == DownloadTaskStatus.FAILED) {
                repository.archiveTerminal(queued.toHistory(terminal, null))
            }
            return if (terminal == DownloadTaskStatus.WAITING_NETWORK) {
                TaskRunResult.WaitingForNetwork
            } else {
                TaskRunResult.Failed
            }
        }
    }

    private fun QueuedDownload.toHistory(
        finalStatus: DownloadTaskStatus,
        stored: StoredMedia?,
    ) = DownloadHistory(
        taskId = task.taskId,
        platform = media.platform,
        contentId = media.contentId,
        originalUrl = media.originalUrl,
        title = media.title,
        creator = media.creator,
        resolution = task.resolution,
        finalStatus = finalStatus,
        outputUri = stored?.uri,
        fileSize = stored?.fileSize ?: 0L,
        fileExists = stored != null,
        completedAt = clock(),
    )
}

object NetworkErrorClassifier {
    fun isRetryable(error: Throwable): Boolean = generateSequence(error) { it.cause }
        .any { cause ->
            val message = cause.message.orEmpty()
            cause is SocketTimeoutException ||
                cause is UnknownHostException ||
                cause is ConnectException ||
                cause is SSLException ||
                cause is RetryableNetworkException ||
                (cause is IOException && message.contains("connection", ignoreCase = true)) ||
                RETRYABLE_MESSAGE_PARTS.any { part -> message.contains(part, ignoreCase = true) }
        }

    private val RETRYABLE_MESSAGE_PARTS = listOf(
        "timed out",
        "timeout",
        "connection closed",
        "connection reset",
        "network is unreachable",
        "temporary failure",
        "urlopen error",
        "ssl",
    )
}

class RetryableNetworkException(message: String, cause: Throwable? = null) : IOException(message, cause)
