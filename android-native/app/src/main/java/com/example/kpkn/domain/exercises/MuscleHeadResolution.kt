package com.example.kpkn.domain.exercises

import com.example.kpkn.domain.training.VolumeCalculator

/**
 * Resuelve cabezas/porciones para UI y filtros.
 *
 * Contrato:
 * - Volumen/fatiga AUGE agregan al **padre** (`Deltoides`, `Glúteos`, …).
 * - Las cabezas solo sirven para emphasis, filtros y breakdown visual.
 * - El haystack combina `muscle` + `emphasis` para tolerar legacy
 *   (`"Deltoides Posterior"`, `"Glúteo Medio"`) además de keywords canónicos.
 */
object MuscleHeadResolution {

    fun resolveDisplayHead(muscle: String, emphasis: String?): String? {
        val canonical = VolumeCalculator.normalizeCanonicalMuscleGroup(muscle, emphasis)
        val haystack = haystackOf(muscle, emphasis)
        return when (canonical) {
            "Deltoides" -> when {
                containsAny(haystack, "posterior", "trasero", "rear") -> "Deltoides Posterior"
                containsAny(haystack, "lateral", "medio", "medial") -> "Deltoides Lateral"
                containsAny(haystack, "anterior", "frontal", "front") -> "Deltoides Anterior"
                else -> "Deltoides Anterior"
            }
            "Glúteos" -> when {
                containsAny(haystack, "menor", "minimus", "mínimo", "minimo") -> "Glúteo Menor"
                containsAny(haystack, "medio", "medius", "tensor") -> "Glúteo Medio"
                containsAny(haystack, "mayor", "maximus") -> "Glúteo Mayor"
                else -> "Glúteo Mayor"
            }
            "Pectorales" -> when {
                containsAny(haystack, "superior", "clavicular") -> "Pectoral Superior"
                containsAny(haystack, "inferior", "esternal") -> "Pectoral Inferior"
                else -> "Pectoral Medio"
            }
            "Trapecio" -> when {
                containsAny(haystack, "superior", "descendente") -> "Trapecio Superior"
                containsAny(haystack, "inferior", "ascendente") -> "Trapecio Inferior"
                else -> "Trapecio Medio"
            }
            "Pantorrillas" -> when {
                containsAny(haystack, "gastrocnemio", "gemelo") -> "Gastrocnemio"
                containsAny(haystack, "sóleo", "soleo") -> "Sóleo"
                else -> "Sóleo"
            }
            else -> null
        }
    }

    /**
     * Keyword canónico del catálogo (`posterior`, `medio`, `mayor`…) o null si no aplica.
     */
    fun resolveEmphasisKeyword(canonicalParent: String, emphasis: String?): String? {
        val anatomy = MUSCLE_BY_CANONICAL[canonicalParent] ?: return null
        val e = emphasis?.lowercase()?.trim().orEmpty()
        if (e.isBlank()) return null

        anatomy.heads.firstOrNull { head ->
            val kw = head.emphasisKeyword ?: return@firstOrNull false
            e == kw
        }?.emphasisKeyword?.let { return it }

        // Legacy: "Deltoides Posterior", "Glúteo Medio", "lateral" → keyword
        if (canonicalParent == "Deltoides") {
            return when {
                containsAny(e, "posterior", "trasero", "rear") -> "posterior"
                containsAny(e, "lateral", "medio", "medial") -> "medio"
                containsAny(e, "anterior", "frontal", "front") -> "anterior"
                else -> null
            }
        }
        if (canonicalParent == "Glúteos") {
            return when {
                containsAny(e, "menor", "minimus", "mínimo", "minimo") -> "menor"
                containsAny(e, "medio", "medius", "tensor") -> "medio"
                containsAny(e, "mayor", "maximus") -> "mayor"
                else -> null
            }
        }

        return anatomy.heads.firstOrNull { head ->
            val kw = head.emphasisKeyword ?: return@firstOrNull false
            e.contains(kw)
        }?.emphasisKeyword
    }

    fun matchesHead(canonicalParent: String, emphasis: String?, head: MuscleHead): Boolean {
        val resolved = resolveEmphasisKeyword(canonicalParent, emphasis)
        return if (head.emphasisKeyword != null) {
            resolved == head.emphasisKeyword
        } else {
            resolved == null
        }
    }

    private fun haystackOf(muscle: String, emphasis: String?): String =
        listOfNotNull(muscle, emphasis)
            .joinToString(" ")
            .lowercase()
            .replace("-", " ")
            .replace("_", " ")
            .trim()

    private fun containsAny(haystack: String, vararg needles: String): Boolean =
        needles.any { haystack.contains(it) }
}
