package com.nanzhufeng.videodownloader.probe

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.Call
import okhttp3.Response
import okhttp3.ResponseBody
import okio.ForwardingSource
import okio.buffer

/** Owns cancellation until the body closes, including a stalled header/body read. */
internal object TransferCallGuard {
    private val watcher = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "nanfeng-transfer-cancel").apply { isDaemon = true }
    }
    fun execute(call: Call, cancelled: AtomicBoolean, aborted: AtomicBoolean? = null): Response {
        val guard = watcher.scheduleWithFixedDelay({
            if (cancelled.get() || aborted?.get() == true) call.cancel()
        }, 0, 100, TimeUnit.MILLISECONDS)
        try {
            val response = call.execute()
            val body = response.body
            if (body == null) { guard.cancel(false); return response }
            val wrapped = object : ResponseBody() {
                private val guardedSource = object : ForwardingSource(body.source()) {
                    override fun close() { try { super.close() } finally { guard.cancel(false) } }
                }.buffer()
                override fun contentType() = body.contentType()
                override fun contentLength() = body.contentLength()
                override fun source() = guardedSource
            }
            return response.newBuilder().body(wrapped).build()
        } catch (error: Exception) {
            guard.cancel(false)
            if (cancelled.get() || aborted?.get() == true) throw java.util.concurrent.CancellationException("下载已取消")
            throw error
        }
    }
}
