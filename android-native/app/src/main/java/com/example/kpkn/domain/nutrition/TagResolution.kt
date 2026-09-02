package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.food.findFoodExactByNormalized
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
        mealType: MealType? = null,
    ): Pair<List<ResolvedTag>, ContextDetector.ContextResult> = withContext(Dispatchers.Default) {
        val resolvedTags = mutableListOf<ResolvedTag>()
        val hasGreaseCooking = parsed.items.any {
            it.cookingMethod == CookingMethod.FRITO ||
                it.cookingMethod == CookingMethod.EMPANIZADO_FRITO
        }
        val hasExcludedOil = parsed.items.any { item ->
            item.isExcluded && isOilTag(item.tag)
        }

        val contextResult = ContextDetector.detect(
            parsed.rawDescription,
            mealType,
            parsed.items.map { it.tag },
        ).let { inferred ->
            if (detectedContext != null && detectedContext.detectedContexts.isNotEmpty() && inferred.detectedContexts.isEmpty()) {
                inferred.copy(
                    primaryContext = detectedContext.primaryContext,
                    detectedContexts = detectedContext.detectedContexts,
                    portionAdjustment = detectedContext.portionAdjustment,
                    proteinAdjustment = detectedContext.proteinAdjustment,
                )
            } else inferred
        }
        val portionAdj = contextResult.portionAdjustment
        val inferPortions = InferredMealContext.shouldInferPortions(
            contextResult.shape,
            parsed.items.size,
            parsed.items.all { it.amountIntent == AmountIntent.UNSPECIFIED },
            parsed.rawDescription,
        )

        // FIX NUT-02: trace común por análisis + subtiempos por tag
        val analysisTraceId = UUID.randomUUID().toString().substring(0, 8)
        for (item in parsed.items) {
            val tagStart = System.nanoTime()
            val lockedPortionAdj = scalingForIntent(
                if (inferPortions && item.amountIntent == AmountIntent.UNSPECIFIED) {
                    AmountIntent.INFERRED_CONTEXT
                } else {
                    item.amountIntent
                },
                portionAdj,
            )

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
                ?: SemanticPortionRetriever.RetrievalResult(
                    query = item.tag,
                    matches = emptyList(),
                    contextDetected = emptyList(),
                    portionPriors = emptyMap(),
                    macroRange = null,
                    confidence = 0.0,
                    elapsedMs = 0,
                )

            // Fallback: lookup estático + búsqueda
            val staticFood = port.staticFood(item.tag)
            val exactFood = findFoodExactByNormalized(item.tag)
            val staticIsExact = exactFood != null

            // R2: preferencia por precisión, no por origen.
            // Exacto estático (alias/nombre) mantiene prioridad sobre candidatos
            // ambiguos; el estático FUZZY ya no le gana al resolver global.
            val smartFood = smartCandidate?.let { port.getFoodById(it.foodId) }
            val staticLocal = HouseholdPortions.rejectUnbrandedGlobal(exactFood ?: staticFood, item.brandHint)
            val smartAccepted = HouseholdPortions.rejectUnbrandedGlobal(smartFood, item.brandHint)
            val smartIsQualifiedDish = smartCandidate != null &&
                !FoodIdentity.isPlainSimpleFood(item.tag, smartCandidate.name) &&
                FoodIdentity.normalize(smartCandidate.name) != FoodIdentity.normalize(item.tag)
            val food = when {
                staticIsExact && staticLocal != null -> staticLocal
                staticLocal != null && item.brandHint.isNullOrBlank() && smartIsQualifiedDish -> staticLocal
                smartAccepted != null &&
                    smartCandidate != null &&
                    smartCandidate.source == "LOCAL" &&
                    !HouseholdPortions.looksLikePackName(smartCandidate.name) &&
                    smartResult.decision != SmartFoodResolver.Decision.UNRESOLVED ->
                    smartAccepted
                staticLocal != null && item.brandHint.isNullOrBlank() -> staticLocal
                smartAccepted != null -> smartAccepted
                else -> staticLocal
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

            val approximationAlias = isApproximationAlias(item.tag)
            val assumeStatus = CookingStateResolver.assumedStateStatus(item.tag, effectiveFood)

            val source = item.analysisSource
            val isVerifiedGlobalExact = effectiveFood != null &&
                HouseholdPortions.isHouseholdIdentity(effectiveFood, item.brandHint) &&
                FoodIdentity.hasPlausibleMacros(effectiveFood)
            val localAuthority = effectiveFood != null &&
                HouseholdPortions.isHouseholdIdentity(effectiveFood, item.brandHint) && (
                staticIsExact ||
                    preparedVariant != null ||
                    assumedVariant != null ||
                    stateAssumed ||
                    smartCandidate?.source == "LOCAL" ||
                    FoodIdentity.hasPlausibleMacros(effectiveFood)
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
            val itemIntent = if (inferPortions && item.amountIntent == AmountIntent.UNSPECIFIED) {
                AmountIntent.INFERRED_CONTEXT
            } else {
                item.amountIntent
            }
            val inferredGrams = if (itemIntent == AmountIntent.INFERRED_CONTEXT) {
                HouseholdPortions.inferredItemGrams(effectiveFood, item.tag, contextResult)
            } else {
                null
            }
            val explicitKilogramPreview = HouseholdPortions.isExplicitKilogram(parsed.rawDescription) ||
                HouseholdPortions.isExplicitKilogram(item.tag)
            val previewGrams = HouseholdPortions.resolveEatenGrams(
                intent = itemIntent,
                quantity = item.quantity,
                food = effectiveFood,
                parsedGrams = inferredGrams ?: item.amountGrams,
                query = item.tag,
                explicitKilogram = explicitKilogramPreview,
            )
            val resolutionStatus = HouseholdPortions.operationalAutoStatus(
                food = effectiveFood,
                grams = previewGrams,
                brandHint = item.brandHint,
                explicitKilogram = explicitKilogramPreview,
                amountIntent = itemIntent,
            )
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

            val resolved = if (effectiveFood != null && !preferAiLoggedFood) {
                val calibratedGrams = if (itemIntent == AmountIntent.UNSPECIFIED) {
                    listOfNotNull(
                        effectiveFood.id.lowercase(),
                        FoodIdentity.normalize(effectiveFood.name),
                        FoodIdentity.familyFor(effectiveFood).orEmpty().lowercase().takeIf { it.isNotBlank() },
                        FoodIdentity.normalize(item.tag),
                    ).firstNotNullOfOrNull { key ->
                        calibrationProfile?.maturePortionsGrams?.get(key)
                            ?.takeIf { it.isFinite() && it > 0.0 && HouseholdPortions.isHouseholdHint(it, effectiveFood, item.tag) }
                    }
                } else null
                val datasetHint = SemanticPortionRetriever.getGramsForFood(item.tag, retrievalResult)
                    ?.takeIf { HouseholdPortions.isHouseholdHint(it, effectiveFood, item.tag) }
                val explicitKilogram = HouseholdPortions.isExplicitKilogram(parsed.rawDescription) ||
                    HouseholdPortions.isExplicitKilogram(item.tag)
                val effectiveGrams = HouseholdPortions.resolveEatenGrams(
                    intent = itemIntent,
                    quantity = item.quantity,
                    food = effectiveFood,
                    parsedGrams = inferredGrams ?: item.amountGrams,
                    datasetHint = calibratedGrams ?: datasetHint,
                    query = item.tag,
                    explicitKilogram = explicitKilogram,
                )
                val effectiveAmountIntent = when {
                    calibratedGrams != null -> AmountIntent.RESOLVED_SUBJECTIVE
                    itemIntent != AmountIntent.UNSPECIFIED -> itemIntent
                    else -> AmountIntent.RESOLVED_SUBJECTIVE
                }

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

                val assumedPortionText = when {
                    itemIntent == AmountIntent.INFERRED_CONTEXT && contextResult.assumedLabel != null ->
                        "Asumí ${contextResult.assumedLabel}."
                    itemIntent == AmountIntent.UNSPECIFIED && item.amountGrams == null ->
                        effectiveGrams.takeIf { it > 0 }?.let { "Asumí ${it.toInt()} g." }
                    else -> null
                }
                val warningText = listOfNotNull(
                    calibratedGrams?.let { "Usé tu habitual: ${it.toInt()} g." },
                    assumedPortionText?.takeIf { calibratedGrams == null },
                    validated.warnings.firstOrNull()?.takeIf { it.isNotBlank() },
                    if (stateAssumed) assumeStatus else null,
                ).joinToString(" ")
                val finalStatus = HouseholdPortions.operationalAutoStatus(
                    food = effectiveFood,
                    grams = effectiveGrams,
                    brandHint = item.brandHint,
                    explicitKilogram = explicitKilogram,
                    amountIntent = itemIntent,
                )

                ResolvedTag(
                    tag = item.tag,
                    portion = item.portion,
                    quantity = item.quantity,
                    amountGrams = effectiveGrams,
                    baseAmountGrams = effectiveGrams,
                    portionMinGrams = effectiveGrams,
                    portionMaxGrams = effectiveGrams,
                    cookingMethod = item.cookingMethod,
                    foodItem = effectiveFood,
                    loggedFood = oiled,
                    isResolved = finalStatus == FoodResolutionStatus.AUTO,
                    isFuzzyMatch = approximationAlias || (isSmartMatch && smartCandidate?.confidence != SmartFoodResolver.Confidence.HIGH),
                    analysisSource = AnalysisSource.DATABASE,
                    statusText = warningText,
                    oilLevel = effectiveOilLevel,
                    isExcluded = item.isExcluded,
                    amountIntent = effectiveAmountIntent,
                    calibrationUsed = calibratedGrams != null,
                    needsCookingClarification = false,
                    clarificationKind = CookingStateResolver.ClarificationKind.NONE,
                    oilApplied = applyOil,
                    reviewCandidates = emptyList(),
                    interpretation = interpretation,
                    canonicalFamily = canonicalFamily,
                    foodState = foodState,
                    resolutionStatus = finalStatus,
                    nutritionSource = nutritionSource,
                    resolutionConfidence = resolutionConfidence,
                    resolutionMargin = resolutionMargin,
                    stateAssumed = stateAssumed,
                )
            } else {
                val dishGrams = when {
                    itemIntent == AmountIntent.INFERRED_CONTEXT && inferredGrams != null -> inferredGrams
                    item.amountIntent == AmountIntent.EXPLICIT_MASS ||
                        item.amountIntent == AmountIntent.RESOLVED_SUBJECTIVE ->
                        item.amountGrams?.takeIf { it > 0 }
                            ?: inferredGrams
                            ?: HouseholdPortions.heuristicDishGrams(item.tag, contextResult)
                    else -> inferredGrams
                        ?: HouseholdPortions.heuristicDishGrams(item.tag, contextResult)
                }
                val profile = NutritionHeuristicEstimator.estimatePer100g(item.tag)
                val scale = dishGrams / 100.0
                val mac = item.macroOverrides
                var logged = createLoggedFood(
                    foodName = "${item.tag} (estimado)",
                    amount = dishGrams,
                    calories = (mac?.calories ?: profile.calories) * if (mac?.calories != null) 1.0 else scale,
                    protein = (mac?.protein ?: profile.protein) * if (mac?.protein != null) 1.0 else scale,
                    carbs = (mac?.carbs ?: profile.carbs) * if (mac?.carbs != null) 1.0 else scale,
                    fats = (mac?.fats ?: profile.fats) * if (mac?.fats != null) 1.0 else scale,
                    fiber = 0.0,
                    sugar = 0.0,
                    sodiumMg = 0.0,
                    potassiumMg = 0.0,
                    waterMl = 0.0,
                    portion = item.portion,
                    cookingMethod = item.cookingMethod,
                )
                logged = applyModifierScale(logged, item.modifierScale)
                val fallbackStatus = listOfNotNull(
                    contextResult.assumedLabel?.let { "Asumí $it." },
                    "Estimación de plato (${dishGrams.toInt()} g). Tocá la tarjeta para editar.",
                    assumeStatus,
                ).joinToString(" ")
                val estimateSaveable = logged.calories.isFinite() && logged.protein.isFinite() &&
                    logged.carbs.isFinite() && logged.fats.isFinite()
                ResolvedTag(
                    tag = item.tag,
                    portion = item.portion,
                    quantity = item.quantity,
                    amountGrams = dishGrams,
                    baseAmountGrams = dishGrams,
                    portionMinGrams = dishGrams,
                    portionMaxGrams = dishGrams,
                    cookingMethod = item.cookingMethod,
                    foodItem = null,
                    loggedFood = logged.copy(analysisSource = AnalysisSource.LOCAL_HEURISTIC),
                    isResolved = estimateSaveable,
                    isFuzzyMatch = true,
                    analysisSource = AnalysisSource.LOCAL_HEURISTIC,
                    statusText = fallbackStatus,
                    oilLevel = effectiveOilLevel,
                    isExcluded = item.isExcluded,
                    amountIntent = itemIntent,
                    needsCookingClarification = false,
                    clarificationKind = CookingStateResolver.ClarificationKind.NONE,
                    interpretation = interpretation,
                    reviewCandidates = emptyList(),
                    canonicalFamily = canonicalFamily,
                    foodState = foodState,
                    resolutionStatus = FoodResolutionStatus.NO_RESOLVED,
                    nutritionSource = NutritionSourceKind.HEURISTIC_ESTIMATE,
                    resolutionConfidence = resolutionConfidence,
                    resolutionMargin = resolutionMargin,
                )
            }
            resolvedTags += resolved
        }

        val combination = FoodCombinationParser.parse(parsed.rawDescription)
        val isSingleTagPlate = resolvedTags.size == 1
        val exactPlate = findFoodExactByNormalized(parsed.rawDescription) != null

        if (!isSingleTagPlate && !exactPlate && combination.confidence >= 0.70) {
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
                            match.amountIntent == AmountIntent.RESOLVED_SUBJECTIVE ||
                            match.amountIntent == AmountIntent.INFERRED_CONTEXT
                        ) {
                            continue
                        }
                        val existingGrams = match.amountGrams ?: existingLogged.amount
                        if (existingGrams > 0.0 &&
                            HouseholdPortions.isHouseholdHint(existingGrams, existingFood, match.tag)
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
                        val rawGrams = (sauceCap?.let { minOf(proportional, it) } ?: proportional)
                            .takeIf { it > 1.0 } ?: continue
                        val newGrams = HouseholdPortions.plausibilityClamp(
                            food = existingFood,
                            grams = rawGrams,
                            query = match.tag,
                            explicitKilogram = false,
                            quantity = match.quantity,
                        )
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
    return if (
        intent == AmountIntent.EXPLICIT_MASS ||
        intent == AmountIntent.RESOLVED_SUBJECTIVE ||
        intent == AmountIntent.INFERRED_CONTEXT
    ) {
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
    if (loggedFood != null &&
        loggedFood.calories.isFinite() &&
        loggedFood.calories >= 0.0 &&
        loggedFood.protein.isFinite() &&
        loggedFood.carbs.isFinite() &&
        loggedFood.fats.isFinite()
    ) {
        return false
    }
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
