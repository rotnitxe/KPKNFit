package com.example.kpkn.services.workout

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutVoiceMishearingCorrectionsTest {

    @Test
    fun unknown_noise_is_not_promoted_to_a_reported_two() {
        assertEquals("rir voz", WorkoutVoiceMishearingCorrections.correct("rir voz"))
        assertEquals("rpe voz", WorkoutVoiceMishearingCorrections.correct("rpe voz"))
    }

    @Test
    fun missing_effort_numbers_are_preserved_for_clarification() {
        listOf("rir toca", "rpe toca", "rir kilos", "rpe kilos").forEach {
            assertEquals(it, WorkoutVoiceMishearingCorrections.correct(it))
        }
    }

    @Test
    fun a_spoken_number_is_not_replaced_by_a_plausibility_guess() {
        assertEquals("ritmo doce", WorkoutVoiceMishearingCorrections.correct("ritmo doce"))
    }

    @Test
    fun reserve_reports_above_five_are_not_rewritten() {
        listOf("rir doce", "rir ocho", "rir diez", "rir reservas doce").forEach {
            assertEquals(it, WorkoutVoiceMishearingCorrections.correct(it))
        }
    }

    @Test
    fun plausible_rpe_is_never_mapped_to_dos() {
        // RPE 8 y 10 son valores válidos: no se tocan.
        assertEquals("rpe ocho", WorkoutVoiceMishearingCorrections.correct("rpe ocho"))
        assertEquals("rpe diez", WorkoutVoiceMishearingCorrections.correct("rpe diez"))
    }

    @Test
    fun valid_short_tokens_are_not_touched() {
        assertEquals("rir dos", WorkoutVoiceMishearingCorrections.correct("rir dos"))
        assertEquals("treinta kilos", WorkoutVoiceMishearingCorrections.correct("treinta kilos"))
    }
}
