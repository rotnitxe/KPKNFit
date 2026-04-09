package com.example.kpkn.data.localai

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * LocalAiManager — Singleton for on-device Gemma 4 E2B nutrition parsing via LiteRT-LM.
 *
 * Model: gemma-4-E2B-it.litertlm (~2.58 GB)
 * SDK: com.google.ai.edge.litertlm:litertlm-android:0.10.1
 *
 * Model loading priority:
 *   1. filesDir/models/ — cached copy (fast path)
 *   2. getExternalFilesDir/models/ — placed via ADB push or in-app download
 *   3. assets/ — bundled in APK (only viable for dev builds)
 *
 * ADB push example for testing:
 *   adb push gemma-4-E2B-it.litertlm \
 *     /sdcard/Android/data/com.example.kpkn/files/models/
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
    /** Valores nutricionales por 100g que el usuario proporcionó explícitamente. El modelo debe usarlos exactamente. */
    val userHints: Map<String, Double> = emptyMap(),
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
private const val MODEL_NAME = "gemma-4-E2B-it.litertlm"
private val ASSET_CANDIDATES = listOf(
    "install-time-models/$MODEL_NAME",
    "install time models/$MODEL_NAME",
    "models/$MODEL_NAME",
    MODEL_NAME,
)
// Gemma 4 E2B is ~2.58 GB; require at least 500 MB to consider the file valid
private const val MIN_VALID_MODEL_BYTES = 500_000_000L
private const val INFERENCE_TIMEOUT_MS = 20_000L
private const val FILE_COPY_BUFFER = 65536
private const val MAX_JSON_LENGTH = 8192
private const val INIT_RETRIES = 3
private const val INIT_RETRY_DELAY_MS = 1500L

// ─── Singleton ───────────────────────────────────────────────────────────────

object LocalAiManager {

    private data class PreparedModelFile(
        val file: File,
        val loadedFromAssetPath: String? = null,
    )

    @Volatile private var engine: Engine? = null
    @Volatile private var status: LocalAiStatus = LocalAiStatus()
    @Volatile private var initialized = false
    @Volatile private var appCtx: Context? = null
    private val initMutex = Mutex()
    private val inferenceMutex = Mutex()

    // Progreso de copia del modelo: 0.0 = sin iniciar, 1.0 = completo
    private val _copyProgress = MutableStateFlow(0f)
    val copyProgress: StateFlow<Float> = _copyProgress.asStateFlow()

    // ─── Public API ──────────────────────────────────────────────────────────

    fun status(): LocalAiStatus = status

    /** Exposes the engine for use by LocalAiPhotoAnalyzer. Returns null if not initialized. */
    fun getEngine(): Engine? = engine

    /**
     * Exposes the JSON output parser for photo results.
     * Photo analyzer produces the same JSON format as text inference.
     */
    fun parseOutputForPhoto(output: String): List<LocalAiNutritionItem> =
        parseModelOutput(output, emptyList())

    suspend fun initialize(context: Context): LocalAiStatus {
        val appContext = context.applicationContext
        appCtx = appContext
        return withContext(Dispatchers.IO) {
            if (initialized && status.ready) {
                if (validateCachedEngine()) return@withContext status
                android.util.Log.w(TAG, "Engine validation failed, forcing re-init")
                initialized = false
            }
            initMutex.withLock {
                if (initialized && status.ready && validateCachedEngine()) return@withLock
                doInit(appContext)
            }
            status
        }
    }

    suspend fun ensureReady(context: Context, retries: Int = INIT_RETRIES): LocalAiStatus {
        val attempts = retries.coerceAtLeast(1)
        var currentStatus = LocalAiStatus()
        repeat(attempts) { attempt ->
            currentStatus = initialize(context)
            if (currentStatus.ready) return currentStatus
            if (attempt < attempts - 1) {
                android.util.Log.w(TAG, "Local AI no lista (intento ${attempt + 1}/$attempts): ${currentStatus.error}")
                delay(INIT_RETRY_DELAY_MS)
            }
        }
        return currentStatus
    }

    suspend fun ensureReady(retries: Int = INIT_RETRIES): LocalAiStatus {
        val context = appCtx ?: return status
        return ensureReady(context, retries)
    }

    suspend fun analyze(request: LocalAiNutritionRequest): LocalAiNutritionResult {
        return withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            if (engine == null && appCtx != null && (!initialized || !status.ready)) {
                ensureReady(appCtx!!, retries = 2)
            }

            return@withContext inferenceMutex.withLock {
                var activeEngine = engine
                if (activeEngine == null && appCtx != null) {
                    ensureReady(appCtx!!, retries = 2)
                    activeEngine = engine
                }

                if (activeEngine == null) {
                    return@withLock LocalAiNutritionResult(elapsedMs = System.currentTimeMillis() - start)
                }

                var result = withTimeoutOrNull(INFERENCE_TIMEOUT_MS) {
                    runInference(activeEngine, request)
                }

                if (result == null) {
                    android.util.Log.w(TAG, "Inference timeout (${INFERENCE_TIMEOUT_MS}ms) for: ${request.description.take(30)}")
                }

                if ((result == null || result.items.isEmpty()) && appCtx != null) {
                    android.util.Log.w(TAG, "Inference vacía, reintentando tras re-init")
                    ensureReady(appCtx!!, retries = 2)
                    val recovered = engine
                    if (recovered != null) {
                        result = withTimeoutOrNull(INFERENCE_TIMEOUT_MS) {
                            runInference(recovered, request)
                        }
                    }
                }

                (result ?: LocalAiNutritionResult()).copy(elapsedMs = System.currentTimeMillis() - start)
            }
        }
    }

    fun destroy() {
        try { engine?.close() } catch (_: Exception) {}
        engine = null
        initialized = false
        status = LocalAiStatus()
    }

    private fun validateCachedEngine(): Boolean {
        val current = status
        if (!current.ready) return false
        val path = current.modelPath ?: return false
        if (!File(path).exists()) {
            android.util.Log.w(TAG, "Cached model missing at $path")
            return false
        }
        return engine != null
    }

    // ─── Init ────────────────────────────────────────────────────────────────

    private fun doInit(context: Context) {
        try { engine?.close() } catch (_: Exception) {}
        engine = null

        val preparedModel = prepareModelFile(context)
        if (preparedModel == null) {
            status = LocalAiStatus(error = "modelo no encontrado — colócalo en getExternalFilesDir/models/ o en assets/")
            initialized = false
            android.util.Log.e(TAG, "Model not found. Push via: adb push $MODEL_NAME /sdcard/Android/data/com.example.kpkn/files/models/")
            return
        }

        val modelFile = preparedModel.file
        android.util.Log.d(TAG, "Model ready: ${modelFile.absolutePath} (${modelFile.length() / 1024 / 1024} MB)")

        // Try GPU first (faster inference), fall back to CPU if unavailable
        val backends: List<Backend> = buildList {
            try { add(Backend.GPU()) } catch (_: Exception) {
                android.util.Log.d(TAG, "GPU backend not available, using CPU only")
            }
            add(Backend.CPU())
        }

        var loaded = false
        var lastError: Throwable? = null

        outer@ for ((backendIndex, backend) in backends.withIndex()) {
            val backendName = if (backendIndex == 0 && backends.size > 1) "GPU" else "CPU"
            val attemptsForBackend = if (backendName == "GPU") 1 else INIT_RETRIES
            repeat(attemptsForBackend) { attempt ->
                if (loaded) return@repeat
                try {
                    android.util.Log.d(TAG, "Loading model [$backendName] attempt=${attempt + 1} abi=${android.os.Build.SUPPORTED_ABIS.firstOrNull()}")
                    val engineConfig = EngineConfig(
                        modelPath = modelFile.absolutePath,
                        backend = backend,
                    )
                    val eng = Engine(engineConfig)
                    eng.initialize()
                    engine = eng
                    status = LocalAiStatus(
                        ready = true,
                        modelVersion = "gemma-4-E2B ($backendName)",
                        modelPath = modelFile.absolutePath,
                        modelSizeBytes = modelFile.length(),
                        loadedFromAssetPath = preparedModel.loadedFromAssetPath,
                    )
                    android.util.Log.d(TAG, "Gemma 4 E2B loaded OK [$backendName] · ${modelFile.length() / 1024 / 1024} MB")
                    loaded = true
                } catch (t: Throwable) {
                    lastError = t
                    android.util.Log.e(TAG, "Model load [$backendName] attempt=${attempt + 1} FAILED: ${t.javaClass.simpleName}: ${t.message}")
                    try { engine?.close() } catch (_: Exception) {}
                    engine = null
                }
            }
            if (loaded) break@outer
        }

        if (!loaded) {
            val abi = android.os.Build.SUPPORTED_ABIS.joinToString(", ")
            status = LocalAiStatus(
                error = "Error al cargar Gemma 4 [$abi]: ${lastError?.message}",
                modelPath = modelFile.absolutePath,
                modelSizeBytes = modelFile.length(),
                loadedFromAssetPath = preparedModel.loadedFromAssetPath,
            )
            android.util.Log.e(TAG, "Model failed after $INIT_RETRIES attempts. ABI=$abi", lastError)
        }
        initialized = status.ready
    }

    /**
     * Model file lookup order:
     * 1. filesDir/models/ (already cached from previous run)
     * 2. getExternalFilesDir/models/ (placed manually or downloaded by app)
     * 3. assets/ (only for dev builds — 2.58 GB won't fit in Play Store APK)
     */
    private fun prepareModelFile(context: Context): PreparedModelFile? {
        // 1. Internal cache
        val internalDir = File(context.filesDir, "models")
        val internalTarget = File(internalDir, MODEL_NAME)
        if (internalTarget.exists() && internalTarget.length() >= MIN_VALID_MODEL_BYTES) {
            android.util.Log.d(TAG, "Model in internal cache: ${internalTarget.length() / 1024 / 1024} MB")
            return PreparedModelFile(file = internalTarget)
        }

        // 2. External files dir (ADB push or in-app download)
        val externalDir = context.getExternalFilesDir(null)?.let { File(it, "models") }
        if (externalDir != null) {
            val externalModel = File(externalDir, MODEL_NAME)
            if (externalModel.exists() && externalModel.length() >= MIN_VALID_MODEL_BYTES) {
                android.util.Log.d(TAG, "Model found in external storage: ${externalModel.length() / 1024 / 1024} MB")
                return PreparedModelFile(file = externalModel)
            }
        }

        // 3. Copy from assets (dev/testing only)
        return copyFromAssets(context, internalDir, internalTarget)
    }

    private fun copyFromAssets(context: Context, dir: File, target: File): PreparedModelFile? {
        if (target.exists()) {
            try { target.delete() } catch (_: Exception) {}
        }
        dir.mkdirs()

        val candidates = discoverAssetCandidates(context)
        for (assetPath in candidates) {
            try {
                // Obtener tamaño total para calcular progreso
                val assetFd = try { context.assets.openFd(assetPath) } catch (_: Exception) { null }
                val totalBytes = assetFd?.length?.takeIf { it > 0 } ?: MIN_VALID_MODEL_BYTES
                assetFd?.close()

                _copyProgress.value = 0f
                context.assets.open(assetPath).use { input ->
                    FileOutputStream(target).use { output ->
                        val buf = ByteArray(FILE_COPY_BUFFER)
                        var total = 0L
                        var read: Int
                        var lastReportedPct = 0
                        while (input.read(buf).also { read = it } != -1) {
                            output.write(buf, 0, read)
                            total += read
                            val pct = ((total.toDouble() / totalBytes) * 100).toInt().coerceIn(0, 99)
                            if (pct > lastReportedPct) {
                                lastReportedPct = pct
                                _copyProgress.value = pct / 100f
                            }
                        }
                        android.util.Log.d(TAG, "Model copied from $assetPath: ${total / 1024 / 1024} MB")
                    }
                }
                if (!target.exists() || target.length() < MIN_VALID_MODEL_BYTES) {
                    target.delete()
                    _copyProgress.value = 0f
                    throw IllegalStateException("Copia incompleta desde $assetPath (${target.length()} bytes)")
                }
                _copyProgress.value = 1f
                return PreparedModelFile(file = target, loadedFromAssetPath = assetPath)
            } catch (e: IOException) {
                android.util.Log.d(TAG, "Asset not found: $assetPath")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error copying from $assetPath", e)
                try { target.delete() } catch (_: Exception) {}
                _copyProgress.value = 0f
            }
        }
        return null
    }

    private fun discoverAssetCandidates(context: Context): List<String> {
        val candidates = linkedSetOf<String>()
        candidates += ASSET_CANDIDATES
        val roots = listOf("", "install time models", "install-time-models", "models")
        for (root in roots) {
            try {
                val entries = if (root.isEmpty()) context.assets.list("") else context.assets.list(root)
                entries?.filter { it.endsWith(".litertlm", ignoreCase = true) }
                    ?.forEach { name -> candidates += if (root.isEmpty()) name else "$root/$name" }
            } catch (_: Exception) {}
        }
        return candidates.toList()
    }

    // ─── Inference ───────────────────────────────────────────────────────────

    private suspend fun runInference(eng: Engine, request: LocalAiNutritionRequest): LocalAiNutritionResult {
        android.util.Log.d(TAG, "Running inference for: ${request.description.take(40)}")

        val systemInstruction = buildSystemInstruction(request.knownFoods)
        val userMessage = buildUserMessage(request.description, request.userHints)

        val raw = try {
            val convConfig = ConversationConfig(
                systemInstruction = Contents.of(systemInstruction),
            )
            eng.createConversation(convConfig).use { conversation ->
                conversation.sendMessage(Contents.of(userMessage))?.toString() ?: ""
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "generateResponse failed", e)
            return LocalAiNutritionResult()
        }

        android.util.Log.d(TAG, "Raw output (${raw.length} chars): ${raw.take(120)}")

        val items = parseModelOutput(raw, request.knownFoods)
        android.util.Log.d(TAG, "Parsed ${items.size} items")

        return LocalAiNutritionResult(
            items = items,
            overallConfidence = if (items.isEmpty()) 0.0 else 0.88,
            modelVersion = status.modelVersion,
            usedModel = true,
        )
    }

    // ─── Prompt Engineering ──────────────────────────────────────────────────

    /**
     * Builds the user message. If the user provided explicit macro hints, they are injected
     * as a mandatory constraint block AFTER the description so the model cannot ignore them.
     */
    private fun buildUserMessage(description: String, userHints: Map<String, Double>): String {
        if (userHints.isEmpty()) return description
        val labelMap = mapOf(
            "calories" to "calories (kcal)",
            "protein"  to "protein (g)",
            "carbs"    to "carbs (g)",
            "fats"     to "fats (g)",
        )
        return buildString {
            append(description)
            append("\n\n[DATOS NUTRICIONALES OBLIGATORIOS por 100g — copia estos valores EXACTOS en nutritionPer100g:]")
            userHints.forEach { (key, value) ->
                append("\n- ${labelMap[key] ?: key}: $value")
            }
        }
    }

    private fun buildSystemInstruction(knownFoods: List<String>): String {
        // Only include foods not already covered by the reference table
        val tableNames = setOf(
            "pollo", "pechuga", "arroz", "huevo", "clara", "pan", "marraqueta", "molde", "integral",
            "avena", "leche", "yogurt", "griego", "palta", "aguacate", "lentejas", "frejoles", "frijoles",
            "garbanzos", "quinoa", "salmon", "atun", "carne", "vacuno", "papa", "camote", "batata",
            "platano", "banana", "manzana", "naranja", "aceite", "mantequilla", "whey", "queso",
        )
        val extraFoods = knownFoods
            .filter { food -> tableNames.none { food.lowercase().contains(it) } }
            .take(20)
            .joinToString(", ")

        return """
Eres un parser nutricional especializado en gastronomía chilena y latinoamericana.
Responde EXCLUSIVAMENTE con JSON puro. Sin texto previo, sin markdown, sin explicaciones.

═══ PRIORIDADES (orden estricto, no negociable) ═══
P1 [OBLIGATORIO] Si el mensaje contiene un bloque "[DATOS NUTRICIONALES OBLIGATORIOS]",
   transcribe esos valores EXACTAMENTE en nutritionPer100g. Nunca los modifiques.
P2 [OBLIGATORIO] Si el usuario menciona macros explícitos en texto ("45g de proteína",
   "350 kcal por 100g"), úsalos exactamente. No los ignores.
P3 Para alimentos de la TABLA DE REFERENCIA → usa sus valores exactos.
P4 Para el resto → usa tu conocimiento nutricional. confidence máximo: 0.75.
P5 Verifica coherencia: calories ≈ (protein×4) + (carbs×4) + (fats×9) ± 10%.
   Si difiere más, recalcula calories para que cuadre.

═══ TABLA DE REFERENCIA — valores por 100g (P:proteína C:carbos G:grasas) ═══
pollo pechuga cocida    | 165kcal | P31  C0   G3.6
pollo pechuga cruda     | 120kcal | P22  C0   G2.6
pollo muslo cocido      | 209kcal | P26  C0   G11
arroz blanco cocido     | 130kcal | P2.7 C28  G0.3
arroz integral cocido   | 123kcal | P2.6 C26  G1.0
huevo entero            | 155kcal | P13  C1.1 G11
clara de huevo          |  52kcal | P11  C0.7 G0.2
pan marraqueta          | 280kcal | P9   C57  G1.5
pan molde blanco        | 265kcal | P9   C49  G3.2
pan molde integral      | 247kcal | P9   C45  G3.4
avena hojuelas cruda    | 379kcal | P13  C67  G6.9
avena cocida agua       |  68kcal | P2.4 C12  G1.4
leche entera            |  61kcal | P3.2 C4.8 G3.3
leche descremada        |  35kcal | P3.4 C5.0 G0.1
yogurt natural          |  59kcal | P3.5 C7.0 G1.5
yogurt griego natural   | 100kcal | P10  C3.6 G5.0
palta/aguacate          | 160kcal | P2.0 C9.0 G15
lentejas cocidas        | 116kcal | P9.0 C20  G0.4
frejoles negros cocidos | 132kcal | P8.9 C24  G0.5
garbanzos cocidos       | 164kcal | P8.9 C27  G2.6
quinoa cocida           | 120kcal | P4.4 C21  G1.9
salmon cocido           | 208kcal | P20  C0   G13
atun en agua escurrido  | 116kcal | P26  C0   G0.5
carne molida vacuno 20% | 218kcal | P26  C0   G12
carne molida vacuno 5%  | 137kcal | P22  C0   G5.0
papa/patata cocida      |  87kcal | P1.9 C20  G0.1
camote/batata cocido    |  90kcal | P2.0 C21  G0.1
platano/banana          |  89kcal | P1.1 C23  G0.3
manzana                 |  52kcal | P0.3 C14  G0.2
naranja                 |  47kcal | P0.9 C12  G0.1
aceite de oliva         | 884kcal | P0   C0   G100
mantequilla             | 717kcal | P0.9 C0.1 G81
queso fresco/cottage    | 264kcal | P18  C2.0 G21
whey protein isolate    | 370kcal | P85  C5.0 G2.0

═══ PORCIONES COMUNES ═══
1 taza arroz cocido=185g | 1 taza avena cocida=240g | 1 huevo grande=50g
1 cucharada aceite=14g | 1 marraqueta=90g | 1 rebanada pan molde=30g
media palta=75g | 1 plátano mediano=120g | 1 taza leche=240g

═══ PREPARATION — REGLA CRÍTICA ═══
preparation: null ES EL VALOR POR DEFECTO. Solo usa un método cuando el usuario lo diga EXPLÍCITAMENTE.
Palabras que activan preparation: "a la plancha", "frito", "al horno", "horneado", "cocido", "crudo", "apanado".
Estos alimentos SIEMPRE tienen preparation: null, sin excepción:
  lácteos (yogurt, leche, queso), frutas, suplementos (whey, creatina),
  cereales secos (avena cruda, granola), bebidas, productos envasados,
  salsas, condimentos, verduras sin indicación de cocción.

═══ CONFIDENCE ═══
0.97 = alimento en tabla de referencia con cantidad exacta
0.85 = alimento en tabla sin cantidad, o cantidad estimada por porción
0.70 = alimento estimado con tu conocimiento
0.50 = alimento compuesto o preparación desconocida
0.30 = no identificas el alimento → reviewRequired: true

═══ FORMATO (responde SOLO con este JSON) ═══
{"items":[{"rawText":"","canonicalName":"","grams":null,"quantity":1,"preparation":null,"confidence":0.9,"nutritionPer100g":{"calories":0,"protein":0,"carbs":0,"fats":0},"reviewRequired":false}]}

═══ EJEMPLOS ═══
Input: "200g de pechuga a la plancha, media taza de arroz y un yogurt Soprole"
Output: {"items":[{"rawText":"200g de pechuga a la plancha","canonicalName":"pollo pechuga cocida","grams":200,"quantity":1,"preparation":"plancha","confidence":0.97,"nutritionPer100g":{"calories":165,"protein":31,"carbs":0,"fats":3.6},"reviewRequired":false},{"rawText":"media taza de arroz","canonicalName":"arroz blanco cocido","grams":93,"quantity":1,"preparation":null,"confidence":0.85,"nutritionPer100g":{"calories":130,"protein":2.7,"carbs":28,"fats":0.3},"reviewRequired":false},{"rawText":"un yogurt Soprole","canonicalName":"yogurt natural","grams":null,"quantity":1,"preparation":null,"confidence":0.85,"nutritionPer100g":{"calories":59,"protein":3.5,"carbs":7.0,"fats":1.5},"reviewRequired":false}]}

Input: "3 huevos revueltos con una cucharada de mantequilla y un vaso de leche entera"
Output: {"items":[{"rawText":"3 huevos revueltos","canonicalName":"huevo entero","grams":150,"quantity":3,"preparation":"cocido","confidence":0.97,"nutritionPer100g":{"calories":155,"protein":13,"carbs":1.1,"fats":11},"reviewRequired":false},{"rawText":"una cucharada de mantequilla","canonicalName":"mantequilla","grams":14,"quantity":1,"preparation":null,"confidence":0.97,"nutritionPer100g":{"calories":717,"protein":0.9,"carbs":0.1,"fats":81},"reviewRequired":false},{"rawText":"un vaso de leche entera","canonicalName":"leche entera","grams":240,"quantity":1,"preparation":null,"confidence":0.97,"nutritionPer100g":{"calories":61,"protein":3.2,"carbs":4.8,"fats":3.3},"reviewRequired":false}]}
${if (extraFoods.isNotBlank()) "\nALIMENTOS ADICIONALES: $extraFoods" else ""}
        """.trimIndent()
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

        for (i in start until minOf(output.length, start + MAX_JSON_LENGTH)) {
            val c = output[i]
            if (escape) { escape = false; continue }
            if (c == '\\' && inString) { escape = true; continue }
            if (c == '"') { inString = !inString; continue }
            if (inString) continue
            when (c) {
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return output.substring(start, i + 1) }
            }
        }
        return null
    }

    private fun parseItemsFromJson(json: String, knownFoods: List<String>): List<LocalAiNutritionItem> {
        val itemsKey = json.indexOf("\"items\"")
        if (itemsKey < 0) return emptyList()
        val arrStart = json.indexOf('[', itemsKey)
        if (arrStart < 0) return emptyList()

        val result = mutableListOf<LocalAiNutritionItem>()
        var pos = arrStart + 1

        while (pos < json.length) {
            while (pos < json.length && (json[pos].isWhitespace() || json[pos] == ',')) pos++
            if (pos >= json.length || json[pos] == ']') break

            val objStart = json.indexOf('{', pos)
            if (objStart < 0) break
            val objEnd = findBalancedClose(json, objStart)
            if (objEnd < 0) break

            parseItem(json.substring(objStart, objEnd + 1), knownFoods)?.let { result.add(it) }
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
        val nutrition = nutrObj?.let {
            NutritionPer100g(
                calories = dbl(it, "calories")?.coerceIn(0.0, 900.0) ?: 0.0,
                protein = dbl(it, "protein")?.coerceIn(0.0, 100.0) ?: 0.0,
                carbs = dbl(it, "carbs")?.coerceIn(0.0, 100.0) ?: 0.0,
                fats = dbl(it, "fats")?.coerceIn(0.0, 100.0) ?: 0.0,
            )
        }

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

    private fun str(json: String, key: String): String? =
        """"$key"\s*:\s*"((?:[^"\\]|\\.)*)"""".toRegex().find(json)?.groupValues?.get(1)

    private fun dbl(json: String, key: String): Double? =
        """"$key"\s*:\s*(-?[\d.]+(?:[eE][+-]?\d+)?)""".toRegex().find(json)?.groupValues?.get(1)?.toDoubleOrNull()

    private fun int(json: String, key: String): Int? =
        """"$key"\s*:\s*(\d+)""".toRegex().find(json)?.groupValues?.get(1)?.toIntOrNull()

    private fun bool(json: String, key: String): Boolean? =
        """"$key"\s*:\s*(true|false)""".toRegex().find(json)?.groupValues?.get(1)?.toBoolean()

    private fun extractNestedObj(json: String, key: String): String? {
        val keyIdx = json.indexOf("\"$key\"")
        if (keyIdx < 0) return null
        val start = json.indexOf('{', json.indexOf(':', keyIdx))
        if (start < 0) return null
        val end = findBalancedClose(json, start)
        if (end < 0) return null
        return json.substring(start, end + 1)
    }
}
