package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseDiscomfortReport
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.domain.training.insights.LimitingFactorCatalog
import com.example.kpkn.domain.training.insights.MuscleIntensityAccounting
import com.example.kpkn.screens.progress.RegionKind
import com.example.kpkn.screens.progress.design.MuscleIntensityHeatmapModel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PostExerciseLimitingFactorFinishTest {

    @Test
    fun `draft resolves limiting factors, round-trips through WorkoutLog JSON, and feeds FinishModel & heatmap`() {
        val exercise = Exercise(
            id = "ex1",
            name = "Sentadilla trasera",
            sets = emptyList(),
        )
        val draft = PostExerciseFeedbackDraft.from(listOf(exercise), emptyMap())
        draft.toggleLimitingFactor("espalda")
        draft.toggleLimitingFactor("cardio")

        val payloads = buildPostExerciseFeedbackPayloads(
            feedbackExercises = listOf(exercise),
            draft = draft,
            previousFeedback = emptyMap(),
            linked = emptyList(),
            canonicalKeyOf = { "sentadilla" },
            nameOf = { it.name },
            primaryMusclesOf = { listOf("quadriceps", "gluteus_maximus") },
        )

        val fb = payloads.single()
        assertEquals(2, fb.limitingFactors.size)
        val backFactor = fb.limitingFactors.first { it.id == "espalda" }
        assertTrue(backFactor.autoLinkedCore)
        assertTrue("core" in backFactor.linkedMuscleIds)

        // JSON round-trip in WorkoutLog preserves limitingFactors
        val log = WorkoutLog(
            id = "log-1",
            programId = "p-1",
            sessionId = "s-1",
            sessionName = "Pierna A",
            date = "2026-11-24T12:00:00Z",
            durationMinutes = 45,
            postExerciseReports = listOf(
                ExerciseDiscomfortReport(
                    exerciseId = fb.exerciseId,
                    exerciseName = fb.exerciseName,
                    technicalQuality = fb.technicalQuality,
                    limitingFactors = fb.limitingFactors,
                ),
            ),
        )
        val codec = Json { ignoreUnknownKeys = true }
        val decoded = codec.decodeFromString<WorkoutLog>(codec.encodeToString(log))
        assertEquals(2, decoded.postExerciseReports.single().limitingFactors.size)

        // FinishModel & Heatmap
        val model = buildFinishModel(
            name = "Pierna A",
            completedExercises = listOf(
                CompletedExercise(
                    exerciseId = "ex1",
                    exerciseName = "Sentadilla trasera",
                    effectiveMuscles = listOf(
                        InvolvedMuscle("quadriceps", role = MuscleRole.PRIMARY),
                    ),
                    sets = listOf(CompletedSet(id = "s1", weight = 100.0, reps = 6, rir = 1)),
                ),
            ),
            durationMinutes = 45,
            effort = null,
            presentation = FinishPresentationContext(
                postExerciseFeedbackByExerciseId = mapOf("ex1" to fb),
            ),
            lifts = emptyList(),
            plannedSets = 1,
            targetMinutes = 45,
        )
        assertTrue(model.intensitySummary.hasIntensityData)
        assertEquals(2, model.intensitySummary.limitingFactorTallies.size)

        val paints = MuscleIntensityHeatmapModel.paints(model.intensitySummary)
        assertEquals(RegionKind.FILLED, paints["Cuádriceps"]!!.kind)
        assertEquals(RegionKind.FILLED, paints["Core"]!!.kind)
    }
}
