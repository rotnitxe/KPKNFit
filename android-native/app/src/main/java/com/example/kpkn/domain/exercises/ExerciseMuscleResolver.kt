package com.example.kpkn.domain.exercises

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.domain.auge.SessionMuscleFilter

object ExerciseMuscleResolver {

    fun effectiveMuscles(
        exercise: Exercise,
        exerciseIndex: Map<String, ExerciseMuscleInfo>,
    ): List<InvolvedMuscle> {
        if (!exercise.effectiveMuscles.isNullOrEmpty()) {
            return exercise.effectiveMuscles!!
        }
        val dbInfo = resolveCatalogInfo(exercise, exerciseIndex)
        return SessionMuscleFilter.relevantMusclesFor(dbInfo)
    }

    fun effectiveMusclesWithoutFilter(
        exercise: Exercise,
        exerciseIndex: Map<String, ExerciseMuscleInfo>,
    ): List<InvolvedMuscle> {
        if (!exercise.effectiveMuscles.isNullOrEmpty()) {
            return exercise.effectiveMuscles!!
        }
        val dbInfo = resolveCatalogInfo(exercise, exerciseIndex)
        return dbInfo?.involvedMuscles.orEmpty()
    }

    private fun resolveCatalogInfo(
        exercise: Exercise,
        exerciseIndex: Map<String, ExerciseMuscleInfo>,
    ): ExerciseMuscleInfo? {
        val dbId = (exercise.exerciseDbId ?: exercise.exerciseId)?.lowercase()
        return dbId?.let { exerciseIndex[it] }
            ?: exerciseIndex.values.firstOrNull {
                it.name.equals(exercise.name, ignoreCase = true)
            }
    }
}
