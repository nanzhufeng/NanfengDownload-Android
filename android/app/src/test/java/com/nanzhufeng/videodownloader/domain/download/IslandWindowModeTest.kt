package com.nanzhufeng.videodownloader.domain.download

import org.junit.Assert.*
import org.junit.Test

class IslandWindowModeTest {
    @Test fun connectedTopServiceUsesAccessibilityWindowWithoutOrdinaryOverlayPermission() {
        assertEquals(IslandWindowMode.ACCESSIBILITY, IslandWindowMode.resolve(true, false))
    }
    @Test fun bothPermissionsStillProduceOneTopWindowRatherThanAnOrdinaryWindow() {
        assertEquals(IslandWindowMode.ACCESSIBILITY, IslandWindowMode.resolve(true, true))
    }
    @Test fun disconnectedServiceFallsBackToOrdinaryWindowOrNotificationOnly() {
        assertEquals(IslandWindowMode.APPLICATION, IslandWindowMode.resolve(false, true))
        assertEquals(IslandWindowMode.NONE, IslandWindowMode.resolve(false, false))
    }
    @Test fun lateDisconnectCannotRemoveReplacementHost() {
        val store = IslandTopHostStore<String>()
        val old = Any(); val current = Any()
        store.connect(old, "old service")
        store.connect(current, "replacement service")
        assertFalse(store.disconnect(old))
        assertEquals("replacement service", store.host)
        assertTrue(store.disconnect(current))
        assertNull(store.host)
        assertFalse(store.disconnect(current))
    }
}
