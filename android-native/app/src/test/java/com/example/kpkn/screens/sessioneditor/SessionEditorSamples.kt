package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.PlannedTechnique
import com.example.kpkn.data.models.PredictedDrain
import com.example.kpkn.data.models.RepRange
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionBackground
import com.example.kpkn.data.models.SessionBackgroundType
import com.example.kpkn.data.models.TechniqueType
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.WarmupSetDefinition
import com.example.kpkn.domain.sessionassistant.AssistantActionType
import com.example.kpkn.domain.sessionassistant.AssistantDetailAction
import com.example.kpkn.domain.sessionassistant.AssistantSuggestion
import com.example.kpkn.domain.sessionassistant.AssistantSuggestionDetail
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.screens.sessioneditor.design.SessionImpact
import com.example.kpkn.screens.sessioneditor.design.SessionPreparationRow
import com.example.kpkn.screens.sessioneditor.design.sessionImpactOf

/** Datos de ejemplo del editor de sesiones para las pruebas y la hoja de render (SE2). Sin datos reales. */
object SessionEditorSamples {
    /** Un nombre largo, para ver los dos renglones del título. */
    const val LONG_NAME = "Torso A · Empuje pesado con dominadas y fondos"

    /** Portada en color: el primer degradado de la lista de presets. */
    val background = SessionBackground(type = SessionBackgroundType.COLOR, value = sessionGradients.first().id)

    /** Cuatro sesiones de la semana: lunes (principal), martes, jueves y viernes. Miércoles, sábado y domingo quedan vacíos. */
    val weekSessions = listOf(
        Session(id = "s-lun", name = "Torso A", dayOfWeek = 1, isMainSession = true),
        Session(id = "s-mar", name = "Pierna", dayOfWeek = 2),
        Session(id = "s-jue", name = "Torso B", dayOfWeek = 4),
        Session(id = "s-vie", name = "Empuje", dayOfWeek = 5),
    )

    /** Días de la semana en el orden del programa (lunes primero). */
    val orderedDays = (1..7).toList()

    /** Tres semanas de un mismo bloque. */
    val roadmap = listOf(
        SessionRoadmapOption(
            macroIndex = 0, blockIndex = 0, mesoIndex = 0, weekIndex = 0, weekId = "w1",
            macroName = "Macro 1", blockName = "Bloque 1", weekName = "Acumulación", sessionCount = 4,
        ),
        SessionRoadmapOption(
            macroIndex = 0, blockIndex = 0, mesoIndex = 0, weekIndex = 1, weekId = "w2",
            macroName = "Macro 1", blockName = "Bloque 1", weekName = "Intensificación", sessionCount = 4,
        ),
        SessionRoadmapOption(
            macroIndex = 0, blockIndex = 0, mesoIndex = 0, weekIndex = 2, weekId = "w3",
            macroName = "Macro 1", blockName = "Bloque 1", weekName = "Descarga", sessionCount = 4,
        ),
    )

    /** Una serie de trabajo de muestra: repeticiones de un rango y, si hay, el RIR. (SE3a) */
    fun workSet(id: String, low: Int, high: Int, rir: Int? = null): ExerciseSet =
        ExerciseSet(id = id, targetRepsRange = RepRange(low, high), targetRIR = rir)

    /** Ejercicios de muestra para la lista del editor (SE3a): una parte de tres, una superserie de dos y dos sueltos. */
    val partExercises = listOf(
        Exercise(
            id = "ex-press", name = "Press inclinado con mancuernas", restTime = 150,
            sets = listOf(workSet("p1", 6, 8, 2), workSet("p2", 6, 8, 2), workSet("p3", 6, 8, 2), workSet("p4", 6, 8, 2)),
        ),
        Exercise(
            id = "ex-fondos", name = "Fondos en paralelas", restTime = 90,
            sets = listOf(workSet("f1", 8, 12), workSet("f2", 8, 12)),
        ),
        Exercise(
            id = "ex-remo", name = "Remo con barra", restTime = 120,
            sets = listOf(workSet("r1", 8, 10, 2), workSet("r2", 8, 10, 2), workSet("r3", 8, 10, 2)),
        ),
        Exercise(
            id = "ex-dominadas", name = "Dominadas asistidas", restTime = 120,
            sets = listOf(workSet("d1", 5, 8, 1), workSet("d2", 5, 8, 1)),
        ),
    )

    /**
     * Resumen del motor de una sesión de empuje (SE4): la torre en 32 / 18 / 12 y cinco músculos. Cubren los cuatro estados de volumen
     * (Alto tolerable, Rango ideal, Subentrenado) y uno sin umbrales («Sin calibrar»).
     */
    val impactSummary = SessionEditorAugeSummary(
        sessionDrain = PredictedDrain(cns = 18, muscular = 32, spinal = 12),
        sessionVolumeByMuscle = mapOf(
            "Dorsales" to 9.0,
            "Deltoides" to 6.5,
            "Pectorales" to 6.0,
            "Core" to 2.0,
            "Tríceps" to 1.5,
        ),
        volumeThresholdsByMuscle = mapOf(
            "Dorsales" to SessionEditorVolumeThreshold(3.0, 6.0, 9.0, 9.0, 18.0, 27.0),
            "Deltoides" to SessionEditorVolumeThreshold(2.0, 5.0, 7.0, 6.0, 15.0, 21.0),
            "Pectorales" to SessionEditorVolumeThreshold(3.0, 6.0, 8.0, 9.0, 18.0, 24.0),
            "Tríceps" to SessionEditorVolumeThreshold(2.0, 4.0, 6.0, 6.0, 12.0, 18.0),
        ),
    )

    /** El impacto de [impactSummary]. */
    val impact: SessionImpact? = sessionImpactOf(impactSummary)

    /** Dos sugerencias del motor: la primera con dos detalles (el segundo sin marcar); la segunda, sin detalles. */
    val suggestions = listOf(
        AssistantSuggestion(
            id = "sug-reduce",
            type = AssistantActionType.REDUCE_SET,
            title = "Quitar una serie de press banca",
            message = "El pecho ya llega a su carga semanal. Una serie menos mantiene el estímulo y baja la fatiga.",
            details = listOf(
                AssistantSuggestionDetail(
                    id = "d-press",
                    label = "Press banca · 4 a 3 series",
                    action = AssistantDetailAction.ReduceSet(exerciseId = "ex-press"),
                ),
                AssistantSuggestionDetail(
                    id = "d-fly",
                    label = "Aperturas · 3 a 2 series",
                    action = AssistantDetailAction.ReduceSet(exerciseId = "ex-fly"),
                    defaultAccepted = false,
                ),
            ),
        ),
        AssistantSuggestion(
            id = "sug-rpe",
            type = AssistantActionType.LOWER_RPE,
            title = "Bajar el esfuerzo de la última serie",
            message = "Las dos últimas series de remo van a RPE 10. Con RPE 9 la sesión se recupera antes.",
        ),
    )

    /** Press banca en RIR: dos series de aproximación y tres de trabajo, con carga sugerida desde un 1RM de 100 kg (SE3b). */
    fun exerciseRir(): Exercise = Exercise(
        id = "ex-rir",
        name = "Press banca",
        trainingMode = TrainingMode.REPS,
        reference1RM = 100.0,
        restTime = 150,
        warmupSets = listOf(
            WarmupSetDefinition(id = "wu-1", percentageOfWorkingWeight = 0.5, targetReps = 10, restBetween = 60),
            WarmupSetDefinition(id = "wu-2", percentageOfWorkingWeight = 0.7, targetReps = 6, restBetween = 90),
        ),
        sets = listOf(
            ExerciseSet(id = "rir-1", targetRepsRange = RepRange(6, 8), targetReps = 8, intensityMode = IntensityMode.RIR, targetRIR = 2),
            ExerciseSet(id = "rir-2", targetRepsRange = RepRange(6, 8), targetReps = 8, intensityMode = IntensityMode.RIR, targetRIR = 2),
            ExerciseSet(id = "rir-3", targetRepsRange = RepRange(6, 8), targetReps = 8, intensityMode = IntensityMode.RIR, targetRIR = 1),
        ),
    )

    /** Sentadilla en RPE, con la tercera serie con carga fija (102,5 kg) (SE3b). */
    fun exerciseRpe(): Exercise = Exercise(
        id = "ex-rpe",
        name = "Sentadilla",
        trainingMode = TrainingMode.REPS,
        restTime = 180,
        sets = listOf(
            ExerciseSet(id = "rpe-1", targetReps = 5, intensityMode = IntensityMode.RPE, targetRPE = 7.5),
            ExerciseSet(id = "rpe-2", targetReps = 5, intensityMode = IntensityMode.RPE, targetRPE = 8.0),
            ExerciseSet(id = "rpe-3", targetReps = 4, intensityMode = IntensityMode.LOAD, weight = 102.5),
        ),
    )

    /** Peso muerto en % del 1RM, con referencia de 180 kg (SE3b). */
    fun exercisePercent(): Exercise = Exercise(
        id = "ex-pct",
        name = "Peso muerto",
        trainingMode = TrainingMode.RM,
        reference1RM = 180.0,
        restTime = 180,
        sets = listOf(
            ExerciseSet(id = "pct-1", targetReps = 5, targetPercentageRM = 75.0),
            ExerciseSet(id = "pct-2", targetRepsRange = RepRange(4, 6), targetReps = 6, targetPercentageRM = 80.0),
            ExerciseSet(id = "pct-3", targetReps = 3, targetPercentageRM = 85.0),
        ),
    )

    /** Plancha por tiempo: la duración de cada serie en segundos (SE3b). */
    fun exerciseTime(): Exercise = Exercise(
        id = "ex-time",
        name = "Plancha",
        trainingMode = TrainingMode.TIME,
        restTime = 60,
        sets = listOf(
            ExerciseSet(id = "time-1", targetDuration = 45),
            ExerciseSet(id = "time-2", targetDuration = 45),
            ExerciseSet(id = "time-3", targetDuration = 60),
        ),
    )

    /** Curl de bíceps en RIR con una serie en drop set y su técnica (SE3b). */
    fun exerciseDrop(): Exercise = Exercise(
        id = "ex-drop",
        name = "Curl de bíceps",
        trainingMode = TrainingMode.REPS,
        reference1RM = 40.0,
        restTime = 90,
        sets = listOf(
            ExerciseSet(id = "drop-1", targetReps = 10, intensityMode = IntensityMode.RIR, targetRIR = 2),
            ExerciseSet(
                id = "drop-2",
                targetReps = 8,
                intensityMode = IntensityMode.RIR,
                targetRIR = 1,
                isDropSet = true,
                plannedIntensityTechniques = listOf(
                    PlannedTechnique(id = "tech-drop", type = TechniqueType.DROP_SET, params = mapOf("weightPcts" to "-5", "count" to "1")),
                ),
            ),
            ExerciseSet(id = "drop-3", targetReps = 10, intensityMode = IntensityMode.RIR, targetRIR = 2),
        ),
    )
}

/**
 * Datos de la hoja de render de la pantalla entera (SE8): la parte «Empuje» con su superserie y su ejercicio desplegado, el cardio de cinta
 * y la preparación. Son datos de la pantalla, sin ViewModel.
 */
internal object SessionEditorScreenSamples {
    /** La preparación de la sesión: movilidad de cadera y la serie de aproximación del primer ejercicio. */
    val preparation = listOf(
        SessionPreparationRow(name = "Movilidad de cadera", dose = "2 × 10"),
        SessionPreparationRow(name = "Calentamiento · Press inclinado con mancuernas", dose = "3 × 8"),
    )

    /** Cinta de 30 min en zona 2. */
    val treadmill = CardioDetails(type = CardioType.TREADMILL, targetDurationSeconds = 1800, targetHrPercent = 65)

    /** La parte «Empuje»: tres ejercicios de la muestra; los dos primeros van en superserie en la pantalla. */
    val empuje = SessionEditorSamples.partExercises.take(3)
}
