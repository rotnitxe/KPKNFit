package com.example.kpkn.domain.performance.lab

import com.example.kpkn.domain.multisport.SportId
import com.example.kpkn.domain.multisport.tracking.SportDocumentPurpose
import com.example.kpkn.domain.multisport.tracking.SportDocumentValidation
import com.example.kpkn.domain.multisport.tracking.SportTrackingDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceLabEngineTest {
    private val protocol = PerformanceLabProtocol("drop-jump", 1, "Drop jump")
    private val context = PerformanceLabContext(
        instrument = "force platform A",
        surface = "rubber mat 20 mm",
        method = "drop jump, hands on hips",
    )

    @Test fun summaryExcludesInvalidMetadataAndAllAmbiguousDuplicateIdentities() {
        val marks = listOf(
            mark("valid", value = 30.0),
            mark("duplicate", value = 80.0), mark("duplicate", value = 90.0),
            mark("wrong-unit", unit = PerformanceLabUnit.SECOND, value = 100.0),
            mark("bad-protocol", protocolValue = protocol.copy(version = 0), value = 110.0),
            mark("missing-context", markContext = context.copy(method = ""), value = 120.0),
            mark("unmeasured-velocity", metric = PerformanceLabMetric.REP_VELOCITY,
                unit = PerformanceLabUnit.METER_PER_SECOND, value = 9.0, provenance = PerformanceLabProvenance.MANUAL_ESTIMATE,
                setId = "set", repetitionIndex = 1),
        )
        val summary = PerformanceLabEngine.summarizeComparable(marks).single()
        assertEquals(1, summary.sampleCount)
        assertEquals("valid", summary.bestMarkId)
        assertEquals(30.0, summary.bestValue!!, 0.0)
    }

    @Test fun summaryDoesNotTrustUnverifiedDerivedRsiValues() {
        val height = mark("height", eventId = "event", value = 40.0)
        val contact = mark("contact", metric = PerformanceLabMetric.CONTACT_TIME,
            unit = PerformanceLabUnit.MILLISECOND, value = 200.0, eventId = "event")
        val derived = (PerformanceLabEngine.calculateRsi(height, contact, "rsi") as PerformanceLabOperation.Success).value
        val forged = derived.copy(value = 99.0)
        assertFalse(PerformanceLabEngine.summarizeComparable(listOf(height, contact, forged)).any { it.metric == PerformanceLabMetric.RSI })
        assertEquals(2.0, PerformanceLabEngine.summarizeComparable(listOf(height, contact, derived))
            .single { it.metric == PerformanceLabMetric.RSI }.bestValue!!, 0.0)
        assertFalse(PerformanceLabEngine.summarizeComparable(listOf(derived)).any { it.metric == PerformanceLabMetric.RSI })
    }

    @Test fun calculationsRejectAmbiguousSourceIdsBeforeProducingDerivedReferences() {
        val velocity = mark("reused", metric = PerformanceLabMetric.REP_VELOCITY, unit = PerformanceLabUnit.METER_PER_SECOND,
            value = 1.0, setId = "set", repetitionIndex = 1)
        val result = PerformanceLabEngine.calculateVelocityLoss(listOf(velocity, velocity.copy(repetitionIndex = 2, value = 0.8)),
            "set", PerformanceLabVelocityReference.FIRST_VALID_REPETITION, "loss")
        assertTrue(result is PerformanceLabOperation.Invalid)
        assertTrue((result as PerformanceLabOperation.Invalid).issues.any { it.code == "DUPLICATE_MARK_ID" })
        val height = mark("height", eventId = "event")
        val contact = mark("contact", metric = PerformanceLabMetric.CONTACT_TIME,
            unit = PerformanceLabUnit.MILLISECOND, value = 200.0, eventId = "event")
        val rsi = PerformanceLabEngine.calculateRsi(height, contact, "height")
        assertTrue(rsi is PerformanceLabOperation.Invalid)
        assertTrue((rsi as PerformanceLabOperation.Invalid).issues.any { it.code == "DISTINCT_FROM_SOURCE_MARK_IDS_REQUIRED" })
    }

    @Test fun medianOfEqualPositiveSubnormalMarksDoesNotBecomeZero() {
        val result = PerformanceLabEngine.summarizeComparable(listOf(
            mark("first", value = Double.MIN_VALUE), mark("second", value = Double.MIN_VALUE),
        )).single()
        assertEquals(Double.MIN_VALUE, result.medianValue, 0.0)
    }

    private fun mark(
        id: String,
        metric: PerformanceLabMetric = PerformanceLabMetric.JUMP_HEIGHT,
        dimension: String = "drop_jump_30cm",
        unit: PerformanceLabUnit = PerformanceLabUnit.CENTIMETER,
        value: Double? = 30.0,
        provenance: PerformanceLabProvenance = PerformanceLabProvenance.INSTRUMENT_MEASURED,
        status: PerformanceLabTrialStatus = PerformanceLabTrialStatus.VALID,
        markContext: PerformanceLabContext = context,
        eventId: String? = null,
        setId: String? = null,
        repetitionIndex: Int? = null,
        protocolValue: PerformanceLabProtocol = protocol,
        reason: String? = null,
    ) = PerformanceLabMark(
        id = id,
        protocol = protocolValue,
        metric = metric,
        dimension = dimension,
        unit = unit,
        value = value,
        provenance = provenance,
        context = markContext,
        status = status,
        eventId = eventId,
        setId = setId,
        repetitionIndex = repetitionIndex,
        reason = reason,
    )

    @Test
    fun onlyValidComparableMarksShareMedianVariationAndBest() {
        val marks = listOf(
            mark("h1", value = 31.0),
            mark("h2", value = 35.0),
            mark("h3", value = 34.0),
            mark("different-surface", value = 60.0, markContext = context.copy(surface = "wood")),
            mark("different-protocol", value = 70.0, protocolValue = protocol.copy(version = 2)),
            mark("invalidated", value = 99.0, status = PerformanceLabTrialStatus.INVALIDATED, reason = "sensor displaced"),
        )

        val summaries = PerformanceLabEngine.summarizeComparable(marks)
        val original = summaries.single { it.context == context && it.protocolVersion == 1 }

        assertEquals(3, original.sampleCount)
        assertEquals(34.0, original.medianValue, 0.0)
        assertEquals(4.0, original.variationRange!!, 0.0)
        assertEquals(35.0, original.bestValue!!, 0.0)
        assertEquals("h2", original.bestMarkId)
        assertEquals(3, summaries.size)
    }

    @Test
    fun missingValueIsNotZeroAndInvalidationNeedsReason() {
        val missingValid = mark("missing", value = null)
        val invalidatedWithoutReason = mark(
            "invalidated",
            status = PerformanceLabTrialStatus.INVALIDATED,
            reason = null,
        )

        val issues = PerformanceLabEngine.validatePayload(PerformanceLabPayload(marks = listOf(missingValid, invalidatedWithoutReason)))

        assertTrue(issues.any { it.path == "marks[0].value" && it.code == "VALID_VALUE_REQUIRED" })
        assertTrue(issues.any { it.path == "marks[1].reason" && it.code == "INVALIDATION_REASON_REQUIRED" })
        assertFalse(PerformanceLabEngine.summarizeComparable(listOf(missingValid, invalidatedWithoutReason)).isNotEmpty())
    }

    @Test
    fun rsiRequiresSameInstrumentedEventAndNeverConvertsFlightTimeToHeight() {
        val height = mark(
            id = "jump-height",
            metric = PerformanceLabMetric.JUMP_HEIGHT,
            unit = PerformanceLabUnit.CENTIMETER,
            value = 80.0,
            eventId = "jump-1",
        )
        val contact = mark(
            id = "contact-time",
            metric = PerformanceLabMetric.CONTACT_TIME,
            unit = PerformanceLabUnit.MILLISECOND,
            value = 200.0,
            eventId = "jump-1",
        )

        val result = PerformanceLabEngine.calculateRsi(height, contact, "rsi-1")

        assertTrue(result is PerformanceLabOperation.Success)
        val rsi = (result as PerformanceLabOperation.Success).value
        assertEquals(4.0, rsi.value!!, 0.0)
        assertEquals(listOf("jump-height", "contact-time"), rsi.derivedFromMarkIds)
        val payload = PerformanceLabPayload(marks = listOf(height, contact, rsi))
        assertTrue(PerformanceLabEngine.validatePayload(payload).isEmpty())

        val flightTimeOnly = mark(
            id = "flight",
            metric = PerformanceLabMetric.FLIGHT_TIME,
            unit = PerformanceLabUnit.SECOND,
            value = 0.8,
            eventId = "jump-1",
        )
        val unsupported = PerformanceLabEngine.calculateRsi(flightTimeOnly, contact, "bad-rsi")
        assertTrue(unsupported is PerformanceLabOperation.Invalid)
        assertTrue((unsupported as PerformanceLabOperation.Invalid).issues.any { it.code == "JUMP_HEIGHT_REQUIRED" })
    }

    @Test
    fun velocityLossUsesExplicitReferenceAndOnlyValidInstrumentedRepetitions() {
        fun velocity(id: String, index: Int, value: Double?, status: PerformanceLabTrialStatus) = mark(
            id = id,
            metric = PerformanceLabMetric.REP_VELOCITY,
            dimension = "back_squat_mean_concentric_velocity",
            unit = PerformanceLabUnit.METER_PER_SECOND,
            value = value,
            status = status,
            setId = "set-1",
            repetitionIndex = index,
            reason = if (status == PerformanceLabTrialStatus.INVALIDATED) "tracking lost" else null,
        )
        val marks = listOf(
            velocity("rep-1", 1, 0.8, PerformanceLabTrialStatus.VALID),
            velocity("rep-2", 2, 1.0, PerformanceLabTrialStatus.VALID),
            velocity("rep-3", 3, null, PerformanceLabTrialStatus.FAILED),
            velocity("rep-4", 4, 0.7, PerformanceLabTrialStatus.VALID),
        )

        val firstReference = PerformanceLabEngine.calculateVelocityLoss(
            marks, "set-1", PerformanceLabVelocityReference.FIRST_VALID_REPETITION, "loss-first",
        )
        val fastestReference = PerformanceLabEngine.calculateVelocityLoss(
            marks, "set-1", PerformanceLabVelocityReference.FASTEST_VALID_REPETITION, "loss-fastest",
        )

        assertTrue(firstReference is PerformanceLabOperation.Success)
        assertTrue(fastestReference is PerformanceLabOperation.Success)
        val first = (firstReference as PerformanceLabOperation.Success).value
        val fastest = (fastestReference as PerformanceLabOperation.Success).value
        assertEquals(12.5, first.velocityLossPercent, 1e-9)
        assertEquals(30.0, fastest.velocityLossPercent, 1e-9)
        assertEquals(1, fastest.excludedRepetitionCount)
        assertEquals(listOf("rep-1", "rep-2", "rep-4"), fastest.sourceMarkIds)

        val payload = PerformanceLabPayload(marks = marks, velocityLossResults = listOf(first, fastest))
        assertTrue(PerformanceLabEngine.validatePayload(payload).isEmpty())
        val serialized = SportTrackingDocument(
            id = "lab-doc",
            engineId = PerformanceLabEngine.id,
            version = PerformanceLabEngine.version,
            sportId = SportId.STRENGTH.name,
            purpose = SportDocumentPurpose.RESULT.name,
            payload = payload.toJsonObject(),
        )
        assertEquals(SportDocumentValidation.Valid, com.example.kpkn.domain.multisport.tracking.SportTrackingRegistry(
            listOf(PerformanceLabEngine),
        ).validate(serialized))
    }

    @Test
    fun videoEstimateCannotBeUsedForVelocityLossOrRsi() {
        val videoHeight = mark(
            "video-height",
            value = 40.0,
            provenance = PerformanceLabProvenance.VIDEO_ESTIMATE,
            eventId = "jump-2",
        )
        val instrumentContact = mark(
            "contact-time",
            metric = PerformanceLabMetric.CONTACT_TIME,
            unit = PerformanceLabUnit.SECOND,
            value = 0.2,
            eventId = "jump-2",
        )
        val rsi = PerformanceLabEngine.calculateRsi(videoHeight, instrumentContact, "rsi-video")
        assertTrue(rsi is PerformanceLabOperation.Invalid)

        val videoVelocity = mark(
            id = "rep-1",
            metric = PerformanceLabMetric.REP_VELOCITY,
            dimension = "bench_mean_concentric_velocity",
            unit = PerformanceLabUnit.METER_PER_SECOND,
            value = 0.7,
            provenance = PerformanceLabProvenance.VIDEO_ESTIMATE,
            setId = "bench-set",
            repetitionIndex = 1,
        )
        val measuredVelocity = mark(
            id = "rep-2",
            metric = PerformanceLabMetric.REP_VELOCITY,
            dimension = "bench_mean_concentric_velocity",
            unit = PerformanceLabUnit.METER_PER_SECOND,
            value = 0.6,
            setId = "bench-set",
            repetitionIndex = 2,
        )
        val loss = PerformanceLabEngine.calculateVelocityLoss(
            listOf(videoVelocity, measuredVelocity),
            "bench-set",
            PerformanceLabVelocityReference.FIRST_VALID_REPETITION,
            "video-loss",
        )
        assertTrue(loss is PerformanceLabOperation.Invalid)
        assertTrue((loss as PerformanceLabOperation.Invalid).issues.any {
            it.code == "VELOCITY_REQUIRES_INSTRUMENT_MEASUREMENT"
        })
    }
}
