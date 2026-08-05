package com.example.kpkn.domain.exercises

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.exercises.resolveCatalogExerciseInfoInIndex
import com.example.kpkn.domain.auge.SessionMuscleFilter

object ExerciseMuscleResolver {

    fun effectiveMuscles(
        exercise: Exercise,
        exerciseIndex: Map<String, ExerciseMuscleInfo>,
    ): List<InvolvedMuscle> {
        if (!exercise.effectiveMuscles.isNullOrEmpty()) {
            return exercise.effectiveMuscles!!.filter {
                it.role != com.example.kpkn.data.models.MuscleRole.NEUTRALIZER
            }
        }
        val dbInfo = resolveCatalogInfo(exercise, exerciseIndex)
        val selected = selectedTechnicalMuscles(exercise, dbInfo)
        if (selected != null) return selected
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
        return selectedTechnicalMuscles(exercise, dbInfo) ?: dbInfo?.involvedMuscles.orEmpty()
    }

    /**
     * Muscle rows used by the training-volume counter.
     *
     * Volume treats secondary and stabilizer work as indirect volume. A
     * stabilizer must not disappear merely because an exercise has a low SSC;
     * that SSC rule belongs to fatigue filtering, not volume accounting.
     */
    fun effectiveMusclesForVolume(
        exercise: Exercise,
        exerciseIndex: Map<String, ExerciseMuscleInfo>,
    ): List<InvolvedMuscle> = effectiveMusclesWithoutFilter(exercise, exerciseIndex)
        .filter { it.role != com.example.kpkn.data.models.MuscleRole.NEUTRALIZER }

    internal fun resolveCatalogInfo(
        exercise: Exercise,
        exerciseIndex: Map<String, ExerciseMuscleInfo>,
    ): ExerciseMuscleInfo? = resolveCatalogExerciseInfoInIndex(
        index = exerciseIndex,
        catalogConfigurationId = exercise.catalogConfigurationId,
        exerciseDbId = exercise.exerciseDbId,
        exerciseId = exercise.exerciseId,
        exerciseName = exercise.name,
    )

    private fun selectedTechnicalMuscles(
        exercise: Exercise,
        catalogInfo: ExerciseMuscleInfo?,
    ): List<InvolvedMuscle>? {
        val selected = exercise.selectedAspects?.takeIf { it.isNotEmpty() } ?: return null
        val info = catalogInfo ?: return null
        val options = info.catalogOptionAxes.orEmpty().mapNotNull { aspect ->
            val optionId = selected[aspect.id] ?: return@mapNotNull null
            aspect.options.firstOrNull { it.id == optionId }
        }
        if (options.isEmpty()) return null
        return TechnicalAspectEngine.computeEffectiveMuscles(info.involvedMuscles, options).effectiveMuscles
    }
}
