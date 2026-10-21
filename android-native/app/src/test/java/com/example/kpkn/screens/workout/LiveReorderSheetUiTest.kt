package com.example.kpkn.screens.workout

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.center
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.up
import androidx.compose.ui.zIndex
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Session
import com.example.kpkn.ui.components.KpknOverlayHostContent
import com.example.kpkn.ui.components.KpknOverlayHostController
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.theme.KPKNTheme
import com.example.kpkn.ui.tour.TourSeenStore
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private object NoToursForReorderSheet : TourSeenStore {
    override fun seenIds(): Set<String> = emptySet()
    override fun markSeen(tourId: String) = Unit
}

/**
 * La hoja «Orden de la sesión» (LV11, A3; prototipo C3) con la pieza real: lo hecho y lo que está en curso queda fijo y sin asa, el orden
 * se cambia arrastrando (o con «Subir» y «Bajar» para el lector de pantalla) y «Seleccionar» abre las acciones por lotes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h880dp-xxhdpi", application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiveReorderSheetUiTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun show(content: @Composable () -> Unit) {
        rule.setContent {
            KPKNTheme {
                val host = remember { KpknOverlayHostController(tourSeen = NoToursForReorderSheet) }
                CompositionLocalProvider(LocalKpknOverlayHost provides host) {
                    Box(Modifier.fillMaxSize()) {
                        content()
                        if (host.entries.isNotEmpty()) {
                            Box(Modifier.fillMaxSize().zIndex(400f)) { KpknOverlayHostContent(host) }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun click(text: String) {
        rule.onNodeWithText(text).performClick()
        rule.waitForIdle()
    }

    // ── C3 · Orden de la sesión ────────────────────────────────────────────────────────────────────────────────────────────────

    /** a: hecho (sus dos series); b: en curso (una de dos); c, d y e: sin empezar. */
    private val reorderExercises = listOf("a", "b", "c", "d", "e").map { liveExercise(it, sets = 2, name = "Ejercicio ${it.uppercase()}") }
    private val reorderCompleted = listOf("a_0", "a_1", "b_0").associateWith { CompletedSet(id = "c-$it", weight = 40.0, reps = 8) }

    private class ReorderCalls {
        var applied: List<String>? = null
        var appliedGlobally: Boolean? = null
        var superset: List<String>? = null
        var skipped: List<String>? = null
    }

    private fun showReorder(
        calls: ReorderCalls,
        state: WorkoutStructureSheetsState = WorkoutStructureSheetsState().apply {
            showReorderSheet = true
            reorderSheetExerciseIds = reorderExercises.map { it.id }
        },
    ): WorkoutStructureSheetsState {
        show {
            WorkoutReorderSheets(
                state = state,
                visibleExercises = reorderExercises,
                originalExercisePartMap = reorderExercises.associate { it.id to "Sesión principal" },
                onApplyReorder = { ids, _, global ->
                    calls.applied = ids
                    calls.appliedGlobally = global
                },
                session = Session(id = "s", name = "Sesión", exercises = reorderExercises),
                completedSets = reorderCompleted,
                omittedSetKeys = emptySet(),
                onCreateSuperset = { calls.superset = it },
                onSkipExercises = { calls.skipped = it },
            )
        }
        return state
    }

    private fun handle(index: Int) = rule.onAllNodesWithContentDescription("Arrastrar para reordenar")[index]

    private fun accessibilityMove(handleIndex: Int, label: String) {
        val node = handle(handleIndex)
        rule.runOnUiThread { node.fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == label }.action() }
        rule.waitForIdle()
    }

    @Test
    fun theDoneAndInProgressRowsAreFixedAndHaveNoHandle() {
        showReorder(ReorderCalls())

        // Solo c, d y e se pueden arrastrar.
        rule.onAllNodesWithContentDescription("Arrastrar para reordenar").assertCountEquals(3)
        rule.onNodeWithText("en curso · 1/2").assertExists()
        rule.onNodeWithText("Orden de la sesión").assertExists()
    }

    @Test
    fun nothingCanBeMovedInFrontOfWhatIsDoneOrInProgress() {
        val state = showReorder(ReorderCalls())

        // «c» es la primera libre: subirla no la pone delante de lo fijo.
        accessibilityMove(handleIndex = 0, label = "Subir")

        assertEquals(listOf("a", "b", "c", "d", "e"), state.reorderSheetExerciseIds)
    }

    @Test
    fun aFreeRowMovesDownWithTheAccessibilityActionAndKeepsTheFixedOnesOnTop() {
        val state = showReorder(ReorderCalls())

        accessibilityMove(handleIndex = 0, label = "Bajar")

        assertEquals(listOf("a", "b", "d", "c", "e"), state.reorderSheetExerciseIds)
    }

    @Test
    fun draggingAFreeRowByItsHandleMovesItBelowItsNeighbour() {
        val state = showReorder(ReorderCalls())

        // El asa de «c» baja un poco más de una fila: «c» pasa detrás de «d».
        handle(0).performTouchInput {
            down(center)
            moveBy(Offset(0f, 230f))
            up()
        }
        rule.waitForIdle()

        assertEquals(listOf("a", "b", "d", "c", "e"), state.reorderSheetExerciseIds)
    }

    @Test
    fun draggingAFreeRowUpOverTheFixedOnesStopsRightBehindThem() {
        val state = showReorder(ReorderCalls())

        handle(1).performTouchInput {
            down(center)
            moveBy(Offset(0f, -900f))
            up()
        }
        rule.waitForIdle()

        assertEquals(
            "«d» sube hasta lo más alto que puede: detrás de lo fijo y como mucho delante de «c»",
            listOf("a", "b", "d", "c", "e"),
            state.reorderSheetExerciseIds,
        )
    }

    @Test
    fun doneAppliesTheNewOrderAndAnUnchangedOrderJustCloses() {
        val calls = ReorderCalls()
        val state = showReorder(calls)

        click("Listo")
        assertEquals("sin cambios no se aplica nada", null, calls.applied)
        assertEquals(false, state.showReorderSheet)
    }

    @Test
    fun doneAppliesWhatWasMoved() {
        val calls = ReorderCalls()
        val state = showReorder(calls)
        accessibilityMove(handleIndex = 0, label = "Bajar")

        click("Listo")

        assertEquals(listOf("a", "b", "d", "c", "e"), calls.applied)
        assertEquals(false, calls.appliedGlobally)
        assertEquals(false, state.showReorderSheet)
    }

    @Test
    fun theSelectionModeMarksOnlyFreeRowsAndRunsTheBatchActions() {
        val calls = ReorderCalls()
        val state = showReorder(calls)

        click("Seleccionar")
        // Lo fijo no se marca.
        click("Ejercicio A")
        click("Ejercicio B")
        rule.onNodeWithText("Marca los ejercicios").assertExists()
        rule.onNodeWithText("Superserie").assertIsNotEnabled()
        rule.onNodeWithText("Omitir").assertIsNotEnabled()

        click("Ejercicio C")
        click("Ejercicio D")
        rule.onNodeWithText("2 ejercicios marcados").assertExists()
        rule.onNodeWithText("Superserie").assertIsEnabled()
        click("Superserie")

        assertEquals(listOf("c", "d"), calls.superset)
        rule.onNodeWithText("Arrastra desde el asa").assertExists()

        click("Seleccionar")
        click("Ejercicio E")
        click("Omitir")
        assertEquals(listOf("e"), calls.skipped)

        click("Seleccionar")
        click("Ejercicio C")
        click("Quitar")
        assertEquals("«Quitar» pide confirmar antes de quitar", listOf("c"), state.pendingRemoveExerciseIds)

        click("Seleccionar")
        click("Ejercicio D")
        click("Ultrarrápido")
        assertEquals(listOf("d"), state.ultraFastTargetIds)
    }

    @Test
    fun theNoteExplainsWhatIsFixedAndWhereTheNewOrderShows() {
        showReorder(ReorderCalls())

        rule.onNodeWithText("Lo hecho y lo que está en curso queda fijo. El orden nuevo se ve en el carrusel y en «Siguiente».").assertExists()
    }
}
