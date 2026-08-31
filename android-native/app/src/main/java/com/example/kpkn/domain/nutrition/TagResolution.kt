package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.food.isApproximationAlias
import com.example.kpkn.data.models.*
import com.example.kpkn.telemetry.nutrition.NutritionTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
/**
 * TagResolution — resolución de ítems parseados a tags registrables.
 *
 * Extraído de FoodLoggerDrawer.resolveTags (F3.1): toda la lógica de decisión
 * (selección de alimento, variantes preparadas, aceite, escalado, validación,
 * capping de combinaciones) vive aquí y es testeable sin UI ni Room.
 * El acceso a datos se inyecta vía [FoodResolutionPort].
 */

data class ResolvedTag(
    val id: String = UUID.randomUUID().toString(),
    val tag: String,
    val portion: PortionPreset = PortionPreset.MEDIUM,
    val quantity: Double = 1.0,
    val amountGrams: Double? = null,
    /** Immutable mass anchor used by every later portion recalculation. */
    val baseAmountGrams: Double? = null,
    /** Absolute interval for a vague or subjective portion. */
    val portionMinGrams: Double? = null,
    val portionMaxGrams: Double? = null,
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
    /** R1: candidatos alternativos para revisión (top-4 del resolver). */
    val reviewCandidates: List<FoodItem> = emptyList(),
    /** D2 legacy: no se expone texto libre del dataset como identidad interpretada. */
    val interpretation: String? = null,
    val canonicalFamily: String? = null,
    val foodState: FoodState = FoodState.UNKNOWN,
    val resolutionStatus: FoodResolutionStatus = FoodResolutionStatus.NO_RESOLVED,
    val nutritionSource: NutritionSourceKind = NutritionSourceKind.HEURISTIC_ESTIMATE,
    val resolutionConfidence: Double? = null,
    /** Gap between the first two candidate scores; small gaps require review. */
    val resolutionMargin: Double? = null,
    /** Explicitly accepted central estimate; the persisted range remains visible. */
    val isUncertain: Boolean = false,
    val explicitDecision: Boolean = false,
    val calibrationUsed: Boolean = false,
    /** True when dry/raw vs cooked was assumed as eaten, not asked. */
    val stateAssumed: Boolean = false,
)

/** Acceso a datos del resolver — implementado por el drawer con NutritionRepository. */
interface FoodResolutionPort {
    suspend fun resolveSmart(
        tag: String,
        brandHint: String?,
        contextHint: String? = null,
        stateHint: FoodState? = null,
    ): SmartFoodResolver.ResolutionResult
    suspend fun getFoodById(id: String): FoodItem?
    suspend fun staticFood(tag: String): FoodItem?
    fun staticIsExact(tag: String): Boolean
    fun recordLearned(query: String, brandHint: String?, foodId: String, portionGrams: Double?, cookingMethod: String?)
}

class TagResolver(
    private val port: FoodResolutionPort,
    private val calibrationProfile: NutritionCalibrationProfile? = null,
) {

    suspend fun resolveAll(
        parsed: ParsedMealDescription,
        detectedContext: ContextDetector.ContextResult? = null,
    ): Pair<List<ResolvedTag>, ContextDetector.ContextResult> = withContext(Dispatchers.Default) {
        val resolvedTags = mutableListOf<ResolvedTag>()
        val hasGreaseCooking = parsed.items.any {
            it.cookingMethod == CookingMethod.FRITO ||
                it.cookingMethod == CookingMethod.EMPANIZADO_FRITO
        }
        val hasExcludedOil = parsed.items.any { item ->
            item.isExcluded && isOilTag(item.tag)
        }

        val contextResult = detectedContext ?: ContextDetector.detect(parsed.rawDescription)
        val portionAdj = contextResult.portionAdjustment

        // FIX NUT-02: trace común por análisis + subtiempos por tag
        val analysisTraceId = UUID.randomUUID().toString().substring(0, 8)
        for (item in parsed.items) {
            val tagStart = System.nanoTime()
            val lockedPortionAdj = scalingForIntent(item.amountIntent, portionAdj)

            // Phase B: SmartFoodResolver para matching fuzzy sobre toda la DB
            // (con contextHint = descripción completa para boost de co-ocurrencia D6;
            // stateHint = estado declarado vía método, porque el parser ya extrajo
            // la palabra "cocida/cruda" del tag antes de resolver)
            val assumedHint = CookingStateResolver.assumedDefault(item.tag, null)
            val smartResult = port.resolveSmart(
                item.tag,
                item.brandHint,
                parsed.rawDescription,
                CookingStateResolver.stateForMethod(item.cookingMethod) ?: assumedHint,
            )
            val smartCandidate = smartResult.candidates.firstOrNull()
            val retrievalResult = smartResult.semanticRetrieval
                ?: SemanticPortionRetriever.retrieve(item.tag)

            // Fallback: lookup estático + búsqueda
            val staticFood = port.staticFood(item.tag)
            val staticIsExact = port.staticIsExact(item.tag)

            // R2: preferencia por precisión, no por origen.
            // Exacto estático (alias/nombre) mantiene prioridad sobre candidatos
            // ambiguos; el estático FUZZY ya no le gana al resolver global.
            val food = when {
                staticIsExact && staticFood != null -> staticFood
                smartResult.decision == SmartFoodResolver.Decision.AUTO_SELECT && smartCandidate != null -> {
                    port.getFoodById(smartCandidate.foodId) ?: staticFood
                }
                smartResult.decision == SmartFoodResolver.Decision.NEEDS_REVIEW && smartCandidate != null -> {
                    // IT2: si el candidato smart es sintético (heuristic_*/dataset_*)
                    // y no resuelve por ID, preferir la fila real estática en vez de
                    // dejar el ítem sin alimento ("xyzwlkr" no debe quedar NO_RESOLVED
                    // si el catálogo tiene una fila razonable).
                    port.getFoodById(smartCandidate.foodId) ?: staticFood
                }
                else -> staticFood
            }

            // Prefer DB row that already encodes the method (pollo frito → pechuga frita).
            val preparedVariant = CookingStateResolver.findPreparedVariant(item.tag, item.cookingMethod)
            val assumedState = if (item.cookingMethod == null) {
                CookingStateResolver.assumedDefault(item.tag, food)
            } else null
            val assumedVariant = if (assumedState != null) {
                CookingStateResolver.resolveAssumedVariant(item.tag, food, assumedState)
            } else null
            val usingPreparedVariant = preparedVariant != null ||
                (assumedVariant != null && assumedVariant.id != food?.id &&
                    (CookingStateResolver.isDbFoodCooked(assumedVariant) ||
                        assumedState == FoodState.RAW))
            val effectiveFood = when {
                preparedVariant != null -> preparedVariant
                assumedVariant != null -> assumedVariant
                item.cookingMethod == CookingMethod.CRUDO && food != null &&
                    CookingStateResolver.isDbFoodCooked(food) ->
                    CookingStateResolver.findRawVariant(food) ?: food
                item.cookingMethod != null && item.cookingMethod != CookingMethod.CRUDO && food != null -> {
                    if (CookingStateResolver.isAlreadyPreparedForMethod(food, item.cookingMethod)) {
                        CookingStateResolver.findRawVariant(food) ?: food
                    } else food
                }
                else -> food
            }
            val stateAssumed = assumedState != null && item.cookingMethod == null && effectiveFood != null

            // Oil only when frying on a raw/base profile (not when DB row is already fried).
            val applyOil = CookingStateResolver.shouldApplyOil(
                effectiveFood,
                item.cookingMethod,
            ) && !usingPreparedVariant && item.cookingMethod != null
            val effectiveOilLevel = when {
                !applyOil -> "medio"
                item.isExcluded && isOilTag(item.tag) -> "poco"
                hasExcludedOil && hasGreaseCooking -> "poco"
                else -> "medio"
            }
            // Method passed to scaler: none when macros already include preparation.
            val scaleMethod = if (usingPreparedVariant) null else item.cookingMethod

            val isSmartMatch = smartResult.decision != SmartFoodResolver.Decision.UNRESOLVED && smartCandidate != null
            // La variante preparada coincide con el método explícito del usuario:
            // la fila ya es autoridad local y no hay identidad pendiente de revisión.
            val requiresCandidateReview =
                smartResult.decision == SmartFoodResolver.Decision.NEEDS_REVIEW &&
                    !usingPreparedVariant &&
                    !stateAssumed &&
                    smartCandidate?.foodId == effectiveFood?.id

            val clarifyKind = CookingStateResolver.clarificationKind(
                item.tag, effectiveFood, item.cookingMethod,
            )
            val needsClarify = clarifyKind != CookingStateResolver.ClarificationKind.NONE
            // A1: alias de aproximación ("torta" ≈ pan blanco) → NUNCA autoconfirmar.
            val approximationAlias = isApproximationAlias(item.tag)
            // Un atún sin medio de conservación se resuelve a la variante al
            // agua como candidato estable, pero requiere que la persona
            // confirme agua/aceite antes de guardarlo. Las consultas explícitas
            // ("atún al agua", "atún en aceite") conservan AUTO.
            val bareTunaQuery = FoodIdentity.normalize(item.tag) in setOf("atun", "tuna")
            val assumeStatus = if (needsClarify) {
                CookingStateResolver.assumedStateStatus(item.tag, effectiveFood)
            } else null

            val source = item.analysisSource
            // FIX NUT-01 systemic: allow verified OFF/USDA exact on-device matches to AUTO.
            // Previously only LOCAL AUTO counted → hallulla/marraqueta/fideos/gauda (OFF) always NEEDS_CONFIRMATION → red.
            // Now: if effectiveFood has plausible macros and is exact normalized match (unique, authoritative), treat as AUTO even if source is OFF/USDA.
            val isVerifiedGlobalExact = effectiveFood != null &&
                smartCandidate != null &&
                smartCandidate.foodId == effectiveFood.id &&
                FoodIdentity.hasPlausibleMacros(effectiveFood) &&
                (
                    smartResult.decision == SmartFoodResolver.Decision.AUTO_SELECT ||
                        FoodIdentity.isPlainSimpleFood(item.tag, effectiveFood.name)
                    ) &&
                (
                    FoodIdentity.isPlainSimpleFood(item.tag, effectiveFood.name) ||
                        FoodIndex.normalizeSearch(item.tag) == FoodIndex.normalizeSearch(effectiveFood.name) ||
                        effectiveFood.searchAliases.any { FoodIndex.normalizeSearch(it) == FoodIndex.normalizeSearch(item.tag) } ||
                        FoodIdentity.normalize(item.tag) == FoodIdentity.normalize(effectiveFood.name) ||
                        (
                            FoodIdentity.familyFor(item.tag) != null &&
                                FoodIdentity.familyFor(item.tag) == FoodIdentity.familyFor(effectiveFood)
                            )
                    ) &&
                smartCandidate.score >= 0.70
            val localAuthority = effectiveFood != null && (
                staticIsExact ||
                    preparedVariant != null ||
                    assumedVariant != null ||
                    stateAssumed ||
                    (smartResult.decision == SmartFoodResolver.Decision.AUTO_SELECT &&
                        smartCandidate?.source == "LOCAL") ||
                    isVerifiedGlobalExact ||
                    (
                        FoodIdentity.isPlainSimpleFood(item.tag, effectiveFood.name) &&
                            FoodIdentity.hasPlausibleMacros(effectiveFood)
                        )
                )
            // Los rangos semánticos describen ejemplos del dataset, no la fila local
            // ya seleccionada. La evidencia todavía puede aportar una porción por defecto,
            // pero nunca debe invalidar ni reinterpretar los macros autoritativos.
            val retrievalForMacroValidation = retrievalResult.takeUnless { localAuthority }
            val preferAiLoggedFood = effectiveFood == null && shouldUseAiLoggedFood(item)
            val canonicalFamily = FoodIdentity.familyFor(effectiveFood?.name ?: item.tag)
            val foodState = effectiveFood?.let { FoodIdentity.stateFor(it) }
                ?: FoodIdentity.stateFor(item.tag)
            val resolutionConfidence = when {
                staticIsExact && !approximationAlias -> 1.0
                smartCandidate != null -> smartCandidate.score
                else -> item.analysisConfidence

            }
            val resolutionMargin = smartResult.candidates
                .sortedByDescending { it.score }
                .let { candidates ->
                    if (candidates.size < 2) 1.0 else (candidates[0].score - candidates[1].score).coerceAtLeast(0.0)
                }
            val resolutionStatus = when {
                approximationAlias -> FoodResolutionStatus.NEEDS_CONFIRMATION
                bareTunaQuery -> FoodResolutionStatus.NEEDS_CONFIRMATION
                needsClarify && !stateAssumed -> FoodResolutionStatus.NEEDS_STATE
                localAuthority && !requiresCandidateReview -> FoodResolutionStatus.AUTO
                effectiveFood != null -> FoodResolutionStatus.NEEDS_CONFIRMATION
                else -> FoodResolutionStatus.NO_RESOLVED
            }
            val nutritionSource = when {
                localAuthority -> NutritionSourceKind.CURATED_LOCAL
                item.analysisSource == AnalysisSource.EXTERNAL_API_ESTIMATE -> NutritionSourceKind.EXTERNAL_ESTIMATE
                effectiveFood != null -> NutritionSourceKind.VERIFIED_GLOBAL
                else -> NutritionSourceKind.HEURISTIC_ESTIMATE
            }
            // FIX NUT-02: per-tag instrumentation (anonimizado, sin texto crudo) — after status known
            val tagElapsed = (System.nanoTime() - tagStart) / 1_000_000L
            val tagHash = FoodIdentity.normalize(item.tag).hashCode().toString(16)
            runCatching {
                NutritionTelemetry.event(
                    "tag_resolved",
                    mapOf(
                        "traceId" to analysisTraceId,
                        "tagHash" to tagHash,
                        "tagLen" to item.tag.length,
                        "source" to (smartCandidate?.source ?: "none"),
                        "score" to (smartCandidate?.score ?: 0.0),
                        "confidence" to (resolutionConfidence ?: 0.0),
                        "margin" to (resolutionMargin ?: 0.0),
                        "decision" to smartResult.decision.name,
                        "resolutionStatus" to resolutionStatus.name,
                        "isResolved" to (resolutionStatus == FoodResolutionStatus.AUTO),
                        "isVerifiedGlobalExact" to isVerifiedGlobalExact,
                        "candidateCount" to smartResult.candidates.size,
                        "durationMs" to tagElapsed,
                        "hasFood" to (effectiveFood != null),
                    ),
                    traceId = analysisTraceId
                )
            }
            // D2: las instrucciones del dataset son evidencia de recuperación, no una
            // interpretación fiable de la identidad. Un vecino como "Completo con
            // champiñones salteados" puede ser una comida distinta aunque comparta un
            // token; no debe llegar al usuario como si la app hubiera entendido eso.
            val interpretation: String? = null
            // R1: candidatos alternativos cuando hay revisión pendiente
            val reviewCandidates = if (requiresCandidateReview && !needsClarify) {
                smartResult.candidates
                    .drop(1)
                    .mapNotNull { port.getFoodById(it.foodId) }
                    .take(3)
            } else {
                emptyList()
            }

            val resolved = if (effectiveFood != null && !preferAiLoggedFood) {
                // Locked intents keep parser grams; unspecified may fill from dataset once.
                val calibratedGrams = if (item.amountIntent == AmountIntent.UNSPECIFIED) {
                    listOfNotNull(
                        effectiveFood.id.lowercase(),
                        FoodIdentity.normalize(effectiveFood.name),
                        FoodIdentity.familyFor(effectiveFood).orEmpty().lowercase().takeIf { it.isNotBlank() },
                        FoodIdentity.normalize(item.tag),
                    ).firstNotNullOfOrNull { key ->
                        calibrationProfile?.maturePortionsGrams?.get(key)
                            ?.takeIf { it.isFinite() && it > 0.0 }
                    }
                } else null
                val effectiveGrams = when (item.amountIntent) {
                    AmountIntent.EXPLICIT_MASS, AmountIntent.RESOLVED_SUBJECTIVE -> item.amountGrams
                    AmountIntent.UNSPECIFIED ->
                        item.amountGrams ?: calibratedGrams ?: SemanticPortionRetriever.getGramsForFood(item.tag, retrievalResult)
                }
                val effectiveAmountIntent = if (calibratedGrams != null) AmountIntent.RESOLVED_SUBJECTIVE else item.amountIntent

                var logged = scaleFoodByPortion(
                    food = effectiveFood,
                    quantity = item.quantity,
                    portion = item.portion,
                    amountGrams = effectiveGrams,
                    cookingMethod = scaleMethod,
                    portionAdjustment = lockedPortionAdj,
                )
                logged = applyModifierScale(logged, item.modifierScale)
                val validated = MacroValidator.validate(
                    input = MacroValidator.MacroInput(
                        calories = logged.calories,
                        protein = logged.protein,
                        carbs = logged.carbs,
                        fats = logged.fats
                    ),
                    retrievalResult = retrievalForMacroValidation,
                    portionGrams = logged.amount
                )

                val finalLogged = if (validated.wasAdjusted) {
                    logged.copy(
                        calories = validated.adjustedCalories,
                        protein = validated.adjustedProtein,
                        carbs = validated.adjustedCarbs,
                        fats = validated.adjustedFats
                    )
                } else logged

                val oiled = if (applyOil) {
                    adjustLoggedFoodForOil(
                        finalLogged.copy(analysisSource = AnalysisSource.DATABASE),
                        item.cookingMethod,
                        effectiveOilLevel,
                        foodName = effectiveFood.name,
                    )
                } else {
                    finalLogged.copy(
                        analysisSource = AnalysisSource.DATABASE,
                        cookingMethod = item.cookingMethod ?: finalLogged.cookingMethod,
                    )
                }

                val assumedPortionText =
                    if (item.amountIntent == AmountIntent.UNSPECIFIED && item.amountGrams == null) {
                        (calibratedGrams ?: effectiveGrams)?.takeIf { it > 0 }?.let { "Asumí ${it.toInt()} g." }
                    } else null
                val warningText = listOfNotNull(
                    calibratedGrams?.let { "Usé tu habitual: ${it.toInt()} g · Cambiar" },
                    assumedPortionText?.takeIf { calibratedGrams == null },
                    validated.warnings.firstOrNull()?.takeIf { it.isNotBlank() && !(needsClarify && !stateAssumed) },
                    if (stateAssumed) assumeStatus else if (needsClarify) "Falta el estado: selecciona seco o cocido para calcular los macros." else assumeStatus,
                    if (approximationAlias) "«${item.tag}» es un plato general: elegí el alimento más parecido (${effectiveFood.name}). Cámbialo si no era eso." else null,
                    if (requiresCandidateReview && !needsClarify) "Coincidencia aproximada: revisa el alimento seleccionado." else null,
                    interpretation?.let { "Entendí: $it" },
                ).joinToString(" ")

                ResolvedTag(
                    tag = item.tag,
                    portion = item.portion,
                    quantity = item.quantity,
                    amountGrams = item.amountGrams ?: calibratedGrams ?: effectiveGrams,
                    baseAmountGrams = item.amountGrams ?: calibratedGrams ?: effectiveGrams,
                    portionMinGrams = if (item.amountIntent == AmountIntent.UNSPECIFIED && calibratedGrams == null) {
                        (item.amountGrams ?: effectiveGrams)?.times(0.75)
                    } else item.amountGrams ?: effectiveGrams,
                    portionMaxGrams = if (item.amountIntent == AmountIntent.UNSPECIFIED && calibratedGrams == null) {
                        (item.amountGrams ?: effectiveGrams)?.times(1.25)
                    } else item.amountGrams ?: effectiveGrams,
                    cookingMethod = item.cookingMethod,
                    foodItem = effectiveFood,
                    loggedFood = oiled.takeUnless { needsClarify && !stateAssumed },
                    isResolved = resolutionStatus == FoodResolutionStatus.AUTO,
                    isFuzzyMatch = approximationAlias || (isSmartMatch && smartCandidate?.confidence != SmartFoodResolver.Confidence.HIGH),
                    analysisSource = AnalysisSource.DATABASE,
                    statusText = warningText,
                    oilLevel = effectiveOilLevel,
                    isExcluded = item.isExcluded,
                    amountIntent = effectiveAmountIntent,
                    calibrationUsed = calibratedGrams != null,
                    needsCookingClarification = needsClarify && !stateAssumed,
                    clarificationKind = clarifyKind,
                    oilApplied = applyOil,
                    reviewCandidates = reviewCandidates,
                    interpretation = interpretation,
                    canonicalFamily = canonicalFamily,
                    foodState = foodState,
                    resolutionStatus = resolutionStatus,
                    nutritionSource = nutritionSource,
                    resolutionConfidence = resolutionConfidence,
                    resolutionMargin = resolutionMargin,
                    stateAssumed = stateAssumed,
                )
            } else if (item.amountGrams != null && item.amountGrams > 0) {
                val mac = item.macroOverrides

                val bestMatch = retrievalResult.macroRange
                val estimatedCandidate = smartCandidate.takeIf {
                    smartResult.decision != SmartFoodResolver.Decision.UNRESOLVED
                }

                val finalCal = mac?.calories ?: estimatedCandidate?.calories ?: bestMatch?.kcalMedian ?: 0.0
                val finalProt = mac?.protein ?: estimatedCandidate?.protein ?: bestMatch?.proteinMedian ?: 0.0
                val finalCarbs = mac?.carbs ?: estimatedCandidate?.carbs ?: bestMatch?.carbsMedian ?: 0.0
                val finalFats = mac?.fats ?: estimatedCandidate?.fats ?: bestMatch?.fatsMedian ?: 0.0
                val fallbackSource = when (estimatedCandidate?.source) {
                    "LOCAL_HEURISTIC" -> AnalysisSource.LOCAL_HEURISTIC
                    "DATASET_SEMANTIC" -> AnalysisSource.LOCAL_AI_ESTIMATE
                    else -> source
                }
                val fallbackNutritionSource = when {
                    estimatedCandidate?.source == "DATASET_SEMANTIC" -> NutritionSourceKind.DATASET_ESTIMATE
                    estimatedCandidate?.source == "LOCAL_HEURISTIC" -> NutritionSourceKind.HEURISTIC_ESTIMATE
                    source == AnalysisSource.EXTERNAL_API_ESTIMATE -> NutritionSourceKind.EXTERNAL_ESTIMATE
                    else -> NutritionSourceKind.HEURISTIC_ESTIMATE
                }
                var logged = createLoggedFood(
                    // A4: el nombre persistido marca la estimación para que nunca
                    // parezca un alimento real encontrado en la base.
                    foodName = when (estimatedCandidate?.source) {
                        "LOCAL_HEURISTIC" -> "${item.tag} (estimado)"
                        "DATASET_SEMANTIC" -> "${item.tag} (aprox. del dataset)"
                        else -> item.tag
                    },
                    amount = item.amountGrams,
                    calories = finalCal,
                    protein = finalProt,
                    carbs = finalCarbs,
                    fats = finalFats,
                    fiber = 0.0,
                    sugar = 0.0,
                    sodiumMg = 0.0,
                    potassiumMg = 0.0,
                    waterMl = 0.0,
                    portion = item.portion,
                    cookingMethod = item.cookingMethod,
                )
                logged = applyModifierScale(logged, item.modifierScale)
                val validated = MacroValidator.validate(
                    input = MacroValidator.MacroInput(
                        calories = logged.calories,
                        protein = logged.protein,
                        carbs = logged.carbs,
                        fats = logged.fats,
                    ),
                    retrievalResult = retrievalForMacroValidation,
                    portionGrams = logged.amount,
                )
                val finalLogged = if (validated.wasAdjusted) {
                    logged.copy(
                        calories = validated.adjustedCalories,
                        protein = validated.adjustedProtein,
                        carbs = validated.adjustedCarbs,
                        fats = validated.adjustedFats,
                    )
                } else {
                    logged
                }
                val applyOilFallback = CookingStateResolver.shouldApplyOil(null, item.cookingMethod)
                val oiled = if (applyOilFallback) {
                    adjustLoggedFoodForOil(
                        finalLogged.copy(analysisSource = fallbackSource),
                        item.cookingMethod,
                        effectiveOilLevel,
                    )
                } else {
                    finalLogged.copy(analysisSource = fallbackSource)
                }
                val fallbackStatus = listOfNotNull(
                    when {
                        needsClarify -> "Falta el estado: selecciona seco o cocido para calcular los macros."
                        approximationAlias -> "«${item.tag}» es un plato general: los macros son una estimación, revísalos."
                        mac != null -> "Estimación externa: confirma estos macros antes de guardar."
                        estimatedCandidate?.source == "DATASET_SEMANTIC" ->
                            "Prior del dataset (${bestMatch?.sampleCount ?: 0} ejemplos): revisa los macros."
                        else -> "Estimación local: revisa los macros antes de guardar."
                    },
                    validated.warnings.firstOrNull()?.takeIf { !needsClarify },
                    if (!needsClarify) assumeStatus else null,
                ).joinToString(" ")

                ResolvedTag(
                    tag = item.tag,
                    portion = item.portion,
                    quantity = item.quantity,
                    amountGrams = item.amountGrams,
                    baseAmountGrams = item.amountGrams,
                    portionMinGrams = item.amountGrams?.times(0.75),
                    portionMaxGrams = item.amountGrams?.times(1.25),
                    cookingMethod = item.cookingMethod,
                    foodItem = null,
                    loggedFood = oiled,
                    isResolved = false,
                    isFuzzyMatch = true,
                    analysisSource = fallbackSource,
                    statusText = fallbackStatus,
                    oilLevel = effectiveOilLevel,
                    isExcluded = item.isExcluded,
                    amountIntent = item.amountIntent,
                    needsCookingClarification = needsClarify,
                    clarificationKind = clarifyKind,
                    oilApplied = applyOilFallback,
                    interpretation = interpretation,
                    reviewCandidates = if (!needsClarify) {
                        smartResult.candidates.drop(1).mapNotNull { port.getFoodById(it.foodId) }.take(3)
                    } else emptyList(),
                    canonicalFamily = canonicalFamily,
                    foodState = foodState,
                    resolutionStatus = if (needsClarify) FoodResolutionStatus.NEEDS_STATE else FoodResolutionStatus.NEEDS_CONFIRMATION,
                )
            } else {
                ResolvedTag(
                    tag = item.tag,
                    portion = item.portion,
                    quantity = item.quantity,
                    baseAmountGrams = null,
                    foodItem = null,
                    loggedFood = null,
                    isResolved = false,
                    analysisSource = source,
                    statusText = if (needsClarify) "Falta el estado: selecciona seco o cocido." else assumeStatus.orEmpty(),
                    isExcluded = item.isExcluded,
                    amountIntent = item.amountIntent,
                    needsCookingClarification = needsClarify,
                    clarificationKind = clarifyKind,
                    interpretation = interpretation,
                    canonicalFamily = canonicalFamily,
                    foodState = foodState,
                    resolutionStatus = if (needsClarify) FoodResolutionStatus.NEEDS_STATE else FoodResolutionStatus.NO_RESOLVED,
                    nutritionSource = nutritionSource,
                    resolutionConfidence = resolutionConfidence,
                )
            }
            resolvedTags += resolved
        }

        val combination = FoodCombinationParser.parse(parsed.rawDescription)
        val isSingleTagPlate = resolvedTags.size == 1

        if (!isSingleTagPlate && combination.confidence >= 0.70) {
            val totalGrams = resolvedTags.sumOf { it.loggedFood?.amount ?: 0.0 }
            val comboParts = buildList {
                add(Triple(combination.baseFood, combination.baseProportion, FoodCombinationParser.Role.STARCH))
                combination.accompaniments.forEach { acc ->
                    add(Triple(acc.food, acc.proportion, acc.role))
                }
            }
            if (totalGrams > 0 && comboParts.isNotEmpty()) {
                for ((foodName, proportion, role) in comboParts) {
                    val matching = resolvedTags.filter { tag ->
                        val name = tag.foodItem?.name?.lowercase() ?: tag.tag.lowercase()
                        val needle = foodName.lowercase()
                        name.contains(needle) || needle.contains(name)
                    }
                    for (match in matching) {
                        val existingFood = match.foodItem ?: continue
                        val existingLogged = match.loggedFood ?: continue
                        val tagLower = match.tag.lowercase()
                        if (tagLower.contains(" con ") || tagLower.contains(" y ") || tagLower.contains(" e ")) {
                            continue
                        }
                        if (match.amountIntent == AmountIntent.EXPLICIT_MASS ||
                            match.amountIntent == AmountIntent.RESOLVED_SUBJECTIVE
                        ) {
                            continue
                        }
                        val proportional = proportion * totalGrams
                        val sauceCap = when (role) {
                            FoodCombinationParser.Role.SAUCE -> {
                                val lowerName = existingFood.name.lowercase()
                                if (lowerName.contains("aceite") || lowerName.contains("oil") ||
                                    lowerName.contains("mantequilla") || lowerName.contains("ghee") ||
                                    lowerName.contains("margarina") || lowerName.contains("manteca") ||
                                    lowerName.contains("mayonesa") || lowerName.contains("mayo")
                                ) 15.0 else 30.0
                            }
                            else -> null
                        }
                        val newGrams = (sauceCap?.let { minOf(proportional, it) } ?: proportional)
                            .takeIf { it > 1.0 } ?: continue
                        if (kotlin.math.abs(existingLogged.amount - newGrams) < 1.0) continue
                        val scaled = scaleFoodByPortion(
                            food = existingFood,
                            quantity = match.quantity,
                            portion = match.portion,
                            amountGrams = newGrams,
                            cookingMethod = match.cookingMethod,
                            portionAdjustment = 1.0,
                        )
                        val idx = resolvedTags.indexOfFirst { it.id == match.id }
                        if (idx >= 0) {
                            resolvedTags[idx] = match.copy(
                                loggedFood = scaled.copy(analysisSource = existingLogged.analysisSource),
                                amountGrams = newGrams,
                                statusText = listOf(match.statusText, "Asumí ${newGrams.toInt()} g.")
                                    .filter { it.isNotBlank() }
                                    .joinToString(" "),
                            )
                        }
                    }
                }
            }
        }
        Pair(resolvedTags, contextResult)
    }
}

// ─── Helpers compartidos (extraídos del drawer) ─────────────────────────────

fun oilGramsForLevel(oilLevel: String): Double = when (oilLevel.lowercase()) {
    "poco" -> 3.0
    "abundante" -> 18.0
    else -> 8.0
}

fun adjustLoggedFoodForOil(logged: LoggedFood, method: CookingMethod?, oilLevel: String): LoggedFood =
    adjustLoggedFoodForOil(logged, method, oilLevel, foodName = null)

/**
 * IT3: aceite por categoría del alimento — "huevo frito" (magro, 6 g) y
 * "papas fritas" desde crudo (masa/tubérculo, 12 g) ya no suman los mismos 8 g.
 * Cuando no se conoce el alimento, se mantiene el default histórico.
 */
fun adjustLoggedFoodForOil(
    logged: LoggedFood,
    method: CookingMethod?,
    oilLevel: String,
    foodName: String?,
): LoggedFood {
    if (method != CookingMethod.FRITO && method != CookingMethod.EMPANIZADO_FRITO) {
        return logged
    }
    val portionGrams = logged.amount
        .takeIf { logged.unit.equals("g", ignoreCase = true) && it.isFinite() && it > 0.0 }
        ?: 100.0
    val oilGrams = if (foodName.isNullOrBlank()) {
        oilGramsForLevel(oilLevel)
    } else {
        oilGramsForLevelInCategory(oilLevel, oilAbsorptionCategory(foodName), portionGrams)
    }
    val addedFat = oilGrams
    val addedCal = oilGrams * 9
    return logged.copy(
        fats = kotlin.math.round((logged.fats + addedFat) * 10.0) / 10.0,
        calories = kotlin.math.round(logged.calories + addedCal),
    )
}

fun stripOilFromLoggedFood(logged: LoggedFood, method: CookingMethod?, oilLevel: String): LoggedFood =
    stripOilFromLoggedFood(logged, method, oilLevel, foodName = null)

/** Versión por categoría para el strip (debe coincidir con el add). */
fun stripOilFromLoggedFood(
    logged: LoggedFood,
    method: CookingMethod?,
    oilLevel: String,
    foodName: String?,
): LoggedFood {
    if (method != CookingMethod.FRITO && method != CookingMethod.EMPANIZADO_FRITO) {
        return logged
    }
    val portionGrams = logged.amount
        .takeIf { logged.unit.equals("g", ignoreCase = true) && it.isFinite() && it > 0.0 }
        ?: 100.0
    val oilGrams = if (foodName.isNullOrBlank()) {
        oilGramsForLevel(oilLevel)
    } else {
        oilGramsForLevelInCategory(oilLevel, oilAbsorptionCategory(foodName), portionGrams)
    }
    return logged.copy(
        fats = kotlin.math.round((logged.fats - oilGrams).coerceAtLeast(0.0) * 10.0) / 10.0,
        calories = kotlin.math.round((logged.calories - oilGrams * 9).coerceAtLeast(0.0)),
    )
}

/**
 * El contexto solo puede mover porciones vagas, nunca macros por 100 g.
 * Una masa explícita o resuelta queda totalmente bloqueada.
 */
fun scalingForIntent(
    intent: AmountIntent,
    portionAdj: Double,
): Double {
    return if (intent == AmountIntent.EXPLICIT_MASS || intent == AmountIntent.RESOLVED_SUBJECTIVE) {
        1.0
    } else {
        portionAdj
    }
}

/**
 * Three absolute choices for a vague portion.  The anchor is intentionally
 * captured once; callers must not multiply the last selected value.  This is
 * what makes Grande → Pequeña → Grande idempotent.
 */
fun absolutePortionOptions(baseAmountGrams: Double?): List<Pair<String, Double>> {
    val base = baseAmountGrams?.takeIf { it.isFinite() && it > 0.0 } ?: return emptyList()
    return listOf(
        "Pequeña" to roundPortionGrams(base * 0.75),
        "Habitual" to roundPortionGrams(base),
        "Grande" to roundPortionGrams(base * 1.25),
    )
}

private fun roundPortionGrams(value: Double): Double =
    kotlin.math.round(value.coerceAtLeast(1.0) * 10.0) / 10.0

/** Material uncertainty used by both the UI gate and JVM tests. */
fun ResolvedTag.hasMaterialQuestion(): Boolean {
    if (isExcluded || explicitDecision) return false
    if (resolutionStatus == FoodResolutionStatus.AUTO && isResolved) return false
    if (needsCookingClarification && !stateAssumed) return true
    if (!isResolved && !isUncertain) return true
    return resolutionStatus == FoodResolutionStatus.NEEDS_CONFIRMATION ||
        resolutionStatus == FoodResolutionStatus.NO_RESOLVED
}

fun applyModifierScale(logged: LoggedFood, scale: MacroOverrides?): LoggedFood {
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

fun shouldUseAiLoggedFood(item: ParsedMealItem): Boolean {
    return item.macroOverrides != null && (
        item.analysisSource == AnalysisSource.LOCAL_AI_ESTIMATE ||
            item.analysisSource == AnalysisSource.EXTERNAL_API_ESTIMATE
        )
}

fun isOilTag(tag: String): Boolean {
    val lower = tag.lowercase().trim()
    return lower == "aceite" || lower == "aceite vegetal" || lower == "aceite de oliva" ||
        lower == "aceite de maravilla" || lower == "aceite de girasol"
}

/**
 * Merges newly-parsed tags with existing tags that have manual edits.
 * - Matching by tag name (case-insensitive)
 * - If old tag has hasManualEdits=true, preserve it over the new tag
 * - Preserve old tags not present in new tags if they have manual edits
 */
fun mergeTagsPreservingManualEdits(oldTags: List<ResolvedTag>, newTags: List<ResolvedTag>): List<ResolvedTag> {
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
