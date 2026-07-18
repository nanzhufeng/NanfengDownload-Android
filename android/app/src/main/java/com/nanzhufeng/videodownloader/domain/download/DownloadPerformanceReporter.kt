package com.nanzhufeng.videodownloader.domain.download

fun interface DownloadPerformanceReporter {
    fun record(taskId: String, stage: String, elapsedMillis: Long)

    companion object {
        val NONE = DownloadPerformanceReporter { _, _, _ -> }
    }
}

internal suspend fun <T> measureDownloadStage(
    taskId: String,
    stage: String,
    reporter: DownloadPerformanceReporter,
    nowNanos: () -> Long,
    block: suspend () -> T,
): T {
    val started = nowNanos()
    return try {
        block()
    } finally {
        reporter.record(
            taskId = taskId,
            stage = stage,
            elapsedMillis = ((nowNanos() - started).coerceAtLeast(0L) / 1_000_000L),
        )
    }
}
