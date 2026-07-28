package com.example.kpkn.domain.auge

/**
 * Contrato de claves musculares RINGS:
 *
 * - **Pillar** ([getAugeMusclePillarId]): storage / engine / `perMuscle` /
 *   `manualMuscleBatteries` / adaptive multipliers / learning. Nunca una cabeza
 *   específica (`"Deltoides"`, no `"Deltoides Lateral"`).
 * - **Display** ([getAugeMuscleDisplayId]): solo etiquetas UI. Al leer/escribir
 *   batteries o multipliers, siempre resolver a pilar.
 */
internal data class AugeMuscleResolution(
    val broad: String,
    val specific: String? = null,
)

private fun normalizeAugeText(value: String?): String =
    (value ?: "")
        .lowercase()
        .trim()
        .replace("á", "a")
        .replace("é", "e")
        .replace("í", "i")
        .replace("ó", "o")
        .replace("ú", "u")
        .replace("ü", "u")

internal fun resolveAugeMuscle(rawMuscle: String, rawEmphasis: String? = null): AugeMuscleResolution {
    val source = normalizeAugeText(rawMuscle)
    val emphasis = normalizeAugeText(rawEmphasis ?: rawMuscle)

    if (source.contains("cuello") || source.contains("cervical") || source.contains("neck")) {
        return AugeMuscleResolution("Cuello")
    }
    if (source.contains("pectineo") || source.contains("aductor") || source.contains("adductor")) {
        return AugeMuscleResolution("Aductores")
    }
    if (source.contains("erector") || source.contains("lumbar") || source.contains("espalda baja") || source.contains("lower back")) {
        return AugeMuscleResolution("Erectores Espinales")
    }
    if (source == "core" || source.contains("transverso")) {
        return AugeMuscleResolution("Core")
    }
    if (source.contains("abdomen") || source.contains("abdominal") || source.contains("oblicuo") ||
        source.contains("recto del abdomen") || source.contains("recto abdominal")
    ) {
        return AugeMuscleResolution("Abdomen")
    }
    if (source.contains("pantorrilla") || source.contains("gemelo") || source.contains("gastrocnemio") || source.contains("soleo") || source.contains("calf")) {
        return AugeMuscleResolution("Pantorrillas")
    }
    if (source.contains("isquio") || source.contains("hamstring") || source.contains("femoral") || source.contains("semitendinoso") || source.contains("semimembranoso")) {
        return AugeMuscleResolution("Isquiosurales")
    }
    if (source.contains("cuadriceps") || source.contains("cuadricep") || source.contains("quad") || source.contains("vasto") || source.contains("recto femoral")) {
        return AugeMuscleResolution("Cuádriceps")
    }
    if (source.contains("antebrazo") || source.contains("forearm")) {
        return AugeMuscleResolution("Antebrazo")
    }
    if (source.contains("tricep")) {
        return AugeMuscleResolution("Tríceps")
    }
    if ((source.contains("bicep") || source.contains("braquial") || source.contains("braquiorradial")) && !source.contains("femoral")) {
        return AugeMuscleResolution("Bíceps")
    }
    if (source.contains("deltoide") || source.contains("deltoides") || source.contains("hombro") || source.contains("shoulder")) {
        val haystack = "$source $emphasis"
        val specific = when {
            haystack.contains("posterior") || haystack.contains("trasero") || haystack.contains("rear") -> "Deltoides Posterior"
            haystack.contains("lateral") || haystack.contains("medio") || haystack.contains("medial") -> "Deltoides Lateral"
            haystack.contains("anterior") || haystack.contains("frontal") || haystack.contains("front") -> "Deltoides Anterior"
            else -> null
        }
        return AugeMuscleResolution("Deltoides", specific)
    }
    if (source.contains("gluteo") || source.contains("gluteos") || source.contains("tensor")) {
        // Pillar only — heads are display/emphasis, never separate batteries.
        return AugeMuscleResolution("Glúteos")
    }
    if (source.contains("trapecio") || source.contains("upper back")) {
        return AugeMuscleResolution("Trapecio")
    }
    if (source.contains("dorsal") || source.contains("lat") || source.contains("romboide") || source.contains("redondo mayor") || source.contains("espalda") || source.contains("back")) {
        return AugeMuscleResolution("Dorsales")
    }
    if (source.contains("pectoral") || source.contains("pecho") || source.contains("chest")) {
        return AugeMuscleResolution("Pectorales")
    }

    return AugeMuscleResolution(rawMuscle.replaceFirstChar { it.uppercase() })
}

/** UI label: specific head when available, otherwise pillar. */
internal fun getAugeMuscleDisplayId(rawMuscle: String, rawEmphasis: String? = null): String {
    val resolved = resolveAugeMuscle(rawMuscle, rawEmphasis)
    return resolved.specific ?: resolved.broad
}

/** Pillar / broad group id used by perMuscle batteries (never a specific head). */
internal fun getAugeMusclePillarId(rawMuscle: String, rawEmphasis: String? = null): String =
    resolveAugeMuscle(rawMuscle, rawEmphasis).broad

/** Alias explícito del contrato de storage/engine. */
internal fun toAugePillarKey(rawMuscle: String, rawEmphasis: String? = null): String =
    getAugeMusclePillarId(rawMuscle, rawEmphasis)

/**
 * Adaptive-cache key for muscle drain multipliers: lowercase pillar.
 */
internal fun toAugeAdaptiveMuscleKey(rawMuscle: String, rawEmphasis: String? = null): String =
    getAugeMusclePillarId(rawMuscle, rawEmphasis).lowercase().trim()

/**
 * Lookup against maps that may contain pillar and/or legacy display-head keys.
 * Prefers exact pillar, then display, then any entry whose pillar matches.
 */
internal fun <T> lookupMuscleValue(
    map: Map<String, T>,
    rawMuscle: String,
    rawEmphasis: String? = null,
): T? {
    if (map.isEmpty()) return null
    val pillar = getAugeMusclePillarId(rawMuscle, rawEmphasis)
    val display = getAugeMuscleDisplayId(rawMuscle, rawEmphasis)
    map[pillar]?.let { return it }
    if (display != pillar) map[display]?.let { return it }
    val pillarLower = pillar.lowercase()
    map[pillarLower]?.let { return it }
    map.entries.firstOrNull { getAugeMusclePillarId(it.key) == pillar }?.value?.let { return it }
    return map.entries.firstOrNull {
        getAugeMusclePillarId(it.key).lowercase() == pillarLower
    }?.value
}

internal fun lookupMuscleScore(
    map: Map<String, Int>,
    rawMuscle: String,
    rawEmphasis: String? = null,
): Int? = lookupMuscleValue(map, rawMuscle, rawEmphasis)

/**
 * Reclaves a pilar. Si pillar y display coexistem, prevalece el valor del pilar.
 */
internal fun remapMuscleIntMapToPillars(map: Map<String, Int>): Map<String, Int> {
    if (map.isEmpty()) return emptyMap()
    val result = linkedMapOf<String, Int>()
    val pillarFirst = map.entries.sortedBy { (key, _) ->
        val pillar = getAugeMusclePillarId(key)
        if (key == pillar) 0 else 1
    }
    pillarFirst.forEach { (key, value) ->
        val pillar = getAugeMusclePillarId(key)
        if (key == pillar || !result.containsKey(pillar)) {
            result[pillar] = value.coerceIn(0, 100)
        }
    }
    return result
}

/**
 * Reclaves multipliers (keys suelen ser lowercase) a pillar lowercase.
 * Si hay colisión, promedio simple.
 */
internal fun remapMuscleMultiplierMapToPillars(map: Map<String, Double>): Map<String, Double> {
    if (map.isEmpty()) return emptyMap()
    val buckets = linkedMapOf<String, MutableList<Double>>()
    map.forEach { (key, value) ->
        val pillarKey = toAugeAdaptiveMuscleKey(key)
        buckets.getOrPut(pillarKey) { mutableListOf() }.add(value)
    }
    return buckets.mapValues { (_, values) -> values.average() }
}

internal fun lookupMuscleDrainMultiplier(
    multipliers: Map<String, Double>,
    rawMuscle: String,
    rawEmphasis: String? = null,
    default: Double = 1.0,
): Double {
    if (multipliers.isEmpty()) return default
    val pillarKey = toAugeAdaptiveMuscleKey(rawMuscle, rawEmphasis)
    multipliers[pillarKey]?.let { return it }
    // Legacy: display-head or raw DB string stored as key
    val remapped = remapMuscleMultiplierMapToPillars(multipliers)
    return remapped[pillarKey] ?: default
}

internal fun matchesAugeMuscleTarget(rawMuscle: String, target: String, rawEmphasis: String? = null): Boolean {
    val targetResolved = resolveAugeMuscle(target)
    val muscleResolved = resolveAugeMuscle(rawMuscle, rawEmphasis)

    if (muscleResolved.broad != targetResolved.broad) return false
    if (targetResolved.specific == null) return true

    return getAugeMuscleDisplayId(rawMuscle, rawEmphasis) == targetResolved.specific
}
