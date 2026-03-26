package com.example.kpkn.screens.programdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.models.ActiveProgramState
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStatus
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.training.ProgramDetailHelpers
import com.example.kpkn.domain.training.RoadmapBlock
import com.example.kpkn.domain.training.WeekAdherence
import com.example.kpkn.domain.training.WeekWithMeta
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MainTab { TRAINING, ANALYTICS }

enum class StructureSubTab { SEMANA, SPLIT, MACROCICLO, LOOPS, PROTOCOLOS }

enum class AnalyticsSubTab { VOLUMEN, PROGRESO, HISTORIALES }

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
                                            sessions = week.sessions.filter { it.id != sessionId }
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
                                            sessions = week.sessions + session
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
                                            week.copy(sessions = sessions)
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
