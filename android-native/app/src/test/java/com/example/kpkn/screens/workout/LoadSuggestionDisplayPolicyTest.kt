package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.LoadModeV2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadSuggestionDisplayPolicyTest {

    @Test
    fun voiceOnlyWhenSettingIsOn() {
        assertFalse(LoadSuggestionDisplayPolicy.shouldSpeakSuggestedLoad(voiceAutoSuggestLoads = false))
        assertTrue(LoadSuggestionDisplayPolicy.shouldSpeakSuggestedLoad(voiceAutoSuggestLoads = true))
    }

    @Test
    fun suggestedChipShowsNumberNotReasonCopy() {
        assertTrue(LoadSuggestionDisplayPolicy.chipShowsNumberOnly(isSuggestedChip = true))
        assertFalse(LoadSuggestionDisplayPolicy.chipShowsNumberOnly(isSuggestedChip = false))
    }

    @Test
    fun placeholderUsesGhostNotSuggestionNumber() {
        assertEquals(
            "75",
            LoadSuggestionDisplayPolicy.cardPlaceholderKg(
                weightText = "",
                loadMode = LoadModeV2.LOAD,
                ghostOrPlannedKg = "75",
            ),
        )
        assertNull(
            LoadSuggestionDisplayPolicy.cardPlaceholderKg(
                weightText = "82,5",
                loadMode = LoadModeV2.LOAD,
                ghostOrPlannedKg = "75",
            ),
        )
        assertEquals(
            "Peso corporal",
            LoadSuggestionDisplayPolicy.cardPlaceholderKg(
                weightText = "",
                loadMode = LoadModeV2.BODYWEIGHT,
                ghostOrPlannedKg = "0",
            ),
        )
    }
}
