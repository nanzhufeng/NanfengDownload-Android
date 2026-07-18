package com.nanzhufeng.videodownloader.feature.home

import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
import com.nanzhufeng.videodownloader.core.ui.SecondaryText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ResolutionPresentationTest {
    @Test
    fun everyResolutionKeepsASemanticColorInsteadOfFallingBackToGray() {
        ResolutionPreset.entries.forEach { preset ->
            assertNotEquals(
                "$preset 必须保留自己的彩色标识",
                SecondaryText,
                resolutionAccent(preset),
            )
        }
    }

    @Test
    fun queueOffersACompact360pPreset() {
        assertEquals("360p", resolutionLabel(ResolutionPreset.UP_TO_360P))
    }
}
