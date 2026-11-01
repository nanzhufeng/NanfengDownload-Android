package com.nanzhufeng.videodownloader.feature.history

import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryMediaMetadataTest {
    @Test fun unreadableIsUnknownUntilAbsenceIsConfirmed() {
        val unavailable = HistoryMediaMetadata(null, 100)
        assertEquals(HistoryMediaAccess.UNKNOWN, historyMediaAccess(true, unavailable))
        assertEquals(HistoryMediaAccess.MISSING,
            historyMediaAccess(true, unavailable.copy(allMediaMissingConfirmed = true)))
    }

    @Test fun currentReadableOutputWinsOverOldMissingProjection() {
        val readable = HistoryMediaMetadata(null, 100, readableUris = listOf("content://current/output"))
        assertEquals(HistoryMediaAccess.AVAILABLE, historyMediaAccess(true, readable))
        assertEquals(HistoryMediaAccess.CHECKING, historyMediaAccess(true, null))
        assertEquals(HistoryMediaAccess.MISSING, historyMediaAccess(false, null))
    }

    @Test
    fun durationFormatter_keepsCompleteHoursMinutesAndSeconds() {
        assertEquals("00:00", formatMediaDuration(0L))
        assertEquals("01:05", formatMediaDuration(65_000L))
        assertEquals("01:02:03", formatMediaDuration(3_723_000L))
    }

    @Test
    fun exactByteFormatter_keepsTheFullValue() {
        assertEquals("21,865,432", formatExactBytes(21_865_432L))
    }
}
