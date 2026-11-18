package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.AnalysisSource
import com.example.kpkn.data.models.FatSubtype
import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.domain.nutrition.nutrients.NutrientId
import com.example.kpkn.domain.nutrition.nutrients.NutritionCatalogAccess
import com.example.kpkn.domain.nutrition.nutrients.NutritionCatalogPort
import com.example.kpkn.domain.nutrition.nutrients.hasNutrientSnapshot
import com.example.kpkn.domain.nutrition.nutrients.nutrientAmount
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

enum class NutrientDataKind { DECLARED, ESTIMATED, MISSING }
enum class NutrientCoverageState { COMPLETE, PARTIAL, NO_DATA }
data class NutrientContribution(val name: String, val amount: Double, val estimated: Boolean, val groupedFoods: Int = 1)
data class NutrientDayTotal(
    val nutrient: NutrientKey, val totalKnown: Double?, val declaredCount: Int, val estimatedCount: Int,
    val missingCount: Int, val declaredFraction: Double, val weightedByEnergy: Boolean,
    val state: NutrientCoverageState, val contributors: List<NutrientContribution>,
    /** Parte del total que viene de alimentos estimados (se dibuja rayada o en contorno discontinuo). */
    val estimatedAmount: Double = 0.0,
    /** Nombres de los alimentos que no traen dato de este nutriente (no cuentan como 0). */
    val missingFoods: List<String> = emptyList(),
)

/** Cobertura de un conjunto de alimentos: qué parte de las kcal tiene dato declarado y cuántos alimentos no traen ficha. */
data class NutrientFoodCoverage(
    val declaredFraction: Double, val weightedByEnergy: Boolean,
    /** Un valor por alimento, en el orden recibido: DECLARED = ficha completa, ESTIMATED = estimado, MISSING = sin ficha. */
    val kinds: List<NutrientDataKind>,
) {
    val foods: Int get() = kinds.size
    val declaredFoods: Int get() = kinds.count { it == NutrientDataKind.DECLARED }
    val estimatedFoods: Int get() = kinds.count { it == NutrientDataKind.ESTIMATED }
    val foodsWithoutData: Int get() = kinds.count { it == NutrientDataKind.MISSING }
    companion object { val EMPTY = NutrientFoodCoverage(0.0, false, emptyList()) }
}

/** Solo suma lo declarado o estimado: una entrada rápida y una ausencia nunca son ceros. */
object NutrientCoverage {
    private data class Datum(val amount: Double?, val kind: NutrientDataKind)
    fun summarize(foods: List<LoggedFood>): List<NutrientDayTotal> = NutrientKey.entries.map { summarize(foods, it) }
    fun summarize(foods: List<LoggedFood>, nutrient: NutrientKey): NutrientDayTotal {
        val data = foods.map { read(it, nutrient) }
        val energies = foods.map { it.calories.takeIf { kcal -> kcal.isFinite() && kcal > 0.0 } ?: 0.0 }
        val maxEnergy = energies.maxOrNull() ?: 0.0
        val weights = if (maxEnergy > 0.0) energies.map { it / maxEnergy } else foods.map { 1.0 }
        val weightTotal = weights.sum()
        val declared = data.count { it.kind == NutrientDataKind.DECLARED }
        val estimated = data.count { it.kind == NutrientDataKind.ESTIMATED }
        val fraction = if (weightTotal > 0.0) data.indices.filter { data[it].kind == NutrientDataKind.DECLARED }.sumOf { weights[it] } / weightTotal else 0.0
        val contributions = foods.indices.mapNotNull { index -> data[index].amount?.let { amount ->
            NutrientContribution(foods[index].foodName.ifBlank { "Alimento sin nombre" }, amount, data[index].kind == NutrientDataKind.ESTIMATED)
        } }.sortedByDescending { it.amount }
        val total = contributions.takeIf { it.isNotEmpty() }?.sumOf { it.amount }?.takeIf { it.isFinite() }
        val estimatedAmount = contributions.filter { it.estimated }.sumOf { it.amount }.takeIf { it.isFinite() } ?: 0.0
        val missingFoods = foods.indices.filter { data[it].kind == NutrientDataKind.MISSING }.map { foods[it].foodName.ifBlank { "Alimento sin nombre" } }
        val visible = if (contributions.size <= 5) contributions else contributions.take(4) + NutrientContribution(
            "Otros alimentos", contributions.drop(4).sumOf { it.amount }, contributions.drop(4).any { it.estimated }, contributions.size - 4)
        return NutrientDayTotal(nutrient, total, declared, estimated, foods.size - declared - estimated, fraction, maxEnergy > 0.0,
            when { total == null -> NutrientCoverageState.NO_DATA; fraction >= 0.9 - 1e-12 -> NutrientCoverageState.COMPLETE; else -> NutrientCoverageState.PARTIAL }, visible,
            estimatedAmount, missingFoods)
    }

    /**
     * Cobertura por alimento: un alimento sin ningún dato de nutrientes (entrada rápida, sin ficha) es MISSING; con algún dato estimado,
     * ESTIMATED; si todo lo que trae es declarado, DECLARED. La fracción declarada se pondera por kcal (o por alimento si no hay kcal).
     */
    fun foodCoverage(foods: List<LoggedFood>): NutrientFoodCoverage {
        if (foods.isEmpty()) return NutrientFoodCoverage.EMPTY
        val kinds = foods.map { food ->
            val read = NutrientKey.entries.map { read(food, it).kind }.filter { it != NutrientDataKind.MISSING }
            when {
                read.isEmpty() -> NutrientDataKind.MISSING
                read.any { it == NutrientDataKind.ESTIMATED } -> NutrientDataKind.ESTIMATED
                else -> NutrientDataKind.DECLARED
            }
        }
        val energies = foods.map { it.calories.takeIf { kcal -> kcal.isFinite() && kcal > 0.0 } ?: 0.0 }
        val byEnergy = energies.sum() > 0.0
        val weights = if (byEnergy) energies else foods.map { 1.0 }
        val fraction = kinds.indices.filter { kinds[it] == NutrientDataKind.DECLARED }.sumOf { weights[it] } / weights.sum()
        return NutrientFoodCoverage(fraction, byEnergy, kinds)
    }
    private fun read(food: LoggedFood, key: NutrientKey): Datum {
        val missing = Datum(null, NutrientDataKind.MISSING)
        if (food.isQuickEntry) return missing
        val weak = food.isUncertain || food.recipeId != null || food.analysisSource in setOf(
            AnalysisSource.LOCAL_AI_ESTIMATE, AnalysisSource.EXTERNAL_API_ESTIMATE, AnalysisSource.LOCAL_HEURISTIC) ||
            weakEvidence(food.evidenceJson)
        val kind = if (weak) NutrientDataKind.ESTIMATED else NutrientDataKind.DECLARED
        // Primero la instantánea del catálogo: un 0 declarado es dato; la ausencia de la clave no lo es.
        food.nutrientAmount(key.nutrientId)?.takeIf { it.isFinite() && it >= 0.0 }?.let {
            return Datum(it, if (energyMismatch(food)) NutrientDataKind.ESTIMATED else kind)
        }
        val explicit = food.micronutrients.firstOrNull { normalize(it.name) in aliases(key) }
        if (explicit != null) {
            val amount = convert(explicit.amount, explicit.unit, key) ?: return missing
            return Datum(amount, kind)
        }
        val explicitFat = when (key) {
            NutrientKey.SAT_FAT -> fatSubtypeDatum(food, FatSubtype.SATURATED, kind, missing)
            NutrientKey.MONO_FAT -> fatSubtypeDatum(food, FatSubtype.MONOUNSATURATED, kind, missing)
            NutrientKey.POLY_FAT -> fatSubtypeDatum(food, FatSubtype.POLYUNSATURATED, kind, missing)
            NutrientKey.TRANS_FAT -> fatSubtypeDatum(food, FatSubtype.TRANS, kind, missing)
            else -> missing
        }
        if (explicitFat.kind != NutrientDataKind.MISSING) return explicitFat
        legacyCatalogDatum(food, key, NutritionCatalogAccess.current)?.let { return it }
        val amount = when (key) {
            NutrientKey.FIBER -> food.fiber
            NutrientKey.SODIUM -> food.sodiumMg
            NutrientKey.POTASSIUM -> food.potassiumMg
            NutrientKey.SUGARS -> food.sugar
            NutrientKey.CAFFEINE -> food.caffeineMg
            else -> 0.0
        }.takeIf { it.isFinite() && it > 0.0 } ?: return missing
        return Datum(amount, kind)
    }

    private fun fatSubtypeDatum(
        food: LoggedFood,
        subtype: FatSubtype,
        kind: NutrientDataKind,
        missing: Datum,
    ): Datum {
        val breakdown = food.fatBreakdown ?: return missing
        val amount = when (subtype) {
            FatSubtype.SATURATED -> breakdown.saturated
            FatSubtype.MONOUNSATURATED -> breakdown.monounsaturated
            FatSubtype.POLYUNSATURATED -> breakdown.polyunsaturated
            FatSubtype.TRANS -> breakdown.trans
        }
        val hasDeclaredAmount = subtype in breakdown.knownSubtypes || subtype !in breakdown.missingSubtypes || amount > 0.0
        if (!hasDeclaredAmount || !amount.isFinite() || amount < 0.0) return missing
        return Datum(amount, if (subtype in breakdown.missingSubtypes) NutrientDataKind.ESTIMATED else kind)
    }

    /** Legacy rows may use their exact record ID, a compatible mass/volume base, and a matching known generation. */
    private fun legacyCatalogDatum(food: LoggedFood, key: NutrientKey, port: NutritionCatalogPort?): Datum? {
        if (food.catalogRecordId.isNullOrBlank() || port == null) return null
        if (food.hasNutrientSnapshot() && food.catalogGeneration == null) return null
        if (food.catalogGeneration != null && food.catalogGeneration != port.generation) return null
        val record = port.record(food.catalogRecordId) ?: return null
        if (food.preparation != null && !food.preparation.equals(record.preparation, ignoreCase = true)) return null
        val raw = port.nutrientsPer100(food.catalogRecordId)[key.nutrientId]
            ?.takeIf { it.isFinite() && it >= 0.0 } ?: return null
        if (!food.amount.isFinite() || food.amount <= 0.0 || !record.basisAmount.isFinite() || record.basisAmount <= 0.0) return null
        val foodUnit = food.unit.lowercase(Locale.ROOT)
        val basisUnit = record.basisUnit.lowercase(Locale.ROOT)
        val compatibleAmount = when {
            foodUnit == basisUnit -> food.amount
            foodUnit == "g" && basisUnit == "ml" -> {
                val density = record.densityGPerMl?.takeIf { it.isFinite() && it > 0.0 } ?: return null
                food.amount / density
            }
            foodUnit == "ml" && basisUnit == "g" -> {
                val density = record.densityGPerMl?.takeIf { it.isFinite() && it > 0.0 } ?: return null
                food.amount * density
            }
            else -> return null
        }
        val amount = raw * compatibleAmount / record.basisAmount
        if (!amount.isFinite()) return null
        val dataKind = when {
            food.catalogGeneration == null -> NutrientDataKind.ESTIMATED
            food.catalogGeneration == port.generation -> NutrientDataKind.DECLARED
            else -> return null
        }
        return Datum(amount, dataKind)
    }
    /** F4-9: si la energía de la instantánea difiere de las kcal del registro en más de 15 % Y más de 25 kcal, la ficha ya no describe lo comido. */
    private fun energyMismatch(food: LoggedFood): Boolean {
        val snapEnergy = food.nutrientAmount(NutrientId.ENERGY)?.takeIf { it.isFinite() } ?: return false
        val kcal = food.calories.takeIf { it.isFinite() && it > 0.0 } ?: return false
        val diff = abs(snapEnergy - kcal)
        return diff > 0.15 * kcal && diff > 25.0
    }
    private fun weakEvidence(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        if (raw.contains("INCOMPLETE", true) || raw.contains("LOW_QUALITY", true)) return true
        return runCatching {
            val evidence = Json.parseToJsonElement(raw).jsonObject
            evidence["sourceQuality"]?.jsonPrimitive?.contentOrNull in setOf("flagged", "unmatched_estimate", "composition_estimate") ||
                evidence["source"]?.jsonPrimitive?.contentOrNull in setOf("HEURISTIC", "DATASET_SEMANTIC")
        }.getOrDefault(false)
    }
    private fun normalize(text: String): String = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").filter { it.isLetterOrDigit() }
    private fun aliases(key: NutrientKey): Set<String> = when (key) {
        NutrientKey.FIBER -> setOf("fibra", "fibratotal", "fiber", "dietaryfiber")
        NutrientKey.SODIUM -> setOf("sodio", "sodium")
        NutrientKey.POTASSIUM -> setOf("potasio", "potassium")
        NutrientKey.CALCIUM -> setOf("calcio", "calcium")
        NutrientKey.IRON -> setOf("hierro", "iron")
        NutrientKey.MAGNESIUM -> setOf("magnesio", "magnesium")
        NutrientKey.ZINC -> setOf("zinc")
        NutrientKey.SELENIUM -> setOf("selenio", "selenium")
        NutrientKey.IODINE -> setOf("yodo", "iodine")
        NutrientKey.FOLATE -> setOf("folato", "folate", "folatodfe")
        NutrientKey.THIAMIN -> setOf("tiamina", "thiamin", "vitaminab1")
        NutrientKey.RIBOFLAVIN -> setOf("riboflavina", "riboflavin", "vitaminab2")
        NutrientKey.NIACIN -> setOf("niacina", "niacin", "vitaminab3")
        NutrientKey.SUGARS -> setOf("azucares", "azucarestotales", "sugars", "sugar", "totalsugars")
        NutrientKey.ADDED_SUGARS -> setOf("azucaresanadidos", "azucaresagregados", "addedsugars")
        NutrientKey.SAT_FAT -> setOf("grasasaturada", "grasassaturadas", "saturatedfat")
        NutrientKey.MONO_FAT -> setOf("grasamonoinsaturada", "grasasmonoinsaturadas", "monounsaturatedfat")
        NutrientKey.POLY_FAT -> setOf("grasapoliinsaturada", "grasaspoliinsaturadas", "polyunsaturatedfat")
        NutrientKey.TRANS_FAT -> setOf("grasatrans", "grasastrans", "transfat")
        NutrientKey.CHOLESTEROL -> setOf("colesterol", "cholesterol")
        NutrientKey.PHOSPHORUS -> setOf("fosforo", "phosphorus")
        NutrientKey.CHOLINE -> setOf("colina", "choline")
        NutrientKey.EPA_DHA -> setOf("epadha", "epamasdha", "omega3epadha")
        NutrientKey.ALA -> setOf("ala", "acidoalfalinolenico", "alphalinolenicacid", "omega3ala")
        NutrientKey.CAFFEINE -> setOf("cafeina", "caffeine")
        NutrientKey.ALCOHOL -> setOf("alcohol", "etanol", "ethanol")
        else -> setOf(normalize(key.label), normalize(key.label.replace("Vitamina", "Vitamin")))
    }
    private fun convert(value: Double, sourceUnit: String, key: NutrientKey): Double? {
        if (!value.isFinite() || value < 0.0) return null
        val unit = sourceUnit.lowercase(Locale.ROOT).replace('μ', 'u').replace('µ', 'u').replace("mcg", "ug").replace(" ", "")
        // Las formas vitamínicas no se convierten de IU ni se presumen RAE/DFE/NE.
        if (key == NutrientKey.VITAMIN_A && !unit.contains("rae")) return null
        if (key == NutrientKey.FOLATE && !unit.contains("dfe")) return null
        if (key == NutrientKey.NIACIN && !unit.contains("ne")) return null
        if (key == NutrientKey.VITAMIN_E && !(unit.contains("α") || unit.contains("alfa") || unit.contains("alpha"))) return null
        fun factor(text: String): Double? = when { text.startsWith("ug") -> 0.001; text.startsWith("mg") -> 1.0; text.startsWith("g") -> 1000.0; else -> null }
        val source = factor(unit) ?: return null
        val target = factor(key.unit.lowercase(Locale.ROOT).replace('µ', 'u').replace('μ', 'u')) ?: return null
        return (value * source / target).takeIf { it.isFinite() }
    }
}
