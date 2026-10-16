package com.example.kpkn.screens.workout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.screens.workout.components.LiveUndoPillHost
import com.example.kpkn.screens.workout.components.undoPillTarget
import com.example.kpkn.screens.workout.components.undoPillText
import com.example.kpkn.ui.theme.KPKNTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * «Deshacer <acción>» sobre la barra tras cualquier cambio de estructura (LV11): la píldora sale cuando la pila de deshacer crece, ofrece
 * siempre la última entrada, se va con el tiempo, al deshacer y al registrar una serie (deshacer devolvería la sesión a una foto sin ella).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h880dp-xxhdpi", application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiveUndoPillTest {

    @get:Rule
    val rule = createComposeRule()

    private val set = CompletedSet(id = "c", weight = 50.0, reps = 8)

    private fun entry(label: String, completed: Map<String, CompletedSet> = emptyMap()) =
        GodModeUndoSnapshot(label = label, completedSets = completed)

    // ── Reglas puras ───────────────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun theTextNamesTheActionInLowerCaseAfterTheVerb() {
        assertEquals("Deshacer omitir ejercicio", undoPillText("Omitir ejercicio"))
        assertEquals("Deshacer eliminar serie", undoPillText("Eliminar serie"))
        assertEquals("Deshacer", undoPillText("  "))
    }

    @Test
    fun theLastEntryIsOfferedWhileTheSessionHasTheSameSetsItHadWhenItWasSaved() {
        val stack = listOf(entry("Omitir serie"), entry("Eliminar ejercicio", mapOf("a_0" to set)))

        assertEquals("Eliminar ejercicio", undoPillTarget(stack, mapOf("a_0" to set))?.label)
    }

    @Test
    fun noEntryIsOfferedOnceASetWasRecordedAfterTheChange() {
        val stack = listOf(entry("Omitir ejercicio", completed = emptyMap()))

        assertNull(
            "deshacer ahora se llevaría por delante la serie recién registrada",
            undoPillTarget(stack, mapOf("a_0" to set)),
        )
    }

    @Test
    fun anEmptyStackOffersNothing() {
        assertNull(undoPillTarget(emptyList(), emptyMap()))
    }

    // ── La píldora ─────────────────────────────────────────────────────────────────────────────────────────────────────────────

    private var stack by mutableStateOf(emptyList<GodModeUndoSnapshot>())
    private var completed by mutableStateOf(emptyMap<String, CompletedSet>())
    private val undone = mutableListOf<Int>()

    private fun show(initial: List<GodModeUndoSnapshot> = emptyList()) {
        stack = initial
        rule.setContent {
            KPKNTheme {
                Box(Modifier.fillMaxSize()) {
                    LiveUndoPillHost(
                        undoStack = stack,
                        completedSets = completed,
                        onUndo = { undone += it },
                        visibleMillis = 2_000L,
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun whatWasAlreadyInTheStackWhenTheScreenOpenedIsNotOffered() {
        show(initial = listOf(entry("Omitir ejercicio")))

        rule.onNodeWithText("Deshacer omitir ejercicio").assertDoesNotExist()
    }

    @Test
    fun theStackGrowingShowsThePillAndTappingItUndoesTheLastEntry() {
        show()

        rule.runOnUiThread { stack = listOf(entry("Omitir ejercicio")) }
        rule.waitForIdle()
        rule.onNodeWithText("Deshacer omitir ejercicio").assertExists()

        rule.onNodeWithText("Deshacer omitir ejercicio").performClick()
        rule.waitForIdle()

        assertEquals("deshace la última entrada", listOf(0), undone)
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithText("Deshacer omitir ejercicio").assertDoesNotExist()
    }

    @Test
    fun aSecondChangeOffersTheNewestEntry() {
        show()

        rule.runOnUiThread { stack = listOf(entry("Omitir ejercicio")) }
        rule.waitForIdle()
        rule.runOnUiThread { stack = stack + entry("Reordenar ejercicios") }
        rule.waitForIdle()

        rule.onNodeWithText("Deshacer reordenar ejercicios").performClick()
        assertEquals(listOf(1), undone)
    }

    @Test
    fun thePillGoesAwayByItselfAfterAFewSeconds() {
        show()
        rule.runOnUiThread { stack = listOf(entry("Eliminar serie")) }
        rule.waitForIdle()
        rule.onNodeWithText("Deshacer eliminar serie").assertExists()

        // El plazo es un `delay` de verdad (no un reloj de animación): se espera a que pase.
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText("Deshacer eliminar serie").fetchSemanticsNodes().isEmpty()
        }

        rule.onNodeWithText("Deshacer eliminar serie").assertDoesNotExist()
    }

    @Test
    fun recordingASetHidesThePillBecauseUndoingWouldDropIt() {
        show()
        rule.runOnUiThread { stack = listOf(entry("Eliminar serie")) }
        rule.waitForIdle()
        rule.onNodeWithText("Deshacer eliminar serie").assertExists()

        rule.runOnUiThread { completed = mapOf("a_0" to set) }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(500)

        rule.onNodeWithText("Deshacer eliminar serie").assertDoesNotExist()
    }

    @Test
    fun theStackShrinkingAfterAnUndoHidesThePill() {
        show()
        rule.runOnUiThread { stack = listOf(entry("Omitir serie")) }
        rule.waitForIdle()

        rule.runOnUiThread { stack = emptyList() }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(500)

        rule.onNodeWithText("Deshacer omitir serie").assertDoesNotExist()
    }
}
