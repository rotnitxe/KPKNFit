package com.example.kpkn.domain.nutrition.checkin

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlinx.serialization.Serializable

enum class CheckinAvailability { NOW, FUTURE, DISMISSED, NONE }
enum class CheckinDecision { PENDING, APPLIED, DECLINED, POSTPONED, DISMISSED }

@Serializable
enum class CheckinModule { INTRODUCTION, LOGGING_BREAK, FASTING, PARTIAL_DAYS, WEIGH_IN, TARGET_UPDATE }

/** Preferencias dentro del JSON de ajustes; los datos anteriores conservan sus valores por defecto. */
@Serializable
data class CheckinPreferences(
    val dayOfWeek: Int? = null,
    val introductionSeen: Boolean = false,
    val dismissedModules: Set<CheckinModule> = emptySet(),
)

data class CheckinRecord(
    val weekKey: LocalDate,
    val status: CheckinDecision,
    val postponedUntil: LocalDate? = null,
    val appliedPlanId: String? = null,
)

/** Fechas locales: viajar de zona no reinterpreta una decisión ya guardada. */
object CheckinPolicy {
    fun scheduledDate(today: LocalDate, checkinDay: DayOfWeek): LocalDate =
        today.with(TemporalAdjusters.previousOrSame(checkinDay))

    fun weekKey(today: LocalDate, weekStartDay: DayOfWeek, checkinDay: DayOfWeek = weekStartDay): LocalDate =
        scheduledDate(today, checkinDay).with(TemporalAdjusters.previousOrSame(weekStartDay))

    fun state(
        today: LocalDate,
        weekStartDay: DayOfWeek,
        record: CheckinRecord?,
        hasActivePlan: Boolean = true,
        checkinDay: DayOfWeek = weekStartDay,
    ): CheckinAvailability {
        if (!hasActivePlan) return CheckinAvailability.NONE
        val scheduled = scheduledDate(today, checkinDay)
        val current = record?.takeIf { it.weekKey == weekKey(today, weekStartDay, checkinDay) }
        val localWeek = today.with(TemporalAdjusters.previousOrSame(weekStartDay))
        if (today > scheduled.plusDays(2) && weekKey(today, weekStartDay, checkinDay) < localWeek &&
            current?.status != CheckinDecision.POSTPONED) return CheckinAvailability.FUTURE
        if (current?.status in setOf(CheckinDecision.APPLIED, CheckinDecision.DECLINED, CheckinDecision.DISMISSED)) {
            return CheckinAvailability.DISMISSED
        }
        if (current?.status == CheckinDecision.POSTPONED) {
            val until = current.postponedUntil ?: return CheckinAvailability.FUTURE
            if (today < until) return CheckinAvailability.FUTURE
            if (today == until) return CheckinAvailability.NOW
        }
        return if (today <= scheduled.plusDays(2)) CheckinAvailability.NOW else CheckinAvailability.FUTURE
    }
}
