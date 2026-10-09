package com.example.kpkn.debug

import com.example.kpkn.data.models.ActiveProgramState
import com.example.kpkn.data.models.CompletedExercise
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.DailyWellbeingLog
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.data.models.MealType
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WellbeingSource
import com.example.kpkn.data.models.WorkoutLog
import java.time.LocalDate

/*
 * SOLO DEPURACIÓN. Parte pura de la siembra de demostración (sin Android): qué entrenos, comidas y check-in se generan
 * para un día. Los ids son fijos por hueco («demo-…»): relanzar la siembra sustituye las filas en lugar de acumularlas.
 */
internal object DemoDataPlan {

    /** Días hacia atrás de los 8 entrenos terminados: todos caen dentro de los últimos 14 días y antes de hoy. */
    val WORKOUT_DAYS_AGO: List<Int> = listOf(13, 11, 9, 8, 6, 4, 2, 1)

    const val BREAKFAST_ID = "demo-meal-breakfast"
    const val LUNCH_ID = "demo-meal-lunch"
    const val CHECK_IN_ID = "demo-wellbeing-today"
    const val DISPLAY_NAME = "Alex"

    /** Id estable del entreno número [slot] (1..8). */
    fun workoutId(slot: Int): String = "demo-workout-%02d".format(slot)

    fun workoutIds(): Set<String> = (1..WORKOUT_DAYS_AGO.size).map { workoutId(it) }.toSet()

    /**
     * Ocho entrenos terminados de las sesiones de la semana activa, en rotación. Cada serie toma su peso y sus
     * repeticiones de la prescripción; sin peso prescrito cuenta como 0 kg (carga corporal o sin dato).
     */
    fun workouts(today: LocalDate, weekId: String, sessions: List<Session>, active: ActiveProgramState): List<WorkoutLog> {
        require(sessions.isNotEmpty()) { "La semana activa no tiene sesiones" }
        return WORKOUT_DAYS_AGO.mapIndexed { index, daysAgo ->
            val slot = index + 1
            val session = sessions[index % sessions.size]
            val logId = workoutId(slot)
            val completed = session.exercises.mapIndexed { exerciseIndex, exercise ->
                val planned: List<ExerciseSet?> = exercise.sets.filterNot { it.isEmptySlot }.ifEmpty { listOf<ExerciseSet?>(null, null, null) }
                CompletedExercise(
                    exerciseId = exercise.exerciseId ?: exercise.id,
                    exerciseName = exercise.name,
                    restTime = exercise.restTime ?: 90,
                    sets = planned.mapIndexed { setIndex, set ->
                        CompletedSet(
                            id = "$logId-e$exerciseIndex-s$setIndex",
                            weight = set?.weight ?: 0.0,
                            reps = set?.targetReps ?: 8,
                            rpe = set?.targetRPE,
                            rir = set?.targetRIR,
                        )
                    },
                )
            }
            val volume = completed.sumOf { exercise -> exercise.sets.sumOf { it.weight * it.reps } }
            WorkoutLog(
                id = logId,
                programId = active.programId,
                sessionId = session.id,
                sessionName = session.name,
                date = "${today.minusDays(daysAgo.toLong())}T17:00:00.000Z",
                durationMinutes = 55 + (index % 3) * 5,
                completedExercises = completed,
                totalVolume = volume,
                weekId = weekId,
                programRunId = active.programRunId,
                cycleNumber = active.currentCycleNumber,
                weekInstanceId = active.currentWeekInstanceId,
            )
        }
    }

    /** Desayuno y comida de hoy, sin el texto que el vídeo escribe en vivo. */
    fun meals(today: LocalDate): List<NutritionLog> {
        val date = "${today}T12:00:00.000Z"
        return listOf(
            NutritionLog(
                id = BREAKFAST_ID,
                date = date,
                mealType = MealType.BREAKFAST,
                foods = listOf(
                    food(BREAKFAST_ID, 1, "Avena cocida", 60.0, kcal = 233.0, protein = 8.4, carbs = 40.0, fats = 4.2),
                    food(BREAKFAST_ID, 2, "Plátano", 120.0, kcal = 107.0, protein = 1.3, carbs = 27.0, fats = 0.4),
                    food(BREAKFAST_ID, 3, "Yogur griego natural", 150.0, kcal = 90.0, protein = 15.0, carbs = 5.0, fats = 0.5),
                ),
            ),
            NutritionLog(
                id = LUNCH_ID,
                date = date,
                mealType = MealType.LUNCH,
                foods = listOf(
                    food(LUNCH_ID, 1, "Pechuga de pollo a la plancha", 150.0, kcal = 248.0, protein = 46.0, carbs = 0.0, fats = 5.4),
                    food(LUNCH_ID, 2, "Arroz integral cocido", 180.0, kcal = 200.0, protein = 4.5, carbs = 42.0, fats = 1.6),
                    food(LUNCH_ID, 3, "Ensalada mixta con aceite de oliva", 150.0, kcal = 120.0, protein = 2.0, carbs = 6.0, fats = 10.0),
                ),
            ),
        )
    }

    /** Check-in de hoy de RINGS: escalas 1–5, fuente diaria. Sustituye el de hoy (una fila por día). */
    fun ringsCheckIn(today: LocalDate): DailyWellbeingLog = DailyWellbeingLog(
        id = CHECK_IN_ID,
        date = today.toString(),
        sleepQuality = 4,
        stressLevel = 2,
        doms = 2,
        motivation = 4,
        source = WellbeingSource.DAILY_CHECK_IN,
    )

    private fun food(
        mealId: String,
        index: Int,
        name: String,
        grams: Double,
        kcal: Double,
        protein: Double,
        carbs: Double,
        fats: Double,
    ) = LoggedFood(
        id = "$mealId-f$index",
        foodName = name,
        amount = grams,
        unit = "g",
        calories = kcal,
        protein = protein,
        carbs = carbs,
        fats = fats,
    )
}
