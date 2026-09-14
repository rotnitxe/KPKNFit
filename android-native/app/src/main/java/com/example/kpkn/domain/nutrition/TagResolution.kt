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
    val baseLoggedFood: LoggedFood? = null,
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
    /** Staple cut options when family is ambiguous (pollo, carne…). */
    val stapleCutOptions: List<FoodStapleOntology.StapleCutOption> = emptyList(),
    val needsCutClarification: Boolean = false,
    val needsOilClarification: Boolean = false,
    /** Learned identity for this query; suppresses family-cut chips. */
    val learnedFoodId: String? = null,
    /** Identity span without portion units; UI correction searches this. */
    val foodQuery: String = "",
    val unitId: String? = null,
    val interpretationV2: FoodInterpretationV2? = null,
    val confirmedDimensions: Set<String> = emptySet(),
    val learnedWeightBasis: FoodState? = null,
    val learnedOilGramsPer100g: Double? = null,
    val appliedOilGrams: Double? = null,
    val excludedIngredients: Set<String> = emptySet(),
    val unresolvedDeclaredAmount: Boolean = false,
    val nutrientsManuallyEdited: Boolean = false,
    val stateConversion: String? = null,
    val ambiguousPackageGrams: Double? = null,
    val nutritionEstimate: NutritionEstimateEvidence? = null,
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

    /**
     * Re-resolve one mention after the user answers an identity question.
     *
     * The answer changes the food identity, not the rest of the meal.  Feed the
     * replacement through the same resolver used by the description pipeline,
     * then restore only the stable UI id and the amount evidence that was truly
     * declared.  In particular, an inferred 40 g wrap portion must not become a
     * declared 40 g egg/potato tortilla just because the old preview used it.
     */
    suspend fun resolveDeclaredComposition(
        original: ResolvedTag,
        declaredQuery: String,
        detectedContext: ContextDetector.ContextResult? = null,
        mealType: MealType? = null,
    ): ResolvedTag {
        val query = declaredQuery.trim()
        if (query.isBlank()) return original

        val preservesDeclaredAmount = when (original.amountIntent) {
            AmountIntent.EXPLICIT_MASS -> original.amountGrams?.let { it.isFinite() && it > 0.0 } == true
            AmountIntent.RESOLVED_SUBJECTIVE -> original.unitId != null &&
                original.amountGrams?.let { it.isFinite() && it > 0.0 } == true
            else -> false
        }
        val amountIntent = if (preservesDeclaredAmount) original.amountIntent else AmountIntent.UNSPECIFIED
        val quantity = original.quantity.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
        val replacementItem = ParsedMealItem(
            tag = query,
            quantity = quantity,
            amountGrams = original.amountGrams.takeIf { preservesDeclaredAmount },
            cookingMethod = original.cookingMethod,
            amountIntent = amountIntent,
            portion = if (preservesDeclaredAmount) original.portion else PortionPreset.MEDIUM,
            foodQuery = query,
            unitId = original.unitId.takeIf { preservesDeclaredAmount },
            excludedIngredients = original.excludedIngredients,
        )
        val (resolved, _) = resolveAll(
            ParsedMealDescription(
                items = listOf(replacementItem),
                rawDescription = query,
                verbatimDescription = query,
            ),
            detectedContext = detectedContext,
            mealType = mealType,
        )
        val replacement = resolved.firstOrNull { !it.isExcluded }
            ?: ResolvedTag(
                tag = query,
                foodQuery = query,
                amountIntent = amountIntent,
                amountGrams = original.amountGrams.takeIf { preservesDeclaredAmount },
                quantity = quantity,
                portion = if (preservesDeclaredAmount) original.portion else PortionPreset.MEDIUM,
                unitId = original.unitId.takeIf { preservesDeclaredAmount },
                excludedIngredients = original.excludedIngredients,
                statusText = "No pude resolver la ficha de $query; conservé la identidad declarada.",
                resolutionStatus = FoodResolutionStatus.NEEDS_CONFIRMATION,
                nutritionSource = NutritionSourceKind.HEURISTIC_ESTIMATE,
            )

        val declared = replacement.copy(
            id = original.id,
            isExpanded = original.isExpanded,
            isExcluded = false,
            hasManualEdits = true,
            // The answer is an explicit identity declaration.  It must survive
            // an optional later "No estoy seguro" without restoring the old food.
            confirmedDimensions = original.confirmedDimensions + "identity",
            explicitDecision = false,
            isUncertain = replacement.isUncertain || replacement.foodItem == null,
            statusText = replacement.statusText.ifBlank {
                "Composición declarada: $query."
            },
        )
        // The resolver result was enriched before the identity declaration was
        // attached. Rebuild V2 so its pending identity question cannot keep the
        // Save action blocked after the user has answered it.
        return NutritionInterpretationBridge.refresh(declared)
    }

    suspend fun resolveAll(
        parsed: ParsedMealDescription,
        detectedContext: ContextDetector.ContextResult? = null,
        mealType: MealType? = null,
    ): Pair<List<ResolvedTag>, ContextDetector.ContextResult> = withContext(Dispatchers.Default) {
        val resolvedTags = mutableListOf<ResolvedTag>()

        val consumedItems = parsed.items.filterNot { it.isExcluded }
        val consumedQueries = consumedItems.map { it.effectiveFoodQuery() }
        val consumedDescription = consumedQueries.joinToString(" y ")
        val contextResult = ContextDetector.detect(
            parsed.rawDescription,
            mealType,
            consumedQueries,
            shapeDescription = consumedDescription,
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
            consumedItems.size,
            consumedItems.all { it.amountIntent == AmountIntent.UNSPECIFIED },
            consumedDescription,
        )

        // FIX NUT-02: trace común por análisis + subtiempos por tag
        val analysisTraceId = UUID.randomUUID().toString().substring(0, 8)
        for (item in parsed.items) {
            val tagStart = System.nanoTime()
            val identityQuery = item.effectiveFoodQuery()
            val hasGreaseCooking = item.cookingMethod in setOf(CookingMethod.FRITO, CookingMethod.EMPANIZADO_FRITO)
            val hasExcludedOil = item.excludedIngredients.any(::isOilTag)
            val mappedCanonicalId = calibrationProfile?.identityMappings?.let { map ->
                sequenceOf(
                    identityQuery.trim().lowercase(),
                    FoodIdentity.normalize(identityQuery),
                    item.tag.trim().lowercase(),
                    FoodIdentity.normalize(item.tag),
                ).mapNotNull { key -> map[key]?.takeIf { it.isNotBlank() } }.firstOrNull()
            }
            val lockedPortionAdj = scalingForIntent(
                if (inferPortions && item.amountIntent == AmountIntent.UNSPECIFIED) {
                    AmountIntent.INFERRED_CONTEXT
                } else {
                    item.amountIntent
                },
                portionAdj,
            )

            // Phase B: SmartFoodResolver para matching fuzzy sobre toda la DB
            // (con contextHint = menciones consumidas para boost de co-ocurrencia D6;
            // las menciones negadas no pueden contaminar el ranking de identidad;
            // stateHint = estado declarado vía método, porque el parser ya extrajo
            // la palabra "cocida/cruda" del tag antes de resolver)
            val preferenceHint = if (item.cookingMethod == null) calibrationProfile?.statePreferences?.get(FoodIdentity.normalize(identityQuery))
                ?.let { runCatching { FoodState.valueOf(it) }.getOrNull() }?.takeUnless { it == FoodState.UNKNOWN } else null
            val assumedHint = preferenceHint ?: CookingStateResolver.assumedDefault(identityQuery, null)
            val smartResult = port.resolveSmart(
                identityQuery,
                item.brandHint,
                consumedDescription,
                CookingStateResolver.stateForMethod(item.cookingMethod) ?: assumedHint,
            )
            val smartCandidate = smartResult.candidates.firstOrNull()
            val effectiveBrandHint = item.brandHint ?: smartCandidate?.brand?.takeIf { brand ->
                val normalized = FoodIdentity.normalize(brand).replace(" ", "")
                normalized.isNotBlank() && normalized !in setOf("generico", "generic", "sinmarca") &&
                    FoodIdentity.normalize(identityQuery).replace(" ", "").contains(normalized)
            }
            val ambiguousPackageGrams = item.amountGrams?.takeIf {
                item.amountIntent == AmountIntent.EXPLICIT_MASS && item.amountIsTrailing && effectiveBrandHint != null
            }
            val consumedGrams = item.amountGrams.takeIf { ambiguousPackageGrams == null }

            val retrievalResult = smartResult.semanticRetrieval
                ?: SemanticPortionRetriever.RetrievalResult(
                    query = identityQuery,
                    matches = emptyList(),
                    contextDetected = emptyList(),
                    portionPriors = emptyMap(),
                    macroRange = null,
                    confidence = 0.0,
                    elapsedMs = 0,
                )

            // Fallback: lookup estático + búsqueda
            val staticFood = port.staticFood(identityQuery)
            val exactFood = findFoodExactByNormalized(identityQuery)
            val staticIsExact = exactFood != null

            // R2: preferencia por precisión, no por origen.
            // Exacto estático (alias/nombre) mantiene prioridad sobre candidatos
            // ambiguos; el estático FUZZY ya no le gana al resolver global.
            val smartFood = smartCandidate?.let { port.getFoodById(it.foodId) }
            val staticLocal = HouseholdPortions.rejectUnbrandedGlobal(exactFood ?: staticFood, effectiveBrandHint)
                ?.takeIf { FoodIdentity.matchesDeclaredIdentity(identityQuery, it, effectiveBrandHint) }
            val smartAccepted = HouseholdPortions.rejectUnbrandedGlobal(smartFood, effectiveBrandHint)
                ?.takeIf { FoodIdentity.matchesDeclaredIdentity(identityQuery, it, effectiveBrandHint) }
            val smartIsQualifiedDish = smartCandidate != null &&
                !FoodIdentity.isPlainSimpleFood(identityQuery, smartCandidate.name) &&
                FoodIdentity.normalize(smartCandidate.name) != FoodIdentity.normalize(identityQuery)
            val learnedStaple = smartResult.learnedFoodId
                ?.takeIf { id ->
                    smartAccepted?.id == id && FoodStapleOntology.isKnownCutForFamily(identityQuery, id)
                }
            val mappedFood = mappedCanonicalId?.let { id -> port.getFoodById(id) }
                ?.takeIf { FoodIdentity.matchesDeclaredIdentity(identityQuery, it, effectiveBrandHint) }
            val food = when {
                mappedFood != null -> mappedFood
                learnedStaple != null && smartAccepted != null -> smartAccepted
                smartAccepted != null && smartResult.decision == SmartFoodResolver.Decision.AUTO_SELECT -> smartAccepted
                staticIsExact && staticLocal != null -> staticLocal
                staticLocal != null && effectiveBrandHint.isNullOrBlank() && smartIsQualifiedDish -> staticLocal
                smartAccepted != null &&
                    smartCandidate != null &&
                    smartCandidate.source == "LOCAL" &&
                    !HouseholdPortions.looksLikePackName(smartCandidate.name) &&
                    smartResult.decision != SmartFoodResolver.Decision.UNRESOLVED ->
                    smartAccepted
                staticLocal != null && effectiveBrandHint.isNullOrBlank() -> staticLocal
                smartAccepted != null -> smartAccepted
                else -> staticLocal
            }

            // Prefer DB row that already encodes the method (pollo frito → pechuga frita).
            val preparedVariant = CookingStateResolver.findPreparedVariant(identityQuery, item.cookingMethod)
                ?.takeUnless { hasExcludedOil && hasGreaseCooking }
            val rememberedState = if (item.cookingMethod == null) preferenceHint ?: food?.id?.let { id ->
                calibrationProfile?.statePreferences?.get(id)?.let { runCatching { FoodState.valueOf(it) }.getOrNull() }
            }?.takeUnless { it == FoodState.UNKNOWN } else null
            val assumedState = if (item.cookingMethod == null) {
                rememberedState ?: CookingStateResolver.assumedDefault(identityQuery, food)
            } else null
            val skipCookedAssumption = learnedStaple != null ||
                (
                    rememberedState == null && item.amountIntent == AmountIntent.EXPLICIT_MASS &&
                        food != null &&
                        CookingStateResolver.isDbFoodRaw(food)
                    )
            val assumedVariant = if (assumedState != null && !skipCookedAssumption) {
                CookingStateResolver.resolveAssumedVariant(identityQuery, food, assumedState)
                    ?.takeUnless { variant ->
                        food != null &&
                            FoodStapleOntology.isFamilyDefault(identityQuery) &&
                            FoodStapleOntology.cutOf(food.id) != null &&
                            FoodStapleOntology.cutOf(variant.id) != null &&
                            FoodStapleOntology.cutOf(food.id) != FoodStapleOntology.cutOf(variant.id)
                    }
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
            }.takeIf { it == null || (NutrientBasis.isVerified(it) && FoodIdentity.matchesDeclaredIdentity(identityQuery, it, effectiveBrandHint) &&
                FoodIdentity.matchesExclusions(it, item.excludedIngredients)) }
            val stateAssumed = assumedState != null && item.cookingMethod == null && effectiveFood != null

            // Oil only when frying on a raw/base profile (not when DB row is already fried).
            val applyOil = CookingStateResolver.shouldApplyOil(
                effectiveFood,
                item.cookingMethod,
            ) && !usingPreparedVariant && item.cookingMethod != null
            val effectiveOilLevel = when {
                !applyOil -> "medio"
                item.isExcluded && isOilTag(item.tag) -> "sin aceite"
                hasExcludedOil && hasGreaseCooking -> "sin aceite"
                else -> "medio"
            }
            // Method passed to scaler: none when macros already include preparation.
            val convertsAssumedState = item.cookingMethod == null && assumedState != null && effectiveFood != null &&
                ((assumedState == FoodState.COOKED && CookingStateResolver.isDbFoodRaw(effectiveFood)) ||
                    (assumedState == FoodState.RAW && CookingStateResolver.isDbFoodCooked(effectiveFood)))
            val scaleMethod = if (usingPreparedVariant) null else item.cookingMethod ?: when {
                convertsAssumedState && assumedState == FoodState.RAW -> CookingMethod.CRUDO
                convertsAssumedState -> CookingMethod.COCIDO
                else -> null
            }
            val convertedTarget = CookingStateResolver.stateForMethod(scaleMethod)?.takeIf { target ->
                effectiveFood != null && ((target == FoodState.COOKED && CookingStateResolver.isDbFoodRaw(effectiveFood)) ||
                    (target == FoodState.RAW && CookingStateResolver.isDbFoodCooked(effectiveFood)))
            }
            val rememberedOil = if (applyOil && !hasExcludedOil) effectiveFood?.id?.let { calibrationProfile?.oilProfiles?.get(it) }
                ?.takeIf { it.isFinite() && it >= 0.0 } else null

            val isSmartMatch = smartResult.decision != SmartFoodResolver.Decision.UNRESOLVED && smartCandidate != null

            val approximationAlias = isApproximationAlias(identityQuery)
            val assumeStatus = CookingStateResolver.assumedStateStatus(identityQuery, effectiveFood)

            val source = item.analysisSource
            val isVerifiedGlobalExact = effectiveFood != null &&
                HouseholdPortions.isHouseholdIdentity(effectiveFood, effectiveBrandHint) &&
                FoodIdentity.hasPlausibleMacros(effectiveFood)
            val compositionUnspecified = FoodIdentity.requiresDeclaredComposition(identityQuery) &&
                mappedFood == null && smartResult.learnedFoodId != effectiveFood?.id
            val identityAccepted = effectiveFood != null && !compositionUnspecified &&
                FoodIdentity.matchesDeclaredIdentity(identityQuery, effectiveFood, effectiveBrandHint) &&
                NutrientBasis.isVerified(effectiveFood) &&
                (mappedFood != null || learnedStaple != null || staticIsExact ||
                    smartResult.decision == SmartFoodResolver.Decision.AUTO_SELECT) &&
                NutrientBasis.source(effectiveFood) !in setOf(NutritionSourceKind.EXTERNAL_ESTIMATE, NutritionSourceKind.HEURISTIC_ESTIMATE, NutritionSourceKind.DATASET_ESTIMATE)
            // Los rangos semánticos describen ejemplos del dataset, no la fila local
            // ya seleccionada. La evidencia todavía puede aportar una porción por defecto,
            // pero nunca debe invalidar ni reinterpretar los macros autoritativos.
            val retrievalForMacroValidation: SemanticPortionRetriever.RetrievalResult? = null
            val preferAiLoggedFood = effectiveFood == null && shouldUseAiLoggedFood(item)
            val canonicalFamily = FoodIdentity.familyFor(effectiveFood?.name ?: identityQuery)
            val foodState = convertedTarget ?: effectiveFood?.let { FoodIdentity.stateFor(it) }
                ?: FoodIdentity.stateFor(identityQuery)
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
            val rawItemIntent = if (ambiguousPackageGrams != null) {
                AmountIntent.UNSPECIFIED
            } else if (inferPortions && item.amountIntent == AmountIntent.UNSPECIFIED) {
                AmountIntent.INFERRED_CONTEXT
            } else {
                item.amountIntent
            }
            val inferredPreview = if (rawItemIntent == AmountIntent.INFERRED_CONTEXT) {
                HouseholdPortions.inferredItemGrams(effectiveFood, identityQuery, contextResult)
            } else {
                null
            }
            val itemIntent = if (
                rawItemIntent == AmountIntent.INFERRED_CONTEXT &&
                consumedItems.size == 1 &&
                HouseholdPortions.hasClassDefault(effectiveFood, identityQuery) &&
                (
                    inferredPreview == null ||
                        inferredPreview >= HouseholdPortions.HEURISTIC_DISH_GRAMS - 1.0 ||
                        contextResult.shape == InferredMealContext.Shape.UNKNOWN
                    )
            ) {
                AmountIntent.UNSPECIFIED
            } else {
                rawItemIntent
            }
            val inferredGrams = if (itemIntent == AmountIntent.INFERRED_CONTEXT) {
                inferredPreview
            } else {
                null
            }
            val learnedFoodId = smartResult.learnedFoodId
            val explicitKilogramPreview = HouseholdPortions.isExplicitKilogram(parsed.rawDescription) ||
                HouseholdPortions.isExplicitKilogram(item.tag)
            val previewGrams = HouseholdPortions.resolveEatenGrams(
                intent = itemIntent,
                quantity = item.quantity,
                food = effectiveFood,
                parsedGrams = inferredGrams ?: consumedGrams,
                query = identityQuery,
                explicitKilogram = explicitKilogramPreview,
                unitId = item.unitId,
            )
            val resolutionStatus = HouseholdPortions.operationalAutoStatus(
                food = effectiveFood,
                grams = previewGrams,
                brandHint = effectiveBrandHint,
                explicitKilogram = explicitKilogramPreview,
                amountIntent = itemIntent,
                identityAccepted = identityAccepted,
            )
            val nutritionSource = if (compositionUnspecified) NutritionSourceKind.HEURISTIC_ESTIMATE else
                effectiveFood?.let(NutrientBasis::source) ?: NutritionSourceKind.HEURISTIC_ESTIMATE
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
            val reviewFoods = smartResult.candidates.mapNotNull { cand ->
                port.getFoodById(cand.foodId)?.takeIf { NutrientBasis.isVerified(it) &&
                    FoodIdentity.matchesExclusions(it, item.excludedIngredients) &&
                    FoodIdentity.matchesDeclaredIdentity(identityQuery, it, effectiveBrandHint) }
            }.distinctBy { it.id }.take(4)

            val resolved = if (effectiveFood != null && !preferAiLoggedFood) {
                val calibratedGrams = if (item.amountIntent == AmountIntent.UNSPECIFIED || ambiguousPackageGrams != null) {
                    // A confirmed portion belongs to this exact variant, never a family.
                    listOf(effectiveFood.id).firstNotNullOfOrNull { key ->
                        calibrationProfile?.maturePortionsGrams?.get(key)
                            ?.takeIf { it.isFinite() && it > 0.0 && HouseholdPortions.isHouseholdHint(it, effectiveFood, identityQuery) }
                    }
                } else null
                val learnedGrams = smartResult.learnedPortionGrams
                    ?.takeIf { it.isFinite() && it > 0.0 && HouseholdPortions.isHouseholdHint(it, effectiveFood, identityQuery) }
                val datasetHint: Double? = null
                val stapleGrams = FoodStapleOntology.householdDefaultGrams(identityQuery, effectiveFood)
                    ?.takeIf { item.amountIntent == AmountIntent.UNSPECIFIED }
                val explicitKilogram = HouseholdPortions.isExplicitKilogram(parsed.rawDescription) ||
                    HouseholdPortions.isExplicitKilogram(item.tag)
                var effectiveGrams = HouseholdPortions.resolveEatenGrams(
                    intent = itemIntent,
                    quantity = item.quantity,
                    food = effectiveFood,
                    parsedGrams = inferredGrams ?: consumedGrams ?: stapleGrams,
                    datasetHint = calibratedGrams ?: learnedGrams ?: datasetHint,
                    query = identityQuery,
                    explicitKilogram = explicitKilogram,
                    unitId = item.unitId,
                )
                if (item.amountIntent == AmountIntent.UNSPECIFIED || ambiguousPackageGrams != null) {
                    effectiveGrams = calibratedGrams ?: learnedGrams ?: effectiveGrams
                }
                val baseGramsBeforeSize = if (item.amountIntent == AmountIntent.RESOLVED_SUBJECTIVE && item.portion != PortionPreset.MEDIUM) {
                    effectiveGrams / (PORTION_MULTIPLIERS[item.portion] ?: 1.0)
                } else effectiveGrams
                if (item.amountIntent == AmountIntent.UNSPECIFIED && item.portion != PortionPreset.MEDIUM) {
                    effectiveGrams *= PORTION_MULTIPLIERS[item.portion] ?: 1.0
                }
                // An inferred portion remains inferred; saving must not relabel it as declared.
                val effectiveAmountIntent = itemIntent

                var logged = scaleFoodByPortion(
                    food = effectiveFood,
                    quantity = item.quantity,
                    portion = item.portion,
                    amountGrams = effectiveGrams,
                    cookingMethod = scaleMethod,
                    portionAdjustment = lockedPortionAdj,
                )
                if (convertedTarget != null) logged = logged.copy(foodName = effectiveFood.name.replace(Regex("\\s*\\([^)]*\\)"), "") + if (convertedTarget == FoodState.RAW) " (crudo, estimado)" else " (cocido, estimado)")
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

                val finalLogged = if (validated.wasAdjusted && item.amountIntent != AmountIntent.EXPLICIT_MASS) {
                    logged.copy(
                        calories = validated.adjustedCalories,
                        protein = validated.adjustedProtein,
                        carbs = validated.adjustedCarbs,
                        fats = validated.adjustedFats
                    )
                } else logged

                val oiled = if (applyOil && rememberedOil != null) {
                    val addedOil = rememberedOil * finalLogged.amount / 100.0
                    finalLogged.copy(calories = finalLogged.calories + addedOil * 9.0,
                        fats = finalLogged.fats + addedOil, analysisSource = AnalysisSource.DATABASE,
                        cookingMethod = item.cookingMethod)
                } else if (applyOil) {
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
                    brandHint = effectiveBrandHint,
                    explicitKilogram = explicitKilogram,
                    amountIntent = itemIntent,
                    identityAccepted = identityAccepted,
                )

                ResolvedTag(
                    tag = item.tag,
                    portion = item.portion,
                    quantity = item.quantity,
                    amountGrams = effectiveGrams,
                    baseAmountGrams = baseGramsBeforeSize,
                    portionMinGrams = if (item.amountIntent == AmountIntent.UNSPECIFIED) effectiveGrams * 0.75 else effectiveGrams,
                    portionMaxGrams = if (item.amountIntent == AmountIntent.UNSPECIFIED) effectiveGrams * 1.25 else effectiveGrams,
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
                    reviewCandidates = reviewFoods,
                    interpretation = interpretation,
                    canonicalFamily = canonicalFamily,
                    foodState = foodState,
                    resolutionStatus = finalStatus,
                    nutritionSource = nutritionSource,
                    nutritionEstimate = if (compositionUnspecified) NutritionEstimateEvidence(
                        assumption = "Asumí ${effectiveFood.name.lowercase()}; composición sin confirmar.",
                        referenceFoodIds = listOf(effectiveFood.id),
                        referenceSourceRecordIds = listOf(effectiveFood.sourceRecordId ?: effectiveFood.id),
                        minPer100g = NutritionProfile(0.0, 0.0, 0.0, 0.0),
                        maxPer100g = NutritionProfile(900.0, 100.0, 100.0, 100.0),
                    ) else null,
                    resolutionConfidence = resolutionConfidence,
                    resolutionMargin = resolutionMargin,
                    stateAssumed = stateAssumed,
                    stateConversion = convertedTarget?.let { target ->
                        "weight_basis:${FoodIdentity.stateFor(effectiveFood).name}->${target.name};yield=${cookingWeightYield(effectiveFood)};source=${effectiveFood.sourceRecordId ?: effectiveFood.id}"
                    },
                    learnedWeightBasis = rememberedState?.takeIf { it == foodState },
                    learnedOilGramsPer100g = rememberedOil,
                    appliedOilGrams = if (applyOil) (oiled.fats - finalLogged.fats).coerceAtLeast(0.0) else 0.0,
                    learnedFoodId = learnedFoodId,
                    foodQuery = identityQuery,
                    unitId = item.unitId,
                    excludedIngredients = item.excludedIngredients,
                    ambiguousPackageGrams = ambiguousPackageGrams,
                )
            } else {
                val dishGramsRaw = when {
                    ambiguousPackageGrams != null -> HouseholdPortions.defaultGrams(smartFood, identityQuery)
                    itemIntent == AmountIntent.INFERRED_CONTEXT && inferredGrams != null -> inferredGrams
                    item.amountIntent == AmountIntent.EXPLICIT_MASS ||
                        item.amountIntent == AmountIntent.RESOLVED_SUBJECTIVE ->
                        item.amountGrams?.takeIf { it > 0 }
                            ?: inferredGrams
                            ?: HouseholdPortions.heuristicDishGrams(identityQuery, contextResult)
                    else -> inferredGrams
                        ?: HouseholdPortions.heuristicDishGrams(identityQuery, contextResult)
                }
                val estimate = NutritionHeuristicEstimator.estimateWithEvidence(identityQuery,
                    if (FoodIdentity.normalize(identityQuery) == "ensalada") listOfNotNull(port.getFoodById("gen066"), port.getFoodById("gen026"))
                        .filter { FoodIdentity.matchesExclusions(it, item.excludedIngredients) } else emptyList())
                val profile = estimate.profile
                var dishGrams = if (item.amountIntent == AmountIntent.UNSPECIFIED &&
                    HouseholdPortions.isWholeDish(identityQuery)) {
                    HouseholdPortions.heuristicDishGrams(identityQuery, contextResult)
                } else dishGramsRaw
                val baseGramsBeforeSize = if (item.amountIntent == AmountIntent.RESOLVED_SUBJECTIVE && item.portion != PortionPreset.MEDIUM) {
                    dishGrams / (PORTION_MULTIPLIERS[item.portion] ?: 1.0)
                } else dishGrams
                if (item.amountIntent == AmountIntent.UNSPECIFIED && item.portion != PortionPreset.MEDIUM) {
                    dishGrams *= PORTION_MULTIPLIERS[item.portion] ?: 1.0
                }
                val mac = item.macroOverrides
                var logged = createLoggedFood(
                    foodName = "${item.tag} (estimado)",
                    amount = dishGrams,
                    calories = mac?.calories ?: profile.calories,
                    protein = mac?.protein ?: profile.protein,
                    carbs = mac?.carbs ?: profile.carbs,
                    fats = mac?.fats ?: profile.fats,
                    fiber = 0.0,
                    sugar = 0.0,
                    sodiumMg = 0.0,
                    potassiumMg = 0.0,
                    waterMl = 0.0,
                    portion = item.portion,
                    cookingMethod = item.cookingMethod,
                )
                logged = applyModifierScale(logged, item.modifierScale)
                val explicitKgHeuristic = HouseholdPortions.isExplicitKilogram(parsed.rawDescription) ||
                    HouseholdPortions.isExplicitKilogram(item.tag)
                if (!explicitKgHeuristic &&
                    logged.calories > HouseholdPortions.MAX_ITEM_KCAL_WITHOUT_KG &&
                    logged.calories.isFinite() &&
                    logged.calories > 0.0
                ) {
                    val factor = HouseholdPortions.MAX_ITEM_KCAL_WITHOUT_KG / logged.calories
                    dishGrams *= factor
                    logged = logged.copy(
                        amount = dishGrams,
                        calories = HouseholdPortions.MAX_ITEM_KCAL_WITHOUT_KG,
                        protein = logged.protein * factor,
                        carbs = logged.carbs * factor,
                        fats = logged.fats * factor,
                    )
                }
                val fallbackStatus = listOfNotNull(
                    contextResult.assumedLabel?.let { "Asumí $it." },
                    if (item.excludedIngredients.isNotEmpty()) "No hay una ficha verificada para esta preparación sin ${item.excludedIngredients.joinToString(", ")}." else null,
                    estimate.evidence.assumption,
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
                    baseAmountGrams = baseGramsBeforeSize,
                    portionMinGrams = if (item.amountIntent == AmountIntent.EXPLICIT_MASS) dishGrams else dishGrams * 0.65,
                    portionMaxGrams = if (item.amountIntent == AmountIntent.EXPLICIT_MASS) dishGrams else dishGrams * 1.35,
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
                    reviewCandidates = reviewFoods,
                    canonicalFamily = canonicalFamily,
                    foodState = foodState,
                    resolutionStatus = FoodResolutionStatus.NEEDS_CONFIRMATION,
                    nutritionSource = NutritionSourceKind.HEURISTIC_ESTIMATE,
                    nutritionEstimate = estimate.evidence,
                    resolutionConfidence = resolutionConfidence,
                    resolutionMargin = resolutionMargin,
                    foodQuery = identityQuery,
                    unitId = item.unitId,
                    excludedIngredients = item.excludedIngredients,
                    ambiguousPackageGrams = ambiguousPackageGrams,
                )
            }
            resolvedTags += resolved
        }

        val compositionDescription = if (parsed.items.any { it.isExcluded }) consumedDescription else parsed.rawDescription
        val combination = FoodCombinationParser.parse(compositionDescription)
        // A full-dish expansion has no recipe quantities with which to remove an ingredient.
        val sandwichExpanded = if (parsed.items.any { it.excludedIngredients.isNotEmpty() || it.isExcluded }) null
            else expandSandwichComponents(combination, parsed.rawDescription, resolvedTags)
        if (sandwichExpanded != null) {
            resolvedTags.clear()
            resolvedTags.addAll(sandwichExpanded)
        }

        val isSingleTagPlate = resolvedTags.count { !it.isExcluded } == 1
        val exactPlate = findFoodExactByNormalized(parsed.rawDescription) != null

        if (sandwichExpanded == null && !isSingleTagPlate && !exactPlate && combination.confidence >= 0.70) {
            val totalGrams = resolvedTags.filterNot { it.isExcluded }.sumOf { it.loggedFood?.amount ?: 0.0 }
            val comboParts = buildList {
                add(Triple(combination.baseFood, combination.baseProportion, FoodCombinationParser.Role.STARCH))
                combination.accompaniments.forEach { acc ->
                    add(Triple(acc.food, acc.proportion, acc.role))
                }
            }
            if (totalGrams > 0 && comboParts.isNotEmpty()) {
                for ((foodName, proportion, role) in comboParts) {
                    val matching = resolvedTags.filterNot { it.isExcluded }.filter { tag ->
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
        val clarified = resolvedTags.map { NutritionInterpretationBridge.enrich(it, parsed) }
        Pair(clarified, contextResult)
    }

    private suspend fun expandSandwichComponents(
        combination: FoodCombinationParser.ParsedCombination,
        rawDescription: String,
        existing: List<ResolvedTag>,
    ): List<ResolvedTag>? {
        val blob = FoodIdentity.normalize(rawDescription)
        if (!blob.contains("sandwich")) return null
        if (combination.baseFood != "pan" || combination.confidence < 0.70) return null
        val hasBread = existing.any { tag ->
            val n = FoodIdentity.normalize("${tag.foodItem?.name.orEmpty()} ${tag.tag}")
            n.contains("pan") || n.contains("hallulla") || n.contains("marraqueta")
        }
        if (hasBread) return null
        val parts = buildList {
            add(combination.baseFood)
            combination.accompaniments.forEach { add(it.food) }
        }
        if (parts.size < 2) return null
        val tags = parts.map { name ->
            val food = HouseholdPortions.householdStaticFood(name) ?: port.staticFood(name)
            val grams = HouseholdPortions.defaultGrams(food, name)
            if (food != null) {
                val logged = scaleFoodByPortion(
                    food = food,
                    quantity = 1.0,
                    portion = PortionPreset.MEDIUM,
                    amountGrams = grams,
                    cookingMethod = null,
                    portionAdjustment = 1.0,
                )
                val status = HouseholdPortions.operationalAutoStatus(
                    food = food,
                    grams = grams,
                    brandHint = null,
                    explicitKilogram = false,
                    amountIntent = AmountIntent.INFERRED_CONTEXT,
                )
                ResolvedTag(
                    tag = name,
                    amountGrams = grams,
                    baseAmountGrams = grams,
                    portionMinGrams = grams,
                    portionMaxGrams = grams,
                    foodItem = food,
                    loggedFood = logged.copy(analysisSource = AnalysisSource.DATABASE),
                    isResolved = status == FoodResolutionStatus.AUTO,
                    analysisSource = AnalysisSource.DATABASE,
                    statusText = "Asumí ${grams.toInt()} g.",
                    amountIntent = AmountIntent.INFERRED_CONTEXT,
                    canonicalFamily = FoodIdentity.familyFor(food),
                    foodState = FoodIdentity.stateFor(food),
                    resolutionStatus = status,
                    nutritionSource = NutritionSourceKind.CURATED_LOCAL,
                    resolutionConfidence = combination.confidence,
                )
            } else {
                val profile = NutritionHeuristicEstimator.estimatePer100g(name)
                val logged = createLoggedFood(
                    foodName = "$name (estimado)",
                    amount = grams,
                    calories = profile.calories,
                    protein = profile.protein,
                    carbs = profile.carbs,
                    fats = profile.fats,
                )
                ResolvedTag(
                    tag = name,
                    amountGrams = grams,
                    baseAmountGrams = grams,
                    portionMinGrams = grams,
                    portionMaxGrams = grams,
                    loggedFood = logged.copy(analysisSource = AnalysisSource.LOCAL_HEURISTIC),
                    isResolved = logged.calories.isFinite(),
                    isFuzzyMatch = true,
                    analysisSource = AnalysisSource.LOCAL_HEURISTIC,
                    statusText = "Asumí ${grams.toInt()} g.",
                    amountIntent = AmountIntent.INFERRED_CONTEXT,
                    nutritionSource = NutritionSourceKind.HEURISTIC_ESTIMATE,
                    resolutionConfidence = combination.confidence,
                )
            }
        }
        return tags.takeIf { it.size >= 2 }
    }
}

// ─── Helpers compartidos (extraídos del drawer) ─────────────────────────────

fun oilGramsForLevel(oilLevel: String): Double = when (oilLevel.lowercase()) {
    "sin aceite", "none", "zero", "cero" -> 0.0
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
    if (isExcluded) return false
    interpretationV2?.let { return it.pendingQuestions.any { question -> question.material } }
    if (needsCutClarification && stapleCutOptions.isNotEmpty()) return true
    if (needsCookingClarification || needsOilClarification) return true
    if (explicitDecision) return false
    if (resolutionStatus == FoodResolutionStatus.AUTO && isResolved) return false
    return !isResolved || resolutionStatus in setOf(
        FoodResolutionStatus.NEEDS_REVIEW,
        FoodResolutionStatus.NEEDS_CONFIRMATION,
        FoodResolutionStatus.NO_RESOLVED,
    )
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
    val remaining = oldTags.filter { it.hasManualEdits }.toMutableList()
    return newTags.map { newTag ->
        val match = remaining.indexOfFirst { oldTag ->
            FoodIdentity.normalize(oldTag.tag) == FoodIdentity.normalize(newTag.tag) &&
                oldTag.cookingMethod == newTag.cookingMethod && oldTag.unitId == newTag.unitId &&
                oldTag.isExcluded == newTag.isExcluded && oldTag.excludedIngredients == newTag.excludedIngredients
        }
        if (match >= 0) remaining.removeAt(match) else newTag
    }
}
