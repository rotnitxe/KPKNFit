package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.resolvedSchedulePlan
import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.data.programs.TrainingFocus
import com.example.kpkn.data.protocols.definitions.NativeProfileKind
import com.example.kpkn.domain.exercises.catalogv2.InMemoryExerciseCatalogRepositoryV2
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.PersonalizerInput
import com.example.kpkn.domain.training.SimpleCyclePersonalizer
import com.example.kpkn.screens.onboarding.design.entreno.layout.slotOrder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Entreno v2 · la semana empieza en el día de inicio del alta (el día con más energía mientras no se mueva). Lo comprueban
 * el programa de un plan propio (su `startDay`, su calendario de sesiones y el orden del revelado) y el orden de «Tu
 * semana». El programa de autor toma el mismo `effectiveWeekStart()` en el ViewModel, que no se puede montar desde JVM.
 */
class WeekStartOrderTest {

    /** Jueves con día fuerte; entrena lunes, jueves y sábado. */
    private val draft = SetupWizardDraft().withWeekdays(setOf(1, 4, 6)).withFreshestDay(4)

    @Test
    fun theWeekStartIsTheFreshDayUntilThePersonMovesIt() {
        assertEquals(4, draft.effectiveWeekStart())
        assertEquals(1, draft.withWeekStart(1).effectiveWeekStart())
    }

    @Test
    fun anOwnPlanStartsItsWeekOnTheFreshDayAndKeepsEachSessionOnItsDay() {
        val catalog = InMemoryExerciseCatalogRepositoryV2(CatalogCompositionTestSupport.catalog)
            .also { runBlocking { it.load() } }
        val result = SimpleCyclePersonalizer(catalog).personalize(
            "week-start-own",
            PersonalizerInput(
                catalogEntryId = NativeProfileKind.STRENGTH.entryId,
                focus = TrainingFocus.FULL_BODY,
                frequency = 3,
                weekdays = listOf(1, 4, 6),
                weekStartDay = draft.effectiveWeekStart(),
                equipment = setOf("bodyweight", "barbell", "rack", "bench"),
                level = CatalogLevel.INTERMEDIATE,
                availableMinutes = 100,
            ),
        )
        val program = requireNotNull(result.program)

        assertEquals(4, program.startDay)
        assertEquals(4, program.resolvedSchedulePlan().weekStartDay)
        val sessionDays = requireNotNull(SetupPlanReveals.firstTrainingWeek(program)).sessions
            .mapNotNull { SetupPlanReveals.dayOf(it) }
            .toSet()
        assertEquals(setOf(1, 4, 6), sessionDays)
        // El revelado ordena la semana tipo desde el día de inicio: jueves, sábado y luego lunes.
        assertEquals(listOf(4, 6, 1), SetupPlanReveals.weekOf(program).map { it.day })
    }

    @Test
    fun theWeekStripStartsOnThatDayAndWrapsAround() {
        assertEquals(listOf(4, 5, 6, 7, 1, 2, 3), slotOrder(4))
    }
}
