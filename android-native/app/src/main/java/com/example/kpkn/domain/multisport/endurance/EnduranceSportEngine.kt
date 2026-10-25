package com.example.kpkn.domain.multisport.endurance

import com.example.kpkn.domain.multisport.SportId
import com.example.kpkn.domain.multisport.tracking.SportDocumentEngine
import com.example.kpkn.domain.multisport.tracking.SportDocumentIssue
import com.example.kpkn.domain.multisport.tracking.SportDocumentPurpose
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

/** Manual endurance capture and calculation; it does not create activities or estimate calories. */
class EnduranceSportEngine(private val sport: SportId) : SportDocumentEngine {
    init {
        require(sport in SUPPORTED_SPORTS) { "EnduranceSportEngine requires a supported endurance SportId" }
    }

    override val id: String = "endurance.manual.${sport.name.lowercase()}"
    override val version: Int = ENDURANCE_SPORT_SCHEMA_VERSION
    override val sports: Set<SportId> = setOf(sport)
    override val purposes: Set<SportDocumentPurpose> = SportDocumentPurpose.entries.toSet()

    private val json = Json { ignoreUnknownKeys = false }

    companion object {
        private val SUPPORTED_SPORTS = setOf(
            SportId.RUNNING,
            SportId.CYCLING,
            SportId.TRAIL,
            SportId.ROWING,
            SportId.SWIMMING,
            SportId.TRIATHLON,
        )

        val running = EnduranceSportEngine(SportId.RUNNING)
        val cycling = EnduranceSportEngine(SportId.CYCLING)
        val trail = EnduranceSportEngine(SportId.TRAIL)
        val rowing = EnduranceSportEngine(SportId.ROWING)
        val swimming = EnduranceSportEngine(SportId.SWIMMING)
        val triathlon = EnduranceSportEngine(SportId.TRIATHLON)
        val registeredEngines: List<EnduranceSportEngine> =
            listOf(running, cycling, trail, rowing, swimming, triathlon)
    }

    override fun validate(payload: JsonObject, purpose: SportDocumentPurpose): List<SportDocumentIssue> {
        val decoded = try {
            json.decodeFromJsonElement<EnduranceSportPayload>(payload)
        } catch (_: Exception) {
            return listOf(SportDocumentIssue("payload", "PAYLOAD_SCHEMA_INVALID"))
        }
        return validatePayload(decoded, purpose).map { SportDocumentIssue(it.path, it.code) }
    }

    override fun validateForSport(
        payload: JsonObject,
        purpose: SportDocumentPurpose,
        sport: SportId,
    ): List<SportDocumentIssue> {
        if (sport != this.sport) return listOf(SportDocumentIssue("sportId", "ENGINE_SPORT_MISMATCH"))
        return validate(payload, purpose)
    }

    override fun plannedDurationSeconds(payload: JsonObject): Long? {
        val decoded = runCatching { json.decodeFromJsonElement<EnduranceSportPayload>(payload) }.getOrNull()
            ?: return null
        if (validatePayload(decoded, SportDocumentPurpose.PLAN).isNotEmpty()) return null
        return decoded.plan?.declaredDurationSeconds?.takeIf { it > 0L }
    }

    fun validatePayload(
        payload: EnduranceSportPayload,
        purpose: SportDocumentPurpose,
    ): List<EnduranceSportIssue> = buildList {
        if (payload.schemaVersion != ENDURANCE_SPORT_SCHEMA_VERSION) {
            add(EnduranceSportIssue("schemaVersion", "UNSUPPORTED_SCHEMA_VERSION"))
        }
        if (payload.sportId != sport) add(EnduranceSportIssue("sportId", "ENGINE_SPORT_MISMATCH"))
        when (purpose) {
            SportDocumentPurpose.PLAN -> {
                if (payload.plan == null) add(EnduranceSportIssue("plan", "REQUIRED"))
                if (payload.result != null) add(EnduranceSportIssue("result", "PLAN_MUST_NOT_CONTAIN_RESULT"))
                if (payload.observation != null) add(EnduranceSportIssue("observation", "PLAN_MUST_NOT_CONTAIN_OBSERVATION"))
                payload.plan?.let { validatePlan(it, this) }
            }
            SportDocumentPurpose.RESULT -> {
                if (payload.result == null) add(EnduranceSportIssue("result", "REQUIRED"))
                if (payload.plan != null) add(EnduranceSportIssue("plan", "RESULT_MUST_NOT_CONTAIN_PLAN"))
                if (payload.observation != null) add(EnduranceSportIssue("observation", "RESULT_MUST_NOT_CONTAIN_OBSERVATION"))
                payload.result?.let { validateResult(it, this) }
            }
            SportDocumentPurpose.OBSERVATION -> {
                if (payload.observation == null) add(EnduranceSportIssue("observation", "REQUIRED"))
                if (payload.plan != null) add(EnduranceSportIssue("plan", "OBSERVATION_MUST_NOT_CONTAIN_PLAN"))
                if (payload.result != null) add(EnduranceSportIssue("result", "OBSERVATION_MUST_NOT_CONTAIN_RESULT"))
                payload.observation?.let { validateObservation(it, this) }
            }
        }
    }.distinct()

    fun validateZoneSet(zoneSet: EnduranceZoneSet): List<EnduranceSportIssue> = buildList {
        if (zoneSet.id.isBlank()) add(EnduranceSportIssue("zoneSet.id", "REQUIRED"))
        if (!zoneSet.unit.isZoneCompatible()) add(EnduranceSportIssue("zoneSet.unit", "UNSUPPORTED_ZONE_UNIT"))
        if (zoneSet.zones.isEmpty()) add(EnduranceSportIssue("zoneSet.zones", "AT_LEAST_ONE_ZONE_REQUIRED"))
        val duplicateIds = zoneSet.zones.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        var previousUpper: Double? = null
        zoneSet.zones.forEachIndexed { index, zone ->
            val path = "zoneSet.zones[$index]"
            if (zone.id.isBlank()) add(EnduranceSportIssue("$path.id", "REQUIRED"))
            if (zone.id in duplicateIds) add(EnduranceSportIssue("$path.id", "DUPLICATE"))
            if (zone.label.isBlank()) add(EnduranceSportIssue("$path.label", "REQUIRED"))
            if (!zone.lowerInclusive.isFinite() || zone.lowerInclusive < 0.0) {
                add(EnduranceSportIssue("$path.lowerInclusive", "FINITE_NON_NEGATIVE_REQUIRED"))
            }
            val upper = zone.upperExclusive
            if (upper != null && (!upper.isFinite() || upper <= zone.lowerInclusive)) {
                add(EnduranceSportIssue("$path.upperExclusive", "MUST_EXCEED_LOWER_BOUND"))
            }
            if (previousUpper != null && zone.lowerInclusive.isFinite() && zone.lowerInclusive < previousUpper!!) {
                add(EnduranceSportIssue("$path.lowerInclusive", "ZONES_OVERLAP_OR_OUT_OF_ORDER"))
            }
            if (previousUpper == null && index > 0) {
                add(EnduranceSportIssue("zoneSet.zones[${index - 1}].upperExclusive", "OPEN_ENDED_ZONE_MUST_BE_LAST"))
            }
            previousUpper = upper
            if (upper == null && index != zoneSet.zones.lastIndex) {
                add(EnduranceSportIssue("$path.upperExclusive", "OPEN_ENDED_ZONE_MUST_BE_LAST"))
            }
        }
    }.distinct()

    /** Piecewise previous-sample hold across accepted adjacent pairs only; gaps and tails stay uncovered. */
    fun timeWeightedMean(series: EnduranceMeasurementSeries): EnduranceWeightedMean? {
        if (validateSeries(series, "series", null).isNotEmpty()) return null
        val coverage = series.coverage ?: return null
        val intervals = observedIntervals(series) ?: return null
        var coveredMillis = 0L
        var weightedValue = 0.0
        for (interval in intervals) {
            val duration = interval.endOffsetMillis - interval.startOffsetMillis
            coveredMillis = safeAdd(coveredMillis, duration) ?: return null
            weightedValue += interval.value * duration.toDouble()
            if (!weightedValue.isFinite()) return null
        }
        if (coveredMillis <= 0L) return null
        val mean = weightedValue / coveredMillis.toDouble()
        if (!mean.isFinite()) return null
        return EnduranceWeightedMean(
            value = mean,
            unit = series.unit,
            coveredMillis = coveredMillis,
            declaredCoverageMillis = coverage.endOffsetMillis - coverage.startOffsetMillis,
            expectedSampleIntervalMillis = coverage.expectedSampleIntervalMillis,
        )
    }

    /** Times are assigned only between adjacent samples within the declared maximum gap. */
    fun timeInZones(
        series: EnduranceMeasurementSeries,
        zoneSet: EnduranceZoneSet,
    ): EnduranceZoneTimeSummary? {
        if (validateZoneSet(zoneSet).isNotEmpty() || zoneSet.unit != series.unit) return null
        if (validateSeries(series, "series", null).isNotEmpty()) return null
        val coverage = series.coverage ?: return null
        val intervals = observedIntervals(series) ?: return null
        val zoneTimes = zoneSet.zones.associate { it.id to 0L }.toMutableMap()
        var unassigned = 0L
        var covered = 0L
        for (interval in intervals) {
            val duration = interval.endOffsetMillis - interval.startOffsetMillis
            covered = safeAdd(covered, duration) ?: return null
            val zone = zoneSet.zones.firstOrNull { candidate ->
                interval.value >= candidate.lowerInclusive &&
                    (candidate.upperExclusive == null || interval.value < candidate.upperExclusive)
            }
            if (zone == null) unassigned = safeAdd(unassigned, duration) ?: return null
            else zoneTimes[zone.id] = safeAdd(zoneTimes.getValue(zone.id), duration) ?: return null
        }
        return EnduranceZoneTimeSummary(
            unit = series.unit,
            zoneSetId = zoneSet.id,
            zones = zoneSet.zones.map { EnduranceZoneTime(it.id, zoneTimes.getValue(it.id)) },
            unassignedMillis = unassigned,
            coveredMillis = covered,
            declaredCoverageMillis = coverage.endOffsetMillis - coverage.startOffsetMillis,
        )
    }

    fun calculateMetrics(payload: EnduranceSportPayload): EnduranceSportMetrics? {
        if (payload.sportId != sport || validatePayload(payload, SportDocumentPurpose.RESULT).isNotEmpty()) return null
        val result = payload.result ?: return null
        val segmentMetrics = result.segments.map { segment ->
            val included = segment.quality == EnduranceRecordQuality.COMPLETE ||
                segment.quality == EnduranceRecordQuality.PARTIAL
            val distance = if (included) segment.distanceMeters?.let {
                EnduranceDerivedMetric(it, EnduranceDerivedUnit.METERS)
            } else null
            val elapsed = if (included) segment.elapsedMillis?.toDouble()?.let {
                EnduranceDerivedMetric(it, EnduranceDerivedUnit.MILLISECONDS)
            } else null
            val moving = if (included) segment.movingMillis?.toDouble()?.let {
                EnduranceDerivedMetric(it, EnduranceDerivedUnit.MILLISECONDS)
            } else null
            val speed = if (included && segment.kind != EnduranceSegmentKind.TRANSITION) {
                segment.distanceMeters?.let { meters -> segment.movingMillis?.let { millis ->
                    if (meters > 0.0 && millis > 0L) {
                        (meters / (millis.toDouble() / 1_000.0)).takeIf { it.isFinite() }
                            ?.let { EnduranceDerivedMetric(it, EnduranceDerivedUnit.METERS_PER_SECOND) }
                    } else null
                } }
            } else null
            val pace = if (included && segment.kind in setOf(EnduranceSegmentKind.RUNNING, EnduranceSegmentKind.TRAIL)) {
                segment.distanceMeters?.let { meters -> segment.movingMillis?.let { millis ->
                    if (meters > 0.0 && millis > 0L) {
                        (millis.toDouble() / meters).takeIf { it.isFinite() }
                            ?.let { EnduranceDerivedMetric(it, EnduranceDerivedUnit.SECONDS_PER_KILOMETER) }
                    } else null
                } }
            } else null
            val means = if (included) segment.measurements.mapNotNull { series ->
                timeWeightedMean(series)?.let { series.id to it }
            }.toMap() else emptyMap()
            val powerSeries = segment.measurements.filter { it.kind == EnduranceSeriesKind.POWER }
            val manualFtpRatio = if (segment.quality == EnduranceRecordQuality.COMPLETE &&
                segment.kind == EnduranceSegmentKind.CYCLING &&
                segment.manualFtpReference != null && powerSeries.size == 1) {
                val powerMean = means[powerSeries.single().id]
                val referenceWatts = segment.manualFtpReference.watts
                val powerCoverage = powerSeries.single().coverage
                val coversFullSegment = segment.elapsedMillis?.let { segmentDuration ->
                    powerCoverage?.let { coverage ->
                        coverage.startOffsetMillis == 0L && coverage.endOffsetMillis == segmentDuration &&
                            coverage.expectedSampleIntervalMillis != null
                    } == true
                } == true
                if (powerMean != null && powerMean.unit == EnduranceUnit.WATTS &&
                    powerMean.coveredMillis == powerMean.declaredCoverageMillis &&
                    referenceWatts > 0.0 && coversFullSegment) {
                    val ratio = powerMean.value / referenceWatts
                    ratio.takeIf { it.isFinite() }?.let {
                        EnduranceManualFtpRatio(it, powerSeries.single().id, powerMean.coveredMillis, powerMean.declaredCoverageMillis)
                    }
                } else null
            } else null
            EnduranceSegmentMetrics(segment.id, segment.quality, distance, elapsed, moving, speed, pace, means, manualFtpRatio)
        }

        val canAggregate = result.quality == EnduranceRecordQuality.COMPLETE && result.segmentSequenceIsComplete
        val totalElapsed = if (canAggregate && result.segments.all { it.elapsedMillis != null }) {
            result.segments.fold(0L) { total, segment ->
                safeAdd(total, requireNotNull(segment.elapsedMillis)) ?: return null
            }
        } else null
        val legSegments = result.segments.filter { it.kind != EnduranceSegmentKind.TRANSITION }
        val totalDistance = if (canAggregate && legSegments.isNotEmpty() && legSegments.all { it.distanceMeters != null }) {
            legSegments.sumOf { requireNotNull(it.distanceMeters) }.takeIf { it.isFinite() }
        } else null
        return EnduranceSportMetrics(segmentMetrics, totalElapsed, totalDistance)
    }

    /** Conservative comparison: complete records, matching route/protocol/conditions, and same splits. */
    fun areComparable(left: EnduranceSportPayload, right: EnduranceSportPayload): Boolean {
        if (left.sportId != sport || right.sportId != sport) return false
        if (validatePayload(left, SportDocumentPurpose.RESULT).isNotEmpty() ||
            validatePayload(right, SportDocumentPurpose.RESULT).isNotEmpty()) return false
        val a = left.result ?: return false
        val b = right.result ?: return false
        if (a.quality != EnduranceRecordQuality.COMPLETE || b.quality != EnduranceRecordQuality.COMPLETE ||
            !a.segmentSequenceIsComplete || !b.segmentSequenceIsComplete || a.context != b.context) return false
        if (a.segments.size != b.segments.size) return false
        return a.segments.zip(b.segments).all { (x, y) ->
            x.quality == EnduranceRecordQuality.COMPLETE && y.quality == EnduranceRecordQuality.COMPLETE &&
                x.kind == y.kind && x.distanceMeters == y.distanceMeters &&
                x.swim?.poolLengthMeters == y.swim?.poolLengthMeters &&
                x.manualFtpReference == y.manualFtpReference &&
                seriesSignatures(x.measurements) == seriesSignatures(y.measurements)
        }
    }

    private fun validatePlan(plan: EnduranceSportPlan, issues: MutableList<EnduranceSportIssue>) {
        validateContext(plan.context, "plan.context", issues)
        if (plan.declaredDurationSeconds != null && plan.declaredDurationSeconds <= 0L) {
            issues += EnduranceSportIssue("plan.declaredDurationSeconds", "POSITIVE_REQUIRED")
        }
        checkPositive(plan.targetDistanceMeters, "plan.targetDistanceMeters", issues)
        checkPositive(plan.targetPaceSecondsPerKilometer, "plan.targetPaceSecondsPerKilometer", issues)
    }

    private fun validateResult(result: EnduranceSportResult, issues: MutableList<EnduranceSportIssue>) {
        validateContext(result.context, "result.context", issues)
        if (result.segments.isEmpty()) issues += EnduranceSportIssue("result.segments", "AT_LEAST_ONE_SEGMENT_REQUIRED")
        val duplicateIds = result.segments.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        val expectedKinds = allowedKinds(sport)
        result.segments.forEachIndexed { index, segment ->
            val path = "result.segments[$index]"
            if (segment.id.isBlank()) issues += EnduranceSportIssue("$path.id", "REQUIRED")
            if (segment.id in duplicateIds) issues += EnduranceSportIssue("$path.id", "DUPLICATE")
            if (segment.kind !in expectedKinds) issues += EnduranceSportIssue("$path.kind", "UNSUPPORTED_SEGMENT_FOR_SPORT")
            validateSegment(segment, path, result.context.poolLengthMeters, issues)
        }
        if (sport == SportId.TRIATHLON) {
            var lastWasTransition = true
            result.segments.forEachIndexed { index, segment ->
                val isTransition = segment.kind == EnduranceSegmentKind.TRANSITION
                if (isTransition && (lastWasTransition || index == result.segments.lastIndex)) {
                    issues += EnduranceSportIssue("result.segments[$index].kind", "TRANSITION_MUST_SEPARATE_LEGS")
                }
                lastWasTransition = isTransition
            }
        }
        if (result.quality == EnduranceRecordQuality.COMPLETE) {
            if (!result.segmentSequenceIsComplete) issues += EnduranceSportIssue("result.segmentSequenceIsComplete", "COMPLETE_REQUIRES_COMPLETE_SEQUENCE")
            result.segments.forEachIndexed { index, segment ->
                if (segment.quality != EnduranceRecordQuality.COMPLETE) {
                    issues += EnduranceSportIssue("result.segments[$index].quality", "COMPLETE_RESULT_REQUIRES_COMPLETE_SEGMENTS")
                }
                if (segment.elapsedMillis == null) {
                    issues += EnduranceSportIssue("result.segments[$index].elapsedMillis", "COMPLETE_RESULT_REQUIRES_ELAPSED_TIME")
                }
            }
        }
        if (result.quality == EnduranceRecordQuality.UNRECORDED &&
            (result.segmentSequenceIsComplete || result.segments.any { it.quality != EnduranceRecordQuality.UNRECORDED })) {
            issues += EnduranceSportIssue("result.quality", "UNRECORDED_RESULT_MUST_CONTAIN_ONLY_UNRECORDED_SEGMENTS")
        }
    }

    private fun validateSegment(
        segment: EnduranceResultSegment,
        path: String,
        contextPoolLengthMeters: Double?,
        issues: MutableList<EnduranceSportIssue>,
    ) {
        if (segment.quality == EnduranceRecordQuality.UNRECORDED) {
            if (segment.distanceMeters != null || segment.elapsedMillis != null || segment.movingMillis != null ||
                segment.elevationGainMeters != null || segment.elevationLossMeters != null ||
                segment.measurements.isNotEmpty() || segment.manualFtpReference != null || segment.swim != null) {
                issues += EnduranceSportIssue(path, "UNRECORDED_SEGMENT_MUST_NOT_CARRY_MEASUREMENTS")
            }
            return
        }
        if (segment.kind == EnduranceSegmentKind.TRANSITION) {
            if (segment.distanceMeters != null || segment.movingMillis != null || segment.elevationGainMeters != null ||
                segment.elevationLossMeters != null || segment.measurements.isNotEmpty() ||
                segment.manualFtpReference != null || segment.swim != null) {
                issues += EnduranceSportIssue(path, "TRANSITION_ONLY_SUPPORTS_ELAPSED_TIME_AND_NOTE")
            }
        } else if (segment.swim != null && segment.kind != EnduranceSegmentKind.SWIMMING) {
            issues += EnduranceSportIssue("$path.swim", "SWIM_DETAILS_REQUIRE_SWIMMING_SEGMENT")
        }

        checkPositive(segment.distanceMeters, "$path.distanceMeters", issues)
        checkPositive(segment.elapsedMillis, "$path.elapsedMillis", issues)
        checkPositive(segment.movingMillis, "$path.movingMillis", issues)
        if (segment.elapsedMillis != null && segment.movingMillis != null && segment.movingMillis > segment.elapsedMillis) {
            issues += EnduranceSportIssue("$path.movingMillis", "CANNOT_EXCEED_ELAPSED_TIME")
        }
        checkNonNegative(segment.elevationGainMeters, "$path.elevationGainMeters", issues)
        checkNonNegative(segment.elevationLossMeters, "$path.elevationLossMeters", issues)
        segment.manualFtpReference?.let { reference ->
            if (segment.kind != EnduranceSegmentKind.CYCLING) {
                issues += EnduranceSportIssue("$path.manualFtpReference", "CYCLING_SEGMENT_REQUIRED")
            }
            checkPositive(reference.watts, "$path.manualFtpReference.watts", issues)
            if (reference.source == EnduranceDataSource.IMPORTED_DEVICE) {
                issues += EnduranceSportIssue("$path.manualFtpReference.source", "MANUAL_REFERENCE_SOURCE_REQUIRED")
            }
            if (reference.contextId != null && reference.contextId.isBlank()) {
                issues += EnduranceSportIssue("$path.manualFtpReference.contextId", "NON_BLANK_OR_NULL_REQUIRED")
            }
        }
        val duplicateSeriesIds = segment.measurements.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        segment.measurements.forEachIndexed { seriesIndex, series ->
            val seriesPath = "$path.measurements[$seriesIndex]"
            if (series.id in duplicateSeriesIds) issues += EnduranceSportIssue("$seriesPath.id", "DUPLICATE")
            issues += validateSeries(series, seriesPath, segment.kind)
            segment.elapsedMillis?.let { segmentDuration ->
                if (series.coverage?.endOffsetMillis?.let { it > segmentDuration } == true) {
                    issues += EnduranceSportIssue("$seriesPath.coverage.endOffsetMillis", "EXCEEDS_SEGMENT_DURATION")
                }
            }
        }
        segment.swim?.let { swim ->
            validateSwim(swim, "$path.swim", segment.elapsedMillis, issues)
            if (swim.poolLengthMeters != null && contextPoolLengthMeters != null &&
                swim.poolLengthMeters != contextPoolLengthMeters) {
                issues += EnduranceSportIssue("$path.swim.poolLengthMeters", "POOL_LENGTH_CONFLICTS_WITH_CONTEXT")
            }
        }
    }

    private fun validateSwim(
        swim: EnduranceSwimDetails,
        path: String,
        segmentElapsedMillis: Long?,
        issues: MutableList<EnduranceSportIssue>,
    ) {
        checkPositive(swim.poolLengthMeters, "$path.poolLengthMeters", issues)
        if (swim.totalTurns != null && swim.totalTurns < 0) issues += EnduranceSportIssue("$path.totalTurns", "NON_NEGATIVE_REQUIRED")
        val duplicateLapIds = swim.laps.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        swim.laps.forEachIndexed { index, lap ->
            val lapPath = "$path.laps[$index]"
            if (lap.id.isBlank()) issues += EnduranceSportIssue("$lapPath.id", "REQUIRED")
            if (lap.id in duplicateLapIds) issues += EnduranceSportIssue("$lapPath.id", "DUPLICATE")
            checkPositive(lap.distanceMeters, "$lapPath.distanceMeters", issues)
            checkPositive(lap.elapsedMillis, "$lapPath.elapsedMillis", issues)
            checkPositive(lap.movingMillis, "$lapPath.movingMillis", issues)
            if (lap.elapsedMillis != null && lap.movingMillis != null && lap.movingMillis > lap.elapsedMillis) {
                issues += EnduranceSportIssue("$lapPath.movingMillis", "CANNOT_EXCEED_ELAPSED_TIME")
            }
            if (lap.turns != null && lap.turns < 0) issues += EnduranceSportIssue("$lapPath.turns", "NON_NEGATIVE_REQUIRED")
            if (lap.strokeId != null && lap.strokeId.isBlank()) issues += EnduranceSportIssue("$lapPath.strokeId", "NON_BLANK_OR_NULL_REQUIRED")
        }
        var previousEnd = -1L
        swim.restIntervals.forEachIndexed { index, rest ->
            val restPath = "$path.restIntervals[$index]"
            if (rest.startOffsetMillis < 0L || rest.endOffsetMillis <= rest.startOffsetMillis) {
                issues += EnduranceSportIssue(restPath, "INVALID_INTERVAL")
            }
            if (index > 0 && rest.startOffsetMillis < previousEnd) {
                issues += EnduranceSportIssue("$restPath.startOffsetMillis", "INTERVALS_OVERLAP_OR_OUT_OF_ORDER")
            }
            if (segmentElapsedMillis != null && rest.endOffsetMillis > segmentElapsedMillis) {
                issues += EnduranceSportIssue("$restPath.endOffsetMillis", "EXCEEDS_SEGMENT_DURATION")
            }
            previousEnd = rest.endOffsetMillis
        }
    }

    private fun validateObservation(observation: EnduranceSportObservation, issues: MutableList<EnduranceSportIssue>) {
        validateContext(observation.context, "observation.context", issues)
        if (observation.segmentKind !in allowedKinds(sport) || observation.segmentKind == EnduranceSegmentKind.TRANSITION) {
            issues += EnduranceSportIssue("observation.segmentKind", "UNSUPPORTED_SEGMENT_FOR_SPORT")
        }
        issues += validateSeries(observation.series, "observation.series", observation.segmentKind)
        if (observation.quality == EnduranceRecordQuality.UNRECORDED && observation.series.samples.isNotEmpty()) {
            issues += EnduranceSportIssue("observation.series.samples", "UNRECORDED_OBSERVATION_MUST_NOT_CARRY_SAMPLES")
        }
    }

    private fun validateSeries(
        series: EnduranceMeasurementSeries,
        path: String,
        segmentKind: EnduranceSegmentKind?,
    ): List<EnduranceSportIssue> = buildList {
        if (series.id.isBlank()) add(EnduranceSportIssue("$path.id", "REQUIRED"))
        if (series.source == EnduranceDataSource.IMPORTED_DEVICE && series.instrumentId.isNullOrBlank()) {
            add(EnduranceSportIssue("$path.instrumentId", "REQUIRED_FOR_IMPORTED_DEVICE"))
        }
        if (series.instrumentId != null && series.instrumentId.isBlank()) {
            add(EnduranceSportIssue("$path.instrumentId", "NON_BLANK_OR_NULL_REQUIRED"))
        }
        if (!series.unit.isAllowedFor(series.kind, segmentKind)) {
            add(EnduranceSportIssue("$path.unit", "UNIT_DIMENSION_MISMATCH"))
        }
        val coverage = series.coverage
        if (coverage == null) {
            if (series.samples.isNotEmpty()) add(EnduranceSportIssue("$path.coverage", "REQUIRED_WITH_SAMPLES"))
        } else {
            if (coverage.startOffsetMillis < 0L || coverage.endOffsetMillis <= coverage.startOffsetMillis) {
                add(EnduranceSportIssue("$path.coverage", "INVALID_WINDOW"))
            }
            if (coverage.maximumObservedGapMillis <= 0L) {
                add(EnduranceSportIssue("$path.coverage.maximumObservedGapMillis", "POSITIVE_REQUIRED"))
            }
            if (coverage.expectedSampleIntervalMillis != null && coverage.expectedSampleIntervalMillis <= 0L) {
                add(EnduranceSportIssue("$path.coverage.expectedSampleIntervalMillis", "POSITIVE_REQUIRED"))
            }
        }
        var previousOffset = -1L
        series.samples.forEachIndexed { index, sample ->
            val samplePath = "$path.samples[$index]"
            if (sample.offsetMillis < 0L) add(EnduranceSportIssue("$samplePath.offsetMillis", "NON_NEGATIVE_REQUIRED"))
            if (index > 0 && sample.offsetMillis <= previousOffset) {
                add(EnduranceSportIssue("$samplePath.offsetMillis", "SAMPLES_NOT_STRICTLY_ORDERED"))
            }
            if (coverage != null && sample.offsetMillis !in coverage.startOffsetMillis..coverage.endOffsetMillis) {
                add(EnduranceSportIssue("$samplePath.offsetMillis", "OUTSIDE_DECLARED_COVERAGE"))
            }
            if (!sample.value.isFinite()) add(EnduranceSportIssue("$samplePath.value", "FINITE_REQUIRED"))
            else if (sample.value < 0.0 || (series.kind in setOf(EnduranceSeriesKind.HEART_RATE, EnduranceSeriesKind.CADENCE) && sample.value == 0.0)) {
                add(EnduranceSportIssue("$samplePath.value", "VALUE_OUT_OF_RANGE"))
            }
            previousOffset = sample.offsetMillis
        }
    }

    private fun validateContext(context: EnduranceContext, path: String, issues: MutableList<EnduranceSportIssue>) {
        listOf(
            "protocolId" to context.protocolId,
            "protocolRevision" to context.protocolRevision,
            "routeId" to context.routeId,
            "routeRevision" to context.routeRevision,
            "surfaceId" to context.surfaceId,
        ).forEach { (name, value) ->
            if (value != null && value.isBlank()) issues += EnduranceSportIssue("$path.$name", "NON_BLANK_OR_NULL_REQUIRED")
        }
        checkPositive(context.poolLengthMeters, "$path.poolLengthMeters", issues)
        context.conditions.forEach { (key, value) ->
            if (key.isBlank() || value.isBlank()) issues += EnduranceSportIssue("$path.conditions", "EMPTY_KEY_OR_VALUE")
        }
    }

    private fun observedIntervals(series: EnduranceMeasurementSeries): List<EnduranceObservedInterval>? {
        val coverage = series.coverage ?: return null
        if (series.samples.size < 2) return emptyList()
        return buildList {
            for (index in 0 until series.samples.lastIndex) {
                val current = series.samples[index]
                val next = series.samples[index + 1]
                val gap = next.offsetMillis - current.offsetMillis
                if (gap <= 0L || gap > coverage.maximumObservedGapMillis) continue
                val start = maxOf(current.offsetMillis, coverage.startOffsetMillis)
                val end = minOf(next.offsetMillis, coverage.endOffsetMillis)
                if (end > start) add(EnduranceObservedInterval(start, end, current.value))
            }
        }
    }

    private fun seriesSignatures(series: List<EnduranceMeasurementSeries>): List<List<String?>> = series
        .map { sampleSeries ->
            val coverage = sampleSeries.coverage
            val coveredMillis = observedIntervals(sampleSeries)?.fold(0L) { total, interval ->
                safeAdd(total, interval.endOffsetMillis - interval.startOffsetMillis) ?: Long.MAX_VALUE
            }
            listOf(
                sampleSeries.id,
                sampleSeries.kind.name,
                sampleSeries.unit.name,
                sampleSeries.source.name,
                sampleSeries.instrumentId,
                coverage?.startOffsetMillis?.toString(),
                coverage?.endOffsetMillis?.toString(),
                coverage?.maximumObservedGapMillis?.toString(),
                coverage?.expectedSampleIntervalMillis?.toString(),
                coveredMillis?.toString(),
            )
        }
        .sortedBy { it.firstOrNull().orEmpty() }

    private fun allowedKinds(sport: SportId): Set<EnduranceSegmentKind> = when (sport) {
        SportId.RUNNING -> setOf(EnduranceSegmentKind.RUNNING)
        SportId.CYCLING -> setOf(EnduranceSegmentKind.CYCLING)
        SportId.TRAIL -> setOf(EnduranceSegmentKind.TRAIL)
        SportId.ROWING -> setOf(EnduranceSegmentKind.ROWING)
        SportId.SWIMMING -> setOf(EnduranceSegmentKind.SWIMMING)
        SportId.TRIATHLON -> setOf(
            EnduranceSegmentKind.SWIMMING,
            EnduranceSegmentKind.CYCLING,
            EnduranceSegmentKind.RUNNING,
            EnduranceSegmentKind.TRANSITION,
        )
        else -> emptySet()
    }

    private fun EnduranceUnit.isZoneCompatible(): Boolean = this in setOf(
        EnduranceUnit.BEATS_PER_MINUTE,
        EnduranceUnit.WATTS,
        EnduranceUnit.RUNNING_STEPS_PER_MINUTE,
        EnduranceUnit.REVOLUTIONS_PER_MINUTE,
        EnduranceUnit.STROKES_PER_MINUTE,
        EnduranceUnit.METERS_PER_SECOND,
    )

    private fun EnduranceUnit.isAllowedFor(kind: EnduranceSeriesKind, segmentKind: EnduranceSegmentKind?): Boolean = when (kind) {
        EnduranceSeriesKind.HEART_RATE -> this == EnduranceUnit.BEATS_PER_MINUTE
        EnduranceSeriesKind.POWER -> this == EnduranceUnit.WATTS
        EnduranceSeriesKind.SPEED -> this == EnduranceUnit.METERS_PER_SECOND
        EnduranceSeriesKind.CADENCE -> when (segmentKind) {
            EnduranceSegmentKind.RUNNING, EnduranceSegmentKind.TRAIL -> this == EnduranceUnit.RUNNING_STEPS_PER_MINUTE
            EnduranceSegmentKind.CYCLING -> this == EnduranceUnit.REVOLUTIONS_PER_MINUTE
            EnduranceSegmentKind.ROWING, EnduranceSegmentKind.SWIMMING -> this == EnduranceUnit.STROKES_PER_MINUTE
            EnduranceSegmentKind.TRANSITION, null -> this in setOf(
                EnduranceUnit.RUNNING_STEPS_PER_MINUTE,
                EnduranceUnit.REVOLUTIONS_PER_MINUTE,
                EnduranceUnit.STROKES_PER_MINUTE,
            )
        }
    }

    private fun checkPositive(value: Double?, path: String, issues: MutableList<EnduranceSportIssue>) {
        if (value != null && (!value.isFinite() || value <= 0.0)) issues += EnduranceSportIssue(path, "FINITE_POSITIVE_REQUIRED")
    }

    private fun checkPositive(value: Long?, path: String, issues: MutableList<EnduranceSportIssue>) {
        if (value != null && value <= 0L) issues += EnduranceSportIssue(path, "POSITIVE_REQUIRED")
    }

    private fun checkNonNegative(value: Double?, path: String, issues: MutableList<EnduranceSportIssue>) {
        if (value != null && (!value.isFinite() || value < 0.0)) issues += EnduranceSportIssue(path, "FINITE_NON_NEGATIVE_REQUIRED")
    }

    private fun safeAdd(left: Long, right: Long): Long? = try {
        Math.addExact(left, right)
    } catch (_: ArithmeticException) {
        null
    }
}
