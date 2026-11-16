package com.example.kpkn.screens.programdetail

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.exercises.initializeExerciseDatabase
import com.example.kpkn.data.models.ActiveProgramState
import com.example.kpkn.data.models.Block
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
import com.example.kpkn.ui.theme.AppThemeMode
import com.example.kpkn.ui.theme.KPKNTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Req. 5: real ProgramDetail resume action, legacy run identity/history, and Room process-restart recovery. */
@RunWith(AndroidJUnit4::class)
class ProgramDetailRecurringResumeDeviceTest {
    private val arguments = InstrumentationRegistry.getArguments()
    private val mode = arguments.getString("qaRecurrenceMode").orEmpty()
    private val fixtureId = arguments.getString("qaRecurrenceId") ?: System.nanoTime().toString()
    private val targetId = "qa-recurring-$fixtureId"
    private val otherId = "qa-active-$fixtureId"
    private val databaseName = "qa-recurrence-$fixtureId.db"
    private val targetRunId = "qa-run-$fixtureId"
    private val otherName = "Fuerza activa QA"
    private val targetName = "Rutina legacy QA"

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var context: android.content.Context
    private lateinit var repository: ProgramRepository

    @org.junit.Before
    fun setUp() {
        runBlocking {
            assertTrue("Ejecuta con qaRecurrenceMode=seed o resume", mode == "seed" || mode == "resume")
            context = InstrumentationRegistry.getInstrumentation().targetContext
            assertEquals("La fixture solo puede usar KPKN Dev", "com.example.kpkn.dbg", context.packageName)
            initializeExerciseDatabase(context)
            ProgramRepository.closeInstance()
            KpknDatabase.closeInstance()
            if (mode != "resume") context.deleteDatabase(databaseName)
            repository = ProgramRepository.initForTestsWithDatabaseFile(context, databaseName)
            withTimeout(30_000) { repository.isReady.first { it } }

            if (mode == "seed") {
                val other = fixtureProgram(otherId, otherName, "other-run-$fixtureId", ProgramRunStatus.ACTIVE)
                assertTrue(repository.addProgramNow(other).isSuccess)
                repository.startProgram(otherId)
                withTimeout(15_000) {
                    repository.activeProgramState.first { it?.programId == otherId && it.status == ProgramStatus.ACTIVE }
                }

                // Null end mode plus no terminal signal is the legacy repeating case.
                val target = fixtureProgram(targetId, targetName, targetRunId, ProgramRunStatus.COMPLETED)
                    .copy(programEndMode = null)
                assertTrue(repository.addProgramNow(target).isSuccess)
                repository.addWorkoutLog(
                    WorkoutLog(
                        id = "qa-history-$fixtureId",
                        programId = targetId,
                        sessionId = "$targetId-session",
                        sessionName = "Día A",
                        date = "2026-11-15T12:00:00Z",
                        durationMinutes = 40,
                        programRunId = targetRunId,
                        cycleNumber = 1,
                        weekId = "$targetId-week",
                    ),
                )
                repository.updateSettings {
                    it.copy(onboardingCompleted = true, tourSeen = it.tourSeen + setOf("tour-home", "tour-training"))
                }
                repository.flushPendingWrites()
                withTimeout(15_000) {
                    repository.history.first { logs -> logs.any { it.id == "qa-history-$fixtureId" } }
                }
            } else {
                val target = requireNotNull(repository.getProgramById(targetId)) {
                    "No sobrevivió el programa sembrado antes del reinicio"
                }
                assertEquals(ProgramRunStatus.ACTIVE, target.runState?.status)
                assertEquals(2, target.runState?.cycleNumber)
                assertEquals(targetRunId, target.runState?.runId)
                assertEquals(targetId, repository.activeProgramState.value?.programId)
                assertEquals(1, repository.getLogsForProgram(targetId).size)
            }
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            runCatching { compose.setContent {} }
            runCatching { compose.activityRule.scenario.onActivity { it.viewModelStore.clear() } }
            if (!::repository.isInitialized) return@runBlocking
            if (mode == "seed") {
                repository.flushPendingWrites()
                return@runBlocking
            }
            ProgramRepository.closeInstance()
            KpknDatabase.closeInstance()
            if (::context.isInitialized) context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun resumeLegacyRoutineFromProgramDetailConfirmsSwitchAndPreservesRunAndHistory() = runBlocking {
        assertEquals("La prueba de interacción debe usar la fase seed", "seed", mode)
        val viewModel = ProgramDetailViewModel(targetId)
        compose.activityRule.scenario.onActivity { activity ->
            activity.viewModelStore.put("qa-recurring-$fixtureId", viewModel)
        }
        compose.setContent {
            KPKNTheme(themeMode = AppThemeMode.HIGH_CONTRAST) {
                ProgramDetailScreen(
                    programId = targetId,
                    onBack = {},
                    onStartWorkout = { _, _ -> },
                    onEditSession = {},
                    onCreateSession = { _, _, _, _, _ -> },
                    viewModel = viewModel,
                )
            }
        }

        compose.waitUntil(20_000) { viewModel.program.value?.id == targetId }
        withTimeout(20_000) { viewModel.activeOtherProgramName.first { it == otherName } }
        compose.onNodeWithText(targetName, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Reanudar rutina", useUnmergedTree = true).assertIsDisplayed().performClick()
        compose.onNodeWithText("¿Reanudar rutina y cambiar el programa activo?").assertIsDisplayed()
        compose.onNodeWithText("Cancelar").performClick()
        assertEquals(otherId, repository.activeProgramState.value?.programId)
        assertEquals(ProgramRunStatus.COMPLETED, repository.getProgramById(targetId)?.runState?.status)

        compose.onNodeWithText("Reanudar rutina", useUnmergedTree = true).performClick()
        compose.onNodeWithText("¿Reanudar rutina y cambiar el programa activo?").assertIsDisplayed()
        val resumeActions = compose.onAllNodesWithText("Reanudar rutina", useUnmergedTree = true)
        val resumeActionCount = resumeActions.fetchSemanticsNodes().size
        assertTrue("Debe estar el CTA de la ficha y el de confirmación", resumeActionCount >= 2)
        resumeActions[resumeActionCount - 1].performClick()
        compose.waitUntil(20_000) {
            repository.activeProgramState.value?.programId == targetId &&
                repository.getProgramById(targetId)?.runState?.status == ProgramRunStatus.ACTIVE
        }

        repository.flushPendingWrites()
        val resumed = requireNotNull(repository.getProgramById(targetId))
        assertEquals(2, resumed.runState?.cycleNumber)
        assertEquals(targetRunId, resumed.runState?.runId)
        assertEquals(setOf(2, 4, 6), resumed.schedulePlan?.trainingDays)
        assertEquals(ProgramStatus.ACTIVE, repository.activeProgramState.value?.status)
        assertEquals(targetId, repository.activeProgramState.value?.programId)
        assertEquals(1, repository.getLogsForProgram(targetId).size)
        assertTrue(viewModel.activeOtherProgramName.value == null)
    }

    @Test
    fun reopenedRoomDoesNotAdvanceTheResumedLegacyRoutineTwice() {
        assertEquals("La prueba de recuperación debe usar la fase resume", "resume", mode)
        val resumed = requireNotNull(repository.getProgramById(targetId))
        assertEquals(ProgramRunStatus.ACTIVE, resumed.runState?.status)
        assertEquals(2, resumed.runState?.cycleNumber)
        assertEquals(targetRunId, resumed.runState?.runId)
        assertEquals(setOf(2, 4, 6), resumed.schedulePlan?.trainingDays)
        assertEquals(targetId, repository.activeProgramState.value?.programId)
        assertEquals(ProgramStatus.ACTIVE, repository.activeProgramState.value?.status)
        assertEquals(1, repository.getLogsForProgram(targetId).size)
        assertNotNull(resumed.runState?.weekInstanceId)
    }

    private fun fixtureProgram(id: String, name: String, runId: String, status: ProgramRunStatus): Program {
        val weekId = "$id-week"
        val blockId = "$id-block"
        val mesocycleId = "$id-meso"
        val macrocycleId = "$id-macro"
        val session = Session(id = "$id-session", name = "Día A", dayOfWeek = 2)
        val week = ProgramWeek(id = weekId, name = "Semana 1", sessions = listOf(session))
        return Program(
            id = id,
            name = name,
            structure = ProgramStructure.COMPLEX,
            macrocycles = listOf(
                Macrocycle(
                    id = macrocycleId,
                    name = "Macro",
                    blocks = listOf(
                        Block(
                            id = blockId,
                            name = "Bloque",
                            mesocycles = listOf(Mesocycle(id = mesocycleId, name = "Meso", weeks = listOf(week))),
                        ),
                    ),
                ),
            ),
            startDay = 2,
            schedulePlan = ProgramSchedulePlan(weekStartDay = 1, trainingDays = setOf(2, 4, 6)),
            runState = ProgramRunState(
                runId = runId,
                cycleNumber = 1,
                weekInstanceId = weekId.takeIf { status == ProgramRunStatus.ACTIVE },
                weekId = weekId.takeIf { status == ProgramRunStatus.ACTIVE },
                macrocycleId = macrocycleId,
                blockId = blockId,
                mesocycleId = mesocycleId,
                status = status,
            ),
            programEndMode = ProgramEndMode.REPEATING,
        )
    }
}
