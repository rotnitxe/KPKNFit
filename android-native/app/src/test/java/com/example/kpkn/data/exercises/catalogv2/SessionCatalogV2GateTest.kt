package com.example.kpkn.data.exercises.catalogv2

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.protocols.PlanProvenance
import com.example.kpkn.data.protocols.PlanProvenanceClass
import com.example.kpkn.domain.exercises.normalizedIdentityFields
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCatalogV2GateTest {
    @Test
    fun imported_legacy_catalog_occurrence_is_recovered_before_the_save_gate() {
        val legacy = Exercise(
            id = "imported-slot",
            name = "Alias elegido en el importador",
            exerciseDbId = "bench__flat__barbell",
            exerciseId = "bench__flat__barbell",
            canonicalExerciseId = "bench",
            exerciseFamilyId = "bench",
            catalogRevision = "catalog-r7",
            catalogDefinitionId = "bench",
            catalogConfigurationId = "bench__flat__barbell",
            performanceProfileId = "bench-profile",
        )
        val sourceSession = Session(id = "session", name = "Día A", exercises = listOf(legacy))
        val program = Program(
            id = "imported-program",
            name = "Importado",
            planProvenance = PlanProvenance(
                planId = "imported:legacy-json",
                category = PlanProvenanceClass.IMPORTED,
            ),
            macrocycles = listOf(
                Macrocycle(
                    id = "macro",
                    name = "Macro",
                    blocks = listOf(
                        Block(
                            id = "block",
                            name = "Bloque",
                            mesocycles = listOf(
                                Mesocycle(
                                    id = "meso",
                                    name = "Meso",
                                    weeks = listOf(ProgramWeek(id = "week", name = "Semana 1", sessions = listOf(sourceSession))),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val normalizedSession = program.normalizedIdentityFields().macrocycles.single()
            .blocks.single().mesocycles.single().weeks.single().sessions.single()

        assertEquals("imported-slot", normalizedSession.exercises.single().occurrenceId)
        assertTrue(normalizedSession.catalogV2SelectionIssues().isEmpty())
    }

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
