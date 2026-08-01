package com.example.kpkn.screens.workout

import com.example.kpkn.data.exercises.EXERCISE_DATABASE
import com.example.kpkn.data.exercises.buildExerciseCatalogLookup
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.domain.exercises.exerciseDisplayName

private val workoutExerciseCatalogLookup by lazy {
    buildExerciseCatalogLookup(EXERCISE_DATABASE)
}

internal fun displayWorkoutExerciseName(exercise: Exercise): String =
    exerciseDisplayName(exercise, workoutExerciseCatalogLookup)
