package com.example.kpkn.screens.workout.components

import com.example.kpkn.data.models.Settings
import com.example.kpkn.domain.calculations.LoadRounding
import com.example.kpkn.domain.workout.LiveInputDockEngine
import com.example.kpkn.domain.workout.LiveInputDockMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveInputDockModePersistenceTest {
    @Test
    fun `parseLiveInputDockMode preserva preferencia guardada KEYPAD y LENS`() {
        assertEquals(LiveInputDockMode.LENS, LiveInputDockEngine.parseLiveInputDockMode(Settings().preferredLiveInputDockMode))
        assertEquals(LiveInputDockMode.KEYPAD, LiveInputDockEngine.parseLiveInputDockMode("KEYPAD"))
        assertEquals(LiveInputDockMode.LENS, LiveInputDockEngine.parseLiveInputDockMode("LENS"))
        assertEquals(LiveInputDockMode.LENS, LiveInputDockEngine.parseLiveInputDockMode("desconocido"))
    }

    @Test
    fun `los incrementos de disco configurables incluyen 0_25 kg`() {
        assertTrue(0.25 in LoadRounding.KG_INCREMENT_OPTIONS)
        assertTrue(2.5 in LiveInputDockEngine.PRESET_LOAD_STEPS_KG)
    }
}
