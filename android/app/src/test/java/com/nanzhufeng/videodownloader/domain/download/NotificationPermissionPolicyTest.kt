package com.nanzhufeng.videodownloader.domain.download

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPermissionPolicyTest {
    @Test
    fun notificationPermission_isNotRequestedBeforeAndroid13() {
        assertFalse(NotificationPermissionPolicy.needsRuntimeRequest(sdkInt = 32, permissionGranted = false))
    }

    @Test
    fun notificationPermission_isRequestedOnAndroid13WhenMissing() {
        assertTrue(NotificationPermissionPolicy.needsRuntimeRequest(sdkInt = 33, permissionGranted = false))
    }

    @Test
    fun notificationPermission_isNotRequestedWhenAlreadyGranted() {
        assertFalse(NotificationPermissionPolicy.needsRuntimeRequest(sdkInt = 35, permissionGranted = true))
    }
}
