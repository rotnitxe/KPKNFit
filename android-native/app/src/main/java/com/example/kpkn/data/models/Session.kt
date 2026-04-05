package com.example.kpkn.data.models

import kotlinx.serialization.Serializable

@Serializable
data class Session(
    val id: String,
    val name: String,
    val description: String? = null,
    val exercises: List<Exercise> = emptyList(),
    val warmup: List<WarmupExercise> = emptyList(),
    val parts: List<SessionPart> = emptyList(),
    val background: SessionBackground? = null,
    val coverStyle: CoverStyle? = null,
    val dayOfWeek: Int? = null,
    val scheduleLabel: String? = null,
    val assignedDays: List<Int> = emptyList(),
    val sessionB: Session? = null,
    val sessionC: Session? = null,
    val sessionD: Session? = null,
    val isMeetDay: Boolean = false,
    val isCompetitionSession: Boolean = false,
    val isMainSession: Boolean = false,
    val focus: String? = null,
    val microProgram: SessionMicroProgram? = null,
    val meetBodyweight: Double? = null,
    val meetResults: MeetResults? = null,
)

@Serializable
data class SessionPart(
    val id: String,
    val name: String,
    val exercises: List<Exercise> = emptyList(),
    val color: String? = null,
)

@Serializable
data class SessionBackground(
    val type: SessionBackgroundType = SessionBackgroundType.COLOR,
    val value: String,
    val style: SessionBackgroundStyle? = null,
)

@Serializable
data class SessionBackgroundStyle(
    val blur: Float? = null,
    val brightness: Float? = null,
)

@Serializable
data class CoverStyle(
    val filters: CoverFilters? = null,
    val enableMotion: Boolean = false,
    val labelPosition: LabelPosition = LabelPosition.BOTTOM_LEFT,
)

@Serializable
data class CoverFilters(
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val brightness: Float = 1f,
    val grayscale: Float = 0f,
    val sepia: Float = 0f,
    val vignette: Float = 0f,
)

@Serializable
data class WarmupExercise(
    val id: String,
    val name: String,
    val description: String? = null,
    val category: String? = null,
    val duration: Int? = null,
    val sets: Int? = null,
    val reps: String? = null,
)

@Serializable
data class SessionMicroProgram(
    val enabled: Boolean = false,
    val everyXCycles: Int = 1,
    val isMainInCycle: Boolean = true,
    val rules: List<MicroProgramRule> = emptyList(),
)

@Serializable
data class MicroProgramRule(
    val id: String,
    val title: String,
    val description: String? = null,
)

@Serializable
data class MeetResults(
    val placement: String? = null,
    val total: Double? = null,
    val dots: Double? = null,
    val awards: List<String> = emptyList(),
)

@Serializable
data class Exercise(
    val id: String,
    val name: String,
    val exerciseDbId: String? = null,
    val exerciseId: String? = null,
    val sets: List<ExerciseSet> = emptyList(),
    val warmupSets: List<WarmupSetDefinition> = emptyList(),
    val restTime: Int? = null,
    val isFavorite: Boolean = false,
    val trainingMode: TrainingMode = TrainingMode.REPS,
    val customUnit: String? = null,
    val reference1RM: Double? = null,
    val targetSessionGoal: String? = null,
    val isStarTarget: Boolean = false,
    val trackHeartRate: Boolean = false,
    val setupDetails: ExerciseSetupDetails? = null,
    val supersetId: String? = null,
    val variantName: String? = null,
    val prFor1RM: PrReference? = null,
    val consolidatedWeight: ConsolidatedWeight? = null,
    val brandEquivalencies: List<BrandEquivalency> = emptyList(),
    val isUnilateral: Boolean = false,
    val isCalibratorAmrap: Boolean = false,
    val goal1RM: Double? = null,
    val calculated1RM: Double? = null,
    val damageProfile: DamageProfile? = null,
    val isCompetitionLift: Boolean = false,
    val setupCues: List<String> = emptyList(),
    val executionCues: List<String> = emptyList(),
)

enum class TrainingMode { REPS, TIME, PERCENT, CUSTOM, DISTANCE }
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
    val completedDuration: Int? = null,
    val completedRPE: Double? = null,
    val completedRIR: Int? = null,
    val isFailure: Boolean = false,
    val isAmrap: Boolean = false,
    val isCalibrator: Boolean = false,
    val isIneffective: Boolean = false,
    val isPartial: Boolean = false,
    val partialReps: Int? = null,
    val machineBrand: String? = null,
    val isChangeOfPlans: Boolean = false,
    val dropSets: List<DropSetData> = emptyList(),
    val restPauses: List<RestPauseData> = emptyList(),
    val performanceMode: PerformanceMode? = null,
    val technicalWeight: Double? = null,
    val consolidatedWeight: Double? = null,
    val attemptResult: AttemptResult? = null,
    val judgingLights: List<Boolean?> = emptyList(),
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

@Serializable
data class ExerciseSetupDetails(
    val seatPosition: String? = null,
    val pinPosition: String? = null,
    val equipmentNotes: String? = null,
)

@Serializable
data class PrReference(
    val weight: Double,
    val reps: Int,
)

@Serializable
data class ConsolidatedWeight(
    val weightKg: Double,
    val reps: Int,
)

@Serializable
data class BrandEquivalency(
    val brand: String,
    val pr: BrandPr? = null,
)

@Serializable
data class BrandPr(
    val weight: Double,
    val reps: Int,
    val e1rm: Double,
)

@Serializable
data class DropSetData(
    val weight: Double,
    val reps: Int,
)

@Serializable
data class RestPauseData(
    val restTime: Int,
    val reps: Int,
)

@Serializable
enum class SessionBackgroundType { COLOR, IMAGE }

@Serializable
enum class LabelPosition { BOTTOM_LEFT, CENTER, BOTTOM_CENTER }

@Serializable
enum class AttemptResult { GOOD, NO_LIFT, PENDING }
