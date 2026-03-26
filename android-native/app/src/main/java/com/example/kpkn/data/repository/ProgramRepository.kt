package com.example.kpkn.data.repository

import android.content.Context
import com.example.kpkn.data.db.*
import com.example.kpkn.data.models.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ProgramRepository — Single source of truth para programas, historial,
 * estado activo, settings y ongoing workout.
 *
 * Patrón write-through cache:
 * - StateFlow = cache en memoria (lecturas instantáneas, UI reactiva)
 * - Room = backing store persistente
 * - init() carga Room → StateFlow una vez al arrancar
 * - Mutaciones actualizan StateFlow inmediatamente + Room en background
 */
class ProgramRepository private constructor(context: Context) {

    private val db = KpknDatabase.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ─── Programs ─────────────────────────────────────────────────────────────

    private val _programs = MutableStateFlow<List<Program>>(emptyList())
    val programs: StateFlow<List<Program>> = _programs.asStateFlow()

    fun addProgram(program: Program) {
        _programs.update { it + program }
        scope.launch { db.programDao().upsert(program.toEntity()) }
    }

    fun updateProgram(program: Program) {
        _programs.update { list -> list.map { if (it.id == program.id) program else it } }
        scope.launch { db.programDao().upsert(program.toEntity()) }
    }

    fun deleteProgram(programId: String) {
        _programs.update { list -> list.filter { it.id != programId } }
        scope.launch { db.programDao().delete(programId) }
    }

    fun clearPrograms() {
        _programs.value = emptyList()
        scope.launch { db.programDao().deleteAll() }
    }

    fun getProgramById(id: String): Program? = _programs.value.find { it.id == id }

    // ─── Active Program State ─────────────────────────────────────────────────

    private val _activeProgramState = MutableStateFlow<ActiveProgramState?>(null)
    val activeProgramState: StateFlow<ActiveProgramState?> = _activeProgramState.asStateFlow()

    fun startProgram(programId: String) {
        val state = ActiveProgramState(programId = programId, status = ProgramStatus.ACTIVE)
        _activeProgramState.value = state
        scope.launch { db.stateDao().upsertActiveProgram(state.toEntity()) }
    }

    fun pauseProgram() {
        _activeProgramState.update { it?.copy(status = ProgramStatus.PAUSED) }
        _activeProgramState.value?.let { scope.launch { db.stateDao().upsertActiveProgram(it.toEntity()) } }
    }

    fun resumeProgram() {
        _activeProgramState.update { it?.copy(status = ProgramStatus.ACTIVE) }
        _activeProgramState.value?.let { scope.launch { db.stateDao().upsertActiveProgram(it.toEntity()) } }
    }

    fun advanceWeek(nextWeekId: String) {
        _activeProgramState.update { it?.copy(currentWeekId = nextWeekId) }
        _activeProgramState.value?.let { scope.launch { db.stateDao().upsertActiveProgram(it.toEntity()) } }
    }

    fun clearActiveProgram() {
        _activeProgramState.value = null
        scope.launch { db.stateDao().clearActiveProgram() }
    }

    // ─── Workout History ──────────────────────────────────────────────────────

    private val _history = MutableStateFlow<List<WorkoutLog>>(emptyList())
    val history: StateFlow<List<WorkoutLog>> = _history.asStateFlow()

    fun addWorkoutLog(log: WorkoutLog) {
        _history.update { listOf(log) + it }
        scope.launch { db.workoutLogDao().insert(log.toEntity()) }
    }

    fun getLogsForProgram(programId: String): List<WorkoutLog> =
        _history.value.filter { it.programId == programId }

    fun getLogsForSession(sessionId: String): List<WorkoutLog> =
        _history.value.filter { it.sessionId == sessionId }

    // ─── Ongoing Workout ──────────────────────────────────────────────────────

    private val _ongoingWorkout = MutableStateFlow<OngoingWorkoutState?>(null)
    val ongoingWorkout: StateFlow<OngoingWorkoutState?> = _ongoingWorkout.asStateFlow()

    fun startWorkout(state: OngoingWorkoutState) {
        _ongoingWorkout.value = state
        scope.launch { db.stateDao().upsertOngoingWorkout(state.toEntity()) }
    }

    fun updateOngoingWorkout(update: (OngoingWorkoutState) -> OngoingWorkoutState) {
        _ongoingWorkout.update { it?.let(update) }
        _ongoingWorkout.value?.let { scope.launch { db.stateDao().upsertOngoingWorkout(it.toEntity()) } }
    }

    fun clearOngoingWorkout() {
        _ongoingWorkout.value = null
        scope.launch { db.stateDao().clearOngoingWorkout() }
    }

    // ─── Settings ─────────────────────────────────────────────────────────────

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    fun updateSettings(update: (Settings) -> Settings) {
        _settings.update(update)
        scope.launch { db.settingsDao().upsert(_settings.value.toEntity()) }
    }

    // ─── Bootstrap ────────────────────────────────────────────────────────────

    /** Carga el estado persistido de Room en los StateFlows. Llamar al arrancar. */
    private fun loadFromDb() {
        scope.launch {
            val programs = db.programDao().getAll().map { it.toProgram() }
            val logs = db.workoutLogDao().getAll().map { it.toWorkoutLog() }
            val settings = db.settingsDao().get()?.toSettings() ?: Settings()
            val activeProgram = db.stateDao().getActiveProgram()?.toActiveProgramState()
            val ongoingWorkout = db.stateDao().getOngoingWorkout()?.toOngoingWorkoutState()

            withContext(Dispatchers.Main) {
                _programs.value = programs
                _history.value = logs
                _settings.value = settings
                _activeProgramState.value = activeProgram
                _ongoingWorkout.value = ongoingWorkout
            }
        }
    }

    // ─── Singleton ────────────────────────────────────────────────────────────

    companion object {
        @Volatile private var INSTANCE: ProgramRepository? = null

        /**
         * Llamar una vez al inicio (MainActivity.onCreate o Application.onCreate).
         * Carga datos de Room → StateFlows en background.
         */
        fun init(context: Context): ProgramRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ProgramRepository(context.applicationContext)
                    .also { INSTANCE = it; it.loadFromDb() }
            }

        /** Acceso rápido después de init(). */
        fun getInstance(): ProgramRepository =
            INSTANCE ?: error("ProgramRepository not initialized — call init(context) first.")
    }
}
