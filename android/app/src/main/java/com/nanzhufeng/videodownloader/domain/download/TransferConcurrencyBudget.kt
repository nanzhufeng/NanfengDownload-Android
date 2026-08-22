package com.nanzhufeng.videodownloader.domain.download

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Shares the finite range-connection pool between active download tasks.
 *
 * A task may download video and audio in parallel. Dividing the connection
 * budget by both active tasks and active streams keeps a batch from letting
 * its first task monopolize the HTTP worker pool.
 */
class TransferConcurrencyBudget(
    private val totalConnections: Int = DEFAULT_TOTAL_CONNECTIONS,
) {
    private val lock = Any()
    private val activeTaskLeases = linkedMapOf<String, Int>()

    init {
        require(totalConnections > 0) { "全局连接预算必须大于 0" }
    }

    fun enter(taskId: String): Lease {
        require(taskId.isNotBlank()) { "下载任务 ID 不能为空" }
        synchronized(lock) {
            activeTaskLeases[taskId] = (activeTaskLeases[taskId] ?: 0) + 1
        }
        return Lease { release(taskId) }
    }

    fun connectionsPerStream(
        taskId: String,
        activeStreamCount: Int,
        requestedConnections: Int,
    ): Int {
        require(activeStreamCount > 0) { "活动媒体流数量必须大于 0" }
        require(requestedConnections > 0) { "请求连接数必须大于 0" }
        synchronized(lock) {
            val activeTaskCount = activeTaskLeases.size.coerceAtLeast(1)
            val perTaskBudget = (totalConnections / activeTaskCount).coerceAtLeast(1)
            val perStreamBudget = (perTaskBudget / activeStreamCount).coerceAtLeast(1)
            return minOf(requestedConnections, perStreamBudget)
        }
    }

    private fun release(taskId: String) {
        synchronized(lock) {
            val remaining = (activeTaskLeases[taskId] ?: return) - 1
            if (remaining <= 0) activeTaskLeases.remove(taskId) else activeTaskLeases[taskId] = remaining
        }
    }

    class Lease internal constructor(
        private val release: () -> Unit,
    ) : AutoCloseable {
        private val closed = AtomicBoolean(false)

        override fun close() {
            if (closed.compareAndSet(false, true)) release()
        }
    }

    companion object {
        const val DEFAULT_TOTAL_CONNECTIONS = 8
    }
}
