package com.example.kpkn.screens.sessioneditor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** AUD2-P03: editar mientras se calcula Impacto no publica el resultado de la petición anterior. */
class EditorAnalysisRequestGateTest {
    @Test
    fun onlyTheLatestRequestIsCurrent() {
        val gate = EditorAnalysisRequestGate()
        val first = gate.next()
        assertTrue(gate.isCurrent(first))
        val second = gate.next()
        assertFalse("una edición posterior invalida la petición anterior", gate.isCurrent(first))
        assertTrue(gate.isCurrent(second))
        val third = gate.next()
        assertFalse(gate.isCurrent(second))
        assertTrue(gate.isCurrent(third))
    }

    @Test
    fun aSlowFirstCalculation_cannotOverwriteTheNewerOne() {
        val gate = EditorAnalysisRequestGate()
        val published = mutableListOf<String>()
        val slow = gate.next()
        val fast = gate.next()
        // La rápida termina primero y publica; la lenta llega después y se descarta.
        if (gate.isCurrent(fast)) published += "rápida"
        if (gate.isCurrent(slow)) published += "lenta"
        assertTrue(published == listOf("rápida"))
    }
}
