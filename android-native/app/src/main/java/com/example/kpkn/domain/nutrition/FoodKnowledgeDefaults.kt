package com.example.kpkn.domain.nutrition

import com.example.kpkn.domain.nutrition.FoodCombinationParser.Role

/**
 * The Kotlin copy of the food knowledge (WP-N13): the tables of the `protectedPhrases`, `typos`, `synonyms`, `householdUnits`, `containers`,
 * `utensils`, `densities` and `dishCompositions` sections, moved here as they were from [ProtectedPhrases], [TextNormalizer],
 * [HouseholdPortions], [SubjectivePortionEngine] and [FoodCombinationParser]. [FoodKnowledge] serves it until the loader installs
 * `assets/food_data/food_knowledge_v1.json`, and FoodKnowledgeParityTest keeps the two equal, entry by entry and in the same order, so that
 * nothing changes whichever is in force. A later increment deletes this file and the asset becomes the only copy.
 *
 * A leaf on purpose: it reads nothing but the standard library and the roles of [FoodCombinationParser] (a plain enum: naming one starts no
 * object), so building the default snapshot can never start an object that is itself waiting for [FoodKnowledge]. Pure Kotlin / JVM: no
 * Android dependency.
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
     * 123, 131, 150, 118 and 69 g), the 182 g of a medium apple as the USDA gives it (FDC 171688, SR Legacy household weight; its large apple
     * is 242 g) and the usual 125 g cup of yogurt. [HouseholdPortions.unitGrams] applies the pieces of the countable
     * markers; the sopaipilla, the cookie and the breads keep their dedicated rows there. A food that is not here keeps its
     * portion default, and a custom food keeps the serving its owner typed.
     */
    val UNIT_GRAMS_BY_TOKEN: Map<String, Double> = buildMap {
        fun weight(grams: Double, vararg tokens: String) = tokens.forEach { put(it, grams) }
        weight(120.0, "tomate", "tomates", "jitomate", "jitomates")
        weight(150.0, "palta", "paltas", "aguacate", "aguacates")
        weight(130.0, "naranja", "naranjas")
        weight(182.0, "manzana", "manzanas")
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

    // ─── dishCompositions (FoodCombinationParser) ──────────────────────────────────────────────────────────────────

    private fun dish(name: String, vararg components: DishComponent) = DishComposition(name, components.toList())

    /**
     * The dishes the parser knows by name, as `FoodCombinationParser.KNOWN_DISHES` had them: the first component of a dish is its base and
     * the order is the order the parser tries them (the longest name wins and, between two of the same length, the first). The foods are the
     * words of a person ("papa", "jamón", "puré"): the resolver reads them like any other mention. A spelling with and without accents is
     * an entry of its own, and the proportions of a dish add up to 1.
     */
    val DISH_COMPOSITIONS: List<DishComposition> = listOf(
        dish("pan con palta", DishComponent("pan", 0.4, Role.STARCH), DishComponent("palta", 0.6, Role.SIDE)),
        dish("pan con mantequilla", DishComponent("pan", 0.7, Role.STARCH), DishComponent("mantequilla", 0.3, Role.SAUCE)),
        dish("pan con tomate", DishComponent("pan", 0.6, Role.STARCH), DishComponent("tomate", 0.4, Role.SIDE)),
        dish("pan con queso", DishComponent("pan", 0.5, Role.STARCH), DishComponent("queso", 0.5, Role.TOPPING)),
        dish("pan con jamon", DishComponent("pan", 0.5, Role.STARCH), DishComponent("jamón", 0.5, Role.TOPPING)),
        dish("pan con jamón", DishComponent("pan", 0.5, Role.STARCH), DishComponent("jamón", 0.5, Role.TOPPING)),
        dish("pan con huevo", DishComponent("pan", 0.5, Role.STARCH), DishComponent("huevo", 0.5, Role.TOPPING)),
        dish("pan con atun", DishComponent("pan", 0.5, Role.STARCH), DishComponent("atún", 0.5, Role.TOPPING)),
        dish("pan con atún", DishComponent("pan", 0.5, Role.STARCH), DishComponent("atún", 0.5, Role.TOPPING)),
        dish("pan con pollo", DishComponent("pan", 0.5, Role.STARCH), DishComponent("pollo", 0.5, Role.TOPPING)),
        dish("pan con salmon", DishComponent("pan", 0.5, Role.STARCH), DishComponent("salmón", 0.5, Role.TOPPING)),
        dish("pan con palta y huevo", DishComponent("pan", 0.35, Role.STARCH), DishComponent("palta", 0.35, Role.SIDE), DishComponent("huevo", 0.3, Role.TOPPING)),
        dish("pan con palta y jamon", DishComponent("pan", 0.4, Role.STARCH), DishComponent("palta", 0.35, Role.SIDE), DishComponent("jamón", 0.25, Role.TOPPING)),
        dish("pan con palta y jamón", DishComponent("pan", 0.4, Role.STARCH), DishComponent("palta", 0.35, Role.SIDE), DishComponent("jamón", 0.25, Role.TOPPING)),
        dish("pan con tomate y aceite", DishComponent("pan", 0.6, Role.STARCH), DishComponent("tomate", 0.3, Role.SIDE), DishComponent("aceite", 0.1, Role.SAUCE)),
        dish("pan con tomate y jamon", DishComponent("pan", 0.4, Role.STARCH), DishComponent("tomate", 0.3, Role.SIDE), DishComponent("jamón", 0.3, Role.TOPPING)),
        dish("pan con tomate y jamón", DishComponent("pan", 0.4, Role.STARCH), DishComponent("tomate", 0.3, Role.SIDE), DishComponent("jamón", 0.3, Role.TOPPING)),
        dish("pan con mantequilla y mermelada", DishComponent("pan", 0.5, Role.STARCH), DishComponent("mantequilla", 0.25, Role.SAUCE), DishComponent("mermelada", 0.25, Role.SAUCE)),
        dish("pan con palta y huevo duro", DishComponent("pan", 0.35, Role.STARCH), DishComponent("palta", 0.35, Role.SIDE), DishComponent("huevo", 0.3, Role.TOPPING)),

        dish("arroz con pollo", DishComponent("arroz", 0.5, Role.STARCH), DishComponent("pollo", 0.5, Role.TOPPING)),
        dish("arroz con huevo", DishComponent("arroz", 0.6, Role.STARCH), DishComponent("huevo", 0.4, Role.TOPPING)),
        dish("arroz con huevo frito", DishComponent("arroz", 0.55, Role.STARCH), DishComponent("huevo", 0.45, Role.TOPPING)),
        dish("arroz con atun", DishComponent("arroz", 0.5, Role.STARCH), DishComponent("atún", 0.5, Role.TOPPING)),
        dish("arroz con atún", DishComponent("arroz", 0.5, Role.STARCH), DishComponent("atún", 0.5, Role.TOPPING)),
        dish("arroz con verduras", DishComponent("arroz", 0.6, Role.STARCH), DishComponent("verduras", 0.4, Role.SIDE)),
        dish("arroz con frijoles", DishComponent("arroz", 0.5, Role.STARCH), DishComponent("frijoles", 0.5, Role.SIDE)),
        dish("arroz con porotos", DishComponent("arroz", 0.5, Role.STARCH), DishComponent("porotos", 0.5, Role.SIDE)),
        dish("arroz con lentejas", DishComponent("arroz", 0.5, Role.STARCH), DishComponent("lentejas", 0.5, Role.SIDE)),
        dish("arroz con garbanzos", DishComponent("arroz", 0.5, Role.STARCH), DishComponent("garbanzos", 0.5, Role.SIDE)),
        dish("arroz con carne", DishComponent("arroz", 0.5, Role.STARCH), DishComponent("carne", 0.5, Role.TOPPING)),
        dish("arroz con pescado", DishComponent("arroz", 0.5, Role.STARCH), DishComponent("pescado", 0.5, Role.TOPPING)),
        dish("arroz con camaron", DishComponent("arroz", 0.5, Role.STARCH), DishComponent("camarón", 0.5, Role.TOPPING)),
        dish("arroz con camarones", DishComponent("arroz", 0.5, Role.STARCH), DishComponent("camarón", 0.5, Role.TOPPING)),
        dish("arroz con mariscos", DishComponent("arroz", 0.5, Role.STARCH), DishComponent("mariscos", 0.5, Role.TOPPING)),
        dish("arroz con chorizo", DishComponent("arroz", 0.5, Role.STARCH), DishComponent("chorizo", 0.5, Role.TOPPING)),
        dish("arroz con tocino", DishComponent("arroz", 0.5, Role.STARCH), DishComponent("tocino", 0.5, Role.TOPPING)),
        dish("arroz con leche", DishComponent("arroz", 0.4, Role.STARCH), DishComponent("leche", 0.6, Role.SIDE)),
        dish("arroz con coco", DishComponent("arroz", 0.5, Role.STARCH), DishComponent("coco", 0.5, Role.SIDE)),
        dish("arroz con curry", DishComponent("arroz", 0.5, Role.STARCH), DishComponent("curry", 0.5, Role.SAUCE)),
        dish("arroz chaufa", DishComponent("arroz", 0.5, Role.STARCH), DishComponent("pollo", 0.3, Role.TOPPING), DishComponent("verduras", 0.2, Role.SIDE)),
        dish("arroz chaufa de pollo", DishComponent("arroz", 0.5, Role.STARCH), DishComponent("pollo", 0.3, Role.TOPPING), DishComponent("verduras", 0.2, Role.SIDE)),
        dish("arroz a la cubana", DishComponent("arroz", 0.4, Role.STARCH), DishComponent("huevo frito", 0.3, Role.TOPPING), DishComponent("platano frito", 0.3, Role.SIDE)),
        dish("arroz tres delicias", DishComponent("arroz", 0.5, Role.STARCH), DishComponent("pollo", 0.2, Role.TOPPING), DishComponent("camarón", 0.15, Role.TOPPING), DishComponent("verduras", 0.15, Role.SIDE)),
        dish("arroz frito con verduras", DishComponent("arroz", 0.5, Role.STARCH), DishComponent("verduras", 0.3, Role.SIDE), DishComponent("huevo", 0.2, Role.TOPPING)),
        dish("arroz frito con pollo", DishComponent("arroz", 0.5, Role.STARCH), DishComponent("pollo", 0.3, Role.TOPPING), DishComponent("verduras", 0.2, Role.SIDE)),

        dish("pasta con salsa de tomate", DishComponent("pasta", 0.6, Role.STARCH), DishComponent("salsa de tomate", 0.4, Role.SAUCE)),
        dish("pasta con boloñesa", DishComponent("pasta", 0.5, Role.STARCH), DishComponent("carne molida", 0.3, Role.TOPPING), DishComponent("salsa de tomate", 0.2, Role.SAUCE)),
        dish("pasta con salsa bolonesa", DishComponent("pasta", 0.5, Role.STARCH), DishComponent("carne molida", 0.3, Role.TOPPING), DishComponent("salsa de tomate", 0.2, Role.SAUCE)),
        dish("pasta con pesto", DishComponent("pasta", 0.6, Role.STARCH), DishComponent("pesto", 0.4, Role.SAUCE)),
        dish("pasta con carbonara", DishComponent("pasta", 0.5, Role.STARCH), DishComponent("huevo", 0.2, Role.SAUCE), DishComponent("tocino", 0.2, Role.TOPPING), DishComponent("queso", 0.1, Role.TOPPING)),
        dish("pasta con atun", DishComponent("pasta", 0.5, Role.STARCH), DishComponent("atún", 0.5, Role.TOPPING)),
        dish("pasta con atún", DishComponent("pasta", 0.5, Role.STARCH), DishComponent("atún", 0.5, Role.TOPPING)),
        dish("pasta con pollo", DishComponent("pasta", 0.5, Role.STARCH), DishComponent("pollo", 0.5, Role.TOPPING)),
        dish("pasta con carne", DishComponent("pasta", 0.5, Role.STARCH), DishComponent("carne", 0.5, Role.TOPPING)),
        dish("pasta con verduras", DishComponent("pasta", 0.6, Role.STARCH), DishComponent("verduras", 0.4, Role.SIDE)),
        dish("pasta con salmon", DishComponent("pasta", 0.5, Role.STARCH), DishComponent("salmón", 0.5, Role.TOPPING)),
        dish("pasta con camaron", DishComponent("pasta", 0.5, Role.STARCH), DishComponent("camarón", 0.5, Role.TOPPING)),
        dish("pasta con champinones", DishComponent("pasta", 0.6, Role.STARCH), DishComponent("champiñones", 0.4, Role.SIDE)),
        dish("pasta con champiñones", DishComponent("pasta", 0.6, Role.STARCH), DishComponent("champiñones", 0.4, Role.SIDE)),
        dish("pasta con crema", DishComponent("pasta", 0.6, Role.STARCH), DishComponent("crema", 0.4, Role.SAUCE)),
        dish("pasta con queso", DishComponent("pasta", 0.6, Role.STARCH), DishComponent("queso", 0.4, Role.TOPPING)),
        dish("pasta con mantequilla", DishComponent("pasta", 0.7, Role.STARCH), DishComponent("mantequilla", 0.3, Role.SAUCE)),

        dish("huevos fritos con papas", DishComponent("huevo", 0.4, Role.TOPPING), DishComponent("papa", 0.6, Role.STARCH)),
        dish("huevos fritos con patatas", DishComponent("huevo", 0.4, Role.TOPPING), DishComponent("papa", 0.6, Role.STARCH)),
        dish("huevos fritos con chorizo", DishComponent("huevo", 0.5, Role.TOPPING), DishComponent("chorizo", 0.5, Role.TOPPING)),
        dish("huevos fritos con jamon", DishComponent("huevo", 0.5, Role.TOPPING), DishComponent("jamón", 0.5, Role.TOPPING)),
        dish("huevos fritos con jamón", DishComponent("huevo", 0.5, Role.TOPPING), DishComponent("jamón", 0.5, Role.TOPPING)),
        dish("huevos fritos con tocino", DishComponent("huevo", 0.5, Role.TOPPING), DishComponent("tocino", 0.5, Role.TOPPING)),
        dish("huevos fritos con arroz", DishComponent("huevo", 0.4, Role.TOPPING), DishComponent("arroz", 0.6, Role.STARCH)),
        dish("huevos revueltos con jamon", DishComponent("huevo", 0.6, Role.TOPPING), DishComponent("jamón", 0.4, Role.TOPPING)),
        dish("huevos revueltos con jamón", DishComponent("huevo", 0.6, Role.TOPPING), DishComponent("jamón", 0.4, Role.TOPPING)),
        dish("huevos revueltos con queso", DishComponent("huevo", 0.6, Role.TOPPING), DishComponent("queso", 0.4, Role.TOPPING)),
        dish("huevos revueltos con chorizo", DishComponent("huevo", 0.5, Role.TOPPING), DishComponent("chorizo", 0.5, Role.TOPPING)),
        dish("huevos revueltos con tocino", DishComponent("huevo", 0.5, Role.TOPPING), DishComponent("tocino", 0.5, Role.TOPPING)),
        dish("huevos revueltos con verduras", DishComponent("huevo", 0.6, Role.TOPPING), DishComponent("verduras", 0.4, Role.SIDE)),
        dish("huevos revueltos con tomate", DishComponent("huevo", 0.6, Role.TOPPING), DishComponent("tomate", 0.4, Role.SIDE)),
        dish("huevos revueltos con champinones", DishComponent("huevo", 0.6, Role.TOPPING), DishComponent("champiñones", 0.4, Role.SIDE)),
        dish("huevos revueltos con champiñones", DishComponent("huevo", 0.6, Role.TOPPING), DishComponent("champiñones", 0.4, Role.SIDE)),
        dish("huevos revueltos con espinaca", DishComponent("huevo", 0.6, Role.TOPPING), DishComponent("espinaca", 0.4, Role.SIDE)),
        dish("huevos revueltos con espinacas", DishComponent("huevo", 0.6, Role.TOPPING), DishComponent("espinacas", 0.4, Role.SIDE)),

        dish("pollo con papas", DishComponent("pollo", 0.5, Role.TOPPING), DishComponent("papa", 0.5, Role.STARCH)),
        dish("pollo con patatas", DishComponent("pollo", 0.5, Role.TOPPING), DishComponent("papa", 0.5, Role.STARCH)),
        dish("pollo con arroz", DishComponent("pollo", 0.5, Role.TOPPING), DishComponent("arroz", 0.5, Role.STARCH)),
        dish("pollo con ensalada", DishComponent("pollo", 0.6, Role.TOPPING), DishComponent("ensalada", 0.4, Role.SIDE)),
        dish("pollo con verduras", DishComponent("pollo", 0.5, Role.TOPPING), DishComponent("verduras", 0.5, Role.SIDE)),
        dish("pollo con pure", DishComponent("pollo", 0.5, Role.TOPPING), DishComponent("puré", 0.5, Role.STARCH)),
        dish("pollo con pasta", DishComponent("pollo", 0.5, Role.TOPPING), DishComponent("pasta", 0.5, Role.STARCH)),
        dish("pollo con frijoles", DishComponent("pollo", 0.5, Role.TOPPING), DishComponent("frijoles", 0.5, Role.SIDE)),
        dish("pollo con lentejas", DishComponent("pollo", 0.5, Role.TOPPING), DishComponent("lentejas", 0.5, Role.SIDE)),
        dish("pollo con champinones", DishComponent("pollo", 0.5, Role.TOPPING), DishComponent("champiñones", 0.5, Role.SIDE)),
        dish("pollo con champiñones", DishComponent("pollo", 0.5, Role.TOPPING), DishComponent("champiñones", 0.5, Role.SIDE)),
        dish("pollo con curry", DishComponent("pollo", 0.5, Role.TOPPING), DishComponent("curry", 0.3, Role.SAUCE), DishComponent("arroz", 0.2, Role.STARCH)),
        dish("pollo con arroz y ensalada", DishComponent("pollo", 0.4, Role.TOPPING), DishComponent("arroz", 0.35, Role.STARCH), DishComponent("ensalada", 0.25, Role.SIDE)),
        dish("pollo a la plancha con verduras", DishComponent("pollo", 0.5, Role.TOPPING), DishComponent("verduras", 0.5, Role.SIDE)),
        dish("pollo a la plancha con ensalada", DishComponent("pollo", 0.6, Role.TOPPING), DishComponent("ensalada", 0.4, Role.SIDE)),
        dish("pollo a la plancha con arroz", DishComponent("pollo", 0.5, Role.TOPPING), DishComponent("arroz", 0.5, Role.STARCH)),
        dish("pollo al horno con papas", DishComponent("pollo", 0.5, Role.TOPPING), DishComponent("papa", 0.5, Role.STARCH)),
        dish("pollo guisado con arroz", DishComponent("pollo", 0.4, Role.TOPPING), DishComponent("arroz", 0.4, Role.STARCH), DishComponent("salsa", 0.2, Role.SAUCE)),
        dish("pollo al curry con arroz", DishComponent("pollo", 0.4, Role.TOPPING), DishComponent("arroz", 0.4, Role.STARCH), DishComponent("curry", 0.2, Role.SAUCE)),
        dish("milanesa de pollo con papas fritas", DishComponent("pollo empanizado", 0.4, Role.TOPPING), DishComponent("papa frita", 0.6, Role.STARCH)),
        dish("milanesa de pollo con pure", DishComponent("pollo empanizado", 0.5, Role.TOPPING), DishComponent("puré", 0.5, Role.STARCH)),
        dish("milanesa de pollo con arroz", DishComponent("pollo empanizado", 0.5, Role.TOPPING), DishComponent("arroz", 0.5, Role.STARCH)),
        dish("milanesa de pollo con ensalada", DishComponent("pollo empanizado", 0.6, Role.TOPPING), DishComponent("ensalada", 0.4, Role.SIDE)),
        dish("milanesa napolitana", DishComponent("pollo empanizado", 0.4, Role.TOPPING), DishComponent("salsa de tomate", 0.2, Role.SAUCE), DishComponent("queso", 0.2, Role.TOPPING), DishComponent("jamon", 0.2, Role.TOPPING)),
        dish("milanesa napolitana con papas", DishComponent("pollo empanizado", 0.3, Role.TOPPING), DishComponent("salsa de tomate", 0.15, Role.SAUCE), DishComponent("queso", 0.15, Role.TOPPING), DishComponent("papa", 0.4, Role.STARCH)),

        dish("bistec con papas", DishComponent("bistec", 0.5, Role.TOPPING), DishComponent("papa", 0.5, Role.STARCH)),
        dish("bistec con patatas", DishComponent("bistec", 0.5, Role.TOPPING), DishComponent("papa", 0.5, Role.STARCH)),
        dish("bistec con arroz", DishComponent("bistec", 0.5, Role.TOPPING), DishComponent("arroz", 0.5, Role.STARCH)),
        dish("bistec con ensalada", DishComponent("bistec", 0.6, Role.TOPPING), DishComponent("ensalada", 0.4, Role.SIDE)),
        dish("bistec con pure", DishComponent("bistec", 0.5, Role.TOPPING), DishComponent("puré", 0.5, Role.STARCH)),
        dish("bistec con verduras", DishComponent("bistec", 0.5, Role.TOPPING), DishComponent("verduras", 0.5, Role.SIDE)),
        dish("bistec con frijoles", DishComponent("bistec", 0.5, Role.TOPPING), DishComponent("frijoles", 0.5, Role.SIDE)),
        dish("bistec a la plancha con ensalada", DishComponent("bistec", 0.6, Role.TOPPING), DishComponent("ensalada", 0.4, Role.SIDE)),
        dish("hamburguesa con papas fritas", DishComponent("hamburguesa", 0.5, Role.TOPPING), DishComponent("papa frita", 0.5, Role.STARCH)),
        dish("hamburguesa con queso", DishComponent("hamburguesa", 0.7, Role.TOPPING), DishComponent("queso", 0.3, Role.TOPPING)),
        dish("hamburguesa con tocino", DishComponent("hamburguesa", 0.7, Role.TOPPING), DishComponent("tocino", 0.3, Role.TOPPING)),
        dish("hamburguesa con huevo", DishComponent("hamburguesa", 0.7, Role.TOPPING), DishComponent("huevo", 0.3, Role.TOPPING)),
        dish("hamburguesa completa", DishComponent("hamburguesa", 0.5, Role.TOPPING), DishComponent("papa frita", 0.3, Role.STARCH), DishComponent("bebida", 0.2, Role.SIDE)),

        dish("pescado con arroz", DishComponent("pescado", 0.5, Role.TOPPING), DishComponent("arroz", 0.5, Role.STARCH)),
        dish("pescado con papas", DishComponent("pescado", 0.5, Role.TOPPING), DishComponent("papa", 0.5, Role.STARCH)),
        dish("pescado con patatas", DishComponent("pescado", 0.5, Role.TOPPING), DishComponent("papa", 0.5, Role.STARCH)),
        dish("pescado con pure", DishComponent("pescado", 0.5, Role.TOPPING), DishComponent("puré", 0.5, Role.STARCH)),
        dish("pescado con ensalada", DishComponent("pescado", 0.6, Role.TOPPING), DishComponent("ensalada", 0.4, Role.SIDE)),
        dish("pescado con verduras", DishComponent("pescado", 0.5, Role.TOPPING), DishComponent("verduras", 0.5, Role.SIDE)),
        dish("pescado a la plancha con ensalada", DishComponent("pescado", 0.6, Role.TOPPING), DishComponent("ensalada", 0.4, Role.SIDE)),
        dish("pescado al horno con papas", DishComponent("pescado", 0.5, Role.TOPPING), DishComponent("papa", 0.5, Role.STARCH)),
        dish("pescado frito con arroz", DishComponent("pescado frito", 0.5, Role.TOPPING), DishComponent("arroz", 0.5, Role.STARCH)),
        dish("salmon con verduras", DishComponent("salmón", 0.5, Role.TOPPING), DishComponent("verduras", 0.5, Role.SIDE)),
        dish("salmon con arroz", DishComponent("salmón", 0.5, Role.TOPPING), DishComponent("arroz", 0.5, Role.STARCH)),
        dish("salmon con pure", DishComponent("salmón", 0.5, Role.TOPPING), DishComponent("puré", 0.5, Role.STARCH)),
        dish("salmon con ensalada", DishComponent("salmón", 0.6, Role.TOPPING), DishComponent("ensalada", 0.4, Role.SIDE)),
        dish("salmon a la plancha con verduras", DishComponent("salmón", 0.5, Role.TOPPING), DishComponent("verduras", 0.5, Role.SIDE)),
        dish("camarones con arroz", DishComponent("camarón", 0.5, Role.TOPPING), DishComponent("arroz", 0.5, Role.STARCH)),
        dish("camarones con pasta", DishComponent("camarón", 0.5, Role.TOPPING), DishComponent("pasta", 0.5, Role.STARCH)),
        dish("camarones con ensalada", DishComponent("camarón", 0.6, Role.TOPPING), DishComponent("ensalada", 0.4, Role.SIDE)),
        dish("camarones al ajillo con arroz", DishComponent("camarón", 0.5, Role.TOPPING), DishComponent("arroz", 0.5, Role.STARCH)),

        dish("ensalada de lechuga y tomate", DishComponent("lechuga", 0.5, Role.SIDE), DishComponent("tomate", 0.5, Role.SIDE)),
        dish("ensalada de lechuga, tomate y cebolla", DishComponent("lechuga", 0.4, Role.SIDE), DishComponent("tomate", 0.35, Role.SIDE), DishComponent("cebolla", 0.25, Role.SIDE)),
        dish("ensalada cesar con pollo", DishComponent("lechuga", 0.4, Role.SIDE), DishComponent("pollo", 0.4, Role.TOPPING), DishComponent("aderezo cesar", 0.2, Role.SAUCE)),
        dish("ensalada griega con queso feta", DishComponent("lechuga", 0.3, Role.SIDE), DishComponent("tomate", 0.2, Role.SIDE), DishComponent("pepino", 0.2, Role.SIDE), DishComponent("queso feta", 0.3, Role.TOPPING)),
        dish("ensalada de atun con aceitunas", DishComponent("atún", 0.4, Role.TOPPING), DishComponent("lechuga", 0.3, Role.SIDE), DishComponent("aceitunas", 0.3, Role.SIDE)),
        dish("ensalada de papa con mayonesa", DishComponent("papa", 0.7, Role.STARCH), DishComponent("mayonesa", 0.3, Role.SAUCE)),
        dish("ensalada rusa", DishComponent("papa", 0.3, Role.STARCH), DishComponent("zanahoria", 0.2, Role.SIDE), DishComponent("arveja", 0.2, Role.SIDE), DishComponent("mayonesa", 0.3, Role.SAUCE)),
        dish("ensaladilla rusa", DishComponent("papa", 0.3, Role.STARCH), DishComponent("zanahoria", 0.2, Role.SIDE), DishComponent("arveja", 0.2, Role.SIDE), DishComponent("mayonesa", 0.3, Role.SAUCE)),

        dish("sopa de pollo con fideos", DishComponent("pollo", 0.3, Role.TOPPING), DishComponent("fideos", 0.3, Role.STARCH), DishComponent("caldo", 0.4, Role.SAUCE)),
        dish("sopa de pollo con verduras", DishComponent("pollo", 0.3, Role.TOPPING), DishComponent("verduras", 0.3, Role.SIDE), DishComponent("caldo", 0.4, Role.SAUCE)),
        dish("sopa de pollo con arroz", DishComponent("pollo", 0.3, Role.TOPPING), DishComponent("arroz", 0.3, Role.STARCH), DishComponent("caldo", 0.4, Role.SAUCE)),
        dish("caldo de pollo con verduras", DishComponent("pollo", 0.25, Role.TOPPING), DishComponent("verduras", 0.35, Role.SIDE), DishComponent("caldo", 0.4, Role.SAUCE)),
        dish("sopa de lentejas con verduras", DishComponent("lentejas", 0.5, Role.SIDE), DishComponent("verduras", 0.3, Role.SIDE), DishComponent("caldo", 0.2, Role.SAUCE)),
        dish("sopa de lentejas con chorizo", DishComponent("lentejas", 0.5, Role.SIDE), DishComponent("chorizo", 0.3, Role.TOPPING), DishComponent("caldo", 0.2, Role.SAUCE)),
        dish("cazuela de vacuno", DishComponent("carne", 0.25, Role.TOPPING), DishComponent("papa", 0.2, Role.STARCH), DishComponent("zapallo", 0.15, Role.SIDE), DishComponent("choclo", 0.1, Role.SIDE), DishComponent("caldo", 0.3, Role.SAUCE)),
        dish("cazuela de pollo", DishComponent("pollo", 0.25, Role.TOPPING), DishComponent("papa", 0.2, Role.STARCH), DishComponent("zapallo", 0.15, Role.SIDE), DishComponent("choclo", 0.1, Role.SIDE), DishComponent("caldo", 0.3, Role.SAUCE)),

        dish("tortilla de patatas", DishComponent("papa", 0.6, Role.STARCH), DishComponent("huevo", 0.4, Role.TOPPING)),
        dish("tortilla de papas", DishComponent("papa", 0.6, Role.STARCH), DishComponent("huevo", 0.4, Role.TOPPING)),
        dish("tortilla de patatas con cebolla", DishComponent("papa", 0.5, Role.STARCH), DishComponent("huevo", 0.35, Role.TOPPING), DishComponent("cebolla", 0.15, Role.SIDE)),
        dish("tortilla de papas con cebolla", DishComponent("papa", 0.5, Role.STARCH), DishComponent("huevo", 0.35, Role.TOPPING), DishComponent("cebolla", 0.15, Role.SIDE)),
        dish("tortilla de espinaca", DishComponent("espinaca", 0.5, Role.SIDE), DishComponent("huevo", 0.5, Role.TOPPING)),
        dish("tortilla de espinacas", DishComponent("espinaca", 0.5, Role.SIDE), DishComponent("huevo", 0.5, Role.TOPPING)),
        dish("tortilla de atun", DishComponent("atún", 0.5, Role.TOPPING), DishComponent("huevo", 0.5, Role.TOPPING)),
        dish("tortilla de atún", DishComponent("atún", 0.5, Role.TOPPING), DishComponent("huevo", 0.5, Role.TOPPING)),
        dish("tortilla de jamon y queso", DishComponent("jamón", 0.3, Role.TOPPING), DishComponent("queso", 0.3, Role.TOPPING), DishComponent("huevo", 0.4, Role.TOPPING)),
        dish("tortilla de jamón y queso", DishComponent("jamón", 0.3, Role.TOPPING), DishComponent("queso", 0.3, Role.TOPPING), DishComponent("huevo", 0.4, Role.TOPPING)),

        dish("porotos con riendas", DishComponent("porotos", 0.5, Role.SIDE), DishComponent("fideos", 0.3, Role.STARCH), DishComponent("caldo", 0.2, Role.SAUCE)),
        dish("porotos granados", DishComponent("porotos", 0.4, Role.SIDE), DishComponent("choclo", 0.3, Role.SIDE), DishComponent("zapallo", 0.2, Role.SIDE), DishComponent("caldo", 0.1, Role.SAUCE)),
        dish("charquican", DishComponent("carne", 0.2, Role.TOPPING), DishComponent("papa", 0.2, Role.STARCH), DishComponent("zapallo", 0.15, Role.SIDE), DishComponent("choclo", 0.15, Role.SIDE), DishComponent("caldo", 0.3, Role.SAUCE)),
        dish("pastel de choclo", DishComponent("choclo", 0.5, Role.STARCH), DishComponent("carne", 0.3, Role.TOPPING), DishComponent("huevo", 0.1, Role.TOPPING), DishComponent("aceituna", 0.1, Role.SIDE)),
        dish("humitas", DishComponent("choclo", 0.8, Role.STARCH), DishComponent("cebolla", 0.1, Role.SIDE), DishComponent("albahaca", 0.1, Role.GARNISH)),
        dish("empanada de pino", DishComponent("masa", 0.4, Role.STARCH), DishComponent("carne molida", 0.3, Role.TOPPING), DishComponent("cebolla", 0.15, Role.SIDE), DishComponent("huevo", 0.1, Role.TOPPING), DishComponent("aceituna", 0.05, Role.SIDE)),

        dish("cafe con leche", DishComponent("café", 0.3, Role.SIDE), DishComponent("leche", 0.7, Role.SIDE)),
        dish("café con leche", DishComponent("café", 0.3, Role.SIDE), DishComponent("leche", 0.7, Role.SIDE)),
        dish("cafe con azucar", DishComponent("café", 0.8, Role.SIDE), DishComponent("azúcar", 0.2, Role.SAUCE)),
        dish("café con azúcar", DishComponent("café", 0.8, Role.SIDE), DishComponent("azúcar", 0.2, Role.SAUCE)),
        dish("leche con chocolate", DishComponent("leche", 0.7, Role.SIDE), DishComponent("chocolate", 0.3, Role.SAUCE)),
        dish("yogurt con fruta", DishComponent("yogurt", 0.6, Role.SIDE), DishComponent("fruta", 0.4, Role.SIDE)),
        dish("yogurt con granola", DishComponent("yogurt", 0.5, Role.SIDE), DishComponent("granola", 0.5, Role.TOPPING)),
        dish("avena con leche", DishComponent("avena", 0.4, Role.STARCH), DishComponent("leche", 0.6, Role.SIDE)),
        dish("avena con frutas", DishComponent("avena", 0.5, Role.STARCH), DishComponent("fruta", 0.5, Role.SIDE)),
        dish("avena con platano", DishComponent("avena", 0.5, Role.STARCH), DishComponent("plátano", 0.5, Role.SIDE)),
        dish("avena con banana", DishComponent("avena", 0.5, Role.STARCH), DishComponent("plátano", 0.5, Role.SIDE)),
        dish("avena con manzana", DishComponent("avena", 0.5, Role.STARCH), DishComponent("manzana", 0.5, Role.SIDE)),
        dish("avena con miel", DishComponent("avena", 0.7, Role.STARCH), DishComponent("miel", 0.3, Role.SAUCE)),
        dish("avena con canela", DishComponent("avena", 0.9, Role.STARCH), DishComponent("canela", 0.1, Role.GARNISH)),
        dish("avena con nueces", DishComponent("avena", 0.6, Role.STARCH), DishComponent("nueces", 0.4, Role.TOPPING)),
        dish("avena con almendras", DishComponent("avena", 0.6, Role.STARCH), DishComponent("almendras", 0.4, Role.TOPPING)),
        dish("avena con chia", DishComponent("avena", 0.7, Role.STARCH), DishComponent("chía", 0.3, Role.TOPPING)),
        dish("avena con chía", DishComponent("avena", 0.7, Role.STARCH), DishComponent("chía", 0.3, Role.TOPPING)),
        dish("avena con yogurt", DishComponent("avena", 0.5, Role.STARCH), DishComponent("yogurt", 0.5, Role.SIDE)),
        dish("avena con chocolate", DishComponent("avena", 0.6, Role.STARCH), DishComponent("chocolate", 0.4, Role.SAUCE)),
        dish("granola con yogurt", DishComponent("granola", 0.4, Role.TOPPING), DishComponent("yogurt", 0.6, Role.SIDE)),
        dish("granola con leche", DishComponent("granola", 0.4, Role.TOPPING), DishComponent("leche", 0.6, Role.SIDE)),
        dish("cereal con leche", DishComponent("cereal", 0.3, Role.TOPPING), DishComponent("leche", 0.7, Role.SIDE)),

        dish("fruta con yogurt", DishComponent("fruta", 0.5, Role.SIDE), DishComponent("yogurt", 0.5, Role.SIDE)),
        dish("fruta con crema", DishComponent("fruta", 0.6, Role.SIDE), DishComponent("crema", 0.4, Role.SAUCE)),
        dish("fruta con miel", DishComponent("fruta", 0.8, Role.SIDE), DishComponent("miel", 0.2, Role.SAUCE)),
        dish("fruta con chocolate", DishComponent("fruta", 0.6, Role.SIDE), DishComponent("chocolate", 0.4, Role.SAUCE)),
        dish("fruta con granola", DishComponent("fruta", 0.5, Role.SIDE), DishComponent("granola", 0.5, Role.TOPPING)),
        dish("fresas con crema", DishComponent("fresa", 0.6, Role.SIDE), DishComponent("crema", 0.4, Role.SAUCE)),
        dish("frutillas con crema", DishComponent("frutilla", 0.6, Role.SIDE), DishComponent("crema", 0.4, Role.SAUCE)),
        dish("platano con dulce de leche", DishComponent("plátano", 0.6, Role.SIDE), DishComponent("dulce de leche", 0.4, Role.SAUCE)),
        dish("banana con dulce de leche", DishComponent("plátano", 0.6, Role.SIDE), DishComponent("dulce de leche", 0.4, Role.SAUCE)),
        dish("manzana con canela", DishComponent("manzana", 0.9, Role.SIDE), DishComponent("canela", 0.1, Role.GARNISH)),
        dish("manzana con miel", DishComponent("manzana", 0.8, Role.SIDE), DishComponent("miel", 0.2, Role.SAUCE)),
        dish("melon con jamon", DishComponent("melón", 0.6, Role.SIDE), DishComponent("jamón", 0.4, Role.TOPPING)),
        dish("melon con jamón", DishComponent("melón", 0.6, Role.SIDE), DishComponent("jamón", 0.4, Role.TOPPING)),
        dish("sandia con queso", DishComponent("sandía", 0.7, Role.SIDE), DishComponent("queso", 0.3, Role.TOPPING)),
        dish("sandía con queso", DishComponent("sandía", 0.7, Role.SIDE), DishComponent("queso", 0.3, Role.TOPPING)),
        dish("mango con yogurt", DishComponent("mango", 0.5, Role.SIDE), DishComponent("yogurt", 0.5, Role.SIDE)),

        dish("arroz con leche y canela", DishComponent("arroz", 0.4, Role.STARCH), DishComponent("leche", 0.5, Role.SIDE), DishComponent("canela", 0.1, Role.GARNISH)),
        dish("arroz con leche con pasas", DishComponent("arroz", 0.4, Role.STARCH), DishComponent("leche", 0.45, Role.SIDE), DishComponent("pasas", 0.15, Role.TOPPING)),
        dish("flan con dulce de leche", DishComponent("flan", 0.6, Role.SIDE), DishComponent("dulce de leche", 0.4, Role.SAUCE)),
        dish("flan con crema", DishComponent("flan", 0.6, Role.SIDE), DishComponent("crema", 0.4, Role.SAUCE)),
        dish("helado con chocolate", DishComponent("helado", 0.6, Role.SIDE), DishComponent("chocolate", 0.4, Role.SAUCE)),
        dish("helado con frutas", DishComponent("helado", 0.5, Role.SIDE), DishComponent("fruta", 0.5, Role.TOPPING)),
        dish("helado con nueces", DishComponent("helado", 0.6, Role.SIDE), DishComponent("nueces", 0.4, Role.TOPPING)),
        dish("brownie con helado", DishComponent("brownie", 0.5, Role.SIDE), DishComponent("helado", 0.5, Role.TOPPING)),
        dish("churros con chocolate", DishComponent("churros", 0.5, Role.SIDE), DishComponent("chocolate", 0.5, Role.SAUCE)),
        dish("churros con dulce de leche", DishComponent("churros", 0.5, Role.SIDE), DishComponent("dulce de leche", 0.5, Role.SAUCE)),
        dish("tres leches con crema", DishComponent("tres leches", 0.6, Role.SIDE), DishComponent("crema", 0.4, Role.SAUCE)),
        dish("cheesecake con mermelada", DishComponent("cheesecake", 0.7, Role.SIDE), DishComponent("mermelada", 0.3, Role.SAUCE)),
        dish("cheesecake con frutos rojos", DishComponent("cheesecake", 0.6, Role.SIDE), DishComponent("frutos rojos", 0.4, Role.TOPPING)),

        dish("arepa con queso", DishComponent("arepa", 0.5, Role.STARCH), DishComponent("queso", 0.5, Role.TOPPING)),
        dish("arepa con jamon y queso", DishComponent("arepa", 0.4, Role.STARCH), DishComponent("jamón", 0.3, Role.TOPPING), DishComponent("queso", 0.3, Role.TOPPING)),
        dish("arepa con jamón y queso", DishComponent("arepa", 0.4, Role.STARCH), DishComponent("jamón", 0.3, Role.TOPPING), DishComponent("queso", 0.3, Role.TOPPING)),
        dish("arepa con carne mechada", DishComponent("arepa", 0.4, Role.STARCH), DishComponent("carne mechada", 0.6, Role.TOPPING)),
        dish("arepa reina pepiada", DishComponent("arepa", 0.3, Role.STARCH), DishComponent("pollo", 0.35, Role.TOPPING), DishComponent("palta", 0.35, Role.TOPPING)),
        dish("arepa con pollo y aguacate", DishComponent("arepa", 0.3, Role.STARCH), DishComponent("pollo", 0.35, Role.TOPPING), DishComponent("palta", 0.35, Role.TOPPING)),
        dish("arepa con huevo", DishComponent("arepa", 0.5, Role.STARCH), DishComponent("huevo", 0.5, Role.TOPPING)),
        dish("arepa con mantequilla", DishComponent("arepa", 0.7, Role.STARCH), DishComponent("mantequilla", 0.3, Role.SAUCE)),
        dish("arepa con pernil", DishComponent("arepa", 0.4, Role.STARCH), DishComponent("pernil", 0.6, Role.TOPPING)),
        dish("arepa con chorizo", DishComponent("arepa", 0.4, Role.STARCH), DishComponent("chorizo", 0.6, Role.TOPPING)),
        dish("arepa con chicharron", DishComponent("arepa", 0.4, Role.STARCH), DishComponent("chicharrón", 0.6, Role.TOPPING)),
        dish("arepa con chicharrón", DishComponent("arepa", 0.4, Role.STARCH), DishComponent("chicharrón", 0.6, Role.TOPPING)),

        dish("taco de carne asada", DishComponent("tortilla", 0.3, Role.STARCH), DishComponent("carne", 0.5, Role.TOPPING), DishComponent("salsa", 0.2, Role.SAUCE)),
        dish("taco de pastor", DishComponent("tortilla", 0.3, Role.STARCH), DishComponent("cerdo", 0.5, Role.TOPPING), DishComponent("pina", 0.2, Role.SIDE)),
        dish("taco de carnitas", DishComponent("tortilla", 0.3, Role.STARCH), DishComponent("cerdo", 0.5, Role.TOPPING), DishComponent("salsa", 0.2, Role.SAUCE)),
        dish("taco de pollo", DishComponent("tortilla", 0.3, Role.STARCH), DishComponent("pollo", 0.5, Role.TOPPING), DishComponent("salsa", 0.2, Role.SAUCE)),
        dish("taco de pescado", DishComponent("tortilla", 0.3, Role.STARCH), DishComponent("pescado", 0.5, Role.TOPPING), DishComponent("salsa", 0.2, Role.SAUCE)),
        dish("taco de camaron", DishComponent("tortilla", 0.3, Role.STARCH), DishComponent("camarón", 0.5, Role.TOPPING), DishComponent("salsa", 0.2, Role.SAUCE)),
        dish("taco de frijoles", DishComponent("tortilla", 0.4, Role.STARCH), DishComponent("frijoles", 0.6, Role.TOPPING)),
        dish("quesadilla con queso", DishComponent("tortilla", 0.4, Role.STARCH), DishComponent("queso", 0.6, Role.TOPPING)),
        dish("quesadilla con pollo", DishComponent("tortilla", 0.3, Role.STARCH), DishComponent("pollo", 0.5, Role.TOPPING), DishComponent("queso", 0.2, Role.TOPPING)),
        dish("burrito de carne", DishComponent("tortilla", 0.2, Role.STARCH), DishComponent("carne", 0.3, Role.TOPPING), DishComponent("arroz", 0.2, Role.STARCH), DishComponent("frijoles", 0.2, Role.SIDE), DishComponent("salsa", 0.1, Role.SAUCE)),
        dish("burrito de pollo", DishComponent("tortilla", 0.2, Role.STARCH), DishComponent("pollo", 0.3, Role.TOPPING), DishComponent("arroz", 0.2, Role.STARCH), DishComponent("frijoles", 0.2, Role.SIDE), DishComponent("salsa", 0.1, Role.SAUCE)),
        dish("enchiladas con pollo", DishComponent("tortilla", 0.3, Role.STARCH), DishComponent("pollo", 0.4, Role.TOPPING), DishComponent("salsa", 0.3, Role.SAUCE)),
        dish("tamal de pollo", DishComponent("masa", 0.5, Role.STARCH), DishComponent("pollo", 0.4, Role.TOPPING), DishComponent("salsa", 0.1, Role.SAUCE)),
        dish("tamal de cerdo", DishComponent("masa", 0.5, Role.STARCH), DishComponent("cerdo", 0.4, Role.TOPPING), DishComponent("salsa", 0.1, Role.SAUCE)),

        dish("sándwich de jamon y queso", DishComponent("pan", 0.4, Role.STARCH), DishComponent("jamón", 0.3, Role.TOPPING), DishComponent("queso", 0.3, Role.TOPPING)),
        dish("sándwich de jamón y queso", DishComponent("pan", 0.4, Role.STARCH), DishComponent("jamón", 0.3, Role.TOPPING), DishComponent("queso", 0.3, Role.TOPPING)),
        dish("sandwich de jamon y queso", DishComponent("pan", 0.4, Role.STARCH), DishComponent("jamón", 0.3, Role.TOPPING), DishComponent("queso", 0.3, Role.TOPPING)),
        dish("sandwich de jamón y queso", DishComponent("pan", 0.4, Role.STARCH), DishComponent("jamón", 0.3, Role.TOPPING), DishComponent("queso", 0.3, Role.TOPPING)),
        dish("sándwich de pavo", DishComponent("pan", 0.5, Role.STARCH), DishComponent("pavo", 0.5, Role.TOPPING)),
        dish("sándwich de pollo", DishComponent("pan", 0.4, Role.STARCH), DishComponent("pollo", 0.6, Role.TOPPING)),
        dish("sándwich de atun", DishComponent("pan", 0.4, Role.STARCH), DishComponent("atún", 0.6, Role.TOPPING)),
        dish("sándwich de atún", DishComponent("pan", 0.4, Role.STARCH), DishComponent("atún", 0.6, Role.TOPPING)),
        dish("sándwich de vegetales", DishComponent("pan", 0.5, Role.STARCH), DishComponent("verduras", 0.5, Role.SIDE)),

        dish("sándwich de pollo con mayonesa", DishComponent("pan", 0.4, Role.STARCH), DishComponent("pollo", 0.5, Role.TOPPING), DishComponent("mayonesa", 0.1, Role.SAUCE)),
        dish("sándwich de pollo con mayo", DishComponent("pan", 0.4, Role.STARCH), DishComponent("pollo", 0.5, Role.TOPPING), DishComponent("mayonesa", 0.1, Role.SAUCE)),
        dish("sándwich de pollo con palta", DishComponent("pan", 0.3, Role.STARCH), DishComponent("pollo", 0.45, Role.TOPPING), DishComponent("palta", 0.25, Role.SIDE)),
        dish("sándwich de pollo con lechuga", DishComponent("pan", 0.35, Role.STARCH), DishComponent("pollo", 0.5, Role.TOPPING), DishComponent("lechuga", 0.15, Role.SIDE)),
        dish("sándwich de pollo con lechuga y tomate", DishComponent("pan", 0.3, Role.STARCH), DishComponent("pollo", 0.45, Role.TOPPING), DishComponent("lechuga", 0.1, Role.SIDE), DishComponent("tomate", 0.15, Role.SIDE)),
        dish("sándwich de pollo con ketchup", DishComponent("pan", 0.4, Role.STARCH), DishComponent("pollo", 0.5, Role.TOPPING), DishComponent("ketchup", 0.1, Role.SAUCE)),
        dish("sándwich de pollo con mostaza", DishComponent("pan", 0.4, Role.STARCH), DishComponent("pollo", 0.5, Role.TOPPING), DishComponent("mostaza", 0.1, Role.SAUCE)),
        dish("sándwich de pollo con queso", DishComponent("pan", 0.35, Role.STARCH), DishComponent("pollo", 0.45, Role.TOPPING), DishComponent("queso", 0.2, Role.TOPPING)),
        dish("sándwich de pollo con jamon", DishComponent("pan", 0.35, Role.STARCH), DishComponent("pollo", 0.35, Role.TOPPING), DishComponent("jamón", 0.3, Role.TOPPING)),
        dish("sándwich de pollo con jamón", DishComponent("pan", 0.35, Role.STARCH), DishComponent("pollo", 0.35, Role.TOPPING), DishComponent("jamón", 0.3, Role.TOPPING)),

        dish("sándwich de jamon con mayonesa", DishComponent("pan", 0.35, Role.STARCH), DishComponent("jamón", 0.5, Role.TOPPING), DishComponent("mayonesa", 0.15, Role.SAUCE)),
        dish("sándwich de jamón con mayonesa", DishComponent("pan", 0.35, Role.STARCH), DishComponent("jamón", 0.5, Role.TOPPING), DishComponent("mayonesa", 0.15, Role.SAUCE)),
        dish("sándwich de atun con mayonesa", DishComponent("pan", 0.35, Role.STARCH), DishComponent("atún", 0.5, Role.TOPPING), DishComponent("mayonesa", 0.15, Role.SAUCE)),
        dish("sándwich de atún con mayonesa", DishComponent("pan", 0.35, Role.STARCH), DishComponent("atún", 0.5, Role.TOPPING), DishComponent("mayonesa", 0.15, Role.SAUCE)),
        dish("sándwich de palta con mayonesa", DishComponent("pan", 0.35, Role.STARCH), DishComponent("palta", 0.5, Role.SIDE), DishComponent("mayonesa", 0.15, Role.SAUCE)),

        dish("sandwich de pollo con mayonesa", DishComponent("pan", 0.4, Role.STARCH), DishComponent("pollo", 0.5, Role.TOPPING), DishComponent("mayonesa", 0.1, Role.SAUCE)),
        dish("sandwich de pollo con mayo", DishComponent("pan", 0.4, Role.STARCH), DishComponent("pollo", 0.5, Role.TOPPING), DishComponent("mayonesa", 0.1, Role.SAUCE)),
        dish("sandwich de pollo con palta", DishComponent("pan", 0.3, Role.STARCH), DishComponent("pollo", 0.45, Role.TOPPING), DishComponent("palta", 0.25, Role.SIDE)),
        dish("sandwich de pollo con lechuga", DishComponent("pan", 0.35, Role.STARCH), DishComponent("pollo", 0.5, Role.TOPPING), DishComponent("lechuga", 0.15, Role.SIDE)),
        dish("sandwich de pollo con lechuga y tomate", DishComponent("pan", 0.3, Role.STARCH), DishComponent("pollo", 0.45, Role.TOPPING), DishComponent("lechuga", 0.1, Role.SIDE), DishComponent("tomate", 0.15, Role.SIDE)),
        dish("sandwich de pollo con ketchup", DishComponent("pan", 0.4, Role.STARCH), DishComponent("pollo", 0.5, Role.TOPPING), DishComponent("ketchup", 0.1, Role.SAUCE)),
        dish("sandwich de pollo con mostaza", DishComponent("pan", 0.4, Role.STARCH), DishComponent("pollo", 0.5, Role.TOPPING), DishComponent("mostaza", 0.1, Role.SAUCE)),
        dish("sandwich de pollo con queso", DishComponent("pan", 0.35, Role.STARCH), DishComponent("pollo", 0.45, Role.TOPPING), DishComponent("queso", 0.2, Role.TOPPING)),
        dish("sandwich de pollo con jamon", DishComponent("pan", 0.35, Role.STARCH), DishComponent("pollo", 0.35, Role.TOPPING), DishComponent("jamón", 0.3, Role.TOPPING)),
        dish("sandwich de pollo con jamón", DishComponent("pan", 0.35, Role.STARCH), DishComponent("pollo", 0.35, Role.TOPPING), DishComponent("jamón", 0.3, Role.TOPPING)),

        dish("sandwich de jamon con mayonesa", DishComponent("pan", 0.35, Role.STARCH), DishComponent("jamón", 0.5, Role.TOPPING), DishComponent("mayonesa", 0.15, Role.SAUCE)),
        dish("sandwich de jamón con mayonesa", DishComponent("pan", 0.35, Role.STARCH), DishComponent("jamón", 0.5, Role.TOPPING), DishComponent("mayonesa", 0.15, Role.SAUCE)),
        dish("sandwich de atun con mayonesa", DishComponent("pan", 0.35, Role.STARCH), DishComponent("atún", 0.5, Role.TOPPING), DishComponent("mayonesa", 0.15, Role.SAUCE)),
        dish("sandwich de atún con mayonesa", DishComponent("pan", 0.35, Role.STARCH), DishComponent("atún", 0.5, Role.TOPPING), DishComponent("mayonesa", 0.15, Role.SAUCE)),
        dish("sandwich de palta con mayonesa", DishComponent("pan", 0.35, Role.STARCH), DishComponent("palta", 0.5, Role.SIDE), DishComponent("mayonesa", 0.15, Role.SAUCE)),

        dish("hamburguesa con mayonesa", DishComponent("hamburguesa", 0.75, Role.TOPPING), DishComponent("mayonesa", 0.25, Role.SAUCE)),
        dish("hamburguesa con ketchup", DishComponent("hamburguesa", 0.75, Role.TOPPING), DishComponent("ketchup", 0.25, Role.SAUCE)),
        dish("hamburguesa con mostaza", DishComponent("hamburguesa", 0.75, Role.TOPPING), DishComponent("mostaza", 0.25, Role.SAUCE)),
        dish("hamburguesa con queso y tocino", DishComponent("hamburguesa", 0.5, Role.TOPPING), DishComponent("queso", 0.25, Role.TOPPING), DishComponent("tocino", 0.25, Role.TOPPING)),
        dish("hamburguesa con lechuga y tomate", DishComponent("hamburguesa", 0.65, Role.TOPPING), DishComponent("lechuga", 0.15, Role.SIDE), DishComponent("tomate", 0.2, Role.SIDE)),

        dish("papas fritas con mayonesa", DishComponent("papa frita", 0.75, Role.STARCH), DishComponent("mayonesa", 0.25, Role.SAUCE)),
        dish("papas fritas con ketchup", DishComponent("papa frita", 0.75, Role.STARCH), DishComponent("ketchup", 0.25, Role.SAUCE)),
        dish("papas fritas con salsa", DishComponent("papa frita", 0.7, Role.STARCH), DishComponent("salsa", 0.3, Role.SAUCE)),
        dish("papas con mayonesa", DishComponent("papa", 0.7, Role.STARCH), DishComponent("mayonesa", 0.3, Role.SAUCE)),
        dish("papas con salsa", DishComponent("papa", 0.7, Role.STARCH), DishComponent("salsa", 0.3, Role.SAUCE)),

        dish("ceviche con maiz", DishComponent("pescado", 0.5, Role.TOPPING), DishComponent("maiz", 0.3, Role.SIDE), DishComponent("limon", 0.2, Role.SAUCE)),
        dish("ceviche con maiz tostado", DishComponent("pescado", 0.5, Role.TOPPING), DishComponent("maiz tostado", 0.3, Role.SIDE), DishComponent("limon", 0.2, Role.SAUCE)),
        dish("ceviche con camote", DishComponent("pescado", 0.5, Role.TOPPING), DishComponent("camote", 0.3, Role.SIDE), DishComponent("limon", 0.2, Role.SAUCE)),
        dish("ceviche con palta", DishComponent("pescado", 0.4, Role.TOPPING), DishComponent("palta", 0.3, Role.SIDE), DishComponent("limon", 0.2, Role.SAUCE), DishComponent("cebolla", 0.1, Role.SIDE)),
        dish("ceviche con chifles", DishComponent("pescado", 0.4, Role.TOPPING), DishComponent("platano frito", 0.4, Role.STARCH), DishComponent("limon", 0.2, Role.SAUCE)),

        dish("lomo saltado con arroz", DishComponent("lomo", 0.35, Role.TOPPING), DishComponent("arroz", 0.35, Role.STARCH), DishComponent("papa frita", 0.3, Role.STARCH)),
        dish("lomo saltado con papas fritas", DishComponent("lomo", 0.4, Role.TOPPING), DishComponent("papa frita", 0.6, Role.STARCH)),
    )

    private fun recipe(foodId: String, vararg ingredients: RecipeIngredient) = RecipeNote(foodId, ingredients.toList())

    /**
     * The recipes of the RECIPE_ESTIMATE rows of the static catalog (WP-D1: gen183-gen188 and gen196), as the `sourceRecordId` and the comment
     * of each row write them: the named profile of every ingredient and its share of the weight, in the order of the record. A note is
     * documentation for the UI to show later; nothing reads it.
     */
    val RECIPE_NOTES: List<RecipeNote> = listOf(
        recipe(
            "gen183",
            RecipeIngredient("marraqueta", "cl010", 37.0), RecipeIngredient("churrasco cocido", "gen093", 31.5),
            RecipeIngredient("tomate", "gen026", 18.5), RecipeIngredient("porotos verdes", "gen173", 11.0),
            RecipeIngredient("ají verde", "gen175", 2.0),
        ),
        recipe(
            "gen184",
            RecipeIngredient("marraqueta", "cl010", 43.5), RecipeIngredient("churrasco cocido", "gen093", 39.1),
            RecipeIngredient("queso gauda", "gen157", 17.4),
        ),
        recipe(
            "gen185",
            RecipeIngredient("marraqueta", "cl010", 44.4), RecipeIngredient("pollo cocido deshilachado", "gen004", 38.9),
            RecipeIngredient("mayonesa", "gen065", 16.7),
        ),
        recipe(
            "gen186",
            RecipeIngredient("caldo", "agua", 45.0), RecipeIngredient("carne de vacuno", "gen093", 12.0),
            RecipeIngredient("papa", "gen021", 20.0), RecipeIngredient("zapallo", "gen072", 6.0),
            RecipeIngredient("choclo", "gen071", 5.0), RecipeIngredient("arroz", "gen005", 5.0),
            RecipeIngredient("zanahoria", "gen024", 3.0), RecipeIngredient("arvejas", "gen055", 3.0),
            RecipeIngredient("cebolla", "gen027", 1.0),
        ),
        recipe(
            "gen187",
            RecipeIngredient("carne", "gen093", 25.0), RecipeIngredient("papa", "gen021", 40.0),
            RecipeIngredient("cebolla", "gen027", 12.0), RecipeIngredient("caldo", "agua", 17.0),
            RecipeIngredient("aceite", "gen099", 3.0), RecipeIngredient("pimentón", "gen036", 3.0),
        ),
        recipe(
            "gen188",
            RecipeIngredient("pescado blanco crudo", "FDC 173713", 68.0), RecipeIngredient("cebolla", "gen027", 18.0),
            RecipeIngredient("jugo de limón", "FDC 167747", 12.0), RecipeIngredient("cilantro y ají", "agua", 2.0),
        ),
        recipe(
            "gen196",
            RecipeIngredient("pisco", "FDC 174815", 22.0), RecipeIngredient("bebida cola", "gen146", 78.0),
        ),
    )

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
        dishCompositions = DishCompositionsKnowledge(dishes = DISH_COMPOSITIONS, recipeNotes = RECIPE_NOTES),
    )
}
