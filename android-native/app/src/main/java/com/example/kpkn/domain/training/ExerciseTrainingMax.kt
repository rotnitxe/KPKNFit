package com.example.kpkn.domain.training

import com.example.kpkn.data.models.*
import com.example.kpkn.data.protocols.*
import com.example.kpkn.domain.calculations.percentLoadKg

/** Per-exercise capacities backed by the existing JSON pool. No TM is inferred at an editing boundary. */
object ExerciseTrainingMax {
    fun exercises(program: Program): List<Exercise> = program.macrocycles.flatMap { it.blocks }
        .flatMap { it.mesocycles }.flatMap { it.weeks }.flatMap { it.sessions }.flatMap { it.allExercises() }

    fun request(exercise: Exercise, kind: PlanLoadReferenceKind): PlanLoadReference? {
        val config = exercise.catalogConfigurationId ?: exercise.exerciseDbId ?: return null
        if (config.isBlank()) return null
        return PlanLoadReference(kind, config, exercise.loadQuantityConvention)
    }

    fun resolved(program: Program, exercise: Exercise, kind: PlanLoadReferenceKind): PlanLoadReference? =
        request(exercise, kind)?.let { ExerciseLoadReferenceStore.resolve(program, exercise.id, it) }

    fun capturedKg(program: Program, exercise: Exercise, kind: PlanLoadReferenceKind): Double? =
        resolved(program, exercise, kind)?.takeIf { it.state == PlanLoadReferenceState.CAPTURED }?.capturedLoadKg

    /** Only the exact profile variant is a legacy primary; a paused or alternate variant never matches by LiftSlot. */
    fun legacyLift(program: Program, exercise: Exercise): LiftSlot? {
        val profile = program.powerliftingProfile ?: return null
        val config = exercise.catalogConfigurationId ?: exercise.exerciseDbId ?: return null
        val squat = when (profile.squatVariant) {
            SquatVariant.LOW_BAR -> CatalogIds.SQ_LOW
            SquatVariant.HIGH_BAR -> CatalogIds.SQ_HIGH
            SquatVariant.FRONT_SQUAT -> CatalogIds.SQ_FRONT
        }
        val deadlift = when (profile.deadliftVariant) {
            DeadliftVariant.CONVENTIONAL -> CatalogIds.DL
            DeadliftVariant.SUMO -> CatalogIds.DL_SUMO
            DeadliftVariant.HEX_BAR -> null
        }
        return when {
            config == squat -> LiftSlot.SQUAT
            config == CatalogIds.BP && profile.benchVariant == BenchVariant.BARBELL -> LiftSlot.BENCH
            config == deadlift -> LiftSlot.DEADLIFT
            config == CatalogIds.OHP -> LiftSlot.OVERHEAD
            else -> null
        }
    }

    private fun storedTm(profile: PowerliftingProfile?, lift: LiftSlot): Double? = when (lift) {
        LiftSlot.SQUAT -> profile?.squatTM
        LiftSlot.BENCH -> profile?.benchTM
        LiftSlot.DEADLIFT -> profile?.deadliftTM
        LiftSlot.OVERHEAD -> profile?.overheadTM
    }?.takeIf { it.isFinite() && it > 0.0 }

    /** Idempotent read migration. Unknown historical dates stay null. Already materialized snapshots retain their provenance. */
    fun migrate(program: Program): Program {
        var result = program
        exercises(program).forEach { exercise ->
            val lift = legacyLift(program, exercise)
            val profileTm = lift?.let { storedTm(program.powerliftingProfile, it) }
            val profileOneRm = lift?.let { TrainingMaxResolver.oneRm(program.powerliftingProfile, it) }
            listOf(PlanLoadReferenceKind.EXERCISE_1RM to (exercise.reference1RM ?: profileOneRm),
                PlanLoadReferenceKind.EXERCISE_TM to (exercise.referenceTrainingMax ?: profileTm)).forEach { (kind, kg) ->
                val query = request(exercise, kind) ?: return@forEach
                if (kg == null || !kg.isFinite() || kg <= 0.0 || capturedKg(result, exercise, kind) != null) return@forEach
                val fromProfile = if (kind == PlanLoadReferenceKind.EXERCISE_TM) profileTm == kg else profileOneRm == kg
                val reference = query.copy(state = PlanLoadReferenceState.CAPTURED, capturedLoadKg = kg,
                    metadata = PlanLoadReferenceMetadata(
                        origin = if (fromProfile) ExerciseLoadReferenceStore.LEGACY_PROFILE else ExerciseLoadReferenceStore.MATERIALIZED_RECIPE,
                        reason = if (fromProfile) "Referencia conservada del perfil anterior" else "Referencia ya presente en la prescripción",
                        sourceId = "migration:${program.id}:${exercise.id}:$kind",
                        tmFraction = program.sourceRecipe?.trainingMaxPercent?.takeIf { it.isFinite() && it > 0.0 },
                    ))
                result = ExerciseLoadReferenceStore.appendCapture(result, setOf(exercise.id), reference)
            }
        }
        return result
    }

    /** A confirmed edit updates the exact configuration across its occurrences, preserving independently captured variants. */
    fun capture(program: Program, exercise: Exercise, kind: PlanLoadReferenceKind, kg: Double,
        nowMs: Long, sourceId: String, reason: String, origin: String = ExerciseLoadReferenceStore.MANUAL): Program {
        require(kind == PlanLoadReferenceKind.EXERCISE_TM || kind == PlanLoadReferenceKind.EXERCISE_1RM)
        require(kg.isFinite() && kg > 0.0)
        val query = request(exercise, kind) ?: return program
        val current = migrate(program)
        val targetIds = exercises(current).filter { candidate -> request(candidate, kind)?.let {
            ExerciseLoadReferenceStore.sameIdentity(it, query) } == true }.mapTo(mutableSetOf()) { it.id } + exercise.id
        val fraction = resolved(current, exercise, PlanLoadReferenceKind.EXERCISE_TM)?.metadata?.tmFraction
            ?: current.sourceRecipe?.trainingMaxPercent?.takeIf { it.isFinite() && it > 0.0 }
        val reference = query.copy(state = PlanLoadReferenceState.CAPTURED, capturedLoadKg = kg, capturedAtMs = nowMs,
            sourceProgramId = program.id, sourceRunId = program.runState?.runId,
            metadata = PlanLoadReferenceMetadata(origin = origin, reason = reason, sourceId = sourceId, tmFraction = fraction))
        val captured = ExerciseLoadReferenceStore.appendCapture(current, targetIds, reference)
        val lift = legacyLift(current, exercise) ?: return captured
        val profile = current.powerliftingProfile ?: return captured
        val updated = if (kind == PlanLoadReferenceKind.EXERCISE_TM) when (lift) {
            LiftSlot.SQUAT -> profile.copy(squatTM = kg)
            LiftSlot.BENCH -> profile.copy(benchTM = kg)
            LiftSlot.DEADLIFT -> profile.copy(deadliftTM = kg)
            LiftSlot.OVERHEAD -> profile.copy(overheadTM = kg)
        } else when (lift) {
            LiftSlot.SQUAT -> profile.copy(squat1RM = kg)
            LiftSlot.BENCH -> profile.copy(bench1RM = kg)
            LiftSlot.DEADLIFT -> profile.copy(deadlift1RM = kg)
            LiftSlot.OVERHEAD -> profile.copy(overhead1RM = kg)
        }
        return captured.copy(powerliftingProfile = updated)
    }

    /** Keeps exact canonical pool snapshots in step with an authored cycle or an accepted legacy proposal. */
    fun syncProfile(before: Program, after: Program, nowMs: Long, sourceId: String,
        reason: String, origin: String = ExerciseLoadReferenceStore.AUTHOR_PROGRESSION): Program {
        var result = migrate(after)
        exercises(after).forEach { exercise ->
            val lift = legacyLift(after, exercise) ?: return@forEach
            val oldTm = storedTm(before.powerliftingProfile, lift)
            val nextTm = storedTm(after.powerliftingProfile, lift)
            if (nextTm != null && nextTm != oldTm) result = capture(result, exercise,
                PlanLoadReferenceKind.EXERCISE_TM, nextTm, nowMs, "$sourceId:${lift.name}", reason, origin)
            val oldOneRm = TrainingMaxResolver.oneRm(before.powerliftingProfile, lift)
            val nextOneRm = TrainingMaxResolver.oneRm(after.powerliftingProfile, lift)
            if (nextOneRm != null && nextOneRm != oldOneRm) result = capture(result, exercise,
                PlanLoadReferenceKind.EXERCISE_1RM, nextOneRm, nowMs, "$sourceId:${lift.name}:1rm", reason, origin)
        }
        return result
    }

    data class Missing(val exercise: Exercise, val suggestedKg: Double?, val fraction: Double?)

    fun missing(program: Program?, session: Session): List<Missing> = session.allExercises().filter { exercise ->
        exercise.sets.any { it.loadBasis == LoadBasis.PERCENT_TM && it.targetPercentageRM != null &&
            it.completedReps == null && !it.isEmptySlot } &&
            exercise.referenceTrainingMax?.let { it.isFinite() && it > 0.0 } != true &&
            (program == null || capturedKg(program, exercise, PlanLoadReferenceKind.EXERCISE_TM) == null)
    }.map { exercise ->
        val fraction = program?.let { resolved(it, exercise, PlanLoadReferenceKind.EXERCISE_TM)?.metadata?.tmFraction }
            ?: program?.sourceRecipe?.trainingMaxPercent?.takeIf { it.isFinite() && it > 0.0 }
        val oneRm = exercise.reference1RM?.takeIf { it.isFinite() && it > 0.0 }
            ?: program?.let { capturedKg(it, exercise, PlanLoadReferenceKind.EXERCISE_1RM) }
        Missing(exercise, if (fraction != null) oneRm?.times(fraction) else null, fraction)
    }

    fun hydrate(program: Program, exercise: Exercise): Exercise = exercise.copy(
        reference1RM = capturedKg(program, exercise, PlanLoadReferenceKind.EXERCISE_1RM) ?: exercise.reference1RM,
        referenceTrainingMax = capturedKg(program, exercise, PlanLoadReferenceKind.EXERCISE_TM) ?: exercise.referenceTrainingMax,
    )

    /** Pending, unprotected prescriptions only. Existing manual-session and executed evidence are authoritative. */
    fun refreshPending(program: Program, executedWeekIds: Set<String>, executedSessionIds: Set<String>): Program {
        val frozen = program.manualSessionOverrides.mapTo(mutableSetOf()) { it.sessionId } + executedSessionIds
        fun refresh(exercise: Exercise): Exercise {
            val oneRm = capturedKg(program, exercise, PlanLoadReferenceKind.EXERCISE_1RM) ?: exercise.reference1RM
            val tm = capturedKg(program, exercise, PlanLoadReferenceKind.EXERCISE_TM) ?: exercise.referenceTrainingMax
            val referenced = exercise.copy(reference1RM = oneRm, referenceTrainingMax = tm)
            return referenced.copy(sets = exercise.sets.map { set ->
                if (set.completedReps != null || set.completedDuration != null || set.isEmptySlot ||
                    set.targetPercentageRM == null || set.loadBasis !in setOf(LoadBasis.PERCENT_TM, LoadBasis.PERCENT_1RM, LoadBasis.PERCENT_DESIRED_MAX)) set
                else percentLoadKg(referenced, set)?.let { kg -> set.copy(weight = kg) } ?: set
            })
        }
        return program.copy(macrocycles = program.macrocycles.map { macro -> macro.copy(blocks = macro.blocks.map { block ->
            block.copy(mesocycles = block.mesocycles.map { meso -> meso.copy(weeks = meso.weeks.map { week ->
                if (week.id in executedWeekIds) week else week.copy(sessions = week.sessions.map { session ->
                    if (session.id in frozen) session else session.copy(exercises = session.exercises.map(::refresh),
                        parts = session.parts.map { it.copy(exercises = it.exercises.map(::refresh)) })
                })
            }) })
        }) })
    }
}
