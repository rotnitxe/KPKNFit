package com.example.kpkn.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/** Avisos (SE9): el éxito no lleva emoji en el texto (la marca KPKN lo pinta aparte); los demás tipos conservan su emoji. */
class KpknSnackbarTest {

    @Test
    fun elExitoNoLlevaEmojiEnElTexto() {
        assertEquals("Sesión creada para Sábado", snackbarText(SnackbarType.SUCCESS, "Sesión creada para Sábado"))
    }

    @Test
    fun losOtrosTiposConservanSuEmoji() {
        assertEquals("❌ No se pudo guardar", snackbarText(SnackbarType.DANGER, "No se pudo guardar"))
    }

    @Test
    fun sinEmojiElAvisoEsDeExito() {
        assertEquals(SnackbarType.SUCCESS, inferSnackbarType("Sesión creada para Sábado"))
        assertEquals(SnackbarType.DANGER, inferSnackbarType("❌ No se pudo guardar"))
    }
}
