package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.food.findFoodByNormalized
import com.example.kpkn.data.food.findFoodExactByNormalized
import com.example.kpkn.data.food.getGramsForReference
import com.example.kpkn.data.food.staticFoodPhrases
import com.example.kpkn.data.models.*

/**
 * FoodParser — Deterministic Spanish NLP parser for free-form food descriptions.
 * Mirrors utils/nutritionDescriptionParser.ts from PWA.
 * Handles: grams, portions, cooking methods, brands, modifiers.
 */

// ─── Regex Patterns ──────────────────────────────────────────────────────────

// "lt/lts", "cc" and "cm3" are rewritten by the normalizer (litres, millilitres); they stay here for text that skips it.
private const val GRAM_UNITS = "g|gr|gramos?|kg|kilogramos?|kilos?|ml|cc|cm3|mililitros?|lts?|l|litros?|oz|onzas?|lb|libras?"

private val GRAM_PATTERN = Regex("""(\d+(?:[.,]\d+)?)\s*(?:$GRAM_UNITS)\b(?:\s+de)?\s*""", RegexOption.IGNORE_CASE)

// A sentence ("2 huevos. Arroz") or a label ("Desayuno: 2 huevos") ends like a comma does; a period or colon
// inside a number ("1.5 kg", "a las 13:30") does not.
private val COMMA_OR_PLUS = Regex("""(?:(?<!\d),\s*|,(?!\d)\s*|;\s*|\s*\+\s*|\s*[\r\n]+\s*|(?<!\d)\.(?!\d)\s*|(?<!\d):(?!\d)\s*)""")
private val CONNECTOR_Y = Regex("""\s+(?:y|e|mas|más)\s+""", RegexOption.IGNORE_CASE)
private val CONNECTOR_CON = Regex("""\s+con\s+""", RegexOption.IGNORE_CASE)

private val PROTECTED_ENTITIES = listOf(
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
) + TextNormalizer.numberWordFoodNames

// Dishes the parser protects from splitting ("empanadas de pino", "pasteles de choclo") keep their plural, like
// catalog names do: the singularizer only changes a phrase into another known phrase.
private val SINGULARIZER_LEXICON: SpanishSingularizer.Lexicon by lazy(LazyThreadSafetyMode.PUBLICATION) {
    SpanishSingularizer.defaultLexicon.withPhrases(PROTECTED_ENTITIES)
}

private val LITERAL_QUANTITIES = mapOf(
    "un" to 1.0, "una" to 1.0, "uno" to 1.0, "dos" to 2.0, "tres" to 3.0,
    "cuatro" to 4.0, "cinco" to 5.0, "seis" to 6.0, "siete" to 7.0,
    "ocho" to 8.0, "nueve" to 9.0, "diez" to 10.0,
    "once" to 11.0, "doce" to 12.0, "trece" to 13.0, "catorce" to 14.0,
    "quince" to 15.0, "dieciséis" to 16.0, "dieciseis" to 16.0,
    "diecisiete" to 17.0, "dieciocho" to 18.0, "diecinueve" to 19.0,
    "veinte" to 20.0, "veintiuno" to 21.0, "veintidós" to 22.0, "veintidos" to 22.0,
    "veintitrés" to 23.0, "veintitres" to 23.0, "veinticuatro" to 24.0,
    "veinticinco" to 25.0, "treinta" to 30.0,
    "media" to 0.5, "medio" to 0.5, "mitad" to 0.5,
    "cuarto" to 0.25, "tercio" to 0.33, "doble" to 2.0, "triple" to 3.0,
    "unos" to 2.0, "unas" to 2.0,
)

private val PORTION_PATTERNS = listOf(
    // Los patrones con "plato" deben evaluarse ANTES que el adjetivo suelto:
    // "un plato grande de arroz" es LARGE, no EXTRA (B9).
    Triple(Regex("""\bplato\s+grande\b""", RegexOption.IGNORE_CASE), PortionPreset.LARGE, "large"),
    Triple(Regex("""\bplato\s+mediano\b""", RegexOption.IGNORE_CASE), PortionPreset.MEDIUM, "medium"),
    Triple(Regex("""\bplato\s+(?:chico|pequeño|pequeña)\b""", RegexOption.IGNORE_CASE), PortionPreset.SMALL, "small"),
    // One size scale (WP-N6): "grande" and "generoso" are the LARGE size (x1.25 in PORTION_MULTIPLIERS), and the adjective
    // agrees in number with its food ("2 manzanas grandes").
    Triple(Regex("""\b(grandes?|generos[oa]s?)\b""", RegexOption.IGNORE_CASE), PortionPreset.LARGE, "large"),
    Triple(Regex("""\b(median[oa]s?)\b""", RegexOption.IGNORE_CASE), PortionPreset.MEDIUM, "medium"),
    Triple(Regex("""\b(pequeñ[oa]s?|chic[oa]s?)\b""", RegexOption.IGNORE_CASE), PortionPreset.SMALL, "small"),
)

private val COOKING_PATTERNS = listOf(
    Pair(Regex("""\b(?:empanizad[oa]s?|empanad[o]s?|apanad[oa]s?|breaded)\b""", RegexOption.IGNORE_CASE), CookingMethod.EMPANIZADO_FRITO),
    Pair(Regex("""\b(?:microondas|microndas|microondead[oa]s?|al\s+microondas|al\s+microndas|al\s+micro)\b""", RegexOption.IGNORE_CASE), CookingMethod.COCIDO),
    
    // 2. PLANCHA / PLANCHADO
    Pair(Regex("""\b(?:a\s+la\s+)?(?:plancha|planchad[oa]s?)\b""", RegexOption.IGNORE_CASE), CookingMethod.PLANCHA),
    
    // 3. HORNO
    Pair(Regex("""\b(?:al\s+)?horno\b|\bhorn(?:ead[oa]s?|er[oa]?)\b|\b(?:baked|airfryer|air\s*fryer|frito\s+al\s+aire)\b""", RegexOption.IGNORE_CASE), CookingMethod.HORNO),
    
    // 4. FRITO + SALTEADO + REVUELTO (unificados como PWA)
    Pair(Regex(RegexEs.bounded("""frit[oa]s?|fre[ií]d[oa]s?|revuelt[oa]s?|saltead[oa]s?|saltear|sofrit[oa]s?|soffrit[oa]s?|fried"""), RegexOption.IGNORE_CASE), CookingMethod.FRITO),
    
    // 5. COCIDO / HERVIDO / SANCOCHADO (incluye "duro": "huevo duro" es cocido, IT3;
    //     "cocinado/cocinada" son sinónimos cotidianos de cocido y antes quedaban
    //     dentro del tag, rompiendo la identidad y el estado)
    Pair(Regex("""\b(?:cocid[oa]s?|cocinad[oa]s?|hervid[oa]s?|sancochad[oa]s?|duro|dura|duros|duras|boiled|estofad[oa]s?)\b""", RegexOption.IGNORE_CASE), CookingMethod.COCIDO),
    
    // 6. CRUDO
    Pair(Regex("""\b(?:crud[oa]s?|fresc[oa]s?|raw)\b""", RegexOption.IGNORE_CASE), CookingMethod.CRUDO),
    
    // 7. VAPOR
    Pair(Regex("""\b(?:al\s+)?vapor\b|\bvaporizad[oa]s?\b|\bsteamed\b""", RegexOption.IGNORE_CASE), CookingMethod.VAPOR),
    
    // 8. OLLA
    Pair(Regex("""\b(?:a\s+la\s+)?olla\b""", RegexOption.IGNORE_CASE), CookingMethod.OLLA),
    
    // 9. ASADO_PARRILLA (ahora separado de plancha)
    Pair(Regex(RegexEs.bounded("""(?:a\s+la\s+)?parrilla|parrill[ae]r[oa]s?|grilled|asad[oa]s?|al\s+carb[oó]n"""), RegexOption.IGNORE_CASE), CookingMethod.ASADO_PARRILLA),
    
    // 10. GUISADO (incluye "guiso"/"guisito"/"guisote", IT3)
    Pair(Regex("""\bguis(?:ad)?[oa]s?\b|\bguisit[oa]s?\b|\bguisote\b|\bcazuel[ae]d[oa]s?\b""", RegexOption.IGNORE_CASE), CookingMethod.GUISADO),
    
    // 11. AHUMADO
    Pair(Regex("""\bahumad[oa]s?\b|\bhumad[oa]s?\b|\bsmoked\b""", RegexOption.IGNORE_CASE), CookingMethod.AHUMADO),
    Pair(Regex(RegexEs.bounded("""sous\s+vide|al\s+vac[ií]o|en\s+bolsa\s+sellada"""), RegexOption.IGNORE_CASE), CookingMethod.COCIDO),
    // "poché" is the method only: "huevo poché" keeps its "huevo" (the alternative that swallowed both words
    // matched on Android alone and left an empty food name).
    Pair(Regex(RegexEs.bounded("""escalfad[oa]s?|pochad[oa]s?|poch[eé]"""), RegexOption.IGNORE_CASE), CookingMethod.COCIDO),
    Pair(Regex(RegexEs.bounded("""olla\s+(?:de\s+)?presi[oó]n|olla\s+expr[eé]s"""), RegexOption.IGNORE_CASE), CookingMethod.OLLA),
    Pair(Regex("""\b(?:en\s+ceviche|estilo\s+ceviche|aguachile)\b""", RegexOption.IGNORE_CASE), CookingMethod.CRUDO),
    Pair(Regex("""\b(?:papillote|en\s+papillote|empapelad[oa]s?)\b""", RegexOption.IGNORE_CASE), CookingMethod.HORNO),
    Pair(Regex("""\b(?:al\s+wok|wok-wok)\b""", RegexOption.IGNORE_CASE), CookingMethod.FRITO),
    Pair(Regex(RegexEs.bounded("""a\s+la\s+brasa|al\s+carb[oó]n|a\s+la\s+le[nñ]a"""), RegexOption.IGNORE_CASE), CookingMethod.ASADO_PARRILLA),
)

private val REFERENCE_PATTERNS = listOf(
    Pair(Regex("""\b(\d+(?:[.,]\d+)?|un|una|medio|media)\s+(scoops?)(?:\s+generosos?)?\s+(?:de\s+)?(.+)""", RegexOption.IGNORE_CASE), "scoop"),
    Pair(Regex("""\b(\d+(?:[.,]\d+)?)\s+(cucharadas?)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "tablespoon"),
    Pair(Regex("""\b(un|una|media|1)\s+(cucharada)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "tablespoon"),
    Pair(Regex("""\b(dos|tres)\s+(cucharadas?)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "tablespoon"),
    Pair(Regex("""\b(\d+(?:[.,]\d+)?)\s+(cucharaditas?)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "teaspoon"),
    Pair(Regex("""\b(\d+(?:[.,]\d+)?)\s+(tazas?)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "cup"),
    Pair(Regex("""\b(un|una|1)\s+(taza)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "cup"),
    Pair(Regex("""\b(un|una|1)\s+(bowl|bol|tazon|tazón)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "bowl"),
    Pair(Regex("""\b(media|medio|1/2)\s+(taza)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "cup"),
    Pair(Regex("""\b(un|una|1)\s+(puñado|punado|puñados|punados)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "handful"),
    Pair(Regex("""\b(un|1)\s+(puño)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "fist"),
    Pair(Regex("""\b(\d+(?:[.,]\d+)?)\s+(vasos?)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "glass"),
    Pair(Regex("""\b(\d+(?:[.,]\d+)?)\s+(rebanadas?|tajadas?|l[aá]minas?|lonchas?|lonjas?|fetas?|cuadritos?|cubitos?|torrijas?|torradas?|medallones?)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "slice"),
    Pair(Regex("""\b(un|una|unos|unas|un\s+par\s+de|1)\s+(rebanadas?|tajadas?|l[aá]minas?|lonchas?|lonjas?|fetas?|cuadritos?|torrijas?|torradas?)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "slice"),
    Pair(Regex("""\b(l[aá]minas?|lonchas?|lonjas?|fetas?|rebanadas?|torrijas?|torradas?)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "slice"),
    Pair(Regex("""\b(\d+(?:[.,]\d+)?)\s+(latas?)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "can"),
    Pair(Regex("""\b(\d+(?:[.,]\d+)?)\s+(scoops?|medidas?)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "scoop"),
    Pair(Regex("""\b(\d+(?:[.,]\d+)?)\s+(porci[oó]n(?:es)?)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "portion"),
    Pair(Regex("""\b(un|una|1)\s+(trozo)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "piece"),
    Pair(Regex("""\b(un|una|1)\s+(pedazo)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "piece"),
    Pair(Regex("""\b(\d+(?:[.,]\d+)?)\s+(trozos?|pedazos?)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "piece"),
    // Indicadores subjetivos de cantidad (el normalizador conserva el literal "un/una"
    // ante estos sustantivos, y se acepta dígito como red de seguridad)
    Pair(Regex("""\b(un|1)\s+(poco)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "little"),
    Pair(Regex("""\b(poquito|poquita)\s+(?:de\s+)?(.+)""", RegexOption.IGNORE_CASE), "little"),
    Pair(Regex("""\b(una?|1)\s+(pizca)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "pinch"),
    Pair(Regex("""\b(un|1)\s+(chorrito)\s+de\s+(.+)""", RegexOption.IGNORE_CASE), "splash"),
)

// Precompiled Regex patterns for optimization
private val GROUP_PATTERN = Regex("^(.+?)\\s*\\((.+)\\)\\s*$")
private val STARTS_WITH_DIGIT = Regex("""^\d""")
// "doble palta" doubles a portion; it does not count two pieces.
private val PORTION_MULTIPLIER_WORD_PATTERN = Regex("""^(?:doble|triple)\s""", RegexOption.IGNORE_CASE)
private val NEGATION_PATTERN = Regex("""\b(?:sin|no|ni)\b""", RegexOption.IGNORE_CASE)
private val GRAM_UNIT_PATTERN = Regex("""(\d+(?:[.,]\d+)?)\s*($GRAM_UNITS)\b""", RegexOption.IGNORE_CASE)
private val KG_LITER_PATTERN = Regex("kg|kilogramos?|kilos?|l$|lts?|litros?")
private val OZ_PATTERN = Regex("oz|onzas?")
private val LB_PATTERN = Regex("lb|libras?")
private val MULTISPACE_PATTERN = Regex("\\s{2,}")
private val HALF_PATTERN = Regex("""\b1/2\b""")
private val QUARTER_PATTERN = Regex("""\b1/4\b""")
private val THREE_QUARTERS_PATTERN = Regex("""\b3/4\b""")
private val RANGE_QUANTITY_PATTERN = Regex("""^(\d+(?:\.\d+)?)\s*-\s*(\d+(?:\.\d+)?)\s+(.+)$""")
private val NUMBER_QUANTITY_PATTERN = Regex("""^(\d+(?:\.\d+)?)\s*(?:x\s*)?(.+)$""")
private val LITERAL_QUANTITY_PATTERN = Regex("""^(un|una|uno|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce|trece|catorce|quince|dieciséis|dieciseis|diecisiete|dieciocho|diecinueve|veinte|veintiuno|veintidós|veintidos|veintitrés|veintitres|veinticuatro|veinticinco|treinta|media|medio|mitad|cuarto|tercio|doble|triple)\s+(.+)$""", RegexOption.IGNORE_CASE)
private val PAIR_PATTERN = Regex("""^(?:un|1)\s+par\s+(?:de\s+)?(.+)$""", RegexOption.IGNORE_CASE)
private val SUFFIX_X_PATTERN = Regex("""^(\d+(?:[.,]\d+)?)?\s*(.+?)\s*[x×]\s*(\d+(?:[.,]\d+)?)\s*$""", RegexOption.IGNORE_CASE)
private val SPACES_PATTERN = Regex("\\s+")
private val LEADING_DE_PATTERN = Regex("^de\\s+")
private val LEADING_ARTICLE_PATTERN = Regex("^(?:el|la|los|las)\\s+")
private val PORTION_PREFIX_PATTERN = Regex("^(?:platos?|porciones?|porción|tazas?|vasos?|boles?|bowls?|fuentes?)\\s+de\\s+")
private val ARTICLE_PORTION_PREFIX_PATTERN = Regex("^(?:un|una|unos|unas)\\s+(?:platos?|porciones?|porción|tazas?|vasos?|boles?|bowls?|fuentes?)\\s+de\\s+")
private val TRAILING_DE_PATTERN = Regex("\\s+de\\s+$")
private val STANDALONE_QUANTITY = Regex(
    """^(?:\d+(?:[.,]\d+)?|${LITERAL_QUANTITIES.keys.joinToString("|") { Regex.escape(it) }})$""",
    RegexOption.IGNORE_CASE,
)
private val COUNT_PREFIX = Regex(
    """^(?:\d+(?:[.,]\d+)?|${LITERAL_QUANTITIES.keys.joinToString("|") { Regex.escape(it) }})\s+(.+)$""",
    RegexOption.IGNORE_CASE,
)
private val ELLIPTICAL_MEASURE = Regex("""^(\S+)\s+de\s+(.+)$""", RegexOption.IGNORE_CASE)
private val INHERITABLE_VESSEL = Regex(
    """\b(cucharaditas?|cucharadas?|tazas?|vasos?|copas?|platos?|bol(?:es)?|bowls?|tazon|tazones|tazón)\b""",
    RegexOption.IGNORE_CASE,
)

// Segmentation / negation helpers: compiled once instead of on every fragment.
private val SIN_PREFIX_PATTERN = Regex("""^sin\s+""", RegexOption.IGNORE_CASE)
private val KNOWN_NEGATION_TARGET_PATTERN = Regex("""^(?:lactosa|gluten|gas|az[uú]car(?:es)?)(?:\b|$)""")
private val COMMA_BEFORE_EXCLUSION_PATTERN = Regex(""",[ \t]*(?=(?:sin|ni)[ \t]+)""", RegexOption.IGNORE_CASE)
private val LEADING_CONNECTOR_PATTERN = Regex("""^(?:con|y|e)\s+""", RegexOption.IGNORE_CASE)
private val CONNECTOR_SINO = Regex("""\s+sino\s+""", RegexOption.IGNORE_CASE)
private val CONNECTOR_NI = Regex("""\s+ni\s+""", RegexOption.IGNORE_CASE)
private val REPAIR_MARKER_PATTERN = Regex("""(?:perd[oó]n|digo|mejor dicho)""", RegexOption.IGNORE_CASE)
private val LEADING_SINO_PATTERN = Regex("""^sino\s+""", RegexOption.IGNORE_CASE)
private val TRAILING_NO_PATTERN = Regex("""^(.+?)\s+no$""", RegexOption.IGNORE_CASE)

// A meal or time-of-day label ("Desayuno: ", "Almuerzo: ", "Tarde: ") introduces a list: it is not a food and
// becomes a separator. Only with its colon: "once huevos" is still eleven eggs.
private val MEAL_LABEL_PATTERN = Regex(
    """(?<![\p{L}\p{N}_])(?:desayuno|almuerzo|once|cena|colaci[oó]n|merienda|snack|comida|postre|""" +
        """media\s+ma[ñn]ana|media\s+tarde|ma[ñn]ana|tarde|noche|mediod[ií]a)\s*:\s*""",
    RegexOption.IGNORE_CASE,
)
// "pollo: 150 g" or "leche: un vaso" is a food and its amount, not two sentences: the colon becomes a space.
private val AMOUNT_COLON_PATTERN = Regex(
    """(?<=\p{L}):[ \t]*(?=\d|(?:un|una|uno|medio|media|poco|poca)(?![\p{L}\p{N}_]))""",
    RegexOption.IGNORE_CASE,
)
// "100 gr. de arroz": the period closes a unit abbreviation; it ends a sentence only before a capital letter.
private val UNIT_ABBREVIATION_PERIOD = Regex(
    """(\d\s*(?:kg|g|ml|l|lt|lts|cc|oz|lb|lbs|cucharadas?|cucharaditas?))\.(?=\s+\p{Ll})""",
)

// Narration that frames a mention without naming a food: "hoy almorcé", "después un café", "a las 13:30 comí".
// A leading "no" survives ("no comí pan" -> "no pan") so the negation is still read.
private const val NARRATIVE_CLOCK = """a\s+las?\s+\d{1,2}(?:[:.]\d{2})?(?:\s*(?:h|hs|hrs?|horas?))?"""
private const val NARRATIVE_ADVERBS =
    """hoy|ayer|anteayer|anoche|reci[eé]n|despu[eé]s|luego|m[aá]s\s+tarde|tambi[eé]n|adem[aá]s|""" +
        """(?:esta|en\s+la)\s+(?:ma[ñn]ana|tarde|noche)|al\s+(?:desayuno|almuerzo|mediod[ií]a)|""" +
        """de\s+(?:desayuno|almuerzo|once|cena|postre)|""" + NARRATIVE_CLOCK
private const val NARRATIVE_VERBS =
    """com[ií]|comimos|he\s+comido|hemos\s+comido|almorc[eé]|almorzamos|cen[eé]|cenamos|desayun[eé]|""" +
        """desayunamos|tom[eé]|tomamos|piqu[eé]|picamos|merend[eé]|merendamos"""
private val NARRATIVE_PREFIX = Regex(
    """^(?:(?:$NARRATIVE_ADVERBS)(?:\s+|$))*(no\s+)?(?:me\s+)?(?:$NARRATIVE_VERBS)(?:\s+|$)""" +
        """|^(?:(?:$NARRATIVE_ADVERBS)(?:\s+|$))+(?!de\s)""",
    RegexOption.IGNORE_CASE,
)

private val PROTECTED_ENTITY_PHRASES = (PROTECTED_ENTITIES + staticFoodPhrases() + listOf("salsa de tomate"))
    .distinct()
    .sortedByDescending { it.length }

// Catalog names such as "castañas de cajú", "mantequilla de maní" or "ñame (cocido)" start or end in an accented letter.
private val PROTECTED_ENTITIES_REGEX = Regex(
    RegexEs.bounded(PROTECTED_ENTITY_PHRASES.joinToString("|") { Regex.escape(it) }),
    RegexOption.IGNORE_CASE,
)

private val REFERENCE_KEYWORDS_FAST = listOf(
    "cucharad", "taza", "puñ", "punad", "vaso", "rebanad", "tajad", "lata",
    "scoop", "medida", "porcion", "porción", "trozo", "pedazo",
    "poco", "poquit", "pizca", "chorrit", "bowl", "bol", "tazon",
    "lamina", "lámina", "lonch", "lonja", "feta", "cuadrit", "cubito",
    "torraj", "torrej", "torrij", "torrad", "cacho", "palito", "ramita", "hojuela", "bolsit",
)

private val COOKING_KEYWORDS_FAST = listOf(
    "empaniz", "apanad", "breaded", "empanad", "plancha", "horno", "horn",
    "baked", "airfryer", "air fryer", "frit", "freid", "freíd", "freir", "revuelt",
    "saltea", "sofrit", "soffrit", "fried", "cocid", "cocin", "hervid", "sancoch", "duro", "dura",
    "boiled", "estofad", "guis", "crud", "raw", "vapor", "steamed", "olla",
    "parrill", "grilled", "asado", "carbón", "carbon", "cazuel",
    "ahumad", "humad", "smoked",
    "microondas", "microndas", "microonde", "al micro", "sous vide", "vacio", "vacío",
    "escalf", "poche", "poché", "presion", "presión", "expres",
    "ceviche", "aguachile",
    "papillote", "empapelad", "wok", "brasa", "leña", "lena",
)

private val PORTION_KEYWORDS_FAST = listOf(
    "grand", "generos", "plato", "median", "pequeñ", "chico", "chica"
)

private val MODIFIER_KEYWORDS_FAST = listOf(
    "piel", "grasa", "miga", "clara", "descremad", "light", "0%", "almibar",
    "almíbar", "azucar", "azúcar", "integral", "colmad", "generos", "rasa", "fina", "pequeñ"
)

// ─── Main Parser ─────────────────────────────────────────────────────────────

fun parseMealDescription(
    description: String,
    retrievalResult: SemanticPortionRetriever.RetrievalResult? = null,
): ParsedMealDescription {
    // Phase G: Normalize user input before parsing
    val normalized = TextNormalizer.normalize(description)
    val trimmed = normalized.trim()
    if (trimmed.isEmpty()) return ParsedMealDescription(rawDescription = description)

    val fragments = splitMentionFragments(trimmed)
    val items = mutableListOf<ParsedMealItem>()

    for (frag in fragments) {
        // D1: retrieval POR FRAGMENTO con confianza por ítem. El retrieval de la
        // descripción completa diluye la confianza entre varios alimentos y bloquea
        // priors buenos por el gate global; cada fragmento recibe el suyo.
        // Si el snapshot no está instalado (tests), se cae al retrieval provisto.
        val fragRetrieval = retrievalResult
        val parsed = parseFragment(frag.text, fragRetrieval, frag.excludedIngredients) ?: continue
        // Only add amounts whose meaning is already known. Separate mentions with
        // omitted amounts must reach context inference separately; cooking and
        // exclusions belong to the mention, not merely its food name.
        val idx = items.indexOfFirst { canCombineMeasuredMentions(it, parsed) }
        if (idx < 0) {
            items.add(parsed)
        } else {
            val previous = items[idx]
            items[idx] = previous.copy(
                quantity = previous.quantity + parsed.quantity,
                amountGrams = previous.amountGrams!! + parsed.amountGrams!!,
            )
        }
    }

    if (items.isEmpty() && trimmed.isNotEmpty()) {
        parseFragment(trimmed, retrievalResult)?.let { items.add(it) }
    }

    return MealLanguageMerge.apply(
        ParsedMealDescription(
            items = items,
            rawDescription = trimmed,
            verbatimDescription = description.trim(),
        ),
    )
}

private fun canCombineMeasuredMentions(a: ParsedMealItem, b: ParsedMealItem): Boolean =
    a.amountGrams != null && b.amountGrams != null &&
        a.amountIntent != AmountIntent.UNSPECIFIED && a.amountIntent == b.amountIntent &&
        canonicalTagKey(a.tag) == canonicalTagKey(b.tag) &&
        a.cookingMethod == b.cookingMethod && a.modifierScale == b.modifierScale &&
        a.portion == b.portion && a.isExcluded == b.isExcluded &&
        a.brandHint == b.brandHint && a.unitId == b.unitId &&
        a.excludedIngredients == b.excludedIngredients &&
        a.amountIsTrailing == b.amountIsTrailing &&
        !a.isGroup && !b.isGroup

internal fun splitMealFragments(description: String): List<String> = splitByListConnectors(description)

internal fun isWholeProtectedMeal(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return false
    if (PROTECTED_ENTITIES.any { it.equals(trimmed, ignoreCase = true) }) return true
    val normalized = FoodIdentity.normalize(trimmed)
    if (PROTECTED_ENTITIES.any { FoodIdentity.normalize(it) == normalized }) return true
    return isNamedDishPhrase(trimmed)
}

private fun isKnownNegationModifier(text: String, negMatch: MatchResult): Boolean {
    val afterNeg = text.substring(negMatch.range.last + 1).trim().lowercase()
    if (!negMatch.value.equals("sin", ignoreCase = true)) return false
    val firstWord = afterNeg.split(SPACES_PATTERN).firstOrNull() ?: return false
    return firstWord in listOf("piel", "grasa", "miga", "pieles", "grasas") ||
        KNOWN_NEGATION_TARGET_PATTERN.containsMatchIn(afterNeg)
}

// ─── Fragment Parser ─────────────────────────────────────────────────────────

private fun parseFragment(
    frag: String,
    retrievalResult: SemanticPortionRetriever.RetrievalResult? = null,
    excludedIngredients: Set<String> = emptySet(),
): ParsedMealItem? {
    var text = frag.trim()
    if (text.isEmpty()) return null

    // Handle negated items: "sin leche" → parse "leche" and mark excluded
    var isExcluded = false
    val sinMatch = SIN_PREFIX_PATTERN.find(text)
    if (sinMatch != null) {
        isExcluded = true
        text = text.removeRange(0, sinMatch.range.last + 1).trim()
        if (text.isEmpty()) return null
    }

    // Handle group: "Recipe (item1, item2)"
    val groupMatch = GROUP_PATTERN.find(text)
    if (groupMatch != null) {
        val groupName = groupMatch.groupValues[1].trim()
        val content = groupMatch.groupValues[2].trim()
        val subFragments = splitMentionFragments(content)
        val subItems = subFragments.mapNotNull { parseFragment(it.text, retrievalResult, it.excludedIngredients) }
        if (subItems.isNotEmpty()) {
            return ParsedMealItem(tag = groupName, isGroup = true, subItems = subItems)
        }
    }

    // A reference or modifier can consume its size adjective before identity
    // extraction. Keep the local descriptor, without applying it to other foods
    // or multiplying an already-resolved utensil amount a second time.
    val declaredPortion = extractPortionFromFragment(text).first

    // Extract grams
    val gramsResult = extractGramsFromFragment(text)
    var grams = gramsResult.grams
    var working = gramsResult.foodPart
    var refQuantity: Double? = null
    var unitId: String? = gramsResult.unitId
    var amountIntent = if (grams != null) AmountIntent.EXPLICIT_MASS else AmountIntent.UNSPECIFIED

    // If no grams, try reference (e.g., "1 cucharada de aceite")
    if (grams == null) {
        val refResult = extractReferenceFromFragment(working, retrievalResult)
        if (refResult.grams != null) {
            grams = refResult.grams
            working = refResult.foodPart
            refQuantity = refResult.quantity
            unitId = refResult.unitId
            amountIntent = AmountIntent.RESOLVED_SUBJECTIVE
        }
    }

    val catalogPhrase = isNamedDishPhrase(working)

    // Extract cooking method. Catalog dish names keep the words ("leche asada").
    val cookingMethod = extractCookingMethod(working)
    if (!catalogPhrase) {
        working = cookingMethod.second
    }

    // Extract anatomical/preparation modifiers (colmada/rasa scale subjective grams)
    val modifierResult = extractModifiers(working, grams, amountIntent)
    if (!catalogPhrase) {
        working = modifierResult.third
    }
    val modifierMacros = if (catalogPhrase) null else modifierResult.first
    grams = modifierResult.second

    // Extract portion preset
    val portionResult = extractPortionFromFragment(working)
    if (!catalogPhrase) {
        working = portionResult.second
    }

    // Extract quantity multiplier ("2 panes", "3 huevos")
    val quantityResult = parseQuantityMultiplier(working)
    val quantity = if (refQuantity != null) refQuantity * quantityResult.first else quantityResult.first
    val foodName = quantityResult.second

    if (foodName.length < 2) return null

    // Canonical resolution
    // A count in front of the food ("3 tomates", "huevos x2", "un par de huevos") counts units of the singular.
    val shouldSingularize = !catalogPhrase && !TextNormalizer.startsWithNumberWordFoodName(foodName) &&
        (STARTS_WITH_DIGIT.containsMatchIn(working.trim()) || quantity != 1.0)
    val canonical = normalizeFoodName(foodName, singularize = shouldSingularize)
    val knownFood = findFoodExactByNormalized(canonical) ?: findFoodByNormalized(canonical)
    val repaired = if (knownFood == null) SemanticPortionRetriever.repairQuery(canonical) else canonical
    val catalogFood = knownFood
        ?: findFoodExactByNormalized(repaired)
        ?: findFoodByNormalized(repaired)
    val countable = HouseholdPortions.isCountable(catalogFood, canonical) ||
        (quantity != 1.0 && HouseholdPortions.isCountable(null, canonical))
    val expressedCount = quantity != 1.0 ||
        HouseholdPortions.looksLikeCountExpression(frag) ||
        HouseholdPortions.looksLikeCountExpression(working.trim())
    // The size of the mention ("grande", "chica"). A counted piece takes it here, once: "2 manzanas grandes" are 2 x 150 g x 1.25,
    // and the resolver reads a declared size back as part of the amount (its base is the same count without the size).
    val itemSize = if (catalogPhrase) PortionPreset.MEDIUM else
        portionResult.first.takeUnless { it == PortionPreset.MEDIUM } ?: declaredPortion
    val householdCountGrams = if (
        amountIntent == AmountIntent.UNSPECIFIED &&
        countable &&
        (expressedCount || isCookieOrCrackerName(canonical))
    ) {
        HouseholdPortions.unitGrams(catalogFood, canonical) * quantity * (PORTION_MULTIPLIERS[itemSize] ?: 1.0)
    } else {
        null
    }
    val datasetHint = retrievalResult
        ?.takeIf { it.confidence >= DATASET_PORTION_MIN_CONFIDENCE }
        ?.takeIf { !FoodStapleOntology.hasAnchoredPortion(canonical) }
        ?.let { SemanticPortionRetriever.getGramsForFood(canonical, it) }
        ?.takeIf { HouseholdPortions.isHouseholdHint(it, catalogFood, canonical) }
    val lockedIntent = when {
        amountIntent == AmountIntent.EXPLICIT_MASS -> AmountIntent.EXPLICIT_MASS
        householdCountGrams != null || amountIntent == AmountIntent.RESOLVED_SUBJECTIVE ->
            AmountIntent.RESOLVED_SUBJECTIVE
        else -> amountIntent
    }
    // A count before a food that is not countable by default ("2 yogures", "una palta", "media palta"): the resolver scales
    // the weight of one unit by it (WP-N8). A food with no unit weight keeps its portion default.
    val countExpressed = lockedIntent == AmountIntent.UNSPECIFIED && !countable && expressedCount &&
        !PORTION_MULTIPLIER_WORD_PATTERN.containsMatchIn(frag.trim()) &&
        HouseholdPortions.countAppliesTo(catalogFood, canonical, quantity)
    val resolvedGrams = HouseholdPortions.resolveEatenGrams(
        intent = lockedIntent,
        quantity = quantity,
        food = catalogFood,
        parsedGrams = householdCountGrams ?: grams,
        datasetHint = datasetHint,
        query = canonical,
        explicitKilogram = HouseholdPortions.isExplicitKilogram(frag),
        unitId = unitId,
    )

    return ParsedMealItem(
        tag = canonical,
        quantity = quantity,
        amountGrams = if (lockedIntent == AmountIntent.UNSPECIFIED) null else resolvedGrams,
        cookingMethod = cookingMethod.first,
        portion = itemSize,
        isFuzzyMatch = false,
        appliedCookingFactor = COOKING_FACTORS[cookingMethod.first]?.kcal ?: 1.0,
        modifierScale = modifierMacros?.let {
            MacroOverrides(calories = it.kcal, protein = it.protein, carbs = it.carbs, fats = it.fats)
        },
        isExcluded = isExcluded,
        amountIntent = lockedIntent,
        unitId = unitId,
        excludedIngredients = excludedIngredients,
        amountIsTrailing = gramsResult.amountIsTrailing,
        countExpressed = countExpressed,
    )
}

// ─── Split Connectors ────────────────────────────────────────────────────────

private data class MentionFragment(val text: String, val excludedIngredients: Set<String> = emptySet())

private fun splitByListConnectors(description: String): List<String> =
    splitMentionFragments(description).map { it.text }

private fun splitMentionFragments(description: String): List<MentionFragment> {
    // A comma before the exclusion preposition does not detach its modifier:
    // "completo, sin mayonesa" has the same ingredient scope as the inline form.
    // Keep sentence/line boundaries and conversational "no" repairs distinct.
    var trimmed = description.trim().replace(COMMA_BEFORE_EXCLUSION_PATTERN, " ")
        .replace(MEAL_LABEL_PATTERN, ", ")
        .replace(AMOUNT_COLON_PATTERN, " ")
        .replace(UNIT_ABBREVIATION_PERIOD) { it.groupValues[1] }
    if (trimmed.isEmpty()) return emptyList()

    // Mask protected entities
    val masks = mutableListOf<Pair<String, String>>()
    trimmed = PROTECTED_ENTITIES_REGEX.replace(trimmed) { match ->
        val token = "__PROTECTED_${masks.size}__"
        masks.add(token to match.value)
        token
    }

    // Split by connectors
    var parts = listOf(trimmed)
    val splitBy = { regex: Regex ->
        parts = parts.flatMap { it.split(regex).map { s -> s.trim() }.filter { it.isNotEmpty() } }
    }
    splitBy(COMMA_OR_PLUS)
    parts = parts.mapIndexed { index, part ->
        if (index == 0) part else part.replace(LEADING_CONNECTOR_PATTERN, "")
    }
    splitBy(CONNECTOR_Y)
    splitBy(CONNECTOR_CON)
    splitBy(CONNECTOR_SINO)
    // Each sentence may open with its own narration ("Desayuné 2 huevos. Almorcé arroz con pollo").
    parts = parts.map { it.replace(NARRATIVE_PREFIX) { match -> match.groupValues[1] }.trim() }
        .filter { it.isNotEmpty() }

    // Conversational repairs replace the preceding mention, after food-list
    // segmentation, so "pollo con arroz, perdón, fideos" keeps the chicken.
    val repairedParts = mutableListOf<String>()
    var replacedMention: String? = null
    for (part in parts) {
        if (part.matches(REPAIR_MARKER_PATTERN)) {
            if (repairedParts.isNotEmpty()) replacedMention = repairedParts.removeAt(repairedParts.lastIndex)
        } else {
            val previous = replacedMention
            val previousCount = previous?.let(COUNT_PREFIX::matchEntire)
            val quantityRepair = previous != null && STANDALONE_QUANTITY.matches(part) &&
                !GRAM_PATTERN.containsMatchIn(previous) &&
                (previousCount != null || HouseholdPortions.isCountable(null, previous))
            repairedParts += if (quantityRepair) {
                // "dos huevos, perdón, uno" changes the count, not the food.
                val correctedCount = LITERAL_QUANTITIES[part.lowercase()] ?: part.replace(',', '.').toDouble()
                "$correctedCount ${previousCount?.groupValues?.get(1) ?: previous}"
            } else part.replace(LEADING_SINO_PATTERN, "")
            replacedMention = null
        }
    }
    // Bind only an adjacent, explicitly stated vessel. This is not general
    // anaphora: "medio de leche" inherits vaso from "medio vaso de jugo".
    parts = repairedParts.mapIndexed { index, part ->
        val omittedVessel = ELLIPTICAL_MEASURE.matchEntire(part)
        val vessel = if (index > 0) INHERITABLE_VESSEL.find(repairedParts[index - 1])?.value else null
        if (omittedVessel != null && vessel != null && STANDALONE_QUANTITY.matches(omittedVessel.groupValues[1])) {
            "${omittedVessel.groupValues[1]} $vessel de ${omittedVessel.groupValues[2]}"
        } else part
    }

    // Unmask and split negations into separate excluded fragments
    val mentions = parts.flatMap { p ->
        var unmasked = p
        for ((token, original) in masks) {
            unmasked = unmasked.replace(token, original)
        }
        unmasked = unmasked.replace(TRAILING_NO_PATTERN) {
            "sin ${it.groupValues[1]}"
        }
        val negMatch = NEGATION_PATTERN.findAll(unmasked)
            .firstOrNull { !isKnownNegationModifier(unmasked, it) }
        if (negMatch != null) {
            val beforeNeg = unmasked.substring(0, negMatch.range.first).trim()
            val afterNeg = unmasked.substring(negMatch.range.last + 1).trim()
            val exclusions = afterNeg.split(CONNECTOR_NI)
                .filter { it.isNotBlank() }
            listOfNotNull(beforeNeg.takeIf { it.isNotBlank() }?.let {
                MentionFragment(it, exclusions.map(FoodIdentity::normalize).toSet())
            }) + exclusions.map { MentionFragment("sin $it") }
        } else {
            listOf(MentionFragment(unmasked.trim()))
        }
    }.filter { it.text.isNotEmpty() }

    // Split fragments containing multiple explicit measures.
    // B6: la segmentación antigua cortaba DESDE cada medida ("arroz 100g pollo 50g"
    // → "100g pollo" + "50g", perdiendo "arroz" y desalineando gramos). Ahora se
    // intentan dos interpretaciones y se elige la que deja un alimento por medida:
    //   - Estilo A (alimento precede a la medida):  "arroz 100g pollo 50g" → [arroz 100g][pollo 50g]
    //   - Estilo B (medida precede al alimento):    "100g arroz 50g pollo" → [100g arroz][50g pollo]
    return mentions.flatMap { mention ->
        val measured = splitMultiMeasure(mention.text)
        measured.mapIndexed { index, text ->
            // An inline exclusion modifies its adjacent food, not every food
            // elsewhere in the meal or in a multiple-measure segment.
            MentionFragment(text, if (index == measured.lastIndex) mention.excludedIngredients else emptySet())
        }
    }.filter { it.text.isNotEmpty() }
}

/** Divide un fragmento con ≥2 medidas explícitas en fragmentos de una sola medida. */
private fun splitMultiMeasure(part: String): List<String> {
    val measures = GRAM_UNIT_PATTERN.findAll(part).toList()
    if (measures.size <= 1) return listOf(part)

    fun cleanFragments(fragments: List<String>): List<String> = fragments
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    fun measureCount(fragment: String): Int = GRAM_UNIT_PATTERN.findAll(fragment).count()

    /** True si tras quitar la (única) medida queda un alimento de ≥2 caracteres. */
    fun hasFood(fragment: String): Boolean {
        if (measureCount(fragment) != 1) return false
        val withoutMeasure = GRAM_UNIT_PATTERN.find(fragment)?.let { m ->
            fragment.removeRange(m.range)
        } ?: fragment
        return withoutMeasure.trim().length >= 2
    }

    // Estilo A: cada medida toma el texto que la precede (fin de la medida anterior).
    val styleA = cleanFragments(
        measures.mapIndexed { index, m ->
            val start = if (index == 0) 0 else measures[index - 1].range.last + 1
            part.substring(start, m.range.last + 1)
        }
    )
    if (styleA.isNotEmpty() && styleA.all { hasFood(it) }) {
        val remainder = part.substring(measures.last().range.last + 1).trim()
        val descriptorOnly = remainder.isNotBlank() && extractPortionFromFragment(
            extractCookingMethod(remainder).second,
        ).second.isBlank()
        if (descriptorOnly) {
            return styleA.dropLast(1) + "${styleA.last()} $remainder"
        }
        return styleA + listOfNotNull(remainder.takeIf { it.isNotBlank() })
    }

    // Estilo B: cada medida toma el texto que la sigue (inicio de la siguiente medida).
    val styleB = cleanFragments(
        measures.mapIndexed { index, m ->
            val end = if (index == measures.lastIndex) part.length else measures[index + 1].range.first
            part.substring(m.range.first, end)
        }
    )
    if (styleB.isNotEmpty() && styleB.all { hasFood(it) }) return styleB

    // Fallback: comportamiento histórico (cortar desde cada medida).
    return cleanFragments(
        measures.mapIndexed { index, m ->
            val start = m.range.first
            val end = measures.getOrNull(index + 1)?.range?.first ?: part.length
            part.substring(start, end)
        }
    )
}

// ─── Extract Grams ───────────────────────────────────────────────────────────

private data class MeasuredAmount(
    val grams: Double?,
    val foodPart: String,
    val unitId: String? = null,
    val amountIsTrailing: Boolean = false,
)

private fun extractGramsFromFragment(text: String): MeasuredAmount {
    val match = GRAM_PATTERN.find(text) ?: return MeasuredAmount(null, text)
    val numMatch = GRAM_UNIT_PATTERN.find(match.value) ?: return MeasuredAmount(null, text)

    var value = numMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: return MeasuredAmount(null, text)
    val unit = numMatch.groupValues[2].lowercase()

    value = when {
        unit.matches(KG_LITER_PATTERN) -> value * 1000
        unit.matches(OZ_PATTERN) -> value * 28.3495
        unit.matches(LB_PATTERN) -> value * 453.592
        else -> value
    }

    val cleaned = text.replace(match.value, " ").replace(MULTISPACE_PATTERN, " ").trim()
    val isVolume = unit == "ml" || unit == "cc" || unit == "cm3" || unit == "l" || unit == "lt" || unit == "lts" ||
        unit.startsWith("mililitro") || unit.startsWith("litro")
    return MeasuredAmount(
        grams = if (isVolume) SubjectivePortionEngine.massFromVolumeMl(value, cleaned) else value,
        foodPart = cleaned,
        unitId = if (isVolume) "ml" else null,
        // The resolver can distinguish a branded package's suffix from eaten
        // mass without treating every clear "arroz 200 g" as a package.
        amountIsTrailing = text.substring(0, match.range.first).any { it.isLetter() },
    )
}

// ─── Extract Portion Reference ───────────────────────────────────────────────

private data class ReferenceResult(
    val grams: Double?,
    val quantity: Double,
    val foodPart: String,
    val unitId: String? = null,
)

private fun extractReferenceFromFragment(
    text: String,
    retrievalResult: SemanticPortionRetriever.RetrievalResult? = null,
): ReferenceResult {
    val lower = text.lowercase()
    if (REFERENCE_KEYWORDS_FAST.none { lower.contains(it) }) {
        return resolveViaSubjectiveEngine(text, retrievalResult)
    }
    for ((pattern, refType) in REFERENCE_PATTERNS) {
        val match = pattern.find(text) ?: continue
        val foodPart = (match.groupValues.lastOrNull { it.isNotEmpty() } ?: continue).trim()
        if (foodPart.length < 2) continue

        // Parse quantity
        val qtyStr = match.groupValues[1].lowercase()
        val qty = LITERAL_QUANTITIES[qtyStr] ?: qtyStr.replace(",", ".").toDoubleOrNull() ?: 1.0

        // Find food in DB to get portion type
        val food = findFoodByNormalized(foodPart)

        // Try high-fidelity subjective resolution first
        val densityCategory = SubjectivePortionEngine.detectDensityCategory(foodPart)
        val subjectiveResult = SubjectivePortionEngine.resolve(
            expression = match.value,
            foodCategory = densityCategory,
            standardPortion = food?.servingSize,
            retrievalResult = retrievalResult,
        )

        val grams = if (subjectiveResult != null) {
            kotlin.math.round(subjectiveResult.grams * 10) / 10.0
        } else {
            val gramsPerUnit = getGramsForReference(refType, food)
            kotlin.math.round(gramsPerUnit * qty * 10) / 10.0
        }

        // Return foodPart as the working text so parseFragment can use it as the food name.
        // Using `cleaned` (text with match removed) was wrong: when the reference covers the full
        // fragment (e.g. "una taza de avena") cleaned becomes "" → foodName.length < 2 → null item.
        return ReferenceResult(grams, qty, foodPart, refType)
    }
    return resolveViaSubjectiveEngine(text, retrievalResult)
}

/**
 * F1.1: Fallback directo al motor subjetivo completo (~310 expresiones: "un montón de",
 * "una botella de", "una marraqueta", "una rodaja de", utensilios, comparaciones…).
 * Antes estas expresiones caían a UNSPECIFIED porque el gate de REFERENCE_KEYWORDS_FAST
 * y los ~19 REFERENCE_PATTERNS no las alcanzaban. El motor devuelve null si no matchea,
 * así que el fallback es seguro.
 */
private val SUBJECTIVE_PHRASE_STRIP = Regex(
    """^(?:(?:un|una|unos|unas|\d+(?:[.,]\d+)?)\s+)?[a-záéíóúñü]+(?:\s+[a-záéíóúñü]+){0,2}\s+de\s+(.+)$""",
    RegexOption.IGNORE_CASE,
)

// A drink in a vessel or a container ("un vaso de jugo de naranja", "una lata chica de coca cola", "2 botellas de agua con
// gas"): the vessel measures the drink, whatever the drink is called. Group 1 is the drink.
private val DRINK_IN_VESSEL = Regex(
    """^(?:(?:un|una|unos|unas|\d+(?:[.,]\d+)?|media|medio)\s+|(?:un|1)\s+cuarto\s+de\s+)?""" +
        """(?:vasos?|tazas?|copas?|jarr[oa]s?|pocillos?|tazones?|botell(?:as?|itas?)|latas?|latitas?|cart[oó](?:n|nes)|cajas?|cajitas?|vasit[oa]s?)""" +
        """(?:\s+(?:chic[oa]s?|peque[ñn][oa]s?|grandes?|rebosantes?))?\s+de\s+(.+)$""",
    RegexOption.IGNORE_CASE,
)
private val DRINK_HEAD = Regex(
    "^" + RegexEs.bounded("""aguas?|t[eé]s?|caf[eé]s?|jugos?|zumos?|n[eé]ctar(?:es)?|bebidas?|gaseosas?|cervezas?|chelas?|schops?|vinos?|refrescos?|mates?|infusi[oó]n(?:es)?|caldos?|leche|coca|sprite|fanta|pepsi|batidos?|licuados?|smoothies?"""),
    RegexOption.IGNORE_CASE,
)

private fun resolveViaSubjectiveEngine(
    text: String,
    retrievalResult: SemanticPortionRetriever.RetrievalResult?,
): ReferenceResult {
    val drinkInVessel = DRINK_IN_VESSEL.find(text)?.groupValues?.get(1)?.trim()?.takeIf { DRINK_HEAD.containsMatchIn(it) }
    // Entidades protegidas ("empanada de pino", "café con leche"…) se resuelven
    // como plato completo: el motor las fragmentaría mal ("una empanada de pino"
    // dejaría "pino" como alimento). Una bebida en su vaso o envase sí se mide: el
    // envase la mide y la bebida es todo lo que sigue a su "de".
    if (drinkInVessel == null && PROTECTED_ENTITIES_REGEX.containsMatchIn(text)) {
        return ReferenceResult(null, 1.0, text)
    }
    val food = findFoodByNormalized(text)
    val densityCategory = SubjectivePortionEngine.detectDensityCategory(text)
    val result = SubjectivePortionEngine.resolve(
        expression = text,
        foodCategory = densityCategory,
        standardPortion = food?.servingSize,
        retrievalResult = retrievalResult.takeUnless {
            HouseholdPortions.looksLikeCountExpression(text)
        },
    ) ?: return ReferenceResult(null, 1.0, text)

    // Quitar la frase subjetiva ("un montón de") conservando el alimento. Si no hay
    // "de" (ej. "una marraqueta"), se deja el texto completo: parseQuantityMultiplier
    // se encarga del artículo y deja "marraqueta" como nombre.
    // The strip below takes up to three words before the "de", which would swallow "vaso de jugo" of
    // "un vaso de jugo de manzana": a drink keeps its whole name.
    val foodPart = drinkInVessel?.takeIf { it.length >= 2 } ?: SUBJECTIVE_PHRASE_STRIP.find(text)
        ?.groupValues
        ?.get(1)
        ?.trim()
        ?.takeIf { it.length >= 2 }
        ?: text

    return ReferenceResult(
        grams = kotlin.math.round(result.grams * 10) / 10.0,
        // Without "de", the working text still contains the count and the
        // normal quantity parser will consume it ("2 marraquetas", not 2 × 2).
        quantity = result.relativeFactor.takeIf {
            foodPart != text && (result.source.startsWith("lexicon:") ||
                result.source.startsWith("utensil:") || result.source.startsWith("scoop:") ||
                result.source.startsWith("bread:") || result.source.startsWith("container:"))
        } ?: 1.0,
        foodPart = foodPart,
        unitId = result.source.takeUnless { it == "dataset-prior" || it.startsWith("subjective:") }
            ?.substringAfter(':')?.substringBefore(':'),
    )
}

private const val DATASET_PORTION_MIN_CONFIDENCE = 0.35

// ─── Extract Cooking Method ──────────────────────────────────────────────────

private fun extractCookingMethod(text: String): Pair<CookingMethod?, String> {
    val lower = text.lowercase()
    if (COOKING_KEYWORDS_FAST.none { lower.contains(it) }) {
        return Pair(null, text)
    }
    for ((pattern, method) in COOKING_PATTERNS) {
        val match = pattern.find(text) ?: continue
        val cleaned = text.replace(match.value, " ").replace(MULTISPACE_PATTERN, " ").trim()
        return Pair(method, cleaned)
    }
    return Pair(null, text)
}

// ─── Extract Portion ─────────────────────────────────────────────────────────

private fun extractPortionFromFragment(text: String): Pair<PortionPreset, String> {
    val lower = text.lowercase()
    if (PORTION_KEYWORDS_FAST.none { lower.contains(it) }) {
        return Pair(PortionPreset.MEDIUM, text)
    }
    for ((pattern, preset, _) in PORTION_PATTERNS) {
        val match = pattern.find(text) ?: continue
        val cleaned = text.replace(match.value, " ").replace(MULTISPACE_PATTERN, " ").trim()
        return Pair(preset, cleaned)
    }
    return Pair(PortionPreset.MEDIUM, text)
}

// ─── Quantity Multiplier ─────────────────────────────────────────────────────

private fun parseQuantityMultiplier(text: String): Pair<Double, String> {
    val trimmed = text.trim().replace(HALF_PATTERN, "0.5")
        .replace(QUARTER_PATTERN, "0.25")
        .replace(THREE_QUARTERS_PATTERN, "0.75")

    // Range: "1-2 manzanas"
    val rangeMatch = RANGE_QUANTITY_PATTERN.find(trimmed)
    if (rangeMatch != null) {
        val qty1 = rangeMatch.groupValues[1].toDoubleOrNull() ?: 1.0
        val qty2 = rangeMatch.groupValues[2].toDoubleOrNull() ?: 1.0
        val rest = rangeMatch.groupValues[3].trim()
        if (rest.length >= 2) {
            val avg = (qty1 + qty2) / 2.0
            return Pair(avg, rest)
        }
    }

    // Pair: "un par de huevos" → 2 (debe ir antes del patrón numérico)
    val pairMatch = PAIR_PATTERN.find(trimmed)
    if (pairMatch != null) {
        val rest = pairMatch.groupValues[1].trim()
        if (rest.length >= 2) {
            return Pair(2.0, rest)
        }
    }

    // Suffix multiplier: "huevos x2", "3 huevos x2" → cantidad × sufijo
    val suffixMatch = SUFFIX_X_PATTERN.find(trimmed)
    if (suffixMatch != null) {
        val rest = suffixMatch.groupValues[2].trim()
        if (rest.length >= 2) {
            val suffix = suffixMatch.groupValues[3].toDoubleOrNull() ?: 1.0
            val base = suffixMatch.groupValues[1].toDoubleOrNull() ?: 1.0
            return Pair(base * suffix, rest)
        }
    }

    // The normalizer has already preserved lexicalized food names. Do not undo
    // that protection by interpreting "tres leches" as three units of milk.
    if (TextNormalizer.startsWithNumberWordFoodName(trimmed)) return Pair(1.0, trimmed)

    // Number: "2 manzanas", "3 huevos"
    val numMatch = NUMBER_QUANTITY_PATTERN.find(trimmed)
    if (numMatch != null) {
        val qty = numMatch.groupValues[1].toDoubleOrNull() ?: 1.0
        val rest = numMatch.groupValues[2].trim()
        if (rest.length >= 2) {
            return Pair(qty.coerceAtLeast(0.0), rest)
        }
    }

    // Literal: "dos huevos", "media manzana"
    val literalMatch = LITERAL_QUANTITY_PATTERN.find(trimmed)
    if (literalMatch != null) {
        val qty = LITERAL_QUANTITIES[literalMatch.groupValues[1].lowercase()]
        val rest = literalMatch.groupValues[2].trim()
        if (qty != null && rest.length >= 2) {
            return Pair(qty, rest)
        }
    }

    return Pair(1.0, trimmed)
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

private fun stripAccents(text: String): String =
    java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
        .replace(TextKeys.MARKS, "")

/**
 * Clave canónica para DEDUPE (G5/G7): minúsculas sin tildes y singularizada.
 * "huevo", "huevos", "Huevo" y "2 huevos" + "1 huevo" se fusionan como el mismo alimento.
 * Solo afecta la fusión de items; el tag visible se conserva tal cual.
 */
private fun canonicalTagKey(tag: String): String =
    SpanishSingularizer.singularize(stripAccents(tag.lowercase()), SINGULARIZER_LEXICON)

private fun extractGlobalPortion(description: String): PortionPreset {
    for ((pattern, preset, _) in PORTION_PATTERNS) {
        if (pattern.containsMatchIn(description)) return preset
    }
    return PortionPreset.MEDIUM
}

private fun isCookieOrCrackerName(name: String): Boolean {
    val n = FoodIdentity.normalize(name)
    return n.contains("galleta") || n.contains("cookie")
}

private fun isNamedDishPhrase(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.length < 4) return false
    if (PROTECTED_ENTITIES.any { it.equals(trimmed, ignoreCase = true) }) return true
    val exact = findFoodExactByNormalized(trimmed) ?: return false
    if (exact.tags.any { it.equals("preparacion", ignoreCase = true) }) return true
    if (exact.name.contains('(')) return false
    return exact.name.contains(' ')
}

private fun normalizeFoodName(name: String, singularize: Boolean = false): String {
    var normalized = name.trim()
        .lowercase()
        .replace(SPACES_PATTERN, " ")
        .replace(LEADING_DE_PATTERN, "")
        .replace(LEADING_ARTICLE_PATTERN, "")
        .replace(PORTION_PREFIX_PATTERN, "")
        .replace(ARTICLE_PORTION_PREFIX_PATTERN, "")
        .replace(TRAILING_DE_PATTERN, "")

    // Diminutivos con validación de raíz: "huevito"→"huevo" pero "mantequilla" NO se rompe
    normalized = TextNormalizer.canonicalizeDiminutives(normalized)

    if (singularize) normalized = singularOfShortPlural(SpanishSingularizer.singularize(normalized, SINGULARIZER_LEXICON))

    return normalized
}

/**
 * A count of a one-word plural of three letters ("2 tés"). The singularizer never changes a word of three letters or fewer, and
 * the plural rule of the catalog lookup ([FoodSearchRanker.stem], used by [findFoodExactByNormalized] since WP-S6) leaves a word
 * of fewer than four letters alone too, so "tés" named no food while "té" did: the count became a 350 g dish estimate. When such
 * a plural names nothing in the catalog and its singular names a food, the count is of the singular, as it is for "3 tomates".
 */
private fun singularOfShortPlural(name: String): String {
    if (name.length != 3 || !name.endsWith('s')) return name
    val singular = name.dropLast(1)
    return if (findFoodExactByNormalized(name) == null && findFoodExactByNormalized(singular) != null) singular else name
}

// ─── Anatomical / Preparation Modifiers ────────────────────────────────

private data class ModifierResult(
    val macroScale: MacroScale?,
    val grams: Double?,
    val cleanedText: String,
)

private data class MacroScale(
    val kcal: Double = 1.0,
    val protein: Double = 1.0,
    val carbs: Double = 1.0,
    val fats: Double = 1.0,
)

private val LEADING_COUNT_PATTERN = Regex("""^(?:\d+(?:[.,]\d+)?|un|una|uno)\s+""", RegexOption.IGNORE_CASE)

private val MODIFIER_PATTERNS = listOf(
    // sin piel / sin grasa → fats ×0.6
    Pair(Regex("""\bsin\s+(piel|grasa)\b""", RegexOption.IGNORE_CASE),
        MacroScale(fats = 0.6)),
    // sin miga (pan) → carbs ×0.6, kcal ×0.65
    Pair(Regex("""\bsin\s+miga\b""", RegexOption.IGNORE_CASE),
        MacroScale(kcal = 0.65, carbs = 0.6)),
    // solo claras → lower protein/fat
    Pair(Regex("""\bsolo?\s+claras?\b""", RegexOption.IGNORE_CASE),
        MacroScale(kcal = 0.55, protein = 0.85, carbs = 1.0, fats = 0.05)),
    // descremado / light / 0% → fats ×0.15 (lácteos)
    Pair(Regex("""\b(descremad[oa]|light|0\s*%)\b""", RegexOption.IGNORE_CASE),
        MacroScale(kcal = 0.7, fats = 0.15)),
    // en almíbar / con azúcar → carbs ×1.4, kcal ×1.3
    Pair(Regex("""\ben\s+alm[ií]bar\b|\bcon\s+az[uú]car\b""", RegexOption.IGNORE_CASE),
        MacroScale(kcal = 1.3, carbs = 1.4)),
    // integral → slight fiber boost (handled by finding integral variant)
    Pair(Regex("""\bintegral(es)?\b""", RegexOption.IGNORE_CASE),
        MacroScale(carbs = 0.9, kcal = 0.95)),
    // grande / colmada → portion ×1.25
    Pair(Regex("""\b(colmad[oa]|generos[oa])\b""", RegexOption.IGNORE_CASE),
        MacroScale()), // handled as portion modifier, not macro
    // rasa / fina / pequeña → portion ×0.75
    Pair(Regex("""\b(ras[oa]|fin[oa]|pequeñ[oa])\b""", RegexOption.IGNORE_CASE),
        MacroScale()), // handled as portion modifier
)

private fun extractModifiers(
    text: String,
    currentGrams: Double?,
    amountIntent: AmountIntent = AmountIntent.UNSPECIFIED,
): Triple<MacroScale?, Double?, String> {
    val lower = text.lowercase()
    if (MODIFIER_KEYWORDS_FAST.none { lower.contains(it) }) {
        return Triple(null, currentGrams, text)
    }
    // E16: una frase de alimento conocida completa ("pan integral", "arroz
    // integral") es identidad: el modificador ya vive en la fila del catálogo
    // y no debe arrancarle la palabra ("pan integral" ≠ "pan").
    // The count in front of it ("1 bebida light") does not turn the catalog phrase into another food.
    val phrase = text.trim()
    val phraseWithoutCount = phrase.replace(LEADING_COUNT_PATTERN, "")
    if (PROTECTED_ENTITY_PHRASES.any { it.equals(phrase, ignoreCase = true) || it.equals(phraseWithoutCount, ignoreCase = true) }) {
        return Triple(null, currentGrams, text)
    }
    var working = text
    var resultScale: MacroScale? = null
    var gramsOverride = currentGrams

    for ((pattern, scale) in MODIFIER_PATTERNS) {
        val match = pattern.find(working) ?: continue
        working = working.replace(match.value, " ").replace(MULTISPACE_PATTERN, " ").trim()

        // Portion modifiers (colmada/rasa) scale subjective grams once; never touch explicit mass.
        val matchText = match.value.lowercase()
        if (matchText.contains("colmad") || matchText.contains("generos")) {
            if (amountIntent != AmountIntent.EXPLICIT_MASS && gramsOverride != null) {
                gramsOverride = gramsOverride * (PORTION_MULTIPLIERS[PortionPreset.LARGE] ?: 1.0)
            }
        } else if (matchText.contains("rasa") || matchText.contains("fina") || matchText.contains("pequeñ")) {
            if (amountIntent != AmountIntent.EXPLICIT_MASS && gramsOverride != null) {
                gramsOverride = gramsOverride * (PORTION_MULTIPLIERS[PortionPreset.SMALL] ?: 1.0)
            }
        } else {
            // It's a macro modifier - combine scales
            resultScale = if (resultScale != null) {
                MacroScale(
                    kcal = resultScale.kcal * scale.kcal,
                    protein = resultScale.protein * scale.protein,
                    carbs = resultScale.carbs * scale.carbs,
                    fats = resultScale.fats * scale.fats,
                )
            } else scale
        }
    }

    return Triple(resultScale, gramsOverride, working)
}
