package com.example.kpkn.data.onboarding

import org.junit.Assert.assertEquals
import org.junit.Test

class SetupDraftResolverTest {
    @Test
    fun canonicalScopesNeverUseResumeAsStorageKey() {
        assertEquals("setup-wizard:full", SetupDraftResolver.canonicalDraftId(SetupDraftScope.FULL))
        assertEquals("setup-wizard:training_only", SetupDraftResolver.canonicalDraftId(SetupDraftScope.TRAINING_ONLY))
        assertEquals("setup-wizard:nutrition_only", SetupDraftResolver.canonicalDraftId(SetupDraftScope.NUTRITION_ONLY))
        assertEquals("setup-wizard:rings_only", SetupDraftResolver.canonicalDraftId(SetupDraftScope.RINGS_ONLY))
        assertEquals("setup-wizard:full", SetupDraftResolver.canonicalDraftId(SetupDraftScope.LEGACY_RESUME))
    }
}
