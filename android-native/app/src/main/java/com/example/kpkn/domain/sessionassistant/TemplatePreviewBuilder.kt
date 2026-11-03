package com.example.kpkn.domain.sessionassistant

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.plannedRepAnchor
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.domain.training.SessionDurationEstimator

/** Una línea de la ficha: «Sentadilla con barra alta · 3 × 6 · RPE 8 · 3:00». */
data class TemplateExercisePreview(
    val name: String,
    val sets: Int,
    val reps: Int?,
    val rpe: Double?,
    val restSeconds: Int?,
)

/** Un grupo de la plantilla (por ejemplo «Compuestos»). */
data class TemplatePartPreview(
    val title: String,
    val exercises: List<TemplateExercisePreview>,
)

/** Las tres cifras de una sesión que decide la pregunta Reemplazar / Añadir. */
data class SessionTotals(val exercises: Int, val sets: Int, val minutes: Int)

/** Cómo quedaría la sesión con cada modo de aplicar una plantilla (`pulido:as-pla-aplicar`). */
data class TemplateApplyOutcomes(
    val replace: SessionTotals,
    val append: SessionTotals,
    /** Minutos que «Añadir» pasa de tu límite de tiempo; null si no hay límite o no se pasa. */
    val appendOverMinutes: Int?,
)

/**
 * La vista previa de una plantilla antes de aplicarla. El tipo [TemplatePreview] ya existía en el modelo del asistente pero el motor
 * nunca lo rellenaba; aquí se rellena con el volumen por músculo (el mismo recorrido de Métricas), la duración con el reloj común y
 * los avisos. Kotlin puro.
 */
object TemplatePreviewBuilder {

    fun preview(
        template: SessionTemplate,
        current: Session?,
        targetMinutes: Int?,
        exerciseIndex: Map<String, ExerciseMuscleInfo>,
    ): TemplatePreview {
        val rows = AssistantMuscleBreakdown.rows(template.session, exerciseIndex)
        val minutes = totals(template.session).minutes
        val warnings = buildList {
            if (current != null && targetMinutes != null) {
                val over = outcomes(totals(current), totals(template.session), targetMinutes).appendOverMinutes
                if (over != null) add("Añadida a tu sesión, te pasa $over min de tu límite.")
            }
        }
        return TemplatePreview(
            template = template,
            modoRecomendado = SessionTemplateApplyMode.REPLACE,
            volumenPorMusculo = rows.associate { it.muscle to it.volume },
            advertencias = warnings,
            duracionEstimada = minutes,
        )
    }

    /** Los grupos de la plantilla con sus ejercicios; los sueltos van en «Ejercicios». */
    fun parts(template: SessionTemplate): List<TemplatePartPreview> {
        val session = template.session
        val loose = session.exercises
        return buildList {
            if (loose.isNotEmpty()) add(TemplatePartPreview("Ejercicios", loose.map(::exercisePreview)))
            session.parts.filter { it.exercises.isNotEmpty() }.forEach { part ->
                add(TemplatePartPreview(part.name, part.exercises.map(::exercisePreview)))
            }
        }
    }

    fun totals(session: Session): SessionTotals {
        val exercises = session.allExercises().distinctBy { it.id }
        return SessionTotals(
            exercises = exercises.size,
            sets = exercises.sumOf { it.sets.size },
            minutes = if (exercises.isEmpty()) 0 else SessionDurationEstimator.estimate(session).totalMinutes,
        )
    }

    /** Sumas simples (el tiempo es aproximado), como en el lienzo. */
    fun outcomes(current: SessionTotals, template: SessionTotals, targetMinutes: Int?): TemplateApplyOutcomes {
        val append = SessionTotals(
            exercises = current.exercises + template.exercises,
            sets = current.sets + template.sets,
            minutes = current.minutes + template.minutes,
        )
        val over = targetMinutes?.takeIf { it > 0 }?.let { append.minutes - it }?.takeIf { it > 0 }
        return TemplateApplyOutcomes(replace = template, append = append, appendOverMinutes = over)
    }

    private fun exercisePreview(exercise: Exercise): TemplateExercisePreview {
        val first = exercise.sets.firstOrNull()
        val rpe = first?.targetRPE ?: first?.targetRIR?.let { 10.0 - it }
        return TemplateExercisePreview(
            name = exercise.name,
            sets = exercise.sets.size,
            reps = first?.plannedRepAnchor(),
            rpe = rpe,
            restSeconds = exercise.restTime,
        )
    }
}
