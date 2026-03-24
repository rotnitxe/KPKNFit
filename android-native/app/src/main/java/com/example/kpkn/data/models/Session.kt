package com.example.kpkn.data.models

import kotlinx.serialization.Serializable

@Serializable
data class Session(
    val id: String,
    val name: String,
    val description: String? = null,
    val exercises: List<Exercise> = emptyList(),
    val parts: List<SessionPart> = emptyList(),
    val dayOfWeek: Int? = null,
    val scheduleLabel: String? = null,
    val assignedDays: List<Int> = emptyList(),
    val sessionB: Session? = null,
    val sessionC: Session? = null,
    val sessionD: Session? = null,
    val isMeetDay: Boolean = false,
    val isMainSession: Boolean = false,
    val focus: String? = null,
)

@Serializable
data class SessionPart(
    val id: String,
    val name: String,
    val exercises: List<Exercise> = emptyList(),
    val color: String? = null,
)

@Serializable
data class Exercise(
    val id: String,
    val name: String,
    val exerciseDbId: String? = null,
    val sets: List<ExerciseSet> = emptyList(),
    val warmupSets: List<WarmupSetDefinition> = emptyList(),
    val restTime: Int? = null,
    val isFavorite: Boolean = false,
    val trainingMode: TrainingMode = TrainingMode.REPS,
    val customUnit: String? = null,
    val reference1RM: Double? = null,
    val isStarTarget: Boolean = false,
    val supersetId: String? = null,
    val variantName: String? = null,
    val isUnilateral: Boolean = false,
    val isCalibratorAmrap: Boolean = false,
    val goal1RM: Double? = null,
    val calculated1RM: Double? = null,
    val damageProfile: DamageProfile? = null,
    val isCompetitionLift: Boolean = false,
)

enum class TrainingMode { REPS, TIME, PERCENT, CUSTOM }
enum class DamageProfile { STRETCH, SQUEEZE, NORMAL }

@Serializable
data class ExerciseSet(
    val id: String,
    val targetReps: Int? = null,
    val targetDuration: Int? = null,
    val targetRPE: Double? = null,
    val targetRIR: Int? = null,
    val intensityMode: IntensityMode? = null,
    val targetPercentageRM: Double? = null,
    val weight: Double? = null,
    val advancedTechnique: String? = null,
    val completedReps: Int? = null,
    val completedRPE: Double? = null,
    val completedRIR: Int? = null,
    val isFailure: Boolean = false,
    val isAmrap: Boolean = false,
    val isIneffective: Boolean = false,
    val isPartial: Boolean = false,
    val partialReps: Int? = null,
    val performanceMode: PerformanceMode? = null,
)

enum class IntensityMode { RPE, RIR, FAILURE, AMRAP, LOAD, SOLO_RM }
enum class PerformanceMode { TARGET, FAILURE, FAILED }

@Serializable
data class WarmupSetDefinition(
    val id: String,
    val percentageOfWorkingWeight: Double,
    val targetReps: Int,
    val matchRPE: Double? = null,
)
