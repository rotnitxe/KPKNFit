package com.example.kpkn.screens.onboarding.design.entreno

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.TrainingPlace
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Los tres componentes con Compose sobre Robolectric (la tubería de punteros y de semántica de verdad, sin dispositivo): qué
 * se anuncia, qué llama cada toque y cómo sigue el dial al dedo. Se corren con movimiento reducido para que todo sea
 * determinista; una prueba aparte comprueba que con las animaciones encendidas la composición se queda en reposo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class EntrenoWeekComponentsTest {

    @get:Rule
    val rule = createComposeRule()

    /** Contenido de 400 dp de ancho y con movimiento reducido. */
    private fun show(content: @Composable () -> Unit) = rule.setContent {
        CompositionLocalProvider(LocalWeekReducedMotion provides true) {
            Box(Modifier.width(400.dp)) { content() }
        }
    }

    private fun left(tag: String): Float = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.left

    // ─── FreshDayRow ─────────────────────────────────────────────────────────

    @Test
    fun freshDayRowIsASingleChoiceOfSevenRadioOptions() {
        var day by mutableStateOf<Int?>(null)
        val calls = mutableListOf<Int>()
        show { FreshDayRow(selectedDay = day, onSelect = { calls += it; day = it }) }

        for (d in 1..7) {
            val node = rule.onNodeWithTag("setup-freshday-$d")
            node.assertContentDescriptionEquals(dayChoiceDescription(d, false)).assertIsNotSelected()
            assertEquals(Role.RadioButton, node.fetchSemanticsNode().config[SemanticsProperties.Role])
        }
        // La nota solo aparece cuando ya hay un día elegido.
        rule.onNodeWithText("También será el primer día de tu semana.").assertDoesNotExist()

        rule.onNodeWithTag("setup-freshday-4").performClick()
        rule.onNodeWithTag("setup-freshday-4").assertIsSelected().assertContentDescriptionEquals("Jueves, elegido")
        rule.onNodeWithTag("setup-freshday-2").assertIsNotSelected()
        rule.onNodeWithText("También será el primer día de tu semana.").assertExists()

        // Selección única: elegir otro día suelta el anterior.
        rule.onNodeWithTag("setup-freshday-2").performClick()
        rule.onNodeWithTag("setup-freshday-4").assertIsNotSelected()
        rule.onNodeWithTag("setup-freshday-2").assertIsSelected()

        // Tocar el elegido no lo desmarca.
        rule.onNodeWithTag("setup-freshday-2").performClick()
        rule.onNodeWithTag("setup-freshday-2").assertIsSelected()
        assertEquals(listOf(4, 2, 2), calls)
    }

    @Test
    fun freshDayRowCanHideItsNote() {
        show { FreshDayRow(selectedDay = 3, onSelect = {}, showNote = false) }
        rule.onNodeWithTag("setup-freshday-3").assertIsSelected()
        rule.onNodeWithText("También será el primer día de tu semana.").assertDoesNotExist()
    }

    @Test
    fun anInvalidFreshDayLeavesEverythingUnselected() {
        show { FreshDayRow(selectedDay = 9, onSelect = {}) }
        for (d in 1..7) rule.onNodeWithTag("setup-freshday-$d").assertIsNotSelected()
    }

    // ─── WeekCalendar ────────────────────────────────────────────────────────

    private class CalendarState(
        start: Int = 1,
        selected: Set<Int> = emptySet(),
        val fresh: Int? = null,
        val places: Set<TrainingPlace> = emptySet(),
    ) {
        var start by mutableIntStateOf(start)
        var selected by mutableStateOf(selected)
        var dayPlaces by mutableStateOf(emptyMap<Int, TrainingPlace>())
        val placeCalls = mutableListOf<Pair<Int, TrainingPlace>>()
        val startCalls = mutableListOf<Int>()
    }

    private fun showCalendar(s: CalendarState) = show {
        WeekCalendar(
            weekStartDay = s.start,
            selectedDays = s.selected,
            freshestDay = s.fresh,
            onToggleDay = { day -> s.selected = if (day in s.selected) s.selected - day else s.selected + day },
            onWeekStartChange = { s.startCalls += it; s.start = it },
            places = s.places,
            dayPlaces = s.dayPlaces,
            onDayPlace = { day, place -> s.placeCalls += day to place; s.dayPlaces = s.dayPlaces + (day to place) },
        )
    }

    @Test
    fun theCalendarTogglesDaysAndCountsThem() {
        val s = CalendarState(start = 4, selected = setOf(4, 5))
        showCalendar(s)

        rule.onNodeWithTag("setup-week-count").assertContentDescriptionEquals("2 días por semana")
        rule.onNodeWithTag("setup-weekday-4").assertIsOn().assertContentDescriptionEquals("Jueves, elegido")
        rule.onNodeWithTag("setup-weekday-1").assertIsOff().assertContentDescriptionEquals("Lunes, sin elegir")
        assertEquals(Role.Checkbox, rule.onNodeWithTag("setup-weekday-1").fetchSemanticsNode().config[SemanticsProperties.Role])

        rule.onNodeWithTag("setup-weekday-1").performClick()
        rule.onNodeWithTag("setup-weekday-1").assertIsOn()
        rule.onNodeWithTag("setup-week-count").assertContentDescriptionEquals("3 días por semana")

        rule.onNodeWithTag("setup-weekday-4").performClick()
        rule.onNodeWithTag("setup-weekday-5").performClick()
        rule.onNodeWithTag("setup-weekday-1").performClick()
        rule.onNodeWithTag("setup-week-count").assertContentDescriptionEquals("0 días por semana")
        rule.onNodeWithTag("setup-weekday-6").performClick()
        rule.onNodeWithTag("setup-week-count").assertContentDescriptionEquals("1 día por semana")
    }

    @Test
    fun theStripFollowsTheWeekStart() {
        val s = CalendarState(start = 4)
        showCalendar(s)
        val order = listOf(4, 5, 6, 7, 1, 2, 3)
        for ((a, b) in order.zipWithNext()) {
            assertTrue("el $a va antes que el $b", left("setup-weekday-$a") < left("setup-weekday-$b"))
        }
    }

    @Test
    fun pickingAnotherWeekStartReordersTheStripAndClosesThePicker() {
        val s = CalendarState(start = 1, selected = setOf(1, 3))
        showCalendar(s)

        rule.onNodeWithTag("setup-weekstart-4").assertDoesNotExist()
        rule.onNodeWithTag("setup-weekstart").assertContentDescriptionEquals("La semana empieza el lunes")
        rule.onNodeWithTag("setup-weekstart").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Plegado"),
        )

        rule.onNodeWithTag("setup-weekstart").performClick()
        rule.onNodeWithTag("setup-weekstart").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Desplegado"),
        )
        for (d in 1..7) rule.onNodeWithTag("setup-weekstart-$d").assertExists()
        rule.onNodeWithTag("setup-weekstart-1").assertIsSelected()

        rule.onNodeWithTag("setup-weekstart-4").performClick()
        rule.waitForIdle()
        assertEquals(listOf(4), s.startCalls)
        rule.onNodeWithTag("setup-weekstart").assertContentDescriptionEquals("La semana empieza el jueves")
        rule.onNodeWithTag("setup-weekstart-4").assertDoesNotExist()
        for ((a, b) in listOf(4, 5, 6, 7, 1, 2, 3).zipWithNext()) {
            assertTrue("el $a va antes que el $b", left("setup-weekday-$a") < left("setup-weekday-$b"))
        }
        // Reordenar la semana no cambia qué días se entrena.
        rule.onNodeWithTag("setup-weekday-1").assertIsOn()
        rule.onNodeWithTag("setup-weekday-3").assertIsOn()
        rule.onNodeWithTag("setup-week-count").assertContentDescriptionEquals("2 días por semana")
    }

    @Test
    fun theStrongestDayGetsItsNoteOnlyWhileItIsATrainingDay() {
        val s = CalendarState(start = 1, selected = setOf(4, 5), fresh = 4)
        showCalendar(s)
        rule.onNodeWithTag("setup-week-strongest").assertExists()
        // Quien no ve el sol lo oye en la descripción del propio día; los demás días no lo dicen.
        rule.onNodeWithTag("setup-weekday-4").assertContentDescriptionEquals("Jueves, elegido, tu sesión más fuerte")
        rule.onNodeWithTag("setup-weekday-5").assertContentDescriptionEquals("Viernes, elegido")

        rule.runOnIdle { s.selected = setOf(5) }
        rule.waitForIdle()
        rule.onNodeWithTag("setup-week-strongest").assertDoesNotExist()
        rule.onNodeWithTag("setup-weekday-4").assertContentDescriptionEquals("Jueves, sin elegir")

        rule.runOnIdle { s.selected = setOf(4, 5) }
        rule.waitForIdle()
        rule.onNodeWithTag("setup-week-strongest").assertExists()
    }

    @Test
    fun withoutAStrongestDayThereIsNoNote() {
        val s = CalendarState(start = 1, selected = setOf(4, 5), fresh = null)
        showCalendar(s)
        rule.onNodeWithTag("setup-week-strongest").assertDoesNotExist()
        rule.onNodeWithTag("setup-weekday-4").assertContentDescriptionEquals("Jueves, elegido")
    }

    @Test
    fun withASinglePlaceThereIsNoPlacePicker() {
        val s = CalendarState(selected = setOf(2), places = setOf(TrainingPlace.HOME))
        showCalendar(s)
        rule.onNodeWithTag("setup-weekplace-2").assertDoesNotExist()
        rule.onNodeWithText("¿Dónde entrenas ese día?").assertDoesNotExist()
    }

    @Test
    fun withSeveralPlacesEachChosenDayWalksThem() {
        val s = CalendarState(selected = setOf(2, 3), places = setOf(TrainingPlace.HOME, TrainingPlace.GYM))
        showCalendar(s)

        rule.onNodeWithText("¿Dónde entrenas ese día?").assertExists()
        // Por defecto, el primero en orden gimnasio, casa, espacios públicos.
        rule.onNodeWithTag("setup-weekplace-2").assertHasClickAction().assertContentDescriptionEquals("Lugar del martes: Gimnasio")
        rule.onNodeWithTag("setup-weekplace-3").assertContentDescriptionEquals("Lugar del miércoles: Gimnasio")
        // Un día sin elegir no tiene lugar que tocar.
        rule.onNodeWithTag("setup-weekplace-4").assertHasNoClickAction()

        rule.onNodeWithTag("setup-weekplace-2").performClick()
        assertEquals(listOf(2 to TrainingPlace.HOME), s.placeCalls)
        rule.onNodeWithTag("setup-weekplace-2").assertContentDescriptionEquals("Lugar del martes: En casa")
        rule.onNodeWithTag("setup-weekplace-3").assertContentDescriptionEquals("Lugar del miércoles: Gimnasio")

        rule.onNodeWithTag("setup-weekplace-2").performClick()
        assertEquals(listOf(2 to TrainingPlace.HOME, 2 to TrainingPlace.GYM), s.placeCalls)
        rule.onNodeWithTag("setup-weekplace-2").assertContentDescriptionEquals("Lugar del martes: Gimnasio")
    }

    @Test
    fun anUnselectedDayGainsItsPlaceWhenItIsChosen() {
        val s = CalendarState(selected = emptySet(), places = TrainingPlace.entries.toSet())
        showCalendar(s)
        rule.onNodeWithTag("setup-weekplace-5").assertHasNoClickAction()
        rule.onNodeWithTag("setup-weekday-5").performClick()
        rule.onNodeWithTag("setup-weekplace-5").assertHasClickAction().assertContentDescriptionEquals("Lugar del viernes: Gimnasio")
        rule.onNodeWithTag("setup-weekplace-5").performClick()
        rule.onNodeWithTag("setup-weekplace-5").performClick()
        assertEquals(listOf(5 to TrainingPlace.HOME, 5 to TrainingPlace.PUBLIC), s.placeCalls)
        rule.onNodeWithTag("setup-weekplace-5").assertContentDescriptionEquals("Lugar del viernes: En espacios públicos")
    }

    // ─── SessionClockDial ────────────────────────────────────────────────────

    private class DialState(initial: Int = 75) {
        var minutes by mutableIntStateOf(initial)
        val calls = mutableListOf<Int>()
    }

    private fun showDial(s: DialState) = show {
        SessionClockDial(minutes = s.minutes, onMinutesChange = { s.calls += it; s.minutes = it })
    }

    private fun dialPoint(minutes: Int, side: Int, reach: Float = 0.88f): Offset {
        val c = side / 2f
        val a = Math.toRadians(angleForMinutes(minutes).toDouble())
        val r = side / 2f * reach
        return Offset(c + r * sin(a).toFloat(), c - r * cos(a).toFloat())
    }

    @Test
    fun theDialIsOneRangeNodeForTalkBack() {
        showDial(DialState(75))
        val node = rule.onNodeWithTag(SESSION_DIAL_TAG)
        node.assertContentDescriptionEquals("Tiempo por sesión")
        val config = node.fetchSemanticsNode().config
        assertEquals("1 hora y 15 minutos", config[SemanticsProperties.StateDescription])
        val range = config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(75f, range.current, 0f)
        assertEquals(30f, range.range.start, 0f)
        assertEquals(180f, range.range.endInclusive, 0f)
        // De 30 a 180 de 5 en 5 son 31 posiciones, así que 29 pasos entre los extremos.
        assertEquals(29, range.steps)
        assertTrue(config.contains(SemanticsActions.SetProgress))
        val labels = config[SemanticsActions.CustomActions].map { it.label }
        assertEquals(listOf("Aumentar 5 minutos", "Disminuir 5 minutos"), labels)
    }

    @Test
    fun setProgressSnapsToTheNearestNotch() {
        val s = DialState(75)
        showDial(s)
        rule.onNodeWithTag(SESSION_DIAL_TAG).performSemanticsAction(SemanticsActions.SetProgress) { it(103f) }
        assertEquals(listOf(105), s.calls)
        rule.onNodeWithTag(SESSION_DIAL_TAG).performSemanticsAction(SemanticsActions.SetProgress) { it(500f) }
        assertEquals(180, s.minutes)
        rule.onNodeWithTag(SESSION_DIAL_TAG).performSemanticsAction(SemanticsActions.SetProgress) { it(-4f) }
        assertEquals(30, s.minutes)
        // Los 20 y 25 min de antes tampoco: TalkBack nunca fija un valor por debajo del mínimo.
        rule.onNodeWithTag(SESSION_DIAL_TAG).performSemanticsAction(SemanticsActions.SetProgress) { it(120f) }
        assertEquals(120, s.minutes)
        rule.onNodeWithTag(SESSION_DIAL_TAG).performSemanticsAction(SemanticsActions.SetProgress) { it(25f) }
        assertEquals(30, s.minutes)
        assertTrue("ningún valor por debajo del mínimo", s.calls.all { it >= 30 })
    }

    @Test
    fun theAccessibilityActionsMoveOneNotch() {
        val s = DialState(75)
        showDial(s)
        fun run(label: String) {
            val actions = rule.onNodeWithTag(SESSION_DIAL_TAG).fetchSemanticsNode().config[SemanticsActions.CustomActions]
            rule.runOnUiThread { assertTrue(actions.first { it.label == label }.action()) }
            rule.waitForIdle()
        }
        run("Aumentar 5 minutos")
        assertEquals(80, s.minutes)
        run("Disminuir 5 minutos")
        run("Disminuir 5 minutos")
        assertEquals(70, s.minutes)
    }

    @Test
    fun tappingTheFaceSetsTheNotchUnderTheFinger() {
        val s = DialState(75)
        showDial(s)
        val size = rule.onNodeWithTag(SESSION_DIAL_TAG).fetchSemanticsNode().size
        rule.onNodeWithTag(SESSION_DIAL_TAG).performTouchInput { click(dialPoint(150, size.width)) }
        assertEquals(150, s.minutes)
        rule.onNodeWithTag(SESSION_DIAL_TAG).performTouchInput { click(dialPoint(30, size.width)) }
        assertEquals(30, s.minutes)
        // Un toque en el hueco de abajo satura en el extremo más cercano.
        rule.onNodeWithTag(SESSION_DIAL_TAG).performTouchInput { click(Offset(size.width * 0.62f, size.height * 0.97f)) }
        assertEquals(180, s.minutes)
    }

    @Test
    fun theCenterOfTheDialIsNotTouchSensitive() {
        val s = DialState(75)
        showDial(s)
        val faceSize = rule.onNodeWithTag(SESSION_DIAL_TAG).fetchSemanticsNode().size
        rule.onNodeWithTag(SESSION_DIAL_TAG).performTouchInput { click(center) }
        // Tampoco lo es el hueco entero de la cifra: tocar el «75» no mueve nada.
        rule.onNodeWithTag(SESSION_DIAL_TAG).performTouchInput { click(Offset(center.x, center.y - faceSize.height * 0.2f)) }
        rule.onNodeWithTag(SESSION_DIAL_TAG).performTouchInput { click(Offset(center.x + faceSize.width * 0.2f, center.y)) }
        assertTrue(s.calls.isEmpty())
        assertEquals(75, s.minutes)
        // Pero justo fuera del hueco, sobre las marcas, sí es la esfera.
        rule.onNodeWithTag(SESSION_DIAL_TAG).performTouchInput { click(dialPoint(120, faceSize.width, reach = 0.7f)) }
        assertEquals(120, s.minutes)
    }

    @Test
    fun draggingTheKnobWalksNotchByNotchWithoutSkipping() {
        val s = DialState(75)
        showDial(s)
        val size = rule.onNodeWithTag(SESSION_DIAL_TAG).fetchSemanticsNode().size
        rule.onNodeWithTag(SESSION_DIAL_TAG).performTouchInput {
            swipe(dialPoint(75, size.width), dialPoint(120, size.width), durationMillis = 600)
        }
        assertEquals(120, s.minutes)
        assertTrue(s.calls.isNotEmpty())
        // Sube sin saltarse muescas ni repetirse: cada llamada es la siguiente muesca.
        assertEquals((80..120 step 5).toList(), s.calls)
        assertTrue(s.calls.all { it % 5 == 0 })
    }

    @Test
    fun draggingBackwardsAlsoWalksTheNotches() {
        val s = DialState(150)
        showDial(s)
        val size = rule.onNodeWithTag(SESSION_DIAL_TAG).fetchSemanticsNode().size
        rule.onNodeWithTag(SESSION_DIAL_TAG).performTouchInput {
            swipe(dialPoint(150, size.width), dialPoint(95, size.width), durationMillis = 600)
        }
        assertEquals(95, s.minutes)
        assertEquals((145 downTo 95 step 5).toList(), s.calls)
    }

    @Test
    fun theShortcutsSetTheValueAndLightTheMatchingOne() {
        val s = DialState(60)
        showDial(s)
        for (v in listOf(30, 45, 60, 90, 120)) rule.onNodeWithTag("setup-sessiontime-$v").assertExists()
        rule.onNodeWithTag("setup-sessiontime-150").assertDoesNotExist()
        rule.onNodeWithTag("setup-sessiontime-60").assertIsSelected()
        rule.onNodeWithTag("setup-sessiontime-45").assertIsNotSelected().assertContentDescriptionEquals("45 minutos")
        rule.onNodeWithTag("setup-sessiontime-90").assertContentDescriptionEquals("1 hora y 30 minutos")

        rule.onNodeWithTag("setup-sessiontime-45").performClick()
        assertEquals(listOf(45), s.calls)
        rule.onNodeWithTag("setup-sessiontime-45").assertIsSelected()
        rule.onNodeWithTag("setup-sessiontime-60").assertIsNotSelected()
        rule.onNodeWithTag(SESSION_DIAL_TAG).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "45 minutos"),
        )
    }

    @Test
    fun aValueOutsideTheRangeIsShownClamped() {
        showDial(DialState(500))
        val config = rule.onNodeWithTag(SESSION_DIAL_TAG).fetchSemanticsNode().config
        assertEquals(180f, config[SemanticsProperties.ProgressBarRangeInfo].current, 0f)
        assertEquals("3 horas", config[SemanticsProperties.StateDescription])
    }

    // ─── Con las animaciones encendidas ──────────────────────────────────────

    @Test
    fun withAnimationsOnTheDayRowStillSettles() {
        var day by mutableStateOf<Int?>(null)
        rule.setContent { Box(Modifier.width(400.dp)) { FreshDayRow(selectedDay = day, onSelect = { day = it }) } }
        rule.onNodeWithTag("setup-freshday-3").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("setup-freshday-3").assertIsSelected()
        rule.onNodeWithTag("setup-freshday-5").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("setup-freshday-5").assertIsSelected()
        rule.onNodeWithTag("setup-freshday-3").assertIsNotSelected()
    }

    @Test
    fun withAnimationsOnTheCalendarStillSettlesEvenAfterReorderingTheWeek() {
        var selected by mutableStateOf(setOf(1, 4))
        var start by mutableIntStateOf(4)
        rule.setContent {
            Box(Modifier.width(400.dp)) {
                WeekCalendar(
                    weekStartDay = start,
                    selectedDays = selected,
                    freshestDay = 4,
                    onToggleDay = { selected = if (it in selected) selected - it else selected + it },
                    onWeekStartChange = { start = it },
                    places = setOf(TrainingPlace.GYM, TrainingPlace.PUBLIC),
                    dayPlaces = emptyMap(),
                    onDayPlace = { _, _ -> },
                )
            }
        }
        rule.onNodeWithTag("setup-weekday-6").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("setup-weekday-6").assertIsOn()
        rule.onNodeWithTag("setup-weekstart").performClick()
        rule.onNodeWithTag("setup-weekstart-1").performClick()
        rule.waitForIdle()
        assertEquals(1, start)
        // Con la cinta ya en reposo, el lunes vuelve a ser el primero de la tira.
        assertTrue(left("setup-weekday-1") < left("setup-weekday-2"))
        assertTrue(left("setup-weekday-7") > left("setup-weekday-6"))
        assertFalse(selected.isEmpty())
    }

    @Test
    fun withAnimationsOnTheDialStillSettles() {
        var minutes by mutableIntStateOf(60)
        rule.setContent { Box(Modifier.width(400.dp)) { SessionClockDial(minutes = minutes, onMinutesChange = { minutes = it }) } }
        rule.onNodeWithTag("setup-sessiontime-90").performClick()
        rule.waitForIdle()
        assertEquals(90, minutes)
        rule.onNodeWithTag("setup-sessiontime-90").assertIsSelected()
    }
}
