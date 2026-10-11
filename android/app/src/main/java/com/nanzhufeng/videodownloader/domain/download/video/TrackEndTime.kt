package com.nanzhufeng.videodownloader.domain.download.video

import kotlin.math.abs

/** Presentation order may move backwards for B frames; EOS must follow the maximum PTS. */
internal class TrackEndTime(private val fallbackStepUs: Long) {
    private var previousUs: Long? = null
    private var maximumUs = 0L
    private var observedStepUs: Long? = null

    fun record(timestampUs: Long) {
        require(timestampUs >= 0)
        previousUs?.let { previous ->
            val delta = abs(timestampUs - previous)
            if (delta > 0) observedStepUs = minOf(observedStepUs ?: delta, delta)
        }
        maximumUs = maxOf(maximumUs, timestampUs)
        previousUs = timestampUs
    }

    fun endUs(): Long = Math.addExact(maximumUs, observedStepUs ?: fallbackStepUs.coerceAtLeast(1L))
}
