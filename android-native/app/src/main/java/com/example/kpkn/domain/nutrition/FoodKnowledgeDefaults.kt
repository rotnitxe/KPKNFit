package com.example.kpkn.domain.nutrition

/**
 * The Kotlin copy of the food knowledge (WP-N13): the tables of the `protectedPhrases`, `typos`, `synonyms`, `householdUnits`, `containers`,
 * `utensils` and `densities` sections, moved here as they were from [ProtectedPhrases], [TextNormalizer], [HouseholdPortions] and
 * [SubjectivePortionEngine]. [FoodKnowledge] serves it until the loader installs `assets/food_data/food_knowledge_v1.json`, and
 * FoodKnowledgeParityTest keeps the two equal, entry by entry and in the same order, so that nothing changes whichever is in force.
 * A later increment deletes this file and the asset becomes the only copy.
 *
 * A leaf on purpose: it reads nothing but the standard library, so building the default snapshot can never start an object that is
 * itself waiting for [FoodKnowledge]. Pure Kotlin / JVM: no Android dependency.
 */
internal object FoodKnowledgeDefaults {

    // ─── protectedPhrases ──────────────────────────────────────────────────────────────────────────────────────────

    val ENTITY_LITERALS: List<String> = listOf(
        "arroz con leche",
        "pastel de choclo", "pasteles de choclo",
        "empanada de pino", "empanadas de pino",
        "empanada de queso", "empanadas de queso",
        "porotos con riendas",
        "cafe con leche", "café con leche",
        "te con leche", "té con leche",
        "leche con chocolate",
        "leche con platano", "leche con plátano",
        "sandwich de pollo con mayonesa", "sandwich de jamon con mayonesa",
        "sándwich de pollo con mayonesa", "sándwich de jamón con mayonesa",
        "sandwich de jamon y queso", "sandwich de jamón y queso",
        "sándwich de jamon y queso", "sándwich de jamón y queso",
        "hamburguesa con queso", "hamburguesas con queso",
        "papas fritas con mayonesa", "papa fritas con mayonesa",
        "papas con mayo",
    )

    /** Names that read as two foods and are one, written out; [ProtectedPhrases.COMPOUND_NAMES] adds the ones built from the heads. */
    val FIXED_COMPOUND_NAMES: List<String> = listOf("agua con gas", "agua sin gas", "agua mineral con gas", "agua mineral sin gas", "ave palta", "ave mayo")

    /** Sandwiches, pastries and snacks named after a filling of two foods: "empanada de jamón y queso" is one thing. */
    val FILLED_HEADS: List<String> = listOf(
        "sándwich", "sanguche", "emparedado", "tostada", "tostadita", "empanada", "croissant", "wrap", "quesadilla", "calzone", "pizza",
    )

    /** Foods made of maize: the "maíz" of "tortilla de maíz" is a material, it is not the choclo of the vegetable stall. */
    val MAIZE_PRODUCTS: List<String> = listOf(
        "tortilla", "harina", "aceite", "almidón", "fécula", "sémola", "pan", "galleta", "palomitas", "cereal", "hojuelas", "jarabe",
        "snack", "nachos", "tostadas", "chips",
    )

    /** Flavours that come in pairs: a scoop, a cake or a yogurt "de vainilla y chocolate" is one food with two flavours. */
    val FLAVORS: List<String> = listOf(
        "vainilla", "chocolate", "frutilla", "lúcuma", "manjar", "menta", "frambuesa", "mora", "coco", "caramelo", "avellana",
        "pistacho", "limón", "maracuyá",
    )

    /**
     * Foods that come in flavours: after one of them, "de vainilla y chocolate" is one food with two flavours. A drink or a juice is
     * left alone ("jugo de naranja y plátano" may well be a juice and a banana, and its estimate would be read from the banana).
     */
    val FLAVORED_HEADS: List<String> = listOf(
        "helado", "helados", "torta", "tortas", "pastel", "pasteles", "queque", "queques", "keke", "kekes", "kuchen", "pie", "pies",
        "mousse", "flan", "flanes", "budín", "budines", "yogur", "yogures", "yogurt", "yogurts", "galleta", "galletas", "alfajor",
        "alfajores", "panqueque", "panqueques", "crepe", "crepes", "cheesecake", "cupcake", "cupcakes", "muffin", "muffins", "brownie",
        "brownies", "bizcocho", "bizcochos", "bombón", "bombones", "paleta", "paletas", "sorbete", "sorbetes", "trufa", "trufas",
        "turrón", "turrones", "barra", "barras", "crema", "cremas",
    )

    /** A catalog food that is not a row of the static catalog (it is a sauce), protected like one. */
    val CATALOG_EXTRA_PHRASES: List<String> = listOf("salsa de tomate")

    // ─── typos and synonyms (TextNormalizer) ───────────────────────────────────────────────────────────────────────

    // ─── Common typos ─────────────────────────────────────────────────────
    // FIX NUT-01: systemic authority — Gauda/Gouda must normalize to single form,
    // otherwise OFF rows "gouda" never match query "gauda" as exact → NEEDS_REVIEW.
    // Spelling mistakes and accent folds only. A plural is not a mistake: "papas", "porotos", "lentejas" and "garbanzos" used to be cut
    // to the singular here, before any protected phrase was read (A-P3); FoodParser gives a bare mention its singular tag
    // (STAPLE_SINGULARS), and a regional name is a [SYNONYMS] entry.
    val TYPOS: Map<String, String> = mapOf(
        "poyo" to "pollo", "polllo" to "pollo", "pyo" to "pollo",
        "arros" to "arroz", "arro" to "arroz", "aros" to "arroz",
        "uebo" to "huevo", "wevo" to "huevo", "guevo" to "huevo", "güevo" to "huevo",
        "gueso" to "queso", "keso" to "queso",
        "gauda" to "gouda", "gouda" to "gouda",
        "panna" to "pana",
        "papa" to "papa",
        "tomate" to "tomate",
        "cebolla" to "cebolla",
        "zanahoria" to "zanahoria", "sanahoria" to "zanahoria",
        "platano" to "platano", "plátano" to "platano",
        "naranja" to "naranja", "naraja" to "naranja",
        "manzana" to "manzana", "mansana" to "manzana",
        "lechuga" to "lechuga", "lechua" to "lechuga",
        "brocoli" to "brocoli", "brocolí" to "brocoli",
        "espinaca" to "espinaca", "espina" to "espinaca",
        "palta" to "palta",
        "choclo" to "choclo",
        "poroto" to "poroto",
        "lenteja" to "lenteja",
        "garbanzo" to "garbanzo",
        "avena" to "avena", "abena" to "avena",
        "merluza" to "merluza",
        "salmon" to "salmon", "salmón" to "salmon",
        "camaron" to "camaron", "camarón" to "camaron",
        "pimenton" to "pimenton", "pimentón" to "pimenton",
        "betarraga" to "betarraga",
        "zapallo" to "zapallo",
        "marraqueta" to "marraqueta",
        "hallulla" to "hallulla", "hallula" to "hallulla", "halulla" to "hallulla", "allulla" to "hallulla",
        "empanada" to "empanada",
        "cazuela" to "cazuela",
        "charquican" to "charquicán", "charquicán" to "charquicán",
        "porotos granados" to "porotos granados",
        "completo" to "completo",
        "chorrillana" to "chorrillana",
    )

    // ─── Regional names ──────────────────────────────────────────────────────
    // Another word for the same food, not a spelling mistake ("aguacate" is Chile's palta). A synonym replaces a word that
    // stands on its own, never one inside a protected phrase: "tortilla de maíz" keeps its maíz (a tortilla "de choclo" is
    // another food), and "aceite de maíz" is a catalog name. applyTypos masks the protected phrases before it gets here (WP-N9).
    val SYNONYMS: Map<String, String> = mapOf(
        "aguacate" to "palta", "aguacates" to "paltas",
        "maiz" to "choclo", "maíz" to "choclo", "maices" to "choclos", "maíces" to "choclos",
        "remolacha" to "betarraga", "remolachas" to "betarragas",
        "calabaza" to "zapallo", "calabazas" to "zapallos",
    )

    // ─── householdUnits (HouseholdPortions) ────────────────────────────────────────────────────────────────────────

    val COUNTABLE_FAMILIES: Set<String> = setOf(
        "pan_chileno", "pan", "huevo", "empanada", "wrap",
    )

    val COUNTABLE_NAME_MARKERS: Set<String> = setOf(
        "hallulla", "hallula", "marraqueta", "sopaipilla", "empanada",
        "completo", "huevo", "galleta", "arepa", "pan amasado", "panecillo",
        "manzana", "platano", "naranja", "pera", "kiwi",
        "taco", "burrito", "sushi", "wrap", "hamburguesa",
        "galletas",
    )

    /**
     * Typical weight of ONE piece by the head noun of the food, accent-free, singular and plural (WP-N8). The default portion
     * of these foods is a serving or a topping ("tomate" and "palta" 80 g), not a piece, so a count cannot scale it: "3 tomates"
     * are 3 x 120 g. Rounded household weights (USDA household measures of a medium tomato, orange, peach, banana and kiwi:
     * 123, 131, 150, 118 and 69 g) and the usual 125 g cup of yogurt. [HouseholdPortions.unitGrams] applies the pieces of the countable
     * markers; the sopaipilla, the cookie and the breads keep their dedicated rows there. A food that is not here keeps its
     * portion default, and a custom food keeps the serving its owner typed.
     */
    val UNIT_GRAMS_BY_TOKEN: Map<String, Double> = buildMap {
        fun weight(grams: Double, vararg tokens: String) = tokens.forEach { put(it, grams) }
        weight(120.0, "tomate", "tomates", "jitomate", "jitomates")
        weight(150.0, "palta", "paltas", "aguacate", "aguacates")
        weight(130.0, "naranja", "naranjas")
        weight(150.0, "manzana", "manzanas")
        weight(120.0, "platano", "platanos")
        weight(150.0, "pera", "peras")
        weight(75.0, "kiwi", "kiwis")
        weight(150.0, "durazno", "duraznos", "melocoton", "melocotones")
        weight(125.0, "yogurt", "yogurts", "yogur", "yogures")
        weight(220.0, "completo", "completos")
    }

    // Qualifiers that make the food something other than a whole piece of the usual size: "tomate cherry" is 17 g, not 120 g, and
    // "durazno seco" or "tomate en conserva" are not a fruit.
    val NOT_A_WHOLE_PIECE: Set<String> = setOf(
        "cherry", "cocktail", "coctel", "mini", "seco", "seca", "secos", "secas", "deshidratado", "deshidratada", "deshidratados",
        "deshidratadas", "enlatado", "enlatada", "enlatados", "enlatadas", "conserva", "congelado", "congelada", "congelados",
        "congeladas", "triturado", "triturada", "triturados", "trituradas", "rallado", "rallada", "picado", "picada",
    )

    /**
     * Default portion by food family when nothing more specific applies: a glass of milk, a cup of yogurt, a plate of rice. The breads
     * ("pan", "pan_chileno") are not here: their default is a piece, [HouseholdPortions.unitGrams].
     */
    val FAMILY_DEFAULT_GRAMS: Map<String, Double> = linkedMapOf(
        "huevo" to 50.0, "leche" to 200.0, "yogurt" to 125.0, "arroz" to 120.0, "avena" to 40.0, "pasta" to 160.0,
        "pollo" to 150.0, "papa" to 100.0, "tomate" to 80.0, "palta" to 80.0,
    )

    // ─── containers and utensils (SubjectivePortionEngine) ─────────────────────────────────────────────────────────

    private const val CONTAINER_DEFAULT = ContainersKnowledge.DEFAULT

    /**
     * The ways to name a container, in the order they are tried: a "chica" variant goes before its container. The count is matched like a
     * utensil's (see [SubjectivePortionEngine.resolve]): "2 latas" is read as "una latas", so the article and the plural agree with nothing
     * here and only the container word matters. The pattern is the regex of the words, without its whole-word edges.
     */
    val CONTAINER_WORDS: List<ContainerWord> = listOf(
        ContainerWord("botella", """media\s+botella""", 0.5),
        ContainerWord("botella", """un\s+cuarto\s+de\s+botella""", 0.25),
        ContainerWord("lata_chica", """(?:un|una)\s+(?:latas?\s+(?:chicas?|peque[ñn][oa]s?)|latitas?)""", 1.0),
        ContainerWord("lata", """(?:un|una)\s+latas?""", 1.0),
        ContainerWord("botellita", """(?:un|una)\s+botellitas?""", 1.0),
        ContainerWord("botella", """(?:un|una)\s+botellas?""", 1.0),
        ContainerWord("vasito", """(?:un|una)\s+vasit[oa]s?""", 1.0),
        ContainerWord("cajita", """(?:un|una)\s+cajitas?""", 1.0),
        ContainerWord("caja", """(?:un|una)\s+cajas?""", 1.0),
        ContainerWord("carton", """(?:un|una)\s+cart[oó](?:n|nes)""", 1.0),
        ContainerWord("bote", """(?:un|una)\s+botes?""", 1.0),
        ContainerWord("frasco", """(?:un|una)\s+frascos?""", 1.0),
        ContainerWord("bolsa", """(?:un|una)\s+bolsas?""", 1.0),
        ContainerWord("capsula", """(?:un|una)\s+c[aá]psulas?""", 1.0),
    )

    /**
     * Content of ONE container by food class (WP-N5): ml for a drink, g for a solid. A can holds 350 ml of soda or
     * beer but 170 g of tuna and 400 g of beans, so the container alone says nothing: the class of the food picks the
     * row, and [CONTAINER_DEFAULT] is the content of a container of anything else (the former fixed table). Common
     * retail sizes: soda/beer can 350 ml, small can 250 ml, water or soda bottle 500 ml, beer bottle 330 ml, wine
     * bottle 750 ml, juice carton 1 L, single-serve juice box 200 ml. Per-class values only where the size is standard.
     */
    val CONTAINER_CONTENT: Map<String, Map<String, Double>> = mapOf(
        "lata" to mapOf("bebida" to 350.0, "cerveza" to 350.0, "jugo" to 350.0, "conserva" to 170.0, "legumbre" to 400.0, CONTAINER_DEFAULT to 180.0),
        "lata_chica" to mapOf("bebida" to 250.0, "cerveza" to 250.0, "conserva" to 120.0, CONTAINER_DEFAULT to 120.0),
        "botellita" to mapOf(CONTAINER_DEFAULT to 330.0),
        "botella" to mapOf("agua" to 500.0, "bebida" to 500.0, "cerveza" to 330.0, "vino" to 750.0, CONTAINER_DEFAULT to 750.0),
        "vasito" to mapOf(CONTAINER_DEFAULT to 150.0),
        "cajita" to mapOf(CONTAINER_DEFAULT to 200.0),
        "caja" to mapOf("jugo" to 200.0, "bebida" to 200.0, CONTAINER_DEFAULT to 500.0),
        "carton" to mapOf(CONTAINER_DEFAULT to 1000.0),
        "bote" to mapOf(CONTAINER_DEFAULT to 400.0),
        "frasco" to mapOf(CONTAINER_DEFAULT to 250.0),
        "bolsa" to mapOf(CONTAINER_DEFAULT to 200.0),
        "capsula" to mapOf(CONTAINER_DEFAULT to 6.0),
    )

    // Containers that only hold liquids: their content is a volume, converted like any measured volume (see [SubjectivePortionEngine.resolve]).
    val LIQUID_CONTAINERS: Set<String> = setOf("botella", "botellita", "carton", "vasito", "cajita")

    /**
     * Food classes of [CONTAINER_CONTENT], on an accent-free key, in the order they are tried: wine, beer and canned fish go before water
     * ("atun al agua"). A word counts as a whole word of the key.
     */
    val CONTAINER_FOOD_CLASSES: List<ContainerFoodClass> = listOf(
        ContainerFoodClass("vino", listOf("vino", "vinos", "tinto", "tintos", "espumante", "espumantes")),
        ContainerFoodClass("cerveza", listOf("cerveza", "cervezas", "chela", "chelas", "schop", "schops")),
        ContainerFoodClass(
            "conserva",
            listOf(
                "atun", "jurel", "sardina", "sardinas", "caballa", "anchoa", "anchoas", "salmon", "chorito", "choritos",
                "macha", "machas", "almeja", "almejas", "mejillones",
            ),
        ),
        ContainerFoodClass(
            "legumbre",
            listOf(
                "poroto", "porotos", "lenteja", "lentejas", "garbanzo", "garbanzos", "arveja", "arvejas", "frijole",
                "frijoles", "frejole", "frejoles", "haba", "habas",
            ),
        ),
        ContainerFoodClass("agua", listOf("agua", "aguas")),
        ContainerFoodClass("jugo", listOf("jugo", "jugos", "zumo", "zumos", "nectar", "nectares")),
        ContainerFoodClass(
            "bebida",
            listOf(
                "bebida", "bebidas", "gaseosa", "gaseosas", "refresco", "refrescos", "coca", "cola", "sprite", "fanta",
                "pepsi", "energetica", "energeticas", "gatorade", "powerade", "monster",
            ),
        ),
    )

    /** IT3: utensilios editables por el usuario — nombre de patrón → ml base. */
    val UTENSIL_DEFAULT_ML: Map<String, Double> = mapOf(
        "cucharadita" to 5.0,
        "cucharada" to 15.0,
        "cucharon" to 90.0,
        // FDA household measures: cup 240 ml, tablespoon 15 ml, teaspoon 5 ml.
        // https://www.fda.gov/regulatory-information/search-fda-guidance-documents/guidance-industry-guidelines-determining-metric-equivalents-household-measures
        "taza" to 240.0,
        "vaso" to 250.0,
        "plato" to 250.0,
        "plato_hondo" to 400.0,
        "bol" to 300.0,
        "copa" to 150.0,
    )

    // ─── densities (SubjectivePortionEngine) ───────────────────────────────────────────────────────────────────────

    /** The density categories, in the order of [SubjectivePortionEngine.FoodDensityCategory]. */
    val DENSITY_CATEGORIES: List<String> = listOf("LIQUID", "POWDER", "GRAIN", "VEGETABLE", "PROTEIN", "FAT", "DAIRY", "NUTS", "FRUIT", "MIXED")

    /** Grams per ml of each category: estimates, not food composition data. */
    val DENSITY_G_PER_ML: Map<String, Double> = linkedMapOf(
        "LIQUID" to 1.0,
        "POWDER" to 0.6,
        "GRAIN" to 0.85,
        "VEGETABLE" to 0.7,
        "PROTEIN" to 1.0,
        "FAT" to 0.9,
        "DAIRY" to 1.03,
        "NUTS" to 0.65,
        "FRUIT" to 0.6,
        "MIXED" to 0.8,
    )

    /**
     * How the category of a food is told, first match wins: [DensityRule.contains] are substrings of the lower-case name and
     * [DensityRule.words] whole words. A drink is a liquid whatever it is made of ("jugo de naranja" must not weigh like an orange), so
     * the drink words go before the foods; the word lists spell out every form the former patterns accepted.
     */
    val DENSITY_RULES: List<DensityRule> = listOf(
        DensityRule(
            "DAIRY",
            contains = listOf("leche", "bebida de avena"),
            words = listOf(
                "batido", "batidos", "licuado", "licuados", "smoothie", "smoothies", "malteada", "malteadas",
            ),
        ),
        DensityRule(
            "LIQUID",
            contains = emptyList(),
            words = listOf(
                "agua", "aguas", "te", "tes", "té", "tés", "cafe", "cafes", "café", "cafés", "bebida", "bebidas", "gaseosa",
                "gaseosas", "jugo", "jugos", "zumo", "zumos", "nectar", "nectares", "néctar", "néctares", "cerveza",
                "cervezas", "chela", "chelas", "schop", "schops", "vino", "vinos", "refresco", "refrescos", "mate", "mates",
                "infusion", "infusiones", "infusión", "infusiónes", "caldo", "caldos", "coca", "sprite", "fanta", "pepsi",
            ),
        ),
        DensityRule(
            "FAT",
            contains = listOf("aceite", "mantequilla", "manteca", "ghee", "margarina", "mayonesa", "mayo"),
            words = emptyList(),
        ),
        DensityRule("POWDER", contains = listOf("azúcar", "azucar", "harina", "cacao", "canela"), words = emptyList()),
        DensityRule(
            "GRAIN",
            contains = listOf("arroz", "pasta", "quinoa", "avena", "lenteja", "garbanzo", "poroto"),
            words = emptyList(),
        ),
        DensityRule(
            "PROTEIN",
            contains = listOf(
                "pollo", "carne", "pescado", "cerdo", "vacuno", "pavo", "huevo", "merluza", "salmón", "camarón",
            ),
            words = emptyList(),
        ),
        DensityRule(
            "VEGETABLE",
            contains = listOf("lechuga", "tomate", "cebolla", "zanahoria", "espinaca", "brócoli", "pepino"),
            words = emptyList(),
        ),
        DensityRule(
            "FRUIT",
            contains = listOf("manzana", "plátano", "naranja", "uva", "frutilla", "pera"),
            words = emptyList(),
        ),
        DensityRule("DAIRY", contains = listOf("leche", "yogurt", "yogur", "queso", "crema"), words = emptyList()),
        DensityRule("NUTS", contains = listOf("almendra", "nuez", "maní", "cashew", "chía"), words = emptyList()),
    )

    const val DENSITY_FALLBACK: String = "MIXED"

    /** The Kotlin default snapshot, built from the tables above. */
    fun snapshot(): FoodKnowledgeSnapshot = FoodKnowledgeSnapshot(
        version = FOOD_KNOWLEDGE_VERSION,
        protectedPhrases = ProtectedPhrasesKnowledge(
            entityLiterals = ENTITY_LITERALS,
            fixedCompoundNames = FIXED_COMPOUND_NAMES,
            filledHeads = FILLED_HEADS,
            maizeProducts = MAIZE_PRODUCTS,
            flavors = FLAVORS,
            flavoredHeads = FLAVORED_HEADS,
            catalogExtraPhrases = CATALOG_EXTRA_PHRASES,
        ),
        typos = TYPOS,
        synonyms = SYNONYMS,
        householdUnits = HouseholdUnitsKnowledge(
            unitGramsByToken = UNIT_GRAMS_BY_TOKEN,
            notAWholePiece = NOT_A_WHOLE_PIECE,
            countableFamilies = COUNTABLE_FAMILIES,
            countableNameMarkers = COUNTABLE_NAME_MARKERS,
            familyDefaultGrams = FAMILY_DEFAULT_GRAMS,
        ),
        containers = ContainersKnowledge(
            words = CONTAINER_WORDS,
            contentByContainer = CONTAINER_CONTENT,
            liquidContainers = LIQUID_CONTAINERS,
            foodClasses = CONTAINER_FOOD_CLASSES,
        ),
        utensils = UtensilsKnowledge(defaultMl = UTENSIL_DEFAULT_ML),
        densities = DensitiesKnowledge(gramsPerMl = DENSITY_G_PER_ML, rules = DENSITY_RULES, fallbackCategory = DENSITY_FALLBACK),
    )
}
