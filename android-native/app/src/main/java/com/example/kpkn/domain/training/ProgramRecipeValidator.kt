package com.example.kpkn.domain.training

import com.example.kpkn.data.protocols.CompositionSeverity
import com.example.kpkn.data.protocols.RecipeCompositionExemption
import com.example.kpkn.data.protocols.TrainingPlanRecipe

object ProgramRecipeValidator {
    fun validate(
        recipe: TrainingPlanRecipe,
        metadata: ExerciseCompositionMetadataProvider,
        extraExemptions: List<RecipeCompositionExemption> = emptyList(),
    ): List<CompositionFinding> = SessionCompositionPolicy.evaluateRecipe(recipe, metadata, extraExemptions)

    fun hardFindings(
        recipe: TrainingPlanRecipe,
        metadata: ExerciseCompositionMetadataProvider,
        extraExemptions: List<RecipeCompositionExemption> = emptyList(),
    ): List<CompositionFinding> = validate(recipe, metadata, extraExemptions)
        .filter { it.severity == CompositionSeverity.HARD }
}
