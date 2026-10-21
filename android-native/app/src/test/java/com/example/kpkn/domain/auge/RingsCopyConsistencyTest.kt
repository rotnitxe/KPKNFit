package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.AugeAdaptiveCache
import com.example.kpkn.data.models.RecoveryBand
import com.example.kpkn.data.models.RecoveryChannelId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RingsCopyConsistencyTest {

    @Test
    fun `los avisos de carga usan tendencia reciente y una actuacion comun`() {
        val adjustment = LoadAdvisoryEngine.evaluate(
            axial = AxialLoadReport(false, null, 4, 2, 4, 3, 2, listOf(55, 54)),
            systemic = SystemicLoadReport(false, null, 2, listOf(55, 54)),
            cache = AugeAdaptiveCache(),
            nowMs = Instant.parse("2026-01-05T12:00:00Z").toEpochMilli(),
        )
        val axialSession = listOf(AxialSessionExercise("Sentadilla alta", axial = 1.0))
        val axialAdjust = adjustment.advisories.single { it.channel == RecoveryChannelId.STRUCTURE }
        val contextualAdjust = LoadAdvisoryEngine.contextualize(axialAdjust, axialSession)
        assertTrue(contextualAdjust.body.contains("Ajusta la carga axial de Sentadilla alta"))
        assertEquals("Columna: Ajusta la carga axial en Sentadilla alta", LoadAdvisoryEngine.badgeLabel(contextualAdjust, axialSession))
        val energyAdjust = adjustment.advisories.single { it.channel == RecoveryChannelId.SYSTEM }
        assertTrue(energyAdjust.body.contains("Deja 1–2 repeticiones en reserva"))
        assertEquals("Energía: Deja 1–2 repeticiones en reserva", LoadAdvisoryEngine.badgeLabel(energyAdjust))

        val unload = LoadAdvisoryEngine.evaluate(
            axial = AxialLoadReport(false, null, 4, 3, 4, 3, 2, listOf(49, 48, 47)),
            systemic = SystemicLoadReport(false, null, 2, listOf(49, 48, 47)),
            cache = AugeAdaptiveCache(),
            nowMs = Instant.parse("2026-01-05T12:00:00Z").toEpochMilli(),
        )
        val axialUnload = unload.advisories.single { it.channel == RecoveryChannelId.STRUCTURE }
        val contextualUnload = LoadAdvisoryEngine.contextualize(axialUnload, axialSession)
        assertTrue(contextualUnload.body.contains("Tendencia reciente de Columna: las lecturas al inicio"))
        assertTrue(contextualUnload.body.contains("Semana ligera: reduce o sustituye Sentadilla alta"))
        assertEquals("Columna: Semana ligera en Sentadilla alta", LoadAdvisoryEngine.badgeLabel(contextualUnload, axialSession))
        val energyUnload = unload.advisories.single { it.channel == RecoveryChannelId.SYSTEM }
        assertTrue(energyUnload.body.contains("Tendencia reciente de Energía: las lecturas al inicio"))
        assertEquals("Energía: Semana ligera", LoadAdvisoryEngine.badgeLabel(energyUnload))
        assertFalse((axialUnload.body + energyUnload.body).contains("lleva semanas", ignoreCase = true))
        assertFalse((axialUnload.body + energyUnload.body).contains("tres semanas", ignoreCase = true))
    }

    @Test
    fun `RecoveryBands define la accion de Columna y las bandas compartidas`() {
        assertEquals(85, RecoveryBands.HIGH_MIN)
        assertEquals(70, RecoveryBands.NORMAL_MIN)
        assertEquals(50, RecoveryBands.MODERATE_MIN)
        assertEquals(35, RecoveryBands.LOW_MIN)

        assertEquals(
            "La estimación de Columna es alta. Mantén una técnica controlada.",
            RecoveryBands.action(RecoveryChannelId.STRUCTURE, RecoveryBands.HIGH_MIN),
        )

        assertEquals(RecoveryBand.HIGH, RecoveryBands.band(85))
        assertEquals(RecoveryBand.NORMAL, RecoveryBands.band(84))
        assertEquals(RecoveryBand.NORMAL, RecoveryBands.band(70))
        assertEquals(RecoveryBand.MODERATE, RecoveryBands.band(69))
        assertEquals(RecoveryBand.MODERATE, RecoveryBands.band(50))
        assertEquals(RecoveryBand.LOW, RecoveryBands.band(49))
        assertEquals(RecoveryBand.LOW, RecoveryBands.band(35))
        assertEquals(RecoveryBand.CRITICAL, RecoveryBands.band(34))

        assertEquals(RecoveryBands.NORMAL_MIN, ExerciseReadinessEngine.ADJUSTMENT_THRESHOLD)
    }
}
