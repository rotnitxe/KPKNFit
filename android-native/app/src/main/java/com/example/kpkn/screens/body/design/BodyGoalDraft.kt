package com.example.kpkn.screens.body.design

import com.example.kpkn.data.models.BodyGoal
import com.example.kpkn.data.models.BodyMetric
import com.example.kpkn.data.models.CalculationOrigin
import com.example.kpkn.data.models.GoalMetric
import java.util.UUID

/**
 * Borrador de la hoja de objetivo propio (BODY_V1 §2.5): la métrica elegida, la meta si el usuario ya la ha fijado (null hasta entonces,
 * nunca un valor inventado), el id del objetivo propio que se reemplaza y su fecha de inicio.
 */
data class BodyGoalDraft(
    val metric: GoalMetric,
    val target: Double? = null,
    val existingId: String? = null,
    val createdAtEpochMs: Long? = null,
) {
    /** Cambia la métrica: la meta anterior no vale para la nueva, así que se empieza sin meta y con una fecha de inicio nueva. */
    fun withMetric(next: GoalMetric): BodyGoalDraft =
        if (next == metric) this else copy(metric = next, target = null, createdAtEpochMs = null)
}

/** Métrica de medida que corresponde a una métrica de objetivo. */
internal fun bodyMetricOf(metric: GoalMetric): BodyMetric = when (metric) {
    GoalMetric.WEIGHT -> BodyMetric.WEIGHT
    GoalMetric.BODY_FAT -> BodyMetric.BODY_FAT_PERCENT
    GoalMetric.MUSCLE_MASS -> BodyMetric.MUSCLE_MASS_PERCENT
}

/**
 * Objetivo propio que guarda la hoja: origen manual, sin plan, con su fecha de inicio (la del objetivo editado si es la misma métrica, o
 * la de ahora) y la de la última edición. Null sin meta válida: un objetivo nunca se guarda sin valor.
 */
internal fun bodyManualGoalFor(draft: BodyGoalDraft, nowEpochMs: Long): BodyGoal? {
    val target = draft.target?.takeIf { it.isFinite() && it > 0.0 } ?: return null
    return BodyGoal(
        id = draft.existingId ?: UUID.randomUUID().toString(),
        metric = bodyMetricOf(draft.metric),
        targetValueSi = target,
        unitSi = if (draft.metric == GoalMetric.WEIGHT) "kg" else "%",
        origin = CalculationOrigin.MANUAL,
        linkedPlanId = null,
        createdAtEpochMs = draft.createdAtEpochMs ?: nowEpochMs,
        updatedAtEpochMs = nowEpochMs,
    )
}
