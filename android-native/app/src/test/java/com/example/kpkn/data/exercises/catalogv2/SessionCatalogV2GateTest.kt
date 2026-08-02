package com.example.kpkn.data.exercises.catalogv2

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCatalogV2GateTest {
    @Test
    fun incomplete_identity_is_blocked_but_custom_namespace_is_explicitly_manual() {
        val session = Session(
            id = "session",
            name = "Test",
            exercises = listOf(
                Exercise(id = "legacy", name = "Legacy"),
                Exercise(id = "custom-occurrence", name = "Manual", exerciseDbId = "custom:manual"),
            ),
        )

        val issues = session.catalogV2SelectionIssues()

        assertEquals(1, issues.size)
        assertEquals("incomplete_v2_identity", issues.single().code)
        assertTrue(issues.single().exerciseId == "legacy")
    }

    @Test
    fun valid_identity_requires_occurrence_and_rejects_legacy_chip_state() {
        val valid = Exercise(
            id = "occurrence-1",
            name = "Parent",
            catalogRevision = "v2",
            catalogDefinitionId = "parent",
            catalogConfigurationId = "parent__standing__barbell",
            performanceProfileId = "profile",
            occurrenceId = "occurrence-1",
        )
        assertTrue(Session(id = "session", name = "Test", exercises = listOf(valid)).catalogV2SelectionIssues().isEmpty())

        val withLegacyChips = valid.copy(selectedAspects = mapOf("grip" to "neutral"))
        assertEquals(
            "legacy_chip_state_present",
            Session(id = "session", name = "Test", exercises = listOf(withLegacyChips))
                .catalogV2SelectionIssues()
                .single()
                .code,
        )
    }

    @Test
    fun mixed_revisions_and_duplicate_occurrences_are_blocked() {
        val first = Exercise(
            id = "first",
            name = "First",
            catalogRevision = "v2-a",
            catalogDefinitionId = "a",
            catalogConfigurationId = "a__default",
            performanceProfileId = "a-profile",
            occurrenceId = "same-occurrence",
        )
        val second = first.copy(
            id = "second",
            catalogRevision = "v2-b",
            catalogDefinitionId = "b",
            catalogConfigurationId = "b__default",
            performanceProfileId = "b-profile",
        )

        val codes = Session(id = "session", name = "Test", exercises = listOf(first, second))
            .catalogV2SelectionIssues()
            .map { it.code }

        assertTrue("mixed_catalog_revisions" in codes)
        assertTrue("duplicate_occurrence_id" in codes)
    }
}
