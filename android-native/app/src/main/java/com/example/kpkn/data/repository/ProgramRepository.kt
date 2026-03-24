package com.example.kpkn.data.repository

import com.example.kpkn.data.models.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * SharedProgramRepository — single source of truth for programs, history,
 * activeProgramState, settings, and ongoingWorkout.
 *
 * Replaces AppContext from the PWA. Singleton — shared across ViewModels
 * via manual DI (companion object). Hilt/DI framework can be added later.
 *
 * Persistence: in-memory for now. DataStore / Room will be added in Phase 4.
 */
class ProgramRepository private constructor() {

    // ─── Programs ─────────────────────────────────────────────────────────

    private val _programs = MutableStateFlow<List<Program>>(emptyList())
    val programs: StateFlow<List<Program>> = _programs.asStateFlow()

    fun addProgram(program: Program) {
        _programs.update { it + program }
    }

    fun updateProgram(program: Program) {
        _programs.update { list -> list.map { if (it.id == program.id) program else it } }
    }

    fun deleteProgram(programId: String) {
        _programs.update { list -> list.filter { it.id != programId } }
    }

    fun getProgramById(id: String): Program? = _programs.value.find { it.id == id }

    // ─── Active Program State ─────────────────────────────────────────────

    private val _activeProgramState = MutableStateFlow<ActiveProgramState?>(null)
    val activeProgramState: StateFlow<ActiveProgramState?> = _activeProgramState.asStateFlow()

    fun startProgram(programId: String) {
        _activeProgramState.value = ActiveProgramState(
            programId = programId,
            status = ProgramStatus.ACTIVE,
        )
    }

    fun pauseProgram() {
        _activeProgramState.update { it?.copy(status = ProgramStatus.PAUSED) }
    }

    fun resumeProgram() {
        _activeProgramState.update { it?.copy(status = ProgramStatus.ACTIVE) }
    }

    fun advanceWeek(nextWeekId: String) {
        _activeProgramState.update { it?.copy(currentWeekId = nextWeekId) }
    }

    fun clearActiveProgram() {
        _activeProgramState.value = null
    }

    // ─── Workout History ──────────────────────────────────────────────────

    private val _history = MutableStateFlow<List<WorkoutLog>>(emptyList())
    val history: StateFlow<List<WorkoutLog>> = _history.asStateFlow()

    fun addWorkoutLog(log: WorkoutLog) {
        _history.update { listOf(log) + it }
    }

    fun getLogsForProgram(programId: String): List<WorkoutLog> =
        _history.value.filter { it.programId == programId }

    fun getLogsForSession(sessionId: String): List<WorkoutLog> =
        _history.value.filter { it.sessionId == sessionId }

    // ─── Ongoing Workout ──────────────────────────────────────────────────

    private val _ongoingWorkout = MutableStateFlow<OngoingWorkoutState?>(null)
    val ongoingWorkout: StateFlow<OngoingWorkoutState?> = _ongoingWorkout.asStateFlow()

    fun startWorkout(state: OngoingWorkoutState) {
        _ongoingWorkout.value = state
    }

    fun updateOngoingWorkout(update: (OngoingWorkoutState) -> OngoingWorkoutState) {
        _ongoingWorkout.update { it?.let(update) }
    }

    fun clearOngoingWorkout() {
        _ongoingWorkout.value = null
    }

    // ─── Settings ─────────────────────────────────────────────────────────

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    fun updateSettings(update: (Settings) -> Settings) {
        _settings.update(update)
    }

    // ─── Singleton ────────────────────────────────────────────────────────

    companion object {
        @Volatile
        private var INSTANCE: ProgramRepository? = null

        fun getInstance(): ProgramRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ProgramRepository().also { INSTANCE = it }
            }
    }
}
