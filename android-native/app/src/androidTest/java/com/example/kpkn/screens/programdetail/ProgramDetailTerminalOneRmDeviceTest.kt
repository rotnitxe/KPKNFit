package com.example.kpkn.screens.programdetail

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.view.inputmethod.InputMethodManager
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.exercises.initializeExerciseDatabase
import com.example.kpkn.data.models.ActiveProgramState
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.OneRmResolutionStatus
import com.example.kpkn.data.models.PendingProgramAction
import com.example.kpkn.data.models.PendingProgramActionType
import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramEndMode
import com.example.kpkn.data.models.ProgramGoals
import com.example.kpkn.data.models.ProgramRunState
import com.example.kpkn.data.models.ProgramRunStatus
import com.example.kpkn.data.models.ProgramStatus
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.onboarding.LiftMark
import com.example.kpkn.screens.onboarding.design.entreno.markTag
import com.example.kpkn.screens.programdetail.design.ONE_RM_SAVE_TAG
import com.example.kpkn.ui.theme.AppThemeMode
import com.example.kpkn.ui.theme.KPKNTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Req. 5: records a terminal 1RM from the real program detail UI and reads it back from Room. */
@RunWith(AndroidJUnit4::class)
class ProgramDetailTerminalOneRmDeviceTest {
    private val suffix = System.nanoTime().toString().takeLast(7)
    private val programId = "qa1rm-$suffix"
    private val runId = "run-$suffix"
    private val databaseName = "qa1rm-$suffix.db"

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var context: android.content.Context
    private lateinit var repository: ProgramRepository
    private var viewModel: ProgramDetailViewModel? = null

    @Before
    fun setUp() {
        runBlocking {
            context = InstrumentationRegistry.getInstrumentation().targetContext
            assertEquals("La fixture solo puede usar KPKN Dev", "com.example.kpkn.dbg", context.packageName)
            initializeExerciseDatabase(context)
            ProgramRepository.closeInstance()
            KpknDatabase.closeInstance()
            context.deleteDatabase(databaseName)
            repository = ProgramRepository.initForTestsWithDatabaseFile(context, databaseName)
            withTimeout(30_000) { repository.isReady.first { it } }

            val program = terminalProgram()
            assertTrue(repository.addProgramNow(program).isSuccess)
            repository.updateActiveProgramState(
                ActiveProgramState(
                    programId = programId,
                    status = ProgramStatus.ACTIVE,
                    currentWeekId = "$programId-w2",
                    currentWeekInstanceId = "$programId-w2",
                    currentCycleNumber = 1,
                    currentMacrocycleId = "$programId-macro",
                    currentBlockId = "$programId-block",
                    currentMesocycleId = "$programId-meso",
                    programRunId = runId,
                ),
            )
            repository.flushPendingWrites()
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            runCatching { compose.activityRule.scenario.onActivity { it.viewModelStore.clear() } }
            runCatching { viewModel?.viewModelScope?.coroutineContext?.get(Job)?.cancelAndJoin() }
            if (!::repository.isInitialized) return@runBlocking
            runCatching { repository.flushPendingWrites() }
            ProgramRepository.closeInstance()
            KpknDatabase.closeInstance()
            if (::context.isInitialized) context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun recordedTerminalOneRmFromProgramDetailPersistsAfterRoomReopen() = runBlocking {
        val detail = ProgramDetailViewModel(programId).also { viewModel = it }
        compose.activityRule.scenario.onActivity { activity ->
            activity.viewModelStore.put("qa-terminal-1rm-$suffix", detail)
        }
        compose.setContent {
            KPKNTheme(themeMode = AppThemeMode.HIGH_CONTRAST) {
                ProgramDetailScreen(
                    programId = programId,
                    onBack = {},
                    onStartWorkout = { _, _ -> },
                    onEditSession = {},
                    onCreateSession = { _, _, _, _, _ -> },
                    viewModel = detail,
                )
            }
        }

        compose.waitUntil(20_000) { detail.program.value?.id == programId }
        withTimeout(20_000) {
            detail.blockTransitionBanner.first { it?.pendingType == PendingProgramActionType.CONFIRM_1RM_TEST }
        }
        compose.onNodeWithContentDescription("Más opciones del programa, hay pendientes")
            .performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Pendiente", substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Pendiente", substring = true, useUnmergedTree = true)
            .performScrollTo()
            .performClick()
        compose.onNodeWithText("Pendiente", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Registrar tus máximos", useUnmergedTree = true)
            .performScrollTo()
            .performClick()
        compose.onNodeWithTag(markTag(LiftMark.SQUAT), useUnmergedTree = true).performTextReplacement("200")
        compose.onNodeWithTag(markTag(LiftMark.BENCH), useUnmergedTree = true).performTextReplacement("120")
        compose.onNodeWithTag(markTag(LiftMark.DEADLIFT), useUnmergedTree = true).performTextReplacement("220")
        compose.activityRule.scenario.onActivity { activity ->
            activity.getSystemService(InputMethodManager::class.java)
                ?.hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
        }
        compose.onNodeWithTag(ONE_RM_SAVE_TAG, useUnmergedTree = true)
            .assertIsEnabled()
            .performClick()

        compose.waitUntil(30_000) {
            val saved = repository.getProgramById(programId)
            val run = saved?.runState ?: return@waitUntil false
            run.pendingAction == null &&
                run.oneRmResolution?.status == OneRmResolutionStatus.RECORDED &&
                run.status == ProgramRunStatus.COMPLETED
        }
        repository.flushPendingWrites()
        val saved = requireNotNull(repository.getProgramById(programId))
        assertEquals(1, saved.runState?.cycleNumber)
        assertEquals(runId, saved.runState?.runId)
        assertEquals(OneRmResolutionStatus.RECORDED, saved.runState?.oneRmAuditTrail?.lastOrNull()?.status)
        assertEquals(ProgramStatus.COMPLETED, repository.activeProgramState.value?.status)
        assertEquals(200.0, saved.powerliftingProfile?.squat1RM ?: 0.0, 0.0)
        assertEquals(120.0, saved.powerliftingProfile?.bench1RM ?: 0.0, 0.0)
        assertEquals(220.0, saved.powerliftingProfile?.deadlift1RM ?: 0.0, 0.0)

        compose.activityRule.scenario.onActivity { it.viewModelStore.clear() }
        detail.viewModelScope.coroutineContext[Job]?.cancelAndJoin()
        ProgramRepository.closeInstance()
        KpknDatabase.closeInstance()
        repository = ProgramRepository.initForTestsWithDatabaseFile(context, databaseName)
        withTimeout(30_000) { repository.isReady.first { it } }

        val reopened = requireNotNull(repository.getProgramById(programId))
        assertEquals(ProgramRunStatus.COMPLETED, reopened.runState?.status)
        assertEquals(null, reopened.runState?.pendingAction)
        assertEquals(1, reopened.runState?.cycleNumber)
        assertEquals(runId, reopened.runState?.runId)
        assertEquals(OneRmResolutionStatus.RECORDED, reopened.runState?.oneRmResolution?.status)
        assertEquals(OneRmResolutionStatus.RECORDED, reopened.runState?.oneRmAuditTrail?.lastOrNull()?.status)
        assertEquals(200.0, reopened.powerliftingProfile?.squat1RM ?: 0.0, 0.0)
        assertEquals(120.0, reopened.powerliftingProfile?.bench1RM ?: 0.0, 0.0)
        assertEquals(220.0, reopened.powerliftingProfile?.deadlift1RM ?: 0.0, 0.0)
        assertEquals(200.0, reopened.goals?.squat1RM ?: 0.0, 0.0)
        assertEquals(120.0, reopened.goals?.bench1RM ?: 0.0, 0.0)
        assertEquals(220.0, reopened.goals?.deadlift1RM ?: 0.0, 0.0)
        assertEquals(ProgramStatus.COMPLETED, repository.activeProgramState.value?.status)
        assertEquals(programId, repository.activeProgramState.value?.programId)
        assertNotNull("La base Room de la fixture debe existir hasta el teardown", context.getDatabasePath(databaseName).takeIf { it.exists() })
    }

    private fun terminalProgram(): Program {
        val firstWeek = ProgramWeek(
            id = "$programId-w1",
            name = "Semana 1",
            sessions = listOf(Session(id = "$programId-s1", name = "Día A", dayOfWeek = 2)),
        )
        val finalWeek = ProgramWeek(
            id = "$programId-w2",
            name = "Semana 2",
            sessions = listOf(Session(id = "$programId-s2", name = "Test final", dayOfWeek = 4)),
        )
        return Program(
            id = programId,
            name = "Cierre terminal QA",
            structure = ProgramStructure.COMPLEX,
            programEndMode = ProgramEndMode.HAS_END,
            goals = ProgramGoals(),
            powerliftingProfile = PowerliftingProfile(),
            macrocycles = listOf(
                Macrocycle(
                    id = "$programId-macro",
                    name = "Macro",
                    blocks = listOf(
                        Block(
                            id = "$programId-block",
                            name = "Bloque final",
                            mesocycles = listOf(Mesocycle(id = "$programId-meso", name = "Meso", weeks = listOf(firstWeek, finalWeek))),
                        ),
                    ),
                ),
            ),
            runState = ProgramRunState(
                runId = runId,
                cycleNumber = 1,
                weekId = finalWeek.id,
                weekInstanceId = finalWeek.id,
                macrocycleId = "$programId-macro",
                blockId = "$programId-block",
                mesocycleId = "$programId-meso",
                status = ProgramRunStatus.ACTIVE,
                pendingAction = PendingProgramAction(
                    type = PendingProgramActionType.CONFIRM_1RM_TEST,
                    message = "Registrar test terminal",
                ),
            ),
        )
    }
}
