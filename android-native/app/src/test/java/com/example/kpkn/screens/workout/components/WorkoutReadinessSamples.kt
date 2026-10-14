package com.example.kpkn.screens.workout.components

import com.example.kpkn.domain.auge.DiscomfortSuggestionEngine

/**
 * Datos de ejemplo de la preparación, para las pruebas y la hoja de render: seis músculos de una sesión, sus pilares, dos molestias
 * sugeridas y un consejo de prueba. No es contenido de producto.
 */
internal object WorkoutReadinessSamples {
    val pillars: List<String> = listOf("Pectorales", "Dorsales", "Hombros", "Cuádriceps", "Glúteos", "Trapecio")

    val muscles: Map<String, Int> = mapOf(
        "Pectorales" to 72,
        "Dorsales" to 60,
        "Hombros" to 48,
        "Cuádriceps" to 88,
        "Glúteos" to 76,
        "Trapecio" to 64,
    )

    /** Las dos primeras molestias sugeridas para estos músculos. */
    fun twoDiscomforts(): List<String> = DiscomfortSuggestionEngine.suggestForMuscles(pillars).take(2).map { it.id }

    val advice: ReadinessAdvice = ReadinessAdvice(
        channelLines = listOf(
            "Músculos: recuperados · entrena con normalidad",
            "Energía: media · vigila el volumen",
            "Columna: alta · sin límite",
        ),
        coachingHeadline = "Empieza por el tren superior",
        coachingDetail = "Las piernas siguen cargadas de la sesión de ayer.",
        advisoryLine = "Carga: sube el peso con cuidado",
    )
}
