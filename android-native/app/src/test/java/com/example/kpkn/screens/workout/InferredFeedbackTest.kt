package com.example.kpkn.screens.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Datos inferidos no se presentan como medidos (paquete 1.12b, hallazgo F-45).
 *
 * a. La claridad mental no se inventa: si nadie la pregunta, `clarityRating` es null (antes era 5).
 * b. Sin claridad preguntada, el factor de estrés es neutro; con valor se mantiene la lógica anterior.
 * El historial antiguo y su etiqueta de fatiga desaparecieron con la limpieza de Programa (P0).
 *
 * Son funciones puras: ninguna prueba usa reloj.
 */
class InferredFeedbackTest {

    // ─── a. Claridad sin preguntar ────────────────────────────────────────────────────────────────

    @Test
    fun clarityRating_sinPreguntarEsNulo() {
        val feedback = SessionClosingFeedback(
            overallFatigue = 3,
            systemAdjustment = 0,
            muscularAdjustment = 0,
            structureAdjustment = 0,
            discomforts = emptyList(),
        )

        assertNull(feedback.clarityRating)
    }

    // ─── b. Factor de estrés por claridad ─────────────────────────────────────────────────────────

    @Test
    fun clarityStressFactor_sinClaridadEsNeutro() {
        assertEquals(1.0, clarityStressFactor(null), DELTA)
    }

    @Test
    fun clarityStressFactor_claridadAltaReduceElEstres() {
        assertEquals(0.96, clarityStressFactor(8), DELTA)
        assertEquals(0.96, clarityStressFactor(10), DELTA)
    }

    @Test
    fun clarityStressFactor_claridadBajaSubeElEstres() {
        assertEquals(1.10, clarityStressFactor(4), DELTA)
        assertEquals(1.10, clarityStressFactor(1), DELTA)
    }

    @Test
    fun clarityStressFactor_claridadMediaEsNeutra() {
        assertEquals(1.0, clarityStressFactor(5), DELTA)
        assertEquals(1.0, clarityStressFactor(7), DELTA)
    }

    private companion object {
        const val DELTA = 1e-9
    }
}
