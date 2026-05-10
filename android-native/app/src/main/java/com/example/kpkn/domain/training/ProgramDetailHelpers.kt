package com.example.kpkn.domain.training

import com.example.kpkn.data.models.ActiveProgramState
import com.example.kpkn.data.models.KeyDateType
import com.example.kpkn.data.models.LoopType
import com.example.kpkn.data.models.discomfortLabel
import com.example.kpkn.data.models.MesocycleGoal
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramKeyDate
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WeekVariant
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.models.isSimpleTemporalProgram
import com.example.kpkn.domain.calculations.getTotalWeeks
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

data class RoadmapBlock(
    val id: String,
    val name: String,
    val description: String? = null,
    val macroIndex: Int,
    val blockIndex: Int,
    val totalWeeks: Int,
    val dateRangeLabel: String? = null,
)

data class WeekWithMeta(
    val id: String,
    val name: String,
    val description: String? = null,
    val sessions: List<Session>,
    val mesoGoal: MesocycleGoal,
    val mesoIndex: Int,
    val variant: WeekVariant? = null,
    val isLoopWeek: Boolean = false,
    val loopId: String? = null,
    val dateRangeLabel: String? = null,
    val keyDateLabel: String? = null,
    val keyDateType: KeyDateType? = null,
)

data class DiscomfortEntry(
    val name: String,
    val count: Int,
)

data class ExerciseDiscomfortAssociationEntry(
    val exerciseDbId: String?,
    val exerciseName: String,
    val discomfortLabel: String,
    val count: Int,
)

data class WeekAdherence(
    val weekName: String,
    val pct: Int,
)

data class RoadmapLoopMarker(
    val id: String,
    val label: String,
    val title: String,
    val repeatEveryCycles: Int,
)

object ProgramDetailHelpers {

    fun isSimpleProgram(program: Program): Boolean {
        return program.isSimpleTemporalProgram
    }

    fun buildRoadmapBlocks(program: Program): List<RoadmapBlock> {
        val blockDateRanges = buildBlockDateRanges(program)
        return program.macrocycles.flatMapIndexed { macroIdx, macro ->
            macro.blocks.mapIndexed { blockIdx, block ->
                RoadmapBlock(
                    id = block.id,
                    name = block.name,
                    description = block.description,
                    macroIndex = macroIdx,
                    blockIndex = blockIdx,
                    totalWeeks = block.mesocycles.sumOf { it.weeks.size },
                    dateRangeLabel = blockDateRanges[block.id],
                )
            }
        }
    }

    fun findActiveBlockId(
        activeProgramState: ActiveProgramState?,
        programId: String,
        roadmapBlocks: List<RoadmapBlock>,
    ): String? {
        if (activeProgramState == null || activeProgramState.programId != programId) return null
        return roadmapBlocks.find {
            it.macroIndex == activeProgramState.currentMacrocycleIndex &&
                it.blockIndex == activeProgramState.currentBlockIndex
        }?.id
    }

    fun getWeeksForBlock(
        selectedBlockId: String?,
        roadmapBlocks: List<RoadmapBlock>,
        program: Program,
    ): List<WeekWithMeta> {
        if (selectedBlockId == null) return emptyList()
        val block = roadmapBlocks.find { it.id == selectedBlockId } ?: return emptyList()
        val macro = program.macrocycles.getOrNull(block.macroIndex) ?: return emptyList()
        val weekDateMeta = buildWeekDateMeta(program)

        var mesoOffset = 0
        for (b in macro.blocks) {
            if (b.id == block.id) break
            mesoOffset += b.mesocycles.size
        }

        return block.let { b ->
            val actualBlock = macro.blocks.getOrNull(b.blockIndex) ?: return emptyList()
            actualBlock.mesocycles.flatMapIndexed { localMesoIdx, meso ->
                meso.weeks.map { week ->
                    WeekWithMeta(
                        id = week.id,
                        name = week.name,
                        description = week.description,
                        sessions = week.sessions,
                        mesoGoal = meso.goal,
                        mesoIndex = mesoOffset + localMesoIdx,
                        variant = week.variant,
                        isLoopWeek = week.isLoopWeek,
                        loopId = week.loopId,
                        dateRangeLabel = weekDateMeta[week.id]?.dateRangeLabel,
                        keyDateLabel = weekDateMeta[week.id]?.keyDateLabel,
                        keyDateType = weekDateMeta[week.id]?.keyDateType,
                    )
                }
            }
        }
    }

    fun getDisplayedSessions(
        selectedWeekId: String?,
        weeks: List<WeekWithMeta>,
    ): List<Session> {
        if (selectedWeekId == null) return emptyList()
        return weeks.find { it.id == selectedWeekId }?.sessions ?: emptyList()
    }

    fun buildSimpleRoadmapLoopMarkers(program: Program): List<RoadmapLoopMarker> {
        if (!program.isSimpleTemporalProgram) return emptyList()

        val loopMarkers = program.loops.map { loop ->
            RoadmapLoopMarker(
                id = loop.id,
                label = loopTypeLabel(loop.type),
                title = loop.title,
                repeatEveryCycles = loop.repeatEveryXLoops.coerceAtLeast(1),
            )
        }

        val legacyMarkers = program.events
            .filter { it.repeatEveryXCycles != null }
            .map { event ->
                RoadmapLoopMarker(
                    id = event.id ?: event.title,
                    label = eventTypeLabel(event.type),
                    title = event.title,
                    repeatEveryCycles = event.repeatEveryXCycles?.coerceAtLeast(1) ?: 1,
                )
            }

        return (loopMarkers + legacyMarkers)
            .distinctBy { it.id }
            .sortedWith(compareBy<RoadmapLoopMarker> { it.repeatEveryCycles }.thenBy { it.title })
    }

    private fun loopTypeLabel(type: LoopType): String = when (type) {
        LoopType.ONE_RM_TEST -> "1RM"
        LoopType.DELOAD -> "Deload"
        LoopType.COMPETITION -> "Comp"
        LoopType.CUSTOM -> "Loop"
    }

    private fun eventTypeLabel(type: String): String = when (type.trim().lowercase()) {
        "one_rm_test", "1rm", "test" -> "1RM"
        "deload", "descarga" -> "Deload"
        "competition", "competicion", "competición" -> "Comp"
        else -> "Evento"
    }

    private data class WeekDateMeta(
        val dateRangeLabel: String?,
        val keyDateLabel: String?,
        val keyDateType: KeyDateType?,
    )

    private fun buildBlockDateRanges(program: Program): Map<String, String> {
        var cursor = parseProgramDate(program.timelineStartDate) ?: return emptyMap()
        val ranges = mutableMapOf<String, String>()
        program.macrocycles.forEach { macro ->
            macro.blocks.forEach { block ->
                val weeks = block.mesocycles.sumOf { it.weeks.size }
                if (weeks > 0) {
                    val start = cursor
                    val end = cursor.plusWeeks(weeks.toLong()).minusDays(1)
                    ranges[block.id] = formatDateRange(start, end)
                    cursor = cursor.plusWeeks(weeks.toLong())
                }
            }
        }
        return ranges
    }

    private fun buildWeekDateMeta(program: Program): Map<String, WeekDateMeta> {
        var cursor = parseProgramDate(program.timelineStartDate) ?: return emptyMap()
        val meta = mutableMapOf<String, WeekDateMeta>()
        program.macrocycles.forEach { macro ->
            macro.blocks.forEach { block ->
                block.mesocycles.forEach { meso ->
                    meso.weeks.forEach { week ->
                        val weekStart = cursor
                        val weekEnd = cursor.plusDays(6)
                        val keyDate = program.keyDates.firstOrNull { it.intersectsWeek(weekStart, weekEnd) }
                        meta[week.id] = WeekDateMeta(
                            dateRangeLabel = formatDateRange(weekStart, weekEnd),
                            keyDateLabel = keyDate?.roadmapLabel(),
                            keyDateType = keyDate?.type,
                        )
                        cursor = cursor.plusWeeks(1)
                    }
                }
            }
        }
        return meta
    }

    private fun ProgramKeyDate.intersectsWeek(weekStart: LocalDate, weekEnd: LocalDate): Boolean {
        val start = parseProgramDate(startDate) ?: parseProgramDate(eventDate) ?: return false
        val end = parseProgramDate(endDate) ?: start
        return end >= weekStart && start <= weekEnd
    }

    private fun ProgramKeyDate.roadmapLabel(): String = when (type) {
        KeyDateType.COMPETITION -> "Comp"
        KeyDateType.EXAMS -> "Examen"
        KeyDateType.VACATION -> "Libre"
        KeyDateType.TRAVEL -> "Viaje"
        KeyDateType.CUSTOM -> title.take(8).ifBlank { "Clave" }
    }

    private fun parseProgramDate(raw: String?): LocalDate? {
        if (raw.isNullOrBlank()) return null
        return try {
            LocalDate.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun formatDateRange(start: LocalDate, end: LocalDate): String {
        val formatter = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es-CL"))
        return "${start.format(formatter)}-${end.format(formatter)}"
    }

    fun computeProgramDiscomforts(
        history: List<WorkoutLog>,
        programId: String,
    ): List<DiscomfortEntry> {
        val map = mutableMapOf<String, Int>()
        history
            .filter { it.programId == programId }
            .forEach { log ->
                log.discomforts.forEach { d -> map[d] = (map[d] ?: 0) + 1 }
            }
        return map.entries
            .map { DiscomfortEntry(it.key, it.value) }
            .sortedByDescending { it.count }
    }

    fun computeExerciseDiscomfortAssociations(
        history: List<WorkoutLog>,
        programId: String,
    ): List<ExerciseDiscomfortAssociationEntry> {
        val map = mutableMapOf<Triple<String?, String, String>, Int>()
        history
            .filter { it.programId == programId }
            .forEach { log ->
                log.postExerciseReports.forEach { report ->
                    report.discomfortIds
                        .filter { it != "none" }
                        .forEach { discomfortId ->
                            val key = Triple(
                                report.exerciseDbId,
                                report.exerciseName,
                                discomfortLabel(discomfortId),
                            )
                            map[key] = (map[key] ?: 0) + 1
                        }
                }
            }

        return map.entries
            .map { (key, count) ->
                ExerciseDiscomfortAssociationEntry(
                    exerciseDbId = key.first,
                    exerciseName = key.second,
                    discomfortLabel = key.third,
                    count = count,
                )
            }
            .sortedByDescending { it.count }
    }

    fun computeProgramLogs(
        history: List<WorkoutLog>,
        programId: String,
    ): List<WorkoutLog> {
        return history
            .filter { it.programId == programId }
            .sortedByDescending { it.date }
    }

    fun computeTotalAdherence(programLogs: List<WorkoutLog>, program: Program): Int {
        val completedIds = programLogs.map { it.sessionId }.toSet()
        val allSessions = program.macrocycles.flatMap { macro ->
            macro.blocks.flatMap { block ->
                block.mesocycles.flatMap { meso ->
                    meso.weeks.flatMap { week -> week.sessions }
                }
            }
        }
        if (allSessions.isEmpty()) return 0
        return ((allSessions.count { it.id in completedIds }.toDouble() / allSessions.size) * 100).toInt()
    }

    fun computeWeeklyAdherence(
        currentWeeks: List<WeekWithMeta>,
        programLogs: List<WorkoutLog>,
    ): List<WeekAdherence> {
        return currentWeeks.mapIndexed { idx, week ->
            val weekSessionIds = week.sessions.map { it.id }.toSet()
            val logs = programLogs.filter { it.sessionId in weekSessionIds }
            val completed = logs.map { it.sessionId }.toSet().size
            val planned = week.sessions.size
            WeekAdherence(
                weekName = "Semana ${idx + 1}",
                pct = if (planned > 0) ((completed.toDouble() / planned) * 100).toInt() else 0,
            )
        }
    }

    fun computeCurrentWeekIndex(
        activeProgramState: ActiveProgramState?,
        program: Program,
    ): Int {
        if (activeProgramState == null || activeProgramState.programId != program.id) return 0
        var weekIdx = 0
        for (m in program.macrocycles) {
            for (b in m.blocks) {
                for (meso in b.mesocycles) {
                    for (w in meso.weeks) {
                        if (w.id == activeProgramState.currentWeekId) return weekIdx
                        weekIdx++
                    }
                }
            }
        }
        return 0
    }

    fun getTotalWeeks(program: Program): Int {
        return program.macrocycles.sumOf { macro ->
            macro.blocks.sumOf { block ->
                block.mesocycles.sumOf { it.weeks.size }
            }
        }
    }
}
