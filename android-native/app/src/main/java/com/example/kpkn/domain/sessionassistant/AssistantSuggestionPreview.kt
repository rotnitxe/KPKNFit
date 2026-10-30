package com.example.kpkn.domain.sessionassistant

import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.Session
import kotlin.math.roundToInt

/** Cómo queda la duración de la sesión con las casillas marcadas de una sugerencia de tiempo (caja «Duración»). */
data class DurationPreview(
    val baseMinutes: Int,
    val afterMinutes: Int,
    val limitMinutes: Int,
    val savedMinutes: Int,
    val fits: Boolean,
    val text: String,
)

/** Cómo queda el músculo que pasa de tu límite con las casillas marcadas de la sugerencia de límites. */
data class MuscleLimitPreview(
    val muscle: String,
    val before: Double,
    val after: Double,
    val limit: Double,
    val fits: Boolean,
    val text: String,
)

/**
 * Efecto en vivo de marcar o desmarcar las casillas de una sugerencia (`pulido:as-sug-lista`). Es una suma de los efectos que el
 * motor ya midió (tiempo) o una nueva simulación con las mismas acciones que luego se aplican (músculo). Kotlin puro.
 */
object AssistantSuggestionPreview {

    /** Minutos que ahorran las casillas [accepted]: suma de sus efectos medidos, redondeada al minuto. */
    fun savedMinutes(accepted: List<AssistantSuggestionDetail>): Int =
        (-accepted.sumOf { it.effect?.deltaSeconds ?: 0 } / 60.0).roundToInt().coerceAtLeast(0)

    fun duration(baseMinutes: Int, limitMinutes: Int, accepted: List<AssistantSuggestionDetail>): DurationPreview {
        val saved = savedMinutes(accepted)
        val after = baseMinutes - saved
        val fits = after <= limitMinutes
        val text = when {
            accepted.isEmpty() -> "Elige qué opciones quieres aplicar."
            fits -> "Con las opciones marcadas la sesión cabría en tu tiempo."
            else -> "Con las opciones marcadas ahorras $saved min; la sesión seguiría ${after - limitMinutes} min por encima del objetivo."
        }
        return DurationPreview(baseMinutes, after, limitMinutes, saved, fits, text)
    }

    /**
     * El músculo con más series por encima de [limit] (o null si ninguno lo pasa) y cuánto queda al aplicar [accepted] sobre
     * [session]. [rows] son las filas actuales de [AssistantMuscleBreakdown.rows].
     */
    fun muscleLimit(
        session: Session,
        rows: List<MuscleBreakdownRow>,
        limit: Double,
        accepted: List<AssistantSuggestionDetail>,
        params: AssistantActionParameters,
        exerciseIndex: Map<String, ExerciseMuscleInfo>,
    ): MuscleLimitPreview? {
        val worst = rows.filter { it.volume > limit }.maxByOrNull { it.volume } ?: return null
        val applied = accepted.fold(session) { acc, detail -> SessionAssistantActions.apply(acc, detail.action, params) }
        val after = AssistantMuscleBreakdown.rows(applied, exerciseIndex).firstOrNull { it.muscle == worst.muscle }?.volume ?: 0.0
        val fits = after <= limit
        val removed = (session.allExercises().sumOf { it.sets.size } - applied.allExercises().sumOf { it.sets.size }).coerceAtLeast(0)
        val text = when {
            fits -> "Quitando $removed ${if (removed == 1) "serie" else "series"}, ${worst.muscle} queda en ${number(after)}: dentro de tu límite."
            else -> "Sin cambios, ${worst.muscle} sigue en ${number(after)}: por encima de tu límite."
        }
        return MuscleLimitPreview(worst.muscle, worst.volume, after, limit, fits, text)
    }

    /** Cifra con coma decimal y sin «,0» («10» / «10,5»). */
    fun number(value: Double): String {
        val rounded = (value * 10).roundToInt() / 10.0
        return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else rounded.toString().replace('.', ',')
    }
}
