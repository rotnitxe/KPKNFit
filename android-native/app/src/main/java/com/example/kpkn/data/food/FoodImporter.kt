package com.example.kpkn.data.food

import android.content.Context
import com.example.kpkn.data.db.GlobalFoodEntity
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.NutritionDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.text.Normalizer
import java.time.Instant

/**
 * FoodImporter — Soporta USDA y OpenFoodFacts Chile.
 */
object FoodImporter {
    private const val TAG = "FoodImporter"
    private const val BATCH_SIZE = 2000
    private const val DATA_VERSION = 7
    private const val USDA_FOOD_CSV = "food_data/food.csv"
    private const val USDA_NUTRIENT_CSV = "food_data/food_nutrient.csv"
    private const val OFF_CHILE_CSV = "food_data/off_chile.csv"

    data class ImportMetadata(
        val version: Int,
        val checksum: String,
        val importedAt: String,
    )

    private val _importProgress = MutableStateFlow<Float?>(null)
    val importProgress: StateFlow<Float?> = _importProgress.asStateFlow()

    suspend fun importIfEmpty(alreadyImported: Boolean, context: Context) = withContext(Dispatchers.IO) {
        importIfNeeded(
            db = KpknDatabase.getInstance(context),
            context = context,
            alreadyImported = alreadyImported,
            existingMeta = null,
            onMetaUpdated = {},
        )
    }

    suspend fun importIfNeeded(
        db: KpknDatabase,
        context: Context,
        alreadyImported: Boolean,
        existingMeta: ImportMetadata?,
        onMetaUpdated: (ImportMetadata) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        val checksum = computeDatasetChecksum(context)
        if (alreadyImported && existingMeta != null && existingMeta.version == DATA_VERSION && existingMeta.checksum == checksum) {
            return@withContext false
        }

        _importProgress.value = 0.01f
        val dao = db.nutritionDao()
        val imported = runCatching {
            importAll(dao, context)
            val meta = ImportMetadata(
                version = DATA_VERSION,
                checksum = checksum,
                importedAt = Instant.now().toString(),
            )
            onMetaUpdated(meta)
            true
        }.getOrElse { throwable ->
            if (throwable is CancellationException) throw throwable
            android.util.Log.e(TAG, "Error importando (${throwable.javaClass.simpleName})", throwable)
            false
        }

        _importProgress.value = if (imported) 1.0f else null
        if (imported) delay(600)
        _importProgress.value = null
        imported
    }

    private suspend fun importAll(dao: NutritionDao, context: Context) {
        dao.clearGlobalFoods()

        android.util.Log.d(TAG, "Importando USDA...")
        val nutIdxMap = mapOf(
            1003 to 1, // protein
            1004 to 2, // fat
            1005 to 3, // carbs
            1079 to 4, // fiber
            2000 to 5, // sugar
            1093 to 6, // sodium mg
            1092 to 7, // potassium mg
            1051 to 8, // water g ~= ml
        )
        val energyPriorityByFood = HashMap<Int, Int>()

        val foodNutrients = HashMap<Int, FloatArray>(120_000)
        context.assets.open(USDA_NUTRIENT_CSV).bufferedReader().use { reader ->
            reader.readLine()
            for (line in reader.lineSequence()) {
                val parts = parseCsvLine(line)
                if (parts.size < 4) continue
                val fdcId = parts[1].toIntOrNull() ?: continue
                val nutrientId = parts[2].toIntOrNull() ?: continue
                val amount = parts[3].toFloatOrNull() ?: 0f
                val nutrients = foodNutrients.getOrPut(fdcId) { FloatArray(9) }
                val energyPriority = when (nutrientId) {
                    2048 -> 3 // Energy, Atwater specific factors (kcal)
                    2047 -> 2 // Energy, Atwater general factors (kcal)
                    1008 -> 1 // Legacy Energy (kcal)
                    else -> null
                }
                if (energyPriority != null) {
                    if (amount > 0f && energyPriority > (energyPriorityByFood[fdcId] ?: 0)) {
                        nutrients[0] = amount
                        energyPriorityByFood[fdcId] = energyPriority
                    }
                } else {
                    val idx = nutIdxMap[nutrientId] ?: continue
                    nutrients[idx] = amount
                }
            }
        }
        _importProgress.value = 0.18f

        val usdaBatch = mutableListOf<GlobalFoodEntity>()
        context.assets.open(USDA_FOOD_CSV).bufferedReader().use { reader ->
            reader.readLine()
            var processed = 0
            for (line in reader.lineSequence()) {
                val parts = parseCsvLine(line)
                if (parts.size < 3) continue
                val fdcId = parts[0].toIntOrNull() ?: continue
                val dataType = parts[1].trim('"')
                if (dataType != "foundation_food") continue
                val name = parts[2].trim('"')
                if (name.isBlank()) continue
                val normalizedName = normalizeSearch(name)
                val nutrients = foodNutrients[fdcId] ?: continue
                if (nutrients[0] <= 0f) continue
                usdaBatch.add(
                    GlobalFoodEntity(
                        foodId = "usda_$fdcId",
                        name = name,
                        normalizedName = normalizedName,
                        calories = nutrients[0].toDouble(),
                        protein = nutrients[1].toDouble(),
                        fats = nutrients[2].toDouble(),
                        carbs = nutrients[3].toDouble(),
                        fiber = nutrients[4].toDouble(),
                        sugar = nutrients[5].toDouble(),
                        sodiumMg = nutrients[6].toDouble(),
                        potassiumMg = nutrients[7].toDouble(),
                        waterMl = nutrients[8].toDouble(),
                        aliasesJson = "[]",
                        source = "USDA",
                        sourcePriority = 70,
                        verifiedScore = 0.85,
                    )
                )
                processed++

                if (usdaBatch.size >= BATCH_SIZE) {
                    dao.insertGlobalFoods(usdaBatch)
                    usdaBatch.clear()
                    _importProgress.value = (0.18f + (processed.coerceAtMost(65000) / 65000f) * 0.44f).coerceIn(0.18f, 0.62f)
                }
            }
        }
        if (usdaBatch.isNotEmpty()) dao.insertGlobalFoods(usdaBatch)
        foodNutrients.clear()
        _importProgress.value = 0.64f

        android.util.Log.d(TAG, "Importando OpenFoodFacts Chile...")
        var offProcessed = 0
        var offSkipped = 0
        var offDbFilled = 0
        runCatching {
            val offBatch = mutableListOf<GlobalFoodEntity>()
            context.assets.open(OFF_CHILE_CSV).bufferedReader().use { reader ->
                // OFF Chile CSV is TAB-separated with NO header row.
                // Column positions (0-indexed, tab-separated):
                //   0   = code (barcode)
                //   10  = product_name
                //   18  = brands
                //   88  = energy-kj_100g
                //   89  = energy-kcal_100g
                //   92  = fat_100g
                //   146 = fiber_100g
                //   156 = sodium_100g (in grams)
                //   129 = carbohydrates_100g
                //   130 = sugars_100g
                //   131 = fiber_100g
                //   150 = proteins_100g
                val idxCode = 0
                val idxName = 10
                val idxBrand = 18
                val idxKcal = 89
                val idxFat = 92
                val idxCarb = 129
                val idxSugar = 130
                val idxFiber = 146
                val idxProt = 150
                val idxSodium = 156

                for (line in reader.lineSequence()) {
                    val parts = parseTsvLine(line)
                    if (parts.size <= idxSodium) {
                        offSkipped++
                        continue
                    }

                    val code = parts[idxCode].trim()
                    if (code.isBlank()) continue

                    val rawName = parts[idxName].trim()
                    if (rawName.isBlank()) {
                        offSkipped++
                        continue
                    }
                    val rawBrand = parts[idxBrand].trim().takeIf { it.isNotBlank() }

                    // Parse raw nutrition values and reject physically impossible/corrupt values.
                    fun boundedValue(index: Int, max: Double): Double {
                        return parts[index].toDoubleOrNull()
                            ?.takeIf { it.isFinite() && it in 0.0..max }
                            ?: 0.0
                    }
                    val rawKcal = boundedValue(idxKcal, 1000.0)
                    val rawProt = boundedValue(idxProt, 100.0)
                    val rawFat = boundedValue(idxFat, 100.0)
                    val rawCarb = boundedValue(idxCarb, 100.0)
                    val rawFiber = boundedValue(idxFiber, 100.0)
                    val rawSugar = boundedValue(idxSugar, 100.0)
                    val rawSodium = parts[idxSodium].toDoubleOrNull()
                        ?.takeIf { it.isFinite() && it in 0.0..5.0 }
                    if (parts[idxSodium].isNotBlank() && rawSodium == null) {
                        offSkipped++
                        continue
                    }

                    val macroEnergy = rawProt * 4.0 + rawFat * 9.0 + rawCarb * 4.0
                    val hasRawNutrition = rawKcal > 0.0 && macroEnergy > 0.0
                    if (!hasRawNutrition) {
                        offSkipped++
                        continue
                    }
                    val energyDeviation = kotlin.math.abs(rawKcal - macroEnergy) / macroEnergy
                    if (energyDeviation > 0.5) {
                        offSkipped++
                        continue
                    }

                    // Clean and validate declared OFF data without substituting generic catalog macros.
                    val parsed = FoodDescriptionParser.parse(
                        rawName = rawName,
                        rawBrand = rawBrand,
                        rawCalories = rawKcal,
                        rawProtein = rawProt,
                        rawFat = rawFat,
                        rawCarbs = rawCarb,
                        rawFiber = rawFiber,
                        rawSugars = rawSugar,
                        rawSodium = rawSodium ?: 0.0,
                        allowDatabaseMatch = false,
                    )

                    val normalizedName = normalizeSearch(parsed.cleanedName)
                    val normalizedBrand = parsed.brandHint?.let(::normalizeSearch)
                    val aliases = buildList {
                        add(normalizedName)
                        if (!normalizedBrand.isNullOrBlank()) add(normalizedBrand)
                        parsed.matchedFoodName?.let { add(normalizeSearch(it)) }
                    }.distinct().filter { it.isNotBlank() }

                    offBatch.add(
                        GlobalFoodEntity(
                            foodId = "off_$code",
                            name = parsed.cleanedName,
                            brand = parsed.brandHint,
                            normalizedName = normalizedName,
                            normalizedBrand = normalizedBrand,
                            aliasesJson = encodeAliases(aliases),
                            calories = parsed.calories,
                            protein = parsed.protein,
                            fats = parsed.fats,
                            carbs = parsed.carbs,
                            fiber = parsed.fiber,
                            sugar = parsed.sugars,
                            sodiumMg = parsed.sodiumMg,
                            source = "OFF Chile",
                            sourcePriority = 80,
                            verifiedScore = parsed.confidence.toDouble(),
                        )
                    )
                    offProcessed++

                    if (offBatch.size >= BATCH_SIZE) {
                        dao.insertGlobalFoods(offBatch)
                        offBatch.clear()
                        _importProgress.value = (0.64f + (offProcessed.coerceAtMost(22000) / 22000f) * 0.35f).coerceIn(0.64f, 0.99f)
                    }
                }
            }
            if (offBatch.isNotEmpty()) dao.insertGlobalFoods(offBatch)
        }
        android.util.Log.d(TAG, "OFF import done: $offProcessed products ($offDbFilled DB-filled), $offSkipped skipped")
    }

    private fun parseCsvLine(line: String): List<String> {
        val res = mutableListOf<String>()
        var cur = StringBuilder()
        var q = false
        for (c in line) {
            if (c == '\"') q = !q
            else if (c == ',' && !q) { res.add(cur.toString()); cur = StringBuilder() }
            else cur.append(c)
        }
        res.add(cur.toString())
        return res
    }

    /**
     * Parse a TAB-separated line (OFF Chile format).
     * The OFF Chile CSV uses tabs, not commas.
     */
    private fun parseTsvLine(line: String): List<String> {
        return line.split('\t')
    }

    private fun normalizeSearch(value: String): String {
        val stripped = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return stripped
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{Nd}]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun encodeAliases(aliases: List<String>): String {
        val escaped = aliases.map { alias ->
            alias
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
        }
        return "[" + escaped.joinToString(",") { "\"$it\"" } + "]"
    }

    private fun computeDatasetChecksum(context: Context): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fun updateAsset(path: String) {
            context.assets.open(path).use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var read = input.read(buffer)
                while (read >= 0) {
                    if (read > 0) digest.update(buffer, 0, read)
                    read = input.read(buffer)
                }
            }
        }

        updateAsset(USDA_FOOD_CSV)
        updateAsset(USDA_NUTRIENT_CSV)
        runCatching { updateAsset(OFF_CHILE_CSV) }

        return digest.digest().joinToString(separator = "") { "%02x".format(it) }
    }
}
