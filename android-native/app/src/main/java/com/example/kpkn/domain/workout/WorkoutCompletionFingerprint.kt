package com.example.kpkn.domain.workout

import com.example.kpkn.data.models.*
import java.security.MessageDigest

/** Stable completion identity based only on recorded sets, never a physiological context. */
object WorkoutCompletionFingerprint {
    fun completedSetInputHash(completedExercises: List<CompletedExercise>): String {
        val canonical = completedExercises.joinToString("|") { exercise ->
            val muscles = exercise.effectiveMuscles.orEmpty()
                .sortedWith(compareBy({ it.muscle }, { it.role.name }, { it.volumeContribution ?: -1.0 }))
                .joinToString(",") { "${it.muscle}:${it.role}:${it.volumeContribution ?: "null"}" }
            val sets = exercise.sets.joinToString(",") { set ->
                listOf(
                    set.id, set.weight, set.reps, set.timeSeconds, set.rpe, set.rir,
                    set.isFailure, set.isFailedSet, set.isPartial, set.partialReps,
                    set.actualIntensityMode, set.actualIntensityValue, set.skipped, set.isWarmup,
                    set.side, set.dropSets.size, set.restPauses.size,
                ).joinToString(";")
            }
            listOf(
                exercise.exerciseId, exercise.exerciseDbId, exercise.catalogDefinitionId,
                exercise.catalogConfigurationId, exercise.performanceProfileId,
                exercise.restTime, exercise.supersetId, muscles, sets,
            ).joinToString("#")
        }
        return sha256(canonical)
    }

    /** Lightweight snapshot hash used while the finish sheet is still open. */
    fun completedSetInputHash(completedSets: Map<String, CompletedSet>): String = sha256(
        completedSets.toSortedMap().entries.joinToString("|") { (key, set) ->
            listOf(
                key, set.id, set.weight, set.reps, set.timeSeconds, set.rpe, set.rir,
                set.actualIntensityMode, set.actualIntensityValue, set.isFailure,
                set.isFailedSet, set.skipped, set.isWarmup, set.side,
            ).joinToString(";")
        },
    )

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
