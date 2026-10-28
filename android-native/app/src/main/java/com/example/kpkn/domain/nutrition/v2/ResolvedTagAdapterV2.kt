package com.example.kpkn.domain.nutrition.v2

import com.example.kpkn.data.models.AmountIntent
import com.example.kpkn.data.models.AnalysisSource
import com.example.kpkn.data.models.CarbBreakdown
import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.data.models.Micronutrient
import com.example.kpkn.domain.nutrition.FoodResolutionStatus
import com.example.kpkn.domain.nutrition.FoodState
import com.example.kpkn.domain.nutrition.NutritionSourceKind
import com.example.kpkn.domain.nutrition.ResolvedTag
import java.util.UUID

/**
 * Convierte la salida del parser v2 ([MealParseV2]) en los [ResolvedTag] que ya consume el registrador de comidas.
 *
 * Una etiqueta por [MentionV2], en el mismo orden. La ficha del catálogo ya corresponde al estado pedido (frito, cocido…):
 * no se aplica ninguna transformación de cocción ni aceite extra. Los gramos y las claves de la ficha no se redondean aquí.
 */
object ResolvedTagAdapterV2 {
    private const val MAX_REVIEW_CANDIDATES = 4
    private val CATALOG_PREFIXES = listOf("usda_", "ciqual_", "off_")

    fun toResolvedTags(parse: MealParseV2, catalog: ConceptCatalogV2): List<ResolvedTag> =
        parse.mentions.map { toResolvedTag(it, catalog) }

    private fun toResolvedTag(mention: MentionV2, catalog: ConceptCatalogV2): ResolvedTag {
        val recordId = mention.recordId
        val grams = mention.grams
        val per100g = mention.per100g.ifEmpty { catalog.record(recordId)?.per100g.orEmpty() }
        if (recordId == null || grams == null || per100g.isEmpty()) {
            return ResolvedTag(
                tag = mention.text,
                isResolved = false,
                isExcluded = mention.excluded,
                statusText = "Sin coincidencia",
            )
        }

        val concept = mention.conceptId?.let(catalog::concept)
        val name = mention.conceptName ?: catalog.record(recordId)?.name ?: mention.text
        val foodState = foodStateOf(mention.state)
        val food = buildFood(recordId, name, concept?.category, per100g, foodState, catalog.generation)
        val logged = buildLoggedFood(food, grams, recordId, per100g, catalog.generation)

        val dimensions = mention.questions.map { it.dimension }.distinct()
        val hasStateQuestion = QuestionDimensionV2.STATE in dimensions
        val status = when {
            dimensions.isEmpty() -> FoodResolutionStatus.AUTO
            hasStateQuestion -> FoodResolutionStatus.NEEDS_STATE
            else -> FoodResolutionStatus.NEEDS_CONFIRMATION
        }
        val statusText = when {
            mention.excluded -> "Excluido"
            dimensions.isEmpty() -> "Interpretado"
            else -> "Revisar: " + dimensions.joinToString(", ") { dimensionName(it) }
        }

        return ResolvedTag(
            tag = mention.text,
            amountGrams = grams,
            baseAmountGrams = grams,
            foodItem = food,
            loggedFood = logged,
            baseLoggedFood = logged,
            isResolved = true,
            isFuzzyMatch = mention.draft.fuzzy,
            analysisSource = AnalysisSource.DATABASE,
            statusText = statusText,
            isExcluded = mention.excluded,
            amountIntent = amountIntentOf(mention.quantityKind),
            needsCookingClarification = hasStateQuestion,
            oilApplied = true,
            reviewCandidates = reviewCandidates(mention, catalog),
            foodState = foodState,
            resolutionStatus = status,
            nutritionSource = sourceOf(recordId),
            resolutionConfidence = mention.confidence,
        )
    }

    private fun reviewCandidates(mention: MentionV2, catalog: ConceptCatalogV2): List<FoodItem> {
        val question = mention.questions.firstOrNull { it.dimension == QuestionDimensionV2.IDENTITY } ?: return emptyList()
        return question.options
            .filter { it != mention.conceptId }
            .mapNotNull { conceptId ->
                val concept = catalog.concept(conceptId) ?: return@mapNotNull null
                val variant = concept.variants.firstOrNull { it.recordId != null && it.state == concept.defaultState && it.form == concept.defaultForm }
                    ?: concept.variants.firstOrNull { it.recordId != null }
                    ?: return@mapNotNull null
                val record = catalog.record(variant.recordId) ?: return@mapNotNull null
                buildFood(record.id, concept.name, concept.category, record.per100g, foodStateOf(variant.state), catalog.generation)
            }
            .take(MAX_REVIEW_CANDIDATES)
    }

    /** FoodItem por 100 g; clave ausente = sin dato (sin desglose de carbohidratos ni micronutriente inventado). */
    private fun buildFood(
        recordId: String,
        name: String,
        category: String?,
        per100g: Map<String, Double>,
        foodState: FoodState,
        generation: String,
    ): FoodItem {
        val fiber = per100g["fiber"]
        val sugars = per100g["sugars"]
        val micronutrients = listOfNotNull(
            per100g["sodium"]?.let { Micronutrient("Sodio", it, "mg") },
            per100g["potassium"]?.let { Micronutrient("Potasio", it, "mg") },
        )
        return FoodItem(
            id = recordId,
            name = name,
            category = category,
            servingSize = 100.0,
            servingUnit = "g",
            unit = "g",
            calories = per100g["energy"] ?: 0.0,
            protein = per100g["protein"] ?: 0.0,
            carbs = per100g["carbs"] ?: 0.0,
            fats = per100g["fat"] ?: 0.0,
            carbBreakdown = if (fiber == null && sugars == null) null else CarbBreakdown(fiber = fiber ?: 0.0, sugar = sugars ?: 0.0),
            micronutrients = micronutrients,
            source = "KPKN catálogo v2",
            sourceRecordId = recordId,
            nutritionBasis = if (foodState == FoodState.COOKED) "PER_100G_COOKED" else "PER_100G_AS_SOLD",
            foodState = foodState.name,
            datasetVersion = generation,
        )
    }

    /**
     * Se arma a mano (sin `scaleFoodByPortion`): éste redondea las kcal al entero y los macros a 0,1 g, y aquí el escalado
     * debe ser exacto (per100g × gramos / 100). Los campos sin dato quedan en 0.0 por el tipo; la ausencia real viaja en
     * [LoggedFood.nutrientsPer100g]. Con ficha del paquete, esa instantánea se deja vacía: la pone completa (todos los
     * nutrientes, no solo los macros del vocabulario) `withCatalogNutrients` al guardar; solo los platos derivados `kpkn_`,
     * que no están en el paquete, llevan aquí la suya.
     */
    private fun buildLoggedFood(food: FoodItem, grams: Double, recordId: String, per100g: Map<String, Double>, generation: String): LoggedFood {
        val ratio = grams / 100.0
        fun scaled(key: String) = (per100g[key] ?: 0.0) * ratio
        val catalogId = recordId.takeIf { id -> CATALOG_PREFIXES.any { id.startsWith(it) } }
        return LoggedFood(
            id = UUID.randomUUID().toString(),
            foodName = food.name,
            amount = grams,
            unit = "g",
            calories = scaled("energy"),
            protein = scaled("protein"),
            carbs = scaled("carbs"),
            fats = scaled("fat"),
            fiber = scaled("fiber"),
            sugar = scaled("sugars"),
            sodiumMg = scaled("sodium"),
            potassiumMg = scaled("potassium"),
            micronutrients = food.micronutrients.map { it.copy(amount = it.amount * ratio) },
            analysisSource = AnalysisSource.DATABASE,
            catalogRecordId = catalogId,
            nutrientsPer100g = if (catalogId == null) per100g else null,
            catalogGeneration = generation,
        )
    }

    private fun foodStateOf(state: FoodStateV2?): FoodState = when (state) {
        null -> FoodState.UNKNOWN
        FoodStateV2.RAW -> FoodState.RAW
        FoodStateV2.COOKED, FoodStateV2.FRIED, FoodStateV2.GRILLED, FoodStateV2.BAKED, FoodStateV2.STEAMED -> FoodState.COOKED
    }

    private fun amountIntentOf(kind: QuantityKindV2): AmountIntent = when (kind) {
        QuantityKindV2.MASS, QuantityKindV2.VOLUME -> AmountIntent.EXPLICIT_MASS
        QuantityKindV2.COUNT, QuantityKindV2.HOUSEHOLD, QuantityKindV2.CONTAINER -> AmountIntent.RESOLVED_SUBJECTIVE
        QuantityKindV2.VAGUE, QuantityKindV2.DEFAULT -> AmountIntent.INFERRED_CONTEXT
    }

    private fun sourceOf(recordId: String): NutritionSourceKind =
        if (CATALOG_PREFIXES.any { recordId.startsWith(it) }) NutritionSourceKind.VERIFIED_GLOBAL else NutritionSourceKind.CURATED_LOCAL

    private fun dimensionName(dimension: QuestionDimensionV2): String = when (dimension) {
        QuestionDimensionV2.IDENTITY -> "identidad"
        QuestionDimensionV2.QUANTITY -> "cantidad"
        QuestionDimensionV2.STATE -> "estado"
        QuestionDimensionV2.FORM -> "presentación"
    }
}
