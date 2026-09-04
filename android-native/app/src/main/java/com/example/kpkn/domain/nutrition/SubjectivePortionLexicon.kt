package com.example.kpkn.domain.nutrition

/**
 * Data-driven Hispanic-America portion lexicon.
 *
 * Runtime source of truth lives here (pure Kotlin, unit-testable). The JSON
 * asset `food_data/subjective_portion_lexicon.json` mirrors the same terms.
 *
 * Rule: optional quantity × unit × food class. Bare plural ("láminas de queso")
 * is [UnitSpec.barePluralCount] units, never a dinner plate.
 */
object SubjectivePortionLexicon {

    const val CHEESE_SLICE_G = 20.0
    const val COLD_CUT_SLICE_G = 17.0
    const val BREAD_SLICE_G = 28.0
    const val DEFAULT_THIN_SLICE_G = 18.0
    const val BARE_PLURAL_COUNT = 2.0

    data class UnitSpec(
        val id: String,
        val defaultGrams: Double,
        val gramsByClass: Map<String, Double> = emptyMap(),
        val barePluralCount: Double = BARE_PLURAL_COUNT,
        val volumeMl: Double? = null,
    )

    data class TermSpec(
        val term: String,
        val unitId: String,
        val plural: Boolean = false,
    )

    val UNITS: Map<String, UnitSpec> = listOf(
        UnitSpec(
            id = "thin_slice",
            defaultGrams = DEFAULT_THIN_SLICE_G,
            gramsByClass = mapOf(
                "queso" to CHEESE_SLICE_G,
                "fiambre" to COLD_CUT_SLICE_G,
                "pan" to BREAD_SLICE_G,
                "fruta" to 25.0,
                "verdura" to 20.0,
            ),
        ),
        UnitSpec(
            id = "thick_slice",
            defaultGrams = 40.0,
            gramsByClass = mapOf(
                "queso" to 35.0,
                "fiambre" to 30.0,
                "pan" to 40.0,
                "dulce" to 70.0,
                "platano" to 90.0,
                "fruta" to 50.0,
            ),
        ),
        UnitSpec(
            id = "cube",
            defaultGrams = 12.0,
            gramsByClass = mapOf(
                "queso" to 10.0,
                "dulce" to 8.0,
                "fruta" to 12.0,
            ),
        ),
        UnitSpec(id = "chunk", defaultGrams = 50.0, gramsByClass = mapOf("queso" to 40.0, "dulce" to 60.0, "pan" to 45.0)),
        UnitSpec(id = "small_chunk", defaultGrams = 20.0, gramsByClass = mapOf("queso" to 15.0, "dulce" to 18.0)),
        UnitSpec(id = "wedge", defaultGrams = 40.0, gramsByClass = mapOf("queso" to 35.0, "fruta" to 40.0)),
        UnitSpec(id = "strip", defaultGrams = 15.0, gramsByClass = mapOf("fiambre" to 12.0, "queso" to 12.0)),
        UnitSpec(id = "cookie", defaultGrams = 12.0, gramsByClass = mapOf("snack" to 12.0, "dulce" to 12.0)),
        UnitSpec(id = "chip", defaultGrams = 2.0, gramsByClass = mapOf("snack" to 2.0)),
        UnitSpec(id = "snack_bag", defaultGrams = 30.0, gramsByClass = mapOf("snack" to 30.0)),
        UnitSpec(id = "handful", defaultGrams = 30.0, gramsByClass = mapOf("snack" to 25.0, "nuts" to 30.0)),
        UnitSpec("pinch", 0.5),
        UnitSpec("splash", 8.0),
        UnitSpec(id = "jarra", defaultGrams = 500.0, volumeMl = 500.0),
        UnitSpec(id = "chupito", defaultGrams = 45.0, volumeMl = 45.0),
        UnitSpec(id = "cana", defaultGrams = 200.0, volumeMl = 200.0),
        UnitSpec(id = "pocillo", defaultGrams = 60.0, volumeMl = 60.0),
        UnitSpec(id = "jarro", defaultGrams = 250.0, volumeMl = 250.0),
        UnitSpec(id = "platito", defaultGrams = 120.0, volumeMl = 120.0),
        UnitSpec(id = "cucharon", defaultGrams = 90.0, volumeMl = 90.0),
    ).associateBy { it.id }

    /**
     * Longest-first matching. Accent-stripped terms; engine compares against
     * [FoodIdentity.normalize].
     */
    val TERMS: List<TermSpec> = listOf(
        // Lonja fina — queso / cecinas (MX rebanada, AR/UY feta, ES loncha, CL lámina)
        TermSpec("laminilla", "thin_slice"),
        TermSpec("laminillas", "thin_slice", plural = true),
        TermSpec("lamina", "thin_slice"),
        TermSpec("laminas", "thin_slice", plural = true),
        TermSpec("lonchita", "thin_slice"),
        TermSpec("lonchitas", "thin_slice", plural = true),
        TermSpec("loncha", "thin_slice"),
        TermSpec("lonchas", "thin_slice", plural = true),
        TermSpec("lonjita", "thin_slice"),
        TermSpec("lonjitas", "thin_slice", plural = true),
        TermSpec("lonja", "thin_slice"),
        TermSpec("lonjas", "thin_slice", plural = true),
        TermSpec("fetita", "thin_slice"),
        TermSpec("fetitas", "thin_slice", plural = true),
        TermSpec("feta", "thin_slice"),
        TermSpec("fetas", "thin_slice", plural = true),
        TermSpec("rebanadita", "thin_slice"),
        TermSpec("rebanaditas", "thin_slice", plural = true),
        TermSpec("rebanada", "thin_slice"),
        TermSpec("rebanadas", "thin_slice", plural = true),
        TermSpec("rodajita", "thin_slice"),
        TermSpec("rodajitas", "thin_slice", plural = true),
        TermSpec("rodaja", "thin_slice"),
        TermSpec("rodajas", "thin_slice", plural = true),
        TermSpec("corte fino", "thin_slice"),
        TermSpec("cortes finos", "thin_slice", plural = true),
        TermSpec("capa fina", "thin_slice"),
        TermSpec("hojita", "thin_slice"),
        TermSpec("hojitas", "thin_slice", plural = true),
        // Tajada: fiambre/queso = thin; plátano/queque = thick (class remap)
        TermSpec("tajadita", "thick_slice"),
        TermSpec("tajaditas", "thick_slice", plural = true),
        TermSpec("tajada", "thick_slice"),
        TermSpec("tajadas", "thick_slice", plural = true),
        TermSpec("torraja", "thick_slice"),
        TermSpec("torrajas", "thick_slice", plural = true),
        TermSpec("torreja", "thick_slice"),
        TermSpec("torrejas", "thick_slice", plural = true),
        TermSpec("torrija", "thick_slice"),
        TermSpec("torrijas", "thick_slice", plural = true),
        TermSpec("torrada", "thick_slice"),
        TermSpec("torradas", "thick_slice", plural = true),
        TermSpec("medallon", "thick_slice"),
        TermSpec("medallones", "thick_slice", plural = true),
        TermSpec("filetito", "thin_slice"),
        TermSpec("filetitos", "thin_slice", plural = true),
        // Cubos / bocados
        TermSpec("cuadrito", "cube"),
        TermSpec("cuadritos", "cube", plural = true),
        TermSpec("cuadradito", "cube"),
        TermSpec("cuadraditos", "cube", plural = true),
        TermSpec("dadito", "cube"),
        TermSpec("daditos", "cube", plural = true),
        TermSpec("dado", "cube"),
        TermSpec("dados", "cube", plural = true),
        TermSpec("cubito", "cube"),
        TermSpec("cubitos", "cube", plural = true),
        TermSpec("bocadito", "cube"),
        TermSpec("bocaditos", "cube", plural = true),
        TermSpec("bocadillo", "chunk"),
        TermSpec("bocado", "cube"),
        TermSpec("bocados", "cube", plural = true),
        // Trozo / cacho
        TermSpec("trocito", "small_chunk"),
        TermSpec("trocitos", "small_chunk", plural = true),
        TermSpec("trozo", "chunk"),
        TermSpec("trozos", "chunk", plural = true),
        TermSpec("pedacito", "small_chunk"),
        TermSpec("pedacitos", "small_chunk", plural = true),
        TermSpec("pedazo", "chunk"),
        TermSpec("pedazos", "chunk", plural = true),
        TermSpec("cacho", "chunk"),
        TermSpec("cachos", "chunk", plural = true),
        TermSpec("cachito", "small_chunk"),
        TermSpec("cachitos", "small_chunk", plural = true),
        TermSpec("tarugo", "chunk"),
        TermSpec("tarugos", "chunk", plural = true),
        TermSpec("penca", "chunk"),
        TermSpec("pencas", "chunk", plural = true),
        // Cuña / gajo / raja
        TermSpec("cuna", "wedge"),
        TermSpec("cunas", "wedge", plural = true),
        TermSpec("gajo", "wedge"),
        TermSpec("gajos", "wedge", plural = true),
        TermSpec("raja", "wedge"),
        TermSpec("rajas", "wedge", plural = true),
        TermSpec("punta", "wedge"),
        TermSpec("puntas", "wedge", plural = true),
        TermSpec("esquina", "wedge"),
        TermSpec("triangulo", "wedge"),
        TermSpec("triangulos", "wedge", plural = true),
        // Tiras
        TermSpec("tirita", "strip"),
        TermSpec("tiritas", "strip", plural = true),
        TermSpec("tira", "strip"),
        TermSpec("tiras", "strip", plural = true),
        // Snacks contables (solo con artículo/cantidad o "de"; no el alimento "galletas de chocolate")
        TermSpec("palito", "chip"),
        TermSpec("palitos", "chip", plural = true),
        TermSpec("ramita", "chip"),
        TermSpec("ramitas", "chip", plural = true),
        TermSpec("hojuela", "chip"),
        TermSpec("hojuelas", "chip", plural = true),
        TermSpec("bolsita", "snack_bag"),
        TermSpec("bolsitas", "snack_bag", plural = true),
        TermSpec("bolsita de snacks", "snack_bag"),
        // Extra utensilios LATAM
        TermSpec("cucharon", "cucharon"),
        TermSpec("cucharones", "cucharon", plural = true),
        TermSpec("pocillo", "pocillo"),
        TermSpec("pocillos", "pocillo", plural = true),
        TermSpec("jarro", "jarro"),
        TermSpec("jarros", "jarro", plural = true),
        TermSpec("jarra", "jarra"),
        TermSpec("jarras", "jarra", plural = true),
        TermSpec("chupito", "chupito"),
        TermSpec("chupitos", "chupito", plural = true),
        TermSpec("cana", "cana"),
        TermSpec("canas", "cana", plural = true),
        TermSpec("platito", "platito"),
        TermSpec("platitos", "platito", plural = true),
        TermSpec("punado", "handful"),
        TermSpec("punados", "handful", plural = true),
    ).sortedByDescending { it.term.length }

    val PORTION_HEAD_TOKENS: Set<String> = TERMS.flatMap { spec ->
        spec.term.split(" ").filter { it.length > 1 }
    }.toSet() + setOf(
        "porcion", "porciones", "racion", "raciones", "unidad", "unidades",
        "pieza", "piezas", "capa", "capas", "corte", "cortes",
    )

    private val FETA_UNIT_TERMS = setOf("feta", "fetas", "fetita", "fetitas")

    private val QUANTITY_WORDS = mapOf(
        "un" to 1.0, "una" to 1.0, "uno" to 1.0,
        "unos" to 2.0, "unas" to 2.0,
        "dos" to 2.0, "tres" to 3.0, "cuatro" to 4.0, "cinco" to 5.0,
        "seis" to 6.0, "siete" to 7.0, "ocho" to 8.0, "nueve" to 9.0, "diez" to 10.0,
        "par" to 2.0, "media" to 0.5, "medio" to 0.5, "mitad" to 0.5,
        "varias" to 3.0, "varios" to 3.0, "algunas" to 3.0, "algunos" to 3.0,
    )

    private val CHEESE_MARKERS = listOf(
        "queso", "gouda", "gauda", "cheddar", "mantecoso", "mozzarella", "mozarela",
        "provolone", "provolona", "brie", "camembert", "parmesano", "manchego",
        "oaxaca", "panela", "fresco", "cottage", "ricotta", "requeson",
    )
    private val COLD_CUT_MARKERS = listOf(
        "jamon", "jamón", "salame", "salami", "mortadela", "mortadella",
        "cecina", "pavo", "tocino", "bacon", "tocineta", "paleta",
        "lomo", "fiambre", "pechuga", "jamonada", "butifarra", "chorizo",
        "salchichon", "bondiola", "pastrami", "roast beef",
    )
    private val BREAD_MARKERS = listOf(
        "pan", "hallulla", "marraqueta", "molde", "baguette", "bollilo", "bolillo",
        "telera", "ciabatta",
    )
    private val CAKE_MARKERS = listOf(
        "queque", "torta", "pastel", "cake", "bizcocho", "kuchen", "brownie",
        "cheesecake",
    )
    private val PLANTAIN_MARKERS = listOf("platano", "maduro", "verde frito", "tajadas de platano")
    private val FRUIT_MARKERS = listOf(
        "naranja", "limon", "limón", "sandia", "melon", "pina", "tomate", "manzana",
        "pera", "mango",
    )
    private val VEG_MARKERS = listOf("pepino", "zanahoria", "cebolla", "palta", "aguacate")
    private val SNACK_MARKERS = listOf(
        "galleta", "chip", "papa frita", "papas fritas", "snack", "dorito", "cheeto",
    )

    fun detectFoodClass(hint: String): String {
        val blob = FoodIdentity.normalize(hint)
        return when {
            PLANTAIN_MARKERS.any { blob.contains(it) } && blob.contains("tajad") -> "platano"
            CAKE_MARKERS.any { blob.contains(it) } -> "dulce"
            COLD_CUT_MARKERS.any { blob.contains(FoodIdentity.normalize(it)) } -> "fiambre"
            CHEESE_MARKERS.any { blob.contains(it) } -> "queso"
            BREAD_MARKERS.any { blob.contains(it) } -> "pan"
            SNACK_MARKERS.any { blob.contains(it) } -> "snack"
            FRUIT_MARKERS.any { blob.contains(it) } -> "fruta"
            VEG_MARKERS.any { blob.contains(it) } -> "verdura"
            blob.contains("almendra") || blob.contains("nuez") || blob.contains("mani") -> "nuts"
            else -> "generic"
        }
    }

    fun isPortionHeadToken(token: String): Boolean {
        val n = FoodIdentity.normalize(token)
        return n in PORTION_HEAD_TOKENS
    }

    fun looksLikePortionExpression(expression: String): Boolean {
        val n = FoodIdentity.normalize(expression)
        return TERMS.any { term ->
            Regex("""\b${Regex.escape(term.term)}\b""").containsMatchIn(n)
        }
    }

    /**
     * Identity span after a bound unit (`láminas de queso gouda` → `queso gouda`).
     * Attribute phrases (`galletas de chocolate`) stay intact.
     */
    fun foodSpanAfterUnit(expression: String): String {
        val raw = expression.trim()
        if (raw.isBlank()) return raw
        val normalized = FoodIdentity.normalize(raw)
        if (isFetaCheeseIdentity(normalized)) return raw
        var bestTerm: TermSpec? = null
        var bestFound: MatchResult? = null
        for (term in TERMS) {
            val regex = Regex("""\b${Regex.escape(term.term)}\b""")
            val found = regex.find(normalized) ?: continue
            if (shouldSkipFetaUnit(term, normalized, found.range.first)) continue
            if (shouldSkipFoodNameAsUnit(term, normalized, found)) continue
            if (bestTerm == null ||
                term.term.length > bestTerm.term.length ||
                (term.term.length == bestTerm.term.length && found.range.first < (bestFound?.range?.first ?: Int.MAX_VALUE))
            ) {
                bestTerm = term
                bestFound = found
            }
        }
        if (bestTerm == null || bestFound == null) return raw
        val afterDe = extractFoodAfterDe(normalized)
        val deIdx = normalized.indexOf(" de ")
        if (afterDe != null && deIdx >= 0 && bestFound.range.first < deIdx) {
            return afterDe
        }
        return raw
    }

    fun boundUnitId(expression: String): String? {
        val result = resolve(expression) ?: return null
        return result.source.removePrefix("lexicon:").substringBefore(":").takeIf { it.isNotBlank() }
    }

    fun resolve(
        expression: String,
        foodHint: String? = null,
    ): SubjectivePortionEngine.PortionResult? {
        val normalized = FoodIdentity.normalize(expression)
        if (normalized.isBlank()) return null
        if (isFetaCheeseIdentity(normalized)) return null

        val classHint = detectFoodClass(foodHint ?: extractFoodAfterDe(normalized) ?: normalized)
        var best: Match? = null
        for (term in TERMS) {
            val regex = Regex("""\b${Regex.escape(term.term)}\b""")
            val found = regex.find(normalized) ?: continue
            if (shouldSkipFetaUnit(term, normalized, found.range.first)) continue
            if (shouldSkipFoodNameAsUnit(term, normalized, found)) continue
            val qty = parseQuantity(normalized, found.range.first, term)
            val candidate = Match(term, qty, found.range.first)
            if (best == null || term.term.length > best.term.term.length ||
                (term.term.length == best.term.term.length && found.range.first < best.index)
            ) {
                best = candidate
            }
        }
        val match = best ?: return null
        val spec = UNITS[match.term.unitId] ?: return null
        val perUnit = gramsForUnit(spec, classHint)
        val grams = (perUnit * match.qty).coerceIn(0.2, 600.0)
        return SubjectivePortionEngine.PortionResult(
            grams = grams,
            confidence = 0.82,
            source = "lexicon:${spec.id}:${classHint}",
            expression = expression,
            relativeFactor = match.qty,
        )
    }

    private data class Match(val term: TermSpec, val qty: Double, val index: Int)

    private fun gramsForUnit(spec: UnitSpec, foodClass: String): Double {
        if (spec.id == "thick_slice" && foodClass in setOf("queso", "fiambre")) {
            val thin = UNITS.getValue("thin_slice")
            return thin.gramsByClass[foodClass] ?: thin.defaultGrams
        }
        return spec.gramsByClass[foodClass] ?: spec.defaultGrams
    }

    private fun isFetaCheeseIdentity(normalized: String): Boolean {
        if (Regex("""\bfetas?\s+de\b""").containsMatchIn(normalized)) return false
        if (Regex("""\bqueso\s+feta\b""").containsMatchIn(normalized)) return true
        if (normalized == "feta" || normalized == "queso feta") return true
        return false
    }

    private fun shouldSkipFetaUnit(term: TermSpec, normalized: String, start: Int): Boolean {
        if (term.term !in FETA_UNIT_TERMS) return false
        if (Regex("""\bfetas?\s+de\b""").containsMatchIn(normalized)) return false
        val before = normalized.substring(0, start).trim()
        if (before.endsWith("queso")) return true
        if (normalized == "feta" || normalized.startsWith("queso feta")) return true
        return !normalized.contains(" de ")
    }

    /**
     * "galletas de chocolate" is the food, not "N cookie-units of chocolate candy".
     * Only treat cookie/chip heads as units when a quantity/article is present
     * ("una galleta", "3 palitos") and the head is not the whole name.
     */
    private val FOOD_NAME_UNITS = setOf("cookie", "chip")

    private fun shouldSkipFoodNameAsUnit(term: TermSpec, normalized: String, found: MatchResult): Boolean {
        val spec = UNITS[term.unitId] ?: return false
        if (spec.id !in FOOD_NAME_UNITS && spec.id != "cookie") return false
        val before = normalized.substring(0, found.range.first).trim()
        val hasQty = before.isNotEmpty() && (
            before.last().isDigit() ||
                QUANTITY_WORDS.keys.any { before == it || before.endsWith(" $it") } ||
                before.endsWith("par de") || before.endsWith("un par")
            )
        if (hasQty) return false
        return true
    }

    private fun parseQuantity(normalized: String, termStart: Int, term: TermSpec): Double {
        val before = normalized.substring(0, termStart).trim()
        if (before.isEmpty()) {
            return if (term.plural) UNITS[term.unitId]?.barePluralCount ?: BARE_PLURAL_COUNT else 1.0
        }
        val par = Regex("""(?:un|una|1)\s+par(?:\s+de)?$""").find(before)
        if (par != null) return 2.0
        val digit = Regex("""(\d+(?:[.,]\d+)?)$""").find(before)
        if (digit != null) {
            return digit.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 1.0
        }
        val words = before.split(" ")
        val last = words.lastOrNull().orEmpty()
        QUANTITY_WORDS[last]?.let { qty ->
            if (last == "unos" || last == "unas" || last == "varias" || last == "varios" ||
                last == "algunas" || last == "algunos"
            ) {
                return qty
            }
            return qty
        }
        return if (term.plural) UNITS[term.unitId]?.barePluralCount ?: BARE_PLURAL_COUNT else 1.0
    }

    private fun extractFoodAfterDe(normalized: String): String? {
        val idx = normalized.lastIndexOf(" de ")
        if (idx < 0) return null
        return normalized.substring(idx + 4).trim().takeIf { it.length >= 2 }
    }
}
