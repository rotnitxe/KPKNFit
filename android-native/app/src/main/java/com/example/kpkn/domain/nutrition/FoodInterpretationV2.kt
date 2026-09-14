package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.food.findFoodByNormalized
import com.example.kpkn.data.food.findStaticFoodById
import com.example.kpkn.data.models.AmountIntent
import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.data.models.NutritionCalibrationProfile as StoredNutritionCalibrationProfile
import com.example.kpkn.data.models.ParsedMealItem
import com.example.kpkn.data.models.PORTION_MULTIPLIERS
import com.example.kpkn.data.models.PortionPreset
import com.example.kpkn.data.models.LoggedFood
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import com.example.kpkn.domain.nutrition.FoodInterpretationV2Engine.Companion.DEFAULT_DATASET_VERSION
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.round

/** Stable, source-level authority for nutrition rows used by the V2 contract. */
enum class FoodSource {
    USDA_FOUNDATION,
    USDA_FNDDS,
    USDA_SR_LEGACY,
    USDA_BRANDED,
    OPEN_FOOD_FACTS,
    CUSTOM_CONFIRMED,
    CURATED_LOCAL,
    MANUAL,
    DATASET_SEMANTIC,
    HEURISTIC,
}

enum class NutritionBasis {
    PER_100G_RAW,
    PER_100G_COOKED,
    PER_100G_AS_SOLD,
    PER_100G_PREPARED,
    PER_SERVING,
}

enum class WeightBasis { RAW, COOKED, AS_SERVED, UNKNOWN }

data class NutritionMacroRange(
    val calories: Double,
    val proteinGrams: Double,
    val carbsGrams: Double,
    val fatGrams: Double,
)

/** Evidence is deliberately persistible and contains no free-form meal text. */
data class FoodEvidence(
    val source: FoodSource,
    val sourceRecordId: String?,
    val datasetVersion: String,
    val nutritionBasis: NutritionBasis,
    val qualityFlags: List<String> = emptyList(),
    val sourceRelease: String? = null,
    val capturedAtEpochMs: Long = 0L,
)

enum class InterpretationStage {
    IDENTITY,
    STATE_AND_BASIS,
    PORTION,
    PREPARATION_AND_OIL,
    MACROS,
}

data class InterpretationStageEvidence(
    val stage: InterpretationStage,
    val status: String,
    val confidence: Double,
    val sourceRecordId: String? = null,
    val transformations: List<String> = emptyList(),
)

data class PortionOption(
    val label: String,
    val grams: Double,
    val minGrams: Double = grams,
    val maxGrams: Double = grams,
    val evidence: List<FoodEvidence> = emptyList(),
    val stageEvidence: List<InterpretationStageEvidence> = emptyList(),
)

sealed interface ClarificationRequest {
    val requestId: String
    val material: Boolean

    data class Portion(
        override val requestId: String,
        val options: List<PortionOption>,
        val allowManualGrams: Boolean = true,
        override val material: Boolean = true,
    ) : ClarificationRequest

    data class Identity(
        override val requestId: String,
        val candidateIds: List<String> = emptyList(),
        val candidateLabels: List<String> = emptyList(),
        override val material: Boolean = true,
    ) : ClarificationRequest

    data class WeightState(
        override val requestId: String,
        val options: List<WeightBasis> = listOf(WeightBasis.RAW, WeightBasis.COOKED),
        override val material: Boolean = true,
    ) : ClarificationRequest

    data class Oil(
        override val requestId: String,
        val options: List<String> = listOf("sin aceite", "poco", "medio", "abundante"),
        override val material: Boolean = true,
    ) : ClarificationRequest
}

sealed interface ClarificationAnswer {
    val requestId: String

    data class PortionSelected(
        override val requestId: String,
        val option: PortionOption,
    ) : ClarificationAnswer

    data class Grams(
        override val requestId: String,
        val grams: Double,
    ) : ClarificationAnswer

    data class IdentitySelected(
        override val requestId: String,
        val candidateId: String,
    ) : ClarificationAnswer

    data class WeightStateSelected(
        override val requestId: String,
        val basis: WeightBasis,
    ) : ClarificationAnswer

    data class OilSelected(
        override val requestId: String,
        val profile: String,
        val grams: Double? = null,
    ) : ClarificationAnswer

    /** Continue with the central value while retaining the min/max range. */
    data class Unsure(override val requestId: String) : ClarificationAnswer
}

data class InterpretationContext(
    val mealType: String? = null,
    val freeContext: String? = null,
    val timezone: String? = null,
    val nowEpochMs: Long = System.currentTimeMillis(),
)

data class InterpretationCorrection(
    val field: String,
    val value: String,
    val confirmed: Boolean = true,
)

/**
 * V2 result. Macros are authoritative only when [evidence] points to a source
 * row or the user explicitly confirms the estimate. [pendingQuestions] remains
 * the source of truth for Guardar blocking in the UI.
 */
data class FoodInterpretationV2(
    val draftId: String,
    val canonicalIdentity: String?,
    val canonicalFamily: String?,
    val selectedCandidateId: String?,
    val source: FoodSource,
    val sourceRecordId: String?,
    val datasetVersion: String,
    val sourceQuality: String,
    val nutritionBasis: NutritionBasis,
    val weightBasis: WeightBasis,
    val observedGrams: Double?,
    val baseAmountGrams: Double?,
    val portionMinGrams: Double?,
    val portionMaxGrams: Double?,
    val preparation: String?,
    val oilProfile: String?,
    val oilGrams: Double?,
    val oilMinGrams: Double?,
    val oilMaxGrams: Double?,
    val calories: Double,
    val caloriesMin: Double,
    val caloriesMax: Double,
    val proteinGrams: Double,
    val proteinMinGrams: Double,
    val proteinMaxGrams: Double,
    val carbsGrams: Double,
    val carbsMinGrams: Double,
    val carbsMaxGrams: Double,
    val fatGrams: Double,
    val fatMinGrams: Double,
    val fatMaxGrams: Double,
    val identityConfidence: Double,
    val portionConfidence: Double,
    val stateConfidence: Double,
    val oilConfidence: Double,
    val transformations: List<String> = emptyList(),
    val pendingQuestions: List<ClarificationRequest> = emptyList(),
    val evidence: List<FoodEvidence> = emptyList(),
    /** Immutable evidence per interpretation stage for audit and parity. */
    val stageEvidence: List<InterpretationStageEvidence> = emptyList(),
    val isConfirmedEstimate: Boolean = false,
    val isUncertain: Boolean = false,
    val queryFingerprint: String? = null,
    /** User's unit remains evidence; observedGrams always stores canonical eaten mass. */
    val declaredUnitId: String? = null,
    val nutritionReferenceNote: String? = null,
)

/** Aliases keep the contract discoverable from the domain package. */
typealias NutritionCalibrationProfile = StoredNutritionCalibrationProfile
typealias DailyGoalSnapshotV2 = com.example.kpkn.data.models.DailyGoalSnapshot

interface FoodInterpretationEngine {
    fun interpret(
        text: String,
        context: InterpretationContext = InterpretationContext(),
        calibration: NutritionCalibrationProfile? = null,
    ): FoodInterpretationV2

    fun answerClarification(
        draftId: String,
        requestId: String,
        answer: ClarificationAnswer,
    ): FoodInterpretationV2?

    fun finalize(draftId: String): FoodInterpretationV2?

    fun recordCorrection(
        resolution: FoodInterpretationV2,
        correction: InterpretationCorrection,
    ): FoodInterpretationV2
}

/**
 * Pure deterministic implementation used by Android now and by future parity
 * clients. Database-backed resolvers can provide [lookup] without changing the
 * contract. It intentionally never lets context mutate per-100 g macros.
 */
class FoodInterpretationV2Engine(
    private val lookup: (String) -> FoodItem? = ::findFoodByNormalized,
    private val datasetVersion: String = DEFAULT_DATASET_VERSION,
) : FoodInterpretationEngine {
    companion object {
        const val DEFAULT_DATASET_VERSION = "local-v2"
    }

    private data class Draft(
        val text: String,
        val context: InterpretationContext,
        val calibration: NutritionCalibrationProfile?,
        var result: FoodInterpretationV2,
        val parsed: ParsedMealItem?,
        val selectedFood: FoodItem?,
    )

    private val drafts = linkedMapOf<String, Draft>()

    /** The logger supplies a resolved mention. Never parse the meal a second time. */
    fun interpretResolved(
        tag: ResolvedTag,
        questions: List<ClarificationRequest> = emptyList(),
    ): FoodInterpretationV2 {
        val food = tag.foodItem
        val logged = tag.loggedFood
        val estimate = tag.nutritionEstimate
        val grams = tag.amountGrams ?: logged?.amount ?: tag.baseAmountGrams ?: 100.0
        val vague = tag.amountIntent == AmountIntent.UNSPECIFIED || tag.amountIntent == AmountIntent.INFERRED_CONTEXT
        val minGrams = (tag.portionMinGrams ?: if (vague) grams * 0.75 else grams).coerceAtMost(grams)
        val maxGrams = (tag.portionMaxGrams ?: if (vague) grams * 1.25 else grams).coerceAtLeast(grams)
        val source = when (tag.nutritionSource) {
            NutritionSourceKind.HEURISTIC_ESTIMATE -> FoodSource.HEURISTIC
            NutritionSourceKind.DATASET_ESTIMATE -> FoodSource.DATASET_SEMANTIC
            NutritionSourceKind.EXTERNAL_ESTIMATE -> FoodSource.HEURISTIC
            NutritionSourceKind.USER_PROVIDED -> FoodSource.MANUAL
            else -> food?.source.toFoodSource()
        }
        val basis = if (tag.nutrientsManuallyEdited) NutritionBasis.PER_SERVING else food?.nutritionBasis.toNutritionBasis(tag.foodState)
        val uncertain = tag.isUncertain || vague || minGrams != maxGrams || questions.isNotEmpty() ||
            source in setOf(FoodSource.HEURISTIC, FoodSource.DATASET_SEMANTIC)
        val sourceId = food?.sourceRecordId ?: food?.id
        val sourceState = food?.let(FoodIdentity::stateFor) ?: FoodState.UNKNOWN
        val convertsWeightBasis = food != null && ((sourceState == FoodState.RAW && tag.foodState == FoodState.COOKED) ||
            (sourceState == FoodState.COOKED && tag.foodState == FoodState.RAW)) &&
            (tag.stateConversion != null || tag.cookingMethod != null)
        val conversion = if (convertsWeightBasis) "weight_basis:${sourceState.name}->${tag.foodState.name};yield=${cookingWeightYield(food!!)};source=${food.id}" else null
        val calculatedName = if (convertsWeightBasis) {
            val stem = food!!.name.replace(Regex("\\b(?:crud[oa]s?|sec[oa]s?|cocid[oa]s?|hidratad[oa]s?|asad[oa]s?|frit[oa]s?)\\b", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\(\\s*\\)"), "").replace(Regex("\\s+"), " ").trim()
            "$stem (${if (tag.foodState == FoodState.RAW) "crudo" else "cocido"}, estimado)"
        } else food?.name ?: logged?.foodName
        fun minimum(value: Double?, explicit: Double?) = explicit ?: (value ?: 0.0) * minGrams / grams.coerceAtLeast(1.0)
        fun maximum(value: Double?, explicit: Double?) = explicit ?: (value ?: 0.0) * maxGrams / grams.coerceAtLeast(1.0)
        return FoodInterpretationV2(
            draftId = tag.id, canonicalIdentity = calculatedName,
            canonicalFamily = tag.canonicalFamily, selectedCandidateId = food?.id,
            source = source, sourceRecordId = sourceId,
            datasetVersion = food?.datasetVersion ?: datasetVersion,
            sourceQuality = when {
                tag.nutrientsManuallyEdited -> "manual_override"
                estimate?.isUnmatchedFallback == true -> "unmatched_estimate"
                estimate != null -> "composition_estimate"
                food == null -> "estimated"
                food.qualityFlags.isEmpty() -> "catalog"
                else -> "flagged"
            },
            nutritionBasis = basis,
            weightBasis = when (tag.foodState) {
                FoodState.RAW -> WeightBasis.RAW
                FoodState.COOKED, FoodState.HYDRATED -> WeightBasis.COOKED
                else -> WeightBasis.AS_SERVED
            },
            observedGrams = grams, baseAmountGrams = tag.baseAmountGrams ?: grams,
            portionMinGrams = minGrams, portionMaxGrams = maxGrams,
            preparation = tag.cookingMethod?.name, oilProfile = tag.oilLevel.takeIf { tag.oilApplied },
            oilGrams = tag.appliedOilGrams ?: if (tag.oilApplied) oilGramsForLevel(tag.oilLevel) else null,
            oilMinGrams = if (tag.needsOilClarification) 0.0 else null,
            oilMaxGrams = if (tag.needsOilClarification) oilGramsForLevel("abundante") else null,
            calories = logged?.calories ?: 0.0,
            caloriesMin = minimum(logged?.calories, logged?.caloriesMin),
            caloriesMax = maximum(logged?.calories, logged?.caloriesMax),
            proteinGrams = logged?.protein ?: 0.0,
            proteinMinGrams = minimum(logged?.protein, logged?.proteinMin),
            proteinMaxGrams = maximum(logged?.protein, logged?.proteinMax),
            carbsGrams = logged?.carbs ?: 0.0,
            carbsMinGrams = minimum(logged?.carbs, logged?.carbsMin),
            carbsMaxGrams = maximum(logged?.carbs, logged?.carbsMax),
            fatGrams = logged?.fats ?: 0.0,
            fatMinGrams = minimum(logged?.fats, logged?.fatsMin),
            fatMaxGrams = maximum(logged?.fats, logged?.fatsMax),
            identityConfidence = tag.resolutionConfidence ?: if (food == null) 0.0 else 1.0,
            portionConfidence = if (vague) 0.55 else 1.0,
            stateConfidence = if (tag.stateAssumed) 0.65 else 1.0,
            oilConfidence = if (tag.needsOilClarification) 0.35 else 1.0,
            pendingQuestions = questions,
            evidence = listOf(FoodEvidence(source, sourceId, food?.datasetVersion ?: datasetVersion, basis, food?.qualityFlags.orEmpty())) +
                estimate?.referenceFoodIds.orEmpty().mapNotNull(::findStaticFoodById).map { it.toEvidence(datasetVersion) },
            stageEvidence = listOf(
                InterpretationStageEvidence(InterpretationStage.IDENTITY, if (questions.any { it is ClarificationRequest.Identity }) "needs_review" else "resolved", tag.resolutionConfidence ?: 0.0, sourceId),
                InterpretationStageEvidence(InterpretationStage.PORTION, if (vague) "habitual_estimate" else "declared", if (vague) 0.55 else 1.0, sourceId),
                InterpretationStageEvidence(InterpretationStage.STATE_AND_BASIS, if (tag.stateAssumed) "assumed_as_eaten" else "resolved", if (tag.stateAssumed) 0.65 else 1.0, sourceId),
                InterpretationStageEvidence(InterpretationStage.MACROS, if (food == null) "estimated" else "calculated", if (food == null) 0.35 else 1.0, sourceId),
            ),
            transformations = listOfNotNull("portion:${tag.amountIntent.name}", tag.cookingMethod?.let { "preparation:${it.name}" }, conversion, "manual:nutrients".takeIf { tag.nutrientsManuallyEdited },
                estimate?.assumption, "composition:confirmed".takeIf { "composition" in tag.confirmedDimensions }),
            isConfirmedEstimate = false,
            isUncertain = uncertain || convertsWeightBasis,
            declaredUnitId = tag.unitId,
            nutritionReferenceNote = estimate?.let {
                it.assumption + if (it.isUnmatchedFallback || it.requiresCompositionClarification) " Rango orientativo; composición sin confirmar." else ""
            },
        )
    }

    override fun interpret(
        text: String,
        context: InterpretationContext,
        calibration: NutritionCalibrationProfile?,
    ): FoodInterpretationV2 {
        val parsedDescription = parseMealDescription(text)
        val item = parsedDescription.items.firstOrNull()
        val baseFood = item?.let { lookup(it.tag) }
        val food = when {
            item?.cookingMethod != null && item.cookingMethod != com.example.kpkn.data.models.CookingMethod.CRUDO ->
                CookingStateResolver.findPreparedVariant(item.tag, item.cookingMethod) ?: baseFood
            item?.cookingMethod == com.example.kpkn.data.models.CookingMethod.CRUDO &&
                baseFood != null && CookingStateResolver.isDbFoodCooked(baseFood) ->
                CookingStateResolver.findRawVariant(baseFood) ?: baseFood
            else -> baseFood
        }
        val draftId = UUID.randomUUID().toString()
        val result = buildResult(draftId, text, item, food, calibration)
        drafts[draftId] = Draft(text, context, calibration, result, item, food)
        while (drafts.size > 128) drafts.remove(drafts.keys.first())
        return result
    }

    override fun answerClarification(
        draftId: String,
        requestId: String,
        answer: ClarificationAnswer,
    ): FoodInterpretationV2? {
        val draft = drafts[draftId] ?: return null
        if (answer.requestId != requestId) return draft.result
        val current = draft.result
        val remaining = current.pendingQuestions.filterNot { it.requestId == requestId }
        val updated = when (answer) {
            is ClarificationAnswer.Unsure -> current.copy(
                pendingQuestions = remaining,
                isUncertain = true,
                isConfirmedEstimate = false,
            )
            is ClarificationAnswer.PortionSelected -> current.copy(
                observedGrams = answer.option.grams,
                baseAmountGrams = answer.option.grams,
                portionMinGrams = answer.option.minGrams,
                portionMaxGrams = answer.option.maxGrams,
                calories = scaleCentral(current.calories, current.observedGrams, answer.option.grams),
                caloriesMin = scaleCentral(current.calories, current.observedGrams, answer.option.minGrams),
                caloriesMax = scaleCentral(current.calories, current.observedGrams, answer.option.maxGrams),
                proteinGrams = scaleCentral(current.proteinGrams, current.observedGrams, answer.option.grams),
                proteinMinGrams = scaleCentral(current.proteinGrams, current.observedGrams, answer.option.minGrams),
                proteinMaxGrams = scaleCentral(current.proteinGrams, current.observedGrams, answer.option.maxGrams),
                carbsGrams = scaleCentral(current.carbsGrams, current.observedGrams, answer.option.grams),
                carbsMinGrams = scaleCentral(current.carbsGrams, current.observedGrams, answer.option.minGrams),
                carbsMaxGrams = scaleCentral(current.carbsGrams, current.observedGrams, answer.option.maxGrams),
                fatGrams = scaleCentral(current.fatGrams, current.observedGrams, answer.option.grams),
                fatMinGrams = scaleCentral(current.fatGrams, current.observedGrams, answer.option.minGrams),
                fatMaxGrams = scaleCentral(current.fatGrams, current.observedGrams, answer.option.maxGrams),
                portionConfidence = 1.0,
                isUncertain = remaining.any { it.material },
                transformations = current.transformations.toListIfAbsent("portion_absolute"),
                pendingQuestions = remaining,
            )
            is ClarificationAnswer.Grams -> {
                val grams = answer.grams.takeIf { it.isFinite() && it > 0.0 } ?: current.observedGrams
                current.copy(
                    observedGrams = grams,
                    baseAmountGrams = grams,
                    portionMinGrams = grams,
                    portionMaxGrams = grams,
                    calories = scaleCentral(current.calories, current.observedGrams, grams),
                    caloriesMin = scaleCentral(current.caloriesMin, current.observedGrams, grams),
                    caloriesMax = scaleCentral(current.caloriesMax, current.observedGrams, grams),
                    proteinGrams = scaleCentral(current.proteinGrams, current.observedGrams, grams),
                    proteinMinGrams = scaleCentral(current.proteinMinGrams, current.observedGrams, grams),
                    proteinMaxGrams = scaleCentral(current.proteinMaxGrams, current.observedGrams, grams),
                    carbsGrams = scaleCentral(current.carbsGrams, current.observedGrams, grams),
                    carbsMinGrams = scaleCentral(current.carbsMinGrams, current.observedGrams, grams),
                    carbsMaxGrams = scaleCentral(current.carbsMaxGrams, current.observedGrams, grams),
                    fatGrams = scaleCentral(current.fatGrams, current.observedGrams, grams),
                    fatMinGrams = scaleCentral(current.fatMinGrams, current.observedGrams, grams),
                    fatMaxGrams = scaleCentral(current.fatMaxGrams, current.observedGrams, grams),
                    portionConfidence = 1.0,
                    isUncertain = remaining.any { it.material },
                    pendingQuestions = remaining,
                )
            }
            is ClarificationAnswer.IdentitySelected -> current.copy(
                selectedCandidateId = answer.candidateId,
                identityConfidence = 1.0,
                pendingQuestions = remaining,
                isUncertain = remaining.any { it.material },
            )
            is ClarificationAnswer.WeightStateSelected -> current.copy(
                weightBasis = answer.basis,
                stateConfidence = 1.0,
                pendingQuestions = remaining,
                isUncertain = remaining.any { it.material },
                transformations = current.transformations.toListIfAbsent("weight_state_confirmed"),
            )
            is ClarificationAnswer.OilSelected -> {
                val grams = answer.grams ?: when (answer.profile.lowercase()) {
                    "sin aceite", "none", "zero" -> 0.0
                    "poco" -> 3.0
                    "abundante" -> 18.0
                    else -> 8.0
                }
                val delta = (grams - (current.oilGrams ?: 0.0)).coerceAtLeast(-current.fatGrams)
                current.copy(
                    oilProfile = answer.profile,
                    oilGrams = grams,
                    oilMinGrams = grams,
                    oilMaxGrams = grams,
                    fatGrams = (current.fatGrams + delta).coerceAtLeast(0.0),
                    fatMinGrams = (current.fatMinGrams + delta).coerceAtLeast(0.0),
                    fatMaxGrams = (current.fatMaxGrams + delta).coerceAtLeast(0.0),
                    calories = (current.calories + delta * 9.0).coerceAtLeast(0.0),
                    caloriesMin = (current.caloriesMin + delta * 9.0).coerceAtLeast(0.0),
                    caloriesMax = (current.caloriesMax + delta * 9.0).coerceAtLeast(0.0),
                    oilConfidence = 1.0,
                    pendingQuestions = remaining,
                    isUncertain = remaining.any { it.material },
                    transformations = current.transformations.toListIfAbsent("oil_explicit"),
                )
            }
        }
        draft.result = updated
        return updated
    }

    override fun finalize(draftId: String): FoodInterpretationV2? {
        val draft = drafts[draftId] ?: return null
        val unresolvedMaterial = draft.result.pendingQuestions.any { it.material }
        if (unresolvedMaterial) return null
        val finalized = draft.result.copy(
            isConfirmedEstimate = draft.result.source == FoodSource.MANUAL ||
                draft.result.source == FoodSource.HEURISTIC,
        )
        drafts.remove(draftId)
        return finalized
    }

    override fun recordCorrection(
        resolution: FoodInterpretationV2,
        correction: InterpretationCorrection,
    ): FoodInterpretationV2 {
        val updated = when (correction.field.lowercase()) {
            "identity", "candidate" -> resolution.copy(
                selectedCandidateId = correction.value,
                identityConfidence = if (correction.confirmed) 1.0 else resolution.identityConfidence,
            )
            "portion", "grams" -> correction.value.toDoubleOrNull()?.let { grams ->
                resolution.copy(
                    observedGrams = grams,
                    baseAmountGrams = grams,
                    portionMinGrams = grams,
                    portionMaxGrams = grams,
                    portionConfidence = if (correction.confirmed) 1.0 else resolution.portionConfidence,
                )
            } ?: resolution
            "state", "weightbasis" -> runCatching { WeightBasis.valueOf(correction.value.uppercase()) }
                .getOrNull()?.let { basis -> resolution.copy(weightBasis = basis, stateConfidence = 1.0) }
                ?: resolution
            "oil" -> resolution.copy(oilProfile = correction.value, oilConfidence = if (correction.confirmed) 1.0 else resolution.oilConfidence)
            else -> resolution
        }
        drafts[resolution.draftId]?.result = updated
        return updated
    }

    private fun buildResult(
        draftId: String,
        text: String,
        item: ParsedMealItem?,
        food: FoodItem?,
        calibration: NutritionCalibrationProfile?,
    ): FoodInterpretationV2 {
        val method = item?.cookingMethod
        val state = when {
            method != null -> CookingStateResolver.stateForMethod(method) ?: FoodState.UNKNOWN
            item != null && FoodIdentity.isStateSensitive(item.tag) && FoodIdentity.stateFor(item.tag) == FoodState.UNKNOWN ->
                FoodState.UNKNOWN
            food != null -> FoodIdentity.stateFor(food)
            else -> FoodState.UNKNOWN
        }
        val basis = food?.nutritionBasis.toNutritionBasis(state)
        val weightBasis = when (state) {
            FoodState.RAW -> WeightBasis.RAW
            FoodState.COOKED, FoodState.HYDRATED -> WeightBasis.COOKED
            FoodState.UNKNOWN -> WeightBasis.UNKNOWN
        }
        val explicitGrams = item?.amountGrams?.takeIf { it.isFinite() && it > 0.0 }
        val defaultGrams = food?.portionGrams?.takeIf { it.isFinite() && it > 0.0 }
            ?: food?.servingSize?.takeIf { it.isFinite() && it > 0.0 }
            ?: 100.0
        val familyKey = food?.let(FoodIdentity::familyFor)?.lowercase()
        val calibratedGrams = listOfNotNull(
            food?.id?.lowercase(),
            food?.name?.let(FoodIdentity::normalize),
            familyKey,
            item?.tag?.let(FoodIdentity::normalize),
        ).firstNotNullOfOrNull { key ->
            calibration?.maturePortionsGrams?.get(key)
                ?.takeIf { it.isFinite() && it > 0.0 }
        }
        val observed = explicitGrams ?: calibratedGrams ?: defaultGrams
        val portionIsVague = item == null ||
            item.amountIntent == AmountIntent.UNSPECIFIED ||
            item.amountIntent == AmountIntent.INFERRED_CONTEXT
        val portionOptions = if (food != null && calibratedGrams == null) portionOptions(food, observed, calibration) else emptyList()
        val pending = buildList {
            if (food == null) add(
                ClarificationRequest.Identity(
                    requestId = "identity",
                    candidateIds = emptyList(),
                    candidateLabels = emptyList(),
                ),
            )
            if (portionIsVague && calibratedGrams == null && portionOptions.isNotEmpty()) add(
                ClarificationRequest.Portion("portion", portionOptions),
            )
            if (food != null && FoodIdentity.isStateSensitive(item?.tag ?: food.name) && state == FoodState.UNKNOWN) {
                add(ClarificationRequest.WeightState("weight_state"))
            }
            if (method == com.example.kpkn.data.models.CookingMethod.FRITO ||
                method == com.example.kpkn.data.models.CookingMethod.EMPANIZADO_FRITO
            ) {
                val lower = text.lowercase()
                if (!lower.contains("sin aceite") && !lower.contains("aceite ")) {
                    add(ClarificationRequest.Oil("oil"))
                }
            }
        }
        val macros = if (food != null) {
            val grams = observed
            NutritionMacroRange(
                calories = food.calories * grams / NutrientBasis.grams(food),
                proteinGrams = food.protein * grams / NutrientBasis.grams(food),
                carbsGrams = food.carbs * grams / NutrientBasis.grams(food),
                fatGrams = food.fats * grams / NutrientBasis.grams(food),
            )
        } else NutritionMacroRange(0.0, 0.0, 0.0, 0.0)
        val rangeMin = if (portionIsVague) portionOptions.minOfOrNull { it.minGrams } ?: observed else observed
        val rangeMax = if (portionIsVague) portionOptions.maxOfOrNull { it.maxGrams } ?: observed else observed
        val evidence = food?.let { listOf(it.toEvidence(datasetVersion)) }.orEmpty()
        return FoodInterpretationV2(
            draftId = draftId,
            canonicalIdentity = food?.name,
            canonicalFamily = food?.let(FoodIdentity::familyFor),
            selectedCandidateId = food?.id,
            source = food?.source.toFoodSource(),
            sourceRecordId = food?.sourceRecordId ?: food?.id,
            datasetVersion = food?.datasetVersion ?: datasetVersion,
            sourceQuality = if (food == null) "unknown" else if (food.qualityFlags.isEmpty()) "verified" else "flagged",
            nutritionBasis = basis,
            weightBasis = weightBasis,
            observedGrams = observed,
            baseAmountGrams = observed,
            portionMinGrams = rangeMin,
            portionMaxGrams = rangeMax,
            preparation = method?.name,
            oilProfile = null,
            oilGrams = null,
            oilMinGrams = null,
            oilMaxGrams = null,
            calories = macros.calories,
            caloriesMin = macros.calories * rangeMin / observed.coerceAtLeast(1.0),
            caloriesMax = macros.calories * rangeMax / observed.coerceAtLeast(1.0),
            proteinGrams = macros.proteinGrams,
            proteinMinGrams = macros.proteinGrams * rangeMin / observed.coerceAtLeast(1.0),
            proteinMaxGrams = macros.proteinGrams * rangeMax / observed.coerceAtLeast(1.0),
            carbsGrams = macros.carbsGrams,
            carbsMinGrams = macros.carbsGrams * rangeMin / observed.coerceAtLeast(1.0),
            carbsMaxGrams = macros.carbsGrams * rangeMax / observed.coerceAtLeast(1.0),
            fatGrams = macros.fatGrams,
            fatMinGrams = macros.fatGrams * rangeMin / observed.coerceAtLeast(1.0),
            fatMaxGrams = macros.fatGrams * rangeMax / observed.coerceAtLeast(1.0),
            identityConfidence = if (food == null) 0.0 else 1.0,
            portionConfidence = when {
                explicitGrams != null -> 1.0
                calibratedGrams != null -> 0.95
                portionIsVague -> 0.55
                else -> 1.0
            },
            stateConfidence = if (state == FoodState.UNKNOWN) 0.0 else 1.0,
            oilConfidence = if (pending.any { it is ClarificationRequest.Oil }) 0.0 else 1.0,
            transformations = buildList {
                if (portionIsVague) add("portion_range")
                if (calibratedGrams != null) add("calibrated_habit:${calibratedGrams.toInt()}g")
                if (method != null) add("preparation:${method.name}")
            },
            pendingQuestions = pending,
            evidence = evidence,
            stageEvidence = listOf(
                InterpretationStageEvidence(
                    stage = InterpretationStage.IDENTITY,
                    status = if (food == null) "needs_review" else "resolved",
                    confidence = if (food == null) 0.0 else 1.0,
                    sourceRecordId = food?.sourceRecordId ?: food?.id,
                ),
                InterpretationStageEvidence(
                    stage = InterpretationStage.STATE_AND_BASIS,
                    status = if (state == FoodState.UNKNOWN) "needs_clarification" else "resolved",
                    confidence = if (state == FoodState.UNKNOWN) 0.0 else 1.0,
                    sourceRecordId = food?.sourceRecordId ?: food?.id,
                ),
                InterpretationStageEvidence(
                    stage = InterpretationStage.PORTION,
                    status = if (portionIsVague && calibratedGrams == null) "needs_clarification" else "resolved",
                    confidence = when {
                        explicitGrams != null -> 1.0
                        calibratedGrams != null -> 0.95
                        portionIsVague -> 0.55
                        else -> 1.0
                    },
                    sourceRecordId = food?.sourceRecordId ?: food?.id,
                ),
                InterpretationStageEvidence(
                    stage = InterpretationStage.PREPARATION_AND_OIL,
                    status = if (pending.any { it is ClarificationRequest.Oil }) "needs_clarification" else "resolved",
                    confidence = if (pending.any { it is ClarificationRequest.Oil }) 0.0 else 1.0,
                    sourceRecordId = food?.sourceRecordId ?: food?.id,
                ),
                InterpretationStageEvidence(
                    stage = InterpretationStage.MACROS,
                    status = if (food == null) "estimated" else "calculated",
                    confidence = if (food == null) 0.35 else 1.0,
                    sourceRecordId = food?.sourceRecordId ?: food?.id,
                ),
            ),
            isConfirmedEstimate = false,
            isUncertain = pending.isNotEmpty(),
            queryFingerprint = fingerprint(text),
        )
    }

    private fun portionOptions(
        food: FoodItem,
        center: Double,
        calibration: NutritionCalibrationProfile?,
    ): List<PortionOption> {
        val base = center.takeIf { it > 0.0 } ?: food.servingSize.coerceAtLeast(1.0)
        val evidence = listOf(food.toEvidence(datasetVersion))
        return listOf(
            PortionOption("Pequeña", base * 0.75, base * 0.65, base * 0.85, evidence),
            PortionOption("Habitual", base, base * 0.9, base * 1.1, evidence),
            PortionOption("Grande", base * 1.25, base * 1.1, base * 1.4, evidence),
        ).map { it.copy(grams = round(it.grams), minGrams = round(it.minGrams), maxGrams = round(it.maxGrams)) }
    }

    private fun scaleCentral(value: Double, oldGrams: Double?, newGrams: Double?): Double {
        if (oldGrams == null || newGrams == null || oldGrams <= 0.0 || !newGrams.isFinite()) return value
        return value * newGrams / oldGrams
    }
}

private fun List<String>.toListIfAbsent(value: String): List<String> =
    if (contains(value)) this else this + value

private fun String?.toNutritionBasis(state: FoodState): NutritionBasis {
    val value = this.orEmpty().uppercase()
    return when {
        value.contains("SERVING") -> NutritionBasis.PER_SERVING
        value.contains("RAW") || state == FoodState.RAW -> NutritionBasis.PER_100G_RAW
        value.contains("COOKED") || state == FoodState.COOKED || state == FoodState.HYDRATED -> NutritionBasis.PER_100G_COOKED
        value.contains("PREPARED") -> NutritionBasis.PER_100G_PREPARED
        else -> NutritionBasis.PER_100G_AS_SOLD
    }
}

private fun String?.toFoodSource(): FoodSource = when {
    this.orEmpty().contains("OFF", ignoreCase = true) -> FoodSource.OPEN_FOOD_FACTS
    this.orEmpty().contains("BRANDED", ignoreCase = true) -> FoodSource.USDA_BRANDED
    this.orEmpty().contains("FNDDS", ignoreCase = true) -> FoodSource.USDA_FNDDS
    this.orEmpty().contains("SR", ignoreCase = true) -> FoodSource.USDA_SR_LEGACY
    this.orEmpty().contains("USDA", ignoreCase = true) -> FoodSource.USDA_FOUNDATION
    this.orEmpty().contains("CUSTOM", ignoreCase = true) -> FoodSource.CUSTOM_CONFIRMED
    // Static Android rows predate provenance columns; they are curated local
    // data, not an AI estimate merely because the optional source is blank.
    this.orEmpty().isBlank() -> FoodSource.CURATED_LOCAL
    else -> FoodSource.CURATED_LOCAL
}

private fun FoodItem.toEvidence(datasetVersion: String): FoodEvidence = FoodEvidence(
    source = source.toFoodSource(),
    sourceRecordId = sourceRecordId ?: id,
    datasetVersion = this.datasetVersion ?: datasetVersion,
    nutritionBasis = nutritionBasis.toNutritionBasis(FoodIdentity.stateFor(this)),
    qualityFlags = qualityFlags,
)

private fun fingerprint(text: String): String = MessageDigest.getInstance("SHA-256")
    .digest(FoodIdentity.normalize(text).toByteArray())
    .joinToString("") { "%02x".format(it) }

/** User-confirmed dimensions only; a draft or accepted estimate cannot create a habit. */
data class FoodLearningConfirmation(
    val query: String,
    val foodId: String,
    val family: String,
    val dimensions: Set<String>,
    val portionGrams: Double? = null,
    val cookingMethod: String? = null,
    val weightBasis: WeightBasis = WeightBasis.UNKNOWN,
    val oilGramsPer100: Double? = null,
)

fun FoodInterpretationV2.canFinalize(): Boolean = pendingQuestions.none { it.material } &&
    observedGrams?.let { it.isFinite() && it > 0.0 } == true &&
    listOf(calories, proteinGrams, carbsGrams, fatGrams).all { it.isFinite() && it >= 0.0 } &&
    calories <= 10000.0 && proteinGrams <= 1000.0 && carbsGrams <= 1000.0 && fatGrams <= 1000.0

/** Persist the same result that the card displays, including its source and interval. */
fun FoodInterpretationV2.toLoggedFood(reference: LoggedFood): LoggedFood = reference.copy(
    foodName = canonicalIdentity ?: reference.foodName,
    amount = observedGrams ?: reference.amount,
    unit = "g",
    calories = calories, protein = proteinGrams, carbs = carbsGrams, fats = fatGrams,
    caloriesMin = caloriesMin, caloriesMax = caloriesMax,
    proteinMin = proteinMinGrams, proteinMax = proteinMaxGrams,
    carbsMin = carbsMinGrams, carbsMax = carbsMaxGrams,
    fatsMin = fatMinGrams, fatsMax = fatMaxGrams,
    interpretationId = draftId, isUncertain = isUncertain,
    nutritionReferenceNote = nutritionReferenceNote,
    evidenceJson = buildJsonObject {
        put("source", source.name)
        put("sourceRecordId", sourceRecordId)
        put("selectedCandidateId", selectedCandidateId)
        put("datasetVersion", datasetVersion)
        put("sourceQuality", sourceQuality)
        put("nutritionBasis", nutritionBasis.name)
        put("weightBasis", weightBasis.name)
        put("identityConfidence", identityConfidence)
        put("portionConfidence", portionConfidence)
        put("stateConfidence", stateConfidence)
        put("oilConfidence", oilConfidence)
        put("declaredUnitId", declaredUnitId)
        put("transformations", transformations.joinToString("|"))
        put("nutritionReferenceNote", nutritionReferenceNote)
        put("references", buildJsonArray {
            evidence.forEach { item -> add(buildJsonObject {
                put("source", item.source.name)
                put("sourceRecordId", item.sourceRecordId)
                put("datasetVersion", item.datasetVersion)
                put("nutritionBasis", item.nutritionBasis.name)
            }) }
        })
    }.toString(),
)

fun ResolvedTag.confirmedLearning(): FoodLearningConfirmation? {
    // Unsure explicitly clears dimensions. Estimated portions alone must not suppress
    // an identity the user actually selected.
    if (confirmedDimensions.isEmpty() || isExcluded || (explicitDecision && isUncertain && confirmedDimensions.isEmpty())) return null
    val food = foodItem ?: return null
    return FoodLearningConfirmation(
        query = foodQuery.ifBlank { tag }, foodId = food.id,
        family = food.id, dimensions = confirmedDimensions,
        portionGrams = amountGrams.takeIf { "portion" in confirmedDimensions },
        cookingMethod = cookingMethod?.name?.takeIf { "state" in confirmedDimensions },
        weightBasis = interpretationV2?.weightBasis ?: WeightBasis.UNKNOWN,
        oilGramsPer100 = if ("oil" in confirmedDimensions) (appliedOilGrams ?: oilGramsForLevel(oilLevel)) * 100.0 / (amountGrams ?: 100.0).coerceAtLeast(1.0) else null,
    )
}

fun rescaleEstimatedFood(base: LoggedFood, grams: Double): LoggedFood {
    val factor = grams / base.amount.coerceAtLeast(1.0)
    return base.copy(
        amount = grams, calories = base.calories * factor, protein = base.protein * factor,
        carbs = base.carbs * factor, fats = base.fats * factor,
        fiber = base.fiber * factor, sugar = base.sugar * factor, sodiumMg = base.sodiumMg * factor,
        caloriesMin = null, caloriesMax = null, proteinMin = null, proteinMax = null,
        carbsMin = null, carbsMax = null, fatsMin = null, fatsMax = null,
    )
}

/** A manual nutrient correction becomes the density for all later size changes. */
fun ResolvedTag.rebaseManualNutrients(): ResolvedTag {
    val edited = loggedFood ?: return this
    val anchorGrams = baseAmountGrams ?: edited.amount
    return copy(
        baseLoggedFood = rescaleEstimatedFood(edited, anchorGrams),
        nutrientsManuallyEdited = true,
        nutritionSource = NutritionSourceKind.USER_PROVIDED,
    )
}
