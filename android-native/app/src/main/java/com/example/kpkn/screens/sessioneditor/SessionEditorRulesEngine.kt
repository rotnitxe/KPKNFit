package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
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
        exerciseIndex: Map<String, ExerciseMuscleInfo> = emptyMap(),
    ): Session {
        val safeSetCount = defaults.setCount.coerceAtLeast(1)
        val safeReps = defaults.reps.coerceAtLeast(1)
        val safeRpe = defaults.rpe.coerceIn(1.0, 10.0)
        val safeRest = defaults.normalRestSeconds.coerceAtLeast(0)
        val safeSideRest = defaults.betweenSidesRestSeconds.coerceAtLeast(0)
        val safeBetween = defaults.supersetBetweenRestSeconds.coerceAtLeast(0)
        val safeRound = defaults.supersetRoundRestSeconds.coerceAtLeast(0)

        fun Exercise.applyRuleDefaults(): Exercise {
            val info = resolveExerciseInfo(this, exerciseIndex)
            val isCompound = defaults.hasCompoundOverrides &&
                com.example.kpkn.domain.templates.SessionTemplateQualityRules.isCompound(info)
            val isIsolation = !isCompound && defaults.hasIsolationOverrides &&
                com.example.kpkn.domain.templates.SessionTemplateQualityRules.isIsolation(info)

            val intensityType = when {
                isCompound -> defaults.compoundIntensityType ?: defaults.intensityType
                isIsolation -> defaults.isolationIntensityType ?: defaults.intensityType
                else -> defaults.intensityType
            }
            val mode = when (intensityType) {
                DefaultIntensityType.RIR -> IntensityMode.RIR
                DefaultIntensityType.FALLO -> IntensityMode.FAILURE
                else -> IntensityMode.RPE
            }
            val effectiveReps = when {
                isCompound -> (defaults.compoundReps ?: safeReps).coerceAtLeast(1)
                isIsolation -> (defaults.isolationReps ?: safeReps).coerceAtLeast(1)
                else -> safeReps
            }
            // FIX: Respetar la intensidad realmente configurada por el usuario.
            // Antes effectiveRpe se coaccionaba siempre como RPE (1-10) y luego se truncaba a RIR 0-5,
            // ignorando RIR 0 para compuestos (coerce 1-10) y perdiendo RIR 6 por toInt+cap 5.
            val rawIntensity = when {
                isCompound -> defaults.compoundRpe ?: defaults.rpe
                isIsolation -> defaults.isolationRpe ?: defaults.rpe
                else -> defaults.rpe
            }
            val effectiveIntensity = when (mode) {
                IntensityMode.RIR -> rawIntensity.coerceIn(0.0, 6.0)
                IntensityMode.RPE -> rawIntensity.coerceIn(1.0, 10.0)
                else -> rawIntensity
            }
            val effectiveRest = when {
                isCompound -> (defaults.compoundRestSeconds ?: safeRest).coerceAtLeast(0)
                isIsolation -> (defaults.isolationRestSeconds ?: safeRest).coerceAtLeast(0)
                else -> safeRest
            }
            val nextSets = List(safeSetCount) { index ->
                val existing = sets.getOrNull(index)
                val target = (existing ?: ExerciseSet(id = UUID.randomUUID().toString())).copy(
                    targetReps = effectiveReps,
                    targetRPE = if (mode == IntensityMode.RPE) effectiveIntensity else null,
                    targetRIR = if (mode == IntensityMode.RIR) effectiveIntensity.roundToInt().coerceIn(0, 6) else null,
                    intensityMode = mode,
                    targetPercentageRM = null,
                    isFailure = mode == IntensityMode.FAILURE,
                )
                normalizeSet(target, this)
            }
            return copy(
                sets = nextSets,
                restTime = effectiveRest,
                // F1: no borrar restBetweenSides si la regla está en 0; preservar valor existente
                restBetweenSidesSeconds = if (safeSideRest > 0) safeSideRest else this.restBetweenSidesSeconds,
            )
        }

        val scopedExerciseIds = if (partId == null) {
            session.allExercises().map { it.id }.toSet()
        } else {
            session.parts.firstOrNull { it.id == partId }?.exercises?.map { it.id }?.toSet().orEmpty()
        }
        val updatedGroups = session.allSupersetGroups().map { group ->
            if (group.exerciseOrder.none { it in scopedExerciseIds }) group
            else if (group.exerciseOrder.all { it in scopedExerciseIds }) group.copy(
                restBetweenExercises = safeBetween,
                restAfterSuperset = safeRound,
            ) else group // cross-part: no tocar (F1 D8)
        }

        return session.copy(
            exercises = if (partId == null) session.exercises.map { it.applyRuleDefaults() } else session.exercises,
            parts = session.parts.map { part ->
                if (partId != null && part.id != partId) part
                else part.copy(exercises = part.exercises.map { it.applyRuleDefaults() })
            },
            supersetGroups = updatedGroups,
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
        if (draft.name.isBlank()) {
            return SessionRulesValidationResult(blockingError = "La sesión debe tener un nombre antes de guardar.")
        }
        // Editor rules are defaults-only. Legacy limit fields may exist in old drafts,
        // but they intentionally do not block saves or emit warnings from this sheet.
        return SessionRulesValidationResult()
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

    private fun resolvePrimaryMuscle(info: ExerciseMuscleInfo): String? {
        val primary = info.involvedMuscles.firstOrNull { it.role == MuscleRole.PRIMARY }
            ?: info.involvedMuscles.firstOrNull()
            ?: return null
        return VolumeCalculator.normalizeCanonicalMuscleGroup(primary.muscle, primary.emphasis)
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
}

