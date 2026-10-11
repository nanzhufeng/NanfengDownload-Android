package com.nanzhufeng.videodownloader.domain.download

import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadWorkerRequestTest {
    @Test
    fun workerDoesNotLetSystemValidationCancelAReachableMediaRequest() {
        assertEquals(NetworkType.NOT_REQUIRED, ForegroundDownloadWorker.request().workSpec.constraints.requiredNetworkType)
    }
}
