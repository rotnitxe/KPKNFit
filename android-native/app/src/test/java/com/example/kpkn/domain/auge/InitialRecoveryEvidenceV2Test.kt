package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.InitialRecoveryEvidence
import com.example.kpkn.data.models.InitialRecoveryEvidenceFactory
import com.example.kpkn.data.models.InitialRecoveryIntensity
import com.example.kpkn.data.models.InitialRecoverySensations
import com.example.kpkn.data.models.RingsLiftPattern
import com.example.kpkn.data.models.RingsMuscleGroup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

class InitialRecoveryEvidenceV2Test {
    private val zone = ZoneId.of("Europe/Madrid")
    private val capture = LocalDate.of(2026, 10, 8).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private val hour = 3_600_000L

    /** Ayer, sentadilla y rumano, HARD, 9 series de piernas: la referencia de calibración del brief. */
    private fun legsEvidence(): InitialRecoveryEvidence = InitialRecoveryEvidenceFactory.fromInputsV2(
        capturedAtMs = capture,
        zone = zone,
        trainedDays = setOf(1),
        intensity = InitialRecoveryIntensity.HARD,
        lifts = setOf(RingsLiftPattern.SQUAT, RingsLiftPattern.HINGE_ROW),
        groupSets = mapOf(RingsMuscleGroup.LEGS to 9),
    )

    private fun resolve(
        evidence: InitialRecoveryEvidence?,
        nowMs: Long,
        overrides: InitialRecoveryOverrides = InitialRecoveryOverrides(),
    ): InitialRecoveryContribution = InitialRecoveryEvidencePolicy.resolve(
        InitialRecoveryPolicyInput(evidence = evidence, nowMs = nowMs, overrides = overrides, zone = zone),
    )

    @Test
    fun v1EvidenceDecodesAndKeepsTodayLaw() {
        val json = """{"capturedAtMs":$capture,"coveredFromMs":${capture - 2 * 24 * hour},""" +
            """"coveredToMs":$capture,"expiresAtMs":${capture + 14 * 24 * hour},"sessions":2,""" +
            """"activityType":"STRENGTH","intensity":"HARD","muscularScore":70,"systemScore":80,""" +
            """"structureScore":90,"confidence":40}"""
        val evidence = Json.decodeFromString<InitialRecoveryEvidence>(json)
        assertEquals(1, evidence.estimatorVersion)
        assertTrue(evidence.dayLoads.isEmpty())
        assertTrue(evidence.structureKnown)
        val result = resolve(evidence, capture + 36 * hour)
        // Ley v1 (τ = 72 h): 100 − (100 − B₀)·exp(−36/72).
        assertEquals(82, result.muscular)
        assertEquals(88, result.system)
        assertEquals(94, result.structure)
    }

    @Test
    fun v2LawRisesMonotonicallyWithTime() {
        val evidence = legsEvidence()
        val results = listOf(0L, 6L, 36L, 72L).map { resolve(evidence, capture + it * hour) }
        for (i in 1 until results.size) {
            assertTrue(results[i].muscular!! >= results[i - 1].muscular!!)
            assertTrue(results[i].system!! >= results[i - 1].system!!)
            assertTrue(results[i].structure!! >= results[i - 1].structure!!)
        }
        assertTrue(results.last().system!! > results.first().system!!)
    }

    @Test
    fun energyDebtOf40RecoversToAboutNinetySevenAndAHalfAt36Hours() {
        val result = resolve(
            legsEvidence(),
            capture + 36 * hour,
            InitialRecoveryOverrides(system = 60),
        )
        // Ancla del motor: 100·(0,60)^d, con d = exp(−ln20·36/36) = 0,05, da 97,5.
        assertTrue(abs((result.system ?: -1) - 97.5) <= 1.0)
    }

    @Test
    fun withoutAxialPatternsTheColumnIsNotCalibrated() {
        val evidence = InitialRecoveryEvidenceFactory.fromInputsV2(
            capturedAtMs = capture,
            zone = zone,
            trainedDays = setOf(0, 1),
            intensity = InitialRecoveryIntensity.MODERATE,
            lifts = setOf(RingsLiftPattern.BENCH),
            groupSets = mapOf(RingsMuscleGroup.CHEST to 8),
        )
        assertFalse(evidence.structureKnown)
        val result = resolve(evidence, capture + 6 * hour)
        assertNull(result.structure)
        assertTrue(result.muscular != null && result.system != null)
    }

    @Test
    fun noTrainingGivesHundredOnEveryChannel() {
        val evidence = InitialRecoveryEvidenceFactory.fromInputsV2(
            capturedAtMs = capture,
            zone = zone,
            trainedDays = emptySet(),
            intensity = InitialRecoveryIntensity.HARD,
            lifts = emptySet(),
            groupSets = emptyMap(),
            sensations = InitialRecoverySensations(muscular = 5, energy = 5, structure = 5),
        )
        assertEquals(100, evidence.muscularScore)
        assertEquals(100, evidence.systemScore)
        assertEquals(100, evidence.structureScore)
        val result = resolve(evidence, capture + 10 * hour)
        assertEquals(100, result.muscular)
        assertEquals(100, result.system)
        assertEquals(100, result.structure)
    }

    @Test
    fun legSetsLowerQuadsAsTheEngineReferenceAndNotChest() {
        val batteries = InitialRecoveryEvidencePolicy.v2PillarBatteries(
            InitialRecoveryPolicyInput(evidence = legsEvidence(), nowMs = capture, zone = zone),
            listOf("Cuádriceps", "Pectorales"),
        )
        // Motor a 24 h en la sesión de referencia: 87,95; ley v2 con 0,4 × 9 × 5,3: 87,93.
        assertEquals(88, batteries["Cuádriceps"])
        assertEquals(100, batteries["Pectorales"])
    }

    @Test
    fun factoryIsDeterministicAndSerializable() {
        val first = legsEvidence()
        val second = legsEvidence()
        assertEquals(first, second)
        assertEquals(2, first.estimatorVersion)
        assertEquals("initial-recovery-v3", first.contractRevision)
        assertEquals(first.normalized(), Json.decodeFromString<InitialRecoveryEvidence>(Json.encodeToString(first)))
        assertEquals(resolve(first, capture + 12 * hour), resolve(second, capture + 12 * hour))
    }
}
