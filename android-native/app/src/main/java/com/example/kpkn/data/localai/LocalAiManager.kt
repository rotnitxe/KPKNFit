package com.example.kpkn.data.localai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream

/**
 * LocalAiManager — Singleton for on-device FunctionGemma nutrition parsing.
 *
 * Design principles:
 * - Model always loaded if asset exists. No conditional "if available".
 * - Timeout-protected inference (max 8 seconds).
 * - Mutex for thread safety.
 * - Idempotent init. Safe to call multiple times.
 * - Heuristic fallback is delegated to domain/nutrition/FoodParser.kt.
 * - Structured logging via Log.d for debugging.
 */

// ─── Public Data Classes ─────────────────────────────────────────────────────

data class LocalAiStatus(
    val ready: Boolean = false,
    val modelVersion: String? = null,
    val error: String? = null,
    val modelPath: String? = null,
    val modelSizeBytes: Long? = null,
    val loadedFromAssetPath: String? = null,
)

data class LocalAiNutritionRequest(
    val description: String,
    val knownFoods: List<String> = emptyList(),
)

data class LocalAiNutritionItem(
    val rawText: String,
    val canonicalName: String,
    val grams: Double? = null,
    val quantity: Int? = null,
    val preparation: String? = null,
    val confidence: Double = 0.5,
    val nutritionPer100g: NutritionPer100g? = null,
    val reviewRequired: Boolean = false,
)

data class NutritionPer100g(
    val calories: Double,
    val protein: Double,
    val carbs: Double,
    val fats: Double,
)

data class LocalAiNutritionResult(
    val items: List<LocalAiNutritionItem> = emptyList(),
    val overallConfidence: Double = 0.0,
    val elapsedMs: Long = 0,
    val modelVersion: String? = null,
    val usedModel: Boolean = false,
)

// ─── Constants ───────────────────────────────────────────────────────────────

private const val TAG = "LocalAiManager"
private const val MODEL_NAME = "kpkn-food-fg270m-v1.litertlm"
private val ASSET_CANDIDATES = listOf(
    "install-time-models/$MODEL_NAME",
    "install time models/$MODEL_NAME",
    "models/$MODEL_NAME",
)
private const val MAX_TOKENS = 384
private const val TOP_K = 8
private const val TEMPERATURE = 0.05f
private const val INFERENCE_TIMEOUT_MS = 8000L
private const val FILE_COPY_BUFFER = 32768
private const val MAX_JSON_LENGTH = 8192
private const val INIT_RETRIES = 3

// ─── Singleton ───────────────────────────────────────────────────────────────

object LocalAiManager {

    private data class PreparedModelFile(
        val file: File,
        val loadedFromAssetPath: String? = null,
    )

    @Volatile private var engine: LlmInference? = null
    @Volatile private var status: LocalAiStatus = LocalAiStatus()
    @Volatile private var initialized = false
    @Volatile private var appCtx: Context? = null
    private val initMutex = Mutex()
    private val inferenceMutex = Mutex()

    // ─── Public API ──────────────────────────────────────────────────────────

    fun status(): LocalAiStatus = status

    /**
     * Idempotent initialization. Safe to call from any screen.
     * First call loads the model. Subsequent calls are no-ops.
     */
    suspend fun initialize(context: Context): LocalAiStatus {
        appCtx = context.applicationContext
        if (initialized && status.ready) return status
        initMutex.withLock {
            if (initialized && status.ready) return status
            doInit(context.applicationContext)
        }
        return status
    }

    /**
     * Run nutrition analysis. Thread-safe. Timeout-protected.
     * Returns null if model isn't ready or inference fails.
     */
    suspend fun analyze(request: LocalAiNutritionRequest): LocalAiNutritionResult {
        val start = System.currentTimeMillis()
        if (engine == null && appCtx != null && (!initialized || !status.ready)) {
            initialize(appCtx!!)
        }
        val eng = engine ?: return LocalAiNutritionResult(elapsedMs = System.currentTimeMillis() - start)

        return inferenceMutex.withLock {
            val result = withTimeoutOrNull(INFERENCE_TIMEOUT_MS) {
                runInference(eng, request)
            }

            if (result == null) {
                android.util.Log.w(TAG, "Inference timed out after ${INFERENCE_TIMEOUT_MS}ms for: ${request.description.take(30)}")
            }

            (result ?: LocalAiNutritionResult()).copy(elapsedMs = System.currentTimeMillis() - start)
        }
    }

    /**
     * Free resources. Call when app is destroyed.
     */
    fun destroy() {
        try { engine?.close() } catch (_: Exception) {}
        engine = null
        initialized = false
        status = LocalAiStatus()
    }

    // ─── Init ────────────────────────────────────────────────────────────────

    private fun doInit(context: Context) {
        try { engine?.close() } catch (_: Exception) {}
        engine = null

        val preparedModel = prepareModelFile(context)
        if (preparedModel == null) {
            status = LocalAiStatus(error = "Modelo no encontrado en assets: ${ASSET_CANDIDATES.joinToString()}")
            initialized = false
            android.util.Log.e(TAG, "Model file not found in candidates: ${ASSET_CANDIDATES.joinToString()}")
            return
        }

        val modelFile = preparedModel.file

        val opts = LlmInferenceOptions.builder()
            .setModelPath(modelFile.absolutePath)
            .setMaxTokens(MAX_TOKENS)
            .build()

        var loaded = false
        var lastError: Throwable? = null
        repeat(INIT_RETRIES) { attempt ->
            try {
                android.util.Log.d(TAG, "Loading model from ${modelFile.absolutePath} (${modelFile.length() / 1024 / 1024}MB), attempt=${attempt + 1}")
                engine = LlmInference.createFromOptions(context, opts)
                status = LocalAiStatus(
                    ready = true,
                    modelVersion = MODEL_NAME.removeSuffix(".litertlm"),
                    modelPath = modelFile.absolutePath,
                    modelSizeBytes = modelFile.length(),
                    loadedFromAssetPath = preparedModel.loadedFromAssetPath,
                )
                android.util.Log.d(TAG, "Model loaded successfully")
                loaded = true
                return@repeat
            } catch (t: Throwable) {
                lastError = t
                android.util.Log.e(TAG, "Failed to load model on attempt=${attempt + 1}", t)
                try { engine?.close() } catch (_: Exception) {}
                engine = null
            }
        }

        if (!loaded) {
            status = LocalAiStatus(
                error = "Error cargando modelo (${android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "abi-desconocida"}): ${lastError?.message}",
                modelPath = modelFile.absolutePath,
                modelSizeBytes = modelFile.length(),
                loadedFromAssetPath = preparedModel.loadedFromAssetPath,
            )
        }
        initialized = status.ready
    }

    /**
     * Copy model from asset pack to app-private storage.
     * MediaPipe requires a file path, not an asset stream.
     * Skips copy if file already exists with size > 0.
     */
    private fun prepareModelFile(context: Context): PreparedModelFile? {
        val dir = File(context.filesDir, "models")
        val target = File(dir, MODEL_NAME)

        if (target.exists() && target.length() > 100_000_000) {
            android.util.Log.d(TAG, "Model already cached: ${target.length() / 1024 / 1024}MB")
            return PreparedModelFile(file = target, loadedFromAssetPath = null)
        }

        dir.mkdirs()

        for (assetPath in ASSET_CANDIDATES) {
            try {
                context.assets.open(assetPath).use { input ->
                    FileOutputStream(target).use { output ->
                        val buf = ByteArray(FILE_COPY_BUFFER)
                        var read: Int
                        var total = 0L
                        while (input.read(buf).also { read = it } != -1) {
                            output.write(buf, 0, read)
                            total += read
                        }
                        android.util.Log.d(TAG, "Model copied from $assetPath: ${total / 1024 / 1024}MB")
                    }
                }
                if (!target.exists() || target.length() <= 100_000_000) {
                    target.delete()
                    throw IllegalStateException("Modelo copiado incompleto desde $assetPath (${target.length()} bytes)")
                }
                return PreparedModelFile(file = target, loadedFromAssetPath = assetPath)
            } catch (_: Exception) {
                android.util.Log.d(TAG, "Asset not found at $assetPath, trying next")
            }
        }

        target.delete()
        android.util.Log.e(TAG, "Model not found in any asset path: ${ASSET_CANDIDATES.joinToString()}")
        return null
    }

    // ─── Inference ───────────────────────────────────────────────────────────

    private fun runInference(eng: LlmInference, request: LocalAiNutritionRequest): LocalAiNutritionResult {
        val prompt = buildPrompt(request)
        android.util.Log.d(TAG, "Running inference for: ${request.description.take(40)}")

        val raw = try {
            eng.generateResponse(prompt)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Model generateResponse failed", e)
            return LocalAiNutritionResult()
        }

        android.util.Log.d(TAG, "Raw output (${raw.length} chars): ${raw.take(100)}")

        val items = parseModelOutput(raw, request.knownFoods)
        android.util.Log.d(TAG, "Parsed ${items.size} items from model output")

        return LocalAiNutritionResult(
            items = items,
            overallConfidence = if (items.isEmpty()) 0.0 else 0.85,
            modelVersion = status.modelVersion,
            usedModel = true,
        )
    }

    // ─── Prompt Engineering ──────────────────────────────────────────────────

    private fun buildPrompt(request: LocalAiNutritionRequest): String {
        val foods = request.knownFoods.take(40).joinToString(", ")
            .ifBlank { "pollo, arroz, huevo, pan, leche, carne, pescado, quinoa, lentejas, palta, avena, yogurt" }

        return """
Eres un analizador nutricional. Recibes una descripción de comida en español chileno y devuelves SOLO JSON válido.

REGLAS:
- Divide la descripción en items individuales.
- Para cada item extrae: nombre canónico, gramos (si hay), cantidad, método de cocina.
- Asigna calorías y macros por 100g basándose en valores nutricionales reales.
- Si no puedes identificar un alimento, usa "confidence": 0.3 y "reviewRequired": true.
- gramos: null si no se especifican. quantity: 1 por defecto.
- preparation: solo "plancha", "horno", "frito", "cocido", "crudo" o null.

FORMATO DE RESPUESTA (SOLO JSON, SIN TEXTO ADICIONAL):
{"items":[{"rawText":"...","canonicalName":"...","grams":200,"quantity":1,"preparation":"plancha","confidence":0.8,"nutritionPer100g":{"calories":165,"protein":31,"carbs":0,"fats":3.6},"reviewRequired":false}]}

ALIMENTOS CONOCIDOS: $foods

ENTRADA: "${request.description}"
RESPUESTA:""".trimIndent()
    }

    // ─── Output Parsing ──────────────────────────────────────────────────────

    private fun parseModelOutput(output: String, knownFoods: List<String>): List<LocalAiNutritionItem> {
        val json = extractJson(output) ?: return emptyList()
        return parseItemsFromJson(json, knownFoods)
    }

    private fun extractJson(output: String): String? {
        val start = output.indexOf('{')
        if (start < 0) return null

        var depth = 0
        var inString = false
        var escape = false
        var lastValidClose = -1

        for (i in start until minOf(output.length, start + MAX_JSON_LENGTH)) {
            val c = output[i]
            if (escape) { escape = false; continue }
            if (c == '\\' && inString) { escape = true; continue }
            if (c == '"') { inString = !inString; continue }
            if (inString) continue

            when (c) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return output.substring(start, i + 1)
                    if (depth > 0) lastValidClose = i
                }
            }
        }
        if (lastValidClose > 0) {
            return output.substring(start, lastValidClose + 1)
        }
        return null
    }

    private fun parseItemsFromJson(json: String, knownFoods: List<String>): List<LocalAiNutritionItem> {
        // Find "items" array
        val itemsKey = json.indexOf("\"items\"")
        if (itemsKey < 0) return emptyList()
        val arrStart = json.indexOf('[', itemsKey)
        if (arrStart < 0) return emptyList()

        val result = mutableListOf<LocalAiNutritionItem>()
        var pos = arrStart + 1

        while (pos < json.length) {
            // Skip whitespace and commas
            while (pos < json.length && json[pos].isWhitespace() || json[pos] == ',') pos++
            if (pos >= json.length || json[pos] == ']') break

            val objStart = json.indexOf('{', pos)
            if (objStart < 0) break

            val objEnd = findBalancedClose(json, objStart)
            if (objEnd < 0) break

            val obj = json.substring(objStart, objEnd + 1)
            parseItem(obj, knownFoods)?.let { result.add(it) }

            pos = objEnd + 1
        }
        return result
    }

    private fun findBalancedClose(json: String, start: Int): Int {
        var depth = 0
        var inString = false
        var escape = false
        for (i in start until json.length) {
            val c = json[i]
            if (escape) { escape = false; continue }
            if (c == '\\' && inString) { escape = true; continue }
            if (c == '"') { inString = !inString; continue }
            if (inString) continue
            when (c) {
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return i }
            }
        }
        return -1
    }

    private fun parseItem(obj: String, knownFoods: List<String>): LocalAiNutritionItem? {
        val rawText = str(obj, "rawText") ?: return null
        val canonicalName = str(obj, "canonicalName") ?: rawText
        val grams = dbl(obj, "grams")?.coerceIn(1.0, 5000.0)
        val qty = int(obj, "quantity")?.coerceIn(1, 50)
        val prep = str(obj, "preparation")
        val confidence = dbl(obj, "confidence")?.coerceIn(0.0, 1.0) ?: 0.7

        val nutrObj = extractNestedObj(obj, "nutritionPer100g")
        val nutrition = if (nutrObj != null) {
            NutritionPer100g(
                calories = dbl(nutrObj, "calories")?.coerceIn(0.0, 900.0) ?: 0.0,
                protein = dbl(nutrObj, "protein")?.coerceIn(0.0, 100.0) ?: 0.0,
                carbs = dbl(nutrObj, "carbs")?.coerceIn(0.0, 100.0) ?: 0.0,
                fats = dbl(nutrObj, "fats")?.coerceIn(0.0, 100.0) ?: 0.0,
            )
        } else null

        val reviewRequired = bool(obj, "reviewRequired")
            ?: (grams == null && knownFoods.none { canonicalName.contains(it, ignoreCase = true) })

        return LocalAiNutritionItem(
            rawText = rawText,
            canonicalName = canonicalName,
            grams = grams,
            quantity = qty ?: 1,
            preparation = prep,
            confidence = confidence,
            nutritionPer100g = nutrition,
            reviewRequired = reviewRequired,
        )
    }

    // ─── JSON Field Extractors ───────────────────────────────────────────────

    private fun str(json: String, key: String): String? {
        val pattern = """"$key"\s*:\s*"((?:[^"\\]|\\.)*)"""".toRegex()
        return pattern.find(json)?.groupValues?.get(1)
    }

    private fun dbl(json: String, key: String): Double? {
        val pattern = """"$key"\s*:\s*(-?[\d.]+(?:[eE][+-]?\d+)?)""".toRegex()
        return pattern.find(json)?.groupValues?.get(1)?.toDoubleOrNull()
    }

    private fun int(json: String, key: String): Int? {
        val pattern = """"$key"\s*:\s*(\d+)""".toRegex()
        return pattern.find(json)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun bool(json: String, key: String): Boolean? {
        val pattern = """"$key"\s*:\s*(true|false)""".toRegex()
        return pattern.find(json)?.groupValues?.get(1)?.toBoolean()
    }

    private fun extractNestedObj(json: String, key: String): String? {
        val keyIdx = json.indexOf("\"$key\"")
        if (keyIdx < 0) return null
        val colonIdx = json.indexOf(':', keyIdx + key.length + 2)
        if (colonIdx < 0) return null
        val start = json.indexOf('{', colonIdx)
        if (start < 0) return null
        val end = findBalancedClose(json, start)
        if (end < 0) return null
        return json.substring(start, end + 1)
    }
}
