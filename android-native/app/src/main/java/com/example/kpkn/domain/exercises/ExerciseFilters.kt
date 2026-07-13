package com.example.kpkn.domain.exercises

import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.domain.training.VolumeCalculator

fun matchesMuscle(exercise: ExerciseMuscleInfo, anatomy: MuscleAnatomy): Boolean {
    return exercise.involvedMuscles.any { involvement ->
        VolumeCalculator.normalizeCanonicalMuscleGroup(involvement.muscle, involvement.emphasis) == anatomy.canonicalName
    }
}

fun matchesMuscleHead(
    exercise: ExerciseMuscleInfo,
    anatomy: MuscleAnatomy,
    head: MuscleHead,
): Boolean {
    if (!matchesMuscle(exercise, anatomy)) return false
    return exercise.involvedMuscles.any { involvement ->
        val canonical = VolumeCalculator.normalizeCanonicalMuscleGroup(involvement.muscle, involvement.emphasis)
        canonical != anatomy.canonicalName && return@any false
        val emphasisLower = involvement.emphasis?.lowercase()?.trim().orEmpty()
        if (head.emphasisKeyword != null) {
            emphasisLower == head.emphasisKeyword
        } else {
            emphasisLower.isBlank()
        }
    }
}
