package com.nanzhufeng.videodownloader.domain.download

object NotificationPermissionPolicy {
    fun needsRuntimeRequest(sdkInt: Int, permissionGranted: Boolean): Boolean =
        sdkInt >= 33 && !permissionGranted
}
