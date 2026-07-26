package com.example.kpkn.data.repository

import android.content.Context
import com.example.kpkn.data.db.*
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.exercises.normalizedIdentityFields
import com.example.kpkn.domain.training.ProgramActiveStateEngine
import com.example.kpkn.domain.training.ProgramCalendarEngine
import com.example.kpkn.domain.training.ProgramMigrationEngine
import com.example.kpkn.domain.training.ProgramProgressEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.room.withTransaction
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
 * - Ongoing workout mutations write Room before returning (durable)
 */
class ProgramRepository private constructor(
    private val db: KpknDatabase,
    private val ownsDatabase: Boolean = false,
) {

    private val repositoryJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + repositoryJob)

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
        repairActiveStateIfNeeded(normalized)
        scope.launch { db.programDao().upsert(normalized.toEntity()) }
    }

    suspend fun updateProgramNow(program: Program) {
        val normalized = program.normalizedIdentityFields()
        _programs.update { list -> list.map { if (it.id == normalized.id) normalized else it } }
        repairActiveStateIfNeeded(normalized)
        withContext(Dispatchers.IO) { db.programDao().upsert(normalized.toEntity()) }
    }

    private fun repairActiveStateIfNeeded(program: Program) {
        val active = _activeProgramState.value
        if (active?.programId != program.id) return
        val repaired = ProgramActiveStateEngine.repairForProgram(program, active)
        if (repaired != null && repaired != active) {
            _activeProgramState.value = repaired
            scope.launch { db.stateDao().upsertActiveProgram(repaired.toEntity()) }
        }
    }

    fun archiveProgram(programId: String) {
        val archived = _settings.value.archivedProgramIds
        if (programId in archived) return
        if (_activeProgramState.value?.programId == programId) {
            clearActiveProgram()
        }
        if (_ongoingWorkout.value?.programId == programId) {
            clearOngoingWorkout()
        }
        val nextArchived = archived + programId
        val nextQueue = _programQueue.value.filterNot { it == programId }
        _programQueue.value = nextQueue
        scope.launch {
            val updated = _settings.value.copy(
                archivedProgramIds = nextArchived,
                programQueueIds = nextQueue,
            )
            _settings.value = updated
            db.settingsDao().upsert(updated.toEntity())
        }
    }

    fun restoreArchivedProgram(programId: String) {
        val nextArchived = _settings.value.archivedProgramIds.filterNot { it == programId }
        scope.launch {
            val updated = _settings.value.copy(archivedProgramIds = nextArchived)
            _settings.value = updated
            db.settingsDao().upsert(updated.toEntity())
        }
    }

    fun permanentlyDeleteProgram(programId: String) {
        deleteProgram(programId)
        val nextArchived = _settings.value.archivedProgramIds.filterNot { it == programId }
        scope.launch {
            val updated = _settings.value.copy(archivedProgramIds = nextArchived)
            _settings.value = updated
            db.settingsDao().upsert(updated.toEntity())
        }
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
        val nextQueue = _programQueue.value.filterNot { it == programId }
        _programQueue.value = nextQueue
        if (_activeProgramState.value?.programId == programId) {
            clearActiveProgram()
        }
        if (_ongoingWorkout.value?.programId == programId) {
            _ongoingWorkout.value = null
            scope.launch { db.stateDao().clearOngoingWorkout() }
        }
        scope.launch {
            db.programDao().delete(programId)
            val updatedSettings = _settings.value.copy(programQueueIds = nextQueue)
            _settings.value = updatedSettings
            db.settingsDao().upsert(updatedSettings.toEntity())
        }
    }

    fun addProgramToQueue(programId: String) {
        if (_programs.value.none { it.id == programId }) return
        val next = if (programId in _programQueue.value) _programQueue.value else _programQueue.value + programId
        persistProgramQueue(next)
    }

    fun removeProgramFromQueue(programId: String) {
        persistProgramQueue(_programQueue.value.filterNot { it == programId })
    }

    fun moveQueuedProgram(programId: String, direction: Int) {
        val queue = _programQueue.value
        val from = queue.indexOf(programId)
        val to = (from + direction).coerceIn(0, queue.lastIndex)
        if (from < 0 || from == to) return
        val next = queue.toMutableList().apply {
            val item = removeAt(from)
            add(to, item)
        }
        persistProgramQueue(next)
    }

    private fun persistProgramQueue(queue: List<String>) {
        _programQueue.value = queue
        scope.launch {
            val updated = _settings.value.copy(programQueueIds = queue)
            _settings.value = updated
            db.settingsDao().upsert(updated.toEntity())
        }
    }

    fun clearPrograms() {
        _programs.value = emptyList()
        scope.launch { db.programDao().deleteAll() }
    }

    /** Synchronous wipe for Robolectric tests — avoids SQLite races between async clears and writes. */
    internal fun resetAllStateSync() {
        runBlocking(Dispatchers.IO + NonCancellable) {
            _programs.value = emptyList()
            _programQueue.value = emptyList()
            _activeProgramState.value = null
            _ongoingWorkout.value = null
            db.programDao().deleteAll()
            db.stateDao().clearActiveProgram()
            db.stateDao().clearOngoingWorkout()
        }
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
        _history.update { listOf(normalized) + it.filterNot { existing -> existing.id == normalized.id } }
        scope.launch { db.workoutLogDao().insert(normalized.toEntity()) }
    }

    /**
     * Atomically persists the finished log, clears ongoing, and commits simple-program
     * progress + active state in one Room transaction. Safe to retry: progress engine
     * is idempotent once the cursor has moved past the completed instance.
     */
    suspend fun finalizeWorkout(log: WorkoutLog) {
        withContext(Dispatchers.IO + NonCancellable) {
            ongoingWorkoutMutex.withLock {
                val program = getProgramById(log.programId)
                val active = _activeProgramState.value
                val cycleNumber = log.cycleNumber
                    ?: program?.runState?.cycleNumber
                    ?: active?.currentCycleNumber
                    ?: 1
                val templateWeekId = log.weekId?.let {
                    ProgramProgressEngine.templateWeekIdFromInstance(it) ?: it
                }
                val resolvedInstanceId = log.weekInstanceId
                    ?: active?.currentWeekInstanceId
                    ?: templateWeekId?.let { ProgramProgressEngine.instanceIdFor(cycleNumber, it) }
                    ?: log.weekId
                val enriched = log.copy(
                    programRunId = log.programRunId ?: program?.runState?.runId ?: active?.programRunId,
                    cycleNumber = cycleNumber,
                    weekInstanceId = resolvedInstanceId,
                    weekId = templateWeekId ?: log.weekId,
                ).normalizedIdentityFields()

                val historyForProgress = listOf(enriched) + _history.value.filterNot { it.id == enriched.id }
                val progress = if (program != null && program.isSimpleProgram && !program.isSimpleCalendarizedProgram) {
                    ProgramProgressEngine.advanceAfterSessionComplete(
                        program = program,
                        activeState = active,
                        completedSession = Session(id = enriched.sessionId, name = enriched.sessionName),
                        weekInstanceId = enriched.weekInstanceId ?: enriched.weekId.orEmpty(),
                        logs = historyForProgress,
                    )
                } else {
                    null
                }
                val nextProgram = progress?.program
                    ?.takeIf { it != program }
                    ?.let { ProgramCalendarEngine.materializeWeekDates(it).normalizedIdentityFields() }
                val progressActive = progress?.activeState
                val repairedActive = when {
                    progressActive != null && progressActive != active -> progressActive
                    nextProgram != null -> ProgramActiveStateEngine.repairForProgram(nextProgram, active)
                        ?.takeIf { it != active }
                    else -> null
                }

                db.withTransaction {
                    db.workoutLogDao().insert(enriched.toEntity())
                    db.stateDao().clearOngoingWorkout()
                    if (nextProgram != null) {
                        db.programDao().upsert(nextProgram.toEntity())
                    }
                    if (repairedActive != null) {
                        db.stateDao().upsertActiveProgram(repairedActive.toEntity())
                    }
                }

                _history.value = historyForProgress
                _ongoingWorkout.value = null
                if (nextProgram != null) {
                    _programs.update { list -> list.map { if (it.id == nextProgram.id) nextProgram else it } }
                }
                if (repairedActive != null) {
                    _activeProgramState.value = repairedActive
                }
            }
        }
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
        runBlocking(Dispatchers.IO + NonCancellable) {
            ongoingWorkoutMutex.withLock {
                val normalized = state.normalizedIdentityFields()
                _ongoingWorkout.value = normalized
                db.stateDao().upsertOngoingWorkout(normalized.toEntity())
            }
        }
    }

    fun updateOngoingWorkout(update: (OngoingWorkoutState) -> OngoingWorkoutState) {
        runBlocking(Dispatchers.IO + NonCancellable) {
            writeOngoingLocked(update)
        }
    }

    /**
     * Updates ongoing state in memory and waits until Room has the same snapshot.
     * Use for structural session events (recorded set, skip, pause, finish prep).
     */
    suspend fun updateOngoingWorkoutAndFlush(update: (OngoingWorkoutState) -> OngoingWorkoutState) {
        withContext(Dispatchers.IO + NonCancellable) {
            writeOngoingLocked(update)
        }
    }

    private suspend fun writeOngoingLocked(update: (OngoingWorkoutState) -> OngoingWorkoutState) {
        ongoingWorkoutMutex.withLock {
            val current = _ongoingWorkout.value ?: return@withLock
            val next = update(current).normalizedIdentityFields()
            _ongoingWorkout.value = next
            db.stateDao().upsertOngoingWorkout(next.toEntity())
        }
    }

    fun clearOngoingWorkout() {
        runBlocking(Dispatchers.IO + NonCancellable) {
            ongoingWorkoutMutex.withLock {
                _ongoingWorkout.value = null
                db.stateDao().clearOngoingWorkout()
            }
        }
    }

    /** Clears ongoing in memory and waits for Room delete. */
    suspend fun clearOngoingWorkoutAndFlush() {
        withContext(Dispatchers.IO + NonCancellable) {
            ongoingWorkoutMutex.withLock {
                _ongoingWorkout.value = null
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
        val latestLogs = _history.value.take(32)
        withContext(Dispatchers.IO + NonCancellable) {
            currentPrograms.forEach { program ->
                db.programDao().upsert(program.normalizedIdentityFields().toEntity())
            }
            ongoingWorkoutMutex.withLock {
                val workout = _ongoingWorkout.value ?: currentWorkout
                if (workout != null) {
                    db.stateDao().upsertOngoingWorkout(workout.toEntity())
                }
            }
            if (currentActiveProgram != null) {
                db.stateDao().upsertActiveProgram(currentActiveProgram.toEntity())
            }
            latestLogs.forEach { log ->
                db.workoutLogDao().insert(log.toEntity())
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
                val rawPrograms = programEntities.map { entity ->
                    entity.id to runCatching { entity.toProgram() }.getOrNull()
                }
                val migrationLoad = ProgramMigrationEngine.loadProgramsSafely(rawPrograms)
                val programs = migrationLoad.programs.map { it.normalizedIdentityFields() }
                val programsById = programs.associateBy { it.id }
                if (migrationLoad.corruptedIds.isNotEmpty()) {
                    android.util.Log.w(
                        "ProgramRepository",
                        "Skipped corrupted programs: ${migrationLoad.corruptedIds}",
                    )
                }
                val logEntities = db.workoutLogDao().getAll()
                val logs = logEntities.mapNotNull { entity ->
                    entity.toWorkoutLog()?.normalizedIdentityFields()
                }
                val settingsEntity = db.settingsDao().get()
                var settings = settingsEntity?.toSettings() ?: Settings()
                if (migrationLoad.corruptedIds.isNotEmpty()) {
                    val backups = settings.quarantinedProgramBackups.toMutableMap()
                    migrationLoad.corruptedIds.forEach { id ->
                        val entity = programEntities.firstOrNull { it.id == id }
                        if (entity != null) backups[id] = entity.data
                    }
                    val quarantined = (settings.quarantinedProgramIds + migrationLoad.corruptedIds).distinct()
                    if (quarantined != settings.quarantinedProgramIds || backups != settings.quarantinedProgramBackups) {
                        settings = settings.copy(
                            quarantinedProgramIds = quarantined,
                            quarantinedProgramBackups = backups,
                        )
                        db.settingsDao().upsert(settings.toEntity())
                    }
                }
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

                programEntities.forEach { entity ->
                    val normalized = programsById[entity.id] ?: return@forEach
                    val migrated = ProgramMigrationEngine.migrateIfNeeded(
                        runCatching { entity.toProgram() }.getOrNull() ?: normalized,
                    ).program.normalizedIdentityFields()
                    if (entity.toProgram() != migrated || migrated != normalized) {
                        scope.launch { db.programDao().upsert(migrated.toEntity()) }
                    }
                }
                logEntities.forEach { entity ->
                    val normalized = entity.toWorkoutLog()?.normalizedIdentityFields() ?: return@forEach
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
                    _programQueue.value = settings.programQueueIds.filter { id ->
                        programs.any { it.id == id }
                    }
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

    private fun Program.allWeekLocations(): List<ProgramWeekLocation> =
        com.example.kpkn.domain.training.ProgramHierarchyIndex(this).orderedWeeks().map { location ->
            ProgramWeekLocation(
                macroIndex = location.macroIndex,
                blockIndex = location.blockIndex,
                mesocycleIndex = location.globalMesoIndex,
                week = location.week,
            )
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
        if (isSimpleProgram && simpleProgramKind == SimpleProgramKind.CYCLIC) {
            val cycle = runState?.cycleNumber ?: loopState?.currentCycle?.coerceAtLeast(1) ?: 1
            val instances = ProgramProgressEngine.resolveCurrentWeekInstances(this, cycle)
            val instance = instances.firstOrNull { it.instanceId == runState?.weekInstanceId }
                ?: instances.firstOrNull { it.templateWeekId == runState?.weekId }
                ?: instances.firstOrNull()
            if (instance != null) {
                val location = com.example.kpkn.domain.training.ProgramHierarchyIndex(this)
                    .locateWeek(instance.templateWeekId)
                return ActiveProgramState(
                    programId = programId,
                    status = ProgramStatus.ACTIVE,
                    currentMacrocycleIndex = location?.macroIndex ?: instance.macroIndex,
                    currentBlockIndex = location?.blockIndex ?: instance.blockIndex,
                    currentMesocycleIndex = location?.globalMesoIndex ?: instance.mesoIndex,
                    currentWeekId = instance.instanceId,
                    currentWeekInstanceId = instance.instanceId,
                    currentCycleNumber = cycle,
                    programRunId = runState?.runId ?: ProgramProgressEngine.newRunId(),
                    currentMacrocycleId = location?.macrocycleId,
                    currentBlockId = location?.blockId,
                    currentMesocycleId = location?.mesocycleId,
                )
            }
        }

        val preferred = resolveDefaultWeekLocation() ?: return null
        val location = com.example.kpkn.domain.training.ProgramHierarchyIndex(this)
            .locateWeek(preferred.week.id)
        return ActiveProgramState(
            programId = programId,
            status = ProgramStatus.ACTIVE,
            currentMacrocycleIndex = location?.macroIndex ?: preferred.macroIndex,
            currentBlockIndex = location?.blockIndex ?: preferred.blockIndex,
            currentMesocycleIndex = location?.globalMesoIndex ?: preferred.mesocycleIndex,
            currentWeekId = preferred.week.id,
            currentMacrocycleId = location?.macrocycleId,
            currentBlockId = location?.blockId,
            currentMesocycleId = location?.mesocycleId,
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
        val program = programs.find { it.id == state.programId } ?: return null
        return ProgramActiveStateEngine.repairForProgram(program, state)
    }
    fun refreshData() {
        loadFromDb()
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
                INSTANCE ?: ProgramRepository(
                    KpknDatabase.getInstance(context.applicationContext),
                ).also { INSTANCE = it; it.loadFromDb() }
            }

        /** Robolectric-friendly init with in-memory Room and isolated singleton. */
        fun initForTests(context: Context): ProgramRepository = synchronized(this) {
            closeInstance()
            KpknDatabase.closeInstance()
            ProgramRepository(
                KpknDatabase.createInMemory(context.applicationContext),
                ownsDatabase = true,
            ).also { INSTANCE = it; it.loadFromDb() }
        }

        /** Acceso rápido después de init(). */
        fun getInstance(): ProgramRepository =
            INSTANCE ?: error("ProgramRepository not initialized — call init(context) first.")

        internal fun closeInstance() {
            INSTANCE?.let { runBlocking { it.repositoryJob.cancelAndJoin() } }
            INSTANCE?.takeIf { it.ownsDatabase }?.db?.close()
            INSTANCE = null
        }
    }
}
