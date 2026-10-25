package com.example.kpkn.data.db

import com.example.kpkn.data.models.CarbBreakdown
import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.data.models.Micronutrient
import kotlinx.serialization.json.Json
import java.text.Normalizer

/*
 * STUBS DEL ARNÉS JVM para data/db. Entities.kt y Daos.kt NO entran en el arnés (llevan anotaciones de Room y el resto
 * de su contenido es Android). Aquí solo está lo que el código puro usa:
 *  - GlobalFoodEntity y LearnedResolutionEntity: mismos campos y tipos que en Entities.kt, sin anotaciones.
 *  - GlobalFoodEntity.toFoodItem(): mismo cuerpo que en Entities.kt (mapeo puro), con copias locales de sus helpers.
 *  - NutritionDao y LearnedResolutionDao: interfaces sin persistencia; los métodos vacíos devuelven nada.
 */

data class GlobalFoodEntity(
    val foodId: String,
    val name: String,
    val brand: String? = null,
    val normalizedName: String = "",
    val normalizedBrand: String? = null,
    val aliasesJson: String = "[]",
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fats: Double = 0.0,
    val fiber: Double = 0.0,
    val sugar: Double = 0.0,
    val sodiumMg: Double = 0.0,
    val potassiumMg: Double = 0.0,
    val waterMl: Double = 0.0,
    val caffeineMg: Double = 0.0,
    val creatineG: Double = 0.0,
    val source: String = "unknown",
    val sourcePriority: Int = 50,
    val verifiedScore: Double = 0.5,
    val usageCount: Int = 0,
    val lastUsedAt: String? = null,
    val sourceRecordId: String? = null,
    val foodState: String = "UNKNOWN",
    val nutritionBasis: String = "PER_100G_AS_SOLD",
    val datasetVersion: String = "",
    val category: String? = null,
    val portionGrams: Double? = null,
    val portionUnit: String? = null,
    val qualityFlagsJson: String = "[]",
)

fun GlobalFoodEntity.toFoodItem(): FoodItem = FoodItem(
    id = foodId,
    name = name,
    brand = brand,
    normalizedName = if (normalizedName.isBlank()) harnessNormalizeSearch(name) else normalizedName,
    normalizedBrand = normalizedBrand,
    category = category,
    servingSize = if (nutritionBasis == "PER_SERVING") portionGrams?.takeIf { it.isFinite() && it > 0.0 } ?: 100.0 else 100.0,
    calories = calories,
    protein = protein,
    carbs = carbs,
    fats = fats,
    carbBreakdown = CarbBreakdown(fiber = fiber, sugar = sugar),
    micronutrients = buildList {
        if (sodiumMg > 0.0) add(Micronutrient(name = "Sodio", amount = sodiumMg, unit = "mg"))
        if (potassiumMg > 0.0) add(Micronutrient(name = "Potasio", amount = potassiumMg, unit = "mg"))
        if (waterMl > 0.0) add(Micronutrient(name = "Agua", amount = waterMl, unit = "ml"))
    },
    tags = listOf(source),
    searchAliases = harnessDecodeAliases(aliasesJson),
    sourcePriority = sourcePriority,
    verifiedScore = verifiedScore,
    usageCount = usageCount,
    lastUsedAt = lastUsedAt,
    source = source,
    sourceRecordId = sourceRecordId,
    nutritionBasis = nutritionBasis,
    foodState = foodState,
    datasetVersion = datasetVersion,
    portionGrams = portionGrams,
    portionUnit = portionUnit,
    qualityFlags = harnessDecodeStringList(qualityFlagsJson),
    caffeineMg = caffeineMg,
    creatineG = creatineG,
)

data class LearnedResolutionEntity(
    val id: String,
    val queryKey: String,
    val foodId: String,
    val portionGrams: Double?,
    val cookingMethod: String?,
    val count: Int = 1,
    val lastUsedAt: Long = 0L,
    val createdAt: Long = 0L,
    val syncedAt: Long? = null,
    val weightBasis: String? = null,
    val portionMinGrams: Double? = null,
    val portionMaxGrams: Double? = null,
    val preparation: String? = null,
    val oilProfile: String? = null,
    val confidence: Double = 1.0,
    val lastConfirmedAt: Long = 0L,
)

/** Sin base de datos en el arnés: el dominio no consulta filas aquí. */
interface NutritionDao

/** Sin base de datos en el arnés: lecturas vacías y escrituras sin efecto. */
interface LearnedResolutionDao {
    fun topFor(queryKey: String, limit: Int): List<LearnedResolutionEntity> = emptyList()

    fun upsert(entity: LearnedResolutionEntity) {}

    fun prune(keep: Int) {}
}

private val harnessDbJson = Json { ignoreUnknownKeys = true }

// Copia de normalizeSearch (Entities.kt): NFD, quitar marcas, minúsculas y espacios colapsados.
private fun harnessNormalizeSearch(value: String?): String {
    if (value.isNullOrBlank()) return ""
    val stripped = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
    return stripped
        .lowercase()
        .replace(Regex("[^\\p{L}\\p{Nd}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

// Copia de decodeAliases (Entities.kt).
private fun harnessDecodeAliases(aliasesJson: String): List<String> = runCatching {
    harnessDbJson.decodeFromString<List<String>>(aliasesJson)
}.getOrDefault(emptyList())

// Copia de decodeStringList (Entities.kt).
private fun harnessDecodeStringList(json: String): List<String> =
    json.trim().removePrefix("[").removeSuffix("]")
        .split(',')
        .map { it.trim().removeSurrounding("\"") }
        .filter { it.isNotBlank() }
