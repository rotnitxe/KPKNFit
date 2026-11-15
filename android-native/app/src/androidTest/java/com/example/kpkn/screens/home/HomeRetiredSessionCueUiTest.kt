package com.example.kpkn.screens.home

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.food.FoodImporter
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.repository.CompetitionRepository
import com.example.kpkn.data.repository.FoodCatalogImporter
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.data.repository.TrainingObservationRepository
import com.example.kpkn.navigation.KpknRoute
import com.example.kpkn.ui.theme.AppThemeMode
import com.example.kpkn.ui.theme.KPKNTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Req. 3: reproduce the retired Home warning trigger in an isolated Room fixture,
 * then verify the current Home UI keeps the session and Entreno path usable.
 * All repositories and observations use in-memory databases; the app database
 * is never used as a fixture or cleaned by this test.
 */
@RunWith(AndroidJUnit4::class)
class HomeRetiredSessionCueUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val viewModelStore = ViewModelStore()
    private lateinit var app: android.app.Application
    private lateinit var room: KpknDatabase
    private lateinit var programs: ProgramRepository
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var program: Program
    private lateinit var todaySession: Session

    private val noCatalogImport = object : FoodCatalogImporter {
        override suspend fun importIfNeeded(
            db: KpknDatabase,
            context: Context,
            alreadyImported: Boolean,
            existingMeta: FoodImporter.ImportMetadata?,
            onMetaUpdated: (FoodImporter.ImportMetadata) -> Unit,
        ): Boolean = false
    }

    @Before
    fun setUp() = runBlocking {
        app = ApplicationProvider.getApplicationContext()
        programs = ProgramRepository.initForTests(app)
        withTimeout(20_000) { programs.isReady.first { it } }
        NutritionRepository.initForTests(app, noCatalogImport)
        room = KpknDatabase.createInMemory(app)
        CompetitionRepository.initForTests(app, room)

        val today = LocalDate.now()
        val exercise = Exercise(
            id = "cue-bench",
            name = "Press de banca",
            sets = listOf(ExerciseSet(id = "cue-set", targetReps = 8, weight = 60.0)),
        )
        todaySession = Session(
            id = "cue-session-today",
            name = "Pecho",
            exercises = listOf(exercise),
            dayOfWeek = today.dayOfWeek.value,
        )
        val week = ProgramWeek(
            id = "cue-week",
            name = "Semana actual",
            startDate = today.minusDays((today.dayOfWeek.value - 1).toLong()).toString(),
            endDate = today.plusDays((7 - today.dayOfWeek.value).toLong()).toString(),
            sessions = listOf(todaySession),
        )
        program = Program(
            id = "qa-retired-session-cue",
            name = "Fixture aviso retirado",
            macrocycles = listOf(
                Macrocycle(
                    id = "cue-macro",
                    name = "Macro",
                    blocks = listOf(Block("cue-block", "Bloque", mesocycles = listOf(Mesocycle("cue-meso", "Meso", weeks = listOf(week))))),
                ),
            ),
        )
        programs.addProgramNow(program).getOrThrow()
        programs.startProgram(program.id)

        // Before retirement this was the triggering shape: same planned exercise,
        // completed in the prior 72 hours, with explicit failure / RPE > 9.
        programs.addWorkoutLog(
            WorkoutLog(
                id = "qa-retired-cue-log",
                programId = program.id,
                sessionId = "past-session-not-today",
                sessionName = "Sesión anterior",
                date = today.minusDays(1).atTime(12, 0).toString(),
                durationMinutes = 45,
                completedExercises = listOf(
                    CompletedExercise(
                        exerciseId = exercise.id,
                        exerciseName = exercise.name,
                        sets = listOf(CompletedSet("cue-failure", reps = 5, rpe = 10.0, isFailure = true)),
                    ),
                ),
            ),
        )
        withTimeout(10_000) {
            programs.activeProgramState.first { it?.programId == program.id }
            programs.history.first { history -> history.any { it.id == "qa-retired-cue-log" } }
        }

        val observations = TrainingObservationRepository(app, room)
        homeViewModel = HomeViewModel(appContext = null, trainingObservationRepository = { observations })
            .also { viewModelStore.put("home", it) }
        homeViewModel.dismissOnboarding()
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        CompetitionRepository.closeInstance()
        NutritionRepository.closeInstance()
        ProgramRepository.closeInstance()
        KpknDatabase.closeInstance()
        if (::room.isInitialized) room.close()
    }

    @Test
    fun retiredFailureCueDoesNotDisplaceHomeSessionOrEntrenoTools() {
        var navigatedRoute: String? = null
        composeRule.setContent {
            KPKNTheme(themeMode = AppThemeMode.HIGH_CONTRAST) {
                HomeScreen(
                    themeMode = AppThemeMode.HIGH_CONTRAST,
                    onThemeChange = {},
                    onNavigate = { navigatedRoute = it },
                    viewModel = homeViewModel,
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home-hero-start").assertExists()
        composeRule.onNodeWithTag("home-hero-edit").assertExists()
        composeRule.onNodeWithText("a menos de 1 repetición del fallo", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Ajustar", substring = true).assertDoesNotExist()

        val metrics = composeRule.onNodeWithTag("home-training-metrics")
        metrics.performScrollTo()
        metrics.assertIsDisplayed()
        val metricsBounds = metrics.fetchSemanticsNode().boundsInRoot
        val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("el módulo de métricas queda dentro del viewport tras hacer scroll", metricsBounds.top >= 0f && metricsBounds.top < rootBounds.bottom)
        assertTrue("el módulo conserva una altura visible", metricsBounds.height > 0f)

        composeRule.onNodeWithTag(HOME_SCREEN_SCROLL_TAG).performScrollToNode(hasText("TUS PROGRAMAS"))
        composeRule.onNodeWithTag(HOME_PROGRAMS_SEE_ALL_TAG).assertIsDisplayed().performClick()
        assertEquals("Home abre la ruta de Entreno desde sus herramientas", KpknRoute.Training.route, navigatedRoute)
    }
}
