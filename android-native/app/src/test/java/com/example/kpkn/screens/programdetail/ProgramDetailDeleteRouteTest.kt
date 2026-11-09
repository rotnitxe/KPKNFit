package com.example.kpkn.screens.programdetail

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toOngoingWorkoutState
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.OngoingWorkoutState
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.data.repository.StartWorkoutResult
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.CompositionMetadataHolder
import com.example.kpkn.testsupport.RoomTestRule
import com.example.kpkn.ui.components.KpknOverlayHostContent
import com.example.kpkn.ui.components.KpknOverlayHostController
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.tour.TourIds
import com.example.kpkn.ui.tour.TourSeenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w400dp-h1000dp-xxhdpi")
class ProgramDetailDeleteRouteTest {
    @get:Rule val room = RoomTestRule()
    @get:Rule val compose = createComposeRule()

    private val store = ViewModelStore()
    private lateinit var repository: ProgramRepository
    private var detailVm: ProgramDetailViewModel? = null
    private var previousCompositionMetadata = CompositionMetadataHolder.current
    private val programId = "delete-route-program"
    private val destination = mutableStateOf("program")
    private var returnToSession: Pair<String, String>? = null

    @Before
    fun setUp() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        previousCompositionMetadata = CompositionMetadataHolder.current
        CatalogCompositionTestSupport.install()
        repository = ProgramRepository.initForTests(ApplicationProvider.getApplicationContext())
        withTimeout(10_000) { repository.isReady.first { it } }
        repository.resetAllStateSync()
        repository.addProgram(programFixture())
        repository.flushPendingWrites()
    }

    @After
    fun tearDown() {
        try {
            store.clear()
            runBlocking {
                try { detailVm?.viewModelScope?.coroutineContext?.get(Job)?.cancelAndJoin() }
                finally { repository.flushPendingWrites() }
            }
        } finally {
            try { ProgramRepository.closeInstance() }
            finally {
                try { KpknDatabase.closeInstance() }
                finally {
                    CompositionMetadataHolder.current = previousCompositionMetadata
                    Dispatchers.resetMain()
                }
            }
        }
    }

    private fun programFixture() = Program(
        id = programId,
        name = "Programa de prueba",
        structure = ProgramStructure.SIMPLE,
        macrocycles = listOf(
            Macrocycle(
                id = "macro",
                name = "Macro",
                blocks = listOf(
                    Block(
                        id = "block",
                        name = "Bloque",
                        mesocycles = listOf(
                            Mesocycle(
                                id = "meso",
                                name = "Meso",
                                weeks = listOf(
                                    ProgramWeek(
                                        id = "week",
                                        name = "Semana 1",
                                        sessions = listOf(Session(id = "session-open", name = "Sesión")),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    private fun mountRoute() {
        val overlays = KpknOverlayHostController(
            tourSeen = object : TourSeenStore {
                override fun seenIds(): Set<String> = setOf(TourIds.TRAINING)
                override fun markSeen(tourId: String) = Unit
            },
        )
        val vm = ProgramDetailViewModel(programId).also {
            detailVm = it
            store.put("program-detail", it)
        }
        compose.setContent {
            CompositionLocalProvider(LocalKpknOverlayHost provides overlays) {
                if (destination.value == "program") {
                    ProgramDetailScreen(
                        programId = programId,
                        onBack = { destination.value = "Entreno" },
                        onStartWorkout = { _, _ -> Unit },
                        onEditSession = {},
                        onCreateSession = { _, _, _, _, _ -> Unit },
                        viewModel = vm,
                        onReturnToSession = { id, sessionId -> returnToSession = id to sessionId },
                        onProgramDeleted = { destination.value = "Entreno" },
                    )
                } else {
                    Text("Entreno")
                }
                KpknOverlayHostContent(overlays)
            }
        }
        compose.mainClock.advanceTimeBy(3_000)
        compose.waitForIdle()
    }

    private fun openDeleteConfirmation() {
        compose.onNodeWithContentDescription("Más opciones del programa", substring = true).performClick()
        compose.onNodeWithText("Eliminar programa").performScrollTo().performClick()
        compose.onNodeWithText("¿Eliminar Programa de prueba?").assertIsDisplayed()
    }

    @Test
    fun detailRoute_cancelDoesNotDelete_thenConfirmedDeleteReturnsToTraining() = runBlocking {
        mountRoute()

        openDeleteConfirmation()
        compose.onNodeWithText("Cancelar").performClick()
        compose.waitForIdle()
        assertNotNull(repository.getProgramById(programId))
        assertEquals("program", destination.value)

        openDeleteConfirmation()
        compose.onNodeWithText("Eliminar").performClick()
        compose.waitForIdle()

        assertNull(repository.getProgramById(programId))
        compose.onNodeWithText("Entreno").assertExists()
        compose.onNodeWithText("¿Eliminar Programa de prueba?").assertDoesNotExist()
    }

    @Test
    fun detailRoute_deleteWithOpenSessionKeepsSessionAndOffersReturnToIt() = runBlocking {
        val ongoing = OngoingWorkoutState(
            programId = programId,
            session = Session(id = "session-open", name = "Sesión"),
            startTime = 7L,
        )
        assertEquals(StartWorkoutResult.Started, repository.startWorkout(ongoing))
        mountRoute()

        openDeleteConfirmation()
        compose.onNodeWithText("Eliminar").performClick()
        compose.onNodeWithText("Sesión abierta").assertIsDisplayed()

        assertNotNull(repository.getProgramById(programId))
        assertEquals(ongoing, repository.ongoingWorkout.value)
        assertEquals(ongoing, repository.databaseForTests().stateDao().getOngoingWorkout()?.toOngoingWorkoutState())
        assertEquals("program", destination.value)

        compose.onNodeWithText("Volver a sesión").performClick()
        assertEquals(programId to "session-open", returnToSession)
        assertEquals(ongoing, repository.ongoingWorkout.value)
    }

    @Test
    fun detailRoute_archiveWithOpenSessionKeepsItAndDoesNotMarkArchived() = runBlocking {
        val ongoing = OngoingWorkoutState(
            programId = programId,
            session = Session(id = "session-open", name = "Sesión"),
            startTime = 8L,
        )
        assertEquals(StartWorkoutResult.Started, repository.startWorkout(ongoing))
        mountRoute()

        compose.onNodeWithContentDescription("Más opciones del programa", substring = true).performClick()
        compose.onNodeWithText("Archivar programa").performScrollTo().performClick()
        compose.onNodeWithText("Sesión abierta").assertIsDisplayed()

        assertNotNull(repository.getProgramById(programId))
        assertEquals(ongoing, repository.ongoingWorkout.value)
        assertFalse(repository.settings.value.archivedProgramIds.contains(programId))
        assertEquals("program", destination.value)

        compose.onNodeWithText("Volver a sesión").performClick()
        assertEquals(programId to "session-open", returnToSession)
        assertEquals(ongoing, repository.ongoingWorkout.value)
    }
}
