package com.example.kpkn.domain.exercises

import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole

object SessionMuscleFilter {
    fun relevantMusclesFor(exercise: ExerciseMuscleInfo?): List<InvolvedMuscle> {
        if (exercise == null) return emptyList()
        return exercise.involvedMuscles.filter { muscle ->
            when (muscle.role) {
                MuscleRole.PRIMARY -> true
                MuscleRole.SECONDARY -> true
                MuscleRole.STABILIZER -> true
                MuscleRole.NEUTRALIZER -> false
            }
        }
    }
}
