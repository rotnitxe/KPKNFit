package com.example.kpkn.domain.training

import com.example.kpkn.data.exercises.catalogConfigurationDisplayName
import com.example.kpkn.data.models.*
import com.example.kpkn.data.protocols.NativeProgressionStrategy
import com.example.kpkn.data.protocols.PlanLoadReference
import com.example.kpkn.data.protocols.PlanLoadReferenceKind
import com.example.kpkn.data.protocols.PlanLoadReferenceState
import com.example.kpkn.data.protocols.PlanSlotChange
import com.example.kpkn.data.protocols.SlotIntent
import com.example.kpkn.data.protocols.TechniqueModifier
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.WeekRecipe
import com.example.kpkn.data.protocols.definitions.NativeProgressionContracts
import com.example.kpkn.data.protocols.definitions.NativeProgressionOutcome
import com.example.kpkn.domain.exercises.replacedWithCatalogExercise
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Runtime bridge from immutable workout logs to the native-plan proposal flow.
 * Only recipes that explicitly opt in through `nativeProgression` are handled;
 * authored/legacy `ProgressionRule`s and AUGE remain separate.
 */
object NativeWorkoutProgressionRuntime {
    // H-BW: escalera de la flexión (rodillas → estándar → pies elevados). Sentadilla, zancada y
    // puente quedan fuera hasta que un entrenador fije el orden de dificultad.
    private val harderBodyweightVariants = mapOf(
        "knee_push_up__default" to "push_up__flat",
        "push_up__flat" to "push_up__feet_elevated",
    )
    private val easierBodyweightVariants = mapOf(
        "push_up__flat" to "knee_push_up__default",
        "push_up__feet_elevated" to "push_up__flat",
    )

    /**
     * H-BW: una variante que exige un apoyo (p. ej. pies elevados → apoyo estable) solo se
     * propone si el material declarado lo incluye; si no, se explica en un aviso.
     */
    private fun supportAvailableFor(configurationId: String, inventory: EquipmentInventory?): Boolean {
        val needs = supportRequirementsFor(configurationId)
        if (needs.isEmpty()) return true
        val declared = inventory?.supportEquipment.orEmpty().mapTo(mutableSetOf()) { it.trim().lowercase() }
        return needs.all { it in declared }
    }

    /** Tolerancia al comparar cargas en kg (misma que usaba el runtime en línea). */
    private const val LOAD_TOLERANCE_KG = 0.0001

    /** Techo de la búsqueda de discos por lado, en gramos (2 000 kg por lado: nunca se alcanza). */
    private const val MAX_PLATE_SEARCH_MILLI = 2_000_000L

    /** `AppliedRecipeProposal.kind` de una variante corporal persistida en la receta efectiva. */
    const val NATIVE_VARIANT_APPLIED_KIND = "NATIVE_BODYWEIGHT_VARIANT"

    /** Prefijo del `proposalId` del aviso único «nuevo bloque» (H-CICLO): `native-cycle-c<N>`. */
    const val NEW_BLOCK_NOTICE_PREFIX = "native-cycle-c"

    /**
     * Ciclo (1-based) al que pertenece un log. Los planes COMPLEX nativos sellan
     * `cycleNumber = 1` en TODOS los logs, así que manda el id de instancia de semana con ciclo
     * (`inst_c<N>_<semana>`); después `cycleNumber` y, por último, el ciclo 1.
     */
    fun logCycle(log: WorkoutLog): Int =
        instanceCycle(log.weekInstanceId)
            ?: instanceCycle(log.weekId)
            ?: log.cycleNumber?.takeIf { it > 0 }
            ?: 1

    private fun instanceCycle(id: String?): Int? =
        id?.takeIf { it.startsWith("inst_c") }
            ?.let { ProgramProgressEngine.cycleFromInstanceId(it) }
            ?.takeIf { it > 0 }

    /** Ciclo en curso del run del programa. */
    private fun currentCycleOf(program: Program): Int =
        program.runState?.cycleNumber?.takeIf { it > 0 } ?: 1

    /** F-02: una sesión se «entrena» por par (ciclo, sesión): el ciclo 2 reutiliza los ids del 1. */
    private fun executionKey(cycle: Int, sessionId: String): String = "$cycle|$sessionId"

    private fun executedKeys(
        logs: List<WorkoutLog>,
        ongoingSessionIds: Set<String> = emptySet(),
        ongoingCycle: Int = 1,
    ): Set<String> {
        val keys = logs.mapTo(mutableSetOf()) { executionKey(logCycle(it), it.sessionId) }
        ongoingSessionIds.forEach { keys += executionKey(ongoingCycle, it) }
        return keys
    }

    /** Logs que cuentan como evidencia: del programa, fuera de breaks y del run en curso. */
    private fun relevantLogsFor(program: Program, logs: List<WorkoutLog>): List<WorkoutLog> {
        val runId = program.runState?.runId
        return logs.asSequence()
            .filter { it.programId == program.id && it.calendarBreakId == null }
            .filter { runId.isNullOrBlank() || it.programRunId.isNullOrBlank() || it.programRunId == runId }
            .distinctBy { it.id }
            .sortedWith(compareBy<WorkoutLog>({ it.date }, { it.id }))
            .toList()
    }

    /**
     * Equipo (`equipmentId`) del catálogo para una configuración, vía el proveedor de metadatos
     * de composición del proceso; null si todavía no hay proveedor o no conoce el id.
     */
    private fun catalogEquipmentId(configurationId: String): String? =
        CompositionMetadataHolder.current?.metadata(configurationId)?.equipmentId

    /** Called only after the repository has accepted and durably identified a new workout log. */
    fun observeCompletedWorkout(
        program: Program,
        logs: List<WorkoutLog>,
        inventory: EquipmentInventory?,
        curatedConfigurations: Set<String>,
        completedLogId: String? = null,
        nowMs: Long = System.currentTimeMillis(),
        /** Equipo del catálogo (`equipmentId`) de una configuración; null si el catálogo no la conoce. */
        equipmentIdOf: (String) -> String? = { configurationId -> catalogEquipmentId(configurationId) },
        /** Nombre visible de una configuración del catálogo (para nombrar la variante propuesta). */
        displayNameOf: (String) -> String? = { configurationId -> catalogConfigurationDisplayName(configurationId) },
    ): Program {
        val recipe = program.sourceRecipe ?: return program
        val spec = recipe.nativeProgression ?: return program
        if (spec.strategy == NativeProgressionStrategy.NONE || spec.exposuresBeforeProposal < 1) return program

        val relevantLogs = relevantLogsFor(program, logs)
        if (relevantLogs.isEmpty()) return program

        val hierarchy = ProgramHierarchyIndex(program)
        val exposures = relevantLogs.flatMap { log ->
            val location = hierarchy.locateSession(log.sessionId) ?: return@flatMap emptyList()
            exposuresForLog(program, recipe.id, recipe.contentVersion, log, location)
        }
        if (exposures.isEmpty()) return program
        // F-02: "ya entrenada" es un par (ciclo, sesión); el ciclo 2 reutiliza los
        // mismos ids de sesión, así que el historial del ciclo 1 no los bloquea.
        val executed = executedKeys(relevantLogs)
        val intents = slotIntents(recipe)

        var next = program
        if (completedLogId != null) {
            exposures.filter { it.logId == completedLogId && it.complete && !it.deload && it.currentLoadKg != null }
                .forEach { exposure ->
                    next = captureManualLoadForFuture(
                        program = next,
                        exposure = exposure,
                        executedKeys = executed,
                        intents = intents,
                        nowMs = nowMs,
                    )
                }
        }
        val contract = NativeProgressionContracts(spec.strategy, spec.exposuresBeforeProposal)
        exposures.groupBy { it.identity }.forEach { (identity, identityExposures) ->
            if (next.nativeProgressionProposals.any { it.identity == identity }) return@forEach
            // F-06/F-08d: la rama la decide la identidad (corporal vs carga) y exige reps.
            val branch = progressionBranchFor(spec.strategy, identity) ?: return@forEach
            val resolvedLogIds = next.nativeProgressionAudit.asSequence()
                .filter { it.identity == identity }
                .flatMap { it.sourceLogIds.asSequence() }
                .toSet()
            // F-08b: la semana de descarga (RIR 4, series reducidas) no cuenta como éxito ni fallo.
            val complete = identityExposures
                .filter { it.complete && !it.deload }
                .filterNot { it.logId in resolvedLogIds }
                // H-IDENT: dos slots del mismo ejercicio en UN entreno son una sola exposición.
                .groupBy { it.logId }
                .values
                .map { sameLog -> mergeSameLog(sameLog) }
                .sortedWith(compareBy<Exposure>({ it.date }, { it.logId }))
            val recent = complete.takeLast(spec.exposuresBeforeProposal)
            if (recent.size < spec.exposuresBeforeProposal) return@forEach
            // F-08a: sin la misma carga en toda la ventana no hay evidencia comparable.
            if (!sameLoadAcrossWindow(recent)) return@forEach

            val allAtTop = recent.all { it.allSetsAtTop }
            val allRirAtTarget = recent.all { it.allRirAtTarget }
            val outcome = contract.exposureOutcome(
                completedExposures = recent.size,
                allSetsAtTopOfRange = allAtTop,
                rirAtTargetOrAbove = allRirAtTarget,
            )
            // Subir exige que cada exposición se hiciera a UNA carga en todas sus series.
            if (outcome == NativeProgressionOutcome.PROPOSE_INCREMENT && !recent.all { it.uniformLoad }) {
                return@forEach
            }
            val latest = recent.last()
            val futureExists = hasFuturePrescription(
                program = next,
                sourceSessionId = latest.sessionId,
                cycle = latest.cycle,
                executedKeys = executed,
                identity = identity,
                recipe = recipe,
                intents = intents,
            )
            if (!futureExists) {
                val kind = proposalKindForOutcome(branch, outcome, recent)
                    ?: return@forEach
                next = appendNoticeOnce(
                    program = next,
                    proposalId = proposalId(next.id, identity, recent.map { it.logId }, kind),
                    kind = kind,
                    identity = identity,
                    sourceLogIds = recent.map { it.logId },
                    nowMs = nowMs,
                    reason = NativeProgressionText.noFutureSessionNotice(),
                )
                return@forEach
            }

            when (branch) {
                ProgressionBranch.LOAD -> {
                    val currentLoad = latest.currentLoadKg ?: return@forEach
                    if (currentLoad <= 0.0) return@forEach
                    val equipmentId = equipmentIdOf(identity.configurationId)
                    // H-UI: unidad y verbo del texto llano («subir de 20 a 22 kg por mancuerna»).
                    val unit = NativeProgressionText.loadUnit(
                        identity.quantityConvention,
                        NativeLoadConventions.stockKindFor(equipmentId, identity.configurationId),
                    )
                    val assistance = isAssistanceLoad(identity)
                    when {
                        outcome == NativeProgressionOutcome.PROPOSE_INCREMENT -> {
                            val step = knownNextLoad(
                                identity = identity,
                                currentLoadKg = currentLoad,
                                inventory = inventory,
                                increasing = loadStepIncreasesResistance(identity, progressionIncrease = true),
                                equipmentId = equipmentId,
                            )
                            if (step is NextLoad.AtLimit) {
                                // F-11: el material declarado no ofrece un paso superior; se informa
                                // el tope en vez de pedir «subir ligeramente» sin salida posible.
                                next = appendNoticeOnce(
                                    next,
                                    proposalId(next.id, identity, recent.map { it.logId }, NativeProgressionProposalKind.INCREASE_LOAD),
                                    NativeProgressionProposalKind.INCREASE_LOAD,
                                    nowMs,
                                    NativeProgressionText.increaseAtLimitNotice(recent.size, latest.topReps, step.detail),
                                    identity,
                                    recent.map { it.logId },
                                )
                                return@forEach
                            }
                            val target = (step as? NextLoad.Reachable)?.kg
                            next = appendProposalOnce(
                                next,
                                makeProposal(
                                    program = next,
                                    identity = identity,
                                    kind = NativeProgressionProposalKind.INCREASE_LOAD,
                                    recent = recent,
                                    targetLoadKg = target,
                                    explanation = NativeProgressionText.loadIncrease(
                                        times = recent.size,
                                        topReps = latest.topReps,
                                        fromKg = currentLoad,
                                        toKg = target,
                                        unit = unit,
                                        assistance = assistance,
                                    ),
                                    nowMs = nowMs,
                                ),
                            )
                        }
                        outcome == NativeProgressionOutcome.KEEP_OR_REGRESS && recent.all { it.belowMinimumOrRir } -> {
                            val step = knownNextLoad(
                                identity = identity,
                                currentLoadKg = currentLoad,
                                inventory = inventory,
                                increasing = loadStepIncreasesResistance(identity, progressionIncrease = false),
                                equipmentId = equipmentId,
                            )
                            if (step is NextLoad.AtLimit) {
                                next = appendNoticeOnce(
                                    next,
                                    proposalId(next.id, identity, recent.map { it.logId }, NativeProgressionProposalKind.REDUCE_LOAD),
                                    NativeProgressionProposalKind.REDUCE_LOAD,
                                    nowMs,
                                    NativeProgressionText.reduceAtLimitNotice(recent.size, latest.minReps, step.detail),
                                    identity,
                                    recent.map { it.logId },
                                )
                                return@forEach
                            }
                            val target = (step as? NextLoad.Reachable)?.kg
                            next = appendProposalOnce(
                                next,
                                makeProposal(
                                    program = next,
                                    identity = identity,
                                    kind = NativeProgressionProposalKind.REDUCE_LOAD,
                                    recent = recent,
                                    targetLoadKg = target,
                                    explanation = NativeProgressionText.loadReduction(
                                        times = recent.size,
                                        minReps = latest.minReps,
                                        fromKg = currentLoad,
                                        toKg = target,
                                        unit = unit,
                                        assistance = assistance,
                                    ),
                                    nowMs = nowMs,
                                ),
                            )
                        }
                    }
                }
                ProgressionBranch.BODYWEIGHT_VARIANT -> {
                    if (identity.side != "bilateral") {
                        next = appendNoticeOnce(
                            next,
                            proposalId(next.id, identity, recent.map { it.logId }, NativeProgressionProposalKind.HARDER_BODYWEIGHT_VARIANT),
                            NativeProgressionProposalKind.HARDER_BODYWEIGHT_VARIANT,
                            nowMs,
                            NativeProgressionText.lateralVariantNotice(),
                            identity,
                            recent.map { it.logId },
                        )
                        return@forEach
                    }
                    val harder = harderBodyweightVariants[identity.configurationId]
                        ?.takeIf { it in curatedConfigurations }
                    val harderUsable = harder != null && supportAvailableFor(harder, inventory)
                    when {
                        outcome == NativeProgressionOutcome.PROPOSE_INCREMENT && harder != null && !harderUsable -> {
                            next = appendNoticeOnce(
                                next,
                                proposalId(next.id, identity, recent.map { it.logId }, NativeProgressionProposalKind.HARDER_BODYWEIGHT_VARIANT),
                                NativeProgressionProposalKind.HARDER_BODYWEIGHT_VARIANT,
                                nowMs,
                                NativeProgressionText.missingSupportNotice(recent.size, latest.topReps, harder.let(displayNameOf)),
                                identity,
                                recent.map { it.logId },
                            )
                        }
                        outcome == NativeProgressionOutcome.PROPOSE_INCREMENT &&
                            contract.bodyweightNextVariant(hasHarderCuratedVariant = harderUsable) -> {
                            next = appendProposalOnce(
                                next,
                                makeProposal(
                                    program = next,
                                    identity = identity,
                                    kind = NativeProgressionProposalKind.HARDER_BODYWEIGHT_VARIANT,
                                    recent = recent,
                                    targetConfigurationId = harder,
                                    explanation = NativeProgressionText.bodyweightVariant(
                                        harder = true,
                                        times = recent.size,
                                        topReps = latest.topReps,
                                        minReps = latest.minReps,
                                        targetName = harder?.let(displayNameOf),
                                    ),
                                    nowMs = nowMs,
                                ),
                            )
                        }
                        outcome == NativeProgressionOutcome.PROPOSE_INCREMENT -> {
                            next = appendNoticeOnce(
                                next,
                                proposalId(next.id, identity, recent.map { it.logId }, NativeProgressionProposalKind.HARDER_BODYWEIGHT_VARIANT),
                                NativeProgressionProposalKind.HARDER_BODYWEIGHT_VARIANT,
                                nowMs,
                                NativeProgressionText.noHarderVariantNotice(recent.size, latest.topReps),
                                identity,
                                recent.map { it.logId },
                            )
                        }
                        outcome == NativeProgressionOutcome.KEEP_OR_REGRESS && recent.all { it.belowMinimumOrRir } -> {
                            val easier = easierBodyweightVariants[identity.configurationId]
                                ?.takeIf { it in curatedConfigurations }
                            if (easier != null) {
                                next = appendProposalOnce(
                                    next,
                                    makeProposal(
                                        program = next,
                                        identity = identity,
                                        kind = NativeProgressionProposalKind.EASIER_BODYWEIGHT_VARIANT,
                                        recent = recent,
                                        targetConfigurationId = easier,
                                        explanation = NativeProgressionText.bodyweightVariant(
                                            harder = false,
                                            times = recent.size,
                                            topReps = latest.topReps,
                                            minReps = latest.minReps,
                                            targetName = displayNameOf(easier),
                                        ),
                                        nowMs = nowMs,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
        return next
    }

    /**
     * H-IDENT: varias apariciones del mismo ejercicio en un mismo entreno (dos slots iguales el
     * mismo día) cuentan como UNA exposición, y la más desfavorable manda: solo es «en el tope»
     * si todas lo fueron, y solo es «uniforme» si todas usaron la misma carga.
     */
    private fun mergeSameLog(sameLog: List<Exposure>): Exposure {
        val first = sameLog.first()
        if (sameLog.size == 1) return first
        val loads = sameLog.map { it.currentLoadKg }
        val sameLoad = loads.all { it == null } ||
            (loads.none { it == null } && loads.maxOf { it!! } - loads.minOf { it!! } <= LOAD_TOLERANCE_KG)
        return first.copy(
            uniformLoad = sameLog.all { it.uniformLoad } && sameLoad,
            allSetsAtTop = sameLog.all { it.allSetsAtTop },
            allRirAtTarget = sameLog.all { it.allRirAtTarget },
            belowMinimumOrRir = sameLog.any { it.belowMinimumOrRir },
            topReps = sameLog.mapNotNull { it.topReps }.minOrNull(),
            minReps = sameLog.mapNotNull { it.minReps }.maxOrNull(),
        )
    }

    /** Resolve exactly one proposal. Repeated resolution is a no-op with a durable terminal ID. */
    fun resolveProposal(
        program: Program,
        proposalId: String,
        accept: Boolean,
        logs: List<WorkoutLog>,
        ongoingSessionIds: Set<String>,
        curatedExercises: Map<String, ExerciseMuscleInfo>,
        nowMs: Long = System.currentTimeMillis(),
    ): Program {
        if (program.nativeProgressionAudit.any { it.proposalId == proposalId }) return program
        val proposal = program.nativeProgressionProposals.firstOrNull { it.proposalId == proposalId } ?: return program
        val remaining = program.nativeProgressionProposals.filterNot { it.proposalId == proposalId }
        if (!accept) {
            return program.copy(
                nativeProgressionProposals = remaining,
                nativeProgressionAudit = program.nativeProgressionAudit + NativeProgressionResolution(
                    proposalId = proposalId,
                    status = NativeProgressionResolutionStatus.REJECTED,
                    kind = proposal.kind,
                    resolvedAtMs = nowMs,
                    reason = "Rechazada por el atleta; no se modificó ninguna prescripción ni registro.",
                    identity = proposal.identity,
                    sourceLogIds = proposal.sourceLogIds,
                ),
            )
        }

        val recipe = program.sourceRecipe
        val progressionStrategy = recipe?.nativeProgression?.strategy
        // F-06: la rama la decide la identidad del slot, no la estrategia global de la receta.
        val strategyMatchesProposal = progressionStrategy != null &&
            proposalMatchesStrategy(proposal.kind, proposal.identity, progressionStrategy)
        if (recipe == null ||
            recipe.id != proposal.identity.recipeId ||
            recipe.contentVersion != proposal.identity.recipeContentVersion ||
            !strategyMatchesProposal
        ) {
            return expire(program, proposal, remaining, nowMs, "El plan cambió desde que se hizo la propuesta; no se modificó nada.")
        }

        // F-02: «entrenada» es el par (ciclo actual, sesión); las mismas sesiones del
        // ciclo anterior vuelven a ser elegibles en el ciclo en curso.
        val cycle = currentCycleOf(program)
        val executed = executedKeys(relevantLogsFor(program, logs), ongoingSessionIds, cycle)
        val locations = ProgramHierarchyIndex(program).orderedSessions()
        val sourceIndex = locations.indexOfFirst { it.sessionId == proposal.sourceSessionId }
        if (sourceIndex < 0) {
            return expire(program, proposal, remaining, nowMs, "Ya no existe la sesión donde se hizo la propuesta; no se modificó nada.")
        }
        val manualOverrides = program.manualSessionOverrides.mapTo(mutableSetOf()) { it.sessionId }
        val intents = slotIntents(recipe)
        // H-DESCARGA: una subida (o bajada) aceptada nunca cae en la semana de descarga: ahí no
        // se entrena con la carga de trabajo y el cambio se perdería al pasar al ciclo siguiente.
        val targets = locations.drop(sourceIndex + 1).filter { location ->
            executionKey(cycle, location.sessionId) !in executed &&
                location.sessionId !in manualOverrides &&
                !isDeloadWeek(recipe, location.hierarchy.week) &&
                location.session.allExercises().any { exercise -> exerciseMatches(exercise, proposal.identity, intents) }
        }
        if (targets.isEmpty()) {
            return expire(program, proposal, remaining, nowMs, "Ya no quedan sesiones sin entrenar donde aplicar la propuesta; no se modificó nada.")
        }

        val replacementInfo = proposal.targetConfigurationId?.let(curatedExercises::get)
        if (proposal.targetConfigurationId != null && replacementInfo == null) {
            return expire(program, proposal, remaining, nowMs, "La variante propuesta ya no está disponible; no se modificó nada.")
        }

        val targetSessionIds = targets.mapTo(mutableSetOf()) { it.sessionId }
        val affectedExerciseIds = mutableSetOf<String>()
        var changedExercises = 0
        val updatedMacros = program.macrocycles.map { macro ->
            macro.copy(blocks = macro.blocks.map { block ->
                block.copy(mesocycles = block.mesocycles.map { meso ->
                    meso.copy(weeks = meso.weeks.map { week ->
                        week.copy(sessions = week.sessions.map { session ->
                            if (session.id !in targetSessionIds) return@map session
                            val updated = session.mapExercises { exercise ->
                                if (!exerciseMatches(exercise, proposal.identity, intents)) return@mapExercises exercise
                                val nextExercise = if (replacementInfo != null) {
                                    exercise.replacedWithCatalogExercise(replacementInfo).copy(
                                        loadReference = null,
                                        loadQuantityConvention = LoadQuantityConvention.UNSPECIFIED,
                                        recipeDayId = exercise.recipeDayId,
                                        recipeSlotId = exercise.recipeSlotId,
                                    )
                                } else {
                                    applyLoadProposal(exercise, proposal, nowMs)
                                }
                                if (nextExercise != exercise) {
                                    changedExercises += 1
                                    affectedExerciseIds += exercise.id
                                }
                                nextExercise
                            }
                            updated
                        })
                    })
                })
            })
        }
        if (changedExercises == 0) {
            return expire(program, proposal, remaining, nowMs, "Las próximas sesiones ya no coinciden con lo que se propuso; no se modificó nada.")
        }

        val nextReferences = if (replacementInfo == null) {
            val updatedExercises = updatedMacros.asSequence()
                .flatMap { it.blocks.asSequence() }
                .flatMap { it.mesocycles.asSequence() }
                .flatMap { it.weeks.asSequence() }
                .flatMap { it.sessions.asSequence() }
                .flatMap { it.allExercises().asSequence() }
                .filter { it.id in affectedExerciseIds }
                .toList()
            mergeLoadReferencePool(program.exerciseLoadReferences, updatedExercises, proposal.identity)
        } else {
            program.exerciseLoadReferences.filterNot { it.exerciseId in affectedExerciseIds }
        }
        // F-10: la variante corporal aceptada también queda en la receta efectiva de
        // cada ocurrencia futura, para que re-materializar la semana no la pierda.
        val nextEffectiveRecipes = if (replacementInfo != null) {
            withVariantInEffectiveRecipes(program, recipe, proposal, targets, cycle, nowMs)
        } else {
            program.effectiveWeekRecipes
        }
        return program.copy(
            macrocycles = updatedMacros,
            exerciseLoadReferences = nextReferences,
            effectiveWeekRecipes = nextEffectiveRecipes,
            nativeProgressionProposals = remaining,
            nativeProgressionAudit = program.nativeProgressionAudit + NativeProgressionResolution(
                proposalId = proposalId,
                status = NativeProgressionResolutionStatus.APPLIED,
                kind = proposal.kind,
                resolvedAtMs = nowMs,
                reason = "Aplicada solo a ${targetSessionIds.size} prescripción(es) futura(s); sesiones entrenadas, cargas registradas e IDs históricos intactos.",
                identity = proposal.identity,
                sourceLogIds = proposal.sourceLogIds,
                targetConfigurationId = proposal.targetConfigurationId,
            ),
        )
    }

    /**
     * Intención («propósito») de cada slot de la receta, por «día|slot». Los slots SPEED quedan
     * fuera: nunca son evidencia ni destino de la progresión (H-IDENT: la identidad ya no incluye
     * día ni posición, así que la intención es lo que impide mezclar un F/H/I con otro propósito).
     */
    private fun slotIntents(recipe: TrainingPlanRecipe): Map<String, Set<String>> =
        recipe.weeks.asSequence()
            .flatMap { it.days.asSequence() }
            .flatMap { day ->
                day.slots.asSequence().mapNotNull { slot ->
                    val intent = slot.intent ?: return@mapNotNull null
                    if (slot.technique == TechniqueModifier.SPEED) return@mapNotNull null
                    "${day.id}|${slot.id}" to intent.name
                }
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.toSet() }

    /** Semana de descarga: ni cuenta como éxito/fallo ni recibe cambios de carga propuestos. */
    private fun isDeloadWeek(recipe: TrainingPlanRecipe, week: ProgramWeek): Boolean =
        week.executionKind == WeekExecutionKind.DELOAD ||
            week.progressionIndex
                ?.let { number -> recipe.weeks.firstOrNull { it.weekNumber == number }?.kind } == WeekExecutionKind.DELOAD

    /**
     * F-10: la variante corporal aceptada también queda en la receta efectiva de cada ocurrencia
     * futura (con el cambio de slot auditado), para que re-materializar la semana (autorregulación
     * AUGE o «Re-materializar») no la pierda. La receta global del programa nunca se muta.
     */
    private fun withVariantInEffectiveRecipes(
        program: Program,
        recipe: TrainingPlanRecipe,
        proposal: NativeProgressionProposal,
        targets: List<ProgramSessionLocation>,
        cycle: Int,
        nowMs: Long,
    ): List<EffectiveWeekRecipe> {
        val targetConfigurationId = proposal.targetConfigurationId ?: return program.effectiveWeekRecipes
        val identity = proposal.identity
        val summary = if (proposal.kind == NativeProgressionProposalKind.EASIER_BODYWEIGHT_VARIANT) {
            "Progresión corporal §12.4: ${identity.configurationId} pasa a la variante más accesible $targetConfigurationId."
        } else {
            "Progresión corporal §12.4: ${identity.configurationId} pasa a la variante más exigente $targetConfigurationId."
        }
        var recipes = program.effectiveWeekRecipes
        targets.map { it.hierarchy }.distinctBy { it.weekId }.forEach { location ->
            recipes = upsertVariantInWeek(
                recipes = recipes,
                recipe = recipe,
                week = location.week,
                weekIndex = location.weekIndex,
                fromConfigurationId = identity.configurationId,
                slotPurpose = identity.slotPurpose,
                toConfigurationId = targetConfigurationId,
                appliedId = proposal.proposalId,
                summary = summary,
                cycle = cycle,
                nowMs = nowMs,
            )
        }
        return recipes
    }

    /**
     * Upsert idempotente por (ocurrencia, ciclo) de la receta efectiva de UNA semana con la
     * configuración del slot cambiada y el [PlanSlotChange] auditado. Sin cambio efectivo (el
     * slot ya apunta a la configuración destino o no existe en esa semana) devuelve [recipes].
     */
    private fun upsertVariantInWeek(
        recipes: List<EffectiveWeekRecipe>,
        recipe: TrainingPlanRecipe,
        week: ProgramWeek,
        weekIndex: Int,
        fromConfigurationId: String,
        slotPurpose: String,
        toConfigurationId: String,
        appliedId: String,
        summary: String,
        cycle: Int,
        nowMs: Long,
    ): List<EffectiveWeekRecipe> {
        val occurrence = PlanMaterializer.weekOccurrenceOf(week, weekIndex)
        val existing = recipes.firstOrNull { it.weekOccurrence == occurrence && it.cycleNumber == cycle }
        val baseWeek: WeekRecipe = existing?.weekRecipe
            ?: recipe.weeks.firstOrNull { it.weekNumber == week.progressionIndex }
            ?: recipe.weeks.getOrNull(weekIndex)
            ?: return recipes
        // H-IDENT: el cambio de variante alcanza a TODOS los slots de la semana con esa
        // configuración y propósito (el mismo ejercicio en dos días cambia junto).
        val changes = mutableListOf<PlanSlotChange>()
        val updatedWeek = baseWeek.copy(
            days = baseWeek.days.map { day ->
                day.copy(
                    slots = day.slots.map { slot ->
                        if (slot.lift.configurationId != fromConfigurationId ||
                            slot.intent?.name != slotPurpose ||
                            slot.technique == TechniqueModifier.SPEED
                        ) {
                            return@map slot
                        }
                        changes += PlanSlotChange(
                            slotId = slot.id,
                            fromConfigurationId = fromConfigurationId,
                            toConfigurationId = toConfigurationId,
                            reason = summary,
                            samePattern = true,
                            loadReferenceKept = false,
                        )
                        slot.copy(lift = slot.lift.copy(configurationId = toConfigurationId))
                    },
                )
            },
        )
        if (changes.isEmpty()) return recipes
        val applied = AppliedRecipeProposal(
            proposalId = appliedId,
            kind = NATIVE_VARIANT_APPLIED_KIND,
            summary = summary,
            acceptedAtMs = nowMs,
        )
        val updated = EffectiveWeekRecipe(
            weekOccurrence = occurrence,
            cycleNumber = cycle,
            version = (existing?.version ?: 0) + 1,
            weekRecipe = updatedWeek,
            changes = existing?.changes.orEmpty() + changes,
            appliedProposals = (existing?.appliedProposals.orEmpty() + applied).distinctBy { it.proposalId },
        )
        return recipes.filterNot { it.weekOccurrence == occurrence && it.cycleNumber == cycle } + updated
    }

    /**
     * §12.1 / §14.5: al cerrar un ciclo de un plan propio la progresión se procesa UNA vez.
     *  - Las propuestas pendientes caducan con motivo (nada se descarta en silencio) y sus
     *    exposiciones quedan consumidas.
     *  - La última referencia de carga de cada identidad (última exposición que no es de descarga
     *    del ciclo cerrado) se escribe en todas las series de trabajo del programa, que el ciclo
     *    siguiente reutiliza, y en la bolsa de referencias por ejercicio.
     *  - La última variante corporal aceptada se adopta en las sesiones del ciclo siguiente y en
     *    su receta efectiva.
     * Logs, ids de sesión/ejercicio y sesiones congeladas por edición manual no cambian.
     * Idempotente: repetirla con los mismos logs devuelve el mismo programa (la guarda de
     * continuación `native-progression-c<N>` evita además reejecutarla en cierres repetidos).
     */
    fun carryForwardToNextCycle(
        program: Program,
        logs: List<WorkoutLog>,
        closedCycle: Int,
        nowMs: Long = System.currentTimeMillis(),
    ): Program {
        val recipe = program.sourceRecipe ?: return program
        val spec = recipe.nativeProgression ?: return program
        if (spec.strategy == NativeProgressionStrategy.NONE) return program

        var next = program
        val hadPending = program.nativeProgressionProposals.isNotEmpty()
        program.nativeProgressionProposals.forEach { pending ->
            // H-CICLO: las caducidades quedan en el registro pero NO se muestran una por una;
            // el atleta recibe UN solo aviso al entrar en el ciclo siguiente.
            next = expire(
                next,
                pending,
                next.nativeProgressionProposals.filterNot { it.proposalId == pending.proposalId },
                nowMs,
                "Caducada al cerrar el ciclo $closedCycle: la propuesta no se resolvió.",
                userFacingNotice = false,
            )
        }
        next = carryLastLoadReferences(next, recipe, logs, closedCycle, nowMs)
        next = carryBodyweightVariants(next, recipe, closedCycle + 1, nowMs)
        return withNewBlockNotice(next, closedCycle + 1, hadPending, nowMs)
    }

    /**
     * H-CICLO: el programa continúa solo con las últimas cargas y se avisa UNA vez, con la
     * duración real del programa (6 o 12 semanas), sin pantalla de oferta. Idempotente por ciclo.
     */
    private fun withNewBlockNotice(program: Program, newCycle: Int, hadPending: Boolean, nowMs: Long): Program {
        val noticeId = "$NEW_BLOCK_NOTICE_PREFIX$newCycle"
        if (program.nativeProgressionAudit.any { it.proposalId == noticeId }) return program
        val weeks = ProgramHierarchyIndex(program).orderedWeeks().size
        val duration = if (weeks > 0) " de $weeks semanas" else ""
        val tail = if (hadPending) " Las propuestas sin responder caducaron." else ""
        return program.copy(
            nativeProgressionAudit = program.nativeProgressionAudit + NativeProgressionResolution(
                proposalId = noticeId,
                status = NativeProgressionResolutionStatus.NOTICE,
                kind = NativeProgressionProposalKind.INCREASE_LOAD,
                resolvedAtMs = nowMs,
                reason = "Empiezas un nuevo bloque$duration con tus últimas cargas.$tail",
                userFacingNotice = true,
            ),
        )
    }

    private fun carryLastLoadReferences(
        program: Program,
        recipe: TrainingPlanRecipe,
        logs: List<WorkoutLog>,
        closedCycle: Int,
        nowMs: Long,
    ): Program {
        val closedLogs = relevantLogsFor(program, logs).filter { logCycle(it) == closedCycle }
        if (closedLogs.isEmpty()) return program
        val hierarchy = ProgramHierarchyIndex(program)
        // La última exposición de cada identidad que NO es de descarga: el último
        // «trabajo observado» del ciclo es la referencia que el siguiente conserva.
        val lastByIdentity = closedLogs
            .flatMap { log ->
                val location = hierarchy.locateSession(log.sessionId) ?: return@flatMap emptyList()
                exposuresForLog(program, recipe.id, recipe.contentVersion, log, location)
            }
            .filter { it.complete && !it.deload && (it.currentLoadKg ?: 0.0) > 0.0 }
            .sortedWith(compareBy<Exposure>({ it.date }, { it.logId }))
            .associateBy { it.identity }
        if (lastByIdentity.isEmpty()) return program

        val frozen = program.manualSessionOverrides.mapTo(mutableSetOf()) { it.sessionId }
        val intents = slotIntents(recipe)
        val updatedByIdentity = LinkedHashMap<NativeProgressionIdentity, MutableList<Exercise>>()
        val updatedMacros = program.macrocycles.map { macro ->
            macro.copy(blocks = macro.blocks.map { block ->
                block.copy(mesocycles = block.mesocycles.map { meso ->
                    meso.copy(weeks = meso.weeks.map { week ->
                        week.copy(sessions = week.sessions.map { session ->
                            if (session.id in frozen) return@map session
                            session.mapExercises { exercise ->
                                var current = exercise
                                lastByIdentity.forEach { (identity, exposure) ->
                                    if (!exerciseMatches(current, identity, intents)) return@forEach
                                    val loadKg = exposure.currentLoadKg ?: return@forEach
                                    val updated = captureManualLoad(current, identity, loadKg, nowMs, onlyUnresolved = false)
                                    if (updated != current) {
                                        updatedByIdentity.getOrPut(identity) { mutableListOf() }.add(updated)
                                        current = updated
                                    }
                                }
                                current
                            }
                        })
                    })
                })
            })
        }
        if (updatedByIdentity.isEmpty()) return program
        var pool = program.exerciseLoadReferences
        updatedByIdentity.forEach { (identity, exercises) ->
            pool = mergeLoadReferencePool(pool, exercises, identity)
        }
        return program.copy(macrocycles = updatedMacros, exerciseLoadReferences = pool)
    }

    private fun carryBodyweightVariants(
        program: Program,
        recipe: TrainingPlanRecipe,
        newCycle: Int,
        nowMs: Long,
    ): Program {
        // H-IDENT: la variante vigente de cada ejercicio (configuración de origen + propósito) es
        // el último cambio aceptado, encadenando los anteriores (rodillas → estándar → pies elevados).
        val latestTarget = LinkedHashMap<Pair<String, String>, String>()
        program.nativeProgressionAudit.asSequence()
            .filter {
                it.status == NativeProgressionResolutionStatus.APPLIED &&
                    (it.kind == NativeProgressionProposalKind.HARDER_BODYWEIGHT_VARIANT ||
                        it.kind == NativeProgressionProposalKind.EASIER_BODYWEIGHT_VARIANT)
            }
            .forEach { entry ->
                val identity = entry.identity ?: return@forEach
                val target = entry.targetConfigurationId ?: return@forEach
                latestTarget[identity.configurationId to identity.slotPurpose] = target
            }
        if (latestTarget.isEmpty()) return program
        val frozen = program.manualSessionOverrides.mapTo(mutableSetOf()) { it.sessionId }
        val intents = slotIntents(recipe)
        var next = program
        latestTarget.keys.toList().forEach { (fromConfiguration, purpose) ->
            var targetConfiguration = latestTarget.getValue(fromConfiguration to purpose)
            var guard = 0
            while (guard++ < 8) {
                val chained = latestTarget[targetConfiguration to purpose] ?: break
                if (chained == targetConfiguration) break
                targetConfiguration = chained
            }
            if (targetConfiguration == fromConfiguration) return@forEach
            val hierarchy = ProgramHierarchyIndex(next)
            // La variante vigente se copia de la última sesión del programa que ya la usa.
            val template = hierarchy.orderedSessions().asSequence()
                .filter { it.sessionId !in frozen }
                .flatMap { it.session.allExercises().asSequence() }
                .lastOrNull { it.catalogConfigurationId == targetConfiguration && it.nativeProgressionManaged }
                ?: return@forEach
            val templateSet = template.sets.firstOrNull()
            val updatedMacros = next.macrocycles.map { macro ->
                macro.copy(blocks = macro.blocks.map { block ->
                    block.copy(mesocycles = block.mesocycles.map { meso ->
                        meso.copy(weeks = meso.weeks.map { week ->
                            week.copy(sessions = week.sessions.map { session ->
                                if (session.id in frozen) return@map session
                                session.mapExercises { exercise ->
                                    if (exercise.catalogConfigurationId == fromConfiguration &&
                                        exercise.nativeProgressionManaged &&
                                        purpose in intents["${exercise.recipeDayId}|${exercise.recipeSlotId}"].orEmpty()
                                    ) {
                                        adoptCatalogVariant(exercise, template, templateSet)
                                    } else {
                                        exercise
                                    }
                                }
                            })
                        })
                    })
                })
            }
            val summary = "Variante corporal $targetConfiguration conservada al continuar al ciclo $newCycle (§12.1)."
            var recipes = next.effectiveWeekRecipes
            hierarchy.orderedWeeks().forEach { location ->
                recipes = upsertVariantInWeek(
                    recipes = recipes,
                    recipe = recipe,
                    week = location.week,
                    weekIndex = location.weekIndex,
                    fromConfigurationId = fromConfiguration,
                    slotPurpose = purpose,
                    toConfigurationId = targetConfiguration,
                    appliedId = "native-variant-c$newCycle:$fromConfiguration:$purpose",
                    summary = summary,
                    cycle = newCycle,
                    nowMs = nowMs,
                )
            }
            next = next.copy(macrocycles = updatedMacros, effectiveWeekRecipes = recipes)
        }
        return next
    }

    /**
     * Copia la identidad de catálogo de la variante vigente ([template]) a [exercise], conservando
     * sus ids, series y receta, y deja la carga sin resolver (una variante nueva no hereda kilos).
     */
    private fun adoptCatalogVariant(exercise: Exercise, template: Exercise, templateSet: ExerciseSet?): Exercise =
        exercise.copy(
            name = template.name,
            exerciseDbId = template.exerciseDbId,
            exerciseId = template.exerciseId,
            canonicalExerciseId = template.canonicalExerciseId,
            exerciseFamilyId = template.exerciseFamilyId,
            relativeToCanonicalExerciseId = template.relativeToCanonicalExerciseId,
            relationshipType = template.relationshipType,
            relationshipNotes = template.relationshipNotes,
            trainingMode = template.trainingMode,
            sets = exercise.sets.map { set ->
                set.copy(
                    weight = null,
                    targetPercentageRM = null,
                    loadModeV2 = templateSet?.loadModeV2 ?: set.loadModeV2,
                    unitModeV2 = templateSet?.unitModeV2 ?: set.unitModeV2,
                    leftTarget = set.leftTarget?.copy(weight = null),
                    rightTarget = set.rightTarget?.copy(weight = null),
                    manualLoadRequiredSides = emptySet(),
                )
            },
            warmupSets = emptyList(),
            reference1RM = null,
            setupDetails = template.setupDetails,
            variantName = template.variantName,
            variantGroupId = template.variantGroupId,
            variantGroupName = template.variantGroupName,
            selectedAspects = template.selectedAspects,
            effectiveMuscles = template.effectiveMuscles,
            selectedMovementPattern = template.selectedMovementPattern,
            selectedExecutionOption = template.selectedExecutionOption,
            setupCues = template.setupCues,
            executionCues = template.executionCues,
            catalogRevision = template.catalogRevision,
            catalogDefinitionId = template.catalogDefinitionId,
            catalogConfigurationId = template.catalogConfigurationId,
            performanceProfileId = template.performanceProfileId,
            loadReference = null,
            loadQuantityConvention = template.loadQuantityConvention,
        )

    /** Contract adapter retained here so a future logged talk-test can share the exact §12.4 ladder. */
    fun nextCardioDurationAfterTwoConversationalExposures(
        completedConversationalExposures: Int,
        currentMinutes: Int,
        dedicatedDay: Boolean,
        userChosenMinutes: Int?,
        budgetAllows: Boolean,
    ): Int? {
        if (completedConversationalExposures < 2) return null
        return com.example.kpkn.data.protocols.definitions.NativeCardioEscalation.nextOffered(
            currentMinutes = currentMinutes,
            dedicatedDay = dedicatedDay,
            userChosenMinutes = userChosenMinutes,
            budgetAllows = budgetAllows,
        )
    }

    private fun exposuresForLog(
        program: Program,
        recipeId: String,
        recipeContentVersion: Int,
        log: WorkoutLog,
        location: ProgramSessionLocation,
    ): List<Exposure> {
        val recipe = program.sourceRecipe ?: return emptyList()
        val session = location.session
        val programWeek = location.hierarchy.week
        // F-08e: DayRecipe.id se repite en las seis semanas; la semana de la sesión decide cuál es.
        val recipeWeek = programWeek.progressionIndex
            ?.let { number -> recipe.weeks.firstOrNull { it.weekNumber == number } }
        val deload = programWeek.executionKind == WeekExecutionKind.DELOAD ||
            recipeWeek?.kind == WeekExecutionKind.DELOAD
        val cycle = logCycle(log)
        return log.completedExercises.flatMap { completed ->
            val planned = session.allExercises().firstOrNull { it.id == completed.exerciseId } ?: return@flatMap emptyList()
            val dayId = planned.recipeDayId ?: return@flatMap emptyList()
            val slotId = planned.recipeSlotId ?: return@flatMap emptyList()
            val slot = recipeWeek?.days?.firstOrNull { it.id == dayId }?.slots?.firstOrNull { it.id == slotId }
                ?: recipe.weeks.asSequence().flatMap { it.days.asSequence() }
                    .firstOrNull { it.id == dayId }
                    ?.slots?.firstOrNull { it.id == slotId }
                ?: return@flatMap emptyList()
            // F-08e: `slot.sets` empieza por los calentamientos; el índice de trabajo no.
            val recipeWorkSets = slot.sets.filter { !it.isWarmup }
            val intent = slot.intent ?: return@flatMap emptyList()
            if (intent !in setOf(SlotIntent.F, SlotIntent.H, SlotIntent.I) || slot.technique == TechniqueModifier.SPEED) {
                return@flatMap emptyList()
            }
            val configurationId = completed.catalogConfigurationId
                ?: planned.catalogConfigurationId
                ?: return@flatMap emptyList()
            // A workout-only replacement has a different identity and must not borrow this slot's load history.
            if (!planned.catalogConfigurationId.isNullOrBlank() && configurationId != planned.catalogConfigurationId) return@flatMap emptyList()
            val workIndices = planned.sets.indices.filter { index ->
                val set = planned.sets[index]
                !set.isEmptySlot && !set.isCalibrator && !set.isTopSet &&
                    !set.isDropSet && !set.isRestPause && !set.isAmrap
            }
            if (workIndices.isEmpty()) return@flatMap emptyList()
            val expectedSides = if (planned.isUnilateral || planned.unilateralMode != UnilateralMode.BILATERAL) {
                listOf("left", "right")
            } else {
                listOf("bilateral")
            }
            expectedSides.mapNotNull { side ->
                val sideSets = completed.sets.filter { normalizeSide(it.side) == side && !it.isWarmup }
                val sideComplete = sideSets.size == workIndices.size && sideSets.none { set ->
                    set.skipped || set.isPartial || (set.partialReps ?: 0) > 0 ||
                        set.failureReason == "execution_error" || set.recordedPayloadV3?.executionError == true ||
                        hasLiveTechnique(set)
                }
                if (!sideComplete) return@mapNotNull null

                val plannedSets = workIndices.map { planned.sets[it] }
                val loadMode = resolveLoadMode(plannedSets, sideSets) ?: return@mapNotNull null
                val unitMode = resolveUnitMode(planned, plannedSets, sideSets) ?: return@mapNotNull null
                if (sideSets.any { actual ->
                        val payload = actual.recordedPayloadV3
                        payload != null && (payload.loadInputMode != loadMode || payload.unitMode != unitMode)
                    }
                ) return@mapNotNull null
                val convention = plannedSets.firstOrNull()?.loadQuantityConvention
                    ?.takeIf { it != LoadQuantityConvention.UNSPECIFIED }
                    ?: planned.loadQuantityConvention
                if (plannedSets.any { set ->
                        val setConvention = set.loadQuantityConvention.takeIf { it != LoadQuantityConvention.UNSPECIFIED }
                            ?: planned.loadQuantityConvention
                        setConvention != convention
                    }
                ) return@mapNotNull null

                val identity = NativeProgressionIdentity(
                    recipeId = recipeId,
                    recipeContentVersion = recipeContentVersion,
                    // H-IDENT: sin día ni posición; el propósito (slotPurpose) distingue F/H/I.
                    configurationId = configurationId,
                    loadMode = loadMode,
                    unitMode = unitMode,
                    side = side,
                    execution = planned.selectedExecutionOption.orEmpty(),
                    slotPurpose = intent.name,
                    quantityConvention = convention,
                )
                val measurements = workIndices.zip(sideSets).mapNotNull { (index, actual) ->
                    val plannedSet = planned.sets[index]
                    val recipeSet = recipeWorkSets.getOrNull(index)
                    val range = plannedSet.targetRepsRange?.let { it.min to it.max }
                        ?: plannedSet.targetReps?.let { it to it }
                        ?: recipeSet?.let { authored ->
                            val min = authored.repsMin ?: authored.reps
                            val max = authored.repsMax ?: authored.reps
                            if (min != null && max != null) min to max else null
                        }
                        ?: return@mapNotNull null
                    val targetRir = plannedSet.targetRIR ?: recipeSet?.rir
                    val actualRir = actualRirOf(actual)
                    WorkSetObservation(
                        reps = actual.reps,
                        minimumReps = range.first,
                        maximumReps = range.second,
                        targetRir = targetRir,
                        actualRir = actualRir,
                        actualLoadKg = actual.recordedPayloadV3?.let { payload ->
                            when (loadMode) {
                                LoadModeV2.LOAD, LoadModeV2.LASTRE -> payload.externalLoad ?: actual.weight.takeIf { it > 0.0 }
                                LoadModeV2.ASSISTED -> payload.assistedLoad ?: actual.weight.takeIf { it > 0.0 }
                                LoadModeV2.BODYWEIGHT -> null
                            }
                        } ?: if (actual.recordedPayloadV3 == null && loadMode != LoadModeV2.BODYWEIGHT) {
                            actual.weight.takeIf { it > 0.0 }
                        } else {
                            null
                        },
                    )
                }
                if (measurements.size != workIndices.size) return@mapNotNull null
                val targetRirKnown = measurements.all { it.targetRir != null }
                // F-08a: la carga de la exposición es la de la PRIMERA serie de trabajo (la
                // elegida por el atleta); una bajada posterior no se convierte en la carga
                // capturada. `uniformLoad` marca si todas las series usaron esa misma carga.
                val workLoads = measurements.map { it.actualLoadKg }
                val firstLoad = workLoads.firstOrNull()
                val uniformLoad = workLoads.all { load ->
                    if (firstLoad == null) load == null else load != null && abs(load - firstLoad) <= LOAD_TOLERANCE_KG
                }
                Exposure(
                    identity = identity,
                    logId = log.id,
                    sessionId = log.sessionId,
                    date = log.date,
                    cycle = cycle,
                    deload = deload,
                    uniformLoad = uniformLoad,
                    complete = true,
                    allSetsAtTop = measurements.all { it.reps >= it.maximumReps && it.targetRir != null && it.actualRir != null && it.actualRir >= it.targetRir },
                    allRirAtTarget = targetRirKnown && measurements.all { it.actualRir != null && it.actualRir >= it.targetRir!! },
                    belowMinimumOrRir = measurements.any { it.reps < it.minimumReps || (it.targetRir != null && it.actualRir != null && it.actualRir < it.targetRir) },
                    currentLoadKg = firstLoad,
                    topReps = measurements.minOf { it.maximumReps },
                    minReps = measurements.maxOf { it.minimumReps },
                )
            }
        }
    }

    /** F-08c: drop/rest-pause/AMRAP vivos distorsionan reps, carga y RIR: esa exposición no cuenta. */
    private fun hasLiveTechnique(set: CompletedSet): Boolean =
        set.dropSets.isNotEmpty() || set.restPauses.isNotEmpty() || set.amrapPerformed ||
            set.recordedPayloadV3?.let { payload ->
                payload.amrapPerformed || payload.techniques.any {
                    it == SetTechniqueV2.DROP_SET || it == SetTechniqueV2.REST_PAUSE || it == SetTechniqueV2.AMRAP
                }
            } == true

    /**
     * F-08c: una serie al fallo cuenta como RIR 0 (queda bajo el RIR objetivo); si no, el RIR
     * registrado o el valor del payload en modo RIR. Sin dato → null (no es éxito ni fallo).
     */
    private fun actualRirOf(set: CompletedSet): Int? {
        val payload = set.recordedPayloadV3
        val reachedFailure = set.isFailure ||
            set.actualIntensityMode == IntensityMode.FAILURE ||
            payload?.reachedFailure == true ||
            payload?.actualIntensityMode == IntensityMode.FAILURE
        if (reachedFailure) return 0
        // H-VERIF (decisión 12): una reserva que el atleta no movió (llegó rellena con lo
        // planificado) no es evidencia de «reserva cumplida»: sin dato, ni éxito ni fallo.
        if (payload?.intensityAdjusted == false) return null
        return set.rir ?: payload
            ?.takeIf { it.actualIntensityMode == IntensityMode.RIR }
            ?.actualIntensityValue?.roundToInt()
    }

    private fun resolveLoadMode(planned: List<ExerciseSet>, logged: List<CompletedSet>): LoadModeV2? {
        val actualModes = logged.mapNotNull { it.recordedPayloadV3?.loadInputMode }.distinct()
        if (actualModes.size > 1) return null
        val plannedModes = planned.mapNotNull { it.loadModeV2 }.distinct()
        if (plannedModes.size > 1) return null
        val actual = actualModes.singleOrNull()
        val expected = plannedModes.singleOrNull()
        if (actual != null && expected != null && actual != expected) return null
        return actual ?: expected
    }

    private fun resolveUnitMode(plannedExercise: Exercise, planned: List<ExerciseSet>, logged: List<CompletedSet>): UnitModeV2? {
        val actualModes = logged.mapNotNull { it.recordedPayloadV3?.unitMode }.distinct()
        if (actualModes.size > 1) return null
        val plannedModes = planned.mapNotNull { it.unitModeV2 }.distinct()
        if (plannedModes.size > 1) return null
        val actual = actualModes.singleOrNull()
        val plannedMode = plannedModes.singleOrNull() ?: when (plannedExercise.trainingMode) {
            TrainingMode.REPS -> UnitModeV2.REPS
            TrainingMode.TIME -> UnitModeV2.TIME
            TrainingMode.DISTANCE -> UnitModeV2.DISTANCE
            else -> null
        }
        if (actual != null && plannedMode != null && actual != plannedMode) return null
        return actual ?: plannedMode
    }

    private fun hasFuturePrescription(
        program: Program,
        sourceSessionId: String,
        cycle: Int,
        executedKeys: Set<String>,
        identity: NativeProgressionIdentity,
        recipe: TrainingPlanRecipe,
        intents: Map<String, Set<String>>,
    ): Boolean {
        val locations = ProgramHierarchyIndex(program).orderedSessions()
        val sourceIndex = locations.indexOfFirst { it.sessionId == sourceSessionId }
        if (sourceIndex < 0) return false
        val frozen = program.manualSessionOverrides.mapTo(mutableSetOf()) { it.sessionId }
        // H-DESCARGA: la semana de descarga no es un destino válido (mismo criterio que al aceptar).
        return locations.drop(sourceIndex + 1).any { location ->
            executionKey(cycle, location.sessionId) !in executedKeys && location.sessionId !in frozen &&
                !isDeloadWeek(recipe, location.hierarchy.week) &&
                location.session.allExercises().any { exerciseMatches(it, identity, intents) }
        }
    }

    /**
     * F-01: la primera carga elegida a mano se conserva para las sesiones futuras del
     * mismo ciclo cuyo peso sigue SIN RESOLVER: marcador de carga manual o, en un plan
     * propio recién generado (que no emite referencias PENDING), peso nulo de un
     * ejercicio con progresión nativa gestionada. Nunca pisa una carga ya resuelta.
     */
    private fun captureManualLoadForFuture(
        program: Program,
        exposure: Exposure,
        executedKeys: Set<String>,
        intents: Map<String, Set<String>>,
        nowMs: Long,
    ): Program {
        val loadKg = exposure.currentLoadKg?.takeIf { it > 0.0 } ?: return program
        val locations = ProgramHierarchyIndex(program).orderedSessions()
        val sourceIndex = locations.indexOfFirst { it.sessionId == exposure.sessionId }
        if (sourceIndex < 0) return program
        val frozen = program.manualSessionOverrides.mapTo(mutableSetOf()) { it.sessionId }
        val targets = locations.drop(sourceIndex + 1).filter { location ->
            executionKey(exposure.cycle, location.sessionId) !in executedKeys && location.sessionId !in frozen &&
                location.session.allExercises().any { exercise ->
                    exerciseMatches(exercise, exposure.identity, intents) &&
                        exercise.sets.any { isProgressionWorkSet(it) && isLoadUnresolved(exercise, it, exposure.identity.side) }
                }
        }
        if (targets.isEmpty()) return program
        val targetIds = targets.mapTo(mutableSetOf()) { it.sessionId }
        val captured = mutableListOf<Exercise>()
        val updatedMacros = program.macrocycles.map { macro ->
            macro.copy(blocks = macro.blocks.map { block ->
                block.copy(mesocycles = block.mesocycles.map { meso ->
                    meso.copy(weeks = meso.weeks.map { week ->
                        week.copy(sessions = week.sessions.map { session ->
                            if (session.id !in targetIds) return@map session
                            session.mapExercises { exercise ->
                                if (!exerciseMatches(exercise, exposure.identity, intents)) return@mapExercises exercise
                                val updated = captureManualLoad(exercise, exposure.identity, loadKg, nowMs)
                                if (updated != exercise) captured += updated
                                updated
                            }
                        })
                    })
                })
            })
        }
        if (captured.isEmpty()) return program
        return program.copy(
            macrocycles = updatedMacros,
            exerciseLoadReferences = mergeLoadReferencePool(
                current = program.exerciseLoadReferences,
                updatedExercises = captured,
                identity = exposure.identity,
            ),
        )
    }

    /**
     * Escribe [loadKg] como carga de trabajo de la identidad. Con [onlyUnresolved] (captura de la
     * primera carga manual) solo toca series sin resolver; sin él (arrastre de ciclo) fija la
     * última referencia en todas las series de trabajo. Idempotente: repetir con la misma carga
     * devuelve el mismo ejercicio, conservando `capturedAtMs`.
     */
    private fun captureManualLoad(
        exercise: Exercise,
        identity: NativeProgressionIdentity,
        loadKg: Double,
        nowMs: Long,
        onlyUnresolved: Boolean = true,
    ): Exercise {
        var capturedFirstWorkSet: ExerciseSet? = null
        val sets = exercise.sets.map { set ->
            if (!isProgressionWorkSet(set)) return@map set
            if (onlyUnresolved && !isLoadUnresolved(exercise, set, identity.side)) return@map set
            val updated = when (identity.side) {
                "left", "right" -> {
                    val left = set.leftTarget ?: set.toUnilateralTarget()
                    val right = set.rightTarget ?: set.toUnilateralTarget()
                    set.copy(
                        leftTarget = if (identity.side == "left") left.copy(weight = loadKg) else left,
                        rightTarget = if (identity.side == "right") right.copy(weight = loadKg) else right,
                        manualLoadRequiredSides = set.manualLoadRequiredSides - identity.side,
                    )
                }
                else -> set.copy(
                    weight = loadKg,
                    leftTarget = set.leftTarget?.copy(weight = loadKg),
                    rightTarget = set.rightTarget?.copy(weight = loadKg),
                    manualLoadRequiredSides = set.manualLoadRequiredSides - identity.side,
                )
            }
            if (capturedFirstWorkSet == null) capturedFirstWorkSet = updated
            updated
        }
        if (sets == exercise.sets && onlyUnresolved) return exercise
        val ref = exercise.loadReference
            ?.takeIf {
                it.configurationId == identity.configurationId &&
                    it.quantityConvention == identity.quantityConvention &&
                    (it.side == null || it.side == identity.side)
            }
        val sameCapture = ref?.takeIf { existing ->
            existing.state == PlanLoadReferenceState.CAPTURED &&
                existing.capturedLoadKg?.let { abs(it - loadKg) <= LOAD_TOLERANCE_KG } == true
        }
        val capturedReference = sameCapture ?: (ref ?: PlanLoadReference(
            kind = if (identity.loadMode in setOf(LoadModeV2.ASSISTED, LoadModeV2.LASTRE)) {
                PlanLoadReferenceKind.BODYWEIGHT_EXTERNAL
            } else {
                PlanLoadReferenceKind.OBSERVED_WORKING_SET
            },
            configurationId = identity.configurationId,
            quantityConvention = identity.quantityConvention,
            side = identity.side,
            repMin = capturedFirstWorkSet?.targetRepsRange?.min ?: capturedFirstWorkSet?.targetReps,
            repMax = capturedFirstWorkSet?.targetRepsRange?.max ?: capturedFirstWorkSet?.targetReps,
        )).copy(
            state = PlanLoadReferenceState.CAPTURED,
            capturedLoadKg = loadKg,
            capturedAtMs = nowMs,
        )
        if (sets == exercise.sets && exercise.loadReference == capturedReference) return exercise
        return exercise.copy(sets = sets, loadReference = capturedReference)
    }

    private fun isProgressionWorkSet(set: ExerciseSet): Boolean =
        !set.isEmptySlot && !set.isCalibrator && !set.isTopSet && !set.isDropSet && !set.isRestPause && !set.isAmrap

    /** Mismo criterio que la UI de entrenamiento: marcador manual o peso nulo de un ejercicio gestionado. */
    private fun isLoadUnresolved(exercise: Exercise, set: ExerciseSet, side: String): Boolean =
        side in set.manualLoadRequiredSides ||
            (exercise.nativeProgressionManaged && planWeightForSide(set, side) == null)

    private fun planWeightForSide(set: ExerciseSet, side: String): Double? = when (side) {
        "left" -> set.leftTarget?.weight ?: if (set.leftTarget == null) set.weight else null
        "right" -> set.rightTarget?.weight ?: if (set.rightTarget == null) set.weight else null
        else -> set.weight
    }

    private fun mergeLoadReferencePool(
        current: List<ExerciseLoadReference>,
        updatedExercises: Collection<Exercise>,
        identity: NativeProgressionIdentity,
    ): List<ExerciseLoadReference> {
        val byId = current.associateBy { it.exerciseId }.toMutableMap()
        updatedExercises.forEach { exercise ->
            val existing = byId[exercise.id]
            val retained = existing?.references.orEmpty().filterNot { reference ->
                reference.configurationId == identity.configurationId &&
                    reference.quantityConvention == identity.quantityConvention &&
                    (reference.side == null || reference.side == identity.side)
            }
            val updatedReference = exercise.loadReference?.takeIf { reference ->
                reference.configurationId == identity.configurationId &&
                    reference.quantityConvention == identity.quantityConvention &&
                    (reference.side == null || reference.side == identity.side)
            }
            val references = retained + listOfNotNull(updatedReference)
            if (references.isEmpty()) {
                byId.remove(exercise.id)
            } else {
                byId[exercise.id] = (existing ?: ExerciseLoadReference(exerciseId = exercise.id)).copy(
                    references = references,
                )
            }
        }
        return byId.values.toList()
    }

    /**
     * H-IDENT: el ejercicio coincide con la identidad por configuración, ejecución, lado, unidad,
     * modo de carga y convención, y por PROPÓSITO del slot (según [intents]); ya no por día ni
     * posición, así el mismo ejercicio sube junto en todos los días de la semana.
     */
    private fun exerciseMatches(
        exercise: Exercise,
        identity: NativeProgressionIdentity,
        intents: Map<String, Set<String>>,
    ): Boolean {
        if (exercise.catalogConfigurationId != identity.configurationId ||
            exercise.selectedExecutionOption.orEmpty() != identity.execution
        ) return false
        val dayId = exercise.recipeDayId ?: return false
        val slotId = exercise.recipeSlotId ?: return false
        if (identity.slotPurpose !in intents["$dayId|$slotId"].orEmpty()) return false
        val exerciseIsUnilateral = exercise.isUnilateral || exercise.unilateralMode != UnilateralMode.BILATERAL
        if ((identity.side == "bilateral") == exerciseIsUnilateral || identity.side !in setOf("bilateral", "left", "right")) {
            return false
        }
        val sets = exercise.sets.filterNot { it.isEmptySlot || it.isCalibrator || it.isTopSet || it.isDropSet || it.isRestPause || it.isAmrap }
        if (sets.isEmpty()) return false
        val unitMode = resolveUnitMode(exercise, sets, emptyList()) ?: return false
        if (unitMode != identity.unitMode) return false
        val declaredLoadModes = sets.mapNotNull { it.loadModeV2 }.distinct()
        if (declaredLoadModes.size > 1 || declaredLoadModes.singleOrNull()?.let { it != identity.loadMode } == true) {
            return false
        }
        val conventions = sets.map { set ->
            set.loadQuantityConvention.takeIf { it != LoadQuantityConvention.UNSPECIFIED }
                ?: exercise.loadQuantityConvention
        }.distinct()
        return conventions.size == 1 && conventions.single() == identity.quantityConvention
    }

    private fun makeProposal(
        program: Program,
        identity: NativeProgressionIdentity,
        kind: NativeProgressionProposalKind,
        recent: List<Exposure>,
        targetConfigurationId: String? = null,
        targetLoadKg: Double? = null,
        explanation: String,
        nowMs: Long,
    ) = NativeProgressionProposal(
        proposalId = proposalId(program.id, identity, recent.map { it.logId }, kind),
        kind = kind,
        identity = identity,
        sourceSessionId = recent.last().sessionId,
        sourceLogIds = recent.map { it.logId },
        targetConfigurationId = targetConfigurationId,
        targetLoadKg = targetLoadKg?.takeIf { it > 0.0 },
        explanation = explanation,
        createdAtMs = nowMs,
    )

    private fun proposalKindForOutcome(
        branch: ProgressionBranch,
        outcome: NativeProgressionOutcome,
        recent: List<Exposure>,
    ): NativeProgressionProposalKind? = when {
        branch == ProgressionBranch.LOAD && outcome == NativeProgressionOutcome.PROPOSE_INCREMENT ->
            NativeProgressionProposalKind.INCREASE_LOAD
        branch == ProgressionBranch.LOAD && outcome == NativeProgressionOutcome.KEEP_OR_REGRESS && recent.all { it.belowMinimumOrRir } ->
            NativeProgressionProposalKind.REDUCE_LOAD
        branch == ProgressionBranch.BODYWEIGHT_VARIANT && outcome == NativeProgressionOutcome.PROPOSE_INCREMENT ->
            NativeProgressionProposalKind.HARDER_BODYWEIGHT_VARIANT
        branch == ProgressionBranch.BODYWEIGHT_VARIANT && outcome == NativeProgressionOutcome.KEEP_OR_REGRESS && recent.all { it.belowMinimumOrRir } ->
            NativeProgressionProposalKind.EASIER_BODYWEIGHT_VARIANT
        else -> null
    }

    /** Rama de progresión de UNA identidad (§12.4): variante corporal o doble progresión de carga. */
    private enum class ProgressionBranch { LOAD, BODYWEIGHT_VARIANT }

    /**
     * F-06/F-08d: la rama la decide la identidad, no la estrategia global de la receta, y
     * siempre exige repeticiones (tiempo/distancia no se comparan contra un rango de reps).
     * Una identidad con carga en un plan solo corporal no progresa por esta ruta.
     */
    private fun progressionBranchFor(
        strategy: NativeProgressionStrategy,
        identity: NativeProgressionIdentity,
    ): ProgressionBranch? = when {
        strategy == NativeProgressionStrategy.NONE -> null
        identity.unitMode != UnitModeV2.REPS -> null
        identity.loadMode == LoadModeV2.BODYWEIGHT -> ProgressionBranch.BODYWEIGHT_VARIANT
        strategy == NativeProgressionStrategy.REP_RANGE_THEN_LOAD -> ProgressionBranch.LOAD
        else -> null
    }

    private fun proposalMatchesStrategy(
        kind: NativeProgressionProposalKind,
        identity: NativeProgressionIdentity,
        strategy: NativeProgressionStrategy,
    ): Boolean {
        val branch = progressionBranchFor(strategy, identity) ?: return false
        return when (kind) {
            NativeProgressionProposalKind.INCREASE_LOAD, NativeProgressionProposalKind.REDUCE_LOAD ->
                branch == ProgressionBranch.LOAD
            NativeProgressionProposalKind.HARDER_BODYWEIGHT_VARIANT, NativeProgressionProposalKind.EASIER_BODYWEIGHT_VARIANT ->
                branch == ProgressionBranch.BODYWEIGHT_VARIANT
        }
    }

    /**
     * F-08a: la ventana solo es comparable si todas sus exposiciones usaron la misma carga de
     * trabajo (primera serie; tolerancia 0,0001 kg). Corporal: ninguna lleva carga y coinciden.
     */
    private fun sameLoadAcrossWindow(recent: List<Exposure>): Boolean {
        val loads = recent.map { it.currentLoadKg }
        if (loads.all { it == null }) return true
        val known = loads.filterNotNull()
        if (known.size != loads.size) return false
        return known.maxOf { it } - known.minOf { it } <= LOAD_TOLERANCE_KG
    }

    private fun appendProposalOnce(program: Program, proposal: NativeProgressionProposal): Program {
        if (program.nativeProgressionProposals.any { it.proposalId == proposal.proposalId } ||
            program.nativeProgressionAudit.any { it.proposalId == proposal.proposalId }
        ) return program
        return program.copy(nativeProgressionProposals = program.nativeProgressionProposals + proposal)
    }

    private fun appendNoticeOnce(
        program: Program,
        proposalId: String,
        kind: NativeProgressionProposalKind,
        nowMs: Long,
        reason: String,
        identity: NativeProgressionIdentity,
        sourceLogIds: List<String>,
    ): Program {
        if (program.nativeProgressionProposals.any { it.proposalId == proposalId } ||
            program.nativeProgressionAudit.any { it.proposalId == proposalId }
        ) return program
        return program.copy(nativeProgressionAudit = program.nativeProgressionAudit + NativeProgressionResolution(
            proposalId = proposalId,
            status = NativeProgressionResolutionStatus.EXPIRED,
            kind = kind,
            resolvedAtMs = nowMs,
            reason = reason,
            userFacingNotice = true,
            identity = identity,
            sourceLogIds = sourceLogIds,
        ))
    }

    private fun expire(
        program: Program,
        proposal: NativeProgressionProposal,
        remaining: List<NativeProgressionProposal>,
        nowMs: Long,
        reason: String,
        userFacingNotice: Boolean = true,
    ) = program.copy(
        nativeProgressionProposals = remaining,
        nativeProgressionAudit = program.nativeProgressionAudit + NativeProgressionResolution(
            proposalId = proposal.proposalId,
            status = NativeProgressionResolutionStatus.EXPIRED,
            kind = proposal.kind,
            resolvedAtMs = nowMs,
            reason = reason,
            userFacingNotice = userFacingNotice,
            identity = proposal.identity,
            sourceLogIds = proposal.sourceLogIds,
        ),
    )

    private fun applyLoadProposal(exercise: Exercise, proposal: NativeProgressionProposal, nowMs: Long): Exercise {
        val side = proposal.identity.side
        val requiresManualLoad = proposal.targetLoadKg == null
        val updatedSets = exercise.sets.map { set ->
            if (set.isEmptySlot || set.isCalibrator || set.isTopSet || set.isDropSet || set.isRestPause || set.isAmrap) return@map set
            val target = proposal.targetLoadKg
            val manualSides = if (requiresManualLoad) {
                set.manualLoadRequiredSides + side
            } else {
                set.manualLoadRequiredSides - side
            }
            when (side) {
                "left", "right" -> {
                    val existingLeft = set.leftTarget ?: set.toUnilateralTarget()
                    val existingRight = set.rightTarget ?: set.toUnilateralTarget()
                    set.copy(
                        weight = null,
                        leftTarget = if (proposal.identity.side == "left") existingLeft.copy(weight = target) else existingLeft,
                        rightTarget = if (proposal.identity.side == "right") existingRight.copy(weight = target) else existingRight,
                        manualLoadRequiredSides = manualSides,
                    )
                }
                else -> set.copy(
                    weight = target,
                    leftTarget = set.leftTarget?.copy(weight = target),
                    rightTarget = set.rightTarget?.copy(weight = target),
                    manualLoadRequiredSides = manualSides,
                )
            }
        }
        val workSet = updatedSets.firstOrNull { !it.isEmptySlot && !it.isCalibrator && !it.isTopSet && !it.isDropSet && !it.isRestPause && !it.isAmrap }
        val loadReference = PlanLoadReference(
            kind = if (proposal.identity.loadMode in setOf(LoadModeV2.ASSISTED, LoadModeV2.LASTRE)) {
                PlanLoadReferenceKind.BODYWEIGHT_EXTERNAL
            } else {
                PlanLoadReferenceKind.OBSERVED_WORKING_SET
            },
            configurationId = proposal.identity.configurationId,
            quantityConvention = proposal.identity.quantityConvention,
            side = side,
            repMin = workSet?.targetRepsRange?.min ?: workSet?.targetReps,
            repMax = workSet?.targetRepsRange?.max ?: workSet?.targetReps,
            state = if (requiresManualLoad) PlanLoadReferenceState.PENDING else PlanLoadReferenceState.CAPTURED,
            capturedLoadKg = proposal.targetLoadKg,
            capturedAtMs = nowMs.takeIf { !requiresManualLoad },
        )
        return exercise.copy(sets = updatedSets, loadReference = loadReference)
    }

    private fun ExerciseSet.toUnilateralTarget() = UnilateralTarget(
        weight = weight,
        targetReps = targetReps,
        targetRepsRange = targetRepsRange,
        targetDuration = targetDuration,
        targetRPE = targetRPE,
        targetRIR = targetRIR,
        intensityMode = intensityMode,
    )

    private fun Session.mapExercises(transform: (Exercise) -> Exercise): Session = copy(
        exercises = exercises.map(transform),
        parts = parts.map { part -> part.copy(exercises = part.exercises.map(transform)) },
    )

    /** Resultado de buscar el siguiente paso de carga que el material declarado SÍ permite. */
    private sealed interface NextLoad {
        data class Reachable(val kg: Double) : NextLoad

        /** Sin material declarado que responda: el atleta elige la carga en el entrenamiento. */
        data object Unknown : NextLoad

        /** Material declarado, pero sin un paso alcanzable en la dirección pedida (tope del material). */
        data class AtLimit(val detail: String) : NextLoad
    }

    /**
     * F-03/F-11: el siguiente paso sale del equipo REAL del catálogo ([equipmentId]) y del
     * inventario declarado: pares de mancuernas completos, kettlebells, rango de la máquina de
     * esa configuración, o una carga de barra realmente formable con los discos (y nunca por
     * debajo de la barra vacía). Sin material declarado → [NextLoad.Unknown]; con material pero
     * sin paso en esa dirección → [NextLoad.AtLimit], nunca «sube ligeramente» sin salida.
     */
    private fun knownNextLoad(
        identity: NativeProgressionIdentity,
        currentLoadKg: Double,
        inventory: EquipmentInventory?,
        increasing: Boolean,
        equipmentId: String?,
    ): NextLoad {
        val stock = inventory ?: return NextLoad.Unknown
        val kind = NativeLoadConventions.stockKindFor(equipmentId, identity.configurationId)
        val perImplement = identity.quantityConvention == LoadQuantityConvention.PER_IMPLEMENT
        val direction = if (increasing) "más pesado" else "más ligero"
        return when {
            kind == NativeLoadConventions.StockKind.DUMBBELL && perImplement -> {
                if (stock.dumbbells.isEmpty()) return NextLoad.Unknown
                stepWithinStock(
                    weights = stock.dumbbells.filter { it.pairAvailable }.map { it.weightPerUnitKg },
                    currentLoadKg = currentLoadKg,
                    increasing = increasing,
                    limitDetail = "el material declarado no tiene un par de mancuernas $direction que ${formatKg(currentLoadKg)} kg por mancuerna.",
                )
            }
            kind == NativeLoadConventions.StockKind.KETTLEBELL && perImplement -> {
                if (stock.kettlebells.isEmpty()) return NextLoad.Unknown
                stepWithinStock(
                    weights = stock.kettlebells.map { it.weightKg },
                    currentLoadKg = currentLoadKg,
                    increasing = increasing,
                    limitDetail = "el material declarado no tiene una kettlebell $direction que ${formatKg(currentLoadKg)} kg.",
                )
            }
            kind == NativeLoadConventions.StockKind.MACHINE -> {
                val range = stock.machines.firstOrNull { it.configurationId == identity.configurationId }
                    ?: return NextLoad.Unknown
                if (range.incrementKg <= 0.0 || currentLoadKg < range.minLoadKg - LOAD_TOLERANCE_KG) return NextLoad.Unknown
                val next = if (increasing) {
                    val index = kotlin.math.floor((currentLoadKg - range.baseLoadKg) / range.incrementKg + LOAD_TOLERANCE_KG).toInt() + 1
                    range.baseLoadKg + index * range.incrementKg
                } else {
                    val index = kotlin.math.ceil((currentLoadKg - range.baseLoadKg) / range.incrementKg - LOAD_TOLERANCE_KG).toInt() - 1
                    range.baseLoadKg + index * range.incrementKg
                }
                val maxLoad = range.maxLoadKg
                when {
                    next < range.minLoadKg - LOAD_TOLERANCE_KG ->
                        NextLoad.AtLimit("la máquina declarada no baja de ${formatKg(range.minLoadKg)} kg.")
                    maxLoad != null && next > maxLoad + LOAD_TOLERANCE_KG ->
                        NextLoad.AtLimit("la máquina declarada llega como máximo a ${formatKg(maxLoad)} kg.")
                    next > 0.0 -> NextLoad.Reachable(next)
                    else -> NextLoad.AtLimit("la máquina declarada no ofrece una carga positiva por debajo de ${formatKg(currentLoadKg)} kg.")
                }
            }
            kind == NativeLoadConventions.StockKind.BARBELL &&
                identity.quantityConvention == LoadQuantityConvention.TOTAL_EXTERNAL -> {
                val plates = stock.plates.filter { it.weightKg > 0.0 && (it.countPerSide == null || it.countPerSide > 0) }
                if (plates.isEmpty()) return NextLoad.Unknown
                nextBarbellLoad(plates, stock.resolvedBarbellWeightKg(), currentLoadKg, increasing)
            }
            else -> NextLoad.Unknown
        }
    }

    private fun stepWithinStock(
        weights: List<Double>,
        currentLoadKg: Double,
        increasing: Boolean,
        limitDetail: String,
    ): NextLoad {
        val candidate = if (increasing) {
            weights.filter { it > currentLoadKg + LOAD_TOLERANCE_KG }.minOrNull()
        } else {
            weights.filter { it < currentLoadKg - LOAD_TOLERANCE_KG }.maxOrNull()
        }
        return if (candidate != null && candidate > 0.0) NextLoad.Reachable(candidate) else NextLoad.AtLimit(limitDetail)
    }

    /**
     * F-11: la menor carga de barra FORMABLE con los discos declarados que cambie la carga actual
     * en la dirección pedida. Subconjuntos de discos por lado (con sus cantidades), en miligramos
     * enteros para evitar ruido de coma flotante; nunca baja del peso de la barra vacía.
     */
    private fun nextBarbellLoad(
        plates: List<PlateStock>,
        barKg: Double,
        currentLoadKg: Double,
        increasing: Boolean,
    ): NextLoad {
        val unit = 1000.0
        val currentSideMilli = ((currentLoadKg - barKg) / 2.0 * unit).roundToLong()
        val kinds = plates.groupBy { (it.weightKg * unit).roundToLong() }
            .filterKeys { it > 0L }
            .map { (milli, stocks) ->
                val unlimited = stocks.any { it.countPerSide == null }
                milli to (if (unlimited) Int.MAX_VALUE else stocks.sumOf { it.countPerSide ?: 0 })
            }
        if (kinds.isEmpty()) return NextLoad.Unknown
        val largestPlateMilli = kinds.maxOf { it.first }
        val upper = currentSideMilli.coerceAtLeast(0L) + largestPlateMilli
        if (upper > MAX_PLATE_SEARCH_MILLI) return NextLoad.Unknown
        val limit = upper.toInt()
        val reachable = BooleanArray(limit + 1)
        reachable[0] = true
        kinds.forEach { (weight, maxCount) ->
            val w = weight.toInt()
            val used = IntArray(limit + 1)
            for (sum in w..limit) {
                if (!reachable[sum] && reachable[sum - w] && used[sum - w] < maxCount) {
                    reachable[sum] = true
                    used[sum] = used[sum - w] + 1
                }
            }
        }
        fun loadOf(sideMilli: Int): Double = ((barKg + 2.0 * sideMilli / unit) * unit).roundToLong() / unit
        if (increasing) {
            val start = (currentSideMilli + 1).coerceAtLeast(0L).toInt()
            val next = (start..limit).firstOrNull { reachable[it] }
                ?: return NextLoad.AtLimit(
                    "con los discos declarados no se puede formar una carga de barra mayor que ${formatKg(currentLoadKg)} kg.",
                )
            return NextLoad.Reachable(loadOf(next))
        }
        if (currentSideMilli <= 0L) {
            return NextLoad.AtLimit(
                "la carga ya es la de la barra vacía (${formatKg(barKg)} kg) y no se puede bajar más.",
            )
        }
        val below = (minOf(currentSideMilli - 1, limit.toLong()).toInt() downTo 0).firstOrNull { reachable[it] }
            ?: return NextLoad.AtLimit("con los discos declarados no se puede formar una carga de barra menor que ${formatKg(currentLoadKg)} kg.")
        return NextLoad.Reachable(loadOf(below))
    }

    private fun loadStepIncreasesResistance(
        identity: NativeProgressionIdentity,
        progressionIncrease: Boolean,
    ): Boolean = if (isAssistanceLoad(identity)) {
        !progressionIncrease
    } else {
        progressionIncrease
    }

    private fun isAssistanceLoad(identity: NativeProgressionIdentity): Boolean =
        identity.loadMode == LoadModeV2.ASSISTED ||
            identity.quantityConvention == LoadQuantityConvention.ASSISTANCE

    private fun proposalId(
        programId: String,
        identity: NativeProgressionIdentity,
        logIds: List<String>,
        kind: NativeProgressionProposalKind,
    ): String {
        val key = listOf(
            programId,
            identity.recipeId,
            identity.recipeContentVersion,
            identity.recipeDayId,
            identity.recipeSlotId,
            identity.configurationId,
            identity.loadMode.name,
            identity.unitMode.name,
            identity.side,
            identity.execution,
            identity.slotPurpose,
            identity.quantityConvention.name,
            kind.name,
            logIds.sorted().joinToString(","),
        ).joinToString("|")
        return "native-${UUID.nameUUIDFromBytes(key.toByteArray(Charsets.UTF_8))}"
    }

    private fun normalizeSide(side: String?): String = when (side?.trim()?.lowercase()) {
        "left", "l" -> "left"
        "right", "r" -> "right"
        else -> "bilateral"
    }

    /** Kilos en texto para el atleta: coma decimal y sin ceros sobrantes («62,5»). */
    private fun formatKg(value: Double): String = NativeProgressionText.formatKg(value)

    private data class WorkSetObservation(
        val reps: Int,
        val minimumReps: Int,
        val maximumReps: Int,
        val targetRir: Int?,
        val actualRir: Int?,
        val actualLoadKg: Double?,
    )

    private data class Exposure(
        val identity: NativeProgressionIdentity,
        val logId: String,
        val sessionId: String,
        val date: String,
        /** Ciclo del log (F-02); distingue la misma sesión entrenada en ciclos distintos. */
        val cycle: Int,
        /** Exposición de una semana de descarga: nunca cuenta como éxito ni como fallo (F-08b). */
        val deload: Boolean,
        /** Todas las series de trabajo usaron la misma carga que la primera (F-08a). */
        val uniformLoad: Boolean,
        val complete: Boolean,
        val allSetsAtTop: Boolean,
        val allRirAtTarget: Boolean,
        val belowMinimumOrRir: Boolean,
        val currentLoadKg: Double?,
        /** Tope del rango de repeticiones que alcanzaron todas las series (solo para el texto de la propuesta). */
        val topReps: Int? = null,
        /** Mínimo del rango de repeticiones de las series de trabajo (solo para el texto de la propuesta). */
        val minReps: Int? = null,
    )
}
