package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.MuscleRole

/** Anonymous golden fixture: nine exercises and exactly thirty working sets. */
object AugeRealSessionFixtures {
    val exerciseDb: Map<String, ExerciseMuscleInfo> = mapOf(
        "squat" to info("squat", "Sentadilla", 4, 100.0, 6, 8.5, listOf(
            primary("Cuádriceps"), secondary("Glúteos"), stabilizer("Erectores Espinales"),
        )),
        "leg-press" to info("leg-press", "Prensa", 4, 180.0, 10, 8.0, listOf(primary("Cuádriceps"), secondary("Glúteos"))),
        "leg-extension" to info("leg-extension", "Extensión de cuádriceps", 3, 65.0, 12, 9.0, listOf(primary("Cuádriceps"))),
        "rdl" to info("rdl", "RDL", 3, 90.0, 8, 8.5, listOf(primary("Isquiosurales"), secondary("Glúteos"), stabilizer("Erectores Espinales"))),
        "bench" to info("bench", "Press banca", 4, 80.0, 8, 8.5, listOf(primary("Pectorales"), secondary("Tríceps", .45), secondary("Deltoides Anterior", .35))),
        "incline" to info("incline", "Press inclinado", 3, 60.0, 10, 8.0, listOf(primary("Pectorales"), secondary("Tríceps", .35), secondary("Deltoides Anterior", .45))),
        "fly" to info("fly", "Aperturas", 3, 25.0, 12, 9.0, listOf(primary("Pectorales"))),
        "shoulder" to info("shoulder", "Press de hombro", 3, 45.0, 8, 8.0, listOf(primary("Deltoides"), secondary("Tríceps", .45))),
        "french" to info("french", "Press francés", 3, 30.0, 10, 9.0, listOf(primary("Tríceps"))),
    )

    val completedExercises: List<CompletedExercise> = listOf(
        completed("squat", "Sentadilla", 4, 100.0, 6, 8.5),
        completed("leg-press", "Prensa", 4, 180.0, 10, 8.0),
        completed("leg-extension", "Extensión de cuádriceps", 3, 65.0, 12, 9.0),
        completed("rdl", "RDL", 3, 90.0, 8, 8.5),
        completed("bench", "Press banca", 4, 80.0, 8, 8.5),
        completed("incline", "Press inclinado", 3, 60.0, 10, 8.0),
        completed("fly", "Aperturas", 3, 25.0, 12, 9.0),
        completed("shoulder", "Press de hombro", 3, 45.0, 8, 8.0),
        completed("french", "Press francés", 3, 30.0, 10, 9.0),
    )

    init {
        check(completedExercises.sumOf { it.sets.size } == 30)
    }

    fun harder(): List<CompletedExercise> = completedExercises.map { exercise ->
        exercise.copy(sets = exercise.sets.map { set ->
            set.copy(
                rpe = 10.0,
                actualIntensityMode = IntensityMode.RPE,
                actualIntensityValue = 10.0,
                rir = 0,
                isFailure = exercise.exerciseId == "french" || set.isFailure,
            )
        })
    }

    private fun info(
        id: String,
        name: String,
        sets: Int,
        weight: Double,
        reps: Int,
        rpe: Double,
        muscles: List<InvolvedMuscle>,
    ) = ExerciseMuscleInfo(
        id = id,
        name = name,
        equipment = "barra",
        efc = 3.0,
        cnc = 3.0,
        ssc = .7,
        involvedMuscles = muscles,
    )

    private fun completed(
        id: String,
        name: String,
        sets: Int,
        weight: Double,
        reps: Int,
        rpe: Double,
    ) = CompletedExercise(
        exerciseId = id,
        exerciseName = name,
        exerciseDbId = id,
        sets = List(sets) { index ->
            CompletedSet(
                id = "$id-set-$index",
                weight = weight,
                reps = reps,
                rpe = rpe,
                rir = if (index % 2 == 0) 1 else 0,
                isFailure = rpe >= 9.0 && index == sets - 1,
            )
        },
    )

    private fun primary(name: String, contribution: Double? = null) =
        InvolvedMuscle(name, MuscleRole.PRIMARY, contribution)

    private fun secondary(name: String, contribution: Double? = null) =
        InvolvedMuscle(name, MuscleRole.SECONDARY, contribution)

    private fun stabilizer(name: String) = InvolvedMuscle(name, MuscleRole.STABILIZER)
}
