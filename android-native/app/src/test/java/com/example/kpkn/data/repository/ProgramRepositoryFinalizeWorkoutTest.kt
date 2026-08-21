package com.example.kpkn.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toActiveProgramState
import com.example.kpkn.data.db.toProgram
import com.example.kpkn.data.models.ActiveProgramState
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramRunState
import com.example.kpkn.data.models.ProgramRunStatus
import com.example.kpkn.data.models.ProgramStatus
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.OngoingWorkoutState
import com.example.kpkn.data.models.WorkoutLog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real Room/Robolectric seam for COMPLEX cursor progress and read-back. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class ProgramRepositoryFinalizeWorkoutTest {

    private val mainDispatcher = UnconfinedTestDispatcher()

    @org.junit.Before
    fun setUp() {
        // Repository bootstrap publishes readiness on Dispatchers.Main. Robolectric's
        // paused looper otherwise leaves the test waiting forever before Room assertions.
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        ProgramRepository.closeInstance()
        KpknDatabase.closeInstance()
        Dispatchers.resetMain()
    }

    @Test
    fun complexFinalizeRequiresAllWeekSessions_thenCompletesAndPersists() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = ProgramRepository.initForTests(context)
        withTimeout(10_000) { repository.isReady.first { it } }
        repository.resetAllStateSync()

        val sessionA = executableSession("session-a", "Sentadilla")
        val sessionB = executableSession("session-b", "Banca")
        val week = ProgramWeek(id = "week-1", name = "Semana 1", sessions = listOf(sessionA, sessionB))
        val meso = Mesocycle(id = "meso-1", name = "Meso 1", weeks = listOf(week))
        val block = Block(id = "block-1", name = "Base", mesocycles = listOf(meso))
        val macro = Macrocycle(id = "macro-1", name = "Macro", blocks = listOf(block))
        val run = ProgramRunState(
            runId = "run-1",
            weekInstanceId = week.id,
            weekId = week.id,
            macrocycleId = macro.id,
            blockId = block.id,
            mesocycleId = meso.id,
        )
        val program = Program(
            id = "complex-finalize",
            name = "Complex finalize",
            structure = ProgramStructure.COMPLEX,
            macrocycles = listOf(macro),
            runState = run,
        )
        repository.addProgram(program)
        withTimeout(5_000) { repository.programs.first { it.any { item -> item.id == program.id } } }
        repository.updateActiveProgramState(
            ActiveProgramState(
                programId = program.id,
                status = ProgramStatus.ACTIVE,
                currentWeekId = week.id,
                currentWeekInstanceId = week.id,
                currentMacrocycleId = macro.id,
                currentBlockId = block.id,
                currentMesocycleId = meso.id,
                programRunId = run.runId,
            ),
        )
        repository.updateProgramNow(program)

        repository.finalizeWorkout(log(program.id, sessionA.id, "a", week.id, run.runId))
        val afterFirst = repository.getProgramById(program.id) ?: error("program missing after first")
        assertEquals(ProgramRunStatus.ACTIVE, afterFirst.runState?.status)
        assertEquals(week.id, afterFirst.runState?.weekId)
        assertTrue(afterFirst.runState?.completedSessionIds?.contains(sessionA.id) == true)
        assertEquals(week.id, repository.activeProgramState.value?.currentWeekId)

        repository.finalizeWorkout(log(program.id, sessionB.id, "b", week.id, run.runId))
        val completed = repository.getProgramById(program.id) ?: error("program missing after second")
        assertEquals(ProgramRunStatus.COMPLETED, completed.runState?.status)
        assertTrue(completed.runState?.weekId == null)
        assertEquals(ProgramStatus.COMPLETED, repository.activeProgramState.value?.status)

        val room = repository.databaseForTests()
        val persisted = room.programDao().getById(program.id)?.toProgram()
            ?: error("Room program read-back missing")
        assertEquals(ProgramRunStatus.COMPLETED, persisted.runState?.status)
        assertTrue(persisted.runState?.weekId == null)
        val persistedActive = room.stateDao().getActiveProgram()?.toActiveProgramState()
            ?: error("Room active state read-back missing")
        assertEquals(ProgramStatus.COMPLETED, persistedActive.status)
        assertFalse(repository.history.value.none { it.sessionId == sessionB.id })
    }

    @Test
    fun complexFinalize_movesCursorToNextWeek_andRoomReadBackSurvivesRestart() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = ProgramRepository.initForTests(context)
        withTimeout(10_000) { repository.isReady.first { it } }
        repository.resetAllStateSync()

        val week1 = ProgramWeek(
            id = "complex-w1",
            name = "Semana 1",
            sessions = listOf(executableSession("complex-s1", "Sentadilla")),
        )
        val week2 = ProgramWeek(
            id = "complex-w2",
            name = "Semana 2",
            sessions = listOf(executableSession("complex-s2", "Banca")),
        )
        val meso = Mesocycle(id = "complex-m", name = "Meso", weeks = listOf(week1, week2))
        val block = Block(id = "complex-b", name = "Bloque", mesocycles = listOf(meso))
        val macro = Macrocycle(id = "complex-mc", name = "Macro", blocks = listOf(block))
        val run = ProgramRunState(
            runId = "complex-run",
            weekId = week1.id,
            weekInstanceId = week1.id,
            macrocycleId = macro.id,
            blockId = block.id,
            mesocycleId = meso.id,
        )
        val program = Program(
            id = "complex-next-week",
            name = "Cursor readback",
            structure = ProgramStructure.COMPLEX,
            macrocycles = listOf(macro),
            runState = run,
        )
        repository.addProgram(program)
        withTimeout(5_000) { repository.programs.first { it.any { item -> item.id == program.id } } }
        repository.updateActiveProgramState(
            ActiveProgramState(
                programId = program.id,
                status = ProgramStatus.ACTIVE,
                currentWeekId = "stale-week",
                currentWeekInstanceId = "stale-week",
                currentBlockId = "stale-block",
                programRunId = "stale-run",
            ),
        )
        // The run cursor in the program blob must win over the stale active row.
        repository.updateProgramNow(program)

        repository.finalizeWorkout(log(program.id, week1.sessions.single().id, "next", week1.id, run.runId))

        val after = repository.getProgramById(program.id) ?: error("program missing after transition")
        assertEquals(ProgramRunStatus.ACTIVE, after.runState?.status)
        assertEquals(week2.id, after.runState?.weekId)
        assertEquals(week2.id, after.runState?.weekInstanceId)
        assertEquals(week2.id, repository.activeProgramState.value?.currentWeekId)
        assertEquals(block.id, repository.activeProgramState.value?.currentBlockId)

        val room = repository.databaseForTests()
        assertEquals(week2.id, room.programDao().getById(program.id)?.toProgram()?.runState?.weekId)
        assertEquals(week2.id, room.stateDao().getActiveProgram()?.toActiveProgramState()?.currentWeekId)

    }

    @Test
    fun active_state_runtime_commands_share_latest_write_lane_and_clear_is_tombstone() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = ProgramRepository.initForTests(context)
        withTimeout(10_000) { repository.isReady.first { it } }
        repository.resetAllStateSync()

        val program = Program(id = "active-race", name = "Active race")
        repository.addProgram(program)
        withTimeout(5_000) { repository.programs.first { it.any { item -> item.id == program.id } } }
        val initial = ActiveProgramState(
            programId = program.id,
            status = ProgramStatus.ACTIVE,
            currentWeekId = "week-old",
        )
        repository.updateActiveProgramState(initial)
        val room = repository.databaseForTests()
        withTimeout(5_000) {
            while (room.stateDao().getActiveProgram()?.toActiveProgramState()?.currentWeekId != "week-old") {
                delay(10)
            }
        }
        // This interleaves an older upsert with the newer tombstone and the
        // no-op runtime commands that used to bypass versioning.
        repository.clearActiveProgram()
        repository.pauseProgram()
        repository.resumeProgram()
        repository.advanceWeek("week-stale")

        withTimeout(5_000) {
            while (room.stateDao().getActiveProgram() != null) delay(10)
        }
        assertNull(room.stateDao().getActiveProgram())

        repository.updateActiveProgramState(initial)
        repository.pauseProgram()
        repository.resumeProgram()
        repository.advanceWeek("week-new")
        val persisted = withTimeout(5_000) {
            while (room.stateDao().getActiveProgram()?.toActiveProgramState()?.currentWeekId != "week-new") delay(10)
            room.stateDao().getActiveProgram()?.toActiveProgramState()
        }
        assertEquals("week-new", persisted?.currentWeekId)
        assertEquals(ProgramStatus.ACTIVE, persisted?.status)
    }

    @Test
    fun finalize_interleaved_with_active_update_and_clear_completes_without_deadlock_and_tombstone_wins() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = ProgramRepository.initForTests(context)
        withTimeout(10_000) { repository.isReady.first { it } }
        repository.resetAllStateSync()

        val session = executableSession("race-session", "Sentadilla")
        val week = ProgramWeek(id = "race-week", name = "Semana", sessions = listOf(session))
        val meso = Mesocycle(id = "race-meso", name = "Meso", weeks = listOf(week))
        val block = Block(id = "race-block", name = "Bloque", mesocycles = listOf(meso))
        val macro = Macrocycle(id = "race-macro", name = "Macro", blocks = listOf(block))
        val run = ProgramRunState(
            runId = "race-run",
            weekId = week.id,
            weekInstanceId = week.id,
            macrocycleId = macro.id,
            blockId = block.id,
            mesocycleId = meso.id,
        )
        val program = Program(
            id = "finalize-clear-race",
            name = "Finalización concurrente",
            structure = ProgramStructure.COMPLEX,
            macrocycles = listOf(macro),
            runState = run,
        )
        repository.addProgram(program)
        withTimeout(5_000) { repository.programs.first { it.any { item -> item.id == program.id } } }
        val initial = ActiveProgramState(
            programId = program.id,
            status = ProgramStatus.ACTIVE,
            currentWeekId = week.id,
            currentWeekInstanceId = week.id,
            currentMacrocycleId = macro.id,
            currentBlockId = block.id,
            currentMesocycleId = meso.id,
            programRunId = run.runId,
        )
        repository.updateActiveProgramState(initial)
        repository.updateProgramNow(program)

        val finalizer = async(Dispatchers.Default) {
            repository.finalizeWorkout(log(program.id, session.id, "race", week.id, run.runId))
        }
        repeat(12) { index ->
            repository.updateActiveProgramState(initial.copy(currentWeekId = "race-$index"))
            repository.clearActiveProgram()
            delay(1)
        }
        withTimeout(10_000) { finalizer.await() }

        // Make the intended final ordering explicit: a clear issued after the
        // transaction must remain a durable tombstone, never an old upsert.
        repository.clearActiveProgram()
        val room = repository.databaseForTests()
        withTimeout(5_000) {
            while (room.stateDao().getActiveProgram() != null) delay(10)
        }
        assertNull(room.stateDao().getActiveProgram())
        assertEquals(
            ProgramRunStatus.COMPLETED,
            room.programDao().getById(program.id)?.toProgram()?.runState?.status,
        )
    }

    @Test
    fun flush_pending_writes_respects_program_update_delete_and_active_tombstone_lanes() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = ProgramRepository.initForTests(context)
        withTimeout(10_000) { repository.isReady.first { it } }
        repository.resetAllStateSync()

        val original = Program(id = "flush-race", name = "Original")
        repository.addProgram(original)
        withTimeout(5_000) { repository.programs.first { it.any { item -> item.id == original.id } } }

        repository.updateActiveProgramState(
            ActiveProgramState(
                programId = original.id,
                status = ProgramStatus.ACTIVE,
                currentWeekId = "flush-week-old",
            ),
        )
        val flushing = async(Dispatchers.Default) {
            repeat(6) { repository.flushPendingWrites() }
        }
        repository.updateProgram(original.copy(name = "Updated before flush settles"))
        repository.updateActiveProgramState(
            ActiveProgramState(
                programId = original.id,
                status = ProgramStatus.ACTIVE,
                currentWeekId = "flush-week-new",
            ),
        )
        repository.clearActiveProgram()
        withTimeout(10_000) { flushing.await() }

        val room = repository.databaseForTests()
        withTimeout(5_000) {
            while (room.programDao().getById(original.id)?.toProgram()?.name != "Updated before flush settles") {
                delay(10)
            }
        }
        assertEquals("Updated before flush settles", room.programDao().getById(original.id)?.toProgram()?.name)

        repository.deleteProgram(original.id)
        repository.clearActiveProgram()
        withTimeout(5_000) {
            while (room.programDao().getById(original.id) != null || room.stateDao().getActiveProgram() != null) {
                delay(10)
            }
        }
        assertNull(room.programDao().getById(original.id))
        assertNull(room.stateDao().getActiveProgram())
    }

    @Test
    fun flush_clear_ongoing_uses_locked_current_state_and_does_not_resurrect_snapshot() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = ProgramRepository.initForTests(context)
        withTimeout(10_000) { repository.isReady.first { it } }
        repository.resetAllStateSync()

        val program = Program(id = "ongoing-flush-race", name = "Ongoing flush race")
        repository.addProgram(program)
        withTimeout(5_000) { repository.programs.first { it.any { item -> item.id == program.id } } }
        repository.startWorkout(
            OngoingWorkoutState(
                programId = program.id,
                session = executableSession("ongoing-session", "Press banca"),
                startTime = 1L,
            ),
        )
        val flushing = async(Dispatchers.Default) {
            repeat(8) { repository.flushPendingWrites() }
        }
        repository.clearOngoingWorkout()
        withTimeout(10_000) { flushing.await() }
        repository.clearOngoingWorkout()

        val room = repository.databaseForTests()
        withTimeout(5_000) {
            while (room.stateDao().getOngoingWorkout() != null) delay(10)
        }
        assertNull(room.stateDao().getOngoingWorkout())
    }

    @Test
    fun delete_program_serializes_ongoing_clear_against_in_flight_update() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = ProgramRepository.initForTests(context)
        withTimeout(10_000) { repository.isReady.first { it } }
        repository.resetAllStateSync()

        val program = Program(id = "delete-ongoing-race", name = "Delete ongoing race")
        repository.addProgram(program)
        withTimeout(5_000) { repository.programs.first { it.any { item -> item.id == program.id } } }
        repository.startWorkout(
            OngoingWorkoutState(
                programId = program.id,
                session = executableSession("delete-race-session", "Press banca"),
                startTime = 1L,
            ),
        )

        val updating = async(Dispatchers.Default) {
            repeat(24) { index ->
                repository.updateOngoingWorkout { it.copy(activeSetIndex = index) }
            }
        }
        repository.deleteProgram(program.id)
        withTimeout(10_000) { updating.await() }

        val room = repository.databaseForTests()
        withTimeout(5_000) {
            while (room.stateDao().getOngoingWorkout() != null) delay(10)
        }
        assertNull(room.stateDao().getOngoingWorkout())
    }

    @Test
    fun stale_start_after_delete_is_rejected_and_room_stays_clear() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = ProgramRepository.initForTests(context)
        withTimeout(10_000) { repository.isReady.first { it } }
        repository.resetAllStateSync()

        val program = Program(id = "stale-start-after-delete", name = "Stale start")
        repository.addProgram(program)
        withTimeout(5_000) { repository.programs.first { it.any { item -> item.id == program.id } } }

        val allowStaleStart = kotlinx.coroutines.CompletableDeferred<Unit>()
        val staleStart = async(Dispatchers.Default) {
            allowStaleStart.await()
            repository.startWorkout(
                OngoingWorkoutState(
                    programId = program.id,
                    session = executableSession("stale-start-session", "Press banca"),
                    startTime = 1L,
                ),
            )
        }

        repository.deleteProgram(program.id)
        allowStaleStart.complete(Unit)
        withTimeout(10_000) { staleStart.await() }

        val room = repository.databaseForTests()
        withTimeout(5_000) {
            while (room.stateDao().getOngoingWorkout() != null) delay(10)
        }
        assertNull(room.stateDao().getOngoingWorkout())
    }

    private fun log(
        programId: String,
        sessionId: String,
        suffix: String,
        weekId: String,
        runId: String,
    ) = WorkoutLog(
        id = "log-$suffix",
        programId = programId,
        sessionId = sessionId,
        sessionName = sessionId,
        date = if (suffix == "a") "2026-08-21T10:00:00Z" else "2026-08-21T10:01:00Z",
        durationMinutes = 45,
        weekId = weekId,
        weekInstanceId = weekId,
        programRunId = runId,
    )

    private fun executableSession(id: String, name: String): Session = Session(
        id = id,
        name = name,
        exercises = listOf(
            Exercise(
                id = "$id-exercise",
                name = name,
                sets = listOf(
                    ExerciseSet(
                        id = "$id-set",
                        targetReps = 5,
                        targetRPE = 7.0,
                    ),
                ),
            ),
        ),
    )
}
