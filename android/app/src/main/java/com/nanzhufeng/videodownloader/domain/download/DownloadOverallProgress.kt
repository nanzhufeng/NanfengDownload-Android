package com.nanzhufeng.videodownloader.domain.download

import com.nanzhufeng.videodownloader.core.model.DownloadProcessingStage
import com.nanzhufeng.videodownloader.core.model.DownloadTask
import com.nanzhufeng.videodownloader.core.model.DownloadTaskStatus
import com.nanzhufeng.videodownloader.core.model.QueuedDownload
import kotlin.math.roundToInt

/**
 * Progress for the complete task pipeline, not only the network transfer.
 *
 * A task reaches 100% only after every output has been published and the
 * repository has transitioned it to COMPLETED. The intermediate ranges leave
 * explicit room for local processing and MediaStore publication.
 */
object DownloadOverallProgress {
    fun fraction(task: DownloadTask): Float {
        if (task.status == DownloadTaskStatus.COMPLETED) return 1f
        if (task.status == DownloadTaskStatus.SKIPPED) return 1f
        if (task.status == DownloadTaskStatus.WAITING) return 0f
        if (task.status == DownloadTaskStatus.PARSING) return PARSING_PROGRESS

        val value = when (task.processingStage) {
            DownloadProcessingStage.NETWORK_MEDIA,
            DownloadProcessingStage.NETWORK_AUDIO,
            DownloadProcessingStage.NETWORK_VIDEO_TO_AUDIO,
            DownloadProcessingStage.NONE,
            -> networkProgress(task)

            DownloadProcessingStage.MERGING,
            DownloadProcessingStage.TRANSCODING,
            DownloadProcessingStage.VIDEO_SEGMENTING,
            -> LOCAL_PROCESSING_START +
                LOCAL_PROCESSING_SPAN * task.processingProgressPercent.coerceIn(0, 100) / 100f

            DownloadProcessingStage.VALIDATING -> VALIDATING_PROGRESS
            DownloadProcessingStage.PUBLISHING -> PUBLISHING_START +
                PUBLISHING_SPAN * task.processingProgressPercent.coerceIn(0, 100) / 100f
        }
        return value.coerceIn(0f, LAST_UNFINISHED_PROGRESS)
    }

    fun percent(task: DownloadTask): Int =
        (fraction(task) * 100f).roundToInt()
            .coerceIn(0, if (task.status == DownloadTaskStatus.COMPLETED) 100 else 99)

    fun queueFraction(queue: List<QueuedDownload>): Float {
        val tracked = queue.filter { queued ->
            queued.task.status in setOf(
                DownloadTaskStatus.WAITING,
                DownloadTaskStatus.PARSING,
                DownloadTaskStatus.DOWNLOADING,
                DownloadTaskStatus.VALIDATING,
                DownloadTaskStatus.PAUSED,
                DownloadTaskStatus.WAITING_NETWORK,
            )
        }
        if (tracked.isEmpty()) return 0f
        return tracked.map { fraction(it.task).toDouble() }.average().toFloat().coerceIn(0f, 1f)
    }

    private fun networkProgress(task: DownloadTask): Float {
        val transferFraction = if (task.totalBytes > 0L) {
            task.downloadedBytes.toDouble()
                .div(task.totalBytes.toDouble())
                .coerceIn(0.0, 1.0)
                .toFloat()
        } else {
            0f
        }
        return NETWORK_START + NETWORK_SPAN * transferFraction
    }

    private const val PARSING_PROGRESS = 0.02f
    private const val NETWORK_START = 0.05f
    private const val NETWORK_SPAN = 0.75f
    private const val LOCAL_PROCESSING_START = 0.82f
    private const val LOCAL_PROCESSING_SPAN = 0.13f
    private const val VALIDATING_PROGRESS = 0.96f
    private const val PUBLISHING_START = 0.96f
    private const val PUBLISHING_SPAN = 0.03f
    private const val LAST_UNFINISHED_PROGRESS = 0.99f
}
