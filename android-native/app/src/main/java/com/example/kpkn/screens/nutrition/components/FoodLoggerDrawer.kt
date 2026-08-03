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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.runtime.collectAsState
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.models.*
import com.example.kpkn.data.food.findFoodByNormalized
import com.example.kpkn.data.food.findFoodExactByNormalized
import com.example.kpkn.data.remote.DeepSeekV4FlashClient
import com.example.kpkn.data.diagnostics.KpknDiagnosticLogger
import com.example.kpkn.data.secure.DeepSeekCredentialStore
import com.example.kpkn.data.remote.AiNutritionRequest
import com.example.kpkn.data.remote.AiNutritionResult
import com.example.kpkn.domain.nutrition.SmartFoodResolver
import com.example.kpkn.domain.nutrition.CookingStateResolver
import com.example.kpkn.domain.nutrition.scaleFoodByPortion
import com.example.kpkn.domain.nutrition.createLoggedFood
import com.example.kpkn.domain.nutrition.parseMealDescription
import com.example.kpkn.domain.nutrition.reconcileParsedFoodItems
import com.example.kpkn.domain.nutrition.FoodCombinationParser
import com.example.kpkn.domain.nutrition.round1
import com.example.kpkn.domain.nutrition.ContextDetector
import com.example.kpkn.domain.nutrition.FoodIdentity
import com.example.kpkn.domain.nutrition.FoodResolutionStatus
import com.example.kpkn.domain.nutrition.NutritionSourceKind
import com.example.kpkn.domain.nutrition.getContextualDefaultServingSize
import com.example.kpkn.domain.nutrition.COOKING_FACTORS
import com.example.kpkn.domain.nutrition.SemanticPortionRetriever
import com.example.kpkn.domain.nutrition.MacroValidator
import com.example.kpkn.domain.nutrition.TagResolver
import com.example.kpkn.domain.nutrition.FoodResolutionPort
import com.example.kpkn.domain.nutrition.ResolvedTag
import com.example.kpkn.domain.nutrition.mergeTagsPreservingManualEdits
import com.example.kpkn.domain.nutrition.applyModifierScale
import com.example.kpkn.domain.nutrition.adjustLoggedFoodForOil
import com.example.kpkn.domain.nutrition.stripOilFromLoggedFood
import com.example.kpkn.domain.nutrition.scalingForIntent
import com.example.kpkn.ui.components.KpknSheet
import java.util.UUID
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

private enum class AnalysisNoticeTone { INFO, WARNING }

private data class AnalysisNotice(
    val title: String,
    val message: String,
    val tone: AnalysisNoticeTone,
)

private data class ResolvedTag(
    val id: String = UUID.randomUUID().toString(),
    val tag: String,
    val portion: PortionPreset = PortionPreset.MEDIUM,
    val quantity: Double = 1.0,
    val amountGrams: Double? = null,
    val cookingMethod: CookingMethod? = null,
    val foodItem: FoodItem? = null,
    val loggedFood: LoggedFood? = null,
    val isResolved: Boolean = false,
    val isFuzzyMatch: Boolean = false,
    val analysisSource: AnalysisSource = AnalysisSource.RULES,
    val statusText: String = "Pendiente",
    val isExpanded: Boolean = false,
    val oilLevel: String = "medio",
    val isExcluded: Boolean = false,
    val hasManualEdits: Boolean = false,
    val amountIntent: AmountIntent = AmountIntent.UNSPECIFIED,
    val needsCookingClarification: Boolean = false,
    val clarificationKind: CookingStateResolver.ClarificationKind = CookingStateResolver.ClarificationKind.NONE,
    /** When true, food macros already include frying — do not add oil grams. */
    val oilApplied: Boolean = false,
)

private fun scalingForIntent(
    intent: AmountIntent,
    portionAdj: Double,
    proteinB: Double,
): Pair<Double, Double> {
    return if (intent == AmountIntent.EXPLICIT_MASS || intent == AmountIntent.RESOLVED_SUBJECTIVE) {
        1.0 to 0.0
    } else {
        portionAdj to proteinB
    }
}

private fun applyModifierScale(logged: LoggedFood, scale: MacroOverrides?): LoggedFood {
    if (scale == null) return logged
    val kcal = scale.calories ?: 1.0
    val prot = scale.protein ?: 1.0
    val carb = scale.carbs ?: 1.0
    val fat = scale.fats ?: 1.0
    if (kcal == 1.0 && prot == 1.0 && carb == 1.0 && fat == 1.0) return logged
    return logged.copy(
        calories = kotlin.math.round(logged.calories * kcal),
        protein = kotlin.math.round(logged.protein * prot * 10) / 10.0,
        carbs = kotlin.math.round(logged.carbs * carb * 10) / 10.0,
        fats = kotlin.math.round(logged.fats * fat * 10) / 10.0,
    )
}

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
private fun mergeTagsPreservingManualEdits(oldTags: List<ResolvedTag>, newTags: List<ResolvedTag>): List<ResolvedTag> {
    val oldEditable = oldTags.filter { it.hasManualEdits }
    val merged = newTags.toMutableList()
    
    for (oldTag in oldEditable) {
        val matchIdx = merged.indexOfFirst { newTag ->
            newTag.tag.lowercase() == oldTag.tag.lowercase()
        }
        if (matchIdx >= 0) {
            merged[matchIdx] = oldTag
        } else {
            merged.add(oldTag)
        }
    }
    return merged
}

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

    // D14 (beta): comparación con IA — panel de SOLO LECTURA. La IA entrega su
    // propia interpretación en paralelo; no toca tags, aprendizaje ni templates.
    // Se eliminará cuando el sistema local sea plenamente operativo.
    var aiComparison by remember { mutableStateOf<AiNutritionResult?>(null) }
    var isAiComparing by remember { mutableStateOf(false) }
    var aiComparisonError by remember { mutableStateOf<String?>(null) }

    // Modo de análisis: siempre determinístico con API externa opcional
    var showApiKey by remember { mutableStateOf(false) }
    var showApiConfigDialog by remember { mutableStateOf(false) }
    var apiDraftKey by remember { mutableStateOf("") }
    var apiDraftFallback by remember { mutableStateOf(true) }
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

    fun openApiConfigDialog() {
        apiDraftKey = DeepSeekCredentialStore.read(context).orEmpty()
        apiDraftFallback = settings.aiFallbackEnabled
        showApiKey = false
        showApiConfigDialog = true
    }

    suspend fun resolveTags(parsed: ParsedMealDescription) {
        val resolver = TagResolver(object : FoodResolutionPort {
            override suspend fun resolveSmart(tag: String, brandHint: String?, contextHint: String?) =
                nutritionRepo.resolveFoodWithSmartResolver(tag, brandHint, contextHint)

            override suspend fun getFoodById(id: String) = nutritionRepo.getFoodById(id)

            override suspend fun staticFood(tag: String): FoodItem? =
                findFoodByNormalized(tag)
                    ?: nutritionRepo.searchFoodCandidates(tag, limit = 1).firstOrNull()?.food

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
        })

        val result = resolver.resolveAll(parsed, detectedContext)

        detectedContext = result.second
        val newTags = result.first
        val mergedTags = mergeTagsPreservingManualEdits(tags, newTags)
        tags = mergedTags
        reviewRequired = mergedTags.any { !it.isExcluded && !it.isResolved }
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
            engine.startsWith("external-api-failed-fallback-") -> AnalysisNotice(
                title = "Se cambió a un análisis alternativo",
                message = "No llegó una respuesta útil a tiempo, así que preparamos el registro con otra lectura para que no tengas que empezar de nuevo.",
                tone = AnalysisNoticeTone.WARNING,
            )
            engine.startsWith("external-api-timeout-fallback-") -> AnalysisNotice(
                title = "La lectura demoró demasiado",
                message = "Usamos una alternativa automática para seguir con tu registro sin bloquearte.",
                tone = AnalysisNoticeTone.WARNING,
            )
            engine.startsWith("external-api-empty-fallback-") -> AnalysisNotice(
                title = "Revisa las cantidades sugeridas",
                message = "La primera lectura no alcanzó para completar todo, así que armamos una propuesta base para que la confirmes.",
                tone = AnalysisNoticeTone.INFO,
            )
            engine.startsWith("external-api-no-key-fallback-") -> AnalysisNotice(
                title = "Se usó el modo simple",
                message = "La comida se preparó con el modo básico de registro. Puedes revisar cantidades y guardar normalmente.",
                tone = AnalysisNoticeTone.INFO,
            )
            engine == "external-api-empty" -> AnalysisNotice(
                title = "No se pudo completar bien la comida",
                message = "Prueba una descripción más concreta para obtener un resultado más útil.",
                tone = AnalysisNoticeTone.WARNING,
            )
            engine == "external-api-failed" -> AnalysisNotice(
                title = "No se pudo interpretar la comida",
                message = "Hubo un problema al procesar la descripción. Intenta de nuevo o registra los alimentos manualmente.",
                tone = AnalysisNoticeTone.WARNING,
            )
            engine == "external-api-timeout" -> AnalysisNotice(
                title = "La lectura tomó demasiado tiempo",
                message = "Intenta nuevamente o agrega los alimentos manualmente si quieres avanzar más rápido.",
                tone = AnalysisNoticeTone.WARNING,
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

    fun analyzeDescription() {
        if (description.isBlank() || isAnalyzing) return

        analysisNotice = null
        detectedContext = ContextDetector.detect(description)
        analysisStage = if (settings.useApiForDescriptions) ParseStage.ESTIMATING else ParseStage.INTERPRETING
        analysisStartedAtMs = System.currentTimeMillis()
        analysisElapsedMs = 0L
        KpknDiagnosticLogger.event(
            namespace = "nutrition",
            name = "analysis_started",
            fields = mapOf(
                "usesDeepSeek" to settings.useApiForDescriptions,
                "descriptionLength" to description.length,
            ),
        )

        nutritionRepo.findMealTemplateMatch(description)?.let { template ->
            tags = template.foods.map { food ->
                val foodItem = findFoodByNormalized(food.foodName)
                ResolvedTag(
                    tag = food.foodName,
                    portion = food.portionPreset ?: PortionPreset.MEDIUM,
                    quantity = food.quantity,
                    amountGrams = food.amount.takeIf { it > 0 },
                    cookingMethod = food.cookingMethod,
                    foodItem = foodItem,
                    loggedFood = food.copy(analysisSource = AnalysisSource.USER_MEMORY),
                    // A3: la memoria del usuario es una SUGERENCIA, nunca una
                    // autoconfirmación: un log erróneo no puede propagarse en silencio.
                    isResolved = false,
                    isFuzzyMatch = true,
                    analysisSource = AnalysisSource.USER_MEMORY,
                    statusText = "Coincide con tu comida habitual: revisa los alimentos y cantidades antes de guardar.",
                    resolutionStatus = FoodResolutionStatus.NEEDS_CONFIRMATION,
                )
            }
            lastAnalyzedDescription = description
            analysisStage = null
            analysisStartedAtMs = 0L
            reviewRequired = true
            return
        }

        isAnalyzing = true
        scope.launch {
            try {
                nutritionRepo.prepareSemanticDataset()
                val descriptionRetrieval = withContext(Dispatchers.Default) {
                    SemanticPortionRetriever.retrieve(description)
                }
                detectedContext = ContextDetector.detect(description)
                val parsed = if (settings.useApiForDescriptions) {
                    val apiService = DeepSeekV4FlashClient(context)
                    val knownFoods = nutritionRepo.mealTemplates.value.flatMap { template ->
                        buildList {
                            add(template.name)
                            if (template.description.isNotBlank()) add(template.description)
                            template.foods.forEach { add(it.foodName) }
                        }
                    }
                    val request = AiNutritionRequest(
                        description = description,
                        knownFoods = knownFoods,
                    )
                    val result = withContext(Dispatchers.IO) {
                        apiService.analyzeNutrition(request)
                    }
                    result.fold(
                        onSuccess = { aiResult ->
                            val rawItems = aiResult.items.map { item ->
                                ParsedMealItem(
                                    tag = item.canonicalName.ifBlank { item.rawText },
                                    quantity = item.quantity?.toDouble() ?: 1.0,
                                    amountGrams = item.grams,
                                    cookingMethod = item.preparation?.let { prep ->
                                        when (prep.lowercase()) {
                                            "cocido" -> CookingMethod.COCIDO
                                            "plancha" -> CookingMethod.PLANCHA
                                            "horno" -> CookingMethod.HORNO
                                            "frito" -> CookingMethod.FRITO
                                            "crudo" -> CookingMethod.CRUDO
                                            else -> null
                                        }
                                    },
                                    portion = PortionPreset.MEDIUM,
                                    analysisSource = AnalysisSource.EXTERNAL_API_ESTIMATE,
                                    macroOverrides = item.nutritionPer100g?.let {
                                        MacroOverrides(
                                            calories = it.calories,
                                            protein = it.protein,
                                            carbs = it.carbs,
                                            fats = it.fats,
                                        )
                                    },
                                    reviewRequired = item.reviewRequired,
                                    analysisConfidence = item.confidence,
                                    // R5: gramos explícitos del LLM quedan bloqueados (no re-escalar por contexto/priors)
                                    amountIntent = if (item.grams != null) AmountIntent.EXPLICIT_MASS else AmountIntent.UNSPECIFIED,
                                )
                            }
                            val items = reconcileParsedFoodItems(rawItems)
                            ParsedMealDescription(
                                items = items,
                                rawDescription = description,
                                overallConfidence = aiResult.overallConfidence,
                                analysisEngine = if (aiResult.usedModel) "external-api" else "deterministic",
                                modelVersion = aiResult.modelVersion,
                                containsEstimatedItems = true,
                                requiresReview = items.any { it.reviewRequired },
                            )
                        },
                        onFailure = { error ->
                            if (settings.aiFallbackEnabled) {
                                val fallback = withContext(Dispatchers.Default) {
                                    parseMealDescription(description, descriptionRetrieval)
                                }
                                fallback.copy(
                                    analysisEngine = "external-api-failed-fallback-deepseek"
                                )
                            } else {
                                ParsedMealDescription(
                                    items = emptyList(),
                                    rawDescription = description,
                                    analysisEngine = "external-api-failed",
                                )
                            }
                        }
                    )
                } else {
                    val userKnownFoods = nutritionRepo.mealTemplates.value.flatMap { template ->
                        buildList {
                            add(template.name)
                            if (template.description.isNotBlank()) add(template.description)
                            template.foods.forEach { food ->
                                add(food.foodName)
                            }
                        }
                    }
                    withContext(Dispatchers.Default) {
                        parseMealDescription(description, descriptionRetrieval)
                    }
                }
                resolveTags(parsed)
                lastAnalyzedDescription = description
                analysisNotice = buildAnalysisNotice(parsed) ?: datasetNotReadyNotice()
                if (parsed.aiInferredFoods.isNotEmpty()) {
                    nutritionRepo.saveAiInferredFoods(parsed.aiInferredFoods)
                }
            } catch (e: Exception) {
                android.util.Log.w("FoodLogger", "Parse failed, usando determinístico", e)
                val fallbackParsed = withContext(Dispatchers.Default) {
                    parseMealDescription(
                        description,
                        SemanticPortionRetriever.retrieve(description),
                    )
                }
                resolveTags(fallbackParsed)
                lastAnalyzedDescription = description
                analysisNotice = AnalysisNotice(
                    title = "No se pudo leer la comida completa",
                    message = "Preparamos una versión base del registro para que puedas revisarla y guardar igual.",
                    tone = AnalysisNoticeTone.WARNING,
                )
            } finally {
                KpknDiagnosticLogger.event(
                    namespace = "nutrition",
                    name = "analysis_finished",
                    fields = mapOf(
                        "tagCount" to tags.size,
                        "usesDeepSeek" to settings.useApiForDescriptions,
                    ),
                )
                isAnalyzing = false
                analysisStage = null
                analysisStartedAtMs = 0L
            }
        }
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

    /**
     * D14 (beta): consulta la IA por separado y muestra SU interpretación como
     * referencia de solo lectura. No persiste nada, no alimenta aprendizaje y
     * no modifica los tags del registro local.
     */
    fun compareWithAi() {
        if (description.isBlank() || isAiComparing) return
        isAiComparing = true
        aiComparison = null
        aiComparisonError = null
        scope.launch {
            try {
                val apiService = DeepSeekV4FlashClient(context)
                val request = AiNutritionRequest(description = description)
                val result = withContext(Dispatchers.IO) {
                    apiService.analyzeNutrition(request)
                }
                result.fold(
                    onSuccess = { aiComparison = it },
                    onFailure = { aiComparisonError = it.message ?: "La IA no respondió." },
                )
            } catch (e: Exception) {
                aiComparisonError = e.message ?: "Error al consultar la IA."
            } finally {
                isAiComparing = false
            }
        }
    }

    fun resolveFood(tagId: String, food: FoodItem) {
        val portionAdj = detectedContext?.portionAdjustment ?: 1.0
        val proteinB = detectedContext?.proteinAdjustment ?: 0.0
        // IT2: conectar el aprendizaje del resolver — una corrección manual del
        // usuario debe persistir para futuras resoluciones (antes era código muerto).
        val targetTag = tags.firstOrNull { it.id == tagId }
        if (targetTag != null) {
            nutritionRepo.recordLearnedResolution(
                query = targetTag.tag,
                brandHint = null,
                foodId = food.id,
                portionGrams = targetTag.amountGrams,
                cookingMethod = targetTag.cookingMethod?.name,
            )
        }
        tags = tags.map { tag ->
            if (tag.id == tagId) {
                val (adj, boost) = scalingForIntent(tag.amountIntent, portionAdj, proteinB)
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
                    proteinBoost = boost,
                )
                val adjustedLogged = if (applyOil) {
                    adjustLoggedFoodForOil(logged, tag.cookingMethod, tag.oilLevel, foodName = food.name)
                } else {
                    logged.copy(cookingMethod = tag.cookingMethod ?: logged.cookingMethod)
                }
                tag.copy(
                    foodItem = food,
                    loggedFood = adjustedLogged.copy(analysisSource = AnalysisSource.DATABASE),
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
                )
            } else tag
        }
    }

    fun confirmEstimate(tagId: String) {
        tags = tags.map { tag ->
            if (tag.id == tagId && tag.loggedFood != null) {
                tag.copy(
                    isResolved = true,
                    resolutionStatus = FoodResolutionStatus.CONFIRMED_ESTIMATE,
                    statusText = "Estimación confirmada por ti.",
                    hasManualEdits = true,
                )
            } else tag
        }
        reviewRequired = tags.any { !it.isExcluded && !it.isResolved }
    }
    fun updateTagPortion(tagId: String, portion: PortionPreset) {
        val portionAdj = detectedContext?.portionAdjustment ?: 1.0
        val proteinB = detectedContext?.proteinAdjustment ?: 0.0
        tags = tags.map { tag ->
            if (tag.id == tagId) {
                val food = tag.foodItem
                val (adj, boost) = scalingForIntent(tag.amountIntent, portionAdj, proteinB)
                if (food != null && tag.analysisSource != AnalysisSource.LOCAL_AI_ESTIMATE && tag.analysisSource != AnalysisSource.EXTERNAL_API_ESTIMATE) {
                    val multiplier = PORTION_MULTIPLIERS[portion] ?: 1.0
                    val newGrams = food.servingSize * tag.quantity * multiplier
                    val usePrepared = CookingStateResolver.isAlreadyPreparedForMethod(food, tag.cookingMethod)
                    val scaleMethod = if (usePrepared) null else tag.cookingMethod
                    val logged = scaleFoodByPortion(
                        food = food,
                        quantity = tag.quantity,
                        portion = portion,
                        amountGrams = newGrams,
                        cookingMethod = scaleMethod,
                        portionAdjustment = adj,
                        proteinBoost = boost,
                    )
                    val adjustedLogged = if (tag.oilApplied) {
                        adjustLoggedFoodForOil(logged, tag.cookingMethod, tag.oilLevel, foodName = food.name)
                    } else logged
                    tag.copy(
                        portion = portion,
                        amountGrams = newGrams,
                        amountIntent = AmountIntent.EXPLICIT_MASS,
                        loggedFood = adjustedLogged,
                        hasManualEdits = true,
                    )
                } else if (tag.loggedFood != null) {
                    val multiplier = PORTION_MULTIPLIERS[portion] ?: 1.0
                    val baseGrams = tag.amountGrams ?: tag.loggedFood.amount.takeIf { it > 0 } ?: 100.0
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
                        amountIntent = AmountIntent.EXPLICIT_MASS,
                        loggedFood = scaledLogged,
                        hasManualEdits = true,
                    )
                } else {
                    tag.copy(portion = portion, hasManualEdits = true)
                }
            } else tag
        }
    }

    fun updateTagGrams(tagId: String, grams: Double) {
        val portionAdj = detectedContext?.portionAdjustment ?: 1.0
        val proteinB = detectedContext?.proteinAdjustment ?: 0.0
        tags = tags.map { tag ->
            if (tag.id == tagId) {
                val food = tag.foodItem
                val (adj, boost) = scalingForIntent(AmountIntent.EXPLICIT_MASS, portionAdj, proteinB)
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
                        proteinBoost = boost,
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
                    amountIntent = AmountIntent.EXPLICIT_MASS,
                    loggedFood = logged,
                    hasManualEdits = true,
                )
            } else tag
        }
    }

    fun updateTagOilLevel(tagId: String, oilLevel: String) {
        val portionAdj = detectedContext?.portionAdjustment ?: 1.0
        val proteinB = detectedContext?.proteinAdjustment ?: 0.0
        tags = tags.map { tag ->
            if (tag.id != tagId) return@map tag
            if (!tag.oilApplied && tag.cookingMethod != CookingMethod.FRITO &&
                tag.cookingMethod != CookingMethod.EMPANIZADO_FRITO
            ) {
                return@map tag.copy(oilLevel = oilLevel, hasManualEdits = true)
            }
            val food = tag.foodItem
            val (adj, boost) = scalingForIntent(tag.amountIntent, portionAdj, proteinB)
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
                    proteinBoost = boost,
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
    }

    fun updateTagCookingClarification(tagId: String, wantCooked: Boolean) {
        val portionAdj = detectedContext?.portionAdjustment ?: 1.0
        val proteinB = detectedContext?.proteinAdjustment ?: 0.0
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
            val (adj, boost) = scalingForIntent(tag.amountIntent, portionAdj, proteinB)
            val usePrepared = CookingStateResolver.isAlreadyPreparedForMethod(food, method)
            val scaleMethod = if (usePrepared) null else method
            val logged = scaleFoodByPortion(
                food = food,
                quantity = tag.quantity,
                portion = tag.portion,
                amountGrams = tag.amountGrams,
                cookingMethod = scaleMethod,
                portionAdjustment = adj,
                proteinBoost = boost,
            )
            tag.copy(
                cookingMethod = method,
                foodItem = food,
                loggedFood = logged.copy(analysisSource = AnalysisSource.DATABASE, cookingMethod = method),
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
            )
        }
        reviewRequired = tags.any { !it.isExcluded && !it.isResolved }
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
        if (activeTags.any { !it.isResolved }) {
            reviewRequired = true
            return
        }
        val resolvedFoods = activeTags.mapNotNull { it.loggedFood }
        if (resolvedFoods.isEmpty()) {
            reviewRequired = true
            return
        }
        val hasNegativeMacros = resolvedFoods.any {
            it.calories < 0.0 || it.protein < 0.0 || it.carbs < 0.0 || it.fats < 0.0
        }
        if (hasNegativeMacros) {
            reviewRequired = true
            return
        }
        val hasTooLargeValues = resolvedFoods.any {
            it.calories > 10000.0 || it.protein > 1000.0 || it.carbs > 1000.0 || it.fats > 1000.0
        }
        if (hasTooLargeValues) {
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

    if (showApiConfigDialog) {
        KpknAlertDialog(
            onDismissRequest = { showApiConfigDialog = false },
            title = { Text("Configurar DeepSeek V4 Flash") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("La app usa exclusivamente deepseek-v4-flash. La key se cifra con Android Keystore.")
                    TextField(
                        value = apiDraftKey,
                        onValueChange = { apiDraftKey = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("API key de DeepSeek V4 Flash") },
                        singleLine = true,
                        visualTransformation = if (showApiKey) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showApiKey = !showApiKey }) {
                                Icon(
                                    imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showApiKey) "Ocultar API key" else "Mostrar API key",
                                )
                            }
                        },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Fallback local", fontWeight = FontWeight.SemiBold)
                            Text("Si DeepSeek falla, conservar el parser local.")
                        }
                        Switch(checked = apiDraftFallback, onCheckedChange = { apiDraftFallback = it })
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiConfigDialog = false }) { Text("Cancelar") }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = apiDraftKey.trim()
                        if (trimmed.isBlank()) DeepSeekCredentialStore.clear(context)
                        else DeepSeekCredentialStore.write(context, trimmed)
                        programRepo.updateSettings { current ->
                            current.copy(
                                apiProvider = ApiProvider.DEEPSEEK,
                                apiKeys = ApiKeys(),
                                aiFallbackEnabled = apiDraftFallback,
                            )
                        }
                        showApiConfigDialog = false
                    },
                ) { Text("Guardar") }
            },
            shape = RoundedCornerShape(20.dp),
        )
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
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                    TabChip("Describir comida", activeTab == 0) { activeTab = 0 }
                    TabChip("Buscar alimento", activeTab == 1) { activeTab = 1 }
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
                            visible = settings.useApiForDescriptions && isAnalyzing,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            AiAnalysisPanel(
                                usingApi = settings.useApiForDescriptions,
                                providerLabel = "DeepSeek V4 Flash",
                                stage = analysisStage,
                                elapsedMs = analysisElapsedMs,
                                fallbackEnabled = settings.aiFallbackEnabled,
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

                        // D14 (beta): comparación con IA — referencia paralela de solo lectura.
                        OutlinedButton(
                            onClick = { compareWithAi() },
                            enabled = description.isNotBlank() && !isAiComparing && !isAnalyzing,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = PRO_COLOR,
                            ),
                        ) {
                            if (isAiComparing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = PRO_COLOR,
                                )
                            } else {
                                Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isAiComparing) "Consultando IA…" else "Comparar con IA (beta)")
                        }
                        if (aiComparison != null || aiComparisonError != null || isAiComparing) {
                            AiComparisonPanel(
                                result = aiComparison,
                                isLoading = isAiComparing,
                                error = aiComparisonError,
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
                        val logged = scaleFoodByPortion(selectedFood)
                        val tag = ResolvedTag(
                            tag = selectedFood.name,
                            portion = PortionPreset.MEDIUM,
                            quantity = 1.0,
                            amountGrams = selectedFood.servingSize,
                            foodItem = selectedFood,
                            loggedFood = logged,
                            isResolved = true,
                            statusText = "",
                            hasManualEdits = true,
                        )
                        nutritionRepo.recordFoodSelection(queryUsed, selectedFood)
                        tags = tags + tag
                        searchQuery = ""
                        searchResults = emptyList()
                        activeTab = 0
                    })
                }
            }

            // ── Tag List ────────────────────────────────────────────────────
            if (activeTab == 0 && tags.isNotEmpty()) {
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
                                nutritionRepo.recordFoodSelection(tag.tag, food)
                                resolveFood(tag.id, food)
                            },
                            onOilLevelChange = { level -> updateTagOilLevel(tag.id, level) },
                            onCookingClarification = { wantCooked ->
                                updateTagCookingClarification(tag.id, wantCooked)
                            },
                            onConfirmEstimate = { confirmEstimate(tag.id) },
                        )
                    }
                }
            }

            // ── Totals ──────────────────────────────────────────────────────
            if (activeTab == 0 && tags.isNotEmpty()) {
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

            // ── Save ────────────────────────────────────────────────────────
            if (activeTab == 0) {
                item {
                    val hasTags = tags.isNotEmpty()
                    val descriptionEdited = hasTags && description.trim() != lastAnalyzedDescription.trim()
                    Button(
                        onClick = {
                            if (descriptionEdited) analyzeDescription()
                            else if (hasTags) saveLog()
                            else analyzeDescription()
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (descriptionEdited) MaterialTheme.colorScheme.primary
                                             else if (hasTags) Color(0xFF2E7D32)
                                             else (if (settings.useApiForDescriptions) PRO_COLOR else MaterialTheme.colorScheme.primary)
                        ),
                        enabled = !isAnalyzing && (hasTags || description.isNotBlank())
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
                                       else (if (settings.useApiForDescriptions) Icons.Default.AutoAwesome else Icons.Default.FlashOn)
                            val label = if (descriptionEdited) "ACTUALIZAR Y BUSCAR"
                                        else if (hasTags) "GUARDAR"
                                        else "REGISTRAR"
                            Icon(icon, null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, fontWeight = FontWeight.Black)
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
private fun AiAnalysisPanel(
    usingApi: Boolean,
    providerLabel: String,
    stage: ParseStage?,
    elapsedMs: Long,
    fallbackEnabled: Boolean,
) {
    val messages = remember(usingApi, providerLabel, stage, fallbackEnabled) {
        when {
            usingApi -> listOf(
                "Leyendo tu descripción",
                "Ordenando alimentos, cantidades y porciones",
                if (fallbackEnabled) "Si hace falta, usaremos una alternativa para no detener el registro" else "Esperando una respuesta útil para completar la comida",
                "Revisando que el resumen tenga sentido",
            )
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
    val infiniteTransition = rememberInfiniteTransition(label = "ai-analysis")
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
        color = if (usingApi) Color(0xFF0F3557).copy(alpha = 0.09f) else PRO_COLOR.copy(alpha = 0.08f),
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
                        imageVector = if (usingApi) Icons.Default.CloudSync else Icons.Default.Memory,
                        contentDescription = null,
                        tint = if (usingApi) Color(0xFF1565C0) else PRO_COLOR,
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

            AnimatedContent(targetState = messages[messageIndex], label = "ai-message") { message ->
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
                                if (usingApi) Color(0xFF1565C0).copy(alpha = alpha)
                                else PRO_COLOR.copy(alpha = alpha)
                            )
                    )
                }
                Text(
                    text = if (usingApi) {
                        if (fallbackEnabled) "Estamos armando la mejor versión posible de tu comida"
                        else "Esperando una respuesta útil para completar el registro"
                    } else {
                        "Estamos interpretando la comida para dejarla lista para guardar"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = if (usingApi) Color(0xFF1565C0) else PRO_COLOR,
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
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(MEAL_OPTIONS) { (type, label) ->
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
private fun TabChip(label: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold,
        )
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
                            text = "${kotlin.math.round(logged.calories).toInt()} kcal · P ${kotlin.math.round(logged.protein).toInt()}g · C ${kotlin.math.round(logged.carbs).toInt()}g · G ${kotlin.math.round(logged.fats).toInt()}g",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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

                    if (tag.resolutionStatus == FoodResolutionStatus.NEEDS_CONFIRMATION && logged != null) {
                        Text(
                            "Macros estimados: confirma antes de guardar.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Button(onClick = onConfirmEstimate, modifier = Modifier.fillMaxWidth()) {
                            Text("Confirmar estimación")
                        }
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

                    if (!tag.needsCookingClarification && tag.reviewCandidates.isNotEmpty()) {
                        // R1: candidatos del resolver con su scoring ("¿Cuál de estos?")
                        Text("¿Cuál de estos?", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            tag.reviewCandidates.forEach { candidate ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            nutritionRepo.recordFoodSelection(tag.tag, candidate)
                                            onResolve(candidate)
                                        },
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(
                                            candidate.name.trim().replaceFirstChar { it.uppercase() },
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            "${kotlin.math.round(candidate.calories).toInt()} kcal / ${candidate.servingSize.toInt()}${candidate.unit}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    } else if (!tag.needsCookingClarification && !tag.isResolved) {
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

/**
 * D14 (beta): panel de SOLO LECTURA con la interpretación de la IA para la misma
 * descripción. No guarda nada: sirve de referencia/contraste con el registro
 * local mientras el sistema local no sea plenamente operativo.
 */
@Composable
private fun AiComparisonPanel(
    result: AiNutritionResult?,
    isLoading: Boolean,
    error: String?,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (error != null) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
        } else {
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(14.dp), tint = PRO_COLOR)
                Text(
                    text = if (error != null) "La IA no pudo responder" else "Interpretación de la IA (solo lectura)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            when {
                isLoading -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                    )
                    Text(
                        text = "La IA está interpretando la descripción…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                error != null -> {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                result?.items.isNullOrEmpty() -> {
                    Text(
                        text = "La IA no reconoció alimentos en esta descripción.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    result!!.items.forEach { item ->
                        val name = item.canonicalName.ifBlank { item.rawText }
                        val grams = item.grams
                        val macros = item.nutritionPer100g
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = name.trim().replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = buildString {
                                    if (grams != null) append("${kotlin.math.round(grams).toInt()} g")
                                    macros?.let { m ->
                                        if (grams != null) append(" · ")
                                        append("${kotlin.math.round(m.calories * (grams ?: 100.0) / 100.0).toInt()} kcal")
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    val model = result.modelVersion
                    if (!model.isNullOrBlank()) {
                        Text(
                            text = "Modelo: $model · no se guarda en tu registro",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
    }
}
