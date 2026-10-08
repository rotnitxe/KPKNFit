package com.example.kpkn.domain.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntrenoStepValuesTest {

    @Test
    fun sessionMinutesRoundToTheNearestMultipleOfFiveWithinTheDialRange() {
        assertEquals(30, EntrenoStepValues.roundSessionMinutes(30.0))
        assertEquals(30, EntrenoStepValues.roundSessionMinutes(27.0))
        assertEquals(30, EntrenoStepValues.roundSessionMinutes(-5.0))
        assertEquals(45, EntrenoStepValues.roundSessionMinutes(47.0))
        assertEquals(50, EntrenoStepValues.roundSessionMinutes(48.0))
        assertEquals(60, EntrenoStepValues.roundSessionMinutes(60.0))
        assertEquals(75, EntrenoStepValues.roundSessionMinutes(75.4))
        assertEquals(180, EntrenoStepValues.roundSessionMinutes(180.0))
        assertEquals(180, EntrenoStepValues.roundSessionMinutes(500.0))
        for (value in 0..400) {
            val rounded = EntrenoStepValues.roundSessionMinutes(value.toDouble())
            assertTrue("$value → $rounded", rounded in 30..180 && rounded % 5 == 0)
        }
    }

    @Test
    fun theDialNeverOffersLessThanHalfAnHourNorTheTwentyAndTwentyFiveOfBefore() {
        // Decisión del 2026-10-08: con 20 min el 58 % de las combinaciones se pasaba de lo pedido; el mínimo es 30.
        assertEquals(30, EntrenoStepValues.SESSION_MINUTES_MIN)
        assertEquals(180, EntrenoStepValues.SESSION_MINUTES_MAX)
        assertEquals(5, EntrenoStepValues.SESSION_MINUTES_STEP)
        // El mínimo cae en una muesca del reloj (múltiplo del paso) y los antiguos 20 y 25 se leen como el mínimo.
        assertEquals(0, EntrenoStepValues.SESSION_MINUTES_MIN % EntrenoStepValues.SESSION_MINUTES_STEP)
        for (old in listOf(20.0, 22.0, 25.0, 29.0)) {
            assertEquals("$old", EntrenoStepValues.SESSION_MINUTES_MIN, EntrenoStepValues.roundSessionMinutes(old))
        }
    }

    @Test
    fun theLegacyChatQuestionNeverAcceptsLessThanTheDialMinimum() {
        // La pregunta heredada del chat (T_TIME) valida con el mismo mínimo que el reloj del asistente.
        val question = requireNotNull(WizChatGraph.question(WizChatQuestionId.T_TIME))
        for (below in listOf(0.0, 20.0, 25.0, 29.0)) {
            assertEquals(
                "$below",
                "El tiempo debe estar entre ${EntrenoStepValues.SESSION_MINUTES_MIN} y 100 minutos",
                WizChatValidation.validate(question, number = below),
            )
        }
        assertNull(WizChatValidation.validate(question, number = EntrenoStepValues.SESSION_MINUTES_MIN.toDouble()))
        assertNull(WizChatValidation.validate(question, number = 100.0))
    }

    @Test
    fun placeValuesRoundTripAndLegacyEnvironmentsReadAsTheirPlace() {
        for (place in TrainingPlace.entries) {
            assertEquals(place, EntrenoStepValues.placeOf(EntrenoStepValues.placeValue(place)))
        }
        assertEquals(TrainingPlace.GYM, EntrenoStepValues.placeOf("machines"))
        assertEquals(TrainingPlace.GYM, EntrenoStepValues.placeOf("Gimnasio completo"))
        assertEquals(TrainingPlace.HOME, EntrenoStepValues.placeOf("none"))
        assertEquals(TrainingPlace.HOME, EntrenoStepValues.placeOf("Sin material"))
        assertNull(EntrenoStepValues.placeOf("terraza"))
    }

    @Test
    fun goalValuesRoundTripAndLegacyValuesReadAsTodaysProfile() {
        for (profile in TrainingGoalProfile.entries) {
            assertEquals(profile, EntrenoStepValues.goalProfileOf(EntrenoStepValues.goalValue(profile)))
        }
        assertEquals(TrainingGoalProfile.POWERLIFTING, EntrenoStepValues.goalProfileOf("strength"))
        assertEquals(TrainingGoalProfile.BODYBUILDING, EntrenoStepValues.goalProfileOf("muscle"))
        assertEquals(TrainingGoalProfile.STRENGTH_MUSCLE, EntrenoStepValues.goalProfileOf("strength_muscle"))
        assertEquals(TrainingGoalProfile.STRENGTH_CARDIO, EntrenoStepValues.goalProfileOf("complete_athlete"))
        assertEquals(TrainingGoalProfile.STRENGTH_CARDIO, EntrenoStepValues.goalProfileOf("mixed"))
        assertEquals(TrainingGoalProfile.FUNCTIONAL_HEALTH, EntrenoStepValues.goalProfileOf("health"))
        assertNull(EntrenoStepValues.goalProfileOf("crossfit"))
    }

    @Test
    fun symbolValuesAreTheSymbolNamesAndTheOldCategoryNamesStillRead() {
        for (symbol in EquipmentSymbolId.entries) {
            assertEquals(symbol, EntrenoStepValues.symbolOf(symbol.name))
        }
        assertEquals(EquipmentSymbolId.BODYWEIGHT_ONLY, EntrenoStepValues.symbolOf("bodyweight_only"))
        assertEquals(EquipmentSymbolId.BANDS, EntrenoStepValues.symbolOf("BAND"))
        assertEquals(EquipmentSymbolId.SMITH, EntrenoStepValues.symbolOf("SMITH_MACHINE"))
        assertNull(EntrenoStepValues.symbolOf("SUPPORT"))
    }

    @Test
    fun weekdaysAreOneToSevenAndTheDefaultWeekIsSpreadWithRest() {
        assertEquals(1, EntrenoStepValues.weekdayOf("1"))
        assertEquals(7, EntrenoStepValues.weekdayOf("7"))
        assertNull(EntrenoStepValues.weekdayOf("0"))
        assertNull(EntrenoStepValues.weekdayOf("8"))
        assertNull(EntrenoStepValues.weekdayOf("lunes"))
        for (count in 1..7) {
            assertEquals("$count días", count, EntrenoStepValues.defaultWeekdays(count).size)
        }
        assertEquals(setOf(1, 3, 5), EntrenoStepValues.defaultWeekdays(3))
        assertEquals(setOf(1), EntrenoStepValues.defaultWeekdays(0))
        assertEquals((1..7).toSet(), EntrenoStepValues.defaultWeekdays(12))
    }
}
