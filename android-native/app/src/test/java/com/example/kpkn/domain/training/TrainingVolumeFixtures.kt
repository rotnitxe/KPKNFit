package com.example.kpkn.domain.training

import com.example.kpkn.data.models.*
import java.time.LocalDate
import java.time.ZoneId

internal object TrainingVolumeFixtures {
    val zone: ZoneId = ZoneId.of("America/Santiago")
    val today: LocalDate = LocalDate.of(2026, 10, 21)
    val chest = listOf(InvolvedMuscle("Pectorales", MuscleRole.PRIMARY, 1.0, "medio"))
    val catalog = mapOf("press" to ExerciseMuscleInfo("press", "Press", involvedMuscles = chest))

    fun exercise(id: String = "press", count: Int = 2, muscles: List<InvolvedMuscle>? = null): Exercise = Exercise(
        id = id,
        name = "Press",
        exerciseDbId = "press",
        effectiveMuscles = muscles,
        sets = List(count) { ExerciseSet("set-$it", targetReps = 8, weight = 60.0, targetRPE = 7.0) },
    )

    fun week(id: String, start: LocalDate, sessions: Int = 0, sets: Int = 2): ProgramWeek = ProgramWeek(
        id = id,
        name = id,
        startDate = start.toString(),
        endDate = start.plusDays(6).toString(),
        sessions = List(sessions) { Session("$id-s$it", "Sesión", exercises = listOf(exercise(count = sets))) },
    )

    fun program(weeks: List<ProgramWeek>): Program = Program(
        id = "program",
        name = "Plan",
        macrocycles = listOf(Macrocycle("macro", "Macro", blocks = listOf(
            Block("block", "Bloque", mesocycles = listOf(Mesocycle("meso", "Meso", weeks = weeks))),
        ))),
    )

    fun log(id: String, date: String = today.toString(), sets: Int = 2, weekId: String? = null): WorkoutLog = WorkoutLog(
        id = id,
        programId = "program",
        sessionId = "session",
        sessionName = "Sesión",
        date = date,
        durationMinutes = 40,
        weekId = weekId,
        completedExercises = listOf(CompletedExercise(
            exerciseId = "press-occurrence",
            exerciseName = "Press",
            exerciseDbId = "press",
            sets = List(sets) { CompletedSet("set-$it", weight = 60.0, reps = 8, rpe = 7.0) },
        )),
    )
}
