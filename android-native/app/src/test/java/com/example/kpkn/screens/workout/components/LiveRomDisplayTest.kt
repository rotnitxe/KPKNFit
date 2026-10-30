package com.example.kpkn.screens.workout.components

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.RecordedSetPayload
import com.example.kpkn.screens.workout.historyExpandedSetText
import com.example.kpkn.screens.workout.historySetMeta
import com.example.kpkn.screens.workout.historySetValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pulido v1 (15): el ROM se muestra donde se habla de la serie («120 × 4 · ROM 80 %») y solo se anota: no cambia ningún otro dato. */
class LiveRomDisplayTest {
    private fun set(rom: Int?, rir: Int? = 2) = CompletedSet(id = "s", weight = 120.0, reps = 4, rir = rir, rom = rom)

    @Test fun elDescansoDiceElRomDeLaUltimaSerie() {
        assertEquals("120 × 4 · RIR 2 · ROM 80 %", restRef02LastSetText(set(80), customUnit = null))
        assertEquals("120 × 4 · RIR 2", restRef02LastSetText(set(null), customUnit = null))
    }

    @Test fun elHistorialDesplegadoDiceSiempreElRomAnotado() {
        assertEquals("120 × 4 · RIR 2 · ROM 80 %", historyExpandedSetText(set(80)))
        assertEquals("120 × 4 · RIR 2 · ROM 100 %", historyExpandedSetText(set(100)))
        assertEquals("120 × 4 · RIR 2", historyExpandedSetText(set(null)))
        assertEquals("120 × 4 · ROM 70 %", historyExpandedSetText(set(70, rir = null)))
    }

    @Test fun laLineaDeLaSesionSoloDiceElRomSiLaSerieNoFueCompleta() {
        assertEquals("RIR 2 · ROM 80 %", historySetMeta(set(80), tag = null))
        assertEquals("RIR 2 · ROM 90 %", historySetMeta(set(90), tag = null))
        assertEquals("RIR 2", historySetMeta(set(95), tag = null))
        assertEquals("RIR 2", historySetMeta(set(100), tag = null))
        assertEquals("RIR 2 · ROM 80 % · Competición", historySetMeta(set(80), tag = "Competición"))
    }

    @Test fun elRomNoCambiaLaCifraDeLaSerie() {
        assertEquals("120 × 4", historySetValue(set(40)))
        assertEquals(historySetValue(set(null)), historySetValue(set(40)))
    }

    @Test fun elPayloadGuardaElRomEnPorcentajeYLosRegistrosAnterioresNoLoTraen() {
        assertNull(RecordedSetPayload(exerciseId = "press").romPercent)
        assertEquals(80, RecordedSetPayload(exerciseId = "press", romPercent = 80).romPercent)
    }
}
