package com.nanzhufeng.videodownloader.domain.download

import com.nanzhufeng.videodownloader.core.model.*
import java.util.Locale

internal enum class DownloadIslandTone { RUNNING, SUCCESS, WARNING, ERROR }
internal data class DownloadIslandState(
    val taskId: String,
    val title: String,
    val stage: String,
    val percentage: Int?,
    val detail: String,
    val speed: String = "",
    val ongoing: Boolean = true,
    val tone: DownloadIslandTone = DownloadIslandTone.RUNNING,
) {
    val compactText: String get() = stage + (percentage?.let { " · $it%" } ?: "") +
        (speed.takeIf(String::isNotBlank)?.let { " · $it" } ?: "")
    val notificationText: String get() = listOf(title, compactText, detail).filter(String::isNotBlank).joinToString("\n")
    companion object {
        fun from(queue: List<QueuedDownload>): DownloadIslandState? {
            val active = queue.filter { it.task.status in setOf(DownloadTaskStatus.PARSING,
                DownloadTaskStatus.DOWNLOADING, DownloadTaskStatus.VALIDATING) }.sortedBy { it.task.sortOrder }
            val first = active.firstOrNull() ?: return null
            val task = first.task
            val processing = task.processingStage in setOf(DownloadProcessingStage.MERGING,
                DownloadProcessingStage.TRANSCODING, DownloadProcessingStage.VIDEO_SEGMENTING,
                DownloadProcessingStage.VALIDATING, DownloadProcessingStage.PUBLISHING)
            val stage = when {
                task.status == DownloadTaskStatus.PARSING -> "解析来源"
                else -> when (task.processingStage) {
                    DownloadProcessingStage.MERGING -> "合并中"
                    DownloadProcessingStage.TRANSCODING -> "转换中"
                    DownloadProcessingStage.VIDEO_SEGMENTING -> "分段中"
                    DownloadProcessingStage.VALIDATING -> "校验中"
                    DownloadProcessingStage.PUBLISHING -> "保存中"
                    DownloadProcessingStage.NETWORK_AUDIO -> "下载音频"
                    else -> if (task.status == DownloadTaskStatus.VALIDATING) "校验与保存" else "下载中"
                }
            }
            val percentage = when {
                task.status == DownloadTaskStatus.PARSING -> null
                processing -> task.processingProgressPercent.coerceIn(0, 99)
                task.totalBytes > 0 -> (task.downloadedBytes.coerceAtLeast(0).toDouble() /
                    task.totalBytes * 100).toInt().coerceIn(0, 99)
                else -> null
            }
            val speed = if (processing || task.status != DownloadTaskStatus.DOWNLOADING) "" else
                task.speedBytesPerSecond.coerceAtLeast(0).takeIf { it > 0 }?.let { "${bytes(it)}/s" }.orEmpty()
            val amount = bytes(task.downloadedBytes) + (task.totalBytes.takeIf { it > 0 }?.let { " / ${bytes(it)}" } ?: " · 总量待确认")
            val detail = listOf(if (active.size > 1) "正在处理 ${active.size} 项" else when (task.resolution) { ResolutionPreset.BEST -> "最佳画质"; ResolutionPreset.AUDIO_MP3 -> "MP3音频"; else -> task.resolution.name.replace("UP_TO_", "") },
                amount, if (processing) "处理完成并保存后才会进入历史" else task.remainingSeconds?.takeIf { it >= 0 }?.let { "预计剩余 ${it / 60}分${it % 60}秒" }.orEmpty())
                .filter(String::isNotBlank).joinToString(" · ")
            return DownloadIslandState(task.taskId, first.media.title, stage, percentage, detail, speed)
        }
        fun finished(completed: Int, skipped: Int, failed: Int) = DownloadIslandState(
            taskId = "finished", title = "南枫下载", stage = when { failed > 0 -> "下载有失败"; completed > 0 -> "下载完成"; else -> "已跳过重复项" },
            percentage = null, detail = completionNotificationText(completed, skipped, failed), ongoing = false,
            tone = when { failed > 0 -> DownloadIslandTone.ERROR; completed > 0 -> DownloadIslandTone.SUCCESS; else -> DownloadIslandTone.WARNING })
        private fun bytes(value: Long): String = when {
            value >= 1_000_000_000 -> String.format(Locale.ROOT, "%.1f GB", value / 1_000_000_000.0)
            value >= 1_000_000 -> String.format(Locale.ROOT, "%.1f MB", value / 1_000_000.0)
            value >= 1_000 -> String.format(Locale.ROOT, "%.0f KB", value / 1_000.0)
            else -> "${value.coerceAtLeast(0)} B"
        }
    }
}

internal class DownloadIslandDisplayController {
    var expanded = false; private set
    private var taskId: String? = null
    fun onTask(id: String) { if (taskId != id) collapse(); taskId = id }
    fun toggle() { expanded = !expanded }
    fun collapse() { expanded = false }
}

internal class DownloadIslandUpdateGate {
    private var last: DownloadIslandState? = null
    private var at = 0L
    fun publish(state: DownloadIslandState, now: Long): Boolean {
        val previous = last
        if (previous == state) return false
        if (previous == null || previous.taskId != state.taskId || previous.stage != state.stage ||
            !state.ongoing || now - at >= 1000) { last = state; at = now; return true }
        return false
    }
}
