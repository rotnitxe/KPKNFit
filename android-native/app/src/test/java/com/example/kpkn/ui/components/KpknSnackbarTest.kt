package com.example.kpkn.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarVisuals
import com.example.kpkn.ui.components.icons.KpknLineIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** Avisos (SE12): sin emoji en el texto; el tipo viaja con el aviso y el icono de KPKN Line dice cuál es. */
class KpknSnackbarTest {

    private fun aviso(texto: String, tipo: SnackbarType) = KpknSnackbarVisuals(
        message = texto,
        type = tipo,
        actionLabel = null,
        withDismissAction = true,
        duration = SnackbarDuration.Short,
    )

    @Test
    fun elTextoDelAvisoLlegaTalCualSinEmojiNiPrefijo() {
        assertEquals("No se pudo guardar", aviso("No se pudo guardar", SnackbarType.DANGER).message)
        assertEquals("Sesión creada para Sábado", aviso("Sesión creada para Sábado", SnackbarType.SUCCESS).message)
    }

    @Test
    fun elTipoViajaConElAviso() {
        assertEquals(SnackbarType.DANGER, aviso("No se pudo guardar", SnackbarType.DANGER).snackbarType())
        assertEquals(SnackbarType.SUGGESTION, aviso("Activa alarmas", SnackbarType.SUGGESTION).snackbarType())
    }

    @Test
    fun unAvisoAjenoAlSetDeKpknSeLeeComoDeExito() {
        val ajeno = object : SnackbarVisuals {
            override val message = "Guardado"
            override val actionLabel: String? = null
            override val withDismissAction = false
            override val duration = SnackbarDuration.Short
        }

        assertEquals(SnackbarType.SUCCESS, ajeno.snackbarType())
    }

    @Test
    fun cadaTipoTieneSuIconoDeKpknLine() {
        assertSame(KpknLineIcons.Tower, SnackbarType.SUCCESS.icon())
        assertSame(KpknLineIcons.Alert, SnackbarType.DANGER.icon())
        assertSame(KpknLineIcons.Trophy, SnackbarType.ACHIEVEMENT.icon())
        assertSame(KpknLineIcons.Bulb, SnackbarType.SUGGESTION.icon())
    }
}
