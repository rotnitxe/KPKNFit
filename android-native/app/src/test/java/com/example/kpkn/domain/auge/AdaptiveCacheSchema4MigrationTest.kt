package com.example.kpkn.domain.auge

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.example.kpkn.data.models.*
import org.junit.Assert.*
import org.junit.Test

class AdaptiveCacheSchema4MigrationTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun roundTrip(cache: AugeAdaptiveCache): AugeAdaptiveCache =
        AugeAdaptiveCacheMigration.migrate(json.decodeFromString(json.encodeToString(cache)), 4)

    @Test fun cuatroVersionesMigranAlPrior() {
        listOf(null, 1, 2, 3).forEach { version ->
            val prefix = version?.let { "\"schemaVersion\":$it," } ?: ""
            val raw = """{$prefix"personalizedRecoveryHours":{"pectorales":55.0},"cnsRecoveryHours":90.0,"spinalRecoveryHours":110.0,
                "muscleDeltas":{"pectorales":12.0},"cnsLearningDelta":8.0,"spinalLearningDelta":-6.0,
                "cnsDrainMultiplier":1.2,"spinalDrainMultiplier":0.8,"muscleDrainMultipliers":{"pectorales":1.3},
                "totalObservations":77,"lastUpdatedMs":123,"lastPerformanceLearnLogId":"log",
                "dismissedAdvisoryKeys":["a|week"],"lastAdvisoryLevelByChannel":{"cns":"WATCH"},
                "lastAdvisoryDay":"2026-10-20","modelUpdateNoticeShown":true}"""
            val cache = AugeAdaptiveCacheMigration.migrate(json.decodeFromString<AugeAdaptiveCache>(raw), version ?: 1)
            assertEquals(4, cache.schemaVersion)
            assertTrue(cache.personalizedRecoveryHours.isEmpty())
            assertNull(cache.cnsRecoveryHours)
            assertNull(cache.spinalRecoveryHours)
            assertTrue(cache.parameterStates.isEmpty())
            assertEquals(77, cache.totalObservations)
            assertEquals(123L, cache.lastUpdatedMs)
            assertEquals("log", cache.lastPerformanceLearnLogId)
            assertEquals(setOf("a|week"), cache.dismissedAdvisoryKeys)
            assertEquals(mapOf("cns" to "WATCH"), cache.lastAdvisoryLevelByChannel)
            assertEquals("2026-10-20", cache.lastAdvisoryDay)
            assertFalse(cache.modelUpdateNoticeShown)
            val encoded = json.encodeToString(cache)
            listOf("muscleDeltas", "cnsLearningDelta", "spinalLearningDelta", "cnsDrainMultiplier", "spinalDrainMultiplier", "muscleDrainMultipliers").forEach {
                assertFalse(encoded.contains(it))
            }
            assertEquals(cache, roundTrip(roundTrip(cache)))
        }
    }

    @Test fun avisoSoloSeReabreAlMigrar() {
        val migrated = AugeAdaptiveCacheMigration.migrate(AugeAdaptiveCache(modelUpdateNoticeShown = true), 3)
        assertFalse(migrated.modelUpdateNoticeShown)
        val read = roundTrip(migrated.copy(modelUpdateNoticeShown = true))
        assertTrue(read.modelUpdateNoticeShown)
        assertEquals(read, roundTrip(read))
    }

    @Test fun parametroInvalidoVuelveSoloASuPriorConDiagnostico() {
        val valid = AugeAdaptiveCacheMigration.prior("cns").copy(n = 8, lastUpdatedAtMs = 10)
        listOf(valid.copy(mean = Double.NaN), valid.copy(variance = -1.0), valid.copy(n = -1),
            valid.copy(lastUpdatedAtMs = -1), valid.copy(replayWatermarkMs = -1), valid.copy(variance = Double.POSITIVE_INFINITY)).forEach { invalid ->
            val diagnostics = mutableListOf<String>()
            val cache = AugeAdaptiveCacheMigration.normalize(AugeAdaptiveCache(
                parameterStates = mapOf("cns" to valid, "spinal" to invalid), modelUpdateNoticeShown = true,
            )) { diagnostics += it }
            assertEquals(valid, cache.parameterStates["cns"])
            assertEquals(AugeAdaptiveCacheMigration.prior("spinal"), cache.parameterStates["spinal"])
            assertEquals(listOf("invalid_parameter:spinal"), diagnostics)
            assertTrue(cache.modelUpdateNoticeShown)
        }
    }

    @Test fun normalizaTambienGruposFueraDeLosTrecePilares() {
        listOf("Cuello", "Aductores", "Antebrazo", "Core").forEach { muscle ->
            val state = AugeAdaptiveCacheMigration.prior(muscle)
            val normalized = AugeAdaptiveCacheMigration.normalize(AugeAdaptiveCache(parameterStates = mapOf(muscle to state)))
            assertEquals(state, normalized.parameterStates[toAugeAdaptiveMuscleKey(muscle)])
        }
    }
}
