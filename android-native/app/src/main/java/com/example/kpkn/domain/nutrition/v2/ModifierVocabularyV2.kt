package com.example.kpkn.domain.nutrition.v2

/**
 * Vocabulario de modificadores del parser v2 (F2, paso 2), en español e inglés: unidades, medidas caseras, envases,
 * tamaños, estados, formas, medios, negación, conectores, preposiciones, correcciones, relleno y cantidades.
 *
 * Las frases se escriben como las diría el usuario (con o sin tilde, multipalabra separada por espacios) y se
 * pasan por [NormalizerV2], así que coinciden con el texto normalizado token a token. Las coincidencias de varias
 * palabras ganan a las de una («sin azucar» → forma sin azúcar; «sin» solo → negación).
 */
object ModifierVocabularyV2 {

    /** Todas las entradas, listas para [LexiconV2]. */
    val entries: List<LexEntry> by lazy { build() }

    // ─── Unidades ─────────────────────────────────────────────────────

    private val massUnits: Map<String, Double> = buildMap {
        listOf("g", "gr", "grs", "gramo", "gramos", "gram", "grams").forEach { put(it, 1.0) }
        listOf("kg", "kgs", "kls", "kilo", "kilos", "kilogramo", "kilogramos", "kilogram", "kilograms").forEach { put(it, 1000.0) }
        put("mg", 0.001)
        listOf("oz", "onza", "onzas", "ounce", "ounces").forEach { put(it, 28.35) }
        listOf("lb", "lbs", "libra", "libras", "pound", "pounds").forEach { put(it, 453.6) }
    }

    private val volumeUnits: Map<String, Double> = buildMap {
        listOf("ml", "mls", "mililitro", "mililitros", "milliliter", "milliliters", "millilitre", "millilitres", "cc", "cm3")
            .forEach { put(it, 1.0) }
        listOf("l", "lt", "lts", "litro", "litros", "liter", "liters", "litre", "litres").forEach { put(it, 1000.0) }
        put("cl", 10.0)
        put("dl", 100.0)
    }

    // ─── Medidas caseras y envases (etiqueta canónica -> formas) ───────

    private val household: Map<String, List<String>> = mapOf(
        "taza" to listOf("taza", "tazas", "tazon", "tazones", "cup", "cups", "mug", "mugs"),
        "vaso" to listOf("vaso", "vasos", "glass", "glasses"),
        "cucharada" to listOf("cucharada", "cucharadas", "cda", "cdas", "tbsp", "tbsps", "tablespoon", "tablespoons"),
        "cucharadita" to listOf(
            "cucharadita", "cucharaditas", "cdta", "cdtas", "cdita", "cditas", "tsp", "tsps", "teaspoon", "teaspoons",
        ),
        "plato" to listOf("plato", "platos", "plate", "plates", "bowl", "bowls", "bol", "boles", "pocillo", "pocillos"),
        "rebanada" to listOf("rebanada", "rebanadas", "tajada", "tajadas", "slice", "slices"),
        "lamina" to listOf("lamina", "laminas"),
        "porcion" to listOf("porcion", "porciones", "serving", "servings", "portion", "portions"),
        "punado" to listOf("punado", "punados", "handful", "handfuls"),
        "scoop" to listOf("scoop", "scoops", "medida", "medidas"),
        "trozo" to listOf("trozo", "trozos", "pedazo", "pedazos", "piece", "pieces"),
        "unidad" to listOf("unidad", "unidades", "u", "unit", "units"),
        "copa" to listOf("copa", "copas"),
        "shot" to listOf("shot", "shots", "caballito", "caballitos"),
        "chorrito" to listOf("chorrito", "chorritos", "splash", "splashes"),
        "pizca" to listOf("pizca", "pizcas", "pinch", "pinches"),
        "cucharon" to listOf("cucharon", "cucharones"),
    )

    private val containers: Map<String, List<String>> = mapOf(
        "lata" to listOf("lata", "latas", "can", "cans", "tarro", "tarros"),
        "botella" to listOf("botella", "botellas", "bottle", "bottles"),
        "caja" to listOf("caja", "cajas", "cajita", "cajitas", "carton", "cartones", "box", "boxes"),
        "sobre" to listOf("sobre", "sobres", "sachet", "sachets"),
        "envase" to listOf("pote", "potes", "envase", "envases", "container", "containers"),
        "paquete" to listOf("paquete", "paquetes", "bolsa", "bolsas", "pack", "packs", "bag", "bags"),
        "schop" to listOf("schop", "schops", "shop", "pint", "pints", "pinta", "pintas"),
    )

    // ─── Tamaño, estado, forma, medio ──────────────────────────────────

    private val sizes: Map<SizeV2, List<String>> = mapOf(
        SizeV2.S to listOf("chico", "chica", "chicos", "chicas", "pequeno", "pequena", "pequenos", "pequenas", "small", "little"),
        SizeV2.M to listOf("mediano", "mediana", "medianos", "medianas", "medium", "regular"),
        SizeV2.L to listOf("grande", "grandes", "large", "big"),
    )

    private val states: Map<FoodStateV2, List<String>> = mapOf(
        FoodStateV2.RAW to listOf("crudo", "cruda", "crudos", "crudas", "raw"),
        FoodStateV2.COOKED to listOf(
            "cocido", "cocida", "cocidos", "cocidas", "hervido", "hervida", "hervidos", "hervidas",
            "cooked", "boiled", "sancochado", "sancochada", "sancochados", "sancochadas",
        ),
        FoodStateV2.FRIED to listOf(
            "frito", "frita", "fritos", "fritas", "fried", "apanado", "apanada", "apanados", "apanadas", "breaded",
        ),
        FoodStateV2.GRILLED to listOf(
            "a la plancha", "plancha", "grilled", "asado", "asada", "asados", "asadas",
            "a la parrilla", "parrilla", "a las brasas",
        ),
        FoodStateV2.BAKED to listOf(
            "al horno", "horneado", "horneada", "horneados", "horneadas", "baked", "roasted",
            "rostizado", "rostizada", "rostizados", "rostizadas",
        ),
        FoodStateV2.STEAMED to listOf("al vapor", "steamed"),
    )

    private val forms: Map<FoodFormV2, List<String>> = mapOf(
        FoodFormV2.POWDER_DRY to listOf("en polvo", "powder", "powdered"),
        FoodFormV2.RECONSTITUTED to listOf(
            "preparado", "preparada", "reconstituido", "reconstituida",
        ),
        FoodFormV2.CONCENTRATE to listOf("concentrado", "concentrada", "concentrate"),
        FoodFormV2.DRIED to listOf("deshidratado", "deshidratada", "seco", "seca", "dried"),
        FoodFormV2.CANNED_DRAINED to listOf("escurrido", "escurrida", "drained"),
        FoodFormV2.LIGHT to listOf("light", "diet", "lite"),
        FoodFormV2.SUGAR_FREE to listOf("zero", "sin azucar", "sugar free", "sugar-free"),
    )

    private val media: Map<MediumV2, List<String>> = mapOf(
        MediumV2.WATER to listOf("con agua", "with water"),
        MediumV2.MILK to listOf("con leche", "with milk"),
    )

    // ─── Gramática: negación, conectores, preposiciones, correcciones, relleno ─

    private val negations = listOf("sin", "without", "no")

    private val andConnectors = listOf("y", "e", "mas", "and", "plus", "+")
    private val withConnectors = listOf("con", "with")

    private val prepositions = listOf("de", "del", "of", "en")

    private val corrections = listOf(
        "eran", "era", "perdon", "digo", "mejor dicho", "en realidad", "corrijo", "no espera",
        "actually", "i mean", "sorry", "wait",
    )

    private val fillers = listOf(
        "me comi", "comi", "almorce", "cene", "desayune", "tome", "me tome", "hoy", "ayer",
        "en la manana", "en la tarde", "en la noche", "para el almuerzo", "de almuerzo", "de once", "de colacion",
        "i had", "i ate", "for lunch", "for dinner", "for breakfast", "today", "yesterday",
        // Extras habituales de la misma familia.
        "para la cena", "para el desayuno", "para la once", "de cena", "de desayuno", "i drank", "for snack",
    )

    // ─── Cantidades ───────────────────────────────────────────────────

    private val weakNumbers: Map<String, Double> =
        listOf("un", "una", "uno", "a", "an", "one").associateWith { 1.0 }

    private val strongNumbers: Map<String, Double> = buildMap {
        listOf("medio", "media", "half").forEach { put(it, 0.5) }
        listOf("cuarto", "quarter", "un cuarto", "a quarter", "one quarter").forEach { put(it, 0.25) }
        put("tres cuartos", 0.75)
        put("three quarters", 0.75)
        listOf("par", "couple").forEach { put(it, 2.0) }
        listOf("docena", "docenas", "dozen", "dozens").forEach { put(it, 12.0) }
        listOf("media docena", "half a dozen", "half dozen").forEach { put(it, 6.0) }
        // «half a cup»: «half a» ya es 0.5, no 0.5 seguido de un artículo.
        listOf("half a", "half an").forEach { put(it, 0.5) }
        // «once» como cantidad (11); «de once» (la merienda) gana por ser más largo.
        put("once", 11.0)
    }

    // ─── Construcción ──────────────────────────────────────────────────

    private fun build(): List<LexEntry> {
        val out = ArrayList<LexEntry>(600)
        fun add(phrase: String, tag: LexTag) {
            LexEntry.of(phrase, tag)?.let { out += it }
        }
        massUnits.forEach { (phrase, grams) -> add(phrase, LexTag.MassUnit(grams)) }
        volumeUnits.forEach { (phrase, ml) -> add(phrase, LexTag.VolumeUnit(ml)) }
        household.forEach { (label, forms) -> forms.forEach { add(it, LexTag.Household(label)) } }
        containers.forEach { (label, forms) -> forms.forEach { add(it, LexTag.Container(label)) } }
        sizes.forEach { (size, forms) -> forms.forEach { add(it, LexTag.Size(size)) } }
        states.forEach { (state, forms) -> forms.forEach { add(it, LexTag.State(state)) } }
        this.forms.forEach { (form, phrases) -> phrases.forEach { add(it, LexTag.Form(form)) } }
        media.forEach { (medium, phrases) -> phrases.forEach { add(it, LexTag.Medium(medium)) } }
        negations.forEach { add(it, LexTag.Negation) }
        andConnectors.forEach { add(it, LexTag.Connector(ConnectorKind.AND)) }
        withConnectors.forEach { add(it, LexTag.Connector(ConnectorKind.WITH)) }
        prepositions.forEach { add(it, LexTag.Preposition) }
        this.corrections.forEach { add(it, LexTag.Correction) }
        fillers.forEach { add(it, LexTag.Filler) }
        weakNumbers.forEach { (phrase, value) -> add(phrase, LexTag.Number(value, weak = true)) }
        strongNumbers.forEach { (phrase, value) -> add(phrase, LexTag.Number(value, weak = false)) }
        return out
    }
}
