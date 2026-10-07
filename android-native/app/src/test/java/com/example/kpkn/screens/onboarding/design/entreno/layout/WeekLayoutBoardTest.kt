package com.example.kpkn.screens.onboarding.design.entreno.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * El tablero de la semana con Compose sobre Robolectric (la tubería de punteros y de semántica de verdad, sin
 * dispositivo): qué marcas de prueba tiene, qué anuncia TalkBack, el «tocar y tocar» y la pulsación larga con
 * arrastre, las acciones «Mover a…» y el carril de repartos con sus botones.
 *
 * Va con «reducir movimiento» (sin resortes ni bucles) y en una pantalla ancha, para que las siete columnas quepan.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1000dp-h900dp", application = android.app.Application::class)
class WeekLayoutBoardTest {

    @get:Rule
    val rule = createComposeRule()

    private val sessions = listOf(
        WeekLayoutSession("a", "Torso A", "Pecho y espalda", 60, 6, isMain = true),
        WeekLayoutSession("b", "Pierna A", "Cuádriceps y glúteos", 55, 5, isMain = false),
        WeekLayoutSession("c", "Torso B", "Hombros y brazos", 50, 6, isMain = false),
    )
    private val splits = listOf(
        SplitOption("tp", "Torso y pierna", "3 días", listOf("Torso", "Pierna", "Torso")),
        SplitOption("ppl", "Empuje, tirón, pierna", "3 días", listOf("Empuje", "Tirón", "Pierna")),
        SplitOption("fb", "Cuerpo completo", "3 días", listOf("A", "B", "A")),
    )

    private var assignment by mutableStateOf(mapOf(1 to "a", 3 to "b", 5 to "c"))
    private var currentSplit by mutableStateOf<String?>("tp")
    private var resettable by mutableStateOf(false)
    private var loading by mutableStateOf(false)
    private val moves = mutableListOf<Pair<String, Int>>()
    private val adapted = mutableListOf<String>()
    private var resets = 0
    private val state = WeekLayoutDragState()

    private fun show(
        start: Int = 1,
        shown: List<WeekLayoutSession> = sessions,
        withSplits: Boolean = true,
        applyMoves: Boolean = true,
        wrap: @Composable (@Composable () -> Unit) -> Unit = { it() },
    ) {
        rule.setContent {
            wrap {
                WeekLayoutBoardContent(
                    weekStartDay = start,
                    sessions = shown,
                    assignment = assignment,
                    onMove = { id, day ->
                        moves += id to day
                        if (applyMoves) assignment = swapAssignment(assignment, id, day)
                    },
                    splitOptions = if (withSplits) splits else emptyList(),
                    selectedSplitId = currentSplit,
                    onAdaptSplit = { adapted += it },
                    canReset = resettable,
                    onReset = { resets++ },
                    adapting = loading,
                    splitNotice = null,
                    reduced = true,
                    state = state,
                )
            }
        }
    }

    private fun sessionNode(id: String) = rule.onNodeWithTag(weekLayoutSessionTag(id))
    private fun slotNode(day: Int) = rule.onNodeWithTag(weekLayoutSlotTag(day))
    private fun centerOf(tag: String) = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.center

    // ------------------------------------------------------------ marcas y orden

    @Test
    fun everySlotSessionAndSplitHasItsTag() {
        show()
        for (day in 1..7) slotNode(day).assertExists()
        for (s in sessions) sessionNode(s.id).assertExists()
        for (o in splits) rule.onNodeWithTag(weekLayoutSplitTag(o.id)).assertExists()
        rule.onNodeWithTag(WEEK_LAYOUT_BOARD_TAG).assertExists()
        assertEquals("setup-layout-slot-3", weekLayoutSlotTag(3))
        assertEquals("setup-layout-session-a", weekLayoutSessionTag("a"))
    }

    @Test
    fun theSlotsFollowTheWeekStart() {
        show(start = 4)
        val lefts = slotOrder(4).map { rule.onNodeWithTag(weekLayoutSlotTag(it)).fetchSemanticsNode().boundsInRoot.left }
        assertEquals("de izquierda a derecha", lefts.sorted(), lefts)
        assertTrue(lefts.toSet().size == 7)
    }

    @Test
    fun aSessionSitsOnItsDaySlot() {
        show()
        for ((day, id) in assignment) {
            val ficha = centerOf(weekLayoutSessionTag(id))
            val slot = centerOf(weekLayoutSlotTag(day))
            assertEquals("$id en el día $day", slot.x, ficha.x, 2f)
        }
    }

    @Test
    fun whenTheAssignmentChangesTheSessionGoesToTheNewSlot() {
        show()
        rule.runOnIdle { assignment = mapOf(2 to "a", 3 to "b", 7 to "c") }
        rule.waitForIdle()
        assertEquals(centerOf(weekLayoutSlotTag(2)).x, centerOf(weekLayoutSessionTag("a")).x, 2f)
        assertEquals(centerOf(weekLayoutSlotTag(7)).x, centerOf(weekLayoutSessionTag("c")).x, 2f)
    }

    @Test
    fun sessionsWithoutADayOrDaysWithUnknownSessionsAreIgnored() {
        assignment = mapOf(1 to "a", 2 to "ghost", 9 to "b")
        show()
        sessionNode("a").assertExists()
        sessionNode("b").assertDoesNotExist()
        sessionNode("ghost").assertDoesNotExist()
        sessionNode("c").assertDoesNotExist()
    }

    // ------------------------------------------------------------ TalkBack

    @Test
    fun aSessionIsAnnouncedAsOneButtonWithItsDayAndDuration() {
        show()
        sessionNode("a").assertContentDescriptionEquals(
            "Torso A, sesión principal. Pecho y espalda. 60 minutos, 6 ejercicios. Lunes.",
        )
        sessionNode("b").assertContentDescriptionEquals("Pierna A. Cuádriceps y glúteos. 55 minutos, 5 ejercicios. Miércoles.")
        assertEquals(Role.Button, sessionNode("a").fetchSemanticsNode().config[SemanticsProperties.Role])
    }

    @Test
    fun everySessionOffersMoveActionsToTheOtherSixDays() {
        show()
        val labels = sessionNode("a").fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }
        assertEquals(
            listOf("Mover a martes", "Mover a miércoles", "Mover a jueves", "Mover a viernes", "Mover a sábado", "Mover a domingo"),
            labels,
        )
    }

    @Test
    fun theMoveActionsFollowTheStripOrderAndSkipTheOwnDay() {
        assignment = mapOf(4 to "a")
        show(start = 4)
        val labels = sessionNode("a").fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }
        assertEquals(
            listOf("Mover a viernes", "Mover a sábado", "Mover a domingo", "Mover a lunes", "Mover a martes", "Mover a miércoles"),
            labels,
        )
    }

    @Test
    fun aMoveActionMovesTheSession() {
        show()
        val actions = sessionNode("a").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        rule.runOnUiThread { assertTrue(actions.first { it.label == "Mover a martes" }.action()) }
        rule.waitForIdle()
        assertEquals(listOf("a" to 2), moves)
        assertEquals(mapOf(2 to "a", 3 to "b", 5 to "c"), assignment)
    }

    @Test
    fun aMoveActionOntoAnOccupiedDayAsksForTheSwap() {
        show()
        val actions = sessionNode("a").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        rule.runOnUiThread { actions.first { it.label == "Mover a miércoles" }.action() }
        rule.waitForIdle()
        assertEquals(listOf("a" to 3), moves)
        assertEquals(mapOf(1 to "b", 3 to "a", 5 to "c"), assignment)
    }

    @Test
    fun theStateOfASelectedSessionIsAnnounced() {
        show()
        assertNull(sessionNode("a").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription))
        sessionNode("a").performClick()
        rule.waitForIdle()
        assertEquals("Seleccionada", sessionNode("a").fetchSemanticsNode().config[SemanticsProperties.StateDescription])
    }

    @Test
    fun theSemanticClickSelectsTheSession() {
        show()
        rule.runOnUiThread {
            val config = sessionNode("a").fetchSemanticsNode().config
            config[SemanticsActions.OnClick].action?.invoke()
        }
        rule.waitForIdle()
        assertEquals("a", state.selectedId)
    }

    // ------------------------------------------------------------ tocar y tocar

    @Test
    fun tappingASessionSelectsItAndTappingAFreeDayMovesIt() {
        show()
        sessionNode("a").performClick()
        rule.waitForIdle()
        assertEquals("a", state.selectedId)
        assertTrue(moves.isEmpty())
        slotNode(2).performClick()
        rule.waitForIdle()
        assertEquals(listOf("a" to 2), moves)
        assertNull(state.selectedId)
        assertEquals(mapOf(2 to "a", 3 to "b", 5 to "c"), assignment)
    }

    @Test
    fun tappingAnOccupiedDaySwapsTheSessions() {
        show()
        sessionNode("a").performClick()
        slotNode(3).performClick()
        rule.waitForIdle()
        assertEquals(listOf("a" to 3), moves)
        assertEquals(mapOf(1 to "b", 3 to "a", 5 to "c"), assignment)
    }

    @Test
    fun tappingAnotherSessionWhileOneIsSelectedSwapsThem() {
        show()
        sessionNode("a").performClick()
        sessionNode("c").performClick()
        rule.waitForIdle()
        assertEquals(listOf("a" to 5), moves)
    }

    @Test
    fun tappingTheSelectedSessionAgainUnselectsIt() {
        show()
        sessionNode("a").performClick()
        sessionNode("a").performClick()
        rule.waitForIdle()
        assertNull(state.selectedId)
        assertTrue(moves.isEmpty())
    }

    @Test
    fun tappingARestDayWithNothingSelectedDoesNothing() {
        show()
        slotNode(2).performClick()
        rule.waitForIdle()
        assertNull(state.selectedId)
        assertTrue(moves.isEmpty())
    }

    @Test
    fun theHintGuidesEachMoment() {
        show()
        val status = rule.onNodeWithTag(WEEK_LAYOUT_STATUS_TAG)
        status.assertTextEquals(WeekLayoutCopy.HINT_IDLE)
        sessionNode("a").performClick()
        rule.waitForIdle()
        status.assertTextEquals(WeekLayoutCopy.hintSelected("Torso A"))
        slotNode(2).performClick()
        rule.waitForIdle()
        status.assertTextEquals("Torso A pasa al martes.")
    }

    @Test
    fun noSessionsGivesTheEmptyHint() {
        assignment = emptyMap()
        show(shown = emptyList())
        rule.onNodeWithTag(WEEK_LAYOUT_STATUS_TAG).assertTextEquals(WeekLayoutCopy.HINT_EMPTY)
        for (day in 1..7) slotNode(day).assertExists()
    }

    @Test
    fun anOwnerThatIgnoresTheMoveLeavesTheBoardAsItWas() {
        show(applyMoves = false)
        sessionNode("a").performClick()
        slotNode(2).performClick()
        rule.waitForIdle()
        assertEquals(listOf("a" to 2), moves)
        assertEquals(mapOf(1 to "a", 3 to "b", 5 to "c"), assignment)
        // De inmediato se dibuja lo pedido…
        assertEquals(centerOf(weekLayoutSlotTag(2)).x, centerOf(weekLayoutSessionTag("a")).x, 2f)
        // …y si la respuesta no llega, el tablero vuelve a lo que se le dio (no inventa una colocación).
        rule.mainClock.advanceTimeBy(1500)
        rule.waitForIdle()
        assertEquals(centerOf(weekLayoutSlotTag(1)).x, centerOf(weekLayoutSessionTag("a")).x, 2f)
    }

    // ------------------------------------------------------------ pulsación larga y arrastre

    @Test
    fun aLongPressAndDragMovesTheSession() {
        show()
        rule.mainClock.autoAdvance = false
        val from = centerOf(weekLayoutSessionTag("a"))
        val to = centerOf(weekLayoutSlotTag(2))
        rule.onRoot().performTouchInput { down(from) }
        // Más que la pulsación larga del sistema: la ficha se levanta.
        rule.mainClock.advanceTimeBy(800)
        assertEquals("a", state.liftedId)
        rule.onRoot().performTouchInput {
            moveTo(Offset((from.x + to.x) / 2f, from.y))
            moveTo(Offset(to.x, from.y))
        }
        assertEquals(2, state.hoverDay)
        rule.onRoot().performTouchInput { up() }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertEquals(listOf("a" to 2), moves)
        assertNull(state.liftedId)
        assertEquals(mapOf(2 to "a", 3 to "b", 5 to "c"), assignment)
    }

    @Test
    fun releasingOutsideTheWeekCancelsTheMove() {
        show()
        rule.mainClock.autoAdvance = false
        val from = centerOf(weekLayoutSessionTag("a"))
        rule.onRoot().performTouchInput { down(from) }
        rule.mainClock.advanceTimeBy(800)
        assertEquals("a", state.liftedId)
        rule.onRoot().performTouchInput { moveTo(Offset(from.x + 250f, from.y + 400f)) }
        assertNull("fuera de la semana no hay destino", state.hoverDay)
        rule.onRoot().performTouchInput { up() }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertTrue(moves.isEmpty())
        assertNull(state.liftedId)
        assertEquals(mapOf(1 to "a", 3 to "b", 5 to "c"), assignment)
    }

    @Test
    fun aQuickSwipeDoesNotLiftAnything() {
        show()
        val from = centerOf(weekLayoutSessionTag("a"))
        rule.onRoot().performTouchInput {
            down(from)
            moveTo(Offset(from.x + 200f, from.y))
            up()
        }
        rule.waitForIdle()
        assertNull(state.liftedId)
        assertTrue(moves.isEmpty())
    }

    @Test
    fun aLongPressOnARestDayLiftsNothing() {
        show()
        rule.mainClock.autoAdvance = false
        val rest = centerOf(weekLayoutSlotTag(2))
        rule.onRoot().performTouchInput { down(rest) }
        rule.mainClock.advanceTimeBy(800)
        assertNull(state.liftedId)
        rule.onRoot().performTouchInput { up() }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertTrue(moves.isEmpty())
    }

    @Test
    fun hoveringAnOccupiedDayShowsTheSwapBeforeDropping() {
        show()
        val geometry = state.geometry
        // Se levanta «a» y se la pasa sobre el miércoles (ocupado por «b»): «b» ya se va al hueco del lunes.
        rule.runOnUiThread {
            state.lift("a", geometry.home(1) + Offset(geometry.colWidth / 2f, 60f))
            state.dragTo(geometry.home(3) + Offset(geometry.colWidth / 2f, 60f))
        }
        rule.waitForIdle()
        assertEquals(3, state.hoverDay)
        rule.onNodeWithTag(WEEK_LAYOUT_STATUS_TAG).assertTextEquals(WeekLayoutCopy.hintDropOn(3))
        assertEquals(centerOf(weekLayoutSlotTag(1)).x, centerOf(weekLayoutSessionTag("b")).x, 2f)
        // Se aparta fuera de la semana: «b» vuelve a su sitio.
        rule.runOnUiThread { state.dragTo(geometry.home(3) + Offset(geometry.colWidth / 2f, 400f)) }
        rule.waitForIdle()
        assertNull(state.hoverDay)
        assertEquals(centerOf(weekLayoutSlotTag(3)).x, centerOf(weekLayoutSessionTag("b")).x, 2f)
        rule.onNodeWithTag(WEEK_LAYOUT_STATUS_TAG).assertTextEquals(WeekLayoutCopy.HINT_DRAGGING)
        rule.runOnUiThread { state.cancel() }
        rule.waitForIdle()
        assertTrue(moves.isEmpty())
    }

    @Test
    fun whileAdaptingTheBoardIgnoresTouches() {
        loading = true
        show()
        sessionNode("a").performClick()
        rule.waitForIdle()
        assertNull(state.selectedId)
    }

    @Test
    fun whileAdaptingTheMoveActionsDoNothingEither() {
        loading = true
        show()
        val actions = sessionNode("a").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        rule.runOnUiThread { actions.first { it.label == "Mover a martes" }.action() }
        rule.waitForIdle()
        assertTrue(moves.isEmpty())
        assertEquals(mapOf(1 to "a", 3 to "b", 5 to "c"), assignment)
    }

    @Test
    fun startingToAdaptDropsASelection() {
        show()
        sessionNode("a").performClick()
        assertEquals("a", state.selectedId)
        rule.runOnIdle { loading = true }
        rule.waitForIdle()
        assertNull(state.selectedId)
    }

    // ------------------------------------------------------------ pantalla estrecha y letra grande

    @Test
    fun theStripScrollsOnANarrowScreenAndNotOnAWideOne() {
        show()
        val wide = rule.onNodeWithTag(WEEK_LAYOUT_STRIP_TAG).fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.HorizontalScrollAxisRange)
        assertEquals("en 1000 dp caben las siete", 0f, wide?.maxValue?.invoke() ?: 0f, 0.5f)
    }

    @Test
    fun at360dpWithBigTextTheStripScrollsAndTheColumnsGrow() {
        var screenDensity = 1f
        show(wrap = { content ->
            screenDensity = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.3f)) {
                Box(Modifier.width(360.dp)) { content() }
            }
        })
        val strip = rule.onNodeWithTag(WEEK_LAYOUT_STRIP_TAG).fetchSemanticsNode()
        val range = strip.config[SemanticsProperties.HorizontalScrollAxisRange]
        assertTrue("la tira se desplaza", range.maxValue() > 0f)
        // Con letra al 130 % la columna ya no es de 92 dp: cabe «6 ejercicios».
        val a = sessionNode("a").fetchSemanticsNode().boundsInRoot
        assertTrue("columna de ${a.width / screenDensity} dp", a.width / screenDensity >= 110f)
    }

    @Test
    fun theStripOpensWithTheFirstSessionInView() {
        // Una sola sesión, el sábado: en 360 dp queda lejos del inicio de la semana (el lunes) y la tira arranca con ella a la vista.
        assignment = mapOf(6 to "a")
        var screenDensity = 1f
        show(shown = listOf(sessions[0]), wrap = { content ->
            screenDensity = LocalDensity.current.density
            Box(Modifier.width(360.dp)) { content() }
        })
        rule.waitForIdle()
        val ficha = sessionNode("a").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "a la vista: ${ficha.left}..${ficha.right}",
            ficha.left >= -1f && ficha.right <= 360f * screenDensity + 1f,
        )
    }

    // ------------------------------------------------------------ repartos

    @Test
    fun tappingAnotherSplitShowsTheAdaptButtonAndAppliesIt() {
        show()
        rule.onNodeWithTag(WEEK_LAYOUT_ADAPT_TAG).assertDoesNotExist()
        rule.onNodeWithTag(weekLayoutSplitTag("ppl")).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(WEEK_LAYOUT_ADAPT_TAG).assertExists()
        rule.onNodeWithText("Adaptar mi programa a este reparto").assertExists()
        rule.onNodeWithTag(WEEK_LAYOUT_ADAPT_TAG).performClick()
        assertEquals(listOf("ppl"), adapted)
    }

    @Test
    fun theCurrentSplitNeverOffersToAdaptItself() {
        show()
        rule.onNodeWithTag(weekLayoutSplitTag("tp")).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(WEEK_LAYOUT_ADAPT_TAG).assertDoesNotExist()
    }

    @Test
    fun tappingTheSameSplitTwiceClearsTheChoice() {
        show()
        rule.onNodeWithTag(weekLayoutSplitTag("ppl")).performClick()
        rule.onNodeWithTag(WEEK_LAYOUT_ADAPT_TAG).assertExists()
        rule.onNodeWithTag(weekLayoutSplitTag("ppl")).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(WEEK_LAYOUT_ADAPT_TAG).assertDoesNotExist()
    }

    @Test
    fun pickingTheCurrentSplitClearsAPendingChoice() {
        show()
        rule.onNodeWithTag(weekLayoutSplitTag("ppl")).performClick()
        rule.onNodeWithTag(weekLayoutSplitTag("tp")).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(WEEK_LAYOUT_ADAPT_TAG).assertDoesNotExist()
    }

    @Test
    fun whenTheCurrentSplitChangesThePendingChoiceIsDone() {
        show()
        rule.onNodeWithTag(weekLayoutSplitTag("ppl")).performClick()
        rule.onNodeWithTag(WEEK_LAYOUT_ADAPT_TAG).assertExists()
        rule.runOnIdle { currentSplit = "ppl" }
        rule.waitForIdle()
        rule.onNodeWithTag(WEEK_LAYOUT_ADAPT_TAG).assertDoesNotExist()
        rule.onNodeWithTag(weekLayoutSplitTag("ppl")).assertIsSelected()
    }

    @Test
    fun theLitSplitIsTheTappedOneOrElseTheCurrentOne() {
        show()
        rule.onNodeWithTag(weekLayoutSplitTag("tp")).assertIsSelected()
        rule.onNodeWithTag(weekLayoutSplitTag("fb")).assertIsNotSelected()
        rule.onNodeWithTag(weekLayoutSplitTag("fb")).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(weekLayoutSplitTag("fb")).assertIsSelected()
        rule.onNodeWithTag(weekLayoutSplitTag("tp")).assertIsNotSelected()
    }

    @Test
    fun splitSymbolsAnnounceTheirRoleAndWhichOneIsCurrent() {
        show()
        rule.onNodeWithTag(weekLayoutSplitTag("tp")).assertContentDescriptionEquals(
            "Torso y pierna. 3 días. Torso, Pierna, Torso. Reparto actual.",
        )
        rule.onNodeWithTag(weekLayoutSplitTag("ppl")).assertContentDescriptionEquals(
            "Empuje, tirón, pierna. 3 días. Empuje, Tirón, Pierna.",
        )
        assertEquals(Role.RadioButton, rule.onNodeWithTag(weekLayoutSplitTag("tp")).fetchSemanticsNode().config[SemanticsProperties.Role])
    }

    @Test
    fun resetShowsOnlyWhenThereIsSomethingToReset() {
        show()
        rule.onNodeWithTag(WEEK_LAYOUT_RESET_TAG).assertDoesNotExist()
        rule.runOnIdle { resettable = true }
        rule.waitForIdle()
        rule.onNodeWithText("Restablecer").assertExists()
        rule.onNodeWithTag(WEEK_LAYOUT_RESET_TAG).performClick()
        assertEquals(1, resets)
    }

    @Test
    fun theActionsHaveAtLeastA48dpTouchTarget() {
        resettable = true
        show()
        rule.onNodeWithTag(weekLayoutSplitTag("ppl")).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(WEEK_LAYOUT_RESET_TAG).assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag(WEEK_LAYOUT_ADAPT_TAG).assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun whileAdaptingTheLoadingLineReplacesTheButtonAndTheRailIsDisabled() {
        show()
        rule.onNodeWithTag(weekLayoutSplitTag("ppl")).performClick()
        rule.onNodeWithTag(WEEK_LAYOUT_ADAPT_TAG).assertExists()
        rule.runOnIdle { loading = true }
        rule.waitForIdle()
        rule.onNodeWithTag(WEEK_LAYOUT_ADAPT_TAG).assertDoesNotExist()
        rule.onNodeWithTag(WEEK_LAYOUT_ADAPTING_TAG).assertExists()
        rule.onNodeWithTag(WEEK_LAYOUT_ADAPTING_TAG).assertContentDescriptionEquals("Adaptando tu semana…")
        rule.onNodeWithTag(weekLayoutSplitTag("fb")).assertIsNotEnabled()
        rule.runOnIdle { loading = false }
        rule.waitForIdle()
        rule.onNodeWithTag(WEEK_LAYOUT_ADAPTING_TAG).assertDoesNotExist()
    }

    @Test
    fun withoutSplitsThereIsNoRailButTheNoteStays() {
        show(withSplits = false)
        rule.onNodeWithTag(WEEK_LAYOUT_RAIL_TAG).assertDoesNotExist()
        rule.onNodeWithTag(WEEK_LAYOUT_RESET_TAG).assertDoesNotExist()
        rule.onNodeWithText(WeekLayoutCopy.FOOTNOTE).assertExists()
    }

    @Test
    fun theFixedNoteIsAlwaysAtTheFoot() {
        show()
        rule.onNodeWithText("Puedes cambiar todo esto cuando quieras desde tu programa.").assertExists()
    }

    @Test
    fun aSplitNoticeAppearsWithTheAdaptButton() {
        rule.setContent {
            WeekLayoutBoardContent(
                weekStartDay = 1, sessions = sessions, assignment = assignment, onMove = { _, _ -> },
                splitOptions = splits, selectedSplitId = "tp", onAdaptSplit = {}, canReset = false, onReset = {},
                adapting = false, splitNotice = "Este programa trae su reparto de autor.", reduced = true,
                state = state,
            )
        }
        rule.onNodeWithText("Este programa trae su reparto de autor.").assertDoesNotExist()
        rule.onNodeWithTag(weekLayoutSplitTag("ppl")).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Este programa trae su reparto de autor.").assertExists()
    }
}
