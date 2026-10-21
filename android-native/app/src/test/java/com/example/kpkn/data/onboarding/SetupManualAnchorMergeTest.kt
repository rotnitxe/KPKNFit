package com.example.kpkn.data.onboarding

import com.example.kpkn.data.models.DailyWellbeingLog
import com.example.kpkn.data.models.WellbeingSource
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class SetupManualAnchorMergeTest {
    private val zone = ZoneId.of("UTC")
    private val t0 = Instant.parse("2026-10-01T10:00:00Z").toEpochMilli()
    private val old = DailyWellbeingLog("hoy", "2026-10-01", manualNeuralBattery = 60, manualSpinalBattery = 55,
        manualMuscleBatteries = mapOf("Pectorales" to 50), manualBatteryAnchorMs = t0,
        source = WellbeingSource.ONBOARDING_INITIAL, capturedFields = setOf("energy", "structure"))

    @Test fun onboardingEnergyEditUpdatesItsClockAndPreservesTheOthers() {
        val incoming = DailyWellbeingLog("alta", old.date, manualNeuralBattery = 30,
            manualBatteryAnchorMs = t0 + 3_600_000L, capturedFields = setOf("energy"))
        val merged = mergeSetupWellbeing(old, incoming, zone)
        assertEquals(30, merged.manualNeuralBattery)
        assertEquals(t0 + 3_600_000L, merged.manualNeuralAnchorMs)
        assertEquals(t0, merged.manualSpinalAnchorMs)
        assertEquals(t0, merged.manualMuscleAnchorMs["Pectorales"])
        assertEquals(old.source, merged.source)
        assertEquals(old.capturedFields, merged.capturedFields)
    }

    @Test fun onboardingMuscleEditCannotMoveAnExistingEnergyClock() {
        val incoming = DailyWellbeingLog("alta", old.date, manualMuscleBatteries = mapOf("Dorsales" to 40),
            manualBatteryAnchorMs = t0 + 3_600_000L, capturedFields = setOf("muscle_batteries"))
        val merged = mergeSetupWellbeing(old, incoming, zone)
        assertEquals(t0, merged.manualNeuralAnchorMs)
        assertEquals(t0, merged.manualSpinalAnchorMs)
        assertEquals(mapOf("Pectorales" to t0, "Dorsales" to t0 + 3_600_000L), merged.manualMuscleAnchorMs)
        assertEquals(60, merged.manualNeuralBattery)
        assertEquals(old.source, merged.source)
    }

    @Test fun aNewDaysPreviewKeepsItsOwnMetadataAndThePreviousDaysUntouchedAnchors() {
        val previous = old.copy(source = WellbeingSource.IMPORTED)
        val incoming = DailyWellbeingLog("alta-nueva", "2026-10-02", manualNeuralBattery = 30,
            manualBatteryAnchorMs = t0 + 24 * 3_600_000L, source = WellbeingSource.ONBOARDING_INITIAL,
            capturedFields = setOf("energy"))
        val projected = requireNotNull(mergePreviewWellbeing(previous, incoming, t0 + 24 * 3_600_000L, zone))
        assertEquals(incoming.id, projected.id)
        assertEquals(incoming.date, projected.date)
        assertEquals(incoming.source, projected.source)
        assertEquals(incoming.capturedFields, projected.capturedFields)
        assertEquals(30, projected.manualNeuralBattery)
        assertEquals(55, projected.manualSpinalBattery)
        assertEquals(t0, projected.manualSpinalAnchorMs)
        assertEquals(50, projected.manualMuscleBatteries["Pectorales"])
    }
}
