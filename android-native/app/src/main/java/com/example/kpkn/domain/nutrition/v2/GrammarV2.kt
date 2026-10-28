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

    /**
     * Palabras sueltas, desconocidas para el léxico, que niegan lo que sigue como «sin»: «ni palta ni mayo», «me salté el
     * pan», «I skipped the fries», «didn't have dessert» (el apóstrofo se descarta: «didn» + «t»).
     */
    private val NEGATION_WORDS = setOf(
        "ni", "salte", "saltamos", "saltaron", "omiti", "skipped", "skip", "didn", "don", "doesn", "haven", "hadn", "wasn",
    )

    private val POSSESSIVES = setOf("mi", "su", "tu")

    private const val TINTO_WINE_ID = "vino_tinto"
    private const val TINTO_COFFEE_ID = "cafe_negro"

    /** Comidas y platos típicamente colombianos (sin tildes): delatan que «un tinto» es el café. */
    private val COLOMBIAN_CUES = setOf(
        "arepa", "arepas", "pandebono", "pandebonos", "bunuelo", "bunuelos", "almojabana", "almojabanas", "pericos",
        "perico", "calentado", "changua", "aguapanela", "santafereno", "ajiaco", "mondongo",
    )

    /** Mayor número desnudo que se lee como conteo tras el alimento («huevos 2»); más arriba suelen ser gramos. */
    private const val MAX_BARE_COUNT = 30.0

    /** Palabras sueltas que preceden a una cantidad dicha aparte: «galletas, como 4», «tacos, serían 3». */
    private val LOOSE_LEAD_WORDS = setOf("como", "tipo", "casi", "serian", "fueron", "fue", "son", "about", "like", "around")

    /** Interjecciones que acompañan a un «no» de corrección: «ah no», «eh, no». */
    private val INTERJECTIONS = setOf("ah", "eh", "ay", "uy", "em", "mm", "ahh")

    /** Frutas que se usan de acompañamiento o aliño (no se comen aparte): primer tramo del id del concepto. */
    private val TOPPING_FRUITS = setOf("palta", "aguacate", "limon", "lima", "aceituna", "aceitunas", "coco")

    /** Palabras que se saltan entre la negación y el alimento negado («sin el pan», «didn't have any dessert»). */
    private val NEGATION_BRIDGE = setOf(
        "el", "la", "los", "las", "mi", "mis", "su", "sus", "tu", "nada", "ningun", "ninguna", "ninguno", "t", "have", "had",
        "eat", "ate", "any", "the", "my", "a", "an", "some", "tomar", "comer", "tener", "tuve", "tuvo", "hubo", "habia",
        "fue", "fueron", "era", "eran", "es", "son", "sea", "hay",
    )

    /** Palabras sueltas que corrigen lo dicho antes («bueno, dos empanadas», «3 galletas mejor», «no, mentira»). */
    private val CORRECTION_WORDS = setOf("bueno", "mentira", "mejor", "ups")

    /** Verbos de intención: «iba a pedir…», «pensaba comprar…» (lo dicho a continuación no se comió). */
    private val INTENT_WORDS = setOf(
        "iba", "ibamos", "iban", "pensaba", "pensabamos", "pensaban", "pense", "pensamos", "pensado", "queria", "querian",
        "queriamos", "quisimos", "planeaba", "planeabamos", "hubiera", "hubiese", "habria", "hubieramos", "wanted", "planned",
        "intended", "considered", "thought", "going", "propuso", "sugirio", "recomendo", "proposed", "suggested",
    )

    /** Acciones de pedir, comprar o preparar que siguen a un verbo de intención. */
    private val INTENT_ACTIONS = setOf(
        "pedir", "pedirme", "pedirnos", "pedirse", "pedido", "pedirle", "comprar", "comprarme", "comprarnos", "comprarle",
        "comprado", "comer", "comerme", "comernos", "comerlo", "comido", "tomar", "tomarme", "tomarnos", "tomado", "hacer",
        "hacerme", "hacernos", "hacerse", "preparar", "prepararme", "prepararnos", "agregar", "agregarle", "anadir", "ordenar",
        "probar", "servirme", "cocinar", "order", "ordering", "buy", "buying", "get", "have", "eat", "make", "cook", "try",
        "drink", "grab", "asar", "asarlo", "cocinarlo", "prepararlo", "freir", "freirlo", "pedirlo", "comprarlo", "comerlo",
    )

    /** Palabras de contraste: lo que sigue ya no es lo que no se hizo («iba a pedir pizza pero me comí una ensalada»). */
    private val CONTRAST_WORDS = setOf("pero", "but", "aunque", "sino", "although", "though", "entonces")

    /** Terceras personas que pueden comer o pedir otra cosa que la persona: «mi primo pidió…», «los niños comieron…». */
    private val OTHER_PERSON_WORDS = setOf(
        "mama", "papa", "madre", "padre", "hermano", "hermana", "primo", "prima", "tio", "tia", "amigo", "amiga", "socio",
        "socia", "esposa", "esposo", "marido", "mujer", "novio", "novia", "hijo", "hija", "hijos", "ninos", "ninas", "chicos",
        "companero", "companera", "suegra", "suegro", "abuela", "abuelo", "vecina", "vecino", "jefe", "ella", "ellos",
        "ellas", "mum", "mom", "dad", "wife", "husband", "friend", "brother", "sister", "kids", "colleague", "cousin",
        "boss",
    )

    /** Verbos de comer o pedir en tercera persona (pasado). */
    private val OTHER_PERSON_VERBS = setOf(
        "pidio", "pidieron", "ordeno", "ordenaron", "comio", "comieron", "tomo", "tomaron", "probo", "probaron", "ate",
        "ordered", "drank", "tried", "devoro", "devoraron", "quiso", "wanted",
    )

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
        val items = colombianTinto(tokens, spans, catalog)
            .filter { it.first in tokens.indices && it.last in tokens.indices && it.first <= it.last }
            .mapNotNull { classify(it, tokens) }
        return Parser(tokens, catalog).run(items)
    }

    /**
     * «un tinto» suelto es un café negro en Colombia y vino tinto en el resto. El texto no trae el país, pero lo delatan
     * las comidas colombianas que lo acompañan («arepa con queso y un tinto», «un tinto con pan de bono»): con alguna de
     * ellas, el tramo «tinto» (una sola palabra; «vino tinto» y «tinto de verano» son tramos más largos) pasa al café negro.
     */
    private fun colombianTinto(tokens: List<TokenV2>, spans: List<TaggedSpan>, catalog: ConceptCatalogV2?): List<TaggedSpan> {
        val coffee = catalog?.concept(TINTO_COFFEE_ID) ?: return spans
        val isTinto = { s: TaggedSpan ->
            s.first == s.last && tokens.getOrNull(s.first)?.norm == "tinto" &&
                s.tags.any { it is LexTag.Food && it.conceptId == TINTO_WINE_ID }
        }
        if (spans.none(isTinto)) return spans
        val norms = tokens.map { it.norm }
        val colombian = norms.any { it in COLOMBIAN_CUES } ||
            (0 until norms.size - 2).any { norms[it] == "pan" && norms[it + 1] == "de" && norms[it + 2] == "bono" }
        if (!colombian) return spans
        return spans.map { s ->
            if (isTinto(s)) TaggedSpan(s.first, s.last, listOf(LexTag.Food(coffee.id, AliasKind.ES)), s.fuzzy) else s
        }
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

    private class Builder(val index: Int, var food: Item) {
        var quantity: QuantityV2? = null
        var state: FoodStateV2? = null
        var form: FoodFormV2? = null
        var medium: MediumV2? = null
        var size: SizeV2? = null
        var excluded = false
        var attachedTo: Int? = null

        /** La corrección «bueno, dos empanadas» sustituyó esta mención por otra del mismo alimento. */
        var dropped = false

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
        // «mi papá» (el padre) no es «papa» (el tubérculo): sin tilde son la misma palabra, pero el posesivo delata a la persona.
        if (first == last && word == "papa" && first > 0 && tokens[first - 1].norm in POSSESSIVES) {
            return Item(Kind.UNKNOWN, first, last, word = word)
        }
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
        if (span.tags.isEmpty()) {
            if (first == last && word in NEGATION_WORDS) return Item(Kind.NEG, first, last, tag = LexTag.Negation, word = word)
            if (first == last && word in CORRECTION_WORDS) return Item(Kind.CORR, first, last, tag = LexTag.Correction, word = word)
            return Item(Kind.UNKNOWN, first, last, word = word)
        }
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

        /** Número de la cláusula en curso y de la que fijó [lastBase]: una cantidad suelta solo se pega a la cláusula anterior. */
        var clauseNo = 0
        var lastBaseClause = -1

        /** Cuántas menciones base (no colgadas) tenía la cláusula de [lastBase]; una cantidad suelta solo vale si era una. */
        var lastBaseCount = 0

        /** Separador que abrió cada cláusula («,», «;», «.», «and», «cut»; vacío en la primera), en el orden de [splitClauses]. */
        val clauseSeps = ArrayList<String>()
        var currentSep = ""

        fun run(items: List<Item>): List<MentionDraftV2> {
            for ((i, clause) in splitClauses(items).withIndex()) {
                clauseNo++
                currentSep = clauseSeps.getOrElse(i) { "" }
                processClause(clause)
            }
            if (out.none { it.dropped }) return out.map { it.toDraft(tokens) }
            val newIndex = HashMap<Int, Int>()
            for (b in out) if (!b.dropped) newIndex[b.index] = newIndex.size
            return out.filter { !it.dropped }.map { b ->
                val d = b.toDraft(tokens)
                d.copy(attachedTo = b.attachedTo?.let { newIndex[it] })
            }
        }

        // ─── Cláusulas ──────────────────────────────────────────────────

        private fun splitClauses(items: List<Item>): List<List<Item>> {
            val clauses = ArrayList<List<Item>>()
            clauseSeps.clear()
            var current = ArrayList<Item>()
            var chain = false
            var openedBy = ""
            fun flush(next: String) {
                if (current.isNotEmpty()) {
                    clauses += current
                    clauseSeps += openedBy
                }
                current = ArrayList()
                chain = false
                openedBy = next
            }
            for ((k, item) in items.withIndex()) {
                when (item.kind) {
                    Kind.COMMA -> flush((item.tag as? LexTag.Punct)?.symbol ?: ",")
                    Kind.CUT -> flush("cut")
                    Kind.AND -> if (opensMention(items, k, chain)) flush("and") else current.add(item)
                    else -> {
                        if (item.kind == Kind.WITH) chain = true
                        current.add(item)
                    }
                }
            }
            flush("")
            return clauses
        }

        /** «y» abre mención nueva si le sigue (saltando desconocidos y modificadores) una cantidad o un alimento. */
        private fun opensMention(items: List<Item>, and: Int, chain: Boolean): Boolean {
            var j = and + 1
            while (j < items.size) {
                when (items[j].kind) {
                    Kind.UNKNOWN, Kind.OTHER, Kind.SIZE, Kind.STATE, Kind.FORM, Kind.CORR, Kind.NEG -> j++
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
                if (!applyCorrection(elems)) attachLooseQuantity(elems)
                return
            }
            val corrected = pendingCorrection || elems.any { it.kind == Kind.CORR }
            pendingCorrection = false

            // Alimentos: exclusión («sin X») y, si el catálogo lo dice, forma de la base en lugar de exclusión.
            val builders = HashMap<Int, Builder>()
            var previousKept = -1
            val unreal = unrealFoods(elems)
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
                if (neg >= 0 || f in unreal) {
                    builder.excluded = true
                    if (neg >= 0) builder.used += elems[neg].first..elems[neg].last
                } else {
                    previousKept = f
                }
            }
            val ordered = foodAt.mapNotNull { f -> builders[f]?.let { f to it } }
            if (corrected) replaceCorrected(ordered.map { it.second })

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
            var basesHere = 0
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
                    withAt < 0 && hasAnd && before?.attachedTo != null && !standsAlone(b) -> b.attachedTo = before.attachedTo
                }
                if (b.attachedTo == null) {
                    lastBase = b.index
                    lastBaseClause = clauseNo
                    basesHere++
                    lastBaseCount = basesHere
                }
                previousAny = idx
                previous = b
            }
        }

        /**
         * Tercer elemento de «X con Y y Z»: una fruta o una bebida sin cantidad («avena con leche y plátano») se come aparte,
         * no es otro ingrediente del plato. Sin catálogo no se sabe y se deja como ingrediente.
         */
        private fun standsAlone(b: Builder): Boolean {
            val cat = catalog ?: return false
            return b.food.ids.isNotEmpty() && b.food.ids.all { id ->
                val c = cat.concept(id)
                c != null && (
                    c.kind == ConceptKindV2.DRINK || c.category == "BEBIDAS" || c.category == "ALCOHOL" ||
                        (c.category == "FRUTAS" && c.id.substringBefore('_') !in TOPPING_FRUITS)
                    )
            }
        }

        /**
         * Una marca de corrección delante de (o en) una cláusula que repite el alimento de la última mención base la
         * sustituye: «me comí una empanada y bueno dos empanadas» deja solo las dos; «no fueron tallarines, fueron
         * ravioles» retira también los tallarines (el excluido de la cláusula repite el alimento de la base).
         */
        private fun replaceCorrected(clause: List<Builder>) {
            val prev = lastBase?.let { out[it] } ?: return
            if (prev.dropped) return
            if (clause.none { b -> prev.food.ids.any { it in b.food.ids } }) {
                // «2 galletas soda, 3 galletas mejor»: la corrección dice el alimento más corto y es el mismo de antes.
                val generic = clause.firstOrNull { b -> !b.excluded && isShorterRepeat(b.food, prev.food) } ?: return
                val f = generic.food
                generic.food = Item(f.kind, f.first, f.last, f.tag, prev.food.ids, f.fuzzy, f.q, f.word)
            }
            prev.dropped = true
            for (b in out) if (b.attachedTo == prev.index) b.dropped = true
            lastBase = null
        }

        /** Las palabras de [short] (sin plural) son un subconjunto propio de las de [long]: «galletas» frente a «galletas soda». */
        private fun isShorterRepeat(short: Item, long: Item): Boolean {
            fun words(item: Item) = (item.first..item.last).map { tokens[it].norm.removeSuffix("es").removeSuffix("s") }.toSet()
            val a = words(short)
            val b = words(long)
            return a.size < b.size && b.containsAll(a)
        }

        private fun isSuffixShaped(q: QuantityV2): Boolean =
            q is QuantityV2.Mass || q is QuantityV2.Volume ||
                (q is QuantityV2.Container && (q.netGrams != null || q.netMl != null))

        /**
         * Posición del «sin»/«no» que precede al alimento [food] (saltando tamaños, estados, formas, artículos y cantidades
         * débiles: «sin el pan», «sin un huevo», «didn't have any dessert»), o -1.
         */
        private fun negationBefore(elems: List<Item>, food: Int): Int {
            var p = food - 1
            while (p >= 0 && isNegationBridge(elems[p])) p--
            return if (p >= 0 && elems[p].kind == Kind.NEG) p else -1
        }

        private fun isNegationBridge(e: Item): Boolean = when (e.kind) {
            Kind.SIZE, Kind.STATE, Kind.FORM -> true
            Kind.Q -> e.q!!.weak
            Kind.NUM -> (e.tag as? LexTag.Number)?.weak == true
            Kind.UNKNOWN -> e.word in NEGATION_BRIDGE
            else -> false
        }

        private fun wordAt(e: Item): String? = if (e.first == e.last) tokens[e.first].norm else null

        /**
         * Alimentos de la cláusula que la persona no comió aunque no lleven «sin»: lo que sigue a un verbo de intención
         * («iba a pedir…», «pensaba comprar…», «se me antojó…» no) o a una tercera persona que come o pide
         * («mi primo pidió…»), hasta una palabra de contraste («pero…»). Devuelve las posiciones de esos alimentos.
         */
        private fun unrealFoods(elems: List<Item>): Set<Int> {
            val out = HashSet<Int>()
            var unreal = false
            for (p in elems.indices) {
                val e = elems[p]
                val w = wordAt(e)
                if (w != null && w in CONTRAST_WORDS) {
                    unreal = false
                    continue
                }
                if (!unreal && w != null && opensUnreal(elems, p, w)) unreal = true
                if (unreal && e.kind == Kind.FOOD) out += p
            }
            return out
        }

        private fun opensUnreal(elems: List<Item>, p: Int, w: String): Boolean {
            if (w in INTENT_WORDS) return actionWithin(elems, p + 1, 3)
            if ((w == "estaba" || w == "estabamos") && elems.getOrNull(p + 1)?.let { wordAt(it) } == "por") {
                return actionWithin(elems, p + 2, 2)
            }
            // «y él un churrasco»: «él» (= «el») seguido de una cantidad no es un artículo.
            if ((w == "el" || w == "ella") && elems.getOrNull(p + 1)?.let { it.kind == Kind.Q || it.kind == Kind.NUM } == true) {
                return true
            }
            if (w in OTHER_PERSON_WORDS && elems[p].kind != Kind.FOOD) {
                // «mi primo (se) pidió…»: sin «me/nos» de por medio (esos son de la persona).
                for (k in p + 1..minOf(p + 4, elems.lastIndex)) {
                    val x = wordAt(elems[k]) ?: continue
                    if (x == "me" || x == "nos") return false
                    if (x in OTHER_PERSON_VERBS) return true
                    if (elems[k].kind == Kind.FOOD) return false
                }
            }
            return false
        }

        private fun actionWithin(elems: List<Item>, from: Int, window: Int): Boolean {
            for (k in from..minOf(from + window - 1, elems.lastIndex)) {
                val x = wordAt(elems[k])
                if (x != null && x in INTENT_ACTIONS) return true
                if (elems[k].kind == Kind.FOOD) return false
            }
            return false
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
                // «huevos 2», «pan 1»: el número desnudo que sigue pegado al alimento contable es su cantidad.
                is QuantityV2.Count -> if (current == null && (!qd.bare || bareCountAfterFood(qd, q, b, hasPrep))) q else null
                else -> null
            }
            if (merged != null) {
                b.quantity = merged
                claimed += e
                b.used += e.first..e.last
            }
        }

        /** Número desnudo (no un artículo) de 1 a [MAX_BARE_COUNT], sin preposición de por medio, tras un alimento contable. */
        private fun bareCountAfterFood(qd: Qd, q: QuantityV2.Count, b: Builder, hasPrep: Boolean): Boolean =
            !qd.weak && !hasPrep && q.n >= 1.0 && q.n <= MAX_BARE_COUNT && countable(b.food.ids)

        /** Sin catálogo no se sabe y se acepta; con catálogo, algún concepto del tramo debe contarse por unidades. */
        private fun countable(ids: List<String>): Boolean =
            catalog == null || ids.any { catalog.concept(it)?.countable == true }

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
        private fun applyCorrection(elems: List<Item>): Boolean {
            val corr = elems.indexOfFirst { it.kind == Kind.CORR }
            if (corr < 0 && !pendingCorrection) {
                // «eran 150, no, 200»: un «no» solo, entre comas, anuncia que lo que sigue corrige lo anterior.
                if (lastBase != null && isLoneNo(elems)) pendingCorrection = true
                return false
            }
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
                        // «un trozo grande, no, uno mediano»: el tamaño nuevo cambia el de la cantidad.
                        is LexTag.Size -> {
                            when (val cur = target.quantity) {
                                is QuantityV2.Count -> target.quantity = cur.copy(size = tag.size)
                                is QuantityV2.Household -> target.quantity = cur.copy(size = tag.size)
                                else -> target.size = tag.size
                            }
                            applied = true
                        }
                        else -> {}
                    }
                }
            }
            pendingCorrection = corr >= 0 && !applied
            return true
        }

        /** Cláusula de un «no» solo (con a lo sumo una interjección): «eran 150, no, 200». */
        private fun isLoneNo(elems: List<Item>): Boolean =
            elems.isNotEmpty() && elems.any { it.kind == Kind.NEG && it.word == "no" } &&
                elems.all { (it.kind == Kind.NEG && it.word == "no") || (it.kind == Kind.UNKNOWN && it.word in INTERJECTIONS) }

        /**
         * Cláusula sin alimento ni corrección que solo trae una cantidad, justo después de la cláusula de una mención sin
         * cantidad: «pizza de pepperoni, 3 pedazos», «tacos al pastor con cebolla, cuatro», «galletas, como 4». También
         * «un tarro de leche condensada, la mitad del tarro»: la fracción de la misma medida reduce la cantidad dicha.
         */
        private fun attachLooseQuantity(elems: List<Item>) {
            val base = lastBase ?: return
            if (lastBaseClause != clauseNo - 1 || currentSep != "," || lastBaseCount != 1) return
            val target = out[base]
            if (target.dropped || target.excluded) return
            val rest = elems.filter { !(it.kind == Kind.UNKNOWN && it.word in LOOSE_LEAD_WORDS) }
            val q = rest.singleOrNull { it.kind == Kind.Q } ?: return
            if (rest.any { it.kind != Kind.Q && it.kind != Kind.SIZE }) return
            val qd = q.q!!
            val current = target.quantity
            val merged: QuantityV2? = when {
                current != null -> if (isHalfPhrase(q)) fractionOf(current, qd.quantity) else null
                qd.weak -> null
                else -> when (val x = qd.quantity) {
                    is QuantityV2.Count -> {
                        val plain = x.n >= 1.0 && x.n <= MAX_BARE_COUNT
                        val half = x.n > 0.0 && x.n < 1.0 && isHalfPhrase(q)
                        if (!qd.bare || ((plain || half) && countable(target.food.ids))) x else null
                    }
                    is QuantityV2.Household, is QuantityV2.Container, is QuantityV2.Mass, is QuantityV2.Volume -> x
                    else -> null
                }
            }
            if (merged == null) return
            target.quantity = merged
            target.used += q.first..q.last
            for (s in rest) {
                if (s.kind == Kind.SIZE && target.size == null) target.size = (s.tag as LexTag.Size).size
            }
        }

        /** ¿La frase de cantidad dice «la mitad», «mitad», «half»? */
        private fun isHalfPhrase(q: Item): Boolean =
            (q.first..q.last).any { tokens[it].norm == "mitad" || tokens[it].norm == "half" }

        /** Fracción [part] (menor que 1) de la misma medida que [current]; null si no son la misma medida. */
        private fun fractionOf(current: QuantityV2, part: QuantityV2): QuantityV2? = when {
            current is QuantityV2.Container && part is QuantityV2.Container && part.label == current.label && part.n < 1.0 ->
                current.copy(n = current.n * part.n)
            current is QuantityV2.Household && part is QuantityV2.Household && part.label == current.label && part.n < 1.0 ->
                current.copy(n = current.n * part.n)
            current is QuantityV2.Count && part is QuantityV2.Count && part.n < 1.0 -> current.copy(n = current.n * part.n)
            else -> null
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
