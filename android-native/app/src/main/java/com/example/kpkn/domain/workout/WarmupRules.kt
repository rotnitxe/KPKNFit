package com.example.kpkn.domain.workout

import com.example.kpkn.data.models.WarmupSetDefinition

/** A warm-up at or above this load is close enough to working intensity to surface a warning. */
const val WARMUP_EFFECTIVE_THRESHOLD = 0.85

/** Conservative editor guardrails that keep warm-up volume proportional. */
fun warmupValidationMessages(
    warmupSets: List<WarmupSetDefinition>,
    effectiveSetCount: Int,
): List<String> {
    if (warmupSets.isEmpty()) return emptyList()
    val maxWarmupSets = (effectiveSetCount.coerceAtLeast(1) * 2).coerceAtLeast(4)
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
