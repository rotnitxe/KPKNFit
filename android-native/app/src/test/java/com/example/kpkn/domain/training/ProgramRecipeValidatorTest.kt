package com.example.kpkn.domain.training

import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.protocols.weekRecipe
import com.example.kpkn.data.programs.VolumeLandmarks
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramRecipeValidatorTest {
    @Test
    fun landmarks_cover_major_groups() {
        assertTrue(VolumeLandmarks.byGroup.isNotEmpty())
        assertTrue(VolumeLandmarks.byGroup.getValue(com.example.kpkn.data.programs.KpknMuscleGroup.QUADS).mev >= 8)
        assertTrue(VolumeLandmarks.byGroup.getValue(com.example.kpkn.data.programs.KpknMuscleGroup.CHEST).mrv >= 20)
    }

    @Test
    fun empty_recipe_has_no_days_to_fail() {
        val recipe = com.example.kpkn.data.protocols.TrainingPlanRecipe(
            id = "empty",
            weeks = listOf(weekRecipe(1, 0, "X", BlockGoal.ACCUMULATION, emptyList())),
        )
        val hard = ProgramRecipeValidator.hardFindings(recipe, CatalogCompositionTestSupport.metadata)
        assertTrue("Una semana sin días debe fallar W6", hard.any { it.rule == "W6" })
    }
}
