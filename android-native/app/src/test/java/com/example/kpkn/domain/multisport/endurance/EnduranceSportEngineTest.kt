package com.example.kpkn.domain.multisport.endurance

import com.example.kpkn.domain.multisport.SportId
import com.example.kpkn.domain.multisport.tracking.SportDocumentPurpose
import com.example.kpkn.domain.multisport.tracking.SportDocumentValidation
import com.example.kpkn.domain.multisport.tracking.SportTrackingDocument
import com.example.kpkn.domain.multisport.tracking.SportTrackingRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnduranceSportEngineTest {
    @Test
    fun engineBindsPayloadAndEnvelopeAndAcceptsJsonRoundTrip() {
        val payload = runningResult()
        val registry = SportTrackingRegistry(listOf(EnduranceSportEngine.running))
        val document = SportTrackingDocument(
            id = "run-1",
            engineId = EnduranceSportEngine.running.id,
            version = 1,
            sportId = SportId.RUNNING.name,
            purpose = SportDocumentPurpose.RESULT.name,
            payload = payload.toJsonObject(),
        )

        assertEquals(SportDocumentValidation.Valid, registry.validate(document))
        assertTrue(EnduranceSportEngine.running.validateForSport(
            payload.toJsonObject(), SportDocumentPurpose.RESULT, SportId.CYCLING,
        ).isNotEmpty())
        assertTrue(EnduranceSportEngine.running.validatePayload(
            payload.copy(sportId = SportId.CYCLING), SportDocumentPurpose.RESULT,
        ).isNotEmpty())
    }

    @Test
    fun planDurationIsOnlyReturnedWhenExplicitlyAuthored() {
        val engine = EnduranceSportEngine.running
        val explicit = EnduranceSportPayload(
            sportId = SportId.RUNNING,
            plan = EnduranceSportPlan(declaredDurationSeconds = 900L),
        )
        val unspecified = EnduranceSportPayload(sportId = SportId.RUNNING, plan = EnduranceSportPlan())

        assertEquals(900L, engine.plannedDurationSeconds(explicit.toJsonObject()))
        assertNull(engine.plannedDurationSeconds(unspecified.toJsonObject()))
        assertTrue(engine.validatePayload(explicit, SportDocumentPurpose.PLAN).isEmpty())
    }

    @Test
    fun weightedPowerUsesOnlyCoveredAdjacentIntervalsAndDoesNotExtrapolateTail() {
        val series = EnduranceMeasurementSeries(
            id = "power-meter",
            kind = EnduranceSeriesKind.POWER,
            unit = EnduranceUnit.WATTS,
            source = EnduranceDataSource.IMPORTED_DEVICE,
            instrumentId = "device-model-1",
            coverage = EnduranceSampleCoverage(0L, 5_000L, 1_500L, expectedSampleIntervalMillis = 1_000L),
            samples = listOf(
                EnduranceSample(0L, 100.0),
                EnduranceSample(1_000L, 200.0),
                EnduranceSample(4_000L, 900.0),
            ),
        )

        val mean = EnduranceSportEngine.cycling.timeWeightedMean(series)
        assertEquals(100.0, mean?.value ?: Double.NaN, 0.0001)
        assertEquals(1_000L, mean?.coveredMillis)
        assertEquals(5_000L, mean?.declaredCoverageMillis)
        assertEquals(EnduranceSampleIntervalMethod.PREVIOUS_SAMPLE_HOLD, mean?.method)
        assertEquals(1_000L, mean?.expectedSampleIntervalMillis)
    }

    @Test
    fun weightedMeanUsesElapsedIntervalWeightsInsteadOfArithmeticSampleMean() {
        val series = EnduranceMeasurementSeries(
            id = "power",
            kind = EnduranceSeriesKind.POWER,
            unit = EnduranceUnit.WATTS,
            source = EnduranceDataSource.IMPORTED_DEVICE,
            instrumentId = "meter-2",
            coverage = EnduranceSampleCoverage(0L, 5_000L, 2_500L),
            samples = listOf(
                EnduranceSample(0L, 100.0),
                EnduranceSample(1_000L, 200.0),
                EnduranceSample(3_000L, 400.0),
            ),
        )

        val mean = EnduranceSportEngine.cycling.timeWeightedMean(series)
        assertEquals(500.0 / 3.0, mean?.value ?: Double.NaN, 0.0001)
        assertEquals(3_000L, mean?.coveredMillis)
        assertEquals(0.6, mean?.coverageRatio ?: Double.NaN, 0.0001)
    }

    @Test
    fun genericMeanPowerToManualFtpRatioRequiresFullDeclaredCoverage() {
        val completeSeries = powerSeries().copy(samples = listOf(
            EnduranceSample(0L, 100.0), EnduranceSample(1_000L, 100.0),
        ))
        val complete = cyclingPowerResult(completeSeries, elapsedMillis = 1_000L)
        val partialCoverage = powerSeries().copy(
            coverage = EnduranceSampleCoverage(0L, 2_000L, 1_000L, expectedSampleIntervalMillis = 1_000L),
            samples = listOf(EnduranceSample(0L, 100.0), EnduranceSample(1_000L, 100.0)),
        )
        val incomplete = cyclingPowerResult(partialCoverage, elapsedMillis = 2_000L)

        val fullRatio = EnduranceSportEngine.cycling.calculateMetrics(complete)?.segments?.single()
            ?.meanPowerToManualFtpRatio
        val partialRatio = EnduranceSportEngine.cycling.calculateMetrics(incomplete)?.segments?.single()
            ?.meanPowerToManualFtpRatio
        assertEquals(0.5, fullRatio?.ratio ?: Double.NaN, 0.0001)
        assertEquals(1_000L, fullRatio?.coveredMillis)
        assertNull(partialRatio)
    }

    @Test
    fun zoneTimesDoNotBridgeLongGapsOrExtendTheLastSample() {
        val series = EnduranceMeasurementSeries(
            id = "hr",
            kind = EnduranceSeriesKind.HEART_RATE,
            unit = EnduranceUnit.BEATS_PER_MINUTE,
            source = EnduranceDataSource.ATHLETE_REPORTED,
            coverage = EnduranceSampleCoverage(0L, 4_000L, 1_500L),
            samples = listOf(
                EnduranceSample(0L, 100.0),
                EnduranceSample(1_000L, 160.0),
                EnduranceSample(4_000L, 170.0),
            ),
        )
        val zones = EnduranceZoneSet(
            id = "manual-hr-zones-v2",
            unit = EnduranceUnit.BEATS_PER_MINUTE,
            zones = listOf(
                EnduranceZone("z1", "Easy", 90.0, 140.0),
                EnduranceZone("z2", "Hard", 140.0, null),
            ),
        )

        val times = EnduranceSportEngine.running.timeInZones(series, zones)
        assertEquals(1_000L, times?.coveredMillis)
        assertEquals(1_000L, times?.zones?.first()?.durationMillis)
        assertEquals(0L, times?.zones?.last()?.durationMillis)
        assertEquals(0L, times?.unassignedMillis)
        assertEquals(4_000L, times?.declaredCoverageMillis)
    }

    @Test
    fun zoneThresholdsMustBeOrderedAndNonOverlapping() {
        val invalid = EnduranceZoneSet(
            "bad",
            EnduranceUnit.WATTS,
            listOf(
                EnduranceZone("a", "A", 0.0, 200.0),
                EnduranceZone("b", "B", 150.0, null),
            ),
        )
        assertTrue(EnduranceSportEngine.cycling.validateZoneSet(invalid).any {
            it.code == "ZONES_OVERLAP_OR_OUT_OF_ORDER"
        })
    }

    @Test
    fun swimLapsRemainExplicitAndDoNotInferDistanceFromLapCount() {
        val payload = EnduranceSportPayload(
            sportId = SportId.SWIMMING,
            result = EnduranceSportResult(
                context = EnduranceContext(poolLengthMeters = 25.0),
                quality = EnduranceRecordQuality.PARTIAL,
                segments = listOf(
                    EnduranceResultSegment(
                        id = "pool-set",
                        kind = EnduranceSegmentKind.SWIMMING,
                        quality = EnduranceRecordQuality.PARTIAL,
                        elapsedMillis = 1_200_000L,
                        movingMillis = 900_000L,
                        swim = EnduranceSwimDetails(
                            poolLengthMeters = 25.0,
                            laps = listOf(
                                EnduranceSwimLap("lap-1", strokeId = "freestyle", elapsedMillis = 60_000L, turns = 1),
                                EnduranceSwimLap("lap-2", strokeId = "backstroke", elapsedMillis = 65_000L, turns = 1),
                            ),
                            restIntervals = listOf(EnduranceRestInterval(60_000L, 90_000L)),
                            totalTurns = 2,
                        ),
                    ),
                ),
            ),
        )

        val engine = EnduranceSportEngine.swimming
        assertTrue(engine.validatePayload(payload, SportDocumentPurpose.RESULT).isEmpty())
        assertNull(engine.calculateMetrics(payload)?.totalDistanceMeters)
    }

    @Test
    fun triathlonTotalsUseTheOrderedSegmentsAndIncludeTransitionsOnce() {
        val segments = listOf(
            EnduranceResultSegment("swim", EnduranceSegmentKind.SWIMMING, EnduranceRecordQuality.COMPLETE,
                distanceMeters = 750.0, elapsedMillis = 900_000L),
            EnduranceResultSegment("t1", EnduranceSegmentKind.TRANSITION, EnduranceRecordQuality.COMPLETE,
                elapsedMillis = 120_000L),
            EnduranceResultSegment("bike", EnduranceSegmentKind.CYCLING, EnduranceRecordQuality.COMPLETE,
                distanceMeters = 20_000.0, elapsedMillis = 2_400_000L),
            EnduranceResultSegment("t2", EnduranceSegmentKind.TRANSITION, EnduranceRecordQuality.COMPLETE,
                elapsedMillis = 90_000L),
            EnduranceResultSegment("run", EnduranceSegmentKind.RUNNING, EnduranceRecordQuality.COMPLETE,
                distanceMeters = 5_000.0, elapsedMillis = 1_500_000L),
        )
        val payload = EnduranceSportPayload(
            sportId = SportId.TRIATHLON,
            result = EnduranceSportResult(
                quality = EnduranceRecordQuality.COMPLETE,
                segments = segments,
                segmentSequenceIsComplete = true,
            ),
        )

        val engine = EnduranceSportEngine.triathlon
        assertTrue(engine.validatePayload(payload, SportDocumentPurpose.RESULT).isEmpty())
        val metrics = engine.calculateMetrics(payload)
        assertEquals(5_010_000L, metrics?.totalElapsedMillis)
        assertEquals(25_750.0, metrics?.totalDistanceMeters ?: Double.NaN, 0.0001)
        assertNull(metrics?.segments?.get(1)?.distance)
    }

    @Test
    fun missingIsNotZeroAndInvalidatedSegmentsDoNotProduceMetrics() {
        val missing = EnduranceSportPayload(
            sportId = SportId.RUNNING,
            result = EnduranceSportResult(
                quality = EnduranceRecordQuality.PARTIAL,
                segments = listOf(EnduranceResultSegment(
                    id = "unknown-split",
                    kind = EnduranceSegmentKind.RUNNING,
                    quality = EnduranceRecordQuality.UNRECORDED,
                )),
            ),
        )
        val zeroDistance = missing.copy(result = missing.result!!.copy(segments = listOf(
            EnduranceResultSegment(
                id = "zero-distance",
                kind = EnduranceSegmentKind.RUNNING,
                quality = EnduranceRecordQuality.PARTIAL,
                distanceMeters = 0.0,
            ),
        )))

        assertTrue(EnduranceSportEngine.running.validatePayload(missing, SportDocumentPurpose.RESULT).isEmpty())
        assertNull(EnduranceSportEngine.running.calculateMetrics(missing)?.segments?.single()?.distance)
        assertTrue(EnduranceSportEngine.running.validatePayload(zeroDistance, SportDocumentPurpose.RESULT).isNotEmpty())
    }

    @Test
    fun invalidatedMeasurementsArePreservedButNotSummarized() {
        val payload = EnduranceSportPayload(
            sportId = SportId.CYCLING,
            result = EnduranceSportResult(
                quality = EnduranceRecordQuality.PARTIAL,
                segments = listOf(EnduranceResultSegment(
                    id = "invalid-power",
                    kind = EnduranceSegmentKind.CYCLING,
                    quality = EnduranceRecordQuality.INVALIDATED,
                    measurements = listOf(powerSeries()),
                )),
            ),
        )

        assertTrue(EnduranceSportEngine.cycling.validatePayload(payload, SportDocumentPurpose.RESULT).isEmpty())
        assertTrue(EnduranceSportEngine.cycling.calculateMetrics(payload)?.segments?.single()?.timeWeightedMeans.isNullOrEmpty())
    }

    @Test
    fun unitAndSampleValidationRejectsWrongCadenceDimensionAndNonFiniteValues() {
        val wrongCadence = EnduranceMeasurementSeries(
            id = "cadence",
            kind = EnduranceSeriesKind.CADENCE,
            unit = EnduranceUnit.REVOLUTIONS_PER_MINUTE,
            source = EnduranceDataSource.MANUAL_ENTRY,
            coverage = EnduranceSampleCoverage(0L, 1_000L, 1_000L),
            samples = listOf(EnduranceSample(0L, 80.0), EnduranceSample(1_000L, Double.POSITIVE_INFINITY)),
        )
        val payload = EnduranceSportPayload(
            sportId = SportId.RUNNING,
            result = EnduranceSportResult(
                quality = EnduranceRecordQuality.PARTIAL,
                segments = listOf(EnduranceResultSegment(
                    id = "run",
                    kind = EnduranceSegmentKind.RUNNING,
                    quality = EnduranceRecordQuality.PARTIAL,
                    measurements = listOf(wrongCadence),
                )),
            ),
        )

        val issues = EnduranceSportEngine.running.validatePayload(payload, SportDocumentPurpose.RESULT)
        assertTrue(issues.any { it.code == "UNIT_DIMENSION_MISMATCH" })
        assertTrue(issues.any { it.code == "FINITE_REQUIRED" })
    }

    @Test
    fun sampleTimestampsMustBeStrictlyIncreasingAndInsideDeclaredCoverage() {
        val unordered = EnduranceMeasurementSeries(
            id = "speed",
            kind = EnduranceSeriesKind.SPEED,
            unit = EnduranceUnit.METERS_PER_SECOND,
            source = EnduranceDataSource.MANUAL_ENTRY,
            coverage = EnduranceSampleCoverage(0L, 2_000L, 2_000L),
            samples = listOf(
                EnduranceSample(1_000L, 3.0),
                EnduranceSample(1_000L, 3.1),
                EnduranceSample(3_000L, 3.2),
            ),
        )
        val payload = EnduranceSportPayload(
            sportId = SportId.RUNNING,
            result = EnduranceSportResult(
                quality = EnduranceRecordQuality.PARTIAL,
                segments = listOf(EnduranceResultSegment(
                    id = "run",
                    kind = EnduranceSegmentKind.RUNNING,
                    quality = EnduranceRecordQuality.PARTIAL,
                    measurements = listOf(unordered),
                )),
            ),
        )

        val issues = EnduranceSportEngine.running.validatePayload(payload, SportDocumentPurpose.RESULT)
        assertTrue(issues.any { it.code == "SAMPLES_NOT_STRICTLY_ORDERED" })
        assertTrue(issues.any { it.code == "OUTSIDE_DECLARED_COVERAGE" })
    }

    @Test
    fun comparabilityRequiresMatchingContextDistancesSequenceAndCompleteQuality() {
        val base = runningResult()
        val engine = EnduranceSportEngine.running
        val same = base.copy()
        val differentRoute = base.copy(result = base.result!!.copy(context = EnduranceContext(routeId = "park-b")))
        val partial = base.copy(result = base.result!!.copy(quality = EnduranceRecordQuality.PARTIAL))

        assertTrue(engine.areComparable(base, same))
        assertFalse(engine.areComparable(base, differentRoute))
        assertFalse(engine.areComparable(base, partial))
    }

    @Test
    fun runningPaceAndSpeedExposeTheirUnitsAndUseMovingTime() {
        val metrics = EnduranceSportEngine.running.calculateMetrics(runningResult())?.segments?.single()

        assertEquals(296.0, metrics?.paceFromMovingTime?.value ?: Double.NaN, 0.0001)
        assertEquals(EnduranceDerivedUnit.SECONDS_PER_KILOMETER, metrics?.paceFromMovingTime?.unit)
        assertEquals(5_000.0 / 1_480.0, metrics?.speedFromMovingTime?.value ?: Double.NaN, 0.0001)
        assertEquals(EnduranceDerivedUnit.METERS_PER_SECOND, metrics?.speedFromMovingTime?.unit)
    }

    private fun runningResult() = EnduranceSportPayload(
        sportId = SportId.RUNNING,
        result = EnduranceSportResult(
            context = EnduranceContext(
                routeId = "park-a",
                protocolId = "5k",
                protocolRevision = "1",
                conditions = mapOf("temperature" to "18C"),
            ),
            quality = EnduranceRecordQuality.COMPLETE,
            segments = listOf(EnduranceResultSegment(
                id = "run-1",
                kind = EnduranceSegmentKind.RUNNING,
                quality = EnduranceRecordQuality.COMPLETE,
                distanceMeters = 5_000.0,
                elapsedMillis = 1_500_000L,
                movingMillis = 1_480_000L,
            )),
            segmentSequenceIsComplete = true,
        ),
    )

    private fun powerSeries() = EnduranceMeasurementSeries(
        id = "power",
        kind = EnduranceSeriesKind.POWER,
        unit = EnduranceUnit.WATTS,
        source = EnduranceDataSource.IMPORTED_DEVICE,
        instrumentId = "meter-1",
        coverage = EnduranceSampleCoverage(0L, 1_000L, 1_000L, expectedSampleIntervalMillis = 1_000L),
        samples = listOf(EnduranceSample(0L, 100.0), EnduranceSample(1_000L, 100.0)),
    )

    private fun cyclingPowerResult(series: EnduranceMeasurementSeries, elapsedMillis: Long) = EnduranceSportPayload(
        sportId = SportId.CYCLING,
        result = EnduranceSportResult(
            quality = EnduranceRecordQuality.COMPLETE,
            segmentSequenceIsComplete = true,
            segments = listOf(EnduranceResultSegment(
                id = "bike",
                kind = EnduranceSegmentKind.CYCLING,
                quality = EnduranceRecordQuality.COMPLETE,
                distanceMeters = 500.0,
                elapsedMillis = elapsedMillis,
                movingMillis = elapsedMillis,
                measurements = listOf(series),
                manualFtpReference = EnduranceManualFtpReference(200.0, EnduranceDataSource.ATHLETE_REPORTED),
            )),
        ),
    )
}
