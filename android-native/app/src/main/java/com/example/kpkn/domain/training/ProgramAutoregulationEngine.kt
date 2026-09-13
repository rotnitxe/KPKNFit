package com.example.kpkn.domain.training

import com.example.kpkn.data.models.AutoregulationAuditEntry
import com.example.kpkn.data.models.AutoregulationMode
import com.example.kpkn.data.models.AutoregulationProposal
import com.example.kpkn.data.models.AutoregulationProposalKind
import com.example.kpkn.data.models.LoadAdvisoryLevel
import com.example.kpkn.data.models.PendingProgramAction
import com.example.kpkn.data.models.PendingProgramActionType
import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.protocols.LiftSlot
import com.example.kpkn.data.protocols.ProgressionRule
import com.example.kpkn.data.protocols.TechniqueModifier
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.domain.auge.LoadAdvisoryEngine
import com.example.kpkn.domain.calculations.calculateHybrid1RM

data class AmrapHit(
    val liftSlot: LiftSlot?,
    val percent: Double?,
    val prescribedReps: Int,
    val actualReps: Int,
    val meanRpe: Double? = null,
)

data class WeeklyAutoregulationSignals(
    val readinessScore: Int? = null,
    val cumulativeFatigue: Double? = null,
    val stressLevel: Int? = null,
    val loadAdvisoryLevel: LoadAdvisoryLevel = LoadAdvisoryLevel.NONE,
    val overtrainedMuscles: List<String> = emptyList(),
    val amrapHits: List<AmrapHit> = emptyList(),
    val e1rmByLift: Map<LiftSlot, Double> = emptyMap(),
    val consecutiveHighE1rmWeeks: Int = 0,
    val repeatedJointPain: Boolean = false,
    val settings: Settings = Settings(),
)

data class AutoregulationEvaluation(
    val proposals: List<AutoregulationProposal>,
    val program: Program,
    val applied: Boolean = false,
)

object ProgramAutoregulationEngine {

    fun evaluate(
        program: Program,
        completedWeek: ProgramWeek,
        logs: List<WorkoutLog>,
        recipe: TrainingPlanRecipe,
        signals: WeeklyAutoregulationSignals,
    ): List<AutoregulationProposal> {
        if (program.autoregulationMode == AutoregulationMode.OFF) return emptyList()
        val hits = signals.amrapHits.ifEmpty { collectAmrapHits(completedWeek, logs) }
        return buildProposals(program, completedWeek, recipe, signals.copy(amrapHits = hits))
    }

    fun apply(
        program: Program,
        nextWeekId: String?,
        proposals: List<AutoregulationProposal>,
        recipe: TrainingPlanRecipe,
        executedWeekIds: Set<String>,
        metadata: ExerciseCompositionMetadataProvider? = null,
        nowMs: Long = System.currentTimeMillis(),
    ): AutoregulationEvaluation {
        if (proposals.isEmpty() || program.autoregulationMode == AutoregulationMode.OFF) {
            return AutoregulationEvaluation(proposals = emptyList(), program = program)
        }
        val audit = program.runState?.autoregulationAudit.orEmpty() + AutoregulationAuditEntry(
            atMs = nowMs,
            mode = program.autoregulationMode,
            kinds = proposals.map { it.kind },
            weekId = nextWeekId,
        )
        return when (program.autoregulationMode) {
            AutoregulationMode.PROPOSE -> {
                val message = proposals.joinToString(" · ") { it.explanation }.ifBlank { "AUGE propone ajustes para la próxima semana." }
                val pending = PendingProgramAction(
                    type = PendingProgramActionType.CONFIRM_AUTOREGULATION,
                    message = message,
                    proposals = proposals,
                    targetWeekId = nextWeekId,
                )
                val run = (program.runState ?: com.example.kpkn.data.models.ProgramRunState(runId = ProgramProgressEngine.newRunId()))
                    .copy(pendingAction = pending, autoregulationAudit = audit)
                AutoregulationEvaluation(proposals = proposals, program = program.copy(runState = run), applied = false)
            }
            AutoregulationMode.AUTO -> {
                val provider = metadata ?: runCatching { CompositionMetadataHolder.resolve() }.getOrNull()
                    ?: return AutoregulationEvaluation(proposals = proposals, program = program.copy(
                        runState = (program.runState ?: com.example.kpkn.data.models.ProgramRunState(runId = ProgramProgressEngine.newRunId()))
                            .copy(
                                pendingAction = PendingProgramAction(
                                    type = PendingProgramActionType.CONFIRM_AUTOREGULATION,
                                    message = proposals.joinToString(" · ") { it.explanation },
                                    proposals = proposals,
                                    targetWeekId = nextWeekId,
                                ),
                                autoregulationAudit = audit,
                            ),
                    ), applied = false)
                val mutated = applyMutations(
                    program = program,
                    nextWeekId = nextWeekId,
                    proposals = proposals,
                    recipe = recipe,
                    executedWeekIds = executedWeekIds,
                    metadata = provider,
                )
                val run = mutated.runState?.copy(autoregulationAudit = audit, pendingAction = null)
                    ?: mutated.runState
                AutoregulationEvaluation(
                    proposals = proposals,
                    program = mutated.copy(runState = run),
                    applied = true,
                )
            }
            AutoregulationMode.OFF -> AutoregulationEvaluation(emptyList(), program)
        }
    }

    fun resolvePending(
        program: Program,
        accept: Boolean,
        metadata: ExerciseCompositionMetadataProvider? = null,
    ): Program {
        val action = program.runState?.pendingAction ?: return program
        if (action.type != PendingProgramActionType.CONFIRM_AUTOREGULATION) return program
        val cleared = program.copy(runState = program.runState?.copy(pendingAction = null))
        if (!accept) return cleared
        val recipe = program.sourceRecipe ?: return cleared
        val executed = program.runState?.weekId?.let { setOf(it) }.orEmpty()
        val provider = metadata ?: runCatching { CompositionMetadataHolder.resolve() }.getOrNull() ?: return cleared
        return applyMutations(
            program = cleared,
            nextWeekId = action.targetWeekId,
            proposals = action.proposals,
            recipe = recipe,
            executedWeekIds = executed,
            metadata = provider,
        )
    }

    fun collectAmrapHits(week: ProgramWeek, logs: List<WorkoutLog>): List<AmrapHit> {
        val weekLogs = logs.filter { it.weekId == week.id || it.weekInstanceId == week.id }
        if (weekLogs.isEmpty()) return emptyList()
        val out = mutableListOf<AmrapHit>()
        week.sessions.forEach { session ->
            session.allExercises().forEach { exercise ->
                val amrapSets = exercise.sets.filter { it.isAmrap }
                if (amrapSets.isEmpty()) return@forEach
                val completed = weekLogs
                    .flatMap { it.completedExercises }
                    .filter { done ->
                        done.exerciseId == exercise.id ||
                            done.catalogConfigurationId == exercise.catalogConfigurationId ||
                            done.exerciseDbId == exercise.exerciseDbId
                    }
                amrapSets.forEach { prescribed ->
                    val match = completed.flatMap { it.sets }.lastOrNull { set ->
                        set.amrapPerformed || prescribed.isAmrap
                    } ?: completed.flatMap { it.sets }.lastOrNull()
                    if (match != null) {
                        out += AmrapHit(
                            liftSlot = liftSlotFor(exercise.catalogConfigurationId ?: exercise.exerciseDbId),
                            percent = prescribed.targetPercentageRM,
                            prescribedReps = prescribed.targetReps ?: match.amrapMinimumReps ?: 0,
                            actualReps = match.reps,
                            meanRpe = match.rpe ?: prescribed.targetRPE,
                        )
                    }
                }
            }
        }
        return out
    }

    fun collectE1rmByLift(logs: List<WorkoutLog>, week: ProgramWeek): Map<LiftSlot, Double> {
        val weekLogs = logs.filter { it.weekId == week.id || it.weekInstanceId == week.id }
        val acc = mutableMapOf<LiftSlot, Double>()
        weekLogs.flatMap { it.completedExercises }.forEach { exercise ->
            val slot = liftSlotFor(exercise.catalogConfigurationId ?: exercise.exerciseDbId) ?: return@forEach
            exercise.sets.filter { !it.isWarmup && it.weight > 0 && it.reps > 0 }.forEach { set ->
                val e1 = calculateHybrid1RM(set.weight, set.reps, isAmrap = set.amrapPerformed)
                acc[slot] = maxOf(acc[slot] ?: 0.0, e1)
            }
        }
        return acc.filterValues { it > 0.0 }
    }

    internal fun applyMutations(
        program: Program,
        nextWeekId: String?,
        proposals: List<AutoregulationProposal>,
        recipe: TrainingPlanRecipe,
        executedWeekIds: Set<String>,
        metadata: ExerciseCompositionMetadataProvider,
    ): Program {
        var working = program
        var profile = working.powerliftingProfile
        proposals.filter { it.kind == AutoregulationProposalKind.ADJUST_TM || it.kind == AutoregulationProposalKind.PROMOTE_TM }
            .forEach { proposal ->
                val slot = proposal.liftSlot?.let { runCatching { LiftSlot.valueOf(it) }.getOrNull() }
                val delta = proposal.percentDelta ?: 0.0
                if (profile != null && delta != 0.0) {
                    profile = applyTmDelta(profile!!, slot, delta)
                }
            }
        if (profile != null) working = working.copy(powerliftingProfile = profile)

        val intensity = proposals
            .filter { it.kind == AutoregulationProposalKind.SCALE_WEEK_INTENSITY }
            .mapNotNull { it.percentDelta }
            .fold(1.0) { acc, delta -> acc * (1.0 + delta / 100.0) }
        val volume = proposals
            .filter { it.kind == AutoregulationProposalKind.SCALE_WEEK_VOLUME }
            .mapNotNull { it.volumeFactor }
            .fold(1.0) { acc, factor -> acc * factor }

        val swap = proposals.any { it.kind == AutoregulationProposalKind.SWAP_TO_TECHNIQUE_VARIANT }
        val delayPeak = proposals.any { it.kind == AutoregulationProposalKind.DELAY_PEAK }
        var recipeForNext = recipe
        if (swap || delayPeak) {
            recipeForNext = recipe.copy(
                weeks = recipe.weeks.map { week ->
                    week.copy(
                        days = week.days.map { day ->
                            day.copy(
                                slots = day.slots.map { slot ->
                                    val next = if (swap && slot.role.name.startsWith("T1")) {
                                        slot.copy(technique = slot.technique ?: TechniqueModifier.PAUSE_2S)
                                    } else slot
                                    if (delayPeak) {
                                        next.copy(sets = next.sets.map { set -> set.copy(percent = set.percent?.times(0.95)) })
                                    } else next
                                },
                            )
                        },
                    )
                },
            )
        }

        if (nextWeekId != null && (intensity != 1.0 || volume != 1.0 || swap || delayPeak || proposals.any { it.kind == AutoregulationProposalKind.ADJUST_TM || it.kind == AutoregulationProposalKind.PROMOTE_TM })) {
            working = PlanMaterializer.rematerializeWeek(
                program = working,
                weekId = nextWeekId,
                recipe = recipeForNext,
                metadata = metadata,
                intensityScale = intensity,
                volumeFactor = volume,
                executedWeekIds = executedWeekIds,
            )
        }

        if (proposals.any { it.kind == AutoregulationProposalKind.INSERT_DELOAD }) {
            val currentBlockId = working.runState?.blockId
                ?: working.macrocycles.firstOrNull()?.blocks?.firstOrNull()?.id
            if (currentBlockId != null) {
                val withDeload = BlockTransitionEngine.insertDeloadBlockAfter(working, currentBlockId)
                if (withDeload != null) working = withDeload.first
            }
        }
        return working
    }

    private fun buildProposals(
        program: Program,
        week: ProgramWeek,
        recipe: TrainingPlanRecipe,
        signals: WeeklyAutoregulationSignals,
    ): List<AutoregulationProposal> {
        val out = mutableListOf<AutoregulationProposal>()
        val readiness = signals.readinessScore
        val fatigue = signals.cumulativeFatigue
        val unload = LoadAdvisoryEngine.rank(signals.loadAdvisoryLevel) >= LoadAdvisoryEngine.rank(LoadAdvisoryLevel.UNLOAD)
        val autoDeload = readiness != null && fatigue != null && readiness < 40 && fatigue > 75.0

        signals.amrapHits.forEach { hit ->
            val short = hit.actualReps < hit.prescribedReps || (hit.prescribedReps > 0 && hit.actualReps <= 1 && (hit.percent ?: 0.0) >= 90.0)
            if (short) {
                val delta = tmDeltaForAmrap(recipe.progression, hit)
                out += AutoregulationProposal(
                    kind = AutoregulationProposalKind.ADJUST_TM,
                    liftSlot = hit.liftSlot?.name,
                    percentDelta = delta,
                    explanation = "AMRAP ${(hit.percent ?: 0.0).toInt()} %: ${hit.actualReps} reps (objetivo ${hit.prescribedReps})" +
                        (hit.meanRpe?.let { ", RPE medio ${"%.1f".format(it)}" } ?: "") +
                        (readiness?.let { ", readiness $it" } ?: ""),
                )
            } else if (recipe.progression is ProgressionRule.AmrapDrivenTm && hit.actualReps >= 2) {
                val delta = tmDeltaForAmrap(recipe.progression, hit)
                if (delta > 0) {
                    out += AutoregulationProposal(
                        kind = AutoregulationProposalKind.ADJUST_TM,
                        liftSlot = hit.liftSlot?.name,
                        percentDelta = delta,
                        explanation = "AMRAP ${hit.actualReps} reps sobre el objetivo ${hit.prescribedReps}: TM +${"%.1f".format(delta)} %",
                    )
                }
            }
        }

        if ((readiness != null && readiness < 40 && unload) || autoDeload || signals.overtrainedMuscles.isNotEmpty()) {
            out += AutoregulationProposal(
                kind = AutoregulationProposalKind.INSERT_DELOAD,
                explanation = buildString {
                    append("Readiness ${readiness ?: "n/d"}")
                    if (unload) append(", ACWR UNLOAD")
                    if (signals.overtrainedMuscles.isNotEmpty()) append(", músculos sobrecargados: ${signals.overtrainedMuscles.joinToString()}")
                    append(" — AUGE propone una descarga táctica")
                },
            )
        } else if (readiness != null && readiness < 45) {
            out += AutoregulationProposal(
                kind = AutoregulationProposalKind.SCALE_WEEK_INTENSITY,
                percentDelta = if (readiness < 35) -5.0 else -2.5,
                explanation = "Readiness $readiness — bajar intensidad de la próxima semana",
            )
        }

        if (unload && out.none { it.kind == AutoregulationProposalKind.INSERT_DELOAD }) {
            out += AutoregulationProposal(
                kind = AutoregulationProposalKind.SCALE_WEEK_VOLUME,
                volumeFactor = 0.8,
                explanation = "ACWR UNLOAD — volumen ×0,8 la próxima semana",
            )
        }

        val profile = program.powerliftingProfile
        if (profile != null && signals.consecutiveHighE1rmWeeks >= 2) {
            signals.e1rmByLift.forEach { (slot, e1) ->
                val tm = TrainingMaxResolver.trainingMax(profile, slot, recipe.trainingMaxPercent) ?: return@forEach
                if (e1 > tm * 1.05) {
                    out += AutoregulationProposal(
                        kind = AutoregulationProposalKind.PROMOTE_TM,
                        liftSlot = slot.name,
                        percentDelta = 2.5,
                        explanation = "e1RM ${e1.toInt()} kg > TM ${tm.toInt()} kg × 1,05 dos semanas seguidas",
                    )
                }
            }
        }

        val nextWeekNumber = (week.progressionIndex ?: 1) + 1
        val nextGoal = recipe.weeks.firstOrNull { it.weekNumber == nextWeekNumber }?.blockGoal
        if (nextGoal == com.example.kpkn.data.models.BlockGoal.PEAK && (readiness ?: 100) < 50) {
            out += AutoregulationProposal(
                kind = AutoregulationProposalKind.DELAY_PEAK,
                explanation = "Pico inminente con readiness ${readiness ?: "baja"} — retrasar 1 semana",
            )
        }

        if (signals.repeatedJointPain) {
            out += AutoregulationProposal(
                kind = AutoregulationProposalKind.SWAP_TO_TECHNIQUE_VARIANT,
                explanation = "Molestias articulares repetidas — pasar el T1 a variante técnica (pausa)",
            )
        }

        return out.distinctBy { it.kind to it.liftSlot }
    }

    private fun tmDeltaForAmrap(rule: ProgressionRule, hit: AmrapHit): Double {
        val extra = hit.actualReps - hit.prescribedReps
        return when (rule) {
            is ProgressionRule.AmrapDrivenTm -> {
                val kg = when {
                    hit.actualReps <= 1 -> rule.zeroToOneKg
                    hit.actualReps <= 3 -> rule.twoToThreeKg
                    hit.actualReps <= 5 -> rule.fourToFiveKg
                    else -> rule.sixPlusKg
                }
                if (hit.actualReps <= 1) -2.5 else (kg / 100.0) * 2.5
            }
            is ProgressionRule.RepTargetDrivenTm -> {
                if (extra >= 0) extra * rule.extraRepPercent
                else if (extra <= -rule.missThreshold) extra * rule.missedRepPercent
                else 0.0
            }
            else -> if (extra < 0) -2.5 else 0.0
        }
    }

    fun applyTmDelta(profile: PowerliftingProfile, slot: LiftSlot?, percentDelta: Double): PowerliftingProfile {
        fun scale(value: Double?): Double? = value?.let { it * (1.0 + percentDelta / 100.0) }
        return when (slot) {
            LiftSlot.SQUAT -> profile.copy(squatTM = scale(profile.squatTM) ?: profile.squatTM)
            LiftSlot.BENCH -> profile.copy(benchTM = scale(profile.benchTM) ?: profile.benchTM)
            LiftSlot.DEADLIFT -> profile.copy(deadliftTM = scale(profile.deadliftTM) ?: profile.deadliftTM)
            LiftSlot.OVERHEAD -> profile.copy(overheadTM = scale(profile.overheadTM) ?: profile.overheadTM)
            null -> profile.copy(
                squatTM = scale(profile.squatTM) ?: profile.squatTM,
                benchTM = scale(profile.benchTM) ?: profile.benchTM,
                deadliftTM = scale(profile.deadliftTM) ?: profile.deadliftTM,
                overheadTM = scale(profile.overheadTM) ?: profile.overheadTM,
            )
        }
    }

    private fun liftSlotFor(configurationId: String?): LiftSlot? {
        val id = configurationId?.lowercase().orEmpty()
        return when {
            id.contains("deadlift") || id.contains("peso_muerto") -> LiftSlot.DEADLIFT
            id.contains("bench") || id.contains("banca") -> LiftSlot.BENCH
            id.contains("squat") || id.contains("sentadilla") -> LiftSlot.SQUAT
            id.contains("military") || id.contains("overhead") || id.contains("push_press") -> LiftSlot.OVERHEAD
            else -> null
        }
    }
}
