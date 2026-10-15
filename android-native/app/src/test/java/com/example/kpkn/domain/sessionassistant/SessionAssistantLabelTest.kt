package com.example.kpkn.domain.sessionassistant

import org.junit.Assert.assertEquals
import org.junit.Test

/** Aviso «bajar el descanso» del asistente (SE9): el número y su unidad van con espacio inseparable. */
class SessionAssistantLabelTest {

    @Test
    fun bajarDescansoEscribeLaUnidadConEspacioInseparable() {
        assertEquals("Bajar 15 s de descanso entre series", reduceRestLabel(seconds = 15))
    }
}
