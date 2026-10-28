package com.example.kpkn.domain.training.calibration

import com.example.kpkn.data.models.CalibrationResponseState
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.MuscleFeedbackEntry
import com.example.kpkn.data.models.PostSessionFeedback
import com.example.kpkn.data.models.VolumeCalibrationResponses
import com.example.kpkn.data.models.WorkoutLog
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Respuestas derivadas del calibrador (IMP-02, T-90): umbrales de la guía y precedencia de lo declarado. */
class CalibrationDerivationTest {
    private val end = LocalDate.of(2026, 10, 25)
    private val zone = ZoneId.of("America/Santiago")

    /** Tres sesiones por semana en las cuatro últimas semanas (12 registros, 4 semanas distintas). */
    private fun twelveLogs(sets: List<CompletedSet> = emptyList()): List<WorkoutLog> =
        (0..3).flatMap { week ->
            listOf(2, 4, 6).map { back ->
                WorkoutLog(
                    id = "l-$week-$back", programId = "p", sessionId = "s", sessionName = "Sesión",
                    date = end.minusDays((back + 7 * week).toLong()).toString(), durationMinutes = 40,
                    programRunId = "run", cycleNumber = 1, weekId = "w",
                    completedExercises = if (week == 0 && back == 2 && sets.isNotEmpty()) listOf(CompletedExercise("e", "Ejercicio", sets = sets)) else emptyList(),
                )
            }
        }

    private fun input(
        logs: List<WorkoutLog> = twelveLogs(),
        planned: Int? = 12,
        squat: Double? = null, bench: Double? = null, deadlift: Double? = null, body: Double? = null,
        feedbacks: List<PostSessionFeedback> = emptyList(),
    ) = CalibrationDerivationInput(logs, "p", "run", end, zone, planned, squat, bench, deadlift, body, feedbacks)

    private fun set(id: Int, rir: Int?, failed: Boolean = false) = CompletedSet(id = "s$id", weight = 50.0, reps = 8, rir = rir, isFailure = failed)

    private fun joint(day: Int, pain: Boolean = true) = PostSessionFeedback(
        logId = "l$day", date = end.minusDays(day.toLong()).toString(),
        muscleFeedback = mapOf("pecho" to MuscleFeedbackEntry(jointPain = pain)),
    )

    @Test fun twelve_of_twelve_planned_sessions_is_consistency_3() {
        val derived = CalibrationEvidence.derive(input())!!
        assertEquals(3, derived.consistency.value)
        assertEquals("12 de 12 sesiones", derived.consistency.detail)
    }

    @Test fun consistency_thresholds_follow_the_ratio_of_recorded_to_planned() {
        assertEquals(3, CalibrationEvidence.derive(input(planned = 14))!!.consistency.value) // 12/14 = 0,857
        assertEquals(2, CalibrationEvidence.derive(input(planned = 15))!!.consistency.value) // 0,8
        assertEquals(2, CalibrationEvidence.derive(input(planned = 20))!!.consistency.value) // 0,6
        assertEquals(1, CalibrationEvidence.derive(input(planned = 21))!!.consistency.value) // 0,571
    }

    @Test fun without_a_plan_consistency_uses_sessions_per_week() {
        assertEquals(3, CalibrationEvidence.derive(input(planned = null))!!.consistency.value) // 12 en 4 semanas = 3,0
        val eight = twelveLogs().filterIndexed { index, _ -> index % 3 != 0 } // 8 registros
        val derived = CalibrationEvidence.derive(input(logs = eight, planned = null))!!
        assertEquals(2, derived.consistency.value)
        assertEquals("2,0 sesiones por semana", derived.consistency.detail)
    }

    @Test fun fewer_than_eight_sessions_or_four_weeks_derive_nothing() {
        assertNull(CalibrationEvidence.derive(input(logs = twelveLogs().take(6))))
        assertNull(CalibrationEvidence.derive(input(logs = twelveLogs().filter { it.date >= end.minusDays(20).toString() })))
        assertNotNull(CalibrationEvidence.derive(input()))
    }

    @Test fun strength_uses_the_mean_ratio_against_the_references() {
        // 150/80 = 1,875 (÷1,5 = 1,25); 100/80 = 1,25 (÷1,0 = 1,25); 180/80 = 2,25 (÷1,75 = 1,29): media 1,26 ≥ 1,2.
        assertEquals(3, CalibrationEvidence.derive(input(squat = 150.0, bench = 100.0, deadlift = 180.0, body = 80.0))!!.strength.value)
        // 100/80 = 1,25 (÷1,5 = 0,83) ⇒ 0,75 ≤ media < 1,2.
        assertEquals(2, CalibrationEvidence.derive(input(squat = 100.0, body = 80.0))!!.strength.value)
        assertEquals(1, CalibrationEvidence.derive(input(squat = 60.0, bench = 40.0, deadlift = 80.0, body = 80.0))!!.strength.value)
    }

    @Test fun strength_without_body_weight_or_lifts_is_2() {
        assertEquals(2, CalibrationEvidence.derive(input(squat = 150.0, bench = 100.0, deadlift = 180.0))!!.strength.value)
        assertEquals(2, CalibrationEvidence.derive(input(body = 80.0))!!.strength.value)
    }

    @Test fun technique_needs_twenty_sets_with_rir_and_grades_the_missed_fraction() {
        assertEquals(2, CalibrationEvidence.derive(input())!!.technique.value) // sin series con RIR
        val precise = (1..20).map { set(it, rir = 2) } // 0 falladas
        assertEquals(3, CalibrationEvidence.derive(input(logs = twelveLogs(precise)))!!.technique.value)
        val someMissed = (1..20).map { set(it, rir = 2, failed = it <= 2) } // 2/20 = 10 %
        assertEquals(2, CalibrationEvidence.derive(input(logs = twelveLogs(someMissed)))!!.technique.value)
        val manyMissed = (1..20).map { set(it, rir = 2, failed = it <= 4) } // 4/20 = 20 %
        assertEquals(1, CalibrationEvidence.derive(input(logs = twelveLogs(manyMissed)))!!.technique.value)
        val failedAtZero = (1..20).map { set(it, rir = 0, failed = true) } // falladas con RIR 0 no cuentan
        assertEquals(3, CalibrationEvidence.derive(input(logs = twelveLogs(failedAtZero)))!!.technique.value)
        val fewSets = (1..19).map { set(it, rir = 2, failed = true) }
        assertEquals(2, CalibrationEvidence.derive(input(logs = twelveLogs(fewSets)))!!.technique.value)
    }

    @Test fun mobility_counts_joint_discomfort_feedbacks_in_thirty_days() {
        assertEquals(3, CalibrationEvidence.derive(input())!!.mobility.value)
        assertEquals(2, CalibrationEvidence.derive(input(feedbacks = listOf(joint(3), joint(40))))!!.mobility.value) // el de 40 días no cuenta
        assertEquals(2, CalibrationEvidence.derive(input(feedbacks = listOf(joint(3), joint(5))))!!.mobility.value)
        val three = CalibrationEvidence.derive(input(feedbacks = listOf(joint(1), joint(5), joint(20))))!!
        assertEquals(1, three.mobility.value)
        assertEquals("3 molestias articulares en 30 días", three.mobility.detail)
        assertEquals(3, CalibrationEvidence.derive(input(feedbacks = listOf(joint(2, pain = false))))!!.mobility.value)
    }

    @Test fun declared_answers_win_and_derived_ones_replace_only_suggested_values() {
        val derived = CalibrationEvidence.derive(input(squat = 150.0, bench = 100.0, deadlift = 180.0, body = 80.0))!!
        val declared = VolumeCalibrationResponses(1, 1, 1, 1, CalibrationResponseState.DECLARED)
        val kept = CalibrationEvidence.resolve(declared, derived)
        assertEquals(listOf(1, 1, 1, 1), kept.map { it.value })
        assertEquals(setOf(CalibrationResponseState.DECLARED), kept.map { it.origin }.toSet())
        assertEquals("Declarada 1/3", kept.first().label())

        val suggested = VolumeCalibrationResponses(2, 2, 1, 2, CalibrationResponseState.SUGGESTED)
        val replaced = CalibrationEvidence.resolve(suggested, derived)
        assertEquals(setOf(CalibrationResponseState.DERIVED), replaced.map { it.origin }.toSet())
        assertEquals("Según tus registros 3/3 (12 de 12 sesiones)", replaced.first { it.trait == CalibrationTrait.CONSISTENCY }.label())

        // Sin registros suficientes se conserva lo sugerido; sin nada guardado ni derivado no hay respuestas.
        val stillSuggested = CalibrationEvidence.resolve(suggested, null)
        assertEquals("Sugerido por KPKN 2/3", stillSuggested.first().label())
        assertEquals(emptyList<ResolvedCalibrationAnswer>(), CalibrationEvidence.resolve(null, null))
        assertEquals(4, CalibrationEvidence.resolve(null, derived).size)
    }
}
