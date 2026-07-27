package com.example.kpkn.domain.exercises

import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.domain.training.VolumeCalculator

fun matchesMuscle(exercise: ExerciseMuscleInfo, anatomy: MuscleAnatomy): Boolean {
    return exercise.involvedMuscles.any {
        VolumeCalculator.normalizeCanonicalMuscleGroup(it.muscle, it.emphasis) == anatomy.canonicalName
            && it.role == MuscleRole.PRIMARY
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
        if (canonical != anatomy.canonicalName) return@any false
        MuscleHeadResolution.matchesHead(anatomy.canonicalName, involvement.emphasis, head)
    }
}
