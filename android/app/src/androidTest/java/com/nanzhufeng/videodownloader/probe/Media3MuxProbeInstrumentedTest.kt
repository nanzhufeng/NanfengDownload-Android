package com.nanzhufeng.videodownloader.probe

import androidx.media3.common.C
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Media3MuxProbeInstrumentedTest {
    @Test
    fun compositionDeclaresOneVideoAndOneAudioTrack() {
        val trackTypes = Media3MuxProbe.declaredTrackTypes()

        assertEquals(setOf(C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_AUDIO), trackTypes)
    }
}
