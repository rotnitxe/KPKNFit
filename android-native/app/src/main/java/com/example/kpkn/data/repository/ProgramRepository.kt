package com.example.kpkn.data.repository

import android.content.Context
import com.example.kpkn.data.db.*
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.exercises.normalizedIdentityFields
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar
import java.util.UUID

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

    private val _programQueue = MutableStateFlow<List<String>>(emptyList())
    val programQueue: StateFlow<List<String>> = _programQueue.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    fun addProgram(program: Program) {
        val normalized = program.normalizedIdentityFields()
        _programs.update { it + normalized }
        scope.launch { db.programDao().upsert(normalized.toEntity()) }
    }

    fun updateProgram(program: Program) {
        val normalized = program.normalizedIdentityFields()
        _programs.update { list -> list.map { if (it.id == normalized.id) normalized else it } }
        scope.launch { db.programDao().upsert(normalized.toEntity()) }
    }

    suspend fun updateProgramNow(program: Program) {
        val normalized = program.normalizedIdentityFields()
        _programs.update { list -> list.map { if (it.id == normalized.id) normalized else it } }
        withContext(Dispatchers.IO) { db.programDao().upsert(normalized.toEntity()) }
    }

    fun upsertSessionInProgram(
        programId: String,
        weekId: String,
        macroIndex: Int,
        mesoIndex: Int,
        session: Session,
    ): Boolean {
        val current = getProgramById(programId) ?: return false
        val updated = current.upsertSessionInWeek(
            weekId = weekId,
            macroIndex = macroIndex,
            mesoIndex = mesoIndex,
            session = session,
        ) ?: return false
        updateProgram(updated)
        return true
    }

    suspend fun upsertSessionInProgramNow(
        programId: String,
        weekId: String,
        macroIndex: Int,
        mesoIndex: Int,
        session: Session,
    ): Boolean {
        val current = getProgramById(programId) ?: return false
        val updated = current.upsertSessionInWeek(
            weekId = weekId,
            macroIndex = macroIndex,
            mesoIndex = mesoIndex,
            session = session,
        ) ?: return false
        updateProgramNow(updated)
        return true
    }

    fun deleteProgram(programId: String) {
        _programs.update { list -> list.filter { it.id != programId } }
        _programQueue.update { queue -> queue.filterNot { it == programId } }
        scope.launch { db.programDao().delete(programId) }
    }

    fun addProgramToQueue(programId: String) {
        if (_programs.value.none { it.id == programId }) return
        _programQueue.update { queue -> if (programId in queue) queue else queue + programId }
    }

    fun removeProgramFromQueue(programId: String) {
        _programQueue.update { queue -> queue.filterNot { it == programId } }
    }

    fun moveQueuedProgram(programId: String, direction: Int) {
        _programQueue.update { queue ->
            val from = queue.indexOf(programId)
            val to = (from + direction).coerceIn(0, queue.lastIndex)
            if (from < 0 || from == to) queue
            else queue.toMutableList().apply {
                val item = removeAt(from)
                add(to, item)
            }
        }
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
        val program = _programs.value.find { it.id == programId }
        val resolved = program?.let { buildDefaultActiveProgramState(it, programId) }
        val state = resolved ?: ActiveProgramState(programId = programId, status = ProgramStatus.ACTIVE)
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
        val normalized = log.normalizedIdentityFields()
        _history.update { listOf(normalized) + it }
        scope.launch { db.workoutLogDao().insert(normalized.toEntity()) }
    }

    fun getLogsForProgram(programId: String): List<WorkoutLog> =
        _history.value.filter { it.programId == programId }

    fun getLogsForSession(sessionId: String): List<WorkoutLog> =
        _history.value.filter { it.sessionId == sessionId }

    // ─── Ongoing Workout ──────────────────────────────────────────────────────

    private val _ongoingWorkout = MutableStateFlow<OngoingWorkoutState?>(null)
    val ongoingWorkout: StateFlow<OngoingWorkoutState?> = _ongoingWorkout.asStateFlow()
    private val ongoingWorkoutMutex = Mutex()

    fun startWorkout(state: OngoingWorkoutState) {
        val normalized = state.normalizedIdentityFields()
        _ongoingWorkout.value = normalized
        scope.launch {
            ongoingWorkoutMutex.withLock {
                db.stateDao().upsertOngoingWorkout(normalized.toEntity())
            }
        }
    }

    fun updateOngoingWorkout(update: (OngoingWorkoutState) -> OngoingWorkoutState) {
        _ongoingWorkout.update { current -> current?.let(update)?.normalizedIdentityFields() }
        val target = _ongoingWorkout.value ?: return
        scope.launch {
            ongoingWorkoutMutex.withLock {
                db.stateDao().upsertOngoingWorkout(target.toEntity())
            }
        }
    }

    fun clearOngoingWorkout() {
        _ongoingWorkout.value = null
        scope.launch {
            ongoingWorkoutMutex.withLock {
                db.stateDao().clearOngoingWorkout()
            }
        }
    }

    /**
     * Synchronously persists the current [ongoingWorkout] state to Room.
     * Call from MainActivity's onStop to prevent data loss when the OS kills the process
     * before the background write coroutine completes.
     */
    suspend fun flushPendingWrites() {
        val currentWorkout = _ongoingWorkout.value
        val currentPrograms = _programs.value
        val currentActiveProgram = _activeProgramState.value
        withContext(Dispatchers.IO) {
            currentPrograms.forEach { program ->
                db.programDao().upsert(program.normalizedIdentityFields().toEntity())
            }
            if (currentWorkout != null) {
                db.stateDao().upsertOngoingWorkout(currentWorkout.toEntity())
            }
            if (currentActiveProgram != null) {
                db.stateDao().upsertActiveProgram(currentActiveProgram.toEntity())
            }
        }
    }

    // ─── Settings ─────────────────────────────────────────────────────────────

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    // ─── Workout V2: Context performance + replacement decisions ─────────────

    private val _contextPerformance = MutableStateFlow<Map<String, ContextPerformanceStateV2>>(emptyMap())
    val contextPerformance: StateFlow<Map<String, ContextPerformanceStateV2>> = _contextPerformance.asStateFlow()

    private val _globalPerformance = MutableStateFlow<Map<String, GlobalPerformanceStateV3>>(emptyMap())
    val globalPerformance: StateFlow<Map<String, GlobalPerformanceStateV3>> = _globalPerformance.asStateFlow()

    private val _contextProfiles = MutableStateFlow<Map<String, WorkoutContextProfile>>(emptyMap())
    val contextProfiles: StateFlow<Map<String, WorkoutContextProfile>> = _contextProfiles.asStateFlow()

    private val _replacementDecisions = MutableStateFlow<List<ExerciseReplacementDecisionV2>>(emptyList())
    val replacementDecisions: StateFlow<List<ExerciseReplacementDecisionV2>> = _replacementDecisions.asStateFlow()

    fun updateSettings(update: (Settings) -> Settings) {
        _settings.update(update)
        scope.launch { db.settingsDao().upsert(_settings.value.toEntity()) }
    }

    fun getContextPerformanceState(contextKey: String): ContextPerformanceStateV2? =
        _contextPerformance.value[contextKey]

    fun upsertContextPerformanceState(state: ContextPerformanceStateV2) {
        _contextPerformance.update { it + (state.contextKey to state) }
        scope.launch { db.workoutV2Dao().upsertContextPerformance(state.toEntity()) }
    }

    fun getGlobalPerformanceState(globalKey: String): GlobalPerformanceStateV3? =
        _globalPerformance.value[globalKey]

    fun upsertGlobalPerformanceState(state: GlobalPerformanceStateV3) {
        _globalPerformance.update { it + (state.globalKey to state) }
        scope.launch { db.workoutV2Dao().upsertGlobalPerformance(state.toEntity()) }
    }

    fun getContextProfilesForExercise(exerciseKey: String): List<WorkoutContextProfile> =
        _contextProfiles.value.values
            .filter { it.exerciseKey == exerciseKey }
            .sortedByDescending { it.lastUsedAtIso.orEmpty() }

    fun upsertContextProfile(profile: WorkoutContextProfile) {
        _contextProfiles.update { it + (profile.id to profile) }
        scope.launch { db.workoutV2Dao().upsertContextProfile(profile.toEntity()) }
    }

    fun getReplacementDecisions(programId: String): List<ExerciseReplacementDecisionV2> =
        _replacementDecisions.value.filter { it.programId == programId }

    fun saveReplacementDecision(decision: ExerciseReplacementDecisionV2) {
        _replacementDecisions.update { current ->
            val filtered = current.filterNot { it.id == decision.id }
            listOf(decision) + filtered
        }
        scope.launch { db.workoutV2Dao().upsertReplacementDecision(decision.toEntity()) }
    }

    fun createAndSaveReplacementDecision(
        programId: String,
        sessionId: String,
        macroIndex: Int,
        mesoIndex: Int,
        weekId: String,
        sessionSlot: Int,
        exerciseSlot: Int,
        fromExerciseDbId: String,
        toExerciseDbId: String,
        scopeType: ReplacementPersistenceScopeV2,
    ): ExerciseReplacementDecisionV2 {
        val decision = ExerciseReplacementDecisionV2(
            id = UUID.randomUUID().toString(),
            programId = programId,
            sessionId = sessionId,
            macroIndex = macroIndex,
            mesoIndex = mesoIndex,
            weekId = weekId,
            sessionSlot = sessionSlot,
            exerciseSlot = exerciseSlot,
            fromExerciseDbId = fromExerciseDbId,
            toExerciseDbId = toExerciseDbId,
            scope = scopeType,
            createdAtIso = java.time.Instant.now().toString(),
        )
        saveReplacementDecision(decision)
        return decision
    }

    // ─── Bootstrap ────────────────────────────────────────────────────────────

    /** Carga el estado persistido de Room en los StateFlows. Llamar al arrancar. */
    private fun loadFromDb() {
        scope.launch {
            runCatching {
                val programEntities = db.programDao().getAll()
                val programs = programEntities.map { entity -> entity.toProgram().normalizedIdentityFields() }
                val logEntities = db.workoutLogDao().getAll()
                val logs = logEntities.map { entity -> entity.toWorkoutLog().normalizedIdentityFields() }
                val settings = db.settingsDao().get()?.toSettings() ?: Settings()
                val activeProgram = db.stateDao().getActiveProgram()?.toActiveProgramState()
                val ongoingWorkout = db.stateDao().getOngoingWorkout()?.toOngoingWorkoutState()?.normalizedIdentityFields()
                val contextPerformance = db.workoutV2Dao().getAllContextPerformance()
                    .map { it.toContextPerformanceStateV2() }
                    .associateBy { it.contextKey }
                val globalPerformance = db.workoutV2Dao().getAllGlobalPerformance()
                    .map { it.toGlobalPerformanceStateV3() }
                    .associateBy { it.globalKey }
                val contextProfiles = db.workoutV2Dao().getAllContextProfiles()
                    .map { it.toWorkoutContextProfile() }
                    .associateBy { it.id }
                val replacementDecisions = db.workoutV2Dao().getAllReplacementDecisions()
                    .map { it.toExerciseReplacementDecisionV2() }
                val normalizedActiveProgram = normalizeActiveProgramState(programs, activeProgram)

                programEntities.zip(programs).forEach { (entity, normalized) ->
                    if (entity.toProgram() != normalized) {
                        scope.launch { db.programDao().upsert(normalized.toEntity()) }
                    }
                }
                logEntities.zip(logs).forEach { (entity, normalized) ->
                    if (entity.toWorkoutLog() != normalized) {
                        scope.launch { db.workoutLogDao().insert(normalized.toEntity()) }
                    }
                }
                val persistedOngoing = db.stateDao().getOngoingWorkout()?.toOngoingWorkoutState()
                if (persistedOngoing != null && persistedOngoing != ongoingWorkout && ongoingWorkout != null) {
                    scope.launch { db.stateDao().upsertOngoingWorkout(ongoingWorkout.toEntity()) }
                }

                if (normalizedActiveProgram != activeProgram && normalizedActiveProgram != null) {
                    scope.launch { db.stateDao().upsertActiveProgram(normalizedActiveProgram.toEntity()) }
                }

                withContext(Dispatchers.Main) {
                    _programs.value = programs
                    _history.value = logs
                    _settings.value = settings
                    _activeProgramState.value = normalizedActiveProgram
                    _ongoingWorkout.value = ongoingWorkout
                    _contextPerformance.value = contextPerformance
                    _globalPerformance.value = globalPerformance
                    _contextProfiles.value = contextProfiles
                    _replacementDecisions.value = replacementDecisions
                    _isReady.value = true
                }
            }.onFailure { error ->
                android.util.Log.e("ProgramRepository", "loadFromDb failed", error)
                _isReady.value = true
            }
        }
    }

    private fun Program.upsertSessionInWeek(
        weekId: String,
        macroIndex: Int,
        mesoIndex: Int,
        session: Session,
    ): Program? {
        var changed = false
        val updatedMacrocycles = macrocycles.mapIndexed { currentMacroIndex, macro ->
            var globalMesoIndex = 0
            macro.copy(
                blocks = macro.blocks.map { block ->
                    block.copy(
                        mesocycles = block.mesocycles.map { meso ->
                            val currentGlobalMeso = globalMesoIndex++
                            if (currentMacroIndex != macroIndex || currentGlobalMeso != mesoIndex) {
                                meso
                            } else {
                                meso.copy(
                                    weeks = meso.weeks.map { week ->
                                        if (week.id != weekId) {
                                            week
                                        } else {
                                            changed = true
                                            val replaced = week.sessions.map { existing ->
                                                if (existing.id == session.id) session else existing
                                            }
                                            val nextSessions = if (replaced.any { it.id == session.id }) {
                                                replaced
                                            } else {
                                                replaced + session
                                            }
                                            week.copy(sessions = normalizeMainSessions(nextSessions))
                                        }
                                    }
                                )
                            }
                        }
                    )
                }
            )
        }
        return if (changed) copy(macrocycles = updatedMacrocycles) else null
    }

    private fun normalizeMainSessions(sessions: List<Session>): List<Session> {
        val distinct = sessions.distinctBy { it.id }
        val mainByDay = mutableMapOf<Int, String>()
        val fallbackByDay = mutableMapOf<Int, String>()

        distinct.forEach { session ->
            val day = session.dayOfWeek ?: 1
            fallbackByDay.putIfAbsent(day, session.id)
            if (session.isMainSession && day !in mainByDay) {
                mainByDay[day] = session.id
            }
        }

        fallbackByDay.forEach { (day, sessionId) ->
            mainByDay.putIfAbsent(day, sessionId)
        }

        return distinct.map { session ->
            val day = session.dayOfWeek ?: 1
            session.copy(isMainSession = mainByDay[day] == session.id)
        }
    }

    private data class ProgramWeekLocation(
        val macroIndex: Int,
        val blockIndex: Int,
        val mesocycleIndex: Int,
        val week: ProgramWeek,
    )

    private fun Program.allWeekLocations(): List<ProgramWeekLocation> {
        val locations = mutableListOf<ProgramWeekLocation>()
        var mesoIndex = 0
        macrocycles.forEachIndexed { macroIndex, macro ->
            macro.blocks.forEachIndexed { blockIndex, block ->
                block.mesocycles.forEach { meso ->
                    meso.weeks.forEach { week ->
                        locations += ProgramWeekLocation(
                            macroIndex = macroIndex,
                            blockIndex = blockIndex,
                            mesocycleIndex = mesoIndex,
                            week = week,
                        )
                    }
                    mesoIndex++
                }
            }
        }
        return locations
    }

    private fun Session.matchesDay(dayOfWeek: Int): Boolean =
        this.dayOfWeek == dayOfWeek || assignedDays.contains(dayOfWeek)

    private fun currentDayOfWeek(): Int {
        val today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        return if (today == Calendar.SUNDAY) 7 else today - 1
    }

    private fun Program.resolveDefaultActiveProgramState(
        programId: String,
    ): ActiveProgramState? {
        val preferred = resolveDefaultWeekLocation() ?: return null

        return ActiveProgramState(
            programId = programId,
            status = ProgramStatus.ACTIVE,
            currentMacrocycleIndex = preferred.macroIndex,
            currentBlockIndex = preferred.blockIndex,
            currentMesocycleIndex = preferred.mesocycleIndex,
            currentWeekId = preferred.week.id,
        )
    }

    private fun Program.resolveDefaultWeekLocation(
        dayOfWeek: Int = currentDayOfWeek(),
    ): ProgramWeekLocation? {
        val locations = allWeekLocations()
        if (locations.isEmpty()) return null

        if (com.example.kpkn.domain.training.ProgramCalendarEngine.isCalendarized(this)) {
            val projection = com.example.kpkn.domain.training.ProgramCalendarEngine.project(this)
            val today = java.time.LocalDate.now()
            val calendarWeek = projection.weekForDate(today)
            if (calendarWeek != null) {
                val resolved = locations.firstOrNull { it.week.id == calendarWeek.weekId }
                if (resolved != null) return resolved
            }
        }

        return locations.firstOrNull { location ->
            location.week.sessions.any { it.matchesDay(dayOfWeek) }
        } ?: locations.first()
    }

    private fun buildDefaultActiveProgramState(
        program: Program,
        programId: String,
    ): ActiveProgramState? = program.resolveDefaultActiveProgramState(programId)

    private fun normalizeActiveProgramState(
        programs: List<Program>,
        state: ActiveProgramState?,
    ): ActiveProgramState? {
        if (state == null) return null
        val program = programs.find { it.id == state.programId } ?: return state
        val locations = program.allWeekLocations()
        if (locations.isEmpty()) return state

        if (com.example.kpkn.domain.training.ProgramCalendarEngine.isCalendarized(program)) {
            val projection = com.example.kpkn.domain.training.ProgramCalendarEngine.project(program)
            val today = java.time.LocalDate.now()
            val calendarWeek = projection.weekForDate(today)
            if (calendarWeek != null) {
                val resolved = locations.firstOrNull { it.week.id == calendarWeek.weekId }
                if (resolved != null) {
                    if (state.currentWeekId != resolved.week.id ||
                        state.currentMacrocycleIndex != resolved.macroIndex ||
                        state.currentBlockIndex != resolved.blockIndex ||
                        state.currentMesocycleIndex != resolved.mesocycleIndex
                    ) {
                        return state.copy(
                            currentMacrocycleIndex = resolved.macroIndex,
                            currentBlockIndex = resolved.blockIndex,
                            currentMesocycleIndex = resolved.mesocycleIndex,
                            currentWeekId = resolved.week.id,
                        )
                    }
                    return state
                }
            }
        }

        val exact = locations.firstOrNull { location ->
            location.macroIndex == state.currentMacrocycleIndex &&
                location.blockIndex == state.currentBlockIndex &&
                location.mesocycleIndex == state.currentMesocycleIndex &&
                location.week.id == state.currentWeekId
        }
        if (exact != null) return state

        val sameContainer = locations.firstOrNull { location ->
            location.macroIndex == state.currentMacrocycleIndex &&
                location.blockIndex == state.currentBlockIndex &&
                location.mesocycleIndex == state.currentMesocycleIndex
        } ?: program.resolveDefaultWeekLocation()
            ?: locations.first()

        return state.copy(
            currentMacrocycleIndex = sameContainer.macroIndex,
            currentBlockIndex = sameContainer.blockIndex,
            currentMesocycleIndex = sameContainer.mesocycleIndex,
            currentWeekId = sameContainer.week.id,
        )
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
