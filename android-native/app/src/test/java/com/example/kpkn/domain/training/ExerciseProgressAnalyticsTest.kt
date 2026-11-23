package com.example.kpkn.domain.training

import com.example.kpkn.data.models.*
import com.example.kpkn.screens.programdetail.design.progress.ProgressScope
import com.example.kpkn.screens.programdetail.design.progress.buildProgramProgress
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ExerciseProgressAnalyticsTest {
    private val today = LocalDate.of(2026, 10, 24)
    private val zone = ZoneId.of("America/Santiago")

    private fun loggedSet(
        id: String,
        weight: Double,
        reps: Int,
        rir: Int? = null,
        rpe: Double? = null,
    ) = CompletedSet(
        id = id,
        weight = weight,
        reps = reps,
        rir = rir,
        rpe = rpe,
        recordedPayloadV3 = RecordedSetPayload(
            exerciseId = "bench",
            loadInputMode = LoadModeV2.LOAD,
            externalLoad = weight,
            completedReps = reps,
        ),
    )

    @Test
    fun effortMetricComputesAverageRirAndConvertsRpeWhenRirMissing() {
        val log1 = WorkoutLog(
            id = "log-1",
            programId = "prog-1",
            sessionId = "s-1",
            sessionName = "Empuje A",
            date = "2026-10-01",
            durationMinutes = 45,
            completedExercises = listOf(
                CompletedExercise(
                    exerciseId = "bench",
                    exerciseName = "Press de banca",
                    catalogConfigurationId = "barbell_flat",
                    sets = listOf(
                        loggedSet("s1", 80.0, 8, rir = 2),
                        loggedSet("s2", 80.0, 8, rpe = 9.0), // 10 - 9.0 = 1.0 RIR
                    ),
                ),
            ),
        )
        val log2 = WorkoutLog(
            id = "log-2",
            programId = "prog-1",
            sessionId = "s-1",
            sessionName = "Empuje A",
            date = "2026-10-08",
            durationMinutes = 45,
            completedExercises = listOf(
                CompletedExercise(
                    exerciseId = "bench",
                    exerciseName = "Press de banca",
                    catalogConfigurationId = "barbell_flat",
                    sets = listOf(
                        loggedSet("s3", 85.0, 8, rir = 1),
                        loggedSet("s4", 85.0, 7, rir = 0),
                    ),
                ),
            ),
        )

        val report = ExerciseProgress.build(
            logs = listOf(log1, log2),
            exerciseId = "configuration:barbell_flat",
            metric = ExerciseProgressMetric.EFFORT,
            window = ExerciseProgressWindow.ALL,
            today = today,
            zone = zone,
        )

        assertTrue(ExerciseProgressMetric.EFFORT in report.available)
        assertEquals(2, report.points.size)
        assertEquals(1.5, report.points[0].value, 0.001)
        assertEquals(0.5, report.points[1].value, 0.001)
        assertEquals(1.0, report.timelineMeta?.avgRir ?: -1.0, 0.001)
    }

    @Test
    fun buildComputesTimelineAdherenceMilestonesAndSessionLogsAgainstProgram() {
        val plannedExercise = Exercise(
            id = "bench",
            name = "Press de banca",
            catalogConfigurationId = "barbell_flat",
            sets = listOf(
                ExerciseSet(id = "ps1", targetRepsRange = RepRange(6, 8), targetRIR = 2, intensityMode = IntensityMode.RIR),
                ExerciseSet(id = "ps2", targetRepsRange = RepRange(6, 8), targetRIR = 2, intensityMode = IntensityMode.RIR),
            ),
        )
        val program = Program(
            id = "prog-1",
            name = "Fuerza Base",
            timelineStartDate = "2026-09-28",
            macrocycles = listOf(
                Macrocycle(
                    id = "m1",
                    name = "Macro 1",
                    blocks = listOf(
                        Block(
                            id = "b1",
                            name = "Bloque 1",
                            mesocycles = listOf(
                                Mesocycle(
                                    id = "me1",
                                    name = "Meso 1",
                                    weeks = listOf(
                                        ProgramWeek(
                                            id = "w1",
                                            name = "Semana 1",
                                            sessions = listOf(
                                                Session(
                                                    id = "s-1",
                                                    name = "Empuje A",
                                                    exercises = listOf(plannedExercise),
                                                ),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val logs = listOf(
            WorkoutLog(
                id = "log-1",
                programId = "prog-1",
                sessionId = "s-1",
                sessionName = "Empuje A",
                date = "2026-10-01",
                durationMinutes = 45,
                completedExercises = listOf(
                    CompletedExercise(
                        exerciseId = "bench",
                        exerciseName = "Press de banca",
                        catalogConfigurationId = "barbell_flat",
                        sets = listOf(
                            loggedSet("s1", 80.0, 8, rir = 2),
                            loggedSet("s2", 80.0, 7, rir = 2),
                        ),
                    ),
                ),
                weekId = "w1",
            ),
            WorkoutLog(
                id = "log-2",
                programId = "prog-1",
                sessionId = "s-1",
                sessionName = "Empuje A",
                date = "2026-10-08",
                durationMinutes = 45,
                completedExercises = listOf(
                    CompletedExercise(
                        exerciseId = "bench",
                        exerciseName = "Press de banca",
                        catalogConfigurationId = "barbell_flat",
                        sets = listOf(
                            loggedSet("s3", 85.0, 8, rir = 2),
                            loggedSet("s4", 85.0, 8, rir = 1),
                        ),
                    ),
                ),
                weekId = "w1",
            ),
        )

        val report = ExerciseProgress.build(
            logs = logs,
            exerciseId = "configuration:barbell_flat",
            metric = ExerciseProgressMetric.E1RM,
            window = ExerciseProgressWindow.ALL,
            today = today,
            zone = zone,
            programs = listOf(program),
            programId = "prog-1",
        )

        val meta = report.timelineMeta
        assertNotNull(meta)
        assertEquals(LocalDate.of(2026, 9, 28), meta!!.firstPlannedDate)
        assertEquals(LocalDate.of(2026, 10, 1), meta.firstLoggedDate)
        assertEquals(2, meta.totalSessions)
        assertEquals(4, meta.totalEffectiveSets)

        val adherence = report.adherence
        assertNotNull(adherence)
        assertEquals(4, adherence!!.setsPlanned)
        assertEquals(4, adherence.setsDone)
        assertEquals(100, adherence.setsCompliancePct)
        assertEquals(100, adherence.repsInTargetPct)
        assertEquals(100, adherence.rirHitRatePct)

        assertTrue(report.milestones.any { it.title.contains("Incluido en el programa") })
        assertTrue(report.milestones.any { it.isPr && it.title.contains("Nuevo PR") })
        assertEquals(2, report.sessionLogs.size)
        assertEquals(2, report.sessionLogs.first().sets.size)
    }

    @Test
    fun programProgressIncludesBothLoggedAndPlannedExercises() {
        val program = Program(
            id = "prog-1",
            name = "Hipertrofia",
            macrocycles = listOf(
                Macrocycle(
                    id = "m1",
                    name = "Macro 1",
                    blocks = listOf(
                        Block(
                            id = "b1",
                            name = "Bloque 1",
                            mesocycles = listOf(
                                Mesocycle(
                                    id = "me1",
                                    name = "Meso 1",
                                    weeks = listOf(
                                        ProgramWeek(
                                            id = "w1",
                                            name = "Semana 1",
                                            sessions = listOf(
                                                Session(
                                                    id = "s-1",
                                                    name = "Torso",
                                                    exercises = listOf(
                                                        Exercise(id = "e1", name = "Press de banca", sets = listOf(ExerciseSet("s1"), ExerciseSet("s2"))),
                                                        Exercise(id = "e2", name = "Remo con barra", sets = listOf(ExerciseSet("s3"), ExerciseSet("s4"))),
                                                    ),
                                                ),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val logs = listOf(
            WorkoutLog(
                id = "log-1",
                programId = "prog-1",
                sessionId = "s-1",
                sessionName = "Torso",
                date = "2026-10-01T10:00:00.000Z",
                durationMinutes = 45,
                completedExercises = listOf(
                    CompletedExercise(
                        exerciseId = "e1",
                        exerciseName = "Press de banca",
                        sets = listOf(CompletedSet(id = "cs1", weight = 80.0, reps = 8)),
                    ),
                ),
                weekId = "w1",
            ),
        )

        val ui = buildProgramProgress(
            program = program,
            logs = logs,
            scope = ProgressScope.PROGRAM,
            selectedBlockId = null,
            selectedWeekId = null,
            currentWeekId = "w1",
            today = today,
        )

        assertEquals(2, ui.programExercises.size)
        assertTrue(ui.programExercises.first().isLogged)
        assertEquals("Press de banca", ui.programExercises.first().name)
        assertFalse(ui.programExercises.last().isLogged)
        assertEquals("Remo con barra", ui.programExercises.last().name)
    }
}
