package com.example.kpkn.screens.onboarding

import com.example.kpkn.domain.onboarding.EntrenoStepValues
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Entreno v2 · el día con más energía llega ya encendido en «¿Qué días puedes entrenar?» mientras la persona no haya
 * elegido días a mano ([withFreshestDay]); y los planes de días fijos siembran su semana desde ese día
 * ([EntrenoStepValues.defaultWeekdays] con `anchor`).
 */
class FreshDayPreselectionTest {

    @Test
    fun anEmptyWeekPreselectsTheFreshDay() {
        val draft = SetupWizardDraft().withFreshestDay(4)

        assertEquals(setOf(4), draft.selectedWeekdays)
        assertEquals(1, draft.daysPerWeek)
    }

    @Test
    fun movingTheFreshDayMovesTheAutomaticPreselection() {
        val draft = SetupWizardDraft().withFreshestDay(4).withFreshestDay(6)

        assertEquals(setOf(6), draft.selectedWeekdays)
        assertEquals(6, draft.weekStartDay)
    }

    @Test
    fun aManualChoiceOfDaysIsNeverOverwritten() {
        val chosenBeforeTheFreshDay = SetupWizardDraft().withWeekdays(setOf(1, 3, 5)).withFreshestDay(4)
        assertEquals(setOf(1, 3, 5), chosenBeforeTheFreshDay.selectedWeekdays)

        val chosenAfterTheFreshDay = SetupWizardDraft().withFreshestDay(4).withWeekdays(setOf(2, 4, 6)).withFreshestDay(6)
        assertEquals(setOf(2, 4, 6), chosenAfterTheFreshDay.selectedWeekdays)
    }

    @Test
    fun theSuggestedPatternStartsOnTheAnchorDay() {
        assertEquals(setOf(4, 6, 1), EntrenoStepValues.defaultWeekdays(3, anchor = 4))
        // Sin ancla (o con el lunes) el patrón sigue igual que antes.
        assertEquals(setOf(1, 3, 5), EntrenoStepValues.defaultWeekdays(3))
        assertEquals((1..7).toSet(), EntrenoStepValues.defaultWeekdays(7, anchor = 4))
        assertEquals(4, EntrenoStepValues.defaultWeekdays(4, anchor = 6).size)
    }
}
