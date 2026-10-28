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
        listOf("g", "gr", "grs", "grm", "grms", "gms", "gramo", "gramos", "gram", "grams").forEach { put(it, 1.0) }
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
        listOf("fl oz", "floz", "fluid ounce", "fluid ounces").forEach { put(it, 29.57) }
    }

    // ─── Medidas caseras y envases (etiqueta canónica -> formas) ───────

    private val household: Map<String, List<String>> = mapOf(
        "taza" to listOf("taza", "tazas", "tacita", "tacitas", "tazon", "tazones", "cup", "cups", "mug", "mugs"),
        "vaso" to listOf("vaso", "vasos", "vasito", "vasitos", "glass", "glasses"),
        "cucharada" to listOf(
            "cucharada", "cucharadas", "cuchara", "cucharas", "cda", "cdas", "tbsp", "tbsps", "tbs", "tablespoon", "tablespoons",
        ),
        "cucharadita" to listOf(
            "cucharadita", "cucharaditas", "cucharita", "cucharitas", "cdta", "cdtas", "cdita", "cditas", "tsp", "tsps",
            "teaspoon", "teaspoons",
        ),
        "plato" to listOf("plato", "platos", "plate", "plates", "bowl", "bowls", "bol", "boles", "pocillo", "pocillos"),
        "rebanada" to listOf("rebanada", "rebanadas", "tajada", "tajadas", "slice", "slices"),
        "lamina" to listOf("lamina", "laminas"),
        "rodaja" to listOf("rodaja", "rodajas", "rodajita", "rodajitas"),
        "presa" to listOf("presa", "presas"),
        "pieza" to listOf("pieza", "piezas", "pza", "pzas"),
        "barra" to listOf("barra", "barras", "barrita", "barritas", "bar", "bars"),
        "porcion" to listOf("porcion", "porciones", "serving", "servings", "portion", "portions"),
        "punado" to listOf("punado", "punados", "handful", "handfuls"),
        "scoop" to listOf("scoop", "scoops", "medida", "medidas"),
        "trozo" to listOf("trozo", "trozos", "pedazo", "pedazos", "piece", "pieces"),
        "unidad" to listOf("unidad", "unidades", "u", "unit", "units"),
        "copa" to listOf("copa", "copas", "copita", "copitas"),
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
        SizeV2.S to listOf(
            "chico", "chica", "chicos", "chicas", "chiquito", "chiquita", "chiquitos", "chiquitas", "pequeno", "pequena",
            "pequenos", "pequenas", "pequenito", "pequenita", "mini", "small", "little",
        ),
        SizeV2.M to listOf("mediano", "mediana", "medianos", "medianas", "medium", "regular"),
        SizeV2.L to listOf(
            "grande", "grandes", "grandote", "grandota", "gigante", "extra grande", "extra large", "xl", "jumbo", "large", "big",
            // «pizza familiar», «cuarto de pizza familiar»: el tamaño más grande de la carta.
            "familiar", "familiares",
        ),
    )

    private val states: Map<FoodStateV2, List<String>> = mapOf(
        FoodStateV2.RAW to listOf("crudo", "cruda", "crudos", "crudas", "raw"),
        FoodStateV2.COOKED to listOf(
            "cocido", "cocida", "cocidos", "cocidas", "hervido", "hervida", "hervidos", "hervidas",
            "cooked", "boiled", "sancochado", "sancochada", "sancochados", "sancochadas",
        ),
        FoodStateV2.FRIED to listOf(
            "frito", "frita", "fritos", "fritas", "fried", "salteado", "salteada", "salteados", "salteadas",
            "apanado", "apanada", "apanados", "apanadas", "breaded",
        ),
        FoodStateV2.GRILLED to listOf(
            "a la plancha", "plancha", "grilled", "asado", "asada", "asados", "asadas",
            "a la parrilla", "parrilla", "a las brasas", "a la brasa", "plancheado", "plancheada", "plancheados",
            "plancheadas",
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
            "preparado", "preparada", "preparados", "preparadas", "reconstituido", "reconstituida", "disuelto", "disuelta",
            "diluido", "diluida", "de sobre", "reconstituted", "prepared",
        ),
        FoodFormV2.CONCENTRATE to listOf("concentrado", "concentrada", "concentrados", "concentradas", "concentrate"),
        FoodFormV2.DRIED to listOf(
            "deshidratado", "deshidratada", "deshidratados", "deshidratadas", "seco", "seca", "secos", "secas",
            "desecado", "desecada", "liofilizado", "liofilizada", "dried", "dehydrated", "freeze dried",
        ),
        FoodFormV2.CANNED_DRAINED to listOf(
            "escurrido", "escurrida", "escurridos", "escurridas", "bien escurrido", "bien escurrida", "drained",
            "sin liquido", "sin el liquido", "sin su liquido", "sin salmuera", "sin almibar", "sin el almibar",
        ),
        FoodFormV2.CANNED_UNDRAINED to listOf(
            "sin escurrir", "no escurrido", "no escurrida", "con liquido", "con su liquido", "con el liquido",
            "con todo el liquido", "con todo su liquido", "con su jugo", "con el jugo de la lata", "con almibar",
            "con su almibar", "con salmuera", "con su salmuera", "undrained", "with liquid", "with the liquid",
        ),
        FoodFormV2.LIQUID to listOf(
            "liquido", "liquida", "liquidos", "liquidas", "bebible", "bebibles", "tomable", "tomables", "para beber",
            "para tomar", "listo para beber", "listo para tomar", "ready to drink", "drinkable", "liquid",
        ),
        FoodFormV2.LIGHT to listOf(
            "light", "diet", "lite", "descremado", "descremada", "descremados", "descremadas", "semidescremado",
            "semidescremada", "desnatado", "desnatada", "semidesnatado", "semidesnatada", "bajo en grasa", "baja en grasa",
            "bajo en calorias", "baja en calorias", "low fat", "reduced fat", "skim", "skimmed", "fat free", "dietetico",
            "dietetica", "ligero", "ligera",
        ),
        FoodFormV2.SUGAR_FREE to listOf(
            "zero", "cero", "sin azucar", "sin azucares", "sin azucar agregada", "sin azucar anadida", "sin endulzar",
            "sin endulzante", "sin edulcorante", "zero azucar", "cero azucar", "sugar free", "sugar-free", "sugarfree",
            "unsweetened", "no sugar", "no added sugar",
        ),
    )

    private val media: Map<MediumV2, List<String>> = mapOf(
        MediumV2.WATER to listOf("con agua", "en agua", "with water", "in water"),
        MediumV2.MILK to listOf("con leche", "en leche", "with milk", "in milk"),
    )

    // ─── Gramática: negación, conectores, preposiciones, correcciones, relleno ─

    private val negations = listOf("sin", "without", "no", "ni", "nor")

    private val andConnectors = listOf("y", "e", "mas", "and", "plus", "+", "ademas", "also", "as well as")
    private val withConnectors = listOf(
        "con", "with", "c", "junto con", "acompanado de", "acompanado con", "acompanada de", "acompanada con",
        "along with", "served with", "topped with",
    )

    private val prepositions = listOf("de", "del", "of", "en")

    private val corrections = listOf(
        "eran", "era", "perdon", "digo", "mejor dicho", "en realidad", "corrijo", "no espera",
        "actually", "i mean", "sorry", "wait", "no mentira", "mentira", "mas bien", "me equivoque", "me equivoco",
        "no perdon", "no sorry", "correction", "rather",
    )

    private val fillers = listOf(
        "me comi", "comi", "almorce", "cene", "desayune", "tome", "me tome", "hoy", "ayer",
        "en la manana", "en la tarde", "en la noche", "para el almuerzo", "de almuerzo", "de once", "de colacion",
        "i had", "i ate", "for lunch", "for dinner", "for breakfast", "today", "yesterday",
        // Extras habituales de la misma familia.
        "para la cena", "para el desayuno", "para la once", "de cena", "de desayuno", "i drank", "for snack",
        // Muletillas y verbos corrientes que no son comida (genéricos).
        // «bueno» no va aquí: la gramática lo lee como corrección («un huevo y bueno 3 huevos»).
        "eh", "ehh", "em", "mm", "mmm", "pues", "o sea", "osea", "mi", "mis", "me servi", "me prepare", "prepare",
        "me compre", "compre", "comimos", "comemos", "comiendo", "bebi", "me bebi", "consumi", "cocine", "cenando",
        "almorzando", "desayunando", "this morning", "this afternoon", "tonight", "last night", "i just had", "i also had",
        "i have had", "we had", "we ate", "i grabbed", "i got", "i made", "i cooked", "for supper", "for brunch",
        "as a snack", "at lunch", "at dinner", "at breakfast", "had", "ate", "drank", "my", "hoy dia", "esta manana",
        "esta tarde", "esta noche", "anoche", "anteayer",
        // «c/u», «cada una» y los aproximadores no cambian la cantidad.
        "c u", "cada uno", "cada una", "each", "aprox", "aproximadamente", "approximately", "approx", "roughly",
        "about", "mas o menos", "more or less", "alrededor de", "unos", "unas",
    )

    // ─── Cantidades ───────────────────────────────────────────────────

    private val weakNumbers: Map<String, Double> =
        listOf("un", "una", "uno", "a", "an", "one").associateWith { 1.0 }

    private val strongNumbers: Map<String, Double> = buildMap {
        listOf("medio", "media", "half").forEach { put(it, 0.5) }
        listOf("cuarto", "quarter", "un cuarto", "a quarter", "one quarter").forEach { put(it, 0.25) }
        put("tres cuartos", 0.75)
        put("three quarters", 0.75)
        listOf("par", "couple", "a couple of", "couple of", "a couple", "a pair of", "a pair", "pair of").forEach { put(it, 2.0) }
        // Fracciones habladas. «tercios» sin número delante («tercios») no se usa: «dos tercios» sale como «2 tercios».
        listOf("mitad", "la mitad", "una mitad").forEach { put(it, 0.5) }
        listOf("tercio", "un tercio", "third", "a third", "one third").forEach { put(it, 1.0 / 3.0) }
        listOf("2 tercios", "dos tercios", "two thirds").forEach { put(it, 2.0 / 3.0) }
        listOf("quinto", "un quinto", "a fifth").forEach { put(it, 0.2) }
        listOf("sexto", "un sexto").forEach { put(it, 1.0 / 6.0) }
        listOf("octavo", "un octavo", "an eighth", "one eighth").forEach { put(it, 0.125) }
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
