package com.example.kpkn.ui.design

import org.junit.Assert.assertEquals
import org.junit.Test

/** Formato del descanso de la tabla de series (SE1): «2:30», sin cero a la izquierda en los minutos. */
class KpknSetFormatTest {

    @Test
    fun elDescansoVaEnMinutosSinCeroYSegundosConDosCifras() {
        assertEquals("2:30", KpknSetFormat.rest(150))
        assertEquals("1:30", KpknSetFormat.rest(90))
        assertEquals("0:45", KpknSetFormat.rest(45))
        assertEquals("10:05", KpknSetFormat.rest(605))
    }

    @Test
    fun ceroYNegativosNoRompen() {
        assertEquals("0:00", KpknSetFormat.rest(0))
        assertEquals("0:00", KpknSetFormat.rest(-5))
        assertEquals("60:00", KpknSetFormat.rest(3600))
    }
}
