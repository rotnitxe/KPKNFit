package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.AnalysisSource
import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.domain.nutrition.nutrients.NutrientId
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
)

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
        val visible = if (contributions.size <= 5) contributions else contributions.take(4) + NutrientContribution(
            "Otros alimentos", contributions.drop(4).sumOf { it.amount }, contributions.drop(4).any { it.estimated }, contributions.size - 4)
        return NutrientDayTotal(nutrient, total, declared, estimated, foods.size - declared - estimated, fraction, maxEnergy > 0.0,
            when { total == null -> NutrientCoverageState.NO_DATA; fraction >= 0.9 - 1e-12 -> NutrientCoverageState.COMPLETE; else -> NutrientCoverageState.PARTIAL }, visible)
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
        val amount = if (explicit != null) {
            convert(explicit.amount, explicit.unit, key) ?: return missing
        } else when (key) {
            NutrientKey.FIBER -> food.fiber
            NutrientKey.SODIUM -> food.sodiumMg
            NutrientKey.POTASSIUM -> food.potassiumMg
            NutrientKey.SUGARS -> food.sugar
            NutrientKey.SAT_FAT -> food.fatBreakdown?.saturated ?: 0.0
            NutrientKey.MONO_FAT -> food.fatBreakdown?.monounsaturated ?: 0.0
            NutrientKey.POLY_FAT -> food.fatBreakdown?.polyunsaturated ?: 0.0
            NutrientKey.TRANS_FAT -> food.fatBreakdown?.trans ?: 0.0
            NutrientKey.CAFFEINE -> food.caffeineMg
            else -> 0.0
        }.takeIf { it.isFinite() && it > 0.0 } ?: return missing
        return Datum(amount, kind)
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
