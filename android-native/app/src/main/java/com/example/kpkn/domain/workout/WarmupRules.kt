package com.example.kpkn.domain.workout

import com.example.kpkn.data.models.WarmupSetDefinition

/** A warm-up at or above this load is close enough to working intensity to surface a warning. */
const val WARMUP_EFFECTIVE_THRESHOLD = 0.85

/**
 * Máximo recomendado de aproximaciones para un bloque con [effectiveSetCount] series efectivas:
 * `max(4, 2 × series efectivas)`, la regla de `WarmupRules.kt:14`.
 */
fun recommendedWarmupMax(effectiveSetCount: Int): Int =
    (effectiveSetCount.coerceAtLeast(1) * 2).coerceAtLeast(4)

/**
 * Carga de una aproximación. El porcentaje se aplica al **peso de trabajo** de la serie efectiva objetivo y solo
 * cae a la capacidad (1RM) cuando esa serie no tiene peso de trabajo. Es la regla que el editor y la sesión en vivo
 * comparten mediante warmupTargetLoadKg, la función ya usada por LV-C.
 *
 * @param workingWeightKg peso de la serie efectiva objetivo, si lo tiene.
 * @param capacityKg capacidad estimada (1RM) para cuando no hay peso de trabajo.
 */
fun warmupLoadKg(
    percentageOfWorkingWeight: Double,
    workingWeightKg: Double?,
    capacityKg: Double?,
): Double? {
    val fraction = normalizeWarmupPercentage(percentageOfWorkingWeight)
    if (!fraction.isFinite() || fraction <= 0.0) return null
    return warmupTargetLoadKg(fraction, workingWeightKg, capacityKg).takeIf { it.isFinite() && it > 0.0 }
}

/** Conservative editor guardrails that keep warm-up volume proportional. */
fun warmupValidationMessages(
    warmupSets: List<WarmupSetDefinition>,
    effectiveSetCount: Int,
): List<String> {
    if (warmupSets.isEmpty()) return emptyList()
    val maxWarmupSets = recommendedWarmupMax(effectiveSetCount)
    return buildList {
        if (warmupSets.size > maxWarmupSets) {
            add("Demasiadas aproximaciones: máximo recomendado $maxWarmupSets para $effectiveSetCount series efectivas.")
        }
        if (warmupSets.any { normalizeWarmupPercentage(it.percentageOfWorkingWeight) !in 0.1..1.0 }) {
            add("Cada aproximación debe estar entre 10% y 100% de la carga efectiva.")
        }
        if (warmupSets.any { normalizeWarmupPercentage(it.percentageOfWorkingWeight) >= WARMUP_EFFECTIVE_THRESHOLD }) {
            add("Una aproximación al 85% o más puede comportarse como una serie efectiva; revisa su carga y repeticiones.")
        }
        if (warmupSets.any { it.targetReps <= 0 }) {
            add("Cada aproximación necesita al menos 1 repetición objetivo.")
        }
    }
}

/** Accepts the legacy 50.0 representation while keeping new editor values as 0.50. */
private fun normalizeWarmupPercentage(rawPercentage: Double): Double =
    if (rawPercentage > 1.0) rawPercentage / 100.0 else rawPercentage
