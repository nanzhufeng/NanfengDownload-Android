package com.nanzhufeng.videodownloader.domain.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nanzhufeng.videodownloader.R
import com.nanzhufeng.videodownloader.NanzhufengApplication
import com.nanzhufeng.videodownloader.MainActivity
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ForegroundDownloadWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result = coroutineScope {
        val container = (applicationContext as NanzhufengApplication).container
        // Check the app's actual connection state, rather than WorkManager's
        // CONNECTED constraint (which also requires system validation probes).
        if (!container.networkAvailable.first()) return@coroutineScope Result.retry()
        container.downloads.recoverInterruptedTasks()
        setForeground(createForegroundInfo(DownloadNotificationState.preparing()))
        val latest = AtomicReference<DownloadIslandState?>(null)
        val gate = DownloadIslandUpdateGate()
        val island = DownloadIslandOverlay(applicationContext,
            onOpen = { applicationContext.startActivity(Intent(applicationContext, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_DOWNLOADS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)) },
            onCancel = { taskId -> launch { container.downloadEngine.stop(taskId) } },
        )
        val publishMutex = Mutex()
        suspend fun publish(state: DownloadIslandState) = publishMutex.withLock {
            if (state != latest.get() || !gate.publish(state, SystemClock.elapsedRealtime())) return@withLock false
            try {
                setForeground(createForegroundInfo(DownloadNotificationState(
                    state.compactText, 100, state.percentage ?: 0, state.percentage == null,
                ), state))
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: RuntimeException) {
                // The initial foreground promotion already succeeded. A presentation update
                // (for example a revoked notification permission) cannot cancel the transfer.
                android.util.Log.w("DownloadIsland", "Notification update unavailable")
            }
            island.update(state)
            true
        }
        val foregroundUpdates = launch {
            container.downloads.activeTasks.collect { queue ->
                val state = DownloadIslandState.from(queue)
                latest.set(state)
                if (state != null) publish(state) else island.hide()
            }
        }
        val visibilityUpdates = launch {
            while (true) {
                latest.get()?.let { publish(it); island.update(it) }
                delay(1000)
            }
        }
        var terminal: DownloadIslandState? = null
        try {
            val settings = container.settings.settings.first()
            val lanes = (0 until settings.maxConcurrentDownloads).map {
                async {
                    var completed = 0
                    var skipped = 0
                    var failed = 0
                    var waitingForNetwork = false
                    while (!isStopped) {
                        when (container.taskRunner.runNext(allowNetworkWaiting = settings.autoResumeNetwork)) {
                            TaskRunResult.Completed -> completed++
                            TaskRunResult.Skipped -> skipped++
                            TaskRunResult.Failed -> failed++

                            TaskRunResult.WaitingForNetwork -> {
                                waitingForNetwork = true
                                break
                            }
                            TaskRunResult.Idle -> break
                        }
                    }
                    LaneResult(completed, skipped, failed, waitingForNetwork)
                }
            }
            val laneResults = lanes.awaitAll()
            val completedCount = laneResults.sumOf(LaneResult::completed)
            val skippedCount = laneResults.sumOf(LaneResult::skipped)
            val failedCount = laneResults.sumOf(LaneResult::failed)
            if (completedCount + skippedCount + failedCount > 0) {
                terminal = DownloadIslandState.finished(completedCount, skippedCount, failedCount)
                runCatching { showCompletionNotification(
                    completionNotificationText(
                        completedCount = completedCount,
                        skippedCount = skippedCount,
                        failedCount = failedCount,
                    ),
                ) }.onFailure { android.util.Log.w("DownloadIsland", "Completion notification unavailable") }
            }
            if (laneResults.any(LaneResult::waitingForNetwork)) {
                terminal = DownloadIslandState("network-wait", "南枫下载", "等待网络", null,
                    if (settings.autoResumeNetwork) "网络恢复后继续，已下载部分保留" else "已下载部分保留，恢复网络后请手动开始",
                    ongoing = false, tone = DownloadIslandTone.WARNING)
            }
            if (laneResults.any(LaneResult::waitingForNetwork) && settings.autoResumeNetwork) {
                Result.retry()
            } else {
                Result.success()
            }
        } finally {
            foregroundUpdates.cancel()
            visibilityUpdates.cancel()
            island.close(terminal)

            withContext(NonCancellable) {
                // A previous stopped attempt may finish after a replacement has resumed.
                // Only the current offline state can put active tasks back on hold.
                // WorkManager 2.10.5 stops timed-out foreground attempts. Preserve their
                // task/cache evidence for existing startup recovery; never globally pause
                // unrelated, unselected waiting items from an automatic cleanup path.
                if (!container.networkAvailable.first()) {
                    container.downloads.markActiveTasksWaitingForNetwork()
                }
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        createForegroundInfo(DownloadNotificationState.preparing())

    private fun createForegroundInfo(state: DownloadNotificationState, island: DownloadIslandState? = null): ForegroundInfo {
        ensureChannel()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(applicationContext.getString(R.string.app_name))
            .setContentText(state.content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(island?.notificationText ?: state.content))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(appLaunchPendingIntent())
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(state.max, state.value, state.indeterminate)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                DOWNLOAD_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(DOWNLOAD_NOTIFICATION_ID, notification)
        }
    }

    private fun showCompletionNotification(content: String) {
        ensureChannel()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("下载任务已结束")
            .setContentText(content)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(appLaunchPendingIntent())
            .setAutoCancel(true)
            .build()
        applicationContext.getSystemService(NotificationManager::class.java)
            .notify(COMPLETION_NOTIFICATION_ID, notification)
    }

    private fun appLaunchPendingIntent(): PendingIntent = PendingIntent.getActivity(
        applicationContext,
        0,
        Intent(applicationContext, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_DOWNLOADS
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "视频下载",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    companion object {
        const val UNIQUE_WORK_NAME = "nanzhufeng-download-queue"
        private const val CHANNEL_ID = "downloads"
        private const val DOWNLOAD_NOTIFICATION_ID = 2001
        private const val COMPLETION_NOTIFICATION_ID = 2002

        fun request() = OneTimeWorkRequestBuilder<ForegroundDownloadWorker>()
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
    }

    private data class LaneResult(
        val completed: Int,
        val skipped: Int,
        val failed: Int,
        val waitingForNetwork: Boolean,
    )
}

internal fun completionNotificationText(
    completedCount: Int,
    skippedCount: Int,
    failedCount: Int,
): String = when {
    failedCount > 0 -> "$failedCount 项下载失败，已保留在队列；点开查看原因并重试。"
    skippedCount > 0 -> "已完成 $completedCount 项，跳过 $skippedCount 项。"
    else -> "已完成 $completedCount 项下载。"
}

class WorkManagerDownloadScheduler(context: Context) : DownloadWorkScheduler {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    override fun enqueue() {
        workManager.enqueueUniqueWork(
            ForegroundDownloadWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            ForegroundDownloadWorker.request(),
        )
    }

    override fun cancel() {
        workManager.cancelUniqueWork(ForegroundDownloadWorker.UNIQUE_WORK_NAME)
    }

    override fun restart() {
        workManager.enqueueUniqueWork(
            ForegroundDownloadWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            ForegroundDownloadWorker.request(),
        )
    }
}
