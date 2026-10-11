package com.nanzhufeng.videodownloader.data.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadNetworkAvailabilityTest {
    @Test
    fun internetConnectionAllowsTheMediaRequestWithoutASystemValidationResult() {
        assertTrue(downloadNetworkAvailable(true, true, false))
    }

    @Test
    fun missingDefaultNetworkOrInternetCapabilityDoesNotStartATransfer() {
        assertFalse(downloadNetworkAvailable(false, true, false))
        assertFalse(downloadNetworkAvailable(true, false, false))
    }

    @Test
    fun captivePortalMustBeResolvedBeforeStartingATransfer() {
        assertFalse(downloadNetworkAvailable(true, true, true))
    }
}
