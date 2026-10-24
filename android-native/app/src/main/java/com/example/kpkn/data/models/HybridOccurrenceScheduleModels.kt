package com.example.kpkn.data.models

import com.example.kpkn.domain.multisport.HybridAppliedWeekSchedule
import kotlinx.serialization.Serializable

/** An exact runtime identity. A template week ID alone never identifies an occurrence. */
@Serializable
data class HybridOccurrenceIdentity(
    val runId: String,
    val cycleNumber: Int,
    val weekInstanceId: String,
    val weekId: String,
)

/** Runtime placement overlay. Never materialize this object into template session dates/days. */
@Serializable
data class HybridOccurrenceWeekSchedule(
    val schemaVersion: Int = 1,
    val programId: String,
    val runId: String,
    val cycleNumber: Int,
    val weekInstanceId: String,
    val weekId: String,
    /** Explicit ISO anchor for this occurrence, including floating programs. */
    val weekStartDate: String,
    val schedule: HybridAppliedWeekSchedule,
) {
    val identity: HybridOccurrenceIdentity
        get() = HybridOccurrenceIdentity(runId, cycleNumber, weekInstanceId, weekId)
}
