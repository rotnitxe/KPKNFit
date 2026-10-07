package com.example.kpkn.screens.onboarding.design.entreno

import com.example.kpkn.domain.onboarding.TrainingPlace
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La semana sin Compose: nombres de los días, el orden de la tira desde cualquier inicio, el camino más corto y la «cinta»
 * con la que los días se deslizan al cambiar el inicio, el lugar de cada día y los textos de duración.
 */
class WeekMathTest {

    // ─── Días ────────────────────────────────────────────────────────────────

    @Test
    fun theWeekStartsWhereAskedAndWrapsAround() {
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), orderedWeek(1))
        assertEquals(listOf(4, 5, 6, 7, 1, 2, 3), orderedWeek(4))
        assertEquals(listOf(7, 1, 2, 3, 4, 5, 6), orderedWeek(7))
        for (start in 1..7) {
            val week = orderedWeek(start)
            assertEquals(start, week.first())
            assertEquals((1..7).toSet(), week.toSet())
            assertEquals(7, week.size)
        }
    }

    @Test
    fun anInvalidStartCountsAsMondayAndNeverEmptiesTheStrip() {
        for (bad in listOf(0, 8, -3, 99, Int.MIN_VALUE, Int.MAX_VALUE)) {
            assertEquals("inicio $bad", orderedWeek(1), orderedWeek(bad))
        }
    }

    @Test
    fun everyDayHasItsInitialShortAndFullName() {
        assertEquals(listOf("L", "M", "X", "J", "V", "S", "D"), (1..7).map(::dayInitial))
        assertEquals(listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"), (1..7).map(::dayShortName))
        assertEquals(
            listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"),
            (1..7).map(::dayFullName),
        )
    }

    @Test
    fun anInvalidDayHasNoNameInsteadOfAWrongOne() {
        for (bad in listOf(0, 8, -1, Int.MIN_VALUE)) {
            assertEquals("", dayInitial(bad))
            assertEquals("", dayShortName(bad))
            assertEquals("", dayFullName(bad))
            assertTrue(!isWeekDay(bad))
        }
        assertTrue((1..7).all(::isWeekDay))
    }

    @Test
    fun theSlotOfADayIsItsPositionInTheOrderedWeek() {
        for (start in 1..7) {
            val week = orderedWeek(start)
            for (day in 1..7) assertEquals(week.indexOf(day), weekSlotOf(day, start))
        }
        assertEquals(-1, weekSlotOf(0, 1))
        assertEquals(-1, weekSlotOf(9, 4))
        assertEquals(weekSlotOf(3, 1), weekSlotOf(3, 0))
    }

    @Test
    fun theCounterUnitIsSingularOnlyForOne() {
        assertEquals("día por semana", daysPerWeekUnit(1))
        assertEquals("días por semana", daysPerWeekUnit(0))
        assertEquals("días por semana", daysPerWeekUnit(2))
        assertEquals("días por semana", daysPerWeekUnit(7))
    }

    @Test
    fun theWeekStartSentenceNamesTheDayInLowerCase() {
        assertEquals("La semana empieza el lunes", weekStartLabel(1))
        assertEquals("La semana empieza el miércoles", weekStartLabel(3))
        assertEquals("La semana empieza el jueves", weekStartLabel(4))
        assertEquals("La semana empieza el sábado", weekStartLabel(6))
        assertEquals("La semana empieza el domingo", weekStartLabel(7))
        assertEquals("La semana empieza el lunes", weekStartLabel(0))
    }

    @Test
    fun theChoiceDescriptionSaysTheFullDayAndTheState() {
        assertEquals("Jueves, elegido", dayChoiceDescription(4, true))
        assertEquals("Domingo, sin elegir", dayChoiceDescription(7, false))
        assertEquals("Jueves, elegido, tu sesión más fuerte", dayChoiceDescription(4, true, strongest = true))
        assertEquals("Viernes, elegido", dayChoiceDescription(5, true, strongest = false))
    }

    // ─── Camino más corto y cinta ────────────────────────────────────────────

    @Test
    fun theShortestDeltaNeverTravelsMoreThanThreeSlotsAroundTheWeek() {
        assertEquals(0f, circularShortestDelta(1f, 1f), 0f)
        assertEquals(3f, circularShortestDelta(1f, 4f), 1e-6f)
        assertEquals(-3f, circularShortestDelta(4f, 1f), 1e-6f)
        assertEquals(-2f, circularShortestDelta(1f, 6f), 1e-6f)
        assertEquals(-1f, circularShortestDelta(1f, 7f), 1e-6f)
        assertEquals(1f, circularShortestDelta(7f, 1f), 1e-6f)
        assertEquals(-3f, circularShortestDelta(3f, 7f), 1e-6f)
        assertEquals(1.2f, circularShortestDelta(6.9f, 1.1f), 1e-5f)
        for (from in 1..7) for (to in 1..7) {
            val delta = circularShortestDelta(from.toFloat(), to.toFloat())
            assertTrue("$from→$to = $delta", abs(delta) <= 3f)
            // Aplicar el desplazamiento lleva realmente al destino (módulo 7).
            assertEquals(to, ((from - 1 + delta.roundToInt()) % 7 + 7) % 7 + 1)
        }
    }

    @Test
    fun aPositionAccumulatedOverSeveralTurnsStillTakesTheShortWay() {
        assertEquals(2f, circularShortestDelta(-6f, -4f), 1e-5f)
        assertEquals(3f, circularShortestDelta(15f, 18f), 1e-5f)
        assertEquals(-3f, circularShortestDelta(-12f, -15f), 1e-4f)
        assertEquals(0f, circularShortestDelta(5f, 5f), 0f)
    }

    @Test
    fun theShortestDeltaIsSafeWithNonsense() {
        assertEquals(0f, circularShortestDelta(Float.NaN, 3f), 0f)
        assertEquals(0f, circularShortestDelta(3f, Float.POSITIVE_INFINITY), 0f)
        assertEquals(0f, circularShortestDelta(3f, 4f, 0f), 0f)
        assertEquals(0f, circularShortestDelta(3f, 4f, -7f), 0f)
    }

    @Test
    fun atRestTheConveyorPutsEveryDayInItsOrderedSlot() {
        for (start in 1..7) for (day in 1..7) {
            val slot = weekConveyorSlot(day, start.toFloat())
            assertEquals("día $day inicio $start", weekSlotOf(day, start).toFloat(), slot, 1e-5f)
            assertEquals(1f, weekConveyorAlpha(slot), 0f)
        }
    }

    @Test
    fun theConveyorAlphaFadesOnlyInTheHalfSlotBeyondEachEdge() {
        assertEquals(1f, weekConveyorAlpha(0f), 0f)
        assertEquals(1f, weekConveyorAlpha(3.3f), 0f)
        assertEquals(1f, weekConveyorAlpha(6f), 0f)
        assertEquals(0.5f, weekConveyorAlpha(-0.25f), 1e-6f)
        assertEquals(0f, weekConveyorAlpha(-0.5f), 0f)
        assertEquals(0.5f, weekConveyorAlpha(6.25f), 1e-6f)
        assertEquals(0f, weekConveyorAlpha(6.5f), 0f)
        assertEquals(0f, weekConveyorAlpha(Float.NaN), 0f)
        // Más allá del hueco de transición siempre está invisible.
        assertEquals(0f, weekConveyorAlpha(-3f), 0f)
        assertEquals(0f, weekConveyorAlpha(9f), 0f)
    }

    @Test
    fun whileSlidingADayNeverJumpsWhereItCanBeSeen() {
        // Del lunes al jueves y de vuelta, y del lunes al sábado por el camino corto: se muestrea la cinta y, cada vez que
        // un día salta de un borde al otro, tiene que ser invisible justo antes y justo después.
        for ((from, to) in listOf(1 to 4, 4 to 1, 1 to 6, 6 to 2, 7 to 3)) {
            val shift = circularShortestDelta(from.toFloat(), to.toFloat())
            for (day in 1..7) {
                var prev = weekConveyorSlot(day, from.toFloat())
                var p = 0.01f
                while (p <= 1.0001f) {
                    val now = weekConveyorSlot(day, from + shift * p)
                    if (abs(now - prev) > 1.5f) {
                        assertTrue("$from→$to día $day antes: ${weekConveyorAlpha(prev)}", weekConveyorAlpha(prev) <= 0.25f)
                        assertTrue("$from→$to día $day después: ${weekConveyorAlpha(now)}", weekConveyorAlpha(now) <= 0.25f)
                    } else {
                        assertTrue("$from→$to día $day saltó ${now - prev}", abs(now - prev) < 0.5f)
                    }
                    prev = now
                    p += 0.01f
                }
                // Termina en la ranura que le toca.
                assertEquals(weekSlotOf(day, to).toFloat(), weekConveyorSlot(day, from + shift), 1e-4f)
            }
        }
    }

    // ─── Lugar de cada día ───────────────────────────────────────────────────

    private val gymAndHome = setOf(TrainingPlace.HOME, TrainingPlace.GYM)

    @Test
    fun placesAlwaysComeInTheFixedOrder() {
        assertEquals(
            listOf(TrainingPlace.GYM, TrainingPlace.HOME, TrainingPlace.PUBLIC),
            orderedPlaces(setOf(TrainingPlace.PUBLIC, TrainingPlace.HOME, TrainingPlace.GYM)),
        )
        assertEquals(listOf(TrainingPlace.GYM, TrainingPlace.HOME), orderedPlaces(gymAndHome))
        assertEquals(emptyList<TrainingPlace>(), orderedPlaces(emptySet()))
    }

    @Test
    fun thePlacePickerNeedsTwoOrMorePlaces() {
        assertTrue(!showsDayPlaces(emptySet()))
        assertTrue(!showsDayPlaces(setOf(TrainingPlace.HOME)))
        assertTrue(showsDayPlaces(gymAndHome))
        assertTrue(showsDayPlaces(TrainingPlace.entries.toSet()))
    }

    @Test
    fun aDayWithoutAssignmentShowsTheFirstPlaceInOrder() {
        assertEquals(TrainingPlace.GYM, placeOfDay(3, gymAndHome, emptyMap()))
        assertEquals(TrainingPlace.HOME, placeOfDay(3, setOf(TrainingPlace.PUBLIC, TrainingPlace.HOME), emptyMap()))
        assertEquals(TrainingPlace.HOME, placeOfDay(3, gymAndHome, mapOf(3 to TrainingPlace.HOME)))
        assertEquals(TrainingPlace.GYM, placeOfDay(4, gymAndHome, mapOf(3 to TrainingPlace.HOME)))
    }

    @Test
    fun anAssignmentToAPlaceThatWasDroppedFallsBackToTheFirstOne() {
        val onlyGymAndPublic = setOf(TrainingPlace.GYM, TrainingPlace.PUBLIC)
        assertEquals(TrainingPlace.GYM, placeOfDay(2, onlyGymAndPublic, mapOf(2 to TrainingPlace.HOME)))
        assertNull(placeOfDay(2, emptySet(), mapOf(2 to TrainingPlace.HOME)))
    }

    @Test
    fun tappingAPlaceWalksTheChosenOnesAndWrapsAround() {
        val all = TrainingPlace.entries.toSet()
        assertEquals(TrainingPlace.HOME, nextPlace(TrainingPlace.GYM, all))
        assertEquals(TrainingPlace.PUBLIC, nextPlace(TrainingPlace.HOME, all))
        assertEquals(TrainingPlace.GYM, nextPlace(TrainingPlace.PUBLIC, all))
        // Con dos lugares alterna.
        assertEquals(TrainingPlace.HOME, nextPlace(TrainingPlace.GYM, gymAndHome))
        assertEquals(TrainingPlace.GYM, nextPlace(TrainingPlace.HOME, gymAndHome))
        // Salta los lugares no elegidos.
        val gymAndPublic = setOf(TrainingPlace.GYM, TrainingPlace.PUBLIC)
        assertEquals(TrainingPlace.PUBLIC, nextPlace(TrainingPlace.GYM, gymAndPublic))
        // Un lugar actual que ya no está entre los elegidos (o ninguno) arranca por el primero.
        assertEquals(TrainingPlace.GYM, nextPlace(TrainingPlace.HOME, gymAndPublic))
        assertEquals(TrainingPlace.GYM, nextPlace(null, gymAndPublic))
        assertNull(nextPlace(TrainingPlace.GYM, emptySet()))
    }

    // ─── Duración ────────────────────────────────────────────────────────────

    @Test
    fun durationsReadTheWayTheCopySays() {
        assertEquals("20 min", formatDuration(20))
        assertEquals("45 min", formatDuration(45))
        assertEquals("59 min", formatDuration(59))
        assertEquals("1 h", formatDuration(60))
        assertEquals("1 h 15 min", formatDuration(75))
        assertEquals("1 h 30 min", formatDuration(90))
        assertEquals("2 h", formatDuration(120))
        assertEquals("2 h 45 min", formatDuration(165))
        assertEquals("3 h", formatDuration(180))
        assertEquals("0 min", formatDuration(0))
        assertEquals("0 min", formatDuration(-15))
    }

    @Test
    fun durationsAreSpokenInFullWords() {
        assertEquals("45 minutos", spokenDuration(45))
        assertEquals("1 minuto", spokenDuration(1))
        assertEquals("1 hora", spokenDuration(60))
        assertEquals("1 hora y 15 minutos", spokenDuration(75))
        assertEquals("1 hora y 1 minuto", spokenDuration(61))
        assertEquals("2 horas", spokenDuration(120))
        assertEquals("2 horas y 45 minutos", spokenDuration(165))
        assertEquals("3 horas", spokenDuration(180))
        assertEquals("0 minutos", spokenDuration(-5))
    }

    @Test
    fun theHintCoversOnlyTheEdgesOfTheRange() {
        assertEquals("Con poco tiempo vamos a lo esencial.", sessionTimeHint(20))
        assertEquals("Con poco tiempo vamos a lo esencial.", sessionTimeHint(30))
        for (m in 35..85 step 5) assertNull("$m", sessionTimeHint(m))
        assertEquals("Con más tiempo sumamos aproximaciones, movilidad y descansos más largos.", sessionTimeHint(90))
        assertEquals("Con más tiempo sumamos aproximaciones, movilidad y descansos más largos.", sessionTimeHint(180))
    }

    @Test
    fun theShortcutsAreTheFiveOfTheCopyThatFitTheRange() {
        assertEquals(listOf(30, 45, 60, 90, 120), sessionShortcuts())
        assertEquals(listOf(30, 45, 60, 90), sessionShortcuts(20..100))
        // Con paso de 10 desde 20 el 45 no cae en una muesca.
        assertEquals(listOf(30, 60, 90, 120), sessionShortcuts(20..180, 10))
        assertEquals(emptyList<Int>(), sessionShortcuts(130..180))
    }
}
