package com.example.kpkn.domain.nutrition

/** Grupos de las listas de nutrientes, en el orden del lienzo Registros v4 (M-D4): Vitaminas · Minerales · Grasas · Carbos · Otros. */
enum class NutrientGroup(val label: String) {
    VITAMINS("Vitaminas"), MINERALS("Minerales"), FATS("Grasas"), CARBS("Carbos"), OTHER("Otros"),
}

private val groupedKeys: List<Pair<NutrientGroup, List<NutrientKey>>> = listOf(
    NutrientGroup.VITAMINS to listOf(NutrientKey.VITAMIN_A, NutrientKey.THIAMIN, NutrientKey.RIBOFLAVIN, NutrientKey.NIACIN,
        NutrientKey.VITAMIN_B6, NutrientKey.FOLATE, NutrientKey.VITAMIN_B12, NutrientKey.CHOLINE, NutrientKey.VITAMIN_C,
        NutrientKey.VITAMIN_D, NutrientKey.VITAMIN_E, NutrientKey.VITAMIN_K),
    NutrientGroup.MINERALS to listOf(NutrientKey.CALCIUM, NutrientKey.PHOSPHORUS, NutrientKey.MAGNESIUM, NutrientKey.IRON,
        NutrientKey.ZINC, NutrientKey.SELENIUM, NutrientKey.IODINE, NutrientKey.SODIUM, NutrientKey.POTASSIUM),
    NutrientGroup.FATS to listOf(NutrientKey.SAT_FAT, NutrientKey.MONO_FAT, NutrientKey.POLY_FAT, NutrientKey.TRANS_FAT,
        NutrientKey.EPA_DHA, NutrientKey.ALA, NutrientKey.CHOLESTEROL),
    NutrientGroup.CARBS to listOf(NutrientKey.FIBER, NutrientKey.SUGARS, NutrientKey.ADDED_SUGARS),
    NutrientGroup.OTHER to listOf(NutrientKey.CAFFEINE, NutrientKey.ALCOHOL),
)

/** Grupo al que pertenece el nutriente en las listas. */
val NutrientKey.group: NutrientGroup
    get() = groupedKeys.first { (_, keys) -> this in keys }.first

/** Los grupos en orden de pantalla, cada uno con sus nutrientes en el orden en que se dibujan. */
fun groupedNutrientKeys(): List<Pair<NutrientGroup, List<NutrientKey>>> = groupedKeys

/** Las tres bandas de la huella: vitaminas, minerales y «otros» (fibra, omega-3 ALA y cafeína). */
enum class FootprintBand(val label: String) { VITAMINS("VITAMINAS"), MINERALS("MINERALES"), OTHER("OTROS") }

/** Un nutriente de la huella: su sigla (la que se escribe sobre las tres barras que más aportan) y su banda. */
data class FootprintSlot(val key: NutrientKey, val sigla: String, val band: FootprintBand)

/**
 * Los 24 nutrientes de la huella, en el orden del lienzo (M-D2): 12 vitaminas, 9 minerales y 3 «otros». El resto de claves
 * (azúcares, tipos de grasa, EPA + DHA, colesterol, alcohol) solo aparece en las listas.
 */
val footprintSlots: List<FootprintSlot> = listOf(
    FootprintSlot(NutrientKey.VITAMIN_A, "A", FootprintBand.VITAMINS),
    FootprintSlot(NutrientKey.THIAMIN, "B1", FootprintBand.VITAMINS),
    FootprintSlot(NutrientKey.RIBOFLAVIN, "B2", FootprintBand.VITAMINS),
    FootprintSlot(NutrientKey.NIACIN, "B3", FootprintBand.VITAMINS),
    FootprintSlot(NutrientKey.VITAMIN_B6, "B6", FootprintBand.VITAMINS),
    FootprintSlot(NutrientKey.FOLATE, "B9", FootprintBand.VITAMINS),
    FootprintSlot(NutrientKey.VITAMIN_B12, "B12", FootprintBand.VITAMINS),
    FootprintSlot(NutrientKey.CHOLINE, "Co", FootprintBand.VITAMINS),
    FootprintSlot(NutrientKey.VITAMIN_C, "C", FootprintBand.VITAMINS),
    FootprintSlot(NutrientKey.VITAMIN_D, "D", FootprintBand.VITAMINS),
    FootprintSlot(NutrientKey.VITAMIN_E, "E", FootprintBand.VITAMINS),
    FootprintSlot(NutrientKey.VITAMIN_K, "K", FootprintBand.VITAMINS),
    FootprintSlot(NutrientKey.CALCIUM, "Ca", FootprintBand.MINERALS),
    FootprintSlot(NutrientKey.PHOSPHORUS, "P", FootprintBand.MINERALS),
    FootprintSlot(NutrientKey.MAGNESIUM, "Mg", FootprintBand.MINERALS),
    FootprintSlot(NutrientKey.IRON, "Fe", FootprintBand.MINERALS),
    FootprintSlot(NutrientKey.ZINC, "Zn", FootprintBand.MINERALS),
    FootprintSlot(NutrientKey.SELENIUM, "Se", FootprintBand.MINERALS),
    FootprintSlot(NutrientKey.IODINE, "I", FootprintBand.MINERALS),
    FootprintSlot(NutrientKey.SODIUM, "Na", FootprintBand.MINERALS),
    FootprintSlot(NutrientKey.POTASSIUM, "K", FootprintBand.MINERALS),
    FootprintSlot(NutrientKey.FIBER, "Fi", FootprintBand.OTHER),
    FootprintSlot(NutrientKey.ALA, "ω3", FootprintBand.OTHER),
    FootprintSlot(NutrientKey.CAFFEINE, "Caf", FootprintBand.OTHER),
)

/** Nutrientes con tope (se leen «del máximo», no como meta): sodio, cafeína, grasa saturada, trans y azúcares añadidos. */
val NutrientKey.isCeiling: Boolean
    get() = this in setOf(NutrientKey.SODIUM, NutrientKey.CAFFEINE, NutrientKey.SAT_FAT, NutrientKey.TRANS_FAT, NutrientKey.ADDED_SUGARS)
