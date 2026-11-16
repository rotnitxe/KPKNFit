package com.example.kpkn.screens.programdetail

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.exercises.initializeExerciseDatabase
import com.example.kpkn.data.models.ActiveProgramState
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramEndMode
import com.example.kpkn.data.models.ProgramRunState
import com.example.kpkn.data.models.ProgramRunStatus
import com.example.kpkn.data.models.ProgramSchedulePlan
import com.example.kpkn.data.models.ProgramStatus
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.training.ProgramProgressEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Req. 5: closes every week in two blocks over two cycles, then retries after Room reopens. */
@RunWith(AndroidJUnit4::class)
class ProgramRecurringMultiBlockCycleDeviceTest {
    private val suffix = System.nanoTime().toString().takeLast(8)
    private val programId = "qa-cycle-$suffix"
    private val runId = "qa-run-$suffix"
    private val databaseName = "$programId.db"
    private val macrocycleId = "$programId-macro"

    private lateinit var context: android.content.Context
    private lateinit var repository: ProgramRepository
    private lateinit var program: Program
    private lateinit var orderedWeeks: List<ProgramWeek>
    private lateinit var lastLog: WorkoutLog

    @Before
    fun setUp() {
        runBlocking {
            context = InstrumentationRegistry.getInstrumentation().targetContext
            assertEquals("La prueba solo puede usar KPKN Dev", "com.example.kpkn.dbg", context.packageName)
            initializeExerciseDatabase(context)
            ProgramRepository.closeInstance()
            KpknDatabase.closeInstance()
            context.deleteDatabase(databaseName)
            repository = ProgramRepository.initForTestsWithDatabaseFile(context, databaseName)
            withTimeout(30_000) { repository.isReady.first { it } }

            program = createProgram()
            orderedWeeks = program.macrocycles.flatMap { macro ->
                macro.blocks.flatMap { block -> block.mesocycles.flatMap { it.weeks } }
            }
            assertEquals("La fixture recorre los dos bloques", 4, orderedWeeks.size)
            assertTrue(repository.addProgramNow(program).isSuccess)
            repository.updateActiveProgramState(activeStateFor(cycle = 1, week = orderedWeeks.first()))
            repository.flushPendingWrites()
            withTimeout(15_000) {
                repository.activeProgramState.first { it?.programId == programId && it.currentCycleNumber == 1 }
            }
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            if (!::repository.isInitialized) return@runBlocking
            runCatching { repository.flushPendingWrites() }
            ProgramRepository.closeInstance()
            KpknDatabase.closeInstance()
            if (::context.isInitialized) context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun twoBlockRoutineClosesTwoCyclesAndRetryAfterRoomReopenDoesNotAdvanceAgain() = runBlocking {
        var dateIndex = 1
        for (cycle in 1..2) {
            for (week in orderedWeeks) {
                for (session in week.sessions) {
                    val log = WorkoutLog(
                        id = "$programId-c$cycle-${session.id}",
                        programId = programId,
                        sessionId = session.id,
                        sessionName = session.name,
                        date = "2026-11-%02dT10:00:00Z".format(dateIndex++),
                        durationMinutes = 35,
                        programRunId = runId,
                        cycleNumber = cycle,
                        weekId = week.id,
                        weekInstanceId = ProgramProgressEngine.instanceIdFor(cycle, week.id),
                    )
                    repository.finalizeWorkout(log)
                    lastLog = log
                }
            }

            val closedCycle = cycle + 1
            val persisted = requireNotNull(repository.getProgramById(programId))
            assertEquals("El cierre debe conservar un único run", runId, persisted.runState?.runId)
            assertEquals("Un programa recurrente permanece activo", ProgramRunStatus.ACTIVE, persisted.runState?.status)
            assertEquals("Debe avanzar exactamente un ciclo", closedCycle, persisted.runState?.cycleNumber)
            assertEquals(
                "La ocurrencia del siguiente ciclo empieza por la primera semana",
                ProgramProgressEngine.instanceIdFor(closedCycle, orderedWeeks.first().id),
                persisted.runState?.weekInstanceId,
            )
            assertEquals(setOf(2, 4), persisted.schedulePlan?.trainingDays)
            assertEquals(ProgramEndMode.REPEATING, persisted.programEndMode)
            assertEquals(closedCycle, repository.activeProgramState.value?.currentCycleNumber)
            assertEquals(
                ProgramProgressEngine.instanceIdFor(closedCycle, orderedWeeks.first().id),
                repository.activeProgramState.value?.currentWeekInstanceId,
            )
            assertEquals(cycle * orderedWeeks.sumOf { it.sessions.size }, repository.getLogsForProgram(programId).size)
        }

        repository.flushPendingWrites()
        ProgramRepository.closeInstance()
        KpknDatabase.closeInstance()
        repository = ProgramRepository.initForTestsWithDatabaseFile(context, databaseName)
        withTimeout(30_000) { repository.isReady.first { it } }

        val reopened = requireNotNull(repository.getProgramById(programId))
        assertEquals(3, reopened.runState?.cycleNumber)
        assertEquals(runId, reopened.runState?.runId)
        assertEquals(ProgramRunStatus.ACTIVE, reopened.runState?.status)
        assertEquals(
            ProgramProgressEngine.instanceIdFor(3, orderedWeeks.first().id),
            reopened.runState?.weekInstanceId,
        )
        assertEquals(setOf(2, 4), reopened.schedulePlan?.trainingDays)
        val beforeRetryCount = repository.getLogsForProgram(programId).size
        assertEquals(16, beforeRetryCount)

        repository.finalizeWorkout(lastLog)

        val afterRetry = requireNotNull(repository.getProgramById(programId))
        assertEquals("El reintento de un recibo durable no vuelve a cerrar el ciclo", 3, afterRetry.runState?.cycleNumber)
        assertEquals(runId, afterRetry.runState?.runId)
        assertEquals(beforeRetryCount, repository.getLogsForProgram(programId).size)
        assertNotNull(repository.activeProgramState.value)
    }

    private fun createProgram(): Program {
        val blocks = (1..2).map { blockNumber ->
            val blockId = "$programId-block-$blockNumber"
            val mesocycleId = "$programId-meso-$blockNumber"
            val weeks = (1..2).map { weekNumber ->
                val weekId = "$programId-b${blockNumber}-w$weekNumber"
                ProgramWeek(
                    id = weekId,
                    name = "Bloque $blockNumber · Semana $weekNumber",
                    sessions = (1..2).map { sessionNumber ->
                        val sessionId = "$weekId-s$sessionNumber"
                        Session(
                            id = sessionId,
                            name = "Día $sessionNumber",
                            dayOfWeek = if (sessionNumber == 1) 2 else 4,
                            isMainSession = true,
                            exercises = listOf(
                                Exercise(
                                    id = "$sessionId-exercise",
                                    name = "Sentadilla",
                                    sets = listOf(ExerciseSet(id = "$sessionId-set", targetReps = 5, targetRPE = 7.0)),
                                ),
                            ),
                        )
                    },
                )
            }
            Block(
                id = blockId,
                name = "Bloque $blockNumber",
                mesocycles = listOf(Mesocycle(id = mesocycleId, name = "Mesociclo $blockNumber", weeks = weeks)),
            )
        }
        val firstWeek = blocks.first().mesocycles.first().weeks.first()
        return Program(
            id = programId,
            name = "Rutina recurrente QA",
            structure = ProgramStructure.COMPLEX,
            macrocycles = listOf(Macrocycle(id = macrocycleId, name = "Macro QA", blocks = blocks)),
            startDay = 2,
            programEndMode = ProgramEndMode.REPEATING,
            schedulePlan = ProgramSchedulePlan(weekStartDay = 1, trainingDays = setOf(2, 4)),
            runState = ProgramRunState(
                runId = runId,
                cycleNumber = 1,
                weekInstanceId = ProgramProgressEngine.instanceIdFor(1, firstWeek.id),
                weekId = firstWeek.id,
                macrocycleId = macrocycleId,
                blockId = blocks.first().id,
                mesocycleId = blocks.first().mesocycles.first().id,
                status = ProgramRunStatus.ACTIVE,
            ),
        )
    }

    private fun activeStateFor(cycle: Int, week: ProgramWeek): ActiveProgramState {
        val block = program.macrocycles.flatMap { it.blocks }
            .first { candidate -> candidate.mesocycles.any { meso -> week in meso.weeks } }
        val mesocycle = block.mesocycles.first { week in it.weeks }
        val instanceId = ProgramProgressEngine.instanceIdFor(cycle, week.id)
        return ActiveProgramState(
            programId = programId,
            status = ProgramStatus.ACTIVE,
            currentWeekId = instanceId,
            currentWeekInstanceId = instanceId,
            currentCycleNumber = cycle,
            currentMacrocycleId = macrocycleId,
            currentBlockId = block.id,
            currentMesocycleId = mesocycle.id,
            programRunId = runId,
        )
    }
}
