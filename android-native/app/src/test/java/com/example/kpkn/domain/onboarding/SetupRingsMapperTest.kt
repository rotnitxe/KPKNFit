package com.example.kpkn.domain.onboarding

import com.example.kpkn.data.models.InitialRecoveryActivityType
import com.example.kpkn.data.models.InitialRecoveryIntensity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SetupRingsMapperTest {
    @Test
    fun allThreeSensationsAreRequiredAndTimestampIsAssignedOnlyAtMapping() {
        val incomplete = SetupRingsMapper.map(
            SetupRingsInput(startAction = "Preparar mi punto de partida", recentTraining = false, muscleFeeling = 2, energy = 2),
            nowMs = 1000L,
        )
        assertEquals(RingsCompletion.INCOMPLETE, incomplete.completion)

        val complete = SetupRingsMapper.map(
            SetupRingsInput(startAction = "Preparar mi punto de partida", recentTraining = false, muscleFeeling = 2, energy = 2, structureFeeling = 1, activityType = InitialRecoveryActivityType.MIXED, intensity = InitialRecoveryIntensity.MODERATE),
            nowMs = 1000L,
        )
        assertEquals(RingsCompletion.VALID, complete.completion)
        assertEquals(1000L, complete.evidence?.capturedAtMs)
        assertNotNull(complete.evidence)
    }

    @Test
    fun removeAndPreserveAreDifferentCommitIntents() {
        assertEquals(RingsCompletion.OMITTED, SetupRingsMapper.map(SetupRingsInput(startAction = "Quitar estimación inicial"), 10L).completion)
        assertEquals(RingsCompletion.PRESERVE, SetupRingsMapper.map(SetupRingsInput(startAction = "Conservar estimación actual"), 10L).completion)
    }
}
