package com.example.kpkn.domain.nutrition.v2

/**
 * Gramática del parser v2 (F2, paso 3): de la secuencia de tramos etiquetados por [LexiconV2.tag] a borradores de
 * mención ([MentionDraftV2]).
 *
 * Determinista y sin estado. Trabaja por cláusulas (se corta en `, ; .` y en «y»/«and» seguidos de una cantidad o
 * un alimento) y dentro de cada una enlaza cantidades y modificadores con el alimento que les toca, resuelve
 * «X con Y», «sin X», la distribución («una con palta y otra con queso») y las correcciones («eran 150 g, no 200»).
 * El contrato y la tabla de casos están en `docs/nutrition-v2/F2_PARSER_V2.md` (§5 y §7).
 */
object GrammarV2 {

    private val OTHER_WORDS = setOf("otra", "otro", "otras", "otros", "other")
    private val VAGUE_WORDS = setOf("poco", "poca", "pocos", "pocas", "poquito", "poquita", "algo", "bit", "some")
    private val CUT_WORDS = setOf("despues", "luego", "then")
    private val DOZEN_WORDS = setOf("docena", "docenas", "dozen", "dozens")

    /** Palabras que delatan, en el nombre del alimento excluido, una forma de la base («sin azúcar» = forma). */
    private val FORM_KEYWORDS: Map<FoodFormV2, Set<String>> = mapOf(
        FoodFormV2.SUGAR_FREE to setOf("azucar", "azucares", "sugar", "sugars", "edulcorante"),
        FoodFormV2.LIGHT to setOf("grasa", "grasas", "fat", "fats"),
    )

    /**
     * Borradores de mención de [tokens] según [spans] (= `LexiconV2.tag(tokens)`). [catalog] solo se consulta para
     * decidir si un «sin X» es en realidad una forma de la base. Texto sin alimentos → lista vacía.
     */
    fun parse(tokens: List<TokenV2>, spans: List<TaggedSpan>, catalog: ConceptCatalogV2? = null): List<MentionDraftV2> {
        if (tokens.isEmpty() || spans.isEmpty()) return emptyList()
        val items = spans
            .filter { it.first in tokens.indices && it.last in tokens.indices && it.first <= it.last }
            .mapNotNull { classify(it, tokens) }
        return Parser(tokens, catalog).run(items)
    }

    // ─── Elementos ──────────────────────────────────────────────────────

    /** El orden importa: con varias etiquetas en un tramo gana la de menor posición (hasta [FILLER]). */
    private enum class Kind {
        MEDIUM, FORM, STATE, HOUSEHOLD, CONTAINER, MASS, VOLUME, SIZE, NUM, NEG, CORR, AND, WITH, PREP, COMMA, FILLER,
        FOOD, VAGUE, OTHER, UNKNOWN, CUT,

        /** Frase de cantidad ya armada (número + unidad, «un poco»…). */
        Q,
    }

    /** Cantidad armada: [weak] = número débil suelto («un»); [bare] = conteo sin unidad (hereda unidad al corregir). */
    private class Qd(val quantity: QuantityV2, val weak: Boolean, val bare: Boolean)

    private class Item(
        val kind: Kind,
        val first: Int,
        val last: Int,
        val tag: LexTag? = null,
        val ids: List<String> = emptyList(),
        val fuzzy: Boolean = false,
        val q: Qd? = null,
        val word: String = "",
    )

    private class Parsed(val item: Item, val next: Int)

    private class Builder(val index: Int, val food: Item) {
        var quantity: QuantityV2? = null
        var state: FoodStateV2? = null
        var form: FoodFormV2? = null
        var medium: MediumV2? = null
        var size: SizeV2? = null
        var excluded = false
        var attachedTo: Int? = null

        /** Rangos de tokens usados (alimento, cantidad, modificadores). */
        val used = ArrayList<IntRange>().also { it += food.first..food.last }

        fun toDraft(tokens: List<TokenV2>): MentionDraftV2 {
            val sized = when (val q = quantity) {
                is QuantityV2.Count -> if (q.size == null && size != null) q.copy(size = size) else q
                is QuantityV2.Household -> if (q.size == null && size != null) q.copy(size = size) else q
                else -> q
            }
            return MentionDraftV2(
                start = used.minOf { tokens[it.first].start },
                end = used.maxOf { tokens[it.last].end },
                foodStart = tokens[food.first].start,
                foodEnd = tokens[food.last].end,
                conceptIds = food.ids,
                quantity = sized,
                state = state,
                form = form,
                medium = medium,
                excluded = excluded,
                fuzzy = food.fuzzy,
                attachedTo = attachedTo,
            )
        }
    }

    // ─── Clasificación de tramos ────────────────────────────────────────

    private fun kindOf(tag: LexTag): Kind? = when (tag) {
        is LexTag.Medium -> Kind.MEDIUM
        is LexTag.Form -> Kind.FORM
        is LexTag.State -> Kind.STATE
        is LexTag.Household -> Kind.HOUSEHOLD
        is LexTag.Container -> Kind.CONTAINER
        is LexTag.MassUnit -> Kind.MASS
        is LexTag.VolumeUnit -> Kind.VOLUME
        is LexTag.Size -> Kind.SIZE
        is LexTag.Number -> Kind.NUM
        is LexTag.Negation -> Kind.NEG
        is LexTag.Correction -> Kind.CORR
        is LexTag.Connector -> if (tag.kind == ConnectorKind.AND) Kind.AND else Kind.WITH
        is LexTag.Preposition -> Kind.PREP
        is LexTag.Punct -> if (tag.symbol == "," || tag.symbol == ";" || tag.symbol == ".") Kind.COMMA else Kind.FILLER
        is LexTag.Filler -> Kind.FILLER
        is LexTag.Food -> null
    }

    /** Elemento de un tramo, o null si se ignora (relleno, paréntesis, dos puntos). */
    private fun classify(span: TaggedSpan, tokens: List<TokenV2>): Item? {
        val first = span.first
        val last = span.last
        val word = tokens[first].norm
        val foods = span.tags.filterIsInstance<LexTag.Food>()
        if (first == last && (foods.isEmpty() || span.fuzzy) && tokens[first].isWord) {
            when {
                word in OTHER_WORDS -> return Item(Kind.OTHER, first, last, word = word)
                word in VAGUE_WORDS -> return Item(Kind.VAGUE, first, last, word = word)
                word in CUT_WORDS && span.tags.isEmpty() -> return Item(Kind.CUT, first, last, word = word)
            }
        }
        if (foods.isNotEmpty()) {
            return Item(Kind.FOOD, first, last, ids = foods.map { it.conceptId }.distinct(), fuzzy = span.fuzzy, word = word)
        }
        if (span.tags.isEmpty()) return Item(Kind.UNKNOWN, first, last, word = word)
        val best = span.tags.mapNotNull { tag -> kindOf(tag)?.let { it to tag } }.minByOrNull { it.first.ordinal }
            ?: return Item(Kind.UNKNOWN, first, last, word = word)
        if (best.first == Kind.FILLER) return null
        return Item(best.first, first, last, tag = best.second, word = word)
    }

    // ─── Analizador ─────────────────────────────────────────────────────

    private class Parser(val tokens: List<TokenV2>, val catalog: ConceptCatalogV2?) {
        val out = ArrayList<Builder>()

        /** Última mención base (no colgada ni excluida): objetivo de distribución y de correcciones. */
        var lastBase: Int? = null
        var pendingCorrection = false

        fun run(items: List<Item>): List<MentionDraftV2> {
            for (clause in splitClauses(items)) processClause(clause)
            return out.map { it.toDraft(tokens) }
        }

        // ─── Cláusulas ──────────────────────────────────────────────────

        private fun splitClauses(items: List<Item>): List<List<Item>> {
            val clauses = ArrayList<List<Item>>()
            var current = ArrayList<Item>()
            var chain = false
            fun flush() {
                if (current.isNotEmpty()) clauses += current
                current = ArrayList()
                chain = false
            }
            for ((k, item) in items.withIndex()) {
                when (item.kind) {
                    Kind.COMMA, Kind.CUT -> flush()
                    Kind.AND -> if (opensMention(items, k, chain)) flush() else current.add(item)
                    else -> {
                        if (item.kind == Kind.WITH) chain = true
                        current.add(item)
                    }
                }
            }
            flush()
            return clauses
        }

        /** «y» abre mención nueva si le sigue (saltando desconocidos y modificadores) una cantidad o un alimento. */
        private fun opensMention(items: List<Item>, and: Int, chain: Boolean): Boolean {
            var j = and + 1
            while (j < items.size) {
                when (items[j].kind) {
                    Kind.UNKNOWN, Kind.OTHER, Kind.SIZE, Kind.STATE, Kind.FORM -> j++
                    Kind.NUM, Kind.HOUSEHOLD, Kind.CONTAINER, Kind.VAGUE -> return true
                    Kind.FOOD -> return !chain
                    else -> return false
                }
            }
            return false
        }

        // ─── Cantidades ─────────────────────────────────────────────────

        private fun quantityItem(first: Int, last: Int, q: Qd) = Item(Kind.Q, first, last, q = q)

        private fun isUnit(item: Item?): Boolean = when (item?.kind) {
            Kind.MASS, Kind.VOLUME, Kind.HOUSEHOLD, Kind.CONTAINER -> true
            else -> false
        }

        /** Sustituye cada frase de cantidad («2 tazas», «medio kilo», «un poco») por un único elemento [Kind.Q]. */
        private fun parseQuantities(items: List<Item>): List<Item> {
            val out = ArrayList<Item>(items.size)
            var i = 0
            while (i < items.size) {
                val item = items[i]
                val parsed: Parsed? = when (item.kind) {
                    Kind.NUM -> parseNumber(items, i)
                    Kind.VAGUE -> Parsed(quantityItem(item.first, item.last, Qd(QuantityV2.Vague, weak = false, bare = false)), i + 1)
                    Kind.HOUSEHOLD, Kind.CONTAINER -> parseBareUnit(items, i)
                    else -> null
                }
                if (parsed == null) {
                    out += item
                    i++
                } else {
                    out += parsed.item
                    i = parsed.next
                }
            }
            return out
        }

        /** «a little of X»: «little» es vago si sigue «of» o un alimento que el catálogo no da por contable. */
        private fun isLittleVague(items: List<Item>, j: Int): Boolean {
            val little = items.getOrNull(j) ?: return false
            if (little.kind != Kind.SIZE || little.word != "little") return false
            val after = items.getOrNull(j + 1) ?: return false
            return when (after.kind) {
                Kind.PREP -> true
                Kind.FOOD -> catalog == null || after.ids.none { catalog.concept(it)?.countable == true }
                else -> false
            }
        }

        private fun parseNumber(items: List<Item>, i: Int): Parsed {
            val head = items[i]
            val number = head.tag as LexTag.Number
            var value = number.value
            var weak = number.weak
            var j = i + 1
            if (weak) {
                val next = items.getOrNull(j)
                if (next != null && (next.kind == Kind.VAGUE || isLittleVague(items, j))) {
                    return Parsed(quantityItem(head.first, next.last, Qd(QuantityV2.Vague, weak = false, bare = false)), j + 1)
                }
            }
            while (true) {
                val next = items.getOrNull(j)
                if (next == null || next.kind != Kind.NUM) break
                val other = next.tag as LexTag.Number
                if (next.word in DOZEN_WORDS) {
                    // «2 docenas».
                    value *= other.value
                    weak = false
                } else if (weak && !other.weak) {
                    // «un par», «una docena»: el artículo se descarta.
                    value = other.value
                    weak = false
                } else if (!weak && !other.weak && value >= 1.0 && other.value > 0.0 && other.value < 1.0) {
                    // «1 1/2».
                    value += other.value
                } else {
                    break
                }
                j++
            }
            var k = j
            // «un cuarto de kilo», «tres cuartos de taza».
            if (value < 1.0 && items.getOrNull(k)?.kind == Kind.PREP && isUnit(items.getOrNull(k + 1))) k++
            val unitAt = items.getOrNull(k)
            if (unitAt != null && (isUnit(unitAt) || unitAt.kind == Kind.SIZE)) {
                parseUnitAt(items, k, value, head.first)?.let { return it }
            }
            return Parsed(quantityItem(head.first, items[j - 1].last, Qd(QuantityV2.Count(value), weak, bare = true)), j)
        }

        /** «taza de café», «lata de atún»: medida o envase sin número valen 1 si les sigue «de» o un alimento. */
        private fun parseBareUnit(items: List<Item>, i: Int): Parsed? {
            val next = items.getOrNull(i + 1) ?: return null
            if (next.kind != Kind.PREP && next.kind != Kind.FOOD && next.kind != Kind.SIZE) return null
            return parseUnitAt(items, i, 1.0, items[i].first)
        }

        private fun parseUnitAt(items: List<Item>, k: Int, value: Double, startFirst: Int): Parsed? {
            var idx = k
            var size: SizeV2? = null
            var unit = items[idx]
            if (unit.kind == Kind.SIZE) {
                val next = items.getOrNull(idx + 1)
                if (next == null || (next.kind != Kind.HOUSEHOLD && next.kind != Kind.CONTAINER)) return null
                size = (unit.tag as LexTag.Size).size
                idx++
                unit = next
            }
            fun result(quantity: QuantityV2, end: Int) =
                Parsed(quantityItem(startFirst, items[end].last, Qd(quantity, weak = false, bare = false)), end + 1)
            when (unit.kind) {
                Kind.MASS -> return result(QuantityV2.Mass(value * (unit.tag as LexTag.MassUnit).grams), idx)
                Kind.VOLUME -> return result(QuantityV2.Volume(value * (unit.tag as LexTag.VolumeUnit).ml), idx)
                Kind.HOUSEHOLD -> {
                    val label = (unit.tag as LexTag.Household).label
                    var end = idx
                    val after = items.getOrNull(end + 1)
                    if (after?.kind == Kind.SIZE && size == null) {
                        size = (after.tag as LexTag.Size).size
                        end++
                    }
                    // «unidad» es un conteo, no una medida casera.
                    return result(if (label == "unidad") QuantityV2.Count(value, size) else QuantityV2.Household(value, label, size), end)
                }
                Kind.CONTAINER -> {
                    val label = (unit.tag as LexTag.Container).label
                    var end = idx
                    if (items.getOrNull(end + 1)?.kind == Kind.SIZE) end++
                    var netGrams: Double? = null
                    var netMl: Double? = null
                    val of = items.getOrNull(end + 1)
                    val amount = items.getOrNull(end + 2)
                    val net = items.getOrNull(end + 3)
                    if (of != null && amount != null && net != null && of.kind == Kind.PREP && amount.kind == Kind.NUM &&
                        (net.kind == Kind.MASS || net.kind == Kind.VOLUME)
                    ) {
                        val n = (amount.tag as LexTag.Number).value
                        if (net.kind == Kind.MASS) netGrams = n * (net.tag as LexTag.MassUnit).grams
                        else netMl = n * (net.tag as LexTag.VolumeUnit).ml
                        end += 3
                    }
                    return result(QuantityV2.Container(value, label, netGrams, netMl), end)
                }
                else -> return null
            }
        }

        // ─── Una cláusula ───────────────────────────────────────────────

        private fun processClause(raw: List<Item>) {
            val elems = parseQuantities(raw)
            val foodAt = elems.indices.filter { elems[it].kind == Kind.FOOD }
            if (foodAt.isEmpty()) {
                applyCorrection(elems)
                return
            }
            pendingCorrection = false

            // Alimentos: exclusión («sin X») y, si el catálogo lo dice, forma de la base en lugar de exclusión.
            val builders = HashMap<Int, Builder>()
            var previousKept = -1
            for (f in foodAt) {
                val neg = negationBefore(elems, f)
                if (neg >= 0 && previousKept >= 0) {
                    val hint = formHint(elems[previousKept].ids, elems[f].ids)
                    if (hint != null) {
                        val base = builders.getValue(previousKept)
                        if (base.form == null) base.form = hint
                        base.used += elems[neg].first..elems[f].last
                        continue
                    }
                }
                val builder = Builder(out.size, elems[f])
                out += builder
                builders[f] = builder
                if (neg >= 0) {
                    builder.excluded = true
                    builder.used += elems[neg].first..elems[neg].last
                } else {
                    previousKept = f
                }
            }
            val ordered = foodAt.mapNotNull { f -> builders[f]?.let { f to it } }

            val claimed = HashSet<Item>()
            // Cantidades de masa/volumen/envase con contenido que cuelgan de «ALIM de …»: no son de un alimento posterior.
            val locked = HashSet<Item>()
            for ((p, e) in elems.withIndex()) {
                if (e.kind == Kind.Q && p >= 2 && elems[p - 1].kind == Kind.PREP && elems[p - 2].kind == Kind.FOOD &&
                    isSuffixShaped(e.q!!.quantity)
                ) {
                    locked += e
                }
            }

            // 1. Cantidad delante del alimento.
            for ((idx, b) in ordered) {
                if (b.excluded) continue
                val e = prefixQuantity(elems, idx, claimed, locked) ?: continue
                claimed += e
                b.quantity = e.q!!.quantity
                b.used += e.first..e.last
            }

            // 2. Cantidad detrás del alimento («pollo 150 g», «yogur de 125 g», «arroz 2 tazas»).
            for ((idx, b) in ordered) {
                if (b.excluded) continue
                suffixQuantity(elems, idx, b, claimed)
            }

            // 3. Modificadores al alimento más cercano.
            val pool = ordered.map { it.second }.filter { !it.excluded }.ifEmpty { ordered.map { it.second } }
            for (m in elems) {
                if (m.kind != Kind.SIZE && m.kind != Kind.STATE && m.kind != Kind.FORM && m.kind != Kind.MEDIUM) continue
                var best: Builder? = null
                var bestDistance = Int.MAX_VALUE
                for (b in pool) {
                    val distance = if (b.food.last < m.first) m.first - b.food.last else b.food.first - m.last
                    if (distance < bestDistance) {
                        best = b
                        bestDistance = distance
                    }
                }
                val target = best ?: continue
                when (val tag = m.tag) {
                    is LexTag.Size -> if (target.size == null) target.size = tag.size
                    is LexTag.State -> if (target.state == null) target.state = tag.state
                    is LexTag.Form -> if (target.form == null) target.form = tag.form
                    is LexTag.Medium -> if (target.medium == null) target.medium = tag.medium
                    else -> {}
                }
                target.used += m.first..m.last
            }

            // 4. Relaciones: «X con Y», «y Z» dentro de una cadena y distribución.
            var previousAny = -1
            var previous: Builder? = null
            for (idx in foodAt) {
                val b = builders[idx]
                if (b == null || b.excluded) {
                    previousAny = idx
                    continue
                }
                val gapStart = previousAny + 1
                var withAt = -1
                var hasAnd = false
                for (g in gapStart until idx) {
                    when (elems[g].kind) {
                        Kind.WITH -> withAt = g
                        Kind.AND -> hasAnd = true
                        else -> {}
                    }
                }
                val before = previous
                val target = if (withAt >= 0 && isMarker(elems, gapStart, withAt, claimed)) distributionTarget() else null
                when {
                    target != null -> b.attachedTo = target.index
                    withAt >= 0 && before != null -> b.attachedTo = before.attachedTo ?: before.index
                    withAt < 0 && hasAnd && before?.attachedTo != null -> b.attachedTo = before.attachedTo
                }
                if (b.attachedTo == null) lastBase = b.index
                previousAny = idx
                previous = b
            }
        }

        private fun isSuffixShaped(q: QuantityV2): Boolean =
            q is QuantityV2.Mass || q is QuantityV2.Volume ||
                (q is QuantityV2.Container && (q.netGrams != null || q.netMl != null))

        /** Posición del «sin»/«no» que precede al alimento [food] (saltando tamaños, estados y formas), o -1. */
        private fun negationBefore(elems: List<Item>, food: Int): Int {
            var p = food - 1
            while (p >= 0 && (elems[p].kind == Kind.SIZE || elems[p].kind == Kind.STATE || elems[p].kind == Kind.FORM)) p--
            return if (p >= 0 && elems[p].kind == Kind.NEG) p else -1
        }

        private fun formHint(baseIds: List<String>, excludedIds: List<String>): FoodFormV2? {
            val cat = catalog ?: return null
            for ((form, keywords) in FORM_KEYWORDS) {
                if (baseIds.none { cat.hasForm(it, form) }) continue
                if (excludedIds.any { id -> conceptWords(cat, id).any { it in keywords } }) return form
            }
            return null
        }

        private fun conceptWords(cat: ConceptCatalogV2, id: String): Set<String> = buildSet {
            addAll(id.lowercase().split('_', ' ', '-'))
            cat.concept(id)?.name?.let { name -> NormalizerV2.tokenize(name).forEach { add(it.norm) } }
        }

        /** Cantidad libre más cercana a la izquierda de [food]: hasta 2 desconocidos, 1 preposición y modificadores. */
        private fun prefixQuantity(elems: List<Item>, food: Int, claimed: Set<Item>, locked: Set<Item>): Item? {
            var p = food - 1
            var unknown = 0
            var preps = 0
            while (p >= 0) {
                val e = elems[p]
                when (e.kind) {
                    Kind.SIZE, Kind.STATE, Kind.FORM -> {}
                    Kind.UNKNOWN, Kind.OTHER -> {
                        unknown++
                        if (unknown > 2) return null
                    }
                    Kind.PREP -> {
                        preps++
                        if (preps > 1) return null
                    }
                    Kind.Q -> {
                        val q = e.q!!
                        if (e in claimed || e in locked) return null
                        // Un artículo suelto («un») solo cuenta si va pegado al alimento.
                        if (q.weak && (unknown > 0 || preps > 0)) return null
                        return e
                    }
                    else -> return null
                }
                p--
            }
            return null
        }

        /** Cantidad con unidad (o contenido neto) justo detrás de [b], tras modificadores y una preposición. */
        private fun suffixQuantity(elems: List<Item>, food: Int, b: Builder, claimed: MutableSet<Item>) {
            var r = food + 1
            while (r < elems.size && (elems[r].kind == Kind.SIZE || elems[r].kind == Kind.STATE ||
                    elems[r].kind == Kind.FORM || elems[r].kind == Kind.MEDIUM)
            ) {
                r++
            }
            var hasPrep = false
            if (r < elems.size && elems[r].kind == Kind.PREP) {
                hasPrep = true
                r++
            }
            val e = elems.getOrNull(r) ?: return
            if (e.kind != Kind.Q || e in claimed) return
            val qd = e.q!!
            val current = b.quantity
            val merged: QuantityV2? = when (val q = qd.quantity) {
                is QuantityV2.Mass -> when {
                    current == null -> q
                    current is QuantityV2.Count && hasPrep -> QuantityV2.Mass(current.n * q.grams)
                    current is QuantityV2.Container && hasPrep && current.netGrams == null && current.netMl == null ->
                        current.copy(netGrams = q.grams)
                    else -> null
                }
                is QuantityV2.Volume -> when {
                    current == null -> q
                    current is QuantityV2.Count && hasPrep -> QuantityV2.Volume(current.n * q.ml)
                    current is QuantityV2.Container && hasPrep && current.netGrams == null && current.netMl == null ->
                        current.copy(netMl = q.ml)
                    else -> null
                }
                is QuantityV2.Container -> when {
                    (q.netGrams != null || q.netMl != null) && current is QuantityV2.Count ->
                        QuantityV2.Container(current.n, q.label, q.netGrams, q.netMl)
                    current == null -> q
                    else -> null
                }
                is QuantityV2.Household -> if (current == null) q else null
                is QuantityV2.Count -> if (current == null && !qd.bare) q else null
                else -> null
            }
            if (merged != null) {
                b.quantity = merged
                claimed += e
                b.used += e.first..e.last
            }
        }

        /** Marca de distribución justo antes de «con»: artículo débil suelto («una con…») u «otra/otro/the other». */
        private fun isMarker(elems: List<Item>, gapStart: Int, withAt: Int, claimed: Set<Item>): Boolean {
            val before = withAt - 1
            if (before < gapStart) return false
            val e = elems[before]
            return e.kind == Kind.OTHER || (e.kind == Kind.Q && e.q!!.weak && e !in claimed)
        }

        /** Última mención base si es un conteo de 2 o más (lo que se puede repartir). */
        private fun distributionTarget(): Builder? {
            val base = lastBase?.let { out[it] } ?: return null
            val count = base.quantity as? QuantityV2.Count ?: return null
            return if (count.n >= 2.0) base else null
        }

        // ─── Correcciones ───────────────────────────────────────────────

        /**
         * Cláusula sin alimentos: si lleva una marca de corrección (o la cláusula anterior era solo la marca), la
         * cantidad y los modificadores que dice sustituyen los de la última mención base. «no N» suelto se ignora.
         */
        private fun applyCorrection(elems: List<Item>) {
            val corr = elems.indexOfFirst { it.kind == Kind.CORR }
            if (corr < 0 && !pendingCorrection) return
            val from = if (corr >= 0) corr + 1 else 0
            val target = (lastBase ?: out.lastIndex.takeIf { it >= 0 })?.let { out[it] }
            var applied = false
            if (target != null) {
                val tail = elems.subList(from, elems.size)
                val q = tail.firstOrNull { it.kind == Kind.Q }
                if (q != null) {
                    target.quantity = inherit(target.quantity, q.q!!)
                    applied = true
                }
                for (m in tail) {
                    when (val tag = m.tag) {
                        is LexTag.State -> { target.state = tag.state; applied = true }
                        is LexTag.Form -> { target.form = tag.form; applied = true }
                        is LexTag.Medium -> { target.medium = tag.medium; applied = true }
                        else -> {}
                    }
                }
            }
            pendingCorrection = corr >= 0 && !applied
        }

        /** Un número desnudo («perdón, 150») hereda la unidad de la cantidad anterior. */
        private fun inherit(previous: QuantityV2?, q: Qd): QuantityV2 {
            if (!q.bare) return q.quantity
            val value = (q.quantity as QuantityV2.Count).n
            return when (previous) {
                is QuantityV2.Mass -> QuantityV2.Mass(value)
                is QuantityV2.Volume -> QuantityV2.Volume(value)
                is QuantityV2.Household -> previous.copy(n = value)
                is QuantityV2.Container -> QuantityV2.Container(value, previous.label)
                is QuantityV2.Count -> previous.copy(n = value)
                else -> q.quantity
            }
        }
    }
}
