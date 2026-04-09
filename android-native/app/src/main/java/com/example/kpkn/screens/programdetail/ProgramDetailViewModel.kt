package com.example.kpkn.screens.programdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.exercises.EXERCISE_DATABASE_BY_ID
import com.example.kpkn.data.models.ActiveProgramState
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.HYPERTROPHY_ROLE_MULTIPLIERS
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStatus
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.training.ProgramDetailHelpers
import com.example.kpkn.domain.training.RoadmapBlock
import com.example.kpkn.domain.training.VolumeCalculator
import com.example.kpkn.domain.training.WeekAdherence
import com.example.kpkn.domain.training.WeekWithMeta
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.floor

enum class MainTab { TRAINING, ANALYTICS }

enum class StructureSubTab { SEMANA, SPLIT, MACROCICLO, LOOPS, PROTOCOLOS }

enum class AnalyticsSubTab { VOLUMEN, PROGRESO, HISTORIALES }

enum class VolumeAdjustmentResult { SUCCESS, REQUIRES_CALIBRATION, NO_WEEK_SELECTED, NO_ADJUSTABLE_VOLUME }

data class ProgramDetailUiState(
    val activeTab: MainTab = MainTab.TRAINING,
    val structureSubTab: StructureSubTab = StructureSubTab.SEMANA,
    val analyticsSubTab: AnalyticsSubTab = AnalyticsSubTab.VOLUMEN,
    val selectedBlockId: String? = null,
    val selectedWeekId: String? = null,
)

class ProgramDetailViewModel(private val programId: String) : ViewModel() {

    private val repository = ProgramRepository.getInstance()

    // ─── UI State ─────────────────────────────────────────────────────────

    private val _uiState = MutableStateFlow(ProgramDetailUiState())

    val uiState: StateFlow<ProgramDetailUiState> = _uiState

    // ─── Raw Data from Repository ─────────────────────────────────────────

    val program: StateFlow<Program?> = combine(repository.programs) { arrays ->
        val programs = arrays.first()
        programs.find { it.id == programId }
    }.stateIn(viewModelScope, SharingStarted.Lazily, repository.getProgramById(programId))

    val activeProgramState: StateFlow<ActiveProgramState?> = repository.activeProgramState

    val history: StateFlow<List<WorkoutLog>> = repository.history

    // ─── Derived State ────────────────────────────────────────────────────

    val isSimpleProgram: StateFlow<Boolean> = combine(program) { (p) ->
        p?.let { ProgramDetailHelpers.isSimpleProgram(it) } ?: true
    }.stateIn(viewModelScope, SharingStarted.Lazily, true)

    val roadmapBlocks: StateFlow<List<RoadmapBlock>> = combine(program) { (p) ->
        p?.let { ProgramDetailHelpers.buildRoadmapBlocks(it) } ?: emptyList()
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val activeBlockId: StateFlow<String?> = combine(activeProgramState, roadmapBlocks) { active, blocks ->
        ProgramDetailHelpers.findActiveBlockId(active, programId, blocks)
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    val currentWeeks: StateFlow<List<WeekWithMeta>> = combine(_uiState, roadmapBlocks, program) { state, blocks, p ->
        if (p == null) emptyList()
        else ProgramDetailHelpers.getWeeksForBlock(state.selectedBlockId, blocks, p)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val displayedSessions: StateFlow<List<Session>> = combine(_uiState, currentWeeks) { state, weeks ->
        ProgramDetailHelpers.getDisplayedSessions(state.selectedWeekId, weeks)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val selectedWeekMeta: StateFlow<WeekWithMeta?> = combine(_uiState, currentWeeks) { state, weeks ->
        weeks.find { it.id == state.selectedWeekId }
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    val programLogs: StateFlow<List<WorkoutLog>> = combine(history) { (h) ->
        ProgramDetailHelpers.computeProgramLogs(h, programId)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val totalAdherence: StateFlow<Int> = combine(programLogs, program) { logs, p ->
        if (p == null) 0 else ProgramDetailHelpers.computeTotalAdherence(logs, p)
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val weeklyAdherence: StateFlow<List<WeekAdherence>> = combine(currentWeeks, programLogs) { weeks, logs ->
        ProgramDetailHelpers.computeWeeklyAdherence(weeks, logs)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val totalWeeks: StateFlow<Int> = combine(program) { (p) ->
        p?.let { ProgramDetailHelpers.getTotalWeeks(it) } ?: 0
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val currentWeekIndex: StateFlow<Int> = combine(activeProgramState, program) { state, p ->
        if (p == null) 0 else ProgramDetailHelpers.computeCurrentWeekIndex(state, p)
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val programDiscomforts: StateFlow<List<com.example.kpkn.domain.training.DiscomfortEntry>> =
        combine(history) { (h) ->
            ProgramDetailHelpers.computeProgramDiscomforts(h, programId)
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val exerciseDiscomfortAssociations: StateFlow<List<com.example.kpkn.domain.training.ExerciseDiscomfortAssociationEntry>> =
        combine(history) { (h) ->
            ProgramDetailHelpers.computeExerciseDiscomfortAssociations(h, programId)
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val isActiveProgram: StateFlow<Boolean> = combine(activeProgramState) { (state) ->
        state?.programId == programId && state?.status == ProgramStatus.ACTIVE
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    val isPausedProgram: StateFlow<Boolean> = combine(activeProgramState) { (state) ->
        state?.programId == programId && state?.status == ProgramStatus.PAUSED
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    // ─── Init: Auto-select + Tour ─────────────────────────────────────────

    init {
        // Auto-select first block when roadmapBlocks changes
        viewModelScope.launch {
            roadmapBlocks.collect { blocks ->
                if (blocks.isNotEmpty() && _uiState.value.selectedBlockId == null) {
                    _uiState.update { it.copy(selectedBlockId = blocks.first().id) }
                }
            }
        }

        // Auto-select first week when currentWeeks changes
        viewModelScope.launch {
            currentWeeks.collect { weeks ->
                val current = _uiState.value.selectedWeekId
                if (weeks.isNotEmpty() && (current == null || weeks.none { it.id == current })) {
                    _uiState.update { it.copy(selectedWeekId = weeks.first().id) }
                }
            }
        }

    }

    // ─── Actions ──────────────────────────────────────────────────────────

    fun setActiveTab(tab: MainTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun setStructureSubTab(tab: StructureSubTab) {
        _uiState.update { it.copy(structureSubTab = tab) }
    }

    fun setAnalyticsSubTab(tab: AnalyticsSubTab) {
        _uiState.update { it.copy(analyticsSubTab = tab) }
    }

    fun selectBlock(blockId: String) {
        _uiState.update { it.copy(selectedBlockId = blockId, selectedWeekId = null) }
    }

    fun selectWeek(weekId: String) {
        _uiState.update { it.copy(selectedWeekId = weekId) }
    }

    fun startProgram() {
        repository.startProgram(programId)
    }

    fun pauseProgram() {
        repository.pauseProgram()
    }

    fun resumeProgram() {
        repository.resumeProgram()
    }

    fun updateProgram(updated: Program) {
        repository.updateProgram(updated)
    }

    fun markVolumeSetupPromptSeen() {
        val current = program.value ?: return
        repository.updateProgram(current.copy(volumeSetupPromptSeen = true))
    }

    fun updateStartDay(day: Int) {
        val current = program.value ?: return
        repository.updateProgram(current.copy(startDay = day))
    }

    fun updateWeekMetadata(weekId: String, name: String, description: String?) {
        val current = program.value ?: return
        val normalizedDescription = description?.trim()?.takeIf { it.isNotEmpty() }
        val updated = current.copy(
            macrocycles = current.macrocycles.map { macro ->
                macro.copy(
                    blocks = macro.blocks.map { block ->
                        block.copy(
                            mesocycles = block.mesocycles.map { meso ->
                                meso.copy(
                                    weeks = meso.weeks.map { week ->
                                        if (week.id == weekId) {
                                            week.copy(
                                                name = name.trim().ifBlank { week.name },
                                                description = normalizedDescription,
                                            )
                                        } else week
                                    }
                                )
                            }
                        )
                    }
                )
            }
        )
        repository.updateProgram(updated)
    }

    fun reduceCurrentWeekVolumeBy20Percent(): VolumeAdjustmentResult {
        return adjustCurrentWeekVolumeByFactor(0.8)
    }

    fun increaseCurrentWeekVolumeBy20Percent(): VolumeAdjustmentResult {
        return adjustCurrentWeekVolumeByFactor(1.2)
    }

    private fun adjustCurrentWeekVolumeByFactor(factor: Double): VolumeAdjustmentResult {
        val current = program.value ?: return VolumeAdjustmentResult.NO_WEEK_SELECTED
        if (current.volumeRecommendations.isEmpty() || current.athleteProfileScore == null) {
            return VolumeAdjustmentResult.REQUIRES_CALIBRATION
        }

        val targetWeekId = when {
            activeProgramState.value?.programId == programId && !activeProgramState.value?.currentWeekId.isNullOrBlank() ->
                activeProgramState.value?.currentWeekId
            !_uiState.value.selectedWeekId.isNullOrBlank() -> _uiState.value.selectedWeekId
            else -> null
        } ?: return VolumeAdjustmentResult.NO_WEEK_SELECTED

        var changed = false
        val updated = current.copy(
            macrocycles = current.macrocycles.map { macro ->
                macro.copy(
                    blocks = macro.blocks.map { block ->
                        block.copy(
                            mesocycles = block.mesocycles.map { meso ->
                                meso.copy(
                                    weeks = meso.weeks.map { week ->
                                        if (week.id != targetWeekId) {
                                            week
                                        } else {
                                            val adjustedSessions = adjustWeekSessionsByCanonicalMuscle(
                                                sessions = week.sessions,
                                                factor = factor,
                                                canonicalMuscles = current.volumeRecommendations.map {
                                                    canonicalizeMuscleName(it.muscleGroup)
                                                }.distinct(),
                                            )
                                            if (adjustedSessions != week.sessions) changed = true
                                            week.copy(sessions = adjustedSessions)
                                        }
                                    }
                                )
                            }
                        )
                    }
                )
            }
        )

        if (!changed) return VolumeAdjustmentResult.NO_ADJUSTABLE_VOLUME
        repository.updateProgram(updated)
        return VolumeAdjustmentResult.SUCCESS
    }

    fun deleteSession(sessionId: String, macroIndex: Int, mesoIndex: Int, weekId: String) {
        val current = program.value ?: return
        val updated = current.copy(
            macrocycles = current.macrocycles.mapIndexed { mi, macro ->
                if (mi != macroIndex) macro
                else macro.copy(
                    blocks = macro.blocks.map { block ->
                        block.copy(
                            mesocycles = block.mesocycles.mapIndexed { mesoI, meso ->
                                if (mesoI != mesoIndex) meso
                                else meso.copy(
                                    weeks = meso.weeks.map { week ->
                                        if (week.id != weekId) week
                                        else week.copy(
                                            sessions = normalizeMainSessions(
                                                week.sessions.filter { it.id != sessionId }
                                            )
                                        )
                                    }
                                )
                            }
                        )
                    }
                )
            }
        )
        repository.updateProgram(updated)
    }

    fun addSession(macroIndex: Int, mesoIndex: Int, weekId: String, session: Session) {
        val current = program.value ?: return
        val updated = current.copy(
            macrocycles = current.macrocycles.mapIndexed { mi, macro ->
                if (mi != macroIndex) macro
                else macro.copy(
                    blocks = macro.blocks.map { block ->
                        block.copy(
                            mesocycles = block.mesocycles.mapIndexed { mesoI, meso ->
                                if (mesoI != mesoIndex) meso
                                else meso.copy(
                                    weeks = meso.weeks.map { week ->
                                        if (week.id != weekId) week
                                        else week.copy(
                                            sessions = normalizeMainSessions(week.sessions + session)
                                        )
                                    }
                                )
                            }
                        )
                    }
                )
            }
        )
        repository.updateProgram(updated)
    }

    fun reorderSessions(weekId: String, fromIndex: Int, toIndex: Int) {
        val current = program.value ?: return
        val updated = current.copy(
            macrocycles = current.macrocycles.map { macro ->
                macro.copy(
                    blocks = macro.blocks.map { block ->
                        block.copy(
                            mesocycles = block.mesocycles.map { meso ->
                                meso.copy(
                                    weeks = meso.weeks.map { week ->
                                        if (week.id != weekId) week
                                        else {
                                            val sessions = week.sessions.toMutableList()
                                            val item = sessions.removeAt(fromIndex)
                                            sessions.add(toIndex, item)
                                            week.copy(sessions = normalizeMainSessions(sessions))
                                        }
                                    }
                                )
                            }
                        )
                    }
                )
            }
        )
        repository.updateProgram(updated)
    }

    fun replaceWeekSessions(weekId: String, sessions: List<Session>) {
        val current = program.value ?: return
        val normalized = normalizeMainSessions(sessions)
        val updated = current.copy(
            macrocycles = current.macrocycles.map { macro ->
                macro.copy(
                    blocks = macro.blocks.map { block ->
                        block.copy(
                            mesocycles = block.mesocycles.map { meso ->
                                meso.copy(
                                    weeks = meso.weeks.map { week ->
                                        if (week.id == weekId) week.copy(sessions = normalized) else week
                                    }
                                )
                            }
                        )
                    }
                )
            }
        )
        repository.updateProgram(updated)
    }

    private fun normalizeMainSessions(sessions: List<Session>): List<Session> {
        val mainByDay = mutableMapOf<Int, String>()
        val fallbackByDay = mutableMapOf<Int, String>()

        sessions.forEach { session ->
            val day = session.dayOfWeek ?: 1
            fallbackByDay.putIfAbsent(day, session.id)
            if (session.isMainSession && day !in mainByDay) {
                mainByDay[day] = session.id
            }
        }

        fallbackByDay.forEach { (day, sessionId) ->
            mainByDay.putIfAbsent(day, sessionId)
        }

        return sessions.map { session ->
            val day = session.dayOfWeek ?: 1
            session.copy(isMainSession = mainByDay[day] == session.id)
        }
    }

    private fun adjustWeekSessionsByCanonicalMuscle(
        sessions: List<Session>,
        factor: Double,
        canonicalMuscles: List<String>,
    ): List<Session> {
        val exerciseNodes = collectExerciseNodes(sessions)
        if (exerciseNodes.isEmpty()) return sessions

        val mutableSetCounts = exerciseNodes.associate { it.exerciseId to it.setCount }.toMutableMap()
        val muscleToExercises = linkedMapOf<String, MutableSet<String>>()

        exerciseNodes.forEach { node ->
            node.muscles.forEach { muscle ->
                muscleToExercises.getOrPut(muscle) { linkedSetOf() }.add(node.exerciseId)
            }
        }

        val orderedMuscles = (canonicalMuscles + muscleToExercises.keys).distinct()
        var changed = false

        orderedMuscles.forEach { muscle ->
            val candidates = muscleToExercises[muscle].orEmpty().toList()
            if (candidates.isEmpty()) return@forEach

            val currentTotal = candidates.sumOf { mutableSetCounts[it] ?: 0 }
            if (currentTotal == 0) return@forEach

            val targetTotal = computeTargetSetTotal(currentTotal, factor)
            when {
                targetTotal > currentTotal -> {
                    if (increaseMuscleVolume(candidates, mutableSetCounts, targetTotal - currentTotal)) {
                        changed = true
                    }
                }
                targetTotal < currentTotal -> {
                    if (decreaseMuscleVolume(candidates, mutableSetCounts, currentTotal - targetTotal)) {
                        changed = true
                    }
                }
            }
        }

        if (!changed) return sessions
        return sessions.map { applySetCountsToSession(it, mutableSetCounts) }
    }

    private fun computeTargetSetTotal(currentTotal: Int, factor: Double): Int {
        if (currentTotal <= 0) return 0
        return if (factor >= 1.0) {
            ceil(currentTotal * factor).toInt()
        } else {
            floor(currentTotal * factor).toInt().coerceAtLeast(1)
        }
    }

    private fun increaseMuscleVolume(
        candidates: List<String>,
        setCounts: MutableMap<String, Int>,
        delta: Int,
    ): Boolean {
        if (delta <= 0 || candidates.isEmpty()) return false
        var remaining = delta
        var changed = false
        val ordered = candidates.sortedWith(compareBy({ setCounts[it] ?: 0 }, { it }))

        while (remaining > 0) {
            ordered.forEach { exerciseId ->
                setCounts[exerciseId] = (setCounts[exerciseId] ?: 0) + 1
                remaining--
                changed = true
                if (remaining <= 0) return changed
            }
        }

        return changed
    }

    private fun decreaseMuscleVolume(
        candidates: List<String>,
        setCounts: MutableMap<String, Int>,
        delta: Int,
    ): Boolean {
        if (delta <= 0 || candidates.isEmpty()) return false
        var remaining = delta
        var changed = false

        while (remaining > 0) {
            val ordered = candidates.sortedByDescending { setCounts[it] ?: 0 }
            var reducedThisRound = false

            ordered.forEach { exerciseId ->
                val currentCount = setCounts[exerciseId] ?: 0
                if (currentCount > 1 && remaining > 0) {
                    setCounts[exerciseId] = currentCount - 1
                    remaining--
                    changed = true
                    reducedThisRound = true
                }
            }

            if (!reducedThisRound) break
        }

        return changed
    }

    private fun collectExerciseNodes(sessions: List<Session>): List<ExerciseNode> {
        val nodes = mutableListOf<ExerciseNode>()
        sessions.forEach { session -> collectExerciseNodesFromSession(session, nodes) }
        return nodes
    }

    private fun collectExerciseNodesFromSession(session: Session, destination: MutableList<ExerciseNode>) {
        session.exercises.forEach { exercise ->
            buildExerciseNode(exercise)?.let(destination::add)
        }
        session.parts.forEach { part ->
            part.exercises.forEach { exercise ->
                buildExerciseNode(exercise)?.let(destination::add)
            }
        }
        listOfNotNull(session.sessionB, session.sessionC, session.sessionD).forEach { nested ->
            collectExerciseNodesFromSession(nested, destination)
        }
    }

    private fun buildExerciseNode(exercise: Exercise): ExerciseNode? {
        val muscles = resolveCanonicalMuscles(exercise)
        if (muscles.isEmpty() || exercise.sets.isEmpty()) return null
        return ExerciseNode(
            exerciseId = exercise.id,
            muscles = muscles,
            setCount = exercise.sets.size,
        )
    }

    private fun resolveCanonicalMuscles(exercise: Exercise): Set<String> {
        val exerciseDbId = exercise.exerciseDbId?.lowercase() ?: return emptySet()
        val info = EXERCISE_DATABASE_BY_ID[exerciseDbId] ?: return emptySet()

        return info.involvedMuscles
            .filter { (HYPERTROPHY_ROLE_MULTIPLIERS[it.role] ?: 0.0) > 0.0 }
            .map { involved ->
                canonicalizeMuscleName(
                    VolumeCalculator.normalizeMuscleGroup(
                        specificMuscle = involved.muscle,
                        emphasis = involved.emphasis,
                    )
                )
            }
            .filter { it.isNotBlank() }
            .toSet()
    }

    private fun canonicalizeMuscleName(muscle: String): String {
        return when (muscle.trim().lowercase()) {
            "cuadriceps", "cuádriceps" -> "Cuádriceps"
            "gluteos", "glúteos" -> "Glúteos"
            "biceps", "bíceps" -> "Bíceps"
            "triceps", "tríceps" -> "Tríceps"
            "isquiotibiales", "isquiosurales" -> "Isquiosurales"
            else -> muscle.trim().replaceFirstChar { it.uppercase() }
        }
    }

    private fun applySetCountsToSession(session: Session, targetCounts: Map<String, Int>): Session {
        val updatedExercises = session.exercises.map { adjustExerciseSetCount(it, targetCounts[it.id]) }
        val updatedParts = session.parts.map { part ->
            part.copy(exercises = part.exercises.map { adjustExerciseSetCount(it, targetCounts[it.id]) })
        }

        return session.copy(
            exercises = updatedExercises,
            parts = updatedParts,
            sessionB = session.sessionB?.let { applySetCountsToSession(it, targetCounts) },
            sessionC = session.sessionC?.let { applySetCountsToSession(it, targetCounts) },
            sessionD = session.sessionD?.let { applySetCountsToSession(it, targetCounts) },
        )
    }

    private fun adjustExerciseSetCount(exercise: Exercise, targetCount: Int?): Exercise {
        val safeTarget = targetCount ?: return exercise
        val currentSets = exercise.sets
        if (safeTarget == currentSets.size || currentSets.isEmpty()) return exercise

        return if (safeTarget < currentSets.size) {
            exercise.copy(sets = currentSets.take(safeTarget))
        } else {
            val template = currentSets.last()
            val extraSets = List(safeTarget - currentSets.size) {
                template.copy(id = UUID.randomUUID().toString())
            }
            exercise.copy(sets = currentSets + extraSets)
        }
    }

    private data class ExerciseNode(
        val exerciseId: String,
        val muscles: Set<String>,
        val setCount: Int,
    )

    // ─── Factory ──────────────────────────────────────────────────────────

    companion object {
        fun factory(programId: String): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ProgramDetailViewModel(programId) as T
            }
        }
    }
}
