package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WorkoutLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramAnalyticsEngineTest {

    @Test
    fun deadlift_rdl_pendlay_fixture_detects_high_lumbar_fatigue() {
        val program = programWithExercises(
            Exercise("deadlift-plan", "Peso muerto", exerciseDbId = "deadlift", sets = workSets(3)),
            Exercise("rdl-plan", "RDL", exerciseDbId = "rdl", sets = workSets(3)),
            Exercise("pendlay-plan", "Remo Pendlay", exerciseDbId = "pendlay", sets = workSets(3)),
        )
        val report = ProgramAnalyticsEngine.analyze(program, logs = emptyList(), exerciseCatalog = lumbarCatalog())

        assertTrue(report.fatigue.lumbarFatigue >= 18.0)
        assertTrue(report.diagnostics.any { it.id == "high-lumbar-fatigue" })
    }

    @Test
    fun low_adherence_diagnosis_blames_execution_before_program() {
        val program = programWithExercises(
            Exercise("bench-plan", "Press banca", exerciseDbId = "bench", sets = workSets(4)),
            Exercise("row-plan", "Remo", exerciseDbId = "row", sets = workSets(4)),
        )
        val logs = listOf(
            WorkoutLog(
                id = "log-1",
                programId = "program",
                sessionId = "session",
                sessionName = "Día",
                date = "2026-01-01T10:00:00.000Z",
                durationMinutes = 45,
                completedExercises = listOf(
                    CompletedExercise(
                        exerciseId = "bench-plan",
                        exerciseName = "Press banca",
                        exerciseDbId = "bench",
                        sets = listOf(CompletedSet(id = "s1", weight = 80.0, reps = 8)),
                    )
                ),
            )
        )

        val report = ProgramAnalyticsEngine.analyze(program, logs = logs, exerciseCatalog = pushPullCatalog())

        assertTrue(report.adherence.completedExerciseRatio < 1.0)
        assertTrue(report.adherence.diagnosis.contains("Ejecución"))
    }

    @Test
    fun balanced_fixture_has_push_pull_sources_and_real_coverage() {
        val program = programWithExercises(
            Exercise("bench-plan", "Press banca", exerciseDbId = "bench", sets = workSets(4)),
            Exercise("row-plan", "Remo", exerciseDbId = "row", sets = workSets(4)),
        )

        val report = ProgramAnalyticsEngine.analyze(program, logs = emptyList(), exerciseCatalog = pushPullCatalog())

        assertEquals(1.0, report.balance.pushPullRatio.ratio, 0.01)
        assertTrue(report.coverage.musclesByWeeklySets.any { it.name == "Pectorales" && it.value > 0.0 })
        assertTrue(report.coverage.musclesByWeeklySets.any { it.name == "Dorsales" && it.value > 0.0 })
        assertTrue(report.balance.movementPatterns.any { it.label == "Empuje horizontal" })
        assertTrue(report.balance.movementPatterns.any { it.label == "Tirón horizontal" })
        assertTrue(report.coverage.stabilityDemand > 0.0)
        assertTrue(report.efficiency.blockIdentity.isNotBlank())
    }

    private fun programWithExercises(vararg exercises: Exercise): Program =
        Program(
            id = "program",
            name = "Programa",
            macrocycles = listOf(
                Macrocycle(
                    id = "macro",
                    name = "Macro",
                    blocks = listOf(
                        Block(
                            id = "block",
                            name = "Bloque",
                            mesocycles = listOf(
                                Mesocycle(
                                    id = "meso",
                                    name = "Meso",
                                    weeks = listOf(
                                        ProgramWeek(
                                            id = "week",
                                            name = "S1",
                                            sessions = listOf(
                                                Session(
                                                    id = "session",
                                                    name = "Día",
                                                    exercises = exercises.toList(),
                                                )
                                            ),
                                        )
                                    ),
                                )
                            ),
                        )
                    ),
                )
            ),
        )

    private fun workSets(count: Int): List<ExerciseSet> =
        (1..count).map { ExerciseSet(id = "set-$it", targetReps = 8, weight = 100.0) }

    private fun lumbarCatalog(): List<ExerciseMuscleInfo> = listOf(
        ExerciseMuscleInfo(
            id = "deadlift",
            name = "Peso muerto",
            force = "Bisagra",
            chain = "posterior",
            bodyPart = "lower",
            efc = 4.0,
            cnc = 4.5,
            ssc = 1.8,
            ttc = 2.5,
            axialLoadFactor = 1.0,
            involvedMuscles = listOf(
                InvolvedMuscle("Erectores Espinales", MuscleRole.PRIMARY, 1.0),
                InvolvedMuscle("Glúteos", MuscleRole.PRIMARY, 0.8),
            ),
        ),
        ExerciseMuscleInfo(
            id = "rdl",
            name = "RDL",
            force = "Bisagra",
            chain = "posterior",
            bodyPart = "lower",
            efc = 3.5,
            cnc = 3.5,
            ssc = 1.4,
            ttc = 2.0,
            axialLoadFactor = 0.8,
            involvedMuscles = listOf(InvolvedMuscle("Erectores Espinales", MuscleRole.PRIMARY, 0.8)),
        ),
        ExerciseMuscleInfo(
            id = "pendlay",
            name = "Remo Pendlay",
            force = "Tirón",
            chain = "posterior",
            bodyPart = "upper",
            efc = 3.0,
            cnc = 3.0,
            ssc = 1.2,
            ttc = 1.6,
            axialLoadFactor = 0.6,
            involvedMuscles = listOf(
                InvolvedMuscle("Dorsales", MuscleRole.PRIMARY, 1.0),
                InvolvedMuscle("Erectores Espinales", MuscleRole.STABILIZER, 0.5),
            ),
        ),
    )

    private fun pushPullCatalog(): List<ExerciseMuscleInfo> = listOf(
        ExerciseMuscleInfo(
            id = "bench",
            name = "Press banca",
            force = "Empuje",
            bodyPart = "upper",
            chain = "anterior",
            involvedMuscles = listOf(InvolvedMuscle("Pectorales", MuscleRole.PRIMARY, 1.0)),
        ),
        ExerciseMuscleInfo(
            id = "row",
            name = "Remo",
            force = "Tirón",
            bodyPart = "upper",
            chain = "posterior",
            involvedMuscles = listOf(InvolvedMuscle("Dorsales", MuscleRole.PRIMARY, 1.0)),
        ),
    )
}
