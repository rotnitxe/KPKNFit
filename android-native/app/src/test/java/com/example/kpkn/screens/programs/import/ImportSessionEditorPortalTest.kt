package com.example.kpkn.screens.programs.importer

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.runtime.mutableStateOf
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.screens.sessioneditor.ImportedProgramEditorStore
import com.example.kpkn.testsupport.RoomTestRule
import com.example.kpkn.ui.components.KpknOverlayHostContent
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.components.KpknOverlayHostController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h1000dp-xxhdpi", application = Application::class)
class ImportSessionEditorPortalTest {
    @get:Rule val room = RoomTestRule()
    @get:Rule val rule = createComposeRule()
    private val dispatcher = UnconfinedTestDispatcher()
    private val stores = mutableListOf<ImportedProgramEditorStore>()

    @After fun close() {
        rule.runOnIdle { stores.forEach { it.editorViewModels.clear() } }
        ProgramRepository.closeInstance()
        Dispatchers.resetMain()
    }

    private fun program(id: String, name: String): Program {
        val session = Session(id = "session-$id", name = name, dayOfWeek = 1)
        val week = ProgramWeek(id = "week", name = "Semana", sessions = listOf(session))
        val meso = Mesocycle(id = "meso", name = "Meso", weeks = listOf(week))
        val block = Block(id = "block", name = "Bloque", mesocycles = listOf(meso))
        return Program(id = id, name = "Plan", macrocycles = listOf(Macrocycle(id = "macro", name = "Macro", blocks = listOf(block))))
    }

    @Test fun closingAfterEditPersistsDraftWithoutChangingLibrary() {
        Dispatchers.setMain(dispatcher)
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repository = ProgramRepository.initForTests(app)
        runBlocking { withTimeout(10_000) { while (!repository.isReady.value) delay(25) } }
        val id = "portal-${UUID.randomUUID()}"
        repository.addProgram(program(id, "Biblioteca"))
        var durable = program(id, "Sesión importada")
        val store = ImportedProgramEditorStore(durable) { durable = it; true }.also(stores::add)
        var closed = false
        rule.setContent { ImportSessionEditorPortal(store, id, "session-$id", "week", 0, 0, 1) { closed = true } }
        rule.waitUntil(15_000) { rule.onAllNodesWithText("Sesión importada").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Sesión importada").performTextReplacement("Sesión editada")
        rule.onNodeWithContentDescription("Atrás").performClick()
        rule.waitUntil(15_000) { closed }
        assertEquals("Sesión editada", durable.macrocycles.single().blocks.single().mesocycles.single().weeks.single().sessions.single().name)
        assertEquals("Biblioteca", repository.getProgramById(id)!!.macrocycles.single().blocks.single().mesocycles.single().weeks.single().sessions.single().name)
    }

    @Test fun failedPersistenceKeepsEditorOpenAndPreservesDraft() {
        Dispatchers.setMain(dispatcher)
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repository = ProgramRepository.initForTests(app)
        runBlocking { withTimeout(10_000) { while (!repository.isReady.value) delay(25) } }
        val id = "portal-failure-${UUID.randomUUID()}"
        val original = program(id, "Sesión importada")
        var attempted = false
        val store = ImportedProgramEditorStore(original) { attempted = true; false }.also(stores::add)
        var closed = false
        rule.setContent { ImportSessionEditorPortal(store, id, "session-$id", "week", 0, 0, 1) { closed = true } }
        rule.waitUntil(15_000) { rule.onAllNodesWithText("Sesión importada").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Sesión importada").performTextReplacement("Sesión editada")
        rule.onNodeWithContentDescription("Atrás").performClick()
        rule.waitUntil(15_000) { attempted }
        rule.waitForIdle()
        assertFalse(closed)
        assertEquals(original, store.program.value)
        assertTrue(rule.onAllNodesWithText("Sesión editada").fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun portalRecreationReusesTheEditorViewModelHeldByTheDraftStore() {
        Dispatchers.setMain(dispatcher)
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repository = ProgramRepository.initForTests(app)
        runBlocking { withTimeout(10_000) { while (!repository.isReady.value) delay(25) } }
        val id = "portal-rotation-${UUID.randomUUID()}"
        var durable = program(id, "Sesión importada")
        val store = ImportedProgramEditorStore(durable) { durable = it; true }.also(stores::add)
        val mounted = mutableStateOf(true)
        var closed = false
        rule.setContent {
            if (mounted.value) {
                ImportSessionEditorPortal(store, id, "session-$id", "week", 0, 0, 1) { closed = true }
            }
        }
        rule.waitUntil(15_000) { rule.onAllNodesWithText("Sesión importada").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Sesión importada").performTextReplacement("Sesión conservada")
        rule.waitUntil(15_000) { rule.onAllNodesWithText("Sesión conservada").fetchSemanticsNodes().isNotEmpty() }

        // Simulate the portal leaving composition and returning after a configuration change.
        rule.runOnIdle { mounted.value = false }
        rule.waitForIdle()
        rule.runOnIdle { mounted.value = true }
        rule.waitUntil(15_000) { rule.onAllNodesWithText("Sesión conservada").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Atrás").performClick()
        rule.waitUntil(15_000) { closed }
        assertEquals("Sesión conservada", durable.macrocycles.single().blocks.single().mesocycles.single()
            .weeks.single().sessions.single().name)
    }

    @Test fun portalRendersThroughTheSameOverlayHostUsedByMainActivity() {
        Dispatchers.setMain(dispatcher)
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repository = ProgramRepository.initForTests(app)
        runBlocking { withTimeout(10_000) { while (!repository.isReady.value) delay(25) } }
        val id = "portal-host-${UUID.randomUUID()}"
        val store = ImportedProgramEditorStore(program(id, "Sesión importada")) { true }.also(stores::add)
        val overlayHost = KpknOverlayHostController()

        rule.setContent {
            CompositionLocalProvider(LocalKpknOverlayHost provides overlayHost) {
                Box(Modifier.fillMaxSize()) {
                    KpknOverlayHostContent(overlayHost)
                    ImportSessionEditorPortal(store, id, "session-$id", "week", 0, 0, 1) {}
                }
            }
        }

        rule.waitUntil(15_000) {
            rule.onAllNodesWithText("Sesión importada").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(rule.onAllNodesWithText("CARGANDO").fetchSemanticsNodes().isEmpty())
    }
}
