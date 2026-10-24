package com.example.kpkn.domain.discomfort

import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.DISCOMFORT_CATALOG
import com.example.kpkn.data.models.ExerciseDiscomfortReport
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.PROFESSIONAL_ADVICE
import com.example.kpkn.data.models.WorkoutLog
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordedDiscomfortHistoryTest {

    private val now: Instant = Instant.parse("2026-10-16T12:00:00Z")
    private val nbsp = ' '

    // ── Datos de prueba ──

    private val squatMuscles = listOf(
        InvolvedMuscle("Cuádriceps", MuscleRole.PRIMARY),
        InvolvedMuscle("Glúteos", MuscleRole.SECONDARY),
        InvolvedMuscle("Erectores Espinales", MuscleRole.STABILIZER),
    )
    private val curlMuscles = listOf(InvolvedMuscle("Bíceps", MuscleRole.PRIMARY))
    private val lungeMuscles = listOf(
        InvolvedMuscle("Cuádriceps", MuscleRole.PRIMARY),
        InvolvedMuscle("Glúteos", MuscleRole.PRIMARY),
    )

    private fun set(weight: Double = 100.0, reps: Int = 8, rir: Int? = 3, isFailure: Boolean = false, warmup: Boolean = false) =
        CompletedSet(
            id = "s-$weight-$reps-$rir-$isFailure",
            weight = weight,
            reps = reps,
            rir = rir,
            isFailure = isFailure,
            isWarmup = warmup,
        )

    private fun sets(count: Int, weight: Double = 100.0, reps: Int = 8, rir: Int? = 3) =
        List(count) { set(weight, reps, rir) }

    private fun todayExercise(
        id: String = "squat",
        name: String = "Sentadilla",
        muscles: List<InvolvedMuscle> = squatMuscles,
        sets: List<CompletedSet> = sets(3),
        technicalQuality: Int? = null,
        axial: Double? = 0.7,
        impact: Boolean = false,
        unilateral: Boolean = false,
    ) = DiscomfortSessionExercise(
        exerciseId = id,
        name = name,
        muscles = muscles,
        sets = sets,
        technicalQuality = technicalQuality,
        
        
        isUnilateral = unilateral,
    )

    private fun session(vararg exercises: DiscomfortSessionExercise, focus: String? = null) =
        DiscomfortSession(exercises.toList(), focus)

    private fun history(vararg logs: WorkoutLog) = DiscomfortHistory(logs.toList(), now)

    private fun daysAgo(days: Long): String = now.minus(days, ChronoUnit.DAYS).toString()

    private fun completed(
        id: String = "squat",
        name: String = "Sentadilla",
        muscles: List<InvolvedMuscle> = squatMuscles,
        sets: List<CompletedSet> = sets(4),
    ) = CompletedExercise(exerciseId = id, exerciseName = name, effectiveMuscles = muscles, sets = sets)

    private var logCounter = 0
    private fun log(
        days: Long,
        vararg exercises: CompletedExercise,
        reports: List<ExerciseDiscomfortReport> = emptyList(),
        stillPresent: List<String> = emptyList(),
    ) = WorkoutLog(
        id = "log-${logCounter++}",
        programId = "p",
        sessionId = "s",
        sessionName = "Pierna",
        date = daysAgo(days),
        durationMinutes = 60,
        completedExercises = exercises.toList(),
        postExerciseReports = reports,
        stillPresentDiscomfortIds = stillPresent,
    )

    private fun report(vararg ids: String, exerciseId: String = "squat") = ExerciseDiscomfortReport(
        exerciseId = exerciseId,
        exerciseName = "Sentadilla",
        technicalQuality = 8,
        discomfortIds = ids.toList(),
    )

    /** Cuatro semanas anteriores con [baseline] series de sentadilla cada una (a 10, 17, 24 y 31 días). */
    private fun baselineLogs(baseline: Int): List<WorkoutLog> =
        listOf(10L, 17L, 24L, 31L).map { log(it, completed(sets = sets(baseline))) }

    private fun historyOf(logs: List<WorkoutLog>) = DiscomfortHistory(logs, now)

    private fun explain(id: String, s: DiscomfortSession, h: DiscomfortHistory) =
        DiscomfortCauseEngine.explain(id, s, h)

    private fun DiscomfortInsight.todayOf(kind: DiscomfortEvidenceKind) = today.firstOrNull { it.kind == kind }
    private fun DiscomfortInsight.weekOf(kind: DiscomfortEvidenceKind) = week.firstOrNull { it.kind == kind }

    // ── Sin evidencia ──

    @Test
    fun `previousReports devuelve las reportadas con el ejercicio la mas reciente primero`() {
        val s = session(todayExercise(), focus = "squat")
        val hist = historyOf(
            listOf(
                log(9, completed(), reports = listOf(report("lumbar"))),
                log(2, completed(), reports = listOf(report("knee_patellar"))),
                log(16, completed(), reports = listOf(report("lumbar"))),
                log(
                    1,
                    completed("curl", "Curl", curlMuscles),
                    reports = listOf(report("elbow_medial", exerciseId = "curl")),
                ),
            ),
        )
        val prev = RecordedDiscomfortHistory.previousReports(s, hist)
        assertEquals(listOf("knee_patellar", "lumbar"), prev.map { it.discomfortId })
        assertEquals(2, prev.last().sessionsCount)
        assertTrue(prev.first().lastReportedAtMs > prev.last().lastReportedAtMs)
    }

}
