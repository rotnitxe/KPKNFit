package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.PredictedDrain
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionBackground
import com.example.kpkn.data.models.SessionBackgroundType
import com.example.kpkn.domain.sessionassistant.AssistantActionType
import com.example.kpkn.domain.sessionassistant.AssistantDetailAction
import com.example.kpkn.domain.sessionassistant.AssistantSuggestion
import com.example.kpkn.domain.sessionassistant.AssistantSuggestionDetail
import com.example.kpkn.screens.sessioneditor.design.SessionImpact
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
}
