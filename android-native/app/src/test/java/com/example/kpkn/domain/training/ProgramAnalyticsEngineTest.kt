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

    @Test
    fun `cobertura semanal divide por todas las semanas prescritas incluida la vacia`() {
        val f = TrainingVolumeFixtures
        val weeks = listOf(
            f.week("w1", f.today.minusWeeks(2), sessions = 3),
            f.week("w2", f.today.minusWeeks(1), sessions = 3),
            f.week("w3", f.today),
        )
        val report = ProgramAnalyticsEngine.analyze(f.program(weeks), emptyList(), f.catalog.values.toList())
        assertEquals(4.0, report.coverage.musclesByWeeklySets.single { it.name == "Pectorales" }.value, 1e-9)
        assertEquals(4.0, report.coverage.directIndirectByMuscle.single { it.muscle == "Pectorales" }.directSets, 1e-9)
        assertEquals(32.0, report.coverage.repsByMuscle.single { it.name == "Pectorales" }.value, 1e-9)
        assertEquals(9.0, report.coverage.timeMinutesByMuscle.single { it.name == "Pectorales" }.value, 1e-9)
    }

    @Test
    fun `configuraciones v2 usan el mismo indice que volumen y no dejan la cobertura vacia`() {
        val configuration = ExerciseMuscleInfo(
            id = "press-config-v2", name = "Press", force = "Empuje", bodyPart = "upper",
            involvedMuscles = listOf(InvolvedMuscle("Pectorales", MuscleRole.PRIMARY, 1.0)),
        )
        val exercise = Exercise(
            id = "ocurrencia", name = "Press", catalogDefinitionId = "press-def",
            catalogConfigurationId = configuration.id, sets = workSets(4),
        )
        val program = programWithExercises(exercise)
        val report = ProgramAnalyticsEngine.analyze(
            program, emptyList(), exerciseCatalog = emptyList(), exerciseIndex = mapOf(configuration.id to configuration),
        )
        assertEquals(4.0, report.coverage.musclesByWeeklySets.single { it.name == "Pectorales" }.value, 1e-9)
        assertTrue(report.balance.movementPatterns.isNotEmpty())
    }

    @Test
    fun `el desglose de cobertura no suma roles de distintas cabezas del mismo grupo`() {
        val muscles = listOf(
            InvolvedMuscle("Deltoides", MuscleRole.PRIMARY, 1.0, "anterior"),
            InvolvedMuscle("Deltoides", MuscleRole.SECONDARY, 0.5, "medio"),
        )
        val program = programWithExercises(Exercise("e", "Press", effectiveMuscles = muscles, sets = workSets(3)))
        val report = ProgramAnalyticsEngine.analyze(program, emptyList(), emptyList())
        val roles = report.coverage.directIndirectByMuscle.single { it.muscle == "Deltoides" }
        val total = report.coverage.musclesByWeeklySets.single { it.name == "Deltoides" }.value
        assertEquals(3.0, total, 1e-9)
        assertEquals(total, roles.directSets + roles.indirectSets, 1e-9)
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
            
            
            
            
            
            involvedMuscles = listOf(InvolvedMuscle("Erectores Espinales", MuscleRole.PRIMARY, 0.8)),
        ),
        ExerciseMuscleInfo(
            id = "pendlay",
            name = "Remo Pendlay",
            force = "Tirón",
            chain = "posterior",
            bodyPart = "upper",
            
            
            
            
            
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
