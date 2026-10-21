package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.effectiveRepRange
import com.example.kpkn.domain.training.NativeProgressionText
import java.util.Locale

/**
 * Lo que viene después del descanso (LV6, F3 y F6): «Serie 2 · 95 kg × 4–6» y, a la derecha, «RIR 2». Sale de la siguiente serie REAL
 * del plan, la que ya marca el cursor mientras dura el descanso.
 */
internal data class RestNextSetSummary(
    /** Serie que toca (1 = la primera) y cuántas tiene su ejercicio. */
    val setNumber: Int,
    val setTotal: Int,
    /** Nombre del ejercicio, solo si el siguiente es de OTRO ejercicio que el de la serie recién hecha (superserie, cambio de ejercicio). */
    val exerciseName: String? = null,
    /** Carga de la serie (kg) si la tiene; sin ella, la que sugiere la app. */
    val loadKg: Double? = null,
    /** Repeticiones objetivo («8», «4–6») o null. */
    val repsText: String? = null,
    /** Duración objetivo (s) de una serie de tiempo. */
    val durationSeconds: Int? = null,
    /** «RIR 2», «RPE 8» o null si la serie no marca intensidad. */
    val intensityText: String? = null,
) {
    /** «Serie 2 · 95 kg × 4–6». Sin carga, solo las repeticiones o el tiempo («Serie 2 · 8 reps», «Serie 1 · 45 s»). */
    fun headline(): String {
        val dose = doseText()
        return if (dose == null) "Serie $setNumber" else "Serie $setNumber · $dose"
    }

    /** «95 kg × 4–6», «8 reps», «45 s» o null. Con espacios inseparables antes de la unidad y alrededor del «×»: la cifra no se parte. */
    fun doseText(): String? {
        val load = loadKg?.takeIf { it > 0.0 }?.let { "${NativeProgressionText.formatKg(it)}\u00A0kg" }
        val effort = when {
            repsText != null -> repsText
            durationSeconds != null -> "${durationSeconds}\u00A0s"
            else -> null
        }
        return when {
            load != null && effort != null -> "$load\u00A0×\u00A0$effort"
            load != null -> load
            repsText != null -> "$repsText reps"
            effort != null -> effort
            else -> null
        }
    }

    /** «Serie 2 de 4: 95 kg × 4–6.» (F6). Con otro ejercicio, su nombre delante: «Press militar. Serie 1 de 3: 40 kg × 8.». */
    fun sentence(): String {
        val head = if (setTotal > 0) "Serie $setNumber de $setTotal" else "Serie $setNumber"
        val dose = doseText()
        val core = if (dose != null) "$head: $dose." else "$head."
        return if (exerciseName != null) "$exerciseName. $core" else core
    }

    /** Lo que lee TalkBack: «Siguiente: Serie 2 · 95 kg × 4–6 · RIR 2». */
    fun spoken(): String = buildString {
        append("Siguiente: ")
        exerciseName?.let { append(it).append(". ") }
        append(headline())
        intensityText?.let { append(" · ").append(it) }
    }
}

/**
 * La siguiente serie, como la deja el cursor durante el descanso: la serie [currentSetIdx] del ejercicio [currentExerciseIdx] entre los
 * [exercises] a la vista. [justLoggedExerciseId] es el ejercicio de la serie recién registrada: si el siguiente es otro, se dice su nombre
 * ([nameOf]). [suggestedKg] da la carga que sugiere la app cuando el plan no fija una. Null si no queda ninguna (cierre de la sesión).
 */
internal fun restNextSetSummary(
    exercises: List<Exercise>,
    currentExerciseIdx: Int,
    currentSetIdx: Int,
    justLoggedExerciseId: String?,
    nameOf: (Exercise) -> String,
    suggestedKg: (Exercise, Int) -> Double? = { _, _ -> null },
): RestNextSetSummary? {
    val exercise = exercises.getOrNull(currentExerciseIdx) ?: return null
    val set = exercise.sets.getOrNull(currentSetIdx) ?: return null
    if (set.isEmptySlot) return null
    val reps = set.effectiveRepRange()?.format()
    val seconds = set.targetDuration?.takeIf { it > 0 }
    val intensity = when {
        set.targetRIR != null -> "RIR ${set.targetRIR}"
        set.targetRPE != null -> "RPE ${restIntensityNumber(set.targetRPE!!)}"
        else -> null
    }
    val load = set.weight?.takeIf { it > 0.0 } ?: suggestedKg(exercise, currentSetIdx)?.takeIf { it > 0.0 }
    val otherExercise = justLoggedExerciseId != null && justLoggedExerciseId != exercise.id
    return RestNextSetSummary(
        setNumber = currentSetIdx + 1,
        setTotal = exercise.sets.size,
        exerciseName = if (otherExercise) nameOf(exercise) else null,
        loadKg = load,
        repsText = reps,
        durationSeconds = if (reps == null) seconds else null,
        intensityText = intensity,
    )
}

/** «8», «8,5»: coma decimal y sin ceros sobrantes. */
private fun restIntensityNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.forLanguageTag("es"), "%.1f", value)

/**
 * Cabecera del descanso a pantalla completa (F1): el ejercicio de la serie que acaba de hacerse y «Serie 1 de 4 hecha».
 * [subtitle] es null si no se sabe qué serie fue.
 */
internal data class RestHeadline(val title: String, val subtitle: String?)

/**
 * La cabecera del descanso: el ejercicio de [justLoggedKey] (la última serie registrada) con «Serie N de M hecha». Si esa serie no se
 * encuentra, el [fallbackName] sin subtítulo.
 */
internal fun restHeadline(
    exercises: List<Exercise>,
    justLoggedKey: String?,
    fallbackName: String,
    nameOf: (Exercise) -> String,
): RestHeadline {
    val parsed = justLoggedKey?.let(::parseCompletedSetKey)
    val exercise = parsed?.let { p -> exercises.firstOrNull { it.id == p.exerciseId } }
    if (parsed == null || exercise == null) return RestHeadline(title = fallbackName, subtitle = null)
    val total = exercise.sets.size
    val number = (parsed.setIdx + 1).coerceAtLeast(1)
    val subtitle = if (total > 0) "Serie $number de $total hecha" else "Serie $number hecha"
    return RestHeadline(title = nameOf(exercise), subtitle = subtitle)
}

/** Número de series de trabajo hechas (sin calentamiento ni saltadas): ordena los descansos para rotar la forma del reloj. */
internal fun restOrdinal(completedSets: Collection<CompletedSet>): Int =
    completedSets.count { !it.isWarmup && !it.skipped }
