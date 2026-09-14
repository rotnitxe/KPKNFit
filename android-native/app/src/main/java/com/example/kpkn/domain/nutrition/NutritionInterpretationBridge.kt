package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.food.findStaticFoodById
import com.example.kpkn.data.models.AmountIntent
import com.example.kpkn.data.models.CookingMethod
import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.data.models.ParsedMealDescription
import com.example.kpkn.data.models.ParsedMealItem
import kotlin.math.abs

/** One result per already-resolved mention; this is the logger's clarification authority. */
object NutritionInterpretationBridge {
    const val VEGETABLE_COMPOSITION_ID = "estimate:salad_vegetables"
    private val engine = FoodInterpretationV2Engine()

    /**
     * Accept the current estimate without allowing an unresolved tortilla
     * alias to turn into the wheat wrap that was only used as a preview.
     *
     * "No estoy seguro" resolves the question gate, not the composition. A
     * generic tortilla therefore keeps its declared mass and nutrition range,
     * but drops the provisional catalog identity and carries a neutral note.
     */
    fun acceptEstimate(tag: ResolvedTag): ResolvedTag {
        if (tag.unresolvedDeclaredAmount) return tag
        if (FoodIdentity.requiresDeclaredComposition(tag.foodQuery.ifBlank { tag.tag }) &&
            "identity" !in tag.confirmedDimensions
        ) {
            val neutralEstimate = tag.nutritionEstimate?.copy(
                assumption = "Tortilla sin ficha; composición no confirmada.",
                referenceFoodIds = emptyList(),
                referenceSourceRecordIds = emptyList(),
                requiresCompositionClarification = false,
                isUnmatchedFallback = true,
            )
            val neutralLogged = tag.loggedFood?.copy(foodName = "Tortilla")
            return refresh(tag.copy(
                tag = "tortilla",
                foodQuery = "tortilla",
                foodItem = null,
                loggedFood = neutralLogged,
                baseLoggedFood = tag.baseLoggedFood?.copy(foodName = "Tortilla") ?: neutralLogged,
                nutritionEstimate = neutralEstimate,
                nutritionSource = NutritionSourceKind.HEURISTIC_ESTIMATE,
                isResolved = neutralLogged != null,
                isUncertain = true,
                explicitDecision = true,
                confirmedDimensions = emptySet(),
                needsCookingClarification = false,
                clarificationKind = CookingStateResolver.ClarificationKind.NONE,
                needsCutClarification = false,
                stapleCutOptions = emptyList(),
                needsOilClarification = false,
                resolutionStatus = FoodResolutionStatus.CONFIRMED_ESTIMATE,
                statusText = "Estimación aceptada; composición de la tortilla sin confirmar.",
                hasManualEdits = true,
            ))
        }
        val interpretation = tag.interpretationV2 ?: return tag
        val accepted = interpretation.copy(
            pendingQuestions = emptyList(),
            isUncertain = true,
            isConfirmedEstimate = false,
        )
        return tag.copy(
            interpretationV2 = accepted,
            loggedFood = tag.loggedFood?.let(accepted::toLoggedFood),
            isResolved = tag.loggedFood != null,
            isUncertain = true,
            explicitDecision = true,
            confirmedDimensions = emptySet(),
            needsCookingClarification = false,
            clarificationKind = CookingStateResolver.ClarificationKind.NONE,
            needsCutClarification = false,
            stapleCutOptions = emptyList(),
            needsOilClarification = false,
            resolutionStatus = FoodResolutionStatus.CONFIRMED_ESTIMATE,
            statusText = "Estimación aceptada; no se aprende como hábito.",
            hasManualEdits = true,
        )
    }

    fun enrich(tag: ResolvedTag, rawDescription: String): ResolvedTag =
        enrich(tag, ParsedMealDescription(rawDescription = rawDescription, verbatimDescription = rawDescription))

    fun refresh(tag: ResolvedTag): ResolvedTag = enrich(tag, ParsedMealDescription(
        items = listOf(ParsedMealItem(tag = tag.tag, amountGrams = tag.amountGrams, amountIntent = tag.amountIntent, cookingMethod = tag.cookingMethod)),
        rawDescription = tag.tag, verbatimDescription = tag.tag,
    ))

    fun enrich(tag: ResolvedTag, parsed: ParsedMealDescription): ResolvedTag {
        val item = parsed.items.firstOrNull {
            FoodIdentity.normalize(it.tag) == FoodIdentity.normalize(tag.tag) && it.cookingMethod == tag.cookingMethod
        } ?: parsed.items.singleOrNull()
        val acceptedEstimate = tag.explicitDecision && tag.confirmedDimensions.isEmpty()
        val grams = tag.amountGrams ?: tag.loggedFood?.amount ?: 100.0
        val mentionText = item?.foodQuery?.ifBlank { item.tag } ?: tag.foodQuery.ifBlank { tag.tag }
        val cut = FoodStapleOntology.cutClarification(tag.tag, mentionText, tag.learnedFoodId)
            ?.takeIf { (item?.amountIntent ?: tag.amountIntent) == AmountIntent.UNSPECIFIED }
            ?.let { clarification -> clarification.copy(options = clarification.options.filter { option ->
                findStaticFoodById(option.foodId)?.let { NutrientBasis.isVerified(it) && FoodIdentity.matchesExclusions(it, tag.excludedIngredients) } == true
            }) }
            ?.takeIf { clarification ->
                val foods = clarification.options.mapNotNull { findStaticFoodById(it.foodId) }
                foods.any { a -> foods.any { b -> materialNutritionDelta(a, b, grams) } }
            }
        val compositionConfirmed = "composition" in tag.confirmedDimensions
        val compositionPending = tag.nutritionEstimate?.requiresCompositionClarification == true &&
            !compositionConfirmed && !acceptedEstimate && !tag.isExcluded
        val validCandidates = tag.reviewCandidates.filter {
            NutrientBasis.isVerified(it) && FoodIdentity.matchesDeclaredIdentity(tag.foodQuery.ifBlank { tag.tag }, it) &&
                FoodIdentity.matchesExclusions(it, tag.excludedIngredients)
        }
        val identityPending = !tag.isExcluded && !acceptedEstimate && !compositionConfirmed && "identity" !in tag.confirmedDimensions &&
            (tag.foodItem == null || tag.resolutionStatus in setOf(FoodResolutionStatus.NEEDS_REVIEW, FoodResolutionStatus.NO_RESOLVED) ||
                (tag.resolutionStatus == FoodResolutionStatus.NEEDS_CONFIRMATION && tag.interpretationV2 == null) ||
                tag.interpretationV2?.pendingQuestions?.any { it.requestId == "identity" } == true)
        val weightKind = if ("state" in tag.confirmedDimensions || tag.learnedWeightBasis != null || acceptedEstimate || tag.isExcluded) CookingStateResolver.ClarificationKind.NONE else cookingQuestionKind(tag, item)
        val oilPending = !acceptedEstimate && !tag.isExcluded && "oil" !in tag.confirmedDimensions &&
            tag.learnedOilGramsPer100g == null && tag.oilLevel == "medio" && shouldAskOil(tag)
        val packageGrams = tag.ambiguousPackageGrams?.takeIf { it.isFinite() && it > 0.0 }
        val packagePending = packageGrams != null && !acceptedEstimate && !tag.isExcluded && "portion" !in tag.confirmedDimensions
        val questions = buildList {
            if (!acceptedEstimate && !tag.isExcluded && "identity" !in tag.confirmedDimensions) {
                when {
                    compositionPending -> {
                        val ingredients = tag.nutritionEstimate?.referenceFoodIds.orEmpty().mapNotNull(::findStaticFoodById)
                            .joinToString(" y ") { it.name.lowercase() }.replaceFirstChar { it.uppercase() }
                        val alternative = findStaticFoodById("cl028")?.takeIf { NutrientBasis.isVerified(it) && FoodIdentity.matchesExclusions(it, tag.excludedIngredients) }
                        add(ClarificationRequest.Identity("composition",
                            listOfNotNull(VEGETABLE_COMPOSITION_ID, alternative?.id),
                            listOfNotNull("$ingredients, sin aderezo", alternative?.name),
                        ))
                    }
                    identityPending && FoodIdentity.requiresDeclaredComposition(tag.foodQuery.ifBlank { tag.tag }) -> {
                        val wheat = findStaticFoodById("gen090")?.takeIf {
                            NutrientBasis.isVerified(it) && FoodIdentity.matchesDeclaredIdentity(tag.foodQuery.ifBlank { tag.tag }, it) &&
                                FoodIdentity.matchesExclusions(it, tag.excludedIngredients)
                        }
                        add(ClarificationRequest.Identity("tortilla_composition", listOfNotNull(wheat?.id), listOfNotNull(wheat?.let { "Trigo (wrap)" })))
                    }
                    identityPending -> add(ClarificationRequest.Identity("identity", validCandidates.map { it.id }, validCandidates.map { it.name }))
                    cut != null -> add(ClarificationRequest.Identity("cut", cut.options.map { it.foodId }, cut.options.map { it.label }))
                }
            }
            if (weightKind != CookingStateResolver.ClarificationKind.NONE) add(ClarificationRequest.WeightState("weight_state"))
            if (oilPending) add(ClarificationRequest.Oil("oil"))
            if (packagePending) {
                val packageLabel = if (tag.unitId == "ml") {
                    val density = SubjectivePortionEngine.massFromVolumeMl(1.0, tag.foodItem?.name ?: tag.tag)
                    "${kotlin.math.round(packageGrams!! / density).toInt()} ml"
                } else "${kotlin.math.round(packageGrams!!).toInt()} g"
                add(ClarificationRequest.Portion("package_portion", listOf(
                    PortionOption("Comí todo el envase ($packageLabel)", packageGrams!!),
                    PortionOption("Una porción habitual", grams, grams * 0.75, grams * 1.25),
                )))
            }
            if (tag.unresolvedDeclaredAmount && !tag.isExcluded) add(ClarificationRequest.Portion("declared_amount", emptyList()))
        }
        val updated = tag.copy(
            needsCutClarification = questions.any { it.requestId == "cut" },
            stapleCutOptions = if (questions.any { it.requestId == "cut" }) cut?.options.orEmpty() else emptyList(),
            needsCookingClarification = weightKind != CookingStateResolver.ClarificationKind.NONE,
            clarificationKind = weightKind, needsOilClarification = oilPending,
            isResolved = questions.isEmpty() && tag.loggedFood != null,
            resolutionStatus = when {
                questions.isNotEmpty() -> if (identityPending) FoodResolutionStatus.NEEDS_REVIEW else FoodResolutionStatus.NEEDS_CONFIRMATION
                tag.loggedFood == null -> FoodResolutionStatus.NO_RESOLVED
                acceptedEstimate -> FoodResolutionStatus.CONFIRMED_ESTIMATE
                else -> FoodResolutionStatus.AUTO
            },
            baseLoggedFood = tag.baseLoggedFood ?: tag.loggedFood,
            portionMinGrams = if (packagePending) minOf(tag.portionMinGrams ?: grams * 0.75, packageGrams!!) else tag.portionMinGrams,
            portionMaxGrams = if (packagePending) maxOf(tag.portionMaxGrams ?: grams * 1.25, packageGrams!!) else tag.portionMaxGrams,
            loggedFood = if (acceptedEstimate) tag.loggedFood else tag.loggedFood?.copy(
                caloriesMin = null, caloriesMax = null, proteinMin = null, proteinMax = null,
                carbsMin = null, carbsMax = null, fatsMin = null, fatsMax = null,
            ),
        )
        var interpretation = engine.interpretResolved(updated, questions)
        updated.nutritionEstimate?.let { estimate ->
            val minScale = (interpretation.portionMinGrams ?: grams) / 100.0
            val maxScale = (interpretation.portionMaxGrams ?: grams) / 100.0
            interpretation = interpretation.copy(
                caloriesMin = minOf(interpretation.caloriesMin, estimate.minPer100g.calories * minScale),
                caloriesMax = maxOf(interpretation.caloriesMax, estimate.maxPer100g.calories * maxScale),
                proteinMinGrams = minOf(interpretation.proteinMinGrams, estimate.minPer100g.protein * minScale),
                proteinMaxGrams = maxOf(interpretation.proteinMaxGrams, estimate.maxPer100g.protein * maxScale),
                carbsMinGrams = minOf(interpretation.carbsMinGrams, estimate.minPer100g.carbs * minScale),
                carbsMaxGrams = maxOf(interpretation.carbsMaxGrams, estimate.maxPer100g.carbs * maxScale),
                fatMinGrams = minOf(interpretation.fatMinGrams, estimate.minPer100g.fats * minScale),
                fatMaxGrams = maxOf(interpretation.fatMaxGrams, estimate.maxPer100g.fats * maxScale),
                isUncertain = true,
            )
        }
        if (questions.any { it.requestId == "cut" }) {
            val alternatives = cut?.options.orEmpty().mapNotNull { findStaticFoodById(it.foodId) }
            for (food in alternatives) {
                val scale = grams / NutrientBasis.grams(food)
                interpretation = interpretation.copy(
                    caloriesMin = minOf(interpretation.caloriesMin, food.calories * scale),
                    caloriesMax = maxOf(interpretation.caloriesMax, food.calories * scale),
                    proteinMinGrams = minOf(interpretation.proteinMinGrams, food.protein * scale),
                    proteinMaxGrams = maxOf(interpretation.proteinMaxGrams, food.protein * scale),
                    fatMinGrams = minOf(interpretation.fatMinGrams, food.fats * scale),
                    fatMaxGrams = maxOf(interpretation.fatMaxGrams, food.fats * scale),
                )
            }
        }
        // State and oil intervals compare alternatives on the same eaten amount.
        if (weightKind != CookingStateResolver.ClarificationKind.NONE) {
            val raw = CookingStateResolver.findDryOrCookedVariant(tag.tag, false) ?: tag.foodItem?.let(CookingStateResolver::findRawVariant)
            val cooked = CookingStateResolver.findDryOrCookedVariant(tag.tag, true) ?: tag.foodItem
            if (raw != null && cooked != null) {
                fun kcal(food: FoodItem) = food.calories * grams / NutrientBasis.grams(food)
                fun protein(food: FoodItem) = food.protein * grams / NutrientBasis.grams(food)
                fun fat(food: FoodItem) = food.fats * grams / NutrientBasis.grams(food)
                interpretation = interpretation.copy(
                    caloriesMin = minOf(interpretation.caloriesMin, kcal(raw), kcal(cooked)),
                    caloriesMax = maxOf(interpretation.caloriesMax, kcal(raw), kcal(cooked)),
                    proteinMinGrams = minOf(interpretation.proteinMinGrams, protein(raw), protein(cooked)),
                    proteinMaxGrams = maxOf(interpretation.proteinMaxGrams, protein(raw), protein(cooked)),
                    fatMinGrams = minOf(interpretation.fatMinGrams, fat(raw), fat(cooked)),
                    fatMaxGrams = maxOf(interpretation.fatMaxGrams, fat(raw), fat(cooked)),
                )
            }
        }
        if (oilPending) {
            val currentOil = tag.appliedOilGrams ?: if (tag.oilApplied) estimatedOilGrams(tag, tag.oilLevel) else 0.0
            val maximumOil = estimatedOilGrams(tag, "abundante")
            interpretation = interpretation.copy(
                oilMinGrams = 0.0, oilMaxGrams = maximumOil,
                caloriesMin = (interpretation.caloriesMin - currentOil * 9.0).coerceAtLeast(0.0),
                caloriesMax = interpretation.caloriesMax + (maximumOil - currentOil).coerceAtLeast(0.0) * 9.0,
                fatMinGrams = (interpretation.fatMinGrams - currentOil).coerceAtLeast(0.0),
                fatMaxGrams = interpretation.fatMaxGrams + (maximumOil - currentOil).coerceAtLeast(0.0),
            )
        }
        return updated.copy(
            interpretationV2 = interpretation,
            loggedFood = updated.loggedFood?.let(interpretation::toLoggedFood),
        )
    }

    fun applyCutOption(tag: ResolvedTag, foodId: String): ResolvedTag {
        val food = findStaticFoodById(foodId) ?: return tag
        if (!NutrientBasis.isVerified(food) || !FoodIdentity.matchesExclusions(food, tag.excludedIngredients)) return tag
        val grams = FoodStapleOntology.householdDefaultGrams(tag.tag, food) ?: tag.amountGrams ?: HouseholdPortions.defaultGrams(food, tag.tag)
        val logged = scaleFoodByPortion(food, tag.quantity, tag.portion, grams, tag.cookingMethod)
        val applyOil = CookingStateResolver.shouldApplyOil(food, tag.cookingMethod)
        val oiled = if (applyOil) adjustLoggedFoodForOil(logged, tag.cookingMethod, tag.oilLevel, foodName = food.name) else logged
        return refresh(tag.copy(
            foodItem = food, amountGrams = grams, baseAmountGrams = grams,
            portionMinGrams = grams * 0.75, portionMaxGrams = grams * 1.25,
            loggedFood = oiled, baseLoggedFood = oiled, oilApplied = applyOil,
            appliedOilGrams = (oiled.fats - logged.fats).coerceAtLeast(0.0),
            confirmedDimensions = tag.confirmedDimensions + "identity",
            isResolved = true, resolutionStatus = FoodResolutionStatus.AUTO,
            explicitDecision = false, statusText = "Corte confirmado.",
            canonicalFamily = FoodIdentity.familyFor(food), foodState = CookingStateResolver.stateForMethod(tag.cookingMethod) ?: FoodIdentity.stateFor(food),
            nutritionSource = NutrientBasis.source(food),
            nutritionEstimate = null,
            hasManualEdits = true,
        ))
    }

    /** Confirm ingredients, while keeping their proportion and nutrients explicitly estimated. */
    fun confirmEstimatedComposition(tag: ResolvedTag): ResolvedTag {
        val estimate = tag.nutritionEstimate ?: return tag
        if (!estimate.requiresCompositionClarification) return tag
        val references = estimate.referenceFoodIds.mapNotNull(::findStaticFoodById)
            .filter { NutrientBasis.isVerified(it) && FoodIdentity.matchesExclusions(it, tag.excludedIngredients) }
        if (references.size != estimate.referenceFoodIds.size || references.isEmpty()) return tag
        val profiles = references.map { food ->
            val factor = 100.0 / NutrientBasis.grams(food)
            NutritionProfile(food.calories * factor, food.protein * factor, food.carbs * factor, food.fats * factor)
        }
        val ingredients = references.joinToString(" y ") { it.name.lowercase() }.replaceFirstChar { it.uppercase() }
        return refresh(tag.copy(
            nutritionEstimate = estimate.copy(
                assumption = "$ingredients sin aderezo." + if (references.size > 1) " Proporción estimada a partes iguales." else "",
                minPer100g = NutritionProfile(profiles.minOf { it.calories }, profiles.minOf { it.protein }, profiles.minOf { it.carbs }, profiles.minOf { it.fats }),
                maxPer100g = NutritionProfile(profiles.maxOf { it.calories }, profiles.maxOf { it.protein }, profiles.maxOf { it.carbs }, profiles.maxOf { it.fats }),
                requiresCompositionClarification = false,
            ),
            confirmedDimensions = tag.confirmedDimensions + "composition", explicitDecision = false, isUncertain = true,
        ))
    }

    fun materialKcalDelta(foodA: FoodItem, foodB: FoodItem, grams: Double): Double {
        val a = foodA.calories * grams / NutrientBasis.grams(foodA)
        val b = foodB.calories * grams / NutrientBasis.grams(foodB)
        return abs(a - b) / minOf(a, b).coerceAtLeast(1.0)
    }

    fun materialNutritionDelta(a: FoodItem, b: FoodItem, grams: Double): Boolean {
        val sa = grams / NutrientBasis.grams(a)
        val sb = grams / NutrientBasis.grams(b)
        return abs(a.calories * sa - b.calories * sb) >= 50.0 ||
            abs(a.protein * sa - b.protein * sb) >= 5.0 || abs(a.fats * sa - b.fats * sb) >= 5.0
    }

    private fun cookingQuestionKind(tag: ResolvedTag, item: ParsedMealItem?): CookingStateResolver.ClarificationKind {
        if ((item?.amountIntent ?: tag.amountIntent) != AmountIntent.EXPLICIT_MASS || tag.cookingMethod != null) return CookingStateResolver.ClarificationKind.NONE
        if (FoodIdentity.stateFor(tag.tag) != FoodState.UNKNOWN || !FoodIdentity.isStateSensitive(tag.tag)) return CookingStateResolver.ClarificationKind.NONE
        val kind = CookingStateResolver.clarificationKind(tag.tag, tag.foodItem, null)
        val raw = CookingStateResolver.findDryOrCookedVariant(tag.tag, false) ?: tag.foodItem?.let(CookingStateResolver::findRawVariant)
        val cooked = CookingStateResolver.findDryOrCookedVariant(tag.tag, true) ?: tag.foodItem
        return if (raw != null && cooked != null && materialNutritionDelta(raw, cooked, tag.amountGrams ?: 100.0)) kind else CookingStateResolver.ClarificationKind.NONE
    }

    private fun shouldAskOil(tag: ResolvedTag): Boolean {
        val method = tag.cookingMethod ?: return false
        if (method !in setOf(CookingMethod.FRITO, CookingMethod.EMPANIZADO_FRITO)) return false
        val food = tag.foodItem
        if (food != null && CookingStateResolver.isAlreadyPreparedForMethod(food, method)) return false
        if (!tag.oilApplied && food != null && !CookingStateResolver.shouldApplyOil(food, method)) return false
        val difference = estimatedOilGrams(tag, "abundante") - estimatedOilGrams(tag, "poco")
        return difference * 9.0 >= 50.0 || difference >= 5.0
    }

    private fun estimatedOilGrams(tag: ResolvedTag, level: String): Double = tag.foodItem?.let {
        oilGramsForLevelInCategory(level, oilAbsorptionCategory(it.name), tag.amountGrams ?: tag.loggedFood?.amount ?: 100.0)
    } ?: oilGramsForLevel(level)
}
