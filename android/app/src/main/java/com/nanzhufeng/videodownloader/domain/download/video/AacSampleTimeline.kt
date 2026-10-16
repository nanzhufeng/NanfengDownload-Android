package com.nanzhufeng.videodownloader.domain.download.video

/** Android TS extraction clamps an initial negative audio PTS to zero. */
internal fun initialAacLeadUs(firstTimeUs: Long, nextTimeUs: Long, frames: Int, sampleRate: Int): Long {
    if (firstTimeUs != 0L || frames < 1 || sampleRate <= 0 || nextTimeUs <= 0L) return 0L
    val lead = frames * 1_024_000_000L / sampleRate - nextTimeUs
    return lead.takeIf { it in 1..250_000L } ?: 0L
}

/** Preserve each AAC frame and real gaps; a splice may overlap by up to two encoder-padding frames. */
internal class AacSampleTimeline(private val sampleRate: Int, private val initialLeadUs: Long) {
    private var nextTimeUs: Long? = null
    private var firstSampleTimeUs: Long? = null
    private var leftInitialTimestamp = false

    init { require(sampleRate > 0) }

    fun times(sampleTimeUs: Long, frameCount: Int): List<Long> {
        require(frameCount > 0)
        val previousEnd = nextTimeUs
        val first = firstSampleTimeUs ?: sampleTimeUs.also { firstSampleTimeUs = it }
        if (sampleTimeUs != first) leftInitialTimestamp = true
        // Several separate AAC access units may each be clamped to the same initial PTS.
        // Recover only the bounded, probed leading interval; never conceal a later rewind.
        val clampedPrefix = initialLeadUs > 0L && !leftInitialTimestamp &&
            sampleTimeUs == first && (previousEnd == null || previousEnd - first <= initialLeadUs)
        val proposed = if (clampedPrefix) previousEnd ?: sampleTimeUs
            else sampleTimeUs + if (previousEnd == null) 0L else initialLeadUs
        val frameDurationUs = 1_024_000_000L / sampleRate
        require(previousEnd == null || proposed >= previousEnd - 2 * frameDurationUs - 1L) {
            "AAC 时间轴倒退超过两帧，不能安全封装：时间=$proposed，前帧结束=$previousEnd，起始音频提前=$initialLeadUs"
        }
        val start = maxOf(proposed, previousEnd ?: proposed)
        val times = List(frameCount) { start + it * 1_024_000_000L / sampleRate }
        nextTimeUs = start + frameCount * 1_024_000_000L / sampleRate
        return times
    }
}
