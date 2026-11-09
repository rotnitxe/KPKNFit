package com.example.kpkn.screens.programs

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.data.repository.SessionTemplateRepository
import com.example.kpkn.testsupport.RoomTestRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
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
class ProgramsQueueDeleteRouteTest {
    @get:Rule val room = RoomTestRule()
    @get:Rule val compose = createComposeRule()

    private val store = ViewModelStore()
    private lateinit var repository: ProgramRepository
    private lateinit var viewModel: ProgramsViewModel
    private val programId = "queued-delete-program"
    private val programName = "Programa en cola"

    @Before
    fun setUp() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        SessionTemplateRepository.resetForTests()
        repository = ProgramRepository.initForTests(ApplicationProvider.getApplicationContext())
        setDatabaseSingletonForTest(repository.databaseForTests())
        withTimeout(10_000) { repository.isReady.first { it } }
        repository.resetAllStateSync()
        repository.addProgram(Program(id = programId, name = programName))
        repository.addProgramToQueue(programId)
        repository.flushPendingWrites()
        viewModel = ProgramsViewModel(ApplicationProvider.getApplicationContext()).also {
            store.put("programs", it)
        }
    }

    @After
    fun tearDown() {
        try {
            store.clear()
            SessionTemplateRepository.resetForTests()
            ProgramRepository.closeInstance()
        } finally {
            try { KpknDatabase.closeInstance() }
            finally {
                setDatabaseSingletonForTest(null)
                Dispatchers.resetMain()
            }
        }
    }

    private fun setDatabaseSingletonForTest(database: KpknDatabase?) {
        KpknDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }.set(null, database)
    }

    private fun mountRoute() {
        compose.setContent {
            ProgramsScreen(
                onNavigateToProgram = {},
                onCreateProgram = {},
                onImportProgram = {},
                onEditProgram = {},
                viewModel = viewModel,
            )
        }
        compose.waitForIdle()
    }

    private fun openQueueMenu() {
        compose.onNodeWithContentDescription("Más opciones de $programName").performClick()
        compose.onNodeWithText("Quitar de la cola").performScrollTo().assertExists()
        compose.onNodeWithText("Eliminar").performScrollTo().assertExists()
    }

    @Test
    fun queueRemoveKeepsProgram_andSeparateDeleteActionRequiresConfirmation() = runBlocking {
        mountRoute()

        openQueueMenu()
        compose.onNodeWithText("Quitar de la cola").performScrollTo().performClick()
        compose.waitForIdle()
        assertNotNull(repository.getProgramById(programId))
        assertFalse(repository.programQueue.value.contains(programId))

        repository.addProgramToQueue(programId)
        compose.waitForIdle()
        openQueueMenu()
        compose.onNodeWithText("Eliminar").performScrollTo().performClick()
        compose.onNodeWithText("¿Eliminar $programName?").assertIsDisplayed()
        compose.onNodeWithText("Cancelar").performClick()
        compose.waitForIdle()
        assertNotNull(repository.getProgramById(programId))
        assertTrue(repository.programQueue.value.contains(programId))

        openQueueMenu()
        compose.onNodeWithText("Eliminar").performScrollTo().performClick()
        compose.onNodeWithText("Eliminar").performClick()
        compose.waitForIdle()

        assertNull(repository.getProgramById(programId))
        assertFalse(repository.programQueue.value.contains(programId))
        compose.onNodeWithText("¿Eliminar $programName?").assertDoesNotExist()
    }
}
