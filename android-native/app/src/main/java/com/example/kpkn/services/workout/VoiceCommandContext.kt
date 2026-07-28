package com.example.kpkn.services.workout

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.screens.workout.WorkoutSetDraft

/**
 * Contexto vivo de comandos para que el runtime de voz no dependa de Activity/ViewModel.
 */
data class VoiceCommandContext(
    val sessionId: String? = null,
    val exercise: Exercise? = null,
    val setIndex: Int = 0,
    val totalSets: Int = 0,
    val isTimeMode: Boolean = false,
    val isUnilateral: Boolean = false,
    val baseIntensityMode: IntensityMode? = null,
    val setDraft: WorkoutSetDraft? = null,
    val suggestedWeight: Double? = null,
    val restSecondsRemaining: Int? = null,
    val nextExerciseName: String? = null,
    val showPostExerciseSheet: Boolean = false,
    val showFinishSheet: Boolean = false,
    val supersetRound: Int? = null,
    val isUnilateralSidePending: Boolean = false,
    val completedSidesCount: Int = 0,
    val pendingUnilateralSide: String? = null,
    val allowedCommands: Set<String> = emptySet(),
    val exerciseAliases: Set<String> = emptySet(),
)
