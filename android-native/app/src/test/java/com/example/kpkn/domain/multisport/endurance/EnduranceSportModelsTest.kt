package com.example.kpkn.domain.multisport.endurance

import com.example.kpkn.domain.multisport.SportId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EnduranceSportModelsTest {
    @Test
    fun serializationKeepsUnknownSeparateFromZeroAndRetainsPartialStatus() {
        val payload = EnduranceSportPayload(
            sportId = SportId.RUNNING,
            result = EnduranceSportResult(
                quality = EnduranceRecordQuality.PARTIAL,
                segments = listOf(
                    EnduranceResultSegment(
                        id = "unrecorded-split",
                        kind = EnduranceSegmentKind.RUNNING,
                        quality = EnduranceRecordQuality.UNRECORDED,
                    ),
                    EnduranceResultSegment(
                        id = "measured-zero-is-invalid-but-preserved",
                        kind = EnduranceSegmentKind.RUNNING,
                        quality = EnduranceRecordQuality.INVALIDATED,
                        distanceMeters = 0.0,
                    ),
                ),
            ),
        )

        val decoded = Json.decodeFromJsonElement<EnduranceSportPayload>(payload.toJsonObject())
        assertEquals(payload, decoded)
        assertNull(decoded.result?.segments?.first()?.distanceMeters)
        assertEquals(0.0, decoded.result?.segments?.last()?.distanceMeters)
        assertEquals(EnduranceRecordQuality.PARTIAL, decoded.result?.quality)
    }

    @Test
    fun measurementJsonRetainsExplicitUnitSourceInstrumentAndCoverageWindow() {
        val series = EnduranceMeasurementSeries(
            id = "power-import-1",
            kind = EnduranceSeriesKind.POWER,
            unit = EnduranceUnit.WATTS,
            source = EnduranceDataSource.IMPORTED_DEVICE,
            instrumentId = "trainer-model-x",
            coverage = EnduranceSampleCoverage(500L, 2_500L, 750L),
            samples = listOf(EnduranceSample(500L, 0.0), EnduranceSample(1_000L, 210.0)),
        )
        val payload = EnduranceSportPayload(
            sportId = SportId.CYCLING,
            observation = EnduranceSportObservation(
                segmentKind = EnduranceSegmentKind.CYCLING,
                series = series,
                quality = EnduranceRecordQuality.PARTIAL,
            ),
        )

        val decoded = Json.decodeFromJsonElement<EnduranceSportPayload>(payload.toJsonObject())
        assertEquals(series, decoded.observation?.series)
        assertEquals(EnduranceUnit.WATTS, decoded.observation?.series?.unit)
        assertEquals(EnduranceDataSource.IMPORTED_DEVICE, decoded.observation?.series?.source)
        assertEquals("trainer-model-x", decoded.observation?.series?.instrumentId)
        assertEquals(EnduranceSampleCoverage(500L, 2_500L, 750L), decoded.observation?.series?.coverage)
    }
}
