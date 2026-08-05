package com.example.kpkn.domain.exercises

/**
 * Derives the canonical emphasis keyword (superior/medio/inferior,
 * anterior/lateral/posterior, mayor/medio) of the Pectoral, Gluteo and
 * Deltoides involvement for a given v2 exercise configuration.
 *
 * The v2 catalog carries no structured portion info: portions exist only in
 * prose notes and as legacy evidence. This engine derives them at runtime from
 * the definition family (incline/decline), movement pattern
 * (shoulder_abduction* / horizontal_abduction / pushes) and selected options
 * (pulley height, support angle).
 *
 * Contract: volume and AUGE engines stay pillar-agnostic; the keyword only
 * feeds display heads, chips and per-portion breakdowns via
 * [MuscleHeadResolution].
 */
object EmphasisEngine {

    private val LATERAL_RAISE_PATTERNS = setOf(
        "shoulder_abduction",
        "shoulder_abduction_diagonal",
        "shoulder_abduction_full_rom",
    )

    fun deriveEmphasis(
        muscleId: String,
        definitionId: String,
        movementPatternId: String?,
        selectedOptions: Map<String, String>,
    ): String? = when (muscleId) {
        "pectoralis" -> pectoralEmphasis(definitionId, selectedOptions)
        "deltoid" -> deltoidEmphasis(definitionId, movementPatternId)
        "gluteus_maximus" -> "mayor"
        "gluteus_medius" -> "medio"
        else -> null
    }

    private fun pectoralEmphasis(definitionId: String, selectedOptions: Map<String, String>): String {
        // Cable crossover: polea baja apunta a la porción clavicular (superior),
        // polea alta a la inferior (esternal).
        when (selectedOptions["pulley_height"]) {
            "high" -> return "inferior"
            "low" -> return "superior"
        }
        // Push-up con pies elevados sube el foco a la porción superior.
        if (selectedOptions["support_angle"] == "feet_elevated") return "superior"
        val id = definitionId.lowercase()
        return when {
            id.contains("incline") -> "superior"
            id.contains("decline") -> "inferior"
            else -> "medio"
        }
    }

    private fun deltoidEmphasis(definitionId: String, movementPatternId: String?): String {
        val id = definitionId.lowercase()
        if (id.contains("face_pull") || id.contains("rear") || id.contains("reverse") || id.contains("posterior")) {
            return "posterior"
        }
        val pattern = movementPatternId
        if (pattern == "horizontal_abduction") return "posterior"
        if (pattern != null && LATERAL_RAISE_PATTERNS.contains(pattern)) return "lateral"
        if (id.contains("upright_row")) return "lateral"
        // Presses (horizontal/vertical/arnold/landmine…) y asistencias de empuje.
        return "anterior"
    }
}
