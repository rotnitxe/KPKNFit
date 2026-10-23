package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.training.*

internal fun apreDurableExercise(side: Boolean = false): Exercise {
    val sets = Apre.block(200.0, ApreVariant.APRE_10).map { row ->
        ExerciseSet("apre-s${row.ordinal}", weight = row.loadKg, targetReps = row.targetReps,
            purpose = row.purpose, amrapMinimumReps = row.minimumReps, loadModeV2 = LoadModeV2.LOAD,
            leftTarget = if(side) UnilateralTarget(weight = row.loadKg) else null,
            rightTarget = if(side) UnilateralTarget(weight = row.loadKg) else null)
    }
    return Exercise("apre", "Press banca", sets = sets,
        unilateralMode = if(side) UnilateralMode.UNILATERAL_DIFFERENTIAL else UnilateralMode.BILATERAL,
        apreBlock = ApreBlockReference(10, sets.map { it.id }))
}
internal fun apreDurableState() = WorkoutUiState(session = Session("apre-session", "APRE",
    exercises = listOf(apreDurableExercise())), startTimeMs = 1_790_000_000_000L,
    currentSetIdx = 2, featureFlags = WorkoutFeatureFlags(workoutV2Homologation = false,
        workoutV2LoadModes = false, workoutV3UnifiedFlow = false))
