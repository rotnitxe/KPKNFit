package com.example.kpkn.domain.training

import com.example.kpkn.data.models.AutoregulationAuditEntry
import com.example.kpkn.data.models.AutoregulationMode
import com.example.kpkn.data.models.AutoregulationProposal
import com.example.kpkn.data.models.AutoregulationProposalKind
import com.example.kpkn.data.models.AppliedRecipeProposal
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.LoadAdvisoryLevel
import com.example.kpkn.data.models.PendingActionResolutionStatus
import com.example.kpkn.data.models.PendingProgramAction
import com.example.kpkn.data.models.PendingProgramActionType
import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.protocols.LiftSlot
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.ProgressionRule
import com.example.kpkn.data.protocols.SlotRecipe
import com.example.kpkn.data.protocols.TechniqueModifier
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.domain.auge.AugeUtils
import com.example.kpkn.domain.auge.LoadAdvisoryEngine
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import com.example.kpkn.domain.text.SpanishPlurals
import kotlin.math.abs
import kotlin.math.floor

/**
 * Una serie AMRAP realizada. [liftSlot] es null cuando el ejercicio no declara levantamiento en su
 * receta (p. ej. las dominadas de Texas): el hit se registra pero no genera propuesta de TM.
 * [weightRatio] es la carga registrada entre la prescrita (null si alguna no se conoce).
 */
data class AmrapHit(
    val liftSlot: LiftSlot?,
    val percent: Double?,
    val prescribedReps: Int,
    val actualReps: Int,
    val meanRpe: Double? = null,
    val weightRatio: Double? = null,
)

/** Un top set realizado (serie con `isTopSet` de un slot con levantamiento), para `TopSetPr`. */
data class TopSetHit(
    val liftSlot: LiftSlot,
    val prescribedReps: Int,
    val actualReps: Int,
    val weightRatio: Double? = null,
)

/**
 * Una serie al máximo (base `REP_MAX`) del levantamiento de competición, para `RepMaxAutoregulated`.
 * [estimatedOneRmKg] sale de `calculateHybrid1RM` con la carga y las repeticiones de la serie.
 */
data class RepMaxHit(
    val liftSlot: LiftSlot,
    val weightKg: Double,
    val reps: Int,
    val estimatedOneRmKg: Double,
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
    /** Top sets de la semana; vacío = `evaluate` los lee de los registros (solo con `TopSetPr`). */
    val topSetHits: List<TopSetHit> = emptyList(),
    /** Series al máximo de la semana; vacío = `evaluate` las lee de los registros (solo con `RepMaxAutoregulated`). */
    val repMaxHits: List<RepMaxHit> = emptyList(),
)

data class AutoregulationEvaluation(
    val proposals: List<AutoregulationProposal>,
    val program: Program,
    val applied: Boolean = false,
)

object ProgramAutoregulationEngine {

    /** Un AMRAP de `AmrapDrivenTm` solo mueve el TM desde este porcentaje del TM: uno más ligero no mide el TM. */
    const val AMRAP_TM_MIN_PERCENT = 85.0

    /** Desde este porcentaje, 1 repetición o menos en un AMRAP cuenta como AMRAP corto aunque el objetivo fuera 1+. */
    const val SHORT_AMRAP_SINGLE_MIN_PERCENT = 90.0

    /** Bajada del TM (en %) por un AMRAP corto, un top set dos o más repeticiones corto o una serie al máximo muy baja. */
    const val TM_DOWN_PERCENT = -2.5

    /** Subida del TM (en %) de una serie al máximo claramente por encima del 1RM que implica el TM. */
    const val TM_UP_PERCENT = 2.5

    /** `TopSetPr`: con tantas repeticiones sobre el objetivo la subida es doble; con tantas por debajo, baja el TM. */
    const val TOP_SET_REPS_MARGIN = 2

    /** `RepMaxAutoregulated`: e1RM igual o por encima de este múltiplo del 1RM implícito del TM sube el TM. */
    const val REP_MAX_RAISE_RATIO = 1.025

    /** `RepMaxAutoregulated`: e1RM igual o por debajo de este múltiplo del 1RM implícito del TM baja el TM. */
    const val REP_MAX_DROP_RATIO = 0.95

    /** `RepMaxAutoregulated`: la estimación del 1RM solo es fiable hasta estas repeticiones (Brzycki). */
    const val REP_MAX_MAX_REPS = 10

    /** Una serie hecha con menos de esta fracción de la carga prescrita no cuenta para SUBIR el TM. */
    const val LIGHTER_LOAD_RATIO = 0.975

    /** Paso de redondeo del TM al aplicar una propuesta en kilos (sin inventario en este motor). */
    private const val KG_ROUNDING_STEP = 0.5

    /**
     * Cambio de TM que propone una señal de rendimiento: en kilos ([kgDelta], de la tabla de la receta)
     * o en porcentaje ([percentDelta]); nunca los dos a la vez.
     */
    internal data class TmChange(val kgDelta: Double? = null, val percentDelta: Double? = null) {
        val isEffective: Boolean get() = (kgDelta ?: 0.0) != 0.0 || (percentDelta ?: 0.0) != 0.0
        val isReduction: Boolean get() = (kgDelta ?: 0.0) < 0.0 || (percentDelta ?: 0.0) < 0.0
    }

    /**
     * Propuestas de la semana completada. La progresión POR RENDIMIENTO (AMRAP, top set, serie al
     * máximo) sale aquí como `ADJUST_TM` y respeta OFF (nada), PROPOSE (pendiente) y AUTO (aplicada con
     * auditoría). Las señales de [signals] mandan; si faltan, se leen de [logs] filtrando por el ciclo
     * y el run del programa y tomando el registro más reciente de cada sesión.
     */
    fun evaluate(
        program: Program,
        completedWeek: ProgramWeek,
        logs: List<WorkoutLog>,
        recipe: TrainingPlanRecipe,
        signals: WeeklyAutoregulationSignals,
    ): List<AutoregulationProposal> {
        if (program.autoregulationMode == AutoregulationMode.OFF) return emptyList()
        val cycle = program.runState?.cycleNumber
        val runId = program.runState?.runId
        val rule = recipe.progression
        val hits = signals.amrapHits.ifEmpty { collectAmrapHits(completedWeek, logs, cycle, program.id, runId, recipe) }
        val topSetHits: List<TopSetHit> = if (rule is ProgressionRule.TopSetPr) {
            signals.topSetHits.ifEmpty { collectTopSetHits(completedWeek, logs, recipe, cycle, program.id, runId) }
        } else {
            emptyList()
        }
        val repMaxHits: List<RepMaxHit> = if (rule is ProgressionRule.RepMaxAutoregulated) {
            signals.repMaxHits.ifEmpty { collectRepMaxHits(completedWeek, logs, recipe, cycle, program.id, runId) }
        } else {
            emptyList()
        }
        return buildProposals(
            program,
            completedWeek,
            recipe,
            signals.copy(amrapHits = hits, topSetHits = topSetHits, repMaxHits = repMaxHits),
        )
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
                    // El TM de SU levantamiento cambió, ya sea por kilos (`kgDelta`) o por porcentaje.
                    tmChangedFor(before, after, proposal) ->
                        PendingActionResolutionStatus.APPLIED to "Aplicada: ${proposal.explanation}"
                    protectedTarget -> PendingActionResolutionStatus.EXPIRED to trainedReason
                    proposal.liftSlot == null -> PendingActionResolutionStatus.EXPIRED to
                        "No aplicada: la propuesta no indica a qué levantamiento corresponde."
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

    /** true si el TM guardado del levantamiento de [proposal] es distinto antes y después. */
    private fun tmChangedFor(before: Program, after: Program, proposal: AutoregulationProposal): Boolean {
        val slot = proposal.liftSlot?.let { runCatching { LiftSlot.valueOf(it) }.getOrNull() } ?: return false
        return storedTm(before.powerliftingProfile, slot) != storedTm(after.powerliftingProfile, slot)
    }

    private fun storedTm(profile: PowerliftingProfile?, slot: LiftSlot): Double? = when (slot) {
        LiftSlot.SQUAT -> profile?.squatTM
        LiftSlot.BENCH -> profile?.benchTM
        LiftSlot.DEADLIFT -> profile?.deadliftTM
        LiftSlot.OVERHEAD -> profile?.overheadTM
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

    // ─── Señales de rendimiento leídas de los registros ───────────────────────────────

    /**
     * Series AMRAP realizadas en [week] (la semana plantilla).
     *
     * Con [cycleNumber] y [programId] solo cuentan los registros de ESA ocurrencia (ciclo, run y semana,
     * `ProgramProgressEngine.logsForInstance`): antes la semana 1 del ciclo 2 leía también la del
     * ciclo 1. Sin ellos se filtra solo por semana, como antes. Cada ejercicio con AMRAP se empareja con
     * el registro MÁS RECIENTE de su sesión que lo contenga y, dentro, con su serie AMRAP realizada
     * (la marcada `amrapPerformed`; sin marca, por posición si el registro tiene las series del plan).
     *
     * [recipe] decide el levantamiento de cada hit: el slot de receta del ejercicio, no una búsqueda de
     * texto. Sin receta, o si la configuración no aparece en ella, se usa la búsqueda de texto de
     * [liftSlotFromConfigurationId].
     */
    fun collectAmrapHits(
        week: ProgramWeek,
        logs: List<WorkoutLog>,
        cycleNumber: Int? = null,
        programId: String? = null,
        runId: String? = null,
        recipe: TrainingPlanRecipe? = null,
    ): List<AmrapHit> {
        val weekLogs = weekLogsOf(week, logs, cycleNumber, programId, runId)
        if (weekLogs.isEmpty()) return emptyList()
        val lifts = RecipeLiftResolver(recipe)
        val weekSessionIds = week.sessions.mapTo(mutableSetOf()) { it.id }
        val out = mutableListOf<AmrapHit>()
        week.sessions.forEach { session ->
            val sessionLogs = logsOfSession(weekLogs, session, weekSessionIds)
            session.allExercises().forEach { exercise ->
                pairedSets(exercise, sessionLogs, isTarget = { it.isAmrap }, isFlagged = { it.amrapPerformed })
                    .forEach { pair ->
                        out += AmrapHit(
                            liftSlot = lifts.liftSlotFor(exercise),
                            percent = pair.prescribed.targetPercentageRM,
                            prescribedReps = pair.prescribed.targetReps
                                ?: pair.prescribed.targetRepsRange?.min
                                ?: pair.performed.amrapMinimumReps
                                ?: 0,
                            actualReps = pair.performed.reps,
                            meanRpe = pair.performed.rpe ?: pair.prescribed.targetRPE,
                            weightRatio = weightRatio(pair.prescribed, pair.performed),
                        )
                    }
            }
        }
        return out
    }

    /**
     * Top sets realizados en [week]: series con `isTopSet` de un slot con levantamiento, emparejadas por
     * posición con el registro más reciente de su sesión (solo si el registro tiene las series del plan).
     * Los ajustes por ciclo y run son los de [collectAmrapHits].
     */
    fun collectTopSetHits(
        week: ProgramWeek,
        logs: List<WorkoutLog>,
        recipe: TrainingPlanRecipe,
        cycleNumber: Int? = null,
        programId: String? = null,
        runId: String? = null,
    ): List<TopSetHit> {
        val weekLogs = weekLogsOf(week, logs, cycleNumber, programId, runId)
        if (weekLogs.isEmpty()) return emptyList()
        val lifts = RecipeLiftResolver(recipe)
        val weekSessionIds = week.sessions.mapTo(mutableSetOf()) { it.id }
        val out = mutableListOf<TopSetHit>()
        week.sessions.forEach { session ->
            val sessionLogs = logsOfSession(weekLogs, session, weekSessionIds)
            session.allExercises().forEach { exercise ->
                val lift = lifts.liftSlotFor(exercise) ?: return@forEach
                pairedSets(exercise, sessionLogs, isTarget = { it.isTopSet }, isFlagged = null)
                    .forEach { pair ->
                        val prescribedReps = pair.prescribed.targetReps
                            ?: pair.prescribed.targetRepsRange?.min
                            ?: return@forEach
                        out += TopSetHit(
                            liftSlot = lift,
                            prescribedReps = prescribedReps,
                            actualReps = pair.performed.reps,
                            weightRatio = weightRatio(pair.prescribed, pair.performed),
                        )
                    }
            }
        }
        return out
    }

    /**
     * Series al máximo realizadas en [week] para `RepMaxAutoregulated`: series con base `REP_MAX` (o top
     * set sin porcentaje) del levantamiento de COMPETICIÓN de la receta (`recipe.liftSlots`). Las
     * variantes (cajón, buenos días, press con cadenas…) no cuentan: su máximo no se compara con el 1RM
     * del levantamiento. Solo series de 1 a [REP_MAX_MAX_REPS] repeticiones con carga.
     */
    fun collectRepMaxHits(
        week: ProgramWeek,
        logs: List<WorkoutLog>,
        recipe: TrainingPlanRecipe,
        cycleNumber: Int? = null,
        programId: String? = null,
        runId: String? = null,
    ): List<RepMaxHit> {
        val weekLogs = weekLogsOf(week, logs, cycleNumber, programId, runId)
        if (weekLogs.isEmpty()) return emptyList()
        val lifts = RecipeLiftResolver(recipe)
        val weekSessionIds = week.sessions.mapTo(mutableSetOf()) { it.id }
        val out = mutableListOf<RepMaxHit>()
        week.sessions.forEach { session ->
            val sessionLogs = logsOfSession(weekLogs, session, weekSessionIds)
            session.allExercises().forEach { exercise ->
                val lift = lifts.liftSlotFor(exercise) ?: return@forEach
                val configurationId = exercise.catalogConfigurationId ?: exercise.exerciseDbId
                if (configurationId == null || recipe.liftSlots[lift] != configurationId) return@forEach
                val isRepMaxSet = { set: ExerciseSet ->
                    set.loadBasis == LoadBasis.REP_MAX || (set.isTopSet && set.targetPercentageRM == null)
                }
                pairedSets(exercise, sessionLogs, isTarget = isRepMaxSet, isFlagged = null)
                    .forEach { pair ->
                        val reps = pair.performed.reps
                        val weight = pair.performed.weight
                        if (reps !in 1..REP_MAX_MAX_REPS || weight <= 0.0) return@forEach
                        out += RepMaxHit(
                            liftSlot = lift,
                            weightKg = weight,
                            reps = reps,
                            estimatedOneRmKg = calculateHybrid1RM(weight, reps, isAmrap = pair.performed.amrapPerformed),
                        )
                    }
            }
        }
        return out
    }

    /**
     * Levantamientos con algún AMRAP corto ([isShortAmrap]) en [weeks], las semanas plantilla del ciclo o
     * bloque que se cierra. La progresión del método no sube su TM ese cierre («un lift con AMRAP corto
     * no sube»).
     */
    internal fun shortAmrapLifts(
        weeks: List<ProgramWeek>,
        logs: List<WorkoutLog>,
        cycleNumber: Int,
        programId: String,
        runId: String?,
        recipe: TrainingPlanRecipe,
    ): Set<LiftSlot> = weeks
        .flatMap { week -> collectAmrapHits(week, logs, cycleNumber, programId, runId, recipe) }
        .filter { hit -> isShortAmrap(hit) }
        .mapNotNullTo(linkedSetOf<LiftSlot>()) { hit -> hit.liftSlot }

    /** Un par serie prescrita / serie registrada del mismo ejercicio. */
    private class SetPair(val prescribed: ExerciseSet, val performed: CompletedSet)

    /**
     * Registros de la semana [week]. Con ciclo y programa solo los de esa ocurrencia
     * (`ProgramProgressEngine.logsForInstance`: ciclo, run y semana); sin ellos, todos los de la semana
     * plantilla, como antes (mezcla ciclos).
     */
    private fun weekLogsOf(
        week: ProgramWeek,
        logs: List<WorkoutLog>,
        cycleNumber: Int?,
        programId: String?,
        runId: String?,
    ): List<WorkoutLog> {
        if (cycleNumber == null || programId == null) {
            return logs.filter { it.weekId == week.id || it.weekInstanceId == week.id }
        }
        val templateWeekId = ProgramProgressEngine.templateWeekIdFromInstance(week.id) ?: week.id
        return ProgramProgressEngine.logsForInstance(
            logs = logs,
            programId = programId,
            instanceId = ProgramProgressEngine.instanceIdFor(cycleNumber, templateWeekId),
            cycleNumber = cycleNumber,
            programRunId = runId,
        )
    }

    /**
     * Registros que pueden contener los ejercicios de [session], del más reciente al más antiguo: los de
     * la propia sesión y los huérfanos (cuyo `sessionId` no es el de ninguna sesión de la semana: sesión
     * renombrada o sustituida). Con la misma fecha manda el primero de [weekLogs].
     */
    private fun logsOfSession(
        weekLogs: List<WorkoutLog>,
        session: Session,
        weekSessionIds: Set<String>,
    ): List<WorkoutLog> = weekLogs
        .filter { it.sessionId == session.id || it.sessionId !in weekSessionIds }
        .sortedByDescending { AugeUtils.logDateMs(it) }

    /** Los ejercicios registrados que corresponden a [exercise]: por id; si no, por configuración. */
    private fun loggedExercisesOf(log: WorkoutLog, exercise: Exercise): List<CompletedExercise> {
        val byId = log.completedExercises.filter { it.exerciseId == exercise.id }
        if (byId.isNotEmpty()) return byId
        val configurationId = exercise.catalogConfigurationId
        val dbId = exercise.exerciseDbId
        return log.completedExercises.filter { done ->
            (configurationId != null && done.catalogConfigurationId == configurationId) ||
                (dbId != null && done.exerciseDbId == dbId)
        }
    }

    /**
     * Empareja las series prescritas de [exercise] que cumplen [isTarget] con las series registradas, en
     * el primer registro de [logs] (el más reciente) que contiene el ejercicio con series de trabajo.
     * Con [isFlagged] la i-ésima prescrita se empareja con la i-ésima serie marcada; sin series
     * marcadas, por posición, y solo si el registro tiene tantas series de trabajo como el plan (si no,
     * una serie omitida desalinearía el emparejamiento y mejor no hay dato).
     */
    private fun pairedSets(
        exercise: Exercise,
        logs: List<WorkoutLog>,
        isTarget: (ExerciseSet) -> Boolean,
        isFlagged: ((CompletedSet) -> Boolean)?,
    ): List<SetPair> {
        val planned = exercise.sets.filterNot { it.isEmptySlot }
        val targets = planned.withIndex().filter { isTarget(it.value) }
        if (targets.isEmpty()) return emptyList()
        for (log in logs) {
            val working = loggedExercisesOf(log, exercise)
                .flatMap { it.sets }
                .filter { !it.isWarmup && !it.skipped }
            if (working.isEmpty()) continue
            val flagged: List<CompletedSet> = if (isFlagged == null) emptyList() else working.filter(isFlagged)
            val pairs: List<SetPair> = when {
                flagged.isNotEmpty() ->
                    targets.zip(flagged) { target, performed -> SetPair(target.value, performed) }
                working.size == planned.size ->
                    targets.map { target -> SetPair(target.value, working[target.index]) }
                else -> emptyList()
            }
            if (pairs.isNotEmpty()) return pairs
        }
        return emptyList()
    }

    /** Carga registrada entre carga prescrita; null si alguna no se conoce. */
    private fun weightRatio(prescribed: ExerciseSet, performed: CompletedSet): Double? {
        val plannedKg = prescribed.weight?.takeIf { it > 0.0 } ?: return null
        val doneKg = performed.weight.takeIf { it > 0.0 } ?: return null
        return doneKg / plannedKg
    }

    /** true si la serie se hizo con claramente menos carga que la prescrita: no vale para SUBIR el TM. */
    private fun carriedLighter(weightRatio: Double?): Boolean =
        weightRatio != null && weightRatio < LIGHTER_LOAD_RATIO

    fun collectE1rmByLift(logs: List<WorkoutLog>, week: ProgramWeek): Map<LiftSlot, Double> {
        val weekLogs = logs.filter { it.weekId == week.id || it.weekInstanceId == week.id }
        val acc = mutableMapOf<LiftSlot, Double>()
        weekLogs.flatMap { it.completedExercises }.forEach { exercise ->
            val slot = liftSlotFromConfigurationId(exercise.catalogConfigurationId ?: exercise.exerciseDbId) ?: return@forEach
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
            val slot = liftSlotFromConfigurationId(exercise.catalogConfigurationId ?: exercise.exerciseDbId) ?: return@forEach
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
                // Sin levantamiento no hay TM que ajustar: antes escalaba los cuatro (L-16, las
                // dominadas de Texas). Una propuesta antigua sin levantamiento queda sin efecto.
                val slot = proposal.liftSlot?.let { runCatching { LiftSlot.valueOf(it) }.getOrNull() }
                    ?: return@forEach
                val current = profile ?: return@forEach
                val kg = proposal.kgDelta
                val percent = proposal.percentDelta
                profile = when {
                    kg != null && kg != 0.0 -> applyTmKgDelta(current, slot, kg, recipe.trainingMaxPercent)
                    percent != null && percent != 0.0 -> applyTmDelta(current, slot, percent)
                    else -> current
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
        val profile = program.powerliftingProfile

        out += performanceProposals(recipe, signals, profile, readiness)

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

    // ─── Progresión por rendimiento: propuestas ADJUST_TM ──────────────────────────────

    /**
     * Propuestas `ADJUST_TM` de la progresión por rendimiento: el AMRAP de la semana (cualquier regla
     * puede bajar el TM si el AMRAP quedó corto; solo `AmrapDrivenTm` y `RepTargetDrivenTm` lo suben), el
     * top set de `TopSetPr` y la serie al máximo de `RepMaxAutoregulated`. Un levantamiento sin ajuste
     * (hit sin levantamiento, cambio nulo) no genera propuesta.
     */
    private fun performanceProposals(
        recipe: TrainingPlanRecipe,
        signals: WeeklyAutoregulationSignals,
        profile: PowerliftingProfile?,
        readiness: Int?,
    ): List<AutoregulationProposal> {
        val proposals = mutableListOf<AutoregulationProposal>()
        signals.amrapHits.forEach { hit ->
            // Sin levantamiento en la receta (las dominadas de Texas) el hit solo se registra: no hay TM que ajustar.
            val lift = hit.liftSlot ?: return@forEach
            val change = amrapTmChange(recipe.progression, hit) ?: return@forEach
            proposals += tmProposal(lift, change, amrapExplanation(hit, lift, change, readiness))
        }
        val rule = recipe.progression
        if (rule is ProgressionRule.TopSetPr) {
            signals.topSetHits.forEach { hit ->
                val change = topSetTmChange(rule, hit) ?: return@forEach
                proposals += tmProposal(hit.liftSlot, change, topSetExplanation(hit, change))
            }
        }
        if (rule is ProgressionRule.RepMaxAutoregulated && profile != null && recipe.trainingMaxPercent > 0.0) {
            signals.repMaxHits.groupBy { it.liftSlot }.forEach { (lift, hits) ->
                val best = hits.maxByOrNull { it.estimatedOneRmKg } ?: return@forEach
                val tm = TrainingMaxResolver.trainingMax(profile, lift, recipe.trainingMaxPercent) ?: return@forEach
                val implied = tm / recipe.trainingMaxPercent
                val change = repMaxTmChange(best.estimatedOneRmKg, implied) ?: return@forEach
                proposals += tmProposal(lift, change, repMaxExplanation(lift, best, implied, change))
            }
        }
        // Una bajada manda sobre una subida del mismo levantamiento: `distinctBy` del final conserva la primera.
        return proposals.sortedBy { proposal -> if (proposal.isReduction()) 0 else 1 }
    }

    private fun tmProposal(lift: LiftSlot, change: TmChange, explanation: String): AutoregulationProposal =
        AutoregulationProposal(
            kind = AutoregulationProposalKind.ADJUST_TM,
            liftSlot = lift.name,
            percentDelta = change.percentDelta,
            explanation = explanation,
            kgDelta = change.kgDelta,
        )

    private fun AutoregulationProposal.isReduction(): Boolean =
        (kgDelta ?: 0.0) < 0.0 || (percentDelta ?: 0.0) < 0.0

    /**
     * AMRAP corto: menos repeticiones que el objetivo, o una repetición o menos desde el
     * [SHORT_AMRAP_SINGLE_MIN_PERCENT] % del TM (a esa intensidad una sola repetición indica un TM
     * demasiado alto aunque el objetivo fuera 1+). Un AMRAP corto nunca sube el TM.
     */
    internal fun isShortAmrap(hit: AmrapHit): Boolean =
        hit.actualReps < hit.prescribedReps ||
            (hit.prescribedReps > 0 && hit.actualReps <= 1 && (hit.percent ?: 0.0) >= SHORT_AMRAP_SINGLE_MIN_PERCENT)

    /**
     * Cambio de TM de un AMRAP según la regla de la receta, o null si no mueve el TM. Lo corto se evalúa
     * PRIMERO y nunca sube:
     * - corto con `AmrapDrivenTm`: baja [TM_DOWN_PERCENT] % (su tabla en kilos no define bajadas); con
     *   `RepTargetDrivenTm`: `missedRepPercent` por repetición que falta, solo si faltan `missThreshold` o
     *   más; con cualquier otra regla: [TM_DOWN_PERCENT] % si faltaron repeticiones.
     * - no corto con `AmrapDrivenTm`: los kilos de su tabla (0-1, 2-3, 4-5 y 6 o más repeticiones; 0 kg =
     *   nada) y solo desde [AMRAP_TM_MIN_PERCENT] % del TM; con `RepTargetDrivenTm`: `extraRepPercent` por
     *   repetición sobre el objetivo. Si se levantó menos de lo prescrito ([LIGHTER_LOAD_RATIO]) no sube.
     */
    internal fun amrapTmChange(rule: ProgressionRule, hit: AmrapHit): TmChange? {
        val extra = hit.actualReps - hit.prescribedReps
        val change: TmChange? = when {
            isShortAmrap(hit) -> when (rule) {
                is ProgressionRule.RepTargetDrivenTm ->
                    if (extra < 0 && extra <= -rule.missThreshold) {
                        TmChange(percentDelta = -(abs(extra) * abs(rule.missedRepPercent)))
                    } else {
                        null
                    }
                is ProgressionRule.AmrapDrivenTm -> TmChange(percentDelta = TM_DOWN_PERCENT)
                else -> if (extra < 0) TmChange(percentDelta = TM_DOWN_PERCENT) else null
            }
            carriedLighter(hit.weightRatio) -> null
            else -> when (rule) {
                is ProgressionRule.AmrapDrivenTm -> amrapTableChange(rule, hit)
                is ProgressionRule.RepTargetDrivenTm ->
                    if (extra > 0 && rule.extraRepPercent > 0.0) TmChange(percentDelta = extra * rule.extraRepPercent) else null
                else -> null
            }
        }
        return change?.takeIf { it.isEffective }
    }

    /** Kilos de la tabla de `AmrapDrivenTm` según las repeticiones del AMRAP; solo desde el 85 % del TM. */
    private fun amrapTableChange(rule: ProgressionRule.AmrapDrivenTm, hit: AmrapHit): TmChange? {
        val percent = hit.percent ?: return null
        if (percent < AMRAP_TM_MIN_PERCENT) return null
        val kg = when {
            hit.actualReps <= 1 -> rule.zeroToOneKg
            hit.actualReps <= 3 -> rule.twoToThreeKg
            hit.actualReps <= 5 -> rule.fourToFiveKg
            else -> rule.sixPlusKg
        }
        return if (kg > 0.0) TmChange(kgDelta = kg) else null
    }

    /**
     * Cambio de TM de un top set (`TopSetPr`): con las repeticiones del objetivo sube el incremento del
     * levantamiento (`upperKg` en banca y press militar, `lowerKg` en sentadilla y peso muerto); con
     * [TOP_SET_REPS_MARGIN] o más sobre el objetivo, el doble; con una repetición menos no cambia nada; con
     * [TOP_SET_REPS_MARGIN] o más por debajo, baja [TM_DOWN_PERCENT] %. Con menos carga que la prescrita
     * no sube.
     */
    internal fun topSetTmChange(rule: ProgressionRule.TopSetPr, hit: TopSetHit): TmChange? {
        val extra = hit.actualReps - hit.prescribedReps
        if (extra <= -TOP_SET_REPS_MARGIN) return TmChange(percentDelta = TM_DOWN_PERCENT)
        if (extra < 0 || carriedLighter(hit.weightRatio)) return null
        val increment = when (hit.liftSlot) {
            LiftSlot.BENCH, LiftSlot.OVERHEAD -> rule.upperKg
            LiftSlot.SQUAT, LiftSlot.DEADLIFT -> rule.lowerKg
        }
        if (increment <= 0.0) return null
        return TmChange(kgDelta = if (extra >= TOP_SET_REPS_MARGIN) increment * 2.0 else increment)
    }

    /**
     * Cambio de TM de una serie al máximo (`RepMaxAutoregulated`, deliberadamente mínimo y conservador): el
     * e1RM de la serie frente al 1RM que implica el TM actual (`TM ÷ trainingMaxPercent`, igual al 1RM del
     * perfil mientras el TM no se haya ajustado, y se actualiza con cada ajuste para no acumular subidas).
     * Desde [REP_MAX_RAISE_RATIO] veces ese 1RM sube el TM [TM_UP_PERCENT] %; hasta [REP_MAX_DROP_RATIO]
     * veces, baja [TM_DOWN_PERCENT] %; entre medias no cambia.
     */
    internal fun repMaxTmChange(estimatedOneRmKg: Double, impliedOneRmKg: Double): TmChange? = when {
        impliedOneRmKg <= 0.0 -> null
        estimatedOneRmKg >= impliedOneRmKg * REP_MAX_RAISE_RATIO -> TmChange(percentDelta = TM_UP_PERCENT)
        estimatedOneRmKg <= impliedOneRmKg * REP_MAX_DROP_RATIO -> TmChange(percentDelta = TM_DOWN_PERCENT)
        else -> null
    }

    // Textos para la persona: coma decimal, sin códigos internos y con el levantamiento nombrado
    // (la tarjeta de propuestas solo muestra la explicación).

    private fun amrapExplanation(hit: AmrapHit, lift: LiftSlot, change: TmChange, readiness: Int?): String {
        val percent = hit.percent?.let { " ${NativeProgressionText.formatKg(it)} %" }.orEmpty()
        val target = hit.prescribedReps.takeIf { it > 0 }?.let { " (objetivo $it+)" }.orEmpty()
        val base = "AMRAP$percent de ${AuthoredProgressionEngine.liftName(lift)}: " +
            "${SpanishPlurals.reps(hit.actualReps)}$target: TM ${changeText(change)}"
        if (!change.isReduction) return base
        val context = listOfNotNull(
            hit.meanRpe?.let { "RPE medio ${NativeProgressionText.formatKg(it)}" },
            readiness?.let { "readiness $it" },
        )
        return if (context.isEmpty()) base else "$base (${context.joinToString(", ")})"
    }

    private fun topSetExplanation(hit: TopSetHit, change: TmChange): String =
        "Top set de ${AuthoredProgressionEngine.liftName(hit.liftSlot)}: ${SpanishPlurals.reps(hit.actualReps)} " +
            "(objetivo ${hit.prescribedReps}): TM ${changeText(change)}"

    private fun repMaxExplanation(lift: LiftSlot, hit: RepMaxHit, impliedOneRmKg: Double, change: TmChange): String =
        "Serie al máximo de ${AuthoredProgressionEngine.liftName(lift)}: e1RM ${NativeProgressionText.formatKg(hit.estimatedOneRmKg)} kg " +
            "frente a los ${NativeProgressionText.formatKg(impliedOneRmKg)} kg de 1RM que implica tu TM: TM ${changeText(change)}"

    /** «+5 kg», «+2,5 kg» o «-2,5 %»: el cambio de TM con su signo y su unidad. */
    private fun changeText(change: TmChange): String {
        val kg = change.kgDelta
        if (kg != null && kg != 0.0) return "${signed(kg)} kg"
        return "${signed(change.percentDelta ?: 0.0)} %"
    }

    private fun signed(value: Double): String =
        if (value >= 0.0) "+" + NativeProgressionText.formatKg(value) else NativeProgressionText.formatKg(value)

    /**
     * Suma [kgDelta] al TM de [slot] (el guardado o, si no hay, el que sale de su 1RM y
     * [trainingMaxPercent]) y lo redondea al medio kilo sin cruzar el TM actual en sentido contrario al
     * cambio. Sin TM ni 1RM del levantamiento no hace nada. Este motor no conoce el inventario de discos.
     */
    fun applyTmKgDelta(
        profile: PowerliftingProfile,
        slot: LiftSlot,
        kgDelta: Double,
        trainingMaxPercent: Double,
    ): PowerliftingProfile {
        val current = TrainingMaxResolver.trainingMax(profile, slot, trainingMaxPercent) ?: return profile
        val rounded = floor((current + kgDelta) / KG_ROUNDING_STEP + 0.5) * KG_ROUNDING_STEP
        val next = if (kgDelta >= 0.0) maxOf(rounded, current) else minOf(rounded, current)
        return when (slot) {
            LiftSlot.SQUAT -> profile.copy(squatTM = next)
            LiftSlot.BENCH -> profile.copy(benchTM = next)
            LiftSlot.DEADLIFT -> profile.copy(deadliftTM = next)
            LiftSlot.OVERHEAD -> profile.copy(overheadTM = next)
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

    /**
     * Ids de configuración que contienen el nombre de un levantamiento sin serlo: peso muerto rumano o
     * rígido, sentadilla hack, frontal, sissy o goblet, zancadas, press inclinado o declinado, extensión
     * de tríceps por encima de la cabeza.
     */
    private val NON_MAIN_LIFT_MARKERS = listOf(
        "romanian", "stiff", "hack", "front", "sissy", "goblet", "lunge", "split",
        "good_morning", "incline", "decline", "tricep",
    )

    /**
     * Levantamiento de un id de configuración por TEXTO. Solo es el último recurso, cuando la receta no
     * declara el levantamiento del ejercicio (sin receta, ejercicio añadido o sustituido a mano) y para
     * las estimaciones de 1RM desde el historial. Los ids de [NON_MAIN_LIFT_MARKERS] no son un
     * levantamiento principal: el peso muerto rumano ya no cuenta como peso muerto ni la hack como
     * sentadilla.
     */
    internal fun liftSlotFromConfigurationId(configurationId: String?): LiftSlot? {
        val id = configurationId?.lowercase().orEmpty()
        if (id.isEmpty() || NON_MAIN_LIFT_MARKERS.any { marker -> id.contains(marker) }) return null
        return when {
            id.contains("deadlift") || id.contains("peso_muerto") -> LiftSlot.DEADLIFT
            id.contains("bench") || id.contains("banca") -> LiftSlot.BENCH
            id.contains("squat") || id.contains("sentadilla") -> LiftSlot.SQUAT
            id.contains("military") || id.contains("overhead") || id.contains("push_press") -> LiftSlot.OVERHEAD
            else -> null
        }
    }
}

/**
 * Resuelve el levantamiento de un ejercicio materializado contra su receta (`SlotRecipe.lift.liftSlot`),
 * no por el texto de su id:
 * 1. con el enlace estable (`recipeDayId` + `recipeSlotId`, solo en recetas con id de día);
 * 2. si no, por configuración y rol entre los slots de la receta; si esos slots no coinciden en el
 *    levantamiento (o ninguno lo declara) no hay levantamiento, y el hit no genera propuesta de TM;
 * 3. si la configuración no aparece en la receta (ejercicio añadido o sustituido) o no hay receta, por
 *    el texto de la configuración.
 */
private class RecipeLiftResolver(recipe: TrainingPlanRecipe?) {
    private class SlotEntry(val dayId: String?, val slot: SlotRecipe)

    private val entries: List<SlotEntry> = recipe?.weeks.orEmpty()
        .flatMap { week -> week.days.flatMap { day -> day.slots.map { slot -> SlotEntry(day.id, slot) } } }

    fun liftSlotFor(exercise: Exercise): LiftSlot? {
        val configurationId = exercise.catalogConfigurationId ?: exercise.exerciseDbId
        if (entries.isEmpty()) return ProgramAutoregulationEngine.liftSlotFromConfigurationId(configurationId)
        val dayId = exercise.recipeDayId
        val slotId = exercise.recipeSlotId
        if (dayId != null && slotId != null) {
            val linked = entries.firstOrNull { it.dayId == dayId && it.slot.id == slotId }
            if (linked != null) return linked.slot.lift.liftSlot
        }
        if (configurationId != null) {
            val candidates = entries.map { it.slot }.filter { slot ->
                slot.lift.configurationId == configurationId &&
                    (exercise.slotRole == null || slot.role == exercise.slotRole)
            }
            if (candidates.isNotEmpty()) return candidates.map { it.lift.liftSlot }.distinct().singleOrNull()
        }
        return ProgramAutoregulationEngine.liftSlotFromConfigurationId(configurationId)
    }
}
