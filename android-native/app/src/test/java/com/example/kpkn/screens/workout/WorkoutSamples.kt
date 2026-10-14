package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CardioBlockType
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioExecutionStatus
import com.example.kpkn.data.models.CardioIntervalBlock
import com.example.kpkn.data.models.CardioTimerState
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.data.models.SessionChecklistItem
import com.example.kpkn.data.models.SessionMilestone
import com.example.kpkn.data.models.SessionSavedNote
import com.example.kpkn.data.models.WarmupSetDefinition
import com.example.kpkn.screens.workout.components.WarmupPhaseRow
import com.example.kpkn.screens.workout.components.WorkoutMobilityChecklistItem
import com.example.kpkn.screens.workout.design.WorkoutSetDot
import com.example.kpkn.screens.workout.design.WorkoutSetDotState
import com.example.kpkn.services.cardio.CardioGpsState
import com.example.kpkn.services.cardio.CardioGpsStatus

/**
 * Datos de ejemplo de la sesión en vivo para las hojas de render: el marco (LS2a) y las piezas de la serie (LS1). Son textos y
 * cifras fijas, sin VM: todo se pinta con las piezas sin estado de `screens/workout/design/`.
 */
internal object WorkoutSamples {
    const val EXERCISE_NAME = "Press inclinado con mancuernas en banco"
    const val EXERCISE_NAME_SHORT = "Press inclinado"
    const val EYEBROW_SUPERSET = "Superserie A · ronda 2 de 3"
    const val EYEBROW_PLAIN = "Ejercicio 3 de 6 · Torso A"
    const val DETAIL = "Mancuernas · Agarre neutro"
    const val TARGET = "Objetivo 4 × 6–8 · RIR 2"
    const val LAST = "Última vez 80 kg × 8"
    const val PROTOCOL = "Drop-set"
    const val PACE_AHEAD = "Vas por delante"
    const val CLOCK = "24:31"
    val DONE = listOf(true, true, false, false, false, false)

    /** Dos hechas, la actual y dos pendientes. */
    val cincoSeries: List<WorkoutSetDot> = listOf(
        WorkoutSetDot("s1", "1", WorkoutSetDotState.DONE, "80 × 8"),
        WorkoutSetDot("s2", "2", WorkoutSetDotState.DONE, "80 × 8"),
        WorkoutSetDot("s3", "3", WorkoutSetDotState.CURRENT, null),
        WorkoutSetDot("s4", "4", WorkoutSetDotState.FUTURE, null),
        WorkoutSetDot("s5", "5", WorkoutSetDotState.FUTURE, null),
    )

    /** Diez series, las seis primeras hechas y la séptima en curso: la tira tiene que dejarla a la vista. */
    val diezSeries: List<WorkoutSetDot> = (1..10).map { n ->
        WorkoutSetDot(
            id = "s$n",
            label = "$n",
            state = when {
                n < 7 -> WorkoutSetDotState.DONE
                n == 7 -> WorkoutSetDotState.CURRENT
                else -> WorkoutSetDotState.FUTURE
            },
            caption = if (n < 7) "80 × 8" else null,
        )
    }

    /** Cuatro series con la segunda en curso: la pantalla de «Serie 2 de 4». */
    val cuatroSeries: List<WorkoutSetDot> = listOf(
        WorkoutSetDot("s1", "1", WorkoutSetDotState.DONE, "80 × 8"),
        WorkoutSetDot("s2", "2", WorkoutSetDotState.CURRENT, null),
        WorkoutSetDot("s3", "3", WorkoutSetDotState.FUTURE, null),
        WorkoutSetDot("s4", "4", WorkoutSetDotState.FUTURE, null),
    )
}

/** Hitos, tareas y notas de ejemplo para el cockpit y el resumen al terminar (LS6). */
internal object WorkoutFinishSamples {
    val milestones: List<SessionMilestone> = listOf(
        SessionMilestone(
            id = "m1",
            exerciseId = "press",
            exerciseName = "Press inclinado con mancuernas",
            kind = "pr_e1rm",
            label = "Nuevo PR e1RM",
            value = 96.0,
            detail = "80 kg × 6 → 96 kg",
            createdAtIso = "2026-10-13T18:40:00Z",
        ),
        SessionMilestone(
            id = "m2",
            exerciseId = "remo",
            exerciseName = "Remo con barra",
            kind = "star_goal_reached",
            label = "Meta estrella alcanzada",
            value = 84.0,
            detail = "70 kg × 8 → 84 kg (meta 80 kg)",
            createdAtIso = "2026-10-13T18:52:00Z",
        ),
    )

    val checklist: List<SessionChecklistItem> = listOf(
        SessionChecklistItem(id = "c1", text = "Llegar a 4 series de press", done = true),
        SessionChecklistItem(id = "c2", text = "Subir el remo a 70 kg", done = false),
    )

    val savedNotes: List<SessionSavedNote> = listOf(
        SessionSavedNote(
            id = "n1",
            text = "Hombro izquierdo algo cargado: bajar el peso el jueves.",
            createdAtIso = "2026-10-13T18:10:00Z",
        ),
    )
}

/**
 * Datos de ejemplo de la fase de preparación y del cardio (KPKN LS5b): calentamiento, movilidad y cardio con GPS, por intervalos y en
 * pausa. Los usan las pruebas y la hoja de render; no hay VM.
 */
internal object WorkoutPrepSamples {
    /** Tres aproximaciones de sentadilla, ninguna hecha: la primera es la que toca. Los pesos son sugerencias. */
    val WARMUP_ROWS: List<WarmupPhaseRow> = listOf(
        WarmupPhaseRow(
            exerciseId = "squat",
            exerciseBadge = null,
            index = 0,
            warmup = WarmupSetDefinition(id = "w1", percentageOfWorkingWeight = 0.5, targetReps = 10),
            suggestedWeightKg = 60.0,
            actualWeightKg = null,
            isCompleted = false,
            exerciseName = "Sentadilla",
        ),
        WarmupPhaseRow(
            exerciseId = "squat",
            exerciseBadge = null,
            index = 1,
            warmup = WarmupSetDefinition(id = "w2", percentageOfWorkingWeight = 0.7, targetReps = 6),
            suggestedWeightKg = 84.0,
            actualWeightKg = null,
            isCompleted = false,
            exerciseName = "Sentadilla",
        ),
        WarmupPhaseRow(
            exerciseId = "squat",
            exerciseBadge = null,
            index = 2,
            warmup = WarmupSetDefinition(id = "w3", percentageOfWorkingWeight = 0.85, targetReps = 3),
            suggestedWeightKg = 100.0,
            actualWeightKg = null,
            isCompleted = false,
            exerciseName = "Sentadilla",
        ),
    )

    private fun movilidad(
        exerciseId: String,
        exerciseName: String,
        id: String,
        name: String,
        sets: Int,
        reps: String?,
        seconds: Int?,
    ) = WorkoutMobilityChecklistItem(
        exerciseId = exerciseId,
        exerciseName = exerciseName,
        mobility = MobilitySeries(id = id, name = name, sets = sets, reps = reps, durationSeconds = seconds),
        mobilitySetIndex = 0,
        stepKey = id,
    )

    /** Cuatro movimientos de dos ejercicios. El primero (`m1`) ya está hecho: lo que toca es el segundo. */
    val MOBILITY_ITEMS: List<WorkoutMobilityChecklistItem> = listOf(
        movilidad("squat", "Sentadilla", "m1", "Cadera 90/90", sets = 1, reps = null, seconds = 45),
        movilidad("squat", "Sentadilla", "m2", "Tobillo contra la pared", sets = 2, reps = "10", seconds = null),
        movilidad("hip", "Peso muerto", "m3", "Círculos de hombro", sets = 1, reps = "10", seconds = null),
        movilidad("hip", "Peso muerto", "m4", "Gato-vaca", sets = 2, reps = "8", seconds = null),
    )

    /** Carrera al aire libre con GPS y 30 minutos de objetivo. */
    val CARDIO_GPS = CardioDetails(type = CardioType.RUN_OUTDOOR, requiresGps = true, targetDurationSeconds = 1800)

    /** Bici por intervalos: 40 s de esfuerzo y 20 s de pausa, dos vueltas (8 tramos). */
    val CARDIO_INTERVALS = CardioDetails(
        type = CardioType.BIKE_STATIONARY,
        targetDurationSeconds = null,
        intervalBlocks = listOf(
            CardioIntervalBlock(id = "e1", type = CardioBlockType.WORK, durationSeconds = 40, watts = 180),
            CardioIntervalBlock(id = "p1", type = CardioBlockType.RECOVER, durationSeconds = 20, watts = 90),
            CardioIntervalBlock(id = "e2", type = CardioBlockType.WORK, durationSeconds = 40, watts = 180),
            CardioIntervalBlock(id = "p2", type = CardioBlockType.RECOVER, durationSeconds = 20, watts = 90),
        ),
        intervalRounds = 2,
    )

    private fun cardioTimer(exerciseId: String, total: Int, elapsed: Int, status: CardioExecutionStatus) = CardioTimerState(
        exerciseId = exerciseId,
        totalSeconds = total,
        remainingSeconds = (total - elapsed).coerceAtLeast(0),
        elapsedSeconds = elapsed,
        status = status,
    )

    /** La carrera a los 5 minutos, con el GPS grabando: 3,42 km a 5:30 /km. */
    val GPS_RUNNING_TIMER: CardioTimerState = cardioTimer("run", total = 1800, elapsed = 300, status = CardioExecutionStatus.RUNNING)
    val GPS_RUNNING: CardioGpsState = CardioGpsState(
        status = CardioGpsStatus.RECORDING,
        distanceMeters = 3420.0,
        elapsedActiveSeconds = 300L,
        paceSecondsPerKm = 330,
        kmSplitPaces = listOf(335, 328, 330),
        pointCount = 40,
    )

    /** La carrera pausada a los 10 minutos: 6,12 km con el GPS en pausa. */
    val GPS_PAUSED_TIMER: CardioTimerState = cardioTimer("run", total = 1800, elapsed = 620, status = CardioExecutionStatus.PAUSED)
    val GPS_PAUSED: CardioGpsState = CardioGpsState(
        status = CardioGpsStatus.PAUSED,
        distanceMeters = 6120.0,
        elapsedActiveSeconds = 620L,
        paceSecondsPerKm = 326,
        kmSplitPaces = listOf(338, 330, 320, 322, 326, 328),
        pointCount = 90,
    )

    /** Los intervalos a 70 s: el tramo 3 de 8 (de trabajo) está en curso. */
    val INTERVALS_TIMER: CardioTimerState = cardioTimer("bike", total = 240, elapsed = 70, status = CardioExecutionStatus.RUNNING)
}
