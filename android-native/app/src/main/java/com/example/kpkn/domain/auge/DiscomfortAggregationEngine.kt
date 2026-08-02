package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.ArticularBattery
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.DISCOMFORT_CATALOG_BY_ID
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.PostExerciseFeedback

data class SessionDiscomfortSummary(
    val discomfortId: String,
    val label: String,
    val reportedInExercises: List<String>,
    val articularVolumeScore: Int,
    val relatedArticular: List<ArticularBattery>,
)

object DiscomfortAggregationEngine {

    fun computeSessionDiscomfortSummary(
        postExerciseFeedbackByExerciseId: Map<String, PostExerciseFeedback>,
        completedExercises: List<CompletedExercise>,
        exerciseDb: Map<String, ExerciseMuscleInfo>,
    ): List<SessionDiscomfortSummary> {
        val articularSetCounts = mutableMapOf<ArticularBattery, Int>()

        for (exercise in completedExercises) {
            val workingSets = exercise.sets.count { !it.isWarmup && !it.skipped }
            if (workingSets == 0) continue

            val dbId = exercise.catalogConfigurationId ?: exercise.exerciseDbId ?: exercise.exerciseId
            val dbInfo = dbId?.trim()?.lowercase()?.let(exerciseDb::get)
            if (dbInfo == null) continue

            val involvedMuscles = exercise.effectiveMuscles?.takeIf { it.isNotEmpty() }
                ?: dbInfo.involvedMuscles
            val relatedArticulars = involvedMuscles
                .flatMap { im -> AugeTtcEngine.articularBatteriesFor(im.muscle, im.emphasis) }
                .distinct()

            for (articular in relatedArticulars) {
                articularSetCounts[articular] = (articularSetCounts[articular] ?: 0) + workingSets
            }
        }

        val discomfortExerciseMap = postExerciseFeedbackByExerciseId
            .filter { (_, feedback) -> feedback.discomfortIds.any { it != "none" } }
            .flatMap { (exerciseId, feedback) ->
                feedback.discomfortIds
                    .filter { it != "none" }
                    .map { it to exerciseId }
            }
            .groupBy({ it.first }, { it.second })

        val summaries = discomfortExerciseMap.map { (discomfortId, exerciseIds) ->
            val entry = DISCOMFORT_CATALOG_BY_ID[discomfortId]
            val label = entry?.label ?: discomfortId
            val relatedArticular = entry?.relatedArticular.orEmpty()

            val volumeScore = relatedArticular.sumOf { articular ->
                articularSetCounts[articular] ?: 0
            }

            val exerciseNames = exerciseIds.mapNotNull { id ->
                completedExercises.firstOrNull { it.exerciseId == id }?.exerciseName
            }.distinct()

            SessionDiscomfortSummary(
                discomfortId = discomfortId,
                label = label,
                reportedInExercises = exerciseNames,
                articularVolumeScore = volumeScore,
                relatedArticular = relatedArticular,
            )
        }

        return summaries
            .sortedByDescending { it.articularVolumeScore }
            .take(5)
    }
}
