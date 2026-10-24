package com.example.kpkn.domain.time

import com.example.kpkn.data.models.WorkoutLog
import java.time.Instant

/** Existing date decoding only; zero denotes an unparseable legacy date and never a recorded observation. */
object TrainingDates {
    fun logDateMs(log: WorkoutLog): Long = try {
        java.time.OffsetDateTime.parse(log.date).toInstant().toEpochMilli()
    } catch (e: Exception) {
        try {
            Instant.parse(log.date).toEpochMilli()
        } catch (e2: Exception) {
            try {
                java.time.ZonedDateTime.parse(log.date).toInstant().toEpochMilli()
            } catch (e3: Exception) {
                try {
                    java.time.LocalDate.parse(log.date.take(10))
                        .atStartOfDay(java.time.ZoneId.systemDefault())
                        .toInstant().toEpochMilli()
                } catch (e4: Exception) { 0L }
            }
        }
    }

    fun parseIsoMs(dateString: String): Long = try {
        java.time.OffsetDateTime.parse(dateString).toInstant().toEpochMilli()
    } catch (e: Exception) {
        try {
            Instant.parse(dateString).toEpochMilli()
        } catch (e2: Exception) {
            try {
                java.time.ZonedDateTime.parse(dateString).toInstant().toEpochMilli()
            } catch (e3: Exception) {
                try {
                    java.time.LocalDate.parse(dateString.take(10))
                        .atStartOfDay(java.time.ZoneId.systemDefault())
                        .toInstant().toEpochMilli()
                } catch (e4: Exception) { 0L }
            }
        }
    }

}
