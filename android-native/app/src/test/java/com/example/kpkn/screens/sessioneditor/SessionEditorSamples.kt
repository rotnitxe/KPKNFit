package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.RepRange
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionBackground
import com.example.kpkn.data.models.SessionBackgroundType

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
}
