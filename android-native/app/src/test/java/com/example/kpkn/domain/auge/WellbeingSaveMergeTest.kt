package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.DailyWellbeingLog
import com.example.kpkn.data.models.ManualMuscleBatteryOverride
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WellbeingSaveMergeTest {

    private fun v2(battery: Int, anchorMs: Long) = ManualMuscleBatteryOverride(
        battery = battery,
        anchorEpochMs = anchorMs,
        automaticBatteryAtAnchor = 90,
    )

    private fun log(
        neural: Int? = null,
        spinal: Int? = null,
        muscular: Int? = null,
        v1: Map<String, Int> = emptyMap(),
        v2Muscles: Map<String, ManualMuscleBatteryOverride> = emptyMap(),
        anchorMs: Long? = null,
        sleepQuality: Int = 3,
        captured: Set<String> = emptySet(),
    ) = DailyWellbeingLog(
        id = "hoy",
        date = "2026-10-09",
        sleepQuality = sleepQuality,
        manualNeuralBattery = neural,
        manualSpinalBattery = spinal,
        manualMuscularBattery = muscular,
        manualMuscleBatteries = v1,
        manualMuscleOverridesV2 = v2Muscles,
        manualBatteryAnchorMs = anchorMs,
        capturedFields = captured,
    )

    @Test
    fun noExistingRecordReturnsIncoming() {
        val incoming = log(neural = 60, sleepQuality = 4)

        assertEquals(incoming, mergeWellbeingForSave(null, incoming))
    }

    @Test
    fun existingV2IsKeptWhenIncomingDeclaresNoManual() {
        val existing = log(v2Muscles = mapOf("Pectorales" to v2(55, 1_000L)), captured = setOf("muscle_batteries"))
        val incoming = log(sleepQuality = 2)

        val merged = mergeWellbeingForSave(existing, incoming)

        assertEquals(mapOf("Pectorales" to v2(55, 1_000L)), merged.manualMuscleOverridesV2)
        assertEquals(2, merged.sleepQuality)
    }

    @Test
    fun storedV1CopyDoesNotDropTheV2OfTheSameMuscle() {
        // El alta guarda V1 y V2 del mismo músculo; el chequeo previo copia la V1 de hoy.
        val existing = log(v1 = mapOf("Pectorales" to 70), v2Muscles = mapOf("Pectorales" to v2(70, 1_000L)))
        val incoming = log(v1 = mapOf("Pectorales" to 70))

        val merged = mergeWellbeingForSave(existing, incoming)

        assertEquals(mapOf("Pectorales" to v2(70, 1_000L)), merged.manualMuscleOverridesV2)
    }

    @Test
    fun newV1DropsTheV2OfThatMuscleAndKeepsTheRest() {
        val existing = log(v2Muscles = mapOf("Pectorales" to v2(55, 1_000L), "Dorsales" to v2(40, 1_000L)))
        val incoming = log(v1 = mapOf("Pectorales" to 70))

        val merged = mergeWellbeingForSave(existing, incoming)

        assertEquals(mapOf("Dorsales" to v2(40, 1_000L)), merged.manualMuscleOverridesV2)
        assertEquals(mapOf("Pectorales" to 70), merged.manualMuscleBatteries)
    }

    @Test
    fun incomingV2WinsOverExistingForItsMuscle() {
        val existing = log(v2Muscles = mapOf("Pectorales" to v2(55, 1_000L), "Dorsales" to v2(40, 1_000L)))
        val incoming = log(v2Muscles = mapOf("Pectorales" to v2(65, 9_000L)))

        val merged = mergeWellbeingForSave(existing, incoming)

        assertEquals(
            mapOf("Dorsales" to v2(40, 1_000L), "Pectorales" to v2(65, 9_000L)),
            merged.manualMuscleOverridesV2,
        )
    }

    @Test
    fun noManualInIncomingKeepsExistingGlobalsAndAnchor() {
        val existing = log(neural = 60, spinal = 55, anchorMs = 5_000L, v1 = mapOf("Dorsales" to 40))
        val incoming = log()

        val merged = mergeWellbeingForSave(existing, incoming)

        assertEquals(60, merged.manualNeuralBattery)
        assertEquals(55, merged.manualSpinalBattery)
        assertEquals(mapOf("Dorsales" to 40), merged.manualMuscleBatteries)
        assertEquals(5_000L, merged.manualBatteryAnchorMs)
    }

    @Test
    fun copiedGlobalDoesNotReanchor() {
        val existing = log(neural = 60, anchorMs = 5_000L)
        val incoming = log(neural = 60, anchorMs = 9_000L)

        val merged = mergeWellbeingForSave(existing, incoming)

        assertEquals(60, merged.manualNeuralBattery)
        assertEquals(5_000L, merged.manualBatteryAnchorMs)
    }

    @Test
    fun changedGlobalKeepsTheOtherChannelsAndTheirAnchor() {
        val existing = log(neural = 60, spinal = 55, anchorMs = 5_000L)
        val incoming = log(neural = 72, anchorMs = 9_000L)

        val merged = mergeWellbeingForSave(existing, incoming)

        assertEquals(72, merged.manualNeuralBattery)
        assertEquals(55, merged.manualSpinalBattery)
        assertEquals(5_000L, merged.manualSpinalAnchorMs)
        assertEquals(9_000L, merged.manualNeuralAnchorMs)
        assertEquals(9_000L, merged.manualBatteryAnchorMs)
    }

    @Test
    fun newV1AlsoTakesIncomingGlobalsAndAnchor() {
        val existing = log(neural = 60, anchorMs = 5_000L)
        val incoming = log(neural = 60, v1 = mapOf("Dorsales" to 40), anchorMs = 9_000L)

        val merged = mergeWellbeingForSave(existing, incoming)

        assertEquals(mapOf("Dorsales" to 40), merged.manualMuscleBatteries)
        assertEquals(5_000L, merged.manualNeuralAnchorMs)
        assertEquals(mapOf("Dorsales" to 9_000L), merged.manualMuscleAnchorMs)
        assertEquals(9_000L, merged.manualBatteryAnchorMs)
    }

    @Test
    fun capturedFieldsAreTheUnion() {
        val existing = log(captured = setOf("sleep", "muscle_batteries"))
        val incoming = log(captured = setOf("energy", "sleep"))

        val merged = mergeWellbeingForSave(existing, incoming)

        assertEquals(setOf("sleep", "muscle_batteries", "energy"), merged.capturedFields)
    }
}
