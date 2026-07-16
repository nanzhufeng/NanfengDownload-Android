package com.nanzhufeng.videodownloader.domain.download

import com.nanzhufeng.videodownloader.core.model.DownloadTaskStatus
import com.nanzhufeng.videodownloader.core.model.QueuedDownload

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

            val sized = active.filter { it.task.totalBytes > 0L }
            if (sized.isEmpty()) {
                return DownloadNotificationState(
                    content = "正在下载 ${active.size} 项",
                    max = 0,
                    value = 0,
                    indeterminate = true,
                )
            }

            val total = sized.sumOf { it.task.totalBytes }
            val downloaded = sized.sumOf { it.task.downloadedBytes.coerceIn(0L, it.task.totalBytes) }
            val progress = ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
            return DownloadNotificationState(
                content = "正在下载 ${active.size} 项 · ${progress}%",
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
