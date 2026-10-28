package com.example.kpkn.domain.nutrition

/** Límite de guía de ingesta total (F4-6): solo un tope, no una meta; no es una DRI. */
data class GuideLimit(
    val nutrient: NutrientKey,
    val maxAmount: Double?,
    val maxEnergyFraction: Double?,
    val kcalPerUnit: Double?,
    val sourceUrl: String,
) {
    /** Tope en la unidad del nutriente; si depende de la energía y [energyKcal] no es finita y > 0, null. */
    fun ceiling(energyKcal: Double?): Double? {
        if (maxAmount != null) return maxAmount
        val fraction = maxEnergyFraction ?: return null
        val perUnit = kcalPerUnit ?: return null
        if (energyKcal == null || !energyKcal.isFinite() || energyKcal <= 0.0) return null
        return (fraction * energyKcal / perUnit).takeIf { it.isFinite() }
    }

    /** True solo con un total conocido, finito y por encima del tope. */
    fun exceeds(total: Double?, energyKcal: Double?): Boolean {
        if (total == null || !total.isFinite()) return false
        val ceiling = ceiling(energyKcal) ?: return false
        return total > ceiling
    }
}

object GuideLimits {
    private const val WHO_FATS = "https://www.who.int/publications/i/item/9789240073630"
    private const val WHO_SUGARS = "https://www.who.int/publications/i/item/9789241549028"
    private const val EFSA_CAFFEINE = "https://www.efsa.europa.eu/en/efsajournal/pub/4102"
    private const val FDA_CAFFEINE = "https://www.fda.gov/consumers/consumer-updates/spilling-beans-how-much-caffeine-too-much"

    fun lookup(nutrient: NutrientKey, profile: DriProfile?): GuideLimit? = when (nutrient) {
        NutrientKey.SAT_FAT -> GuideLimit(nutrient, null, 0.10, 9.0, WHO_FATS)
        NutrientKey.TRANS_FAT -> GuideLimit(nutrient, null, 0.01, 9.0, WHO_FATS)
        NutrientKey.ADDED_SUGARS -> GuideLimit(nutrient, null, 0.10, 4.0, WHO_SUGARS)
        NutrientKey.CAFFEINE -> when {
            profile == null -> null
            profile.stage == DriStage.PREGNANT || profile.stage == DriStage.LACTATING -> GuideLimit(nutrient, 200.0, null, null, EFSA_CAFFEINE)
            else -> GuideLimit(nutrient, 400.0, null, null, FDA_CAFFEINE)
        }
        else -> null
    }
}
