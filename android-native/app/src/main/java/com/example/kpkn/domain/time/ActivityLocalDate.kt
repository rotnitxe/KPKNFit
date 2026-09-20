package com.example.kpkn.domain.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Maps an ISO instant to the user's local activity day (AUD-05).
 * Policy: use the **completion** instant in the device zone for finish/nutrition burn.
 */
object ActivityLocalDate {

    private val isoDate = DateTimeFormatter.ISO_LOCAL_DATE

    fun fromInstantIso(instantIso: String, zoneId: ZoneId = ZoneId.systemDefault()): LocalDate {
        val instant = Instant.parse(instantIso)
        return instant.atZone(zoneId).toLocalDate()
    }

    fun fromInstantIsoOrDatePrefix(value: String, zoneId: ZoneId = ZoneId.systemDefault()): LocalDate {
        return runCatching { fromInstantIso(value, zoneId) }
            .getOrElse { LocalDate.parse(value.take(10)) }
    }

    fun formatIsoDate(localDate: LocalDate): String = localDate.format(isoDate)

    fun matchesActivityDay(storedDate: String, activityDay: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): Boolean {
        val day = fromInstantIsoOrDatePrefix(storedDate, zoneId)
        return day == activityDay
    }
}
