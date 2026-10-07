package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.CardioType
import com.example.kpkn.domain.onboarding.CapabilityLevel
import com.example.kpkn.domain.onboarding.CapabilitySkill
import com.example.kpkn.domain.onboarding.EquipmentSymbolId
import com.example.kpkn.domain.onboarding.EquipmentSymbols
import com.example.kpkn.domain.onboarding.LiftMark
import com.example.kpkn.domain.onboarding.MuscleSymbol
import com.example.kpkn.domain.onboarding.MuscleSymbols
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.generator.RoutineLevel
import com.example.kpkn.domain.training.generator.RoutineMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Entreno v2 · el pedido del generador sale del borrador sin perder nada de lo declarado: días en el orden de la semana,
 * día con más energía, minutos, nivel y calibraciones, material por lugar, músculos, capacidades, cardio, marcas y la
 * semilla de «otra versión».
 */
class SetupRoutineRequestTest {

    private val catalog get() = CatalogCompositionTestSupport.catalog

    private val base = SetupWizardDraft(
        commitId = "alta-1",
        experience = SetupExperience.ADVANCED,
        trainingPlaces = setOf(TrainingPlace.GYM),
        trainingOptions = SetupWizardDraft().trainingOptions.copy(
            availability = EquipmentSymbols.availabilityOf(EquipmentSymbols.seedFor(setOf(TrainingPlace.GYM)), setOf(TrainingPlace.GYM)),
        ),
        goal = SetupGoal.STRENGTH_MUSCLE,
        selectedWeekdays = setOf(1, 3, 4, 6),
        daysPerWeek = 4,
        minutesPerSession = 75,
        freshestDay = 4,
        weekStartDay = 4,
        volumeAnswers = SetupVolumeAnswers(technique = 3, consistency = 2, strength = 1, mobility = 2),
    )

    @Test
    fun the_week_starts_on_the_chosen_day_and_keeps_the_freshest_day() {
        val request = base.routineRequest(RoutineMode.GENERAL_STRENGTH_MUSCLE, catalog)
        assertEquals("días desde el jueves", listOf(4, 6, 1, 3), request.weekdays)
        assertEquals(4, request.weekStartDay)
        assertEquals(4, request.freshestDay)
        assertEquals(75, request.targetMinutes)
        assertEquals(RoutineLevel.ADVANCED, request.level)
        assertEquals(listOf(3, 2, 1, 2), listOf(request.technique, request.consistency, request.strength, request.mobility))
        assertEquals("los ids del programa son los del alta", "alta-1", request.programId)
        // Sin inicio de semana tocado, la semana empieza el día con más energía.
        assertEquals(listOf(6, 1, 3, 4), base.copy(weekStartDay = null, freshestDay = 6).orderedWeekdays())
        // Sin ninguno de los dos, el primer día de entreno.
        assertEquals(listOf(1, 3, 4, 6), base.copy(weekStartDay = null, freshestDay = null).orderedWeekdays())
    }

    @Test
    fun what_the_person_declared_reaches_the_generator() {
        val symbols = setOf(MuscleSymbol.TRICEPS, MuscleSymbol.CHEST, MuscleSymbol.GLUTES)
        val draft = base.copy(
            trainingOptions = base.trainingOptions.copy(orderPriorities = MuscleSymbols.orderBagOf(symbols)),
            capabilities = mapOf(CapabilitySkill.PULL_UP to CapabilityLevel.SOME),
            liftMarks = mapOf(LiftMark.SQUAT to 140.0, LiftMark.OVERHEAD_PRESS to 60.0),
            planVariantSeed = 3,
        )
        val request = draft.routineRequest(RoutineMode.GENERAL_STRENGTH_MUSCLE, catalog)
        assertEquals("músculos en el orden del contrato", listOf(MuscleSymbol.CHEST, MuscleSymbol.TRICEPS, MuscleSymbol.GLUTES), request.priorityMuscles)
        assertEquals(draft.capabilities, request.capabilities)
        assertEquals("todas las marcas, no solo las de powerlifting", draft.liftMarks, request.marks)
        assertEquals(3, request.variantSeed)
        assertNull("sin cardio en un objetivo que no lo pide", request.cardio)
        val hybrid = draft.copy(goal = SetupGoal.COMPLETE_ATHLETE, cardioType = CardioType.RUN_OUTDOOR, cardioMinutes = 20)
        val cardio = hybrid.routineRequest(RoutineMode.GENERAL_HYBRID, catalog).cardio
        assertEquals(CardioType.RUN_OUTDOOR, cardio?.type)
        assertEquals(20, cardio?.minutes)
    }

    @Test
    fun minutes_and_level_stay_inside_the_generator_range() {
        assertEquals(20, base.copy(minutesPerSession = 5).routineRequest(RoutineMode.GENERAL_FUNCTIONAL, catalog).targetMinutes)
        assertEquals(180, base.copy(minutesPerSession = 240).routineRequest(RoutineMode.GENERAL_FUNCTIONAL, catalog).targetMinutes)
        assertEquals(RoutineLevel.NOVICE, base.copy(experience = SetupExperience.NEW).routineRequest(RoutineMode.GENERAL_FUNCTIONAL, catalog).level)
        assertEquals(RoutineLevel.RETURNING, base.copy(experience = SetupExperience.RETURNING).routineRequest(RoutineMode.GENERAL_FUNCTIONAL, catalog).level)
        assertEquals(RoutineLevel.NOVICE, base.copy(experience = null).routineRequest(RoutineMode.GENERAL_FUNCTIONAL, catalog).level)
    }

    @Test
    fun with_two_places_each_day_has_its_place_and_each_place_its_material() {
        val places = setOf(TrainingPlace.GYM, TrainingPlace.HOME)
        val selected = EquipmentSymbols.seedFor(setOf(TrainingPlace.GYM)) + EquipmentSymbolId.RINGS
        val draft = base.copy(
            trainingPlaces = places,
            trainingOptions = base.trainingOptions.copy(availability = EquipmentSymbols.availabilityOf(selected, places)),
            dayPlaces = mapOf(3 to TrainingPlace.HOME, 6 to TrainingPlace.HOME),
        )
        val request = draft.routineRequest(RoutineMode.GENERAL_STRENGTH_MUSCLE, catalog)
        assertEquals(places, request.places)
        assertEquals(
            mapOf(1 to TrainingPlace.GYM, 3 to TrainingPlace.HOME, 4 to TrainingPlace.GYM, 6 to TrainingPlace.HOME),
            request.dayPlaces,
        )
        assertEquals(places, request.availabilityByPlace.keys)
        assertEquals(
            setOf(EquipmentSymbolId.RINGS),
            EquipmentSymbols.selectedFrom(request.availabilityByPlace.getValue(TrainingPlace.HOME)),
        )
        // Con un solo lugar no hay lugar por día ni material por lugar.
        val single = base.routineRequest(RoutineMode.GENERAL_STRENGTH_MUSCLE, catalog)
        assertTrue(single.dayPlaces.isEmpty())
        assertTrue(single.availabilityByPlace.isEmpty())
    }
}
