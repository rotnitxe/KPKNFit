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
    val competitionDetails: CompetitionDetails? = null,
    val competitionRecordId: String? = null,
    val competitionKeyDateId: String? = null,
    val competitionSportType: CompetitionTemplateType? = null,
    val competitionRecordMode: CompetitionRecordMode? = null,
    val trainingBackup: TrainingBackup? = null,
    val supersetGroups: List<SupersetGroup> = emptyList(),
    val lastModifiedAtMs: Long = 0L,
) {
    fun allSupersetGroups(): List<SupersetGroup> {
        val local = supersetGroups.ifEmpty { legacySupersetGroups() }
        if (local.isNotEmpty()) return local
        return legacySupersetGroups()
    }

    private fun legacySupersetGroups(): List<SupersetGroup> {
        val supersetIds = allExercises().mapNotNull { it.supersetId?.takeIf { s -> s.isNotBlank() } }.distinct()
        if (supersetIds.isEmpty()) return emptyList()
        return supersetIds.map { id ->
            val members = allExercises().filter { it.supersetId == id }
            SupersetGroup(
                id = id,
                exerciseOrder = members.map { it.id },
                restBetweenExercises = members.firstOrNull()?.supersetRestBetween ?: 60,
                restAfterSuperset = members.firstOrNull()?.supersetRestAfter ?: 120,
                rounds = null,
                isOptional = false,
            )
        }
    }

    fun allExercises(): List<Exercise> = exercises + parts.flatMap { it.exercises }
}

@Serializable
data class SupersetGroup(
    val id: String,
    val exerciseOrder: List<String>,
    val restBetweenExercises: Int = 60,
    val restAfterSuperset: Int = 120,
    val rounds: Int? = null,
    val visualPlacement: SupersetVisualPlacement? = null,
    val roundRestBetweenExercises: Map<Int, Int> = emptyMap(),
    val roundRestAfterSuperset: Map<Int, Int> = emptyMap(),
    val isOptional: Boolean = false,
)

@Serializable
data class SupersetVisualPlacement(
    val partId: String? = null,
    val anchorExerciseId: String? = null,
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
data class CompetitionDetails(
    val competitionDate: String? = null,
    val startTime: String? = null,
    val location: String? = null,
    val federation: String? = null,
    val category: String? = null,
    val division: String? = null,
    val equipment: String? = null,
    val targetBodyweightKg: Double? = null,
    val weighInDate: String? = null,
    val weighInTime: String? = null,
    val reminderOneWeekEnabled: Boolean = true,
    val reminder48hEnabled: Boolean = true,
    val reminderStartEnabled: Boolean = false,
    val strategyNotes: String? = null,
)

@Serializable
data class TrainingBackup(
    val exercises: List<Exercise> = emptyList(),
    val parts: List<SessionPart> = emptyList(),
    val warmup: List<WarmupExercise> = emptyList(),
    val savedAtMs: Long = 0L,
)

@Serializable
data class Exercise(
    val id: String,
    val name: String,
    val exerciseDbId: String? = null,
    val exerciseId: String? = null,
    val canonicalExerciseId: String? = null,
    val exerciseFamilyId: String? = null,
    val relativeToCanonicalExerciseId: String? = null,
    val relationshipType: ExerciseRelationshipType? = null,
    val relationshipNotes: String? = null,
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
    val supersetRestBetween: Int? = null,
    val supersetRestAfter: Int? = null,
    val supersetGroupRef: String? = null,
    val variantName: String? = null,
    val prFor1RM: PrReference? = null,
    val consolidatedWeight: ConsolidatedWeight? = null,
    val brandEquivalencies: List<BrandEquivalency> = emptyList(),
    val isUnilateral: Boolean = false,
    val unilateralMode: UnilateralMode = UnilateralMode.BILATERAL,
    val unilateralSideOrder: UnilateralSideOrder = UnilateralSideOrder.LEFT_RIGHT,
    val unilateralIntensityMode: UnilateralIntensityMode = UnilateralIntensityMode.SHARED,
    val restBetweenSidesSeconds: Int? = null,
    val isCalibratorAmrap: Boolean = false,
    val goal1RM: Double? = null,
    val goalPr: PrReference? = null,
    val calculated1RM: Double? = null,
    val damageProfile: DamageProfile? = null,
    val isCompetitionLift: Boolean = false,
    val setupCues: List<String> = emptyList(),
    val executionCues: List<String> = emptyList(),
    val contextProfilesV3: List<WorkoutContextProfile> = emptyList(),
    val defaultContextProfileIdV3: String? = null,
    val mobilitySeries: List<MobilitySeries> = emptyList(),
    val timeStrategy: TimeStrategy? = null,
)

@Serializable
data class MobilitySeries(
    val id: String,
    val exerciseDbId: String? = null,
    val name: String,
    val sets: Int = 1,
    val reps: String? = null,
    val durationSeconds: Int? = null,
    val notes: String? = null,
    val associatedDiscomforts: List<String> = emptyList(),
    val bodyZones: List<String> = emptyList(),
    val movementPatterns: List<String> = emptyList(),
)

enum class TrainingMode { REPS, TIME, RM, CUSTOM, DISTANCE, SOLO_RPE, AMRAP }
enum class TimeStrategy { COUNTDOWN, CHRONOMETER, FREE }
enum class DamageProfile { STRETCH, SQUEEZE, NORMAL }
enum class ExerciseRelationshipType { VARIATION, ASSISTANCE, OVERLOAD, TECHNIQUE }
enum class UnilateralMode { BILATERAL, UNILATERAL_PAIRED, UNILATERAL_DIFFERENTIAL }
enum class UnilateralSideOrder { LEFT_RIGHT, RIGHT_LEFT }
enum class UnilateralIntensityMode { SHARED, INDEPENDENT }
enum class TechniqueType { DROP_SET, REST_PAUSE, PARTIALS, ISO_HOLD, NEGATIVES, CLUSTER_SET }

@Serializable
data class PlannedTechnique(
    val type: TechniqueType,
    val params: Map<String, String> = emptyMap(),
)

@Serializable
data class UnilateralTarget(
    val weight: Double? = null,
    val targetReps: Int? = null,
    val targetDuration: Int? = null,
    val targetValue: Double? = null,
    val targetRPE: Double? = null,
    val targetRIR: Int? = null,
    val intensityMode: IntensityMode? = null,
)

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
    val technicalQuality: Int? = null,
    val discomfortIds: List<String> = emptyList(),
    val refereeNotes: String? = null,
    val loadModeV2: LoadModeV2? = null,
    val unitModeV2: UnitModeV2? = null,
    val plannedTargetV2: Double? = null,
    val tagId: String? = null,
    val setupId: String? = null,
    val contextKeyV2: String? = null,
    val contextProfileIdV3: String? = null,
    val defaultTagIdV3: String? = null,
    val defaultSetupProfileIdV3: String? = null,
    val timeProgressionStrategyV3: TimeProgressionStrategyV3 = TimeProgressionStrategyV3.LOAD_THEN_TIME,
    val leftTarget: UnilateralTarget? = null,
    val rightTarget: UnilateralTarget? = null,
    val restBetweenSides: Int? = null,
    val plannedIntensityTechniques: List<PlannedTechnique> = emptyList(),
)

enum class IntensityMode { RPE, RIR, FAILURE, AMRAP, LOAD, SOLO_RM }
enum class PerformanceMode { TARGET, FAILURE, FAILED }

@Serializable
data class WarmupSetDefinition(
    val id: String,
    val percentageOfWorkingWeight: Double,
    val targetReps: Int,
    val matchRPE: Double? = null,
    val restBetween: Int? = null,
)

@Serializable
data class ExerciseSetupDetails(
    val seatPosition: String? = null,
    val pinPosition: String? = null,
    val equipmentNotes: String? = null,
    val barWeightKg: Double? = null,
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

fun Exercise.isInSuperset(): Boolean =
    supersetGroupRef?.isNotBlank() == true || supersetId?.isNotBlank() == true

fun Exercise.isEffectivelyUnilateral(): Boolean =
    unilateralMode != UnilateralMode.BILATERAL || isUnilateral

fun Session.effectiveSupersetGroupFor(exercise: Exercise): SupersetGroup? {
    val ref = exercise.supersetGroupRef ?: exercise.supersetId ?: return null
    return allSupersetGroups().firstOrNull { it.id == ref }
}

fun Exercise.supersetGroupRefOrLegacyId(): String? =
    supersetGroupRef?.takeIf { it.isNotBlank() } ?: supersetId?.takeIf { it.isNotBlank() }
