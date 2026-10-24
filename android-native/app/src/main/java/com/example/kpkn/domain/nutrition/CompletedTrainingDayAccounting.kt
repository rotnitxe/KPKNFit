package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.multisport.WorkoutActivity
import com.example.kpkn.domain.multisport.WorkoutActivityAccounting
import com.example.kpkn.domain.multisport.WorkoutActivityEnergyRange
import com.example.kpkn.domain.time.ActivityLocalDate
import java.time.LocalDate
import java.time.ZoneId

/** Final session estimates known for a day; missing estimates are never measured zero. */
data class CompletedTrainingDayAccounting(
    val date: LocalDate,
    val activities: List<WorkoutActivity>,
    val knownFinalEnergyKcal: WorkoutActivityEnergyRange?,
    val hasPartialEnergy: Boolean,
    /** Records with an unresolved date stay outside every dated total. */
    val undatedActivityCount: Int,
) {
    /** Compatibility with the existing Int balance, without wrapping or silently capping a total. */
    val balanceMidpointKcal: Int?
        get() = knownFinalEnergyKcal?.mid?.takeIf { it in 0..Int.MAX_VALUE.toLong() }?.toInt()

    val isOutsideBalanceRange: Boolean
        get() = knownFinalEnergyKcal != null && balanceMidpointKcal == null
}

object CompletedTrainingDayAccountant {
    fun summarize(
        logs: List<WorkoutLog>,
        date: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): CompletedTrainingDayAccounting {
        // Resolve identity before filtering by date: two variants of an ID on different days
        // must not each become an apparently valid independent activity.
        val all = WorkoutActivityAccounting.summarize(logs)
        val dated = all.activities.map { it to activityDate(it.log, zone) }
        val activities = dated.filter { it.second == date }.map { it.first }
        val eligible = activities.filter { it.hasValidId && !it.hasIdConflict }
        val known = WorkoutActivityAccounting.summarize(eligible.map { it.log })
        return CompletedTrainingDayAccounting(
            date = date,
            activities = activities,
            knownFinalEnergyKcal = known.totalFinalEnergyKcal,
            hasPartialEnergy = known.hasPartialEnergyTotal || activities.size != eligible.size,
            undatedActivityCount = dated.count { it.second == null },
        )
    }

    private fun activityDate(log: WorkoutLog, zone: ZoneId): LocalDate? =
        log.actualDate?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: ActivityLocalDate.fromInstantIsoOrDatePrefixOrNull(log.date, zone)
}
