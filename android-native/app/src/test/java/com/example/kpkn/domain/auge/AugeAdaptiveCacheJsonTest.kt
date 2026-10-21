package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.*
import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray

class AugeAdaptiveCacheJsonTest {
    @Test fun cuatroVersionesPasanPorElCodecReal() {
        listOf(null, 1, 2, 3).forEach { version ->
            val prefix = version?.let { "\"schemaVersion\":$it," } ?: ""
            val raw = """{$prefix"personalizedRecoveryHours":{"pectorales":55.0},"cnsRecoveryHours":90.0,"spinalRecoveryHours":110.0,
                "muscleDeltas":{"pectorales":12.0},"cnsLearningDelta":8.0,"spinalLearningDelta":-6.0,
                "cnsDrainMultiplier":1.2,"spinalDrainMultiplier":0.8,"muscleDrainMultipliers":{"pectorales":1.3},
                "totalObservations":77,"lastUpdatedMs":123,"lastPerformanceLearnLogId":"log",
                "dismissedAdvisoryKeys":["a|week"],"lastAdvisoryLevelByChannel":{"cns":"WATCH"},
                "lastAdvisoryDay":"2026-10-20","modelUpdateNoticeShown":true}"""
            val cache = AugeAdaptiveCacheMigration.toAdaptiveCache(raw)
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
            val encoded = AugeAdaptiveCacheMigration.encodeAdaptiveCache(cache)
            listOf("muscleDeltas", "cnsLearningDelta", "spinalLearningDelta", "cnsDrainMultiplier", "spinalDrainMultiplier", "muscleDrainMultipliers").forEach {
                assertFalse(encoded.contains(it))
            }
            assertEquals(cache, AugeAdaptiveCacheMigration.toAdaptiveCache(AugeAdaptiveCacheMigration.encodeAdaptiveCache(AugeAdaptiveCacheMigration.toAdaptiveCache(encoded))))
        }
    }

    @Test fun idaYVueltaVaciaYPobladaConDoscientosRegistros() {
        val states = listOf("cns", "spinal", "pectorales").associateWith {
            AugeAdaptiveCacheMigration.prior(it).copy(n = 13, lastUpdatedAtMs = 1234, replayWatermarkMs = 1200)
        }
        val records = (1..200).map { i -> AugeObservationRecord(
            id = "$i", parameterKey = states.keys.elementAt(i % 3), y = 51.12345678901234, predicted = 60.98765432109876,
            deltaHours = 8.25, source = if (i % 2 == 0) "manual" else "performance", tauUsedHours = 36.5,
            observedAtMs = 1_700_000_000_000L + i, paramsVersion = 1, relevantLogId = "log-$i",
            outcome = AugeObservationOutcome(AugeObservationStatus.entries[i % 3], if (i % 3 == 0) null else "saturada"),
            priorPredicted = if (i % 2 == 0) 61.23456789012345 else null,
        ) }
        listOf(AugeAdaptiveCache(), AugeAdaptiveCache(parameterStates = states, observations = records, modelUpdateNoticeShown = true)).forEach {
            val encoded = AugeAdaptiveCacheMigration.encodeAdaptiveCache(it)
            val decoded = AugeAdaptiveCacheMigration.toAdaptiveCache(encoded)
            val reEncoded = AugeAdaptiveCacheMigration.encodeAdaptiveCache(decoded)
            assertEquals(it, AugeAdaptiveCacheMigration.toAdaptiveCache(reEncoded))
        }
    }

    @Test fun futuroNoEsEscribibleInclusoConPayloadDesconocido() {
        val result = AugeAdaptiveCacheMigration.decodeAdaptiveCache("""{"schemaVersion":5,"parameterStates":"otra-forma"}""")
        assertFalse(result.writable)
        assertEquals(AugeAdaptiveCache(), result.cache)
        assertEquals(listOf("future_schema:5"), result.diagnostics)
    }

    @Test fun jsonIlegibleVuelveAlPriorConDiagnostico() {
        val result = AugeAdaptiveCacheMigration.decodeAdaptiveCache("{roto")
        assertTrue(result.writable)
        assertEquals(AugeAdaptiveCache(), result.cache)
        assertEquals(listOf("unreadable_json"), result.diagnostics)
    }

    @Test fun varianzaInvalidaDelJsonSeDiagnosticaSinPerderAvisos() {
        val result = AugeAdaptiveCacheMigration.decodeAdaptiveCache("""{"schemaVersion":4,"parameterStates":{"cns":{"mean":3.0,"variance":-1.0}},"modelUpdateNoticeShown":true}""")
        assertEquals(AugeAdaptiveCacheMigration.prior("cns"), result.cache.parameterStates["cns"])
        assertEquals(listOf("invalid_parameter:cns"), result.diagnostics)
        assertTrue(result.cache.modelUpdateNoticeShown)
    }

    @Test fun noFinitoDelJsonReseteaSoloEseParametro() {
        val result = AugeAdaptiveCacheMigration.decodeAdaptiveCache("""{"schemaVersion":4,"parameterStates":{"cns":{"mean":NaN},"spinal":{"mean":3.5,"n":7}},"cnsRecoveryHours":80.0,"modelUpdateNoticeShown":true}""")
        assertEquals(AugeAdaptiveCacheMigration.prior("cns"), result.cache.parameterStates["cns"])
        assertEquals(7, result.cache.parameterStates.getValue("spinal").n)
        assertNull(result.cache.cnsRecoveryHours)
        assertEquals(listOf("invalid_parameter:cns"), result.diagnostics)
        assertTrue(result.cache.modelUpdateNoticeShown)
    }

    @Test fun codecNuncaPersisteMasDeDoscientosRegistros() {
        val records = (1..201).map { i -> AugeObservationRecord(
            id = "$i", parameterKey = "cns", y = 50.0, predicted = 60.0, deltaHours = 8.0,
            source = "manual", tauUsedHours = 36.0, observedAtMs = 1_700_000_000_000L + i, paramsVersion = 1,
        ) }
        val encoded = AugeAdaptiveCacheMigration.encodeAdaptiveCache(AugeAdaptiveCache(observations = records))
        assertEquals(200, Json.parseToJsonElement(encoded).jsonObject.getValue("observations").jsonArray.size)
        assertEquals((2..201).map { "$it" }, AugeAdaptiveCacheMigration.toAdaptiveCache(encoded).observations.map { it.id })
    }
}
