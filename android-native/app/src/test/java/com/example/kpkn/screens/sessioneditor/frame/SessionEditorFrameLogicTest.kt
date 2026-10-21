package com.example.kpkn.screens.sessioneditor.frame

import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WeekVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionEditorFrameLogicTest {

    @Test
    fun ringTonesFollowTheOriginalThresholds() {
        // E21 del original: ok por debajo del 85 %, Energía hasta el 108 % y danger por encima.
        assertEquals(FrameRingTone.OK, frameTimeRingTone(84, 100))
        assertEquals(FrameRingTone.ENERGIA, frameTimeRingTone(85, 100))
        assertEquals(FrameRingTone.ENERGIA, frameTimeRingTone(108, 100))
        assertEquals(FrameRingTone.DANGER, frameTimeRingTone(109, 100))
        assertEquals(FrameRingTone.OK, frameTimeRingTone(0, 60))
    }

    @Test
    fun ringIsNeutralWithoutLimit() {
        assertEquals(FrameRingTone.NEUTRAL, frameTimeRingTone(54, null))
        assertEquals(FrameRingTone.NEUTRAL, frameTimeRingTone(54, 0))
        assertEquals(0f, frameTimeRingFraction(54, null), 0.0001f)
    }

    @Test
    fun ringFractionIsClampedToTheRing() {
        assertEquals(0.5f, frameTimeRingFraction(30, 60), 0.0001f)
        assertEquals(1f, frameTimeRingFraction(90, 60), 0.0001f)
    }

    @Test
    fun arrowsGoToTheNeighbourDayThatHasASession() {
        val order = frameWeekOrder(1)
        val sessionDays = setOf(1, 3, 6)
        assertEquals(3, neighbourSessionDay(order, currentDay = 1, sessionDays = sessionDays, step = 1))
        assertEquals(6, neighbourSessionDay(order, currentDay = 3, sessionDays = sessionDays, step = 1))
        assertNull(neighbourSessionDay(order, currentDay = 6, sessionDays = sessionDays, step = 1))
        assertEquals(3, neighbourSessionDay(order, currentDay = 6, sessionDays = sessionDays, step = -1))
        assertNull(neighbourSessionDay(order, currentDay = 1, sessionDays = sessionDays, step = -1))
        assertNull(neighbourSessionDay(order, currentDay = null, sessionDays = sessionDays, step = 1))
    }

    @Test
    fun weekStartsOnTheProgramDay() {
        assertEquals(listOf(7, 1, 2, 3, 4, 5, 6), frameWeekOrder(7))
    }

    @Test
    fun swipeGoesToTheAdjacentDayEvenIfEmpty() {
        val order = frameWeekOrder(1)
        assertEquals(2, adjacentWeekDay(order, currentDay = 1, step = 1))
        assertNull(adjacentWeekDay(order, currentDay = 1, step = -1))
        assertNull(adjacentWeekDay(order, currentDay = 7, step = 1))
    }

    @Test
    fun aDayOpensItsMainSession() {
        val sessions = listOf(
            Session(id = "a", name = "A", dayOfWeek = 2, isMainSession = false),
            Session(id = "b", name = "B", dayOfWeek = 2, isMainSession = true),
            Session(id = "c", name = "C", dayOfWeek = 3),
        )
        assertEquals("b", primarySessionOnDay(sessions, 2)?.id)
        assertEquals("c", primarySessionOnDay(sessions, 3)?.id)
        assertNull(primarySessionOnDay(sessions, 5))
    }

    @Test
    fun summaryLineNamesTheCountsAndTheMinutes() {
        assertEquals("7 ejercicios · 22 series · ≈ 54 min", sessionFrameSummary(7, 22, 54))
        assertEquals("1 ejercicio · 1 serie", sessionFrameSummary(1, 1, null))
        assertEquals("Sin ejercicios", sessionFrameSummary(0, 0, 0))
    }

    @Test
    fun dayLabelShowsTheDayAndTheSessionName() {
        assertEquals("Jue · Pierna A", frameDayLabel(4, "Pierna A"))
        assertEquals("Sesión", frameDayLabel(null, ""))
    }

    @Test
    fun nextFreeVariantSkipsTheOnesInUse() {
        assertEquals(WeekVariant.C, nextFreeVariant(listOf(WeekVariant.A, WeekVariant.B)))
        assertNull(nextFreeVariant(WeekVariant.values().toList()))
    }
}
