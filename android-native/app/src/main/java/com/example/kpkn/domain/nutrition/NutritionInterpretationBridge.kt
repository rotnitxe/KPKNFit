package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.food.findStaticFoodById
import com.example.kpkn.data.models.AmountIntent
import com.example.kpkn.data.models.CookingMethod
import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.data.models.ParsedMealDescription

/**
 * Unique clarification authority for the food logger.
 *
 * [TagResolver] produces identity + eaten grams. This bridge is the only place
 * that may attach 0–1 material question (Δkcal ≥ 15%): cut, dry/cooked, oil.
 */
object NutritionInterpretationBridge {

    private val engine = FoodInterpretationV2Engine()

    fun enrich(tag: ResolvedTag, rawDescription: String): ResolvedTag =
        enrich(tag, ParsedMealDescription(rawDescription = rawDescription, verbatimDescription = rawDescription))

    fun enrich(tag: ResolvedTag, parsed: ParsedMealDescription): ResolvedTag {
        if (tag.explicitDecision || tag.isExcluded) return tag.clearQuestionsIfResolved()

        val item = parsed.items.firstOrNull { FoodIdentity.normalize(it.tag) == FoodIdentity.normalize(tag.tag) }
            ?: parsed.items.singleOrNull()
        val verbatim = parsed.verbatimDescription.ifBlank { parsed.rawDescription }
        val learnedId = tag.learnedFoodId
        val cut = FoodStapleOntology.cutClarification(tag.tag, verbatim, learnedId)
            .takeIf { parsed.items.size == 1 && (item?.amountIntent ?: tag.amountIntent) == AmountIntent.UNSPECIFIED }

        val weightKind = cookingQuestionKind(tag, item)
        val oilMaterial = shouldAskOil(tag)
        val v2Query = v2QueryFor(tag, item)
        val v2 = engine.interpret(v2Query)
        val portionMaterial = v2.pendingQuestions.any { it is ClarificationRequest.Portion } &&
            isMaterialDelta(v2) &&
            !HouseholdPortions.hasClassDefault(tag.foodItem, tag.tag) &&
            tag.amountIntent == AmountIntent.UNSPECIFIED

        val chosen = when {
            cut != null -> Question.CUT
            weightKind != CookingStateResolver.ClarificationKind.NONE -> Question.WEIGHT
            oilMaterial -> Question.OIL
            portionMaterial -> Question.PORTION
            else -> null
        }

        return when (chosen) {
            Question.CUT -> tag.copy(
                needsCutClarification = true,
                stapleCutOptions = cut!!.options,
                needsCookingClarification = false,
                clarificationKind = CookingStateResolver.ClarificationKind.NONE,
                needsOilClarification = false,
                isResolved = false,
                resolutionStatus = FoodResolutionStatus.NEEDS_CONFIRMATION,
            )
            Question.WEIGHT -> tag.copy(
                needsCutClarification = false,
                stapleCutOptions = emptyList(),
                needsCookingClarification = true,
                clarificationKind = weightKind,
                needsOilClarification = false,
                stateAssumed = false,
                isResolved = false,
                resolutionStatus = FoodResolutionStatus.NEEDS_CONFIRMATION,
            )
            Question.OIL -> tag.copy(
                needsCutClarification = false,
                stapleCutOptions = emptyList(),
                needsCookingClarification = false,
                needsOilClarification = true,
                isResolved = false,
                resolutionStatus = FoodResolutionStatus.NEEDS_CONFIRMATION,
            )
            Question.PORTION -> {
                val options = absolutePortionOptions(tag.baseAmountGrams ?: tag.amountGrams)
                if (options.isEmpty()) return tag.withoutQuestions()
                tag.copy(
                    needsCutClarification = false,
                    stapleCutOptions = emptyList(),
                    needsCookingClarification = false,
                    needsOilClarification = false,
                    portionMinGrams = options.first().second,
                    portionMaxGrams = options.last().second,
                    isResolved = false,
                    resolutionStatus = FoodResolutionStatus.NEEDS_CONFIRMATION,
                )
            }
            null -> tag.withoutQuestions()
        }
    }

    fun applyCutOption(tag: ResolvedTag, foodId: String): ResolvedTag {
        val food = findStaticFoodById(foodId) ?: return tag
        val grams = FoodStapleOntology.householdDefaultGrams(tag.tag, food)
            ?: tag.amountGrams
            ?: HouseholdPortions.defaultGrams(food, tag.tag)
        val logged = scaleFoodByPortion(
            food = food,
            quantity = tag.quantity,
            portion = tag.portion,
            amountGrams = grams,
            cookingMethod = tag.cookingMethod,
        )
        return tag.copy(
            foodItem = food,
            amountGrams = grams,
            baseAmountGrams = grams,
            portionMinGrams = grams,
            portionMaxGrams = grams,
            loggedFood = logged,
            needsCutClarification = false,
            stapleCutOptions = emptyList(),
            needsCookingClarification = false,
            needsOilClarification = false,
            isResolved = true,
            resolutionStatus = FoodResolutionStatus.AUTO,
            explicitDecision = true,
            statusText = "Corte confirmado.",
        )
    }

    fun materialKcalDelta(foodA: FoodItem, foodB: FoodItem, grams: Double): Double {
        val kcalA = foodA.calories * grams / foodA.servingSize.coerceAtLeast(1.0)
        val kcalB = foodB.calories * grams / foodB.servingSize.coerceAtLeast(1.0)
        val minK = minOf(kcalA, kcalB).coerceAtLeast(1.0)
        return kotlin.math.abs(kcalA - kcalB) / minK
    }

    private enum class Question { CUT, WEIGHT, OIL, PORTION }

    private fun cookingQuestionKind(
        tag: ResolvedTag,
        item: com.example.kpkn.data.models.ParsedMealItem?,
    ): CookingStateResolver.ClarificationKind {
        val intent = item?.amountIntent ?: tag.amountIntent
        if (intent != AmountIntent.EXPLICIT_MASS) return CookingStateResolver.ClarificationKind.NONE
        if (tag.cookingMethod != null) return CookingStateResolver.ClarificationKind.NONE
        val blob = FoodIdentity.normalize(tag.tag)
        if (FoodIdentity.stateFor(blob) != FoodState.UNKNOWN) return CookingStateResolver.ClarificationKind.NONE
        if (!FoodIdentity.isStateSensitive(blob)) return CookingStateResolver.ClarificationKind.NONE
        val kind = CookingStateResolver.clarificationKind(tag.tag, tag.foodItem, cookingMethod = null)
        if (kind == CookingStateResolver.ClarificationKind.NONE) return CookingStateResolver.ClarificationKind.NONE
        val cooked = tag.foodItem?.let { cookedKcal(it, tag) } ?: return CookingStateResolver.ClarificationKind.NONE
        val rawFood = CookingStateResolver.findDryOrCookedVariant(tag.tag, wantCooked = false)
            ?: tag.foodItem?.let { CookingStateResolver.findRawVariant(it) }
            ?: return CookingStateResolver.ClarificationKind.NONE
        val raw = cookedKcal(rawFood, tag)
        val minK = minOf(cooked, raw).coerceAtLeast(1.0)
        if (kotlin.math.abs(cooked - raw) / minK < FoodStapleOntology.MATERIAL_KCAL_DELTA) {
            return CookingStateResolver.ClarificationKind.NONE
        }
        return kind
    }

    private fun shouldAskOil(tag: ResolvedTag): Boolean {
        val method = tag.cookingMethod ?: return false
        if (method != CookingMethod.FRITO && method != CookingMethod.EMPANIZADO_FRITO) return false
        val food = tag.foodItem
        if (food != null && CookingStateResolver.isAlreadyPreparedForMethod(food, method)) return false
        if (!tag.oilApplied && food != null && !CookingStateResolver.shouldApplyOil(food, method)) return false
        val grams = tag.amountGrams ?: tag.loggedFood?.amount ?: return false
        val baseKcal = tag.loggedFood?.calories?.takeIf { it > 0 }
            ?: food?.let { it.calories * grams / it.servingSize.coerceAtLeast(1.0) }
            ?: return false
        val poco = oilGramsForLevel("poco") * 9.0
        val alto = oilGramsForLevel("abundante") * 9.0
        val minK = (baseKcal + poco).coerceAtLeast(1.0)
        return (alto - poco) / minK >= FoodStapleOntology.MATERIAL_KCAL_DELTA
    }

    private fun cookedKcal(food: FoodItem, tag: ResolvedTag): Double {
        val grams = tag.amountGrams ?: 100.0
        return food.calories * grams / food.servingSize.coerceAtLeast(1.0)
    }

    private fun v2QueryFor(
        tag: ResolvedTag,
        item: com.example.kpkn.data.models.ParsedMealItem?,
    ): String {
        val grams = item?.amountGrams ?: tag.amountGrams
        return if (grams != null && (item?.amountIntent == AmountIntent.EXPLICIT_MASS || tag.amountIntent == AmountIntent.EXPLICIT_MASS)) {
            "${grams.toInt()} g ${tag.tag}"
        } else {
            tag.tag
        }
    }

    private fun isMaterialDelta(v2: FoodInterpretationV2): Boolean {
        if (v2.caloriesMax <= v2.caloriesMin) return false
        val minK = v2.caloriesMin.coerceAtLeast(1.0)
        return (v2.caloriesMax - v2.caloriesMin) / minK >= FoodStapleOntology.MATERIAL_KCAL_DELTA
    }

    private fun ResolvedTag.withoutQuestions(): ResolvedTag = copy(
        needsCutClarification = false,
        stapleCutOptions = emptyList(),
        needsCookingClarification = false,
        clarificationKind = CookingStateResolver.ClarificationKind.NONE,
        needsOilClarification = false,
        isResolved = if (explicitDecision) isResolved else (resolutionStatus == FoodResolutionStatus.AUTO || loggedFood != null),
        resolutionStatus = if (resolutionStatus == FoodResolutionStatus.NEEDS_CONFIRMATION && loggedFood != null) {
            FoodResolutionStatus.AUTO
        } else resolutionStatus,
    )

    private fun ResolvedTag.clearQuestionsIfResolved(): ResolvedTag =
        if (isResolved && resolutionStatus == FoodResolutionStatus.AUTO) withoutQuestions() else this
}
