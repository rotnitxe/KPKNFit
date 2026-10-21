package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class AugeObservationRingTest {
    private fun observation(i: Int, key: String = "cns") = AugeObservationRecord(
        id = "id-$i", parameterKey = key, y = 51.12345678901234, predicted = 60.98765432109876,
        deltaHours = 8.25, source = "manual", tauUsedHours = 36.5, observedAtMs = 1_700_000_000_000L + i,
        paramsVersion = AugeParameters.VERSION, relevantLogId = "log", priorPredicted = 61.23456789012345,
    )

    @Test fun bordesDelAnilloYReplayExpulsadoNoCambianNada() {
        var cache = AugeAdaptiveCache()
        assertTrue(cache.observations.isEmpty())
        (1..250).forEach { i ->
            cache = AugeAdaptiveCacheMigration.record(cache, observation(i))
            if (i in listOf(1, 199, 200, 201)) assertEquals(minOf(i, 200), cache.observations.size)
            if (i == 201) assertEquals((2..201).map { "id-$it" }, cache.observations.map { it.id })
        }
        val json = Json { encodeDefaults = true }
        cache = json.decodeFromString<AugeAdaptiveCache>(json.encodeToString(cache))
        val before = cache
        assertSame(before, AugeAdaptiveCacheMigration.record(cache, observation(250)))
        assertSame(before, AugeAdaptiveCacheMigration.record(cache, observation(1)))
        assertSame(before, AugeAdaptiveCacheMigration.record(cache, observation(1).copy(id = "otro")))
        assertEquals(0, cache.parameterStates.getValue("cns").n)
    }

    @Test fun censuradaAvanzaMarcaSinActualizarPosteriorYRechazaReplay() {
        val state = AugeAdaptiveCacheMigration.prior("cns").copy(n = 9, lastUpdatedAtMs = 123)
        val record = observation(1).copy(outcome = AugeObservationOutcome(AugeObservationStatus.CENSORED, "saturada"))
        val cache = AugeAdaptiveCacheMigration.record(AugeAdaptiveCache(parameterStates = mapOf("cns" to state)), record)
        assertEquals(state.copy(replayWatermarkMs = record.anchorMs), cache.parameterStates["cns"])
        val expelled = cache.copy(observations = emptyList())
        assertSame(expelled, AugeAdaptiveCacheMigration.record(expelled, record))
    }

    @Test fun empateSeOrdenaPorIdYDuplicadoNoMueveOrden() {
        val a = observation(1).copy(parameterKey = "cns", id = "a")
        val b = a.copy(parameterKey = "spinal", id = "b")
        val cache = AugeAdaptiveCacheMigration.record(AugeAdaptiveCacheMigration.record(AugeAdaptiveCache(), b), a)
        assertEquals(listOf("a", "b"), cache.observations.map { it.id })
        assertEquals(cache, AugeAdaptiveCacheMigration.record(cache, b.copy(y = 22.0)))
    }
    @Test fun registroInvalidoNoConsumeElAnclaYRechazadoNoAvanzaMarca() {
        val cache = AugeAdaptiveCache()
        assertEquals(cache, AugeAdaptiveCacheMigration.record(cache, observation(1).copy(y = Double.NaN)))
        val rejected = AugeAdaptiveCacheMigration.record(cache, observation(1).copy(
            outcome = AugeObservationOutcome(AugeObservationStatus.REJECTED, "sin señal"),
        ))
        assertTrue(rejected.parameterStates.isEmpty())
        assertEquals(1, rejected.observations.size)
    }

    @Test fun idEsDeterministaYCanonico() {
        val first = AugeAdaptiveCacheMigration.observationId("Energía", 123, 45.0, "log|id")
        assertEquals(first, AugeAdaptiveCacheMigration.observationId("cns", 123, 45.0, "log|id"))
        assertNotEquals(first, AugeAdaptiveCacheMigration.observationId("cns", 123, 46.0, "log|id"))
    }

}
