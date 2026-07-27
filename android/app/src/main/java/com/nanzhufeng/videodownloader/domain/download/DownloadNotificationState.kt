package com.nanzhufeng.videodownloader.domain.download

import com.nanzhufeng.videodownloader.core.model.DownloadTaskStatus
import com.nanzhufeng.videodownloader.core.model.QueuedDownload
import kotlin.math.roundToInt

data class DownloadNotificationState(
    val content: String,
    val max: Int,
    val value: Int,
    val indeterminate: Boolean,
) {
    companion object {
        fun from(queue: List<QueuedDownload>): DownloadNotificationState {
            val active = queue.filter {
                it.task.status in setOf(
                    DownloadTaskStatus.PARSING,
                    DownloadTaskStatus.DOWNLOADING,
                    DownloadTaskStatus.VALIDATING,
                )
            }
            if (active.isEmpty()) return preparing()

            val measurable = active.any {
                it.task.totalBytes > 0L ||
                    it.task.processingStage !in setOf(
                        com.nanzhufeng.videodownloader.core.model.DownloadProcessingStage.NONE,
                        com.nanzhufeng.videodownloader.core.model.DownloadProcessingStage.NETWORK_MEDIA,
                        com.nanzhufeng.videodownloader.core.model.DownloadProcessingStage.NETWORK_AUDIO,
                        com.nanzhufeng.videodownloader.core.model.DownloadProcessingStage.NETWORK_VIDEO_TO_AUDIO,
                    )
            }
            if (!measurable) {
                return DownloadNotificationState(
                    content = "正在下载 ${active.size} 项",
                    max = 0,
                    value = 0,
                    indeterminate = true,
                )
            }

            val progress = (
                active.map { DownloadOverallProgress.fraction(it.task).toDouble() }
                    .average() * 100.0
                ).roundToInt().coerceIn(0, 99)
            return DownloadNotificationState(
                content = "正在处理 ${active.size} 项 · ${progress}%",
                max = 100,
                value = progress,
                indeterminate = false,
            )
        }

        fun preparing() = DownloadNotificationState(
            content = "正在准备下载",
            max = 0,
            value = 0,
            indeterminate = true,
        )
    }
}
