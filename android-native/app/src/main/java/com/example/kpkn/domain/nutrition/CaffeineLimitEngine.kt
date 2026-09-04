package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.PregnancyLactation

/**
 * Daily caffeine limits based on EFSA (2015), FDA (400 mg/day), ACOG/EFSA pregnancy
 * (200 mg), and Health Canada adolescent guidance (2.5 mg/kg, cap 200 mg).
 */
object CaffeineLimitEngine {

    const val IDEAL_RANGE_MIN_MG = 160.0
    const val IDEAL_RANGE_MAX_MG = 250.0
    private const val ADULT_ABSOLUTE_MAX_MG = 400.0
    private const val ADULT_MG_PER_KG = 6.0
    private const val ADOLESCENT_MG_PER_KG = 2.5
    private const val ADOLESCENT_ABSOLUTE_MAX_MG = 200.0
    private const val PREGNANCY_LACTATION_MAX_MG = 200.0
    private const val ADULT_AGE_THRESHOLD = 18

    data class CaffeineLimitInput(
        val weightKg: Double? = null,
        val ageYears: Int? = null,
        val pregnancyLactation: PregnancyLactation = PregnancyLactation.NONE,
    )

    data class CaffeineLimits(
        val idealMinMg: Double?,
        val idealMaxMg: Double?,
        val safetyMaxMg: Double,
        val singleDoseReferenceMg: Double?,
        val rationale: String,
    )

    fun computeLimits(input: CaffeineLimitInput): CaffeineLimits {
        val weight = input.weightKg?.takeIf { it > 0.0 }
        val age = input.ageYears
        val isMinor = age != null && age < ADULT_AGE_THRESHOLD
        val isPregnantOrLactating = input.pregnancyLactation != PregnancyLactation.NONE

        if (isPregnantOrLactating) {
            val idealMax = minOf(IDEAL_RANGE_MAX_MG, PREGNANCY_LACTATION_MAX_MG)
            return CaffeineLimits(
                idealMinMg = IDEAL_RANGE_MIN_MG,
                idealMaxMg = idealMax,
                safetyMaxMg = PREGNANCY_LACTATION_MAX_MG,
                singleDoseReferenceMg = weight?.let { 3.0 * it },
                rationale = "Embarazo/lactancia: máximo 200 mg/día (EFSA/ACOG).",
            )
        }

        if (isMinor) {
            val safetyMax = if (weight != null) {
                minOf(weight * ADOLESCENT_MG_PER_KG, ADOLESCENT_ABSOLUTE_MAX_MG)
            } else {
                ADOLESCENT_ABSOLUTE_MAX_MG
            }
            return CaffeineLimits(
                idealMinMg = null,
                idealMaxMg = null,
                safetyMaxMg = safetyMax,
                singleDoseReferenceMg = weight?.let { 3.0 * it },
                rationale = "Menores de 18 años: máximo ${safetyMax.toInt()} mg/día (2,5 mg/kg).",
            )
        }

        val safetyMax = if (weight != null) {
            minOf(weight * ADULT_MG_PER_KG, ADULT_ABSOLUTE_MAX_MG)
        } else {
            ADULT_ABSOLUTE_MAX_MG
        }

        val rationale = if (weight != null) {
            "Máximo ${safetyMax.toInt()} mg/día (6 mg/kg, tope 400 mg EFSA/FDA)."
        } else {
            "Máximo 400 mg/día (EFSA/FDA). Indica tu peso para un techo personalizado."
        }

        return CaffeineLimits(
            idealMinMg = IDEAL_RANGE_MIN_MG,
            idealMaxMg = IDEAL_RANGE_MAX_MG,
            safetyMaxMg = safetyMax,
            singleDoseReferenceMg = weight?.let { 3.0 * it },
            rationale = rationale,
        )
    }
}
