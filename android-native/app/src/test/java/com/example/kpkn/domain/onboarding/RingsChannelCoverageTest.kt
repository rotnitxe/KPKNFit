package com.example.kpkn.domain.onboarding

import com.example.kpkn.data.models.GlobalBatteries
import com.example.kpkn.data.models.InitialRecoveryActivityType
import com.example.kpkn.data.models.InitialRecoveryEvidenceFactory
import com.example.kpkn.data.models.InitialRecoveryIntensity
import com.example.kpkn.data.models.InitialRecoverySensations
import com.example.kpkn.data.models.RecoveryChannelId
import com.example.kpkn.domain.auge.InitialRecoveryEvidencePolicy
import com.example.kpkn.domain.auge.InitialRecoveryPolicyInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RingsChannelCoverageTest {
    @Test
    fun noHistoryNoEvidenceNoCheckInMarksChannelsAsSinDatosWithoutAssertingOneHundredPercent() {
        val contribution = InitialRecoveryEvidencePolicy.resolve(
            InitialRecoveryPolicyInput(evidence = null, nowMs = 1_000L),
        )
        val coverage = RingsCoverage.evaluate(
            historyIsEmpty = true,
            wellbeing = null,
            contribution = contribution,
            subjective = SetupRingsCheckIn(),
            score = { 100 },
        )
        listOf(coverage.muscular, coverage.system, coverage.structure).forEach { channel ->
            assertEquals(RingsCoverageSource.NO_DATA, channel.source)
            assertEquals("Sin datos", channel.label)
            assertFalse(channel.hasData)
            assertFalse(channel.isEstimated)
            // El 100 del motor no se expone como valor afirmativo.
            assertNull(channel.score)
        }
    }

    @Test
    fun sensationOnlyIsPresentedAsSubjectiveEstimate() {
        val contribution = InitialRecoveryEvidencePolicy.resolve(
            InitialRecoveryPolicyInput(evidence = null, nowMs = 1_000L),
        )
        val coverage = RingsCoverage.evaluate(
            historyIsEmpty = true,
            wellbeing = null,
            contribution = contribution,
            subjective = SetupRingsCheckIn(energy = 2),
            score = { channel -> if (channel == RecoveryChannelId.SYSTEM) 75 else 100 },
        )
        assertEquals(RingsCoverageSource.SUBJECTIVE_SENSATION, coverage.system.source)
        assertEquals("Estimación subjetiva", coverage.system.label)
        assertTrue(coverage.system.isEstimated)
        assertTrue(coverage.system.hasData)
        assertEquals(75, coverage.system.score)
        assertTrue(coverage.system.uncertainty >= 50)
        // Los otros canales siguen sin datos: no se confirman fisiológicamente.
        assertEquals(RingsCoverageSource.NO_DATA, coverage.muscular.source)
        assertEquals(RingsCoverageSource.NO_DATA, coverage.structure.source)
        assertNull(coverage.muscular.score)
        assertNull(coverage.structure.score)
    }

    @Test
    fun initialEvidenceCoverageIsEstimatedWithUncertaintyFromConfidence() {
        val nowMs = 100_000L
        val evidence = InitialRecoveryEvidenceFactory.fromInputs(
            capturedAtMs = nowMs,
            recencyDays = 0,
            sessions = 2,
            type = InitialRecoveryActivityType.MIXED,
            intensity = InitialRecoveryIntensity.MODERATE,
            sensations = InitialRecoverySensations(3, 3, 3),
        )
        val contribution = InitialRecoveryEvidencePolicy.resolve(
            InitialRecoveryPolicyInput(evidence = evidence, nowMs = nowMs),
        )
        val coverage = RingsCoverage.evaluate(
            historyIsEmpty = true,
            wellbeing = null,
            contribution = contribution,
            subjective = SetupRingsCheckIn(),
            score = { 78 },
        )
        listOf(coverage.muscular, coverage.system, coverage.structure).forEach { channel ->
            assertEquals(RingsCoverageSource.INITIAL_ESTIMATE, channel.source)
            assertTrue(channel.isEstimated)
            assertEquals(100 - contribution.confidence, channel.uncertainty)
            assertEquals(78, channel.score)
        }
    }

    @Test
    fun uncalibratedEngineScoresAreNotExposedAsOneHundredPercent() {
        val uncalibrated = RingsCoverage.fromBatteries(
            GlobalBatteries(muscular = 100, cnc = 100, spinal = 100, sourceLabel = "Sin calibrar"),
        )
        listOf(uncalibrated.muscular, uncalibrated.system, uncalibrated.structure).forEach { channel ->
            assertEquals(RingsCoverageSource.NO_DATA, channel.source)
            assertNull(channel.score)
        }
        val history = RingsCoverage.fromBatteries(
            GlobalBatteries(muscular = 100, cnc = 82, spinal = 90, sourceLabel = "Historial real"),
        )
        assertEquals(RingsCoverageSource.REAL_HISTORY, history.muscular.source)
        assertEquals(100, history.muscular.score)
        assertEquals(82, history.system.score)
        assertEquals(90, history.structure.score)
    }
}
