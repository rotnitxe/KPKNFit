package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.exercises.catalogv2.JointInvolvementV2
import com.example.kpkn.domain.exercises.catalogv2.ResolvedExerciseProfileV2

internal data class RelatorExerciseContext(
    val joints: List<JointInvolvementV2>,
    val movementPatternId: String?,
    val family: RelatorFamily,
    val compound: RelatorCompound = RelatorCompound.NONE,
    val axialLoadFactor: Double? = null,
)

internal fun resolveRelatorExerciseContext(
    exercise: Exercise?,
    catalog: ExerciseCatalogV2?,
): RelatorExerciseContext {
    val profile = resolveExerciseProfileForRelator(exercise, catalog)
    val joints = profile?.jointInvolvement.orEmpty()
    val patternId = profile?.movementPatternId
    val family = relatorFamilyFrom(
        exerciseName = exercise?.name.orEmpty(),
        jointIds = joints.map { it.jointId },
        movementPatternId = patternId,
    )
    val axial = profile?.axialLoadFactor
    val compound = detectRelatorCompound(
        exerciseName = exercise?.name.orEmpty(),
        movementPatternId = patternId,
        axialLoadFactor = axial,
    )
    return RelatorExerciseContext(
        joints = joints,
        movementPatternId = patternId,
        family = family,
        compound = compound,
        axialLoadFactor = axial,
    )
}

internal fun resolveJointInvolvementForExercise(
    exercise: Exercise,
    catalog: ExerciseCatalogV2?,
): List<JointInvolvementV2> = resolveExerciseProfileForRelator(exercise, catalog)
    ?.jointInvolvement
    .orEmpty()

private fun resolveExerciseProfileForRelator(
    exercise: Exercise?,
    catalog: ExerciseCatalogV2?,
): ResolvedExerciseProfileV2? {
    if (exercise == null || catalog == null) return null
    val configurationId = exercise.catalogConfigurationId?.takeIf { it.isNotBlank() }
    val definitionId = exercise.catalogDefinitionId?.takeIf { it.isNotBlank() }
    val definition = catalog.families
        .asSequence()
        .flatMap { it.definitions.asSequence() }
        .firstOrNull { def ->
            (definitionId != null && def.id == definitionId) ||
                def.canonicalName.equals(exercise.name.trim(), ignoreCase = true) ||
                def.configurations.any { it.id.equals(exercise.exerciseDbId, ignoreCase = true) }
        } ?: return null
    val config = definition.configurations.firstOrNull { it.id == configurationId }
        ?: definition.configurations.firstOrNull { it.id == definition.defaultConfigurationId }
        ?: definition.configurations.firstOrNull()
        ?: return null
    return config.profile
}
