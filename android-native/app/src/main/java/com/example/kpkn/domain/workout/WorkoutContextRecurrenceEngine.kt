package com.example.kpkn.domain.workout

import com.example.kpkn.data.models.WorkoutLog
import java.time.DayOfWeek
import java.time.LocalDate

object WorkoutContextRecurrenceEngine {

    data class RecurrenceResult(
        val tagId: String?,
        val profileId: String?,
        val confidence: Int,
    )

    fun detectDayRecurrence(
        exerciseDbId: String,
        dayOfWeek: DayOfWeek,
        logs: List<WorkoutLog>,
    ): RecurrenceResult {
        val sameDayLogs = logs.filter { log ->
            try {
                LocalDate.parse(log.date.take(10)).dayOfWeek == dayOfWeek
            } catch (_: Exception) { false }
        }
        if (sameDayLogs.size < 2) return RecurrenceResult(null, null, 0)

        data class TagAndSetup(val tagId: String?, val setupId: String?)
        val patterns = sameDayLogs.mapNotNull { log ->
            val tag = log.exerciseTags[exerciseDbId]
            val setupId = log.completedExercises
                .firstOrNull { it.exerciseDbId == exerciseDbId }
                ?.sets?.firstNotNullOfOrNull { it.setupProfileId }
            if (tag != null || setupId != null) TagAndSetup(tag, setupId) else null
        }

        val mostFrequent = patterns
            .groupBy { it }
            .maxByOrNull { it.value.size }
            ?: return RecurrenceResult(null, null, 0)

        val count = mostFrequent.value.size
        return if (count >= 2) {
            RecurrenceResult(
                tagId = mostFrequent.key.tagId,
                profileId = mostFrequent.key.setupId,
                confidence = count,
            )
        } else {
            RecurrenceResult(null, null, 0)
        }
    }
}
