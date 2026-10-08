package com.example.kpkn.screens.onboarding.design.entreno.layout

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
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
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.LocalWizardPageScroll
import com.example.kpkn.screens.onboarding.design.WizardPageScroll
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
 * dispositivo): qué marcas de prueba tiene, qué anuncia TalkBack, el «tocar y tocar», la pulsación larga y el arrastre
 * desde el asa, las acciones «Mover a…», que los siete días caben a 360 y a 320 dp (con la letra grande) sin desplazarse de
 * lado y el carril de repartos con sus botones.
 *
 * Va con «reducir movimiento» (sin resortes ni bucles) y en una pantalla de 360 × 900 dp con densidad 1 (un dp es un píxel).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h900dp", application = android.app.Application::class)
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

    /** El umbral de movimiento del sistema en este entorno (en Robolectric son 16 px, no 8): lo lee [show] del contexto de la composición. */
    private var touchSlop = 8f

    private fun show(
        start: Int = 1,
        shown: List<WeekLayoutSession> = sessions,
        withSplits: Boolean = true,
        applyMoves: Boolean = true,
        wrap: @Composable (@Composable () -> Unit) -> Unit = { it() },
    ) {
        rule.setContent {
            touchSlop = LocalViewConfiguration.current.touchSlop
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
    private fun boundsOf(tag: String) = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
    private fun centerOf(tag: String) = boundsOf(tag).center

    // ------------------------------------------------------------ marcas y orden

    @Test
    fun everySlotSessionAndSplitHasItsTag() {
        show()
        for (day in 1..7) slotNode(day).assertExists()
        for (s in sessions) sessionNode(s.id).assertExists()
        for (o in splits) rule.onNodeWithTag(weekLayoutSplitTag(o.id)).assertExists()
        rule.onNodeWithTag(WEEK_LAYOUT_BOARD_TAG).assertExists()
        rule.onNodeWithTag(WEEK_LAYOUT_LIST_TAG).assertExists()
        assertEquals("setup-layout-slot-3", weekLayoutSlotTag(3))
        assertEquals("setup-layout-session-a", weekLayoutSessionTag("a"))
    }

    @Test
    fun theRowsFollowTheWeekStartFromTopToBottom() {
        show(start = 4)
        val tops = slotOrder(4).map { boundsOf(weekLayoutSlotTag(it)).top }
        assertEquals("de arriba abajo", tops.sorted(), tops)
        assertTrue(tops.toSet().size == 7)
    }

    @Test
    fun theSevenRowsStackWithoutGapsAndHaveTheSameHeight() {
        show()
        val rows = (1..7).map { boundsOf(weekLayoutSlotTag(it)) }
        for (i in 1 until rows.size) {
            assertEquals("la fila ${i + 1} empieza donde acaba la anterior", rows[i - 1].bottom, rows[i].top, 0.5f)
        }
        assertEquals("todas miden lo mismo", 1, rows.map { Math.round(it.height) }.toSet().size)
        assertTrue("cada fila es un objetivo táctil holgado: ${rows[0].height}", rows[0].height >= 48f)
    }

    @Test
    fun aSessionSitsOnItsDayRow() {
        show()
        for ((day, id) in assignment) {
            val ficha = centerOf(weekLayoutSessionTag(id))
            val slot = centerOf(weekLayoutSlotTag(day))
            assertEquals("$id en el día $day", slot.y, ficha.y, 2f)
        }
    }

    @Test
    fun whenTheAssignmentChangesTheSessionGoesToTheNewRow() {
        show()
        rule.runOnIdle { assignment = mapOf(2 to "a", 3 to "b", 7 to "c") }
        rule.waitForIdle()
        assertEquals(centerOf(weekLayoutSlotTag(2)).y, centerOf(weekLayoutSessionTag("a")).y, 2f)
        assertEquals(centerOf(weekLayoutSlotTag(7)).y, centerOf(weekLayoutSessionTag("c")).y, 2f)
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
    fun aRestDayIsReadAsRestAndAnOccupiedOneLeavesItToItsSession() {
        show()
        slotNode(2).assertContentDescriptionEquals("Martes, descanso")
        assertNull(
            "con una sesión encima, la fila no dice nada: lo dice su ficha",
            slotNode(1).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription),
        )
    }

    @Test
    fun withASessionChosenARestDayOffersToReceiveIt() {
        show()
        assertNull(slotNode(2).fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick))
        sessionNode("a").performClick()
        rule.waitForIdle()
        val action = slotNode(2).fetchSemanticsNode().config[SemanticsActions.OnClick]
        assertEquals("Mover aquí la sesión elegida", action.label)
        rule.runOnUiThread { action.action?.invoke() }
        rule.waitForIdle()
        assertEquals(listOf("a" to 2), moves)
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
    fun theMoveActionsFollowTheWeekOrderAndSkipTheOwnDay() {
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
        assertEquals(centerOf(weekLayoutSlotTag(2)).y, centerOf(weekLayoutSessionTag("a")).y, 2f)
        // …y si la respuesta no llega, el tablero vuelve a lo que se le dio (no inventa una colocación).
        rule.mainClock.advanceTimeBy(1500)
        rule.waitForIdle()
        assertEquals(centerOf(weekLayoutSlotTag(1)).y, centerOf(weekLayoutSessionTag("a")).y, 2f)
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
            moveTo(Offset(from.x, (from.y + to.y) / 2f))
            moveTo(Offset(from.x, to.y))
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
    fun draggingFromTheHandleLiftsTheSessionAtOnceWithoutWaitingForALongPress() {
        show()
        rule.waitForIdle()
        // Con el reloj parado la pulsación larga no puede cumplirse: si la ficha se levanta, es por el arrastre desde el asa.
        rule.mainClock.autoAdvance = false
        val row = boundsOf(weekLayoutSlotTag(1))
        val third = boundsOf(weekLayoutSlotTag(3))
        // El asa son los últimos 44 dp de la fila.
        val handle = Offset(row.right - 20f, row.center.y)
        // Un movimiento vertical algo mayor que el umbral del sistema.
        val moved = touchSlop * 2f + 2f
        rule.onRoot().performTouchInput {
            down(handle)
            moveTo(Offset(handle.x, handle.y + moved))
        }
        rule.mainClock.advanceTimeByFrame()
        assertEquals("a", state.liftedId)
        // La ficha se agarró `moved` px más abajo de donde se tocó: para que su centro caiga sobre la tercera fila, el dedo va esos px
        // por debajo del centro de la fila.
        rule.onRoot().performTouchInput { moveTo(Offset(handle.x, third.center.y + moved)) }
        rule.mainClock.advanceTimeByFrame()
        assertEquals(3, state.hoverDay)
        rule.onRoot().performTouchInput { up() }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertEquals(listOf("a" to 3), moves)
        assertNull(state.liftedId)
        assertEquals(mapOf(1 to "b", 3 to "a", 5 to "c"), assignment)
    }

    @Test
    fun aMovementBelowTheSystemThresholdFromTheHandleDoesNotLiftYet() {
        show()
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        val row = boundsOf(weekLayoutSlotTag(1))
        val handle = Offset(row.right - 20f, row.center.y)
        rule.onRoot().performTouchInput {
            down(handle)
            moveTo(Offset(handle.x, handle.y + touchSlop * 0.5f))
        }
        rule.mainClock.advanceTimeByFrame()
        assertNull("un temblor del dedo no agarra la ficha", state.liftedId)
        rule.onRoot().performTouchInput { up() }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertTrue(moves.isEmpty())
    }

    /** La lista dentro de una página que se desplaza (como en el asistente), con sitio de sobra por debajo. */
    private fun scrollingPage(scroll: ScrollState): @Composable (@Composable () -> Unit) -> Unit = { content ->
        Column(Modifier.verticalScroll(scroll)) {
            content()
            Spacer(Modifier.height(900.dp))
        }
    }

    @Test
    fun aDragFromTheHandleLiftsTheSessionInsteadOfScrollingThePage() {
        val scroll = ScrollState(0)
        show(wrap = scrollingPage(scroll))
        rule.waitForIdle()
        assertTrue("la página tiene por dónde desplazarse", scroll.maxValue > 0)
        rule.mainClock.autoAdvance = false
        val row = boundsOf(weekLayoutSlotTag(1))
        val handle = Offset(row.right - 20f, row.center.y)
        val moved = touchSlop * 2f + 2f
        rule.onRoot().performTouchInput {
            down(handle)
            moveTo(Offset(handle.x, handle.y + moved))
            moveTo(Offset(handle.x, handle.y + moved + 40f))
        }
        rule.mainClock.advanceTimeByFrame()
        assertEquals("a", state.liftedId)
        assertEquals("el movimiento que levanta la ficha no desplaza la página", 0, scroll.value)
        rule.onRoot().performTouchInput { up() }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertEquals(0, scroll.value)
    }

    @Test
    fun aVerticalSwipeFromTheBodyOfARowStillScrollsThePageAndLiftsNothing() {
        val scroll = ScrollState(0)
        show(wrap = scrollingPage(scroll))
        rule.waitForIdle()
        val from = centerOf(weekLayoutSessionTag("c"))
        rule.onRoot().performTouchInput { swipe(from, Offset(from.x, from.y - 200f), durationMillis = 250) }
        rule.waitForIdle()
        assertNull(state.liftedId)
        assertTrue("la página se desplazó: ${scroll.value}", scroll.value > 0)
        assertTrue(moves.isEmpty())
    }

    @Test
    fun aLiftedSessionNearTheBottomEdgeOfTheVisibleBandMakesThePageScrollByItself() {
        val scroll = ScrollState(0)
        val band = WizardPageScroll(scroll = scroll, limit = { scroll.maxValue }, top = { 0f }, bottom = { 700f })
        show(wrap = { content ->
            CompositionLocalProvider(LocalWizardPageScroll provides band) { scrollingPage(scroll)(content) }
        })
        rule.waitForIdle()
        // Con el reloj parado los bucles de fotogramas (el auto-desplazamiento) avanzan solo cuando la prueba los avanza.
        rule.mainClock.autoAdvance = false
        val from = centerOf(weekLayoutSessionTag("c"))
        rule.onRoot().performTouchInput { down(from) }
        rule.mainClock.advanceTimeBy(800)
        assertEquals("c", state.liftedId)
        assertEquals(0, scroll.value)
        // El dedo a 20 px del borde inferior de la franja visible (zona de borde de 56 dp): tras el respiro, la página baja sola.
        rule.onRoot().performTouchInput { moveTo(Offset(from.x, 680f)) }
        rule.mainClock.advanceTimeBy(1000)
        assertTrue("la página bajó sola: ${scroll.value}", scroll.value > 100)
        // La ficha sigue bajo el dedo: lo que se desplazó la página se suma a la posición del dedo en el contenido.
        assertEquals(680f + scroll.value, state.finger.y, 2f)
        rule.onRoot().performTouchInput { up() }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertNull(state.liftedId)
    }

    @Test
    fun aLiftedSessionInTheMiddleOfTheBandDoesNotMakeThePageScroll() {
        val scroll = ScrollState(0)
        val band = WizardPageScroll(scroll = scroll, limit = { scroll.maxValue }, top = { 0f }, bottom = { 700f })
        show(wrap = { content ->
            CompositionLocalProvider(LocalWizardPageScroll provides band) { scrollingPage(scroll)(content) }
        })
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        val from = centerOf(weekLayoutSessionTag("c"))
        rule.onRoot().performTouchInput { down(from) }
        rule.mainClock.advanceTimeBy(800)
        assertEquals("c", state.liftedId)
        rule.onRoot().performTouchInput { moveTo(Offset(from.x, from.y + 40f)) }
        rule.mainClock.advanceTimeBy(1000)
        assertEquals(0, scroll.value)
        rule.onRoot().performTouchInput { up() }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
    }

    @Test
    fun dragSidewaysFromTheHandleDoesNotLiftAnything() {
        show()
        val row = boundsOf(weekLayoutSlotTag(1))
        val handle = Offset(row.right - 20f, row.center.y)
        rule.onRoot().performTouchInput {
            down(handle)
            moveTo(Offset(handle.x - 80f, handle.y + 2f))
            up()
        }
        rule.waitForIdle()
        assertNull(state.liftedId)
        assertTrue(moves.isEmpty())
    }

    @Test
    fun releasingOutsideTheListCancelsTheMove() {
        show()
        rule.mainClock.autoAdvance = false
        val from = centerOf(weekLayoutSessionTag("a"))
        rule.onRoot().performTouchInput { down(from) }
        rule.mainClock.advanceTimeBy(800)
        assertEquals("a", state.liftedId)
        // Por debajo de la última fila y más allá de la distancia de cancelación (52 dp): no hay destino.
        val bottom = boundsOf(WEEK_LAYOUT_LIST_TAG).bottom
        rule.onRoot().performTouchInput { moveTo(Offset(from.x, bottom + 80f)) }
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
            moveTo(Offset(from.x, from.y + 200f))
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
        val glyph = Offset(24f, geometry.rowHeight / 2f)
        // Se levanta «a» y se la pasa sobre el miércoles (ocupado por «b»): «b» ya se va al hueco del lunes.
        rule.runOnUiThread {
            state.lift("a", geometry.home(1) + glyph)
            state.dragTo(geometry.home(3) + glyph)
        }
        rule.waitForIdle()
        assertEquals(3, state.hoverDay)
        rule.onNodeWithTag(WEEK_LAYOUT_STATUS_TAG).assertTextEquals(WeekLayoutCopy.hintDropOn(3))
        assertEquals(centerOf(weekLayoutSlotTag(1)).y, centerOf(weekLayoutSessionTag("b")).y, 2f)
        // Se aparta fuera de la semana: «b» vuelve a su sitio.
        rule.runOnUiThread { state.dragTo(Offset(geometry.fichaLeft + 24f, -400f)) }
        rule.waitForIdle()
        assertNull(state.hoverDay)
        assertEquals(centerOf(weekLayoutSlotTag(3)).y, centerOf(weekLayoutSessionTag("b")).y, 2f)
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

    // ------------------------------------------------------------ la semana completa a la vista

    /** Las siete sesiones, una por día, para llenar la lista. */
    private val sevenSessions = (1..7).map { WeekLayoutSession("s$it", "Sesión $it", "Foco $it", 40 + it, 4 + it, isMain = it == 1) }

    private fun assertAllSevenDaysInsideTheScreen(widthDp: Float, label: String) {
        for (day in 1..7) {
            val row = boundsOf(weekLayoutSlotTag(day))
            assertTrue("$label: la fila $day empieza dentro (${row.left})", row.left >= -0.5f)
            assertTrue("$label: la fila $day acaba dentro (${row.right} de $widthDp)", row.right <= widthDp + 0.5f)
            assertTrue("$label: la fila $day mide ≥ 48 dp (${row.height})", row.height >= 48f)
            slotNode(day).assertIsDisplayed()
        }
        for (s in sevenSessions) {
            val ficha = boundsOf(weekLayoutSessionTag(s.id))
            assertTrue("$label: ${s.id} cabe a lo ancho (${ficha.left}..${ficha.right} de $widthDp)", ficha.left >= -0.5f && ficha.right <= widthDp + 0.5f)
            sessionNode(s.id).assertIsDisplayed()
        }
        // No hay desplazamiento de lado en ningún sitio de la lista.
        val list = rule.onNodeWithTag(WEEK_LAYOUT_LIST_TAG).fetchSemanticsNode().config
        assertNull("$label: la lista no se desplaza de lado", list.getOrNull(SemanticsProperties.HorizontalScrollAxisRange))
    }

    private fun showSevenAt(widthDp: Float, fontScale: Float) {
        assignment = (1..7).associateWith { "s$it" }
        show(
            shown = sevenSessions,
            wrap = { content ->
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = fontScale)) {
                    Box(Modifier.width(widthDp.dp)) { content() }
                }
            },
        )
        rule.waitForIdle()
    }

    @Test
    fun allSevenDaysFitAt360dpWithoutScrollingSideways() {
        showSevenAt(360f, 1f)
        assertAllSevenDaysInsideTheScreen(360f, "360 dp")
    }

    @Test
    fun allSevenDaysFitAt360dpWithTheLargeText() {
        showSevenAt(360f, 1.3f)
        assertAllSevenDaysInsideTheScreen(360f, "360 dp al 130 %")
    }

    @Test
    fun allSevenDaysFitAt320dpWithAndWithoutTheLargeText() {
        showSevenAt(320f, 1f)
        assertAllSevenDaysInsideTheScreen(320f, "320 dp")
    }

    @Test
    fun allSevenDaysFitAt320dpWithTheLargeText() {
        showSevenAt(320f, 1.3f)
        assertAllSevenDaysInsideTheScreen(320f, "320 dp al 130 %")
    }

    @Test
    fun withNormalTextTheRowsAre64dpAndTheDayColumnIs34dpWide() {
        showSevenAt(360f, 1f)
        val row = boundsOf(weekLayoutSlotTag(1))
        assertEquals("alto de fila ${row.height}", 64f, row.height, 0.6f)
        // La ficha empieza donde acaba la columna del día (34 dp) y su aire (8 dp).
        assertEquals(42f, boundsOf(weekLayoutSessionTag("s1")).left, 0.6f)
    }

    @Test
    fun withBigTextTheDayColumnWidensAndTheRowsStayEqual() {
        showSevenAt(360f, 1.3f)
        // La columna del día crece con la letra (34 dp × 1,3 = 44,2 dp) y la ficha se desplaza con ella.
        assertEquals(52.2f, boundsOf(weekLayoutSessionTag("s1")).left, 1f)
        // Siguen siendo siete filas iguales y de al menos 64 dp (el alto exacto lo mide el texto: ver las hojas de dibujo).
        val heights = (1..7).map { boundsOf(weekLayoutSlotTag(it)).height }
        assertEquals(1, heights.map { Math.round(it) }.toSet().size)
        assertTrue("alto ${heights.first()}", heights.first() >= 63.5f)
    }

    @Test
    fun aSingleSessionLateInTheWeekStillShowsTheWholeWeek() {
        // Una sola sesión, el sábado: ya no hay tira que desplazar para encontrarla, está en su fila.
        assignment = mapOf(6 to "a")
        show(shown = listOf(sessions[0]))
        rule.waitForIdle()
        for (day in 1..7) slotNode(day).assertIsDisplayed()
        sessionNode("a").assertIsDisplayed()
        assertEquals(centerOf(weekLayoutSlotTag(6)).y, centerOf(weekLayoutSessionTag("a")).y, 2f)
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
    fun everySessionIsA48dpTouchTargetToo() {
        show()
        for (s in sessions) sessionNode(s.id).assertHeightIsAtLeast(48.dp)
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
