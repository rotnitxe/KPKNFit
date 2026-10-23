package com.example.kpkn.domain.workout

import com.example.kpkn.data.models.WarmupSetDefinition

/** A warm-up at or above this load is close enough to working intensity to surface a warning. */
const val WARMUP_EFFECTIVE_THRESHOLD = 0.85

/** Máximo recomendado de aproximaciones: el doble de las series efectivas, y nunca menos de 4. */
fun warmupMaxRecommended(effectiveSetCount: Int): Int = (effectiveSetCount.coerceAtLeast(1) * 2).coerceAtLeast(4)

private fun warmupTooManyMessage(maxWarmupSets: Int, effectiveSetCount: Int): String =
    "Demasiadas aproximaciones: máximo recomendado $maxWarmupSets para $effectiveSetCount series efectivas."

private const val WARMUP_NEAR_EFFECTIVE_MESSAGE =
    "Una aproximación al 85% o más puede comportarse como una serie efectiva; revisa su carga y repeticiones."

/**
 * Aviso al AÑADIR una aproximación en vivo (LV-C): el mismo texto del editor si ya hay más del máximo recomendado o si la
 * recién añadida (la última) llega al 85 % de la carga efectiva. Null si no hay nada que avisar.
 */
fun warmupLiveAddAdvisory(warmupSets: List<WarmupSetDefinition>, effectiveSetCount: Int): String? {
    if (warmupSets.isEmpty()) return null
    val max = warmupMaxRecommended(effectiveSetCount)
    if (warmupSets.size > max) return warmupTooManyMessage(max, effectiveSetCount)
    if (normalizeWarmupPercentage(warmupSets.last().percentageOfWorkingWeight) >= WARMUP_EFFECTIVE_THRESHOLD) {
        return WARMUP_NEAR_EFFECTIVE_MESSAGE
    }
    return null
}

/** Nombre compartido por las fichas del editor; conserva la regla única de LV-C. */
fun recommendedWarmupMax(effectiveSetCount: Int): Int = warmupMaxRecommended(effectiveSetCount)

/** Aplica el porcentaje al peso de trabajo objetivo y solo cae a la capacidad cuando falta. */
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
    val maxWarmupSets = warmupMaxRecommended(effectiveSetCount)
    return buildList {
        if (warmupSets.size > maxWarmupSets) {
            add("Demasiadas aproximaciones: máximo recomendado $maxWarmupSets para $effectiveSetCount series efectivas.")
        }
        if (warmupSets.any { normalizeWarmupPercentage(it.percentageOfWorkingWeight) !in 0.1..1.0 }) {
            add("Cada aproximación debe estar entre 10% y 100% de la carga efectiva.")
        }
        if (warmupSets.any { normalizeWarmupPercentage(it.percentageOfWorkingWeight) >= WARMUP_EFFECTIVE_THRESHOLD }) {
            add(WARMUP_NEAR_EFFECTIVE_MESSAGE)
        }
        if (warmupSets.any { it.targetReps <= 0 }) {
            add("Cada aproximación necesita al menos 1 repetición objetivo.")
        }
    }
}

/** Accepts the legacy 50.0 representation while keeping new editor values as 0.50. */
private fun normalizeWarmupPercentage(rawPercentage: Double): Double =
    if (rawPercentage > 1.0) rawPercentage / 100.0 else rawPercentage
