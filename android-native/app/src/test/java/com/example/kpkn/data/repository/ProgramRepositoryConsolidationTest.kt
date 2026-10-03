package com.example.kpkn.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toActiveProgramState
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.db.toProgram
import com.example.kpkn.data.db.toWorkoutLog
import com.example.kpkn.data.models.ActiveProgramState
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.NativeProgressionIdentity
import com.example.kpkn.data.models.NativeProgressionProposal
import com.example.kpkn.data.models.NativeProgressionProposalKind
import com.example.kpkn.data.models.NativeProgressionResolutionStatus
import com.example.kpkn.data.models.OngoingWorkoutState
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramRunState
import com.example.kpkn.data.models.ProgramStatus
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.training.KPKN_NATIVE_CURATED_ORIGIN
import com.example.kpkn.domain.training.ProgramProgressEngine
import com.example.kpkn.screens.workout.WorkoutPersistResult
import java.util.concurrent.Callable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Consolidación W+S de [ProgramRepository]: ciclo real de los planes nativos al
 * finalizar, evidencia de entrenamiento por ciclo, descarte asíncrono sin
 * excepciones sueltas, orden global de locks, flush vs finalización,
 * reconciliación del cursor y arranque que converge con Room.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class ProgramRepositoryConsolidationTest {

    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        ProgramRepository.closeInstance()
        KpknDatabase.closeInstance()
        Dispatchers.resetMain()
    }

    // ─── Fixtures ────────────────────────────────────────────────────────────

    private class NativeFixture(
        val program: Program,
        val week1: ProgramWeek,
        val week2: ProgramWeek,
        val macroId: String,
        val blockId: String,
        val mesoId: String,
        val runId: String,
    )

    private fun executableSession(id: String, name: String): Session = Session(
        id = id,
        name = name,
        exercises = listOf(
            Exercise(
                id = "$id-exercise",
                name = name,
                sets = listOf(ExerciseSet(id = "$id-set", targetReps = 5, targetRPE = 7.0)),
            ),
        ),
    )

    /**
     * Plan COMPLEX de dos semanas. Con [native] el bloque queda marcado como
     * curado por el motor nativo, así que usa instancias de ciclo
     * (`inst_c<N>_<semana>`); sin él es un COMPLEX lineal clásico.
     */
    private fun nativeFixture(
        id: String,
        cycle: Int = 1,
        week1Sessions: Int = 1,
        native: Boolean = true,
    ): NativeFixture {
        val week1 = ProgramWeek(
            id = "$id-w1",
            name = "Semana 1",
            sessions = (1..week1Sessions).map { executableSession("$id-w1-s$it", "Sesion $it") },
        )
        val week2 = ProgramWeek(
            id = "$id-w2",
            name = "Semana 2",
            sessions = listOf(executableSession("$id-w2-s1", "Sesion semana 2")),
        )
        val meso = Mesocycle(id = "$id-meso", name = "Meso", weeks = listOf(week1, week2))
        val block = Block(
            id = "$id-block",
            name = "Bloque",
            mesocycles = listOf(meso),
            prescriptionOrigin = if (native) KPKN_NATIVE_CURATED_ORIGIN else null,
        )
        val macro = Macrocycle(id = "$id-macro", name = "Macro", blocks = listOf(block))
        val runId = "$id-run"
        val weekInstance = if (native) ProgramProgressEngine.instanceIdFor(cycle, week1.id) else week1.id
        val program = Program(
            id = id,
            name = "Plan $id",
            structure = ProgramStructure.COMPLEX,
            macrocycles = listOf(macro),
            runState = ProgramRunState(
                runId = runId,
                cycleNumber = cycle,
                weekId = week1.id,
                weekInstanceId = weekInstance,
                macrocycleId = macro.id,
                blockId = block.id,
                mesocycleId = meso.id,
            ),
        )
        return NativeFixture(program, week1, week2, macro.id, block.id, meso.id, runId)
    }

    private fun activeStateOf(fixture: NativeFixture, cycle: Int = 1, native: Boolean = true): ActiveProgramState {
        val instance = if (native) ProgramProgressEngine.instanceIdFor(cycle, fixture.week1.id) else fixture.week1.id
        return ActiveProgramState(
            programId = fixture.program.id,
            status = ProgramStatus.ACTIVE,
            currentWeekId = instance,
            currentWeekInstanceId = instance,
            currentCycleNumber = cycle,
            currentMacrocycleId = fixture.macroId,
            currentBlockId = fixture.blockId,
            currentMesocycleId = fixture.mesoId,
            programRunId = fixture.runId,
        )
    }

    private suspend fun newRepository(): ProgramRepository {
        val repository = ProgramRepository.initForTests(ApplicationProvider.getApplicationContext<Context>())
        withTimeout(10_000) { repository.isReady.first { it } }
        repository.resetAllStateSync()
        return repository
    }

    private suspend fun seedActive(
        repository: ProgramRepository,
        fixture: NativeFixture,
        cycle: Int = 1,
        native: Boolean = true,
    ) {
        repository.addProgram(fixture.program)
        withTimeout(5_000) { repository.programs.first { list -> list.any { it.id == fixture.program.id } } }
        repository.updateActiveProgramState(activeStateOf(fixture, cycle, native))
        repository.updateProgramNow(fixture.program)
    }

    private fun logOf(
        id: String,
        fixture: NativeFixture,
        session: Session,
        week: ProgramWeek,
        cycle: Int? = null,
        withInstance: Boolean = false,
        runId: String? = fixture.runId,
    ) = WorkoutLog(
        id = id,
        programId = fixture.program.id,
        sessionId = session.id,
        sessionName = session.name,
        date = "2026-09-30T10:00:00Z",
        durationMinutes = 30,
        weekId = week.id,
        weekInstanceId = if (withInstance && cycle != null) ProgramProgressEngine.instanceIdFor(cycle, week.id) else null,
        cycleNumber = cycle,
        programRunId = runId,
    )

    // ─── (1) finalizeWorkout respeta el ciclo de los planes nativos ──────────

    @Test
    fun finalizeWorkout_nativeProgramInCycleTwo_storesCycleTwoAndItsWeekInstance() = runBlocking {
        val repository = newRepository()
        val fixture = nativeFixture("finalize-c2", cycle = 2, week1Sessions = 2)
        seedActive(repository, fixture, cycle = 2)
        val session = fixture.week1.sessions.first()

        // Igual que WorkoutFinishController: el log no trae ciclo ni instancia.
        repository.finalizeWorkout(logOf("finalize-c2-log", fixture, session, fixture.week1, runId = null))

        val cycleTwoInstance = ProgramProgressEngine.instanceIdFor(2, fixture.week1.id)
        val stored = repository.history.value.single { it.id == "finalize-c2-log" }
        assertEquals(2, stored.cycleNumber)
        assertEquals(cycleTwoInstance, stored.weekInstanceId)
        assertEquals(fixture.week1.id, stored.weekId)
        val persisted = repository.databaseForTests().workoutLogDao().getById("finalize-c2-log")?.toWorkoutLog()
        assertEquals(2, persisted?.cycleNumber)
        assertEquals(cycleTwoInstance, persisted?.weekInstanceId)
        // La semana sigue incompleta (2 sesiones requeridas): el cursor no se mueve del ciclo 2.
        val program = repository.getProgramById(fixture.program.id)!!
        assertEquals(2, program.runState?.cycleNumber)
        assertEquals(cycleTwoInstance, program.runState?.weekInstanceId)
    }

    @Test
    fun finalizeWorkout_explicitCycleOnNativeLogIsKept_andLinearComplexStillStoresCycleOne() = runBlocking {
        val repository = newRepository()
        val native = nativeFixture("finalize-explicit", cycle = 2, week1Sessions = 2)
        seedActive(repository, native, cycle = 2)
        repository.finalizeWorkout(
            logOf(
                "finalize-explicit-log", native, native.week1.sessions.first(), native.week1,
                cycle = 2, withInstance = true,
            ),
        )
        assertEquals(2, repository.history.value.single { it.id == "finalize-explicit-log" }.cycleNumber)

        // Un COMPLEX sin instancias de ciclo nunca avanza de ciclo: sigue forzando 1.
        val linear = nativeFixture("finalize-linear", cycle = 1, week1Sessions = 2, native = false)
        seedActive(repository, linear, cycle = 1, native = false)
        repository.finalizeWorkout(
            logOf("finalize-linear-log", linear, linear.week1.sessions.first(), linear.week1, cycle = 2),
        )
        assertEquals(1, repository.history.value.single { it.id == "finalize-linear-log" }.cycleNumber)
    }

    // ─── (1) executedTrainingEvidence consciente del ciclo ───────────────────

    @Test
    fun executedTrainingEvidence_nativeProgramCountsOnlyTheCurrentCycle() = runBlocking {
        val repository = newRepository()
        val fixture = nativeFixture("evidence-native", cycle = 2, week1Sessions = 2)
        repository.addProgram(fixture.program)
        withTimeout(5_000) { repository.programs.first { list -> list.any { it.id == fixture.program.id } } }
        val s1 = fixture.week1.sessions[0]
        val s2 = fixture.week1.sessions[1]
        val s3 = fixture.week2.sessions.single()
        fun evidence() = repository.executedTrainingEvidence(repository.getProgramById(fixture.program.id)!!)

        // Todo lo entrenado en el ciclo 1 NO protege las mismas sesiones del ciclo 2.
        repository.addWorkoutLog(logOf("c1-s1", fixture, s1, fixture.week1, cycle = 1, withInstance = true))
        repository.addWorkoutLog(logOf("c1-s2", fixture, s2, fixture.week1, cycle = 1, withInstance = true))
        assertEquals(emptySet<String>(), evidence().sessionIds)
        assertEquals(emptySet<String>(), evidence().weekIds)

        repository.addWorkoutLog(logOf("c2-s1", fixture, s1, fixture.week1, cycle = 2, withInstance = true))
        assertEquals(setOf(s1.id), evidence().sessionIds)
        assertEquals("la semana necesita sus dos sesiones requeridas del ciclo 2", emptySet<String>(), evidence().weekIds)

        repository.addWorkoutLog(logOf("c2-s2", fixture, s2, fixture.week1, cycle = 2, withInstance = true))
        assertEquals(setOf(s1.id, s2.id), evidence().sessionIds)
        assertEquals(setOf(fixture.week1.id), evidence().weekIds)

        // Un log sin ciclo se sigue contando (nunca se arriesga perder una sesión entrenada).
        repository.addWorkoutLog(logOf("legacy-s3", fixture, s3, fixture.week2, cycle = null))
        assertEquals(setOf(s1.id, s2.id, s3.id), evidence().sessionIds)
        assertEquals(setOf(fixture.week1.id, fixture.week2.id), evidence().weekIds)
    }

    @Test
    fun executedTrainingEvidence_nonNativeProgramKeepsEveryLogOfTheRun() = runBlocking {
        val repository = newRepository()
        val fixture = nativeFixture("evidence-linear", cycle = 2, week1Sessions = 1, native = false)
        repository.addProgram(fixture.program)
        withTimeout(5_000) { repository.programs.first { list -> list.any { it.id == fixture.program.id } } }
        val s1 = fixture.week1.sessions.single()
        repository.addWorkoutLog(logOf("linear-c1-s1", fixture, s1, fixture.week1, cycle = 1))

        val evidence = repository.executedTrainingEvidence(repository.getProgramById(fixture.program.id)!!)
        assertEquals(setOf(s1.id), evidence.sessionIds)
        assertEquals(setOf(fixture.week1.id), evidence.weekIds)
    }

    // ─── (2) clearOngoingWorkout fire-and-forget no tumba el proceso ─────────

    @Test
    fun clearOngoingWorkout_failureIsLoggedNotThrown_keepsSessionAndAllowsRetry() = runBlocking {
        val repository = newRepository()
        val program = Program(id = "clear-failure", name = "Clear failure")
        repository.addProgram(program)
        withTimeout(5_000) { repository.programs.first { list -> list.any { it.id == program.id } } }
        val session = executableSession("clear-failure-session", "Press banca")
        assertEquals(
            StartWorkoutResult.Started,
            repository.startWorkout(OngoingWorkoutState(programId = program.id, session = session, startTime = 5L)),
        )
        val db = repository.databaseForTests()
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_clear_ongoing BEFORE DELETE ON ongoing_workout " +
                "BEGIN SELECT RAISE(ABORT, 'injected clear failure'); END",
        )

        val uncaught = CopyOnWriteArrayList<Throwable>()
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, error -> uncaught += error }
        try {
            // 1) Falla de Room al borrar.
            val failing = repository.launchClearOngoingWorkout()
            assertNotNull(failing)
            withTimeout(10_000) { failing!!.join() }
            // 2) La ejecución cambió antes de que corriera la coroutine (check del descarte).
            val stale = repository.ongoingWorkout.value!!.copy(startTime = 99L)
            val staleJob = repository.launchClearOngoingWorkout(stale)
            assertNotNull(staleJob)
            withTimeout(10_000) { staleJob!!.join() }
            // La variante suspend SIGUE lanzando: el flujo de cancelación necesita la señal.
            assertTrue(runCatching { repository.clearOngoingWorkoutAndFlush(stale) }.isFailure)
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        }

        assertTrue("excepciones sin capturar: $uncaught", uncaught.isEmpty())
        assertEquals(session.id, repository.ongoingWorkout.value?.session?.id)
        assertNotNull(db.stateDao().getOngoingWorkout())

        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_clear_ongoing")
        withTimeout(10_000) { repository.launchClearOngoingWorkout()!!.join() }
        assertNull(repository.ongoingWorkout.value)
        assertNull(db.stateDao().getOngoingWorkout())
        // Sin sesión en curso ni fila corrupta no hay nada que lanzar.
        assertNull(repository.launchClearOngoingWorkout())
    }

    @Test
    fun archiveProgram_withOngoingSession_clearsItWithoutThrowing() = runBlocking {
        val repository = newRepository()
        val program = Program(id = "archive-ongoing", name = "Archive ongoing")
        repository.addProgram(program)
        withTimeout(5_000) { repository.programs.first { list -> list.any { it.id == program.id } } }
        val session = executableSession("archive-ongoing-session", "Press")
        repository.startWorkout(OngoingWorkoutState(programId = program.id, session = session, startTime = 7L))

        repository.archiveProgram(program.id)

        withTimeout(10_000) { repository.ongoingWorkout.first { it == null } }
        assertNull(repository.databaseForTests().stateDao().getOngoingWorkout())
    }

    // ─── (3) orden global de locks ───────────────────────────────────────────

    /** Corre [block] en un hilo daemon: un deadlock falla con timeout en vez de colgar la suite. */
    private fun <T> runWithDeadline(timeoutSeconds: Long, block: suspend () -> T): T {
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "repository-lock-order-test").apply { isDaemon = true }
        }
        try {
            return executor.submit(Callable { runBlocking { block() } }).get(timeoutSeconds, TimeUnit.SECONDS)
        } catch (timeout: TimeoutException) {
            throw AssertionError("Posible deadlock de locks: no terminó en ${timeoutSeconds}s", timeout)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun resolveNativeProgressionProposal_concurrentWithReplaceProgramSafely_neverDeadlocks() {
        val repository = runBlocking { newRepository() }
        val fixture = nativeFixture("lock-order", week1Sessions = 2)
        val identity = NativeProgressionIdentity(
            recipeId = "lock-order-recipe",
            recipeContentVersion = 1,
            recipeDayId = "lock-order-day",
            recipeSlotId = "lock-order-slot",
            configurationId = "bench_press__barbell",
            loadMode = LoadModeV2.LOAD,
            unitMode = UnitModeV2.REPS,
            side = "bilateral",
            execution = "standard",
            slotPurpose = "F",
        )
        fun proposal(id: String) = NativeProgressionProposal(
            proposalId = id,
            kind = NativeProgressionProposalKind.INCREASE_LOAD,
            identity = identity,
            sourceSessionId = fixture.week1.sessions.first().id,
            sourceLogIds = listOf("log-$id"),
            explanation = "Prueba de orden de locks",
            createdAtMs = 1L,
        )
        val proposals = (0 until 10).map { proposal("lock-order-p$it") }
        runBlocking {
            repository.addProgram(fixture.program.copy(nativeProgressionProposals = proposals))
            withTimeout(5_000) { repository.programs.first { list -> list.any { it.id == fixture.program.id } } }
            repository.updateProgramNow(repository.getProgramById(fixture.program.id)!!)
        }

        // Cada par arranca a la vez: resolver (snapshot bajo ongoing + mutateProgramNow) contra
        // reemplazar el plan (Coordinator -> ongoing -> programWrite -> active).
        val results = runWithDeadline(60) {
            coroutineScope {
                proposals.map { item ->
                    async(Dispatchers.Default) {
                        val resolving = async {
                            repository.resolveNativeProgressionProposalNow(
                                fixture.program.id, item.proposalId, accept = false,
                            )
                        }
                        val replacing = async {
                            runCatching { repository.replaceProgramSafely(repository.getProgramById(fixture.program.id)!!) }
                        }
                        resolving.await() to replacing.await().isSuccess
                    }
                }.awaitAll()
            }
        }
        assertEquals(proposals.size, results.size)
        assertTrue("replaceProgramSafely nunca debe fallar aquí: $results", results.all { it.second })

        // Tras la tormenta, el lane sigue vivo y coherente.
        runBlocking {
            val extra = proposal("lock-order-extra")
            assertTrue(
                repository.mutateProgramNow(fixture.program.id) {
                    it.copy(nativeProgressionProposals = it.nativeProgressionProposals + extra)
                },
            )
            assertTrue(repository.resolveNativeProgressionProposalNow(fixture.program.id, extra.proposalId, accept = false))
            val audit = repository.getProgramById(fixture.program.id)!!
                .nativeProgressionAudit.single { it.proposalId == extra.proposalId }
            assertEquals(NativeProgressionResolutionStatus.REJECTED, audit.status)
        }
    }

    @Test
    fun resolveNativeProgressionProposal_whileASessionIsInProgress_retriesAndNeverBlocksTheWorkoutLane() = runBlocking {
        val repository = newRepository()
        val fixture = nativeFixture("resolve-ongoing", week1Sessions = 2)
        val identity = NativeProgressionIdentity(
            recipeId = "resolve-ongoing-recipe", recipeContentVersion = 1, recipeDayId = "day", recipeSlotId = "slot",
            configurationId = "bench_press__barbell", loadMode = LoadModeV2.LOAD, unitMode = UnitModeV2.REPS,
            side = "bilateral", execution = "standard", slotPurpose = "F",
        )
        val proposal = NativeProgressionProposal(
            proposalId = "resolve-ongoing-p", kind = NativeProgressionProposalKind.INCREASE_LOAD, identity = identity,
            sourceSessionId = fixture.week1.sessions.first().id, sourceLogIds = listOf("l"),
            explanation = "x", createdAtMs = 1L,
        )
        repository.addProgram(fixture.program.copy(nativeProgressionProposals = listOf(proposal)))
        withTimeout(5_000) { repository.programs.first { list -> list.any { it.id == fixture.program.id } } }
        repository.startWorkout(
            OngoingWorkoutState(
                programId = fixture.program.id,
                session = fixture.week1.sessions.last(),
                startTime = 3L,
            ),
        )
        // Mientras resuelve, el carril de escritura de la sesión sigue respondiendo.
        val resolving = async(Dispatchers.Default) {
            repository.resolveNativeProgressionProposalNow(fixture.program.id, proposal.proposalId, accept = false)
        }
        repeat(20) { index ->
            assertEquals(
                WorkoutPersistResult.Ok,
                withTimeout(10_000) { repository.updateOngoingWorkoutAndFlush { it.copy(activeSetIndex = index) } },
            )
            delay(1)
        }
        assertEquals(19, repository.ongoingWorkout.value?.activeSetIndex)
        // Una escritura de sesión en vuelo puede hacer que el primer intento ceda (devuelve false
        // sin aplicar nada, reintentable); en reposo el segundo intento siempre se aplica.
        val firstAttempt = withTimeout(20_000) { resolving.await() }
        val applied = firstAttempt ||
            repository.resolveNativeProgressionProposalNow(fixture.program.id, proposal.proposalId, accept = false)
        assertTrue(applied)
        val audit = repository.getProgramById(fixture.program.id)!!.nativeProgressionAudit
            .filter { it.proposalId == proposal.proposalId }
        assertEquals("la propuesta queda resuelta exactamente una vez", 1, audit.size)
        assertEquals(NativeProgressionResolutionStatus.REJECTED, audit.single().status)
    }

    // ─── (4) F5: flush dentro de ongoingWorkoutMutex ─────────────────────────

    @Test
    fun flushPendingWrites_concurrentWithFinalize_keepsAdvancedProgramAndCursorDurable() = runBlocking {
        val repository = newRepository()
        repeat(10) { round ->
            val fixture = nativeFixture("flush-finalize-$round")
            seedActive(repository, fixture)
            val session = fixture.week1.sessions.single()
            val flushing = async(Dispatchers.Default) { repeat(6) { repository.flushPendingWrites() } }
            val finalizing = async(Dispatchers.Default) {
                repository.finalizeWorkout(
                    logOf("flush-finalize-log-$round", fixture, session, fixture.week1, runId = null)
                        .copy(date = "2026-09-30T10:0$round:00Z"),
                )
            }
            withTimeout(30_000) { listOf(flushing, finalizing).awaitAll() }

            val room = repository.databaseForTests()
            val week2Instance = ProgramProgressEngine.instanceIdFor(1, fixture.week2.id)
            val persisted = room.programDao().getById(fixture.program.id)?.toProgram()
            assertEquals("ronda $round: el programa avanzado llega a Room", fixture.week2.id, persisted?.runState?.weekId)
            assertEquals(week2Instance, persisted?.runState?.weekInstanceId)
            assertEquals(
                "ronda $round: el cursor activo avanzado llega a Room",
                week2Instance,
                room.stateDao().getActiveProgram()?.toActiveProgramState()?.currentWeekInstanceId,
            )
            assertEquals("ronda $round: caché == Room", repository.getProgramById(fixture.program.id), persisted)
            assertNotNull(room.workoutLogDao().getById("flush-finalize-log-$round"))
        }
    }

    // ─── (4) F6: la reconciliación del cursor se escribe con el programa ─────

    @Test
    fun updateProgramNow_reconcilesNativeCursorWithLogsBeforeWritingSoRoomAndCacheAgree() = runBlocking {
        val repository = newRepository()
        val fixture = nativeFixture("reconcile-cursor")
        seedActive(repository, fixture)
        val session = fixture.week1.sessions.single()
        // La semana 1 ya está entrenada pero el cursor del run sigue (obsoleto) en ella.
        repository.addWorkoutLog(
            logOf("reconcile-log", fixture, session, fixture.week1, cycle = 1, withInstance = true),
        )

        repository.updateProgramNow(repository.getProgramById(fixture.program.id)!!.copy(name = "Renombrado"))

        val memory = repository.getProgramById(fixture.program.id)!!
        assertEquals(fixture.week2.id, memory.runState?.weekId)
        assertEquals(ProgramProgressEngine.instanceIdFor(1, fixture.week2.id), memory.runState?.weekInstanceId)
        val persisted = repository.databaseForTests().programDao().getById(fixture.program.id)?.toProgram()
        assertEquals("Room guarda el MISMO programa reconciliado que publica la caché", memory, persisted)
        assertEquals("Renombrado", persisted?.name)
    }

    // ─── (4) F7: loadFromDb persiste lo que publica ──────────────────────────

    @Test
    fun loadFromDb_persistsTheNormalizedProgramSoRoomConvergesWithTheCache() = runBlocking {
        val repository = newRepository()
        val fixture = nativeFixture("load-converge")
        // Forma antigua: el cursor del run apunta a la plantilla, no a la instancia de ciclo.
        val stale = fixture.program.copy(
            runState = fixture.program.runState!!.copy(weekInstanceId = fixture.week1.id),
        )
        val room = repository.databaseForTests()
        room.programDao().upsert(stale.toEntity())

        repository.refreshData()

        withTimeout(10_000) { repository.programs.first { list -> list.any { it.id == stale.id } } }
        val published = repository.getProgramById(stale.id)!!
        assertEquals(
            "la caché publica el cursor reparado",
            ProgramProgressEngine.instanceIdFor(1, fixture.week1.id),
            published.runState?.weekInstanceId,
        )
        withTimeout(10_000) {
            while (room.programDao().getById(stale.id)?.toProgram() != published) delay(20)
        }
        assertEquals(published, room.programDao().getById(stale.id)?.toProgram())
    }

    // ─── (4) F11: la versión reservada se entrega, no se predice ─────────────

    @Test
    fun mutateProgramNowReportingVersion_reportsExactlyTheVersionPredictedUnderTheMonitor() = runBlocking {
        val repository = newRepository()
        val fixture = nativeFixture("reported-version")
        seedActive(repository, fixture)
        val flushing = async(Dispatchers.Default) { repeat(30) { repository.flushPendingWrites() } }

        repeat(15) { index ->
            var predicted = -1L
            var reported = -2L
            repository.mutateProgramNowReportingVersion(
                programId = fixture.program.id,
                onVersionReserved = { reported = it },
            ) { current ->
                predicted = repository.nextProgramWriteVersionForEditorRecovery()
                current.copy(name = "Renombrado $index")
            }
            // El resultado puede ser un conflicto con el flush; lo que no puede fallar es
            // que la versión de recuperación coincida con la realmente reservada.
            assertEquals("iteración $index", predicted, reported)
        }
        withTimeout(30_000) { flushing.await() }
    }
}
