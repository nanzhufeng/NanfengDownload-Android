package com.nanzhufeng.videodownloader.domain.playback

import org.junit.Assert.*
import org.junit.Test

class VideoPlayerGestureControllerTest {
    private class Playback(override var positionMillis: Long = 15000, override val durationMillis: Long = 120000) : VideoGesturePlayback {
        override var playWhenReady = true
        var controlsVisible = true
        var controlToggles = 0
        override fun seekTo(positionMillis: Long) { this.positionMillis = positionMillis }
        override fun hideControls() { controlsVisible = false }
        override fun toggleControls() { controlToggles++; controlsVisible = !controlsVisible }
    }
    @Test fun aRightDoubleTapSeeksTenSecondsAndKeepsPlayingWithoutShowingChrome() {
        val player = Playback()
        val gestures = VideoPlayerGestureController(player)
        gestures.doubleTap(VideoSeekSide.RIGHT, 200, 100, 200)
        assertEquals(25000L, player.positionMillis)
        assertTrue(player.playWhenReady)
        assertFalse(player.controlsVisible)
        gestures.confirmedSingleTap(100)
        gestures.confirmedSingleTap(200)
        assertEquals(0, player.controlToggles)
    }
    @Test fun thirdAndFourthTapsEachAddTenSecondsAndTheirConfirmedTapNeverShowsControls() {
        val player = Playback()
        val gestures = VideoPlayerGestureController(player)
        gestures.doubleTap(VideoSeekSide.RIGHT, 200, 100, 200)
        assertEquals(20L, gestures.continuedTap(VideoSeekSide.RIGHT, 400, 400)?.seconds)
        assertEquals(30L, gestures.doubleTap(VideoSeekSide.RIGHT, 600, 400, 600)?.seconds)
        assertEquals(45000L, player.positionMillis)
        gestures.confirmedSingleTap(400)
        gestures.confirmedSingleTap(600)
        assertEquals(0, player.controlToggles)
        assertTrue(player.playWhenReady)
    }
    @Test fun leftTapsRewindAndClampToStartWithoutTurningPausedPlaybackOn() {
        val player = Playback(8000).apply { playWhenReady = false }
        val gestures = VideoPlayerGestureController(player)
        assertEquals(10L, gestures.doubleTap(VideoSeekSide.LEFT, 200, 100, 200)?.seconds)
        assertEquals(0L, player.positionMillis)
        gestures.continuedTap(VideoSeekSide.LEFT, 400, 400)
        assertEquals(0L, player.positionMillis)
        assertFalse(player.playWhenReady)
    }
    @Test fun anExpiredOrOppositeSideSingleTapWaitsForConfirmationInsteadOfSeeking() {
        val player = Playback()
        val gestures = VideoPlayerGestureController(player)
        gestures.doubleTap(VideoSeekSide.RIGHT, 200, 100, 200)
        assertNull(gestures.continuedTap(VideoSeekSide.LEFT, 400, 400))
        assertNull(gestures.continuedTap(VideoSeekSide.RIGHT, 1000, 1000))
        assertEquals(25000L, player.positionMillis)
        gestures.confirmedSingleTap(1000)
        assertEquals(1, player.controlToggles)
    }
    @Test fun switchingDoubleTapSideStartsANewRewindBurstAndForwardClampsToDuration() {
        val player = Playback(115000)
        val gestures = VideoPlayerGestureController(player)
        gestures.doubleTap(VideoSeekSide.RIGHT, 200, 100, 200)
        assertEquals(120000L, player.positionMillis)
        assertEquals(10L, gestures.doubleTap(VideoSeekSide.LEFT, 500, 400, 500)?.seconds)
        assertEquals(110000L, player.positionMillis)
    }
}
