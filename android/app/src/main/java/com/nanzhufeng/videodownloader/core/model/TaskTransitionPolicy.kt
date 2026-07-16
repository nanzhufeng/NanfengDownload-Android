package com.nanzhufeng.videodownloader.core.model

object TaskTransitionPolicy {
    private val allowed = mapOf(
        DownloadTaskStatus.WAITING to setOf(
            DownloadTaskStatus.PARSING,
            DownloadTaskStatus.SKIPPED,
            DownloadTaskStatus.CANCELLED,
        ),
        DownloadTaskStatus.PARSING to setOf(
            DownloadTaskStatus.DOWNLOADING,
            DownloadTaskStatus.FAILED,
            DownloadTaskStatus.SKIPPED,
            DownloadTaskStatus.CANCELLED,
        ),
        DownloadTaskStatus.DOWNLOADING to setOf(
            DownloadTaskStatus.VALIDATING,
            DownloadTaskStatus.PAUSED,
            DownloadTaskStatus.WAITING_NETWORK,
            DownloadTaskStatus.FAILED,
            DownloadTaskStatus.CANCELLED,
        ),
        DownloadTaskStatus.PAUSED to setOf(
            DownloadTaskStatus.DOWNLOADING,
            DownloadTaskStatus.CANCELLED,
        ),
        DownloadTaskStatus.WAITING_NETWORK to setOf(
            DownloadTaskStatus.DOWNLOADING,
            DownloadTaskStatus.FAILED,
            DownloadTaskStatus.CANCELLED,
        ),
        DownloadTaskStatus.VALIDATING to setOf(
            DownloadTaskStatus.COMPLETED,
            DownloadTaskStatus.FAILED,
        ),
    )

    fun canTransition(from: DownloadTaskStatus, to: DownloadTaskStatus): Boolean =
        to in allowed[from].orEmpty()

    fun requireTransition(from: DownloadTaskStatus, to: DownloadTaskStatus) {
        require(canTransition(from, to)) { "不允许的任务状态转换：$from -> $to" }
    }
}
