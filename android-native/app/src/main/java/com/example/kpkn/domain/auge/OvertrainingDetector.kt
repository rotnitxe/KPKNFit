package com.example.kpkn.domain.auge

import com.example.kpkn.data.exercises.catalogExerciseIndex
import com.example.kpkn.data.exercises.catalogSearchRedirects
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.PostSessionFeedback
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.training.VolumeCalculator

/**
 * Detects overtrained muscle groups from volume, pain, fatigue, DOMS/strength and load drops.
 * Pure domain logic extracted from HomeViewModel.
 */
object OvertrainingDetector {

    fun detectOvertrainedMuscles(
        program: Program,
        historyLogs: List<WorkoutLog>,
        feedbacks: List<PostSessionFeedback>,
        exerciseDb: Map<String, ExerciseMuscleInfo> = catalogExerciseIndex(),
    ): List<String> {
        val logs = historyLogs.filter { it.programId == program.id }
        if (logs.isEmpty() || program.volumeRecommendations.isEmpty()) return emptyList()

        val overtrainedList = mutableListOf<String>()
        val exerciseList = exerciseDb.values.toList()
        val weeksCount = (logs.size / 3).coerceAtLeast(1)

        val completedVolumes = VolumeCalculator.calculateCompletedWeeklyMuscleVolume(
            logs = logs,
            exerciseList = exerciseList,
            aliases = catalogSearchRedirects(),
            weeksCount = weeksCount,
        )

        program.volumeRecommendations.forEach { rec ->
            val muscle = rec.muscleGroup
            val canonical = VolumeCalculator.normalizeCanonicalMuscleGroup(muscle)
            val mrv = rec.maxRecoverableVolume
            val normalizedMuscleLower = canonical.lowercase()

            val completedSets = completedVolumes.find { it.muscleName == canonical }?.weeklySets ?: 0.0
            val factorVol = completedSets > mrv

            val factorPain = logs.take(5).any { log ->
                log.discomforts.any { d ->
                    val dl = d.lowercase()
                    dl.contains(normalizedMuscleLower) ||
                        (normalizedMuscleLower.contains("hombro") && dl.contains("deltoid")) ||
                        (normalizedMuscleLower.contains("cuádriceps") && dl.contains("rodilla")) ||
                        (normalizedMuscleLower.contains("espalda baja") && dl.contains("lumbar"))
                }
            }

            val factorSystemic = logs.firstOrNull()?.fatigueLevel?.let { it >= 8 } ?: false

            val muscleLogs = feedbacks.filter { fb ->
                fb.muscleFeedback.keys.any { key ->
                    VolumeCalculator.normalizeCanonicalMuscleGroup(key).lowercase() == normalizedMuscleLower
                }
            }.take(3)

            var totalDoms = 0.0
            var totalStr = 0.0
            var fbCount = 0
            muscleLogs.forEach { fb ->
                val entryKey = fb.muscleFeedback.keys.find { key ->
                    VolumeCalculator.normalizeCanonicalMuscleGroup(key).lowercase() == normalizedMuscleLower
                } ?: return@forEach
                val entry = fb.muscleFeedback[entryKey] ?: return@forEach
                totalDoms += entry.doms.toDouble()
                totalStr += entry.strengthCapacity.toDouble()
                fbCount++
            }
            val factorLocal = if (fbCount > 0) {
                (totalDoms / fbCount) >= 3.5 || (totalStr / fbCount) <= 5.0
            } else {
                false
            }

            val primaryExercises = exerciseList.filter { db ->
                db.involvedMuscles.any {
                    it.role == MuscleRole.PRIMARY &&
                        VolumeCalculator.normalizeCanonicalMuscleGroup(it.muscle).lowercase() == normalizedMuscleLower
                }
            }.map { it.id.lowercase() }

            var factorProg = false
            val exercisesWithLogs = logs.flatMap { it.completedExercises }
                .filter { it.exerciseDbId?.lowercase() in primaryExercises }
                .groupBy { it.exerciseDbId?.lowercase() }

            for ((_, exLogs) in exercisesWithLogs) {
                if (exLogs.size >= 2) {
                    val recentWeight = exLogs.first().sets.firstOrNull { !it.skipped }?.weight ?: 0.0
                    val olderWeight = exLogs.last().sets.firstOrNull { !it.skipped }?.weight ?: 0.0
                    if (recentWeight < olderWeight && recentWeight > 0.0) {
                        factorProg = true
                        break
                    }
                }
            }

            var activeCount = 0
            if (factorVol) activeCount++
            if (factorPain) activeCount++
            if (factorSystemic) activeCount++
            if (factorLocal) activeCount++
            if (factorProg) activeCount++

            if (activeCount >= 3) {
                overtrainedList.add(canonical)
            }
        }

        return overtrainedList
    }
}
