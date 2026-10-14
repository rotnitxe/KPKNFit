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

    @Test
    fun elTiempoDeTextoCorridoTieneSuUnidadBajoUnMinutoYMSSDesdeAhi() {
        assertEquals("40 s", KpknSetFormat.doseTime(40))
        assertEquals("59 s", KpknSetFormat.doseTime(59))
        assertEquals("1:00", KpknSetFormat.doseTime(60))
        assertEquals("3:15", KpknSetFormat.doseTime(195))
    }

    @Test
    fun elTiempoDeTextoCorridoNoRompeConCeroONegativos() {
        assertEquals("0 s", KpknSetFormat.doseTime(0))
        assertEquals("0 s", KpknSetFormat.doseTime(-5))
    }
}
