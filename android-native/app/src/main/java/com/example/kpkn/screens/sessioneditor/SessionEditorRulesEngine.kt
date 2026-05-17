package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.FATIGUE_ROLE_MULTIPLIERS
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.domain.calculations.calculateSuggestedLoad
import com.example.kpkn.domain.calculations.suggestRestSeconds
import com.example.kpkn.domain.training.VolumeCalculator
import java.util.UUID
import kotlin.math.roundToInt

data class SessionRulesValidationResult(
    val blockingError: String? = null,
    val warnings: List<String> = emptyList(),
)

object SessionEditorRulesEngine {

    fun applyDefaults(
        session: Session,
        defaults: SessionEditorRuleDefaults,
        partId: String?,
    ): Session {
        val safeSetCount = defaults.setCount.coerceAtLeast(1)
        val safeReps = defaults.reps.coerceAtLeast(1)
        val safeRpe = defaults.rpe.coerceIn(1.0, 10.0)

        fun Exercise.applyRuleDefaults(): Exercise {
            val nextSets = List(safeSetCount) { index ->
                val existing = sets.getOrNull(index)
                val target = (existing ?: ExerciseSet(id = UUID.randomUUID().toString())).copy(
                    targetReps = safeReps,
                    targetRPE = safeRpe,
                    intensityMode = IntensityMode.RPE,
                    targetRIR = null,
                    targetPercentageRM = null,
                    isFailure = false,
                )
                normalizeSet(target, this)
            }
            return copy(
                sets = nextSets,
                restTime = suggestRestSeconds(nextSets.size, safeRpe),
            )
        }

        return session.copy(
            exercises = if (partId == null) session.exercises.map { it.applyRuleDefaults() } else session.exercises,
            parts = session.parts.map { part ->
                if (partId != null && part.id != partId) part
                else part.copy(exercises = part.exercises.map { it.applyRuleDefaults() })
            },
        )
    }

    fun normalizeRuleLimits(
        existing: SessionEditorRuleLimits,
        maxRPE: Double?,
        maxExercisesPerMuscle: Int?,
    ): SessionEditorRuleLimits {
        val normalizedMaxRpe = maxRPE?.takeIf { it > 0.0 }?.coerceIn(1.0, 10.0)
        val normalizedMaxExercises = maxExercisesPerMuscle?.takeIf { it > 0 }
        return existing.copy(
            maxRPE = normalizedMaxRpe,
            maxExercisesPerMuscle = normalizedMaxExercises,
        )
    }

    fun normalizeAdvancedRuleLimits(
        existing: SessionEditorRuleLimits,
        maxVolumePerMuscleSession: Double?,
        maxVolumePerMuscleWeekly: Double?,
        maxSamePatternPerSession: Int?,
        rigidLimits: Boolean,
    ): SessionEditorRuleLimits {
        return existing.copy(
            maxVolumePerMuscleSession = maxVolumePerMuscleSession?.takeIf { it > 0.0 },
            maxVolumePerMuscleWeekly = maxVolumePerMuscleWeekly?.takeIf { it > 0.0 },
            maxSamePatternPerSession = maxSamePatternPerSession?.takeIf { it > 0 },
            rigidLimits = rigidLimits,
        )
    }

    fun applyGlobalIntensityAdjustment(
        session: Session,
        targetMode: IntensityMode,
        value: Double,
        targetMuscles: Set<String>?,
        exerciseIndex: Map<String, ExerciseMuscleInfo>,
    ): Session {
        val normalizedMuscles = targetMuscles.orEmpty().map { VolumeCalculator.normalizeCanonicalMuscleGroup(it) }.toSet()
        val applyEverywhere = normalizedMuscles.isEmpty()
        return session.copy(
            exercises = session.exercises.map { exercise ->
                adjustExerciseIntensity(
                    exercise = exercise,
                    targetMode = targetMode,
                    value = value,
                    targetMuscles = normalizedMuscles,
                    applyEverywhere = applyEverywhere,
                    exerciseIndex = exerciseIndex,
                )
            },
            parts = session.parts.map { part ->
                part.copy(
                    exercises = part.exercises.map { exercise ->
                        adjustExerciseIntensity(
                            exercise = exercise,
                            targetMode = targetMode,
                            value = value,
                            targetMuscles = normalizedMuscles,
                            applyEverywhere = applyEverywhere,
                            exerciseIndex = exerciseIndex,
                        )
                    }
                )
            }
        )
    }

    fun validateBeforeSave(
        draft: Session,
        weekSessions: List<Session>,
        ruleLimits: SessionEditorRuleLimits,
        exerciseIndex: Map<String, ExerciseMuscleInfo>,
    ): SessionRulesValidationResult {
        val warnings = mutableListOf<String>()
        if (draft.name.isBlank()) {
            return SessionRulesValidationResult(blockingError = "La sesión debe tener un nombre antes de guardar.")
        }

        val maxRpe = ruleLimits.maxRPE
        if (maxRpe != null) {
            draft.allExercises().forEach { exercise ->
                exercise.sets.forEach { set ->
                    val isFailureSet = set.isFailure || set.intensityMode == IntensityMode.FAILURE
                    if (isFailureSet) return@forEach
                    val effectiveRpe = when {
                        set.targetRPE != null -> set.targetRPE
                        set.targetRIR != null -> (10 - set.targetRIR).toDouble()
                        else -> null
                    }
                    if (effectiveRpe != null && effectiveRpe > maxRpe) {
                        val message = "Intensidad máxima configurada: RPE ${formatOneDecimal(maxRpe)}. Hay series que la superan (RPE ${formatOneDecimal(effectiveRpe)})."
                        if (ruleLimits.rigidLimits) return SessionRulesValidationResult(blockingError = message)
                        warnings += message
                    }
                }
            }
        }

        val maxExercisesPerMuscle = ruleLimits.maxExercisesPerMuscle
        if (maxExercisesPerMuscle != null) {
            val muscleCount = mutableMapOf<String, Int>()
            draft.allExercises().forEach { exercise ->
                val info = resolveExerciseInfo(exercise, exerciseIndex)
                val primaryMuscle = info
                    ?.involvedMuscles
                    ?.firstOrNull { it.role == MuscleRole.PRIMARY }
                    ?.let { VolumeCalculator.normalizeMuscleGroup(it.muscle, it.emphasis) }
                    ?: "_desconocido"
                muscleCount[primaryMuscle] = (muscleCount[primaryMuscle] ?: 0) + 1
            }

            val exceeded = muscleCount.entries.firstOrNull { (_, count) -> count > maxExercisesPerMuscle }
            if (exceeded != null) {
                val message = "Máx $maxExercisesPerMuscle ejercicios por músculo. ${exceeded.key} tiene ${exceeded.value}."
                if (ruleLimits.rigidLimits) return SessionRulesValidationResult(blockingError = message)
                warnings += message
            }
        }

        val maxVolumePerMuscleSession = ruleLimits.maxVolumePerMuscleSession
        if (maxVolumePerMuscleSession != null) {
            val sessionVolume = computeSessionVolumeByMuscle(draft, exerciseIndex)
            val exceeded = sessionVolume.entries.firstOrNull { it.value > maxVolumePerMuscleSession }
            if (exceeded != null) {
                val message = "Volumen sesión por músculo excedido (${formatOneDecimal(maxVolumePerMuscleSession)}). ${exceeded.key}: ${formatOneDecimal(exceeded.value)}."
                if (ruleLimits.rigidLimits) return SessionRulesValidationResult(blockingError = message)
                warnings += message
            }
        }

        val maxVolumePerMuscleWeekly = ruleLimits.maxVolumePerMuscleWeekly
        if (maxVolumePerMuscleWeekly != null) {
            val draftAwareWeekSessions = if (weekSessions.any { it.id == draft.id }) {
                weekSessions.map { if (it.id == draft.id) draft else it }
            } else {
                weekSessions + draft
            }
            val weeklyVolume = computeWeeklyVolumeByMuscle(draftAwareWeekSessions, exerciseIndex)
            val exceeded = weeklyVolume.entries.firstOrNull { it.value > maxVolumePerMuscleWeekly }
            if (exceeded != null) {
                val message = "Volumen semanal por músculo excedido (${formatOneDecimal(maxVolumePerMuscleWeekly)}). ${exceeded.key}: ${formatOneDecimal(exceeded.value)}."
                if (ruleLimits.rigidLimits) return SessionRulesValidationResult(blockingError = message)
                warnings += message
            }
        }

        val maxSamePatternPerSession = ruleLimits.maxSamePatternPerSession
        if (maxSamePatternPerSession != null) {
            val patternCount = mutableMapOf<String, Int>()
            draft.allExercises().forEach { exercise ->
                val info = resolveExerciseInfo(exercise, exerciseIndex)
                val pattern = info?.force?.ifBlank { null } ?: "Patrón desconocido"
                patternCount[pattern] = (patternCount[pattern] ?: 0) + 1
            }
            val exceeded = patternCount.entries.firstOrNull { it.value > maxSamePatternPerSession }
            if (exceeded != null) {
                val message = "Patrón repetido excedido (máx ${maxSamePatternPerSession}). ${exceeded.key}: ${exceeded.value}."
                if (ruleLimits.rigidLimits) return SessionRulesValidationResult(blockingError = message)
                warnings += message
            }
        }

        return SessionRulesValidationResult(warnings = warnings)
    }

    private fun adjustExerciseIntensity(
        exercise: Exercise,
        targetMode: IntensityMode,
        value: Double,
        targetMuscles: Set<String>,
        applyEverywhere: Boolean,
        exerciseIndex: Map<String, ExerciseMuscleInfo>,
    ): Exercise {
        val info = resolveExerciseInfo(exercise, exerciseIndex)
        val primary = info?.let(::resolvePrimaryMuscle)?.let(VolumeCalculator::normalizeCanonicalMuscleGroup)
        val shouldApply = applyEverywhere || (primary != null && primary in targetMuscles)
        if (!shouldApply) return exercise

        val normalizedSets = exercise.sets.map { set ->
            when (targetMode) {
                IntensityMode.RPE -> set.copy(
                    intensityMode = IntensityMode.RPE,
                    targetRPE = value.coerceIn(1.0, 10.0),
                    targetRIR = null,
                    isFailure = false,
                )
                IntensityMode.RIR -> set.copy(
                    intensityMode = IntensityMode.RIR,
                    targetRIR = value.roundToInt().coerceIn(0, 6),
                    targetRPE = null,
                    isFailure = false,
                )
                IntensityMode.FAILURE -> set.copy(
                    intensityMode = IntensityMode.FAILURE,
                    isFailure = true,
                    targetRPE = null,
                    targetRIR = null,
                )
                IntensityMode.SOLO_RM -> set.copy(
                    intensityMode = IntensityMode.SOLO_RM,
                    targetPercentageRM = value.coerceIn(40.0, 100.0),
                    targetRPE = null,
                    targetRIR = null,
                    isFailure = false,
                )
                IntensityMode.AMRAP,
                IntensityMode.LOAD -> set
            }
        }
        return exercise.copy(sets = normalizedSets)
    }

    private fun resolveExerciseInfo(
        exercise: Exercise,
        exerciseIndex: Map<String, ExerciseMuscleInfo>,
    ): ExerciseMuscleInfo? {
        val byId = exercise.exerciseDbId ?: exercise.exerciseId
        return byId?.lowercase()?.let(exerciseIndex::get)
            ?: exerciseIndex.values.firstOrNull { it.name.equals(exercise.name, ignoreCase = true) }
    }

    private fun resolvePrimaryMuscle(info: ExerciseMuscleInfo): String? {
        val primary = info.involvedMuscles.firstOrNull { it.role == MuscleRole.PRIMARY }
            ?: info.involvedMuscles.firstOrNull()
            ?: return null
        return VolumeCalculator.normalizeMuscleGroup(primary.muscle, primary.emphasis)
    }

    private fun computeSessionVolumeByMuscle(
        session: Session,
        exerciseIndex: Map<String, ExerciseMuscleInfo>,
    ): Map<String, Double> {
        val map = mutableMapOf<String, Double>()
        session.allExercises().forEach { exercise ->
            val setCount = exercise.sets.count { !it.isIneffective }.coerceAtLeast(1)
            val info = resolveExerciseInfo(exercise, exerciseIndex)
            if (info == null) {
                val fallback = exercise.name.ifBlank { "General" }
                map[fallback] = (map[fallback] ?: 0.0) + setCount.toDouble()
                return@forEach
            }
            info.involvedMuscles.forEach { muscle ->
                val canonical = VolumeCalculator.normalizeCanonicalMuscleGroup(muscle.muscle, muscle.emphasis)
                val multiplier = FATIGUE_ROLE_MULTIPLIERS[muscle.role] ?: 0.0
                if (canonical.isNotBlank() && multiplier > 0.0) {
                    map[canonical] = (map[canonical] ?: 0.0) + setCount * multiplier
                }
            }
        }
        return map
    }

    private fun computeWeeklyVolumeByMuscle(
        sessions: List<Session>,
        exerciseIndex: Map<String, ExerciseMuscleInfo>,
    ): Map<String, Double> {
        val map = mutableMapOf<String, Double>()
        sessions.forEach { session ->
            computeSessionVolumeByMuscle(session, exerciseIndex).forEach { (muscle, value) ->
                map[muscle] = (map[muscle] ?: 0.0) + value
            }
        }
        return map
    }

    private fun normalizeSet(set: ExerciseSet, exercise: Exercise): ExerciseSet {
        val normalized = when (exercise.trainingMode) {
            TrainingMode.RM -> set.copy(
                intensityMode = IntensityMode.LOAD,
                targetRPE = null,
                targetRIR = null,
                isFailure = false,
                isAmrap = false,
                targetPercentageRM = (set.targetPercentageRM ?: 75.0).coerceIn(40.0, 100.0),
            )
            TrainingMode.SOLO_RPE -> set.copy(
                intensityMode = IntensityMode.RPE,
                targetRPE = (set.targetRPE ?: 8.0).coerceIn(1.0, 10.0),
                targetRIR = null,
                targetPercentageRM = null,
                targetReps = null,
                targetDuration = null,
                isFailure = false,
                isAmrap = false,
            )
            else -> {
                val resolvedMode = when (set.intensityMode) {
                    null, IntensityMode.SOLO_RM -> IntensityMode.RPE
                    else -> set.intensityMode
                }
                set.copy(intensityMode = resolvedMode)
            }
        }
        val autoWeight = calculateSuggestedLoad(exercise, normalized)
        return normalized.copy(weight = autoWeight ?: normalized.weight)
    }

    private fun formatOneDecimal(value: Double): String = "%.1f".format(value)
}

