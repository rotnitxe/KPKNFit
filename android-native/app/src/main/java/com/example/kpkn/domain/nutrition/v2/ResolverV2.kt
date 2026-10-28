package com.example.kpkn.domain.nutrition.v2

import java.text.Normalizer
import kotlin.math.abs

/**
 * Resolutor del parser v2 (F2-c): borrador de la gramática → mención con ficha, gramos y preguntas.
 *
 * Para cada [MentionDraftV2]: elige el concepto (`conceptIds[0]`), la variante (estado + forma → ficha), calcula los
 * gramos según el tipo de cantidad (masa, volumen, conteo, medida casera, envase, vaga o ausente), reconstituye los
 * polvos cuando la medida es de bebida, deriva los macros comidos y formula las preguntas materiales (identidad,
 * estado, forma). Determinista y sin estado. Contrato en `docs/nutrition-v2/F2_PARSER_V2.md` §2.4, §5.
 */
class ResolverV2(private val catalog: ConceptCatalogV2) {

    fun resolve(text: String, drafts: List<MentionDraftV2>): List<MentionV2> = drafts.map { resolveOne(text, it) }

    // ─── Una mención ───────────────────────────────────────────────────

    private class Solved(
        val variant: ConceptVariantV2?,
        val record: RecordV2?,
        val state: FoodStateV2?,
        val form: FoodFormV2,
        val grams: Double,
        val kind: QuantityKindV2,
    )

    private class Macros(val kcal: Double?, val protein: Double?, val fat: Double?)

    private fun resolveOne(text: String, draft: MentionDraftV2): MentionV2 {
        val start = draft.start.coerceIn(0, text.length)
        val end = draft.end.coerceIn(start, text.length)
        val span = text.substring(start, end)
        val kind = kindOf(draft.quantity)
        val conceptId = draft.conceptIds.firstOrNull()
        val concept = conceptId?.let { catalog.concept(it) }
        if (concept == null) {
            return MentionV2(
                draft, span, conceptId, null, null, null, kind, draft.state, draft.form ?: FoodFormV2.NA,
                emptyMap(), null, null, null, null, draft.excluded, 0.0, emptyList(), false,
            )
        }
        if (draft.excluded) {
            return MentionV2(
                draft, span, concept.id, concept.name, null, null, kind, draft.state, draft.form ?: FoodFormV2.NA,
                emptyMap(), null, null, null, null, true, identityConfidence(draft, emptyList()) * quantityConfidence(kind),
                emptyList(), false,
            )
        }
        val solved = solve(concept, draft)
        val per100g = solved.record?.per100g.orEmpty()
        val questions = questionsFor(concept, draft, solved)
        val confidence = identityConfidence(draft, questions) * quantityConfidence(solved.kind)
        val recordId = solved.variant?.recordId
        return MentionV2(
            draft = draft,
            text = span,
            conceptId = concept.id,
            conceptName = concept.name,
            recordId = recordId,
            grams = solved.grams,
            quantityKind = solved.kind,
            state = solved.state,
            form = solved.form,
            per100g = per100g,
            eatenKcal = eaten(per100g, "energy", solved.grams),
            eatenProtein = eaten(per100g, "protein", solved.grams),
            eatenFat = eaten(per100g, "fat", solved.grams),
            eatenCarbs = eaten(per100g, "carbs", solved.grams),
            excluded = false,
            confidence = confidence,
            questions = questions,
            autoAccepted = questions.isEmpty() && recordId != null,
        )
    }

    private fun eaten(per100g: Map<String, Double>, key: String, grams: Double): Double? =
        per100g[key]?.let { it * grams / 100.0 }

    private fun kindOf(q: QuantityV2?): QuantityKindV2 = when (q) {
        is QuantityV2.Mass -> QuantityKindV2.MASS
        is QuantityV2.Volume -> QuantityKindV2.VOLUME
        is QuantityV2.Count -> QuantityKindV2.COUNT
        is QuantityV2.Household -> QuantityKindV2.HOUSEHOLD
        is QuantityV2.Container -> QuantityKindV2.CONTAINER
        QuantityV2.Vague -> QuantityKindV2.VAGUE
        null -> QuantityKindV2.DEFAULT
    }

    private fun identityConfidence(draft: MentionDraftV2, questions: List<QuestionV2>): Double = when {
        questions.any { it.dimension == QuestionDimensionV2.IDENTITY } -> 0.6
        draft.fuzzy -> 0.85
        else -> 1.0
    }

    private fun quantityConfidence(kind: QuantityKindV2): Double = when (kind) {
        QuantityKindV2.MASS, QuantityKindV2.VOLUME -> 1.0
        QuantityKindV2.COUNT, QuantityKindV2.HOUSEHOLD, QuantityKindV2.CONTAINER -> 0.9
        QuantityKindV2.VAGUE, QuantityKindV2.DEFAULT -> 0.7
    }

    // ─── Variante y gramos ─────────────────────────────────────────────

    /** Resuelve [concept] con los datos de [draft]: variante, ficha y gramos. */
    private fun solve(concept: ConceptV2, draft: MentionDraftV2): Solved {
        val q = draft.quantity
        val kind = kindOf(q)
        val desiredState = draft.state ?: concept.defaultState
        val reconstitution = concept.reconstitution
        var forcedForm: FoodFormV2? = null

        if (reconstitution != null && (draft.form == null || draft.form == FoodFormV2.POWDER_DRY)) {
            val drinkMl = drinkMl(q)
            if (drinkMl != null) {
                // Se comió la bebida preparada.
                val prepared = pickVariant(concept, desiredState, FoodFormV2.RECONSTITUTED)
                    ?.takeIf { it.form == FoodFormV2.RECONSTITUTED }
                if (prepared != null) {
                    val record = catalog.record(prepared.recordId)
                    val grams = drinkMl * (record?.densityGPerMl ?: DEFAULT_RECONSTITUTED_DENSITY)
                    return Solved(prepared, record, prepared.state, prepared.form, grams, kind)
                }
                val powder = pickVariant(concept, desiredState, FoodFormV2.POWDER_DRY)
                val grams = drinkMl * reconstitution.gramsPer100ml / 100.0
                return Solved(
                    powder, catalog.record(powder?.recordId), powder?.state ?: desiredState,
                    powder?.form ?: FoodFormV2.POWDER_DRY, grams, kind,
                )
            }
            if (isDryMeasure(q)) forcedForm = FoodFormV2.POWDER_DRY
        }

        val desiredForm = forcedForm ?: draft.form ?: concept.defaultForm
        val variant = pickVariant(concept, desiredState, desiredForm)
        val record = catalog.record(variant?.recordId)
        val state = if (variant != null) variant.state else desiredState
        val form = variant?.form ?: desiredForm
        val powder = form == FoodFormV2.POWDER_DRY
        val grams = gramsFor(concept, record, state, powder, q, draft.attachedTo != null)
        return Solved(variant, record, state, form, grams, kind)
    }

    private fun pickVariant(concept: ConceptV2, state: FoodStateV2?, form: FoodFormV2): ConceptVariantV2? {
        val candidates = concept.variants.filter { it.recordId != null }
        if (candidates.isEmpty()) return null
        return candidates.firstOrNull { it.state == state && it.form == form }
            ?: candidates.firstOrNull { it.form == form && statesCompatible(it.state, state) }
            ?: candidates.firstOrNull { it.state == state }
            ?: candidates.firstOrNull { it.form == form }
            ?: candidates.firstOrNull { it.state == concept.defaultState && it.form == concept.defaultForm }
            ?: candidates.first()
    }

    private fun statesCompatible(a: FoodStateV2?, b: FoodStateV2?): Boolean =
        a == b || (a == FoodStateV2.COOKED && b in COOKED_LIKE) || (b == FoodStateV2.COOKED && a in COOKED_LIKE)

    // ─── Reconstitución ────────────────────────────────────────────────

    /** Mililitros si la medida es de bebida (se come el polvo preparado); null si es una medida de polvo seco u otra. */
    private fun drinkMl(q: QuantityV2?): Double? = when (q) {
        is QuantityV2.Volume -> q.ml
        is QuantityV2.Household -> DRINK_HOUSEHOLD_ML[normalize(q.label)]?.let { q.n * it * sizeFactor(q.size) }
        is QuantityV2.Container ->
            if (q.netGrams != null) null
            else DRINK_CONTAINER_ML[normalize(q.label)]?.let { q.n * (q.netMl ?: it) }
        else -> null
    }

    private fun isDryMeasure(q: QuantityV2?): Boolean = when (q) {
        is QuantityV2.Mass -> true
        is QuantityV2.Household -> normalize(q.label) in DRY_HOUSEHOLD
        is QuantityV2.Container -> normalize(q.label) == "sobre"
        else -> false
    }

    // ─── Gramos ────────────────────────────────────────────────────────

    private fun gramsFor(
        concept: ConceptV2,
        record: RecordV2?,
        state: FoodStateV2?,
        powder: Boolean,
        q: QuantityV2?,
        attached: Boolean,
    ): Double = when (q) {
        is QuantityV2.Mass -> q.grams
        is QuantityV2.Volume -> q.ml * (concept.densityGPerMl ?: record?.densityGPerMl ?: 1.0)
        is QuantityV2.Count -> countGrams(concept, q.n, q.size)
        is QuantityV2.Household -> householdGrams(concept, record, state, powder, q)
        is QuantityV2.Container -> containerGrams(concept, record, state, powder, q)
        QuantityV2.Vague -> 0.5 * concept.defaultGrams
        null ->
            if (attached) {
                concept.portions.filter { normalize(it.label) in SMALL_PORTION_LABELS }.minOfOrNull { it.grams }
                    ?: (0.5 * concept.defaultGrams)
            } else if (concept.countable) {
                countGrams(concept, 1.0, SizeV2.M)
            } else {
                concept.defaultGrams
            }
    }

    private fun countGrams(concept: ConceptV2, n: Double, size: SizeV2?): Double {
        val unit = concept.unit
        if (unit != null && unit.grams.isNotEmpty()) {
            val grams = unit.grams[size ?: SizeV2.M] ?: unit.grams[SizeV2.M] ?: unit.grams.values.first()
            return n * grams
        }
        val portion = COUNT_LABELS.firstNotNullOfOrNull { label ->
            concept.portions.firstOrNull { normalize(it.label) == label }
        }
        return n * (portion?.grams ?: concept.defaultGrams)
    }

    private fun householdGrams(
        concept: ConceptV2,
        record: RecordV2?,
        state: FoodStateV2?,
        powder: Boolean,
        q: QuantityV2.Household,
    ): Double {
        val label = normalize(q.label)
        val factor = sizeFactor(q.size)
        conceptPortion(concept, label, state)?.let { return q.n * it.grams * factor }
        recordPortionGrams(record, HOUSEHOLD_EN[label])?.let { return q.n * it * factor }
        GENERIC_HOUSEHOLD_ML[label]?.let { return q.n * it * genericDensity(concept, record, powder) * factor }
        GENERIC_HOUSEHOLD_G[label]?.let { return q.n * it * factor }
        return when (label) {
            "porcion" -> q.n * concept.defaultGrams * factor
            "unidad" -> countGrams(concept, q.n, q.size)
            else -> q.n * concept.defaultGrams * factor
        }
    }

    private fun containerGrams(
        concept: ConceptV2,
        record: RecordV2?,
        state: FoodStateV2?,
        powder: Boolean,
        q: QuantityV2.Container,
    ): Double {
        val label = normalize(q.label)
        q.netGrams?.let { return it * q.n }
        q.netMl?.let { return it * (concept.densityGPerMl ?: record?.densityGPerMl ?: 1.0) * q.n }
        conceptPortion(concept, label, state)?.let { return q.n * it.grams }
        recordPortionGrams(record, CONTAINER_EN[label])?.let { return q.n * it }
        val isDrink = concept.kind == ConceptKindV2.DRINK
        val genericMl = when (label) {
            "lata" -> if (isDrink) 350.0 else null
            "botella" -> 500.0
            "caja" -> 200.0
            "schop" -> 500.0
            else -> null
        }
        if (genericMl != null) return q.n * genericMl * genericDensity(concept, record, powder)
        val genericG = when (label) {
            "lata" -> 170.0
            "sobre" -> 10.0
            "envase" -> 125.0
            "paquete" -> 40.0
            else -> null
        }
        return q.n * (genericG ?: concept.defaultGrams)
    }

    /** Densidad de una medida genérica: concepto, ficha, o 1,0 en bebidas / 0,6 en polvos / 0,85 en el resto. */
    private fun genericDensity(concept: ConceptV2, record: RecordV2?, powder: Boolean): Double =
        concept.densityGPerMl ?: record?.densityGPerMl ?: when {
            concept.kind == ConceptKindV2.DRINK -> 1.0
            powder -> 0.6
            else -> 0.85
        }

    private fun conceptPortion(concept: ConceptV2, label: String, state: FoodStateV2?): ConceptPortionV2? {
        val matching = concept.portions.filter { normalize(it.label) == label }
        if (matching.isEmpty()) return null
        return matching.firstOrNull { state != null && it.state == state }
            ?: matching.firstOrNull { it.state == null }
            ?: matching.first()
    }

    /** Gramos de UNA unidad de la primera porción de [record] cuya etiqueta contiene alguna de [keywords]. */
    private fun recordPortionGrams(record: RecordV2?, keywords: List<String>?): Double? {
        if (record == null || keywords == null) return null
        for (keyword in keywords) {
            for (portion in record.portions) {
                val words = WORD.findAll(portion.label.lowercase()).map { it.value }.toList()
                if (words.any { it == keyword || it == keyword + "s" || it == keyword + "es" }) {
                    return portion.grams / leadingQuantity(portion.label)
                }
            }
        }
        return null
    }

    /** Cantidad con la que empieza la etiqueta de una porción de ficha («2 tbsp» → 2, «1/2 cup» → 0,5); 1 si no hay. */
    private fun leadingQuantity(label: String): Double {
        val m = LEADING_QUANTITY.find(label.trim()) ?: return 1.0
        val value = when {
            m.groupValues[1].isNotEmpty() -> {
                val whole = m.groupValues[1].toDouble()
                whole + m.groupValues[2].toDouble() / m.groupValues[3].toDouble()
            }
            m.groupValues[4].isNotEmpty() -> m.groupValues[4].toDouble() / m.groupValues[5].toDouble()
            else -> m.groupValues[6].replace(',', '.').toDoubleOrNull() ?: 1.0
        }
        return if (value > 0.0) value else 1.0
    }

    private fun sizeFactor(size: SizeV2?): Double = when (size) {
        SizeV2.S -> 0.75
        SizeV2.L -> 1.33
        else -> 1.0
    }

    // ─── Preguntas ─────────────────────────────────────────────────────

    private fun questionsFor(concept: ConceptV2, draft: MentionDraftV2, solved: Solved): List<QuestionV2> {
        val out = ArrayList<QuestionV2>(1)

        if (draft.conceptIds.size > 1) {
            val macros = draft.conceptIds.mapNotNull { id ->
                catalog.concept(id)?.let { c ->
                    val s = if (c.id == concept.id) solved else solve(c, draft)
                    macrosOf(s.record, s.grams)
                }
            }
            if (macros.size >= 2 && anyPairMaterial(macros)) {
                out += QuestionV2(QuestionDimensionV2.IDENTITY, draft.conceptIds)
            }
        }

        val withRecord = concept.variants.filter { catalog.record(it.recordId) != null }

        if (draft.state == null && concept.defaultState == null) {
            val groups = withRecord.filter { it.state != null }.groupBy { it.state!! }
            val alternatives = groups.mapNotNull { (state, variants) ->
                val v = variants.firstOrNull { it.form == solved.form } ?: variants.first()
                macrosOf(catalog.record(v.recordId), solved.grams)?.let { state.name to it }
            }
            if (alternatives.size >= 2 && anyPairMaterial(alternatives.map { it.second })) {
                out += QuestionV2(QuestionDimensionV2.STATE, alternatives.map { it.first })
            }
        }

        if (draft.form == null && concept.defaultForm == FoodFormV2.NA) {
            val groups = withRecord.groupBy { it.form }
            val alternatives = groups.mapNotNull { (form, variants) ->
                val v = variants.firstOrNull { it.state == solved.state } ?: variants.first()
                macrosOf(catalog.record(v.recordId), solved.grams)?.let { form.name to it }
            }
            if (alternatives.size >= 2 && anyPairMaterial(alternatives.map { it.second })) {
                out += QuestionV2(QuestionDimensionV2.FORM, alternatives.map { it.first })
            }
        }
        return out
    }

    private fun macrosOf(record: RecordV2?, grams: Double): Macros? {
        record ?: return null
        return Macros(
            kcal = eaten(record.per100g, "energy", grams),
            protein = eaten(record.per100g, "protein", grams),
            fat = eaten(record.per100g, "fat", grams),
        )
    }

    private fun anyPairMaterial(macros: List<Macros>): Boolean {
        for (i in macros.indices) for (j in i + 1 until macros.size) if (material(macros[i], macros[j])) return true
        return false
    }

    private fun material(a: Macros, b: Macros): Boolean =
        gap(a.kcal, b.kcal) > MATERIAL_KCAL || gap(a.protein, b.protein) > MATERIAL_GRAMS || gap(a.fat, b.fat) > MATERIAL_GRAMS

    private fun gap(x: Double?, y: Double?): Double = if (x == null || y == null) 0.0 else abs(x - y)

    // ─── Tablas ────────────────────────────────────────────────────────

    private fun normalize(label: String): String =
        Normalizer.normalize(label.trim().lowercase(), Normalizer.Form.NFD).filter { it.category != CharCategory.NON_SPACING_MARK }

    private companion object {
        const val MATERIAL_KCAL = 50.0
        const val MATERIAL_GRAMS = 5.0
        const val DEFAULT_RECONSTITUTED_DENSITY = 1.03

        val COOKED_LIKE = setOf(FoodStateV2.GRILLED, FoodStateV2.BAKED, FoodStateV2.STEAMED)

        val COUNT_LABELS = listOf("unidad", "pieza", "trozo", "porcion")
        val SMALL_PORTION_LABELS = setOf("cucharada", "rebanada", "lamina", "porcion")

        val DRINK_HOUSEHOLD_ML = mapOf("taza" to 240.0, "vaso" to 200.0, "copa" to 150.0)
        val DRINK_CONTAINER_ML = mapOf("caja" to 200.0, "botella" to 500.0, "schop" to 500.0)
        val DRY_HOUSEHOLD = setOf("cucharada", "cucharadita", "scoop")

        val HOUSEHOLD_EN = mapOf(
            "taza" to listOf("cup"),
            "vaso" to listOf("glass", "cup"),
            "cucharada" to listOf("tbsp", "tablespoon"),
            "cucharadita" to listOf("tsp", "teaspoon"),
            "rebanada" to listOf("slice"),
            "lamina" to listOf("slice"),
            "trozo" to listOf("piece"),
            "unidad" to listOf("each", "medium", "item", "regular"),
            "porcion" to listOf("serving"),
            "copa" to listOf("glass"),
            "shot" to listOf("jigger", "shot"),
        )

        val CONTAINER_EN = mapOf(
            "lata" to listOf("can"),
            "botella" to listOf("bottle"),
            "caja" to listOf("box", "carton"),
            "sobre" to listOf("packet"),
            "envase" to listOf("container", "cup"),
            "paquete" to listOf("package", "bag"),
            "schop" to listOf("pint"),
        )

        val GENERIC_HOUSEHOLD_ML = mapOf(
            "taza" to 240.0, "vaso" to 200.0, "cucharada" to 15.0, "cucharadita" to 5.0,
            "copa" to 150.0, "shot" to 40.0, "chorrito" to 10.0, "cucharon" to 120.0,
        )
        val GENERIC_HOUSEHOLD_G = mapOf(
            "plato" to 300.0, "punado" to 30.0, "rebanada" to 25.0, "lamina" to 15.0, "trozo" to 50.0,
            "scoop" to 30.0, "pizca" to 0.4,
        )

        val WORD = Regex("[a-z]+")

        /** «1 1/2», «1/2» o «1.5» al inicio: grupos 1-3 mixto, 4-5 fracción, 6 decimal. */
        val LEADING_QUANTITY = Regex("""^(?:(\d+)\s+(\d+)/(\d+)|(\d+)/(\d+)|(\d+(?:[.,]\d+)?))""")
    }
}
