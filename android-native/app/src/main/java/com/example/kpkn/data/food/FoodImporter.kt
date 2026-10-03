package com.example.kpkn.data.food

import android.content.Context
import androidx.room.withTransaction
import com.example.kpkn.data.db.GlobalFoodEntity
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.NutritionDao
import com.example.kpkn.domain.nutrition.FoodIdentity
import com.example.kpkn.domain.nutrition.HouseholdPortions
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
    /**
     * Versión de datos del catálogo importado: subirla fuerza un re-import en todas las instalaciones.
     * 10 (WP-S9): nutrientes por prioridad (azúcar 2000 > 1063, grasa 1004 > 1085), porción de UNA unidad, categoría
     * legible y nombres/alias en español desde `usda_es_aliases.csv`.
     */
    internal const val DATA_VERSION = 10
    private const val USDA_FOOD_CSV = "food_data/food.csv"
    private const val USDA_NUTRIENT_CSV = "food_data/food_nutrient.csv"
    private const val USDA_PORTION_CSV = "food_data/food_portion.csv"
    private const val USDA_MEASURE_UNIT_CSV = "food_data/measure_unit.csv"
    private const val USDA_CATEGORY_CSV = "food_data/food_category.csv"
    private const val USDA_ALIASES_CSV = "food_data/usda_es_aliases.csv"
    private const val OFF_CHILE_CSV = "food_data/off_chile.csv"

    /**
     * Assets que lee el import. Los auxiliares se degradan en silencio si faltan (ver [readOptionalCsv]), así que un test
     * comprueba que todos existen; también son lo que resume el SHA-256 del log.
     */
    internal val IMPORT_ASSETS = listOf(
        USDA_FOOD_CSV, USDA_NUTRIENT_CSV, USDA_PORTION_CSV, USDA_MEASURE_UNIT_CSV, USDA_CATEGORY_CSV, USDA_ALIASES_CSV,
        OFF_CHILE_CSV,
    )

    /** Umbral de incoherencia energética kcal vs 4P+4C+9G para flag (no rechazo). */
    internal const val ENERGY_MISMATCH_TOLERANCE = 0.35

    data class ImportMetadata(
        val version: Int,
        val checksum: String,
        val importedAt: String,
    )

    private val _importProgress = MutableStateFlow<Float?>(null)
    val importProgress: StateFlow<Float?> = _importProgress.asStateFlow()

    /**
     * Huella esperada del dataset embebido (WP-S3). En esta etapa es solo la versión de datos: un cambio de contenido
     * en los CSV exige subir [DATA_VERSION]. Calcularla no abre ningún asset, a diferencia del SHA-256 de ~72 MB que
     * antes se hasheaba en CADA arranque en frío, antes de publicar las comidas del usuario.
     */
    internal fun datasetFingerprint(): String = "v$DATA_VERSION"

    /**
     * Compuerta pura del arranque (no lee assets ni la base): importa si el catálogo global está vacío, si falta la
     * meta guardada, si cambió [DATA_VERSION] o si cambió la huella [expectedFingerprint].
     */
    internal fun shouldImport(
        alreadyImported: Boolean,
        meta: ImportMetadata?,
        expectedFingerprint: String,
    ): Boolean = !(
        alreadyImported &&
            meta != null &&
            meta.version == DATA_VERSION &&
            meta.checksum == expectedFingerprint
        )

    /** Antes de WP-S3 el `checksum` guardado era el SHA-256 hexadecimal (64 caracteres) de los CSV. */
    private val LEGACY_CONTENT_SHA256 = Regex("^[0-9a-f]{64}$")

    /**
     * Las instalaciones previas guardaron el SHA-256 de los CSV como `checksum`. Con la MISMA [DATA_VERSION] el
     * dataset importado es el mismo, así que se adopta la huella vigente en vez de re-importar ~72 MB (la importación
     * actual es una sola transacción larga que bloquea las escrituras de Room mientras la app ya es usable). Solo
     * aplica mientras la huella sea el esquema por versión; cualquier otro esquema compara de forma estricta.
     */
    internal fun adoptLegacyChecksum(meta: ImportMetadata?, expectedFingerprint: String): ImportMetadata? {
        if (meta == null || meta.version != DATA_VERSION) return meta
        if (expectedFingerprint != datasetFingerprint()) return meta
        return if (LEGACY_CONTENT_SHA256.matches(meta.checksum)) meta.copy(checksum = expectedFingerprint) else meta
    }

    suspend fun importIfEmpty(alreadyImported: Boolean, context: Context) = withContext(Dispatchers.IO) {
        importIfNeeded(
            db = KpknDatabase.getInstance(context),
            context = context,
            alreadyImported = alreadyImported,
            existingMeta = null,
            onMetaUpdated = {},
        )
    }

    /**
     * Importa el catálogo global si hace falta. La decisión es barata (sin leer assets, ver [shouldImport]); el SHA-256
     * de los CSV solo se calcula dentro de la importación, para dejar rastro de qué contenido se importó.
     */
    suspend fun importIfNeeded(
        db: KpknDatabase,
        context: Context,
        alreadyImported: Boolean,
        existingMeta: ImportMetadata?,
        onMetaUpdated: (ImportMetadata) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        val fingerprint = datasetFingerprint()
        if (!shouldImport(alreadyImported, adoptLegacyChecksum(existingMeta, fingerprint), fingerprint)) {
            return@withContext false
        }

        try {
            _importProgress.value = 0.01f
            NutritionTelemetry.catalogImportStarted(DATA_VERSION.toString())
            val dao = db.nutritionDao()
            // Atómica: clear+insert dentro de una transacción. Si algo falla a
            // mitad de importación, el rollback conserva la versión anterior
            // completa (antes clearGlobalFoods() borraba primero y un fallo dejaba
            // el catálogo vacío). El FTS external-content se mantiene por triggers
            // dentro de la misma transacción y la meta se persiste solo al final.
            val imported = runCatching {
                // Solo documenta el contenido importado; ya no decide nada ni corre en el arranque normal.
                val contentSha256 = runCatching { computeDatasetChecksum(context) }.getOrNull()
                android.util.Log.i(TAG, "Importando catálogo v$DATA_VERSION (sha256 de los CSV: ${contentSha256 ?: "no disponible"})")
                db.withTransaction {
                    importAll(dao, context)
                }
                val meta = ImportMetadata(
                    version = DATA_VERSION,
                    checksum = fingerprint,
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
            imported
        } finally {
            // También ante cancelación: el indicador nunca queda colgado a mitad de camino.
            _importProgress.value = null
        }
    }

    private suspend fun importAll(dao: NutritionDao, context: Context) {
        dao.clearGlobalFoods()

        android.util.Log.d(TAG, "Importando USDA...")
        val foodNutrients = context.assets.open(USDA_NUTRIENT_CSV).bufferedReader().use { reader ->
            reader.readLine()
            parseUsdaNutrients(reader.lineSequence())
        }
        _importProgress.value = 0.18f

        // Tablas auxiliares (WP-S9). Ninguna es imprescindible: si falta o no se puede leer, el catálogo USDA se importa
        // igual (sin porción, sin categoría o con el nombre en inglés) en vez de perderse entero.
        val measureUnits = readOptionalCsv(context, USDA_MEASURE_UNIT_CSV, emptyMap<Int, String>()) { parseMeasureUnits(it) }
        // Porciones domésticas autoritativas de USDA (food_portion.csv): la primera porción declarada por ficha, ya
        // dividida por su cantidad. Sin esto, todo alimento global caería en el "100 g" genérico aunque la fuente
        // declare "1 breast = 174 g" (compuerta Fase 2).
        val authoritativePortions = readOptionalCsv(context, USDA_PORTION_CSV, emptyMap<Int, UsdaPortion>()) {
            parseUsdaPortions(it, measureUnits)
        }
        val categories = readOptionalCsv(context, USDA_CATEGORY_CSV, emptyMap<Int, String>()) { parseFoodCategories(it) }
        val spanishAliases = readOptionalCsv(context, USDA_ALIASES_CSV, emptyMap<Int, UsdaAlias>()) { parseUsdaAliases(it) }

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
                val nutrients = foodNutrients[fdcId] ?: continue
                val entity = usdaEntityOrNull(
                    fdcId = fdcId,
                    description = parts[2],
                    category = usdaCategory(parts.getOrNull(3), categories),
                    nutrients = nutrients,
                    portion = authoritativePortions[fdcId],
                    alias = spanishAliases[fdcId],
                ) ?: continue
                usdaBatch.add(entity)
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

    // ─── USDA: nutrientes, porciones, categoría y alias (WP-S9, testeables sin Android) ───────────────

    /** Columnas de salida de un alimento USDA (posiciones dentro de [UsdaNutrients]). */
    private const val COL_ENERGY = 0
    private const val COL_PROTEIN = 1
    private const val COL_FAT = 2
    private const val COL_CARBS = 3
    private const val COL_FIBER = 4
    private const val COL_SUGAR = 5
    private const val COL_SODIUM_MG = 6
    private const val COL_POTASSIUM_MG = 7
    private const val COL_WATER = 8
    private const val COL_CAFFEINE_MG = 9
    private const val COLUMN_COUNT = 10

    private class NutrientTarget(val column: Int, val rank: Int)

    /**
     * Id de `nutrient.csv` -> columna de salida y prioridad. Varios ids pueden alimentar la misma columna y gana, POR
     * ALIMENTO, el de mayor prioridad que esté presente: azúcar 2000 > 1063 (casi todo Foundation solo trae el 1063,
     * "Sugars, Total"), grasa 1004 > 1085 ("Total fat (NLEA)") y energía 2048 > 2047 > 1008.
     */
    private val NUTRIENT_TARGETS: Map<Int, NutrientTarget> = mapOf(
        2048 to NutrientTarget(COL_ENERGY, 3), // Energy, Atwater specific factors (kcal)
        2047 to NutrientTarget(COL_ENERGY, 2), // Energy, Atwater general factors (kcal)
        1008 to NutrientTarget(COL_ENERGY, 1), // Legacy Energy (kcal)
        1003 to NutrientTarget(COL_PROTEIN, 1),
        1004 to NutrientTarget(COL_FAT, 2), // Total lipid (fat)
        1085 to NutrientTarget(COL_FAT, 1), // Total fat (NLEA): solo si falta el 1004
        1005 to NutrientTarget(COL_CARBS, 1),
        1079 to NutrientTarget(COL_FIBER, 1),
        2000 to NutrientTarget(COL_SUGAR, 2), // Total Sugars
        1063 to NutrientTarget(COL_SUGAR, 1), // Sugars, Total: solo si falta el 2000
        1093 to NutrientTarget(COL_SODIUM_MG, 1),
        1092 to NutrientTarget(COL_POTASSIUM_MG, 1),
        1051 to NutrientTarget(COL_WATER, 1), // g ~= ml
        1057 to NutrientTarget(COL_CAFFEINE_MG, 1),
    )

    /**
     * Nutrientes de UN alimento USDA, resueltos por prioridad (ver [NUTRIENT_TARGETS]). Los valores siguen siendo float,
     * como antes: los números importados no cambian salvo el azúcar, que quedaba en 0 si solo existía el id 1063.
     */
    internal class UsdaNutrients {
        private val values = FloatArray(COLUMN_COUNT)
        private val ranks = IntArray(COLUMN_COUNT)

        val energy: Float get() = values[COL_ENERGY]
        val protein: Float get() = values[COL_PROTEIN]
        val fat: Float get() = values[COL_FAT]
        val carbs: Float get() = values[COL_CARBS]
        val fiber: Float get() = values[COL_FIBER]
        val sugar: Float get() = values[COL_SUGAR]
        val sodiumMg: Float get() = values[COL_SODIUM_MG]
        val potassiumMg: Float get() = values[COL_POTASSIUM_MG]
        val water: Float get() = values[COL_WATER]
        val caffeineMg: Float get() = values[COL_CAFFEINE_MG]

        /** Registra una fila de `food_nutrient`. Devuelve true si cambió el valor de alguna columna. */
        fun accept(nutrientId: Int, amount: Float): Boolean {
            val target = NUTRIENT_TARGETS[nutrientId] ?: return false
            // Una energía nula no cuenta: ni tapa a una fuente de menor prioridad con valor ni crea energía de la nada.
            if (target.column == COL_ENERGY && !(amount > 0f)) return false
            if (target.rank <= ranks[target.column]) return false
            values[target.column] = amount
            ranks[target.column] = target.rank
            return true
        }
    }

    /**
     * `food_nutrient.csv` (id, fdc_id, nutrient_id, amount, ...; sin la cabecera) -> nutrientes por alimento. Las filas
     * de nutrientes que el catálogo no usa, o sin cantidad legible, no crean entrada.
     */
    internal fun parseUsdaNutrients(lines: Sequence<String>, expectedFoods: Int = 120_000): HashMap<Int, UsdaNutrients> {
        val byFood = HashMap<Int, UsdaNutrients>(expectedFoods)
        for (line in lines) {
            val parts = parseCsvLine(line)
            if (parts.size < 4) continue
            val fdcId = parts[1].toIntOrNull() ?: continue
            val nutrientId = parts[2].toIntOrNull() ?: continue
            if (nutrientId !in NUTRIENT_TARGETS) continue
            val amount = parts[3].toFloatOrNull() ?: continue
            byFood.getOrPut(fdcId) { UsdaNutrients() }.accept(nutrientId, amount)
        }
        return byFood
    }

    /** Porción doméstica de UNA unidad: 1 [unit] = [grams] g (ya dividida por la cantidad `amount` de la fuente). */
    internal data class UsdaPortion(val grams: Double, val unit: String)

    /** Por debajo de 5 g por unidad es una densidad (aceite: 100 ml = 90,7 g), no una porción que alguien coma. */
    private const val MIN_PORTION_GRAMS = 5.0

    /** `amount` viene con un decimal (0.2 por 0.25 taza): bajo 0,5 el redondeo distorsiona los gramos por unidad. */
    private const val MIN_PORTION_AMOUNT = 0.5

    private class SeqPortion(val seq: Int, val portion: UsdaPortion)

    /**
     * Porción doméstica por alimento desde `food_portion.csv` (id, fdc_id, seq_num, amount, measure_unit_id,
     * portion_description, modifier, gram_weight, ...; sin la cabecera). `gram_weight` pesa `amount` unidades (hummus:
     * 2 cucharadas = 33,9 g), así que la porción de UNA unidad es gram_weight / amount. Por alimento se queda con la
     * primera fila (menor `seq_num`) cuya porción por unidad cae entre [MIN_PORTION_GRAMS] y
     * [HouseholdPortions.PACK_GRAMS]; las filas sin unidad ni descripción conocidas se omiten.
     */
    internal fun parseUsdaPortions(lines: Sequence<String>, measureUnits: Map<Int, String>): Map<Int, UsdaPortion> {
        val best = HashMap<Int, SeqPortion>()
        for (line in lines) {
            val parts = parseCsvLine(line)
            if (parts.size < 8) continue
            val fdcId = parts[1].trim().toIntOrNull() ?: continue
            val amount = parts[3].trim().toDoubleOrNull()?.takeIf { it.isFinite() && it >= MIN_PORTION_AMOUNT } ?: continue
            val gramWeight = parts[7].trim().toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: continue
            val gramsPerUnit = gramWeight / amount
            if (gramsPerUnit < MIN_PORTION_GRAMS || gramsPerUnit > HouseholdPortions.PACK_GRAMS) continue
            val unitName = parts[4].trim().toIntOrNull()?.let { measureUnits[it] }
            val label = usdaPortionLabel(unitName, parts[5].ifBlank { parts[6] }) ?: continue
            // Sin seq_num (filas que no son de Foundation) queda al final; a igual seq_num gana la fila que aparece antes.
            val seq = parts[2].trim().toIntOrNull() ?: Int.MAX_VALUE
            val current = best[fdcId]
            if (current == null || seq < current.seq) best[fdcId] = SeqPortion(seq, UsdaPortion(gramsPerUnit, label))
        }
        return best.mapValues { it.value.portion }
    }

    /**
     * Etiqueta de la unidad: "tablespoon", "cup, chopped", "egg, whole without shell". Une el nombre de `measure_unit.csv`
     * con el detalle (`portion_description`, si no `modifier`) para no perder la unidad cuando hay detalle. "undetermined"
     * o una unidad desconocida no es unidad: queda solo el detalle, y sin detalle devuelve null.
     */
    internal fun usdaPortionLabel(unitName: String?, detail: String): String? {
        val unit = unitName?.trim()?.takeUnless { it.isEmpty() || it.equals("undetermined", ignoreCase = true) }
        val extra = detail.trim()
        return when {
            unit != null && extra.isNotEmpty() -> "$unit, $extra"
            unit != null -> unit
            extra.isNotEmpty() -> extra
            else -> null
        }
    }

    /** `measure_unit.csv` (id, name; sin la cabecera) -> id a nombre. */
    internal fun parseMeasureUnits(lines: Sequence<String>): Map<Int, String> = parseIdNameCsv(lines, nameColumn = 1)

    /** `food_category.csv` (id, code, description; sin la cabecera) -> id a descripción. */
    internal fun parseFoodCategories(lines: Sequence<String>): Map<Int, String> = parseIdNameCsv(lines, nameColumn = 2)

    private fun parseIdNameCsv(lines: Sequence<String>, nameColumn: Int): Map<Int, String> {
        val result = HashMap<Int, String>()
        for (line in lines) {
            val parts = parseCsvLine(line)
            if (parts.size <= nameColumn) continue
            val id = parts[0].trim().toIntOrNull() ?: continue
            val name = parts[nameColumn].trim()
            if (name.isNotEmpty()) result[id] = name
        }
        return result
    }

    /** Categoría legible (`food.csv` solo trae el id numérico); null si el id no existe en `food_category.csv`. */
    internal fun usdaCategory(rawCategoryId: String?, categories: Map<Int, String>): String? =
        rawCategoryId?.trim()?.toIntOrNull()?.let { categories[it] }

    /** Fila de `usda_es_aliases.csv`: nombre en español (null = se queda el inglés) y sinónimos tal como se curaron. */
    internal data class UsdaAlias(val esName: String?, val aliases: List<String>)

    /**
     * `usda_es_aliases.csv` (fdc_id, en_description, es_name, aliases; sin la cabecera). `aliases` separa con `|`. Las
     * filas sin nombre ni alias se omiten: ese alimento conserva su descripción en inglés.
     */
    internal fun parseUsdaAliases(lines: Sequence<String>): Map<Int, UsdaAlias> {
        val result = HashMap<Int, UsdaAlias>()
        for (line in lines) {
            if (line.isBlank()) continue
            val parts = parseCsvLine(line)
            if (parts.size < 3) continue
            val fdcId = parts[0].trim().toIntOrNull() ?: continue
            val esName = parts[2].trim().takeIf { it.isNotEmpty() }
            val aliases = parts.getOrNull(3).orEmpty().split('|').map { it.trim() }.filter { it.isNotEmpty() }
            if (esName != null || aliases.isNotEmpty()) result[fdcId] = UsdaAlias(esName, aliases)
        }
        return result
    }

    /** Alias de búsqueda: `[normalize(es_name), normalize(descripción en inglés)] + alias`, sin vacíos ni repetidos. */
    internal fun usdaSearchAliases(description: String, alias: UsdaAlias?): List<String> = buildList {
        alias?.esName?.let { add(normalizeSearch(it)) }
        add(normalizeSearch(description))
        alias?.aliases?.forEach { add(normalizeSearch(it)) }
    }.filter { it.isNotEmpty() }.distinct()

    private val WHITESPACE = Regex("""\s+""")

    /** `food.csv` trae espacios duros (U+00A0) y espacios sobrantes en algunas descripciones. */
    internal fun cleanUsdaDescription(raw: String): String =
        raw.replace(Char(0x00A0), ' ').trim().replace(WHITESPACE, " ")

    /**
     * Entidad global de un alimento USDA, o null si la fila no entra al catálogo (sin energía o físicamente imposible).
     * El nombre es el español curado de `usda_es_aliases.csv` o, sin él, la descripción en inglés; el estado crudo/cocido
     * se deduce SIEMPRE de la descripción en inglés, que es la que declara la fuente.
     */
    internal fun usdaEntityOrNull(
        fdcId: Int,
        description: String,
        category: String?,
        nutrients: UsdaNutrients,
        portion: UsdaPortion?,
        alias: UsdaAlias?,
    ): GlobalFoodEntity? {
        val english = cleanUsdaDescription(description)
        if (english.isBlank()) return null
        if (nutrients.energy <= 0f) return null
        val calories = nutrients.energy.toDouble()
        val protein = nutrients.protein.toDouble()
        val fats = nutrients.fat.toDouble()
        val carbs = nutrients.carbs.toDouble()
        // Validación física (plan Fase 2): negativos, no finitos o macros individuales > 100 g/100 g no entran.
        if (!hasPhysicallyPlausibleMacros(calories, protein, carbs, fats)) return null
        val name = alias?.esName ?: english
        val state = stateForDescription(english)
        return GlobalFoodEntity(
            foodId = "usda_$fdcId",
            name = name,
            normalizedName = normalizeSearch(name),
            calories = calories,
            protein = protein,
            fats = fats,
            carbs = carbs,
            fiber = nutrients.fiber.toDouble(),
            sugar = nutrients.sugar.toDouble(),
            sodiumMg = nutrients.sodiumMg.toDouble(),
            potassiumMg = nutrients.potassiumMg.toDouble(),
            waterMl = nutrients.water.toDouble(),
            caffeineMg = nutrients.caffeineMg.toDouble(),
            aliasesJson = encodeAliases(usdaSearchAliases(english, alias)),
            source = "USDA",
            sourcePriority = 70,
            verifiedScore = 0.85,
            // Procedencia v22
            sourceRecordId = fdcId.toString(),
            foodState = state,
            nutritionBasis = nutritionBasisFor(state),
            datasetVersion = DATA_VERSION.toString(),
            category = category,
            portionGrams = portion?.grams,
            portionUnit = portion?.unit,
            qualityFlagsJson = encodeQualityFlags(usdaQualityFlags(calories, protein, carbs, fats)),
        )
    }

    /** Lee un CSV auxiliar (sin la cabecera); si no existe o no se puede leer devuelve [fallback] y el import sigue. */
    private inline fun <T> readOptionalCsv(context: Context, path: String, fallback: T, parse: (Sequence<String>) -> T): T =
        runCatching {
            context.assets.open(path).bufferedReader().use { reader ->
                reader.readLine() // cabecera
                parse(reader.lineSequence())
            }
        }.getOrElse {
            android.util.Log.w(TAG, "$path no disponible: se importa sin él", it)
            fallback
        }

    /**
     * Parse a TAB-separated line (OFF Chile format).
     * The OFF Chile CSV uses tabs, not commas.
     */
    private fun parseTsvLine(line: String): List<String> {
        return line.split('\t')
    }

    internal fun normalizeSearch(value: String): String {
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

        IMPORT_ASSETS.forEach(::updateAsset)

        return digest.digest().joinToString(separator = "") { "%02x".format(it) }
    }
}
