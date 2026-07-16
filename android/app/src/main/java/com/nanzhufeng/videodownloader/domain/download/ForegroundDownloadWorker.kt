package com.nanzhufeng.videodownloader.domain.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nanzhufeng.videodownloader.NanzhufengApplication
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ForegroundDownloadWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result = coroutineScope {
        val container = (applicationContext as NanzhufengApplication).container
        container.downloads.recoverInterruptedTasks()
        setForeground(createForegroundInfo(DownloadNotificationState.preparing()))
        val foregroundUpdates = launch {
            container.downloads.activeTasks.collect { queue ->
                setForeground(createForegroundInfo(DownloadNotificationState.from(queue)))
            }
        }
        var processedTask = false

        try {
            while (!isStopped) {
                when (container.taskRunner.runNext()) {
                    TaskRunResult.Completed,
                    TaskRunResult.Skipped,
                    TaskRunResult.Failed,
                    -> processedTask = true

                    TaskRunResult.WaitingForNetwork -> {
                        return@coroutineScope if (container.settings.settings.first().autoResumeNetwork) {
                            Result.retry()
                        } else {
                            Result.success()
                        }
                    }
                    TaskRunResult.Idle -> {
                        if (processedTask) showCompletionNotification()
                        return@coroutineScope Result.success()
                    }
                }
            }
            Result.success()
        } finally {
            foregroundUpdates.cancel()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        createForegroundInfo(DownloadNotificationState.preparing())

    private fun createForegroundInfo(state: DownloadNotificationState): ForegroundInfo {
        ensureChannel()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("南烛枫视频下载器")
            .setContentText(state.content)
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

    private fun showCompletionNotification() {
        ensureChannel()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("下载任务已完成")
            .setContentText("所选作品已处理完毕，可在历史记录中查看结果。")
            .setAutoCancel(true)
            .build()
        applicationContext.getSystemService(NotificationManager::class.java)
            .notify(COMPLETION_NOTIFICATION_ID, notification)
    }

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
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
    }
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
