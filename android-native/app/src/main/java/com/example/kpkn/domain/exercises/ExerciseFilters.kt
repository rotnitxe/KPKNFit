package com.example.kpkn.domain.exercises

import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.domain.training.VolumeCalculator

fun matchesMuscle(exercise: ExerciseMuscleInfo, anatomy: MuscleAnatomy): Boolean {
    val involvements = exercise.involvedMuscles.filter {
        VolumeCalculator.normalizeCanonicalMuscleGroup(it.muscle, it.emphasis) == anatomy.canonicalName
    }
    if (involvements.isEmpty()) return false

    if (anatomy.canonicalName.equals("Trapecio", ignoreCase = true)) {
        return involvements.any { it.role == MuscleRole.PRIMARY || it.role == MuscleRole.SECONDARY }
    }
    return true
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
