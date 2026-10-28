package com.example.kpkn.domain.nutrition.v2

import com.example.kpkn.data.models.AmountIntent
import com.example.kpkn.data.models.AnalysisSource
import com.example.kpkn.data.models.CarbBreakdown
import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.data.models.Micronutrient
import com.example.kpkn.domain.nutrition.ClarificationRequest
import com.example.kpkn.domain.nutrition.FoodInterpretationV2Engine
import com.example.kpkn.domain.nutrition.FoodResolutionStatus
import com.example.kpkn.domain.nutrition.FoodState
import com.example.kpkn.domain.nutrition.NutritionSourceKind
import com.example.kpkn.domain.nutrition.PortionOption
import com.example.kpkn.domain.nutrition.ResolvedTag
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Convierte la salida del parser v2 ([MealParseV2]) en los [ResolvedTag] que ya consume el registrador de comidas.
 *
 * Una etiqueta por [MentionV2], en el mismo orden. La ficha del catálogo ya corresponde al estado pedido (frito, cocido…):
 * no se aplica ninguna transformación de cocción ni aceite extra. Los gramos y las claves de la ficha no se redondean aquí.
 *
 * Cada etiqueta resuelta sale ya con `interpretationV2` construida (el registrador con el intérprete v2 no la pasa por
 * `NutritionInterpretationBridge.enrich`). Invariante: para toda mención resuelta y no excluida,
 * `tag.hasMaterialQuestion() == !mention.autoAccepted`; una mención autoaceptada no lleva preguntas pendientes y una no
 * autoaceptada lleva siempre al menos una que se puede contestar (identidad, estado, presentación o cantidad), de modo que
 * la app bloquea exactamente lo que el examen A/B no autoacepta.
 */
object ResolvedTagAdapterV2 {
    private const val MAX_REVIEW_CANDIDATES = 4
    private const val MAX_PORTION_OPTIONS = 4
    private const val REQUEST_IDENTITY = "identity"
    private const val REQUEST_STATE = "state"
    private const val REQUEST_FORM = "form"
    private const val REQUEST_QUANTITY = "quantity"
    /** Fichas que están en el paquete de catálogo; el guardado les pone la instantánea completa. */
    private val PACK_PREFIXES = listOf("usda_", "ciqual_", "off_", "kpkn_")

    /** Fuentes públicas verificadas; los platos `kpkn_` (derivados de sus ingredientes, N39) siguen siendo CURATED_LOCAL. */
    private val VERIFIED_PREFIXES = listOf("usda_", "ciqual_", "off_")
    private val interpreter = FoodInterpretationV2Engine()

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
        val baseName = mention.conceptName ?: catalog.record(recordId)?.name ?: mention.text
        val name = preparedName(baseName, mention, concept)
        val foodState = foodStateOf(mention.state)
        val food = buildFood(recordId, name, concept?.category, per100g, foodState, catalog.generation)
        val logged = buildLoggedFood(food, grams, recordId, per100g, catalog.generation)

        val plan = clarificationPlan(mention, concept, name, food, grams, catalog)
        val requests = plan.requests
        val status = when {
            requests.isEmpty() -> FoodResolutionStatus.AUTO
            requests.any { it.requestId == REQUEST_IDENTITY } -> FoodResolutionStatus.NEEDS_REVIEW
            requests.any { it.requestId == REQUEST_STATE } -> FoodResolutionStatus.NEEDS_STATE
            else -> FoodResolutionStatus.NEEDS_CONFIRMATION
        }
        val statusText = when {
            requests.isEmpty() -> if (mention.excluded) "Excluido" else "Interpretado"
            else -> "Revisar: " + requests.joinToString(", ") { requestName(it.requestId) }
        }

        val tag = ResolvedTag(
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
            needsCookingClarification = false,
            oilApplied = true,
            appliedOilGrams = 0.0,
            reviewCandidates = plan.reviewCandidates,
            foodState = foodState,
            resolutionStatus = status,
            nutritionSource = sourceOf(recordId),
            resolutionConfidence = mention.confidence,
        )
        return tag.copy(interpretationV2 = interpreter.interpretResolved(tag, requests))
    }

    private val PORTION_WORDS = mapOf(
        "porcion" to "porción", "tazon" to "tazón", "punado" to "puñado", "lamina" to "lámina", "laminas" to "láminas",
        "cucharon" to "cucharón", "capsula" to "cápsula", "botellin" to "botellín", "balon" to "balón",
    )

    /** Etiquetas del vocabulario (ASCII) con tildes para el texto mostrado; solo palabras completas. */
    private fun portionLabel(label: String): String =
        label.split(" ").joinToString(" ") { PORTION_WORDS[it] ?: it }

    /** Polvo preparado (RECONSTITUTED de un concepto en polvo seco): el nombre lo dice, con género. */
    private fun preparedName(name: String, mention: MentionV2, concept: ConceptV2?): String {
        if (mention.form != FoodFormV2.RECONSTITUTED || concept?.defaultForm != FoodFormV2.POWDER_DRY) return name
        if (name.contains("prepar", ignoreCase = true)) return name
        val first = name.trim().substringBefore(' ').lowercase()
        val feminine = first == "leche" || (first.endsWith("a") && first != "bcaa")
        return name + if (feminine) " (preparada)" else " (preparado)"
    }

    private class Candidate(val food: FoodItem, val label: String)

    private class Plan(val requests: List<ClarificationRequest>, val reviewCandidates: List<FoodItem>)

    /**
     * Peticiones de aclaración de una mención: ninguna si está autoaceptada o excluida; si no, una por dimensión preguntada
     * (identidad, estado, presentación, cantidad, en ese orden). Una petición de identidad/estado/presentación con menos de
     * 2 candidatos se sustituye por la de cantidad, que siempre se puede contestar (también a mano).
     */
    private fun clarificationPlan(
        mention: MentionV2,
        concept: ConceptV2?,
        name: String,
        food: FoodItem,
        grams: Double,
        catalog: ConceptCatalogV2,
    ): Plan {
        if (mention.excluded || mention.autoAccepted) return Plan(emptyList(), emptyList())
        val current = Candidate(food, name)
        val dimensions = mention.questions.map { it.dimension }.distinct()
        val requests = mutableListOf<ClarificationRequest>()
        val candidateLists = mutableListOf<List<Candidate>>()

        fun add(request: ClarificationRequest) {
            if (requests.none { it.requestId == request.requestId }) requests += request
        }
        fun addQuantity() = add(quantityRequest(mention, concept, grams))
        fun addIdentity(id: String, candidates: List<Candidate>) {
            if (candidates.map { it.food.id }.distinct().size < 2) return addQuantity()
            candidateLists += candidates
            add(ClarificationRequest.Identity(id, candidates.map { it.food.id }, candidates.map { it.label }))
        }

        val order = listOf(QuestionDimensionV2.IDENTITY, QuestionDimensionV2.STATE, QuestionDimensionV2.FORM, QuestionDimensionV2.QUANTITY)
        for (dimension in order) {
            if (dimension !in dimensions) continue
            when (dimension) {
                QuestionDimensionV2.IDENTITY -> {
                    val options = mention.questions.first { it.dimension == dimension }.options
                    addIdentity(REQUEST_IDENTITY, identityCandidates(current, options, mention, catalog))
                }
                QuestionDimensionV2.STATE ->
                    addIdentity(REQUEST_STATE, variantCandidates(current, mention, concept, name, catalog, byState = true))
                QuestionDimensionV2.FORM ->
                    addIdentity(REQUEST_FORM, variantCandidates(current, mention, concept, name, catalog, byState = false))
                QuestionDimensionV2.QUANTITY -> addQuantity()
            }
        }
        if (requests.isEmpty()) {
            if (mention.draft.fuzzy) addIdentity(REQUEST_IDENTITY, identityCandidates(current, mention.draft.conceptIds, mention, catalog))
            else addQuantity()
        }
        val review = candidateLists.flatten().map { it.food }.distinctBy { it.id }
        return Plan(requests, review)
    }

    /** La comida actual primero y luego las de los otros conceptos (ficha por defecto), máx. 4 en total. */
    private fun identityCandidates(
        current: Candidate,
        conceptIds: List<String>,
        mention: MentionV2,
        catalog: ConceptCatalogV2,
    ): List<Candidate> {
        val others = conceptIds
            .filter { it != mention.conceptId }
            .distinct()
            .mapNotNull { conceptId ->
                val concept = catalog.concept(conceptId) ?: return@mapNotNull null
                val variant = concept.variants.firstOrNull { it.recordId != null && it.state == concept.defaultState && it.form == concept.defaultForm }
                    ?: concept.variants.firstOrNull { it.recordId != null }
                    ?: return@mapNotNull null
                val record = catalog.record(variant.recordId) ?: return@mapNotNull null
                Candidate(
                    buildFood(record.id, concept.name, concept.category, record.per100g, foodStateOf(variant.state), catalog.generation),
                    concept.name,
                )
            }
        return (listOf(current) + others).distinctBy { it.food.id }.take(MAX_REVIEW_CANDIDATES)
    }

    /** Una ficha por estado (o por forma) distinto entre las variantes con ficha del concepto; la actual primero. */
    private fun variantCandidates(
        current: Candidate,
        mention: MentionV2,
        concept: ConceptV2?,
        name: String,
        catalog: ConceptCatalogV2,
        byState: Boolean,
    ): List<Candidate> {
        if (concept == null) return listOf(current)
        val currentState = mention.state ?: concept.variants.firstOrNull { it.recordId == current.food.id }?.state
        val currentKey: Any? = if (byState) currentState else mention.form
        fun keyOf(variant: ConceptVariantV2): Any? = if (byState) variant.state else variant.form
        val others = concept.variants
            .filter { it.recordId != null && catalog.record(it.recordId) != null }
            .filter { keyOf(it) != null && keyOf(it) != currentKey && it.recordId != current.food.id }
            .groupBy { keyOf(it) }
            .values
            .mapNotNull { group ->
                val preferred = if (byState) group.firstOrNull { it.form == mention.form } else group.firstOrNull { it.state == currentState }
                val variant = preferred ?: group.first()
                val record = catalog.record(variant.recordId) ?: return@mapNotNull null
                val food = buildFood(record.id, name, concept.category, record.per100g, foodStateOf(variant.state), catalog.generation)
                Candidate(food, "$name (${if (byState) stateName(variant.state) else formName(variant.form)})")
            }
        val currentLabel = "$name (${if (byState) stateName(currentState) else formName(mention.form)})"
        return (listOf(Candidate(current.food, currentLabel)) + others).distinctBy { it.food.id }.take(MAX_REVIEW_CANDIDATES)
    }

    /** Primera opción: lo estimado; luego hasta 4 porciones del concepto (distintas por etiqueta). */
    private fun quantityRequest(mention: MentionV2, concept: ConceptV2?, grams: Double): ClarificationRequest.Portion {
        val estimated = PortionOption("Lo estimado (${grams.roundToInt()} g)", grams)
        val portions = concept?.portions.orEmpty()
            .filter { it.grams.isFinite() && it.grams > 0.0 }
            .sortedBy { if (it.state == null || it.state == mention.state) 0 else 1 }
            .distinctBy { it.label }
            .take(MAX_PORTION_OPTIONS)
            .map { PortionOption("${portionLabel(it.label)} (${it.grams.roundToInt()} g)", it.grams) }
        return ClarificationRequest.Portion(REQUEST_QUANTITY, listOf(estimated) + portions)
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
     * [LoggedFood.nutrientsPer100g]. Con ficha del paquete (incluidos los platos `kpkn_`, que desde N39 están en él), esa
     * instantánea se deja vacía: la pone completa (todos los nutrientes, no solo los macros del vocabulario)
     * `withCatalogNutrients` al guardar; solo una ficha sin prefijo del paquete lleva aquí la del vocabulario.
     */
    private fun buildLoggedFood(food: FoodItem, grams: Double, recordId: String, per100g: Map<String, Double>, generation: String): LoggedFood {
        val ratio = grams / 100.0
        fun scaled(key: String) = (per100g[key] ?: 0.0) * ratio
        val catalogId = recordId.takeIf { id -> PACK_PREFIXES.any { id.startsWith(it) } }
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
        if (VERIFIED_PREFIXES.any { recordId.startsWith(it) }) NutritionSourceKind.VERIFIED_GLOBAL else NutritionSourceKind.CURATED_LOCAL

    private fun requestName(requestId: String): String = when (requestId) {
        REQUEST_IDENTITY -> "identidad"
        REQUEST_STATE -> "estado"
        REQUEST_FORM -> "presentación"
        else -> "cantidad"
    }

    private fun stateName(state: FoodStateV2?): String = when (state) {
        null -> "sin especificar"
        FoodStateV2.RAW -> "crudo"
        FoodStateV2.COOKED -> "cocido"
        FoodStateV2.FRIED -> "frito"
        FoodStateV2.GRILLED -> "a la plancha"
        FoodStateV2.BAKED -> "al horno"
        FoodStateV2.STEAMED -> "al vapor"
    }

    private fun formName(form: FoodFormV2): String = when (form) {
        FoodFormV2.NA -> "natural"
        FoodFormV2.POWDER_DRY -> "en polvo"
        FoodFormV2.RECONSTITUTED -> "preparado"
        FoodFormV2.CONCENTRATE -> "concentrado"
        FoodFormV2.LIQUID -> "líquido"
        FoodFormV2.DRIED -> "deshidratado"
        FoodFormV2.CANNED_DRAINED -> "en conserva, escurrido"
        FoodFormV2.CANNED_UNDRAINED -> "en conserva, con su líquido"
        FoodFormV2.LIGHT -> "light"
        FoodFormV2.SUGAR_FREE -> "sin azúcar"
    }
}
