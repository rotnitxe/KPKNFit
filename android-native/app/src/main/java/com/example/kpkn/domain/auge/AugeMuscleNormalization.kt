package com.example.kpkn.domain.auge

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
    if (source == "core") {
        return AugeMuscleResolution("Core")
    }
    if (source.contains("abdomen") || source.contains("abdominal") || source.contains("oblicuo") || source.contains("transverso")) {
        return AugeMuscleResolution("Abdomen")
    }
    if (source.contains("pantorrilla") || source.contains("gemelo") || source.contains("gastrocnemio") || source.contains("soleo") || source.contains("calf")) {
        return AugeMuscleResolution("Pantorrillas")
    }
    if (source.contains("gluteo") || source.contains("gluteos")) {
        return AugeMuscleResolution("Glúteos")
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
        val specific = when {
            source.contains("posterior") || emphasis == "posterior" || emphasis == "rear" -> "Deltoides Posterior"
            source.contains("lateral") || source.contains("medio") || source.contains("medial") || emphasis == "lateral" || emphasis == "medio" -> "Deltoides Lateral"
            source.contains("anterior") || source.contains("frontal") || emphasis == "anterior" || emphasis == "front" -> "Deltoides Anterior"
            else -> null
        }
        return AugeMuscleResolution("Deltoides", specific)
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

internal fun getAugeMuscleDisplayId(rawMuscle: String, rawEmphasis: String? = null): String {
    val resolved = resolveAugeMuscle(rawMuscle, rawEmphasis)
    return resolved.specific ?: resolved.broad
}

/** Pillar / broad group id used by perMuscle batteries (never a specific head). */
internal fun getAugeMusclePillarId(rawMuscle: String, rawEmphasis: String? = null): String =
    resolveAugeMuscle(rawMuscle, rawEmphasis).broad

internal fun matchesAugeMuscleTarget(rawMuscle: String, target: String, rawEmphasis: String? = null): Boolean {
    val targetResolved = resolveAugeMuscle(target)
    val muscleResolved = resolveAugeMuscle(rawMuscle, rawEmphasis)

    if (muscleResolved.broad != targetResolved.broad) return false
    if (targetResolved.specific == null) return true

    return getAugeMuscleDisplayId(rawMuscle, rawEmphasis) == targetResolved.specific
}
