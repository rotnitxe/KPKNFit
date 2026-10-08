package com.example.kpkn.screens.onboarding.design.entreno.layout

import androidx.compose.ui.geometry.Rect
import com.example.kpkn.domain.onboarding.TrainingPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La parte pura del tablero de la semana: orden de las ranuras, qué ranura queda bajo un punto, cómo cambia la
 * colocación al mover una sesión, el auto-desplazamiento y los textos. Sin dibujar nada.
 */
class WeekLayoutMathTest {

    // ------------------------------------------------------------ slotOrder

    @Test
    fun slotOrderStartsOnTheGivenDayAndWrapsAround() {
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), slotOrder(1))
        assertEquals(listOf(4, 5, 6, 7, 1, 2, 3), slotOrder(4))
        assertEquals(listOf(7, 1, 2, 3, 4, 5, 6), slotOrder(7))
    }

    @Test
    fun slotOrderIsAlwaysAPermutationOfTheWeek() {
        for (start in 1..7) {
            val order = slotOrder(start)
            assertEquals(start, order.first())
            assertEquals((1..7).toList(), order.sorted())
        }
    }

    @Test
    fun anInvalidStartCountsAsMonday() {
        for (start in listOf(0, 8, -3, 100, Int.MIN_VALUE, Int.MAX_VALUE)) {
            assertEquals("inicio $start", listOf(1, 2, 3, 4, 5, 6, 7), slotOrder(start))
        }
    }

    // ------------------------------------------------------------ swapAssignment

    private val base = mapOf(1 to "a", 3 to "b", 5 to "c")

    @Test
    fun movingToAFreeDayFreesTheOldOne() {
        assertEquals(mapOf(2 to "a", 3 to "b", 5 to "c"), swapAssignment(base, "a", 2))
        assertEquals(mapOf(1 to "a", 3 to "b", 7 to "c"), swapAssignment(base, "c", 7))
    }

    @Test
    fun movingOntoAnOccupiedDaySwapsBothSessions() {
        assertEquals(mapOf(1 to "b", 3 to "a", 5 to "c"), swapAssignment(base, "a", 3))
        assertEquals(mapOf(1 to "a", 3 to "c", 5 to "b"), swapAssignment(base, "b", 5))
    }

    @Test
    fun movingToTheSameDayChangesNothing() {
        assertSame(base, swapAssignment(base, "b", 3))
    }

    @Test
    fun anUnplacedSessionOrAnInvalidDayChangesNothing() {
        assertSame(base, swapAssignment(base, "zzz", 2))
        assertSame(base, swapAssignment(base, "a", 0))
        assertSame(base, swapAssignment(base, "a", 8))
        assertSame(base, swapAssignment(base, "a", -1))
    }

    @Test
    fun swappingNeverModifiesTheInput() {
        val input = linkedMapOf(1 to "a", 3 to "b")
        val copy = LinkedHashMap(input)
        swapAssignment(input, "a", 3)
        swapAssignment(input, "a", 6)
        assertEquals(copy, input)
    }

    @Test
    fun aSwapDoneTwiceGoesBack() {
        val once = swapAssignment(base, "a", 3)
        assertEquals(base, swapAssignment(once, "a", 1))
        val moved = swapAssignment(base, "a", 6)
        assertEquals(base, swapAssignment(moved, "a", 1))
    }

    @Test
    fun swappingKeepsTheNumberOfSessions() {
        for (session in listOf("a", "b", "c")) {
            for (day in 1..7) {
                val result = swapAssignment(base, session, day)
                assertEquals(3, result.size)
                assertEquals(setOf("a", "b", "c"), result.values.toSet())
            }
        }
    }

    // ------------------------------------------------------------ hitSlot

    private val rects: Map<Int, Rect> = (0 until 3).associate { i ->
        (i + 1) to Rect(i * 110f, 0f, i * 110f + 100f, 200f)
    }

    @Test
    fun hitSlotFindsTheSlotUnderThePoint() {
        assertEquals(1, hitSlot(50f, 100f, rects))
        assertEquals(2, hitSlot(150f, 10f, rects))
        assertEquals(3, hitSlot(300f, 199f, rects))
    }

    @Test
    fun hitSlotIncludesTheEdges() {
        assertEquals(1, hitSlot(0f, 0f, rects))
        assertEquals(1, hitSlot(100f, 200f, rects))
    }

    @Test
    fun hitSlotMissesWhatIsOutsideAndTheGapsWithoutSlop() {
        assertNull(hitSlot(105f, 100f, rects))
        assertNull(hitSlot(50f, 201f, rects))
        assertNull(hitSlot(-1f, 50f, rects))
        assertNull(hitSlot(331f, 50f, rects))
    }

    @Test
    fun hitSlotSlopFillsTheGapsAndTheNearestCenterWins() {
        // En el hueco entre 1 y 2 (100..110) con tolerancia: gana la de centro más cercano.
        assertEquals(1, hitSlot(103f, 100f, rects, slopX = 10f))
        assertEquals(2, hitSlot(108f, 100f, rects, slopX = 10f))
        // Un poco por encima de la tira, con tolerancia vertical.
        assertEquals(2, hitSlot(150f, -8f, rects, slopX = 0f, slopY = 10f))
        assertNull(hitSlot(150f, -11f, rects, slopX = 0f, slopY = 10f))
    }

    @Test
    fun hitSlotSingleSlopAppliesToBothAxes() {
        assertEquals(1, hitSlot(-5f, -5f, rects, slopX = 6f))
        assertNull(hitSlot(-7f, -5f, rects, slopX = 6f))
    }

    @Test
    fun hitSlotBreaksTiesWithTheSmallerDay() {
        val twin = mapOf(5 to Rect(0f, 0f, 10f, 10f), 2 to Rect(0f, 0f, 10f, 10f))
        assertEquals(2, hitSlot(5f, 5f, twin))
    }

    @Test
    fun hitSlotIgnoresEmptyRectsInvalidPointsAndEmptyMaps() {
        assertNull(hitSlot(1f, 1f, emptyMap()))
        assertNull(hitSlot(Float.NaN, 1f, rects))
        assertNull(hitSlot(1f, Float.NaN, rects))
        assertNull(hitSlot(5f, 5f, mapOf(1 to Rect(5f, 5f, 5f, 20f))))
        // Una tolerancia negativa no encoge las ranuras.
        assertEquals(1, hitSlot(50f, 100f, rects, slopX = -20f))
    }

    // ------------------------------------------------------------ auto-desplazamiento

    @Test
    fun noPushInTheMiddleOfTheViewport() {
        assertEquals(0f, autoScrollFraction(150f, 300f, 50f), 0f)
        assertEquals(0f, autoScrollFraction(50f, 300f, 50f), 0f)
        assertEquals(0f, autoScrollFraction(250f, 300f, 50f), 0f)
        assertEquals(0f, autoScrollDelta(150f, 300f, 50f, 600f, 0.016f), 0f)
    }

    @Test
    fun theEdgesPushTowardsThemWithALinearRamp() {
        assertEquals(-1f, autoScrollFraction(0f, 300f, 50f), 1e-6f)
        assertEquals(-0.5f, autoScrollFraction(25f, 300f, 50f), 1e-6f)
        assertEquals(1f, autoScrollFraction(300f, 300f, 50f), 1e-6f)
        assertEquals(0.5f, autoScrollFraction(275f, 300f, 50f), 1e-6f)
    }

    @Test
    fun beyondTheEdgePushesAtMostFullSpeed() {
        assertEquals(-1f, autoScrollFraction(-80f, 300f, 50f), 1e-6f)
        assertEquals(1f, autoScrollFraction(400f, 300f, 50f), 1e-6f)
    }

    @Test
    fun theDeltaIsSpeedTimesFrameTimeAndKeepsTheSign() {
        assertEquals(-600f * 0.016f, autoScrollDelta(0f, 300f, 50f, 600f, 0.016f), 1e-4f)
        assertEquals(600f * 0.016f, autoScrollDelta(300f, 300f, 50f, 600f, 0.016f), 1e-4f)
        assertEquals(300f * 0.016f, autoScrollDelta(275f, 300f, 50f, 600f, 0.016f), 1e-4f)
    }

    @Test
    fun aLongFrameDoesNotJumpTheStrip() {
        val capped = autoScrollDelta(300f, 300f, 50f, 600f, 0.05f)
        assertEquals(capped, autoScrollDelta(300f, 300f, 50f, 600f, 2f), 1e-4f)
    }

    @Test
    fun theTwoEdgeZonesNeverOverlapInANarrowViewport() {
        // Un visor de 60 con bordes de 50: cada zona se acorta a 30, así el centro exacto no empuja a ningún lado.
        assertEquals(0f, autoScrollFraction(30f, 60f, 50f), 0f)
        assertEquals(-1f, autoScrollFraction(0f, 60f, 50f), 1e-6f)
        assertEquals(1f, autoScrollFraction(60f, 60f, 50f), 1e-6f)
        assertTrue(autoScrollFraction(10f, 60f, 50f) < 0f)
        assertTrue(autoScrollFraction(50f, 60f, 50f) > 0f)
    }

    @Test
    fun invalidInputsNeverScroll() {
        assertEquals(0f, autoScrollFraction(Float.NaN, 300f, 50f), 0f)
        assertEquals(0f, autoScrollFraction(10f, 0f, 50f), 0f)
        assertEquals(0f, autoScrollFraction(10f, -300f, 50f), 0f)
        assertEquals(0f, autoScrollFraction(10f, 300f, 0f), 0f)
        assertEquals(0f, autoScrollFraction(10f, Float.POSITIVE_INFINITY, 50f), 0f)
        assertEquals(0f, autoScrollDelta(0f, 300f, 50f, 0f, 0.016f), 0f)
        assertEquals(0f, autoScrollDelta(0f, 300f, 50f, 600f, 0f), 0f)
        assertEquals(0f, autoScrollDelta(0f, 300f, 50f, 600f, -1f), 0f)
        assertEquals(0f, autoScrollDelta(0f, 300f, 50f, 600f, Float.NaN), 0f)
    }

    // ------------------------------------------------------------ sanitizeAssignment

    @Test
    fun sanitizeKeepsOnlyKnownSessionsOnValidDaysOnce() {
        val order = slotOrder(1)
        val messy = mapOf(0 to "a", 1 to "a", 2 to "ghost", 3 to "b", 4 to "a", 9 to "b", 6 to "c")
        // «a» aparece en 1 y 4: se queda con el primero en el orden de la tira; «ghost» no existe; 0 y 9 no son días.
        assertEquals(mapOf(1 to "a", 3 to "b", 6 to "c"), sanitizeAssignment(messy, setOf("a", "b", "c"), order))
    }

    @Test
    fun sanitizeKeepsTheFirstDayInTheOrderOfTheStrip() {
        // Con la semana empezando en jueves, el 4 va antes que el 1.
        val repeated = mapOf(1 to "a", 4 to "a")
        assertEquals(mapOf(4 to "a"), sanitizeAssignment(repeated, setOf("a"), slotOrder(4)))
        assertEquals(mapOf(1 to "a"), sanitizeAssignment(repeated, setOf("a"), slotOrder(1)))
    }

    // ------------------------------------------------------------ mini-semana de un reparto

    @Test
    fun splitPatternUsesTheCurrentDaysWhenTheCountMatches() {
        assertEquals(listOf(1, 3, 5, 6), splitPatternDays(4, 1, listOf(6, 5, 3, 1)))
        // Con inicio en jueves, el orden de la tira manda.
        assertEquals(listOf(5, 7, 1, 2), splitPatternDays(4, 4, setOf(1, 2, 5, 7)))
    }

    @Test
    fun splitPatternSpreadsTheDaysWhenTheCountDiffers() {
        assertEquals(listOf(1, 3, 5), splitPatternDays(3, 1, listOf(1, 2)))
        assertEquals(listOf(4, 6, 1), splitPatternDays(3, 4, emptyList()))
        assertEquals(listOf(1), splitPatternDays(1, 1, listOf(2, 3)))
        assertEquals(listOf(1, 4), splitPatternDays(2, 1, emptyList()))
    }

    @Test
    fun splitPatternHasOneDayPerTitleAndNeverRepeatsADay() {
        for (count in 1..7) {
            for (start in 1..7) {
                val days = splitPatternDays(count, start, emptyList())
                assertEquals("$count días", count, days.size)
                assertEquals(days.size, days.toSet().size)
                assertTrue(days.all { it in 1..7 })
                // Siempre en el orden de la semana de la persona.
                val order = slotOrder(start)
                assertEquals(days, days.sortedBy { order.indexOf(it) })
            }
        }
    }

    @Test
    fun splitPatternClampsTheCount() {
        assertEquals(emptyList<Int>(), splitPatternDays(0, 1, listOf(1)))
        assertEquals(emptyList<Int>(), splitPatternDays(-2, 1, listOf(1)))
        assertEquals((1..7).toList(), splitPatternDays(12, 1, emptyList()))
    }

    @Test
    fun theSameTitleKeepsTheSameColorIndex() {
        assertEquals(listOf(0, 1, 0, 1), splitTitleColorIndexes(listOf("Torso", "Pierna", "Torso", "Pierna"), 6))
        assertEquals(listOf(0, 1, 2, 0, 1, 2), splitTitleColorIndexes(listOf("A", "B", "C", "A", "B", "C"), 6))
    }

    @Test
    fun colorIndexesIgnoreCaseAndSpacesAndWrapAroundThePalette() {
        assertEquals(listOf(0, 0, 0), splitTitleColorIndexes(listOf("Torso", " torso ", "TORSO"), 6))
        assertEquals(listOf(0, 1, 2, 0), splitTitleColorIndexes(listOf("a", "b", "c", "d"), 3))
        assertEquals(listOf(0, 0), splitTitleColorIndexes(listOf("a", "b"), 0))
        assertEquals(emptyList<Int>(), splitTitleColorIndexes(emptyList(), 6))
    }

    // ------------------------------------------------------------ textos

    @Test
    fun dayNamesAndShortNamesAreInSpanishAndDistinct() {
        assertEquals("Lunes", weekDayName(1))
        assertEquals("Domingo", weekDayName(7))
        assertEquals("Mié", weekDayShort(3))
        assertEquals(listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"), (1..7).map(::weekDayShort))
        assertEquals(7, (1..7).map(::weekDayShort).toSet().size)
        assertEquals("Lunes", weekDayName(0))
        assertEquals("Lunes", weekDayName(9))
    }

    @Test
    fun theDetailJoinsWhatTheSessionHasAndNothingElse() {
        assertEquals("60 min · 6 ejercicios", sessionDetailText(WeekLayoutSession("a", "Torso", "", 60, 6, false)))
        assertEquals("1 ejercicio", sessionDetailText(WeekLayoutSession("a", "Torso", "", 0, 1, false)))
        assertEquals("45 min", sessionDetailText(WeekLayoutSession("a", "Torso", "", 45, 0, false)))
        assertEquals("", sessionDetailText(WeekLayoutSession("a", "Torso", "", 0, 0, false)))
    }

    @Test
    fun thePlaceComesFromTheSessionOrFromTheEndOfItsFocus() {
        assertEquals(TrainingPlace.HOME, WeekLayoutSession("a", "T", "Pecho · En casa", 1, 1, false).shownPlace())
        assertEquals(TrainingPlace.GYM, WeekLayoutSession("a", "T", "Gimnasio", 1, 1, false).shownPlace())
        assertEquals(TrainingPlace.PUBLIC, WeekLayoutSession("a", "T", "Piernas · en espacios públicos", 1, 1, false).shownPlace())
        // El lugar propio manda sobre lo que diga el foco.
        assertEquals(
            TrainingPlace.GYM,
            WeekLayoutSession("a", "T", "Pecho · En casa", 1, 1, false, place = TrainingPlace.GYM).shownPlace(),
        )
        // Un foco que no acaba en un lugar no dice ninguno.
        assertNull(WeekLayoutSession("a", "T", "Pecho y espalda", 1, 1, false).shownPlace())
        assertNull(WeekLayoutSession("a", "T", "Casa de cambios · Pecho", 1, 1, false).shownPlace())
        assertNull(WeekLayoutSession("a", "T", "", 1, 1, false).shownPlace())
    }

    @Test
    fun theDescriptionReadsTitleFocusAmountsAndDay() {
        val main = WeekLayoutSession("a", "Torso A", "Pecho y espalda", 60, 6, isMain = true)
        assertEquals(
            "Torso A, sesión principal. Pecho y espalda. 60 minutos, 6 ejercicios. Lunes.",
            sessionDescription(main, 1),
        )
        val plain = WeekLayoutSession("b", "Pierna", "pierna", 1, 1, isMain = false)
        // El foco igual al título no se repite; el singular concuerda; sin día no hay día.
        assertEquals("Pierna. 1 minuto, 1 ejercicio.", sessionDescription(plain, null))
        val bare = WeekLayoutSession("c", "Core", "", 0, 0, isMain = false)
        assertEquals("Core. Domingo.", sessionDescription(bare, 7))
    }

    @Test
    fun theMoveAnnouncementNamesWhoMovedWhere() {
        val sessions = listOf(
            WeekLayoutSession("a", "Torso A", "", 60, 6, true),
            WeekLayoutSession("b", "Pierna A", "", 60, 6, false),
        )
        val before = mapOf(1 to "a", 3 to "b")
        assertEquals("Torso A pasa al martes.", moveAnnouncement(sessions, before, mapOf(2 to "a", 3 to "b")))
        assertEquals(
            "Torso A pasa al miércoles y Pierna A pasa al lunes.",
            moveAnnouncement(sessions, before, mapOf(1 to "b", 3 to "a")),
        )
        assertNull(moveAnnouncement(sessions, before, before))
        // Una sesión que no estaba colocada o que no se conoce no se anuncia.
        assertNull(moveAnnouncement(sessions, before, mapOf(1 to "a", 3 to "b", 5 to "zzz")))
    }
}
