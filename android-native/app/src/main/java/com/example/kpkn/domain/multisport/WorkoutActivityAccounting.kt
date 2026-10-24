package com.example.kpkn.domain.multisport

import com.example.kpkn.data.models.CalorieRange
import com.example.kpkn.data.models.EnergyEstimateSource
import com.example.kpkn.data.models.WorkoutLog
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement

/** A calorie range represented without Int aggregation limits. */
data class WorkoutActivityEnergyRange(
    val low: Long,
    val mid: Long,
    val high: Long,
)

/** Energy retained from PLANNED or LIVE data and kept explicitly as an estimate. */
data class WorkoutActivityEnergyEstimate(
    val source: EnergyEstimateSource,
    val totalKcal: WorkoutActivityEnergyRange,
)

enum class WorkoutActivityMissingField {
    LOG_ID,
    DURATION_MINUTES,
    FINAL_ENERGY,
    CONFLICT_RESOLUTION,
}

enum class WorkoutActivityWarningCode {
    BLANK_ID,
    ID_CONFLICT,
    NEGATIVE_DURATION,
    NON_FINAL_ENERGY,
    INVALID_ENERGY_RANGE,
    DURATION_TOTAL_OVERFLOW,
    ENERGY_TOTAL_OVERFLOW,
}

/** One distinct history record. Exact repeats of a valid ID are represented once. */
data class WorkoutActivity(
    /** Original model reference; accounting reads it without modifying it. */
    val log: WorkoutLog,
    /** Number of identical records collapsed for this valid ID; always one for blank IDs. */
    val sourceOccurrences: Int,
    val hasValidId: Boolean,
    /** True for every distinct record variant of an ID that has conflicting contents. */
    val hasIdConflict: Boolean,
    /** Null only when the source duration is negative. Zero is a known duration. */
    val durationMinutes: Long?,
    /** Existing engine FINAL range; accounting does not infer a measurement. */
    val finalEnergyKcal: WorkoutActivityEnergyRange?,
    /** Populated for valid PLANNED/LIVE totalKcal values and kept explicitly estimated. */
    val estimatedEnergy: WorkoutActivityEnergyEstimate?,
)

data class WorkoutActivityIdConflict(
    val id: String,
    /** Distinct contents for this ID, in deterministic order. */
    val distinctLogs: List<WorkoutLog>,
)

data class WorkoutActivityMissingData(
    val logId: String?,
    val log: WorkoutLog,
    val fields: Set<WorkoutActivityMissingField>,
)

/** A null [log] identifies an aggregate-level warning such as a Long overflow. */
data class WorkoutActivityAccountingWarning(
    val code: WorkoutActivityWarningCode,
    val logId: String?,
    val log: WorkoutLog? = null,
)

data class WorkoutActivitySummary(
    /** Valid unique records plus each unmergeable blank-ID record; includes conflict variants. */
    val activities: List<WorkoutActivity>,
    val idConflicts: List<WorkoutActivityIdConflict>,
    val missingData: List<WorkoutActivityMissingData>,
    val warnings: List<WorkoutActivityAccountingWarning>,
    /** Known eligible sum; null if nonempty history has no valid duration or the Long sum overflowed. */
    val totalDurationMinutes: Long?,
    /** True when duration is unavailable or excluded for any input activity. */
    val hasPartialDurationTotal: Boolean,
    /** Sum of known FINAL ranges; null when none is known or the sum overflowed, never known zero. */
    val totalFinalEnergyKcal: WorkoutActivityEnergyRange?,
    /** True when FINAL energy is unavailable or excluded for any input activity. */
    val hasPartialEnergyTotal: Boolean,
)

/**
 * Adapts existing workout history for future activity summaries. It does not infer a sport,
 * re-estimate energy, or add set calories / per-exercise contributions to the session total.
 * Only [com.example.kpkn.data.models.SessionEnergySummary.totalKcal] with source FINAL enters
 * the energy aggregate. FINAL is the existing engine's finalized estimate, not a measurement
 * inferred here. PLANNED and LIVE ranges remain labeled estimates on their activity. Empty
 * history has zero known duration and no known final-energy range (`null`), not zero energy.
 * Canonical JSON keys are an internal ordering aid only: special floating-point values from
 * unrelated log metadata are retained for ordering, but are neither validated nor used to
 * estimate or aggregate calories, and this JSON is not persisted.
 *
 * Identical contents under one nonblank ID count once. Conflicting contents under an ID are all
 * retained for resolution and excluded from aggregates. Blank IDs cannot be safely deduplicated,
 * so each is surfaced separately and excluded from totals.
 */
object WorkoutActivityAccounting {
    private data class Variant(
        var representative: WorkoutLog,
        val canonicalKey: String,
        var occurrences: Int,
    )

    private data class Candidate(
        val log: WorkoutLog,
        val occurrences: Int,
        val hasValidId: Boolean,
        val hasIdConflict: Boolean,
        val canonicalKey: String,
    )

    private data class AccountedActivity(
        val activity: WorkoutActivity,
        val canonicalKey: String,
    )

    private val canonicalJson = Json {
        encodeDefaults = true
        allowSpecialFloatingPointValues = true
    }

    fun summarize(logs: List<WorkoutLog>): WorkoutActivitySummary {
        val variantsById = linkedMapOf<String, MutableList<Variant>>()
        val blankIdLogs = mutableListOf<WorkoutLog>()

        logs.forEach { log ->
            if (log.id.isBlank()) {
                // An invalid key is never used as a shared bucket, even for exact repeats.
                blankIdLogs += log
            } else {
                val variants = variantsById.getOrPut(log.id) { mutableListOf() }
                val identical = variants.firstOrNull { it.representative == log }
                if (identical == null) {
                    variants += Variant(
                        representative = log,
                        canonicalKey = stableLogKey(log),
                        occurrences = 1,
                    )
                } else {
                    identical.occurrences += 1
                }
            }
        }

        val conflicts = variantsById.entries
            .filter { (_, variants) -> variants.size > 1 }
            .sortedBy { (id, _) -> id }
            .map { (id, variants) ->
                WorkoutActivityIdConflict(
                    id = id,
                    distinctLogs = variants.sortedBy(Variant::canonicalKey).map(Variant::representative),
                )
            }
        val conflictIds = conflicts.mapTo(hashSetOf(), WorkoutActivityIdConflict::id)

        val candidates = buildList {
            variantsById.forEach { (id, variants) ->
                val conflicted = id in conflictIds
                variants.forEach { variant ->
                    add(
                        Candidate(
                            log = variant.representative,
                            occurrences = variant.occurrences,
                            hasValidId = true,
                            hasIdConflict = conflicted,
                            canonicalKey = variant.canonicalKey,
                        ),
                    )
                }
            }
            blankIdLogs.forEach { log ->
                add(
                    Candidate(
                        log = log,
                        occurrences = 1,
                        hasValidId = false,
                        hasIdConflict = false,
                        canonicalKey = stableLogKey(log),
                    ),
                )
            }
        }.sortedWith(compareBy<Candidate>({ it.log.id }, Candidate::canonicalKey))

        val activityRecords = candidates.map { candidate ->
            AccountedActivity(toActivity(candidate), candidate.canonicalKey)
        }
        val activities = activityRecords.map(AccountedActivity::activity)
        val eligibleActivities = activities.filter { it.hasValidId && !it.hasIdConflict }

        var durationSum = 0L
        var durationOverflow = false
        eligibleActivities.forEach { activity ->
            val duration = activity.durationMinutes ?: return@forEach
            if (!durationOverflow) {
                val next = addNonNegative(durationSum, duration)
                if (next == null) {
                    durationOverflow = true
                } else {
                    durationSum = next
                }
            }
        }

        var energySum = WorkoutActivityEnergyRange(0L, 0L, 0L)
        var hasKnownEnergy = false
        var energyOverflow = false
        eligibleActivities.forEach { activity ->
            val range = activity.finalEnergyKcal ?: return@forEach
            hasKnownEnergy = true
            if (!energyOverflow) {
                val next = addRanges(energySum, range)
                if (next == null) {
                    energyOverflow = true
                } else {
                    energySum = next
                }
            }
        }

        val hasExcludedIdentity = activities.any { !it.hasValidId || it.hasIdConflict }
        val durationPartial = hasExcludedIdentity || durationOverflow || activities.any { it.durationMinutes == null }
        val energyPartial = hasExcludedIdentity || energyOverflow || activities.any { it.finalEnergyKcal == null }

        val missingData = activityRecords.mapNotNull { record ->
            missingDataFor(record.activity)?.let { record.canonicalKey to it }
        }
            .sortedWith(
                compareBy<Pair<String, WorkoutActivityMissingData>>(
                    { it.second.logId ?: "" },
                    { it.first },
                    { it.second.fields.sortedBy { field -> field.ordinal }.joinToString() },
                ),
            ).map { it.second }
        val warnings = buildList<Pair<String, WorkoutActivityAccountingWarning>> {
            activityRecords.forEach { record ->
                val activity = record.activity
                val id = activity.log.id.takeIf(String::isNotBlank)
                if (!activity.hasValidId) {
                    add(record.canonicalKey to WorkoutActivityAccountingWarning(
                        WorkoutActivityWarningCode.BLANK_ID,
                        null,
                        activity.log,
                    ))
                }
                if (activity.hasIdConflict) {
                    add(record.canonicalKey to WorkoutActivityAccountingWarning(
                        WorkoutActivityWarningCode.ID_CONFLICT,
                        id,
                        activity.log,
                    ))
                }
                if (activity.log.durationMinutes < 0) {
                    add(record.canonicalKey to WorkoutActivityAccountingWarning(
                        WorkoutActivityWarningCode.NEGATIVE_DURATION,
                        id,
                        activity.log,
                    ))
                }
                val energySummary = activity.log.energySummary
                if (energySummary != null && energySummary.source != EnergyEstimateSource.FINAL) {
                    add(record.canonicalKey to WorkoutActivityAccountingWarning(
                        WorkoutActivityWarningCode.NON_FINAL_ENERGY,
                        id,
                        activity.log,
                    ))
                }
                if (energySummary != null && energySummary.totalKcal.toAccountingRangeOrNull() == null) {
                    add(record.canonicalKey to WorkoutActivityAccountingWarning(
                        WorkoutActivityWarningCode.INVALID_ENERGY_RANGE,
                        id,
                        activity.log,
                    ))
                }
            }
            if (durationOverflow) {
                add("" to WorkoutActivityAccountingWarning(WorkoutActivityWarningCode.DURATION_TOTAL_OVERFLOW, null))
            }
            if (energyOverflow) {
                add("" to WorkoutActivityAccountingWarning(WorkoutActivityWarningCode.ENERGY_TOTAL_OVERFLOW, null))
            }
        }.sortedWith(
            compareBy<Pair<String, WorkoutActivityAccountingWarning>>(
                { it.second.logId ?: "" },
                { it.second.code.ordinal },
                { it.first },
            ),
        ).map { it.second }

        return WorkoutActivitySummary(
            activities = activities,
            idConflicts = conflicts,
            missingData = missingData,
            warnings = warnings,
            totalDurationMinutes = when {
                durationOverflow -> null
                activities.isEmpty() -> 0L
                eligibleActivities.none { it.durationMinutes != null } -> null
                else -> durationSum
            },
            hasPartialDurationTotal = durationPartial,
            totalFinalEnergyKcal = if (energyOverflow || !hasKnownEnergy) null else energySum,
            hasPartialEnergyTotal = energyPartial,
        )
    }

    private fun toActivity(candidate: Candidate): WorkoutActivity {
        val log = candidate.log
        val summary = log.energySummary
        val energyRange = summary?.totalKcal?.toAccountingRangeOrNull()
        val finalEnergy = if (summary?.source == EnergyEstimateSource.FINAL) energyRange else null
        val estimatedEnergy = if (
            summary != null && summary.source != EnergyEstimateSource.FINAL && energyRange != null
        ) {
            WorkoutActivityEnergyEstimate(summary.source, energyRange)
        } else {
            null
        }

        return WorkoutActivity(
            log = log,
            sourceOccurrences = candidate.occurrences,
            hasValidId = candidate.hasValidId,
            hasIdConflict = candidate.hasIdConflict,
            durationMinutes = log.durationMinutes.takeIf { it >= 0 }?.toLong(),
            finalEnergyKcal = finalEnergy,
            estimatedEnergy = estimatedEnergy,
        )
    }

    private fun missingDataFor(activity: WorkoutActivity): WorkoutActivityMissingData? {
        val fields = linkedSetOf<WorkoutActivityMissingField>()
        if (!activity.hasValidId) fields += WorkoutActivityMissingField.LOG_ID
        if (activity.durationMinutes == null) fields += WorkoutActivityMissingField.DURATION_MINUTES
        if (activity.finalEnergyKcal == null) fields += WorkoutActivityMissingField.FINAL_ENERGY
        if (activity.hasIdConflict) fields += WorkoutActivityMissingField.CONFLICT_RESOLUTION
        if (fields.isEmpty()) return null
        return WorkoutActivityMissingData(
            logId = activity.log.id.takeIf(String::isNotBlank),
            log = activity.log,
            fields = fields,
        )
    }

    private fun CalorieRange.toAccountingRangeOrNull(): WorkoutActivityEnergyRange? {
        if (low < 0 || mid < 0 || high < 0 || low > mid || mid > high) return null
        return WorkoutActivityEnergyRange(low.toLong(), mid.toLong(), high.toLong())
    }

    private fun addRanges(
        left: WorkoutActivityEnergyRange,
        right: WorkoutActivityEnergyRange,
    ): WorkoutActivityEnergyRange? {
        val low = addNonNegative(left.low, right.low) ?: return null
        val mid = addNonNegative(left.mid, right.mid) ?: return null
        val high = addNonNegative(left.high, right.high) ?: return null
        return WorkoutActivityEnergyRange(low, mid, high)
    }

    /** All callers pass validated non-negative inputs, so this check cannot itself overflow. */
    private fun addNonNegative(left: Long, right: Long): Long? {
        if (right > Long.MAX_VALUE - left) return null
        return left + right
    }

    /** Full JSON with defaults and recursively sorted object keys; arrays retain source order. */
    private fun stableLogKey(log: WorkoutLog): String =
        canonicalJson.encodeToJsonElement(WorkoutLog.serializer(), log).canonicalText()

    private fun JsonElement.canonicalText(): String = when (this) {
        is JsonObject -> entries
            .sortedBy { it.key }
            .joinToString(separator = ",", prefix = "{", postfix = "}") { (key, value) ->
                "${JsonPrimitive(key)}:${value.canonicalText()}"
            }
        is JsonArray -> joinToString(separator = ",", prefix = "[", postfix = "]") { it.canonicalText() }
        else -> toString()
    }
}
