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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.security.MessageDigest
import java.text.Normalizer
import java.time.Instant
import kotlin.coroutines.CoroutineContext

/**
 * FoodImporter — Soporta USDA y OpenFoodFacts Chile.
 *
 * v22 (plan 2026-08-16_nutrition_precision_v2): cada fila importada conserva
 * procedencia (ID de registro, estado, base nutricional, versión del dataset,
 * categoría, porción y flags de calidad) y la importación es atómica — un
 * fallo a mitad de camino deja intacta la versión anterior del catálogo.
 *
 * WP-S8: la importación se parte en análisis de los CSV (sin lock, en [Dispatchers.Default]) y un commit corto
 * de Room ([commitRows]); ver [importIfNeeded].
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

    // Progreso de la importación (WP-S8): el análisis sin lock ocupa la banda 0,05..0,95; antes solo hay arranque y
    // después el commit corto y el cierre en 1,0.
    private const val PROGRESS_STARTED = 0.01f
    private const val PROGRESS_PARSE_START = 0.05f
    private const val PROGRESS_PARSE_END = 0.95f

    /** Fracción del análisis que ocupa USDA (~24 % de los bytes, ~35 % del tiempo medido); el resto es OFF Chile. */
    private const val PARSE_USDA_SHARE = 0.30f

    /** Cada cuántas líneas un CSV comprueba la cancelación y publica su avance. */
    private const val CSV_CHECK_EVERY = 256

    /** Tamaños esperados para reservar listas de una vez: ~436 Foundation y ~5,4 mil filas OFF aceptadas. */
    private const val FOUNDATION_EXPECTED_FOODS = 512
    private const val OFF_EXPECTED_ROWS = 6_000

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
     * dataset importado es el mismo, así que se adopta la huella vigente en vez de re-importar ~72 MB (el análisis tarda
     * varios segundos y el commit bloquea brevemente las escrituras de Room mientras la app ya es usable). Solo
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
     *
     * WP-S8: ya no es UNA transacción larga. Los ~72 MB de CSV se analizan en [Dispatchers.Default] sin tocar la base
     * (el progreso va de 0,05 a 0,95) y solo después una transacción corta de Room reemplaza las filas ([commitRows]).
     * Mientras se analiza, el resto de la app escribe en Room sin esperar el lock del import.
     */
    suspend fun importIfNeeded(
        db: KpknDatabase,
        context: Context,
        alreadyImported: Boolean,
        existingMeta: ImportMetadata?,
        onMetaUpdated: (ImportMetadata) -> Unit,
    ): Boolean = runImport(db, alreadyImported, existingMeta, onMetaUpdated) { onProgress ->
        // Solo documenta el contenido importado; ya no decide nada ni corre en el arranque normal.
        val contentSha256 = runCatching { computeDatasetChecksum(context) }.getOrNull()
        android.util.Log.i(TAG, "Importando catálogo v$DATA_VERSION (sha256 de los CSV: ${contentSha256 ?: "no disponible"})")
        parseCatalog({ path -> context.assets.open(path) }, onProgress)
    }

    /**
     * Ciclo completo de una importación, sin acoplarse a los assets: compuerta, análisis con [parse] (sin lock sobre la
     * base), [commitRows], meta y telemetría. Atómica: un fallo de análisis no abre transacción y un fallo del commit
     * hace rollback, así que el catálogo anterior sigue entero; la meta se persiste solo al final. `onProgress` de
     * [parse] recibe la fracción 0..1 del análisis, que aquí ocupa la banda 0,05..0,95 de [importProgress].
     */
    internal suspend fun runImport(
        db: KpknDatabase,
        alreadyImported: Boolean,
        existingMeta: ImportMetadata?,
        onMetaUpdated: (ImportMetadata) -> Unit,
        dao: NutritionDao = db.nutritionDao(),
        parse: suspend (onProgress: (Float) -> Unit) -> List<GlobalFoodEntity>,
    ): Boolean = withContext(Dispatchers.IO) {
        val fingerprint = datasetFingerprint()
        if (!shouldImport(alreadyImported, adoptLegacyChecksum(existingMeta, fingerprint), fingerprint)) {
            return@withContext false
        }

        try {
            _importProgress.value = PROGRESS_STARTED
            NutritionTelemetry.catalogImportStarted(DATA_VERSION.toString())
            val imported = runCatching {
                val rows = withContext(Dispatchers.Default) {
                    parse { fraction ->
                        publishProgress(PROGRESS_PARSE_START + (PROGRESS_PARSE_END - PROGRESS_PARSE_START) * fraction.coerceIn(0f, 1f))
                    }
                }
                publishProgress(PROGRESS_PARSE_END)
                commitRows(db, dao, rows)
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

    /** El progreso solo sube durante una importación: un valor menor que el ya publicado se ignora. */
    private fun publishProgress(value: Float) {
        _importProgress.update { current -> maxOf(current ?: 0f, value.coerceIn(0f, 1f)) }
    }

    /**
     * Analiza TODO el catálogo embebido (USDA + OFF Chile) sin tocar la base. `onProgress` recibe la fracción 0..1 del
     * análisis completo: USDA ocupa el primer [PARSE_USDA_SHARE] y OFF Chile el resto.
     */
    private suspend fun parseCatalog(openAsset: (String) -> InputStream, onProgress: (Float) -> Unit): List<GlobalFoodEntity> {
        val started = System.nanoTime()
        android.util.Log.d(TAG, "Importando USDA...")
        val usda = parseUsda(openAsset) { onProgress(PARSE_USDA_SHARE * it) }
        android.util.Log.d(TAG, "Importando OpenFoodFacts Chile...")
        val off = parseOff(openAsset) { onProgress(PARSE_USDA_SHARE + (1f - PARSE_USDA_SHARE) * it) }
        android.util.Log.d(TAG, "Catálogo analizado: ${usda.size} USDA + ${off.size} OFF Chile en ${elapsedMs(started)} ms")
        return usda + off
    }

    /**
     * Reemplaza el catálogo global por [rows] en UNA transacción corta (solo la escritura de ~6 mil filas: el análisis
     * de los CSV ya terminó): lee los contadores de uso, vacía la tabla, inserta por lotes y restaura los contadores.
     * Si algo falla, Room hace rollback y las filas anteriores (con su uso) siguen intactas. El log `Catálogo
     * reemplazado` deja el tiempo real de la transacción en el dispositivo.
     *
     * Compromiso: los ids son estables (`usda_<fdcId>`, `off_<código>`), así que el uso sobrevive cuando la fila sigue
     * en el dataset nuevo; una fila que ya no está pierde sus contadores junto con ella.
     *
     * Después de la transacción reconstruye el índice FTS: `insertGlobalFoods` usa REPLACE y, con `recursive_triggers`
     * apagado, un REPLACE sobre un `foodId` existente salta el trigger de borrado y deja una entrada FTS huérfana. El
     * índice solo lo usa `searchGlobalFoodsWithFts` (la búsqueda de producción usa `searchGlobalFoodsNormalized`), así
     * que un fallo de la reconstrucción se registra pero no deshace un catálogo ya publicado.
     */
    internal suspend fun commitRows(db: KpknDatabase, dao: NutritionDao, rows: List<GlobalFoodEntity>) {
        // Un análisis vacío (CSV truncados o con otro formato) no debe vaciar el catálogo que sí funciona.
        check(rows.isNotEmpty()) { "El análisis del catálogo no produjo filas: se conserva el catálogo anterior" }
        val started = System.nanoTime()
        val keptUsage = db.withTransaction {
            val usage = dao.getGlobalFoodUsage()
            dao.clearGlobalFoods()
            rows.chunked(BATCH_SIZE).forEach { dao.insertGlobalFoods(it) }
            usage.forEach { dao.restoreGlobalFoodUsage(it.foodId, it.usageCount, it.lastUsedAt) }
            usage.size
        }
        val committed = System.nanoTime()
        rebuildFtsIndex(db)
        info("Catálogo reemplazado: ${rows.size} filas (uso de $keptUsage conservado) en ${elapsedMs(started, committed)} ms de transacción + ${elapsedMs(committed)} ms de índice FTS")
    }

    private fun elapsedMs(from: Long, to: Long = System.nanoTime()): Long = (to - from) / 1_000_000

    private fun rebuildFtsIndex(db: KpknDatabase) {
        try {
            db.openHelper.writableDatabase.execSQL("INSERT INTO `global_foods_fts`(`global_foods_fts`) VALUES('rebuild')")
        } catch (e: Exception) {
            warn("No se pudo reconstruir global_foods_fts; quedan entradas huérfanas hasta la próxima importación", e)
        }
    }

    // ─── Análisis de los CSV embebidos (sin base de datos: testeable sin Android) ──────────────────────

    /**
     * USDA Foundation como filas del catálogo. Lee `food.csv` PRIMERO para quedarse con los ids Foundation (~436) y así
     * saltar, sin reservar objetos, las filas de `food_nutrient.csv` de los ~14 mil alimentos que no lo son. [openAsset]
     * entrega un asset por su ruta (en producción `context.assets.open`). Los auxiliares (unidades, porciones,
     * categorías, alias en español) son opcionales: si faltan, el catálogo se importa igual sin ellos.
     * `onProgress` recibe la fracción 0..1 de este análisis.
     */
    internal suspend fun parseUsda(openAsset: (String) -> InputStream, onProgress: (Float) -> Unit = {}): List<GlobalFoodEntity> {
        val ctx = currentCoroutineContext()
        val foods = readCsv(ctx, openAsset, USDA_FOOD_CSV, report = { onProgress(0.40f * it) }) { parseUsdaFoundationFoods(it) }
        val foundationIds = foods.mapTo(HashSet(foods.size * 2)) { it.fdcId }
        val foodNutrients = readCsv(ctx, openAsset, USDA_NUTRIENT_CSV, report = { onProgress(0.40f + 0.55f * it) }) {
            parseUsdaNutrients(it, expectedFoods = foundationIds.size * 2, onlyFoodIds = foundationIds)
        }

        // Tablas auxiliares (WP-S9). Ninguna es imprescindible: si falta o no se puede leer, el catálogo USDA se importa
        // igual (sin porción, sin categoría o con el nombre en inglés) en vez de perderse entero.
        val measureUnits = readOptionalCsv(ctx, openAsset, USDA_MEASURE_UNIT_CSV, emptyMap<Int, String>()) { parseMeasureUnits(it) }
        // Porciones domésticas autoritativas de USDA (food_portion.csv): la primera porción declarada por ficha, ya
        // dividida por su cantidad. Sin esto, todo alimento global caería en el "100 g" genérico aunque la fuente
        // declare "1 breast = 174 g" (compuerta Fase 2).
        val authoritativePortions = readOptionalCsv(ctx, openAsset, USDA_PORTION_CSV, emptyMap<Int, UsdaPortion>()) {
            parseUsdaPortions(it, measureUnits)
        }
        val categories = readOptionalCsv(ctx, openAsset, USDA_CATEGORY_CSV, emptyMap<Int, String>()) { parseFoodCategories(it) }
        val spanishAliases = readOptionalCsv(ctx, openAsset, USDA_ALIASES_CSV, emptyMap<Int, UsdaAlias>()) { parseUsdaAliases(it) }

        val rows = ArrayList<GlobalFoodEntity>(foods.size)
        for (food in foods) {
            val nutrients = foodNutrients[food.fdcId] ?: continue
            val entity = usdaEntityOrNull(
                fdcId = food.fdcId,
                description = food.description,
                category = usdaCategory(food.categoryId, categories),
                nutrients = nutrients,
                portion = authoritativePortions[food.fdcId],
                alias = spanishAliases[food.fdcId],
            ) ?: continue
            rows.add(entity)
        }
        onProgress(1f)
        return rows
    }

    /** OpenFoodFacts Chile (TSV sin cabecera) como filas del catálogo: las líneas que [parseOffLine] rechaza se omiten. */
    internal suspend fun parseOff(openAsset: (String) -> InputStream, onProgress: (Float) -> Unit = {}): List<GlobalFoodEntity> {
        val ctx = currentCoroutineContext()
        return readCsv(ctx, openAsset, OFF_CHILE_CSV, hasHeader = false, report = onProgress) { lines ->
            val rows = ArrayList<GlobalFoodEntity>(OFF_EXPECTED_ROWS)
            for (line in lines) parseOffLine(line)?.let(rows::add)
            rows
        }
    }

    // OFF Chile CSV is TAB-separated with NO header row. Column positions (0-indexed, tab-separated):
    //   0   = code (barcode)
    //   10  = product_name
    //   18  = brands
    //   89  = energy-kcal_100g
    //   92  = fat_100g
    //   129 = carbohydrates_100g
    //   130 = sugars_100g
    //   146 = fiber_100g
    //   150 = proteins_100g
    //   156 = sodium_100g (in grams)
    private const val OFF_CODE = 0
    private const val OFF_NAME = 10
    private const val OFF_BRAND = 18
    private const val OFF_KCAL = 89
    private const val OFF_FAT = 92
    private const val OFF_CARBS = 129
    private const val OFF_SUGAR = 130
    private const val OFF_FIBER = 146
    private const val OFF_PROTEIN = 150
    private const val OFF_SODIUM = 156

    /** Una línea con kcal declaradas a más de esta fracción de 4P+4C+9G es un dato corrupto y se rechaza (no se marca). */
    private const val OFF_MAX_ENERGY_DEVIATION = 0.5

    /**
     * La ÚNICA vía de una línea de `off_chile.csv` a una fila del catálogo: la usan el importador, el corpus de búsqueda
     * y las pruebas de regresión, para que ninguno se desvíe del importador real. Devuelve null si la línea no entra:
     * columnas de menos, sin código o sin nombre, sodio ilegible, sin energía o macros, o energía declarada incoherente.
     */
    internal fun parseOffLine(line: String): GlobalFoodEntity? {
        val parts = parseTsvLine(line)
        if (parts.size <= OFF_SODIUM) return null

        val code = parts[OFF_CODE].trim()
        if (code.isBlank()) return null
        val rawName = parts[OFF_NAME].trim()
        if (rawName.isBlank()) return null
        val rawBrand = parts[OFF_BRAND].trim().takeIf { it.isNotBlank() }

        // Parse raw nutrition values and reject physically impossible/corrupt values.
        fun boundedValue(index: Int, max: Double): Double {
            return parts[index].toDoubleOrNull()
                ?.takeIf { it.isFinite() && it in 0.0..max }
                ?: 0.0
        }
        val rawKcal = boundedValue(OFF_KCAL, 1000.0)
        val rawProt = boundedValue(OFF_PROTEIN, 100.0)
        val rawFat = boundedValue(OFF_FAT, 100.0)
        val rawCarb = boundedValue(OFF_CARBS, 100.0)
        val rawFiber = boundedValue(OFF_FIBER, 100.0)
        val rawSugar = boundedValue(OFF_SUGAR, 100.0)
        val rawSodium = parts[OFF_SODIUM].toDoubleOrNull()
            ?.takeIf { it.isFinite() && it in 0.0..5.0 }
        if (parts[OFF_SODIUM].isNotBlank() && rawSodium == null) return null

        val macroEnergy = rawProt * 4.0 + rawFat * 9.0 + rawCarb * 4.0
        val hasRawNutrition = rawKcal > 0.0 && macroEnergy > 0.0
        if (!hasRawNutrition) return null
        val energyDeviation = kotlin.math.abs(rawKcal - macroEnergy) / macroEnergy
        if (energyDeviation > OFF_MAX_ENERGY_DEVIATION) return null

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

        return GlobalFoodEntity(
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

    /** Fila Foundation de `food.csv`: lo único que hace falta para decidir qué filas de `food_nutrient.csv` se leen. */
    internal class UsdaFoodRow(val fdcId: Int, val description: String, val categoryId: String?)

    /** `food.csv` (fdc_id, data_type, description, food_category_id, ...; sin la cabecera) -> solo los `foundation_food`. */
    internal fun parseUsdaFoundationFoods(lines: Sequence<String>): List<UsdaFoodRow> {
        val rows = ArrayList<UsdaFoodRow>(FOUNDATION_EXPECTED_FOODS)
        for (line in lines) {
            val parts = parseCsvLine(line)
            if (parts.size < 3) continue
            val fdcId = parts[0].toIntOrNull() ?: continue
            val dataType = parts[1].trim('"')
            if (dataType != "foundation_food") continue
            rows.add(UsdaFoodRow(fdcId, parts[2], parts.getOrNull(3)))
        }
        return rows
    }

    /**
     * `food_nutrient.csv` (id, fdc_id, nutrient_id, amount, ...; sin la cabecera) -> nutrientes por alimento. Las filas
     * de nutrientes que el catálogo no usa, o sin cantidad legible, no crean entrada. Con [onlyFoodIds] las filas de
     * cualquier otro alimento se saltan ANTES de reservar nada (WP-S8): de ~14 mil alimentos con nutrientes a los ~436 Foundation.
     */
    internal fun parseUsdaNutrients(
        lines: Sequence<String>,
        expectedFoods: Int = 120_000,
        onlyFoodIds: Set<Int>? = null,
    ): HashMap<Int, UsdaNutrients> {
        val byFood = HashMap<Int, UsdaNutrients>(expectedFoods)
        for (line in lines) {
            val parts = parseCsvLine(line)
            if (parts.size < 4) continue
            val fdcId = parts[1].toIntOrNull() ?: continue
            if (onlyFoodIds != null && fdcId !in onlyFoodIds) continue
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

    /**
     * Abre [path] con [openAsset], salta la cabecera si [hasHeader] y entrega las líneas a [parse]. Cada
     * [CSV_CHECK_EVERY] líneas comprueba la cancelación de [ctx] y publica en [report] el avance por bytes (los
     * caracteres leídos sobre los bytes que el flujo declaraba al abrirse); sin tamaño conocido solo publica el 1,0 final.
     */
    private fun <T> readCsv(
        ctx: CoroutineContext,
        openAsset: (String) -> InputStream,
        path: String,
        hasHeader: Boolean = true,
        report: (Float) -> Unit = {},
        parse: (Sequence<String>) -> T,
    ): T = openAsset(path).use { stream ->
        val totalBytes = stream.available().toLong()
        val reader = stream.bufferedReader()
        if (hasHeader) reader.readLine()
        var consumed = 0L
        var lineNumber = 0
        val lines = reader.lineSequence().onEach { line ->
            consumed += line.length + 1
            if (++lineNumber % CSV_CHECK_EVERY == 0) {
                ctx.ensureActive()
                if (totalBytes > 0) report((consumed.toFloat() / totalBytes).coerceIn(0f, 1f))
            }
        }
        val result = parse(lines)
        report(1f)
        result
    }

    /** Lee un CSV auxiliar (con cabecera); si no existe o no se puede leer devuelve [fallback] y el import sigue. */
    private fun <T> readOptionalCsv(
        ctx: CoroutineContext,
        openAsset: (String) -> InputStream,
        path: String,
        fallback: T,
        parse: (Sequence<String>) -> T,
    ): T = try {
        readCsv(ctx, openAsset, path, parse = parse)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        warn("$path no disponible: se importa sin él", e)
        fallback
    }

    /** `android.util.Log` lanza "not mocked" en las pruebas JVM puras: un log nunca debe romper el análisis. */
    private fun warn(message: String, error: Throwable) {
        try {
            android.util.Log.w(TAG, message, error)
        } catch (_: RuntimeException) {
            // Sin Android no hay dónde registrar; el fallback ya cubre el caso.
        }
    }

    private fun info(message: String) {
        try {
            android.util.Log.i(TAG, message)
        } catch (_: RuntimeException) {
            // Ver warn.
        }
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
