package com.nanzhufeng.videodownloader.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nanzhufeng.videodownloader.core.model.DownloadPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WorkbenchPaletteContractTest {
    @Test
    fun everyWorkbenchCardUsesTheSameWhiteSurfaceAndNeutralBorder() {
        assertEquals(
            setOf(Color.White),
            AppCardTone.entries.map { it.containerColor() }.toSet(),
        )
        assertEquals(
            setOf(Color(0xFFD7DED9)),
            AppCardTone.entries.map { it.borderColor() }.toSet(),
        )
    }

    @Test
    fun workspaceIsDarkerThanCardsWhileSelectionKeepsItsOwnSurface() {
        assertEquals(Color(0xFFE6EAE7), WorkspaceBackground)
        assertNotEquals(Color.White, WorkspaceBackground)
        assertNotEquals(Color.White, SelectedSage)
    }

    @Test
    fun supportingTextUsesNeutralColorInsteadOfASectionAccent() {
        assertEquals(Color(0xFF5F6C64), SecondaryText)
        assertNotEquals(ForestGreen, SecondaryText)
    }

    @Test
    fun everyPlatformUsesADistinctIconTreatment() {
        assertEquals(
            DownloadPlatform.entries.size,
            DownloadPlatform.entries.map(DownloadPlatform::iconTreatment).toSet().size,
        )
    }

    @Test
    fun douyinGlyphKeepsGenerousWhitespaceInsideItsPlatformTile() {
        assertEquals(24.dp, PlatformMarkSize)
        assertEquals(12.dp, DouyinGlyphSize)
    }
}
