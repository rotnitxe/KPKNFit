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

    data class RecurrenceObservation(
        val dateIso: String,
        val tagId: String?,
        val profileId: String?,
    )

    fun detectDayRecurrence(
        exerciseDbId: String,
        dayOfWeek: DayOfWeek,
        logs: List<WorkoutLog>,
    ): RecurrenceResult {
        val observations = logs.map { log ->
            val matching = log.completedExercises.firstOrNull { exercise ->
                exercise.exerciseDbId == exerciseDbId ||
                    exercise.canonicalExerciseId == exerciseDbId ||
                    exercise.exerciseId == exerciseDbId
            }
            val tagId = matching?.let { exercise ->
                WorkoutTagResolver.lookupLogTagId(log, exercise)
                    ?: WorkoutTagResolver.lookupLogTagName(log, exercise)
                    ?: exercise.sets.firstNotNullOfOrNull { set -> set.tagId?.trim()?.takeIf { it.isNotEmpty() } }
            } ?: log.exerciseTagIds[exerciseDbId] ?: log.exerciseTags[exerciseDbId]
            val setupId = matching?.sets?.firstNotNullOfOrNull { it.setupProfileId }
            RecurrenceObservation(dateIso = log.date, tagId = tagId, profileId = setupId)
        }
        return detectDayRecurrence(dayOfWeek, observations)
    }

    fun detectDayRecurrence(
        dayOfWeek: DayOfWeek,
        observations: List<RecurrenceObservation>,
    ): RecurrenceResult {
        val sameDay = observations.filter { observation ->
            try {
                LocalDate.parse(observation.dateIso.take(10)).dayOfWeek == dayOfWeek
            } catch (_: Exception) {
                false
            }
        }
        if (sameDay.size < 2) return RecurrenceResult(null, null, 0)

        data class TagAndSetup(val tagId: String?, val setupId: String?)
        val patterns = sameDay.mapNotNull { observation ->
            if (observation.tagId != null || observation.profileId != null) {
                TagAndSetup(observation.tagId, observation.profileId)
            } else {
                null
            }
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
