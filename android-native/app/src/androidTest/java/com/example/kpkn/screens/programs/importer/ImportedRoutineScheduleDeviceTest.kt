package com.example.kpkn.screens.programs.importer

import android.app.Application
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.exercises.initializeExerciseDatabase
import com.example.kpkn.data.models.ProgramEndMode
import com.example.kpkn.data.models.ProgramRunStatus
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.entitlements.PlanLevel
import com.example.kpkn.domain.training.ProgramProgressEngine
import com.example.kpkn.domain.training.importer.ImportWizardPage
import com.example.kpkn.screens.programdetail.ProgramDetailScreen
import com.example.kpkn.screens.programdetail.ProgramDetailViewModel
import com.example.kpkn.ui.entitlements.LocalPlan
import com.example.kpkn.ui.theme.AppThemeMode
import com.example.kpkn.ui.theme.KPKNTheme
import com.example.kpkn.data.models.WorkoutLog
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
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/** Import acceptance: paste two dated sessions, move Thursday to Tuesday, save, reopen Room and open that program. */
@RunWith(AndroidJUnit4::class)
class ImportedRoutineScheduleDeviceTest {
    private val suffix = System.nanoTime().toString().takeLast(8)
    private val databaseName = "qa-import-schedule-$suffix.db"
    private val draftFileName = "qa-import-schedule-$suffix.json"
    private val title = "Rutina importada QA $suffix"
    private val sourceText = "Jueves — Pierna\nSentadilla 3x5\nSábado — Torso\nPress banca 3x8"
    private val showEmpty = mutableStateOf(false)
    private val showDetail = mutableStateOf(false)
    private var detailProgramId: String? = null

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var context: android.content.Context
    private lateinit var repository: ProgramRepository
    private lateinit var importViewModel: ImportProgramViewModel
    private lateinit var detailViewModel: ProgramDetailViewModel
    private val savedProgramId = AtomicReference<String?>(null)

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

            val draftFile = File(context.cacheDir, draftFileName)
            importViewModel = ImportProgramViewModel(context.applicationContext as Application, draftFile).also { vm ->
                vm.matchNames = { emptyMap() }
                vm.saveProgram = { imported -> repository.addProgramNow(imported) }
                vm.findExistingProgram = { id -> repository.getProgramById(id) }
            }
            compose.activityRule.scenario.onActivity { it.viewModelStore.put("qa-import-$suffix", importViewModel) }
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            runCatching {
                compose.runOnIdle {
                    showEmpty.value = true
                    showDetail.value = false
                }
            }
            runCatching { compose.activityRule.scenario.onActivity { it.viewModelStore.clear() } }
            if (::repository.isInitialized) {
                runCatching { repository.flushPendingWrites() }
                ProgramRepository.closeInstance()
                KpknDatabase.closeInstance()
            }
            if (::context.isInitialized) {
                context.deleteDatabase(databaseName)
                context.cacheDir.listFiles()?.filter {
                    it.name == draftFileName || it.name.startsWith("$draftFileName.")
                }?.forEach { it.delete() }
            }
        }
    }

    @Test
    fun pasteMoveThursdayToTuesdaySaveReopenRoomAndOpenMatchingProgramDetail() = runBlocking {
        compose.setContent {
            when {
                showEmpty.value -> Unit
                showDetail.value -> KPKNTheme(themeMode = AppThemeMode.HIGH_CONTRAST) {
                    ProgramDetailScreen(
                        programId = requireNotNull(detailProgramId),
                        onBack = {},
                        onStartWorkout = { _, _ -> },
                        onEditSession = {},
                        onCreateSession = { _, _, _, _, _ -> },
                        viewModel = detailViewModel,
                    )
                }
                else -> CompositionLocalProvider(LocalPlan provides PlanLevel.PRO) {
                    KPKNTheme(themeMode = AppThemeMode.HIGH_CONTRAST) {
                        ImportProgramScreen(
                            onBack = {},
                            onSaved = savedProgramId::set,
                            onUseCatalogProgram = {},
                            viewModel = importViewModel,
                        )
                    }
                }
            }
        }

        compose.onNodeWithTag("import-entry-paste").assertIsDisplayed().performClick()
        compose.onNodeWithTag("import-paste-field").performTextInput(sourceText)
        compose.activityRule.scenario.onActivity { activity ->
            activity.getSystemService(InputMethodManager::class.java)
                ?.hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
        }
        compose.onNodeWithTag("import-read-text").performScrollTo().performClick()
        compose.waitUntil(45_000) {
            !importViewModel.state.value.busy && importViewModel.state.value.imported != null
        }
        compose.waitUntil(30_000) {
            !importViewModel.state.value.draftPersistencePending && importViewModel.state.value.draftPersistenceError == null
        }

        val parsed = requireNotNull(importViewModel.state.value.imported)
        assertEquals(ImportWizardPage.EXERCISES, importViewModel.state.value.page)
        val sessions = parsed.weeks.single().days.filterNot { it.isRestDay }
        assertEquals(2, sessions.size)
        assertEquals(listOf(4, 6), sessions.map { it.dayOfWeek })
        sessions.flatMap { it.exercises }.forEach { exercise ->
            val candidate = importViewModel.suggestions(exercise.rawName).firstOrNull()
                ?: error("No hay una coincidencia de catálogo segura para ${exercise.rawName}")
            val candidateTag = "import-match-candidate-${candidate.configurationId}"
            compose.waitUntil(20_000) {
                compose.onAllNodesWithTag(candidateTag).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag(candidateTag).performScrollTo().performClick()
            compose.waitUntil(20_000) {
                !importViewModel.state.value.draftPersistencePending && importViewModel.state.value.draftPersistenceError == null
            }
            assertEquals(candidate.configurationId, importViewModel.state.value.resolutions[exercise.rawName])
        }

        compose.onNodeWithTag("import-title").performScrollTo().performTextReplacement(title)
        compose.onNodeWithTag("import-week-session-${sessions.first().id}").performScrollTo().performClick()
        compose.onNodeWithTag("import-weekday-2").performScrollTo().performClick()
        compose.waitUntil(20_000) {
            !importViewModel.state.value.draftPersistencePending && importViewModel.state.value.draftPersistenceError == null
        }
        val assigned = effectiveImportedWeekdays(
            requireNotNull(importViewModel.state.value.imported),
            importViewModel.state.value.weekdays,
        )
        assertEquals(2, assigned[sessions.first().id])
        assertEquals(6, assigned[sessions.last().id])

        compose.onNodeWithTag("import-step-next").assertIsDisplayed().assertIsEnabled().performClick()
        compose.waitUntil(45_000) {
            savedProgramId.get() != null || importViewModel.state.value.error != null
        }
        assertNotNull("No se guardó desde el dock: ${importViewModel.state.value}", savedProgramId.get())
        val id = requireNotNull(savedProgramId.get())
        repository.flushPendingWrites()
        val saved = requireNotNull(repository.getProgramById(id))
        assertEquals(title, saved.name)
        assertEquals(ProgramEndMode.REPEATING, saved.programEndMode)
        assertEquals(setOf(2, 6), saved.schedulePlan?.trainingDays)
        val savedSessions = saved.macrocycles.flatMap { it.blocks }
            .flatMap { it.mesocycles }.flatMap { it.weeks }.single().sessions
        assertEquals(listOf(2, 6), savedSessions.map { it.dayOfWeek })

        compose.runOnIdle { showEmpty.value = true }
        compose.waitForIdle()
        compose.activityRule.scenario.onActivity { it.viewModelStore.clear() }
        ProgramRepository.closeInstance()
        KpknDatabase.closeInstance()
        repository = ProgramRepository.initForTestsWithDatabaseFile(context, databaseName)
        withTimeout(30_000) { repository.isReady.first { it } }
        val reopened = requireNotNull(repository.getProgramById(id))
        assertEquals(ProgramEndMode.REPEATING, reopened.programEndMode)
        assertEquals(setOf(2, 6), reopened.schedulePlan?.trainingDays)
        val reopenedSessions = reopened.macrocycles.flatMap { it.blocks }
            .flatMap { it.mesocycles }.flatMap { it.weeks }.single().sessions
        assertEquals(listOf(2, 6), reopenedSessions.map { it.dayOfWeek })

        detailProgramId = id
        detailViewModel = ProgramDetailViewModel(id)
        compose.activityRule.scenario.onActivity { it.viewModelStore.put("qa-import-detail-$suffix", detailViewModel) }
        compose.runOnIdle {
            showEmpty.value = false
            showDetail.value = true
        }
        compose.waitUntil(30_000) { detailViewModel.program.value?.id == id }
        compose.onNodeWithText(title, useUnmergedTree = true).assertIsDisplayed()
        assertNotNull(detailViewModel.program.value)
        assertTrue(detailViewModel.program.value?.schedulePlan?.trainingDays == setOf(2, 6))

        repository.startProgram(id)
        withTimeout(15_000) { repository.activeProgramState.first { it?.programId == id } }
        repository.flushPendingWrites()
        val started = requireNotNull(repository.getProgramById(id))
        val firstRun = requireNotNull(started.runState)
        val runId = firstRun.runId
        val importedWeek = started.macrocycles.flatMap { it.blocks }
            .flatMap { it.mesocycles }.flatMap { it.weeks }.single()
        val firstCycleInstance = ProgramProgressEngine.instanceIdFor(1, importedWeek.id)
        var lastLog: WorkoutLog? = null
        reopenedSessions.forEachIndexed { index, session ->
            val log = WorkoutLog(
                id = "$id-cycle1-session-${index + 1}",
                programId = id,
                sessionId = session.id,
                sessionName = session.name,
                date = "2026-11-%02dT10:00:00Z".format(index + 1),
                durationMinutes = 35,
                programRunId = runId,
                cycleNumber = 1,
                weekId = importedWeek.id,
                weekInstanceId = firstCycleInstance,
            )
            repository.finalizeWorkout(log)
            lastLog = log
        }
        repository.flushPendingWrites()
        val nextCycle = requireNotNull(repository.getProgramById(id))
        assertEquals(ProgramEndMode.REPEATING, nextCycle.programEndMode)
        assertEquals(ProgramRunStatus.ACTIVE, nextCycle.runState?.status)
        assertEquals(2, nextCycle.runState?.cycleNumber)
        assertEquals(runId, nextCycle.runState?.runId)
        assertEquals(ProgramProgressEngine.instanceIdFor(2, importedWeek.id), nextCycle.runState?.weekInstanceId)
        assertEquals(2, repository.getLogsForProgram(id).size)

        compose.runOnIdle {
            showEmpty.value = true
            showDetail.value = false
        }
        compose.waitForIdle()
        compose.activityRule.scenario.onActivity { it.viewModelStore.clear() }
        ProgramRepository.closeInstance()
        KpknDatabase.closeInstance()
        repository = ProgramRepository.initForTestsWithDatabaseFile(context, databaseName)
        withTimeout(30_000) { repository.isReady.first { it } }
        val cycleAfterReopen = requireNotNull(repository.getProgramById(id))
        assertEquals(2, cycleAfterReopen.runState?.cycleNumber)
        assertEquals(runId, cycleAfterReopen.runState?.runId)
        assertEquals(ProgramRunStatus.ACTIVE, cycleAfterReopen.runState?.status)
        assertEquals(2, repository.getLogsForProgram(id).size)
        repository.finalizeWorkout(requireNotNull(lastLog))
        val afterRetry = requireNotNull(repository.getProgramById(id))
        assertEquals(2, afterRetry.runState?.cycleNumber)
        assertEquals(runId, afterRetry.runState?.runId)
        assertEquals("Retry no duplica el historial de la rutina importada", 2, repository.getLogsForProgram(id).size)
    }
}
