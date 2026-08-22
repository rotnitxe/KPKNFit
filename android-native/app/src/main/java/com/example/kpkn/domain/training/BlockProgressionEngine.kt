package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.BlockProgressionScheme
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.MesocycleGoal
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.plannedRepAnchor
import com.example.kpkn.data.protocols.ProtocolBlock
import com.example.kpkn.domain.templates.SessionClonePurpose
import com.example.kpkn.domain.templates.SessionTemplateEngine
import kotlin.math.roundToInt

/**
 * Genera / actualiza la prescripción semanal de un [Block] según objetivo + esquema,
 * reutilizando [PeriodizationEngine] y clonando sesiones con ids frescos.
 */
object BlockProgressionEngine {

    data class PrescriptionDiff(
        val weekFromIndex: Int,
        val weekToIndex: Int,
        val percentageDelta: Double,
        val setsDelta: Int,
        val rpeDelta: Double,
        val summary: String,
    )

    data class ApplyResult(
        val block: Block,
        val diffs: List<PrescriptionDiff>,
    )

    fun intensityRangeFor(goal: BlockGoal?, protocolBlock: ProtocolBlock?): Pair<Int, Int> {
        if (protocolBlock != null) return protocolBlock.intensityMin to protocolBlock.intensityMax
        return when (goal) {
            BlockGoal.ACCUMULATION, BlockGoal.DENSITY -> 65 to 75
            BlockGoal.INTENSIFICATION -> 72 to 85
            BlockGoal.SPECIFICITY -> 80 to 90
            BlockGoal.REALIZATION, BlockGoal.PEAK -> 85 to 95
            BlockGoal.DELOAD -> 55 to 70
            // Taper: exposición final controlada 70-90% con descarga progresiva
            // (-50/-70% volumen) distinta del deload profundo 55-70%.
            BlockGoal.TAPER -> 70 to 90
            BlockGoal.CUSTOM, null -> 65 to 85
        }
    }

    fun mesocycleGoalFrom(blockGoal: BlockGoal?): MesocycleGoal = when (blockGoal) {
        BlockGoal.ACCUMULATION, BlockGoal.DENSITY -> MesocycleGoal.ACCUMULATION
        BlockGoal.INTENSIFICATION -> MesocycleGoal.INTENSIFICATION
        BlockGoal.SPECIFICITY -> MesocycleGoal.REALIZATION
        BlockGoal.REALIZATION, BlockGoal.PEAK -> MesocycleGoal.REALIZATION
        BlockGoal.DELOAD, BlockGoal.TAPER -> MesocycleGoal.DELOAD
        BlockGoal.CUSTOM, null -> MesocycleGoal.CUSTOM
    }

    /**
     * Materializa todas las semanas del bloque.  A template source is copied with
     * fresh ids for every week, while cardio/mobility payloads are left intact and
     * strength prescriptions receive the selected progression.
     */
    fun applyProgression(
        block: Block,
        scheme: BlockProgressionScheme = block.progressionScheme ?: BlockProgressionScheme.PERCENT_RM,
        protocolBlock: ProtocolBlock? = null,
        seedSessions: List<Session>? = null,
    ): ApplyResult {
        if (scheme == BlockProgressionScheme.NONE) {
            return ApplyResult(block = block, diffs = emptyList())
        }
        val totalWeeks = block.mesocycles.sumOf { it.weeks.size }
        if (totalWeeks == 0) return ApplyResult(block, emptyList())

        val (intensityMin, intensityMax) = intensityRangeFor(block.goal, protocolBlock)
        val volumeModifier = protocolBlock?.volumeModifier
        val fallbackSeed = seedSessions?.takeIf { it.isNotEmpty() }
            ?: block.mesocycles.asSequence()
                .flatMap { it.weeks.asSequence() }
                .map { it.sessions }
                .firstOrNull { it.isNotEmpty() }

        var globalIndex = 0
        val updatedMesocycles = block.mesocycles.map { mesocycle ->
            val mesoGoal = block.goal?.let(::mesocycleGoalFrom) ?: mesocycle.goal
            mesocycle.copy(
                goal = mesoGoal,
                weeks = mesocycle.weeks.map { week ->
                    globalIndex += 1
                    // progressionIndex is local to a mesocycle in legacy JSON;
                    // using it here would reset the load wave at every meso.
                    val weekNumber = globalIndex
                    val baseSessions = when {
                        !seedSessions.isNullOrEmpty() -> seedSessions
                        week.sessions.isNotEmpty() -> week.sessions
                        !fallbackSeed.isNullOrEmpty() -> fallbackSeed
                        else -> emptyList()
                    }
                    week.copy(
                        sessions = baseSessions.map { session ->
                            prescribeSession(
                                source = session,
                                goal = mesoGoal,
                                scheme = scheme,
                                volumeModifier = volumeModifier,
                                intensityMin = intensityMin,
                                intensityMax = intensityMax,
                                weekNumber = weekNumber,
                                totalWeeks = totalWeeks,
                            )
                        },
                        progressionIndex = weekNumber,
                    )
                },
            )
        }

        val diffGoal = block.goal?.let(::mesocycleGoalFrom)
            ?: block.mesocycles.firstOrNull()?.goal
            ?: MesocycleGoal.CUSTOM

        val cleanDiffs = (1 until totalWeeks).map { i ->
            val fromRx = PeriodizationEngine.prescriptionFor(
                goal = diffGoal,
                baseSets = 3,
                baseReps = 8,
                volumeModifier = volumeModifier,
                intensityMin = intensityMin,
                intensityMax = intensityMax,
                weekNumber = i,
                totalWeeksInBlock = totalWeeks,
            )
            val toRx = PeriodizationEngine.prescriptionFor(
                goal = diffGoal,
                baseSets = 3,
                baseReps = 8,
                volumeModifier = volumeModifier,
                intensityMin = intensityMin,
                intensityMax = intensityMax,
                weekNumber = i + 1,
                totalWeeksInBlock = totalWeeks,
            )
            val schemeNote = when (scheme) {
                BlockProgressionScheme.LINEAR_LOAD -> "carga lineal"
                BlockProgressionScheme.UNDULATING -> "ondulación"
                BlockProgressionScheme.PERCENT_RM -> "%RM"
                BlockProgressionScheme.RPE_CAP -> "tope RPE"
                BlockProgressionScheme.NONE -> "sin progresión"
            }
            PrescriptionDiff(
                weekFromIndex = i,
                weekToIndex = i + 1,
                percentageDelta = toRx.percentageRM - fromRx.percentageRM,
                setsDelta = toRx.sets - fromRx.sets,
                rpeDelta = toRx.rpe - fromRx.rpe,
                summary = "Semana $i→${i + 1} ($schemeNote): %RM ${"%.1f".format(fromRx.percentageRM)}→${"%.1f".format(toRx.percentageRM)}, " +
                    "series ${fromRx.sets}→${toRx.sets}, RPE ${fromRx.rpe}→${toRx.rpe}",
            )
        }

        val updatedBlock = block.copy(
            mesocycles = updatedMesocycles,
            progressionScheme = scheme,
            materializationPending = false,
        )
        return ApplyResult(block = updatedBlock, diffs = cleanDiffs)
    }

    fun previewDiff(
        block: Block,
        weekFrom: Int,
        weekTo: Int,
        protocolBlock: ProtocolBlock? = null,
    ): PrescriptionDiff? {
        val total = block.mesocycles.sumOf { it.weeks.size }
        if (total == 0) return null
        if (weekFrom < 1 || weekTo < 1 || weekFrom > total || weekTo > total) return null
        val scheme = block.progressionScheme ?: BlockProgressionScheme.PERCENT_RM
        val (intensityMin, intensityMax) = intensityRangeFor(block.goal, protocolBlock)
        val mesoGoal = block.goal?.let(::mesocycleGoalFrom)
            ?: block.mesocycles.firstOrNull()?.goal
            ?: MesocycleGoal.CUSTOM
        val volumeModifier = protocolBlock?.volumeModifier
        val fromRx = PeriodizationEngine.prescriptionFor(
            mesoGoal, 3, 8, volumeModifier, intensityMin, intensityMax, weekFrom, total,
        )
        val toRx = PeriodizationEngine.prescriptionFor(
            mesoGoal, 3, 8, volumeModifier, intensityMin, intensityMax, weekTo, total,
        )
        return PrescriptionDiff(
            weekFromIndex = weekFrom,
            weekToIndex = weekTo,
            percentageDelta = toRx.percentageRM - fromRx.percentageRM,
            setsDelta = toRx.sets - fromRx.sets,
            rpeDelta = toRx.rpe - fromRx.rpe,
            summary = "Semana $weekFrom→$weekTo ($scheme): %RM ${"%.1f".format(fromRx.percentageRM)}→${"%.1f".format(toRx.percentageRM)}",
        )
    }

    private fun prescribeSession(
        source: Session,
        goal: MesocycleGoal,
        scheme: BlockProgressionScheme,
        volumeModifier: Double?,
        intensityMin: Int,
        intensityMax: Int,
        weekNumber: Int,
        totalWeeks: Int,
    ): Session {
        val cloned = SessionTemplateEngine.cloneSessionContent(source, SessionClonePurpose.PROGRESSION_SEED)
        fun mapExercise(exercise: Exercise): Exercise {
            // Cardio and mobility have their own prescribed timers/intervals.  A
            // strength %RM progression must not corrupt those payloads.
            if (exercise.cardioDetails != null || exercise.mobilitySeries.isNotEmpty() || exercise.mobilityConfig != null) {
                return exercise
            }
            if (exercise.sets.isEmpty()) return exercise
            val baseSets = exercise.sets.size.coerceAtLeast(1)
            val baseReps = exercise.sets.mapNotNull { it.plannedRepAnchor() }.average().let { avg ->
                if (avg.isNaN()) 8 else avg.roundToInt().coerceIn(1, 20)
            }
            val rx = PeriodizationEngine.prescriptionFor(
                goal = goal,
                baseSets = baseSets,
                baseReps = baseReps,
                volumeModifier = volumeModifier,
                intensityMin = intensityMin,
                intensityMax = intensityMax,
                weekNumber = weekNumber,
                totalWeeksInBlock = totalWeeks,
            )
            // Names are editorial labels, not loadable identities.  In
            // particular, an OHP or legacy exercise named "squat" may not
            // receive %RM merely because it is first in a session.  Keep RM
            // reserved for an explicit competition marker, or preserve an
            // already-RM exercise only when it has a usable reference.
            val explicitlyAnchoredRm = exercise.trainingMode == com.example.kpkn.data.models.TrainingMode.RM &&
                (exercise.reference1RM ?: 0.0) > 0.0
            // Configuration IDs are not roles. A bench/squat/deadlift in a
            // hypertrophy or powerbuilding template is still an accessory until
            // the recipe explicitly marks it as the competition lift. This
            // prevents a global SBD whitelist from leaking %RM into non-PL tracks.
            val isMainLift = exercise.isCompetitionLift || explicitlyAnchoredRm
            // Deload/taper is an unload contract, not another place where a
            // scheme may add a wave or linearly increase the load.  Always use
            // PeriodizationEngine's descending percentage/RPE for this goal;
            // accessories still stay in REPS+RPE below because pct is gated by
            // the main-lift marker.
            val pct = if (goal == MesocycleGoal.DELOAD) {
                rx.percentageRM.takeIf { isMainLift }
            } else {
                when (scheme) {
                    BlockProgressionScheme.UNDULATING -> {
                        val wave = if (weekNumber % 2 == 0) -2.5 else 2.5
                        (rx.percentageRM + wave).coerceIn(intensityMin.toDouble(), intensityMax.toDouble())
                    }
                    BlockProgressionScheme.LINEAR_LOAD -> exercise.sets.firstOrNull()?.targetPercentageRM
                        ?.plus((weekNumber - 1) * 1.25)
                        ?.coerceIn(intensityMin.toDouble(), intensityMax.toDouble())
                        ?: rx.percentageRM
                    BlockProgressionScheme.RPE_CAP, BlockProgressionScheme.NONE -> null
                    BlockProgressionScheme.PERCENT_RM -> rx.percentageRM
                }?.takeIf { isMainLift }
            }
            val rpe = if (goal == MesocycleGoal.DELOAD) {
                rx.rpe
            } else {
                when (scheme) {
                    BlockProgressionScheme.RPE_CAP -> rx.rpe.coerceAtMost(8.5)
                    BlockProgressionScheme.LINEAR_LOAD -> (exercise.sets.firstOrNull()?.targetRPE ?: rx.rpe)
                        .plus((weekNumber - 1) * 0.25)
                        .coerceAtMost(9.0)
                    else -> rx.rpe
                }
            }
            val setCount = if (isMainLift) rx.sets.coerceAtLeast(1) else {
                // Accessories retain their role: their volume can scale, but they
                // do not turn into a low-rep competition lift during a peak.
                PeriodizationEngine.scaleSets(baseSets, goal, volumeModifier).coerceAtLeast(1)
            }
            val targetReps = if (isMainLift) rx.reps else baseReps.coerceIn(6, 20)
            val templateSet = exercise.sets.firstOrNull()
            val newSets = (0 until setCount).map { idx ->
                val base = exercise.sets.getOrNull(idx) ?: templateSet ?: ExerciseSet(id = "tmp")
                base.copy(
                    id = java.util.UUID.randomUUID().toString(),
                    targetReps = targetReps,
                    targetRPE = rpe,
                    targetPercentageRM = pct,
                    intensityMode = when (scheme) {
                        BlockProgressionScheme.RPE_CAP -> IntensityMode.RPE
                        BlockProgressionScheme.NONE -> base.intensityMode
                        else -> if (pct != null) IntensityMode.SOLO_RM else IntensityMode.RPE
                    },
                )
            }
            return exercise.copy(
                sets = newSets,
                // Accessories remain reps/RPE work. RM mode is reserved for a
                // real competition/main lift with an explicit percentage.
                trainingMode = if (pct != null) com.example.kpkn.data.models.TrainingMode.RM
                else com.example.kpkn.data.models.TrainingMode.REPS,
            )
        }
        fun mapSession(session: Session): Session {
            return session.copy(
            exercises = session.exercises.map(::mapExercise),
            parts = session.parts.map { part ->
                part.copy(exercises = part.exercises.map(::mapExercise))
            },
            sessionB = session.sessionB?.let(::mapSession),
            sessionC = session.sessionC?.let(::mapSession),
            sessionD = session.sessionD?.let(::mapSession),
            )
        }
        return mapSession(cloned)
    }
}
