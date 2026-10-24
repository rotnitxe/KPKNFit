package com.example.kpkn.domain.performance.lab

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PerformanceLabModelsTest {
    @Test
    fun payloadJsonRoundTripPreservesUnitsProvenanceAndMissingValue() {
        val mark = PerformanceLabMark(
            id = "failed-sprint",
            protocol = PerformanceLabProtocol("sprint-20m", 2, "20 m sprint"),
            metric = PerformanceLabMetric.SPRINT_TIME,
            dimension = "20m_from_standing_start",
            unit = PerformanceLabUnit.SECOND,
            value = null,
            provenance = PerformanceLabProvenance.MANUAL_ESTIMATE,
            context = PerformanceLabContext(
                instrument = "manual stopwatch",
                surface = "indoor track",
                method = "hand timed",
                conditions = mapOf("wind" to "not recorded"),
            ),
            status = PerformanceLabTrialStatus.FAILED,
        )
        val payload = PerformanceLabPayload(marks = listOf(mark))

        val roundTrip = Json.decodeFromJsonElement<PerformanceLabPayload>(payload.toJsonObject())

        assertEquals(payload, roundTrip)
        assertNull(roundTrip.marks.single().value)
    }
}
