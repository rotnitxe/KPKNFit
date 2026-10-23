package com.example.kpkn.domain.training

import com.example.kpkn.data.models.AutoregulationAuditEntry
import com.example.kpkn.data.models.AutoregulationMode
import com.example.kpkn.data.models.AutoregulationProposal
import com.example.kpkn.data.models.AutoregulationProposalKind
import com.example.kpkn.data.models.ApreXrmCapture
import com.example.kpkn.data.models.AppliedRecipeProposal
import com.example.kpkn.data.models.CalibrationScope
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.performedPurpose
import com.example.kpkn.data.models.LoadAdvisoryLevel
import com.example.kpkn.data.models.PendingActionResolutionStatus
import com.example.kpkn.data.models.PendingProgramAction
import com.example.kpkn.data.models.PendingProgramActionType
import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.SetPurpose
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.protocols.AutoregulationHookKind
import com.example.kpkn.data.protocols.LiftSlot
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.ProgressionRule
import com.example.kpkn.data.protocols.SlotRecipe
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TechniqueModifier
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.domain.auge.AugeUtils
import com.example.kpkn.domain.auge.LoadAdvisoryEngine
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import com.example.kpkn.domain.calibration.CalibrationContext
import com.example.kpkn.domain.calibration.CalibrationDecisionType
import com.example.kpkn.domain.calibration.CalibrationEngine
import com.example.kpkn.domain.calibration.CalibrationReferenceCandidates
import com.example.kpkn.domain.calibration.DeclaredCalibrationOneRm
import com.example.kpkn.domain.calibration.RecordedCalibrationSet
import com.example.kpkn.domain.text.SpanishPlurals
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

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
    /** Carga realmente levantada (kg normalizados); null si el registro no la trae. */
    val weightKg: Double? = null,
    /** Propósito PRESCRITO de la serie: `AMRAP` (rinde y alimenta la regla del programa) o `CALIBRATOR`. */
    val purpose: SetPurpose = SetPurpose.AMRAP,
    /** Alcance del calibrador prescrito; solo cuenta con `CALIBRATOR`. */
    val calibrationScope: CalibrationScope? = null,
    /** Reserva declarada al terminar la serie (RIR) y si llegó al fallo: elegibilidad del calibrador. */
    val declaredRir: Int? = null,
    val reachedFailure: Boolean = false,
    val partialReps: Int = 0,
    /** Configuración del catálogo del ejercicio: contexto exacto del calibrador. */
    val configurationId: String? = null,
    /** true si es la ÚLTIMA serie AMRAP de su ejercicio en el registro (la serie 4 de APRE); con una sola AMRAP, siempre. */
    val isLastAmrapOfExercise: Boolean = true,
    /** S4 APRE validada desde el set registrado y su identidad prescrita. */
    val apreXrmCapture: ApreXrmCapture? = null,
    /** Ejercicio de la ocurrencia real que produjo este hit; señales sin identidad son ambiguas. */
    val sourceExerciseId: String? = null,
    /** La serie pertenece a un bloque APRE real, aunque no haya datos suficientes para capturar S4. */
    val isApreBlock: Boolean = false,
    /** Carga S4 real de la calibración S3, cuando su identidad sigue vigente. */
    val apreSet4LoadKg: Double? = null,
    /** true sólo cuando la decisión S3 fue aplicada y S4 conserva esa carga. */
    val apreSet4Applied: Boolean = false,
) {
    /** true si la serie es un calibrador de «próximas sesiones»: su canal es la propuesta de TM del programa. */
    val isNextSessionsCalibrator: Boolean
        get() = purpose == SetPurpose.CALIBRATOR && calibrationScope == CalibrationScope.NEXT_SESSIONS
}

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
    /**
     * Dolor articular repetido en las últimas sesiones. Sin efecto desde DEC-w3-07: antes generaba la
     * propuesta `SWAP_TO_TECHNIQUE_VARIANT` («pasar el T1 a variante técnica»), que se retiró. El campo se
     * conserva porque quien arma las señales lo sigue rellenando; hoy ninguna propuesta lo lee.
     */
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
     * DEC-w3-07: motivo con el que caduca una propuesta `SWAP_TO_TECHNIQUE_VARIANT` guardada antes de
     * retirarla. El enum se conserva por compatibilidad del JSON: las propuestas pendientes siguen leyéndose.
     */
    internal const val TECHNIQUE_VARIANT_RETIRED_REASON =
        "La propuesta de variante técnica ya no se aplica: las variantes son configuraciones del catálogo (DEC-w3-07)"

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
        sourceCycleNumber: Int? = program.runState?.cycleNumber,
    ): List<AutoregulationProposal> {
        if (program.autoregulationMode == AutoregulationMode.OFF) return emptyList()
        val cycle = sourceCycleNumber
        val runId = program.runState?.runId
        val rule = recipe.progression
        val observedHits = collectAmrapHits(
            completedWeek, logs, cycle, program.id, runId, recipe,
            sourceProtocolId = program.sourceProtocolId ?: recipe.id,
            sourceProgramId = program.id,
        )
        // Las capturas se derivan siempre del set guardado; un snapshot transportado en señales puede estar obsoleto.
        val observedApreHits = observedHits.filter { it.isApreBlock }
        val safeSignalHits = signals.amrapHits.filterNot { shadowsObservedApre(it, observedApreHits) }
            .map { it.copy(apreXrmCapture = null, isApreBlock = false) }
        val hits = if (signals.amrapHits.isEmpty()) observedHits else safeSignalHits + observedApreHits
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

    /** Canal APRE cuando no corresponde evaluar otras reglas; sólo lee S4 con bloque APRE real. */
    fun evaluateApreXrmOnly(
        program: Program,
        completedWeek: ProgramWeek,
        logs: List<WorkoutLog>,
        sourceCycleNumber: Int? = program.runState?.cycleNumber,
        recipe: TrainingPlanRecipe? = null,
        sourceProtocolId: String? = program.sourceProtocolId ?: recipe?.id,
    ): List<AutoregulationProposal> {
        if (program.autoregulationMode == AutoregulationMode.OFF) return emptyList()
        val hits = collectAmrapHits(
            week = completedWeek,
            logs = logs,
            cycleNumber = sourceCycleNumber,
            programId = program.id,
            runId = program.runState?.runId,
            recipe = recipe,
            sourceProtocolId = sourceProtocolId,
            sourceProgramId = program.id,
        )
        return hits.mapNotNull { apreProposal(it, program.id) }
            .distinctBy { proposal ->
                val capture = proposal.apreXrm!!
                listOf(
                    capture.reference.sourceProgramId,
                    capture.sourceExerciseId,
                    capture.sourcePlannedSetId,
                    capture.reference.metadata?.sourceId,
                )
            }
    }

    private fun validXrmProposalForProgram(proposal: AutoregulationProposal, programId: String): Boolean {
        if (proposal.kind != AutoregulationProposalKind.ADJUST_XRM) return true
        val capture = proposal.apreXrm ?: return false
        return validApreXrmCapture(capture) &&
            (capture.reference.sourceProgramId == null || capture.reference.sourceProgramId == programId)
    }

    private fun filterNewProposals(
        program: Program,
        targetWeekId: String?,
        proposals: List<AutoregulationProposal>,
    ): List<AutoregulationProposal> {
        val pending = program.runState?.pendingAction?.takeIf {
            it.type == PendingProgramActionType.CONFIRM_AUTOREGULATION && it.targetWeekId == targetWeekId
        }
        return proposals.filterNot { proposal ->
            if (proposal.kind == AutoregulationProposalKind.ADJUST_XRM) {
                val capture = proposal.apreXrm ?: return@filterNot false
                ApreXrmProgression.hasSameCapture(program, capture) || pending?.proposals.orEmpty().any {
                    sameXrmProposal(it, proposal)
                }
            } else {
                pending?.proposals.orEmpty().contains(proposal)
            }
        }
    }

    /** Conserva el pendiente y payloads diferentes; una corrección reemplaza solo el mismo evento APRE. */
    private fun mergePendingProposals(
        existing: List<AutoregulationProposal>,
        incoming: List<AutoregulationProposal>,
    ): List<AutoregulationProposal> {
        val merged = existing.toMutableList()
        incoming.forEach { proposal ->
            if (proposal.kind == AutoregulationProposalKind.ADJUST_XRM) {
                val sourceId = proposal.apreXrm?.reference?.metadata?.sourceId
                val sourceExerciseId = proposal.apreXrm?.sourceExerciseId
                val sameEvent = merged.filter { it.kind == proposal.kind &&
                    it.apreXrm?.sourceExerciseId == sourceExerciseId &&
                    it.apreXrm?.reference?.metadata?.sourceId == sourceId }
                if (sameEvent.any { sameXrmProposal(it, proposal) }) return@forEach
                if (sourceId != null) merged.removeAll(sameEvent.toSet())
            } else if (proposal in merged) {
                return@forEach
            }
            merged += proposal
        }
        return merged
    }

    private fun pendingMessage(proposals: List<AutoregulationProposal>): String =
        proposals.joinToString(" · ") { it.explanation }.ifBlank { "AUGE propone ajustes para la próxima semana." }

    /** Las señales sin identidad no pueden volver a introducir como TM una serie APRE del registro real. */
    private fun shadowsObservedApre(signal: AmrapHit, observedApre: List<AmrapHit>): Boolean {
        if (signal.isApreBlock || signal.apreXrmCapture != null) return true
        if (observedApre.isEmpty()) return false
        signal.sourceExerciseId?.let { id ->
            return observedApre.any { it.sourceExerciseId == id }
        }
        signal.configurationId?.takeIf { it.isNotBlank() }?.let { configuration ->
            return observedApre.any { it.configurationId == configuration }
        }
        // Sin identidad, el mismo levantamiento es ambiguo; por seguridad, prevalece el registro APRE.
        return observedApre.any { it.liftSlot == signal.liftSlot }
    }

    private fun sameXrmProposal(first: AutoregulationProposal, second: AutoregulationProposal): Boolean {
        val a = first.apreXrm ?: return false
        val b = second.apreXrm ?: return false
        if (first.kind != AutoregulationProposalKind.ADJUST_XRM || second.kind != first.kind) return false
        return a.copy(reference = a.reference.copy(capturedAtMs = null,
            metadata = a.reference.metadata?.copy(revision = 0))) ==
            b.copy(reference = b.reference.copy(capturedAtMs = null,
                metadata = b.reference.metadata?.copy(revision = 0)))
    }
    fun apply(
        program: Program,
        nextWeekId: String?,
        proposals: List<AutoregulationProposal>,
        recipe: TrainingPlanRecipe?,
        executedWeekIds: Set<String>,
        metadata: ExerciseCompositionMetadataProvider? = null,
        nowMs: Long = System.currentTimeMillis(),
        /** Evidencia a nivel de sesión (§14.5): sesiones iniciadas/registradas del objetivo. */
        executedSessionIds: Set<String> = emptySet(),
    ): AutoregulationEvaluation {
        if (proposals.isEmpty() || program.autoregulationMode == AutoregulationMode.OFF) {
            return AutoregulationEvaluation(proposals = emptyList(), program = program)
        }
        val existingAction = program.runState?.pendingAction
        if (existingAction != null && existingAction.type != PendingProgramActionType.CONFIRM_AUTOREGULATION) {
            return AutoregulationEvaluation(proposals = emptyList(), program = program)
        }
        val pending = existingAction?.takeIf { it.type == PendingProgramActionType.CONFIRM_AUTOREGULATION }
        if (pending != null && pending.targetWeekId != nextWeekId) {
            return AutoregulationEvaluation(proposals = emptyList(), program = program)
        }
        val invalidXrm = proposals.filter { it.kind == AutoregulationProposalKind.ADJUST_XRM &&
            !validXrmProposalForProgram(it, program.id) }
        val candidates = proposals.filterNot { it in invalidXrm }
        val newProposals = filterNewProposals(program, nextWeekId, candidates)
        val invalidEntries = invalidXrm.map { proposal ->
            resolutionEntry(proposal, PendingActionResolutionStatus.EXPIRED,
                "No aplicada: la captura XRM no es válida para este programa.", nextWeekId, program.autoregulationMode, nowMs)
        }
        if (newProposals.isEmpty() && invalidEntries.isEmpty()) {
            return AutoregulationEvaluation(proposals = emptyList(), program = program)
        }
        val generationAudit = if (newProposals.isEmpty()) emptyList() else listOf(AutoregulationAuditEntry(
            atMs = nowMs,
            mode = program.autoregulationMode,
            kinds = newProposals.map { it.kind }.distinct(),
            weekId = nextWeekId,
        ))
        val xrm = newProposals.filter { it.kind == AutoregulationProposalKind.ADJUST_XRM }
        val regular = newProposals.filterNot { it.kind == AutoregulationProposalKind.ADJUST_XRM }
        return when (program.autoregulationMode) {
            AutoregulationMode.PROPOSE -> {
                if (newProposals.isEmpty()) {
                    val run = (program.runState ?: com.example.kpkn.data.models.ProgramRunState(
                        runId = ProgramProgressEngine.newRunId())).copy(
                        autoregulationAudit = program.runState?.autoregulationAudit.orEmpty() + invalidEntries,
                    )
                    return AutoregulationEvaluation(emptyList(), program.copy(runState = run))
                }
                val merged = mergePendingProposals(pending?.proposals.orEmpty(), newProposals)
                val message = merged.joinToString(" · ") { it.explanation }
                    .ifBlank { "AUGE propone ajustes para la próxima semana." }
                val pendingAction = PendingProgramAction(
                    type = PendingProgramActionType.CONFIRM_AUTOREGULATION,
                    message = message,
                    proposals = merged,
                    targetWeekId = nextWeekId,
                )
                val run = (program.runState ?: com.example.kpkn.data.models.ProgramRunState(runId = ProgramProgressEngine.newRunId()))
                    .copy(pendingAction = pendingAction, autoregulationAudit = program.runState?.autoregulationAudit.orEmpty() + generationAudit + invalidEntries)
                AutoregulationEvaluation(newProposals, program.copy(runState = run), applied = false)
            }
            AutoregulationMode.AUTO -> {
                val xrmResult = ApreXrmProgression.apply(program, nextWeekId, xrm, executedWeekIds, executedSessionIds)
                val provider = if (regular.isEmpty()) null else metadata ?: runCatching { CompositionMetadataHolder.resolve() }.getOrNull()
                if (regular.isNotEmpty() && (provider == null || recipe == null)) {
                    val merged = mergePendingProposals(pending?.proposals.orEmpty(), regular)
                    val message = merged.joinToString(" · ") { it.explanation }
                        .ifBlank { "AUGE propone ajustes para la próxima semana." }
                    val pendingAction = PendingProgramAction(
                        type = PendingProgramActionType.CONFIRM_AUTOREGULATION,
                        message = message,
                        proposals = merged,
                        targetWeekId = nextWeekId,
                    )
                    val xrmOutcomes = outcomeEntries(program, xrmResult.program, xrm, nextWeekId,
                        executedWeekIds, program.autoregulationMode, nowMs, xrmResult)
                    val run = (xrmResult.program.runState ?: com.example.kpkn.data.models.ProgramRunState(
                        runId = ProgramProgressEngine.newRunId())).copy(
                        pendingAction = pendingAction,
                        autoregulationAudit = program.runState?.autoregulationAudit.orEmpty() + generationAudit + xrmOutcomes + invalidEntries,
                    )
                    return AutoregulationEvaluation(newProposals, xrmResult.program.copy(runState = run),
                        applied = xrmOutcomes.any { it.resolution == PendingActionResolutionStatus.APPLIED })
                }
                var mutated = xrmResult.program
                if (regular.isNotEmpty()) {
                    mutated = applyMutations(
                        program = mutated,
                        nextWeekId = nextWeekId,
                        proposals = regular,
                        recipe = recipe!!,
                        executedWeekIds = executedWeekIds,
                        metadata = provider!!,
                        executedSessionIds = executedSessionIds,
                        nowMs = nowMs,
                    )
                }
                val outcomes = outcomeEntries(program, mutated, newProposals, nextWeekId,
                    executedWeekIds, program.autoregulationMode, nowMs, xrmResult)
                val baseRun = mutated.runState
                    ?: com.example.kpkn.data.models.ProgramRunState(runId = ProgramProgressEngine.newRunId())
                val run = baseRun.copy(autoregulationAudit = program.runState?.autoregulationAudit.orEmpty() + generationAudit + outcomes + invalidEntries)
                AutoregulationEvaluation(newProposals, mutated.copy(runState = run),
                    applied = outcomes.any { it.resolution == PendingActionResolutionStatus.APPLIED })
            }
            AutoregulationMode.OFF -> AutoregulationEvaluation(emptyList(), program)
        }
    }

    /** Acepta/rechaza incluso parcialmente; ADJUST_XRM no depende de receta ni de un proveedor de catálogo. */
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
        if (resolved.isEmpty()) return program
        val remaining = if (only == null) emptyList() else action.proposals.filterNot { it == only }
        val nextPending = if (remaining.isEmpty()) null else action.copy(
            proposals = remaining,
            message = pendingMessage(remaining),
        )

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

        val evidence = executedWeekIds ?: derivedExecutedWeekIds(program)
        val xrm = resolved.filter { it.kind == AutoregulationProposalKind.ADJUST_XRM }
        val regular = resolved.filterNot { it.kind == AutoregulationProposalKind.ADJUST_XRM }
        val validXrm = xrm.filter { validXrmProposalForProgram(it, program.id) }
        val base = program.copy(runState = run.copy(pendingAction = nextPending))
        val xrmResult = ApreXrmProgression.apply(base, action.targetWeekId, validXrm, evidence, executedSessionIds)
        var mutated = xrmResult.program
        val entries = mutableListOf<AutoregulationAuditEntry>()
        entries += outcomeEntries(program, mutated, validXrm, action.targetWeekId, evidence,
            program.autoregulationMode, nowMs, xrmResult)
        xrm.filterNot { it in validXrm }.forEach { proposal ->
            entries += resolutionEntry(proposal, PendingActionResolutionStatus.EXPIRED,
                "No aplicada: la captura XRM no es válida para este programa.", action.targetWeekId, program.autoregulationMode, nowMs)
        }
        if (regular.isNotEmpty()) {
            val recipe = program.sourceRecipe
            val provider = metadata ?: runCatching { CompositionMetadataHolder.resolve() }.getOrNull()
            when {
                recipe == null -> regular.forEach { proposal ->
                    entries += resolutionEntry(proposal, PendingActionResolutionStatus.EXPIRED,
                        "No aplicada: el programa no tiene receta fuente para este ajuste.", action.targetWeekId, program.autoregulationMode, nowMs)
                }
                provider == null -> regular.forEach { proposal ->
                    entries += resolutionEntry(proposal, PendingActionResolutionStatus.EXPIRED,
                        "No aplicada: sin metadatos de composición del catálogo.", action.targetWeekId, program.autoregulationMode, nowMs)
                }
                else -> {
                    mutated = applyMutations(
                        program = mutated,
                        nextWeekId = action.targetWeekId,
                        proposals = regular,
                        recipe = recipe,
                        executedWeekIds = evidence,
                        metadata = provider,
                        executedSessionIds = executedSessionIds,
                        nowMs = nowMs,
                    )
                    entries += outcomeEntries(program, mutated, regular, action.targetWeekId, evidence,
                        program.autoregulationMode, nowMs)
                }
            }
        }
        return mutated.withResolutionEntries(pending = mutated.runState?.pendingAction ?: nextPending, entries = entries)
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
        apreResult: ApreXrmApplicationResult? = null,
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
        val hasPercent = percentsBefore.any { it != null }
        val trainedReason = "No aplicada: la semana objetivo ya tiene sesiones entrenadas; su prescripción se conserva (§14.5)."
        return proposals.map { proposal ->
            val (status, reason) = when (proposal.kind) {
                AutoregulationProposalKind.ADJUST_XRM -> {
                    val capture = proposal.apreXrm
                    val sourceId = capture?.reference?.metadata?.sourceId
                    when {
                        capture == null || !validXrmProposalForProgram(proposal, before.id) ->
                            PendingActionResolutionStatus.EXPIRED to "No aplicada: la captura XRM no es válida para este programa."
                        sourceId != null && apreResult?.changedSourceIds.orEmpty().contains(sourceId) ->
                            PendingActionResolutionStatus.APPLIED to "Cargas APRE actualizadas: ${proposal.explanation}"
                        sourceId != null && apreResult?.savedSourceIds.orEmpty().contains(sourceId) ->
                            PendingActionResolutionStatus.APPLIED to "Referencia XRM guardada; no se modificaron series."
                        protectedTarget -> PendingActionResolutionStatus.EXPIRED to trainedReason
                        else -> PendingActionResolutionStatus.EXPIRED to
                            "No aplicada: no se modificaron series ni se guardó una referencia XRM nueva."
                    }
                }

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
                    hasPercent && percentsBefore != percentsAfter ->
                        PendingActionResolutionStatus.APPLIED to "Aplicada: ${proposal.explanation}"
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

                // DEC-w3-07: la propuesta de variante técnica se retiró y el enum se conserva solo para leer
                // las guardadas. Nunca se aplica, ni con la semana objetivo sin entrenar: caduca con motivo.
                AutoregulationProposalKind.SWAP_TO_TECHNIQUE_VARIANT ->
                    PendingActionResolutionStatus.EXPIRED to TECHNIQUE_VARIANT_RETIRED_REASON
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
        sourceProtocolId: String? = null,
        sourceProgramId: String? = programId,
    ): List<AmrapHit> {
        val weekLogs = weekLogsOf(week, logs, cycleNumber, programId, runId)
        if (weekLogs.isEmpty()) return emptyList()
        val lifts = RecipeLiftResolver(recipe)
        val sessionVariants = week.sessions.flatMap { it.withVariants() }
        val weekSessionIds = sessionVariants.mapTo(mutableSetOf()) { it.id }
        val out = mutableListOf<AmrapHit>()
        sessionVariants.forEach { session ->
            // WorkoutFinishController conserva el sessionId exterior A aunque el usuario
            // haya terminado B/C/D. Solo asociamos ese contenedor si CompletedExercise
            // identifica sin ambigüedad un ejercicio de la variante.
            val sessionLogs = logsOfSession(weekLogs, session, weekSessionIds, sessionVariants)
            session.allExercises().forEach { exercise ->
                val hasApreProgression = recipe?.progression is ProgressionRule.ApreXrm
                val isApreBlock = exercise.apreBlock != null ||
                    resolveLiveApreBlock(exercise, sourceProtocolId, hasApreProgression) != null
                val exerciseLogs = if (isApreBlock) {
                    sessionLogs.filter { log -> hasUnambiguousVariantExercise(log, exercise, sessionVariants) }
                } else sessionLogs
                val amrapPairs = pairedSets(
                    exercise, exerciseLogs,
                    isTarget = { it.purpose.isAmrap },
                    isFlagged = { it.performedPurpose()?.isAmrap == true },
                    requireExactExerciseId = isApreBlock,
                )
                amrapPairs.forEachIndexed { pairIndex, pair ->
                        val prescribedIndex = exercise.sets.indexOfFirst { it.id == pair.prescribed.id }
                        val storedCapture = pair.performed.apreXrm
                        val plannedContext = plannedApreContext(exercise, pair.prescribed, pair.performed.side, pair.performed)
                        val storedSourceMatches = storedCapture?.reference?.sourceProgramId
                            ?.let { it == sourceProgramId } ?: true
                        val currentBlock = resolveLiveApreBlock(exercise, sourceProtocolId, hasApreProgression)
                        val storedContextMatches = storedCapture != null && storedCapture.context == plannedContext &&
                            storedCapture.reference.configurationId == plannedContext.configurationId &&
                            storedCapture.reference.quantityConvention == plannedContext.loadQuantityConvention &&
                            storedCapture.reference.side == pair.performed.side &&
                            storedCapture.reference.repMin == currentBlock?.variant?.xrmReps &&
                            storedCapture.reference.repMax == currentBlock?.variant?.xrmReps &&
                            storedCapture.reference.metadata?.contextKey == plannedContext.key &&
                            (storedCapture.reference.sourceSlotId == null || exercise.recipeSlotId == null ||
                                storedCapture.reference.sourceSlotId == exercise.recipeSlotId) &&
                            (storedCapture.sourceRecipeDayId == null || exercise.recipeDayId == null ||
                                storedCapture.sourceRecipeDayId == exercise.recipeDayId) &&
                            (storedCapture.sourceRecipeSlotId == null || exercise.recipeSlotId == null ||
                                storedCapture.sourceRecipeSlotId == exercise.recipeSlotId)
                        val recordedContextMatches = pair.performed.contextKey?.let { it == plannedContext.key } ?: true
                        val legacyPurposeFromPlan = pair.performed.performedPurpose() == null
                        val completedForCapture = if (legacyPurposeFromPlan) {
                            pair.performed.copy(purpose = pair.prescribed.purpose)
                        } else pair.performed
                        val regeneratedStoredCapture = storedCapture?.takeIf {
                            storedSourceMatches && storedContextMatches && prescribedIndex >= 0 &&
                                pair.performed.setIndex?.let { it != prescribedIndex } != true
                        }?.let {
                            captureApreXrm(
                                exercise = exercise,
                                index = prescribedIndex,
                                completed = completedForCapture,
                                sourceProtocolId = sourceProtocolId,
                                hasApreProgression = hasApreProgression,
                                incrementKg = it.incrementKg,
                                capturedAtMs = it.reference.capturedAtMs,
                                sourceProgramId = it.reference.sourceProgramId,
                                contextOverride = plannedContext,
                            )
                        }
                        val storedPayloadMatches = storedCapture == null || regeneratedStoredCapture == storedCapture
                        val storedIdentityMatches = storedCapture != null && storedSourceMatches &&
                            storedContextMatches && storedPayloadMatches &&
                            validApreXrmCapture(storedCapture) && storedCapture.sourceExerciseId == exercise.id &&
                            storedCapture.sourcePlannedSetId == pair.prescribed.id &&
                            storedCapture.reference.metadata?.sourceId == pair.performed.id
                        // Un snapshot o contextKey contradictorio es evidencia de otra variante/contexto;
                        // no se recaptura con la configuración actual para "convertirlo" en una captura nueva.
                        val captureIdentityConflicts = (storedCapture != null && !storedIdentityMatches) || !recordedContextMatches
                        // Si existe un snapshot real y compatible, su incremento de material manda sobre la regla;
                        // sólo los registros sin snapshot usan el incremento de receta.
                        val incrementKg = storedCapture?.takeIf { storedIdentityMatches }?.incrementKg
                            ?: (recipe?.progression as? ProgressionRule.ApreXrm)?.incrementKg
                            ?: Apre.DEFAULT_INCREMENT_KG
                        val apreCapture = if (captureIdentityConflicts || !storedSourceMatches || prescribedIndex < 0 ||
                            pair.performed.setIndex?.let { it != prescribedIndex } == true) null else
                            captureApreXrm(
                                exercise = exercise,
                                index = prescribedIndex,
                                completed = completedForCapture,
                                sourceProtocolId = sourceProtocolId,
                                hasApreProgression = hasApreProgression,
                                incrementKg = incrementKg,
                                capturedAtMs = storedCapture?.takeIf { storedIdentityMatches }?.reference?.capturedAtMs,
                                sourceProgramId = sourceProgramId,
                                contextOverride = plannedContext,
                            )
                        out += AmrapHit(
                            liftSlot = lifts.liftSlotFor(exercise),
                            percent = pair.prescribed.targetPercentageRM,
                            prescribedReps = (if (pair.performed.purpose != null || pair.performed.recordedPayloadV3?.purpose != null) {
                                pair.performed.amrapMinimumReps
                            } else pair.performed.amrapMinimumReps ?: pair.prescribed.amrapMinimumReps) ?: 0,
                            actualReps = pair.performed.reps,
                            meanRpe = pair.performed.rpe ?: pair.prescribed.targetRPE,
                            weightRatio = weightRatio(pair.prescribed, pair.performed),
                            weightKg = pair.performed.weight.takeIf { it > 0.0 },
                            purpose = if (pair.prescribed.purpose == SetPurpose.CALIBRATOR) SetPurpose.CALIBRATOR else SetPurpose.AMRAP,
                            calibrationScope = pair.prescribed.calibrationScope.takeIf { pair.prescribed.purpose == SetPurpose.CALIBRATOR },
                            declaredRir = pair.performed.rir ?: pair.performed.rpe?.let { rpe -> (10.0 - rpe).roundToInt().coerceAtLeast(0) },
                            reachedFailure = pair.performed.isFailure,
                            partialReps = (pair.performed.partialReps ?: 0).coerceAtLeast(0),
                            configurationId = exercise.catalogConfigurationId ?: exercise.exerciseDbId,
                            isLastAmrapOfExercise = pairIndex == amrapPairs.lastIndex,
                            apreXrmCapture = apreCapture,
                            sourceExerciseId = exercise.id,
                            isApreBlock = isApreBlock,
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
     * set sin porcentaje), series AMRAP con porcentaje (el RM semanal de J&T 2.0, CAL5) y el single del test
     * al 100 % del 1RM, del levantamiento de COMPETICIÓN de la receta (`recipe.liftSlots`). Las
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
                // CAL5 (J&T 2.0): el RM semanal del T1 es una serie AMRAP con porcentaje («1×10+ al 77,5 %»), no una serie con base
                // REP_MAX ni un top set sin porcentaje: por eso el gancho nunca se disparaba. Cuenta también el single del test al 100 %.
                val isRepMaxSet = { set: ExerciseSet ->
                    set.loadBasis == LoadBasis.REP_MAX ||
                        (set.isTopSet && set.targetPercentageRM == null) ||
                        isMaxRepsAtPercent(set) ||
                        isOneRmTestSet(set)
                }
                val positional = pairedSets(exercise, sessionLogs, isTarget = isRepMaxSet, isFlagged = null)
                val flagged = pairedSets(
                    exercise, sessionLogs,
                    isTarget = { it.purpose.isAmrap },
                    isFlagged = { it.performedPurpose()?.isAmrap == true },
                )
                (positional + flagged)
                    .distinctBy { it.performed.id }
                    .forEach { pair ->
                        val reps = pair.performed.reps
                        val weight = pair.performed.weight
                        if (reps !in 1..REP_MAX_MAX_REPS || weight <= 0.0) return@forEach
                        out += RepMaxHit(
                            liftSlot = lift,
                            weightKg = weight,
                            reps = reps,
                            estimatedOneRmKg = calculateHybrid1RM(weight, reps),
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

    /** Una serie al máximo de repeticiones con un porcentaje prescrito (el RM semanal de J&T): `AMRAP` y `%`, sin ser un calibrador de sesión. */
    internal fun isMaxRepsAtPercent(set: ExerciseSet): Boolean =
        set.purpose.isAmrap && set.targetPercentageRM != null && set.loadBasis != LoadBasis.RPE

    /** El single del test de 1RM: un top set de 1 repetición al 100 % o más del 1RM (`PERCENT_1RM`). */
    internal fun isOneRmTestSet(set: ExerciseSet): Boolean =
        set.isTopSet && set.loadBasis == LoadBasis.PERCENT_1RM &&
            (set.targetPercentageRM ?: 0.0) >= 100.0 &&
            (set.targetReps ?: set.targetRepsRange?.min ?: 1) <= 1

    /**
     * CAL5 · 5/3/1: ciclos SEGUIDOS, contando el que se cierra ([closingCycle]), en los que cada levantamiento tuvo un AMRAP
     * corto (no alcanzó las repeticiones mínimas). Solo salen los levantamientos congelados en el ciclo que se cierra. El
     * reinicio del TM llega cada [CycleIncrement.resetAfterFrozenCycles] ciclos congelados seguidos: con 2, en la racha 2, 4,
     * 6… (tras un reinicio la cuenta vuelve a empezar). Lee los registros de cada ciclo: no guarda estado.
     */
    internal fun frozenCycleStreaks(
        weeks: List<ProgramWeek>,
        logs: List<WorkoutLog>,
        closingCycle: Int,
        programId: String,
        runId: String?,
        recipe: TrainingPlanRecipe,
    ): Map<LiftSlot, Int> {
        val streaks = mutableMapOf<LiftSlot, Int>()
        var alive: Set<LiftSlot>? = null
        var cycle = closingCycle
        while (cycle >= 1) {
            val frozen = shortAmrapLifts(weeks, logs, cycle, programId, runId, recipe)
            val current = alive?.intersect(frozen) ?: frozen
            if (current.isEmpty()) break
            current.forEach { lift -> streaks[lift] = (streaks[lift] ?: 0) + 1 }
            alive = current
            cycle--
        }
        return streaks
    }

    /**
     * Resultado de un T1, T2 o T3 de GZCLP en una semana: repeticiones prescritas (volumen base de la etapa: 15, 12 o 10 en el
     * T1; 30, 24 o 18 en el T2) frente a las hechas, y las de la última serie (el T3 sube cuando esa serie llega a 25). El T3 no
     * declara levantamiento ([liftSlot] null).
     */
    data class GzclpTierCheck(
        val liftSlot: LiftSlot?,
        val role: SlotRole,
        val plannedReps: Int,
        val doneReps: Int,
        val lastSetReps: Int?,
        val exerciseName: String? = null,
    ) {
        val completedBaseVolume: Boolean get() = doneReps >= plannedReps
    }

    /**
     * CAL5 · GZCLP: por cada sesión de [weeks] con un T1 o un T2 de levantamiento conocido (y cada T3), el volumen hecho frente
     * al prescrito en el registro más reciente de la sesión. «No completar el volumen base» (Lefever) es quedarse por debajo de
     * las repeticiones prescritas del slot; un ejercicio sin registro no da dato.
     */
    internal fun collectGzclpTierChecks(
        weeks: List<ProgramWeek>,
        logs: List<WorkoutLog>,
        cycleNumber: Int,
        programId: String,
        runId: String?,
        recipe: TrainingPlanRecipe,
    ): List<GzclpTierCheck> {
        val lifts = RecipeLiftResolver(recipe)
        val out = mutableListOf<GzclpTierCheck>()
        weeks.forEach { week ->
            val weekLogs = weekLogsOf(week, logs, cycleNumber, programId, runId)
            if (weekLogs.isEmpty()) return@forEach
            val weekSessionIds = week.sessions.mapTo(mutableSetOf()) { it.id }
            week.sessions.forEach { session ->
                val sessionLogs = logsOfSession(weekLogs, session, weekSessionIds)
                session.allExercises().forEach { exercise ->
                    val role = exercise.slotRole
                    if (role != SlotRole.T1_MAIN && role != SlotRole.T2_SUPPLEMENTAL && role != SlotRole.T3_ACCESSORY) return@forEach
                    val lift = lifts.liftSlotFor(exercise)
                    if (role != SlotRole.T3_ACCESSORY && lift == null) return@forEach
                    val plannedReps = exercise.sets.filterNot { it.isEmptySlot }
                        .sumOf { set -> set.targetReps ?: set.targetRepsRange?.min ?: 0 }
                    if (plannedReps <= 0) return@forEach
                    val done = sessionLogs.firstNotNullOfOrNull { log ->
                        loggedExercisesOf(log, exercise).flatMap { it.sets }
                            .filter { !it.isWarmup && !it.skipped }
                            .takeIf { it.isNotEmpty() }
                    } ?: return@forEach
                    out += GzclpTierCheck(
                        liftSlot = lift,
                        role = role,
                        plannedReps = plannedReps,
                        doneReps = done.sumOf { it.reps },
                        lastSetReps = done.last().reps,
                        exerciseName = exercise.name,
                    )
                }
            }
        }
        return out
    }

    /** El single de un test de 1RM realizado: levantamiento, carga y repeticiones (CAL5, precarga del test de bloques). */
    data class OneRmTestHit(val liftSlot: LiftSlot, val weightKg: Double, val reps: Int)

    /**
     * CAL5: las series «Test» de [weeks] ([isOneRmTestSet]: el single al 100 % del 1RM de un top set) realizadas en los
     * registros, para precargar el test de 1RM al cerrar el bloque. Solo cuentan las series con repeticiones y carga y las
     * no marcadas como fallidas. Sin [cycleNumber] y [programId] se leen todos los registros de la semana plantilla.
     */
    fun collectOneRmTestHits(
        weeks: List<ProgramWeek>,
        logs: List<WorkoutLog>,
        recipe: TrainingPlanRecipe?,
        cycleNumber: Int? = null,
        programId: String? = null,
        runId: String? = null,
    ): List<OneRmTestHit> {
        val lifts = RecipeLiftResolver(recipe)
        val out = mutableListOf<OneRmTestHit>()
        weeks.forEach { week ->
            val weekLogs = weekLogsOf(week, logs, cycleNumber, programId, runId)
            if (weekLogs.isEmpty()) return@forEach
            val weekSessionIds = week.sessions.mapTo(mutableSetOf()) { it.id }
            week.sessions.forEach { session ->
                val sessionLogs = logsOfSession(weekLogs, session, weekSessionIds)
                session.allExercises().forEach { exercise ->
                    val lift = lifts.liftSlotFor(exercise) ?: return@forEach
                    pairedSets(exercise, sessionLogs, isTarget = { isOneRmTestSet(it) }, isFlagged = null).forEach { pair ->
                        val performed = pair.performed
                        if (performed.reps < 1 || performed.weight <= 0.0 || performed.isFailedSet) return@forEach
                        out += OneRmTestHit(lift, performed.weight, performed.reps)
                    }
                }
            }
        }
        return out
    }

    /** Un par serie prescrita / serie registrada del mismo ejercicio. */
    private class SetPair(val prescribed: ExerciseSet, val performed: CompletedSet)

    /**
     * Registros de la semana [week]. Con ciclo y programa solo los de esa ocurrencia
     * (`ProgramProgressEngine.logsForInstance`: ciclo, run y semana); sin ellos, todos los de la semana
     * plantilla, como antes (mezcla ciclos).
     */
    fun collectExecutedSessionIds(
        week: ProgramWeek,
        logs: List<WorkoutLog>,
        cycleNumber: Int?,
        programId: String?,
        runId: String?,
    ): Set<String> {
        val sessionIds = week.sessions.flatMap { it.withVariants() }.mapTo(linkedSetOf()) { it.id }
        return weekLogsOf(week, logs, cycleNumber, programId, runId)
            .map { it.sessionId }.filterTo(linkedSetOf()) { it in sessionIds }
    }

    /** Sesiones A/B/C/D configuradas en una misma ocurrencia semanal. */
    private fun Session.withVariants(): List<Session> = buildList {
        add(this@withVariants)
        sessionB?.let { addAll(it.withVariants()) }
        sessionC?.let { addAll(it.withVariants()) }
        sessionD?.let { addAll(it.withVariants()) }
    }

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
        sessionVariants: List<Session> = emptyList(),
    ): List<WorkoutLog> = weekLogs
        .filter { log ->
            log.sessionId == session.id || log.sessionId !in weekSessionIds ||
                (sessionVariants.isNotEmpty() && hasUnambiguousVariantExercise(log, session, sessionVariants))
        }
        .sortedByDescending { AugeUtils.logDateMs(it) }

    /** Asocia un log de A a B/C/D solo con identidad única o snapshot/contexto exacto. */
    private fun hasUnambiguousVariantExercise(
        log: WorkoutLog,
        session: Session,
        sessionVariants: List<Session>,
    ): Boolean = session.allExercises().any { planned ->
        hasUnambiguousVariantExercise(log, planned, sessionVariants)
    }

    private fun hasUnambiguousVariantExercise(
        log: WorkoutLog,
        planned: Exercise,
        sessionVariants: List<Session>,
    ): Boolean =
        log.completedExercises.any { completed ->
            completed.exerciseId == planned.id && !hasContradictoryExerciseIdentity(completed, planned) &&
                run {
                    val sameIdentityCandidates = sessionVariants.flatMap { it.allExercises() }.filter { candidate ->
                        candidate.id == completed.exerciseId && !hasContradictoryExerciseIdentity(completed, candidate)
                    }.distinct()
                    sameIdentityCandidates.size == 1 ||
                        completedPinsVariant(completed, planned, sameIdentityCandidates)
                }
        }

    /** Si la identidad se reutiliza entre variantes, exige una captura/contexto que seleccione una sola. */
    private fun completedPinsVariant(
        completed: CompletedExercise,
        planned: Exercise,
        candidates: List<Exercise>,
    ): Boolean {
        val captureMatches = candidates.filter { candidate ->
            completed.sets.any { performed ->
                val capture = performed.apreXrm
                val capturedSet = capture?.let { candidate.sets.firstOrNull { set -> set.id == it.sourcePlannedSetId } }
                val exactCapture = capture != null && capturedSet != null && validApreXrmCapture(capture) &&
                    capture.sourceExerciseId == candidate.id &&
                    capture.sourcePlannedSetId == capturedSet.id &&
                    capture.reference.metadata?.sourceId == performed.id &&
                    capture.sourceRecipeDayId == candidate.recipeDayId &&
                    capture.sourceRecipeSlotId == candidate.recipeSlotId &&
                    capture.context == plannedApreContext(candidate, capturedSet, performed.side, performed)
                val exactContext = performed.contextKey != null && performed.setIndex != null &&
                    candidate.sets.getOrNull(performed.setIndex)?.let { prescribed ->
                        performed.contextKey == plannedApreContext(candidate, prescribed, performed.side, performed).key
                    } == true
                exactCapture || exactContext
            }
        }
        return captureMatches.singleOrNull() == planned
    }

    /** Los ejercicios registrados que corresponden a [exercise]: por id; si no, por configuración. */
    private fun loggedExercisesOf(log: WorkoutLog, exercise: Exercise): List<CompletedExercise> {
        val byId = log.completedExercises.filter { it.exerciseId == exercise.id }
        // Un ID de ocurrencia puede sobrevivir a un reemplazo de variante. Si el log
        // y el plan conservan configuraciones conocidas distintas, el ID no autoriza
        // a reinterpretar la carga (ni a caer al emparejamiento por nombre/DB).
        if (byId.isNotEmpty()) return byId.filterNot { hasContradictoryExerciseIdentity(it, exercise) }
        val configurationId = exercise.catalogConfigurationId
        val dbId = exercise.exerciseDbId
        return log.completedExercises.filter { done ->
            !hasContradictoryExerciseIdentity(done, exercise) &&
                ((configurationId != null && done.catalogConfigurationId == configurationId) ||
                    (dbId != null && done.exerciseDbId == dbId))
        }
    }

    /** Compara sólo identidades homólogas; `exerciseDbId` y `catalogConfigurationId` no se mezclan. */
    private fun hasContradictoryExerciseIdentity(done: CompletedExercise, exercise: Exercise): Boolean =
        (done.catalogConfigurationId != null && exercise.catalogConfigurationId != null &&
            done.catalogConfigurationId != exercise.catalogConfigurationId) ||
            (done.exerciseDbId != null && exercise.exerciseDbId != null && done.exerciseDbId != exercise.exerciseDbId)

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
        requireExactExerciseId: Boolean = false,
    ): List<SetPair> {
        val planned = exercise.sets.filterNot { it.isEmptySlot }
        val targets = planned.withIndex().filter { isTarget(it.value) }
        if (targets.isEmpty() && isFlagged == null) return emptyList()
        for (log in logs) {
            val matchingExercises = if (requireExactExerciseId) {
                log.completedExercises.filter { it.exerciseId == exercise.id }
                    .filterNot { hasContradictoryExerciseIdentity(it, exercise) }
            } else {
                loggedExercisesOf(log, exercise)
            }
            val working = matchingExercises
                .flatMap { it.sets }
                .filter { !it.isWarmup && !it.skipped }
            if (working.isEmpty()) continue
            val flagged: List<CompletedSet> = if (isFlagged == null) emptyList() else working.filter(isFlagged)
            if (isFlagged != null && working.any { it.performedPurpose() != null }) {
                // Modern evidence, including NONE, belongs to this occurrence. Never revive
                // an opt-out from the plan's ordinal or an older session's AMRAP.
                return flagged.mapIndexedNotNull { flaggedIndex, performed ->
                    val prescribed = performed.setIndex?.let { exercise.sets.getOrNull(it) }
                        ?: planned.firstOrNull { it.id == performed.id }
                        ?: working.indexOf(performed).takeIf { working.size == planned.size }?.let(planned::getOrNull)
                        ?: targets.getOrNull(flaggedIndex)?.value
                    prescribed?.let { SetPair(it, performed) }
                }
            }
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
                val e1 = calculateHybrid1RM(set.weight, set.reps)
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
                val e1 = calculateHybrid1RM(set.weight, set.reps)
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
        if (profile != null) working = ExerciseTrainingMax.syncProfile(program,
            working.copy(powerliftingProfile = profile), nowMs,
            "autoregulation:${program.runState?.runId}:$nextWeekId:" + proposals.hashCode(),
            "Propuesta de autorregulación aceptada")

        // La intensidad solo se materializa si la receta efectiva de la ocurrencia
        // objetivo contiene porcentajes; una semana RIR no debe reconstruirse sin efecto.
        val targetWeekUsesPercent = nextWeekId?.let { targetWeekId ->
            PlanMaterializer.weekRecipeSourceFor(working, recipe, targetWeekId)
                ?.weekRecipe
                ?.days
                ?.any { day -> day.slots.any { slot -> slot.sets.any { it.percent != null } } }
        } == true
        val mutationProposals = if (targetWeekUsesPercent) proposals else proposals.filterNot {
            it.kind == AutoregulationProposalKind.SCALE_WEEK_INTENSITY
        }
        val intensity = mutationProposals
            .filter { it.kind == AutoregulationProposalKind.SCALE_WEEK_INTENSITY }
            .mapNotNull { it.percentDelta }
            .fold(1.0) { acc, delta -> acc * (1.0 + delta / 100.0) }
        val volume = proposals
            .filter { it.kind == AutoregulationProposalKind.SCALE_WEEK_VOLUME }
            .mapNotNull { it.volumeFactor }
            .fold(1.0) { acc, factor -> acc * factor }

        // DEC-w3-07: una propuesta SWAP_TO_TECHNIQUE_VARIANT guardada se IGNORA aquí: no reescribe la receta
        // efectiva ni reconstruye la semana. Antes solo disparaba una reconstrucción sin cambios (el cambio
        // de variante nunca se implementó) y quedaba «sin efecto»; `outcomeEntries` la marca caducada con motivo.
        val delayPeak = proposals.any { it.kind == AutoregulationProposalKind.DELAY_PEAK }
        var recipeForNext = recipe
        if (delayPeak) {
            recipeForNext = recipe.copy(
                weeks = recipe.weeks.map { week ->
                    week.copy(
                        days = week.days.map { day ->
                            day.copy(
                                slots = day.slots.map { slot ->
                                    slot.copy(sets = slot.sets.map { set -> set.copy(percent = set.percent?.times(0.95)) })
                                },
                            )
                        },
                    )
                },
            )
        }

        if (nextWeekId != null && (intensity != 1.0 || volume != 1.0 || delayPeak || proposals.any { it.kind == AutoregulationProposalKind.ADJUST_TM || it.kind == AutoregulationProposalKind.PROMOTE_TM })) {
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
                        applied = mutationProposals
                            .filter { it.kind != AutoregulationProposalKind.INSERT_DELOAD }
                            // DEC-w3-07: la variante técnica retirada no consta como aprobada en la receta efectiva.
                            .filter { it.kind != AutoregulationProposalKind.SWAP_TO_TECHNIQUE_VARIANT }
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

        out += performanceProposals(program.id, recipe, signals, profile, readiness)

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
                val oneRm = TrainingMaxResolver.oneRm(profile, slot) ?: return@forEach
                if (e1 > oneRm) {
                    out += AutoregulationProposal(
                        kind = AutoregulationProposalKind.PROMOTE_TM,
                        liftSlot = slot.name,
                        percentDelta = 2.5,
                        explanation = "e1RM ${e1.toInt()} kg > 1RM ${oneRm.toInt()} kg dos semanas seguidas",
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

        // DEC-w3-07: el dolor articular repetido ([WeeklyAutoregulationSignals.repeatedJointPain]) ya no
        // genera la propuesta «pasar el T1 a variante técnica (pausa)»: las variantes son configuraciones
        // del catálogo y el cambio de ejercicio va por `PlanAdaptationResolver`. Tampoco queda como aviso
        // informativo: este motor devuelve propuestas aplicables y no tiene canal de avisos.

        val distinct = mutableListOf<AutoregulationProposal>()
        out.forEach { proposal ->
            val duplicate = if (proposal.kind == AutoregulationProposalKind.ADJUST_XRM) {
                val sourceId = proposal.apreXrm?.reference?.metadata?.sourceId
                distinct.any { it.kind == proposal.kind &&
                    it.apreXrm?.sourceExerciseId == proposal.apreXrm?.sourceExerciseId &&
                    it.apreXrm?.reference?.metadata?.sourceId == sourceId }
            } else {
                distinct.any { it.kind == proposal.kind && it.liftSlot == proposal.liftSlot }
            }
            if (!duplicate) distinct += proposal
        }
        return distinct
    }

    // ─── Progresión por rendimiento: propuestas por carga, XRM observado o TM ───────────

    /**
     * Propuestas `ADJUST_TM` de la progresión por rendimiento: el AMRAP de la semana (solo `AmrapDrivenTm` y
     * `RepTargetDrivenTm`, más una regla o un gancho genéricos, lo traducen a TM), el calibrador de «próximas sesiones», el
     * top set de `TopSetPr` y la serie al máximo de `RepMaxAutoregulated`. Un levantamiento sin ajuste
     * (hit sin levantamiento, cambio nulo) no genera propuesta.
     *
     * H-05 (B.S6 parte 2b): el AMRAP de una receta SIN regla de progresión (`None`) y sin el gancho
     * `AMRAP_TM` no mueve el TM, ni para subirlo ni para bajarlo ([amrapMovesTm]).
     *
     * CAL5: el AMRAP de cada programa de autor sigue la regla de su fuente (RES1 §12.6). En 5/3/1 la serie «+» no mueve el TM
     * (lo sube el ciclo y lo congela o reinicia un ciclo sin repeticiones mínimas); en Juggernaut y GZCLP el AMRAP actúa al
     * cerrar la ola o el ciclo; en J&T lo mide la serie al máximo. Ninguna de esas reglas pasa por esta función
     * ([amrapMovesTm]). El calibrador de «próximas sesiones» (plantillas `power-*`) sí: su capacidad se compara con el 1RM que
     * implica el TM y el factor del calibrador, acotado por `CalibrationPolicy`, se propone como cambio porcentual del TM.
     */
    private fun performanceProposals(
        programId: String,
        recipe: TrainingPlanRecipe,
        signals: WeeklyAutoregulationSignals,
        profile: PowerliftingProfile?,
        readiness: Int?,
    ): List<AutoregulationProposal> {
        val proposals = mutableListOf<AutoregulationProposal>()
        val amrapCanMoveTm = amrapMovesTm(recipe)
        signals.amrapHits.forEach { hit ->
            // Todo hit de un bloque APRE real permanece fuera del canal TM; solo S4 validada produce XRM.
            if (hit.isApreBlock || recipe.progression is ProgressionRule.ApreXrm) {
                if (hit.apreXrmCapture != null) apreProposal(hit, programId)?.let { proposals += it }
                return@forEach
            }
            // Una captura transportada fuera del bloque real no puede convertirse en TM ni en XRM.
            if (hit.apreXrmCapture != null) return@forEach
            // Sin levantamiento en la receta (las dominadas de Texas) el hit solo se registra: no hay TM que ajustar.
            val lift = hit.liftSlot ?: return@forEach
            if (hit.isNextSessionsCalibrator) {
                calibratorProposal(recipe, profile, hit, lift)?.let { proposals += it }
                return@forEach
            }
            if (!amrapCanMoveTm) return@forEach
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

    /**
     * CAL5/CAL6 · canal de propuestas del calibrador de «próximas sesiones» en programas con TM (`power-*`): la capacidad C de
     * la serie (e1RM del motor común, tope de 10 repeticiones, reserva declarada 0-1 o fallo) se compara con la referencia B,
     * el 1RM que implica el TM (`TM ÷ trainingMaxPercent`), y el factor del calibrador (zona muerta 0,97-1,025, topes +5 % y
     * −8 %) pasa a ser un cambio porcentual del TM. Una serie no elegible (sin esfuerzo declarado, reserva alta, sin carga
     * externa…) o con una capacidad dentro de la zona muerta no genera propuesta: el calibrador nunca pasa por encima de la
     * receta. Respeta OFF, PROPOSE y AUTO porque sale de [evaluate].
     */
    internal fun calibratorProposal(
        recipe: TrainingPlanRecipe,
        profile: PowerliftingProfile?,
        hit: AmrapHit,
        lift: LiftSlot,
    ): AutoregulationProposal? {
        val decision = calibratorDecision(recipe, profile, hit, lift) ?: return null
        if (decision.type != CalibrationDecisionType.RAISE && decision.type != CalibrationDecisionType.LOWER) return null
        val percent = (decision.factor - 1.0) * 100.0
        if (abs(percent) < 1e-9) return null
        val capacity = decision.capacityKg ?: return null
        val weight = hit.weightKg ?: return null
        val implied = decision.referenceKg ?: return null
        val change = TmChange(percentDelta = percent)
        return tmProposal(
            lift,
            change,
            "Calibrador de ${AuthoredProgressionEngine.liftName(lift)}: ${SpanishPlurals.reps(hit.actualReps)} con " +
                "${NativeProgressionText.formatKg(weight)} kg estiman ${NativeProgressionText.formatKg(capacity)} kg de 1RM " +
                "frente a los ${NativeProgressionText.formatKg(implied)} kg que implica tu TM: TM ${changeText(change)}",
        )
    }

    /** Compatibilidad del consumidor anterior: sin captura S4 real no hay propuesta APRE. */
    internal fun apreProposal(
        recipe: TrainingPlanRecipe,
        rule: ProgressionRule.ApreXrm,
        profile: PowerliftingProfile?,
        hit: AmrapHit,
        lift: LiftSlot,
    ): AutoregulationProposal? = apreProposal(hit, hit.apreXrmCapture?.reference?.sourceProgramId)

    /** Propuesta de XRM físico basada exclusivamente en una captura S4 validada. */
    internal fun apreProposal(hit: AmrapHit, programId: String?): AutoregulationProposal? {
        val capture = hit.apreXrmCapture ?: return null
        if (!validApreXrmCapture(capture) ||
            (capture.reference.sourceProgramId != null && capture.reference.sourceProgramId != programId)) return null
        val reason = capture.reference.metadata?.reason?.takeIf { it.isNotBlank() }
            ?: "APRE: ${capture.reference.repMin}RM ${NativeProgressionText.formatKg(capture.reference.capturedLoadKg!!)} kg para la próxima semana."
        return AutoregulationProposal(
            kind = AutoregulationProposalKind.ADJUST_XRM,
            explanation = reason,
            apreXrm = capture,
        )
    }
    /** La decisión del motor de calibración (CAL2) para [hit] contra el 1RM que implica el TM del programa; null sin carga, TM o perfil. */
    internal fun calibratorDecision(
        recipe: TrainingPlanRecipe,
        profile: PowerliftingProfile?,
        hit: AmrapHit,
        lift: LiftSlot,
    ): com.example.kpkn.domain.calibration.CalibrationDecision? {
        val weight = hit.weightKg ?: return null
        if (profile == null || recipe.trainingMaxPercent <= 0.0) return null
        val tm = TrainingMaxResolver.trainingMax(profile, lift, recipe.trainingMaxPercent) ?: return null
        val implied = tm / recipe.trainingMaxPercent
        val configurationId = hit.configurationId ?: lift.name
        val context = CalibrationContext(configurationId = configurationId, equipmentKey = "programa")
        return CalibrationEngine.calibrate(
            source = RecordedCalibrationSet(
                setId = "programa-${lift.name}",
                exerciseId = configurationId,
                context = context,
                externalLoadKg = weight,
                fullReps = hit.actualReps,
                partialReps = hit.partialReps,
                declaredRir = hit.declaredRir,
                reachedFailure = hit.reachedFailure,
            ),
            pendingSets = emptyList(),
            references = CalibrationReferenceCandidates(declaredOneRm = DeclaredCalibrationOneRm(context, implied)),
            nowEpochMillis = 0L,
            scope = CalibrationScope.NEXT_SESSIONS,
        )
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
     * true si el AMRAP de [recipe] puede mover el TM por la vía semanal (propuesta `ADJUST_TM`): la receta declara
     * `AmrapDrivenTm` o `RepTargetDrivenTm`, o una regla o el gancho `AMRAP_TM` genéricos. Sin ninguna de las dos (`None` y
     * sin gancho) el AMRAP es solo una serie al máximo de la tabla del autor y no genera propuestas de TM (H-05).
     *
     * CAL5: el AMRAP de las reglas de ciclo u ola no mueve el TM aquí, porque en su fuente actúa al cerrar el ciclo o la ola:
     * `CycleIncrement` (5/3/1: la serie «+» no mueve el TM), `RepsOverStandardTm` (Juggernaut) y `StagedVolume` (GZCLP), y
     * `RepMaxAutoregulated` lo mide con el e1RM de la serie al máximo (J&T), no con el AMRAP corto.
     */
    internal fun amrapMovesTm(recipe: TrainingPlanRecipe): Boolean = when (recipe.progression) {
        is ProgressionRule.AmrapDrivenTm,
        is ProgressionRule.RepTargetDrivenTm,
        -> true
        is ProgressionRule.ApreXrm,
        is ProgressionRule.CycleIncrement,
        is ProgressionRule.RepsOverStandardTm,
        is ProgressionRule.StagedVolume,
        ProgressionRule.RepMaxAutoregulated,
        -> false
        else -> recipe.progression != ProgressionRule.None ||
            recipe.autoregulationHooks.any { it.kind == AutoregulationHookKind.AMRAP_TM }
    }

    /**
     * AMRAP corto: menos repeticiones que el objetivo. Un AMRAP corto nunca sube el TM.
     *
     * Una sola repetición en una serie «1+» NO es corta (decisión B.S6 parte 2b, H-01): cumple el objetivo.
     * nSuns y 5/3/1 tratan 1 repetición al 95 % como un éxito y la tabla de `AmrapDrivenTm`
     * (`zeroToOneKg = 0`) ya expresa «sin subida» para ese caso, así que no hace falta una cláusula aparte
     * que la volviera una bajada del TM. Sin mínimo declarado (`prescribedReps = 0`) el AMRAP nunca es corto.
     */
    internal fun isShortAmrap(hit: AmrapHit): Boolean = hit.actualReps < hit.prescribedReps

    /**
     * Cambio de TM de un AMRAP según la regla de la receta, o null si no mueve el TM. Lo corto se evalúa
     * PRIMERO y nunca sube:
     * - corto con `AmrapDrivenTm` (nSuns): sin cambio (CAL5: la tabla de su fuente va de «0-1 repeticiones: sin subida» y no
     *   define bajadas); con `RepTargetDrivenTm`: `missedRepPercent` por repetición que falta, solo si faltan `missThreshold` o
     *   más; con las reglas de ciclo, de ola y de etapas y con `RepMaxAutoregulated`: sin cambio (su AMRAP actúa al cerrar o
     *   por el e1RM); con cualquier otra regla genérica: [TM_DOWN_PERCENT] % si faltaron repeticiones.
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
                is ProgressionRule.AmrapDrivenTm,
                is ProgressionRule.CycleIncrement,
                is ProgressionRule.RepsOverStandardTm,
                is ProgressionRule.StagedVolume,
                is ProgressionRule.ApreXrm,
                ProgressionRule.RepMaxAutoregulated,
                -> null
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
