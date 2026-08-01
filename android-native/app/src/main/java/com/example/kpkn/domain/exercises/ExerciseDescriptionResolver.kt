package com.example.kpkn.domain.exercises

import com.example.kpkn.data.models.AspectOption
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.TechnicalAspect

/** Resolves the canonical blurb plus the context supplied by selected chips. */
fun adaptedExerciseDescription(
    exercise: ExerciseMuscleInfo,
    selectedAspects: Map<String, String> = emptyMap(),
): String {
    val canonical = exercise.description
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: canonicalFallbackDescription(exercise)
    val selectedDetails = exercise.technicalAspects.orEmpty().mapNotNull { aspect ->
        val optionId = selectedAspects[aspect.id] ?: return@mapNotNull null
        val option = aspect.options.firstOrNull { it.id == optionId } ?: return@mapNotNull null
        val defaultId = aspect.defaultOptionId ?: aspect.options.firstOrNull()?.id
        if (option.id == defaultId) return@mapNotNull null
        technicalOptionDescription(exercise, aspect, option)
    }
    return if (selectedDetails.isEmpty()) canonical
    else "$canonical ${selectedDetails.joinToString(" ")}"
}

fun technicalOptionDescription(
    exercise: ExerciseMuscleInfo,
    aspect: TechnicalAspect,
    option: AspectOption,
): String = option.description
    ?.trim()
    ?.takeIf { it.isNotBlank() }
    ?: aspect.description
        ?.trim()
        ?.takeIf { it.isNotBlank() }
    ?: genericTechnicalOptionDescription(aspect, option)

private fun canonicalFallbackDescription(exercise: ExerciseMuscleInfo): String {
    val muscle = exercise.involvedMuscles
        .firstOrNull { it.role.name.equals("PRIMARY", ignoreCase = true) }
        ?.muscle
        ?: "la musculatura objetivo"
    val equipment = exercise.equipment?.takeIf { it.isNotBlank() } ?: "carga controlada"
    return "${exercise.name} es un ejercicio orientado a $muscle. Se realiza con $equipment, priorizando un rango controlado y una progresión técnica estable."
}

private fun genericTechnicalOptionDescription(
    aspect: TechnicalAspect,
    option: AspectOption,
): String {
    val key = "${aspect.id} ${option.id} ${option.name}".lowercase()
    return when {
        key.contains("martillo") || key.contains("hammer") ->
            "El agarre martillo mantiene las palmas enfrentadas y suele aumentar la participación del braquial y el braquiorradial."
        key.contains("supino") || key.contains("supination") ->
            "El agarre supino coloca las palmas hacia arriba y favorece la participación del bíceps en los patrones de flexión del codo."
        key.contains("pronado") || key.contains("inverso") || key.contains("pronation") ->
            "El agarre pronado reduce la ventaja del bíceps y desplaza parte del esfuerzo hacia braquial, braquiorradial y extensores."
        key.contains("unilateral") || key.contains("unilateral") ->
            "La ejecución unilateral permite ajustar cada lado por separado y exige más control de la pelvis y el tronco."
        key.contains("bilateral") ->
            "La ejecución bilateral facilita una distribución simétrica de la carga y una progresión más sencilla."
        key.contains("inclinado") || key.contains("incline") ->
            "El ángulo inclinado cambia la línea de resistencia y suele aumentar la demanda de las fibras superiores del músculo objetivo."
        key.contains("declinado") || key.contains("decline") ->
            "El ángulo declinado modifica la línea de empuje y suele favorecer la porción inferior del músculo objetivo."
        key.contains("sentado") || key.contains("seated") ->
            "La posición sentada reduce la contribución de las piernas y exige mantener estable la pelvis durante todo el recorrido."
        key.contains("de pie") || key.contains("standing") ->
            "La posición de pie requiere estabilizar el tronco y permite coordinar la fuerza con la cadena inferior."
        key.contains("polea") || key.contains("cable") ->
            "La polea mantiene tensión relativamente constante y permite ajustar con precisión la trayectoria y el rango."
        key.contains("mancuerna") || key.contains("dumbbell") ->
            "Las mancuernas permiten libertad de trayectoria y hacen visible cualquier diferencia de control entre ambos lados."
        else -> "Esta variante modifica la posición o la resistencia para cambiar la demanda del ejercicio; conserva un rango cómodo y controlado."
    }
}
