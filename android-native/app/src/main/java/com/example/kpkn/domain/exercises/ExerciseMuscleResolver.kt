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
    ): ExerciseMuscleInfo? {
        // Try every identity the exercise carries, in order of specificity,
        // until one resolves. Volume counters are indexed by definition id
        // (snapshot) while AUGE/history lookups also index configuration ids;
        // a configuration miss must fall through to the exercise/db/instance
        // id instead of silently returning null (which drops the sets in
        // volume accounting).
        val candidates = listOfNotNull(
            exercise.catalogConfigurationId,
            exercise.exerciseDbId,
            exercise.exerciseId,
        ).map { it.trim().lowercase() }.distinct()
        candidates.forEach { id ->
            exerciseIndex[id]?.let { return it }
        }
        // Last resort: restore the name-equality fallback that was in place
        // before the v2 reconstruction, so legacy exercises (which carry
        // neither configuration nor catalog ids) still resolve.
        return exerciseIndex.values.firstOrNull {
            it.name.equals(exercise.name, ignoreCase = true)
        }
    }

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
