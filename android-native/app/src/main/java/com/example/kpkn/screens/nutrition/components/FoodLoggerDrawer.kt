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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.runtime.collectAsState
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.models.*
import com.example.kpkn.data.food.findFoodByNormalized
import com.example.kpkn.data.remote.ExternalAiService
import com.example.kpkn.data.remote.AiNutritionRequest
import com.example.kpkn.domain.nutrition.SmartFoodResolver
import com.example.kpkn.domain.nutrition.scaleFoodByPortion
import com.example.kpkn.domain.nutrition.createLoggedFood
import com.example.kpkn.domain.nutrition.parseMealDescription
import com.example.kpkn.domain.nutrition.FoodCombinationParser
import com.example.kpkn.domain.nutrition.round1
import com.example.kpkn.domain.nutrition.ContextDetector
import com.example.kpkn.domain.nutrition.getContextualDefaultServingSize
import com.example.kpkn.domain.nutrition.COOKING_FACTORS
import java.util.UUID
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    val quantity: Int = 1,
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
)

private fun adjustLoggedFoodForOil(logged: LoggedFood, method: CookingMethod?, oilLevel: String): LoggedFood {
    if (method != CookingMethod.FRITO && method != CookingMethod.EMPANIZADO_FRITO) {
        return logged
    }
    
    // Gramos de aceite absorbido estimados según nivel
    val oilGrams = when (oilLevel.lowercase()) {
        "poco" -> 3.0
        "abundante" -> 18.0
        else -> 8.0  // "medio"
    }
    
    val addedFat = oilGrams  // el aceite es ~100% grasa
    val addedCal = oilGrams * 9  // 9 kcal por gramo de grasa
    
    return logged.copy(
        fats = kotlin.math.round((logged.fats + addedFat) * 10.0) / 10.0,
        calories = kotlin.math.round(logged.calories + addedCal)
    )
}

private fun shouldUseAiLoggedFood(item: ParsedMealItem): Boolean {
    return item.macroOverrides != null && (
        item.analysisSource == AnalysisSource.LOCAL_AI_ESTIMATE ||
            item.analysisSource == AnalysisSource.EXTERNAL_API_ESTIMATE
    )
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

    // Modo de análisis: siempre determinístico con API externa opcional
    var showApiKey by remember { mutableStateOf(false) }
    var showApiConfigDialog by remember { mutableStateOf(false) }
    var apiDraftProvider by remember { mutableStateOf(ApiProvider.GEMINI) }
    var apiDraftKey by remember { mutableStateOf("") }
    var apiDraftFallback by remember { mutableStateOf(true) }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { value ->
            // Prevent accidental dismiss when there's content (description typed or foods added)
            if (value == SheetValue.Hidden) description.isBlank() && tags.isEmpty()
            else true
        },
    )
    val listState = rememberLazyListState()

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

    fun providerLabel(provider: ApiProvider): String = when (provider) {
        ApiProvider.GEMINI -> "Gemini 2 Flash Lite"
        ApiProvider.DEEPSEEK -> "DeepSeek"
        ApiProvider.GPT -> "GPT-4o Mini"
    }

    fun selectedApiKey(currentSettings: Settings, provider: ApiProvider): String {
        return when (provider) {
            ApiProvider.GEMINI -> currentSettings.apiKeys.gemini.orEmpty()
            ApiProvider.DEEPSEEK -> currentSettings.apiKeys.deepseek.orEmpty()
            ApiProvider.GPT -> currentSettings.apiKeys.gpt.orEmpty()
        }
    }

    fun openApiConfigDialog() {
        val provider = when (settings.apiProvider) {
            ApiProvider.GPT -> ApiProvider.GPT
            ApiProvider.DEEPSEEK -> ApiProvider.DEEPSEEK
            ApiProvider.GEMINI -> ApiProvider.GEMINI
        }
        apiDraftProvider = provider
        apiDraftKey = selectedApiKey(settings, provider)
        apiDraftFallback = settings.aiFallbackEnabled
        showApiKey = false
        showApiConfigDialog = true
    }

    suspend fun resolveTags(parsed: ParsedMealDescription) {
        val result = withContext(Dispatchers.Default) {
            val resolvedTags = mutableListOf<ResolvedTag>()
            val hasGreaseCooking = parsed.items.any { 
                it.cookingMethod == CookingMethod.FRITO || 
                it.cookingMethod == CookingMethod.EMPANIZADO_FRITO 
            }
            
            val contextResult = detectedContext ?: ContextDetector.detect(parsed.rawDescription)
            val portionAdj = contextResult.portionAdjustment
            val proteinB = contextResult.proteinAdjustment
            
            for (item in parsed.items) {
                if (hasGreaseCooking) {
                    val lowerTag = item.tag.lowercase().trim()
                    if (lowerTag == "aceite" || lowerTag == "aceite vegetal" || lowerTag == "aceite de oliva" || lowerTag == "aceite de maravilla" || lowerTag == "aceite de girasol") {
                        continue
                    }
                }

                // Phase B: Use SmartFoodResolver for full-DB fuzzy matching
                val smartResult = nutritionRepo.resolveFoodWithSmartResolver(item.tag, item.brandHint)
                val smartCandidate = smartResult.candidates.firstOrNull()

                // Fallback: static lookup + search
                val staticFood = findFoodByNormalized(item.tag)
                    ?: nutritionRepo.searchFoodCandidates(item.tag, limit = 1).firstOrNull()?.food

                // Prefer SmartFoodResolver result if it has good confidence
                val food = when {
                    smartResult.decision == SmartFoodResolver.Decision.AUTO_SELECT && smartCandidate != null -> {
                        // Auto-selected: use the smart match
                        nutritionRepo.getFoodById(smartCandidate.foodId) ?: staticFood
                    }
                    smartResult.decision == SmartFoodResolver.Decision.NEEDS_REVIEW && smartCandidate != null -> {
                        // Needs review: use best candidate but mark as fuzzy
                        nutritionRepo.getFoodById(smartCandidate.foodId) ?: staticFood
                    }
                    else -> staticFood
                }

                // Si hay método de cocción detectado y el alimento resuelto NO es crudo,
                // intentar buscar la variante cruda del mismo alimento
                val effectiveFood = if (item.cookingMethod != null && item.cookingMethod != CookingMethod.CRUDO && food != null) {
                    val foodName = food.name.lowercase()
                    if (foodName.contains("cocid") || foodName.contains("frit") || foodName.contains("plancha") || 
                        foodName.contains("horno") || foodName.contains("asad")) {
                        // Buscar variante cruda: quitar "(cocida)", "(frita)", etc. y buscar de nuevo
                        val rawName = foodName.replace(Regex("""\s*\((?:cocid[oa]|frit[oa]|plancha|horno|asad[oa]|vapor|parrilla)\)"""), "")
                            .replace(Regex("""\s+(?:cocid[oa]|frit[oa]|plancha|horno|asad[oa])"""), "")
                            .trim()
                        findFoodByNormalized(rawName) ?: food
                    } else food
                } else food

                val isSmartMatch = smartResult.decision != SmartFoodResolver.Decision.UNRESOLVED && smartCandidate != null

                val source = item.analysisSource
                val preferAiLoggedFood = shouldUseAiLoggedFood(item)
                
                val resolved = if (effectiveFood != null && !preferAiLoggedFood) {
                    val logged = scaleFoodByPortion(
                        food = effectiveFood,
                        quantity = item.quantity,
                        portion = item.portion,
                        amountGrams = item.amountGrams,
                        cookingMethod = item.cookingMethod,
                        portionAdjustment = portionAdj,
                        proteinBoost = proteinB
                    )
                    // Record learned resolution
                    nutritionRepo.recordLearnedResolution(item.tag, item.brandHint, effectiveFood.id, item.amountGrams, item.cookingMethod?.name)
                    ResolvedTag(
                        tag = item.tag,
                        portion = item.portion,
                        quantity = item.quantity,
                        amountGrams = item.amountGrams,
                        cookingMethod = item.cookingMethod,
                        foodItem = effectiveFood,
                        loggedFood = adjustLoggedFoodForOil(logged.copy(analysisSource = AnalysisSource.DATABASE), item.cookingMethod, "medio"),
                        isResolved = true,
                        isFuzzyMatch = isSmartMatch && smartCandidate?.confidence != SmartFoodResolver.Confidence.HIGH,
                        analysisSource = AnalysisSource.DATABASE,
                        statusText = "",
                        oilLevel = "medio",
                    )
                } else if (item.amountGrams != null && item.amountGrams > 0) {
                    val mac = item.macroOverrides
                    val logged = createLoggedFood(
                        foodName = item.tag,
                        amount = item.amountGrams,
                        calories = mac?.calories ?: 0.0,
                        protein = mac?.protein ?: 0.0,
                        carbs = mac?.carbs ?: 0.0,
                        fats = mac?.fats ?: 0.0,
                        fiber = 0.0,
                        sugar = 0.0,
                        sodiumMg = 0.0,
                        potassiumMg = 0.0,
                        waterMl = 0.0,
                        portion = item.portion,
                        cookingMethod = item.cookingMethod,
                    )

                    ResolvedTag(
                        tag = item.tag,
                        portion = item.portion,
                        quantity = item.quantity,
                        amountGrams = item.amountGrams,
                        cookingMethod = item.cookingMethod,
                        foodItem = null,
                        loggedFood = adjustLoggedFoodForOil(logged.copy(analysisSource = source), item.cookingMethod, "medio"),
                        isResolved = mac != null,
                        isFuzzyMatch = true,
                        analysisSource = source,
                        statusText = "",
                        oilLevel = "medio",
                    )
                } else {
                    ResolvedTag(
                        tag = item.tag,
                        portion = item.portion,
                        quantity = item.quantity,
                        foodItem = null,
                        loggedFood = null,
                        isResolved = false,
                        analysisSource = source,
                        statusText = "",
                    )
                }
                resolvedTags += resolved
            }

            // Composite food context capping
            val combination = FoodCombinationParser.parse(parsed.rawDescription)
            
            if (combination.confidence >= 0.70 && combination.accompaniments.isNotEmpty()) {
                val totalGrams = resolvedTags.sumOf { it.loggedFood?.amount ?: 0.0 }
                val baseGrams = combination.baseProportion * totalGrams

                for (accomp in combination.accompaniments) {
                    val matching = resolvedTags.filter { tag ->
                        val name = tag.foodItem?.name?.lowercase() ?: tag.tag.lowercase()
                        name.contains(accomp.food.lowercase()) || accomp.food.lowercase().contains(name)
                    }
                    for (match in matching) {
                        val existingFood = match.foodItem ?: continue
                        val existingLogged = match.loggedFood ?: continue

                        val maxAllowedGrams = when (accomp.role) {
                            FoodCombinationParser.Role.SAUCE -> {
                                val lowerName = existingFood.name.lowercase()
                                if (lowerName.contains("aceite") || lowerName.contains("oil") || lowerName.contains("mantequilla") || lowerName.contains("ghee") || lowerName.contains("margarina") || lowerName.contains("manteca") || lowerName.contains("mayonesa") || lowerName.contains("mayo")) {
                                    minOf(accomp.proportion * totalGrams, 15.0) // Fats capped tighter to 15g!
                                } else {
                                    minOf(accomp.proportion * totalGrams, 30.0)
                                }
                            }
                            FoodCombinationParser.Role.TOPPING -> minOf(accomp.proportion * totalGrams, 60.0)
                            FoodCombinationParser.Role.FILLING -> minOf(accomp.proportion * totalGrams, 80.0)
                            FoodCombinationParser.Role.SIDE, FoodCombinationParser.Role.STARCH,
                            FoodCombinationParser.Role.GARNISH -> null
                        }

                        if (maxAllowedGrams != null && existingLogged.amount > maxAllowedGrams && maxAllowedGrams > 1.0) {
                            val capped = scaleFoodByPortion(
                                food = existingFood,
                                quantity = match.quantity,
                                portion = PortionPreset.MEDIUM,
                                amountGrams = maxAllowedGrams,
                                cookingMethod = match.cookingMethod,
                                portionAdjustment = 1.0, // Capped explicitly
                                proteinBoost = proteinB
                            )
                            val idx = resolvedTags.indexOfFirst { it.id == match.id }
                            if (idx >= 0) {
                                resolvedTags[idx] = match.copy(
                                    loggedFood = capped.copy(analysisSource = existingLogged.analysisSource),
                                    amountGrams = maxAllowedGrams,
                                    portion = PortionPreset.SMALL,
                                    statusText = "${match.statusText} (comp)",
                                )
                            }
                        }
                    }
                }
            }
            Pair(resolvedTags, contextResult)
        }

        detectedContext = result.second
        tags = result.first
    }

    fun buildAnalysisNotice(parsed: ParsedMealDescription): AnalysisNotice? {
        val engine = parsed.analysisEngine
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

    fun analyzeDescription() {
        if (description.isBlank() || isAnalyzing) return

        analysisNotice = null
        detectedContext = ContextDetector.detect(description)
        analysisStage = if (settings.useApiForDescriptions) ParseStage.ESTIMATING else ParseStage.INTERPRETING
        analysisStartedAtMs = System.currentTimeMillis()
        analysisElapsedMs = 0L

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
                    isResolved = true,
                    isFuzzyMatch = true,
                    analysisSource = AnalysisSource.USER_MEMORY,
                    statusText = "",
                )
            }
            lastAnalyzedDescription = description
            analysisStage = null
            analysisStartedAtMs = 0L
            return
        }

        isAnalyzing = true
        scope.launch {
            try {
                val parsed = if (settings.useApiForDescriptions) {
                    val apiService = ExternalAiService(settings.apiKeys, settings.apiProvider)
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
                            val items = aiResult.items.map { item ->
                                ParsedMealItem(
                                    tag = item.canonicalName.ifBlank { item.rawText },
                                    quantity = item.quantity ?: 1,
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
                                )
                            }
                            ParsedMealDescription(
                                items = items,
                                rawDescription = description,
                                overallConfidence = aiResult.overallConfidence,
                                analysisEngine = if (aiResult.usedModel) "external-api" else "deterministic",
                                modelVersion = aiResult.modelVersion,
                            )
                        },
                        onFailure = { error ->
                            if (settings.aiFallbackEnabled) {
                                val fallback = withContext(Dispatchers.Default) {
                                    parseMealDescription(description)
                                }
                                fallback.copy(
                                    analysisEngine = "external-api-failed-fallback-${settings.apiProvider.name.lowercase()}"
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
                        parseMealDescription(description)
                    }
                }
                resolveTags(parsed)
                lastAnalyzedDescription = description
                analysisNotice = buildAnalysisNotice(parsed)
                if (parsed.aiInferredFoods.isNotEmpty()) {
                    nutritionRepo.saveAiInferredFoods(parsed.aiInferredFoods)
                }
            } catch (e: Exception) {
                android.util.Log.w("FoodLogger", "Parse failed, usando determinístico", e)
                val fallbackParsed = withContext(Dispatchers.Default) {
                    parseMealDescription(description)
                }
                resolveTags(fallbackParsed)
                lastAnalyzedDescription = description
                analysisNotice = AnalysisNotice(
                    title = "No se pudo leer la comida completa",
                    message = "Preparamos una versión base del registro para que puedas revisarla y guardar igual.",
                    tone = AnalysisNoticeTone.WARNING,
                )
            } finally {
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

    fun resolveFood(tagId: String, food: FoodItem) {
        val portionAdj = detectedContext?.portionAdjustment ?: 1.0
        val proteinB = detectedContext?.proteinAdjustment ?: 0.0
        tags = tags.map { tag ->
            if (tag.id == tagId) {
                if (tag.analysisSource == AnalysisSource.LOCAL_AI_ESTIMATE || tag.analysisSource == AnalysisSource.EXTERNAL_API_ESTIMATE) {
                    return@map tag.copy(foodItem = food, isFuzzyMatch = false)
                }
                val logged = scaleFoodByPortion(
                    food = food,
                    quantity = tag.quantity,
                    portion = tag.portion,
                    amountGrams = tag.amountGrams,
                    cookingMethod = tag.cookingMethod,
                    portionAdjustment = portionAdj,
                    proteinBoost = proteinB
                )
                val adjustedLogged = adjustLoggedFoodForOil(logged, tag.cookingMethod, tag.oilLevel)
                tag.copy(
                    foodItem = food,
                    loggedFood = adjustedLogged.copy(analysisSource = AnalysisSource.DATABASE),
                    isResolved = true,
                    isFuzzyMatch = false,
                    analysisSource = AnalysisSource.DATABASE,
                    statusText = "",
                )
            } else tag
        }
    }

    fun updateTagPortion(tagId: String, portion: PortionPreset) {
        val portionAdj = detectedContext?.portionAdjustment ?: 1.0
        val proteinB = detectedContext?.proteinAdjustment ?: 0.0
        tags = tags.map { tag ->
            if (tag.id == tagId) {
                val food = tag.foodItem
                if (food != null && tag.analysisSource != AnalysisSource.LOCAL_AI_ESTIMATE && tag.analysisSource != AnalysisSource.EXTERNAL_API_ESTIMATE) {
                    val multiplier = PORTION_MULTIPLIERS[portion] ?: 1.0
                    val newGrams = food.servingSize * tag.quantity * multiplier
                    val logged = scaleFoodByPortion(
                        food = food,
                        quantity = tag.quantity,
                        portion = portion,
                        amountGrams = newGrams,
                        cookingMethod = tag.cookingMethod,
                        portionAdjustment = portionAdj,
                        proteinBoost = proteinB
                    )
                    val adjustedLogged = adjustLoggedFoodForOil(logged, tag.cookingMethod, tag.oilLevel)
                    tag.copy(portion = portion, amountGrams = newGrams, loggedFood = adjustedLogged)
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
                        loggedFood = scaledLogged,
                    )
                } else {
                    tag.copy(portion = portion)
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
                val logged = if (food != null && tag.analysisSource != AnalysisSource.LOCAL_AI_ESTIMATE && tag.analysisSource != AnalysisSource.EXTERNAL_API_ESTIMATE) {
                    val baseLogged = scaleFoodByPortion(
                        food = food,
                        quantity = tag.quantity,
                        portion = tag.portion,
                        amountGrams = grams,
                        cookingMethod = tag.cookingMethod,
                        portionAdjustment = portionAdj,
                        proteinBoost = proteinB
                    )
                    adjustLoggedFoodForOil(baseLogged, tag.cookingMethod, tag.oilLevel)
                } else {
                    val old = tag.loggedFood
                    val baseGrams = tag.amountGrams ?: old?.amount ?: 100.0
                    val scale = if (baseGrams > 0.0) grams / baseGrams else 1.0
                    old?.copy(
                        amount = grams,
                        calories = old.calories * scale,
                        protein = old.protein * scale,
                        carbs = old.carbs * scale,
                        fats = old.fats * scale,
                    )
                }
                tag.copy(amountGrams = grams, loggedFood = logged)
            } else tag
        }
    }

    fun updateTagOilLevel(tagId: String, oilLevel: String) {
        val portionAdj = detectedContext?.portionAdjustment ?: 1.0
        val proteinB = detectedContext?.proteinAdjustment ?: 0.0
        tags = tags.map { tag ->
            if (tag.id == tagId) {
                val food = tag.foodItem
                if (food != null && tag.analysisSource != AnalysisSource.LOCAL_AI_ESTIMATE && tag.analysisSource != AnalysisSource.EXTERNAL_API_ESTIMATE) {
                    val logged = scaleFoodByPortion(
                        food = food,
                        quantity = tag.quantity,
                        portion = tag.portion,
                        amountGrams = tag.amountGrams,
                        cookingMethod = tag.cookingMethod,
                        portionAdjustment = portionAdj,
                        proteinBoost = proteinB
                    )
                    val adjustedLogged = adjustLoggedFoodForOil(logged, tag.cookingMethod, oilLevel)
                    tag.copy(oilLevel = oilLevel, loggedFood = adjustedLogged)
                } else if (tag.loggedFood != null) {
                    val old = tag.loggedFood
                    val cf = tag.cookingMethod?.let { COOKING_FACTORS[it] }
                    val baseFatsFactor = cf?.fats ?: 1.0
                    val excessFactor = baseFatsFactor - 1.0
                    val scaleStandard = when (tag.oilLevel.lowercase()) {
                        "poco" -> 0.5
                        "abundante" -> 1.6
                        else -> 1.0
                    }
                    val scaleNew = when (oilLevel.lowercase()) {
                        "poco" -> 0.5
                        "abundante" -> 1.6
                        else -> 1.0
                    }
                    if (baseFatsFactor > 1.0) {
                        val baseFats = old.fats / (1.0 + excessFactor * scaleStandard)
                        val newFats = kotlin.math.round((baseFats * (1.0 + excessFactor * scaleNew)) * 10.0) / 10.0
                        val diffFat = newFats - old.fats
                        val newCalories = kotlin.math.round(old.calories + diffFat * 9.0)
                        tag.copy(
                            oilLevel = oilLevel,
                            loggedFood = old.copy(fats = newFats.coerceAtLeast(0.0), calories = newCalories.coerceAtLeast(0.0))
                        )
                    } else {
                        tag.copy(oilLevel = oilLevel)
                    }
                } else {
                    tag.copy(oilLevel = oilLevel)
                }
            } else tag
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
                ))
            } else tag
        }
    }

    fun updateTagProtein(tagId: String, protein: Double) {
        tags = tags.map { tag ->
            if (tag.id == tagId) {
                val old = tag.loggedFood ?: return@map tag
                tag.copy(loggedFood = old.copy(
                    protein = protein,
                    calories = protein * 4 + old.carbs * 4 + old.fats * 9,
                ))
            } else tag
        }
    }

    fun updateTagCarbs(tagId: String, carbs: Double) {
        tags = tags.map { tag ->
            if (tag.id == tagId) {
                val old = tag.loggedFood ?: return@map tag
                tag.copy(loggedFood = old.copy(
                    carbs = carbs,
                    calories = old.protein * 4 + carbs * 4 + old.fats * 9,
                ))
            } else tag
        }
    }

    fun updateTagFats(tagId: String, fats: Double) {
        tags = tags.map { tag ->
            if (tag.id == tagId) {
                val old = tag.loggedFood ?: return@map tag
                tag.copy(loggedFood = old.copy(
                    fats = fats,
                    calories = old.protein * 4 + old.carbs * 4 + fats * 9,
                ))
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
        val resolvedFoods = tags.mapNotNull { it.loggedFood }
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
        showSuccess = true
        reviewRequired = false
    }

    val tagTotals = remember(tags) {
        DailyMacroTotals(
            calories = tags.sumOf { it.loggedFood?.calories ?: 0.0 },
            protein = tags.sumOf { it.loggedFood?.protein ?: 0.0 },
            carbs = tags.sumOf { it.loggedFood?.carbs ?: 0.0 },
            fats = tags.sumOf { it.loggedFood?.fats ?: 0.0 },
        )
    }

    if (showApiConfigDialog) {
        AlertDialog(
            onDismissRequest = { showApiConfigDialog = false },
            title = {
                Text(
                    text = "Configurar API personal",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Ingresa tu API key y define fallback. Guardar aplica todo de inmediato.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(ApiProvider.GEMINI, ApiProvider.DEEPSEEK, ApiProvider.GPT).forEach { provider ->
                            FilterChip(
                                selected = apiDraftProvider == provider,
                                onClick = {
                                    apiDraftProvider = provider
                                    apiDraftKey = selectedApiKey(settings, provider)
                                },
                                label = {
                                    Text(
                                        providerLabel(provider),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                },
                            )
                        }
                    }

                    TextField(
                        value = apiDraftKey,
                        onValueChange = { apiDraftKey = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("API Key ${providerLabel(apiDraftProvider)}") },
                        placeholder = { Text("Pega aquí tu key") },
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
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Fallback API -> Parser",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Si la API falla, usar Parser automáticamente",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = apiDraftFallback,
                            onCheckedChange = { apiDraftFallback = it },
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiConfigDialog = false }) {
                    Text("Cancelar")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = apiDraftKey.trim()
                        val keyOrNull = trimmed.ifBlank { null }
                        programRepo.updateSettings { current ->
                            current.copy(
                                apiProvider = apiDraftProvider,
                                aiFallbackEnabled = apiDraftFallback,
                                apiKeys = when (apiDraftProvider) {
                                    ApiProvider.GEMINI -> current.apiKeys.copy(gemini = keyOrNull)
                                    ApiProvider.DEEPSEEK -> current.apiKeys.copy(deepseek = keyOrNull)
                                    ApiProvider.GPT -> current.apiKeys.copy(gpt = keyOrNull)
                                },
                            )
                        }
                        showApiConfigDialog = false
                    },
                ) {
                    Text("Guardar")
                }
            },
            shape = RoundedCornerShape(20.dp),
        )
    }

    // ─── Sheet ───────────────────────────────────────────────────────────────

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .width(32.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
            )
        },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
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
                                providerLabel = providerLabel(settings.apiProvider),
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
                            quantity = 1,
                            amountGrams = selectedFood.servingSize,
                            foodItem = selectedFood,
                            loggedFood = logged,
                            isResolved = true,
                            statusText = "",
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
            Snackbar(
                modifier = Modifier.padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                containerColor = Color(0xFF4CAF50),
                contentColor = Color.White,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("¡Comida registrada!", fontWeight = FontWeight.Bold)
                }
            }
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
) {
    val logged = tag.loggedFood

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (tag.isResolved) MaterialTheme.colorScheme.surfaceContainer
            else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = tag.tag.trim().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                    if (logged != null) {
                        Text(
                            text = "${kotlin.math.round(logged.calories).toInt()} kcal · P ${kotlin.math.round(logged.protein).toInt()}g · C ${kotlin.math.round(logged.carbs).toInt()}g · G ${kotlin.math.round(logged.fats).toInt()}g",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        Text("Gramos (${kotlin.math.round(tag.amountGrams ?: tag.loggedFood?.amount ?: 100.0).toInt()}g)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
                        Slider(
                            value = ((tag.amountGrams ?: tag.loggedFood?.amount ?: 100.0) / 500.0).toFloat().coerceIn(0f, 1f),
                            onValueChange = { onGramsChange(kotlin.math.round(it * 500.0)) },
                            valueRange = 0f..1f,
                        )
                    }

                    Text("Ajustar macros", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            MacroOverrideCol("Calorías", logged?.calories ?: 0.0, onCaloriesChange)
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            MacroOverrideCol("Proteína", logged?.protein ?: 0.0, onProteinChange)
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            MacroOverrideCol("Carbos", logged?.carbs ?: 0.0, onCarbsChange)
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            MacroOverrideCol("Grasas", logged?.fats ?: 0.0, onFatsChange)
                        }
                    }

                    if (logged != null) {
                        Text(
                            "Fibra: ${kotlin.math.round(logged.fiber).toInt()}g · Azúcar: ${kotlin.math.round(logged.sugar).toInt()}g · Sodio: ${kotlin.math.round(logged.sodiumMg).toInt()}mg",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (tag.cookingMethod == CookingMethod.FRITO || tag.cookingMethod == CookingMethod.EMPANIZADO_FRITO) {
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
private fun MacroOverrideCol(label: String, value: Double, onChange: (Double) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChange((value - 5).coerceAtLeast(0.0)) }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Remove, null, modifier = Modifier.size(12.dp))
            }
            Text(
                text = "${kotlin.math.round(value).toInt()}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(36.dp),
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = { onChange((value + 5).coerceAtMost(9999.0)) }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(12.dp))
            }
        }
    }
}


