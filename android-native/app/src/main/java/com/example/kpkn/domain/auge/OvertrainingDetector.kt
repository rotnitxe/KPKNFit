package com.example.kpkn.domain.auge

import com.example.kpkn.data.exercises.catalogExerciseIndex
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.PostSessionFeedback
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.training.VolumeCalculator
import com.example.kpkn.domain.training.TrainingVolumeResult
import com.example.kpkn.domain.training.TrainingWindow

/**
 * Detects overtrained muscle groups from volume, pain, fatigue, DOMS/strength and load drops.
 * Pure domain logic extracted from HomeViewModel.
 */
object OvertrainingDetector {

    /** Promedio de la misma ventana que usa el estado muscular del detalle del programa. */
    fun completedVolume(
        program: Program,
        historyLogs: List<WorkoutLog>,
        exerciseDb: Map<String, ExerciseMuscleInfo> = catalogExerciseIndex(),
        window: TrainingWindow = TrainingWindow.last28Days(),
    ): TrainingVolumeResult = VolumeCalculator.calculateCompletedVolume(
        logs = historyLogs,
        exerciseIndex = exerciseDb,
        window = window,
        programId = program.id,
    )

    fun detectOvertrainedMuscles(
        program: Program,
        historyLogs: List<WorkoutLog>,
        feedbacks: List<PostSessionFeedback>,
        exerciseDb: Map<String, ExerciseMuscleInfo> = catalogExerciseIndex(),
        window: TrainingWindow = TrainingWindow.last28Days(),
    ): List<String> {
        val logs = window.selectLogs(historyLogs, program.id).logs
        if (logs.isEmpty() || program.volumeRecommendations.isEmpty()) return emptyList()

        val overtrainedList = mutableListOf<String>()
        val exerciseList = exerciseDb.values.toList()
        val sortedLogs = logs
        val completedVolumes = completedVolume(program, historyLogs, exerciseDb, window)

        program.volumeRecommendations.forEach { rec ->
            val muscle = rec.muscleGroup
            val canonical = VolumeCalculator.normalizeCanonicalMuscleGroup(muscle)
            val mrv = rec.maxRecoverableVolume
            val normalizedMuscleLower = canonical.lowercase()

            val completedSets = completedVolumes.muscles[canonical]?.sets ?: 0.0
            val factorVol = completedSets > mrv

            val factorPain = sortedLogs.take(5).any { log ->
                log.discomforts.any { d ->
                    val dl = d.lowercase()
                    dl.contains(normalizedMuscleLower) ||
                        (normalizedMuscleLower.contains("hombro") && dl.contains("deltoid")) ||
                        (normalizedMuscleLower.contains("cuádriceps") && dl.contains("rodilla")) ||
                        (normalizedMuscleLower.contains("espalda baja") && dl.contains("lumbar"))
                }
            }

            val factorSystemic = sortedLogs.firstOrNull()?.fatigueLevel?.let { it >= 8 } ?: false

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
            val exercisesWithLogs = sortedLogs.flatMap { it.completedExercises }
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
