package com.nanzhufeng.videodownloader.domain.download

/** Bounds in current display pixels, not a device-model or camera-size guess. */
internal data class IslandCutout(val left: Int, val top: Int, val right: Int, val bottom: Int)
internal data class DownloadIslandPlacement(
    val screenWidth: Int,
    val statusBarHeight: Int,
    val compactWidth: Int,
    val surfaceHeight: Int,
    val surfaceTop: Int,
    val cameraGap: Int,
    val inStatusBar: Boolean,
) {
    companion object {
        fun resolve(width: Int, statusBarHeight: Int, density: Float, cutouts: List<IslandCutout>): DownloadIslandPlacement {
            fun dp(value: Int) = (value * density).toInt()
            val available = (width - dp(24)).coerceAtLeast(1)
            val center = width / 2
            val maxWidth = minOf(dp(320), available)
            // Keep the centered compact surface away from the clock/notification and
            // signal/battery ends of the status bar, including narrow outer screens.
            val compactLimit = minOf(maxWidth, (width - dp(176)).coerceAtLeast(dp(144)))
            // Ignore a corner/side camera (inner display or landscape). A cutout intersecting
            // the centered capsule is mirrored about its center so neither text wing can cross it.
            val camera = cutouts.filter {
                it.top < statusBarHeight && it.bottom > 0 && it.right > it.left &&
                    it.left < center + dp(110) && it.right > center - dp(110)
            }
            val halfGap = camera.maxOfOrNull { maxOf(center - it.left, it.right - center) + dp(8) } ?: 0
            val gap = halfGap * 2
            val canFit = statusBarHeight >= dp(20) && (gap == 0 || gap + dp(128) <= compactLimit)
            val surface = if (canFit) minOf(dp(34), statusBarHeight) else dp(34)
            return DownloadIslandPlacement(
                width, statusBarHeight,
                minOf(compactLimit, maxOf(dp(220), gap + dp(128))),
                // A small visible lower edge remains below the system-owned touch band,
                // so the island can still be expanded without opening the notification shade.
                surface, if (canFit) statusBarHeight - surface + dp(8) else dp(7),
                if (canFit) gap else 0, canFit,
            )
        }
    }
}
