package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.onboarding.SetupDraftCandidate
import com.example.kpkn.data.onboarding.SetupDraftResolver
import com.example.kpkn.data.onboarding.SetupDraftScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SetupEntryDraftSelectionTest {
    private fun candidate(
        draftId: String,
        scope: SetupDraftScope,
        updatedAtEpochMs: Long,
    ) = SetupDraftCandidate(
        draftId = draftId,
        scope = scope,
        revision = 1L,
        updatedAtEpochMs = updatedAtEpochMs,
        catalogRevision = null,
    )

    @Test
    fun returnsNullWhenOnlyModuleOrPendingNutritionDraftsExist() {
        val candidates = listOf(
            candidate("setup-wizard:training_only", SetupDraftScope.TRAINING_ONLY, 500L),
            candidate("setup-wizard:nutrition_only", SetupDraftScope.NUTRITION_ONLY, 600L),
            candidate("${SetupDraftResolver.PENDING_NUTRITION_PREFIX}c1", SetupDraftScope.FULL, 900L),
            candidate("custom-unknown", SetupDraftScope.UNKNOWN, 700L),
        )

        assertNull(selectRecoverableFullDraft(candidates))
    }

    @Test
    fun ignoresNewerModuleDraftsAndSelectsMostRecentFullOrLegacyResumeDraft() {
        val olderFull = candidate("setup-wizard:full:1", SetupDraftScope.FULL, 300L)
        val newerLegacy = candidate("setup-wizard:legacy:2", SetupDraftScope.LEGACY_RESUME, 450L)
        val candidates = listOf(
            olderFull,
            newerLegacy,
            candidate("setup-wizard:training_only", SetupDraftScope.TRAINING_ONLY, 1_000L),
            candidate("setup-wizard:nutrition_only", SetupDraftScope.NUTRITION_ONLY, 1_100L),
            candidate("${SetupDraftResolver.PENDING_NUTRITION_PREFIX}c2", SetupDraftScope.FULL, 1_200L),
        )

        assertEquals(newerLegacy, selectRecoverableFullDraft(candidates))
    }
}
