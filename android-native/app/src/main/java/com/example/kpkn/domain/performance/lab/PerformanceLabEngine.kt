package com.example.kpkn.domain.performance.lab

import com.example.kpkn.domain.multisport.SportId
import com.example.kpkn.domain.multisport.tracking.SportDocumentEngine
import com.example.kpkn.domain.multisport.tracking.SportDocumentIssue
import com.example.kpkn.domain.multisport.tracking.SportDocumentPurpose
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

/** Manual, provenance-aware tests. This is a measurement ledger, not a sport programming engine. */
object PerformanceLabEngine : SportDocumentEngine {
    override val id: String = "performance_lab.manual"
    override val version: Int = 1
    override val sports: Set<SportId> = setOf(
        SportId.STRENGTH,
        SportId.HYPERTROPHY,
        SportId.POWERLIFTING,
        SportId.POWERBUILDING,
        SportId.WEIGHTLIFTING,
        SportId.CALISTHENICS,
        SportId.STRONGMAN,
        SportId.ARMWRESTLING,
        SportId.SPRINT,
        SportId.JUMP,
    )
    override val purposes: Set<SportDocumentPurpose> = setOf(
        SportDocumentPurpose.RESULT,
        SportDocumentPurpose.OBSERVATION,
    )

    private val json = Json { ignoreUnknownKeys = false }

    private data class ComparableKey(
        val protocolId: String,
        val protocolVersion: Int,
        val metric: PerformanceLabMetric,
        val dimension: String,
        val unit: PerformanceLabUnit,
        val provenance: PerformanceLabProvenance,
        val context: PerformanceLabContext,
        val formulaId: String?,
    )

    override fun validate(payload: JsonObject, purpose: SportDocumentPurpose): List<SportDocumentIssue> {
        if (purpose !in purposes) return listOf(SportDocumentIssue("purpose", "UNSUPPORTED_PURPOSE"))
        val decoded = try {
            json.decodeFromJsonElement<PerformanceLabPayload>(payload)
        } catch (_: Exception) {
            return listOf(SportDocumentIssue("payload", "PAYLOAD_SCHEMA_INVALID"))
        }
        return validatePayload(decoded).map { SportDocumentIssue(it.path, it.code) }
    }

    fun validatePayload(payload: PerformanceLabPayload): List<PerformanceLabIssue> = buildList {
        if (payload.schemaVersion != PERFORMANCE_LAB_PAYLOAD_VERSION) {
            add(PerformanceLabIssue("schemaVersion", "UNSUPPORTED_SCHEMA_VERSION"))
        }
        if (payload.marks.isEmpty()) add(PerformanceLabIssue("marks", "AT_LEAST_ONE_MARK_REQUIRED"))

        val duplicateMarkIds = payload.marks.groupingBy { it.id }.eachCount()
            .filterValues { it > 1 }.keys
        payload.marks.forEachIndexed { index, mark ->
            validateMark(mark, "marks[$index]", this)
            if (mark.id in duplicateMarkIds) add(PerformanceLabIssue("marks[$index].id", "DUPLICATE"))
        }

        val marksById = payload.marks.associateBy { it.id }
        payload.marks.forEachIndexed { index, mark ->
            if (mark.metric != PerformanceLabMetric.RSI) return@forEachIndexed
            if (mark.derivedFromMarkIds.size != 2 || mark.derivedFromMarkIds.distinct().size != 2) {
                add(PerformanceLabIssue("marks[$index].derivedFromMarkIds", "RSI_REQUIRES_HEIGHT_AND_CONTACT_MARKS"))
                return@forEachIndexed
            }
            val height = marksById[mark.derivedFromMarkIds[0]]
            val contact = marksById[mark.derivedFromMarkIds[1]]
            if (height == null || contact == null) {
                add(PerformanceLabIssue("marks[$index].derivedFromMarkIds", "SOURCE_MARK_NOT_FOUND"))
                return@forEachIndexed
            }
            val expected = calculateRsi(height, contact, mark.id)
            if (expected !is PerformanceLabOperation.Success || expected.value != mark) {
                add(PerformanceLabIssue("marks[$index]", "RSI_DERIVATION_MISMATCH"))
            }
        }

        val duplicateCalculationIds = payload.velocityLossResults.groupingBy { it.calculationId }.eachCount()
            .filterValues { it > 1 }.keys
        payload.velocityLossResults.forEachIndexed { index, stored ->
            if (stored.calculationId.isBlank()) {
                add(PerformanceLabIssue("velocityLossResults[$index].calculationId", "REQUIRED"))
            }
            if (stored.calculationId in duplicateCalculationIds) {
                add(PerformanceLabIssue("velocityLossResults[$index].calculationId", "DUPLICATE"))
            }
            val calculated = calculateVelocityLoss(
                marks = payload.marks,
                setId = stored.setId,
                reference = stored.reference,
                calculationId = stored.calculationId,
            )
            if (calculated !is PerformanceLabOperation.Success || calculated.value != stored) {
                add(PerformanceLabIssue("velocityLossResults[$index]", "VELOCITY_LOSS_DERIVATION_MISMATCH"))
            }
        }
    }

    private fun validateMark(
        mark: PerformanceLabMark,
        path: String,
        issues: MutableList<PerformanceLabIssue>,
    ) {
        fun issue(field: String, code: String) = issues.add(PerformanceLabIssue("$path.$field", code))

        if (mark.id.isBlank()) issue("id", "REQUIRED")
        if (mark.protocol.id.isBlank()) issue("protocol.id", "REQUIRED")
        if (mark.protocol.version <= 0) issue("protocol.version", "POSITIVE_REQUIRED")
        if (mark.protocol.label.isBlank()) issue("protocol.label", "REQUIRED")
        if (mark.dimension.isBlank()) issue("dimension", "REQUIRED")
        if (mark.context.instrument.isBlank()) issue("context.instrument", "REQUIRED")
        if (mark.context.surface.isBlank()) issue("context.surface", "REQUIRED")
        if (mark.context.method.isBlank()) issue("context.method", "REQUIRED")
        mark.context.conditions.forEach { (name, value) ->
            if (name.isBlank() || value.isBlank()) issue("context.conditions", "EMPTY_KEY_OR_VALUE")
        }
        if (mark.capturedAtEpochMillis != null && mark.capturedAtEpochMillis < 0) {
            issue("capturedAtEpochMillis", "NON_NEGATIVE_REQUIRED")
        }
        if (mark.unit !in unitsFor(mark.metric)) issue("unit", "UNIT_MISMATCH_FOR_METRIC")

        val value = mark.value
        if (value != null && (!value.isFinite() || value <= 0.0)) issue("value", "POSITIVE_FINITE_OR_NULL_REQUIRED")
        if (mark.status == PerformanceLabTrialStatus.VALID && value == null) issue("value", "VALID_VALUE_REQUIRED")
        if (mark.status == PerformanceLabTrialStatus.INVALIDATED && mark.reason.isNullOrBlank()) {
            issue("reason", "INVALIDATION_REASON_REQUIRED")
        }

        if (mark.metric == PerformanceLabMetric.REP_VELOCITY) {
            if (mark.setId.isNullOrBlank()) issue("setId", "REQUIRED_FOR_REPETITION_VELOCITY")
            if (mark.repetitionIndex == null || mark.repetitionIndex <= 0) {
                issue("repetitionIndex", "POSITIVE_REQUIRED_FOR_REPETITION_VELOCITY")
            }
            if (mark.status == PerformanceLabTrialStatus.VALID &&
                mark.provenance != PerformanceLabProvenance.INSTRUMENT_MEASURED
            ) {
                issue("provenance", "VELOCITY_REQUIRES_INSTRUMENT_MEASUREMENT")
            }
        } else {
            if (mark.setId != null) issue("setId", "ONLY_FOR_REPETITION_VELOCITY")
            if (mark.repetitionIndex != null) issue("repetitionIndex", "ONLY_FOR_REPETITION_VELOCITY")
        }

        if (mark.metric == PerformanceLabMetric.RSI) {
            if (mark.provenance != PerformanceLabProvenance.DERIVED) issue("provenance", "RSI_MUST_BE_DERIVED")
            if (mark.status != PerformanceLabTrialStatus.VALID) issue("status", "DERIVED_RSI_MUST_BE_VALID")
            if (mark.formulaId != PERFORMANCE_LAB_RSI_FORMULA_ID) issue("formulaId", "RSI_FORMULA_REQUIRED")
            if (mark.eventId.isNullOrBlank()) issue("eventId", "RSI_REQUIRES_SHARED_EVENT_ID")
            if (mark.derivedFromMarkIds.size != 2) issue("derivedFromMarkIds", "RSI_REQUIRES_TWO_SOURCE_MARKS")
        } else {
            if (mark.provenance == PerformanceLabProvenance.DERIVED) issue("provenance", "DERIVED_SOURCE_ONLY_FOR_RSI")
            if (mark.formulaId != null) issue("formulaId", "ONLY_FOR_DERIVED_RESULT")
            if (mark.derivedFromMarkIds.isNotEmpty()) issue("derivedFromMarkIds", "ONLY_FOR_DERIVED_RESULT")
        }
    }

    private fun unitsFor(metric: PerformanceLabMetric): Set<PerformanceLabUnit> = when (metric) {
        PerformanceLabMetric.MAX_STRENGTH -> setOf(PerformanceLabUnit.KILOGRAM, PerformanceLabUnit.POUND)
        PerformanceLabMetric.SPRINT_TIME,
        PerformanceLabMetric.SPLIT_TIME,
        PerformanceLabMetric.FLIGHT_TIME,
        PerformanceLabMetric.CONTACT_TIME -> setOf(PerformanceLabUnit.SECOND, PerformanceLabUnit.MILLISECOND)
        PerformanceLabMetric.JUMP_HEIGHT,
        PerformanceLabMetric.JUMP_DISTANCE -> setOf(PerformanceLabUnit.METER, PerformanceLabUnit.CENTIMETER)
        PerformanceLabMetric.REP_VELOCITY,
        PerformanceLabMetric.RSI -> setOf(PerformanceLabUnit.METER_PER_SECOND)
    }

    /**
     * Groups only independently valid marks. All duplicate identities are excluded rather than
     * selecting an arbitrary copy; derived RSI also needs its two unique validated source marks.
     */
    fun summarizeComparable(marks: List<PerformanceLabMark>): List<PerformanceLabComparableSummary> {
        val duplicateIds = marks.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        val validMarks = marks.filter { mark ->
            if (mark.id in duplicateIds || mark.status != PerformanceLabTrialStatus.VALID) false
            else mutableListOf<PerformanceLabIssue>().also { validateMark(mark, "mark", it) }.isEmpty()
        }
        val byId = validMarks.associateBy { it.id }
        return validMarks.asSequence()
            .filter { mark ->
                if (mark.metric != PerformanceLabMetric.RSI) true else {
                    val height = mark.derivedFromMarkIds.getOrNull(0)?.let(byId::get)
                    val contact = mark.derivedFromMarkIds.getOrNull(1)?.let(byId::get)
                    if (height == null || contact == null) false else {
                        val calculated = calculateRsi(height, contact, mark.id)
                        calculated is PerformanceLabOperation.Success && calculated.value == mark
                    }
                }
            }
            .groupBy(::comparisonKey)
            .map { (key, group) -> summarize(key, group) }
            .sortedWith(compareBy<PerformanceLabComparableSummary> { it.protocolId }.thenBy { it.dimension }
                .thenBy { it.metric.name }.thenBy { it.context.instrument }.thenBy { it.context.surface })
    }

    private fun comparisonKey(mark: PerformanceLabMark) = ComparableKey(
        protocolId = mark.protocol.id,
        protocolVersion = mark.protocol.version,
        metric = mark.metric,
        dimension = mark.dimension,
        unit = mark.unit,
        provenance = mark.provenance,
        context = mark.context,
        formulaId = mark.formulaId,
    )

    private fun summarize(key: ComparableKey, marks: List<PerformanceLabMark>): PerformanceLabComparableSummary {
        val sorted = marks.sortedWith(compareBy<PerformanceLabMark> { it.value!! }.thenBy { it.id })
        val values = sorted.map { it.value!! }
        val middle = values.size / 2
        val median = if (values.size % 2 == 1) values[middle]
            else values[middle - 1] + (values[middle] - values[middle - 1]) / 2.0
        val highestIsBest = when (key.metric) {
            PerformanceLabMetric.MAX_STRENGTH,
            PerformanceLabMetric.JUMP_HEIGHT,
            PerformanceLabMetric.JUMP_DISTANCE,
            PerformanceLabMetric.REP_VELOCITY,
            PerformanceLabMetric.RSI -> true
            PerformanceLabMetric.SPRINT_TIME,
            PerformanceLabMetric.SPLIT_TIME -> false
            PerformanceLabMetric.FLIGHT_TIME,
            PerformanceLabMetric.CONTACT_TIME -> null
        }
        val best = when (highestIsBest) {
            true -> sorted.last()
            false -> sorted.first()
            null -> null
        }
        val min = values.first()
        val maxValue = values.last()
        return PerformanceLabComparableSummary(
            protocolId = key.protocolId,
            protocolVersion = key.protocolVersion,
            metric = key.metric,
            dimension = key.dimension,
            unit = key.unit,
            provenance = key.provenance,
            context = key.context,
            sampleCount = values.size,
            minimumValue = min,
            maximumValue = maxValue,
            medianValue = median,
            variationRange = if (values.size >= 2) maxValue - min else null,
            bestValue = best?.value,
            bestMarkId = best?.id,
        )
    }

    /**
     * Computes conventional RSI from instrument-measured jump height and contact time from the
     * same event. Height must already have been measured; flight time is deliberately not converted
     * to height here. Formula reference: https://pmc.ncbi.nlm.nih.gov/articles/PMC6680983/.
     */
    fun calculateRsi(
        jumpHeight: PerformanceLabMark,
        contactTime: PerformanceLabMark,
        derivedMarkId: String,
    ): PerformanceLabOperation<PerformanceLabMark> {
        val sourceIssues = mutableListOf<PerformanceLabIssue>()
        validateMark(jumpHeight, "jumpHeight", sourceIssues)
        validateMark(contactTime, "contactTime", sourceIssues)
        val issues = buildList {
            addAll(sourceIssues)
            if (derivedMarkId.isBlank()) add(PerformanceLabIssue("id", "REQUIRED"))
            if (derivedMarkId == jumpHeight.id || derivedMarkId == contactTime.id) {
                add(PerformanceLabIssue("id", "DISTINCT_FROM_SOURCE_MARK_IDS_REQUIRED"))
            }
            if (jumpHeight.id == contactTime.id) add(PerformanceLabIssue("sources", "DISTINCT_SOURCE_MARKS_REQUIRED"))
            if (jumpHeight.metric != PerformanceLabMetric.JUMP_HEIGHT) add(PerformanceLabIssue("jumpHeight.metric", "JUMP_HEIGHT_REQUIRED"))
            if (contactTime.metric != PerformanceLabMetric.CONTACT_TIME) add(PerformanceLabIssue("contactTime.metric", "CONTACT_TIME_REQUIRED"))
            if (jumpHeight.unit !in setOf(PerformanceLabUnit.METER, PerformanceLabUnit.CENTIMETER)) {
                add(PerformanceLabIssue("jumpHeight.unit", "LENGTH_UNIT_REQUIRED"))
            }
            if (contactTime.unit !in setOf(PerformanceLabUnit.SECOND, PerformanceLabUnit.MILLISECOND)) {
                add(PerformanceLabIssue("contactTime.unit", "TIME_UNIT_REQUIRED"))
            }
            if (jumpHeight.provenance != PerformanceLabProvenance.INSTRUMENT_MEASURED ||
                contactTime.provenance != PerformanceLabProvenance.INSTRUMENT_MEASURED
            ) add(PerformanceLabIssue("sources.provenance", "INSTRUMENT_MEASUREMENTS_REQUIRED"))
            if (jumpHeight.status != PerformanceLabTrialStatus.VALID || contactTime.status != PerformanceLabTrialStatus.VALID) {
                add(PerformanceLabIssue("sources.status", "VALID_SOURCE_MARKS_REQUIRED"))
            }
            if (jumpHeight.protocol != contactTime.protocol) add(PerformanceLabIssue("sources.protocol", "PROTOCOL_MISMATCH"))
            if (jumpHeight.context != contactTime.context) add(PerformanceLabIssue("sources.context", "CONTEXT_MISMATCH"))
            if (jumpHeight.dimension != contactTime.dimension) add(PerformanceLabIssue("sources.dimension", "DIMENSION_MISMATCH"))
            if (jumpHeight.eventId.isNullOrBlank() || jumpHeight.eventId != contactTime.eventId) {
                add(PerformanceLabIssue("sources.eventId", "SHARED_EVENT_ID_REQUIRED"))
            }
            if (jumpHeight.value == null || !jumpHeight.value.isFinite() || jumpHeight.value <= 0.0) {
                add(PerformanceLabIssue("jumpHeight.value", "POSITIVE_FINITE_VALUE_REQUIRED"))
            }
            if (contactTime.value == null || !contactTime.value.isFinite() || contactTime.value <= 0.0) {
                add(PerformanceLabIssue("contactTime.value", "POSITIVE_FINITE_VALUE_REQUIRED"))
            }
        }
        if (issues.isNotEmpty()) return PerformanceLabOperation.Invalid(issues)

        val heightMeters = when (jumpHeight.unit) {
            PerformanceLabUnit.METER -> jumpHeight.value!!
            PerformanceLabUnit.CENTIMETER -> jumpHeight.value!! / 100.0
            else -> error("Validated above")
        }
        val contactSeconds = when (contactTime.unit) {
            PerformanceLabUnit.SECOND -> contactTime.value!!
            PerformanceLabUnit.MILLISECOND -> contactTime.value!! / 1000.0
            else -> error("Validated above")
        }
        val rsi = heightMeters / contactSeconds
        if (!rsi.isFinite() || rsi <= 0.0) {
            return PerformanceLabOperation.Invalid(listOf(PerformanceLabIssue("value", "RESULT_NOT_FINITE")))
        }
        return PerformanceLabOperation.Success(
            PerformanceLabMark(
                id = derivedMarkId,
                protocol = jumpHeight.protocol,
                metric = PerformanceLabMetric.RSI,
                dimension = jumpHeight.dimension,
                unit = PerformanceLabUnit.METER_PER_SECOND,
                value = rsi,
                provenance = PerformanceLabProvenance.DERIVED,
                context = jumpHeight.context,
                status = PerformanceLabTrialStatus.VALID,
                eventId = jumpHeight.eventId,
                derivedFromMarkIds = listOf(jumpHeight.id, contactTime.id),
                formulaId = PERFORMANCE_LAB_RSI_FORMULA_ID,
                capturedAtEpochMillis = listOfNotNull(jumpHeight.capturedAtEpochMillis, contactTime.capturedAtEpochMillis).maxOrNull(),
            ),
        )
    }

    /**
     * Reports velocity change from an explicit valid-repetition reference to the last valid rep.
     * Research reports both first-rep and fastest-rep references; callers choose which one is meant.
     * See https://pmc.ncbi.nlm.nih.gov/articles/PMC11812168/ (fastest and last repetition).
     */
    fun calculateVelocityLoss(
        marks: List<PerformanceLabMark>,
        setId: String,
        reference: PerformanceLabVelocityReference,
        calculationId: String,
    ): PerformanceLabOperation<PerformanceLabVelocityLossResult> {
        val issues = mutableListOf<PerformanceLabIssue>()
        if (setId.isBlank()) issues += PerformanceLabIssue("setId", "REQUIRED")
        if (calculationId.isBlank()) issues += PerformanceLabIssue("calculationId", "REQUIRED")
        val series = marks.filter { it.metric == PerformanceLabMetric.REP_VELOCITY && it.setId == setId }
        if (series.isEmpty()) issues += PerformanceLabIssue("marks", "VELOCITY_SET_NOT_FOUND")
        val duplicateMarkIds = marks.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        if (series.any { it.id in duplicateMarkIds }) issues += PerformanceLabIssue("marks.id", "DUPLICATE_MARK_ID")
        series.forEachIndexed { index, mark -> validateMark(mark, "marks[$index]", issues) }
        val repIndices = series.mapNotNull { it.repetitionIndex }
        if (repIndices.distinct().size != repIndices.size) issues += PerformanceLabIssue("marks.repetitionIndex", "DUPLICATE_REPETITION_INDEX")

        val valid = series.filter { it.status == PerformanceLabTrialStatus.VALID }
        if (valid.size < 2) issues += PerformanceLabIssue("marks", "TWO_VALID_REPETITIONS_REQUIRED")
        if (valid.any { it.provenance != PerformanceLabProvenance.INSTRUMENT_MEASURED }) {
            issues += PerformanceLabIssue("marks.provenance", "VELOCITY_REQUIRES_INSTRUMENT_MEASUREMENT")
        }
        if (valid.map(::comparisonKey).distinct().size > 1) {
            issues += PerformanceLabIssue("marks", "INCOMPARABLE_VELOCITY_REPETITIONS")
        }
        if (valid.any { it.value == null || !it.value.isFinite() || it.value <= 0.0 }) {
            issues += PerformanceLabIssue("marks.value", "VALID_VELOCITY_REQUIRED")
        }
        if (issues.isNotEmpty()) return PerformanceLabOperation.Invalid(issues)

        val byRep = valid.sortedBy { it.repetitionIndex!! }
        val referenceMark = when (reference) {
            PerformanceLabVelocityReference.FIRST_VALID_REPETITION -> byRep.first()
            PerformanceLabVelocityReference.FASTEST_VALID_REPETITION -> byRep
                .sortedWith(compareByDescending<PerformanceLabMark> { it.value!! }.thenBy { it.repetitionIndex })
                .first()
        }
        val terminalMark = byRep.last()
        val referenceVelocity = referenceMark.value!!
        val terminalVelocity = terminalMark.value!!
        val lossPercent = (referenceVelocity - terminalVelocity) / referenceVelocity * 100.0
        if (!lossPercent.isFinite()) {
            return PerformanceLabOperation.Invalid(listOf(PerformanceLabIssue("velocityLossPercent", "RESULT_NOT_FINITE")))
        }
        return PerformanceLabOperation.Success(
            PerformanceLabVelocityLossResult(
                calculationId = calculationId,
                setId = setId,
                reference = reference,
                formulaId = PERFORMANCE_LAB_VELOCITY_LOSS_FORMULA_ID,
                referenceVelocityMetersPerSecond = referenceVelocity,
                lastValidVelocityMetersPerSecond = terminalVelocity,
                velocityLossPercent = lossPercent,
                validRepetitionCount = valid.size,
                excludedRepetitionCount = series.size - valid.size,
                sourceMarkIds = byRep.map { it.id },
            ),
        )
    }
}
