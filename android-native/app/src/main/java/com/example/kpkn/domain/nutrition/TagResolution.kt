package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.*
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
    /** D2: instrucción del ejemplo más similar del dataset ("Entendí: …"). */
    val interpretation: String? = null,
    val canonicalFamily: String? = null,
    val foodState: FoodState = FoodState.UNKNOWN,
    val resolutionStatus: FoodResolutionStatus = FoodResolutionStatus.NO_RESOLVED,
    val nutritionSource: NutritionSourceKind = NutritionSourceKind.HEURISTIC_ESTIMATE,
    val resolutionConfidence: Double? = null
)

/** Acceso a datos del resolver — implementado por el drawer con NutritionRepository. */
interface FoodResolutionPort {
    suspend fun resolveSmart(tag: String, brandHint: String?, contextHint: String? = null): SmartFoodResolver.ResolutionResult
    suspend fun getFoodById(id: String): FoodItem?
    suspend fun staticFood(tag: String): FoodItem?
    fun staticIsExact(tag: String): Boolean
    fun recordLearned(query: String, brandHint: String?, foodId: String, portionGrams: Double?, cookingMethod: String?)
}

class TagResolver(private val port: FoodResolutionPort) {

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
        val proteinB = contextResult.proteinAdjustment

        for (item in parsed.items) {
            val (lockedPortionAdj, lockedProteinB) = scalingForIntent(item.amountIntent, portionAdj, proteinB)

            // Phase B: SmartFoodResolver para matching fuzzy sobre toda la DB
            // (con contextHint = descripción completa para boost de co-ocurrencia D6)
            val smartResult = port.resolveSmart(item.tag, item.brandHint, parsed.rawDescription)
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
                    port.getFoodById(smartCandidate.foodId)
                }
                else -> staticFood
            }

            // Prefer DB row that already encodes the method (pollo frito → pechuga frita).
            val preparedVariant = CookingStateResolver.findPreparedVariant(item.tag, item.cookingMethod)
            val usingPreparedVariant = preparedVariant != null
            val effectiveFood = when {
                preparedVariant != null -> preparedVariant
                item.cookingMethod != null && item.cookingMethod != CookingMethod.CRUDO && food != null -> {
                    if (CookingStateResolver.isAlreadyPreparedForMethod(food, item.cookingMethod)) {
                        CookingStateResolver.findRawVariant(food) ?: food
                    } else food
                }
                else -> food
            }

            // Oil only when frying on a raw/base profile (not when DB row is already fried).
            val applyOil = CookingStateResolver.shouldApplyOil(
                if (usingPreparedVariant) preparedVariant else effectiveFood,
                item.cookingMethod,
            ) && !usingPreparedVariant
            val effectiveOilLevel = when {
                !applyOil -> "medio"
                item.isExcluded && isOilTag(item.tag) -> "poco"
                hasExcludedOil && hasGreaseCooking -> "poco"
                else -> "medio"
            }
            // Method passed to scaler: none when macros already include preparation.
            val scaleMethod = if (usingPreparedVariant) null else item.cookingMethod

            val isSmartMatch = smartResult.decision != SmartFoodResolver.Decision.UNRESOLVED && smartCandidate != null
            val requiresCandidateReview =
                smartResult.decision == SmartFoodResolver.Decision.NEEDS_REVIEW &&
                    smartCandidate?.foodId == effectiveFood?.id

            val clarifyKind = CookingStateResolver.clarificationKind(
                item.tag, effectiveFood, item.cookingMethod,
            )
            val needsClarify = clarifyKind != CookingStateResolver.ClarificationKind.NONE
            val assumeStatus = if (needsClarify) {
                CookingStateResolver.assumedStateStatus(item.tag, effectiveFood)
            } else null

            val source = item.analysisSource
            val localAuthority = effectiveFood != null && (
                staticIsExact ||
                    preparedVariant != null ||
                    (smartResult.decision == SmartFoodResolver.Decision.AUTO_SELECT &&
                        smartCandidate?.source == "LOCAL")
                )
            val preferAiLoggedFood = effectiveFood == null && shouldUseAiLoggedFood(item)
            val canonicalFamily = FoodIdentity.familyFor(effectiveFood?.name ?: item.tag)
            val foodState = effectiveFood?.let { FoodIdentity.stateFor(it) }
                ?: FoodIdentity.stateFor(item.tag)
            val resolutionConfidence = when {
                staticIsExact -> 1.0
                smartCandidate != null -> smartCandidate.score
                else -> item.analysisConfidence

            }
            val resolutionStatus = when {
                needsClarify -> FoodResolutionStatus.NEEDS_STATE
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
            // D2: el ejemplo más similar del dataset como interpretación
            val interpretation = retrievalResult.matches
                .firstOrNull()
                ?.takeIf {
                    retrievalResult.confidence >= 0.35 &&
                        !needsClarify &&
                        FoodIdentity.familyFor(item.tag) == null
                }
                ?.instruction
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
                val effectiveGrams = when (item.amountIntent) {
                    AmountIntent.EXPLICIT_MASS, AmountIntent.RESOLVED_SUBJECTIVE -> item.amountGrams
                    AmountIntent.UNSPECIFIED ->
                        item.amountGrams ?: SemanticPortionRetriever.getGramsForFood(item.tag, retrievalResult)
                }

                var logged = scaleFoodByPortion(
                    food = effectiveFood,
                    quantity = item.quantity,
                    portion = item.portion,
                    amountGrams = effectiveGrams,
                    cookingMethod = scaleMethod,
                    portionAdjustment = lockedPortionAdj,
                    proteinBoost = lockedProteinB,
                )
                logged = applyModifierScale(logged, item.modifierScale)
                val validated = MacroValidator.validate(
                    input = MacroValidator.MacroInput(
                        calories = logged.calories,
                        protein = logged.protein,
                        carbs = logged.carbs,
                        fats = logged.fats
                    ),
                    retrievalResult = retrievalResult,
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
                    )
                } else {
                    finalLogged.copy(
                        analysisSource = AnalysisSource.DATABASE,
                        cookingMethod = item.cookingMethod ?: finalLogged.cookingMethod,
                    )
                }

                val warningText = listOfNotNull(
                    validated.warnings.firstOrNull()?.takeIf { it.isNotBlank() && !needsClarify },
                    if (needsClarify) "Falta el estado: selecciona seco o cocido para calcular los macros." else assumeStatus,
                    if (requiresCandidateReview && !needsClarify) "Coincidencia aproximada: revisa el alimento seleccionado." else null,
                    interpretation?.let { "Entendí: $it" },
                ).joinToString(" ")

                ResolvedTag(
                    tag = item.tag,
                    portion = item.portion,
                    quantity = item.quantity,
                    amountGrams = item.amountGrams ?: effectiveGrams,
                    cookingMethod = item.cookingMethod,
                    foodItem = effectiveFood,
                    loggedFood = oiled.takeUnless { needsClarify },
                    isResolved = resolutionStatus == FoodResolutionStatus.AUTO,
                    isFuzzyMatch = isSmartMatch && smartCandidate?.confidence != SmartFoodResolver.Confidence.HIGH,
                    analysisSource = AnalysisSource.DATABASE,
                    statusText = warningText,
                    oilLevel = effectiveOilLevel,
                    isExcluded = item.isExcluded,
                    amountIntent = item.amountIntent,
                    needsCookingClarification = needsClarify,
                    clarificationKind = clarifyKind,
                    oilApplied = applyOil,
                    reviewCandidates = reviewCandidates,
                    interpretation = interpretation,
                    canonicalFamily = canonicalFamily,
                    foodState = foodState,
                    resolutionStatus = resolutionStatus,
                    nutritionSource = nutritionSource,
                    resolutionConfidence = resolutionConfidence,
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
                    foodName = item.tag,
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
                    retrievalResult = retrievalResult,
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

                    if (maxAllowedGrams != null &&
                        match.amountIntent == AmountIntent.UNSPECIFIED &&
                        match.amountGrams == null &&
                        existingLogged.amount > maxAllowedGrams &&
                        maxAllowedGrams > 1.0
                    ) {
                        val capped = scaleFoodByPortion(
                            food = existingFood,
                            quantity = match.quantity,
                            portion = PortionPreset.MEDIUM,
                            amountGrams = maxAllowedGrams,
                            cookingMethod = match.cookingMethod,
                            portionAdjustment = 1.0, // Capped explicitly
                            proteinBoost = 0.0,
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
}

// ─── Helpers compartidos (extraídos del drawer) ─────────────────────────────

fun oilGramsForLevel(oilLevel: String): Double = when (oilLevel.lowercase()) {
    "poco" -> 3.0
    "abundante" -> 18.0
    else -> 8.0
}

fun adjustLoggedFoodForOil(logged: LoggedFood, method: CookingMethod?, oilLevel: String): LoggedFood {
    if (method != CookingMethod.FRITO && method != CookingMethod.EMPANIZADO_FRITO) {
        return logged
    }
    val oilGrams = oilGramsForLevel(oilLevel)
    val addedFat = oilGrams
    val addedCal = oilGrams * 9
    return logged.copy(
        fats = kotlin.math.round((logged.fats + addedFat) * 10.0) / 10.0,
        calories = kotlin.math.round(logged.calories + addedCal),
    )
}

fun stripOilFromLoggedFood(logged: LoggedFood, method: CookingMethod?, oilLevel: String): LoggedFood {
    if (method != CookingMethod.FRITO && method != CookingMethod.EMPANIZADO_FRITO) {
        return logged
    }
    val oilGrams = oilGramsForLevel(oilLevel)
    return logged.copy(
        fats = kotlin.math.round((logged.fats - oilGrams).coerceAtLeast(0.0) * 10.0) / 10.0,
        calories = kotlin.math.round((logged.calories - oilGrams * 9).coerceAtLeast(0.0)),
    )
}

fun scalingForIntent(
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
