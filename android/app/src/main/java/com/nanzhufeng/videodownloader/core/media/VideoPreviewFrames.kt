package com.nanzhufeng.videodownloader.core.media

/** Shared, bounded selection policy for local video previews. */
internal object VideoPreviewFrames {
    fun <T> select(
        durationMillis: Long?,
        frameAt: (Long) -> T?,
        isUsable: (T) -> Boolean,
        discard: (T) -> Unit,
        checkActive: () -> Unit = {},
    ): T? {
        val duration = durationMillis?.takeIf { it > 0 }?.coerceAtMost(Long.MAX_VALUE / 1_000)
        val lastFrame = duration?.minus(1) ?: 30_000L
        val candidates = listOf(750L, 2_000L, 5_000L) +
            (duration?.let { listOf(it / 10, it / 4, it / 2, it * 3 / 4) }
                ?: listOf(10_000L, 20_000L, 30_000L)) + 0L
        for (time in candidates.map { it.coerceIn(0L, lastFrame) }.distinct()) {
            checkActive()
            val frame = frameAt(time) ?: continue
            try {
                checkActive()
                if (isUsable(frame)) return frame
            } catch (failure: Throwable) {
                discard(frame)
                throw failure
            }
            discard(frame)
        }
        return null
    }

    /** Reject near-black blanks, while retaining small highlights and genuinely dark scenes. */
    fun hasVisibleContent(pixels: IntArray): Boolean {
        if (pixels.isEmpty()) return false
        var minimum = 255
        var maximum = 0
        var visible = 0
        var total = 0L
        for (pixel in pixels) {
            val brightness = maxOf(pixel ushr 16 and 255, pixel ushr 8 and 255, pixel and 255)
            minimum = minOf(minimum, brightness)
            maximum = maxOf(maximum, brightness)
            total += brightness
            if (brightness > 12) visible++
        }
        return total > pixels.size * 24L ||
            (maximum - minimum >= 12 && visible * 100L >= pixels.size * 2L)
    }
}
