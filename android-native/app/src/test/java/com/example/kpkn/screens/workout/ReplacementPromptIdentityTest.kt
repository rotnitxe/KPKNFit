package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.WeekVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ReplacementPromptIdentityTest {

    @Test
    fun promptCapturesSourceCatalogIdentityNotReplacement() {
        val source = Exercise(
            id = "ex1",
            name = "Press banca",
            catalogRevision = "rev-old",
            catalogDefinitionId = "def-old",
            catalogConfigurationId = "cfg-old",
            exerciseDbId = "db-old",
        )
        val replacement = ExerciseMuscleInfo(
            id = "ex-new",
            name = "Press mancuernas",
            catalogRevision = "rev-new",
            catalogDefinitionId = "def-new",
            catalogConfigurationId = "cfg-new",
        )
        val prompt = captureReplacementPromptIdentity(
            exerciseId = "ex1",
            replacement = replacement,
            sourceExercise = source,
            sourceExerciseSlot = 2,
            sourceLogicalLineageId = null,
            activeMode = WeekVariant.A,
        )
        assertEquals("rev-old", prompt.fromCatalogRevision)
        assertEquals("def-old", prompt.fromCatalogDefinitionId)
        assertEquals("cfg-old", prompt.fromCatalogConfigurationId)
        assertEquals(2, prompt.sourceExerciseSlot)
        assertEquals(LiveExerciseSlotIdentity.from(source), prompt.sourceTarget)
        assertEquals(WeekVariant.A, prompt.activeMode)
        assertNotEquals(replacement.catalogRevision, prompt.fromCatalogRevision)
        assertEquals("Press mancuernas", prompt.replacement.name)
    }

    @Test
    fun toExerciseDbIdPrefersCatalogConfigurationId() {
        val replacement = ExerciseMuscleInfo(
            id = "legacy-or-uuid",
            name = "Press mancuernas",
            catalogConfigurationId = "cfg-new",
        )
        assertEquals("cfg-new", replacementToExerciseDbId(replacement))
        assertEquals(
            "legacy-or-uuid",
            replacementToExerciseDbId(ExerciseMuscleInfo(id = "legacy-or-uuid", name = "X")),
        )
    }
}
