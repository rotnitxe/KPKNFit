package com.example.kpkn.data.repository

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.multisport.*

internal object HybridPlanningFixtures {
    val configuration = HybridPlanningConfiguration(
        availableWindows = listOf(
            AvailableTrainingWindow("gym-mon", 1, 540, 660, "GYM", setOf("barbell")),
            AvailableTrainingWindow("outside-wed", 3, 540, 660, "PUBLIC", emptySet()),
        ),
        fixedCommitments = listOf(FixedTrainingCommitment("class-fri", 5, 1080, 1170)),
    )
    val bindings = listOf(
        HybridSessionGoalBinding("strength-session", "strength", requiredEquipment = setOf("barbell")),
        HybridSessionGoalBinding("run-session", "running"),
    )

    fun program(): Program {
        val sessions = listOf(
            Session(
                id = "strength-session", name = "Sesión existente", dayOfWeek = 2,
                assignedDays = listOf(2), targetDurationMinutes = 60, placeId = "GYM",
                exercises = listOf(Exercise("lift", "Lift", sets = listOf(ExerciseSet("lift-set", targetReps = 5)))),
                sessionB = Session("alternative", "Variante existente", targetDurationMinutes = 45),
            ),
            Session(
                id = "run-session", name = "Cardio existente", dayOfWeek = 4,
                assignedDays = listOf(4), targetDurationMinutes = 35, placeId = "PUBLIC",
                exercises = listOf(Exercise("run", "Run", cardioDetails = CardioDetails(
                    CardioType.RUN_OUTDOOR, targetDurationSeconds = 30 * 60,
                ))),
            ),
        )
        return Program(
            id = "hybrid", name = "Programa existente", author = "Persona",
            trainingIntent = TrainingIntent(
                goals = listOf(
                    SportGoal("strength", SportId.STRENGTH, "Progresar", GoalPriority.PROGRESS, SportExperience.ADVANCED),
                    SportGoal("running", SportId.RUNNING, "Correr", GoalPriority.MAINTAIN, SportExperience.BEGINNER),
                ),
                primaryGoalId = "strength",
            ),
            schedulePlan = ProgramSchedulePlan(trainingDays = setOf(2, 4)),
            macrocycles = listOf(Macrocycle("macro", "Macro", blocks = listOf(
                Block("block", "Bloque", mesocycles = listOf(
                    Mesocycle("meso", "Meso", weeks = listOf(
                        ProgramWeek("week", "Semana", sessions = sessions),
                        ProgramWeek("other-week", "Otra semana", sessions = listOf(Session("untouched", "Otra"))),
                    )),
                )),
            ))),
        )
    }

    fun week(program: Program): ProgramWeek =
        program.macrocycles.single().blocks.single().mesocycles.single().weeks.first()
}
