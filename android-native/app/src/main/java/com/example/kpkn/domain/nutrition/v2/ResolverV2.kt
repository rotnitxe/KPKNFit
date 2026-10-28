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

    fun resolve(text: String, drafts: List<MentionDraftV2>): List<MentionV2> {
        val resolved = drafts.map { resolveOne(text, it) }
        // «un plato de X con Y»: la medida del plato es de TODO lo que lleva; lo «colgado» sin cantidad sale de ella.
        return resolved.mapIndexed { i, m ->
            val plate = plateGrams(m, drafts, resolved, i)
            if (plate == null) m else resolveOne(text, drafts[i], plate)
        }
    }

    /** Gramos del plato menos lo que le cuelga sin cantidad; null si la mención no es una medida de plato con compañía. */
    private fun plateGrams(m: MentionV2, drafts: List<MentionDraftV2>, resolved: List<MentionV2>, index: Int): Double? {
        val q = drafts[index].quantity as? QuantityV2.Household ?: return null
        if (m.excluded || m.grams == null || q.n != 1.0 || normalize(q.label) !in PLATE_LABELS) return null
        val companions = drafts.indices.filter { j ->
            drafts[j].attachedTo == index && drafts[j].quantity == null && !resolved[j].excluded && resolved[j].grams != null
        }
        if (companions.isEmpty()) return null
        val rest = companions.sumOf { resolved[it].grams!! }
        return (m.grams - rest).coerceAtLeast(PLATE_MIN_SHARE * m.grams)
    }

    // ─── Una mención ───────────────────────────────────────────────────

    private class Solved(
        val variant: ConceptVariantV2?,
        val record: RecordV2?,
        val state: FoodStateV2?,
        val form: FoodFormV2,
        val grams: Double,
        val kind: QuantityKindV2,
        val flags: Flags = Flags(),
    )

    /** Avisos de cómo se llegó a los gramos y a la ficha; alimentan la política de preguntas. */
    private class Flags {
        /** Fracción de una pieza/envase cuyo peso entero se estimó (no lo dijo el texto). */
        var assumedWhole = false
        /** Medida casera o envase sin porción propia del concepto: se usó la medida genérica. */
        var genericMeasure = false
        /** Plural sin número («galletas»): se supuso un número de unidades. */
        var pluralDefault = false
        /** El estado pedido no existe en el concepto (y la diferencia con el elegido es de fondo). */
        var stateFallback = false
        /** La forma pedida no existe en el concepto. */
        var formFallback = false
    }

    private class Macros(val kcal: Double?, val protein: Double?, val fat: Double?)

    private fun resolveOne(text: String, draft: MentionDraftV2, gramsOverride: Double? = null): MentionV2 {
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
        val plural = isBarePlural(text, draft)
        val solved = solve(concept, draft, plural).let { s ->
            if (gramsOverride == null) s else Solved(s.variant, s.record, s.state, s.form, gramsOverride, s.kind, s.flags)
        }
        val per100g = solved.record?.per100g.orEmpty()
        val questions = questionsFor(concept, draft, solved, plural)
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
            autoAccepted = questions.isEmpty() && recordId != null && confidence >= ACCEPT_MIN_CONFIDENCE,
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
    private fun solve(concept: ConceptV2, draft: MentionDraftV2, plural: Boolean = false): Solved {
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
        val flags = Flags()
        val grams = gramsFor(concept, record, state, powder, q, draft.attachedTo != null, plural, flags)
        if (variant != null) {
            // Lo que el texto pidió y el concepto no tiene: se usó la ficha más cercana y el hueco se avisa.
            flags.stateFallback = draft.state != null && !stateSatisfied(draft.state, variant.state)
            flags.formFallback = draft.form != null && !formSatisfied(draft.form, variant.form)
        }
        return Solved(variant, record, state, form, grams, kind, flags)
    }

    /** El estado pedido se cubre con el de la variante (los de cocción seca se tratan como equivalentes entre sí). */
    private fun stateSatisfied(wanted: FoodStateV2?, got: FoodStateV2?): Boolean =
        wanted == got || (wanted in DRY_HEAT && got in DRY_HEAT) || (wanted == FoodStateV2.COOKED && got in COOKED_LIKE) ||
            (got == FoodStateV2.COOKED && wanted in COOKED_LIKE)

    private fun formSatisfied(wanted: FoodFormV2?, got: FoodFormV2): Boolean =
        wanted == got || (wanted in DIET_FORMS && got in DIET_FORMS)

    private fun pickVariant(concept: ConceptV2, state: FoodStateV2?, form: FoodFormV2): ConceptVariantV2? {
        val candidates = concept.variants.filter { it.recordId != null }
        if (candidates.isEmpty()) return null
        return candidates.firstOrNull { it.state == state && it.form == form }
            ?: candidates.firstOrNull { it.form == form && statesCompatible(it.state, state) }
            ?: candidates.firstOrNull { form in DIET_FORMS && it.form in DIET_FORMS && statesCompatible(it.state, state) }
            ?: candidates.firstOrNull { it.state == state && it.form == concept.defaultForm }
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
        plural: Boolean,
        flags: Flags,
    ): Double = when (q) {
        is QuantityV2.Mass -> q.grams
        is QuantityV2.Volume -> q.ml * (concept.densityGPerMl ?: record?.densityGPerMl ?: 1.0)
        is QuantityV2.Count -> countGrams(concept, q.n, q.size, flags)
        is QuantityV2.Household -> householdGrams(concept, record, state, powder, q, flags)
        is QuantityV2.Container -> containerGrams(concept, record, state, powder, q, flags)
        QuantityV2.Vague ->
            if (plural && concept.countable) pluralUnits(concept, flags) else 0.5 * concept.defaultGrams
        null ->
            if (attached) {
                attachedDefault(concept)
            } else if (concept.countable) {
                if (plural) pluralUnits(concept, flags) else countGrams(concept, 1.0, SizeV2.M, flags)
            } else {
                concept.defaultGrams
            }
    }

    /**
     * Cantidad por defecto de lo que viene «con» otra cosa: la menor porción de aderezo/lámina del concepto; si el
     * concepto es una fruta de pieza entera y esa porción es una rodaja (menos de [ATTACHED_SLICE_SHARE] de la unidad),
     * «con plátano» es media pieza. Las verduras de aderezo («con tomate») conservan su porción chica.
     */
    private fun attachedDefault(concept: ConceptV2): Double {
        val small = concept.portions.filter { normalize(it.label) in SMALL_PORTION_LABELS }.minOfOrNull { it.grams }
        val half = 0.5 * concept.defaultGrams
        if (small == null) return half
        val unitM = concept.unit?.grams?.get(SizeV2.M)
        if (unitM == null || !concept.countable || concept.category != FRUIT_CATEGORY) return small
        return if (small < ATTACHED_SLICE_SHARE * unitM) 0.5 * unitM else small
    }

    /**
     * Plural sin número («galletas»): dos unidades, tres si la unidad es pequeña (< [SMALL_UNIT_GRAMS] g). Las verduras y
     * tubérculos en plural sin número son un acompañamiento de una porción, no varias piezas.
     */
    private fun pluralUnits(concept: ConceptV2, flags: Flags): Double {
        val unitM = concept.unit?.grams?.let { it[SizeV2.M] ?: it.values.firstOrNull() } ?: concept.defaultGrams
        val side = concept.category in SIDE_CATEGORIES && unitM >= SMALL_UNIT_GRAMS
        val n = if (side) 1.0 else if (unitM < SMALL_UNIT_GRAMS) PLURAL_UNITS_SMALL else PLURAL_UNITS
        flags.pluralDefault = n != 1.0
        return countGrams(concept, n, SizeV2.M, Flags())
    }

    private fun countGrams(concept: ConceptV2, n: Double, size: SizeV2?, flags: Flags): Double {
        if (n > 0.0 && n < 1.0) {
            // Fracción de la pieza entera cuando la unidad del concepto es una porción de ella («un cuarto de pizza»).
            wholeItemGrams(concept)?.let {
                flags.assumedWhole = true
                return n * it
            }
        }
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

    /**
     * Peso de la pieza entera cuando la unidad del concepto es una porción de ella (trozo, porción, pedazo): la mediana de
     * las porciones del concepto que pesan al menos [WHOLE_MIN_RATIO] veces la unidad. Null si no hay tal porción.
     */
    private fun wholeItemGrams(concept: ConceptV2): Double? {
        val unit = concept.unit ?: return null
        val names = unit.names.map { normalize(it) }
        if (names.none { it in SLICE_UNIT_NAMES } || "unidad" in names) return null
        val unitM = unit.grams[SizeV2.M] ?: return null
        val whole = concept.portions.map { it.grams }.filter { it >= WHOLE_MIN_RATIO * unitM }.sorted()
        return whole.getOrNull((whole.size - 1) / 2)
    }

    private fun householdGrams(
        concept: ConceptV2,
        record: RecordV2?,
        state: FoodStateV2?,
        powder: Boolean,
        q: QuantityV2.Household,
        flags: Flags,
    ): Double {
        val label = normalize(q.label)
        val factor = sizeFactor(q.size)
        conceptPortion(concept, label, state)?.let { return q.n * it.grams * factor }
        recordPortionGrams(record, HOUSEHOLD_EN[label])?.let { return q.n * it * factor }
        if (label != "unidad") flags.genericMeasure = true
        GENERIC_HOUSEHOLD_ML[label]?.let { return q.n * it * genericDensity(concept, record, powder) * factor }
        GENERIC_HOUSEHOLD_G[label]?.let { return q.n * it * factor }
        return when (label) {
            "porcion" -> q.n * concept.defaultGrams * factor
            "unidad" -> countGrams(concept, q.n, q.size, flags)
            else -> q.n * concept.defaultGrams * factor
        }
    }

    private fun containerGrams(
        concept: ConceptV2,
        record: RecordV2?,
        state: FoodStateV2?,
        powder: Boolean,
        q: QuantityV2.Container,
        flags: Flags,
    ): Double {
        val label = normalize(q.label)
        q.netGrams?.let { return it * q.n }
        q.netMl?.let { return it * (concept.densityGPerMl ?: record?.densityGPerMl ?: 1.0) * q.n }
        conceptPortion(concept, label, state)?.let { return q.n * it.grams }
        recordPortionGrams(record, CONTAINER_EN[label])?.let { return q.n * it }
        flags.genericMeasure = true
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

    /** Plural sin número ni «un poco» («galletas», «unas aceitunas»): la primera palabra del alimento termina en s. */
    private fun isBarePlural(text: String, draft: MentionDraftV2): Boolean {
        if (draft.quantity != null && draft.quantity != QuantityV2.Vague) return false
        val start = draft.foodStart.coerceIn(0, text.length)
        val end = draft.foodEnd.coerceIn(start, text.length)
        val word = normalize(text.substring(start, end)).split(' ', '-').firstOrNull { it.isNotEmpty() } ?: return false
        return word.length > 3 && word.endsWith("s") && !word.endsWith("ss") && !word.endsWith("is") && !word.endsWith("us")
    }

    private fun sizeFactor(size: SizeV2?): Double = when (size) {
        SizeV2.S -> 0.75
        SizeV2.L -> 1.33
        else -> 1.0
    }

    // ─── Preguntas ─────────────────────────────────────────────────────

    private fun questionsFor(concept: ConceptV2, draft: MentionDraftV2, solved: Solved, plural: Boolean): List<QuestionV2> {
        val out = ArrayList<QuestionV2>(2)

        if (draft.conceptIds.size > 1) {
            val macros = draft.conceptIds.mapNotNull { id ->
                catalog.concept(id)?.let { c ->
                    val s = if (c.id == concept.id) solved else solve(c, draft, plural)
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

        // Estado o forma pedidos en el texto que el concepto no tiene: se usó la ficha más cercana.
        val base = macrosOf(solved.record, solved.grams)
        if (solved.flags.stateFallback && out.none { it.dimension == QuestionDimensionV2.STATE } && (base?.kcal ?: 0.0) >= FALLBACK_MIN_KCAL) {
            out += QuestionV2(QuestionDimensionV2.STATE, listOfNotNull(solved.state?.name, draft.state?.name))
        }
        if (solved.flags.formFallback && out.none { it.dimension == QuestionDimensionV2.FORM } && (base?.kcal ?: 0.0) >= FALLBACK_MIN_KCAL) {
            out += QuestionV2(QuestionDimensionV2.FORM, listOfNotNull(solved.form.name, draft.form?.name))
        }

        if (quantityIsDoubtful(concept, draft, solved)) {
            out += QuestionV2(QuestionDimensionV2.QUANTITY, concept.portions.map { it.label }.distinct().take(MAX_QUANTITY_OPTIONS))
        }
        return out
    }

    /**
     * La cantidad es una suposición cuyo error razonable (±50 % de los gramos) pesa [MATERIAL_KCAL] kcal o
     * [MATERIAL_GRAMS] g de proteína o grasa: fracción de una pieza o envase de peso estimado, plural sin número,
     * medida genérica de envase y porción por defecto de lo que no se cuenta por unidades. Lo «colgado» de otro alimento
     * sin cantidad no se pregunta (queda por confirmar por su confianza).
     */
    private fun quantityIsDoubtful(concept: ConceptV2, draft: MentionDraftV2, solved: Solved): Boolean {
        val guessed = when (solved.kind) {
            QuantityKindV2.MASS, QuantityKindV2.VOLUME -> false
            QuantityKindV2.VAGUE -> true
            QuantityKindV2.DEFAULT -> draft.attachedTo == null && (!concept.countable || solved.flags.pluralDefault)
            QuantityKindV2.COUNT -> solved.flags.assumedWhole || solved.flags.pluralDefault
            QuantityKindV2.HOUSEHOLD -> solved.flags.genericMeasure
            QuantityKindV2.CONTAINER -> solved.flags.genericMeasure || solved.flags.assumedWhole ||
                (draft.quantity as? QuantityV2.Container)?.let { it.n < 1.0 && it.netGrams == null && it.netMl == null } == true
        }
        if (!guessed) return false
        val swing = macrosOf(solved.record, solved.grams * QUANTITY_SWING) ?: return false
        return (swing.kcal ?: 0.0) >= MATERIAL_KCAL || (swing.protein ?: 0.0) >= MATERIAL_GRAMS || (swing.fat ?: 0.0) >= MATERIAL_GRAMS
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
        /** Error razonable de una cantidad supuesta: ±50 % de los gramos. */
        const val QUANTITY_SWING = 0.5
        /** Estado/forma pedido que no existe: solo se pregunta si lo comido pesa al menos esta energía. */
        const val FALLBACK_MIN_KCAL = 100.0
        const val MAX_QUANTITY_OPTIONS = 4
        /**
         * Confianza mínima para aceptar sin confirmar. Una cantidad no dicha (0,7) o una coincidencia aproximada sobre una
         * cantidad estimada (0,85 × 0,9) queda por confirmar aunque no merezca una pregunta.
         */
        const val ACCEPT_MIN_CONFIDENCE = 0.8
        const val DEFAULT_RECONSTITUTED_DENSITY = 1.03

        val COOKED_LIKE = setOf(FoodStateV2.GRILLED, FoodStateV2.BAKED, FoodStateV2.STEAMED)
        val DRY_HEAT = setOf(FoodStateV2.COOKED, FoodStateV2.GRILLED, FoodStateV2.BAKED, FoodStateV2.STEAMED)
        val DIET_FORMS = setOf(FoodFormV2.LIGHT, FoodFormV2.SUGAR_FREE)

        /** Unidades del concepto que son un pedazo de una pieza mayor (la pizza tiene «trozos», no «unidades»). */
        val SLICE_UNIT_NAMES = setOf("trozo", "porcion", "pedazo")
        /** Una porción cuenta como «pieza entera» si pesa al menos esto veces la unidad-trozo. */
        const val WHOLE_MIN_RATIO = 2.5

        /** Plural sin número: unidades que se suponen; tres si la unidad pesa menos de [SMALL_UNIT_GRAMS]. */
        const val PLURAL_UNITS = 2.0
        const val PLURAL_UNITS_SMALL = 3.0
        const val SMALL_UNIT_GRAMS = 30.0
        /** Una porción de aderezo menor que esta parte de la unidad es una rodaja de la pieza, no lo que se comió. */
        const val ATTACHED_SLICE_SHARE = 0.1
        const val FRUIT_CATEGORY = "FRUTAS"
        val SIDE_CATEGORIES = setOf("VERDURAS", "TUBERCULOS")

        /** Medidas de plato: abarcan todo lo que va «con» el alimento; el resto del plato no baja de esta parte. */
        val PLATE_LABELS = setOf("plato", "tazon", "bowl")
        const val PLATE_MIN_SHARE = 0.5

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
