package com.example.kpkn.domain.nutrition

/** Secciones de la hoja «Nutrientes del día», en el orden en que se muestran. */
enum class NutrientGroup(val label: String) {
    CARBS("Carbohidratos"), FATS("Grasas"), VITAMINS("Vitaminas"), MINERALS("Minerales"), OTHER("Otros"),
}

private val groupedKeys: List<Pair<NutrientGroup, List<NutrientKey>>> = listOf(
    NutrientGroup.CARBS to listOf(NutrientKey.FIBER, NutrientKey.SUGARS, NutrientKey.ADDED_SUGARS),
    NutrientGroup.FATS to listOf(NutrientKey.SAT_FAT, NutrientKey.MONO_FAT, NutrientKey.POLY_FAT, NutrientKey.TRANS_FAT,
        NutrientKey.EPA_DHA, NutrientKey.ALA, NutrientKey.CHOLESTEROL),
    NutrientGroup.VITAMINS to listOf(NutrientKey.VITAMIN_A, NutrientKey.THIAMIN, NutrientKey.RIBOFLAVIN, NutrientKey.NIACIN,
        NutrientKey.VITAMIN_B6, NutrientKey.FOLATE, NutrientKey.VITAMIN_B12, NutrientKey.CHOLINE, NutrientKey.VITAMIN_C,
        NutrientKey.VITAMIN_D, NutrientKey.VITAMIN_E, NutrientKey.VITAMIN_K),
    NutrientGroup.MINERALS to listOf(NutrientKey.CALCIUM, NutrientKey.PHOSPHORUS, NutrientKey.MAGNESIUM, NutrientKey.IRON,
        NutrientKey.ZINC, NutrientKey.SELENIUM, NutrientKey.IODINE, NutrientKey.SODIUM, NutrientKey.POTASSIUM),
    NutrientGroup.OTHER to listOf(NutrientKey.CAFFEINE, NutrientKey.ALCOHOL),
)

/** Grupo al que pertenece el nutriente en la hoja. */
val NutrientKey.group: NutrientGroup
    get() = groupedKeys.first { (_, keys) -> this in keys }.first

/** Los grupos en orden de pantalla, cada uno con sus nutrientes en el orden en que se dibujan. */
fun groupedNutrientKeys(): List<Pair<NutrientGroup, List<NutrientKey>>> = groupedKeys
