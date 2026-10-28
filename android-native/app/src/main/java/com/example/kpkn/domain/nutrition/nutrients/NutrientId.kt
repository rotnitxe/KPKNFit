package com.example.kpkn.domain.nutrition.nutrients

/**
 * Identificador estable de nutriente (F4-5). Replica 1:1 la tabla `NUTRIENTS` de `scripts/nutrition_v2/catalog/nutrients.py`:
 * [id] numérico que no cambia nunca, [key] que se guarda en la instantánea de `LoggedFood` y [unit] de la cantidad.
 */
enum class NutrientId(val id: Int, val key: String, val unit: String) {
    ENERGY(1, "energy", "kcal"),
    PROTEIN(2, "protein", "g"),
    FAT(3, "fat", "g"),
    CARBS(4, "carbs", "g"),
    FIBER(5, "fiber", "g"),
    SUGARS(6, "sugars", "g"),
    ADDED_SUGARS(7, "added_sugars", "g"),
    SAT_FAT(8, "sat_fat", "g"),
    MONO_FAT(9, "mono_fat", "g"),
    POLY_FAT(10, "poly_fat", "g"),
    TRANS_FAT(11, "trans_fat", "g"),
    CHOLESTEROL(12, "cholesterol", "mg"),
    SODIUM(13, "sodium", "mg"),
    POTASSIUM(14, "potassium", "mg"),
    CALCIUM(15, "calcium", "mg"),
    IRON(16, "iron", "mg"),
    MAGNESIUM(17, "magnesium", "mg"),
    PHOSPHORUS(18, "phosphorus", "mg"),
    ZINC(19, "zinc", "mg"),
    SELENIUM(20, "selenium", "µg"),
    IODINE(21, "iodine", "µg"),
    VITAMIN_A_RAE(22, "vitamin_a_rae", "µg"),
    VITAMIN_C(23, "vitamin_c", "mg"),
    VITAMIN_D(24, "vitamin_d", "µg"),
    VITAMIN_E(25, "vitamin_e", "mg"),
    VITAMIN_K(26, "vitamin_k", "µg"),
    THIAMIN(27, "thiamin", "mg"),
    RIBOFLAVIN(28, "riboflavin", "mg"),
    NIACIN(29, "niacin", "mg"),
    VITAMIN_B6(30, "vitamin_b6", "mg"),
    FOLATE_DFE(31, "folate_dfe", "µg"),
    VITAMIN_B12(32, "vitamin_b12", "µg"),
    CHOLINE(33, "choline", "mg"),
    EPA_DHA(34, "epa_dha", "g"),
    ALA(35, "ala", "g"),
    CAFFEINE(36, "caffeine", "mg"),
    ALCOHOL(37, "alcohol", "g"),
    WATER(38, "water", "g"),
    ;

    companion object {
        private val byKey: Map<String, NutrientId> = entries.associateBy { it.key }
        private val byId: Map<Int, NutrientId> = entries.associateBy { it.id }

        fun fromKey(key: String): NutrientId? = byKey[key]

        fun fromId(id: Int): NutrientId? = byId[id]
    }
}
