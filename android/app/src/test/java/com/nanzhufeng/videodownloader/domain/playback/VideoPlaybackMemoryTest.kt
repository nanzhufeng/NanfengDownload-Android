package com.nanzhufeng.videodownloader.domain.playback

import org.junit.Assert.*
import org.junit.Test

class VideoPlaybackMemoryTest {
    private class Storage : VideoPositionStorage {
        val values = mutableMapOf<String, Long>()
        var flushed = false
        override fun read(key: String) = values[key] ?: 0L
        override fun write(key: String, positionMillis: Long, flush: Boolean): Boolean {
            values[key] = positionMillis
            flushed = flush
            return true
        }
    }
    @Test fun reopeningTheSameVideoRestoresItsPositionAcrossMemoryInstances() {
        val storage = Storage()
        VideoPlaybackMemory(storage).save("content://video/181", 35000, 3600000, false, true)
        assertEquals(35000L, VideoPlaybackMemory(storage).positionFor("content://video/181"))
        assertTrue(storage.flushed)
    }
    @Test fun eachVideoKeepsAnIndependentPositionAndNewOutputStartsAtZero() {
        val storage = Storage()
        val memory = VideoPlaybackMemory(storage)
        memory.save("content://video/181", 35000, 3600000, false)
        memory.save("content://video/186", 12000, 24000, false)
        assertEquals(35000L, memory.positionFor("content://video/181"))
        assertEquals(12000L, memory.positionFor("content://video/186"))
        assertEquals(0L, memory.positionFor("content://video/187"))
    }
    @Test fun aFinishedVideoReplaysFromStartWithoutErasingOtherBookmarks() {
        val storage = Storage()
        val memory = VideoPlaybackMemory(storage)
        memory.save("a", 35000, 60000, false)
        memory.save("b", 12000, 24000, false)
        memory.save("a", 60000, 60000, true)
        assertEquals(0L, memory.positionFor("a"))
        assertEquals(12000L, memory.positionFor("b"))
    }
    @Test fun invalidPositionsAreClampedButUnknownDurationDoesNotEraseProgress() {
        val memory = VideoPlaybackMemory(Storage())
        memory.save("a", -2000, 60000, false)
        assertEquals(0L, memory.positionFor("a"))
        memory.save("a", 90000, 60000, false)
        assertEquals(60000L, memory.positionFor("a"))
        memory.save("b", 35000, -9223372036854775807L, false)
        assertEquals(35000L, memory.positionFor("b"))
    }
}
