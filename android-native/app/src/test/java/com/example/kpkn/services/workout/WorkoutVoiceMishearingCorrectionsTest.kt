package com.example.kpkn.services.workout

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutVoiceMishearingCorrectionsTest {

    @Test
    fun rir_voz_is_corrected_to_rir_dos() {
        assertEquals("rir dos", WorkoutVoiceMishearingCorrections.correct("rir voz"))
        assertEquals("rpe dos", WorkoutVoiceMishearingCorrections.correct("rpe voz"))
    }

    @Test
    fun rir_toca_and_rir_kilos_are_corrected_to_rir_dos() {
        assertEquals("rir dos", WorkoutVoiceMishearingCorrections.correct("rir toca"))
        assertEquals("rpe dos", WorkoutVoiceMishearingCorrections.correct("rpe toca"))
        assertEquals("rir dos", WorkoutVoiceMishearingCorrections.correct("rir kilos"))
        assertEquals("rpe dos", WorkoutVoiceMishearingCorrections.correct("rpe kilos"))
    }

    @Test
    fun ritmo_doce_is_corrected_to_rir_dos() {
        assertEquals("rir dos", WorkoutVoiceMishearingCorrections.correct("ritmo doce"))
    }

    @Test
    fun valid_short_tokens_are_not_touched() {
        assertEquals("rir dos", WorkoutVoiceMishearingCorrections.correct("rir dos"))
        assertEquals("treinta kilos", WorkoutVoiceMishearingCorrections.correct("treinta kilos"))
    }
}
