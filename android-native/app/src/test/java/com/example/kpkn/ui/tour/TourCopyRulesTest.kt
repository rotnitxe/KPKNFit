package com.example.kpkn.ui.tour

import org.junit.Assert.assertTrue
import org.junit.Test

/** Reglas de texto de los pasos: título y cuerpo de cada paso de todos los recorridos, en 90 caracteres como mucho. */
class TourCopyRulesTest {
    @Test
    fun ningun_texto_de_paso_pasa_de_90_caracteres() {
        val tooLong = TourScripts.todos().values.flatten()
            .flatMap { listOf(it.title, it.body) }
            .filter { it.length > 90 }

        assertTrue("Textos de más de 90 caracteres: $tooLong", tooLong.isEmpty())
    }
}
