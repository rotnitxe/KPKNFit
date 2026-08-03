package com.example.kpkn.domain.nutrition

import java.text.Normalizer

/**
 * TextNormalizer — Pre-processes "dirty" user input before FoodParser and SmartFoodResolver.
 * Handles: emojis, fillers, voice hedges, repeated letters, shorthand, diminutivos, typos.
 */
object TextNormalizer {

    // ─── Emoji → word mapping (50+ food emojis) ────────────────────────
    private val EMOJI_MAP = mapOf(
        "\uD83E\uDD51" to "palta",       // 🥑
        "\uD83C\uDF57" to "pollo",       // 🍗
        "\uD83E\uDD5A" to "huevo",       // 🥚
        "\uD83C\uDF5E" to "pan",         // 🍞
        "\uD83C\uDF5A" to "arroz",       // 🍚
        "\uD83C\uDF4C" to "platano",     // 🍌
        "\uD83E\uDD5B" to "leche",       // 🥛
        "\uD83C\uDF4E" to "manzana",     // 🍎
        "\uD83C\uDF54" to "hamburguesa", // 🍔
        "\uD83C\uDF55" to "pizza",       // 🍕
        "\uD83C\uDF63" to "sushi",       // 🍣
        "\uD83C\uDF2E" to "taco",        // 🌮
        "\uD83C\uDF2F" to "burrito",     // 🌯
        "\uD83E\uDD50" to "pan",         // 🥐
        "\uD83E\uDD55" to "zanahoria",   // 🥕
        "\uD83E\uDD52" to "pepino",      // 🥒
        "\uD83C\uDF45" to "tomate",      // 🍅
        "\uD83C\uDF53" to "frutilla",    // 🍓
        "\uD83E\uDD5E" to "queso",       // 🧀
        "\uD83C\uDF47" to "uva",         // 🍇
        "\uD83C\uDF4A" to "naranja",     // 🍊
        "\uD83C\uDF4B" to "limon",       // 🍋
        "\uD83C\uDF49" to "sandia",      // 🍉
        "\uD83C\uDF51" to "durazno",     // 🍑
        "\uD83C\uDF52" to "cereza",      // 🍒
        "\uD83E\uDD5D" to "kiwi",        // 🥝
        "\uD83C\uDF4D" to "pina",        // 🍍
        "\uD83E\uDD65" to "coco",        // 🥥
        "\uD83E\uDD5C" to "mani",        // 🥜
        "\uD83C\uDF3D" to "choclo",      // 🌽
        "\uD83E\uDD66" to "brocoli",     // 🥦
        "\uD83C\uDF46" to "berenjena",   // 🍆
        "\uD83E\uDD57" to "ensalada",    // 🥗
        "\uD83C\uDF5D" to "pasta",       // 🍝
        "\uD83C\uDF5C" to "sopa",        // 🍜
        "\uD83C\uDF72" to "guiso",       // 🍲
        "\uD83C\uDF5F" to "papas fritas",// 🍟
        "\uD83E\uDD69" to "carne",       // 🥩
        "\uD83C\uDF56" to "carne",       // 🍖
        "\uD83C\uDF64" to "camaron",     // 🍤
        "\uD83D\uDC1F" to "pescado",     // 🐟
        "\uD83E\uDD90" to "camaron",     // 🦐
        "\uD83C\uDF70" to "pastel",      // 🍰
        "\uD83C\uDF6B" to "chocolate",   // 🍫
        "\uD83C\uDF69" to "donut",       // 🍩
        "\uD83C\uDF6A" to "galleta",     // 🍪
        "\uD83C\uDF6F" to "miel",        // 🍯
        "\uD83C\uDF7A" to "cerveza",     // 🍺
        "\uD83C\uDF77" to "vino",        // 🍷
        "\uD83E\uDD43" to "cafe",        // ☕
        "\uD83C\uDF75" to "te",          // 🍵
        "\uD83E\uDD64" to "jugo",        // 🧃
        "\uD83E\uDDCA" to "hielo",       // 🧊
        "\uD83E\uDDC2" to "sal",         // 🧂
    )

    // ─── Voice fillers ────────────────────────────────────────────────────
    private val FILLER_PATTERN = Regex(
        """\b(eh{1,}|este|osea|o\s+sea|como\s+que|m{3,}|aj[aá]|a\s+ver|por\s+a[hí]|no\s+s[eé]|ps|pe)\b""",
        RegexOption.IGNORE_CASE
    )

    // ─── Quantity hedges ──────────────────────────────────────────────────
    private val HEDGE_PATTERN = Regex(
        """\b(creo\s+que\s+(?:fue|era)|me\s+parece|m[aá]s\s+o\s+menos|como\s+unos|casi|alrededor\s+de|cerca\s+de|aprox(?:imadamente)?|tipo|unos|unas|lo\s+que\s+(?:sobr[oó]|qued[oó])\s+de|el\s+resto\s+de)\s*""",
        RegexOption.IGNORE_CASE
    )

    // ─── Repeated letters from voice/chat noise ───────────────────────────
    private val REPEATED_VOWELS = Regex("""([aeiouáéíóúü])\1+""", RegexOption.IGNORE_CASE)
    private val REPEATED_VOWELS_3_PLUS = Regex("""([aeiouáéíóúü])\1{2,}""", RegexOption.IGNORE_CASE)
    private val REPEATED_LETTERS_3_PLUS = Regex("""([a-záéíóúüñ])\1{2,}""", RegexOption.IGNORE_CASE)

    // ─── Repeated punctuation → remove ────────────────────────────────────
    private val REPEATED_PUNCT = Regex("""[!?]{2,}|\.{3,}""")

    // ─── Common typos ─────────────────────────────────────────────────────
    private val TYPO_MAP = mapOf(
        "poyo" to "pollo", "polllo" to "pollo", "pyo" to "pollo",
        "arros" to "arroz", "arro" to "arroz", "aros" to "arroz",
        "uebo" to "huevo", "wevo" to "huevo", "guevo" to "huevo", "güevo" to "huevo",
        "gueso" to "queso", "keso" to "queso",
        "panna" to "pana",
        "papa" to "papa", "papas" to "papa",
        "tomate" to "tomate", "tomate" to "tomate",
        "cebolla" to "cebolla", "cebolla" to "cebolla",
        "zanahoria" to "zanahoria", "sanahoria" to "zanahoria",
        "platano" to "platano", "plátano" to "platano",
        "naranja" to "naranja", "naraja" to "naranja",
        "manzana" to "manzana", "mansana" to "manzana",
        "lechuga" to "lechuga", "lechua" to "lechuga",
        "brocoli" to "brocoli", "brocolí" to "brocoli",
        "espinaca" to "espinaca", "espina" to "espinaca",
        "palta" to "palta", "aguacate" to "palta",
        "choclo" to "choclo", "maiz" to "choclo", "maíz" to "choclo",
        "poroto" to "poroto", "porotos" to "poroto",
        "lenteja" to "lenteja", "lentejas" to "lenteja",
        "garbanzo" to "garbanzo", "garbanzos" to "garbanzo",
        "avena" to "avena", "abena" to "avena",
        "merluza" to "merluza", "merluza" to "merluza",
        "salmon" to "salmon", "salmón" to "salmon",
        "camaron" to "camaron", "camarón" to "camaron",
        "pimenton" to "pimenton", "pimentón" to "pimenton",
        "betarraga" to "betarraga", "remolacha" to "betarraga",
        "zapallo" to "zapallo", "calabaza" to "zapallo",
        "marraqueta" to "marraqueta", "marraqueta" to "marraqueta",
        "hallulla" to "hallulla",
        "empanada" to "empanada", "empanada" to "empanada",
        "cazuela" to "cazuela",
        "charquican" to "charquicán", "charquicán" to "charquicán",
        "porotos granados" to "porotos granados",
        "completo" to "completo",
        "chorrillana" to "chorrillana",
    )

    // ─── English → Spanish food words + culinary jargon ─────────────────────
    private val EN_ES_MAP = mapOf(
        "chicken" to "pollo", "rice" to "arroz", "egg" to "huevo",
        "bread" to "pan", "milk" to "leche", "cheese" to "queso",
        "fish" to "pescado", "shrimp" to "camaron", "beef" to "vacuno",
        "pork" to "cerdo", "oats" to "avena", "grilled" to "plancha",
        "fried" to "frito", "baked" to "horno", "steamed" to "vapor",
        "boiled" to "cocido", "smoked" to "ahumado", "raw" to "crudo",
        "banana" to "platano", "apple" to "manzana", "avocado" to "palta",
        "potato" to "papa", "tomato" to "tomate", "onion" to "cebolla",
        "garlic" to "ajo", "pepper" to "pimenton", "carrot" to "zanahoria",
        "pasta" to "pasta", "salad" to "ensalada",
        "soup" to "sopa", "juice" to "jugo", "coffee" to "cafe",
        "tea" to "te", "water" to "agua", "beer" to "cerveza",
        "wine" to "vino", "yogurt" to "yogurt", "honey" to "miel",
        "sugar" to "azucar", "salt" to "sal", "oil" to "aceite",
        "butter" to "mantequilla", "cream" to "crema",
        // Plurales y cortes comunes
        "eggs" to "huevos", "apples" to "manzanas", "bananas" to "platanos",
        "potatoes" to "papas", "tomatoes" to "tomates", "carrots" to "zanahorias",
        "onions" to "cebollas", "avocados" to "paltas", "oranges" to "naranjas",
        "grapes" to "uvas", "strawberries" to "frutillas", "almonds" to "almendras",
        "walnuts" to "nueces", "mushrooms" to "champiñones", "beans" to "porotos",
        "lentils" to "lentejas", "chickpeas" to "garbanzos", "sausages" to "salchichas",
        "pancakes" to "panqueques", "blueberries" to "arandanos", "pineapples" to "pinas",
        "cherries" to "cerezas", "peaches" to "duraznos", "lemons" to "limones",
        "cucumbers" to "pepinos", "breast" to "pechuga", "thigh" to "muslo",
        "turkey" to "pavo", "steak" to "bistec", "bacon" to "tocino", "ham" to "jamon",
        "lamb" to "cordero", "tuna" to "atun", "oatmeal" to "avena", "cereal" to "cereal",
        "jam" to "mermelada", "spinach" to "espinaca",
        // Culinary jargon
        "al dente" to "al dente",
        "golden" to "dorado", "crispy" to "crocante", "juicy" to "jugoso",
        "overcooked" to "pasado", "undercooked" to "poco cocido",
        "well done" to "bien cocido", "medium" to "termino medio",
        "rare" to "poco cocido", "medium rare" to "medio crudo",
    )

    // ─── Shorthand / chat abbreviations ───────────────────────────────────
    private val SHORTHAND_PATTERN = Regex(
        """\b(xq|pq|porq|q|ke|tmb|tb|grs?|gramit[oa]s|gramines?|gramos?|gr|kilit[oa]s|kls|kgs|mililitr[oa]s|mlts|cdas?|cdita|cucharaditas?)\b""",
        RegexOption.IGNORE_CASE
    )

    // ─── Number words → digits ────────────────────────────────────────────
    private val NUMBER_WORDS = mapOf(
        "un" to 1, "uno" to 1, "una" to 1,
        "dos" to 2, "tres" to 3, "cuatro" to 4, "cinco" to 5,
        "seis" to 6, "siete" to 7, "ocho" to 8, "nueve" to 9, "diez" to 10,
        // E16: "once" NO se convierte: en Chile es la merienda ("la once") y el
        // parser ya soporta "once huevos" como cantidad literal.
        "doce" to 12, "trece" to 13, "catorce" to 14, "quince" to 15,
        "dieciseis" to 16, "diecisiete" to 17, "dieciocho" to 18, "diecinueve" to 19,
        "veinte" to 20, "treinta" to 30, "cuarenta" to 40, "cincuenta" to 50,
        "sesenta" to 60, "setenta" to 70, "ochenta" to 80, "noventa" to 90,
        "cien" to 100, "ciento" to 100, "doscientos" to 200, "trescientos" to 300,
        "cuatrocientos" to 400, "quinientos" to 500,
        "seiscientos" to 600, "setecientos" to 700, "ochocientos" to 800, "novecientos" to 900,
        "mil" to 1000,
    )

    // Common food roots for augmentative validation
    private val COMMON_FOOD_ROOTS = setOf(
        "pan", "carne", "pollo", "pescado", "huevo", "arroz", "papa", "pasta",
        "leche", "agua", "jugo", "cafe", "te", "vino", "cerveza", "queso",
        "yogurt", "crema", "mantequilla", "azucar", "sal", "aceite", "ajo",
        "cebolla", "tomate", "palta", "platano", "manzana", "naranja", "limon",
        "zanahoria", "pepino", "lechuga", "espinaca", "brócoli", "coliflor",
        "poroto", "lenteja", "garbanzo", "avena", "trigo", "maiz", "platano",
        "sopa", "ensalada", "guiso", "asado", "ají", "pimenton", "choclo",
        "churrasco", "bistec", "hamburguesa", "pizza", "taco", "burrito", "sushi",
    )

    // ─── Fractional patterns ──────────────────────────────────────────────
    private val FRACTION_PATTERN = Regex(
        """\b(medio\s+kilo|cuarto\s+kilo|un\s+kilo\s+y\s+medio)\b""",
        RegexOption.IGNORE_CASE
    )

    // B8: nombres de platos que contienen números-palabra. Convertir "tres leches"
    // → "3 leches" rompería el plato; se enmascaran antes de convertNumberWords.
    private val NUMBER_WORD_PLATES = listOf(
        "tres leches", "cuatro leches", "mil hojas", "cuatro quesos",
        "tres quesos", "dos quesos", "cinco quesos",
    )

    private val NUMBER_WORD_PLATES_REGEX by lazy {
        Regex(
            NUMBER_WORD_PLATES.joinToString("|") { "\\b${Regex.escape(it)}\\b" },
            RegexOption.IGNORE_CASE,
        )
    }

    private val SPACES_PATTERN = Regex("\\s+")
    private val MULTISPACE_PATTERN = Regex("\\s{2,}")

    private val PLURAL_WORD_PATTERN = Regex(
        """(?<![a-záéíóúñü])[a-záéíóúñü]+(?:es|s)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val TYPO_REGEX_LIST: List<Pair<Regex, String>> by lazy {
        TYPO_MAP.map { (typo, correction) ->
            Regex("""\b${Regex.escape(typo)}\b""", RegexOption.IGNORE_CASE) to correction
        }
    }

    private val EN_ES_REGEX_LIST: List<Pair<Regex, String>> by lazy {
        EN_ES_MAP.map { (en, es) ->
            Regex("""\b${Regex.escape(en)}\b""", RegexOption.IGNORE_CASE) to es
        }
    }

    // ─── Estructura EN → ES (solo con ≥2 señales de inglés) ─────────────────

    /** Frases de medida primero (más específicas), luego conectores. */
    private val EN_STRUCTURE_REPLACEMENTS: List<Pair<Regex, String>> = listOf(
        Regex("""\bhalf\s+a\s+cup\s+of\b""", RegexOption.IGNORE_CASE) to "media taza de",
        Regex("""\bhalf\s+an?\b""", RegexOption.IGNORE_CASE) to "media",
        Regex("""\bquarter\s+of\s+an?\b""", RegexOption.IGNORE_CASE) to "cuarto de",
        Regex("""\ba\s+cup\s+of\b""", RegexOption.IGNORE_CASE) to "una taza de",
        Regex("""\ba\s+glass\s+of\b""", RegexOption.IGNORE_CASE) to "un vaso de",
        Regex("""\ba\s+tablespoon\s+of\b""", RegexOption.IGNORE_CASE) to "una cucharada de",
        Regex("""\btablespoons?\s+of\b""", RegexOption.IGNORE_CASE) to "cucharadas de",
        Regex("""\ba\s+teaspoon\s+of\b""", RegexOption.IGNORE_CASE) to "una cucharadita de",
        Regex("""\bteaspoons?\s+of\b""", RegexOption.IGNORE_CASE) to "cucharaditas de",
        Regex("""\ba\s+handful\s+of\b""", RegexOption.IGNORE_CASE) to "un puñado de",
        Regex("""\ba\s+slice\s+of\b""", RegexOption.IGNORE_CASE) to "una rebanada de",
        Regex("""\bslices\s+of\b""", RegexOption.IGNORE_CASE) to "rebanadas de",
        Regex("""\ba\s+bowl\s+of\b""", RegexOption.IGNORE_CASE) to "un bol de",
        Regex("""\ba\s+can\s+of\b""", RegexOption.IGNORE_CASE) to "una lata de",
        Regex("""\ba\s+scoop\s+of\b""", RegexOption.IGNORE_CASE) to "un scoop de",
        Regex("""\bgrams?\s+of\b|\bgr\s+of\b""", RegexOption.IGNORE_CASE) to "g de",
        Regex("""\bml\s+of\b""", RegexOption.IGNORE_CASE) to "ml de",
        Regex("""\bkg\s+of\b""", RegexOption.IGNORE_CASE) to "kg de",
        Regex("""\band\b""", RegexOption.IGNORE_CASE) to "y",
        Regex("""\bwith\b""", RegexOption.IGNORE_CASE) to "con",
        Regex("""\bplus\b""", RegexOption.IGNORE_CASE) to "+",
        Regex("""\bwithout\b""", RegexOption.IGNORE_CASE) to "sin",
        Regex("""\bno\b""", RegexOption.IGNORE_CASE) to "sin",
        Regex("""\bof\b""", RegexOption.IGNORE_CASE) to "de",
        Regex("""\ba\b""", RegexOption.IGNORE_CASE) to "un",
        Regex("""\ban\b""", RegexOption.IGNORE_CASE) to "un",
    )

    private val EN_NUMBER_WORDS = mapOf(
        "one" to "1", "two" to "2", "three" to "3", "four" to "4", "five" to "5",
        "six" to "6", "seven" to "7", "eight" to "8", "nine" to "9", "ten" to "10",
        "eleven" to "11", "twelve" to "12", "thirteen" to "13", "fourteen" to "14",
        "fifteen" to "15", "sixteen" to "16", "seventeen" to "17", "eighteen" to "18",
        "nineteen" to "19", "twenty" to "20", "thirty" to "30",
    )

    private val EN_NUMBER_WORD_REGEX_LIST: List<Pair<Regex, String>> by lazy {
        EN_NUMBER_WORDS.map { (word, num) ->
            Regex("""\b${Regex.escape(word)}\b""", RegexOption.IGNORE_CASE) to num
        }
    }

    /** Palabras señal de inglés — un texto con ≥2 de ellas se traduce estructuralmente. */
    private val EN_SIGNAL_WORDS: Set<String> = buildSet {
        addAll(EN_ES_MAP.keys)
        addAll(
            listOf(
                "and", "with", "plus", "without", "no", "of", "a", "an", "half", "quarter",
                "cup", "glass", "tablespoon", "teaspoon", "handful", "slice", "bowl",
                "can", "scoop", "grams", "gram", "gr", "ml", "kg", "breast", "thigh",
                "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
                "ten", "eleven", "twelve", "fifteen", "twenty",
            ),
        )
    }

    private fun isLikelyEnglish(text: String): Boolean {
        val tokens = text.lowercase().split(SPACES_PATTERN)
        var hits = 0
        for (token in tokens) {
            if (token in EN_SIGNAL_WORDS) {
                hits++
                if (hits >= 2) return true
            }
        }
        return false
    }

    private fun applyEnglishStructure(text: String): String {
        var result = text
        for ((regex, replacement) in EN_STRUCTURE_REPLACEMENTS) {
            result = regex.replace(result) { " $replacement " }
        }
        return result.replace(MULTISPACE_PATTERN, " ").trim()
    }

    private fun applyEnglishNumberWords(text: String): String {
        var result = text
        for ((regex, num) in EN_NUMBER_WORD_REGEX_LIST) {
            result = regex.replace(result, num)
        }
        return result
    }

    // Sustantivos de referencia/medida que exigen el literal "un/una" para que
    // REFERENCE_PATTERNS y SubjectivePortionEngine los reconozcan (B1 + F1.1):
    // convertir "un poco" → "1 poco" rompe "un poco de aceite"; "una marraqueta"
    // tampoco matchearía los patrones del motor con "1 marraqueta".
    // OJO: debe ser UNA sola línea — los saltos de línea en raw strings son literales.
    private val NUMBER_WORD_EXCLUDE_NEXT = Regex(
        """\s+(?:poc[oa]|poquit\w*|pizca\w*|chorrit\w*|par|mont[oó]n\w*|tantico|chin\b|pel[ií]n\b|miaja\w*|gotit\w*|gota\w*|hilito|hilo\b|velo\b|chorret[oó]n\w*|chorro\w*|cul[ií]n\w*|culillo|fondo\w*|capit\w*|capa\w*|raci[oó]n\w*|cerro\w*|barbaridad\w*|bestialidad\w*|exageraci[oó]n\w*|disparate\w*|porr[oó]n\w*|cuchar[oó]n\w*|tacit\w*|pocill\w*|taz[oó]n\w*|fuente\w*|vaso\w*|copa\w*|caballit\w*|dedal\w*|plato\w*|bol(?:es)?\b|bowl\w*|botell\w*|bot[ée]\b|frasco\w*|caja\w*|bolsa\w*|paquet\w*|sobre\b|marraquet\w*|hallull\w*|empanad\w*|gallet\w*|tortill\w*|boll\w*|tamal\w*|pastel\w*|bizcoch\w*|panecill\w*|mollet\w*|arep\w*|rebanad\w*|hogaza\w*|puñad\w*|pu[ñn]o\w*|rodaja\w*|tajad\w*|trozo\w*|pedaz\w*|lonch\w*|l[aá]mina\w*|tira\w*|gajo\w*|raja\w*|cu[ñn]a\w*|esquina\w*|punta\w*|tri[aá]ngulo\w*|dado\b|cubito\w*|pastilla\w*|tableta\w*|barra\w*|onza\w*|nuez\b|avellana\w*|aceituna\w*|garbanzo\w*|grano\w*|hoja\w*|ram[ao]\w*|ramillet\w*|tallo\w*|cabeza\w*|diente\w*|cogollo\w*|vara\w*|astilla\w*|pellizc\w*|dedo\w*|palma\w*|nudillo\w*|scoop\w*|medida\w*)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val NUMBER_WORD_REGEX_LIST: List<Triple<String, Regex, String>> by lazy {
        NUMBER_WORDS.map { (word, num) ->
            val exclusion = if (word == "un" || word == "una" || word == "uno") {
                """(?!${NUMBER_WORD_EXCLUDE_NEXT.pattern})"""
            } else {
                ""
            }
            Triple(
                word,
                Regex("""\b${Regex.escape(word)}\b$exclusion""", RegexOption.IGNORE_CASE),
                num.toString(),
            )
        }
    }

    /**
     * Full normalization pipeline. Apply before FoodParser and SmartFoodResolver.
     */
    fun normalize(input: String): String {
        var text = input.trim()
        if (text.isEmpty()) return text

        // 1. Strip emojis → replace with words
        text = replaceEmojis(text)

        // 2. Strip repeated punctuation
        text = REPEATED_PUNCT.replace(text, "")

        // 3. Collapse 3+ repeated vowels (voice noise: "eeeeehhh" → "ehhh",
        //    "polloooo" → "pollo"). NO toca "coffee" (solo 2 vocales).
        text = REPEATED_VOWELS_3_PLUS.replace(text) { it.groupValues[1] }

        // 4. Strip voice fillers
        text = FILLER_PATTERN.replace(text, "")

        // 5. Strip quantity hedges (keep the number that follows)
        text = HEDGE_PATTERN.replace(text, "")

        // 5. Apply shorthand replacements
        text = applyShorthand(text)

        // 6. Apply common typos
        text = applyTypos(text)
        text = REPEATED_LETTERS_3_PLUS.replace(text) { it.groupValues[1] }

        // 7. Map English → Spanish.
        //    7a. Estructural: conectores, medidas y negación (solo si hay ≥2 señales EN,
        //        para no romper español: "no" español ≠ "no" inglés).
        //    7b. Número-palabras EN ("two eggs" → "2 eggs").
        //    7c. Vocabulario de alimentos (mapa existente).
        if (isLikelyEnglish(text)) {
            text = applyEnglishStructure(text)
            text = applyEnglishNumberWords(text)
        }
        text = applyEnglishMapping(text)

        // 8. Collapse repeated vowels (polloooo → pollo). Va DESPUÉS del mapeo EN:
        //    "coffee" tiene "ee" legítimo que el colapso destruiría antes de traducirse.
        text = REPEATED_VOWELS.replace(text) { it.groupValues[1] }

        // 9. Expand fractional patterns
        text = expandFractions(text)

        // 10. Convert number words to digits (B8: protegiendo nombres de platos
        //     como "tres leches" o "mil hojas" que contienen números-palabra).
        text = convertNumberWordsProtectingPlates(text)

        // 11. Final cleanup: collapse multiple spaces
        text = text.replace(MULTISPACE_PATTERN, " ").trim()

        return text
    }

    private fun convertNumberWordsProtectingPlates(text: String): String {
        val masks = mutableListOf<Pair<String, String>>()
        val masked = NUMBER_WORD_PLATES_REGEX.replace(text) { match ->
            val token = "__NUMBERPLATE_${masks.size}__"
            masks.add(token to match.value)
            token
        }
        var result = convertNumberWords(masked)
        for ((token, original) in masks) {
            result = result.replace(token, original)
        }
        return result
    }

    /**
     * Normalize a food name specifically (additional to general normalization).
     * Handles diminutivos and aumentativos.
     */
    fun normalizeFoodName(name: String): String {
        var normalized = name.trim().lowercase()
            .replace(SPACES_PATTERN, " ")

        normalized = canonicalizeDiminutives(normalized)

        // Aumentativos: -azo/-aza/-ote/-ota (only if root exists in common foods)
        val augmentativeSuffixes = listOf("azo", "aza", "ote", "ota")
        for (suffix in augmentativeSuffixes) {
            if (normalized.endsWith(suffix) && normalized.length > suffix.length + 2) {
                val root = normalized.dropLast(suffix.length)
                // Check if root (or root+"o"/"a"/"e") is a known food
                val candidates = listOf(root, root + "o", root + "a", root + "e")
                val matched = candidates.firstOrNull { it in COMMON_FOOD_ROOTS }
                if (matched != null) {
                    normalized = matched
                    break
                }
            }
        }

        return normalized
    }

    /**
     * Reduce diminutivos (-ito/-ita/-illo/-illa/-cito/-cita/-ecito/-ecita) solo cuando la
     * raíz (o raíz + o/a/e) existe en COMMON_FOOD_ROOTS. Evita falsos positivos como
     * "mantequilla" → "mantequ", que el regex ciego rompe. Opera palabra a palabra
     * ("huevitos revueltos" → "huevos revueltos") y tolera plurales ("huevitos" → "huevo").
     */
    fun canonicalizeDiminutives(name: String): String {
        val diminutiveSuffixes = listOf("ecito", "ecita", "cito", "cita", "illo", "illa", "ito", "ita")

        fun tryWord(word: String): String {
            val attempts = if (word.endsWith("s")) listOf(word, word.dropLast(1)) else listOf(word)
            for (w in attempts) {
                for (suffix in diminutiveSuffixes) {
                    if (w.endsWith(suffix) && w.length > suffix.length + 2) {
                        val root = w.dropLast(suffix.length)
                        if (root.length >= 3) {
                            val candidates = listOf(root, root + "o", root + "a", root + "e")
                            val validated = candidates.firstOrNull { it in COMMON_FOOD_ROOTS }
                            if (validated != null) return validated
                        }
                    }
                }
            }
            return word
        }

        return name.split(' ').joinToString(" ") { tryWord(it) }
    }

    // ─── Internal ──────────────────────────────────────────────────────────

    private fun stripRemainingEmojis(text: String): String {
        return text.filter { ch ->
            val type = Character.getType(ch)
            type != Character.SURROGATE.toInt() && type != Character.PRIVATE_USE.toInt() &&
            !(ch in '\uD800'..'\uDFFF') && !(ch in '\uE000'..'\uF8FF')
        }
    }

    private fun replaceEmojis(text: String): String {
        var result = text
        for ((emoji, word) in EMOJI_MAP) {
            result = result.replace(emoji, " $word ")
        }
        return stripRemainingEmojis(result)
    }

    private fun applyShorthand(text: String): String {
        return SHORTHAND_PATTERN.replace(text) { match ->
            val word = match.value.lowercase()
            when {
                word == "xq" || word == "pq" || word == "porq" -> "porque"
                word == "q" || word == "ke" -> "que"
                word == "tmb" || word == "tb" -> "tambien"
                word == "gr" || word == "g" || word == "grs" || word == "gramos" || word.startsWith("gramit") || word.startsWith("gramin") -> "g"
                word == "kls" || word == "kgs" || word.startsWith("kilit") -> "kg"
                word == "ml" || word == "mlts" || word.startsWith("mililitr") -> "ml"
                // B7: "cucharadita" es 5g, NO "cucharada" (15g). "cdita" y "cda" son abreviaturas distintas.
                word == "cdita" || word == "cucharadita" || word.startsWith("cucharadita") -> "cucharadita"
                word == "cda" || word == "cucharada" || word.startsWith("cda") -> "cucharada"
                else -> word
            }
        }
    }

    private fun applyTypos(text: String): String {
        var result = text
        for ((regex, correction) in TYPO_REGEX_LIST) {
            result = result.replace(regex, correction)
        }
        // Typos flexionados: "uebos" no matchea \buebo\b, así que se intenta con el
        // lema sin sufijo plural ("uebo" → "huevo" → "huevos").
        result = PLURAL_WORD_PATTERN.replace(result) { match ->
            val word = match.value.lowercase()
            val stem = if (word.endsWith("es")) word.dropLast(2) else word.dropLast(1)
            val corrected = TYPO_MAP[stem] ?: return@replace match.value
            corrected + if (word.endsWith("es")) "es" else "s"
        }
        return result
    }

    private fun applyEnglishMapping(text: String): String {
        var result = text
        for ((regex, correction) in EN_ES_REGEX_LIST) {
            result = result.replace(regex, correction)
        }
        return result
    }

    private fun expandFractions(text: String): String {
        return FRACTION_PATTERN.replace(text) { match ->
            when (match.value.lowercase().trim()) {
                "medio kilo" -> "500 g"
                "cuarto kilo" -> "250 g"
                "un kilo y medio" -> "1500 g"
                else -> match.value
            }
        }
    }

    private fun convertNumberWords(text: String): String {
        var result = text
        val words = text.lowercase().split(SPACES_PATTERN)
        for ((word, regex, numStr) in NUMBER_WORD_REGEX_LIST) {
            if (word in words) {
                result = result.replace(regex, numStr)
            }
        }
        return result
    }
}
