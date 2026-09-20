package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.InitialRecoveryActivityType
import com.example.kpkn.data.models.InitialRecoveryEvidence
import com.example.kpkn.data.models.InitialRecoveryEvidenceFactory
import com.example.kpkn.data.models.InitialRecoveryIntensity
import com.example.kpkn.data.models.InitialRecoverySensations
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.models.DailyWellbeingLog
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InitialRecoveryEvidencePolicyTest {
    private val now = 1_700_000_000_000L

    @Test
    fun factoryIsSerializableAndBoundsScores() {
        val evidence = InitialRecoveryEvidenceFactory.fromInputs(
            capturedAtMs = now,
            recencyDays = 2,
            sessions = 3,
            type = InitialRecoveryActivityType.MIXED,
            intensity = InitialRecoveryIntensity.HARD,
            zones = listOf("piernas", "piernas", "axial"),
            sensations = InitialRecoverySensations(muscular = 5, energy = 4),
        )
        val roundTrip = Json.decodeFromString<InitialRecoveryEvidence>(Json.encodeToString(evidence))
        assertEquals(evidence.normalized(), roundTrip)
        assertTrue(roundTrip.muscularScore in 0..100)
        assertTrue(roundTrip.confidence in 0..100)
    }

    @Test
    fun noSessionsCreateNoTrainingDebtButKeepDeclaredSensations() {
        val clear = InitialRecoveryEvidenceFactory.fromInputs(
            capturedAtMs = now,
            recencyDays = 0,
            sessions = 0,
            type = InitialRecoveryActivityType.STRENGTH,
            intensity = InitialRecoveryIntensity.VERY_HARD,
        )
        val declared = InitialRecoveryEvidenceFactory.fromInputs(
            capturedAtMs = now,
            recencyDays = 0,
            sessions = 0,
            type = InitialRecoveryActivityType.STRENGTH,
            intensity = InitialRecoveryIntensity.VERY_HARD,
            sensations = InitialRecoverySensations(muscular = 5),
        )
        assertEquals(100, clear.muscularScore)
        assertEquals(100, clear.systemScore)
        assertEquals(100, clear.structureScore)
        assertEquals(76, declared.muscularScore)
        assertEquals(100, declared.systemScore)
        assertEquals(100, declared.structureScore)
    }

    @Test
    fun recentEffortDecaysWithRecencyAndHigherIntensityAddsDebt() {
        val fresh = InitialRecoveryEvidenceFactory.fromInputs(
            capturedAtMs = now,
            recencyDays = 0,
            sessions = 2,
            type = InitialRecoveryActivityType.STRENGTH,
            intensity = InitialRecoveryIntensity.MODERATE,
        )
        val older = InitialRecoveryEvidenceFactory.fromInputs(
            capturedAtMs = now,
            recencyDays = 7,
            sessions = 2,
            type = InitialRecoveryActivityType.STRENGTH,
            intensity = InitialRecoveryIntensity.MODERATE,
        )
        val harder = InitialRecoveryEvidenceFactory.fromInputs(
            capturedAtMs = now,
            recencyDays = 0,
            sessions = 2,
            type = InitialRecoveryActivityType.STRENGTH,
            intensity = InitialRecoveryIntensity.HARD,
        )
        assertTrue(fresh.muscularScore < older.muscularScore)
        assertTrue(harder.muscularScore < fresh.muscularScore)
        assertEquals(100, older.structureScore)
    }

    @Test
    fun structureUsesDeclaredAxialLoadOrStructureSensationOnly() {
        val strength = InitialRecoveryEvidenceFactory.fromInputs(
            capturedAtMs = now,
            recencyDays = 0,
            sessions = 2,
            type = InitialRecoveryActivityType.STRENGTH,
            intensity = InitialRecoveryIntensity.HARD,
        )
        val axial = InitialRecoveryEvidenceFactory.fromInputs(
            capturedAtMs = now,
            recencyDays = 0,
            sessions = 2,
            type = InitialRecoveryActivityType.STRENGTH,
            intensity = InitialRecoveryIntensity.HARD,
            zones = listOf("axial"),
        )
        val sensed = InitialRecoveryEvidenceFactory.fromInputs(
            capturedAtMs = now,
            recencyDays = 0,
            sessions = 0,
            type = InitialRecoveryActivityType.STRENGTH,
            intensity = InitialRecoveryIntensity.HARD,
            sensations = InitialRecoverySensations(structure = 4),
        )
        assertEquals(100, strength.structureScore)
        assertTrue(axial.structureScore < strength.structureScore)
        assertEquals(85, sensed.structureScore)
    }

    @Test
    fun factoryKeepsScoresAndConfidenceBounded() {
        val evidence = InitialRecoveryEvidenceFactory.fromInputs(
            capturedAtMs = now,
            recencyDays = 0,
            sessions = 14,
            type = InitialRecoveryActivityType.MIXED,
            intensity = InitialRecoveryIntensity.VERY_HARD,
            zones = listOf("axial"),
            sensations = InitialRecoverySensations(5, 5, 5),
        )
        assertTrue(evidence.muscularScore in 0..100)
        assertTrue(evidence.systemScore in 0..100)
        assertTrue(evidence.structureScore in 0..100)
        assertTrue(evidence.confidence in 0..82)
    }

    @Test
    fun noEvidenceIsUncalibratedAndDoesNotInventBaseline() {
        val result = InitialRecoveryEvidencePolicy.resolve(
            InitialRecoveryPolicyInput(evidence = null, nowMs = now),
        )
        assertNull(result.muscular)
        assertNull(result.system)
        assertNull(result.structure)
        assertEquals("Sin calibrar", result.label)
        assertEquals(0, result.confidence)
    }

    @Test
    fun evidenceDecaysFromStableAnchorAndExpires() {
        val evidence = InitialRecoveryEvidenceFactory.fromInputs(
            capturedAtMs = now,
            recencyDays = 0,
            sessions = 2,
            type = InitialRecoveryActivityType.STRENGTH,
            intensity = InitialRecoveryIntensity.HARD,
        )
        val atAnchor = InitialRecoveryEvidencePolicy.resolve(InitialRecoveryPolicyInput(evidence, now))
        val later = InitialRecoveryEvidencePolicy.resolve(InitialRecoveryPolicyInput(evidence, now + 72L * 3_600_000L))
        val expired = InitialRecoveryEvidencePolicy.resolve(InitialRecoveryPolicyInput(evidence, evidence.expiresAtMs))
        assertEquals(evidence.muscularScore, atAnchor.muscular)
        assertTrue((later.muscular ?: 0) > (atAnchor.muscular ?: 0))
        assertNull(expired.muscular)
        assertTrue(expired.isExpired)
    }

    @Test
    fun coveredRealLogDeduplicatesOnlyCoveredInterval() {
        val evidence = InitialRecoveryEvidenceFactory.fromInputs(
            capturedAtMs = now,
            recencyDays = 3,
            sessions = 2,
            type = InitialRecoveryActivityType.MIXED,
            intensity = InitialRecoveryIntensity.MODERATE,
        )
        val coveredLog = WorkoutLog("covered", "p", "s", "s", iso(now - 86_400_000L), 30)
        val laterLog = WorkoutLog("later", "p", "s", "s", iso(now + 86_400_000L), 30)
        val covered = InitialRecoveryEvidencePolicy.resolve(InitialRecoveryPolicyInput(evidence, now, listOf(coveredLog)))
        val later = InitialRecoveryEvidencePolicy.resolve(InitialRecoveryPolicyInput(evidence, now + 86_400_000L, listOf(laterLog)))
        assertNull(covered.muscular)
        assertTrue(later.isEstimated)
        assertTrue((later.muscular ?: 0) >= evidence.muscularScore)
    }

    @Test
    fun explicitOverridesWinOverEvidenceAndSettingsKeepsOptionalField() {
        val evidence = InitialRecoveryEvidenceFactory.fromInputs(
            capturedAtMs = now,
            recencyDays = 1,
            sessions = 1,
            type = InitialRecoveryActivityType.CARDIO,
            intensity = InitialRecoveryIntensity.VERY_HARD,
        )
        val result = InitialRecoveryEvidencePolicy.resolve(
            InitialRecoveryPolicyInput(
                evidence = evidence,
                nowMs = now,
                overrides = InitialRecoveryOverrides(muscular = 91, system = 87, structure = 83),
            ),
        )
        assertEquals(91, result.muscular)
        assertEquals(87, result.system)
        assertEquals(83, result.structure)
        assertEquals("Ajuste manual anclado", result.label)
        assertEquals(evidence, Settings(initialRecoveryEvidence = evidence).initialRecoveryEvidence)
    }

    @Test
    fun engineKeepsLegacyNoEvidenceMetadataAndAppliesBootstrapAsAnchor() {
        val legacy = AugeRecoveryEngine.calculateGlobalBatteries(
            history = emptyList(),
            wellbeing = null,
            settings = Settings(),
            nowOverrideMs = now,
        )
        assertEquals("Sin calibrar", legacy.sourceLabel)
        assertEquals(null, legacy.sourceConfidence)

        val evidence = InitialRecoveryEvidenceFactory.fromInputs(
            capturedAtMs = now,
            recencyDays = 0,
            sessions = 3,
            type = InitialRecoveryActivityType.STRENGTH,
            intensity = InitialRecoveryIntensity.HARD,
        )
        val bootstrapped = AugeRecoveryEngine.calculateGlobalBatteries(
            history = emptyList(),
            wellbeing = null,
            settings = Settings(initialRecoveryEvidence = evidence),
            nowOverrideMs = now,
        )
        assertEquals("Estimación inicial", bootstrapped.sourceLabel)
        assertEquals(evidence.muscularScore, bootstrapped.muscular)
        assertTrue((bootstrapped.sourceConfidence ?: 0) in 1..99)
    }

    @Test
    fun engineManualOverrideIsAnchoredAndNotReportedAsVerified() {
        val result = AugeRecoveryEngine.calculateGlobalBatteries(
            history = emptyList(),
            wellbeing = DailyWellbeingLog(
                id = "w",
                date = "2023-11-14",
                manualMuscularBattery = 35,
                manualBatteryAnchorMs = now,
            ),
            settings = Settings(),
            nowOverrideMs = now,
        )
        assertEquals(35, result.muscular)
        assertEquals("Ajuste manual", result.sourceLabel)
        assertNull(result.sourceConfidence)
    }

    private fun iso(ms: Long): String = java.time.Instant.ofEpochMilli(ms).toString()
}
