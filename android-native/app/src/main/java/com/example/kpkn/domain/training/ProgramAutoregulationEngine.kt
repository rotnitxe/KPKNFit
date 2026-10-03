package com.example.kpkn.domain.training

import com.example.kpkn.data.models.AutoregulationAuditEntry
import com.example.kpkn.data.models.AutoregulationMode
import com.example.kpkn.data.models.AutoregulationProposal
import com.example.kpkn.data.models.AutoregulationProposalKind
import com.example.kpkn.data.models.AppliedRecipeProposal
import com.example.kpkn.data.models.LoadAdvisoryLevel
import com.example.kpkn.data.models.PendingActionResolutionStatus
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
        /** Evidencia a nivel de sesión (§14.5): sesiones iniciadas/registradas del objetivo. */
        executedSessionIds: Set<String> = emptySet(),
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
                    executedSessionIds = executedSessionIds,
                    nowMs = nowMs,
                )
                // §14.5/AC-G4: AUTO también deja estado terminal por propuesta; nunca
                // se declara «aplicada» una propuesta sin efecto observable.
                val outcomes = outcomeEntries(
                    before = program,
                    after = mutated,
                    proposals = proposals,
                    targetWeekId = nextWeekId,
                    protectedWeekIds = executedWeekIds,
                    mode = program.autoregulationMode,
                    nowMs = nowMs,
                )
                val baseRun = mutated.runState
                    ?: com.example.kpkn.data.models.ProgramRunState(runId = ProgramProgressEngine.newRunId())
                val run = baseRun.copy(autoregulationAudit = audit + outcomes, pendingAction = null)
                AutoregulationEvaluation(
                    proposals = proposals,
                    program = mutated.copy(runState = run),
                    applied = outcomes.any { it.resolution == PendingActionResolutionStatus.APPLIED },
                )
            }
            AutoregulationMode.OFF -> AutoregulationEvaluation(emptyList(), program)
        }
    }

    /**
     * Resuelve la acción `CONFIRM_AUTOREGULATION` pendiente (§14.5): rechazar y
     * aceptar dejan SIEMPRE estado terminal con motivo en `runState.autoregulationAudit`.
     *
     * @param executedWeekIds evidencia real de semanas entrenadas. Si es null se
     *   deriva del propio run (sesiones realmente completadas), nunca del mero
     *   `runState.weekId`: esa es la colisión R-202 (avance asigna el run a la
     *   semana siguiente vacía y la protección la marcaba como ejecutada).
     */
    fun resolvePending(
        program: Program,
        accept: Boolean,
        metadata: ExerciseCompositionMetadataProvider? = null,
        only: AutoregulationProposal? = null,
        executedWeekIds: Set<String>? = null,
        executedSessionIds: Set<String> = emptySet(),
        nowMs: Long = System.currentTimeMillis(),
    ): Program {
        val run = program.runState ?: return program
        val action = run.pendingAction ?: return program
        if (action.type != PendingProgramActionType.CONFIRM_AUTOREGULATION) return program
        val resolved = if (only == null) action.proposals else action.proposals.filter { it == only }
        val remaining = if (only == null) emptyList() else action.proposals.filterNot { it == only }
        val nextPending = if (remaining.isEmpty()) null else action.copy(proposals = remaining)

        if (!accept) {
            return program.withResolutionEntries(
                pending = nextPending,
                entries = resolved.map { proposal ->
                    resolutionEntry(
                        proposal = proposal,
                        status = PendingActionResolutionStatus.REJECTED,
                        reason = "Rechazada por el atleta: ${proposal.explanation}",
                        targetWeekId = action.targetWeekId,
                        mode = program.autoregulationMode,
                        nowMs = nowMs,
                    )
                },
            )
        }

        fun expired(reason: String): Program = program.withResolutionEntries(
            pending = nextPending,
            entries = resolved.map { proposal ->
                resolutionEntry(proposal, PendingActionResolutionStatus.EXPIRED, reason, action.targetWeekId, program.autoregulationMode, nowMs)
            },
        )

        val recipe = program.sourceRecipe
            ?: return expired("No aplicada: el programa no tiene receta fuente.")
        val provider = metadata ?: runCatching { CompositionMetadataHolder.resolve() }.getOrNull()
            ?: return expired("No aplicada: sin metadatos de composición del catálogo.")
        val evidence = executedWeekIds ?: derivedExecutedWeekIds(program)
        val mutated = applyMutations(
            program = program.copy(runState = run.copy(pendingAction = nextPending)),
            nextWeekId = action.targetWeekId,
            proposals = resolved,
            recipe = recipe,
            executedWeekIds = evidence,
            metadata = provider,
            executedSessionIds = executedSessionIds,
            nowMs = nowMs,
        )
        val outcomes = outcomeEntries(
            before = program,
            after = mutated,
            proposals = resolved,
            targetWeekId = action.targetWeekId,
            protectedWeekIds = evidence,
            mode = program.autoregulationMode,
            nowMs = nowMs,
        )
        return mutated.withResolutionEntries(pending = mutated.runState?.pendingAction, entries = outcomes)
    }

    /**
     * Estado terminal «expirada» (§14.5/AC-G4) para una acción pendiente que
     * otra transición sustituye: avance de semana, cierre de ciclo o cambio de
     * bloque. Idempotente: sin acción `CONFIRM_AUTOREGULATION` no añade nada.
     */
    fun expirePending(
        program: Program,
        reason: String,
        nowMs: Long = System.currentTimeMillis(),
    ): Program {
        val run = program.runState ?: return program
        val action = run.pendingAction ?: return program
        if (action.type != PendingProgramActionType.CONFIRM_AUTOREGULATION) return program
        val entries = if (action.proposals.isEmpty()) {
            listOf(
                AutoregulationAuditEntry(
                    atMs = nowMs,
                    mode = program.autoregulationMode,
                    kinds = emptyList(),
                    weekId = action.targetWeekId,
                    resolution = PendingActionResolutionStatus.EXPIRED,
                    resolutionReason = reason,
                ),
            )
        } else {
            action.proposals.map { proposal ->
                resolutionEntry(
                    proposal = proposal,
                    status = PendingActionResolutionStatus.EXPIRED,
                    reason = reason,
                    targetWeekId = action.targetWeekId,
                    mode = program.autoregulationMode,
                    nowMs = nowMs,
                )
            }
        }
        return program.copy(
            runState = run.copy(
                pendingAction = null,
                autoregulationAudit = run.autoregulationAudit + entries,
            ),
        )
    }

    /** §14.5: evidencia de semanas con trabajo real, derivada del propio run. */
    internal fun derivedExecutedWeekIds(program: Program): Set<String> {
        val run = program.runState ?: return emptySet()
        // `completedSessionIds` solo se llena con sesiones realmente completadas
        // en la semana del cursor; vacío = semana todavía sin trabajo (R-202).
        if (run.completedSessionIds.isEmpty()) return emptySet()
        val ids = mutableSetOf<String>()
        listOfNotNull(run.weekId, run.weekInstanceId).forEach { id ->
            ids += id
            ids += ProgramProgressEngine.templateWeekIdFromInstance(id) ?: id
        }
        return ids
    }

    private fun resolutionEntry(
        proposal: AutoregulationProposal,
        status: PendingActionResolutionStatus,
        reason: String,
        targetWeekId: String?,
        mode: AutoregulationMode,
        nowMs: Long,
    ): AutoregulationAuditEntry = AutoregulationAuditEntry(
        atMs = nowMs,
        mode = mode,
        kinds = listOf(proposal.kind),
        weekId = targetWeekId,
        resolution = status,
        resolutionReason = reason,
    )

    private fun Program.withResolutionEntries(
        pending: PendingProgramAction?,
        entries: List<AutoregulationAuditEntry>,
    ): Program {
        val current = runState
            ?: return copy(
                runState = com.example.kpkn.data.models.ProgramRunState(
                    runId = ProgramProgressEngine.newRunId(),
                    pendingAction = pending,
                    autoregulationAudit = entries,
                ),
            )
        return copy(
            runState = current.copy(
                pendingAction = pending,
                autoregulationAudit = current.autoregulationAudit + entries,
            ),
        )
    }

    /**
     * Estado terminal de cada propuesta (AC-G4): una propuesta solo se marca
     * APPLIED si su efecto es observable (perfil de TM, semana objetivo o
     * inserción de descarga); si la semana objetivo está protegida por trabajo
     * real o el mecanismo no aplica (p. ej. intensidad sobre receta RIR sin
     * porcentajes), queda EXPIRED con el motivo concreto.
     */
    internal fun outcomeEntries(
        before: Program,
        after: Program,
        proposals: List<AutoregulationProposal>,
        targetWeekId: String?,
        protectedWeekIds: Set<String>,
        mode: AutoregulationMode,
        nowMs: Long,
    ): List<AutoregulationAuditEntry> {
        if (proposals.isEmpty()) return emptyList()
        val beforeWeek = targetWeekId?.let { id -> weekWithId(before, id) }
        val afterWeek = targetWeekId?.let { id -> weekWithId(after, id) }
        val protectedTarget = targetWeekId != null && targetWeekId in protectedWeekIds
        val profileChanged = after.powerliftingProfile != before.powerliftingProfile
        val deloadChanged = blockSignature(before) != blockSignature(after)
        val percentsBefore = percentSignature(beforeWeek)
        val percentsAfter = percentSignature(afterWeek)
        val countsBefore = setCountSignature(beforeWeek)
        val countsAfter = setCountSignature(afterWeek)
        val techniqueBefore = techniqueSignature(beforeWeek)
        val techniqueAfter = techniqueSignature(afterWeek)
        val hasPercent = percentsBefore.any { it != null }
        val trainedReason = "No aplicada: la semana objetivo ya tiene sesiones entrenadas; su prescripción se conserva (§14.5)."
        return proposals.map { proposal ->
            val (status, reason) = when (proposal.kind) {
                AutoregulationProposalKind.ADJUST_TM,
                AutoregulationProposalKind.PROMOTE_TM,
                -> when {
                    profileChanged -> PendingActionResolutionStatus.APPLIED to "Aplicada: ${proposal.explanation}"
                    protectedTarget -> PendingActionResolutionStatus.EXPIRED to trainedReason
                    else -> PendingActionResolutionStatus.EXPIRED to
                        "No aplicada: el programa no tiene perfil de cargas para ajustar el TM."
                }

                AutoregulationProposalKind.INSERT_DELOAD -> when {
                    deloadChanged -> PendingActionResolutionStatus.APPLIED to "Aplicada: ${proposal.explanation}"
                    protectedTarget -> PendingActionResolutionStatus.EXPIRED to trainedReason
                    else -> PendingActionResolutionStatus.EXPIRED to "No aplicada: no se pudo insertar la descarga propuesta."
                }

                AutoregulationProposalKind.SCALE_WEEK_INTENSITY,
                AutoregulationProposalKind.DELAY_PEAK,
                -> when {
                    percentsBefore != percentsAfter -> PendingActionResolutionStatus.APPLIED to "Aplicada: ${proposal.explanation}"
                    protectedTarget -> PendingActionResolutionStatus.EXPIRED to trainedReason
                    !hasPercent -> PendingActionResolutionStatus.EXPIRED to
                        "No aplicable: la prescripción de esa semana no usa porcentajes (RIR/esfuerzo); " +
                        "usa una propuesta explícita de carga/esfuerzo de la misma configuración (§12.4)."
                    else -> PendingActionResolutionStatus.EXPIRED to "Sin efecto observable sobre la semana objetivo."
                }

                AutoregulationProposalKind.SCALE_WEEK_VOLUME -> when {
                    countsBefore != countsAfter -> PendingActionResolutionStatus.APPLIED to "Aplicada: ${proposal.explanation}"
                    protectedTarget -> PendingActionResolutionStatus.EXPIRED to trainedReason
                    proposal.volumeFactor == null || proposal.volumeFactor!! >= 1.0 ->
                        PendingActionResolutionStatus.EXPIRED to "No aplicable: el factor de volumen propuesto no reduce la semana."
                    else -> PendingActionResolutionStatus.EXPIRED to
                        "Sin efecto observable: la semana no perdió series (respetando SPEED y mínimos)."
                }

                AutoregulationProposalKind.SWAP_TO_TECHNIQUE_VARIANT -> when {
                    techniqueBefore != techniqueAfter -> PendingActionResolutionStatus.APPLIED to "Aplicada: ${proposal.explanation}"
                    protectedTarget -> PendingActionResolutionStatus.EXPIRED to trainedReason
                    else -> PendingActionResolutionStatus.EXPIRED to
                        "No aplicada: el cambio de variante no alteró la prescripción de la semana objetivo."
                }
            }
            resolutionEntry(proposal, status, reason, targetWeekId, mode, nowMs)
        }
    }

    private fun percentSignature(week: ProgramWeek?): List<Double?> =
        week?.sessions?.flatMap { it.allExercises() }?.flatMap { it.sets }?.map { it.targetPercentageRM }.orEmpty()

    private fun setCountSignature(week: ProgramWeek?): List<Int> =
        week?.sessions?.flatMap { it.allExercises() }?.map { it.sets.size }.orEmpty()

    private fun techniqueSignature(week: ProgramWeek?): List<String?> =
        week?.sessions?.flatMap { it.allExercises() }?.map { it.techniqueModifier?.name ?: it.variantName }.orEmpty()

    private fun weekWithId(program: Program, weekId: String): ProgramWeek? {
        program.macrocycles.forEach { macro ->
            macro.blocks.forEach { block ->
                block.mesocycles.forEach { meso ->
                    meso.weeks.firstOrNull { it.id == weekId }?.let { return it }
                }
            }
        }
        return null
    }

    private fun blockSignature(program: Program): List<Pair<String, String?>> =
        program.macrocycles.flatMap { it.blocks }.map { it.id to it.goal?.name }

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

    fun collectE1rmFromHistory(logs: List<WorkoutLog>): Map<LiftSlot, Double> {
        val acc = mutableMapOf<LiftSlot, Double>()
        logs.flatMap { it.completedExercises }.forEach { exercise ->
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
        /** Evidencia a nivel de sesión (§14.5); esas sesiones no se reconstruyen. */
        executedSessionIds: Set<String> = emptySet(),
        nowMs: Long = System.currentTimeMillis(),
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
                                        slot
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
            val beforeWeeks = working.macrocycles
            working = PlanMaterializer.rematerializeWeek(
                program = working,
                weekId = nextWeekId,
                recipe = recipeForNext,
                metadata = metadata,
                intensityScale = intensity,
                volumeFactor = volume,
                executedWeekIds = executedWeekIds,
                executedSessionIds = executedSessionIds,
            )
            // §14.4: la receta efectiva aprobada se persiste JUNTO a las sesiones;
            // nunca se deja el cambio solo en `recipeForNext`. La upsert es por
            // (ocurrencia, ciclo), así que repetir no duplica entradas.
            if (working.macrocycles !== beforeWeeks && working.macrocycles != beforeWeeks) {
                val source = PlanMaterializer.weekRecipeSourceFor(working, recipeForNext, nextWeekId)
                if (source != null) {
                    working = PlanMaterializer.withEffectiveWeekRecipe(
                        program = working,
                        weekOccurrence = source.weekOccurrence,
                        cycleNumber = source.cycleNumber,
                        weekRecipe = PlanMaterializer.scaleWeekRecipe(source.weekRecipe, intensity, volume),
                        applied = proposals
                            .filter { it.kind != AutoregulationProposalKind.INSERT_DELOAD }
                            .map { proposal ->
                                AppliedRecipeProposal(
                                    proposalId = "${proposal.kind.name}:${proposal.liftSlot.orEmpty()}:$nextWeekId",
                                    kind = proposal.kind.name,
                                    summary = proposal.explanation,
                                    acceptedAtMs = nowMs,
                                )
                            },
                    )
                }
            }
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
