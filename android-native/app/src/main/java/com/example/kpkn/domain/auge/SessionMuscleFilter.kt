package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole

object SessionMuscleFilter {
    fun relevantMusclesFor(exercise: ExerciseMuscleInfo?): List<InvolvedMuscle> {
        if (exercise == null) return emptyList()
        val hasHighAxialLoad = (exercise.ssc ?: 0.0) >= 1.5
        return exercise.involvedMuscles.filter { muscle ->
            when (muscle.role) {
                MuscleRole.PRIMARY -> true
                MuscleRole.SECONDARY -> true
                MuscleRole.STABILIZER -> hasHighAxialLoad
                MuscleRole.NEUTRALIZER -> false
            }
        }
    }
}
