package com.nanzhufeng.videodownloader.domain.download

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioSourceCacheCompatibilityTest {
    @Test
    fun unknownExpectedLengthKeepsValidCachedSourceReusable() {
        assertTrue(isAudioSourceLengthCompatible(actualBytes = 30_000_000L, expectedBytes = 0L))
    }

    @Test
    fun smallMetadataDifferenceDoesNotDiscardAReusableSource() {
        assertTrue(
            isAudioSourceLengthCompatible(
                actualBytes = 10_271_496L,
                expectedBytes = 10_271_468L,
            ),
        )
    }

    @Test
    fun aDifferentSelectedAudioTrackInvalidatesTheOldSource() {
        assertFalse(
            isAudioSourceLengthCompatible(
                actualBytes = 30_767_611L,
                expectedBytes = 10_271_468L,
            ),
        )
    }
}
