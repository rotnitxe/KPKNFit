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
    private const val DATA_VERSION = 3
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
            1008 to 0, // kcal
            1003 to 1, // protein
            1004 to 2, // fat
            1005 to 3, // carbs
            1079 to 4, // fiber
            2000 to 5, // sugar
            1093 to 6, // sodium mg
            1092 to 7, // potassium mg
            1051 to 8, // water g ~= ml
        )

        val foodNutrients = HashMap<Int, FloatArray>(120_000)
        context.assets.open(USDA_NUTRIENT_CSV).bufferedReader().use { reader ->
            reader.readLine()
            for (line in reader.lineSequence()) {
                val parts = parseCsvLine(line)
                if (parts.size < 4) continue
                val fdcId = parts[1].toIntOrNull() ?: continue
                val nutrientId = parts[2].toIntOrNull() ?: continue
                val idx = nutIdxMap[nutrientId] ?: continue
                val amount = parts[3].toFloatOrNull() ?: 0f
                foodNutrients.getOrPut(fdcId) { FloatArray(9) }[idx] = amount
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
                val name = parts[2].trim('"')
                if (name.isBlank()) continue
                val normalizedName = normalizeSearch(name)
                val nutrients = foodNutrients[fdcId]
                usdaBatch.add(
                    GlobalFoodEntity(
                        foodId = "usda_$fdcId",
                        name = name,
                        normalizedName = normalizedName,
                        calories = nutrients?.get(0)?.toDouble() ?: 0.0,
                        protein = nutrients?.get(1)?.toDouble() ?: 0.0,
                        fats = nutrients?.get(2)?.toDouble() ?: 0.0,
                        carbs = nutrients?.get(3)?.toDouble() ?: 0.0,
                        fiber = nutrients?.get(4)?.toDouble() ?: 0.0,
                        sugar = nutrients?.get(5)?.toDouble() ?: 0.0,
                        sodiumMg = nutrients?.get(6)?.toDouble() ?: 0.0,
                        potassiumMg = nutrients?.get(7)?.toDouble() ?: 0.0,
                        waterMl = nutrients?.get(8)?.toDouble() ?: 0.0,
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
                //   88  = energy-kcal_100g
                //   92  = fat_100g
                //   128 = sodium_100g (in grams)
                //   129 = carbohydrates_100g
                //   130 = sugars_100g
                //   131 = fiber_100g
                //   150 = proteins_100g
                val idxCode = 0
                val idxName = 10
                val idxBrand = 18
                val idxKcal = 88
                val idxFat = 92
                val idxSodium = 128
                val idxCarb = 129
                val idxSugar = 130
                val idxFiber = 131
                val idxProt = 150

                for (line in reader.lineSequence()) {
                    val parts = parseTsvLine(line)
                    if (parts.size < 151) {
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

                    // Parse raw nutrition values
                    val rawKcal = parts[idxKcal].toDoubleOrNull() ?: 0.0
                    val rawProt = parts[idxProt].toDoubleOrNull() ?: 0.0
                    val rawFat = parts[idxFat].toDoubleOrNull() ?: 0.0
                    val rawCarb = parts[idxCarb].toDoubleOrNull() ?: 0.0
                    val rawFiber = parts[idxFiber].toDoubleOrNull() ?: 0.0
                    val rawSugar = parts[idxSugar].toDoubleOrNull() ?: 0.0
                    val rawSodium = parts[idxSodium].toDoubleOrNull() ?: 0.0

                    val hasRawNutrition = rawKcal > 0 || rawProt > 0 || rawFat > 0 || rawCarb > 0

                    // Run heuristic description parser (works with or without raw nutrition)
                    val parsed = FoodDescriptionParser.parse(
                        rawName = rawName,
                        rawBrand = rawBrand,
                        rawCalories = rawKcal,
                        rawProtein = rawProt,
                        rawFat = rawFat,
                        rawCarbs = rawCarb,
                        rawFiber = rawFiber,
                        rawSugars = rawSugar,
                        rawSodium = rawSodium,
                    )

                    // Accept if: has raw nutrition data OR matched FoodDatabase with confidence >= 0.65
                    val hasDbMatch = parsed.matchedFoodName != null && parsed.confidence >= 0.65f
                    if (!hasRawNutrition && !hasDbMatch) {
                        offSkipped++
                        continue
                    }

                    if (!hasRawNutrition && hasDbMatch) {
                        offDbFilled++
                    }

                    val normalizedName = normalizeSearch(parsed.cleanedName)
                    val normalizedBrand = parsed.brandHint?.let(::normalizeSearch)
                    val aliases = buildList {
                        add(normalizedName)
                        if (!normalizedBrand.isNullOrBlank()) add(normalizedBrand)
                        parsed.matchedFoodName?.let { add(normalizeSearch(it)) }
                    }.distinct().filter { it.isNotBlank() }

                    // Lower priority for DB-filled entries (no real OFF nutrition data)
                    val (sourcePriority, verifiedScore) = if (!hasRawNutrition && hasDbMatch) {
                        60 to 0.55 // DB-filled: lower priority, lower confidence
                    } else {
                        80 to parsed.confidence.toDouble() // Real OFF data
                    }

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
                            sourcePriority = sourcePriority,
                            verifiedScore = verifiedScore,
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
