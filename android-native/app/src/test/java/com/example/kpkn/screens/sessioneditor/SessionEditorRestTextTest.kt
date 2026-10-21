package com.example.kpkn.screens.sessioneditor

import org.junit.Assert.assertEquals
import org.junit.Test

/** El descanso en texto corrido del editor (SE9): el mismo formato que la dosis de la tarjeta de Program Detail. */
class SessionEditorRestTextTest {

    @Test
    fun descansoDeMenosDeUnMinutoVaConSuUnidad() {
        assertEquals("45 s", formatRestSummary(45))
    }

    @Test
    fun descansoDesdeUnMinutoVaEnMinSS() {
        assertEquals("1:00", formatRestSummary(60))
        assertEquals("2:30", formatRestSummary(150))
    }
}
