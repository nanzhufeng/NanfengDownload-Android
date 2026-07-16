package com.nanzhufeng.videodownloader.data.settings

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nanzhufeng.videodownloader.core.model.ResolutionPreset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreferencesSettingsRepositoryInstrumentedTest {
    @Test
    fun resolutionAndDraftRemainAfterRepositoryRecreation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val first = PreferencesSettingsRepository(context)
        try {
            first.setDefaultResolution(ResolutionPreset.UP_TO_1080P)
            first.saveInputDraft("https://example.com/persisted")

            val recreated = PreferencesSettingsRepository(context)
            val persisted = recreated.settings.first {
                it.defaultResolution == ResolutionPreset.UP_TO_1080P && it.inputDraft.isNotBlank()
            }

            assertEquals(ResolutionPreset.UP_TO_1080P, persisted.defaultResolution)
            assertEquals("https://example.com/persisted", persisted.inputDraft)
        } finally {
            first.setDefaultResolution(ResolutionPreset.UP_TO_720P)
            first.saveInputDraft("")
        }
    }
}
