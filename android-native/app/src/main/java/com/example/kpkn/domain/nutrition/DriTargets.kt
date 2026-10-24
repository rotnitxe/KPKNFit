package com.example.kpkn.domain.nutrition

/** Referencias adultas del CSV aprobado: NAM/IOM y NIH ODS, revisión de la ola 24-oct-2026.
 * Los 240 valores se contrastan con la copia íntegra del CSV en pruebas. No hay IO en dominio.
 */
enum class NutrientKey(val csvName: String, val label: String, val unit: String, val inCatalog: Boolean) {
    FIBER("fibra total", "Fibra", "g", true),
    SODIUM("sodio", "Sodio", "mg", true),
    POTASSIUM("potasio", "Potasio", "mg", true),
    CALCIUM("calcio", "Calcio", "mg", true),
    IRON("hierro", "Hierro", "mg", true),
    MAGNESIUM("magnesio", "Magnesio", "mg", true),
    ZINC("zinc", "Zinc", "mg", false),
    SELENIUM("selenio", "Selenio", "µg", true),
    IODINE("yodo", "Yodo", "µg", false),
    VITAMIN_A("vitamina A", "Vitamina A", "µg RAE", true),
    VITAMIN_C("vitamina C", "Vitamina C", "mg", false),
    VITAMIN_D("vitamina D", "Vitamina D", "µg", false),
    VITAMIN_E("vitamina E", "Vitamina E", "mg α-tocoferol", false),
    VITAMIN_K("vitamina K", "Vitamina K", "µg", false),
    VITAMIN_B6("vitamina B6", "Vitamina B6", "mg", false),
    VITAMIN_B12("vitamina B12", "Vitamina B12", "µg", true),
    FOLATE("folato", "Folato", "µg DFE", false),
    THIAMIN("tiamina", "Tiamina", "mg", false),
    RIBOFLAVIN("riboflavina", "Riboflavina", "mg", false),
    NIACIN("niacina", "Niacina", "mg NE", false);
}

enum class DriSex { MALE, FEMALE }
enum class DriStage { ADULT, PREGNANT, LACTATING }
enum class DriKind { RDA, AI }
enum class UpperLimitScope { TOTAL, SUPPLEMENTS_ONLY, PREFORMED_ONLY, NONE }
data class DriProfile(val age: Int, val sex: DriSex, val stage: DriStage = DriStage.ADULT)
data class DriTarget(
    val nutrient: NutrientKey, val kind: DriKind, val value: Double,
    val upperLimit: Double?, val upperLimitUnit: String?, val scope: UpperLimitScope,
    val sourceUrl: String,
) {
    /** CDRR para sodio; los UL de suplementos o retinol no son alarmas de ingesta total. */
    val totalCeiling: Double? get() = if (nutrient == NutrientKey.SODIUM) 2300.0 else upperLimit.takeIf { scope == UpperLimitScope.TOTAL }
    val comparisonValue: Double get() = if (nutrient == NutrientKey.SODIUM) 2300.0 else value
    fun exceedsKnownTotal(total: Double?): Boolean = total != null && total.isFinite() && totalCeiling?.let { total > it } == true
}

object DriTargets {
    /** Usa la referencia explícita del plan; nunca infiere sexo de identidad de género u hormonas. */
    fun profileFor(settings: com.example.kpkn.data.models.Settings, plan: com.example.kpkn.data.models.NutritionPlan?): DriProfile? {
        val age = settings.userVitals.age ?: settings.age ?: return null
        if (age < 19 || settings.userVitals.pregnancyLactation != com.example.kpkn.data.models.PregnancyLactation.NONE) return null
        val sex = when (plan?.calculationSnapshot?.inputs?.get("sex")) {
            "MALE" -> DriSex.MALE
            "FEMALE" -> DriSex.FEMALE
            else -> return null
        }
        return DriProfile(age, sex)
    }

    const val CSV_SHA256 = "6fe6027cf95cd907cdbd4abccb3b4cc184fc46d67c825c54d55519ddac336182"
    private data class Row(val nutrient: NutrientKey, val sex: DriSex, val group: String, val target: DriTarget)
    fun lookup(nutrient: NutrientKey, sex: DriSex?, age: Int?, stage: DriStage = DriStage.ADULT): DriTarget? {
        if (sex == null || age == null || age < 19) return null
        val range = when { age <= 30 -> "19-30"; age <= 50 -> "31-50"; age <= 70 -> "51-70"; else -> "> 70" }
        val group = range + when (stage) { DriStage.ADULT -> ""; DriStage.PREGNANT -> " (embarazo)"; DriStage.LACTATING -> " (lactancia)" }
        return rows.firstOrNull { it.nutrient == nutrient && it.sex == sex && it.group == group }?.target
    }
    fun lookup(nutrient: NutrientKey, profile: DriProfile?): DriTarget? = profile?.let { lookup(nutrient, it.sex, it.age, it.stage) }
    private fun scope(key: NutrientKey): UpperLimitScope = when (key) {
        NutrientKey.CALCIUM, NutrientKey.IRON, NutrientKey.ZINC, NutrientKey.SELENIUM, NutrientKey.IODINE,
        NutrientKey.VITAMIN_C, NutrientKey.VITAMIN_D, NutrientKey.VITAMIN_B6 -> UpperLimitScope.TOTAL
        NutrientKey.MAGNESIUM, NutrientKey.FOLATE, NutrientKey.NIACIN, NutrientKey.VITAMIN_E -> UpperLimitScope.SUPPLEMENTS_ONLY
        NutrientKey.VITAMIN_A -> UpperLimitScope.PREFORMED_ONLY
        else -> UpperLimitScope.NONE
    }
    private val sources = mapOf(
        NutrientKey.FIBER to "https://nap.nationalacademies.org/catalog/10490/dietary-reference-intakes-for-energy-carbohydrate-fiber-fat-fatty-acids-cholesterol-protein-and-amino-acids",
        NutrientKey.SODIUM to "https://nap.nationalacademies.org/catalog/25353/dietary-reference-intakes-for-sodium-and-potassium",
        NutrientKey.POTASSIUM to "https://ods.od.nih.gov/factsheets/Potassium-HealthProfessional/",
        NutrientKey.CALCIUM to "https://ods.od.nih.gov/factsheets/Calcium-HealthProfessional/",
        NutrientKey.IRON to "https://ods.od.nih.gov/factsheets/Iron-HealthProfessional/",
        NutrientKey.MAGNESIUM to "https://ods.od.nih.gov/factsheets/Magnesium-HealthProfessional/",
        NutrientKey.ZINC to "https://ods.od.nih.gov/factsheets/Zinc-HealthProfessional/",
        NutrientKey.SELENIUM to "https://ods.od.nih.gov/factsheets/Selenium-HealthProfessional/",
        NutrientKey.IODINE to "https://ods.od.nih.gov/factsheets/Iodine-HealthProfessional/",
        NutrientKey.VITAMIN_A to "https://ods.od.nih.gov/factsheets/VitaminA-HealthProfessional/",
        NutrientKey.VITAMIN_C to "https://ods.od.nih.gov/factsheets/VitaminC-HealthProfessional/",
        NutrientKey.VITAMIN_D to "https://ods.od.nih.gov/factsheets/VitaminD-HealthProfessional/",
        NutrientKey.VITAMIN_E to "https://ods.od.nih.gov/factsheets/VitaminE-HealthProfessional/",
        NutrientKey.VITAMIN_K to "https://ods.od.nih.gov/factsheets/VitaminK-HealthProfessional/",
        NutrientKey.VITAMIN_B6 to "https://ods.od.nih.gov/factsheets/VitaminB6-HealthProfessional/",
        NutrientKey.VITAMIN_B12 to "https://ods.od.nih.gov/factsheets/VitaminB12-HealthProfessional/",
        NutrientKey.FOLATE to "https://ods.od.nih.gov/factsheets/Folate-HealthProfessional/",
        NutrientKey.THIAMIN to "https://ods.od.nih.gov/factsheets/Thiamin-HealthProfessional/",
        NutrientKey.RIBOFLAVIN to "https://ods.od.nih.gov/factsheets/Riboflavin-HealthProfessional/",
        NutrientKey.NIACIN to "https://ods.od.nih.gov/factsheets/Niacin-HealthProfessional/",
    )
    private val rows: List<Row> by lazy {
        table.trimIndent().lineSequence().filter { it.isNotBlank() }.map { line ->
            val c = line.split('|')
            val key = NutrientKey.valueOf(c[0])
            val ul = c[5].takeUnless { it == "ND" }
            Row(key, DriSex.valueOf(c[1]), c[2], DriTarget(key, DriKind.valueOf(c[3]), c[4].toDouble(),
                ul?.substringBefore(' ')?.toDouble(), ul?.substringAfter(' '), scope(key), sources.getValue(key)))
        }.toList()
    }
    // Derivado mecánicamente del CSV aprobado, sin transcribir cifras de memoria.
    private val table = """
        FIBER|MALE|19-30|AI|38|ND
        FIBER|MALE|31-50|AI|38|ND
        FIBER|MALE|51-70|AI|30|ND
        FIBER|MALE|> 70|AI|30|ND
        FIBER|FEMALE|19-30|AI|25|ND
        FIBER|FEMALE|31-50|AI|25|ND
        FIBER|FEMALE|51-70|AI|21|ND
        FIBER|FEMALE|> 70|AI|21|ND
        FIBER|FEMALE|19-30 (embarazo)|AI|28|ND
        FIBER|FEMALE|31-50 (embarazo)|AI|28|ND
        FIBER|FEMALE|19-30 (lactancia)|AI|29|ND
        FIBER|FEMALE|31-50 (lactancia)|AI|29|ND
        SODIUM|MALE|19-30|AI|1500|ND
        SODIUM|MALE|31-50|AI|1500|ND
        SODIUM|MALE|51-70|AI|1500|ND
        SODIUM|MALE|> 70|AI|1500|ND
        SODIUM|FEMALE|19-30|AI|1500|ND
        SODIUM|FEMALE|31-50|AI|1500|ND
        SODIUM|FEMALE|51-70|AI|1500|ND
        SODIUM|FEMALE|> 70|AI|1500|ND
        SODIUM|FEMALE|19-30 (embarazo)|AI|1500|ND
        SODIUM|FEMALE|31-50 (embarazo)|AI|1500|ND
        SODIUM|FEMALE|19-30 (lactancia)|AI|1500|ND
        SODIUM|FEMALE|31-50 (lactancia)|AI|1500|ND
        POTASSIUM|MALE|19-30|AI|3400|ND
        POTASSIUM|MALE|31-50|AI|3400|ND
        POTASSIUM|MALE|51-70|AI|3400|ND
        POTASSIUM|MALE|> 70|AI|3400|ND
        POTASSIUM|FEMALE|19-30|AI|2600|ND
        POTASSIUM|FEMALE|31-50|AI|2600|ND
        POTASSIUM|FEMALE|51-70|AI|2600|ND
        POTASSIUM|FEMALE|> 70|AI|2600|ND
        POTASSIUM|FEMALE|19-30 (embarazo)|AI|2900|ND
        POTASSIUM|FEMALE|31-50 (embarazo)|AI|2900|ND
        POTASSIUM|FEMALE|19-30 (lactancia)|AI|2800|ND
        POTASSIUM|FEMALE|31-50 (lactancia)|AI|2800|ND
        CALCIUM|MALE|19-30|RDA|1000|2500 mg
        CALCIUM|MALE|31-50|RDA|1000|2500 mg
        CALCIUM|MALE|51-70|RDA|1000|2000 mg
        CALCIUM|MALE|> 70|RDA|1200|2000 mg
        CALCIUM|FEMALE|19-30|RDA|1000|2500 mg
        CALCIUM|FEMALE|31-50|RDA|1000|2500 mg
        CALCIUM|FEMALE|51-70|RDA|1200|2000 mg
        CALCIUM|FEMALE|> 70|RDA|1200|2000 mg
        CALCIUM|FEMALE|19-30 (embarazo)|RDA|1000|2500 mg
        CALCIUM|FEMALE|31-50 (embarazo)|RDA|1000|2500 mg
        CALCIUM|FEMALE|19-30 (lactancia)|RDA|1000|2500 mg
        CALCIUM|FEMALE|31-50 (lactancia)|RDA|1000|2500 mg
        IRON|MALE|19-30|RDA|8|45 mg
        IRON|MALE|31-50|RDA|8|45 mg
        IRON|MALE|51-70|RDA|8|45 mg
        IRON|MALE|> 70|RDA|8|45 mg
        IRON|FEMALE|19-30|RDA|18|45 mg
        IRON|FEMALE|31-50|RDA|18|45 mg
        IRON|FEMALE|51-70|RDA|8|45 mg
        IRON|FEMALE|> 70|RDA|8|45 mg
        IRON|FEMALE|19-30 (embarazo)|RDA|27|45 mg
        IRON|FEMALE|31-50 (embarazo)|RDA|27|45 mg
        IRON|FEMALE|19-30 (lactancia)|RDA|9|45 mg
        IRON|FEMALE|31-50 (lactancia)|RDA|9|45 mg
        MAGNESIUM|MALE|19-30|RDA|400|350 mg
        MAGNESIUM|MALE|31-50|RDA|420|350 mg
        MAGNESIUM|MALE|51-70|RDA|420|350 mg
        MAGNESIUM|MALE|> 70|RDA|420|350 mg
        MAGNESIUM|FEMALE|19-30|RDA|310|350 mg
        MAGNESIUM|FEMALE|31-50|RDA|320|350 mg
        MAGNESIUM|FEMALE|51-70|RDA|320|350 mg
        MAGNESIUM|FEMALE|> 70|RDA|320|350 mg
        MAGNESIUM|FEMALE|19-30 (embarazo)|RDA|350|350 mg
        MAGNESIUM|FEMALE|31-50 (embarazo)|RDA|360|350 mg
        MAGNESIUM|FEMALE|19-30 (lactancia)|RDA|310|350 mg
        MAGNESIUM|FEMALE|31-50 (lactancia)|RDA|320|350 mg
        ZINC|MALE|19-30|RDA|11|40 mg
        ZINC|MALE|31-50|RDA|11|40 mg
        ZINC|MALE|51-70|RDA|11|40 mg
        ZINC|MALE|> 70|RDA|11|40 mg
        ZINC|FEMALE|19-30|RDA|8|40 mg
        ZINC|FEMALE|31-50|RDA|8|40 mg
        ZINC|FEMALE|51-70|RDA|8|40 mg
        ZINC|FEMALE|> 70|RDA|8|40 mg
        ZINC|FEMALE|19-30 (embarazo)|RDA|11|40 mg
        ZINC|FEMALE|31-50 (embarazo)|RDA|11|40 mg
        ZINC|FEMALE|19-30 (lactancia)|RDA|12|40 mg
        ZINC|FEMALE|31-50 (lactancia)|RDA|12|40 mg
        SELENIUM|MALE|19-30|RDA|55|400 µg
        SELENIUM|MALE|31-50|RDA|55|400 µg
        SELENIUM|MALE|51-70|RDA|55|400 µg
        SELENIUM|MALE|> 70|RDA|55|400 µg
        SELENIUM|FEMALE|19-30|RDA|55|400 µg
        SELENIUM|FEMALE|31-50|RDA|55|400 µg
        SELENIUM|FEMALE|51-70|RDA|55|400 µg
        SELENIUM|FEMALE|> 70|RDA|55|400 µg
        SELENIUM|FEMALE|19-30 (embarazo)|RDA|60|400 µg
        SELENIUM|FEMALE|31-50 (embarazo)|RDA|60|400 µg
        SELENIUM|FEMALE|19-30 (lactancia)|RDA|70|400 µg
        SELENIUM|FEMALE|31-50 (lactancia)|RDA|70|400 µg
        IODINE|MALE|19-30|RDA|150|1100 µg
        IODINE|MALE|31-50|RDA|150|1100 µg
        IODINE|MALE|51-70|RDA|150|1100 µg
        IODINE|MALE|> 70|RDA|150|1100 µg
        IODINE|FEMALE|19-30|RDA|150|1100 µg
        IODINE|FEMALE|31-50|RDA|150|1100 µg
        IODINE|FEMALE|51-70|RDA|150|1100 µg
        IODINE|FEMALE|> 70|RDA|150|1100 µg
        IODINE|FEMALE|19-30 (embarazo)|RDA|220|1100 µg
        IODINE|FEMALE|31-50 (embarazo)|RDA|220|1100 µg
        IODINE|FEMALE|19-30 (lactancia)|RDA|290|1100 µg
        IODINE|FEMALE|31-50 (lactancia)|RDA|290|1100 µg
        VITAMIN_A|MALE|19-30|RDA|900|3000 µg
        VITAMIN_A|MALE|31-50|RDA|900|3000 µg
        VITAMIN_A|MALE|51-70|RDA|900|3000 µg
        VITAMIN_A|MALE|> 70|RDA|900|3000 µg
        VITAMIN_A|FEMALE|19-30|RDA|700|3000 µg
        VITAMIN_A|FEMALE|31-50|RDA|700|3000 µg
        VITAMIN_A|FEMALE|51-70|RDA|700|3000 µg
        VITAMIN_A|FEMALE|> 70|RDA|700|3000 µg
        VITAMIN_A|FEMALE|19-30 (embarazo)|RDA|770|3000 µg
        VITAMIN_A|FEMALE|31-50 (embarazo)|RDA|770|3000 µg
        VITAMIN_A|FEMALE|19-30 (lactancia)|RDA|1300|3000 µg
        VITAMIN_A|FEMALE|31-50 (lactancia)|RDA|1300|3000 µg
        VITAMIN_C|MALE|19-30|RDA|90|2000 mg
        VITAMIN_C|MALE|31-50|RDA|90|2000 mg
        VITAMIN_C|MALE|51-70|RDA|90|2000 mg
        VITAMIN_C|MALE|> 70|RDA|90|2000 mg
        VITAMIN_C|FEMALE|19-30|RDA|75|2000 mg
        VITAMIN_C|FEMALE|31-50|RDA|75|2000 mg
        VITAMIN_C|FEMALE|51-70|RDA|75|2000 mg
        VITAMIN_C|FEMALE|> 70|RDA|75|2000 mg
        VITAMIN_C|FEMALE|19-30 (embarazo)|RDA|85|2000 mg
        VITAMIN_C|FEMALE|31-50 (embarazo)|RDA|85|2000 mg
        VITAMIN_C|FEMALE|19-30 (lactancia)|RDA|120|2000 mg
        VITAMIN_C|FEMALE|31-50 (lactancia)|RDA|120|2000 mg
        VITAMIN_D|MALE|19-30|RDA|15|100 µg
        VITAMIN_D|MALE|31-50|RDA|15|100 µg
        VITAMIN_D|MALE|51-70|RDA|15|100 µg
        VITAMIN_D|MALE|> 70|RDA|20|100 µg
        VITAMIN_D|FEMALE|19-30|RDA|15|100 µg
        VITAMIN_D|FEMALE|31-50|RDA|15|100 µg
        VITAMIN_D|FEMALE|51-70|RDA|15|100 µg
        VITAMIN_D|FEMALE|> 70|RDA|20|100 µg
        VITAMIN_D|FEMALE|19-30 (embarazo)|RDA|15|100 µg
        VITAMIN_D|FEMALE|31-50 (embarazo)|RDA|15|100 µg
        VITAMIN_D|FEMALE|19-30 (lactancia)|RDA|15|100 µg
        VITAMIN_D|FEMALE|31-50 (lactancia)|RDA|15|100 µg
        VITAMIN_E|MALE|19-30|RDA|15|1000 mg
        VITAMIN_E|MALE|31-50|RDA|15|1000 mg
        VITAMIN_E|MALE|51-70|RDA|15|1000 mg
        VITAMIN_E|MALE|> 70|RDA|15|1000 mg
        VITAMIN_E|FEMALE|19-30|RDA|15|1000 mg
        VITAMIN_E|FEMALE|31-50|RDA|15|1000 mg
        VITAMIN_E|FEMALE|51-70|RDA|15|1000 mg
        VITAMIN_E|FEMALE|> 70|RDA|15|1000 mg
        VITAMIN_E|FEMALE|19-30 (embarazo)|RDA|15|1000 mg
        VITAMIN_E|FEMALE|31-50 (embarazo)|RDA|15|1000 mg
        VITAMIN_E|FEMALE|19-30 (lactancia)|RDA|19|1000 mg
        VITAMIN_E|FEMALE|31-50 (lactancia)|RDA|19|1000 mg
        VITAMIN_K|MALE|19-30|AI|120|ND
        VITAMIN_K|MALE|31-50|AI|120|ND
        VITAMIN_K|MALE|51-70|AI|120|ND
        VITAMIN_K|MALE|> 70|AI|120|ND
        VITAMIN_K|FEMALE|19-30|AI|90|ND
        VITAMIN_K|FEMALE|31-50|AI|90|ND
        VITAMIN_K|FEMALE|51-70|AI|90|ND
        VITAMIN_K|FEMALE|> 70|AI|90|ND
        VITAMIN_K|FEMALE|19-30 (embarazo)|AI|90|ND
        VITAMIN_K|FEMALE|31-50 (embarazo)|AI|90|ND
        VITAMIN_K|FEMALE|19-30 (lactancia)|AI|90|ND
        VITAMIN_K|FEMALE|31-50 (lactancia)|AI|90|ND
        VITAMIN_B6|MALE|19-30|RDA|1.3|100 mg
        VITAMIN_B6|MALE|31-50|RDA|1.3|100 mg
        VITAMIN_B6|MALE|51-70|RDA|1.7|100 mg
        VITAMIN_B6|MALE|> 70|RDA|1.7|100 mg
        VITAMIN_B6|FEMALE|19-30|RDA|1.3|100 mg
        VITAMIN_B6|FEMALE|31-50|RDA|1.3|100 mg
        VITAMIN_B6|FEMALE|51-70|RDA|1.5|100 mg
        VITAMIN_B6|FEMALE|> 70|RDA|1.5|100 mg
        VITAMIN_B6|FEMALE|19-30 (embarazo)|RDA|1.9|100 mg
        VITAMIN_B6|FEMALE|31-50 (embarazo)|RDA|1.9|100 mg
        VITAMIN_B6|FEMALE|19-30 (lactancia)|RDA|2.0|100 mg
        VITAMIN_B6|FEMALE|31-50 (lactancia)|RDA|2.0|100 mg
        VITAMIN_B12|MALE|19-30|RDA|2.4|ND
        VITAMIN_B12|MALE|31-50|RDA|2.4|ND
        VITAMIN_B12|MALE|51-70|RDA|2.4|ND
        VITAMIN_B12|MALE|> 70|RDA|2.4|ND
        VITAMIN_B12|FEMALE|19-30|RDA|2.4|ND
        VITAMIN_B12|FEMALE|31-50|RDA|2.4|ND
        VITAMIN_B12|FEMALE|51-70|RDA|2.4|ND
        VITAMIN_B12|FEMALE|> 70|RDA|2.4|ND
        VITAMIN_B12|FEMALE|19-30 (embarazo)|RDA|2.6|ND
        VITAMIN_B12|FEMALE|31-50 (embarazo)|RDA|2.6|ND
        VITAMIN_B12|FEMALE|19-30 (lactancia)|RDA|2.8|ND
        VITAMIN_B12|FEMALE|31-50 (lactancia)|RDA|2.8|ND
        FOLATE|MALE|19-30|RDA|400|1000 µg
        FOLATE|MALE|31-50|RDA|400|1000 µg
        FOLATE|MALE|51-70|RDA|400|1000 µg
        FOLATE|MALE|> 70|RDA|400|1000 µg
        FOLATE|FEMALE|19-30|RDA|400|1000 µg
        FOLATE|FEMALE|31-50|RDA|400|1000 µg
        FOLATE|FEMALE|51-70|RDA|400|1000 µg
        FOLATE|FEMALE|> 70|RDA|400|1000 µg
        FOLATE|FEMALE|19-30 (embarazo)|RDA|600|1000 µg
        FOLATE|FEMALE|31-50 (embarazo)|RDA|600|1000 µg
        FOLATE|FEMALE|19-30 (lactancia)|RDA|500|1000 µg
        FOLATE|FEMALE|31-50 (lactancia)|RDA|500|1000 µg
        THIAMIN|MALE|19-30|RDA|1.2|ND
        THIAMIN|MALE|31-50|RDA|1.2|ND
        THIAMIN|MALE|51-70|RDA|1.2|ND
        THIAMIN|MALE|> 70|RDA|1.2|ND
        THIAMIN|FEMALE|19-30|RDA|1.1|ND
        THIAMIN|FEMALE|31-50|RDA|1.1|ND
        THIAMIN|FEMALE|51-70|RDA|1.1|ND
        THIAMIN|FEMALE|> 70|RDA|1.1|ND
        THIAMIN|FEMALE|19-30 (embarazo)|RDA|1.4|ND
        THIAMIN|FEMALE|31-50 (embarazo)|RDA|1.4|ND
        THIAMIN|FEMALE|19-30 (lactancia)|RDA|1.4|ND
        THIAMIN|FEMALE|31-50 (lactancia)|RDA|1.4|ND
        RIBOFLAVIN|MALE|19-30|RDA|1.3|ND
        RIBOFLAVIN|MALE|31-50|RDA|1.3|ND
        RIBOFLAVIN|MALE|51-70|RDA|1.3|ND
        RIBOFLAVIN|MALE|> 70|RDA|1.3|ND
        RIBOFLAVIN|FEMALE|19-30|RDA|1.1|ND
        RIBOFLAVIN|FEMALE|31-50|RDA|1.1|ND
        RIBOFLAVIN|FEMALE|51-70|RDA|1.1|ND
        RIBOFLAVIN|FEMALE|> 70|RDA|1.1|ND
        RIBOFLAVIN|FEMALE|19-30 (embarazo)|RDA|1.4|ND
        RIBOFLAVIN|FEMALE|31-50 (embarazo)|RDA|1.4|ND
        RIBOFLAVIN|FEMALE|19-30 (lactancia)|RDA|1.6|ND
        RIBOFLAVIN|FEMALE|31-50 (lactancia)|RDA|1.6|ND
        NIACIN|MALE|19-30|RDA|16|35 mg
        NIACIN|MALE|31-50|RDA|16|35 mg
        NIACIN|MALE|51-70|RDA|16|35 mg
        NIACIN|MALE|> 70|RDA|16|35 mg
        NIACIN|FEMALE|19-30|RDA|14|35 mg
        NIACIN|FEMALE|31-50|RDA|14|35 mg
        NIACIN|FEMALE|51-70|RDA|14|35 mg
        NIACIN|FEMALE|> 70|RDA|14|35 mg
        NIACIN|FEMALE|19-30 (embarazo)|RDA|18|35 mg
        NIACIN|FEMALE|31-50 (embarazo)|RDA|18|35 mg
        NIACIN|FEMALE|19-30 (lactancia)|RDA|17|35 mg
        NIACIN|FEMALE|31-50 (lactancia)|RDA|17|35 mg
    """
}
