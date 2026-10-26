package com.example.kpkn.data.repository

import android.content.Context
import com.example.kpkn.data.db.*
import com.example.kpkn.data.models.*
import com.example.kpkn.data.persistence.PersistenceWriteCoordinator
import com.example.kpkn.domain.exercises.normalizedIdentityFields
import com.example.kpkn.domain.exercises.ExerciseNicknameResolver
import com.example.kpkn.data.exercises.exerciseCatalogSnapshot
import com.example.kpkn.domain.training.ProgramActiveStateEngine
import com.example.kpkn.domain.training.ProgramCalendarEngine
import com.example.kpkn.domain.training.ProgramMigrationEngine
import com.example.kpkn.domain.training.ProgramPersistNormalizer
import com.example.kpkn.domain.training.ProgramKeyDateEngine
import com.example.kpkn.domain.training.ProgramProgressEngine
import com.example.kpkn.domain.training.PlanMaterializer
import com.example.kpkn.domain.training.CompositionMetadataHolder
import com.example.kpkn.domain.training.coerceNativeWeekInstanceId
import com.example.kpkn.domain.training.requiresNativeWeekInstances
import com.example.kpkn.domain.training.NativeWorkoutProgressionRuntime
import com.example.kpkn.domain.training.BlockTransitionEngine
import com.example.kpkn.domain.training.WeeklyAutoregulationSignals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.room.Room
import androidx.room.withTransaction
import kotlin.time.Duration.Companion.seconds
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

private const val PROGRAM_DATA_CHUNK_CHARS = 128_000

/** Reintentos de [ProgramRepository.resolveNativeProgressionProposalNow] cuando la sesión/historial cambian a mitad. */
private const val NATIVE_RESOLVE_MAX_ATTEMPTS = 5
private const val NATIVE_RESOLVE_RETRY_DELAY_MS = 25L

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
    val calibrationDecisions = CalibrationDecisionRepository(db.calibrationDecisionDao())

    private val repositoryJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + repositoryJob)

    // ─── Programs ─────────────────────────────────────────────────────────────

    private val _programs = MutableStateFlow<List<Program>>(emptyList())
    val programs: StateFlow<List<Program>> = _programs.asStateFlow()
    /** Serializes program blobs so an older async editor write cannot win later. */
    private val programWriteMutex = Mutex()
    private val programWriteSequence = AtomicLong(0L)
    private val newestProgramWrite = ConcurrentHashMap<String, Long>()

    /**
     * Monitor ÚNICO de reserva + publicación en memoria del caché de programas.
     *
     * Toda escritura que reserve una versión y publique en [_programs] debe
     * hacer ambas cosas dentro de este monitor, sin Room ni suspend adentro
     * (sección corta: CAS sobre el StateFlow y un incremento atómico). En una
     * mutación durable también puede repararse aquí el cursor activo; esa
     * operación solo cambia su StateFlow y agenda su persistencia, sin esperar
     * otro lock. Así el orden de reservas es SIEMPRE el orden de publicaciones
     * y una versión más nueva no puede quedar cubierta por una publicación
     * vieja posterior.
     *
     * Orden de locks: `[PersistenceWriteCoordinator] -> [programWriteMutex] ->
     * programMutationLock`. El monitor es hoja: quien lo sostiene no espera a
     * ningún otro lock; la reparación opcional del cursor solo actualiza su
     * StateFlow y agenda persistencia.
     *
     * Orden GLOBAL de los mutex del repositorio (nadie pide uno anterior
     * mientras sostiene uno posterior):
     * `ongoingWorkoutMutex -> programRmwMutex -> PersistenceWriteCoordinator ->
     * programWriteMutex -> activeStateWriteMutex`, con este monitor como hoja de
     * todos. [replaceProgramSafely] también toma ongoing ANTES de Coordinator;
     * ninguna transacción puede invertir ese orden y bloquear una aceptación.
     */
    private val programMutationLock = Any()

    /**
     * Test-only seam del camino durable: se invoca DESPUÉS del upsert de Room y
     * ANTES del chequeo+publicación, para reproducir de forma determinista una
     * reserva concurrente que llega justo en ese hueco. Nulo en producción.
     *
     * El hook corre sosteniendo los locks de storage, así que solo debe usar
     * escrituras no bloqueantes (`addProgram`/`updateProgram`/`deleteProgram`,
     * que reservan y publican en memoria y delegan Room en un `launch`); nunca
     * una que espere `programWriteMutex` de forma síncrona.
     */
    @Volatile
    internal var durableCommitInterleaverForTests: (suspend () -> Unit)? = null

    /**
     * Lane de read-modify-write de [mutateProgramNow]: serializa
     * LECTURA del último snapshot → transform → commit Room → publicación.
     * Así dos mutaciones rápidas no pueden leer ambas antes de ningún commit
     * (la segunda ve el resultado ya publicado de la primera) y la caché solo
     * se publica tras confirmarse Room.
     *
     * La lectura/reserva inicial toma [programMutationLock] y lo suelta antes
     * de entrar en los lanes de storage. Después, el commit sigue el orden
     * `[PersistenceWriteCoordinator]` → [programWriteMutex] →
     * [programMutationLock] (hoja). Ningún otro camino toma `programRmwMutex`,
     * por lo que no hay ciclo de espera con los lanes de Room.
     */
    private val programRmwMutex = Mutex()

    /**
     * Test-only: se invoca dentro del commit durable, bajo los locks de
     * storage, ANTES del chequeo previo y del upsert. Permite a un test (a)
     * lanzar un fallo de storage —nada escrito, nada publicado— o (b) reservar
     * una versión más nueva para forzar el salto obsoleto. Nulo en producción.
     */
    @Volatile
    internal var durableCommitBeforeWriteForTests: (() -> Unit)? = null

    /** Test seam after progress preparation, before version reservation or any finalization write. */
    @Volatile
    internal var finalizeBeforeCommitForTests: (suspend () -> Unit)? = null

    private val _programQueue = MutableStateFlow<List<String>>(emptyList())
    val programQueue: StateFlow<List<String>> = _programQueue.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    fun addProgram(program: Program) {
        val normalized = normalizeProgramWithCompetitions(program)
        // Reserva + publicación optimista atómicas entre sí (ver [programMutationLock]).
        val version = synchronized(programMutationLock) {
            val reserved = reserveProgramWrite(normalized.id)
            _programs.update { list -> list + normalized }
            reserved
        }
        scope.launch { persistProgramIfNewest(normalized, version) }
    }

    fun updateProgram(program: Program) {
        val normalized = normalizeProgramWithCompetitions(program)
        val version = synchronized(programMutationLock) {
            val reserved = reserveProgramWrite(normalized.id)
            _programs.update { list -> list.map { if (it.id == normalized.id) normalized else it } }
            reserved
        }
        repairActiveStateIfNeeded(normalized)
        scope.launch { persistProgramIfNewest(normalized, version) }
    }

    suspend fun updateProgramNow(program: Program) {
        val normalized = normalizeProgramWithCompetitions(program)
        val version = synchronized(programMutationLock) {
            val reserved = reserveProgramWrite(normalized.id)
            _programs.update { list -> list.map { if (it.id == normalized.id) normalized else it } }
            reserved
        }
        repairActiveStateIfNeeded(normalized)
        withContext(Dispatchers.IO) { persistProgramIfNewest(normalized, version) }
    }

    /**
     * Guardado DURABLE de un programa (nuevo o reemplazo con el mismo id):
     * suspende hasta que Room confirma el write y SOLO entonces publica la
     * caché en memoria. A diferencia de [addProgram]/[updateProgram]
     * (fire-and-forget), el éxito aquí garantiza fila persistida Y caché
     * publicada con esa misma versión: quien llama puede reportar éxito sin
     * peligro de éxito falso. Errores vía [Result]; [CancellationException] se
     * propaga.
     *
     * Garantías del camino durable (y quién las hace):
     * - **Escritura protegida**: Room solo se escribe si, bajo los locks de
     *   storage (`PersistenceWriteCoordinator` → `programWriteMutex`), esta
     *   versión sigue siendo la más reciente; si no, ni Room ni la caché se
     *   tocan y llega [ProgramWriteConflictException].
     * - **Chequeo + publicación atómicos**: el re-chequeo post-Room y la
     *   publicación en [_programs] corren juntos dentro de
     *   [programMutationLock], el MISMO monitor con el que todo otro escritor
     *   hace `reserva + publicación`. Por eso una reserva más nueva no puede
     *   colarse entre chequeo y publicación (orden de reservas == orden de
     *   publicaciones) y esta versión no puede pisar una caché más nueva.
     * - **Estado devuelto = estado publicado**: si la versión se perdió DESPUÉS
     *   del upsert, no se publica y se devuelve `false` (conflicto), nunca un
     *   éxito que describa una caché que no se escribió.
     * - **Publicación fiable**: commit + chequeo + publicación corren en
     *   `NonCancellable` sobre IO; nunca se espera al dispatcher Main dentro de
     *   los locks (sin deadlocks contra hilos que se bloquean en Main).
     */
    suspend fun addProgramNow(program: Program): Result<Unit> {
        val normalized = normalizeProgramWithCompetitions(program)
        val version = synchronized(programMutationLock) { reserveProgramWrite(normalized.id) }
        return try {
            val published = commitProgramDurableForCaller(normalized, version)
            if (published) Result.success(Unit)
            else Result.failure(ProgramWriteConflictException(normalized.id))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    /**
     * Camino explícito del guardado durable, en tres pasos:
     * 1. chequeo previo bajo [programMutationLock] (Room intacto si está
     *    obsoleto);
     * 2. upsert de Room bajo los locks de storage, SIN monitor encima
     *    (Room es suspend y no puede correr dentro de `synchronized`);
     * 3. re-chequeo + publicación atómicos bajo [programMutationLock].
     *
     * Entre (2) y (3) puede entrar una reserva más nueva (p. ej. un
     * `updateProgram` concurrente): en ese caso no se publica nada y el
     * resultado es `false`, de modo que la caché nunca queda con la versión
     * vieja mientras Room termina con la nueva.
     *
     * @param repairActiveState si se debe reparar el cursor activo después de
     *   publicar una mutación del programa; se ejecuta dentro de la publicación
     *   para que no pueda aplicarse desde una versión ya obsoleta.
     * @return `true` solo si se escribió Room Y se publicó la caché con esta
     * versión todavía vigente.
     */
    private suspend fun commitProgramDurable(
        program: Program,
        version: Long,
        repairActiveState: Boolean = false,
        guardInTransaction: (suspend () -> Boolean)? = null,
    ): Boolean =
        PersistenceWriteCoordinator.mutex.withLock {
            programWriteMutex.withLock storage@{
                // Test-only (null en producción): fallo de storage o reserva
                // concurrente ANTES del chequeo previo; nada se tocó todavía.
                durableCommitBeforeWriteForTests?.invoke()
                val canWrite = synchronized(programMutationLock) {
                    newestProgramWrite[program.id] == version
                }
                if (!canWrite) {
                    false
                } else {
                    if (guardInTransaction == null) db.programDao().upsert(program.toEntity())
                    else {
                        val saved = db.withTransaction {
                            if (!guardInTransaction()) false else { db.programDao().upsert(program.toEntity()); true }
                        }
                        if (!saved) return@storage false
                    }
                    // Hueco de test: reserva concurrente ENTRE el upsert y el
                    // chequeo+publicación (sección que ahora es indivisible).
                    durableCommitInterleaverForTests?.invoke()
                    synchronized(programMutationLock) {
                        val isStillNewest = newestProgramWrite[program.id] == version
                        if (isStillNewest) {
                            _programs.update { list ->
                                if (list.any { it.id == program.id }) {
                                    list.map { if (it.id == program.id) program else it }
                                } else {
                                    list + program
                                }
                            }
                            if (repairActiveState) repairActiveStateIfNeeded(program)
                        }
                        isStillNewest
                    }
                }
            }
        }

    /**
     * Room's coroutine bridge may surface a storage/closed-database failure as
     * [CancellationException] even while the caller is still active. Convert
     * only that storage-side cancellation to a persistence error; preserve
     * cancellation of the original caller. Once inside the storage lane, the
     * commit remains non-cancellable and still publishes any confirmed write.
     */
    private suspend fun commitProgramDurableForCaller(
        program: Program,
        version: Long,
        repairActiveState: Boolean = false,
        guardInTransaction: (suspend () -> Boolean)? = null,
    ): Boolean {
        val callerJob = currentCoroutineContext()[Job]
        return withContext(NonCancellable + Dispatchers.IO) {
            try {
                commitProgramDurable(program, version, repairActiveState,guardInTransaction)
            } catch (cancelled: CancellationException) {
                if (callerJob?.isActive == false) throw cancelled
                throw IllegalStateException("No se pudo guardar el programa en Room.", cancelled)
            }
        }
    }

    /**
     * Durable read-modify-write of a program against the latest in-memory
     * snapshot. Mutations through this API are serialized from snapshot read
     * through Room commit and post-commit publication, so a later transform
     * observes the earlier committed result instead of reusing a stale copy.
     *
     * Room is written before [_programs] changes. A storage exception
     * propagates without publishing the candidate; `false` means the program
     * disappeared, [transform] aborted with `null` or changed the program ID,
     * or a newer writer superseded this version. Callers must treat every
     * `false` as non-success.
     */
    suspend fun mutateProgramNow(programId: String, transform: (Program) -> Program?): Boolean =
        mutateProgramNowReportingVersion(programId, onVersionReserved = null, transform = transform)

    /**
     * Calendar acceptance shares the execution lane, so a workout cannot start/finish halfway
     * through acceptance. Synchronous lifecycle commands are deferred only during this commit;
     * no Room work or coroutine suspension occurs while holding the publication monitor.
     */
    internal suspend fun applyHybridCalendarIfUnchanged(
        expected: com.example.kpkn.domain.multisport.HybridCalendarSourceSnapshot,
        replacement: Program,
    ): Boolean = withContext(Dispatchers.IO) {
        ongoingWorkoutMutex.withLock {
            programRmwMutex.withLock programEdit@{
                var reserved = false
                try {
                    val prepared = synchronized(programMutationLock) {
                        val programId = expected.program.id
                        val current = getProgramById(programId)
                        val active = _activeProgramState.value?.takeIf { it.programId == programId }
                        // Log identity is global: filtering first could hide an imported ID collision.
                        val history = _history.value
                        val ongoing = _ongoingWorkout.value?.programId == programId
                        if (current != expected.program || replacement.id != programId ||
                            active != expected.activeProgram || history != expected.history || ongoing != expected.hasOngoingWorkout
                        ) null else {
                            reserveHybridExecutionPublication()
                            reserved = true
                            normalizeProgramWithCompetitions(replacement) to reserveProgramWrite(programId)
                        }
                    } ?: return@programEdit false
                    commitProgramDurableForCaller(prepared.first, prepared.second, repairActiveState = true)
                } finally {
                    if (reserved) releaseHybridExecutionPublication()
                }
            }
        }
    }

    private var hybridCalendarCommitInFlight = false // guarded by programMutationLock
    private var hybridExecutionCompletion: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    private val pendingHybridLifecycleCommands = java.util.ArrayDeque<() -> Unit>()
    private var hybridLifecycleDrainScheduled = false

    /** Caller holds programMutationLock and the ongoing lane; the promise also covers external completion. */
    private fun reserveHybridExecutionPublication() {
        check(!hybridCalendarCommitInFlight)
        hybridCalendarCommitInFlight = true
        hybridExecutionCompletion = kotlinx.coroutines.CompletableDeferred()
    }

    private fun releaseHybridExecutionPublication() {
        val completion = synchronized(programMutationLock) {
            hybridCalendarCommitInFlight = false
            hybridExecutionCompletion.also { hybridExecutionCompletion = null }
        }
        completion?.complete(Unit)
    }

    private fun whenHybridCalendarCommitFree(action: () -> Unit) {
        val scheduleDrain = synchronized(programMutationLock) {
            if (hybridCalendarCommitInFlight || hybridLifecycleDrainScheduled) {
                pendingHybridLifecycleCommands.addLast(action)
                if (hybridLifecycleDrainScheduled) false else { hybridLifecycleDrainScheduled = true; true }
            } else { action(); false }
        }
        if (scheduleDrain) scope.launch {
            while (true) {
                synchronized(programMutationLock) { hybridExecutionCompletion }?.await()
                programRmwMutex.withLock { /* Wait for the reserved calendar commit to finish. */ }
                val finished = synchronized(programMutationLock) {
                    when {
                        hybridCalendarCommitInFlight -> false
                        pendingHybridLifecycleCommands.isEmpty() -> { hybridLifecycleDrainScheduled = false; true }
                        else -> {
                            try { pendingHybridLifecycleCommands.removeFirst().invoke() }
                            catch (error: Exception) { android.util.Log.e("ProgramRepository", "No se pudo aplicar un cambio de ejecución pendiente.", error) }
                            false
                        }
                    }
                }
                if (finished) break
            }
        }
    }

    /**
     * [mutateProgramNow] que entrega la versión de escritura REALMENTE reservada.
     *
     * [onVersionReserved] corre dentro del monitor, justo después de reservar y
     * antes de soltar el lane de lectura-modificación-escritura: quien necesite
     * reconciliar un commit incierto con [recoverCommittedProgramForEditor]
     * guarda ese valor en vez de predecirlo.
     */
    internal suspend fun mutateProgramNowReportingVersion(
        programId: String,
        onVersionReserved: ((Long) -> Unit)?,
        transform: (Program) -> Program?,
    ): Boolean =
        withContext(Dispatchers.IO) {
            programRmwMutex.withLock {
                val prepared = synchronized(programMutationLock) {
                    val current = getProgramById(programId) ?: return@withLock false
                    val next = transform(current) ?: return@withLock false
                    if (next.id != programId) return@withLock false
                    val normalized = normalizeProgramWithCompetitions(next)
                    val version = reserveProgramWrite(programId)
                    onVersionReserved?.invoke(version)
                    normalized to version
                }
                commitProgramDurableForCaller(
                    program = prepared.first,
                    version = prepared.second,
                    repairActiveState = true,
                )
            }
        }

    private fun normalizeProgramWithCompetitions(program: Program): Program {
        val existingRecords = runCatching { CompetitionRepository.getInstance().records.value }
            .getOrDefault(emptyList())
        val weekId = ProgramKeyDateEngine.competitionKeyDate(program)
            ?.let { ProgramKeyDateEngine.locateCompetitionWeekDay(program, it)?.first }
        val migrated = com.example.kpkn.domain.training.CompetitionKeyDateSync.migrate(
            program = program,
            existingRecords = existingRecords,
            competitionWeekId = weekId,
        )
        runCatching {
            val repo = CompetitionRepository.getInstance()
            migrated.recordsToUpsert.forEach(repo::upsert)
        }
        return ProgramPersistNormalizer.normalize(migrated.program)
            .let(ProgramPersistNormalizer::repairNativeRunWeekCursor)
            .let(::reconcileNativeCursorOfActiveProgram)
            .normalizedIdentityFields()
    }

    /**
     * Realinea el cursor nativo con los logs ANTES de reservar/persistir/publicar:
     * el programa que se escribe ya es el reconciliado, así que la caché, Room y
     * la versión reservada describen el MISMO estado (sin una segunda
     * publicación que otra escritura concurrente pueda pisar o que no se
     * persista). Solo aplica al programa con cursor activo; la reconciliación
     * es idempotente.
     */
    private fun reconcileNativeCursorOfActiveProgram(program: Program): Program {
        if (_activeProgramState.value?.programId != program.id) return program
        val logs = _history.value.filter { it.programId == program.id }
        return ProgramProgressEngine.reconcileNativeRunCursorWithLogs(program, logs)
    }

    /**
     * Toda reserva de versión pasa por [programMutationLock] (reentrante para
     * quien ya lo sostiene): así el orden de reservas es el de la secuencia y
     * [nextProgramWriteVersionForEditorRecovery], que predice `secuencia + 1`
     * dentro del monitor, nunca se desfasa por una reserva concurrente hecha
     * fuera de él (flush de ciclo de vida, finalización, reemplazo de plan).
     */
    private fun reserveProgramWrite(programId: String): Long = synchronized(programMutationLock) {
        programWriteSequence.incrementAndGet().also { version -> newestProgramWrite[programId] = version }
    }

    private suspend fun persistProgramIfNewest(program: Program, version: Long) {
        PersistenceWriteCoordinator.mutex.withLock {
            programWriteMutex.withLock {
                if (newestProgramWrite[program.id] == version) {
                    db.programDao().upsert(program.toEntity())
                }
            }
        }
    }

    private fun repairActiveStateIfNeeded(program: Program) {
        val active = _activeProgramState.value
        if (active?.programId != program.id) return
        val logs = _history.value.filter { it.programId == program.id }
        val reconciled = ProgramProgressEngine.reconcileNativeRunCursorWithLogs(program, logs)
        var baseProgram = program
        if (reconciled != program) {
            // Camino residual: los programas que llegan aquí sin pasar por
            // [normalizeProgramWithCompetitions] (recuperación del editor,
            // reconcileTemporalState). La reconciliación es una escritura más:
            // CAS contra la copia exacta que se reconcilió (una escritura
            // concurrente más nueva nunca se pisa con una copia obsoleta) y
            // versión reservada + persistencia por el lane normal.
            val reservedVersion = synchronized(programMutationLock) {
                var swapped = false
                _programs.update { list ->
                    swapped = false
                    list.map { current ->
                        if (current == program) {
                            swapped = true
                            reconciled
                        } else {
                            current
                        }
                    }
                }
                if (swapped) reserveProgramWrite(reconciled.id) else null
            }
            // Copia obsoleta: quien la reemplazó repara su propio programa.
            if (reservedVersion == null) return
            baseProgram = reconciled
            scope.launch { persistProgramIfNewest(reconciled, reservedVersion) }
        }
        val repaired = ProgramActiveStateEngine.repairForProgram(baseProgram, active)
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
        synchronized(programMutationLock) {
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
        // Reserva + retirada de la caché atómicas: un commit durable en vuelo
        // no puede re-publicar la fila entre ambas (re-animación en memoria).
        val version = synchronized(programMutationLock) {
            val reserved = reserveProgramWrite(programId)
            _programs.update { list -> list.filter { it.id != programId } }
            reserved
        }
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
            _ongoingWorkoutCorrupt.value = false
            db.programDao().deleteAll()
            persistActiveProgramStateIfLatest(null, activeVersion)
            db.stateDao().clearOngoingWorkout()
        }
    }

    fun getProgramById(id: String): Program? = _programs.value.find { it.id == id }

    /** Room-only recovery read; it must not confuse an unpublished cache with a failed commit. */
    internal suspend fun readPersistedProgramForEditorRecovery(programId: String): Program? =
        withContext(Dispatchers.IO) {
            val header = db.programDao().getAllHeaders().firstOrNull { it.id == programId }
                ?: return@withContext null
            val data = buildString {
                var start = 1
                while (true) {
                    val chunk = db.programDao().getDataChunk(
                        id = header.id,
                        start = start,
                        length = PROGRAM_DATA_CHUNK_CHARS,
                    ).orEmpty()
                    if (chunk.isEmpty()) break
                    append(chunk)
                    if (chunk.length < PROGRAM_DATA_CHUNK_CHARS) break
                    start += chunk.length
                }
            }
            if (data.isBlank()) null else ProgramEntity(header.id, header.name, data).toProgram()
        }

    /**
     * Called inside the editor's RMW transform, before that lane reserves its write.
     *
     * Exacto mientras el transform corra bajo [programMutationLock] (como en
     * [mutateProgramNow]): toda reserva toma ese monitor, así que la reserva
     * siguiente es `secuencia + 1`. Quien pueda obtener la versión real debe
     * preferir [mutateProgramNowReportingVersion].
     */
    internal fun nextProgramWriteVersionForEditorRecovery(): Long =
        synchronized(programMutationLock) { programWriteSequence.get() + 1L }

    /** Reconcile a confirmed editor commit only while its reserved revision still owns publication. */
    internal suspend fun recoverCommittedProgramForEditor(
        programId: String,
        expectedWriteVersion: Long,
        matchesCommitted: (Program) -> Boolean,
    ): Program? = withContext(NonCancellable + Dispatchers.IO) {
        programRmwMutex.withLock {
            PersistenceWriteCoordinator.mutex.withLock {
                programWriteMutex.withLock {
                    val durable = readPersistedProgramForEditorRecovery(programId)
                        ?: return@withContext null
                    if (!matchesCommitted(durable)) return@withContext null
                    val published = synchronized(programMutationLock) {
                        if (newestProgramWrite[programId] != expectedWriteVersion) false
                        else {
                            _programs.update { list ->
                                if (list.any { it.id == programId }) {
                                    list.map { if (it.id == programId) durable else it }
                                } else list + durable
                            }
                            runCatching { repairActiveStateIfNeeded(durable) }
                            true
                        }
                    }
                    durable.takeIf { published }
                }
            }
        }
    }

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

    fun startProgram(programId: String) = whenHybridCalendarCommitFree {
        val program = _programs.value.find { it.id == programId }
        val resolved = program?.let { buildDefaultActiveProgramState(it, programId) }
            ?.let { state -> ProgramActiveStateEngine.repairForProgram(program, state) }
        val state = (resolved ?: ActiveProgramState(programId = programId, status = ProgramStatus.ACTIVE))
            .copy(status = ProgramStatus.ACTIVE)
        _activeProgramState.value = state
        persistActiveProgramStateAsync(state)
        syncRunLifecycle(ProgramRunStatus.ACTIVE)
    }

    fun pauseProgram() = whenHybridCalendarCommitFree {
        val current = _activeProgramState.value ?: return@whenHybridCalendarCommitFree
        if (current.status == ProgramStatus.COMPLETED) return@whenHybridCalendarCommitFree
        val paused = current.copy(status = ProgramStatus.PAUSED)
        _activeProgramState.value = paused
        persistActiveProgramStateAsync(paused)
        syncRunLifecycle(ProgramRunStatus.PAUSED)
    }

    fun resumeProgram() = whenHybridCalendarCommitFree {
        val current = _activeProgramState.value ?: return@whenHybridCalendarCommitFree
        if (current.status == ProgramStatus.COMPLETED) return@whenHybridCalendarCommitFree
        val resumed = current.copy(status = ProgramStatus.ACTIVE)
        _activeProgramState.value = resumed
        persistActiveProgramStateAsync(resumed)
        syncRunLifecycle(ProgramRunStatus.ACTIVE)
    }

    private fun syncRunLifecycle(target: ProgramRunStatus) {
        val active = _activeProgramState.value ?: return
        val program = _programs.value.find { it.id == active.programId } ?: return
        val run = program.runState
        val nextStatus = when (run?.status) {
            ProgramRunStatus.COMPLETED -> ProgramRunStatus.COMPLETED
            ProgramRunStatus.BREAK -> ProgramRunStatus.BREAK
            else -> target
        }
        val cycleNumber = active.currentCycleNumber ?: 1
        val templateWeekId = active.currentWeekId.takeIf { it.isNotBlank() }
            ?.let { ProgramProgressEngine.templateWeekIdFromInstance(it) ?: it }
        val weekInstanceId = when {
            program.requiresNativeWeekInstances() && !templateWeekId.isNullOrBlank() ->
                coerceNativeWeekInstanceId(
                    cycleNumber,
                    templateWeekId,
                    active.currentWeekInstanceId,
                )
            else -> active.currentWeekInstanceId ?: active.currentWeekId.takeIf { it.isNotBlank() }
        }
        val baseRun = run ?: ProgramRunState(
            runId = active.programRunId ?: ProgramProgressEngine.newRunId(),
            cycleNumber = cycleNumber,
            weekId = templateWeekId,
            weekInstanceId = weekInstanceId,
            macrocycleId = active.currentMacrocycleId,
            blockId = active.currentBlockId,
            mesocycleId = active.currentMesocycleId,
            status = nextStatus,
        )
        val nextRun = baseRun.copy(
            status = nextStatus,
            cycleNumber = cycleNumber,
            weekId = templateWeekId ?: baseRun.weekId,
            weekInstanceId = weekInstanceId ?: baseRun.weekInstanceId,
        )
        if (run == nextRun) return
        updateProgram(program.copy(runState = nextRun))
    }

    fun advanceWeek(nextWeekId: String) = whenHybridCalendarCommitFree {
        _activeProgramState.update { it?.copy(currentWeekId = nextWeekId) }
        _activeProgramState.value?.let(::persistActiveProgramStateAsync)
    }

    /** Commits a domain-engine cursor transition without rebuilding it heuristically. */
    fun updateActiveProgramState(state: ActiveProgramState) = whenHybridCalendarCommitFree {
        val program = _programs.value.firstOrNull { it.id == state.programId }
        val resolved = if (
            program?.structure == ProgramStructure.COMPLEX && program.runState != null
        ) {
            ProgramActiveStateEngine.repairForProgram(program, state) ?: state
        } else state
        _activeProgramState.value = resolved
        persistActiveProgramStateAsync(resolved)
    }

    fun clearActiveProgram() = whenHybridCalendarCommitFree {
        _activeProgramState.value = null
        persistActiveProgramStateAsync(null)
    }

    // ─── Workout History ──────────────────────────────────────────────────────

    private val _history = MutableStateFlow<List<WorkoutLog>>(emptyList())
    val history: StateFlow<List<WorkoutLog>> = _history.asStateFlow()

    fun addWorkoutLog(log: WorkoutLog) = whenHybridCalendarCommitFree {
        val normalized = checkNotNull(log.normalizedIdentityFields().toEntity().toWorkoutLog()) { "No se pudo preservar el registro" }
        _history.update { listOf(normalized) + it.filterNot { existing -> existing.id == normalized.id } }
        scope.launch {
            ongoingWorkoutMutex.withLock {
                // A delayed legacy import must not overwrite a later durable annotation.
                if (_history.value.firstOrNull { it.id == normalized.id } == normalized) {
                    db.workoutLogDao().insert(normalized.toEntity())
                }
            }
        }
    }

    /**
     * Adds validated specialised observations to one existing activity. No progress, energy,
     * duration, identity or completion is recomputed. Room precedes cache publication.
     * false means stale/missing/not-yet-durable data; reread before retrying.
     */
    internal suspend fun replaceWorkoutSportDocumentsNow(
        expected: WorkoutLog,
        documents: List<com.example.kpkn.domain.multisport.tracking.SportTrackingDocument>,
    ): Boolean = withContext(Dispatchers.IO) {
        ongoingWorkoutMutex.withLock {
            if (expected.id.isBlank() || _history.value.firstOrNull { it.id == expected.id } != expected) return@withLock false
            val replacement = expected.copy(sportDocuments = documents)
            val committed = withContext(NonCancellable) {
                db.withTransaction {
                    if (db.workoutLogDao().getById(expected.id)?.toWorkoutLog() != expected ||
                        _history.value.firstOrNull { it.id == expected.id } != expected
                    ) false else {
                        db.workoutLogDao().insert(replacement.toEntity())
                        true
                    }
                }
            }
            if (!committed) return@withLock false
            while (true) {
                val current = _history.value
                if (current.firstOrNull { it.id == expected.id } != expected) return@withLock false
                if (_history.compareAndSet(current, current.map { if (it.id == expected.id) replacement else it })) break
            }
            true
        }
    }

    /**
     * Atomically persists the finished log, clears ongoing, and commits simple-program
     * progress + active state in one Room transaction.
     *
     * Idempotent: if [log.id] already exists, identity fields are frozen from the first
     * write and progress is not reapplied.
     */
    suspend fun finalizeWorkout(
        log: WorkoutLog,
        clearOngoing: Boolean = true,
        mediaSessionKey: String? = null,
        retainedOngoingTransform: ((OngoingWorkoutState) -> OngoingWorkoutState)? = null,
        expectedExecutionStartTimeMs: Long? = null,
        expectedProgramForExternalCompletion: Program? = null,
        requireNoOngoingWorkout: Boolean = false,
    ) {
        withContext(Dispatchers.IO + NonCancellable) {
            ongoingWorkoutMutex.withLock {
                val externalCommit = expectedProgramForExternalCompletion != null
                if (externalCommit) synchronized(programMutationLock) { reserveHybridExecutionPublication() }
                try {
                val prior = _history.value.firstOrNull { it.id == log.id }
                    ?: db.workoutLogDao().getById(log.id)?.toWorkoutLog()?.normalizedIdentityFields()
                val currentOngoing = _ongoingWorkout.value
                if (requireNoOngoingWorkout) check(currentOngoing == null) { "Hay una sesión en curso. Finalízala antes de registrar otra actividad." }
                if (prior == null && expectedProgramForExternalCompletion != null) {
                    check(getProgramById(log.programId) == expectedProgramForExternalCompletion) {
                        "El programa cambió antes de registrar la actividad. Revisa la semana actual."
                    }
                }
                if (expectedExecutionStartTimeMs != null &&
                    (currentOngoing == null || currentOngoing.programId != log.programId ||
                        currentOngoing.session.id != log.sessionId || currentOngoing.startTime != expectedExecutionStartTimeMs)) {
                    // An old screen must never finalize or clear another execution.
                    check(prior != null && currentOngoing == null) { "La ejecución activa cambió; vuelve a abrir la sesión." }
                }
                val retainedOngoing = if (!clearOngoing && retainedOngoingTransform != null) {
                    val current = checkNotNull(currentOngoing) { "No hay una sesión activa que conservar." }
                    retainedOngoingTransform(current).also {
                        check(it.programId == current.programId && it.session.id == current.session.id && it.startTime == current.startTime)
                    }
                } else null

                if (prior != null) {
                    // Retry path: never reinterpret identity against the current cursor.
                    db.withTransaction {
                        db.workoutLogDao().insert(prior.toEntity())
                        mediaSessionKey?.takeIf { it.isNotBlank() }?.let { db.bindWorkoutMediaSession(it, prior) }
                        if (clearOngoing) db.stateDao().clearOngoingWorkout()
                        else if (retainedOngoing != null) db.stateDao().upsertOngoingWorkout(retainedOngoing.toEntity())
                    }
                    _history.update { current -> listOf(prior) + current.filterNot { it.id == prior.id } }
                    if (clearOngoing) {
                        _ongoingWorkout.value = null
                        _ongoingWorkoutCorrupt.value = false
                    } else if (retainedOngoing != null) _ongoingWorkout.value = retainedOngoing
                    return@withLock
                }

                val program = getProgramById(log.programId)
                val active = _activeProgramState.value
                val isCalendarized = program?.isSimpleCalendarizedProgram == true
                val isComplex = program?.structure == ProgramStructure.COMPLEX
                val breakId = program?.activeCalendarBreakId

                val enrichedCandidate = if (isCalendarized) {
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
                    // Los planes nativos COMPLEX llevan instancias de ciclo
                    // (`inst_c<N>_<semana>`): su ciclo real (log -> run -> cursor)
                    // se respeta. Solo el COMPLEX sin instancias sigue forzando el
                    // ciclo 1 (nunca avanza de ciclo).
                    val nativeInstances = program?.requiresNativeWeekInstances() == true
                    val cycleNumber = if (isComplex && !nativeInstances) 1 else log.cycleNumber
                        ?: program?.runState?.cycleNumber
                        ?: active?.currentCycleNumber
                        ?: 1
                    val templateWeekId = log.weekId?.let {
                        ProgramProgressEngine.templateWeekIdFromInstance(it) ?: it
                    } ?: active?.currentWeekId?.let {
                        ProgramProgressEngine.templateWeekIdFromInstance(it) ?: it
                    }
                    val resolvedInstanceId = if (isComplex && !nativeInstances) {
                        log.weekInstanceId
                            ?: active?.currentWeekInstanceId
                            ?: program?.runState?.weekInstanceId
                            ?: templateWeekId
                            ?: log.weekId
                    } else if (nativeInstances) {
                        coerceNativeWeekInstanceId(
                            cycleNumber,
                            templateWeekId ?: log.weekId.orEmpty(),
                            log.weekInstanceId
                                ?: active?.currentWeekInstanceId
                                ?: program?.runState?.weekInstanceId,
                        )
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

                val enriched = checkNotNull(enrichedCandidate.toEntity().toWorkoutLog()) { "No se pudo preservar el registro" }
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
                        weeklySignals = buildWeeklyAutoregulationSignals(program, historyForProgress),
                        compositionMetadata = if (program.sourceRecipe != null) CompositionMetadataHolder.resolve() else CompositionMetadataHolder.current,
                        nowMs = System.currentTimeMillis(),
                        reviewCatalog = exerciseCatalogSnapshot(),
                        // B.S3: la progresión de autor redondea el TM que sube con los discos reales
                        // del atleta (sin inventario, medio kilo).
                        inventory = _settings.value.resolvedEquipmentInventory(),
                    )
                } else {
                    null
                }
                val progressedProgram = progress?.program
                    ?.takeIf { it != program }
                    ?.let { ProgramCalendarEngine.materializeWeekDates(it).normalizedIdentityFields() }
                val progressionBase = progressedProgram ?: program
                val nativeProgressedProgram = if (
                    progressionBase != null &&
                    enriched.calendarBreakId.isNullOrBlank() &&
                    // El catálogo solo se materializa cuando la receta opta por la
                    // progresión nativa; los programas de autor/legacy (el runtime
                    // retorna de inmediato sin `nativeProgression`) no pagan la
                    // instantánea completa del catálogo en cada finalización.
                    progressionBase.sourceRecipe?.nativeProgression != null
                ) {
                    NativeWorkoutProgressionRuntime.observeCompletedWorkout(
                        program = progressionBase,
                        logs = historyForProgress,
                        inventory = _settings.value.resolvedEquipmentInventory(),
                        curatedConfigurations = exerciseCatalogSnapshot().mapNotNullTo(mutableSetOf()) {
                            it.catalogConfigurationId
                        },
                        completedLogId = enriched.id,
                    )
                } else {
                    progressionBase
                }
                // El runtime nativo puede añadir ejercicios/referencias: el programa que
                // se persiste y publica pasa por el mismo normalizador que el resto de
                // escrituras (las identidades nunca llegan a Room sin normalizar).
                val nextProgram = nativeProgressedProgram?.takeIf { it != program }
                    ?.normalizedIdentityFields()
                val progressActive = progress?.activeState
                val repairedActive = when {
                    progressActive != null && progressActive != active -> progressActive
                    nextProgram != null -> ProgramActiveStateEngine.repairForProgram(nextProgram, active)
                        ?.takeIf { it != active }
                    else -> null
                }

                finalizeBeforeCommitForTests?.invoke()
                val reservedVersions = synchronized(programMutationLock) {
                    if (expectedProgramForExternalCompletion != null &&
                        getProgramById(log.programId) != expectedProgramForExternalCompletion
                    ) throw ExternalSportCompletionConflictException()
                    (nextProgram?.let { reserveProgramWrite(it.id) }) to
                        (repairedActive?.let { reserveActiveStateWrite() })
                }
                val nextProgramVersion = reservedVersions.first
                // Reserve the active-state version before entering the Room
                // transaction. A concurrent updateActiveProgramState can then
                // supersede this cursor instead of being overwritten by a
                // late finalization write.
                val repairedActiveVersion = reservedVersions.second
                suspend fun persistFinalization() {
                    // Keep the lock order identical to the async lane:
                    // active mutex first, then Room.  The transaction uses the
                    // locked-only helper and therefore never acquires a mutex
                    // while Room already owns its transaction connection.
                    activeStateWriteMutex.withLock {
                        db.withTransaction {
                            db.workoutLogDao().insert(enriched.toEntity())
                            calibrationDecisions.saveSnapshot(enriched.calibrationDecisions)
                            mediaSessionKey?.takeIf { it.isNotBlank() }?.let { db.bindWorkoutMediaSession(it, enriched) }
                            if (clearOngoing) db.stateDao().clearOngoingWorkout()
                            else if (retainedOngoing != null) db.stateDao().upsertOngoingWorkout(retainedOngoing.toEntity())
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

                _history.update { current -> listOf(enriched) + current.filterNot { it.id == enriched.id } }
                if (clearOngoing) {
                    _ongoingWorkout.value = null
                    _ongoingWorkoutCorrupt.value = false
                } else if (retainedOngoing != null) _ongoingWorkout.value = retainedOngoing
                if (nextProgram != null && nextProgramVersion != null) {
                    // Chequeo + publicación atómicos: una reserva más nueva no
                    // puede colarse entre ambos (ver [programMutationLock]).
                    synchronized(programMutationLock) {
                        if (newestProgramWrite[nextProgram.id] == nextProgramVersion) {
                            _programs.update { list -> list.map { if (it.id == nextProgram.id) nextProgram else it } }
                        }
                    }
                }
                if (repairedActive != null && repairedActiveVersion != null && newestActiveStateWrite == repairedActiveVersion) {
                    _activeProgramState.value = repairedActive
                }
                } finally {
                    if (externalCommit) releaseHybridExecutionPublication()
                }
            }
        }
    }

    /**
     * Builds the transition evidence from persisted workout history plus a live
     * AUGE readiness snapshot (wellbeing, batteries, feedback). Readiness stays
     * null only when AUGE cannot be computed.
     */
    private suspend fun buildTransitionContext(
        program: Program,
        history: List<WorkoutLog>,
    ): BlockTransitionEngine.TransitionContext {
        return BlockTransitionEngine.TransitionContext(settings = _settings.value)
    }

    private suspend fun buildWeeklyAutoregulationSignals(
        program: Program,
        history: List<WorkoutLog>,
    ): WeeklyAutoregulationSignals {
        val week = program.runState?.weekId?.let { id ->
            program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }
                .firstOrNull { it.id == id }
        }
        // B.S4: los AMRAP son los del ciclo y el run en curso (antes se mezclaban los de ciclos anteriores) y
        // el levantamiento de cada uno sale del slot de la receta del programa.
        val amrap = week?.let {
            com.example.kpkn.domain.training.ProgramAutoregulationEngine.collectAmrapHits(
                week = it,
                logs = history,
                cycleNumber = program.runState?.cycleNumber,
                programId = program.id,
                runId = program.runState?.runId,
                recipe = program.sourceRecipe,
            )
        }.orEmpty()
        val e1rm = week?.let {
            com.example.kpkn.domain.training.ProgramAutoregulationEngine.collectE1rmByLift(history, it)
        }.orEmpty()
        return WeeklyAutoregulationSignals(
            amrapHits = amrap,
            e1rmByLift = e1rm,
            settings = _settings.value,
        )
    }

    fun getLogsForProgram(programId: String): List<WorkoutLog> =
        _history.value.filter { it.programId == programId }

    fun getLogsForSession(sessionId: String): List<WorkoutLog> =
        _history.value.filter { it.sessionId == sessionId }

    /**
     * Evidencia real de entrenamiento (§14.5/AC-G1): sesiones realmente
     * iniciadas o registradas y semanas cuyo trabajo REQUIRED está completo.
     * Se deriva de los logs del run (weekInstanceId/cycle/run aware), **nunca**
     * del cursor `runState.weekId`, que puede apuntar a la próxima semana vacía
     * (colisión R-202). Solo se protege una semana como «ejecutada» cuando su
     * trabajo requerido está logueado; el resto se reconstruye preservando las
     * sesiones entrenadas una a una.
     *
     * Los planes nativos con instancias de ciclo ([requiresNativeWeekInstances]) y los
     * programas Simples cíclicos (un 5/3/1 de autor, por ejemplo) reutilizan las
     * MISMAS sesiones (mismos ids) en cada ciclo: la evidencia solo cuenta los logs
     * del ciclo en curso (`runState.cycleNumber`). Un log sin ciclo se sigue contando
     * (nunca se arriesga perder una sesión entrenada) y la sesión en curso siempre
     * protege su id. El resto de programas conserva la evidencia de todo el run.
     */
    data class ExecutedTrainingEvidence(
        val sessionIds: Set<String>,
        val weekIds: Set<String>,
    )

    fun executedTrainingEvidence(program: Program): ExecutedTrainingEvidence {
        val runId = program.runState?.runId
        // Sin esto, tras cerrar el ciclo 1 de un programa cíclico sus semanas figuraban como
        // entrenadas y la reconstrucción (RE-MATERIALIZAR, propuestas) no tocaba nada.
        val cyclic = program.requiresNativeWeekInstances() ||
            (program.isSimpleProgram && program.simpleProgramKind == SimpleProgramKind.CYCLIC)
        val currentCycle = program.runState?.cycleNumber?.takeIf { cyclic }
        val logs = getLogsForProgram(program.id)
            .filter { it.calendarBreakId.isNullOrBlank() }
            .filter { log -> runId == null || log.programRunId == null || log.programRunId == runId }
            .filter { log -> currentCycle == null || log.cycleNumber == null || log.cycleNumber == currentCycle }
        val sessionIds = logs.map { it.sessionId }.toMutableSet()
        _ongoingWorkout.value?.let { ongoing ->
            if (ongoing.programId == program.id) sessionIds += ongoing.session.id
        }
        val loggedWeekIds = mutableSetOf<String>()
        logs.forEach { log ->
            listOfNotNull(log.weekId, log.weekInstanceId).forEach { id ->
                loggedWeekIds += id
                loggedWeekIds += ProgramProgressEngine.templateWeekIdFromInstance(id) ?: id
            }
        }
        val weekIds = mutableSetOf<String>()
        program.macrocycles.forEach { macro ->
            macro.blocks.forEach { block ->
                block.mesocycles.forEach { meso ->
                    meso.weeks.forEach { week ->
                        val required = week.sessions.filter {
                            it.requirement == SessionRequirement.REQUIRED
                        }
                        val recognized = week.sessions.count { it.id in sessionIds }
                        val fullyTrained = when {
                            required.isNotEmpty() -> required.all { it.id in sessionIds }
                            // Sesiones con logs que no reconocemos: proteger la
                            // semana por referencia antes que arriesgar su pérdida.
                            else -> week.id in loggedWeekIds && recognized == 0
                        }
                        if (fullyTrained) weekIds += week.id
                    }
                }
            }
        }
        return ExecutedTrainingEvidence(sessionIds = sessionIds, weekIds = weekIds)
    }

    /** Sólo invalidación de lecturas: ningún objetivo nutricional se modifica desde programas. */
    val seasonContextChanges: kotlinx.coroutines.flow.Flow<Unit> = callbackFlow {
        val observer = object : androidx.room.InvalidationTracker.Observer("nutrition_plans","nutrition_active_state","body_observations","competition_records") {
            override fun onInvalidated(tables: Set<String>) { trySend(Unit) }
        }
        db.invalidationTracker.addObserver(observer)
        trySend(Unit)
        awaitClose { db.invalidationTracker.removeObserver(observer) }
    }

    suspend fun seasonInput(programId: String,today: java.time.LocalDate = java.time.LocalDate.now(),
        zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): com.example.kpkn.domain.season.SeasonInput = withContext(Dispatchers.IO) {
        val program = getProgramById(programId) ?: return@withContext com.example.kpkn.domain.season.SeasonInput()
        db.withTransaction {
            val activeId = db.nutritionDao().getActiveState()?.activePlanId
            val nutrition = db.nutritionDao().getAllPlans().firstOrNull { it.id == activeId }?.toNutritionPlan()
            val body = db.bodyProgressDao().getAllObservations().mapNotNull { it.toBodyObservation() }
            val records = db.competitionRecordDao().getAll().map { it.toCompetitionRecord() }
            val hierarchy = com.example.kpkn.domain.training.ProgramHierarchyIndex(program)
            val current = program.runState?.weekId?.let { hierarchy.locateWeek(ProgramProgressEngine.templateWeekIdFromInstance(it) ?: it) }
            val blocks = program.macrocycles.flatMap { it.blocks }
            val blockIndex = blocks.indexOfFirst { it.id == current?.blockId }
            val key = program.keyDates.filter { it.type == KeyDateType.COMPETITION }.mapNotNull { date ->
                runCatching { java.time.LocalDate.parse(date.eventDate ?: date.startDate) }.getOrNull()?.let { date to it }
            }.sortedWith(compareBy<Pair<ProgramKeyDate,java.time.LocalDate>> { if (it.second < today) 1 else 0 }
                .thenBy { kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(today,it.second)) }).firstOrNull()
            val record = key?.let { (date,eventDate) -> records.firstOrNull { record ->
                record.status != CompetitionRecordStatus.ARCHIVED && isCompetitionLinked(program,record) && (record.keyDateId == date.id || record.eventDate == eventDate.toString())
            } }
            val maintenance = nutrition?.calculationSnapshot?.result?.get("eerKcal")?.toDoubleOrNull()
                ?.takeIf { it.isFinite() && it > 0 && it < Int.MAX_VALUE }?.let { kotlin.math.round(it).toInt() }
            com.example.kpkn.domain.season.SeasonInput(
                blockGoal = current?.block?.goal ?: current?.mesocycle?.goal?.name?.let { name -> BlockGoal.entries.firstOrNull { it.name == name } },
                nextBlockGoal = if (blockIndex >= 0) blocks.getOrNull(blockIndex + 1)?.goal else null,
                currentWeekIsDeload = current?.week?.executionKind == WeekExecutionKind.DELOAD,
                direction = nutrition?.direction,calorieTarget = nutrition?.calorieTarget?.takeIf { it > 0 },maintenanceKcal = maintenance,
                weeklyWeightRateKg = com.example.kpkn.domain.body.measuredWeeklyRate(body,BodyMetric.WEIGHT,zone,today),
                daysToCompetition = key?.let { java.time.temporal.ChronoUnit.DAYS.between(today,it.second).coerceIn(Int.MIN_VALUE.toLong(),Int.MAX_VALUE.toLong()).toInt() },
                targetBodyweightKg = record?.targetBodyweightKg?.takeIf { it.isFinite() && it > 0 },
                currentBodyweightKg = _settings.value.userVitals.weight?.takeIf { it.isFinite() && it > 0 })
        }
    }

    data class MeetResultPreview(
        val original: Program,val result: Program,
        val record: com.example.kpkn.data.models.CompetitionRecord,
        val summary: String,val settings: Settings,val evidence: ExecutedTrainingEvidence,
    )

    fun isCompetitionLinked(program: Program,record: com.example.kpkn.data.models.CompetitionRecord): Boolean {
        if (record.plannedProgramId != null && record.plannedProgramId != program.id) return false
        return program.keyDates.any { key -> key.type == com.example.kpkn.data.models.KeyDateType.COMPETITION &&
            (record.keyDateId == key.id || record.plannedProgramId == program.id && record.eventDate == (key.eventDate ?: key.startDate)) }
    }

    fun supportsSbdCompetition(program: Program,record: CompetitionRecord): Boolean =
        record.sportType == CompetitionTemplateType.POWERLIFTING ||
            record.sportType == CompetitionTemplateType.CUSTOM && program.mode == ProgramMode.POWERLIFTING

    fun previewMeetResult(programId: String,recordId: String): MeetResultPreview? {
        val current = getProgramById(programId) ?: return null
        val competitions = runCatching { CompetitionRepository.getInstance() }.getOrNull() ?: return null
        val record = competitions.getById(recordId) ?: return null
        if (!competitions.usesDatabase(db) || !isCompetitionLinked(current,record) || !supportsSbdCompetition(current,record)) return null
        val proposal = com.example.kpkn.domain.competitions.MeetResultToProfile.propose(record,current.powerliftingProfile,
            com.example.kpkn.domain.training.TrainingMaxMerge.trainingMaxPercentOf(current)) ?: return null
        val now = System.currentTimeMillis()
        val evidence = executedTrainingEvidence(current)
        val decisionId = "meet-result:${record.id}"
        var result = com.example.kpkn.domain.training.ExerciseTrainingMax.syncProfile(current,
            current.copy(powerliftingProfile = proposal.profile),now,decisionId,"Marcas válidas de competición confirmadas","COMPETITION")
        val recipe = current.sourceRecipe
        if (recipe != null) {
            val blocks = current.macrocycles.flatMap { it.blocks }.filter { it.sourceDefinitionId == recipe.id }
            val blockIds = blocks.map { it.id }.toSet()
            blocks.flatMap { it.mesocycles }.flatMap { it.weeks }.filterNot { it.isLoopWeek }.forEach { week ->
                result = PlanMaterializer.rematerializeWeek(result,week.id,recipe,CompositionMetadataHolder.resolve(),
                    executedWeekIds = evidence.weekIds,executedSessionIds = evidence.sessionIds)
            }
            result = result.copy(macrocycles = result.macrocycles.map { macro -> macro.copy(blocks = macro.blocks.map { block ->
                if (block.id in blockIds) block.copy(materializationPending = false) else block
            }) })
        }
        result = result.copy(programDecisions = result.programDecisions + com.example.kpkn.data.models.ProgramDecision(
            decisionId,now,"COMPETITION","Marcas de competición confirmadas",proposal.summary))
        return MeetResultPreview(current,result,record,proposal.summary,_settings.value,evidence)
    }

    internal var meetAfterProgramWriteForTests: (() -> Unit)? = null

    /** Perfil, decisión y marca de aplicación se confirman juntos; ninguna fila implica un entreno nuevo. */
    suspend fun applyMeetResult(programId: String,preview: MeetResultPreview): Boolean = withContext(Dispatchers.IO) {
        val competitions = CompetitionRepository.getInstance()
        if (!competitions.usesDatabase(db)) return@withContext false
        ongoingWorkoutMutex.withLock {
            programRmwMutex.withLock rmw@{
                val reservations = synchronized(programMutationLock) {
                    if (getProgramById(programId) != preview.original || preview.original.id != programId || preview.result.id != programId ||
                        _settings.value != preview.settings || executedTrainingEvidence(preview.original) != preview.evidence ||
                        !isCompetitionLinked(preview.original,preview.record)) null
                    else {
                        val recordWrite = competitions.reserveIfUnchanged(preview.record,preview.record.copy(meetResultAppliedAtMs = System.currentTimeMillis()))
                        recordWrite?.let { reserveProgramWrite(programId) to it }
                    }
                } ?: return@rmw false
                withContext(NonCancellable + Dispatchers.IO) {
                    PersistenceWriteCoordinator.mutex.withLock {
                        programWriteMutex.withLock storage@{
                            competitions.recordWriteMutex.withLock recordStorage@{
                                durableCommitBeforeWriteForTests?.invoke()
                                val current = synchronized(programMutationLock) {
                                    newestProgramWrite[programId] == reservations.first && competitions.isCurrent(reservations.second)
                                }
                                if (!current) return@recordStorage false
                                db.withTransaction {
                                    db.programDao().upsert(preview.result.toEntity())
                                    meetAfterProgramWriteForTests?.invoke()
                                    db.competitionRecordDao().upsert(reservations.second.record.toEntity())
                                }
                                durableCommitInterleaverForTests?.invoke()
                                synchronized(programMutationLock) {
                                    if (newestProgramWrite[programId] != reservations.first || !competitions.publishIfCurrent(reservations.second)) false
                                    else {
                                        _programs.update { plans -> plans.map { if (it.id == programId) preview.result else it } }
                                        repairActiveStateIfNeeded(preview.result)
                                        true
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    data class ScheduleContinuationPreview(
        val original: Program,
        val result: Program,
        val settings: Settings,
        val logs: List<WorkoutLog>,
        val activeState: ActiveProgramState?,
        val evidence: ExecutedTrainingEvidence,
    )

    /** Prepara una elección explícita sin crear registros de entrenamiento ni mover aún el cursor. */
    suspend fun previewScheduleContinuation(programId: String,
        choice: com.example.kpkn.domain.training.schedule.MissedSessionChoice?,
        expected: com.example.kpkn.domain.training.schedule.MissedSessionsRequest? = null,
    ): ScheduleContinuationPreview? = withContext(Dispatchers.IO) {
        val current = getProgramById(programId) ?: return@withContext null
        if (_ongoingWorkout.value != null || choice != null && current.pendingMissedSessions != expected) return@withContext null
        val settings = _settings.value
        val logs = getLogsForProgram(programId)
        val active = _activeProgramState.value
        val evidence = executedTrainingEvidence(current)
        val metadata = if (current.sourceRecipe != null) CompositionMetadataHolder.resolve() else CompositionMetadataHolder.current
        val now = System.currentTimeMillis()
        var result = if (choice == null) ProgramProgressEngine.applySoftReturn(current,metadata ?: return@withContext null,evidence.sessionIds,now)
            else ProgramProgressEngine.resolveMissedSessions(current,choice,metadata,logs,evidence.sessionIds,now)
        if (result == current) return@withContext null
        if (choice == com.example.kpkn.domain.training.schedule.MissedSessionChoice.SKIP) {
            val run = result.runState
            val week = run?.weekId?.let { id -> com.example.kpkn.domain.training.ProgramHierarchyIndex(result)
                .locateWeek(ProgramProgressEngine.templateWeekIdFromInstance(id) ?: id)?.week }
            if (run != null && week != null) {
                val instance = ProgramProgressEngine.instanceIdFor(run.cycleNumber,week.id)
                val actual = ProgramProgressEngine.logsForInstance(logs,programId,instance,run.cycleNumber,run.runId)
                    .firstOrNull { log -> week.sessions.any { it.id == log.sessionId } }
                val session = actual?.let { log -> week.sessions.firstOrNull { it.id == log.sessionId } }
                if (session != null) result = ProgramProgressEngine.advanceAfterSessionComplete(result,
                    active?.takeIf { it.programId == programId },session,instance,logs,
                    transitionContext = buildTransitionContext(result,logs),
                    weeklySignals = buildWeeklyAutoregulationSignals(result,logs),compositionMetadata = metadata,
                    inventory = settings.resolvedEquipmentInventory(),nowMs = now,reviewCatalog = exerciseCatalogSnapshot()).program
            }
        }
        ScheduleContinuationPreview(current,result,settings,logs,active,evidence)
    }

    suspend fun submitScheduleContinuation(programId: String,preview: ScheduleContinuationPreview): Boolean = withContext(Dispatchers.IO) {
        ongoingWorkoutMutex.withLock {
            mutateProgramNow(programId) { current ->
                if (current != preview.original || current.id != programId || _settings.value != preview.settings ||
                    _activeProgramState.value != preview.activeState || _ongoingWorkout.value != null ||
                    getLogsForProgram(programId) != preview.logs || executedTrainingEvidence(current) != preview.evidence) null
                else preview.result
            }
        }
    }

    data class WeeklyCheckinPreview(
        val original: Program,
        val result: Program,
        val answer: com.example.kpkn.domain.training.cycle.WeeklyCheckin,
        val evidence: ExecutedTrainingEvidence,
        val season: com.example.kpkn.domain.season.SeasonInput,
        val settings: Settings,
    )

    data class BlockReviewPreview(
        val original: Program,
        val result: Program,
        val evidence: ExecutedTrainingEvidence,
    )

    data class ExerciseSwapRequest(
        val program: Program,
        val settings: Settings,
        val sessionId: String,
        val exerciseId: String,
        val sourceName: String,
        val options: List<com.example.kpkn.domain.training.SwapOption>,
        val requiresEquipmentConfirmation: Boolean,
        val catalog: com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2,
    )
    data class ExerciseSwapPreview(val original: Program,val result: Program,val settings: Settings,val evidence: ExecutedTrainingEvidence)

    fun exerciseSwapRequest(programId: String,sessionId: String,exerciseId: String,
                            catalog: com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2): ExerciseSwapRequest? {
        val program = getProgramById(programId) ?: return null
        fun nested(s: Session): List<Session> = listOf(s) + listOfNotNull(s.sessionB,s.sessionC,s.sessionD).flatMap(::nested)
        val sessions = com.example.kpkn.domain.training.ProgramHierarchyIndex(program).orderedWeeks().flatMap { it.week.sessions }.flatMap(::nested)
        val session = sessions.filter { it.id == sessionId }.singleOrNull() ?: return null
        val exercise = session.allExercises().filter { it.id == exerciseId }.singleOrNull() ?: return null
        val settings = _settings.value
        // La declaración global no acredita el equipo de otro lugar nombrado sin una vinculación guardada.
        val availability = settings.equipmentAvailability?.takeIf { session.placeId.isNullOrBlank() }
        val avoided = program.avoidedExerciseGroups.mapNotNull { name ->
            com.example.kpkn.domain.onboarding.AvoidGroup.entries.firstOrNull { it.name == name }
        }.toSet()
        val options = if (availability == null) com.example.kpkn.domain.training.ExerciseSwapOptions.optionsForUnknownEquipment(exercise,catalog,avoided)
            else com.example.kpkn.domain.training.ExerciseSwapOptions.optionsFor(exercise,availability,catalog,avoided)
        return ExerciseSwapRequest(program,settings,sessionId,exerciseId,exercise.name,options,availability == null,catalog)
    }

    fun previewExerciseSwap(request: ExerciseSwapRequest,targetConfigurationId: String,scope: com.example.kpkn.domain.training.SwapScope,
                            equipmentConfirmed: Boolean): ExerciseSwapPreview? {
        val current = getProgramById(request.program.id) ?: return null
        if (current != request.program || _settings.value != request.settings || request.requiresEquipmentConfirmation && !equipmentConfirmed) return null
        val refreshed = exerciseSwapRequest(current.id,request.sessionId,request.exerciseId,request.catalog) ?: return null
        val target = refreshed.options.firstOrNull { it.configId == targetConfigurationId } ?: return null
        val evidence = executedTrainingEvidence(current)
        var result = com.example.kpkn.domain.training.applyExerciseSwap(current,request.sessionId,request.exerciseId,target,scope,
            request.catalog,evidence.sessionIds,System.currentTimeMillis())
        if (result == current) return null
        if (request.requiresEquipmentConfirmation) result = result.copy(programDecisions = result.programDecisions.dropLast(1) +
            result.programDecisions.last().let { it.copy(detail = it.detail + " Material confirmado para esta sustitución.") })
        return ExerciseSwapPreview(current,result,request.settings,evidence)
    }

    suspend fun submitExerciseSwap(programId: String,preview: ExerciseSwapPreview): Boolean = withContext(Dispatchers.IO) {
        ongoingWorkoutMutex.withLock {
            mutateProgramNow(programId) { current ->
                if (current.id != programId || current != preview.original || _settings.value != preview.settings ||
                    executedTrainingEvidence(current) != preview.evidence) null else preview.result
            }
        }
    }

    /** Test-only failure inside the transaction, after the copy row and before resolving its source. */
    internal var continuationAfterCopyWriteForTests: (() -> Unit)? = null

    data class ContinuationPreview(
        val original: Program,
        val result: Program,
        val choice: com.example.kpkn.domain.training.block.ContinuationChoice,
        val nextProgram: Program?,
    )

    fun previewContinuation(programId: String, expected: com.example.kpkn.domain.training.block.ContinuationProposal,
                            choice: com.example.kpkn.domain.training.block.ContinuationChoice): ContinuationPreview? {
        val current = getProgramById(programId) ?: return null
        if (current.pendingContinuation != expected) return null
        val result = ProgramProgressEngine.resolveContinuation(current,choice,System.currentTimeMillis(),expected)
        if (result == current) return null
        val factory = com.example.kpkn.domain.training.block.ContinuationProgramFactory
        val next = if (choice == com.example.kpkn.domain.training.block.ContinuationChoice.PICK_FROM_CATALOG) null else {
            val id = factory.candidateId(current,choice) ?: return null
            val existing = getProgramById(id)
            if (existing != null) {
                if (existing.programDecisions.none { it.id == factory.sourceDecisionId(current) }) return null
                existing
            } else normalizeProgramWithCompetitions(factory.build(current,choice,CompositionMetadataHolder.resolve(),
                java.time.LocalDate.now(),System.currentTimeMillis()) ?: return null)
        }
        return ContinuationPreview(current,normalizeProgramWithCompetitions(result),choice,next)
    }

    /** La elección y su copia se escriben juntas; el programa terminado conserva su historial. */
    suspend fun submitContinuation(programId: String, preview: ContinuationPreview): Boolean = withContext(Dispatchers.IO) {
        ongoingWorkoutMutex.withLock {
            programRmwMutex.withLock rmw@{
                val next = preview.nextProgram
                val reserved = synchronized(programMutationLock) {
                    if (getProgramById(programId) != preview.original || preview.original.id != programId ||
                        preview.result.id != programId || _ongoingWorkout.value != null ||
                        (next != null && getProgramById(next.id)?.let { it != next } == true)) null
                    else reserveProgramWrite(programId) to next?.let { reserveProgramWrite(it.id) }
                } ?: return@rmw false
                if (next == null) return@rmw commitProgramDurableForCaller(preview.result,reserved.first,repairActiveState = true)
                val nextVersion = reserved.second ?: return@rmw false
                val callerJob = currentCoroutineContext()[Job]
                withContext(NonCancellable + Dispatchers.IO) {
                    try {
                        PersistenceWriteCoordinator.mutex.withLock {
                            programWriteMutex.withLock storage@{
                                durableCommitBeforeWriteForTests?.invoke()
                                val valid = synchronized(programMutationLock) {
                                    newestProgramWrite[programId] == reserved.first && newestProgramWrite[next.id] == nextVersion
                                }
                                if (!valid) return@storage false
                                db.withTransaction {
                                    db.programDao().upsert(next.toEntity())
                                    continuationAfterCopyWriteForTests?.invoke()
                                    db.programDao().upsert(preview.result.toEntity())
                                }
                                durableCommitInterleaverForTests?.invoke()
                                synchronized(programMutationLock) {
                                    val newest = newestProgramWrite[programId] == reserved.first && newestProgramWrite[next.id] == nextVersion
                                    if (newest) {
                                        _programs.update { plans ->
                                            val updated = plans.map { when (it.id) { programId -> preview.result; next.id -> next; else -> it } }
                                            if (plans.any { it.id == next.id }) updated else updated + next
                                        }
                                        repairActiveStateIfNeeded(preview.result)
                                    }
                                    newest
                                }
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        if (callerJob?.isActive == false) throw cancelled
                        throw IllegalStateException("No se pudo guardar la continuación en Room.",cancelled)
                    }
                }
            }
        }
    }

    fun previewBlockReview(
        programId: String,
        expected: com.example.kpkn.domain.training.block.BlockReviewProposal,
        accept: Boolean,
        alternativeIndex: Int? = null,
    ): BlockReviewPreview? {
        val current = getProgramById(programId) ?: return null
        if (current.pendingBlockReview != expected) return null
        val evidence = executedTrainingEvidence(current)
        val result = ProgramProgressEngine.resolvePendingBlockReview(current, accept, CompositionMetadataHolder.resolve(),
            evidence.weekIds, evidence.sessionIds, System.currentTimeMillis(), expected, alternativeIndex)
        return if (result == current) null else BlockReviewPreview(current, result, evidence)
    }

    suspend fun submitBlockReview(programId: String, preview: BlockReviewPreview): Boolean = withContext(Dispatchers.IO) {
        ongoingWorkoutMutex.withLock {
            mutateProgramNow(programId) { current ->
                if (current.id != programId || current != preview.original ||
                    executedTrainingEvidence(current) != preview.evidence) null else preview.result
            }
        }
    }

    data class PlanningRangePreview(
        val original: Program,
        val proposals: List<com.example.kpkn.domain.training.calibration.PlanningRangeProposal>,
    )

    fun previewPlanningRanges(programId: String): PlanningRangePreview? {
        val current = getProgramById(programId) ?: return null
        val proposals = com.example.kpkn.domain.training.calibration.PlanningRangeProposals.build(current, CompositionMetadataHolder.resolve())
        return if (proposals.isEmpty()) null else PlanningRangePreview(current, proposals)
    }

    suspend fun resolvePlanningRanges(programId: String, preview: PlanningRangePreview, accept: Boolean): Boolean =
        mutateProgramNow(programId) { current ->
            if (current != preview.original || current.id != programId || preview.proposals.isEmpty() ||
                com.example.kpkn.domain.training.calibration.PlanningRangeProposals.build(current, CompositionMetadataHolder.resolve()) != preview.proposals)
                return@mutateProgramNow null
            val ids = preview.proposals.map { it.id }
            val groups = preview.proposals.map { it.proposed.muscleGroup }.toSet()
            val now = System.currentTimeMillis()
            val targets = if (!accept) current.planningVolumeTargets else
                current.planningVolumeTargets.filterNot { it.muscleGroup in groups } + preview.proposals.map {
                    it.proposed.copy(chosenAtMs = now, source = "WEEKLY_FEEDBACK_CONFIRMED")
                }
            if (!com.example.kpkn.domain.training.calibration.PlanningVolumeTargets.valid(targets)) return@mutateProgramNow null
            current.copy(planningVolumeTargets = targets,
                resolvedPlanningRangeProposalIds = (current.resolvedPlanningRangeProposalIds + ids).distinct(),
                programDecisions = current.programDecisions + ProgramDecision(
                    id = "volume-range:${UUID.randomUUID()}", atMs = now, source = "WEEKLY_CHECKIN",
                    title = if (accept) "Rangos de planificación confirmados" else "Propuestas de rango descartadas",
                    detail = preview.proposals.joinToString("\n") { it.reason }))
        }

    /** Cambia sólo el rango elegido de un músculo, sobre el último programa; nunca reconstruye sesiones. */
    suspend fun savePlanningVolumeTarget(
        programId: String,
        group: com.example.kpkn.data.programs.KpknMuscleGroup,
        target: com.example.kpkn.domain.training.calibration.PlanningVolumeTarget?,
    ): Boolean {
        if (target != null && (target.muscleGroup != group ||
                !com.example.kpkn.domain.training.calibration.PlanningVolumeTargets.valid(listOf(target)))) return false
        return mutateProgramNow(programId) { current ->
            val next = current.planningVolumeTargets.filterNot { it.muscleGroup == group } + listOfNotNull(target)
            if (!com.example.kpkn.domain.training.calibration.PlanningVolumeTargets.valid(next)) return@mutateProgramNow null
            if (next == current.planningVolumeTargets) return@mutateProgramNow current
            val label = com.example.kpkn.domain.training.cycle.kpknMuscleGroupLabel(group.name)
            val detail = if (target == null) "$label: se vuelve a la referencia editorial de planificación."
                else "$label: ${target.minSets}–${target.maxSets} series equivalentes por semana, elegidas por la persona."
            current.copy(planningVolumeTargets = next, programDecisions = current.programDecisions + ProgramDecision(
                id = "volume-target:${UUID.randomUUID()}", atMs = System.currentTimeMillis(), source = "MANUAL",
                title = "Objetivo de series actualizado", detail = detail))
        }
    }

    /** Vista previa sin persistencia, con las sesiones ya iniciadas o registradas protegidas. */
    suspend fun previewWeeklyCheckin(programId: String, answer: com.example.kpkn.domain.training.cycle.WeeklyCheckin): WeeklyCheckinPreview? = withContext(Dispatchers.IO) {
        if (!_settings.value.algorithmSettings.weeklyCheckinEnabled) return@withContext null
        val current = getProgramById(programId) ?: return@withContext null
        val settings = _settings.value
        val season = seasonInput(programId)
        val evidence = executedTrainingEvidence(current)
        val result = ProgramProgressEngine.applyWeeklyCheckin(current, answer,
            CompositionMetadataHolder.resolve(), evidence.sessionIds, System.currentTimeMillis(),
            deficitMode = com.example.kpkn.domain.season.SeasonAdvisor.deficitMode(season))
        if (result == current) return@withContext null
        WeeklyCheckinPreview(current, result, answer, evidence,season,settings)
    }

    /** Confirma exactamente la vista previa; una sesión iniciada o edición intermedia la invalida. */
    suspend fun submitWeeklyCheckin(programId: String, preview: WeeklyCheckinPreview): Boolean =
        withContext(Dispatchers.IO) {
            ongoingWorkoutMutex.withLock {
                programRmwMutex.withLock rmw@{
                    val prepared = synchronized(programMutationLock) {
                        val current = getProgramById(programId)
                        if (current == null || current != preview.original || current.id != programId || _settings.value != preview.settings ||
                            !_settings.value.algorithmSettings.weeklyCheckinEnabled || executedTrainingEvidence(current) != preview.evidence) null
                        else normalizeProgramWithCompetitions(preview.result) to reserveProgramWrite(programId)
                    } ?: return@rmw false
                    commitProgramDurableForCaller(prepared.first,prepared.second,repairActiveState = true,guardInTransaction = {
                        _settings.value == preview.settings && seasonInput(programId) == preview.season
                    })
                }
            }
        }

    /**
     * Resuelve la propuesta AUGE pendiente con la evidencia real del propio
     * programa y persiste el resultado en UNA mutación (§14.5). Toda resolución
     * queda con estado terminal (aplicada/rechazada/expirada) y motivo en el
     * audit del run; una propuesta protegida por sesiones entrenadas no se
     * reclama aplicada.
     */
    suspend fun resolvePendingAutoregulationNow(
        programId: String,
        accept: Boolean,
        only: AutoregulationProposal? = null,
    ): Boolean {
        val ok = mutateProgramNow(programId) { current ->
            val evidence = executedTrainingEvidence(current)
            val logs = getLogsForProgram(programId)
            val resolved = ProgramProgressEngine.resolvePendingAutoregulation(
                program = current,
                accept = accept,
                only = only,
                executedWeekIds = evidence.weekIds,
                executedSessionIds = evidence.sessionIds,
            ).program
            ProgramProgressEngine.reconcileNativeRunCursorWithLogs(resolved, logs)
        }
        if (!ok) return false
        val program = getProgramById(programId) ?: return false
        val active = _activeProgramState.value?.takeIf { it.programId == programId } ?: return true
        val repaired = ProgramActiveStateEngine.repairForProgram(program, active) ?: active
        if (repaired != active) {
            _activeProgramState.value = repaired
            persistActiveProgramStateAsync(repaired)
        }
        return true
    }

    /**
     * Resolves one native per-exercise proposal through the same durable Program
     * mutation lane as the existing plan proposal UI. Past sessions/logs are
     * excluded by the runtime before any future prescription is changed.
     */
    suspend fun resolveNativeProgressionProposalNow(
        programId: String,
        proposalId: String,
        accept: Boolean,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        val catalog = exerciseCatalogSnapshot().mapNotNull { info ->
            info.catalogConfigurationId?.let { it to info }
        }.toMap()
        repeat(NATIVE_RESOLVE_MAX_ATTEMPTS) { attempt ->
            // Orden global de locks: ongoing -> programRmw -> Coordinator ->
            // programWrite -> active. Esta función NO sostiene [ongoingWorkoutMutex]
            // mientras corre [mutateProgramNow] (que toma programRmw/Coordinator/
            // programWrite): [replaceProgramSafely] pide Coordinator ANTES que ongoing,
            // y sostener ongoing aquí cerraba un ciclo de espera entre ambas acciones
            // de Detalle de programa. En su lugar: la instantánea de logs/sesión en
            // curso se toma BAJO el mutex (esperando a que termine cualquier
            // finalización/escritura en vuelo), el mutex se suelta y la instantánea
            // se revalida DENTRO del transform (bajo el monitor del programa).
            val (snapshotLogIds, snapshotOngoingIds) = ongoingWorkoutMutex.withLock {
                getLogsForProgram(programId).mapTo(mutableSetOf()) { it.id } to ongoingSessionIdsFor(programId)
            }
            var proposalWasKnown = false
            var deferred = false
            val committed = mutateProgramNow(programId) { current ->
                proposalWasKnown = current.nativeProgressionProposals.any { it.proposalId == proposalId } ||
                    current.nativeProgressionAudit.any { it.proposalId == proposalId }
                if (!proposalWasKnown) return@mutateProgramNow null
                val liveLogs = getLogsForProgram(programId)
                val liveOngoingIds = ongoingSessionIdsFor(programId)
                if (
                    // Una finalización/inicio/escritura de sesión ya está en vuelo
                    // (mantiene el mutex de la reserva a la publicación) o el
                    // historial/sesión en curso cambió tras la instantánea: no se
                    // reserva una versión que le quite su avance a esa finalización
                    // ni se aplica sobre una sesión que ya no es la que se midió.
                    ongoingWorkoutMutex.isLocked ||
                    liveOngoingIds != snapshotOngoingIds ||
                    liveLogs.mapTo(mutableSetOf()) { it.id } != snapshotLogIds
                ) {
                    deferred = true
                    return@mutateProgramNow null
                }
                NativeWorkoutProgressionRuntime.resolveProposal(
                    program = current,
                    proposalId = proposalId,
                    accept = accept,
                    logs = liveLogs,
                    ongoingSessionIds = liveOngoingIds,
                    curatedExercises = catalog,
                    nowMs = nowMs,
                )
            }
            if (!deferred) return committed && proposalWasKnown
            if (attempt < NATIVE_RESOLVE_MAX_ATTEMPTS - 1) delay(NATIVE_RESOLVE_RETRY_DELAY_MS)
        }
        // La sesión en curso / el historial no se estabilizaron: nada se aplicó.
        return false
    }

    private fun ongoingSessionIdsFor(programId: String): Set<String> =
        _ongoingWorkout.value
            ?.takeIf { it.programId == programId }
            ?.let { setOf(it.session.id) }
            .orEmpty()

    // ─── Ongoing Workout ──────────────────────────────────────────────────────

    private val _ongoingWorkout = MutableStateFlow<OngoingWorkoutState?>(null)
    val ongoingWorkout: StateFlow<OngoingWorkoutState?> = _ongoingWorkout.asStateFlow()
    private val _ongoingWorkoutCorrupt = MutableStateFlow(false)
    val ongoingWorkoutCorrupt: StateFlow<Boolean> = _ongoingWorkoutCorrupt.asStateFlow()
    private val ongoingWorkoutMutex = Mutex()
    val ongoingPersistenceScope: CoroutineScope get() = scope

    suspend fun startWorkout(
        state: OngoingWorkoutState,
        replaceExisting: Boolean = false,
    ): StartWorkoutResult {
        return withContext(Dispatchers.IO + NonCancellable) {
            ongoingWorkoutMutex.withLock {
                if (state.session.captureMode == SessionCaptureMode.EXTERNAL) {
                    return@withLock StartWorkoutResult.Failed(IllegalStateException(
                        "Esta sesión se registra como actividad externa; conserva su clase o práctica original.",
                    ))
                }
                if (_ongoingWorkoutCorrupt.value && !replaceExisting) {
                    val existing = _ongoingWorkout.value
                    if (existing != null && !existing.sameOngoingIdentity(state.programId, state.session.id)) {
                        return@withLock StartWorkoutResult.Conflict(existing)
                    }
                    return@withLock StartWorkoutResult.Corrupt
                }
                val existing = _ongoingWorkout.value
                if (existing != null &&
                    !existing.sameOngoingIdentity(state.programId, state.session.id) &&
                    !replaceExisting
                ) {
                    return@withLock StartWorkoutResult.Conflict(existing)
                }
                // A blank program id is the explicit ad-hoc workout sentinel.
                // Any non-blank id must still exist in the authoritative cache;
                // otherwise a delayed start for a deleted program would revive
                // an ongoing Room row after deleteProgram returned.
                if (state.programId.isNotBlank() && _programs.value.none { it.id == state.programId }) {
                    return@withLock StartWorkoutResult.NotFound
                }
                val normalized = state.normalizedIdentityFields()
                try {
                    db.withTransaction {
                        db.stateDao().upsertOngoingWorkout(normalized.toEntity())
                        calibrationDecisions.saveSnapshot(normalized.calibrationDecisions)
                    }
                    _ongoingWorkout.value = normalized
                    _ongoingWorkoutCorrupt.value = false
                    StartWorkoutResult.Started
                } catch (error: Throwable) {
                    StartWorkoutResult.Failed(error)
                }
            }
        }
    }

    fun updateOngoingWorkout(update: (OngoingWorkoutState) -> OngoingWorkoutState) {
        scope.launch { writeOngoingLocked(update) }
    }

    /**
     * Updates ongoing state in memory and waits until Room has the same snapshot.
     * Use for structural session events (recorded set, skip, pause, finish prep).
     */
    suspend fun updateOngoingWorkoutAndFlush(update: (OngoingWorkoutState) -> OngoingWorkoutState): com.example.kpkn.screens.workout.WorkoutPersistResult {
        return withContext(Dispatchers.IO + NonCancellable) {
            writeOngoingLocked(update)
        }
    }

    private suspend fun writeOngoingLocked(update: (OngoingWorkoutState) -> OngoingWorkoutState): com.example.kpkn.screens.workout.WorkoutPersistResult {
        return ongoingWorkoutMutex.withLock {
            val current = _ongoingWorkout.value ?: return@withLock com.example.kpkn.screens.workout.WorkoutPersistResult.Cancelled
            try {
                val next = update(current).normalizedIdentityFields()
                db.withTransaction {
                    db.stateDao().upsertOngoingWorkout(next.toEntity())
                    calibrationDecisions.saveSnapshot(next.calibrationDecisions)
                }
                _ongoingWorkout.value = next
                com.example.kpkn.screens.workout.WorkoutPersistResult.Ok
            } catch (error: Throwable) {
                com.example.kpkn.screens.workout.WorkoutPersistResult.Failed(error)
            }
        }
    }

    /**
     * Descarte fire-and-forget (p. ej. archivar un programa con sesión en curso).
     *
     * [clearOngoingWorkoutAndFlush] SÍ lanza cuando la ejecución cambió o Room
     * falla (el flujo de cancelación necesita esa señal: borrado ANTES de lo
     * terminal, error ⇒ la sesión se conserva y se puede reintentar). Aquí nadie
     * espera el resultado, y el scope del repositorio no tiene
     * `CoroutineExceptionHandler`: una excepción sin capturar tumbaría el
     * proceso. Por eso se registra y se descarta; la sesión queda intacta.
     * También limpia una fila ongoing corrupta (sin estado en memoria).
     */
    fun clearOngoingWorkout() {
        launchClearOngoingWorkout()
    }

    /** Misma operación que [clearOngoingWorkout], devolviendo el job para que un test lo espere. */
    internal fun launchClearOngoingWorkout(
        expected: OngoingWorkoutState? = _ongoingWorkout.value,
    ): Job? {
        if (expected == null && !_ongoingWorkoutCorrupt.value) return null
        return scope.launch {
            try {
                clearOngoingWorkoutAndFlush(expected)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                android.util.Log.w(
                    "ProgramRepository",
                    "clearOngoingWorkout: la sesión en curso se conserva (${error.message})",
                    error,
                )
            }
        }
    }

    private fun clearOngoingWorkoutForProgram(programId: String) {
        runBlocking(Dispatchers.IO + NonCancellable) {
            ongoingWorkoutMutex.withLock {
                if (_ongoingWorkout.value?.programId != programId) return@withLock
                val discarded = _ongoingWorkout.value
                _ongoingWorkout.value = null
                _ongoingWorkoutCorrupt.value = false
                clearOngoingRowAndDiscardedCalibrations(discarded)
            }
        }
    }

    /**
     * Borra la fila de la sesión en curso y, si se descarta (no si ya se escribió su registro), también las decisiones del
     * calibrador de esa ejecución: no hay serie en el historial que las respalde y no deben quedar como referencia aceptada.
     */
    private suspend fun clearOngoingRowAndDiscardedCalibrations(discarded: OngoingWorkoutState?) {
        db.withTransaction {
            db.stateDao().clearOngoingWorkout()
            if (discarded != null && discarded.logAlreadyWrittenId == null) {
                calibrationDecisions.discardExecution(discarded.programId, discarded.session.id, discarded.startTime)
            }
        }
    }

    /** Clears ongoing in memory and waits for Room delete. */
    suspend fun clearOngoingWorkoutAndFlush(expected: OngoingWorkoutState? = null) {
        withContext(Dispatchers.IO + NonCancellable) {
            ongoingWorkoutMutex.withLock {
                if (expected != null) {
                    val current = _ongoingWorkout.value ?: return@withLock
                    check(current.programId == expected.programId && current.session.id == expected.session.id &&
                        current.startTime == expected.startTime) { "La ejecución cambió antes del descarte." }
                }
                val discarded = _ongoingWorkout.value
                clearOngoingRowAndDiscardedCalibrations(discarded)
                _ongoingWorkout.value = null
                _ongoingWorkoutCorrupt.value = false
            }
        }
    }

    /**
     * Synchronously persists the current [ongoingWorkout] state to Room.
     * Call from MainActivity's onStop to prevent data loss when the OS kills the process
     * before the background write coroutine completes.
     */
    suspend fun flushPendingWrites() {
        withContext(Dispatchers.IO + NonCancellable) {
            // Finalization acquires ongoing→program; keep the same order here
            // so a lifecycle flush cannot deadlock a concurrent completion.
            val snapshot = ongoingWorkoutMutex.withLock {
                // Reservas y snapshots DENTRO de [ongoingWorkoutMutex]: una
                // finalización en vuelo lo mantiene de la reserva a la
                // publicación, así que el flush ve su programa avanzado, su
                // cursor y su log ya publicados (o empieza después y su reserva
                // es la más nueva). Reservar antes de pedir el mutex dejaba que
                // las guardas de versión descartaran el programa/cursor de la
                // finalización mientras el flush escribía el snapshot previo.
                // Reserve both lanes before taking their snapshots. A concurrent
                // command that mutates either cache after this point receives a
                // newer version and supersedes this lifecycle flush.
                val activeVersion = reserveActiveStateWrite()
                val programVersions = _programs.value.map { it.id }.associateWith(::reserveProgramWrite)
                val flushSnapshot = FlushSnapshot(
                    activeVersion = activeVersion,
                    programVersions = programVersions,
                    activeProgram = _activeProgramState.value,
                    programs = _programs.value.filter { it.id in programVersions },
                    latestLogs = _history.value.take(32),
                )
                val workout = _ongoingWorkout.value
                if (workout != null) {
                    db.stateDao().upsertOngoingWorkout(workout.toEntity())
                } else {
                    db.stateDao().clearOngoingWorkout()
                }
                flushSnapshot
            }
            // Keep program→active→Room ordering aligned with finalizeWorkout.
            programWriteMutex.withLock {
                snapshot.programs.forEach { program ->
                    val version = snapshot.programVersions[program.id] ?: return@forEach
                    if (newestProgramWrite[program.id] == version) {
                        db.programDao().upsert(program.normalizedIdentityFields().toEntity())
                    }
                }
                // Null is an explicit tombstone. The same versioned lane is
                // used for both upsert and clear so an old cursor cannot be
                // revived on process stop.
                persistActiveProgramStateNow(snapshot.activeProgram, snapshot.activeVersion)
                snapshot.latestLogs.forEach { log ->
                    db.workoutLogDao().insert(log.toEntity())
                }
            }
        }
    }

    /** Estado capturado por [flushPendingWrites] bajo [ongoingWorkoutMutex]. */
    private class FlushSnapshot(
        val activeVersion: Long,
        val programVersions: Map<String, Long>,
        val activeProgram: ActiveProgramState?,
        val programs: List<Program>,
        val latestLogs: List<WorkoutLog>,
    )

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

    private val _workoutTags = MutableStateFlow<Map<String, WorkoutTag>>(emptyMap())
    val workoutTags: StateFlow<Map<String, WorkoutTag>> = _workoutTags.asStateFlow()

    private val _replacementDecisions = MutableStateFlow<List<ExerciseReplacementDecisionV2>>(emptyList())
    val replacementDecisions: StateFlow<List<ExerciseReplacementDecisionV2>> = _replacementDecisions.asStateFlow()

    fun updateSettings(update: (Settings) -> Settings) {
        _settings.update(update)
        ExerciseNicknameResolver.nicknames = _settings.value.exerciseNicknames
        scope.launch {
            PersistenceWriteCoordinator.mutex.withLock {
                db.settingsDao().upsert(_settings.value.toEntity())
            }
        }
    }

    /** Guarda solo el material y confirma Room antes de cerrar el editor. */
    suspend fun saveEquipmentInventory(inventory: EquipmentInventory) = withContext(Dispatchers.IO) {
        PersistenceWriteCoordinator.mutex.withLock {
            db.settingsDao().upsert(_settings.value.copy(equipmentInventory = inventory).toEntity())
            withContext(Dispatchers.Main.immediate) {
                _settings.update { it.copy(equipmentInventory = inventory) }
            }
        }
    }

    /**
     * Publica en las cachés el resultado de un commit de setup (M12), igual que
     * `NutritionRepository.publishSetupCommit` publica el suyo.
     *
     * El estado activo se re-sincroniza **siempre desde la fila commiteada en
     * Room**: aunque [program] sea null y [activateProgram] sea false. Un replay
     * de un commit viejo (o un flag heredado) no puede dejar la caché obsoleta
     * cuando la DB ya contiene una activación más nueva. Solo se refleja lo que
     * ya está commiteado en Room —no se activa nada nuevo ni se escribe la DB.
     *
     * [activateProgram] se conserva en la firma por compatibilidad con M12;
     * el committedDB ya es el resultado de ese flag.
     */
    suspend fun publishSetupCommit(settings: Settings, program: Program?, activateProgram: Boolean) {
        val committedActive = withContext(Dispatchers.IO) {
            db.stateDao().getActiveProgram()?.toActiveProgramState()
        }
        withContext(Dispatchers.Main.immediate) {
            _settings.value = settings
            ExerciseNicknameResolver.nicknames = settings.exerciseNicknames
            if (program != null) {
                // Reserva + publicación del programa atómicas entre sí, con el
                // mismo monitor que el resto de escritores de esta caché.
                synchronized(programMutationLock) {
                    reserveProgramWrite(program.id)
                    _programs.update { current ->
                        val index = current.indexOfFirst { it.id == program.id }
                        if (index < 0) current + program else current.toMutableList().also { it[index] = program }
                    }
                }
            }
            // Competición del alta (C4c): el registro PLANNED y sus recordatorios salen del mismo camino que el guardado normal.
            if (program != null) publishCompetitionRecordsOf(program)
            // Commiteado en DB => publicado en memoria, con o sin programa y
            // con cualquier valor del flag de activación.
            _activeProgramState.value = committedActive
        }
    }

    /**
     * Registro PLANNED y recordatorios de la fecha clave COMPETITION de un programa ya guardado (alta). Mismo cálculo que
     * [normalizeProgramWithCompetitions]; sin fecha clave no hace nada. Un fallo se registra en el log y no revierte el alta.
     */
    private fun publishCompetitionRecordsOf(program: Program) {
        try {
            val repo = CompetitionRepository.getInstance()
            val weekId = ProgramKeyDateEngine.competitionKeyDate(program)
                ?.let { ProgramKeyDateEngine.locateCompetitionWeekDay(program, it)?.first }
            com.example.kpkn.domain.training.CompetitionKeyDateSync
                .migrate(program = program, existingRecords = repo.records.value, competitionWeekId = weekId)
                .recordsToUpsert
                .forEach(repo::upsert)
        } catch (error: Throwable) {
            android.util.Log.w(
                "ProgramRepository",
                "publishSetupCommit: el registro de competición del alta no se guardó (${error.message})",
                error,
            )
        }
    }

    suspend fun replaceProgramSafely(replacement: Program): ReplaceProgramResult {
        return withContext(Dispatchers.IO + NonCancellable) {
            ongoingWorkoutMutex.withLock {
                PersistenceWriteCoordinator.mutex.withLock {
                    programWriteMutex.withLock {
                    activeStateWriteMutex.withLock {
                        check(_isReady.value) { "Los programas todavía se están cargando." }
                        check(_programs.value.any { it.id == replacement.id }) { "El programa de destino ya no existe." }
                        check(!_ongoingWorkoutCorrupt.value && _ongoingWorkout.value?.programId != replacement.id) {
                            "Termina o descarta la sesión en curso antes de reemplazar el plan."
                        }
                        val active = _activeProgramState.value?.takeIf { it.programId == replacement.id }
                        val replacementToStore: Program
                        val replacementActive: ActiveProgramState?
                        if (active == null) {
                            replacementToStore = normalizeProgramWithCompetitions(replacement)
                            replacementActive = null
                        } else {
                            val seedProgram = normalizeProgramWithCompetitions(replacement.copy(runState = null))
                            val seedState = ProgramActiveStateEngine.repairForProgram(
                                seedProgram,
                                ActiveProgramState(programId = seedProgram.id),
                            ) ?: ActiveProgramState(programId = seedProgram.id)
                            val run = ProgramRunState(
                                runId = ProgramProgressEngine.newRunId(),
                                cycleNumber = seedState.currentCycleNumber ?: 1,
                                weekInstanceId = seedState.currentWeekInstanceId ?: seedState.currentWeekId.takeIf { it.isNotBlank() },
                                weekId = seedState.currentWeekId.takeIf { it.isNotBlank() }
                                    ?.let { ProgramProgressEngine.templateWeekIdFromInstance(it) ?: it },
                                macrocycleId = seedState.currentMacrocycleId,
                                blockId = seedState.currentBlockId,
                                mesocycleId = seedState.currentMesocycleId,
                                status = ProgramRunStatus.ACTIVE,
                            )
                            replacementToStore = seedProgram.copy(runState = run)
                            replacementActive = ProgramActiveStateEngine.repairForProgram(
                                replacementToStore,
                                seedState.copy(
                                    status = active.status,
                                    programRunId = run.runId,
                                ),
                            ) ?: seedState.copy(status = active.status, programRunId = run.runId)
                        }

                        val programVersion = reserveProgramWrite(replacementToStore.id)
                        val activeVersion = replacementActive?.let { reserveActiveStateWrite() }
                        db.withTransaction {
                            if (newestProgramWrite[replacementToStore.id] == programVersion) {
                                db.programDao().upsert(replacementToStore.toEntity())
                            }
                            if (replacementActive != null && activeVersion != null) {
                                persistActiveProgramStateLocked(replacementActive, activeVersion)
                            }
                        }

                        // Chequeo + publicación atómicos con la reserva de
                        // arriba (ver [programMutationLock]): si otra escritura
                        // reservó más tarde, esta caché no se pisa y Room
                        // converge a la más nueva. Mismo patrón que el guard
                        // del estado activo justo debajo.
                        synchronized(programMutationLock) {
                            if (newestProgramWrite[replacementToStore.id] == programVersion) {
                                _programs.update { programs ->
                                    val index = programs.indexOfFirst { it.id == replacementToStore.id }
                                    if (index < 0) programs + replacementToStore
                                    else programs.toMutableList().also { it[index] = replacementToStore }
                                }
                            }
                        }
                        if (replacementActive != null && newestActiveStateWrite == activeVersion) {
                            _activeProgramState.value = replacementActive
                        }
                        ReplaceProgramResult(
                            programId = replacementToStore.id,
                            keptActiveRun = replacementActive != null,
                            ongoingWorkoutProtected = _ongoingWorkout.value?.programId == replacementToStore.id,
                        )
                    }
                }
            }
        }
        }
    }

    data class ReplaceProgramResult(val programId: String, val keptActiveRun: Boolean, val ongoingWorkoutProtected: Boolean)

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

    fun getWorkoutTagsForExercise(exerciseKey: String): List<WorkoutTag> =
        _workoutTags.value.values
            .filter { it.exerciseKey == exerciseKey }
            .sortedByDescending { it.lastUsedAtIso }

    fun upsertWorkoutTag(tag: WorkoutTag) {
        val normalized = com.example.kpkn.domain.workout.WorkoutTagResolver.withNormalized(tag)
        _workoutTags.update { it + (normalized.id to normalized) }
        scope.launch { db.workoutV2Dao().upsertWorkoutTag(normalized.toEntity()) }
    }

    fun deleteWorkoutTag(tagId: String) {
        _workoutTags.update { it - tagId }
        scope.launch { db.workoutV2Dao().deleteWorkoutTag(tagId) }
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
                // Program JSON is intentionally denormalized and can exceed CursorWindow's
                // per-row limit. Read only metadata first, then reconstruct each blob from
                // bounded SQL substrings so a valid large program remains loadable after restart.
                val programEntities = db.programDao().getAllHeaders().mapNotNull { header ->
                    val data = buildString {
                        var start = 1
                        while (true) {
                            val chunk = db.programDao().getDataChunk(
                                id = header.id,
                                start = start,
                                length = PROGRAM_DATA_CHUNK_CHARS,
                            ).orEmpty()
                            if (chunk.isEmpty()) break
                            append(chunk)
                            if (chunk.length < PROGRAM_DATA_CHUNK_CHARS) break
                            start += chunk.length
                        }
                    }
                    if (data.isBlank()) null else ProgramEntity(header.id, header.name, data)
                }
                val rawPrograms = programEntities.map { entity ->
                    entity.id to runCatching { entity.toProgram() }.getOrNull()
                }
                val migrationLoad = ProgramMigrationEngine.loadProgramsSafely(rawPrograms)
                val programs = migrationLoad.programs.map { program ->
                    ProgramPersistNormalizer.repairNativeRunWeekCursor(program.normalizedIdentityFields())
                }
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
                val ongoingEntity = db.stateDao().getOngoingWorkout()
                val ongoingDecode = ongoingEntity?.toOngoingDecode() ?: OngoingDecode.Empty
                val ongoingWorkout = (ongoingDecode as? OngoingDecode.Ok)?.state?.normalizedIdentityFields()
                val ongoingCorrupt = ongoingDecode is OngoingDecode.Corrupt
                val contextPerformance = db.workoutV2Dao().getAllContextPerformance()
                    .map { it.toContextPerformanceStateV2() }
                    .associateBy { it.contextKey }
                val globalPerformance = db.workoutV2Dao().getAllGlobalPerformance()
                    .map { it.toGlobalPerformanceStateV3() }
                    .associateBy { it.globalKey }
                val contextProfiles = db.workoutV2Dao().getAllContextProfiles()
                    .map { it.toWorkoutContextProfile() }
                    .associateBy { it.id }
                val workoutTags = db.workoutV2Dao().getAllWorkoutTags()
                    .map { it.toWorkoutTag() }
                    .associateBy { it.id }
                val replacementDecisions = db.workoutV2Dao().getAllReplacementDecisions()
                    .map { it.toExerciseReplacementDecisionV2() }
                val normalizedActiveProgram = normalizeActiveProgramState(programs, activeProgram)

                programEntities.forEach { entity ->
                    // Lo que se publica en memoria ([programs]) es lo que Room debe
                    // terminar guardando: migración + identidades + reparación del
                    // cursor nativo. Persistir la copia sin reparar (`migrated`) hacía
                    // que Room nunca convergiera con la caché y se reescribiera en
                    // cada arranque. La escritura reserva su versión: una escritura
                    // posterior de la app la supera en vez de ser pisada.
                    val normalized = programsById[entity.id] ?: return@forEach
                    val stored = runCatching { entity.toProgram() }.getOrNull()
                    if (stored != normalized) {
                        val version = reserveProgramWrite(normalized.id)
                        scope.launch { persistProgramIfNewest(normalized, version) }
                    }
                }
                logEntities.forEach { entity ->
                    val normalized = entity.toWorkoutLog()?.normalizedIdentityFields() ?: return@forEach
                    if (entity.toWorkoutLog() != normalized) {
                        scope.launch { db.workoutLogDao().insert(normalized.toEntity()) }
                    }
                }
                val persistedOngoing = db.stateDao().getOngoingWorkout()?.toOngoingWorkoutState()
                if (!ongoingCorrupt &&
                    persistedOngoing != null &&
                    persistedOngoing != ongoingWorkout &&
                    ongoingWorkout != null
                ) {
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
                    ExerciseNicknameResolver.nicknames = settings.exerciseNicknames
                    _activeProgramState.value = normalizedActiveProgram
                    _ongoingWorkout.value = ongoingWorkout
                    _ongoingWorkoutCorrupt.value = ongoingCorrupt
                    _contextPerformance.value = contextPerformance
                    _globalPerformance.value = globalPerformance
                    _contextProfiles.value = contextProfiles
                    _workoutTags.value = workoutTags
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

        if (requiresNativeWeekInstances()) {
            val cycle = runState?.cycleNumber ?: 1
            val hierarchy = com.example.kpkn.domain.training.ProgramHierarchyIndex(this)
            val templateWeekId = runState?.weekId
                ?.let { ProgramProgressEngine.templateWeekIdFromInstance(it) ?: it }
                ?: hierarchy.orderedWeeks().firstOrNull()?.week?.id
            val location = templateWeekId?.let { hierarchy.locateWeek(it) }
                ?: hierarchy.orderedWeeks().firstOrNull()
            if (location != null) {
                val templateWeekId = runState?.weekId
                    ?.let { ProgramProgressEngine.templateWeekIdFromInstance(it) ?: it }
                    ?: location.week.id
                val instanceId = coerceNativeWeekInstanceId(
                    cycle,
                    templateWeekId,
                    runState?.weekInstanceId,
                )
                return ActiveProgramState(
                    programId = programId,
                    status = if (runState?.status == ProgramRunStatus.COMPLETED) {
                        ProgramStatus.COMPLETED
                    } else {
                        ProgramStatus.ACTIVE
                    },
                    currentMacrocycleIndex = location.macroIndex,
                    currentBlockIndex = location.blockIndex,
                    currentMesocycleIndex = location.globalMesoIndex,
                    currentWeekId = instanceId,
                    currentWeekInstanceId = instanceId,
                    currentCycleNumber = cycle,
                    programRunId = runState?.runId ?: ProgramProgressEngine.newRunId(),
                    currentMacrocycleId = location.macrocycleId,
                    currentBlockId = location.blockId,
                    currentMesocycleId = location.mesocycleId,
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
        @Volatile private var appContext: Context? = null

        /**
         * Llamar una vez al inicio (MainActivity.onCreate o Application.onCreate).
         * Carga datos de Room → StateFlows en background.
         */
        fun init(context: Context): ProgramRepository =
            INSTANCE ?: synchronized(this) {
                appContext = context.applicationContext
                INSTANCE ?: ProgramRepository(
                    KpknDatabase.getInstance(context.applicationContext),
                ).also { INSTANCE = it; it.loadFromDb() }
            }

        /** Robolectric-friendly init with in-memory Room and isolated singleton. */
        fun initForTests(context: Context): ProgramRepository = synchronized(this) {
            closeInstance()
            appContext = context.applicationContext
            KpknDatabase.closeInstance()
            ProgramRepository(
                KpknDatabase.createInMemory(context.applicationContext),
                ownsDatabase = true,
            ).also { INSTANCE = it; it.loadFromDb() }
        }

        /**
         * Robolectric init sobre Room en ARCHIVO con un nombre propio: para los tests
         * que cierran y reabren el repositorio y necesitan una BD aislada por test
         * (el singleton de producción siempre usa `kpkn.db`). El repositorio es dueño
         * de la BD: [closeInstance] la cierra; borrar el archivo
         * (`context.deleteDatabase(name)`) es responsabilidad del test.
         */
        internal fun initForTestsWithDatabaseFile(context: Context, databaseName: String): ProgramRepository =
            synchronized(this) {
                closeInstance()
                appContext = context.applicationContext
                KpknDatabase.closeInstance()
                ProgramRepository(
                    Room.databaseBuilder(context.applicationContext, KpknDatabase::class.java, databaseName).build(),
                    ownsDatabase = true,
                ).also { INSTANCE = it; it.loadFromDb() }
            }

        /** Acceso rápido después de init(). */
        fun getInstance(): ProgramRepository =
            INSTANCE ?: error("ProgramRepository not initialized — call init(context) first.")

        internal fun closeInstance() {
            // Espera acotada: un hijo encolado en Main (este mismo hilo, bloqueado aquí) no terminaría nunca.
            INSTANCE?.let { runBlocking { withTimeoutOrNull(2.seconds) { it.repositoryJob.cancelAndJoin() } } }
            INSTANCE?.takeIf { it.ownsDatabase }?.db?.close()
            INSTANCE = null
            appContext = null
        }
    }
}

sealed class StartWorkoutResult {
    data object Started : StartWorkoutResult()
    data class Conflict(val existing: OngoingWorkoutState) : StartWorkoutResult()
    data object Corrupt : StartWorkoutResult()
    data object NotFound : StartWorkoutResult()
    data class Failed(val cause: Throwable) : StartWorkoutResult()
}

/**
 * Un commit durable ([ProgramRepository.addProgramNow]) fue reemplazado por una
 * escritura más reciente del mismo programa antes de llegar a Room: NO se
 * escribió la fila ni se publicó la caché. El llamador debe informar conflicto
 * (y reintentar si quiere), nunca un éxito con estado obsoleto.
 */
class ProgramWriteConflictException(val programId: String) : IllegalStateException(
    "El programa cambió mientras se guardaba. Revisa los cambios y vuelve a guardar.",
)
