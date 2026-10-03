package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.food.findStaticFoodById
import com.example.kpkn.data.models.AmountIntent
import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.data.models.PORTION_MULTIPLIERS
import com.example.kpkn.data.models.PortionPreset
import com.example.kpkn.data.models.LoggedFood
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import com.example.kpkn.domain.nutrition.FoodInterpretationV2Engine.Companion.DEFAULT_DATASET_VERSION

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
    /** User's unit remains evidence; observedGrams always stores canonical eaten mass. */
    val declaredUnitId: String? = null,
    val nutritionReferenceNote: String? = null,
)

/** The alias keeps the contract discoverable from the domain package. */
typealias DailyGoalSnapshotV2 = com.example.kpkn.data.models.DailyGoalSnapshot

/**
 * Pure deterministic mapping of a resolved mention to its V2 result, used by Android now and by future parity clients. The logger
 * resolves the mentions and this engine only describes them (WP-N7 removed the mini-pipeline that parsed, asked and answered on its
 * own). It intentionally never lets context mutate per-100 g macros.
 */
class FoodInterpretationV2Engine(
    private val datasetVersion: String = DEFAULT_DATASET_VERSION,
) {
    companion object {
        const val DEFAULT_DATASET_VERSION = "local-v2"
    }

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
            "${conversionStem(food!!.name)} (${if (tag.foodState == FoodState.RAW) "crudo" else "cocido"}, estimado)"
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
}

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

/** State and cooking words that a catalog name uses to qualify its food: crudo, seco, cocido, hidratado, asado and frito, in their four forms. */
private val STATE_WORDS: Set<String> = listOf("crud", "sec", "cocid", "hidratad", "asad", "frit")
    .flatMap { stem -> listOf("o", "a", "os", "as").map { ending -> stem + ending } }.toSet()

/** Words that would be left hanging at the end of a name once its state word is gone ("Pollo al asado" would end in "al"). */
private val HANGING_WORDS = setOf("de", "del", "al", "a", "la", "lo", "el", "en", "con", "y", "e")

/** A parenthesis, kept whole, or any other run of characters that holds no blank and no parenthesis. */
private val NAME_TOKEN = Regex("""\([^)]*\)|[^\s(]+""")

private fun isStateWord(word: String): Boolean = TextKeys.normalize(word) in STATE_WORDS

/** A parenthesis without its state words, or null when nothing else is left in it: "(cruda, sin piel)" is "(sin piel)", "(cruda)" goes. */
private fun withoutStateWords(group: String): String? {
    val pieces = group.removePrefix("(").removeSuffix(")").split(',', ';', '/')
    if (pieces.none { piece -> piece.split(' ').any { it.isNotEmpty() && isStateWord(it) } }) return group
    val kept = pieces.map { piece -> piece.split(' ').filter { it.isNotEmpty() && !isStateWord(it) }.joinToString(" ") }
        .filter { it.isNotEmpty() }
    return if (kept.isEmpty()) null else "(" + kept.joinToString(", ") + ")"
}

/**
 * The name of a row without its raw/cooked qualifier: the stem of "Pechuga de Pollo (cocido, estimado)" (WP-N10b). A state or cooking
 * word is a qualifier only inside parentheses ("(crudo)", "(hidratado/cocido)") or at the end of the name ("Papas Fritas"). The FIRST
 * word is the dish and is never removed ("Asado de Tira (crudo)" is "Asado de Tira", not "de Tira"), and neither is a word in the
 * middle of the name. An annotation at the end ("(FDC 334849)", "(snack)") stays and does not hide the qualifier before it.
 */
internal fun conversionStem(name: String): String {
    val tokens = NAME_TOKEN.findAll(name).map { it.value }
        .mapNotNull { token -> if (token.startsWith("(")) withoutStateWords(token) else token }.toList()
    val annotationsFrom = tokens.indexOfLast { !it.startsWith("(") } + 1
    val words = tokens.take(annotationsFrom).toMutableList()
    var stripped = false
    while (words.size > 1 && isStateWord(words.last())) {
        words.removeAt(words.lastIndex)
        stripped = true
    }
    if (stripped) {
        while (words.size > 1 && TextKeys.normalize(words.last()) in HANGING_WORDS) words.removeAt(words.lastIndex)
    }
    return (words + tokens.drop(annotationsFrom)).joinToString(" ").ifBlank { name.trim() }
}
