package com.example.kpkn.domain.exercises

/** Display normalization only; no scores, recovery or inference. */
internal data class MuscleLabelResolution(
    val broad: String,
    val specific: String? = null,
)

private fun normalizeMuscleLabelText(value: String?): String =
    (value ?: "")
        .lowercase()
        .trim()
        .replace("á", "a")
        .replace("é", "e")
        .replace("í", "i")
        .replace("ó", "o")
        .replace("ú", "u")
        .replace("ü", "u")

internal fun resolveMuscleLabel(rawMuscle: String, rawEmphasis: String? = null): MuscleLabelResolution {
    val source = normalizeMuscleLabelText(rawMuscle)
    val emphasis = normalizeMuscleLabelText(rawEmphasis ?: rawMuscle)

    if (source.contains("cuello") || source.contains("cervical") || source.contains("neck")) {
        return MuscleLabelResolution("Cuello")
    }
    if (source.contains("pectineo") || source.contains("aductor") || source.contains("adductor")) {
        return MuscleLabelResolution("Aductores")
    }
    if (source.contains("erector") || source.contains("lumbar") || source.contains("espalda baja") || source.contains("lower back")) {
        return MuscleLabelResolution("Erectores Espinales")
    }
    if (source == "core" || source.contains("transverso")) {
        return MuscleLabelResolution("Core")
    }
    if (source.contains("abdomen") || source.contains("abdominal") || source.contains("oblicuo") ||
        source.contains("recto del abdomen") || source.contains("recto abdominal")
    ) {
        return MuscleLabelResolution("Abdomen")
    }
    if (source.contains("pantorrilla") || source.contains("gemelo") || source.contains("gastrocnemio") || source.contains("soleo") || source.contains("calf")) {
        return MuscleLabelResolution("Pantorrillas")
    }
    if (source.contains("isquio") || source.contains("hamstring") || source.contains("femoral") || source.contains("semitendinoso") || source.contains("semimembranoso")) {
        return MuscleLabelResolution("Isquiosurales")
    }
    if (source.contains("cuadriceps") || source.contains("cuadricep") || source.contains("quad") || source.contains("vasto") || source.contains("recto femoral")) {
        return MuscleLabelResolution("Cuádriceps")
    }
    if (source.contains("antebrazo") || source.contains("forearm")) {
        return MuscleLabelResolution("Antebrazo")
    }
    if (source.contains("tricep")) {
        return MuscleLabelResolution("Tríceps")
    }
    if ((source.contains("bicep") || source.contains("braquial") || source.contains("braquiorradial")) && !source.contains("femoral")) {
        return MuscleLabelResolution("Bíceps")
    }
    if (source.contains("deltoide") || source.contains("deltoides") || source.contains("hombro") || source.contains("shoulder")) {
        val haystack = "$source $emphasis"
        val specific = when {
            haystack.contains("posterior") || haystack.contains("trasero") || haystack.contains("rear") -> "Deltoides Posterior"
            haystack.contains("lateral") || haystack.contains("medio") || haystack.contains("medial") -> "Deltoides Lateral"
            haystack.contains("anterior") || haystack.contains("frontal") || haystack.contains("front") -> "Deltoides Anterior"
            else -> null
        }
        return MuscleLabelResolution("Deltoides", specific)
    }
    if (source.contains("gluteo") || source.contains("gluteos") || source.contains("tensor")) {
        // Broad display group.
        return MuscleLabelResolution("Glúteos")
    }
    if (source.contains("trapecio") || source.contains("upper back")) {
        return MuscleLabelResolution("Trapecio")
    }
    if (source.contains("dorsal") || source.contains("lat") || source.contains("romboide") || source.contains("redondo mayor") || source.contains("espalda") || source.contains("back")) {
        return MuscleLabelResolution("Dorsales")
    }
    if (source.contains("pectoral") || source.contains("pecho") || source.contains("chest")) {
        return MuscleLabelResolution("Pectorales")
    }

    return MuscleLabelResolution(rawMuscle.replaceFirstChar { it.uppercase() })
}

/** UI label: specific head when available, otherwise pillar. */
internal fun muscleDisplayLabel(rawMuscle: String, rawEmphasis: String? = null): String {
    val resolved = resolveMuscleLabel(rawMuscle, rawEmphasis)
    return resolved.specific ?: resolved.broad
}

