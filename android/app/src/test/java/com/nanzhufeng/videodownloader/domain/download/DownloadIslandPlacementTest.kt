package com.nanzhufeng.videodownloader.domain.download

import org.junit.Assert.*
import org.junit.Test

class DownloadIslandPlacementTest {
    @Test fun outerScreenUsesStatusBarAndLeavesCameraClear() {
        val p = DownloadIslandPlacement.resolve(1140, 124, 3.25f, listOf(IslandCutout(533, 0, 607, 107)))
        assertTrue(p.inStatusBar)
        assertTrue(p.surfaceTop >= 0)
        assertEquals(124 + (8 * 3.25).toInt(), p.surfaceTop + p.surfaceHeight)
        assertTrue(p.cameraGap / 2 >= 37 + (8 * 3.25).toInt())
        assertTrue((p.compactWidth - p.cameraGap) / 2 >= (64 * 3.25).toInt())
        assertTrue((p.screenWidth - p.compactWidth) / 2 >= (88 * 3.25).toInt())
    }
    @Test fun innerScreenRightCameraDoesNotSplitCenteredText() {
        val p = DownloadIslandPlacement.resolve(2248, 124, 3.25f, listOf(IslandCutout(2099, 0, 2248, 107)))
        assertTrue(p.inStatusBar)
        assertEquals(0, p.cameraGap)
    }
    @Test fun foldAndRotationRecomputeTheCameraGap() {
        val outer = DownloadIslandPlacement.resolve(1140,124,3.25f,listOf(IslandCutout(533,0,607,107)))
        val inner = DownloadIslandPlacement.resolve(2248,124,3.25f,listOf(IslandCutout(2099,0,2248,107)))
        val rotated = DownloadIslandPlacement.resolve(2616,80,3.25f,listOf(IslandCutout(0,533,107,607)))
        assertTrue(outer.cameraGap > 0)
        assertEquals(0,inner.cameraGap)
        assertEquals(0,rotated.cameraGap)
    }
    @Test fun asymmetricCentralHoleIsProtectedOnBothSides() {
        val p=DownloadIslandPlacement.resolve(400,36,1f,listOf(IslandCutout(180,0,230,28)))
        assertTrue(p.cameraGap / 2 >= 38)
    }
    @Test fun shortStatusBarKeepsAVisibleLowerTapEdge() {
        val p=DownloadIslandPlacement.resolve(360,24,1f,emptyList())
        assertTrue(p.inStatusBar)
        assertEquals(0,p.cameraGap)
        assertEquals(24,p.surfaceHeight)
        assertEquals(8,p.surfaceTop)
    }
    @Test fun oversizedNotchUsesSafeFallback() {
        val p=DownloadIslandPlacement.resolve(320,50,1f,listOf(IslandCutout(30,0,290,50)))
        assertFalse(p.inStatusBar)
        assertEquals(0,p.cameraGap)
    }
}
