package com.nanzhufeng.videodownloader.feature.history

import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryMediaKindTest {
    @Test
    fun imageMimeTypeUsesTheImageGalleryEvenWhenTheDownloadResolutionLooksLikeVideo() {
        assertEquals(
            HistoryMediaKind.IMAGE,
            historyMediaKind(ResolutionPreset.UP_TO_720P, listOf("image/png", "image/jpeg")),
        )
    }

    @Test
    fun audioResolutionKeepsTheDedicatedAudioPlayer() {
        assertEquals(
            HistoryMediaKind.AUDIO,
            historyMediaKind(ResolutionPreset.AUDIO_MP3, listOf("audio/mpeg")),
        )
    }

    @Test
    fun videoDefaultsToTheInAppVideoPlayer() {
        assertEquals(
            HistoryMediaKind.VIDEO,
            historyMediaKind(ResolutionPreset.UP_TO_720P, listOf("video/mp4")),
        )
    }
}
