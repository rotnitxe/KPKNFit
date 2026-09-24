package com.example.kpkn.domain.onboarding

import com.example.kpkn.data.models.WellbeingSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupRingsResponseMappingTest {
    @Test
    fun sensationLevelsConvertToBatteryPercentagesNeverRawScale() {
        assertEquals(100, SetupRingsResponseMapping.batteryFromLevel(1))
        assertEquals(75, SetupRingsResponseMapping.batteryFromLevel(2))
        assertEquals(50, SetupRingsResponseMapping.batteryFromLevel(3))
        assertEquals(25, SetupRingsResponseMapping.batteryFromLevel(4))
        assertEquals(0, SetupRingsResponseMapping.batteryFromLevel(5))
        assertNull(SetupRingsResponseMapping.batteryFromLevel(null))
        // La escala cruda nunca se usa como porcentaje.
        val staged = SetupRingsResponseMapping.stageCheckIn(SetupRingsCheckIn(muscular = 1, energy = 3, structure = 5))
        assertEquals(100, staged.manualMuscularBattery)
        assertEquals(50, staged.manualNeuralBattery)
        assertEquals(0, staged.manualSpinalBattery)
    }

    @Test
    fun stagedCheckInCapturesOnlyDeclaredFields() {
        val staged = SetupRingsResponseMapping.stageCheckIn(
            checkIn = SetupRingsCheckIn(energy = 2),
            manualMuscleLevels = mapOf("Pectorales" to 2),
        )
        assertEquals(75, staged.manualNeuralBattery)
        assertNull(staged.manualMuscularBattery)
        assertNull(staged.manualSpinalBattery)
        assertEquals(mapOf("Pectorales" to 75), staged.manualMuscleBatteries)
        // Un campo no informado no se captura: el desconocimiento no es ausencia.
        assertEquals(setOf("energy", "muscle_batteries"), staged.capturedFields)
        assertNull(staged.preWorkoutDiscomforts)
    }

    @Test
    fun discomfortFieldTreatsExplicitNoneAndUnreportedDifferently() {
        assertEquals(emptyList<String>(), SetupRingsResponseMapping.discomfortField(RingsDiscomfortResponse.NONE, listOf("ignored")))
        assertNull(SetupRingsResponseMapping.discomfortField(RingsDiscomfortResponse.NOT_ANSWERED, emptyList()))
        assertNull(SetupRingsResponseMapping.discomfortField(RingsDiscomfortResponse.OMITTED, emptyList()))
        assertEquals(listOf("knee"), SetupRingsResponseMapping.discomfortField(RingsDiscomfortResponse.DECLARED, listOf("knee", "knee")))
    }

    @Test
    fun stagedCheckInToWellbeingLogKeepsSharedFieldMapping() {
        val staged = SetupRingsResponseMapping.stageCheckIn(
            checkIn = SetupRingsCheckIn(muscular = 2, energy = 2, structure = 2),
            discomforts = emptyList(),
        )
        val log = staged.toWellbeingLog(id = "onboarding-check-in", date = "2026-09-24", anchorMs = 4_242L)
        assertEquals("onboarding-check-in", log.id)
        assertEquals("2026-09-24", log.date)
        assertEquals(WellbeingSource.ONBOARDING_INITIAL, log.source)
        assertEquals(4_242L, log.manualBatteryAnchorMs)
        assertEquals(75, log.manualMuscularBattery)
        assertEquals(75, log.manualNeuralBattery)
        assertEquals(75, log.manualSpinalBattery)
        assertEquals(emptyList<String>(), log.preWorkoutDiscomforts)
        assertEquals(setOf("muscular", "energy", "structure", "discomforts"), log.capturedFields)
        assertTrue(log.manualMuscleBatteries.isEmpty())
        assertTrue(log.manualMuscleOverridesV2.isEmpty())
    }
}
