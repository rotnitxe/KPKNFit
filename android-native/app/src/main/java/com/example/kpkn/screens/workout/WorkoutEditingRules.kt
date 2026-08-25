package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramCalendarizationMode
import com.example.kpkn.data.models.ReplacementPersistenceScopeV2
import com.example.kpkn.data.models.SimpleProgramKind
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.models.effectiveRepRange
import com.example.kpkn.data.models.isEffectivelyUnilateral
import com.example.kpkn.data.models.isSimpleProgram
import com.example.kpkn.domain.exercises.resolvedCanonicalExerciseId
import com.example.kpkn.domain.workout.expectedSidesForSet
import java.text.Normalizer
import kotlin.math.roundToInt

enum class WorkoutLiveEditPersistenceScope {
    SESSION_ONLY,
    PERMANENT_ALLOWED,
}

object WorkoutEditingRules {
    fun buildEditingState(
        completedSets: Map<String, CompletedSet>,
        exercise: Exercise?,
        setIdx: Int,
        preferredSide: String? = null,
    ): WorkoutEditingState? {
        exercise ?: return null
        val safeSetIdx = setIdx.coerceIn(0, exercise.sets.lastIndex.coerceAtLeast(0))
        val isUnilateral = exercise.isEffectivelyUnilateral()
        if (!isSetDoneForExercise(completedSets, exercise, safeSetIdx)) return null

        val resolvedSide = when {
            !isUnilateral -> null
            preferredSide != null && completedSets.containsKey(buildCompletedSetKey(exercise.id, safeSetIdx, preferredSide)) -> preferredSide
            completedSets.containsKey(buildCompletedSetKey(exercise.id, safeSetIdx, "left")) -> "left"
            completedSets.containsKey(buildCompletedSetKey(exercise.id, safeSetIdx, "right")) -> "right"
            else -> null
        }

        return WorkoutEditingState(
            setKey = workoutSetKey(exercise.id, safeSetIdx, resolvedSide),
            exerciseId = exercise.id,
            setIdx = safeSetIdx,
            side = resolvedSide,
        )
    }

    private fun isSetDone(completedSets: Map<String, CompletedSet>, exerciseId: String, setIdx: Int, isUnilateral: Boolean): Boolean {
        // Fallback for callers without Exercise: keep bilateral vs paired logic.
        // Prefer overload with Exercise when available (see buildEditingState below).
        return completedSets.containsKey(buildCompletedSetKey(exerciseId, setIdx, null)) ||
            (isUnilateral &&
                completedSets.containsKey(buildCompletedSetKey(exerciseId, setIdx, "left")) &&
                completedSets.containsKey(buildCompletedSetKey(exerciseId, setIdx, "right")))
    }

    private fun isSetDoneForExercise(completedSets: Map<String, CompletedSet>, exercise: Exercise, setIdx: Int): Boolean {
        val set = exercise.sets.getOrNull(setIdx) ?: return false
        val sides = exercise.expectedSidesForSet(set)
        return sides.all { side ->
            val key = when (side) {
                "L" -> buildCompletedSetKey(exercise.id, setIdx, "left")
                "R" -> buildCompletedSetKey(exercise.id, setIdx, "right")
                else -> buildCompletedSetKey(exercise.id, setIdx, null)
            }
            completedSets.containsKey(key)
        }
    }

    private fun buildCompletedSetKey(exerciseId: String, setIdx: Int, side: String?): String = when (side) {
        "left" -> "${exerciseId}_${setIdx}_L"
        "right" -> "${exerciseId}_${setIdx}_R"
        else -> "${exerciseId}_${setIdx}"
    }

    fun liveEditPersistenceScope(program: Program): WorkoutLiveEditPersistenceScope {
        val isSimpleCyclic = program.isSimpleProgram &&
            program.simpleProgramKind == SimpleProgramKind.CYCLIC &&
            program.calendarization?.mode != ProgramCalendarizationMode.SIMPLE_DATED
        return if (isSimpleCyclic) {
            WorkoutLiveEditPersistenceScope.PERMANENT_ALLOWED
        } else {
            WorkoutLiveEditPersistenceScope.SESSION_ONLY
        }
    }

    fun canPersistLiveStructuralChanges(program: Program): Boolean =
        liveEditPersistenceScope(program) == WorkoutLiveEditPersistenceScope.PERMANENT_ALLOWED

    fun replacementPersistenceOptions(
        program: Program,
        sessionId: String? = null,
    ): List<ReplacementPersistenceScopeV2> = when {
        canPersistLiveStructuralChanges(program) -> listOf(
            ReplacementPersistenceScopeV2.SESSION_ONLY,
            ReplacementPersistenceScopeV2.PERMANENT,
        )
        program.structure.name == "COMPLEX" &&
            sessionId != null &&
            hasRepeatedLogicalSessionInBlock(program, sessionId) -> listOf(
            ReplacementPersistenceScopeV2.SESSION_ONLY,
            ReplacementPersistenceScopeV2.BLOCK_MATCHING,
        )
        else -> listOf(ReplacementPersistenceScopeV2.SESSION_ONLY)
    }

    fun hasRepeatedLogicalSessionInBlock(program: Program, sessionId: String): Boolean {
        program.macrocycles.forEach { macro ->
            macro.blocks.forEach { block ->
                block.mesocycles.forEach { mesocycle ->
                    mesocycle.weeks.forEach { week ->
                        val targetSlot = week.sessions.indexOfFirst { it.id == sessionId }
                        if (targetSlot < 0) return@forEach
                        val target = week.sessions[targetSlot]
                        val repeated = block.mesocycles
                            .flatMap { it.weeks }
                            .flatMap { candidateWeek ->
                                candidateWeek.sessions.withIndex().map { indexed -> candidateWeek to indexed }
                            }
                            .any { (_, indexed) ->
                                indexed.value.id != sessionId &&
                                    isEquivalentLogicalSession(target, targetSlot, indexed.value, indexed.index)
                            }
                        if (repeated) return true
                    }
                }
            }
        }
        return false
    }

    internal fun isEquivalentLogicalSession(
        target: com.example.kpkn.data.models.Session,
        targetSlot: Int,
        candidate: com.example.kpkn.data.models.Session,
        candidateSlot: Int,
    ): Boolean {
        val targetDay = target.dayOfWeek ?: target.assignedDays.firstOrNull()
        val candidateDay = candidate.dayOfWeek ?: candidate.assignedDays.firstOrNull()
        return normalizeSessionRole(target) == normalizeSessionRole(candidate) &&
            (targetSlot == candidateSlot || (targetDay != null && targetDay == candidateDay)) &&
            sessionStructureSignature(target) == sessionStructureSignature(candidate)
    }

    private fun normalizeSessionRole(session: com.example.kpkn.data.models.Session): String =
        normalizeRoleText(session.scheduleLabel?.takeIf { it.isNotBlank() } ?: session.name)

    private fun normalizeRoleText(value: String?): String =
        Normalizer.normalize(value.orEmpty().trim(), Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
            .lowercase()
            .replace("[^a-z0-9]+".toRegex(), " ")
            .trim()
            .replace("\\s+".toRegex(), " ")

    private fun sessionStructureSignature(session: com.example.kpkn.data.models.Session): String =
        session.parts.joinToString("|") { part ->
            normalizeRoleText(part.name) + ":" + part.exercises.joinToString(",") { exercise ->
                exercise.resolvedCanonicalExerciseId().lowercase()
            }
        }.ifBlank {
            session.exercises.joinToString(",") { it.resolvedCanonicalExerciseId().lowercase() }
        }

    fun unitModeForTrainingMode(mode: TrainingMode): UnitModeV2 = when (mode) {
        TrainingMode.TIME -> UnitModeV2.TIME
        TrainingMode.DISTANCE -> UnitModeV2.DISTANCE
        TrainingMode.CUSTOM -> UnitModeV2.CUSTOM
        TrainingMode.REPS,
        TrainingMode.RM,
        TrainingMode.SOLO_RPE,
        TrainingMode.AMRAP,
        -> UnitModeV2.REPS
    }

    fun normalizeLiveEditedExercise(exercise: Exercise): Exercise = exercise.copy(
        sets = exercise.sets.map { set -> normalizeLiveEditedSet(exercise.trainingMode, set) },
    )

    fun normalizeLiveEditedSet(mode: TrainingMode, set: ExerciseSet): ExerciseSet {
        val unitMode = unitModeForTrainingMode(mode)
        val metricNormalized = when (mode) {
            TrainingMode.TIME -> set.copy(
                unitModeV2 = UnitModeV2.TIME,
                targetDuration = set.targetDuration ?: set.plannedTargetV2?.roundToInt(),
                targetReps = null,
                targetRepsRange = null,
                plannedTargetV2 = null,
                targetPercentageRM = null,
                isAmrap = false,
            )
            TrainingMode.DISTANCE,
            TrainingMode.CUSTOM,
            -> set.copy(
                unitModeV2 = unitMode,
                plannedTargetV2 = set.plannedTargetV2 ?: set.targetReps?.toDouble() ?: set.targetDuration?.toDouble(),
                targetReps = null,
                targetRepsRange = null,
                targetDuration = null,
                targetPercentageRM = null,
                isAmrap = false,
            )
            TrainingMode.RM -> set.copy(
                unitModeV2 = UnitModeV2.REPS,
                targetReps = set.targetReps ?: set.plannedTargetV2?.roundToInt(),
                targetRepsRange = null,
                targetDuration = null,
                plannedTargetV2 = null,
                targetPercentageRM = (set.targetPercentageRM ?: 75.0).coerceIn(40.0, 100.0)
                    .let { kotlin.math.round(it * 100.0) / 100.0 },
                isAmrap = false,
            )
            TrainingMode.SOLO_RPE -> set.copy(
                unitModeV2 = UnitModeV2.REPS,
                targetReps = null,
                targetRepsRange = null,
                targetDuration = null,
                plannedTargetV2 = null,
                targetPercentageRM = null,
                isAmrap = false,
            )
            TrainingMode.AMRAP -> set.copy(
                unitModeV2 = UnitModeV2.REPS,
                targetReps = set.effectiveRepRange()?.max ?: set.targetReps ?: set.plannedTargetV2?.roundToInt(),
                targetDuration = null,
                plannedTargetV2 = null,
                targetPercentageRM = null,
                isAmrap = true,
                intensityMode = IntensityMode.AMRAP,
            )
            TrainingMode.REPS -> set.copy(
                unitModeV2 = UnitModeV2.REPS,
                targetReps = set.effectiveRepRange()?.max ?: set.targetReps ?: set.plannedTargetV2?.roundToInt(),
                targetDuration = null,
                plannedTargetV2 = null,
                targetPercentageRM = null,
                isAmrap = set.isAmrap || set.intensityMode == IntensityMode.AMRAP,
            )
        }

        val intensityNormalized = if (metricNormalized.isAmrap || metricNormalized.intensityMode == IntensityMode.AMRAP) {
            metricNormalized.copy(
                intensityMode = IntensityMode.AMRAP,
                targetRPE = null,
                targetRIR = null,
                isFailure = false,
            )
        } else if (metricNormalized.isFailure || metricNormalized.intensityMode == IntensityMode.FAILURE) {
            metricNormalized.copy(
                intensityMode = IntensityMode.FAILURE,
                targetRPE = null,
                targetRIR = null,
                isFailure = true,
            )
        } else when (mode) {
            // RM prescrito por %RM: la intensidad de esfuerzo se reporta en vivo
            // (RPE/RIR/Fallo). No forzar LOAD ni borrar la elección del usuario.
            TrainingMode.RM -> metricNormalized.copy(
                intensityMode = when (metricNormalized.intensityMode) {
                    IntensityMode.LOAD -> null
                    IntensityMode.SOLO_RM -> null
                    else -> metricNormalized.intensityMode
                },
            )
            TrainingMode.SOLO_RPE -> metricNormalized.copy(
                intensityMode = IntensityMode.RPE,
                targetRPE = (metricNormalized.targetRPE ?: 8.0).coerceIn(1.0, 10.0),
                targetRIR = null,
                isFailure = false,
            )
            else -> metricNormalized.copy(
                intensityMode = when (metricNormalized.intensityMode) {
                    // Preserve an omitted intensity. The live set must not
                    // invent an RPE/RIR requirement during normalization.
                    null -> null
                    IntensityMode.SOLO_RM -> IntensityMode.SOLO_RM
                    else -> metricNormalized.intensityMode
                },
            )
        }

        return intensityNormalized.copy(loadModeV2 = intensityNormalized.loadModeV2 ?: LoadModeV2.LOAD)
    }
}
