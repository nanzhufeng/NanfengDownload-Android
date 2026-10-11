package com.nanzhufeng.videodownloader.core.ui

import com.nanzhufeng.videodownloader.core.model.DownloadHistory
import com.nanzhufeng.videodownloader.core.model.DownloadTaskStatus

/** Presentation-only observation; task/publication truth remains owned by the repository. */
internal class DownloadCompletionNotices {
    private var initialized = false
    private val known = mutableSetOf<String>()

    fun onSnapshot(history: List<DownloadHistory>): IslandNotice? {
        val completed = history.filter { it.finalStatus == DownloadTaskStatus.COMPLETED }
        val newest = if (initialized) completed.firstOrNull {
            it.taskId !in known && it.fileExists && it.fileSize > 0L && it.outputUris.isNotEmpty()
        } else null
        known.addAll(completed.map { it.taskId })
        initialized = true
        return newest?.let { IslandNotice("下载完成", "${it.title}\n文件已保存，可在历史中查看。", IslandTone.SUCCESS) }
    }
}
