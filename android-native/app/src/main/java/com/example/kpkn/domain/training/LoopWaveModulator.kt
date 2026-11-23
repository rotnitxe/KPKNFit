package com.example.kpkn.domain.training

import com.example.kpkn.data.models.ApreBlockReference
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Loop
import com.example.kpkn.data.models.LoopApreVariant
import com.example.kpkn.data.models.LoopMode
import com.example.kpkn.data.models.LoopModulationScope
import com.example.kpkn.data.models.LoopType
import com.example.kpkn.data.models.LoopWaveStep
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.RepRange
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SetPurpose
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.workout.LoadSuggestionEngine
import kotlin.math.roundToInt

/**
 * Pure domain modulator for progressive / dynamic loops (#14.3).
 * No `android.*` imports.
 */
object LoopWaveModulator {

    data class WavePresetDefinition(
        val id: String,
        val title: String,
        val subtitle: String,
        val loop: Loop,
    )

    fun wavePresets(): List<WavePresetDefinition> = listOf(
        WavePresetDefinition(
            id = "onda-apre-10-6-3",
            title = "Onda APRE 10 → 6 → 3",
            subtitle = "3 semanas · Hipertrofia (10RM) → Fuerza base (6RM) → Fuerza máx (3RM)",
            loop = Loop(
                id = "wave_apre_10_6_3",
                title = "Onda APRE 10 → 6 → 3",
                type = LoopType.CUSTOM,
                repeatEveryXLoops = 1,
                mode = LoopMode.PROGRESSIVE_WAVE,
                modulationScope = LoopModulationScope.APRE_EXERCISES,
                waveSteps = listOf(
                    LoopWaveStep(weekNumber = 1, label = "Semana 1 · APRE 10", apreVariant = LoopApreVariant.APRE_10),
                    LoopWaveStep(weekNumber = 2, label = "Semana 2 · APRE 6", apreVariant = LoopApreVariant.APRE_6),
                    LoopWaveStep(weekNumber = 3, label = "Semana 3 · APRE 3", apreVariant = LoopApreVariant.APRE_3),
                ),
            ),
        ),
        WavePresetDefinition(
            id = "progresion-rir-3-0",
            title = "Progresión RIR 3 → 0",
            subtitle = "4 semanas · Acercamiento progresivo al fallo técnico",
            loop = Loop(
                id = "wave_rir_3_0",
                title = "Progresión RIR 3 → 0",
                type = LoopType.CUSTOM,
                repeatEveryXLoops = 1,
                mode = LoopMode.PROGRESSIVE_WAVE,
                modulationScope = LoopModulationScope.ALL_EXERCISES,
                waveSteps = listOf(
                    LoopWaveStep(weekNumber = 1, label = "Semana 1 · RIR 3", targetRirOverride = 3),
                    LoopWaveStep(weekNumber = 2, label = "Semana 2 · RIR 2", targetRirOverride = 2),
                    LoopWaveStep(weekNumber = 3, label = "Semana 3 · RIR 1", targetRirOverride = 1),
                    LoopWaveStep(weekNumber = 4, label = "Semana 4 · RIR 0", targetRirOverride = 0),
                ),
            ),
        ),
        WavePresetDefinition(
            id = "rampa-volumen-descarga",
            title = "Rampa de volumen + descarga",
            subtitle = "4 semanas · +0, +1, +2 series y descarga final",
            loop = Loop(
                id = "wave_volume_ramp",
                title = "Rampa de volumen + descarga",
                type = LoopType.CUSTOM,
                repeatEveryXLoops = 1,
                mode = LoopMode.PROGRESSIVE_WAVE,
                modulationScope = LoopModulationScope.ALL_EXERCISES,
                waveSteps = listOf(
                    LoopWaveStep(weekNumber = 1, label = "Semana 1 · Base", setsDelta = 0),
                    LoopWaveStep(weekNumber = 2, label = "Semana 2 · +1 serie", setsDelta = 1),
                    LoopWaveStep(weekNumber = 3, label = "Semana 3 · +2 series", setsDelta = 2),
                    LoopWaveStep(weekNumber = 4, label = "Semana 4 · Descarga", isDeload = true, loadPercentDelta = -15.0),
                ),
            ),
        ),
        WavePresetDefinition(
            id = "ondulacion-reps-12-6",
            title = "Ondulación de reps 12 → 6",
            subtitle = "3 semanas · 10–12 → 8–10 → 6–8 reps",
            loop = Loop(
                id = "wave_reps_12_6",
                title = "Ondulación de reps 12 → 6",
                type = LoopType.CUSTOM,
                repeatEveryXLoops = 1,
                mode = LoopMode.PROGRESSIVE_WAVE,
                modulationScope = LoopModulationScope.MAIN_COMPOUNDS,
                waveSteps = listOf(
                    LoopWaveStep(weekNumber = 1, label = "Semana 1 · 10–12 reps", repRangeOverride = 10 to 12),
                    LoopWaveStep(weekNumber = 2, label = "Semana 2 · 8–10 reps", repRangeOverride = 8 to 10),
                    LoopWaveStep(weekNumber = 3, label = "Semana 3 · 6–8 reps", repRangeOverride = 6 to 8),
                ),
            ),
        ),
    )

    fun stepForCycle(loop: Loop, cycleNumber: Int): LoopWaveStep? {
        if (loop.mode != LoopMode.PROGRESSIVE_WAVE || loop.waveSteps.isEmpty()) return null
        val idx = (cycleNumber - 1).coerceAtLeast(0).mod(loop.waveSteps.size)
        return loop.waveSteps[idx]
    }

    fun applyActiveWavesToWeek(
        program: Program,
        week: ProgramWeek,
        cycleNumber: Int,
        workoutLogs: List<WorkoutLog> = emptyList(),
    ): ProgramWeek {
        val cancelled = program.loopState?.cancelled?.toSet().orEmpty()
        val waves = program.loops.filter { it.mode == LoopMode.PROGRESSIVE_WAVE && it.id !in cancelled && it.waveSteps.isNotEmpty() }
        if (waves.isEmpty()) return week
        var current = week
        for (wave in waves) {
            current = applyWaveToWeek(current, wave, cycleNumber, workoutLogs)
        }
        val activeLabel = activeWaveLabel(program, cycleNumber)
        return if (activeLabel != null && current.description.isNullOrBlank()) {
            current.copy(description = activeLabel)
        } else {
            current
        }
    }

    fun applyActiveWavesToSession(
        program: Program,
        session: Session,
        cycleNumber: Int,
        workoutLogs: List<WorkoutLog> = emptyList(),
    ): Session {
        val cancelled = program.loopState?.cancelled?.toSet().orEmpty()
        val waves = program.loops.filter { it.mode == LoopMode.PROGRESSIVE_WAVE && it.id !in cancelled && it.waveSteps.isNotEmpty() }
        if (waves.isEmpty()) return session
        var current = session
        for (wave in waves) {
            val step = stepForCycle(wave, cycleNumber) ?: continue
            current = applyWaveToSession(current, wave, step, workoutLogs)
        }
        return current
    }

    fun apreExercisesInProgram(program: Program): List<Exercise> =
        program.macrocycles
            .asSequence()
            .flatMap { it.blocks.asSequence() }
            .flatMap { it.mesocycles.asSequence() }
            .flatMap { it.weeks.asSequence() }
            .filterNot { it.isLoopWeek }
            .flatMap { it.sessions.asSequence() }
            .flatMap { session ->
                (session.allExercises() + session.sessionB?.allExercises().orEmpty()).asSequence()
            }
            .filter { exercise -> resolveLiveApreBlock(exercise) != null }
            .toList()

    fun activeWaveLabel(program: Program, cycleNumber: Int): String? {
        val cancelled = program.loopState?.cancelled?.toSet().orEmpty()
        val hasActiveApre = apreExercisesInProgram(program).isNotEmpty()
        return program.loops
            .filter { loop ->
                loop.mode == LoopMode.PROGRESSIVE_WAVE &&
                    loop.id !in cancelled &&
                    loop.waveSteps.isNotEmpty() &&
                    (loop.modulationScope != LoopModulationScope.APRE_EXERCISES || hasActiveApre)
            }
            .mapNotNull { loop ->
                val step = stepForCycle(loop, cycleNumber) ?: return@mapNotNull null
                if (!hasActiveApre && step.apreVariant != null && step.targetRirOverride == null && step.targetRpeOverride == null &&
                    step.setsDelta == null && step.repRangeOverride == null && !step.isDeload
                ) {
                    null
                } else {
                    step.label.takeIf(String::isNotBlank)
                }
            }
            .joinToString(" · ")
            .takeIf { it.isNotBlank() }
    }

    fun applyWaveToWeek(
        week: ProgramWeek,
        loop: Loop,
        cycleNumber: Int,
        workoutLogs: List<WorkoutLog> = emptyList(),
    ): ProgramWeek {
        val step = stepForCycle(loop, cycleNumber) ?: return week
        val modulatedSessions = week.sessions.map { session ->
            applyWaveToSession(session, loop, step, workoutLogs)
        }
        return week.copy(sessions = modulatedSessions)
    }

    fun applyWaveToSession(
        session: Session,
        loop: Loop,
        step: LoopWaveStep,
        workoutLogs: List<WorkoutLog> = emptyList(),
    ): Session = session.copy(
        exercises = session.exercises.map { ex -> applyWaveToExercise(ex, loop, step, workoutLogs) },
        parts = session.parts.map { part ->
            part.copy(exercises = part.exercises.map { ex -> applyWaveToExercise(ex, loop, step, workoutLogs) })
        },
        sessionB = session.sessionB?.let { variantB -> applyWaveToSession(variantB, loop, step, workoutLogs) },
    )

    fun applyWaveToExercise(
        exercise: Exercise,
        loop: Loop,
        step: LoopWaveStep,
        workoutLogs: List<WorkoutLog> = emptyList(),
    ): Exercise {
        if (!matchesScope(exercise, loop)) return exercise

        // 1. APRE variant modulation (10 -> 6 -> 3)
        if (step.apreVariant != null && exercise.apreBlock != null) {
            val targetVariant = when (step.apreVariant) {
                LoopApreVariant.APRE_10 -> ApreVariant.APRE_10
                LoopApreVariant.APRE_6 -> ApreVariant.APRE_6
                LoopApreVariant.APRE_3 -> ApreVariant.APRE_3
            }
            val oneRmKg = resolveOneRmForWave(exercise, workoutLogs)
            val apreLoadFactor = when {
                step.loadPercentDelta != null -> 1.0 + step.loadPercentDelta / 100.0
                step.isDeload -> 0.85
                else -> 1.0
            }
            val effectiveOneRmKg = oneRmKg?.let { it * apreLoadFactor }
            val generated = if (effectiveOneRmKg != null && effectiveOneRmKg > 0.0) {
                Apre.block(effectiveOneRmKg, targetVariant)
            } else {
                emptyList()
            }
            val existingSets = exercise.sets
            val nextSets = if (generated.size == 4 && existingSets.size >= 4) {
                existingSets.mapIndexed { idx, set ->
                    if (idx < 4) {
                        val g = generated[idx]
                        set.copy(
                            weight = g.loadKg,
                            targetReps = g.targetReps,
                            amrapMinimumReps = g.minimumReps,
                            purpose = g.purpose,
                            targetRIR = step.targetRirOverride ?: set.targetRIR,
                            targetRPE = step.targetRpeOverride ?: set.targetRPE,
                        )
                    } else {
                        set
                    }
                }
            } else {
                existingSets.mapIndexed { idx, set ->
                    val scaledWeight = if (apreLoadFactor != 1.0 && set.weight != null && set.weight > 0.0) {
                        LoadSuggestionEngine.roundLoad(set.weight * apreLoadFactor, Apre.DEFAULT_INCREMENT_KG)
                    } else {
                        set.weight
                    }
                    when (idx) {
                        0 -> set.copy(weight = scaledWeight, targetReps = targetVariant.set1Reps)
                        1 -> set.copy(weight = scaledWeight, targetReps = targetVariant.set2Reps)
                        2, 3 -> set.copy(
                            weight = scaledWeight,
                            targetReps = null,
                            amrapMinimumReps = targetVariant.xrmReps,
                            purpose = SetPurpose.AMRAP,
                            targetRIR = step.targetRirOverride ?: set.targetRIR,
                            targetRPE = step.targetRpeOverride ?: set.targetRPE,
                        )
                        else -> set
                    }
                }
            }
            val nextRest = step.restDeltaSec?.let { delta ->
                exercise.restTime?.let { (it + delta).coerceAtLeast(15) }
            } ?: exercise.restTime
            return exercise.copy(
                apreBlock = ApreBlockReference(
                    xrmReps = targetVariant.xrmReps,
                    setIds = nextSets.take(4).map { it.id },
                ),
                sets = nextSets,
                restTime = nextRest,
            )
        }

        // 2. Deload / general set modulation
        var workingSets = exercise.sets
        if (step.isDeload) {
            val targetCount = (workingSets.size * 0.6).roundToInt().coerceAtLeast(2).coerceAtMost(workingSets.size)
            workingSets = workingSets.take(targetCount)
        } else if (step.setsDelta != null && step.setsDelta != 0 && workingSets.isNotEmpty()) {
            workingSets = if (step.setsDelta > 0) {
                val template = workingSets.last()
                workingSets + List(step.setsDelta.coerceAtMost(6)) { i ->
                    template.copy(id = "${template.id}_w_${step.weekNumber}_$i")
                }
            } else {
                val targetCount = (workingSets.size + step.setsDelta).coerceAtLeast(1)
                workingSets.take(targetCount)
            }
        }

        val loadFactor = if (step.loadPercentDelta != null) {
            1.0 + step.loadPercentDelta / 100.0
        } else if (step.isDeload) {
            0.85
        } else {
            1.0
        }

        val nextSets = workingSets.map { set ->
            val nextWeight = if (loadFactor != 1.0 && set.weight != null && set.weight > 0.0) {
                LoadSuggestionEngine.roundLoad(set.weight * loadFactor, Apre.DEFAULT_INCREMENT_KG)
            } else {
                set.weight
            }
            val nextRange = step.repRangeOverride?.let { (lo, hi) -> RepRange(lo, hi) } ?: set.targetRepsRange
            val nextReps = when {
                step.repRangeOverride != null -> null
                step.repsDelta != null && set.targetReps != null -> (set.targetReps + step.repsDelta).coerceAtLeast(1)
                else -> set.targetReps
            }
            set.copy(
                weight = nextWeight,
                targetReps = nextReps,
                targetRepsRange = nextRange,
                targetRIR = step.targetRirOverride ?: set.targetRIR,
                targetRPE = step.targetRpeOverride ?: set.targetRPE,
            )
        }

        val nextRest = step.restDeltaSec?.let { delta ->
            exercise.restTime?.let { (it + delta).coerceAtLeast(15) }
        } ?: exercise.restTime

        return exercise.copy(
            sets = nextSets,
            restTime = nextRest,
        )
    }

    private fun matchesScope(exercise: Exercise, loop: Loop): Boolean = when (loop.modulationScope) {
        LoopModulationScope.APRE_EXERCISES -> {
            val isLiveApre = resolveLiveApreBlock(exercise) != null
            if (!isLiveApre) {
                false
            } else if (loop.targetExerciseIds.isEmpty()) {
                true
            } else {
                val canonical = exercise.exerciseDbId ?: exercise.id
                exercise.id in loop.targetExerciseIds || canonical in loop.targetExerciseIds
            }
        }
        LoopModulationScope.MAIN_COMPOUNDS -> resolveLiveApreBlock(exercise) != null || exercise.sets.any { it.isTopSet } ||
            exercise.reference1RM != null || exercise.prFor1RM != null
        LoopModulationScope.ALL_EXERCISES -> true
        LoopModulationScope.SELECTED_EXERCISES -> {
            val canonical = exercise.exerciseDbId ?: exercise.id
            exercise.id in loop.targetExerciseIds || canonical in loop.targetExerciseIds
        }
    }

    private fun resolveOneRmForWave(exercise: Exercise, workoutLogs: List<WorkoutLog>): Double? {
        val latestXrm = workoutLogs
            .sortedByDescending { it.date }
            .asSequence()
            .flatMap { it.completedExercises.asSequence() }
            .filter {
                it.exerciseId == exercise.id ||
                    (!exercise.exerciseDbId.isNullOrBlank() && it.exerciseDbId == exercise.exerciseDbId) ||
                    (exercise.name.isNotBlank() && it.exerciseName == exercise.name)
            }
            .flatMap { it.sets.asSequence() }
            .mapNotNull { it.apreXrm }
            .firstOrNull { (it.reference.capturedLoadKg ?: 0.0) > 0.0 }
        val capturedKg = latestXrm?.reference?.capturedLoadKg
        val xrmReps = latestXrm?.reference?.let { it.capturedTargetReps ?: it.repMin ?: it.repMax }
        if (capturedKg != null && xrmReps != null) {
            val prevVariant = ApreVariant.entries.firstOrNull { it.xrmReps == xrmReps }
            if (prevVariant != null && prevVariant.oneRmFraction > 0.0) {
                return capturedKg / prevVariant.oneRmFraction
            }
        }
        return com.example.kpkn.domain.calculations.resolveReferenceCapacity(exercise)?.takeIf { it.isFinite() && it > 0.0 }
            ?: exercise.referenceTrainingMax?.takeIf { it.isFinite() && it > 0.0 }?.let { it / 0.90 }
    }
}
