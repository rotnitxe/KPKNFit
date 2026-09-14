package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.LoadModeV2

/**
 * Four former load-suggestion channels collapse to three roles:
 * card shows the number, relator explains, voice speaks only with the setting.
 * Ghost/history is a fill for last session, not a second copy of the suggestion.
 */
internal object LoadSuggestionDisplayPolicy {
    fun shouldSpeakSuggestedLoad(voiceAutoSuggestLoads: Boolean): Boolean = voiceAutoSuggestLoads

    fun chipShowsNumberOnly(isSuggestedChip: Boolean): Boolean = isSuggestedChip

    fun cardPlaceholderKg(
        weightText: String,
        loadMode: LoadModeV2,
        ghostOrPlannedKg: String?,
    ): String? {
        if (weightText.isNotBlank()) return null
        if (loadMode == LoadModeV2.BODYWEIGHT) return "Peso corporal"
        ghostOrPlannedKg?.takeIf { it.isNotBlank() }?.let { return it }
        return when (loadMode) {
            LoadModeV2.LASTRE -> "Ej: 10"
            LoadModeV2.ASSISTED -> "Ej: 20"
            else -> null
        }
    }
}
