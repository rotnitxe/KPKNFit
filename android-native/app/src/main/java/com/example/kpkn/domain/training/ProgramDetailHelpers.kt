package com.example.kpkn.domain.training

import com.example.kpkn.data.models.ActiveProgramState
import com.example.kpkn.data.models.discomfortLabel
import com.example.kpkn.data.models.MesocycleGoal
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WeekVariant
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.models.isSimpleTemporalProgram
import com.example.kpkn.domain.calculations.getTotalWeeks

data class RoadmapBlock(
    val id: String,
    val name: String,
    val macroIndex: Int,
    val blockIndex: Int,
    val totalWeeks: Int,
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

object ProgramDetailHelpers {

    fun isSimpleProgram(program: Program): Boolean {
        return program.isSimpleTemporalProgram
    }

    fun buildRoadmapBlocks(program: Program): List<RoadmapBlock> {
        return program.macrocycles.flatMapIndexed { macroIdx, macro ->
            macro.blocks.mapIndexed { blockIdx, block ->
                RoadmapBlock(
                    id = block.id,
                    name = block.name,
                    macroIndex = macroIdx,
                    blockIndex = blockIdx,
                    totalWeeks = block.mesocycles.sumOf { it.weeks.size },
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
