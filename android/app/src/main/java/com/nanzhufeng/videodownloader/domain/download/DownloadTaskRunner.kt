package com.nanzhufeng.videodownloader.domain.download

import com.nanzhufeng.videodownloader.core.model.DownloadHistory
import com.nanzhufeng.videodownloader.core.model.DownloadFailureType
import com.nanzhufeng.videodownloader.core.model.DownloadTaskStatus
import com.nanzhufeng.videodownloader.core.model.DownloadProcessingStage
import com.nanzhufeng.videodownloader.core.model.MediaItem
import com.nanzhufeng.videodownloader.core.model.QueuedDownload
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
import com.nanzhufeng.videodownloader.data.repository.DownloadRepository
import com.nanzhufeng.videodownloader.data.settings.FileNameRule
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import com.nanzhufeng.videodownloader.probe.HttpDownloadException
import java.util.concurrent.CancellationException
import javax.net.ssl.SSLException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ResolvedMedia(
    val videoUrl: String,
    val audioUrl: String?,
    val videoExtension: String,
    val videoSizeBytes: Long = 0L,
    val audioExtension: String?,
    val headers: Map<String, String>,
    val audioFromVideoSource: Boolean = false,
    val reprobeCount: Int = 0,
)

data class PreparedMedia(
    val file: File,
    val mimeType: String,
    val additionalFiles: List<File> = emptyList(),
) {
    val files: List<File>
        get() = listOf(file) + additionalFiles
}

data class StoredMedia(
    val uri: String,
    val fileSize: Long,
    val additionalUris: List<String> = emptyList(),
) {
    val uris: List<String>
        get() = listOf(uri) + additionalUris
}

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

    suspend fun findExisting(
        media: MediaItem,
        resolution: ResolutionPreset,
        saveTreeUri: String?,
        fileNameRule: FileNameRule,
        audioSegmentCount: Int = 1,
    ): StoredMedia? = findExisting(media, resolution)

    suspend fun uriExists(uri: String): Boolean

    suspend fun publish(
        media: MediaItem,
        resolution: ResolutionPreset,
        prepared: PreparedMedia,
    ): StoredMedia

    suspend fun publish(
        media: MediaItem,
        resolution: ResolutionPreset,
        prepared: PreparedMedia,
        saveTreeUri: String?,
        fileNameRule: FileNameRule,
        audioSegmentCount: Int = 1,
    ): StoredMedia = publish(media, resolution, prepared)
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
    private val performanceReporter: DownloadPerformanceReporter = DownloadPerformanceReporter.NONE,
    private val monotonicNanos: () -> Long = System::nanoTime,
) {
    private val claimMutex = Mutex()

    suspend fun runNext(): TaskRunResult {
        val queued = claimMutex.withLock {
            val next = repository.nextSelectedRunnable() ?: return@withLock null
            repository.transition(next.task.taskId, DownloadTaskStatus.PARSING)
            next
        } ?: return TaskRunResult.Idle
        val completed = repository.findCompleted(
            queued.media.platform,
            queued.media.contentId,
            queued.task.resolution,
            queued.task.audioSegmentCount,
        )
        val completedUris = completed?.outputUris.orEmpty()
        var completedFilesExist = completedUris.isNotEmpty()
        for (uri in completedUris) {
            if (!outputStore.uriExists(uri)) {
                completedFilesExist = false
                break
            }
        }
        val existing = when {
            completed != null && completedFilesExist ->
                StoredMedia(
                    uri = completedUris.first(),
                    fileSize = completed.fileSize,
                    additionalUris = completedUris.drop(1),
                )

            completed != null -> outputStore.findExisting(
                queued.media,
                queued.task.resolution,
                queued.task.saveTreeUri,
                queued.task.fileNameRule,
                queued.task.audioSegmentCount,
            )

            else -> null
        }
        if (existing != null) {
            repository.transition(queued.task.taskId, DownloadTaskStatus.SKIPPED)
            repository.archiveTerminal(queued.toHistory(DownloadTaskStatus.SKIPPED, existing))
            return TaskRunResult.Skipped
        }

        var status = DownloadTaskStatus.PARSING
        try {
            var reprobeCount = 0
            var prepared: PreparedMedia
            while (true) {
                val source = measureDownloadStage(
                    taskId = queued.task.taskId,
                    stage = if (reprobeCount == 0) "resolve" else "reprobe_source",
                    reporter = performanceReporter,
                    nowNanos = monotonicNanos,
                ) {
                    resolver.resolve(queued.media, queued.task.resolution)
                        .copy(reprobeCount = reprobeCount)
                }

                if (status == DownloadTaskStatus.PARSING) {
                    repository.transition(queued.task.taskId, DownloadTaskStatus.DOWNLOADING)
                    status = DownloadTaskStatus.DOWNLOADING
                }
                try {
                    prepared = measureDownloadStage(
                        taskId = queued.task.taskId,
                        stage = "prepare_media",
                        reporter = performanceReporter,
                        nowNanos = monotonicNanos,
                    ) {
                        transfer.download(queued, source) { downloaded, total, speed, remaining ->
                            repository.updateTransfer(
                                taskId = queued.task.taskId,
                                downloadedBytes = downloaded,
                                totalBytes = total,
                                speedBytesPerSecond = speed,
                                remainingSeconds = remaining,
                            )
                        }
                    }
                    break
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    if (reprobeCount >= MAX_SOURCE_REPROBES || !TransferReprobePolicy.shouldReprobe(error)) {
                        throw error
                    }
                    reprobeCount += 1
                }
            }

            repository.updateProcessing(
                queued.task.taskId,
                DownloadProcessingStage.VALIDATING,
                0,
            )
            repository.transition(queued.task.taskId, DownloadTaskStatus.VALIDATING)
            status = DownloadTaskStatus.VALIDATING
            repository.updateProcessing(
                queued.task.taskId,
                DownloadProcessingStage.PUBLISHING,
                0,
            )
            val stored = measureDownloadStage(
                taskId = queued.task.taskId,
                stage = "publish",
                reporter = performanceReporter,
                nowNanos = monotonicNanos,
            ) {
                outputStore.publish(
                    queued.media,
                    queued.task.resolution,
                    prepared,
                    queued.task.saveTreeUri,
                    queued.task.fileNameRule,
                    queued.task.audioSegmentCount,
                )
            }
            repository.transition(queued.task.taskId, DownloadTaskStatus.COMPLETED)
            repository.updateProcessing(
                queued.task.taskId,
                DownloadProcessingStage.NONE,
                100,
            )
            repository.archiveTerminal(queued.toHistory(DownloadTaskStatus.COMPLETED, stored))
            return TaskRunResult.Completed
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val retryable = NetworkErrorClassifier.shouldWaitForNetwork(error)
            val terminal = if (retryable) {
                DownloadTaskStatus.WAITING_NETWORK
            } else {
                DownloadTaskStatus.FAILED
            }
            val failureType = if (retryable) {
                DownloadFailureType.NETWORK
            } else {
                status.toFailureType()
            }
            val errorSummary = error.toErrorSummary()
            if (status != terminal) {
                repository.transitionWithProblem(
                    taskId = queued.task.taskId,
                    to = terminal,
                    failureType = failureType,
                    errorSummary = errorSummary,
                )
            }
            if (terminal == DownloadTaskStatus.FAILED) {
                repository.archiveTerminal(
                    queued.toHistory(
                        finalStatus = terminal,
                        stored = null,
                        failureType = failureType,
                        errorSummary = errorSummary,
                    ),
                )
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
        failureType: DownloadFailureType? = null,
        errorSummary: String? = null,
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
        failureType = failureType,
        errorSummary = errorSummary,
        outputUris = stored?.uris.orEmpty(),
        audioSegmentCount = task.audioSegmentCount.coerceIn(1, 20),
    )

    private fun DownloadTaskStatus.toFailureType(): DownloadFailureType = when (this) {
        DownloadTaskStatus.PARSING -> DownloadFailureType.SOURCE
        DownloadTaskStatus.DOWNLOADING -> DownloadFailureType.TRANSFER
        DownloadTaskStatus.VALIDATING -> DownloadFailureType.OUTPUT
        else -> DownloadFailureType.UNKNOWN
    }

    private fun Throwable.toErrorSummary(): String = generateSequence(this) { it.cause }
        .mapNotNull { it.message?.trim()?.takeIf(String::isNotEmpty) }
        .firstOrNull()
        ?.replace(Regex("\\s+"), " ")
        ?.take(MAX_ERROR_SUMMARY_LENGTH)
        ?: "下载任务失败，未返回具体原因"

    private companion object {
        const val MAX_ERROR_SUMMARY_LENGTH = 400
        const val MAX_SOURCE_REPROBES = 1
    }
}

object TransferReprobePolicy {
    fun shouldReprobe(error: Throwable): Boolean = generateSequence(error) { it.cause }
        .any { cause ->
            (cause is HttpDownloadException && cause.statusCode in REFRESHABLE_HTTP_STATUSES) ||
                NetworkErrorClassifier.isRetryable(cause)
        }

    private val REFRESHABLE_HTTP_STATUSES = setOf(401, 403, 404, 410, 416)
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

    /**
     * A stream which is still truncated after all connection retries and a
     * fresh source probe needs an explicit retry, not an endless network wait.
     */
    fun shouldWaitForNetwork(error: Throwable): Boolean =
        isRetryable(error) && generateSequence(error) { it.cause }.none { cause ->
            val message = cause.message.orEmpty()
            EXHAUSTED_STREAM_PARTS.any { part -> message.contains(part, ignoreCase = true) }
        }

    private val RETRYABLE_MESSAGE_PARTS = listOf(
        "timed out",
        "timeout",
        "connection closed",
        "connection reset",
        "unexpected end of stream",
        "premature eof",
        "stream was reset",
        "network is unreachable",
        "temporary failure",
        "urlopen error",
        "ssl",
    )

    private val EXHAUSTED_STREAM_PARTS = listOf(
        "unexpected end of stream",
        "premature eof",
        "stream was reset",
        "分段连接提前结束",
        "下载连接提前结束",
    )
}

class RetryableNetworkException(message: String, cause: Throwable? = null) : IOException(message, cause)
