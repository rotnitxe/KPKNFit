package com.example.kpkn.data.repository

import android.content.Context
import com.example.kpkn.data.db.*
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.exercises.normalizedIdentityFields
import com.example.kpkn.domain.training.ProgramActiveStateEngine
import com.example.kpkn.domain.training.ProgramCalendarEngine
import com.example.kpkn.domain.training.ProgramMigrationEngine
import com.example.kpkn.domain.training.ProgramProgressEngine
import com.example.kpkn.domain.training.BlockTransitionEngine
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

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

    /** Test-only read-back seam; production callers continue using repository APIs. */
    internal fun databaseForTests(): KpknDatabase = db

    private val repositoryJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + repositoryJob)

    // ─── Programs ─────────────────────────────────────────────────────────────

    private val _programs = MutableStateFlow<List<Program>>(emptyList())
    val programs: StateFlow<List<Program>> = _programs.asStateFlow()
    /** Serializes program blobs so an older async editor write cannot win later. */
    private val programWriteMutex = Mutex()
    private val programWriteSequence = AtomicLong(0L)
    private val newestProgramWrite = ConcurrentHashMap<String, Long>()

    private val _programQueue = MutableStateFlow<List<String>>(emptyList())
    val programQueue: StateFlow<List<String>> = _programQueue.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    fun addProgram(program: Program) {
        val normalized = program.normalizedIdentityFields()
        val version = reserveProgramWrite(normalized.id)
        _programs.update { it + normalized }
        scope.launch { persistProgramIfNewest(normalized, version) }
    }

    fun updateProgram(program: Program) {
        val normalized = program.normalizedIdentityFields()
        val version = reserveProgramWrite(normalized.id)
        _programs.update { list -> list.map { if (it.id == normalized.id) normalized else it } }
        repairActiveStateIfNeeded(normalized)
        scope.launch { persistProgramIfNewest(normalized, version) }
    }

    suspend fun updateProgramNow(program: Program) {
        val normalized = program.normalizedIdentityFields()
        val version = reserveProgramWrite(normalized.id)
        _programs.update { list -> list.map { if (it.id == normalized.id) normalized else it } }
        repairActiveStateIfNeeded(normalized)
        withContext(Dispatchers.IO) { persistProgramIfNewest(normalized, version) }
    }

    private fun reserveProgramWrite(programId: String): Long =
        programWriteSequence.incrementAndGet().also { version -> newestProgramWrite[programId] = version }

    private suspend fun persistProgramIfNewest(program: Program, version: Long) {
        programWriteMutex.withLock {
            if (newestProgramWrite[program.id] == version) {
                db.programDao().upsert(program.toEntity())
            }
        }
    }

    private fun repairActiveStateIfNeeded(program: Program) {
        val active = _activeProgramState.value
        if (active?.programId != program.id) return
        val repaired = ProgramActiveStateEngine.repairForProgram(program, active)
        if (repaired != null && repaired != active) {
            _activeProgramState.value = repaired
            persistActiveProgramStateAsync(repaired)
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
        val version = reserveProgramWrite(programId)
        _programs.update { list -> list.filter { it.id != programId } }
        val nextQueue = _programQueue.value.filterNot { it == programId }
        _programQueue.value = nextQueue
        if (_activeProgramState.value?.programId == programId) {
            clearActiveProgram()
        }
        // The in-flight workout writer and lifecycle flush share this mutex.
        // The program check is intentionally inside the lane: a stale
        // startWorkout must not win after this delete has removed the cache row.
        clearOngoingWorkoutForProgram(programId)
        scope.launch {
            programWriteMutex.withLock {
                if (newestProgramWrite[programId] == version) db.programDao().delete(programId)
            }
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
            val activeVersion = reserveActiveStateWrite()
            _programs.value = emptyList()
            _programQueue.value = emptyList()
            _activeProgramState.value = null
            _ongoingWorkout.value = null
            db.programDao().deleteAll()
            persistActiveProgramStateIfLatest(null, activeVersion)
            db.stateDao().clearOngoingWorkout()
        }
    }

    fun getProgramById(id: String): Program? = _programs.value.find { it.id == id }

    // ─── Active Program State ─────────────────────────────────────────────────

    private val _activeProgramState = MutableStateFlow<ActiveProgramState?>(null)
    val activeProgramState: StateFlow<ActiveProgramState?> = _activeProgramState.asStateFlow()
    /** Serializes active-state cache writes so a repair cannot overwrite a newer cursor. */
    private val activeStateWriteMutex = Mutex()
    private val activeStateWriteSequence = AtomicLong(0L)
    @Volatile private var newestActiveStateWrite: Long = 0L

    private fun reserveActiveStateWrite(): Long =
        activeStateWriteSequence.incrementAndGet().also { newestActiveStateWrite = it }

    /**
     * The active cursor has one write lane.  A null value is a real tombstone,
     * not a cache-only update: it must win over an older in-flight upsert so a
     * cleared/archived program cannot be resurrected by a late coroutine.
     */
    private fun persistActiveProgramStateAsync(state: ActiveProgramState?) {
        val version = reserveActiveStateWrite()
        scope.launch {
            persistActiveProgramStateIfLatest(state, version)
        }
    }

    /** Synchronous counterpart used by lifecycle flush; still version-guarded. */
    private suspend fun persistActiveProgramStateNow(
        state: ActiveProgramState?,
        version: Long = reserveActiveStateWrite(),
    ) {
        persistActiveProgramStateIfLatest(state, version)
    }

    /** Single guarded Room lane used by async commands and transactions. */
    private suspend fun persistActiveProgramStateIfLatest(
        state: ActiveProgramState?,
        version: Long,
    ) {
        activeStateWriteMutex.withLock {
            persistActiveProgramStateLocked(state, version)
        }
    }

    /**
     * Room-only half of the active-state lane.  Callers must already hold
     * [activeStateWriteMutex]; keeping this separate is required when the
     * write participates in an enclosing Room transaction.  Acquiring the
     * mutex from inside that transaction reverses the async lane's order
     * (mutex -> Room) and can deadlock with a pending writer.
     */
    private suspend fun persistActiveProgramStateLocked(
        state: ActiveProgramState?,
        version: Long,
    ) {
        if (newestActiveStateWrite != version) return
        if (state == null) {
            db.stateDao().clearActiveProgram()
        } else {
            db.stateDao().upsertActiveProgram(state.toEntity())
        }
    }

    fun startProgram(programId: String) {
        val program = _programs.value.find { it.id == programId }
        val resolved = program?.let { buildDefaultActiveProgramState(it, programId) }
            ?.let { state -> ProgramActiveStateEngine.repairForProgram(program, state) }
        val state = resolved ?: ActiveProgramState(programId = programId, status = ProgramStatus.ACTIVE)
        _activeProgramState.value = state
        persistActiveProgramStateAsync(state)
    }

    fun pauseProgram() {
        _activeProgramState.update { it?.copy(status = ProgramStatus.PAUSED) }
        _activeProgramState.value?.let(::persistActiveProgramStateAsync)
    }

    fun resumeProgram() {
        _activeProgramState.update { it?.copy(status = ProgramStatus.ACTIVE) }
        _activeProgramState.value?.let(::persistActiveProgramStateAsync)
    }

    fun advanceWeek(nextWeekId: String) {
        _activeProgramState.update { it?.copy(currentWeekId = nextWeekId) }
        _activeProgramState.value?.let(::persistActiveProgramStateAsync)
    }

    /** Commits a domain-engine cursor transition without rebuilding it heuristically. */
    fun updateActiveProgramState(state: ActiveProgramState) {
        val program = _programs.value.firstOrNull { it.id == state.programId }
        val resolved = if (
            program?.structure == ProgramStructure.COMPLEX && program.runState != null
        ) {
            ProgramActiveStateEngine.repairForProgram(program, state) ?: state
        } else state
        _activeProgramState.value = resolved
        persistActiveProgramStateAsync(resolved)
    }

    fun clearActiveProgram() {
        _activeProgramState.value = null
        persistActiveProgramStateAsync(null)
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
     * progress + active state in one Room transaction.
     *
     * Idempotent: if [log.id] already exists, identity fields are frozen from the first
     * write and progress is not reapplied.
     */
    suspend fun finalizeWorkout(log: WorkoutLog) {
        withContext(Dispatchers.IO + NonCancellable) {
            ongoingWorkoutMutex.withLock {
                val prior = _history.value.firstOrNull { it.id == log.id }
                    ?: db.workoutLogDao().getById(log.id)?.toWorkoutLog()?.normalizedIdentityFields()

                if (prior != null) {
                    // Retry path: never reinterpret identity against the current cursor.
                    db.withTransaction {
                        db.workoutLogDao().insert(prior.toEntity())
                        db.stateDao().clearOngoingWorkout()
                    }
                    _history.value = listOf(prior) + _history.value.filterNot { it.id == prior.id }
                    _ongoingWorkout.value = null
                    return@withLock
                }

                val program = getProgramById(log.programId)
                val active = _activeProgramState.value
                val isCalendarized = program?.isSimpleCalendarizedProgram == true
                val isComplex = program?.structure == ProgramStructure.COMPLEX
                val breakId = program?.activeCalendarBreakId

                val enriched = if (isCalendarized) {
                    val templateWeekId = log.weekId?.let {
                        ProgramProgressEngine.templateWeekIdFromInstance(it) ?: it
                    } ?: log.weekId
                    log.copy(
                        programRunId = log.programRunId
                            ?: program?.runState?.runId
                            ?: "run_cal_${program?.id ?: log.programId}",
                        // Do not stamp paused cyclic cycle/instance onto break logs.
                        cycleNumber = null,
                        weekInstanceId = log.weekInstanceId ?: templateWeekId,
                        weekId = templateWeekId,
                        calendarBreakId = log.calendarBreakId ?: breakId,
                    ).normalizedIdentityFields()
                } else {
                    val cycleNumber = if (isComplex) 1 else log.cycleNumber
                        ?: program?.runState?.cycleNumber
                        ?: active?.currentCycleNumber
                        ?: 1
                    val templateWeekId = log.weekId?.let {
                        ProgramProgressEngine.templateWeekIdFromInstance(it) ?: it
                    } ?: active?.currentWeekId?.let {
                        ProgramProgressEngine.templateWeekIdFromInstance(it) ?: it
                    }
                    val resolvedInstanceId = if (isComplex) {
                        log.weekInstanceId
                            ?: active?.currentWeekInstanceId
                            ?: program?.runState?.weekInstanceId
                            ?: templateWeekId
                            ?: log.weekId
                    } else {
                        log.weekInstanceId
                            ?: active?.currentWeekInstanceId
                            ?: templateWeekId?.let { ProgramProgressEngine.instanceIdFor(cycleNumber, it) }
                            ?: log.weekId
                    }
                    log.copy(
                        programRunId = log.programRunId ?: program?.runState?.runId ?: active?.programRunId,
                        cycleNumber = cycleNumber,
                        weekInstanceId = resolvedInstanceId,
                        weekId = templateWeekId ?: log.weekId,
                        calendarBreakId = null,
                    ).normalizedIdentityFields()
                }

                val historyForProgress = listOf(enriched) + _history.value.filterNot { it.id == enriched.id }
                val progress = if (
                    program != null &&
                    !isCalendarized &&
                    (program.isSimpleProgram || program.structure == ProgramStructure.COMPLEX) &&
                    enriched.calendarBreakId.isNullOrBlank()
                ) {
                    ProgramProgressEngine.advanceAfterSessionComplete(
                        program = program,
                        activeState = active,
                        completedSession = Session(id = enriched.sessionId, name = enriched.sessionName),
                        weekInstanceId = enriched.weekInstanceId ?: enriched.weekId.orEmpty(),
                        logs = historyForProgress,
                        transitionContext = buildTransitionContext(program, historyForProgress),
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

                val nextProgramVersion = nextProgram?.let { reserveProgramWrite(it.id) }
                // Reserve the active-state version before entering the Room
                // transaction. A concurrent updateActiveProgramState can then
                // supersede this cursor instead of being overwritten by a
                // late finalization write.
                val repairedActiveVersion = repairedActive?.let { reserveActiveStateWrite() }
                suspend fun persistFinalization() {
                    // Keep the lock order identical to the async lane:
                    // active mutex first, then Room.  The transaction uses the
                    // locked-only helper and therefore never acquires a mutex
                    // while Room already owns its transaction connection.
                    activeStateWriteMutex.withLock {
                        db.withTransaction {
                            db.workoutLogDao().insert(enriched.toEntity())
                            db.stateDao().clearOngoingWorkout()
                            if (nextProgram != null && nextProgramVersion != null && newestProgramWrite[nextProgram.id] == nextProgramVersion) {
                                db.programDao().upsert(nextProgram.toEntity())
                            }
                            if (repairedActive != null && repairedActiveVersion != null) {
                                persistActiveProgramStateLocked(repairedActive, repairedActiveVersion)
                            }
                        }
                    }
                }
                if (nextProgram != null) {
                    programWriteMutex.withLock { persistFinalization() }
                } else {
                    persistFinalization()
                }

                _history.value = historyForProgress
                _ongoingWorkout.value = null
                if (nextProgram != null && nextProgramVersion != null && newestProgramWrite[nextProgram.id] == nextProgramVersion) {
                    _programs.update { list -> list.map { if (it.id == nextProgram.id) nextProgram else it } }
                }
                if (repairedActive != null && repairedActiveVersion != null && newestActiveStateWrite == repairedActiveVersion) {
                    _activeProgramState.value = repairedActive
                }
            }
        }
    }

    /**
     * Builds the transition evidence from persisted workout history. A missing
     * readiness measurement intentionally stays null: this layer must not
     * manufacture a readiness score just to satisfy the AUGE gate. Stress EMA,
     * athlete-reported fatigue, and overtrained muscles all come from logs.
     */
    private fun buildTransitionContext(
        program: Program,
        history: List<WorkoutLog>,
    ): BlockTransitionEngine.TransitionContext {
        val relevant = history
            .asSequence()
            .filter { it.programId == program.id }
            .sortedBy { it.date }
            .toList()
        val stressScores = relevant.mapNotNull { it.sessionStressScore?.takeIf { score -> score.isFinite() } }
        var stressEma = 0.0
        stressScores.forEachIndexed { index, score ->
            stressEma = if (index == 0) score else {
                // Same smoothing constant used by AugeFatigueEngine's
                // mesocycle EMA; no synthetic score is introduced.
                (0.17 * score) + (0.83 * stressEma)
            }
        }
        val measuredFatigue = relevant
            .asReversed()
            .firstNotNullOfOrNull { it.fatigueLevel?.coerceIn(1, 10)?.times(10.0) }
        return BlockTransitionEngine.TransitionContext(
            cumulativeFatigue = measuredFatigue,
            // WorkoutLog currently has no persisted readiness verdict. Keep
            // this null so shouldSuggestAutoDeload cannot infer one.
            readinessScore = null,
            settings = _settings.value,
            mesocycleStressEma = stressEma,
            overtrainedMuscles = BlockTransitionEngine.detectOvertrained(program, relevant),
        )
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
                // A blank program id is the explicit ad-hoc workout sentinel.
                // Any non-blank id must still exist in the authoritative cache;
                // otherwise a delayed start for a deleted program would revive
                // an ongoing Room row after deleteProgram returned.
                if (state.programId.isNotBlank() && _programs.value.none { it.id == state.programId }) {
                    _ongoingWorkout.value = null
                    db.stateDao().clearOngoingWorkout()
                    return@withLock
                }
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

    private fun clearOngoingWorkoutForProgram(programId: String) {
        runBlocking(Dispatchers.IO + NonCancellable) {
            ongoingWorkoutMutex.withLock {
                if (_ongoingWorkout.value?.programId != programId) return@withLock
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
        // Reserve both lanes before taking their snapshots. A concurrent
        // command that mutates either cache after this point receives a newer
        // version and supersedes this lifecycle flush.
        val activeVersion = reserveActiveStateWrite()
        val programIds = _programs.value.map { it.id }
        val programVersions = programIds.associateWith(::reserveProgramWrite)
        val currentActiveProgram = _activeProgramState.value
        val currentPrograms = _programs.value.filter { it.id in programVersions }
        val latestLogs = _history.value.take(32)
        withContext(Dispatchers.IO + NonCancellable) {
            // Finalization acquires ongoing→program; keep the same order here
            // so a lifecycle flush cannot deadlock a concurrent completion.
            ongoingWorkoutMutex.withLock {
                val workout = _ongoingWorkout.value
                if (workout != null) {
                    db.stateDao().upsertOngoingWorkout(workout.toEntity())
                } else {
                    db.stateDao().clearOngoingWorkout()
                }
            }
            // Keep program→active→Room ordering aligned with finalizeWorkout.
            programWriteMutex.withLock {
                currentPrograms.forEach { program ->
                    val version = programVersions[program.id] ?: return@forEach
                    if (newestProgramWrite[program.id] == version) {
                        db.programDao().upsert(program.normalizedIdentityFields().toEntity())
                    }
                }
                // Null is an explicit tombstone. The same versioned lane is
                // used for both upsert and clear so an old cursor cannot be
                // revived on process stop.
                persistActiveProgramStateNow(currentActiveProgram, activeVersion)
                latestLogs.forEach { log ->
                    db.workoutLogDao().insert(log.toEntity())
                }
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

    fun deleteContextProfile(profileId: String) {
        _contextProfiles.update { it - profileId }
        scope.launch { db.workoutV2Dao().deleteContextProfile(profileId) }
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
        fromCatalogRevision: String? = null,
        fromDefinitionId: String? = null,
        fromConfigurationId: String? = null,
        toCatalogRevision: String? = null,
        toDefinitionId: String? = null,
        toConfigurationId: String? = null,
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
            fromCatalogRevision = fromCatalogRevision,
            fromDefinitionId = fromDefinitionId,
            fromConfigurationId = fromConfigurationId,
            toCatalogRevision = toCatalogRevision,
            toDefinitionId = toDefinitionId,
            toConfigurationId = toConfigurationId,
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
                val previouslyQuarantined = settings.quarantinedProgramIds.toSet()
                // Drop rows already quarantined so decode failures are not retried forever.
                previouslyQuarantined.forEach { id ->
                    if (programEntities.any { it.id == id }) {
                        db.programDao().delete(id)
                    }
                }
                val freshCorrupted = migrationLoad.corruptedIds.filterNot { it in previouslyQuarantined }
                if (migrationLoad.corruptedIds.isNotEmpty()) {
                    val backups = settings.quarantinedProgramBackups.toMutableMap()
                    migrationLoad.corruptedIds.forEach { id ->
                        val entity = programEntities.firstOrNull { it.id == id }
                        if (entity != null) backups[id] = entity.data
                    }
                    val quarantined = (settings.quarantinedProgramIds + migrationLoad.corruptedIds).distinct()
                    settings = settings.copy(
                        quarantinedProgramIds = quarantined,
                        quarantinedProgramBackups = backups,
                    )
                    db.settingsDao().upsert(settings.toEntity())
                    // Remove corrupt rows after backup — quarantine must not leave failing JSON in Room.
                    migrationLoad.corruptedIds.forEach { id ->
                        db.programDao().delete(id)
                    }
                    if (freshCorrupted.isNotEmpty()) {
                        android.util.Log.w(
                            "ProgramRepository",
                            "Quarantined and removed corrupted programs: $freshCorrupted",
                        )
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

                if (normalizedActiveProgram != activeProgram) {
                    persistActiveProgramStateAsync(normalizedActiveProgram)
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

        if (structure == ProgramStructure.COMPLEX) {
            val requestedWeekId = runState?.weekId
                ?.let { ProgramProgressEngine.templateWeekIdFromInstance(it) ?: it }
            val requestedLocation = requestedWeekId?.let {
                com.example.kpkn.domain.training.ProgramHierarchyIndex(this).locateWeek(it)
            }
            if (requestedLocation != null) {
                return ActiveProgramState(
                    programId = programId,
                    status = if (runState?.status == ProgramRunStatus.COMPLETED) ProgramStatus.COMPLETED else ProgramStatus.ACTIVE,
                    currentMacrocycleIndex = requestedLocation.macroIndex,
                    currentBlockIndex = requestedLocation.blockIndex,
                    currentMesocycleIndex = requestedLocation.globalMesoIndex,
                    currentWeekId = requestedLocation.week.id,
                    currentWeekInstanceId = requestedLocation.week.id,
                    currentCycleNumber = 1,
                    programRunId = runState?.runId,
                    currentMacrocycleId = requestedLocation.macrocycleId,
                    currentBlockId = requestedLocation.blockId,
                    currentMesocycleId = requestedLocation.mesocycleId,
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

    /**
     * Reconciles expired simple calendarizations and other temporal migrations while the
     * process is alive (e.g. after crossing the end date without restarting).
     */
    fun reconcileTemporalState(clock: com.example.kpkn.domain.training.AppClock = com.example.kpkn.domain.training.SystemAppClock) {
        scope.launch {
            val snapshot = _programs.value
            if (snapshot.isEmpty()) return@launch
            var changed = false
            val next = snapshot.map { program ->
                val migrated = ProgramMigrationEngine.reconcileExpiredCalendarization(program, clock).program
                    .normalizedIdentityFields()
                if (migrated != program) {
                    changed = true
                    db.programDao().upsert(migrated.toEntity())
                    migrated
                } else {
                    program
                }
            }
            if (!changed) return@launch
            withContext(Dispatchers.Main) {
                _programs.value = next
                next.forEach { repairActiveStateIfNeeded(it) }
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
