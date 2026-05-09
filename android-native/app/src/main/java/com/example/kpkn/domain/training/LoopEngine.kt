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

object LoopEngine {

    fun materializeLoopWeeks(program: Program): Program {
        if (!program.isSimpleTemporalProgram || program.loops.isEmpty()) return program
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
                sessions = existing?.sessions ?: loop.sessions,
                isLoopWeek = true,
                loopId = loop.id,
            )
        }

        return program.copy(
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
        )
        return program.copy(
            loops = program.loops.filterNot { it.id == loopId },
            loopState = nextState,
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
    }

    fun getCycleLength(program: Program): Int {
        val block = program.macrocycles.firstOrNull()?.blocks?.firstOrNull() ?: return 1
        return block.mesocycles.sumOf { meso -> meso.weeks.count { !it.isLoopWeek } }.takeIf { it > 0 } ?: 1
    }

    fun getCurrentCycle(program: Program): Int {
        return program.loopState?.currentCycle ?: 0
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
        val loops = program.loops
        if (loops.isEmpty()) return emptyList()

        val cycleLength = getCycleLength(program)
        val cycleDays = cycleLength * (program.weekDays ?: 7)
        val postponed = program.loopState?.postponed ?: emptyList()
        val cancelled = (program.loopState?.cancelled ?: emptyList()).toSet()
        val projections = mutableListOf<LoopProjection>()

        for (cycle in fromCycle until fromCycle + lookAheadCycles) {
            for (loop in loops) {
                if (loop.id in cancelled) continue

                val isActive = cycle > 0 && cycle % loop.repeatEveryXLoops == 0
                val postponement = postponed.find { it.loopId == loop.id && it.fromCycle == cycle }

                if (isActive && postponement == null) {
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
                if (deferredHere != null) {
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

        return program.copy(
            loopState = state.copy(
                postponed = state.postponed + PostponedLoop(
                    loopId = loopId,
                    fromCycle = fromCycle,
                    toCycle = fromCycle + 1,
                )
            )
        )
    }

    fun cancelLoop(program: Program, loopId: String): Program {
        val state = program.loopState ?: LoopState()
        if (loopId in state.cancelled) return program

        return program.copy(
            loopState = state.copy(
                cancelled = state.cancelled + loopId
            )
        )
    }

    fun reactivateLoop(program: Program, loopId: String): Program {
        val state = program.loopState ?: return program
        return program.copy(
            loopState = state.copy(
                cancelled = state.cancelled.filter { it != loopId }
            )
        )
    }

    fun advanceCycle(program: Program): Program {
        val state = program.loopState ?: LoopState()
        val newCycle = state.currentCycle + 1

        return program.copy(
            loopState = state.copy(
                currentCycle = newCycle,
                postponed = state.postponed.filter { it.toCycle > newCycle }
            )
        )
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
}
