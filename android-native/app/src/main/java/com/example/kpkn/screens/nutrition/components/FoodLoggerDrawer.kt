package com.example.kpkn.screens.nutrition.components

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.collectAsState
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.NutritionCalibrationRepository
import com.example.kpkn.data.models.*
import com.example.kpkn.data.food.findFoodExactByNormalized
import com.example.kpkn.domain.nutrition.HouseholdPortions
import com.example.kpkn.domain.nutrition.FoodLoggerPrimaryAction
import com.example.kpkn.data.diagnostics.KpknDiagnosticLogger
import com.example.kpkn.telemetry.nutrition.NutritionTelemetry
import com.example.kpkn.domain.nutrition.SmartFoodResolver
import com.example.kpkn.domain.nutrition.SubjectivePortionEngine
import com.example.kpkn.domain.nutrition.CookingStateResolver
import com.example.kpkn.domain.nutrition.scaleFoodByPortion
import com.example.kpkn.domain.nutrition.createLoggedFood
import com.example.kpkn.domain.nutrition.parseMealDescription
import com.example.kpkn.domain.nutrition.reconcileParsedFoodItems
import com.example.kpkn.domain.nutrition.FoodCombinationParser
import com.example.kpkn.domain.nutrition.round1
import com.example.kpkn.domain.nutrition.ContextDetector
import com.example.kpkn.domain.nutrition.FoodState
import com.example.kpkn.domain.nutrition.FoodIdentity
import com.example.kpkn.domain.nutrition.FoodResolutionStatus
import com.example.kpkn.domain.nutrition.NutritionSourceKind
import com.example.kpkn.domain.nutrition.getContextualDefaultServingSize
import com.example.kpkn.domain.nutrition.COOKING_FACTORS
import com.example.kpkn.domain.nutrition.SemanticPortionRetriever
import com.example.kpkn.domain.nutrition.LastResortSplitter
import com.example.kpkn.domain.nutrition.NutritionHeuristicEstimator
import com.example.kpkn.domain.nutrition.MacroValidator
import com.example.kpkn.domain.nutrition.TagResolver
import com.example.kpkn.domain.nutrition.NutritionCalibrationWizardEngine
import com.example.kpkn.domain.nutrition.FoodResolutionPort
import com.example.kpkn.domain.nutrition.ResolvedTag
import com.example.kpkn.domain.nutrition.mergeTagsPreservingManualEdits
import com.example.kpkn.domain.nutrition.absolutePortionOptions
import com.example.kpkn.domain.nutrition.hasMaterialQuestion
import com.example.kpkn.domain.nutrition.applyModifierScale
import com.example.kpkn.domain.nutrition.adjustLoggedFoodForOil
import com.example.kpkn.domain.nutrition.stripOilFromLoggedFood
import com.example.kpkn.domain.nutrition.scalingForIntent
import com.example.kpkn.domain.nutrition.oilGramsForLevel
import com.example.kpkn.ui.components.KpknSheet
import java.util.UUID
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.ui.components.KpknSnackbarBanner
import com.example.kpkn.ui.components.SnackbarType
import com.example.kpkn.ui.components.KpknAlertDialog

// ─── Constants ───────────────────────────────────────────────────────────────

private const val PREF_FILE = "kpkn_nutrition_prefs"
private const val PREF_ANALYSIS_MODE = "analysis_mode"
private const val MODE_BASIC = "BASIC"

private enum class ParseStage { INTERPRETING, ESTIMATING }

private val MEAL_OPTIONS = listOf(
    MealType.BREAKFAST to "Desayuno",
    MealType.LUNCH to "Almuerzo",
    MealType.DINNER to "Cena",
    MealType.SNACK to "Snack",
)

private val PROTEIN_COLOR = Color(0xFFB3261E)
private val CARBS_COLOR = Color(0xFF6750A4)
private val FATS_COLOR = Color(0xFF006A6A)
private val PRO_COLOR = Color(0xFF7C3AED)

/** CRI-ANALYSIS: fragmento corto y seguro (errorType: mensaje) para mostrar en avisos
 *  de fallo. On-device, truncado; ayuda a diagnosticar la causa raíz. Puro y top-level
 *  para poder referenciarlo desde cualquier función local. */
private fun technicalDetailOf(error: Throwable): String {
    val message = error.message?.take(160)?.replace('\n', ' ').orEmpty()
    return if (message.isBlank()) error.javaClass.simpleName else "${error.javaClass.simpleName}: $message"
}

private enum class AnalysisNoticeTone { INFO, WARNING }

private data class AnalysisNotice(
    val title: String,
    val message: String,
    val tone: AnalysisNoticeTone,
    /** CRI-ANALYSIS: detalle técnico de la excepción (errorType: mensaje), on-device.
     *  Truncado. Solo para diagnóstico; nunca texto crudo de comidas. */
    val technicalDetail: String? = null,
)

private fun shouldUseAiLoggedFood(item: ParsedMealItem): Boolean {
    return item.macroOverrides != null && (
        item.analysisSource == AnalysisSource.LOCAL_AI_ESTIMATE ||
            item.analysisSource == AnalysisSource.EXTERNAL_API_ESTIMATE
    )
}

private fun isOilTag(tag: String): Boolean {
    val lower = tag.lowercase().trim()
    return lower == "aceite" || lower == "aceite vegetal" || lower == "aceite de oliva" || lower == "aceite de maravilla" || lower == "aceite de girasol"
}

/**
 * Merges newly-parsed tags with existing tags that have manual edits.
 * - Matching by tag name (case-insensitive)
 * - If old tag has hasManualEdits=true, preserve it over the new tag
 * - Preserve old tags not present in new tags if they have manual edits
 */
// ─── Composable ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodLoggerDrawer(
    nutritionRepo: NutritionRepository,
    onSave: (NutritionLog) -> Unit,
    onDismiss: () -> Unit,
    isOpen: Boolean,
    foodDatabase: List<FoodItem>,
    initialDate: String,
    initialMealType: MealType,
    initialDescription: String? = null,
    initialTab: Int = 0,
) {
    val programRepo = ProgramRepository.getInstance()
    val settings by programRepo.settings.collectAsState()
    if (!isOpen) return

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREF_FILE, android.content.Context.MODE_PRIVATE) }

    var description by remember { mutableStateOf(initialDescription.orEmpty()) }
    var lastAnalyzedDescription by remember { mutableStateOf("") }
    var mealType by remember { mutableStateOf(initialMealType) }
    var logDate by remember { mutableStateOf(initialDate) }
    var tags by remember { mutableStateOf(emptyList<ResolvedTag>()) }
    var detectedContext by remember { mutableStateOf<ContextDetector.ContextResult?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf(emptyList<FoodCandidate>()) }
    var activeTab by remember { mutableIntStateOf(initialTab.coerceIn(0, 1)) }
    var showSuccess by remember { mutableStateOf(false) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var reviewRequired by remember { mutableStateOf(false) }
    var analysisStage by remember { mutableStateOf<ParseStage?>(null) }
    var analysisElapsedMs by remember { mutableStateOf(0L) }
    var analysisStartedAtMs by remember { mutableStateOf(0L) }
    var analysisNotice by remember { mutableStateOf<AnalysisNotice?>(null) }

    // E16/IT2: invalidación del aprendizaje desde la UI.
    var learnedMemoryCleared by remember { mutableStateOf(false) }

    // IT3: incertidumbre preservada como rango (referencia del dataset local).
    var analysisKcalRange by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    // Configuración general de comidas (medidas / aprendizaje)
    var showSettingsDialog by remember { mutableStateOf(false) }

    // IT3: utensilios configurables (ml por utensilio).
    var showUtensilDialog by remember { mutableStateOf(false) }
    var utensilValues by remember {
        mutableStateOf(
            SubjectivePortionEngine.UTENSIL_DEFAULTS.mapValues { (_, ml) -> ml.toFloat() },
        )
    }

    val listState = rememberLazyListState()
    val requestDismiss = {
        // Prevent accidental dismiss when there's content (description typed or foods added)
        if (description.isBlank() && tags.isEmpty()) {
            onDismiss()
        }
    }

    // Auto-scroll to show newly detected foods when analysis finishes
    LaunchedEffect(isAnalyzing) {
        if (!isAnalyzing && tags.isNotEmpty()) {
            val lastIndex = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
            listState.animateScrollToItem(lastIndex)
        }
    }

    LaunchedEffect(isAnalyzing, analysisStartedAtMs) {
        if (!isAnalyzing || analysisStartedAtMs == 0L) {
            analysisElapsedMs = 0L
            return@LaunchedEffect
        }
        while (isAnalyzing) {
            analysisElapsedMs = System.currentTimeMillis() - analysisStartedAtMs
            delay(900)
        }
    }

    // Mostrar diálogo de selección si es la primera vez en tab Descripción
    LaunchedEffect(activeTab) {
        if (activeTab == 0 && false) {
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    fun updateCalibrationProfile(update: (com.example.kpkn.data.models.NutritionCalibrationProfile) -> com.example.kpkn.data.models.NutritionCalibrationProfile) {
        scope.launch(Dispatchers.IO) {
            val repository = NutritionCalibrationRepository.getInstance(context)
            val current = repository.get() ?: com.example.kpkn.data.models.NutritionCalibrationProfile()
            repository.save(update(current))
        }
    }

    suspend fun resolveTags(parsed: ParsedMealDescription) {
        val calibrationProfile = NutritionCalibrationRepository.getInstance(context).get()
        val resolver = TagResolver(object : FoodResolutionPort {
            override suspend fun resolveSmart(
                tag: String,
                brandHint: String?,
                contextHint: String?,
                stateHint: FoodState?,
            ) = nutritionRepo.resolveFoodWithSmartResolver(tag, brandHint, contextHint, stateHint)

            override suspend fun getFoodById(id: String) = nutritionRepo.getFoodById(id)

            override suspend fun staticFood(tag: String): FoodItem? =
                HouseholdPortions.householdStaticFood(tag)

            override fun staticIsExact(tag: String): Boolean =
                findFoodExactByNormalized(tag) != null

            override fun recordLearned(
                query: String,
                brandHint: String?,
                foodId: String,
                portionGrams: Double?,
                cookingMethod: String?,
            ) {
                nutritionRepo.recordLearnedResolution(query, brandHint, foodId, portionGrams, cookingMethod)
            }
        }, calibrationProfile = calibrationProfile)

        val result = resolver.resolveAll(parsed, detectedContext, mealType)

        detectedContext = result.second
        val newTags = result.first
        // FIX NUT-04: key drawer state per request — don't keep stale tags from previous description
        // If description changed, old manual edits for hallulla shouldn't block marraqueta analysis.
        val isSameRequest = lastAnalyzedDescription.isNotBlank() &&
            FoodIdentity.normalize(parsed.rawDescription) == FoodIdentity.normalize(lastAnalyzedDescription)
        val mergedTags = if (isSameRequest || tags.isEmpty()) {
            mergeTagsPreservingManualEdits(tags, newTags)
        } else {
            // New description → fresh state, don't carry stale tags. Preserve only if tag still present.
            val oldByTag = tags.filter { it.hasManualEdits }.associateBy { FoodIdentity.normalize(it.tag) }
            newTags.map { newTag ->
                oldByTag[FoodIdentity.normalize(newTag.tag)] ?: newTag
            }
        }
        tags = mergedTags
        analysisKcalRange = null
        reviewRequired = mergedTags.any { it.hasMaterialQuestion() }
    }

    fun buildAnalysisNotice(parsed: ParsedMealDescription): AnalysisNotice? {        val engine = parsed.analysisEngine
        return when {
            engine == "local-ai-timeout" -> AnalysisNotice(
                title = "La lectura tardó más de lo esperado",
                message = "Completamos el registro con una estimación rápida para no frenarte. Si quieres, puedes ajustar los alimentos antes de guardar.",
                tone = AnalysisNoticeTone.WARNING,
            )
            engine == "local-ai-unavailable" -> AnalysisNotice(
                title = "Se usó una lectura alternativa",
                message = "Igualmente preparamos el registro para que puedas continuar y revisar los datos antes de guardar.",
                tone = AnalysisNoticeTone.WARNING,
            )
            engine == "local-ai-empty" -> AnalysisNotice(
                title = "Faltó contexto en la descripción",
                message = "Prueba con una descripción simple, por ejemplo alimento + cantidad, para obtener un registro más claro.",
                tone = AnalysisNoticeTone.INFO,
            )
            else -> null
        }
    }

    /** D7: aviso no-silencioso cuando el dataset de porciones no cargó. */
    fun datasetNotReadyNotice(): AnalysisNotice? {
        val status = SemanticPortionRetriever.status()
        return if (!status.ready) {
            AnalysisNotice(
                title = "Dataset de porciones no cargado",
                message = "Las porciones sugeridas pueden ser menos precisas. Reintenta; si persiste, avísanos.",
                tone = AnalysisNoticeTone.WARNING,
            )
        } else null
    }

    // CRASH-FIX: última red de seguridad. Cualquier fallo no capturado en el
    // pipeline de análisis se registra en NutriTelemetry y degrada a un aviso
    // visible en vez de tumbar el proceso.
    val analysisErrorHandler = CoroutineExceptionHandler { _, handlerError ->
        NutritionTelemetry.event(
            "analysis_coroutine_crash",
            mapOf(
                "errorType" to handlerError.javaClass.name,
                "message" to (handlerError.message ?: ""),
            ),
        )
        NutritionTelemetry.clearInFlight()
        analysisNotice = AnalysisNotice(
            title = "Error inesperado durante el análisis",
            message = "Puedes reintentarlo o registrar los alimentos manualmente.",
            tone = AnalysisNoticeTone.WARNING,
        )
        isAnalyzing = false
        analysisStage = null
        analysisStartedAtMs = 0L
    }

    /** CRI-ANALYSIS: último nivel del pipeline. No usa parser, dataset, resolver, Room
     *  ni telemetría: solo separa la descripción en fragmentos y crea tags para revisar.
     *  No puede lanzar (garantía del llamador vía runCatching). Devuelve si produjo tags.
     *  Declarado AQUÍ (antes de analyzeDescription) porque las funciones locales no se
     *  pueden referenciar antes de su declaración. */
    fun createLastResortManualTags(raw: String): Boolean {
        val fragments = LastResortSplitter.split(raw)
        val trimmed = raw.trim()
                // El último nivel muestra una estimación editable, pero exige una
                // confirmación explícita antes de guardarla y de alimentar aprendizaje.
        fun manualTag(fragment: String): ResolvedTag {
            val profile = runCatching {
                NutritionHeuristicEstimator.estimatePer100g(fragment)
            }.getOrNull()
            val logged = profile?.let {
                createLoggedFood(
                    foodName = "$fragment (estimado)",
                    amount = 100.0,
                    calories = it.calories,
                    protein = it.protein,
                    carbs = it.carbs,
                    fats = it.fats,
                )
            }
            return ResolvedTag(
                tag = fragment,
                portion = PortionPreset.MEDIUM,
                quantity = 1.0,
                amountGrams = logged?.amount,
                foodItem = null,
                loggedFood = logged,
                analysisSource = AnalysisSource.LOCAL_HEURISTIC,
                statusText = if (logged != null) {
                    "Estimación local. Tocá la tarjeta para editar."
                } else {
                    "No pude identificar el alimento."
                },
                isResolved = logged != null,
                isFuzzyMatch = true,
                resolutionStatus = if (logged != null) FoodResolutionStatus.AUTO else FoodResolutionStatus.NO_RESOLVED,
                nutritionSource = NutritionSourceKind.HEURISTIC_ESTIMATE,
                resolutionConfidence = 0.35,
                resolutionMargin = 0.0,
            )
        }
        if (fragments.isEmpty()) {
            if (trimmed.isEmpty()) return false
            tags = listOf(manualTag(trimmed))
            return true
        }
        tags = fragments.distinct().map(::manualTag)
        // La estimación queda explícitamente pendiente de revisión; Guardar se
        // mantiene bloqueado hasta que el usuario confirme o elija una ficha.
        return true
    }

    fun analyzeDescription() {
        if (description.isBlank() || isAnalyzing) return

        analysisNotice = null
        analysisKcalRange = null
        analysisStage = ParseStage.INTERPRETING
        analysisStartedAtMs = System.currentTimeMillis()
        analysisElapsedMs = 0L
        KpknDiagnosticLogger.event(
            namespace = "nutrition",
            name = "analysis_started",
            fields = mapOf(
                "descriptionLength" to description.length,
                "engine" to "local",
            ),
        )

        val descriptionSnapshot = description
        // NutriTelemetry: una traza por análisis, solo con métricas (sin texto).
        val analysisTrace = NutritionTelemetry.startTrace(
            source = if (!initialDescription.isNullOrBlank()) "shared" else "manual",
            fields = mapOf(
                "descriptionLength" to descriptionSnapshot.length,
                "engine" to "local",
            ),
        )
        NutritionTelemetry.markInFlight(analysisTrace.traceId, "start")

        isAnalyzing = true
        scope.launch(analysisErrorHandler) {
            var traceFinished = false
            fun endTraceOnce(outcome: String, fields: Map<String, Any?> = emptyMap()) {
                if (!traceFinished) {
                    traceFinished = true
                    NutritionTelemetry.clearInFlight()
                    analysisTrace.end(outcome, fields)
                }
            }
            try {
                detectedContext = analysisTrace.stage("context_detect") {
                    ContextDetector.detect(descriptionSnapshot, mealType)
                }
                NutritionTelemetry.markInFlight(analysisTrace.traceId, "template_match")
                // CRASH-FIX: el match de templates ejecuta regex costosos; antes corría
                // en el hilo principal fuera de cualquier try/catch → crash al pulsar.
                val templateMatch = analysisTrace.stage("template_match") {
                    nutritionRepo.findMealTemplateMatch(descriptionSnapshot)
                }
                if (templateMatch != null) {
                    tags = templateMatch.foods.map { food ->
                        val (foodItem, grams) = HouseholdPortions.eatenGramsForTemplateFood(
                            food,
                            descriptionSnapshot,
                        )
                        val logged = if (foodItem != null) {
                            scaleFoodByPortion(
                                food = foodItem,
                                quantity = food.quantity.coerceAtLeast(1.0),
                                portion = food.portionPreset ?: PortionPreset.MEDIUM,
                                amountGrams = grams,
                                cookingMethod = food.cookingMethod,
                            ).copy(analysisSource = AnalysisSource.USER_MEMORY)
                        } else {
                            food.copy(amount = grams, analysisSource = AnalysisSource.USER_MEMORY)
                        }
                        val status = HouseholdPortions.operationalAutoStatus(
                            food = foodItem,
                            grams = grams,
                            brandHint = null,
                            explicitKilogram = HouseholdPortions.isExplicitKilogram(descriptionSnapshot),
                            amountIntent = AmountIntent.RESOLVED_SUBJECTIVE,
                        )
                        ResolvedTag(
                            tag = food.foodName,
                            portion = food.portionPreset ?: PortionPreset.MEDIUM,
                            quantity = food.quantity,
                            amountGrams = grams,
                            cookingMethod = food.cookingMethod,
                            foodItem = foodItem,
                            loggedFood = logged,
                            isResolved = status == FoodResolutionStatus.AUTO && logged.calories.isFinite(),
                            isFuzzyMatch = false,
                            analysisSource = AnalysisSource.USER_MEMORY,
                            statusText = "Usé tu comida habitual. Tocá la tarjeta para editar.",
                            resolutionStatus = status,
                        )
                    }
                    lastAnalyzedDescription = descriptionSnapshot
                    analysisStage = null
                    analysisStartedAtMs = 0L
                    reviewRequired = false
                    endTraceOnce("template_match", mapOf("templateFoods" to templateMatch.foods.size))
                    return@launch
                }

                NutritionTelemetry.markInFlight(analysisTrace.traceId, "dataset_prepare")
                analysisTrace.stage("dataset_prepare") {
                    nutritionRepo.prepareSemanticDataset()
                }
                NutritionTelemetry.markInFlight(analysisTrace.traceId, "retrieval")
                val descriptionRetrieval = analysisTrace.stage("retrieval") {
                    withContext(Dispatchers.Default) {
                        SemanticPortionRetriever.retrieve(descriptionSnapshot)
                    }
                }
                analysisKcalRange = descriptionRetrieval.macroRange
                    ?.takeIf { it.kcalMin > 0 && it.kcalMax > it.kcalMin }
                    ?.takeIf { descriptionRetrieval.confidence >= 0.35 }
                    ?.let { kotlin.math.round(it.kcalMin).toInt() to kotlin.math.round(it.kcalMax).toInt() }
                    ?.takeIf { it.second - it.first >= 30 }
                detectedContext = ContextDetector.detect(descriptionSnapshot, mealType)
                NutritionTelemetry.markInFlight(analysisTrace.traceId, "parse")
                val parseStartedAtMs = System.currentTimeMillis()
                val parsed = withContext(Dispatchers.Default) {
                    parseMealDescription(descriptionSnapshot, descriptionRetrieval)
                }
                analysisTrace.stageEnded(
                    name = "parse",
                    durationMs = System.currentTimeMillis() - parseStartedAtMs,
                    ok = true,
                )
                NutritionTelemetry.markInFlight(analysisTrace.traceId, "resolve_tags")
                analysisTrace.stage("resolve_tags") {
                    resolveTags(parsed)
                }
                // CRI-AUDIT (P2): si el parseo local devolvió 0 alimentos para un texto
                // no-vacío, NUNCA dejar tags vacíos en silencio: caemos al último nivel
                // para que el usuario siempre tenga algo que revisar y guardar.
                if (tags.isEmpty() && descriptionSnapshot.isNotBlank()) {
                    createLastResortManualTags(descriptionSnapshot)
                }
                lastAnalyzedDescription = descriptionSnapshot
                analysisNotice = buildAnalysisNotice(parsed) ?: datasetNotReadyNotice()
                if (parsed.aiInferredFoods.isNotEmpty()) {
                    nutritionRepo.saveAiInferredFoods(parsed.aiInferredFoods)
                }
                endTraceOnce(
                    "completed",
                    mapOf(
                        "items" to parsed.items.size,
                        "tags" to tags.size,
                        "resolved" to tags.count { it.isResolved },
                        "engine" to parsed.analysisEngine,
                        "aiInferred" to parsed.aiInferredFoods.size,
                        "kcalRangeKnown" to (analysisKcalRange != null),
                    ),
                )
            } catch (cancelled: CancellationException) {
                NutritionTelemetry.clearInFlight()
                analysisTrace.event("analysis_cancelled")
                throw cancelled
            } catch (pipelineError: Throwable) {
                android.util.Log.w("FoodLogger", "Parse failed, usando determinístico", pipelineError)
                analysisKcalRange = null
                val pipelineDetail = technicalDetailOf(pipelineError)
                analysisTrace.event(
                    "analysis_pipeline_failed",
                    mapOf(
                        "errorType" to pipelineError.javaClass.name,
                        "message" to (pipelineError.message ?: ""),
                    ),
                )
                // CRASH-FIX: el fallback ejecuta las mismas operaciones que pueden
                // fallar; va protegido en su propio try para que nunca escape.
                NutritionTelemetry.markInFlight(analysisTrace.traceId, "salvage")
                val (salvageDetail, salvaged) = try {
                    analysisTrace.stage("salvage_parse") {
                        withContext(Dispatchers.Default) {
                            val fallbackParsed = parseMealDescription(
                                descriptionSnapshot,
                                SemanticPortionRetriever.retrieve(descriptionSnapshot),
                            )
                            resolveTags(fallbackParsed)
                        }
                    }
                    // CRI-ANALYSIS: el éxito del salvage NO depende de la telemetría
                    // (stage ya no puede lanzar por emisión). LastAnalyzed solo tras éxito.
                    lastAnalyzedDescription = descriptionSnapshot
                    null to true
                } catch (cancelled: CancellationException) {
                    NutritionTelemetry.clearInFlight()
                    throw cancelled
                } catch (salvageError: Throwable) {
                    val salvageFailedDetail = technicalDetailOf(salvageError)
                    analysisTrace.event(
                        "analysis_salvage_failed",
                        mapOf(
                            "errorType" to salvageError.javaClass.name,
                            "message" to (salvageError.message ?: ""),
                        ),
                    )
                    // ÚLTIMO NIVEL: independiente de parser/dataset/resolver/Room. Crea tags
                    // manuales no-resueltos desde un split trivial para que NUNCA quede un
                    // callejón sin salida. runCatching: no puede lanzar.
                    val lastResortOk = runCatching {
                        createLastResortManualTags(descriptionSnapshot)
                    }.getOrDefault(false)
                    if (lastResortOk) lastAnalyzedDescription = descriptionSnapshot
                    salvageFailedDetail to lastResortOk
                }
                analysisNotice = when {
                    salvaged -> AnalysisNotice(
                        title = "No se pudo leer la comida completa",
                        message = "Preparamos una versión base del registro para que puedas revisarla y guardar igual.",
                        tone = AnalysisNoticeTone.WARNING,
                        technicalDetail = pipelineDetail,
                    )
                    salvageDetail != null -> AnalysisNotice(
                        title = if (tags.isNotEmpty()) "Preparamos un borrador manual" else "No se pudo analizar esta descripción",
                        message = if (tags.isNotEmpty()) {
                            "El análisis automático falló; revisa cada alimento y ajústalo antes de guardar."
                        } else {
                            "Inténtalo de nuevo o registra los alimentos manualmente."
                        },
                        tone = AnalysisNoticeTone.WARNING,
                        technicalDetail = salvageDetail,
                    )
                    else -> AnalysisNotice(
                        title = "No se pudo analizar esta descripción",
                        message = "Inténtalo de nuevo o registra los alimentos manualmente.",
                        tone = AnalysisNoticeTone.WARNING,
                        technicalDetail = pipelineDetail,
                    )
                }
                endTraceOnce(if (salvaged) "salvaged" else if (tags.isNotEmpty()) "last_resort" else "failed")
            } finally {
                KpknDiagnosticLogger.event(
                    namespace = "nutrition",
                    name = "analysis_finished",
                    fields = mapOf(
                        "tagCount" to tags.size,
                        "engine" to "local",
                    ),
                )
                NutritionTelemetry.clearInFlight()
                isAnalyzing = false
                analysisStage = null
                analysisStartedAtMs = 0L
            }
        }
    }

    // CRI-AUDIT (P0): precalentar dataset + índice de alimentos al abrir el registro,
    // para que el primer toque de ANALIZAR no se topen con la carga (dataset 1.3MB + índice).
    LaunchedEffect(Unit) {
        nutritionRepo.prepareSemanticDataset()
        nutritionRepo.initFoodIndex()
    }

    LaunchedEffect(initialDescription, initialTab) {
        if (initialDescription.isNullOrBlank()) return@LaunchedEffect
        val normalized = initialDescription.trim()
        if (normalized.isBlank()) return@LaunchedEffect
        description = normalized
        activeTab = initialTab.coerceIn(0, 1)
        if (tags.isEmpty()) {
            analyzeDescription()
        }
    }

    // FIX NUT-04: deduplicate rapid double taps and fix rank 0 hardcode
    var lastLearnedKey by remember { mutableStateOf<String?>(null) }
    var lastLearnedAt by remember { mutableLongStateOf(0L) }

    fun resolveFood(tagId: String, food: FoodItem) {
        val targetTag = tags.firstOrNull { it.id == tagId }
        // FIX NUT-04: real rank from candidate position (was always 0)
        val realRank = targetTag?.reviewCandidates?.indexOfFirst { it.id == food.id }?.let { if (it >= 0) it + 1 else 0 } ?: 0
        NutritionTelemetry.event("candidate_selected", mapOf("source" to "manual", "rank" to realRank))
        val portionAdj = detectedContext?.portionAdjustment ?: 1.0
        // IT2: conectar el aprendizaje del resolver — una corrección manual del
        // usuario debe persistir para futuras resoluciones (antes era código muerto).
        if (targetTag != null) {
            val learnKey = "${targetTag.tag.lowercase()}|${food.id}"
            val now = System.currentTimeMillis()
            val isDuplicate = learnKey == lastLearnedKey && (now - lastLearnedAt) < 2000
            if (!isDuplicate) {
                lastLearnedKey = learnKey
                lastLearnedAt = now
                nutritionRepo.recordLearnedResolution(
                    query = targetTag.tag,
                    brandHint = null,
                    foodId = food.id,
                    portionGrams = targetTag.amountGrams,
                    cookingMethod = targetTag.cookingMethod?.name,
                )
            }
            updateCalibrationProfile { profile ->
                NutritionCalibrationWizardEngine.recordConfirmedIdentity(profile, targetTag.tag, food.id)
            }
        }
        tags = tags.map { tag ->
            if (tag.id == tagId) {
                val adj = scalingForIntent(tag.amountIntent, portionAdj)
                val usePrepared = CookingStateResolver.isAlreadyPreparedForMethod(food, tag.cookingMethod)
                val scaleMethod = if (usePrepared) null else tag.cookingMethod
                val applyOil = CookingStateResolver.shouldApplyOil(food, tag.cookingMethod) && !usePrepared
                val logged = scaleFoodByPortion(
                    food = food,
                    quantity = tag.quantity,
                    portion = tag.portion,
                    amountGrams = tag.amountGrams,
                    cookingMethod = scaleMethod,
                    portionAdjustment = adj,
                )
                val adjustedLogged = if (applyOil) {
                    adjustLoggedFoodForOil(logged, tag.cookingMethod, tag.oilLevel, foodName = food.name)
                } else {
                    logged.copy(cookingMethod = tag.cookingMethod ?: logged.cookingMethod)
                }
                tag.copy(
                    foodItem = food,
                    loggedFood = adjustedLogged.copy(analysisSource = AnalysisSource.DATABASE),
                    baseAmountGrams = tag.baseAmountGrams ?: tag.amountGrams ?: adjustedLogged.amount,
                    portionMinGrams = tag.portionMinGrams ?: tag.amountGrams ?: adjustedLogged.amount,
                    portionMaxGrams = tag.portionMaxGrams ?: tag.amountGrams ?: adjustedLogged.amount,
                    isResolved = true,
                    isFuzzyMatch = false,
                    analysisSource = AnalysisSource.DATABASE,
                    statusText = "",
                    hasManualEdits = true,
                    oilApplied = applyOil,
                    needsCookingClarification = false,
                    clarificationKind = CookingStateResolver.ClarificationKind.NONE,
                    canonicalFamily = FoodIdentity.familyFor(food),
                    foodState = FoodIdentity.stateFor(food),
                    resolutionStatus = FoodResolutionStatus.AUTO,
                    nutritionSource = NutritionSourceKind.CURATED_LOCAL,
                    resolutionConfidence = 1.0,
                    explicitDecision = true,
                    isUncertain = false,
                )
            } else tag
        }
    }

    fun confirmEstimate(tagId: String) {
        NutritionTelemetry.event("manual_correction", mapOf("field" to "confirm_estimate"))
        tags = tags.map { tag ->
            if (tag.id == tagId && tag.loggedFood != null) {
                tag.copy(
                    isResolved = true,
                    resolutionStatus = FoodResolutionStatus.CONFIRMED_ESTIMATE,
                    statusText = "Estimación confirmada por ti.",
                    hasManualEdits = true,
                    explicitDecision = true,
                )
            } else tag
        }
        reviewRequired = tags.any { it.hasMaterialQuestion() }
    }
    fun updateTagPortion(tagId: String, portion: PortionPreset) {
        NutritionTelemetry.event("manual_correction", mapOf("field" to "portion"))
        val portionAdj = detectedContext?.portionAdjustment ?: 1.0
        val selectedTag = tags.firstOrNull { it.id == tagId }
        tags = tags.map { tag ->
            if (tag.id == tagId) {
                val food = tag.foodItem
                val adj = scalingForIntent(tag.amountIntent, portionAdj)
                if (food != null && tag.analysisSource != AnalysisSource.LOCAL_AI_ESTIMATE && tag.analysisSource != AnalysisSource.EXTERNAL_API_ESTIMATE) {
                    val multiplier = PORTION_MULTIPLIERS[portion] ?: 1.0
                    // Never derive the next value from the previous selection.
                    // The immutable anchor makes Grande → Pequeña → Grande exact.
                    val baseGrams = tag.baseAmountGrams
                        ?: tag.amountGrams?.takeIf { it > 0.0 }
                        ?: HouseholdPortions.defaultGrams(food, tag.tag)
                    val newGrams = baseGrams * multiplier
                    val usePrepared = CookingStateResolver.isAlreadyPreparedForMethod(food, tag.cookingMethod)
                    val scaleMethod = if (usePrepared) null else tag.cookingMethod
                    val logged = scaleFoodByPortion(
                        food = food,
                        quantity = tag.quantity,
                        portion = portion,
                        amountGrams = newGrams,
                        cookingMethod = scaleMethod,
                        portionAdjustment = adj,
                    )
                    val adjustedLogged = if (tag.oilApplied) {
                        adjustLoggedFoodForOil(logged, tag.cookingMethod, tag.oilLevel, foodName = food.name)
                    } else logged
                    tag.copy(
                        portion = portion,
                        amountGrams = newGrams,
                        baseAmountGrams = baseGrams,
                        portionMinGrams = baseGrams * 0.75,
                        portionMaxGrams = baseGrams * 1.25,
                        amountIntent = AmountIntent.EXPLICIT_MASS,
                        loggedFood = adjustedLogged,
                        hasManualEdits = true,
                    )
                } else if (tag.loggedFood != null) {
                    val multiplier = PORTION_MULTIPLIERS[portion] ?: 1.0
                    val baseGrams = tag.baseAmountGrams
                        ?: tag.amountGrams?.takeIf { it > 0 }
                        ?: tag.loggedFood.amount.takeIf { it > 0 }
                        ?: 100.0
                    val newGrams = baseGrams * multiplier
                    val scale = if (baseGrams > 0.0) newGrams / baseGrams else 1.0
                    val old = tag.loggedFood
                    val scaledLogged = old.copy(
                        amount = newGrams,
                        calories = old.calories * scale,
                        protein = old.protein * scale,
                        carbs = old.carbs * scale,
                        fats = old.fats * scale,
                    )
                    tag.copy(
                        portion = portion,
                        amountGrams = newGrams,
                        baseAmountGrams = baseGrams,
                        portionMinGrams = baseGrams * 0.75,
                        portionMaxGrams = baseGrams * 1.25,
                        amountIntent = AmountIntent.EXPLICIT_MASS,
                        loggedFood = scaledLogged,
                        hasManualEdits = true,
                    )
                } else {
                    tag.copy(portion = portion, hasManualEdits = true)
                }
            } else tag
        }
        selectedTag?.let { tag ->
            val base = tag.baseAmountGrams ?: tag.amountGrams ?: tag.loggedFood?.amount
            val grams = base?.times(PORTION_MULTIPLIERS[portion] ?: 1.0)
            val key = tag.canonicalFamily ?: tag.foodItem?.let(FoodIdentity::familyFor) ?: tag.tag
            grams?.let { updateCalibrationProfile { profile -> NutritionCalibrationWizardEngine.recordConfirmedPortion(profile, key, it) } }
        }
    }

    /** Accept the central estimate without training any learned resolver. */
    fun useEstimate(tagId: String) {
        NutritionTelemetry.event("clarification_answered", mapOf("kind" to "unsure"))
        tags = tags.map { tag ->
            if (tag.id != tagId) return@map tag
            val food = tag.foodItem
            val grams = tag.amountGrams ?: tag.baseAmountGrams ?: tag.loggedFood?.amount
            val central = tag.loggedFood ?: food?.let {
                val base = scaleFoodByPortion(
                    food = it,
                    quantity = tag.quantity,
                    portion = tag.portion,
                    amountGrams = grams,
                    cookingMethod = tag.cookingMethod,
                    portionAdjustment = 1.0,
                )
                if (tag.oilApplied) {
                    adjustLoggedFoodForOil(base, tag.cookingMethod, tag.oilLevel, foodName = it.name)
                } else base
            }
            val minGrams = tag.portionMinGrams ?: grams
            val maxGrams = tag.portionMaxGrams ?: grams
            val centralWithRange = central?.copy(
                caloriesMin = central.calories * (minGrams ?: central.amount) / central.amount.coerceAtLeast(1.0),
                caloriesMax = central.calories * (maxGrams ?: central.amount) / central.amount.coerceAtLeast(1.0),
                proteinMin = central.protein * (minGrams ?: central.amount) / central.amount.coerceAtLeast(1.0),
                proteinMax = central.protein * (maxGrams ?: central.amount) / central.amount.coerceAtLeast(1.0),
                carbsMin = central.carbs * (minGrams ?: central.amount) / central.amount.coerceAtLeast(1.0),
                carbsMax = central.carbs * (maxGrams ?: central.amount) / central.amount.coerceAtLeast(1.0),
                fatsMin = central.fats * (minGrams ?: central.amount) / central.amount.coerceAtLeast(1.0),
                fatsMax = central.fats * (maxGrams ?: central.amount) / central.amount.coerceAtLeast(1.0),
                interpretationId = tag.id,
                isUncertain = true,
            )
            tag.copy(
                loggedFood = centralWithRange,
                isResolved = centralWithRange != null,
                isUncertain = true,
                explicitDecision = true,
                needsCookingClarification = false,
                clarificationKind = CookingStateResolver.ClarificationKind.NONE,
                resolutionStatus = if (centralWithRange != null) FoodResolutionStatus.AUTO else FoodResolutionStatus.NO_RESOLVED,
                statusText = "Estimación visible. Tocá la tarjeta para editar.",
                hasManualEdits = true,
            )
        }
        reviewRequired = tags.any { it.hasMaterialQuestion() }
    }

    fun updateTagGrams(tagId: String, grams: Double) {
        NutritionTelemetry.event("manual_correction", mapOf("field" to "grams"))
        val portionAdj = detectedContext?.portionAdjustment ?: 1.0
        tags = tags.map { tag ->
            if (tag.id == tagId) {
                val food = tag.foodItem
                val adj = scalingForIntent(AmountIntent.EXPLICIT_MASS, portionAdj)
                val logged = if (food != null && tag.analysisSource != AnalysisSource.LOCAL_AI_ESTIMATE && tag.analysisSource != AnalysisSource.EXTERNAL_API_ESTIMATE) {
                    val usePrepared = CookingStateResolver.isAlreadyPreparedForMethod(food, tag.cookingMethod)
                    val scaleMethod = if (usePrepared) null else tag.cookingMethod
                    val baseLogged = scaleFoodByPortion(
                        food = food,
                        quantity = tag.quantity,
                        portion = tag.portion,
                        amountGrams = grams,
                        cookingMethod = scaleMethod,
                        portionAdjustment = adj,
                    )
                    if (tag.oilApplied) {
                        adjustLoggedFoodForOil(baseLogged, tag.cookingMethod, tag.oilLevel, foodName = food.name)
                    } else baseLogged
                } else {
                    val old = tag.loggedFood
                    val baseGrams = tag.amountGrams ?: old?.amount ?: 100.0
                    val scale = if (baseGrams > 0.0) grams / baseGrams else 1.0
                    old?.copy(
                        amount = grams,
                        calories = kotlin.math.round(old.calories * scale),
                        protein = kotlin.math.round(old.protein * scale * 10) / 10.0,
                        carbs = kotlin.math.round(old.carbs * scale * 10) / 10.0,
                        fats = kotlin.math.round(old.fats * scale * 10) / 10.0,
                    )
                }
                tag.copy(
                    amountGrams = grams,
                    baseAmountGrams = grams,
                    portionMinGrams = grams,
                    portionMaxGrams = grams,
                    amountIntent = AmountIntent.EXPLICIT_MASS,
                    loggedFood = logged,
                    hasManualEdits = true,
                )
            } else tag
        }
    }

    fun updateTagOilLevel(tagId: String, oilLevel: String) {
        val portionAdj = detectedContext?.portionAdjustment ?: 1.0
        tags = tags.map { tag ->
            if (tag.id != tagId) return@map tag
            if (!tag.oilApplied && tag.cookingMethod != CookingMethod.FRITO &&
                tag.cookingMethod != CookingMethod.EMPANIZADO_FRITO
            ) {
                return@map tag.copy(oilLevel = oilLevel, hasManualEdits = true)
            }
            val food = tag.foodItem
            val adj = scalingForIntent(tag.amountIntent, portionAdj)
            if (food != null && tag.analysisSource != AnalysisSource.LOCAL_AI_ESTIMATE &&
                tag.analysisSource != AnalysisSource.EXTERNAL_API_ESTIMATE
            ) {
                val usePrepared = CookingStateResolver.isAlreadyPreparedForMethod(food, tag.cookingMethod)
                val scaleMethod = if (usePrepared) null else tag.cookingMethod
                val logged = scaleFoodByPortion(
                    food = food,
                    quantity = tag.quantity,
                    portion = tag.portion,
                    amountGrams = tag.amountGrams,
                    cookingMethod = scaleMethod,
                    portionAdjustment = adj,
                )
                val adjustedLogged = adjustLoggedFoodForOil(logged, tag.cookingMethod, oilLevel, foodName = food.name)
                tag.copy(
                    oilLevel = oilLevel,
                    loggedFood = adjustedLogged,
                    hasManualEdits = true,
                    oilApplied = true,
                )
            } else if (tag.loggedFood != null) {
                val stripped = stripOilFromLoggedFood(tag.loggedFood, tag.cookingMethod, tag.oilLevel, foodName = tag.foodItem?.name)
                val adjusted = adjustLoggedFoodForOil(stripped, tag.cookingMethod, oilLevel, foodName = tag.foodItem?.name)
                tag.copy(
                    oilLevel = oilLevel,
                    loggedFood = adjusted,
                    hasManualEdits = true,
                    oilApplied = true,
                )
            } else {
                tag.copy(oilLevel = oilLevel, hasManualEdits = true)
            }
        }
        tags.firstOrNull { it.id == tagId }?.let { tag ->
            val key = tag.canonicalFamily ?: tag.foodItem?.let(FoodIdentity::familyFor) ?: tag.tag
            val portion = (tag.amountGrams ?: tag.loggedFood?.amount ?: 100.0).coerceAtLeast(1.0)
            val gramsPer100 = oilGramsForLevel(oilLevel) * 100.0 / portion
            updateCalibrationProfile { profile -> NutritionCalibrationWizardEngine.recordConfirmedOil(profile, key, gramsPer100) }
        }
    }

    fun updateTagCookingClarification(tagId: String, wantCooked: Boolean) {
        NutritionTelemetry.event("clarification_answered", mapOf("kind" to "cooking_state", "answer" to if (wantCooked) "cooked" else "raw"))
        val portionAdj = detectedContext?.portionAdjustment ?: 1.0
        tags = tags.map { tag ->
            if (tag.id != tagId) return@map tag
            val method = if (wantCooked) CookingMethod.COCIDO else CookingMethod.CRUDO
            val variant = CookingStateResolver.findDryOrCookedVariant(tag.tag, wantCooked)
                ?: tag.foodItem
            val food = variant ?: tag.foodItem
            if (food == null) return@map tag.copy(
                cookingMethod = method,
                needsCookingClarification = false,
                clarificationKind = CookingStateResolver.ClarificationKind.NONE,
                statusText = "",
                hasManualEdits = true,
            )
            nutritionRepo.recordFoodSelection(tag.tag, food)
            val adj = scalingForIntent(tag.amountIntent, portionAdj)
            val usePrepared = CookingStateResolver.isAlreadyPreparedForMethod(food, method)
            val scaleMethod = if (usePrepared) null else method
            val logged = scaleFoodByPortion(
                food = food,
                quantity = tag.quantity,
                portion = tag.portion,
                amountGrams = tag.amountGrams,
                cookingMethod = scaleMethod,
                portionAdjustment = adj,
            )
            tag.copy(
                cookingMethod = method,
                foodItem = food,
                loggedFood = logged.copy(analysisSource = AnalysisSource.DATABASE, cookingMethod = method),
                baseAmountGrams = tag.baseAmountGrams ?: tag.amountGrams ?: logged.amount,
                portionMinGrams = tag.portionMinGrams ?: tag.amountGrams ?: logged.amount,
                portionMaxGrams = tag.portionMaxGrams ?: tag.amountGrams ?: logged.amount,
                isResolved = true,
                analysisSource = AnalysisSource.DATABASE,
                statusText = "",
                needsCookingClarification = false,
                clarificationKind = CookingStateResolver.ClarificationKind.NONE,
                hasManualEdits = true,
                oilApplied = false,
                canonicalFamily = FoodIdentity.familyFor(food),
                foodState = FoodIdentity.stateFor(food),
                resolutionStatus = FoodResolutionStatus.AUTO,
                nutritionSource = NutritionSourceKind.CURATED_LOCAL,
                resolutionConfidence = 1.0,
                explicitDecision = true,
                isUncertain = false,
            )
        }
        reviewRequired = tags.any { it.hasMaterialQuestion() }
        tags.firstOrNull { it.id == tagId }?.let { tag ->
            val key = tag.canonicalFamily ?: tag.foodItem?.let(FoodIdentity::familyFor) ?: tag.tag
            updateCalibrationProfile { profile ->
                NutritionCalibrationWizardEngine.recordConfirmedState(
                    profile,
                    key,
                    if (wantCooked) com.example.kpkn.domain.nutrition.WeightBasis.COOKED else com.example.kpkn.domain.nutrition.WeightBasis.RAW,
                )
            }
        }
    }

                    fun updateTagCalories(tagId: String, calories: Double) {
        tags = tags.map { tag ->
            if (tag.id == tagId) {
                val old = tag.loggedFood ?: return@map tag
                if (old.calories <= 0.0) return@map tag
                val scale = calories / old.calories
                tag.copy(loggedFood = old.copy(
                    calories = calories,
                    protein = kotlin.math.round(old.protein * scale * 10) / 10.0,
                    carbs = kotlin.math.round(old.carbs * scale * 10) / 10.0,
                    fats = kotlin.math.round(old.fats * scale * 10) / 10.0,
                ), hasManualEdits = true)
            } else tag
        }
    }

    fun updateTagProtein(tagId: String, protein: Double) {
        tags = tags.map { tag ->
            if (tag.id == tagId) {
                val old = tag.loggedFood ?: return@map tag
                val roundedP = kotlin.math.round(protein * 10) / 10.0
                val newCalories = kotlin.math.round(roundedP * 4.0 + old.carbs * 4.0 + old.fats * 9.0)
                tag.copy(loggedFood = old.copy(
                    protein = roundedP,
                    calories = newCalories,
                ), hasManualEdits = true)
            } else tag
        }
    }

    fun updateTagCarbs(tagId: String, carbs: Double) {
        tags = tags.map { tag ->
            if (tag.id == tagId) {
                val old = tag.loggedFood ?: return@map tag
                val roundedC = kotlin.math.round(carbs * 10) / 10.0
                val newCalories = kotlin.math.round(old.protein * 4.0 + roundedC * 4.0 + old.fats * 9.0)
                tag.copy(loggedFood = old.copy(
                    carbs = roundedC,
                    calories = newCalories,
                ), hasManualEdits = true)
            } else tag
        }
    }

    fun updateTagFats(tagId: String, fats: Double) {
        tags = tags.map { tag ->
            if (tag.id == tagId) {
                val old = tag.loggedFood ?: return@map tag
                val roundedF = kotlin.math.round(fats * 10) / 10.0
                val newCalories = kotlin.math.round(old.protein * 4.0 + old.carbs * 4.0 + roundedF * 9.0)
                tag.copy(loggedFood = old.copy(
                    fats = roundedF,
                    calories = newCalories,
                ), hasManualEdits = true)
            } else tag
        }
    }

    fun removeTag(tagId: String) { tags = tags.filter { it.id != tagId } }
    fun toggleTagExpanded(tagId: String) {
        tags = tags.map { if (it.id == tagId) it.copy(isExpanded = !it.isExpanded) else it }
    }

    fun performSearch() {
        if (searchQuery.isBlank()) {
            searchResults = emptyList()
            return
        }
        scope.launch {
            searchResults = nutritionRepo.searchFoodCandidates(searchQuery, limit = 15)
        }
    }

    fun saveLog() {
        val activeTags = tags.filterNot { it.isExcluded }
        val resolvedFoods = activeTags.mapNotNull { tag ->
            tag.loggedFood?.takeIf { food ->
                food.calories.isFinite() && food.protein.isFinite() &&
                    food.carbs.isFinite() && food.fats.isFinite()
            }
        }
        if (resolvedFoods.isEmpty()) {
            NutritionTelemetry.event("save_rejected", mapOf("reason" to "no_resolved_foods", "tags" to activeTags.size))
            reviewRequired = true
            return
        }
        val hasNegativeMacros = resolvedFoods.any {
            it.calories < 0.0 || it.protein < 0.0 || it.carbs < 0.0 || it.fats < 0.0
        }
        if (hasNegativeMacros) {
            NutritionTelemetry.event("save_rejected", mapOf("reason" to "negative_macros", "foods" to resolvedFoods.size))
            reviewRequired = true
            return
        }
        val hasTooLargeValues = resolvedFoods.any {
            it.calories > 10000.0 || it.protein > 1000.0 || it.carbs > 1000.0 || it.fats > 1000.0
        }
        if (hasTooLargeValues) {
            NutritionTelemetry.event("save_rejected", mapOf("reason" to "macro_out_of_range", "foods" to resolvedFoods.size))
            reviewRequired = true
            return
        }

        val log = NutritionLog(
            id = UUID.randomUUID().toString(),
            date = "${logDate}T12:00:00.000Z",
            mealType = mealType,
            foods = resolvedFoods,
            notes = null,
            status = NutritionStatus.CONSUMED,
        )
        onSave(log)
        KpknDiagnosticLogger.event(
            namespace = "nutrition",
            name = "meal_saved",
            fields = mapOf(
                "foodCount" to resolvedFoods.size,
                "mealType" to mealType.name,
                "date" to logDate,
            ),
        )
        NutritionTelemetry.event(
            "save_log",
            mapOf(
                "foodCount" to resolvedFoods.size,
                "mealType" to mealType.name,
                "tagCount" to tags.size,
                "fromDescription" to lastAnalyzedDescription.isNotBlank(),
                "descriptionLength" to lastAnalyzedDescription.length,
            ),
        )
        showSuccess = true
        reviewRequired = false
    }

    val tagTotals = remember(tags) {
        val activeTags = tags.filterNot { it.isExcluded }
        DailyMacroTotals(
            calories = activeTags.sumOf { it.loggedFood?.calories ?: 0.0 },
            protein = activeTags.sumOf { it.loggedFood?.protein ?: 0.0 },
            carbs = activeTags.sumOf { it.loggedFood?.carbs ?: 0.0 },
            fats = activeTags.sumOf { it.loggedFood?.fats ?: 0.0 },
        )
    }

    if (showSettingsDialog) {
        KpknAlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = {
                Text(
                    text = "Configuración",
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showSettingsDialog = false
                                utensilValues = SubjectivePortionEngine.UTENSIL_DEFAULTS
                                    .mapValues { (_, ml) -> ml.toFloat() }
                                showUtensilDialog = true
                            },
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Column {
                                Text(
                                    text = "Ajustar medidas",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = "Personaliza el volumen en ml de tazas y platos",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                nutritionRepo.clearLearnedResolutions()
                                learnedMemoryCleared = true
                                showSettingsDialog = false
                            },
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Column {
                                Text(
                                    text = "Olvidar lo aprendido",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = "Borra las elecciones y porciones recordadas",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Cerrar")
                }
            },
            shape = RoundedCornerShape(20.dp),
        )
    }

    if (showUtensilDialog) {
        UtensilSettingsDialog(
            values = utensilValues,
            onValueChange = { key, ml -> utensilValues = utensilValues + (key to ml) },
            onSave = {
                utensilValues.forEach { (key, ml) ->
                    nutritionRepo.saveUtensilOverride(key, ml.toDouble())
                }
                showUtensilDialog = false
            },
            onDismiss = { showUtensilDialog = false },
        )
    }

    val activeTagsForSave = tags.filterNot { it.isExcluded }
    val saveBlocked = activeTagsForSave.isEmpty() || activeTagsForSave.any { tag ->
        val food = tag.loggedFood
        food == null || !food.calories.isFinite() || !food.protein.isFinite() ||
            !food.carbs.isFinite() || !food.fats.isFinite()
    }

    // ─── Sheet ───────────────────────────────────────────────────────────────

    KpknSheet(onDismissRequest = requestDismiss) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Header ───────────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "Registrar comida",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            text = "Describe tu comida o agrega alimentos uno por uno.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Configuración",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ── Meal Type Selector ───────────────────────────────────────────
            item { MealTypeSelector(mealType = mealType, onSelect = { mealType = it }) }

            // ── Date ────────────────────────────────────────────────────────
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Default.CalendarMonth, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = try {
                            java.time.LocalDate.parse(logDate)
                                .format(java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM", java.util.Locale.getDefault()))
                        } catch (e: Exception) { logDate },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // ── Tab Selector ────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TabChip("Describir comida", activeTab == 0, modifier = Modifier.weight(1f)) { activeTab = 0 }
                    TabChip("Buscar alimento", activeTab == 1, modifier = Modifier.weight(1f)) { activeTab = 1 }
                }
            }

            // ── Description Tab ──────────────────────────────────────────────
            if (activeTab == 0) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (reviewRequired) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                            ) {
                                Text(
                                    text = "Revisa la comida antes de guardar: hay datos incompletos o fuera de rango.",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        }
                        TextField(
                            value = description,
                            onValueChange = { description = it },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isAnalyzing,
                            placeholder = {
                                Text(
                                    "Ej: pollo a la plancha 200 g, arroz cocido 150 g, palta 80 g"
                                )
                            },
                            minLines = 2,
                            maxLines = 4,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { analyzeDescription() }),
                            shape = RoundedCornerShape(12.dp),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                            )
                        )

                        // Barra de progreso de copia del modelo (sólo modo PRO mientras copia)
                        AnimatedVisibility(
                            visible = false && 0f > 0f && 0f < 1f,
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = "Preparando Parser...",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = PRO_COLOR,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = "${(0f * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = PRO_COLOR,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { 0f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = PRO_COLOR,
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = isAnalyzing,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            LocalAnalysisPanel(
                                stage = analysisStage,
                                elapsedMs = analysisElapsedMs,
                            )
                        }

                        AnimatedVisibility(
                            visible = analysisNotice != null,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            analysisNotice?.let { notice ->
                                AnalysisNoticeCard(notice = notice)
                            }
                        }



                        if (learnedMemoryCleared) {
                            Text(
                                "Memoria de aprendizaje borrada",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
            }

            // ── Search Tab ──────────────────────────────────────────────────
            if (activeTab == 1) {
                item {
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Busca un alimento para agregarlo") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { performSearch() }),
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    )
                }
                items(searchResults, key = { "search_${it.food.name}_${it.food.brand.orEmpty()}" }) { food ->
                    FoodSearchResultCard(candidate = food, onClick = {
                        val selectedFood = food.food
                        val queryUsed = searchQuery.ifBlank { selectedFood.name }
                        val identity = HouseholdPortions.identityForSearchPick(selectedFood, queryUsed)
                            ?: return@FoodSearchResultCard
                        val grams = HouseholdPortions.eatenGramsForSearchPick(
                            identity,
                            queryUsed,
                            selectedFood,
                        )
                        val logged = scaleFoodByPortion(identity, amountGrams = grams)
                        val status = HouseholdPortions.operationalAutoStatus(
                            food = identity,
                            grams = grams,
                            brandHint = null,
                            explicitKilogram = HouseholdPortions.isExplicitKilogram(queryUsed),
                            amountIntent = AmountIntent.UNSPECIFIED,
                        )
                        val tag = ResolvedTag(
                            tag = identity.name,
                            portion = PortionPreset.MEDIUM,
                            quantity = 1.0,
                            amountGrams = grams,
                            foodItem = identity,
                            loggedFood = logged,
                            isResolved = status == FoodResolutionStatus.AUTO,
                            statusText = "",
                            hasManualEdits = true,
                            resolutionStatus = status,
                            nutritionSource = if (HouseholdPortions.isGlobalSku(identity)) {
                                NutritionSourceKind.VERIFIED_GLOBAL
                            } else {
                                NutritionSourceKind.CURATED_LOCAL
                            },
                            resolutionConfidence = 1.0,
                        )
                        nutritionRepo.recordFoodSelection(queryUsed, identity)
                        tags = tags + tag
                        searchQuery = ""
                        searchResults = emptyList()
                    })
                }
            }

            // ── Tag List ────────────────────────────────────────────────────
            if (tags.isNotEmpty()) {
                item {
                    Text(
                        text = "Resumen de la comida",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (0.1f).sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(tags, key = { it.id }) { tag ->
                    Box(modifier = Modifier.animateItem()) {
                        TagCard(
                            tag = tag,
                            nutritionRepo = nutritionRepo,
                            onToggleExpanded = { toggleTagExpanded(tag.id) },
                            onPortionChange = { updateTagPortion(tag.id, it) },
                            onGramsChange = { updateTagGrams(tag.id, it) },
                            onCaloriesChange = { updateTagCalories(tag.id, it) },
                            onProteinChange = { updateTagProtein(tag.id, it) },
                            onCarbsChange = { updateTagCarbs(tag.id, it) },
                            onFatsChange = { updateTagFats(tag.id, it) },
                            onRemove = { removeTag(tag.id) },
                            foodDatabase = foodDatabase,
                            onResolve = { food ->
                                // FIX NUT-04: removed duplicate recordFoodSelection — resolveFood is single source (dedup + rank)
                                resolveFood(tag.id, food)
                            },
                            onOilLevelChange = { level -> updateTagOilLevel(tag.id, level) },
                            onCookingClarification = { wantCooked ->
                                updateTagCookingClarification(tag.id, wantCooked)
                            },
                            onConfirmEstimate = { confirmEstimate(tag.id) },
                            onUnsure = { useEstimate(tag.id) },
                        )
                    }
                }
            }

            // ── Totals ──────────────────────────────────────────────────────
            if (tags.isNotEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text("TOTAL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.1f.sp)
                                Text("${kotlin.math.round(tagTotals.calories).toInt()} kcal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                                analysisKcalRange?.let { (minK, maxK) ->
                                    Text(
                                        "referencia ${minK}–${maxK} kcal",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.6f),
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                MacroBadge("P", "${kotlin.math.round(tagTotals.protein).toInt()}g", PROTEIN_COLOR)
                                MacroBadge("C", "${kotlin.math.round(tagTotals.carbs).toInt()}g", CARBS_COLOR)
                                MacroBadge("G", "${kotlin.math.round(tagTotals.fats).toInt()}g", FATS_COLOR)
                            }
                        }
                    }
                }
            }

            // ── Save & Action Row ────────────────────────────────────────────
            item {
                val hasTags = tags.isNotEmpty()
                val descriptionEdited = FoodLoggerPrimaryAction.isDescriptionEdited(
                    current = description,
                    lastAnalyzed = lastAnalyzedDescription,
                    hasTags = hasTags,
                    describeTab = activeTab == 0,
                )
                val isSearchMode = activeTab == 1

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = {
                            if (isSearchMode) {
                                if (hasTags) saveLog()
                                else if (searchQuery.isNotBlank()) performSearch()
                            } else {
                                if (descriptionEdited) analyzeDescription()
                                else if (hasTags) saveLog()
                                else analyzeDescription()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (descriptionEdited) MaterialTheme.colorScheme.primary
                                             else if (hasTags) Color(0xFF2E7D32)
                                             else MaterialTheme.colorScheme.primary
                        ),
                        enabled = !isAnalyzing && (
                            if (isSearchMode) (hasTags && !saveBlocked) || searchQuery.isNotBlank()
                            else descriptionEdited || (hasTags && !saveBlocked) || (!hasTags && description.isNotBlank())
                        )
                    ) {
                        if (isAnalyzing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = LocalContentColor.current
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Interpretando...", fontWeight = FontWeight.Black)
                        } else {
                            val icon = if (descriptionEdited) Icons.Default.Refresh
                                       else if (hasTags) Icons.Default.Check
                                       else if (isSearchMode && !hasTags) Icons.Default.Search
                                       else Icons.Default.Check
                            val label = FoodLoggerPrimaryAction.label(
                                hasTags = hasTags,
                                descriptionEdited = descriptionEdited,
                                isSearchMode = isSearchMode,
                            )
                            Icon(icon, null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, fontWeight = FontWeight.Black)
                        }
                    }

                    // Botón redondo al lado del de registrar normal
                    Surface(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .clickable {
                                if (activeTab == 0) activeTab = 1
                                else activeTab = 0
                            },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 2.dp,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (activeTab == 0) Icons.Default.Search else Icons.Default.EditNote,
                                contentDescription = if (activeTab == 0) "Buscar en catálogo" else "Describir comida",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    // ── Success Snackbar ─────────────────────────────────────────────────────
    if (showSuccess) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(1500)
            showSuccess = false
            onDismiss()
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            KpknSnackbarBanner(
                message = "¡Comida registrada!",
                type = SnackbarType.SUCCESS,
            )
        }
    }
}

@Composable
private fun LocalAnalysisPanel(stage: ParseStage?, elapsedMs: Long) {
    val messages = remember(stage) {
        when {
            stage == ParseStage.INTERPRETING -> listOf(
                "Convirtiendo la comida en un registro editable",
                "Comprobando porciones y cantidades",
                "Ajustando el resumen para que sea más consistente",
            )
            else -> listOf(
                "Leyendo tu comida",
                "Estimando alimentos y cantidades",
                "Esto puede tardar un poco más la primera vez",
                "Preparando un resumen para que lo revises",
            )
        }
    }

    val messageIndex = ((elapsedMs / 2600L).toInt()).mod(messages.size.coerceAtLeast(1))
    val infiniteTransition = rememberInfiniteTransition(label = "local-analysis")
    val pulseA by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseA",
    )
    val pulseB by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, delayMillis = 180, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseB",
    )
    val pulseC by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, delayMillis = 360, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseC",
    )

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = PRO_COLOR.copy(alpha = 0.08f),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        tint = PRO_COLOR,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Preparando registro",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                Text(
                    text = "${(elapsedMs / 1000L).coerceAtLeast(1L)}s",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedContent(targetState = messages[messageIndex], label = "local-message") { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf(pulseA, pulseB, pulseC).forEach { alpha ->
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                PRO_COLOR.copy(alpha = alpha)
                            )
                    )
                }
                Text(
                    text = "Estamos interpretando la comida para dejarla lista para guardar",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = PRO_COLOR,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
        }
    }
}

@Composable
private fun AnalysisNoticeCard(notice: AnalysisNotice) {
    val accent = when (notice.tone) {
        AnalysisNoticeTone.INFO -> Color(0xFF1565C0)
        AnalysisNoticeTone.WARNING -> Color(0xFFB3261E)
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = accent.copy(alpha = 0.08f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = if (notice.tone == AnalysisNoticeTone.WARNING) Icons.Default.Info else Icons.Default.TipsAndUpdates,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = notice.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = notice.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                notice.technicalDetail?.let { detail ->
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

// ─── Mode Option Card ────────────────────────────────────────────────────────

@Composable
private fun ModeOptionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    badge: String?,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Black)
                    if (badge != null) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = PRO_COLOR,
                        ) {
                            Text(
                                text = badge,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                            )
                        }
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

// ─── Sub-composables ─────────────────────────────────────────────────────────

@Composable
private fun MealTypeSelector(mealType: MealType, onSelect: (MealType) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MEAL_OPTIONS.forEach { (type, label) ->
            val selected = type == mealType
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.clickable { onSelect(type) },
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TabChip(label: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun MacroBadge(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), fontWeight = FontWeight.ExtraBold, color = color)
        Text(text = value, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FoodSearchResultCard(candidate: FoodCandidate, onClick: () -> Unit) {
    val food = candidate.food
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = food.name.trim().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = when (candidate.confidence) {
                        SearchConfidence.HIGH -> "Alta"
                        SearchConfidence.MEDIUM -> "Media"
                        SearchConfidence.LOW -> "Baja"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when (candidate.confidence) {
                        SearchConfidence.HIGH -> Color(0xFF2E7D32)
                        SearchConfidence.MEDIUM -> Color(0xFFF57C00)
                        SearchConfidence.LOW -> MaterialTheme.colorScheme.error
                    },
                )
            }
            Text(
                text = "${kotlin.math.round(food.calories).toInt()} kcal · P ${kotlin.math.round(food.protein).toInt()}g · C ${kotlin.math.round(food.carbs).toInt()}g · G ${kotlin.math.round(food.fats).toInt()}g / ${kotlin.math.round(food.servingSize).toInt()}${food.unit}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!food.brand.isNullOrBlank()) {
                Text(
                    text = "Marca: ${food.brand}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FoodClarificationPanel(
    tag: ResolvedTag,
    onPortionChange: (PortionPreset) -> Unit,
    onGramsChange: (Double) -> Unit,
    onCookingClarification: (Boolean) -> Unit,
    onUnsure: () -> Unit,
) {
    var manualGrams by remember(tag.id, tag.amountGrams) {
        mutableStateOf((tag.amountGrams ?: tag.baseAmountGrams ?: "").toString().removeSuffix(".0"))
    }
    val options = absolutePortionOptions(tag.baseAmountGrams ?: tag.amountGrams ?: tag.loggedFood?.amount)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.58f),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                when {
                    tag.needsCookingClarification -> when (tag.clarificationKind) {
                        CookingStateResolver.ClarificationKind.DRY_VS_COOKED -> "¿Pesaste seco o ya cocido/hidratado?"
                        CookingStateResolver.ClarificationKind.RAW_VS_COOKED -> "¿El peso es en crudo o ya cocido?"
                        else -> "Aclara el estado de cocción"
                    }
                    else -> "¿Qué cantidad fue?"
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
            if (options.isNotEmpty() && !tag.needsCookingClarification) {
                Text("Elige una porción o escribe los gramos exactos.", style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    options.forEachIndexed { index, (label, grams) ->
                        val preset = when (index) {
                            0 -> PortionPreset.SMALL
                            1 -> PortionPreset.MEDIUM
                            else -> PortionPreset.LARGE
                        }
                        Surface(
                            modifier = Modifier.weight(1f).clickable { onPortionChange(preset) },
                            shape = RoundedCornerShape(9.dp),
                            color = if (tag.portion == preset) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Text(
                                "$label · ${grams.toInt()} g",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (tag.portion == preset) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (tag.needsCookingClarification) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    val stateOptions = when (tag.clarificationKind) {
                        CookingStateResolver.ClarificationKind.DRY_VS_COOKED -> listOf(false to "Seco", true to "Cocido")
                        else -> listOf(false to "Crudo", true to "Cocido")
                    }
                    stateOptions.forEach { (cooked, label) ->
                        OutlinedButton(onClick = { onCookingClarification(cooked) }, modifier = Modifier.weight(1f)) {
                            Text(label)
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = manualGrams,
                    onValueChange = { manualGrams = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Ingresar gramos") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                )
                Button(
                    onClick = { manualGrams.toDoubleOrNull()?.takeIf { it > 0.0 }?.let(onGramsChange) },
                    enabled = manualGrams.toDoubleOrNull()?.let { it > 0.0 } == true,
                ) { Text("Aplicar") }
            }
            TextButton(onClick = onUnsure, modifier = Modifier.fillMaxWidth()) {
                Text("No estoy seguro; usar estimación")
            }
        }
    }
}

@Composable
private fun TagCard(
    tag: ResolvedTag,
    nutritionRepo: NutritionRepository,
    onToggleExpanded: () -> Unit,
    onPortionChange: (PortionPreset) -> Unit,
    onGramsChange: (Double) -> Unit,
    onCaloriesChange: (Double) -> Unit,
    onProteinChange: (Double) -> Unit,
    onCarbsChange: (Double) -> Unit,
    onFatsChange: (Double) -> Unit,
    onRemove: () -> Unit,
    foodDatabase: List<FoodItem>,
    onResolve: (FoodItem) -> Unit,
    onOilLevelChange: (String) -> Unit,
    onCookingClarification: (Boolean) -> Unit,
    onConfirmEstimate: () -> Unit,
    onUnsure: () -> Unit,
) {
    val logged = tag.loggedFood

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (tag.isExcluded) MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.4f)
            else if (tag.isResolved) MaterialTheme.colorScheme.surfaceContainer
            else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp).then(
            if (tag.isExcluded) Modifier.alpha(0.5f) else Modifier
        )) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = tag.tag.trim().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, textDecoration = if (tag.isExcluded) TextDecoration.LineThrough else null)
                        if (tag.isExcluded) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.errorContainer) {
                                Text("Excluido", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                    if (logged != null) {
                        Text(
                            text = "${if (tag.isUncertain || logged.isUncertain) "≈ " else ""}${kotlin.math.round(logged.calories).toInt()} kcal · P ${kotlin.math.round(logged.protein).toInt()}g · C ${kotlin.math.round(logged.carbs).toInt()}g · G ${kotlin.math.round(logged.fats).toInt()}g",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            val sourceLabel = when {
                                tag.foodItem?.source?.contains("USDA", ignoreCase = true) == true -> "USDA"
                                tag.foodItem?.source?.contains("OFF", ignoreCase = true) == true -> "OFF"
                                tag.nutritionSource == NutritionSourceKind.HEURISTIC_ESTIMATE || tag.isUncertain -> "Estimado"
                                else -> "Local"
                            }
                            AssistChip(onClick = {}, label = { Text(sourceLabel, style = MaterialTheme.typography.labelSmall) })
                            if (tag.calibrationUsed) {
                                AssistChip(onClick = {}, label = { Text("Usé tu habitual", style = MaterialTheme.typography.labelSmall) })
                            }
                            val stateLabel = when (tag.foodState) {
                                FoodState.RAW -> "crudo"
                                FoodState.COOKED, FoodState.HYDRATED -> "cocido"
                                else -> "estado pendiente"
                            }
                            AssistChip(onClick = {}, label = { Text(stateLabel, style = MaterialTheme.typography.labelSmall) })
                        }
                        if (tag.isUncertain || logged.isUncertain) {
                            val minKcal = logged.caloriesMin?.let { kotlin.math.round(it).toInt() }
                            val maxKcal = logged.caloriesMax?.let { kotlin.math.round(it).toInt() }
                            if (minKcal != null && maxKcal != null) {
                                Text("Rango estimado: $minKcal–$maxKcal kcal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (tag.statusText.isNotBlank()) {
                        Text(
                            text = tag.statusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (tag.isResolved) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onToggleExpanded, modifier = Modifier.size(32.dp)) {
                        Icon(if (tag.isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            AnimatedVisibility(visible = tag.isExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Porción", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PortionPreset.entries.forEach { preset ->
                            val label = when (preset) {
                                PortionPreset.SMALL -> "Pequeño"
                                PortionPreset.MEDIUM -> "Mediano"
                                PortionPreset.LARGE -> "Grande"
                                PortionPreset.EXTRA -> "Extra"
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (tag.portion == preset) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.clickable { onPortionChange(preset) },
                            ) {
                                Text(
                                    text = label,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (tag.portion == preset) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    if (tag.amountGrams != null || tag.loggedFood != null) {
                        val currentGrams = tag.amountGrams ?: tag.loggedFood?.amount ?: 100.0
                        val gramsLabel = kotlin.math.round(currentGrams).toInt()
                        Text("Gramos (${gramsLabel}g)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
                        // B11: tope dinámico — "1.5 kg de arroz" (1500g) no debe colapsar a 500g.
                        val gramsMax = maxOf(600.0, kotlin.math.round(currentGrams * 2.0))
                        Slider(
                            value = (currentGrams / gramsMax).toFloat().coerceIn(0f, 1f),
                            onValueChange = { onGramsChange(kotlin.math.round(it * gramsMax)) },
                            valueRange = 0f..1f,
                        )
                    }

                    Text("Ajustar macros", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            MacroOverrideCol("Calorías", logged?.calories ?: 0.0, step = 5.0, onChange = onCaloriesChange)
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            MacroOverrideCol("Proteína", logged?.protein ?: 0.0, step = 1.0, onChange = onProteinChange)
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            MacroOverrideCol("Carbos", logged?.carbs ?: 0.0, step = 1.0, onChange = onCarbsChange)
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            MacroOverrideCol("Grasas", logged?.fats ?: 0.0, step = 1.0, onChange = onFatsChange)
                        }
                    }

                    if (logged != null) {
                        Text(
                            "Fibra: ${kotlin.math.round(logged.fiber).toInt()}g · Azúcar: ${kotlin.math.round(logged.sugar).toInt()}g · Sodio: ${kotlin.math.round(logged.sodiumMg).toInt()}mg",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (tag.needsCookingClarification) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    when (tag.clarificationKind) {
                                        CookingStateResolver.ClarificationKind.DRY_VS_COOKED ->
                                            "¿Estaba seco o ya cocido/hidratado?"
                                        CookingStateResolver.ClarificationKind.RAW_VS_COOKED ->
                                            "¿El peso es en crudo o ya cocido?"
                                        else -> "Aclara el estado de cocción"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Los gramos que escribiste se mantienen; solo cambian los macros.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    val options = when (tag.clarificationKind) {
                                        CookingStateResolver.ClarificationKind.DRY_VS_COOKED ->
                                            listOf(false to "Seco", true to "Cocido")
                                        else ->
                                            listOf(false to "Crudo", true to "Cocido")
                                    }
                                    options.forEach { (wantCooked, label) ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { onCookingClarification(wantCooked) },
                                        ) {
                                            Text(
                                                text = label,
                                                modifier = Modifier.padding(vertical = 6.dp),
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                }
                                TextButton(onClick = onUnsure, modifier = Modifier.fillMaxWidth()) {
                                    Text("No estoy seguro; usar estimación")
                                }
                            }
                        }
                    }

                    if (tag.oilApplied && (tag.cookingMethod == CookingMethod.FRITO || tag.cookingMethod == CookingMethod.EMPANIZADO_FRITO)) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "Se añadió grasa por tipo de cocción",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Ajusta la cantidad de aceite absorbido para recalcular automáticamente:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    listOf("poco" to "Poco", "medio" to "Medio", "abundante" to "Abundante").forEach { (level, label) ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (tag.oilLevel == level) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { onOilLevelChange(level) },
                                        ) {
                                            Text(
                                                text = label,
                                                modifier = Modifier.padding(vertical = 6.dp),
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (tag.oilLevel == level) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (!tag.isResolved) {
                        Text("Resoluciones sugeridas:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
                        val unresolvedQuery = tag.tag
                        var suggestions by remember(unresolvedQuery, foodDatabase.size) { mutableStateOf<List<FoodCandidate>>(emptyList()) }
                        LaunchedEffect(unresolvedQuery) {
                            suggestions = nutritionRepo.searchFoodCandidates(unresolvedQuery, limit = 5)
                        }
                        if (suggestions.isEmpty()) {
                            Text("No se encontraron coincidencias.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        } else {
                            suggestions.forEach { candidate ->
                                val food = candidate.food
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        nutritionRepo.recordFoodSelection(unresolvedQuery, food)
                                        onResolve(food)
                                    },
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Text(food.name.trim().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                when (candidate.confidence) {
                                                    SearchConfidence.HIGH -> "Alta"
                                                    SearchConfidence.MEDIUM -> "Media"
                                                    SearchConfidence.LOW -> "Baja"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Text("${kotlin.math.round(food.calories).toInt()} kcal / ${food.servingSize.toInt()}${food.unit}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun MacroOverrideCol(label: String, value: Double, step: Double = 1.0, onChange: (Double) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChange((value - step).coerceAtLeast(0.0)) }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Remove, null, modifier = Modifier.size(12.dp))
            }
            Text(
                text = "${kotlin.math.round(value).toInt()}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(36.dp),
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = { onChange((value + step).coerceAtMost(9999.0)) }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(12.dp))
            }
        }
    }
}

// ─── IT3: utensilios configurables ───────────────────────────────────────────

private data class UtensilSpec(
    val key: String,
    val label: String,
    val range: ClosedFloatingPointRange<Float>,
)

private val CONFIGURABLE_UTENSILS = listOf(
    UtensilSpec("cucharadita", "Cucharadita", 3f..10f),
    UtensilSpec("cucharada", "Cucharada", 10f..25f),
    UtensilSpec("cucharon", "Cucharón", 60f..150f),
    UtensilSpec("taza", "Taza", 150f..400f),
    UtensilSpec("vaso", "Vaso", 150f..400f),
    UtensilSpec("plato", "Plato", 150f..400f),
    UtensilSpec("plato_hondo", "Plato hondo", 250f..600f),
    UtensilSpec("bol", "Bol", 200f..500f),
    UtensilSpec("copa", "Copa", 100f..300f),
)

@Composable
private fun UtensilSettingsDialog(
    values: Map<String, Float>,
    onValueChange: (String, Float) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    KpknAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajustar medidas") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Cuánto consideras que cabe en cada utensilio de tu casa. Se usa al escribir 'una taza', 'un plato', etc.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                CONFIGURABLE_UTENSILS.forEach { spec ->
                    val current = values[spec.key] ?: spec.range.start
                    Text(
                        "${spec.label}: ${current.toInt()} ml",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Slider(
                        value = current.coerceIn(spec.range.start, spec.range.endInclusive),
                        onValueChange = { onValueChange(spec.key, it) },
                        valueRange = spec.range,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}
