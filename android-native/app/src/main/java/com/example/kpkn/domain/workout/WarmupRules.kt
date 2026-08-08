package com.example.kpkn.domain.workout

import com.example.kpkn.data.models.WarmupSetDefinition

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
        if (warmupSets.any { it.percentageOfWorkingWeight !in 0.1..1.0 }) {
            add("Cada aproximación debe estar entre 10% y 100% de la carga efectiva.")
        }
        if (warmupSets.any { it.targetReps <= 0 }) {
            add("Cada aproximación necesita al menos 1 repetición objetivo.")
        }
    }
}
