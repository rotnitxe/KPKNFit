package com.example.kpkn.ui.tour

import org.junit.Assert.assertTrue
import org.junit.Test

/** Reglas de texto de los pasos. Hoy vigila la demo; F2 apuntará esta prueba a sus recorridos registrados. */
class TourCopyRulesTest {
    private val demoSteps = listOf(
        TourStep("home.sesion", "Sesión de hoy", "Toca para ver los ejercicios."),
        TourStep(
            "home.rings",
            "Tus RINGS",
            "Toca uno. Si no coincide con cómo te sientes, ajústalo.",
            TourStepKind.TRY,
        ),
        TourStep("home.programas", "Tus programas", "Aquí están los programas que sigues y el que tienes activo."),
    )

    @Test
    fun ningun_texto_de_paso_pasa_de_90_caracteres() {
        val tooLong = demoSteps.flatMap { listOf(it.title, it.body) }.filter { it.length > 90 }

        assertTrue("Textos de más de 90 caracteres: $tooLong", tooLong.isEmpty())
    }
}
