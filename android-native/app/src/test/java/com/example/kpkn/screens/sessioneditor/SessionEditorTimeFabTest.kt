package com.example.kpkn.screens.sessioneditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** SE9: sin ejercicios el FAB de tiempo no muestra un «≈ 1 min» que el estimador da por defecto (port de cbf6ac33c). */
class SessionEditorTimeFabTest {

    @Test
    fun unaSesionSinEjerciciosNoMuestraTiempoAunqueElEstimadorDiga1Min() {
        assertNull(sessionTimeFabMinutes(hasExercises = false, breakdownMinutes = 1, fallbackMinutes = 1))
    }

    @Test
    fun conEjerciciosMuestraElDesgloseDelEstimador() {
        assertEquals(42, sessionTimeFabMinutes(hasExercises = true, breakdownMinutes = 42, fallbackMinutes = 0))
    }

    @Test
    fun conEjerciciosSinDesgloseUsaElRespaldo() {
        assertEquals(17, sessionTimeFabMinutes(hasExercises = true, breakdownMinutes = null, fallbackMinutes = 17))
    }
}
