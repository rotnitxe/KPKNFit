package com.example.kpkn.domain.training

import com.example.kpkn.data.models.*

data class LoopProjection(
    val loop: Loop,
    val cycle: Int,
    val isPostponed: Boolean,
    val isCancelled: Boolean,
    val daysUntil: Int,
    val weekInCycle: Int,
)

enum class LoopIssueType {
    DUPLICATE_ID,
    INVALID_CADENCE,
    MISSING_MATERIALIZED_WEEK,
    ORPHAN_MATERIALIZED_WEEK,
    UNKNOWN_OCCURRENCE_LOOP,
    DUPLICATE_OCCURRENCE,
    STALE_OCCURRENCES,
    UNKNOWN_STATE_REFERENCE,
    CALENDARIZED_LOOP_STATE,
}

/** Diagnóstico estructural de la representación triplicada de loops. */
data class LoopIssue(
    val type: LoopIssueType,
    val message: String,
    val loopId: String? = null,
)

object LoopEngine {

    fun materializeLoopWeeks(program: Program): Program {
        if (!program.isSimpleProgram || program.simpleProgramKind != SimpleProgramKind.CYCLIC || program.loops.isEmpty()) return program
        val firstMacro = program.macrocycles.firstOrNull() ?: return program
        val firstBlock = firstMacro.blocks.firstOrNull() ?: return program
        val firstMeso = firstBlock.mesocycles.firstOrNull() ?: return program
        val existingLoopIds = program.loops.map { it.id }.toSet()
        val existingWeeksByLoop = firstBlock.mesocycles
            .flatMap { it.weeks }
            .filter { it.isLoopWeek && it.loopId != null }
            .associateBy { it.loopId }

        val loopWeeks = program.loops.map { loop ->
            val existing = existingWeeksByLoop[loop.id]
            ProgramWeek(
                id = existing?.id ?: "loop_week_${loop.id}",
                name = loop.title.ifBlank { getLoopTypeLabel(loop.type) },
                description = "Loop ${getLoopTypeLabel(loop.type)} · cada ${loop.repeatEveryXLoops.coerceAtLeast(1)} ciclos",
                sessions = (existing?.sessions ?: loop.sessions).ifEmpty {
                    listOf(
                        Session(
                            id = "loop_session_${loop.id}",
                            name = loop.title.ifBlank { getLoopTypeLabel(loop.type) },
                            dayOfWeek = loop.dayOfWeek?.coerceIn(1, 7) ?: 1,
                            isMainSession = true,
                        ),
                    )
                },
                isLoopWeek = true,
                loopId = loop.id,
            )
        }

        return syncOccurrences(
            program.copy(
                macrocycles = program.macrocycles.mapIndexed { macroIndex, macro ->
                    if (macroIndex != 0) macro
                    else macro.copy(
                        blocks = macro.blocks.mapIndexed { blockIndex, block ->
                            if (blockIndex != 0) block
                            else block.copy(
                                mesocycles = block.mesocycles.mapIndexed { mesoIndex, meso ->
                                    if (mesoIndex != 0) {
                                        meso.copy(weeks = meso.weeks.filterNot { it.isLoopWeek && it.loopId !in existingLoopIds })
                                    } else {
                                        val normalWeeks = meso.weeks.filterNot { it.isLoopWeek }
                                        meso.copy(weeks = normalWeeks + loopWeeks)
                                    }
                                }
                            )
                        }
                    )
                }
            )
        )
    }

    fun upsertLoop(program: Program, loop: Loop): Program {
        val exists = program.loops.any { it.id == loop.id }
        val nextLoops = if (exists) program.loops.map { if (it.id == loop.id) loop else it } else program.loops + loop
        return materializeLoopWeeks(program.copy(loops = nextLoops))
    }

    fun deleteLoop(program: Program, loopId: String): Program {
        val nextState = program.loopState?.copy(
            cancelled = program.loopState.cancelled.filterNot { it == loopId },
            postponed = program.loopState.postponed.filterNot { it.loopId == loopId },
            cancelledOccurrences = program.loopState.cancelledOccurrences.filterNot {
                it.startsWith("$loopId:")
            },
        )
        return syncOccurrences(
            program.copy(
                loops = program.loops.filterNot { it.id == loopId },
                loopState = nextState,
                loopOccurrences = program.loopOccurrences.filterNot { it.loopId == loopId },
                macrocycles = program.macrocycles.map { macro ->
                    macro.copy(
                        blocks = macro.blocks.map { block ->
                            block.copy(
                                mesocycles = block.mesocycles.map { meso ->
                                    meso.copy(weeks = meso.weeks.filterNot { it.isLoopWeek && it.loopId == loopId })
                                }
                            )
                        }
                    )
                },
            )
        )
    }

    fun getCycleLength(program: Program): Int {
        val block = program.macrocycles.firstOrNull()?.blocks?.firstOrNull() ?: return 1
        return block.mesocycles.sumOf { meso -> meso.weeks.count { !it.isLoopWeek } }.takeIf { it > 0 } ?: 1
    }

    fun getCurrentCycle(program: Program): Int {
        return program.runState?.cycleNumber
            ?: program.loopState?.currentCycle?.takeIf { it > 0 }
            ?: 0
    }

    fun getDaysIntoCycle(program: Program, daysSinceStart: Int): Int {
        val cycleLength = getCycleLength(program)
        val cycleDays = cycleLength * (program.weekDays ?: 7)
        return if (cycleDays > 0) daysSinceStart % cycleDays else 0
    }

    fun projectLoops(
        program: Program,
        fromCycle: Int,
        lookAheadCycles: Int = 12,
    ): List<LoopProjection> {
        if (!program.isSimpleProgram || program.simpleProgramKind != SimpleProgramKind.CYCLIC) return emptyList()
        val loops = program.loops
        if (loops.isEmpty()) return emptyList()

        val cycleLength = getCycleLength(program)
        val cycleDays = cycleLength * (program.weekDays ?: 7)
        val postponed = program.loopState?.postponed ?: emptyList()
        val cancelled = (program.loopState?.cancelled ?: emptyList()).toSet()
        val cancelledOccurrences = (program.loopState?.cancelledOccurrences ?: emptyList()).toSet()
        val projections = mutableListOf<LoopProjection>()

        for (cycle in fromCycle until fromCycle + lookAheadCycles) {
            for (loop in loops) {
                if (loop.id in cancelled) continue

                val occurrenceKey = occurrenceKey(loop.id, cycle)
                val isActive = cycle > 0 && cycle % loop.repeatEveryXLoops == 0
                val postponement = postponed.find { it.loopId == loop.id && it.fromCycle == cycle }

                if (isActive && postponement == null && occurrenceKey !in cancelledOccurrences) {
                    projections.add(
                        LoopProjection(
                            loop = loop,
                            cycle = cycle,
                            isPostponed = false,
                            isCancelled = false,
                            daysUntil = (cycle - fromCycle) * cycleDays,
                            weekInCycle = cycleLength,
                        )
                    )
                }

                val deferredHere = postponed.find { it.loopId == loop.id && it.toCycle == cycle }
                if (deferredHere != null && occurrenceKey !in cancelledOccurrences) {
                    projections.add(
                        LoopProjection(
                            loop = loop,
                            cycle = cycle,
                            isPostponed = true,
                            isCancelled = false,
                            daysUntil = (cycle - fromCycle) * cycleDays,
                            weekInCycle = cycleLength,
                        )
                    )
                }
            }
        }

        return projections.sortedWith(
            compareBy<LoopProjection> { it.cycle }
                .thenByDescending { it.loop.priority ?: 0 }
        )
    }

    fun detectLoopCollisions(projections: List<LoopProjection>): Map<Int, List<LoopProjection>> {
        return projections
            .groupBy { it.cycle }
            .filter { it.value.size > 1 }
    }

    fun postponeLoop(program: Program, loopId: String, fromCycle: Int): Program {
        val state = program.loopState ?: LoopState()
        val updated = program.copy(
            loopState = state.copy(
                postponed = state.postponed + PostponedLoop(
                    loopId = loopId,
                    fromCycle = fromCycle,
                    toCycle = fromCycle + 1,
                )
            )
        )
        return syncOccurrences(updated)
    }

    fun nextActionableOccurrence(program: Program, loopId: String): LoopOccurrence? {
        val synced = if (program.loopOccurrences.any { it.loopId == loopId }) program else syncOccurrences(program)
        return synced.loopOccurrences
            .filter {
                it.loopId == loopId &&
                    it.status != LoopStatus.CANCELLED &&
                    it.status != LoopStatus.COMPLETED &&
                    it.status != LoopStatus.POSTPONED
            }
            .minByOrNull { it.scheduledCycle }
    }

    fun nextScheduledCycle(program: Program, loopId: String): Int? {
        val loop = program.loops.firstOrNull { it.id == loopId } ?: return null
        val cadence = loop.repeatEveryXLoops.coerceAtLeast(1)
        nextActionableOccurrence(program, loopId)?.scheduledCycle?.let { return it }
        val current = getCurrentCycle(program).coerceAtLeast(0)
        val next = ((current / cadence) + 1) * cadence
        return next.coerceAtLeast(cadence)
    }

    fun postponeOccurrence(program: Program, occurrenceId: String): Program {
        val occ = program.loopOccurrences.firstOrNull { it.id == occurrenceId }
            ?: return program
        return ProgramProgressEngine.reconcileCursorAfterLoopChange(
            postponeLoop(program, occ.loopId, occ.scheduledCycle),
        )
    }

    fun postponeNextOccurrence(program: Program, loopId: String): Program {
        val fromCycle = nextScheduledCycle(program, loopId) ?: return program
        return ProgramProgressEngine.reconcileCursorAfterLoopChange(
            postponeLoop(program, loopId, fromCycle),
        )
    }

    fun cancelOccurrence(program: Program, occurrenceId: String): Program {
        val occ = program.loopOccurrences.firstOrNull { it.id == occurrenceId } ?: return program
        val state = program.loopState ?: LoopState()
        val key = occurrenceKey(occ.loopId, occ.scheduledCycle)
        if (key in state.cancelledOccurrences) return program
        val updated = program.copy(
            loopState = state.copy(cancelledOccurrences = state.cancelledOccurrences + key),
            loopOccurrences = program.loopOccurrences.map {
                if (it.id == occurrenceId) it.copy(status = LoopStatus.CANCELLED) else it
            },
        )
        return ProgramProgressEngine.reconcileCursorAfterLoopChange(syncOccurrences(updated))
    }

    fun cancelLoop(program: Program, loopId: String): Program {
        val state = program.loopState ?: LoopState()
        if (loopId in state.cancelled) return program
        val updated = program.copy(
            loopState = state.copy(
                cancelled = state.cancelled + loopId
            )
        )
        return syncOccurrences(updated)
    }

    fun reactivateLoop(program: Program, loopId: String): Program {
        val state = program.loopState ?: return program
        val updated = program.copy(
            loopState = state.copy(
                cancelled = state.cancelled.filter { it != loopId }
            )
        )
        return syncOccurrences(updated)
    }

    fun advanceCycle(program: Program): Program {
        val state = program.loopState ?: LoopState()
        val newCycle = state.currentCycle + 1
        val updated = program.copy(
            loopState = state.copy(
                currentCycle = newCycle,
                postponed = state.postponed.filter { it.toCycle > newCycle }
            )
        )
        return syncOccurrences(updated)
    }

    /**
     * Materializa [LoopOccurrence] como fuente operativa a partir de loops + loopState.
     * Conserva ocurrencias históricas COMPLETED / CANCELLED y el ciclo de origen al posponer.
     */
    fun syncOccurrences(program: Program, lookAheadCycles: Int = 24): Program {
        if (!program.isSimpleProgram || program.simpleProgramKind != SimpleProgramKind.CYCLIC || program.loops.isEmpty()) {
            return if (program.loopOccurrences.isEmpty()) program else program.copy(loopOccurrences = emptyList())
        }
        val current = getCurrentCycle(program).coerceAtLeast(0)
        val projections = projectLoops(program, current, lookAheadCycles)
        val postponed = program.loopState?.postponed ?: emptyList()
        val existingByOrigin = program.loopOccurrences.associateBy {
            "${it.loopId}_${it.originCycle}"
        }
        val synced = projections.map { projection ->
            val originCycle = if (projection.isPostponed) {
                postponed.find {
                    it.loopId == projection.loop.id && it.toCycle == projection.cycle
                }?.fromCycle ?: projection.cycle
            } else {
                projection.cycle
            }
            val originKey = "${projection.loop.id}_$originCycle"
            val existing = existingByOrigin[originKey]
            // Deferred slots are the live occurrence after postpone (SCHEDULED/ACTIVE),
            // not a POSTPONED tombstone — origin is preserved separately.
            // Never downgrade a COMPLETED occurrence while re-syncing projections.
            val status = when {
                existing?.status == LoopStatus.COMPLETED -> LoopStatus.COMPLETED
                projection.isCancelled -> LoopStatus.CANCELLED
                projection.daysUntil <= 0 -> LoopStatus.ACTIVE
                else -> LoopStatus.SCHEDULED
            }
            existing?.copy(
                status = status,
                cycleNumber = projection.cycle,
                scheduledCycle = projection.cycle,
                originalScheduledCycle = originCycle,
                postponedToCycle = if (originCycle != projection.cycle) projection.cycle else null,
                weekInstanceId = existing.weekInstanceId ?: "loop_week_${projection.loop.id}",
            ) ?: LoopOccurrence(
                id = "occ_${projection.loop.id}_$originCycle",
                loopId = projection.loop.id,
                cycleNumber = projection.cycle,
                scheduledCycle = projection.cycle,
                status = status,
                weekInstanceId = "loop_week_${projection.loop.id}",
                originalScheduledCycle = originCycle,
                postponedToCycle = if (originCycle != projection.cycle) projection.cycle else null,
            )
        }
        val historical = program.loopOccurrences.filter { occ ->
            occ.status == LoopStatus.COMPLETED ||
                occ.status == LoopStatus.CANCELLED ||
                occ.status == LoopStatus.POSTPONED ||
                (occ.originCycle < current && synced.none { it.id == occ.id })
        }
        return program.copy(
            loopOccurrences = (historical + synced)
                .distinctBy { "${it.loopId}_${it.originCycle}" }
                .sortedWith(compareBy({ it.scheduledCycle }, { it.loopId })),
        )
    }

    /**
     * Comprueba que las tres representaciones de loops siguen reconciliadas.
     * No muta el programa: la reparación queda explícitamente en [syncOccurrences]
     * y en las operaciones de mutación de este motor.
     */
    fun validate(program: Program): List<LoopIssue> {
        val issues = mutableListOf<LoopIssue>()
        val loopsById = program.loops.groupBy { it.id }
        loopsById.filterValues { it.size > 1 }.forEach { (loopId, matches) ->
            issues += LoopIssue(
                type = LoopIssueType.DUPLICATE_ID,
                loopId = loopId,
                message = "El loop $loopId aparece ${matches.size} veces en loops[].",
            )
        }
        program.loops.filter { it.repeatEveryXLoops < 1 }.forEach { loop ->
            issues += LoopIssue(
                type = LoopIssueType.INVALID_CADENCE,
                loopId = loop.id,
                message = "El loop ${loop.id} tiene una cadencia inválida: ${loop.repeatEveryXLoops}.",
            )
        }

        val loopWeeks = program.macrocycles
            .flatMap { it.blocks }
            .flatMap { it.mesocycles }
            .flatMap { it.weeks }
            .filter { it.isLoopWeek }
        val loopIds = loopsById.keys
        loopWeeks.filter { it.loopId.isNullOrBlank() || it.loopId !in loopIds }.forEach { week ->
            issues += LoopIssue(
                type = LoopIssueType.ORPHAN_MATERIALIZED_WEEK,
                loopId = week.loopId,
                message = "La semana de loop ${week.id} no referencia una regla existente.",
            )
        }
        program.loops.forEach { loop ->
            val matches = loopWeeks.count { it.loopId == loop.id }
            if (program.isSimpleProgram && program.simpleProgramKind == SimpleProgramKind.CYCLIC && matches == 0) {
                issues += LoopIssue(
                    type = LoopIssueType.MISSING_MATERIALIZED_WEEK,
                    loopId = loop.id,
                    message = "El loop ${loop.id} no tiene semana materializada.",
                )
            }
            if (matches > 1) {
                issues += LoopIssue(
                    type = LoopIssueType.DUPLICATE_ID,
                    loopId = loop.id,
                    message = "El loop ${loop.id} tiene $matches semanas materializadas.",
                )
            }
        }

        val state = program.loopState
        val referencedStateLoopIds = buildSet {
            addAll(state?.cancelled.orEmpty())
            state?.postponed.orEmpty().forEach { add(it.loopId) }
        }
        referencedStateLoopIds.filterNot { it in loopIds }.forEach { loopId ->
            issues += LoopIssue(
                type = LoopIssueType.UNKNOWN_STATE_REFERENCE,
                loopId = loopId,
                message = "loopState referencia un loop inexistente: $loopId.",
            )
        }

        val occurrenceGroups = program.loopOccurrences.groupBy { occurrenceKey(it.loopId, it.originCycle) }
        occurrenceGroups.filterValues { it.size > 1 }.forEach { (key, matches) ->
            issues += LoopIssue(
                type = LoopIssueType.DUPLICATE_OCCURRENCE,
                loopId = matches.firstOrNull()?.loopId,
                message = "La ocurrencia $key está duplicada ${matches.size} veces.",
            )
        }
        program.loopOccurrences.filter { it.loopId !in loopIds }.forEach { occurrence ->
            issues += LoopIssue(
                type = LoopIssueType.UNKNOWN_OCCURRENCE_LOOP,
                loopId = occurrence.loopId,
                message = "loopOccurrences referencia un loop inexistente: ${occurrence.loopId}.",
            )
        }

        if (program.simpleProgramKind == SimpleProgramKind.CALENDARIZED &&
            (program.loops.isNotEmpty() || program.loopState != null || program.loopOccurrences.isNotEmpty())
        ) {
            issues += LoopIssue(
                type = LoopIssueType.CALENDARIZED_LOOP_STATE,
                message = "Los loops deben quedar en pausedCyclicSnapshot durante un break calendarizado.",
            )
        }

        if (program.isSimpleProgram && program.simpleProgramKind == SimpleProgramKind.CYCLIC && program.loops.isNotEmpty()) {
            val currentKeys = program.loopOccurrences
                .map { occurrenceKey(it.loopId, it.originCycle) }
                .toSet()
            val expected = syncOccurrences(program).loopOccurrences
            expected
                .filter { occurrenceKey(it.loopId, it.originCycle) !in currentKeys }
                .groupBy { it.loopId }
                .forEach { (loopId, missing) ->
                    issues += LoopIssue(
                        type = LoopIssueType.STALE_OCCURRENCES,
                        loopId = loopId,
                        message = "Faltan ${missing.size} ocurrencias materializadas para el loop $loopId.",
                    )
                }
        }
        return issues.distinct()
    }

    fun migrateEventsToLoops(program: Program): Program {
        val legacyEvents = program.events.filter { it.repeatEveryXCycles != null }
        if (legacyEvents.isEmpty()) return program

        val existingLoops = program.loops.toMutableList()
        val remainingEvents = program.events.toMutableList()

        for (event in legacyEvents) {
            if (existingLoops.any { it.title == event.title }) continue

            existingLoops.add(
                Loop(
                    id = event.id ?: java.util.UUID.randomUUID().toString(),
                    title = event.title,
                    type = try { LoopType.valueOf((event.type ?: "custom").uppercase()) } catch (_: Exception) { LoopType.CUSTOM },
                    repeatEveryXLoops = event.repeatEveryXCycles!!,
                    durationType = DurationType.WEEK,
                    sessions = event.sessions,
                )
            )
            remainingEvents.remove(event)
        }

        return materializeLoopWeeks(program.copy(
            loops = existingLoops,
            events = remainingEvents,
        ))
    }

    fun formatLoopCountdown(daysUntil: Int): String = when {
        daysUntil <= 0 -> "Ahora"
        daysUntil == 1 -> "1 día"
        daysUntil < 7 -> "$daysUntil días"
        else -> {
            val weeks = daysUntil / 7
            val days = daysUntil % 7
            if (days == 0) "$weeks sem" else "${weeks}s ${days}d"
        }
    }

    fun getLoopTypeEmoji(type: LoopType): String = when (type) {
        LoopType.ONE_RM_TEST -> "\uD83C\uDFCB\uFE0F"
        LoopType.DELOAD -> "\uD83E\uDDD8"
        LoopType.COMPETITION -> "\uD83C\uDFC6"
        LoopType.CUSTOM -> "\u26A1"
    }

    fun getLoopTypeLabel(type: LoopType): String = when (type) {
        LoopType.ONE_RM_TEST -> "Test 1RM"
        LoopType.DELOAD -> "Descarga"
        LoopType.COMPETITION -> "Competición"
        LoopType.CUSTOM -> "Personalizado"
    }

    fun occurrenceKey(loopId: String, scheduledCycle: Int): String = "$loopId:$scheduledCycle"
}
