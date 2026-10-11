package com.nanzhufeng.videodownloader.domain.playback

enum class VideoSeekSide { LEFT, RIGHT }
data class VideoSeekFeedback(val side: VideoSeekSide, val seconds: Long)
interface VideoGesturePlayback {
    val positionMillis: Long
    val durationMillis: Long
    var playWhenReady: Boolean
    fun seekTo(positionMillis: Long)
    fun hideControls()
    fun toggleControls()
}

/** The first tap waits for confirmation. A double tap seeks once, then every
 * additional tap on that side in the burst seeks once more. Native chrome
 * never sees those pointer events or delayed single-tap confirmations. */
class VideoPlayerGestureController(private val playback: VideoGesturePlayback) {
    private var lastSide: VideoSeekSide? = null
    private var lastSeekTime = Long.MIN_VALUE
    private var steps = 0L
    private val consumedDownTimes = ArrayDeque<Long>()

    fun doubleTap(side: VideoSeekSide, eventTimeMillis: Long, firstDownTime: Long, secondDownTime: Long): VideoSeekFeedback {
        consume(firstDownTime)
        consume(secondDownTime)
        return seek(side, eventTimeMillis)
    }

    fun continuedTap(side: VideoSeekSide, eventTimeMillis: Long, downTime: Long): VideoSeekFeedback? {
        if (!withinBurst(side, eventTimeMillis)) return null
        consume(downTime)
        return seek(side, eventTimeMillis)
    }

    fun confirmedSingleTap(downTime: Long) {
        if (downTime !in consumedDownTimes) playback.toggleControls()
    }

    private fun seek(side: VideoSeekSide, eventTimeMillis: Long): VideoSeekFeedback {
        steps = if (withinBurst(side, eventTimeMillis)) (steps + 1L).coerceAtMost(Long.MAX_VALUE / STEP_SECONDS) else 1L
        lastSide = side
        lastSeekTime = eventTimeMillis
        val current = playback.positionMillis.coerceAtLeast(0L)
        val target = if (side == VideoSeekSide.LEFT) (current - STEP_MILLIS).coerceAtLeast(0L)
            else current + minOf(STEP_MILLIS, Long.MAX_VALUE - current)
        val bounded = if (playback.durationMillis > 0L) target.coerceAtMost(playback.durationMillis) else target
        playback.hideControls()
        playback.seekTo(bounded)
        return VideoSeekFeedback(side, steps * STEP_SECONDS)
    }

    private fun withinBurst(side: VideoSeekSide, time: Long): Boolean = lastSide == side &&
        time >= lastSeekTime && time - lastSeekTime <= BURST_WINDOW_MILLIS

    private fun consume(downTime: Long) {
        consumedDownTimes.addLast(downTime)
        while (consumedDownTimes.size > 32) consumedDownTimes.removeFirst()
    }

    companion object {
        const val STEP_SECONDS = 10L
        const val STEP_MILLIS = STEP_SECONDS * 1000L
        const val BURST_WINDOW_MILLIS = 600L
    }
}
