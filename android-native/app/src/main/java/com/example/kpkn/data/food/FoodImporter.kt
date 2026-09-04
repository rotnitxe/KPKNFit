package com.example.kpkn.data.food

import android.content.Context
import androidx.room.withTransaction
import com.example.kpkn.data.db.GlobalFoodEntity
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.NutritionDao
import com.example.kpkn.domain.nutrition.FoodIdentity
import com.example.kpkn.telemetry.nutrition.NutritionTelemetry
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
 *
 * v22 (plan 2026-08-16_nutrition_precision_v2): cada fila importada conserva
 * procedencia (ID de registro, estado, base nutricional, versión del dataset,
 * categoría, porción y flags de calidad) y la importación es atómica — un
 * fallo a mitad de camino deja intacta la versión anterior del catálogo.
 */
object FoodImporter {
    private const val TAG = "FoodImporter"
    private const val BATCH_SIZE = 2000
    private const val DATA_VERSION = 9
    private const val USDA_FOOD_CSV = "food_data/food.csv"
    private const val USDA_NUTRIENT_CSV = "food_data/food_nutrient.csv"
    private const val USDA_PORTION_CSV = "food_data/food_portion.csv"
    private const val OFF_CHILE_CSV = "food_data/off_chile.csv"

    /** Umbral de incoherencia energética kcal vs 4P+4C+9G para flag (no rechazo). */
    internal const val ENERGY_MISMATCH_TOLERANCE = 0.35

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
        NutritionTelemetry.catalogImportStarted(DATA_VERSION.toString())
        val dao = db.nutritionDao()
        // Atómica: clear+insert dentro de una transacción. Si algo falla a
        // mitad de importación, el rollback conserva la versión anterior
        // completa (antes clearGlobalFoods() borraba primero y un fallo dejaba
        // el catálogo vacío). El FTS external-content se mantiene por triggers
        // dentro de la misma transacción y la meta se persiste solo al final.
        val imported = runCatching {
            db.withTransaction {
                importAll(dao, context)
            }
            val meta = ImportMetadata(
                version = DATA_VERSION,
                checksum = checksum,
                importedAt = Instant.now().toString(),
            )
            onMetaUpdated(meta)
            NutritionTelemetry.catalogImportCompleted(DATA_VERSION.toString(), dao.getGlobalFoodCount())
            true
        }.getOrElse { throwable ->
            if (throwable is CancellationException) throw throwable
            android.util.Log.e(TAG, "Error importando (${throwable.javaClass.simpleName})", throwable)
            NutritionTelemetry.catalogImportFailed(throwable.javaClass.simpleName)
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
            1057 to 9, // caffeine mg
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
                val nutrients = foodNutrients.getOrPut(fdcId) { FloatArray(10) }
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

        // Porciones domésticas autoritativas de USDA (food_portion.csv): la
        // primera porción declarada por ficha. Sin esto, todo alimento global
        // caería en el "100 g" genérico aunque la fuente declare "1 breast =
        // 174 g" (compuerta Fase 2).
        val authoritativePortions = HashMap<Int, Pair<Double, String>>(30_000)
        runCatching {
            context.assets.open(USDA_PORTION_CSV).bufferedReader().use { reader ->
                reader.readLine() // header
                for (line in reader.lineSequence()) {
                    val parts = parseCsvLine(line)
                    if (parts.size < 8) continue
                    val fdcId = parts[1].toIntOrNull() ?: continue
                    val gramWeight = parts[7].toDoubleOrNull() ?: continue
                    if (gramWeight <= 0.0 || !gramWeight.isFinite()) continue
                    val unit = parts[4].trim('"').takeIf { it.isNotBlank() } ?: "g"
                    // La primera fila (seq_num menor) es la porción principal.
                    authoritativePortions.putIfAbsent(fdcId, gramWeight to unit)
                }
            }
        }.onFailure {
            android.util.Log.w(TAG, "food_portion.csv no disponible: porciones domésticas omitidas", it)
        }

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
                val calories = nutrients[0].toDouble()
                val protein = nutrients[1].toDouble()
                val fats = nutrients[2].toDouble()
                val carbs = nutrients[3].toDouble()
                // Validación física (plan Fase 2): negativos, no finitos o
                // macros individuales > 100 g/100 g no entran al catálogo.
                if (!hasPhysicallyPlausibleMacros(calories, protein, carbs, fats)) continue
                usdaBatch.add(
                    GlobalFoodEntity(
                        foodId = "usda_$fdcId",
                        name = name,
                        normalizedName = normalizedName,
                        calories = calories,
                        protein = protein,
                        fats = fats,
                        carbs = carbs,
                        fiber = nutrients[4].toDouble(),
                        sugar = nutrients[5].toDouble(),
                        sodiumMg = nutrients[6].toDouble(),
                        potassiumMg = nutrients[7].toDouble(),
                        waterMl = nutrients[8].toDouble(),
                        caffeineMg = nutrients[9].toDouble(),
                        aliasesJson = "[]",
                        source = "USDA",
                        sourcePriority = 70,
                        verifiedScore = 0.85,
                        // Procedencia v22
                        sourceRecordId = fdcId.toString(),
                        foodState = stateForDescription(name),
                        nutritionBasis = nutritionBasisFor(stateForDescription(name)),
                        datasetVersion = DATA_VERSION.toString(),
                        category = parts.getOrNull(3)?.trim('"')?.takeIf { it.isNotBlank() },
                        portionGrams = authoritativePortions[fdcId]?.first,
                        portionUnit = authoritativePortions[fdcId]?.second,
                        qualityFlagsJson = encodeQualityFlags(
                            usdaQualityFlags(calories, protein, carbs, fats)
                        ),
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
                            // Procedencia v22: OFF declara per-100g tal como se
                            // vende; el código de barras es el ID de registro.
                            sourceRecordId = code,
                            foodState = "UNKNOWN",
                            nutritionBasis = "PER_100G_AS_SOLD",
                            datasetVersion = DATA_VERSION.toString(),
                            qualityFlagsJson = encodeQualityFlags(
                                offQualityFlags(rawKcal, rawProt, rawCarb, rawFat, parsed.confidence)
                            ),
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

    // ─── Validación y calidad (Fase 2, testeables sin Android) ─────────────

    /** Rechaza negativos, no finitos, energía nula y macros > 100 g/100 g. */
    internal fun hasPhysicallyPlausibleMacros(
        calories: Double,
        protein: Double,
        carbs: Double,
        fats: Double,
    ): Boolean {
        val macros = listOf(protein, carbs, fats)
        if (macros.any { !it.isFinite() || it < 0.0 || it > 100.0 }) return false
        if (!calories.isFinite() || calories <= 0.0) return false
        return true
    }

    /**
     * Flags de calidad USDA: incoherencia energética frente al diagnóstico
     * Atwater (4P+4C+9G). Se marca, no se rechaza — diferencias explicables
     * por fibra, alcohol, polioles o factores específicos no deben caerse.
     */
    internal fun usdaQualityFlags(
        calories: Double,
        protein: Double,
        carbs: Double,
        fats: Double,
    ): List<String> {
        val flags = mutableListOf<String>()
        val macroEnergy = protein * 4.0 + carbs * 4.0 + fats * 9.0
        if (macroEnergy > 0.0) {
            val deviation = kotlin.math.abs(calories - macroEnergy) / macroEnergy
            if (deviation > ENERGY_MISMATCH_TOLERANCE) flags.add("ENERGY_MISMATCH")
        }
        if (protein <= 0.0 && carbs <= 0.0 && fats <= 0.0) flags.add("INCOMPLETE")
        return flags
    }

    /** Flags OFF: naturaleza colaborativa — además de energía, baja confianza del parser. */
    internal fun offQualityFlags(
        calories: Double,
        protein: Double,
        carbs: Double,
        fats: Double,
        parserConfidence: Float,
    ): List<String> {
        val flags = usdaQualityFlags(calories, protein, carbs, fats).toMutableList()
        if (parserConfidence < 0.6f) flags.add("LOW_QUALITY")
        return flags.distinct()
    }

    /** Estado raw/cooked derivado de la descripción de origen (USDA en inglés). */
    internal fun stateForDescription(description: String): String =
        FoodIdentity.stateFor(description).name

    internal fun nutritionBasisFor(state: String): String = when (state.uppercase()) {
        "RAW" -> "PER_100G_RAW"
        "COOKED", "HYDRATED" -> "PER_100G_COOKED"
        else -> "PER_100G_AS_SOLD"
    }

    internal fun encodeQualityFlags(flags: List<String>): String {
        if (flags.isEmpty()) return "[]"
        return "[" + flags.joinToString(",") { "\"$it\"" } + "]"
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
        updateAsset(USDA_PORTION_CSV)
        updateAsset(OFF_CHILE_CSV)

        return digest.digest().joinToString(separator = "") { "%02x".format(it) }
    }
}
