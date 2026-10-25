package com.example.kpkn.domain.multisport.functional

import com.example.kpkn.domain.multisport.SportId
import com.example.kpkn.domain.multisport.tracking.SportDocumentEngine
import com.example.kpkn.domain.multisport.tracking.SportDocumentIssue
import com.example.kpkn.domain.multisport.tracking.SportDocumentPurpose
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

/** Manual capture and structural validation for CrossFit-style formats and HYROX segment logs. */
class FunctionalSportEngine(private val sport: SportId) : SportDocumentEngine {
    init {
        require(sport == SportId.CROSSFIT || sport == SportId.HYROX) {
            "FunctionalSportEngine is registered only for CROSSFIT or HYROX"
        }
    }

    override val id: String = "functional.manual.${sport.name.lowercase()}"
    override val version: Int = 1
    override val sports: Set<SportId> = setOf(sport)
    override val purposes: Set<SportDocumentPurpose> = setOf(SportDocumentPurpose.PLAN, SportDocumentPurpose.RESULT)

    private val json = Json { ignoreUnknownKeys = false }

    companion object {
        val crossfit = FunctionalSportEngine(SportId.CROSSFIT)
        val hyrox = FunctionalSportEngine(SportId.HYROX)
        val registeredEngines: List<FunctionalSportEngine> = listOf(crossfit, hyrox)
    }

    private val formats: Set<FunctionalWorkoutFormat> = when (sport) {
        SportId.CROSSFIT -> setOf(
            FunctionalWorkoutFormat.FOR_TIME,
            FunctionalWorkoutFormat.AMRAP,
            FunctionalWorkoutFormat.EMOM,
            FunctionalWorkoutFormat.INTERVALS,
        )
        SportId.HYROX -> setOf(FunctionalWorkoutFormat.HYROX)
        else -> emptySet()
    }

    sealed interface AmrapScoreResult {
        data class Available(val score: FunctionalAmrapScore) : AmrapScoreResult
        data class Invalid(val issues: List<FunctionalSportIssue>) : AmrapScoreResult
        object Unavailable : AmrapScoreResult
    }

    override fun validate(payload: JsonObject, purpose: SportDocumentPurpose): List<SportDocumentIssue> {
        if (purpose !in purposes) return listOf(SportDocumentIssue("purpose", "UNSUPPORTED_PURPOSE"))
        val decoded = try {
            json.decodeFromJsonElement<FunctionalSportPayload>(payload)
        } catch (_: Exception) {
            return listOf(SportDocumentIssue("payload", "PAYLOAD_SCHEMA_INVALID"))
        }
        return validatePayload(decoded, purpose).map { SportDocumentIssue(it.path, it.code) }
    }

    override fun plannedDurationSeconds(payload: JsonObject): Long? {
        val decoded = try {
            json.decodeFromJsonElement<FunctionalSportPayload>(payload)
        } catch (_: Exception) {
            return null
        }
        if (validatePayload(decoded, SportDocumentPurpose.PLAN).isNotEmpty()) return null
        return decoded.definition.declaredDurationSeconds?.takeIf { it > 0L }
    }

    fun validatePayload(
        payload: FunctionalSportPayload,
        purpose: SportDocumentPurpose,
    ): List<FunctionalSportIssue> = buildList {
        if (payload.schemaVersion != FUNCTIONAL_SPORT_PAYLOAD_VERSION) {
            add(FunctionalSportIssue("schemaVersion", "UNSUPPORTED_SCHEMA_VERSION"))
        }
        validateDefinition(payload.definition, this)
        when (purpose) {
            SportDocumentPurpose.PLAN -> if (payload.result != null) {
                add(FunctionalSportIssue("result", "PLAN_MUST_NOT_CONTAIN_RESULT"))
            }
            SportDocumentPurpose.RESULT -> {
                val result = payload.result
                if (result == null) add(FunctionalSportIssue("result", "RESULT_REQUIRED"))
                else validateResult(payload.definition, result, this)
            }
            SportDocumentPurpose.OBSERVATION -> add(FunctionalSportIssue("purpose", "UNSUPPORTED_PURPOSE"))
        }
    }

    private fun validateDefinition(
        definition: FunctionalWorkoutDefinition,
        issues: MutableList<FunctionalSportIssue>,
    ) {
        fun issue(path: String, code: String) = issues.add(FunctionalSportIssue("definition.$path", code))

        if (definition.sportId != sport) issue("sportId", "ENGINE_SPORT_MISMATCH")
        if (definition.format !in formats) issue("format", "UNSUPPORTED_FORMAT_FOR_SPORT")
        if (definition.workoutId.isBlank()) issue("workoutId", "REQUIRED")
        if (definition.workoutRevision.isBlank()) issue("workoutRevision", "REQUIRED")
        if (definition.title.isBlank()) issue("title", "REQUIRED")
        if (definition.declaredDurationSeconds != null && definition.declaredDurationSeconds <= 0L) {
            issue("declaredDurationSeconds", "POSITIVE_REQUIRED")
        }
        if (definition.scaling.id.isBlank()) issue("scaling.id", "REQUIRED")
        if (definition.scaling.label.isBlank()) issue("scaling.label", "REQUIRED")
        definition.scaling.parameters.forEach { (key, value) ->
            if (key.isBlank() || value.isBlank()) issue("scaling.parameters", "EMPTY_KEY_OR_VALUE")
        }
        definition.division?.let { division ->
            if (division.id.isBlank()) issue("division.id", "REQUIRED")
            if (division.label.isBlank()) issue("division.label", "REQUIRED")
            if (division.sexCategory != null && division.sexCategory.isBlank()) issue("division.sexCategory", "NON_BLANK_OR_NULL_REQUIRED")
        }
        definition.ruleset?.let { ruleset ->
            if (ruleset.id.isBlank()) issue("ruleset.id", "REQUIRED")
            if (ruleset.version <= 0) issue("ruleset.version", "POSITIVE_REQUIRED")
            if (ruleset.label.isBlank()) issue("ruleset.label", "REQUIRED")
            if (!ruleset.custom && ruleset.sourceReference.isNullOrBlank()) {
                issue("ruleset.sourceReference", "REFERENCE_REQUIRED_FOR_EXTERNAL_RULESET")
            }
            if (ruleset.custom && !ruleset.sourceReference.isNullOrBlank()) {
                issue("ruleset.sourceReference", "CUSTOM_RULESET_MUST_BE_LABELLED_CUSTOM")
            }
        }
        if (definition.timeCapMillis != null && definition.timeCapMillis <= 0L) {
            issue("timeCapMillis", "POSITIVE_REQUIRED")
        }

        when (definition.format) {
            FunctionalWorkoutFormat.FOR_TIME -> {
                validateTargets(definition.targetSequence, "targetSequence", issues)
                if (definition.targetSequence.isEmpty()) issue("targetSequence", "AT_LEAST_ONE_TARGET_REQUIRED")
                if (definition.emomWindows.isNotEmpty()) issue("emomWindows", "FORMAT_FIELD_NOT_ALLOWED")
                if (definition.intervalPlan.isNotEmpty()) issue("intervalPlan", "FORMAT_FIELD_NOT_ALLOWED")
                if (definition.hyroxSegments.isNotEmpty()) issue("hyroxSegments", "FORMAT_FIELD_NOT_ALLOWED")
            }
            FunctionalWorkoutFormat.AMRAP -> {
                validateTargets(definition.targetSequence, "targetSequence", issues)
                if (definition.targetSequence.isEmpty()) issue("targetSequence", "AT_LEAST_ONE_TARGET_REQUIRED")
                if (definition.timeCapMillis == null || definition.timeCapMillis <= 0L) {
                    issue("timeCapMillis", "AMRAP_REQUIRES_EXPLICIT_POSITIVE_CAP")
                }
                if (definition.emomWindows.isNotEmpty()) issue("emomWindows", "FORMAT_FIELD_NOT_ALLOWED")
                if (definition.intervalPlan.isNotEmpty()) issue("intervalPlan", "FORMAT_FIELD_NOT_ALLOWED")
                if (definition.hyroxSegments.isNotEmpty()) issue("hyroxSegments", "FORMAT_FIELD_NOT_ALLOWED")
            }
            FunctionalWorkoutFormat.EMOM -> {
                if (definition.targetSequence.isNotEmpty()) issue("targetSequence", "FORMAT_FIELD_NOT_ALLOWED")
                if (definition.timeCapMillis != null) issue("timeCapMillis", "FORMAT_FIELD_NOT_ALLOWED")
                if (definition.intervalPlan.isNotEmpty()) issue("intervalPlan", "FORMAT_FIELD_NOT_ALLOWED")
                if (definition.hyroxSegments.isNotEmpty()) issue("hyroxSegments", "FORMAT_FIELD_NOT_ALLOWED")
                validateEmomPlans(definition.emomWindows, issues)
            }
            FunctionalWorkoutFormat.INTERVALS -> {
                if (definition.targetSequence.isNotEmpty()) issue("targetSequence", "FORMAT_FIELD_NOT_ALLOWED")
                if (definition.timeCapMillis != null) issue("timeCapMillis", "FORMAT_FIELD_NOT_ALLOWED")
                if (definition.emomWindows.isNotEmpty()) issue("emomWindows", "FORMAT_FIELD_NOT_ALLOWED")
                if (definition.hyroxSegments.isNotEmpty()) issue("hyroxSegments", "FORMAT_FIELD_NOT_ALLOWED")
                validateIntervalPlans(definition.intervalPlan, issues)
            }
            FunctionalWorkoutFormat.HYROX -> {
                if (definition.targetSequence.isNotEmpty()) issue("targetSequence", "FORMAT_FIELD_NOT_ALLOWED")
                if (definition.timeCapMillis != null) issue("timeCapMillis", "FORMAT_FIELD_NOT_ALLOWED")
                if (definition.emomWindows.isNotEmpty()) issue("emomWindows", "FORMAT_FIELD_NOT_ALLOWED")
                if (definition.intervalPlan.isNotEmpty()) issue("intervalPlan", "FORMAT_FIELD_NOT_ALLOWED")
                if (definition.division == null) issue("division", "HYROX_DIVISION_SNAPSHOT_REQUIRED")
                if (definition.ruleset == null) issue("ruleset", "HYROX_RULESET_SNAPSHOT_REQUIRED")
                validateHyroxSegments(definition.hyroxSegments, issues)
            }
        }
    }

    private fun validateTargets(
        targets: List<FunctionalMovementTarget>,
        path: String,
        issues: MutableList<FunctionalSportIssue>,
    ) {
        val duplicateIds = targets.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        targets.forEachIndexed { index, target ->
            val targetPath = "definition.$path[$index]"
            if (target.id.isBlank()) issues += FunctionalSportIssue("$targetPath.id", "REQUIRED")
            if (target.id in duplicateIds) issues += FunctionalSportIssue("$targetPath.id", "DUPLICATE")
            if (target.label.isBlank()) issues += FunctionalSportIssue("$targetPath.label", "REQUIRED")
            validateQuantity(target.quantity, "$targetPath.quantity", issues, allowZero = false)
            target.load?.let { validateLoad(it, "$targetPath.load", issues) }
        }
    }

    private fun validateEmomPlans(
        windows: List<FunctionalEmomWindowPlan>,
        issues: MutableList<FunctionalSportIssue>,
    ) {
        if (windows.isEmpty()) issues += FunctionalSportIssue("definition.emomWindows", "AT_LEAST_ONE_WINDOW_REQUIRED")
        val duplicateIds = windows.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        var previousEnd: Long? = null
        windows.forEachIndexed { index, window ->
            val path = "definition.emomWindows[$index]"
            val previousWindowEnd = previousEnd
            if (window.id.isBlank()) issues += FunctionalSportIssue("$path.id", "REQUIRED")
            if (window.id in duplicateIds) issues += FunctionalSportIssue("$path.id", "DUPLICATE")
            if (window.startsAtMillis < 0L) issues += FunctionalSportIssue("$path.startsAtMillis", "NON_NEGATIVE_REQUIRED")
            if (window.durationMillis <= 0L) issues += FunctionalSportIssue("$path.durationMillis", "POSITIVE_REQUIRED")
            if (index > 0 && previousWindowEnd != null && window.startsAtMillis < previousWindowEnd) {
                issues += FunctionalSportIssue("$path.startsAtMillis", "WINDOWS_OVERLAP_OR_OUT_OF_ORDER")
            }
            previousEnd = if (window.startsAtMillis >= 0L && window.durationMillis > 0L) {
                try {
                    Math.addExact(window.startsAtMillis, window.durationMillis)
                } catch (_: ArithmeticException) {
                    issues += FunctionalSportIssue("$path", "WINDOW_END_OVERFLOW")
                    null
                }
            } else {
                null
            }
            validateTargets(window.targets, "emomWindows[$index].targets", issues)
            if (window.targets.isEmpty()) issues += FunctionalSportIssue("$path.targets", "AT_LEAST_ONE_TARGET_REQUIRED")
        }
    }

    private fun validateIntervalPlans(
        intervals: List<FunctionalIntervalPlan>,
        issues: MutableList<FunctionalSportIssue>,
    ) {
        if (intervals.isEmpty()) issues += FunctionalSportIssue("definition.intervalPlan", "AT_LEAST_ONE_INTERVAL_REQUIRED")
        val duplicateIds = intervals.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        intervals.forEachIndexed { index, interval ->
            val path = "definition.intervalPlan[$index]"
            if (interval.id.isBlank()) issues += FunctionalSportIssue("$path.id", "REQUIRED")
            if (interval.id in duplicateIds) issues += FunctionalSportIssue("$path.id", "DUPLICATE")
            if (interval.workDurationMillis <= 0L) issues += FunctionalSportIssue("$path.workDurationMillis", "POSITIVE_REQUIRED")
            if (interval.restDurationMillis < 0L) issues += FunctionalSportIssue("$path.restDurationMillis", "NON_NEGATIVE_REQUIRED")
            validateTargets(interval.targets, "intervalPlan[$index].targets", issues)
        }
    }

    private fun validateHyroxSegments(
        segments: List<FunctionalHyroxSegment>,
        issues: MutableList<FunctionalSportIssue>,
    ) {
        if (segments.isEmpty()) issues += FunctionalSportIssue("definition.hyroxSegments", "AT_LEAST_ONE_SEGMENT_REQUIRED")
        val duplicateIds = segments.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        segments.forEachIndexed { index, segment ->
            val path = "definition.hyroxSegments[$index]"
            if (segment.id.isBlank()) issues += FunctionalSportIssue("$path.id", "REQUIRED")
            if (segment.id in duplicateIds) issues += FunctionalSportIssue("$path.id", "DUPLICATE")
            if (segment.label.isBlank()) issues += FunctionalSportIssue("$path.label", "REQUIRED")
            when (segment.kind) {
                FunctionalHyroxSegmentKind.RUN -> {
                    if (segment.target == null) issues += FunctionalSportIssue("$path.target", "RUN_DISTANCE_REQUIRED")
                    else {
                        validateQuantity(segment.target, "$path.target", issues, allowZero = false)
                        if (segment.target.unit !in setOf(FunctionalQuantityUnit.METER, FunctionalQuantityUnit.KILOMETER)) {
                            issues += FunctionalSportIssue("$path.target.unit", "RUN_DISTANCE_UNIT_REQUIRED")
                        }
                    }
                    if (segment.prescribedLoad != null) validateLoad(segment.prescribedLoad, "$path.prescribedLoad", issues)
                }
                FunctionalHyroxSegmentKind.STATION -> {
                    if (segment.target == null) issues += FunctionalSportIssue("$path.target", "STATION_TARGET_WITH_UNIT_REQUIRED")
                    else validateQuantity(segment.target, "$path.target", issues, allowZero = false)
                    segment.prescribedLoad?.let { validateLoad(it, "$path.prescribedLoad", issues) }
                }
                FunctionalHyroxSegmentKind.TRANSITION -> {
                    if (segment.target != null) issues += FunctionalSportIssue("$path.target", "TRANSITION_HAS_NO_TARGET_QUANTITY")
                    if (segment.prescribedLoad != null) issues += FunctionalSportIssue("$path.prescribedLoad", "TRANSITION_HAS_NO_LOAD")
                }
            }
        }
    }

    private fun validateResult(
        definition: FunctionalWorkoutDefinition,
        result: FunctionalWorkoutResult,
        issues: MutableList<FunctionalSportIssue>,
    ) {
        val outcomes = listOf(result.forTime, result.amrap, result.emom, result.intervals, result.hyrox)
        val presentCount = outcomes.count { it != null }
        if (presentCount != 1) issues += FunctionalSportIssue("result", "EXACTLY_ONE_FORMAT_OUTCOME_REQUIRED")

        when (definition.format) {
            FunctionalWorkoutFormat.FOR_TIME -> {
                if (result.forTime == null) issues += FunctionalSportIssue("result.forTime", "FORMAT_OUTCOME_REQUIRED")
                else validateForTime(definition, result.forTime, issues)
            }
            FunctionalWorkoutFormat.AMRAP -> {
                if (result.amrap == null) issues += FunctionalSportIssue("result.amrap", "FORMAT_OUTCOME_REQUIRED")
                else validateAmrap(definition, result.amrap, issues)
            }
            FunctionalWorkoutFormat.EMOM -> {
                if (result.emom == null) issues += FunctionalSportIssue("result.emom", "FORMAT_OUTCOME_REQUIRED")
                else validateEmom(definition, result.emom, issues)
            }
            FunctionalWorkoutFormat.INTERVALS -> {
                if (result.intervals == null) issues += FunctionalSportIssue("result.intervals", "FORMAT_OUTCOME_REQUIRED")
                else validateIntervals(definition, result.intervals, issues)
            }
            FunctionalWorkoutFormat.HYROX -> {
                if (result.hyrox == null) issues += FunctionalSportIssue("result.hyrox", "FORMAT_OUTCOME_REQUIRED")
                else validateHyrox(definition, result.hyrox, issues)
            }
        }
        validatePenalties(definition, result.penalties, issues)
    }

    private fun validateForTime(
        definition: FunctionalWorkoutDefinition,
        outcome: FunctionalForTimeOutcome,
        issues: MutableList<FunctionalSportIssue>,
    ) {
        val states = listOf(outcome.finish, outcome.cappedPartial, outcome.stoppedPartial).count { it != null }
        if (states != 1) issues += FunctionalSportIssue("result.forTime", "EXACTLY_ONE_COMPLETION_STATE_REQUIRED")
        outcome.finish?.let {
            if (it.finishTimeMillis <= 0L) issues += FunctionalSportIssue("result.forTime.finish.finishTimeMillis", "POSITIVE_REQUIRED")
            if (definition.timeCapMillis != null && it.finishTimeMillis > definition.timeCapMillis) {
                issues += FunctionalSportIssue("result.forTime.finish.finishTimeMillis", "FINISH_EXCEEDS_TIME_CAP")
            }
        }
        outcome.cappedPartial?.let { partial ->
            val cap = definition.timeCapMillis
            if (cap == null) issues += FunctionalSportIssue("result.forTime.cappedPartial", "TIME_CAP_NOT_DECLARED")
            if (partial.elapsedMillis <= 0L) issues += FunctionalSportIssue("result.forTime.cappedPartial.elapsedMillis", "POSITIVE_REQUIRED")
            if (cap != null && partial.elapsedMillis < cap) {
                issues += FunctionalSportIssue("result.forTime.cappedPartial.elapsedMillis", "CAP_NOT_REACHED")
            }
            if (cap != null && partial.elapsedMillis > cap) {
                issues += FunctionalSportIssue("result.forTime.cappedPartial.elapsedMillis", "ELAPSED_EXCEEDS_TIME_CAP")
            }
            partial.progress?.let {
                validateTargetProgress(definition.targetSequence, it, "result.forTime.cappedPartial.progress", issues)
            }
        }
        outcome.stoppedPartial?.let { partial ->
            if (partial.elapsedMillis <= 0L) issues += FunctionalSportIssue("result.forTime.stoppedPartial.elapsedMillis", "POSITIVE_REQUIRED")
            partial.progress?.let {
                validateTargetProgress(definition.targetSequence, it, "result.forTime.stoppedPartial.progress", issues)
            }
            if (definition.timeCapMillis != null && partial.elapsedMillis >= definition.timeCapMillis) {
                issues += FunctionalSportIssue("result.forTime.stoppedPartial", "CAP_REACHED_MUST_USE_CAPPED_PARTIAL")
            }
        }
    }

    private fun validateAmrap(
        definition: FunctionalWorkoutDefinition,
        outcome: FunctionalAmrapOutcome,
        issues: MutableList<FunctionalSportIssue>,
    ) {
        if (outcome.completedRounds == null) issues += FunctionalSportIssue("result.amrap.completedRounds", "VALUE_REQUIRED")
        if (outcome.completedRounds != null && outcome.completedRounds < 0) {
            issues += FunctionalSportIssue("result.amrap.completedRounds", "NON_NEGATIVE_REQUIRED")
        }
        outcome.partialRoundProgress?.let {
            validateTargetProgress(definition.targetSequence, it, "result.amrap.partialRoundProgress", issues)
        }
    }

    private fun validateTargetProgress(
        targets: List<FunctionalMovementTarget>,
        progress: List<FunctionalTargetProgress>,
        path: String,
        issues: MutableList<FunctionalSportIssue>,
    ) {
        if (progress.size > targets.size) issues += FunctionalSportIssue(path, "MORE_PROGRESS_ITEMS_THAN_TARGETS")
        var incompleteSeen = false
        progress.forEachIndexed { index, item ->
            val itemPath = "$path[$index]"
            val target = targets.getOrNull(index)
            if (target == null) return@forEachIndexed
            if (item.targetId != target.id) issues += FunctionalSportIssue("$itemPath.targetId", "PROGRESS_MUST_FOLLOW_TARGET_ORDER")
            if (item.quantity.unit != target.quantity.unit) {
                issues += FunctionalSportIssue("$itemPath.quantity.unit", "PROGRESS_UNIT_MISMATCH")
            }
            val value = item.quantity.value
            if (!value.isFinite() || value < 0.0 || value > target.quantity.value) {
                issues += FunctionalSportIssue("$itemPath.quantity.value", "PROGRESS_OUT_OF_RANGE")
            }
            if (item.quantity.unit == FunctionalQuantityUnit.REPETITIONS && value.isFinite() && value % 1.0 != 0.0) {
                issues += FunctionalSportIssue("$itemPath.quantity.value", "REPETITIONS_MUST_BE_WHOLE")
            }
            if (incompleteSeen) issues += FunctionalSportIssue(itemPath, "PROGRESS_AFTER_INCOMPLETE_TARGET")
            if (value.isFinite() && value < target.quantity.value) incompleteSeen = true
        }
        if (progress.size == targets.size && targets.isNotEmpty() &&
            progress.indices.all { index -> progress[index].quantity.value == targets[index].quantity.value }
        ) {
            issues += FunctionalSportIssue(path, "FULL_ROUND_MUST_NOT_BE_STORED_AS_PARTIAL")
        }
    }

    private fun validateEmom(
        definition: FunctionalWorkoutDefinition,
        outcome: FunctionalEmomOutcome,
        issues: MutableList<FunctionalSportIssue>,
    ) {
        if (outcome.windows.size != definition.emomWindows.size) {
            issues += FunctionalSportIssue("result.emom.windows", "WINDOW_COUNT_MISMATCH")
        }
        outcome.windows.forEachIndexed { index, result ->
            val planned = definition.emomWindows.getOrNull(index)
            if (planned == null || result.windowId != planned.id) {
                issues += FunctionalSportIssue("result.emom.windows[$index].windowId", "WINDOW_ORDER_OR_ID_MISMATCH")
            }
        }
    }

    private fun validateIntervals(
        definition: FunctionalWorkoutDefinition,
        outcome: FunctionalIntervalsOutcome,
        issues: MutableList<FunctionalSportIssue>,
    ) {
        if (outcome.intervals.size != definition.intervalPlan.size) {
            issues += FunctionalSportIssue("result.intervals.intervals", "INTERVAL_COUNT_MISMATCH")
        }
        if (outcome.intervals.none { it.workStartedAtEpochMillis != null || it.workEndedAtEpochMillis != null ||
                    it.restStartedAtEpochMillis != null || it.restEndedAtEpochMillis != null }) {
            issues += FunctionalSportIssue("result.intervals.intervals", "AT_LEAST_ONE_TIMESTAMP_REQUIRED")
        }
        var previousIntervalTimestamp: Long? = null
        outcome.intervals.forEachIndexed { index, observed ->
            val planned = definition.intervalPlan.getOrNull(index)
            val path = "result.intervals.intervals[$index]"
            if (planned == null || observed.intervalId != planned.id) {
                issues += FunctionalSportIssue("$path.intervalId", "INTERVAL_ORDER_OR_ID_MISMATCH")
            }
            val times = listOf(
                observed.workStartedAtEpochMillis,
                observed.workEndedAtEpochMillis,
                observed.restStartedAtEpochMillis,
                observed.restEndedAtEpochMillis,
            )
            times.forEachIndexed { timeIndex, time ->
                if (time != null && time < 0L) issues += FunctionalSportIssue("$path.timestamps[$timeIndex]", "NON_NEGATIVE_REQUIRED")
            }
            val workStart = observed.workStartedAtEpochMillis
            val workEnd = observed.workEndedAtEpochMillis
            val restStart = observed.restStartedAtEpochMillis
            val restEnd = observed.restEndedAtEpochMillis
            if (workEnd != null && workStart == null) issues += FunctionalSportIssue("$path.workEndedAtEpochMillis", "WORK_START_REQUIRED")
            if (restStart != null && workEnd == null) issues += FunctionalSportIssue("$path.restStartedAtEpochMillis", "WORK_END_REQUIRED")
            if (restEnd != null && restStart == null) issues += FunctionalSportIssue("$path.restEndedAtEpochMillis", "REST_START_REQUIRED")
            if (workStart != null && workEnd != null && workEnd < workStart) {
                issues += FunctionalSportIssue("$path.workEndedAtEpochMillis", "TIMESTAMP_ORDER_INVALID")
            }
            if (workEnd != null && restStart != null && restStart < workEnd) {
                issues += FunctionalSportIssue("$path.restStartedAtEpochMillis", "TIMESTAMP_ORDER_INVALID")
            }
            if (restStart != null && restEnd != null && restEnd < restStart) {
                issues += FunctionalSportIssue("$path.restEndedAtEpochMillis", "TIMESTAMP_ORDER_INVALID")
            }
            if (workStart != null && previousIntervalTimestamp != null && workStart < previousIntervalTimestamp!!) {
                issues += FunctionalSportIssue("$path.workStartedAtEpochMillis", "INTERVAL_SEQUENCE_ORDER_INVALID")
            }
            times.filterNotNull().maxOrNull()?.let { previousIntervalTimestamp = it }
        }
    }

    private fun validateHyrox(
        definition: FunctionalWorkoutDefinition,
        outcome: FunctionalHyroxOutcome,
        issues: MutableList<FunctionalSportIssue>,
    ) {
        if (outcome.segments.size != definition.hyroxSegments.size) {
            issues += FunctionalSportIssue("result.hyrox.segments", "SEGMENT_COUNT_MISMATCH")
        }
        outcome.segments.forEachIndexed { index, result ->
            val planned = definition.hyroxSegments.getOrNull(index)
            val path = "result.hyrox.segments[$index]"
            if (planned == null || result.segmentId != planned.id) {
                issues += FunctionalSportIssue("$path.segmentId", "SEGMENT_ORDER_OR_ID_MISMATCH")
            }
            if (result.splitMillis != null && result.splitMillis <= 0L) {
                issues += FunctionalSportIssue("$path.splitMillis", "POSITIVE_OR_NULL_REQUIRED")
            }
            when (result.status) {
                FunctionalHyroxSegmentStatus.COMPLETED -> if (result.splitMillis == null) {
                    issues += FunctionalSportIssue("$path.splitMillis", "COMPLETED_SEGMENT_SPLIT_REQUIRED")
                }
                FunctionalHyroxSegmentStatus.PARTIAL -> if (result.splitMillis == null && result.actualQuantity == null) {
                    issues += FunctionalSportIssue(path, "PARTIAL_RESULT_NEEDS_SPLIT_OR_QUANTITY")
                }
                FunctionalHyroxSegmentStatus.NOT_ATTEMPTED,
                FunctionalHyroxSegmentStatus.UNRECORDED -> if (result.splitMillis != null || result.actualQuantity != null || result.actualLoad != null) {
                    issues += FunctionalSportIssue(path, "NO_MEASUREMENTS_ALLOWED_FOR_UNATTEMPTED_OR_UNRECORDED")
                }
                FunctionalHyroxSegmentStatus.INVALIDATED -> if (result.reason.isNullOrBlank()) {
                    issues += FunctionalSportIssue("$path.reason", "INVALIDATION_REASON_REQUIRED")
                }
            }
            if (planned != null) {
                result.actualQuantity?.let { actual ->
                    validateQuantity(actual, "$path.actualQuantity", issues, allowZero = false)
                    if (planned.target != null && actual.unit != planned.target.unit) {
                        issues += FunctionalSportIssue("$path.actualQuantity.unit", "SEGMENT_UNIT_MISMATCH")
                    }
                }
                val target = planned.target
                val actual = result.actualQuantity
                if (target == null && actual != null && result.status in setOf(
                        FunctionalHyroxSegmentStatus.COMPLETED,
                        FunctionalHyroxSegmentStatus.PARTIAL,
                    )
                ) {
                    issues += FunctionalSportIssue("$path.actualQuantity", "SEGMENT_TARGET_NOT_DECLARED")
                }
                if (target != null && actual != null && actual.unit == target.unit &&
                    target.value.isFinite() && target.value > 0.0 && actual.value.isFinite()
                ) {
                    when (result.status) {
                        FunctionalHyroxSegmentStatus.COMPLETED -> if (actual.value < target.value) {
                            issues += FunctionalSportIssue("$path.actualQuantity", "COMPLETED_QUANTITY_BELOW_TARGET")
                        }
                        FunctionalHyroxSegmentStatus.PARTIAL -> if (actual.value >= target.value) {
                            issues += FunctionalSportIssue("$path.actualQuantity", "PARTIAL_QUANTITY_MEETS_TARGET")
                        }
                        else -> Unit
                    }
                }
                if (result.status == FunctionalHyroxSegmentStatus.COMPLETED && target != null && actual == null) {
                    issues += FunctionalSportIssue("$path.actualQuantity", "COMPLETED_SEGMENT_QUANTITY_REQUIRED")
                }
                result.actualLoad?.let { load ->
                    validateLoad(load, "$path.actualLoad", issues)
                    if (planned.prescribedLoad != null && load.unit != planned.prescribedLoad.unit) {
                        issues += FunctionalSportIssue("$path.actualLoad.unit", "LOAD_UNIT_MISMATCH")
                    }
                }
            }
        }
        val allCompleted = outcome.segments.size == definition.hyroxSegments.size &&
            outcome.segments.all { it.status == FunctionalHyroxSegmentStatus.COMPLETED }
        if (outcome.status == FunctionalHyroxRaceStatus.FINISHED && !allCompleted) {
            issues += FunctionalSportIssue("result.hyrox.status", "FINISHED_REQUIRES_ALL_SEGMENTS_COMPLETED")
        }
        if (outcome.status == FunctionalHyroxRaceStatus.PARTIAL && allCompleted) {
            issues += FunctionalSportIssue("result.hyrox.status", "PARTIAL_CANNOT_HAVE_ALL_SEGMENTS_COMPLETED")
        }
        if (outcome.status == FunctionalHyroxRaceStatus.PARTIAL && outcome.elapsedMillis == null &&
            outcome.segments.none { it.status == FunctionalHyroxSegmentStatus.COMPLETED ||
                    it.status == FunctionalHyroxSegmentStatus.PARTIAL || it.status == FunctionalHyroxSegmentStatus.INVALIDATED }
        ) {
            issues += FunctionalSportIssue("result.hyrox", "PARTIAL_RESULT_HAS_NO_OBSERVATIONS")
        }
        if (outcome.status == FunctionalHyroxRaceStatus.FINISHED && (outcome.elapsedMillis == null || outcome.elapsedMillis <= 0L)) {
            issues += FunctionalSportIssue("result.hyrox.elapsedMillis", "FINISHED_TIME_REQUIRED")
        }
        if (outcome.elapsedMillis != null && outcome.elapsedMillis <= 0L) {
            issues += FunctionalSportIssue("result.hyrox.elapsedMillis", "POSITIVE_OR_NULL_REQUIRED")
        }
        val elapsed = outcome.elapsedMillis
        if (elapsed != null && elapsed > 0L) {
            var splitTotal = 0L
            var overflow = false
            outcome.segments.forEachIndexed { index, segment ->
                if (overflow) return@forEachIndexed
                if (segment.status !in setOf(
                        FunctionalHyroxSegmentStatus.COMPLETED,
                        FunctionalHyroxSegmentStatus.PARTIAL,
                    )
                ) return@forEachIndexed
                val split = segment.splitMillis ?: return@forEachIndexed
                if (split <= 0L) return@forEachIndexed
                try {
                    splitTotal = Math.addExact(splitTotal, split)
                } catch (_: ArithmeticException) {
                    issues += FunctionalSportIssue("result.hyrox.segments[$index].splitMillis", "SEGMENT_SPLIT_TOTAL_OVERFLOW")
                    overflow = true
                    return@forEachIndexed
                }
            }
            if (!overflow && splitTotal > elapsed) {
                issues += FunctionalSportIssue("result.hyrox.elapsedMillis", "SEGMENT_SPLITS_EXCEED_ELAPSED")
            }
        }
    }

    private fun validatePenalties(
        definition: FunctionalWorkoutDefinition,
        penalties: List<FunctionalPenalty>,
        issues: MutableList<FunctionalSportIssue>,
    ) {
        val duplicateIds = penalties.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        penalties.forEachIndexed { index, penalty ->
            val path = "result.penalties[$index]"
            if (penalty.id.isBlank()) issues += FunctionalSportIssue("$path.id", "REQUIRED")
            if (penalty.id in duplicateIds) issues += FunctionalSportIssue("$path.id", "DUPLICATE")
            if (penalty.code.isBlank()) issues += FunctionalSportIssue("$path.code", "REQUIRED")
            if (!penalty.value.isFinite() || penalty.value <= 0.0) {
                issues += FunctionalSportIssue("$path.value", "POSITIVE_FINITE_REQUIRED")
            }
            if (penalty.note != null && penalty.note.isBlank()) issues += FunctionalSportIssue("$path.note", "NON_BLANK_OR_NULL_REQUIRED")
            if (definition.format != FunctionalWorkoutFormat.HYROX && penalty.segmentId != null) {
                issues += FunctionalSportIssue("$path.segmentId", "SEGMENT_REFERENCE_ONLY_FOR_HYROX")
            }
            if (penalty.segmentId != null && definition.hyroxSegments.none { it.id == penalty.segmentId }) {
                issues += FunctionalSportIssue("$path.segmentId", "HYROX_SEGMENT_NOT_FOUND")
            }
        }
    }

    private fun validateQuantity(
        quantity: FunctionalQuantity,
        path: String,
        issues: MutableList<FunctionalSportIssue>,
        allowZero: Boolean,
    ) {
        val lowerBoundInvalid = if (allowZero) quantity.value < 0.0 else quantity.value <= 0.0
        if (!quantity.value.isFinite() || lowerBoundInvalid) {
            issues += FunctionalSportIssue("$path.value", if (allowZero) "NON_NEGATIVE_FINITE_REQUIRED" else "POSITIVE_FINITE_REQUIRED")
        }
        if (quantity.unit == FunctionalQuantityUnit.REPETITIONS && quantity.value.isFinite() && quantity.value % 1.0 != 0.0) {
            issues += FunctionalSportIssue("$path.value", "REPETITIONS_MUST_BE_WHOLE")
        }
    }

    private fun validateLoad(load: FunctionalLoad, path: String, issues: MutableList<FunctionalSportIssue>) {
        if (!load.value.isFinite() || load.value <= 0.0) issues += FunctionalSportIssue("$path.value", "POSITIVE_FINITE_REQUIRED")
        if (load.equipment != null && load.equipment.isBlank()) issues += FunctionalSportIssue("$path.equipment", "NON_BLANK_OR_NULL_REQUIRED")
    }

    /** Returns a score only after checking both the source record and the finite aggregate. */
    fun calculateAmrapScore(payload: FunctionalSportPayload): AmrapScoreResult {
        if (payload.definition.format != FunctionalWorkoutFormat.AMRAP) return AmrapScoreResult.Unavailable
        val issues = validatePayload(payload, SportDocumentPurpose.RESULT)
        if (issues.isNotEmpty()) return AmrapScoreResult.Invalid(issues)
        val outcome = payload.result?.amrap ?: return AmrapScoreResult.Unavailable
        val progress = outcome.partialRoundProgress
        val partialReps = if (progress != null &&
            payload.definition.targetSequence.all { it.quantity.unit == FunctionalQuantityUnit.REPETITIONS } &&
            progress.all { it.quantity.unit == FunctionalQuantityUnit.REPETITIONS }
        ) {
            var total = 0.0
            progress.forEachIndexed { index, item ->
                total += item.quantity.value
                if (!total.isFinite()) {
                    return AmrapScoreResult.Invalid(listOf(
                        FunctionalSportIssue(
                            "result.amrap.partialRoundProgress[$index].quantity.value",
                            "AGGREGATE_OVERFLOW",
                        ),
                    ))
                }
            }
            total
        } else null
        return AmrapScoreResult.Available(FunctionalAmrapScore(
            completedRounds = outcome.completedRounds!!,
            partialRepetitions = partialReps,
            partialRoundProgress = progress,
        ))
    }

    /** Legacy nullable accessor; overflow and invalid records never export a non-finite score. */
    fun amrapScore(payload: FunctionalSportPayload): FunctionalAmrapScore? =
        (calculateAmrapScore(payload) as? AmrapScoreResult.Available)?.score

    /** Reports raw adherence counts. UNRECORDED is excluded from observed windows, not treated as missed. */
    fun emomAdherence(payload: FunctionalSportPayload): FunctionalEmomAdherenceSummary? {
        if (payload.definition.format != FunctionalWorkoutFormat.EMOM ||
            validatePayload(payload, SportDocumentPurpose.RESULT).isNotEmpty()
        ) return null
        val windows = payload.result?.emom?.windows ?: return null
        val completed = windows.count { it.adherence == FunctionalEmomWindowAdherence.COMPLETED }
        val partial = windows.count { it.adherence == FunctionalEmomWindowAdherence.PARTIAL }
        val missed = windows.count { it.adherence == FunctionalEmomWindowAdherence.MISSED }
        val unrecorded = windows.count { it.adherence == FunctionalEmomWindowAdherence.UNRECORDED }
        return FunctionalEmomAdherenceSummary(
            completedWindows = completed,
            partialWindows = partial,
            missedWindows = missed,
            observedWindows = completed + partial + missed,
            unrecordedWindows = unrecorded,
            totalWindows = windows.size,
        )
    }

    /** Exact scoring snapshot equality prevents mixing revisions, scaling, loads, divisions, or rulesets. */
    fun areComparable(
        first: FunctionalWorkoutDefinition,
        second: FunctionalWorkoutDefinition,
    ): Boolean = first.copy(title = "") == second.copy(title = "")
}
