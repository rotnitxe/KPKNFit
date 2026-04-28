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
        """\b(creo\s+que\s+(?:fue|era)|me\s+parece|m[aá]s\s+o\s+menos|como\s+unos|aprox(?:imadamente)?|tipo)\s*""",
        RegexOption.IGNORE_CASE
    )

    // ─── Repeated letters from voice/chat noise ───────────────────────────
    private val REPEATED_VOWELS = Regex("""([aeiouáéíóúü])\1+""", RegexOption.IGNORE_CASE)
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
        // Culinary jargon
        "al dente" to "al dente",
        "golden" to "dorado", "crispy" to "crocante", "juicy" to "jugoso",
        "overcooked" to "pasado", "undercooked" to "poco cocido",
        "well done" to "bien cocido", "medium" to "termino medio",
        "rare" to "poco cocido", "medium rare" to "medio crudo",
    )

    // ─── Shorthand / chat abbreviations ───────────────────────────────────
    private val SHORTHAND_PATTERN = Regex(
        """\b(xq|pq|porq|q|ke|tmb|tb|grs?|gramit[oa]s|gramines?|gramos?|gr|kilit[oa]s|kls|kgs|mililitr[oa]s|mlts|cdas?|cdita|cucharadita)\b""",
        RegexOption.IGNORE_CASE
    )

    // ─── Number words → digits ────────────────────────────────────────────
    private val NUMBER_WORDS = mapOf(
        "un" to 1, "uno" to 1, "una" to 1,
        "dos" to 2, "tres" to 3, "cuatro" to 4, "cinco" to 5,
        "seis" to 6, "siete" to 7, "ocho" to 8, "nueve" to 9, "diez" to 10,
        "once" to 11, "doce" to 12, "trece" to 13, "catorce" to 14, "quince" to 15,
        "dieciseis" to 16, "diecisiete" to 17, "dieciocho" to 18, "diecinueve" to 19,
        "veinte" to 20, "treinta" to 30, "cuarenta" to 40, "cincuenta" to 50,
        "sesenta" to 60, "setenta" to 70, "ochenta" to 80, "noventa" to 90,
        "cien" to 100, "ciento" to 100, "doscientos" to 200, "trescientos" to 300,
        "cuatrocientos" to 400, "quinientos" to 500,
        "seiscientos" to 600, "setecientos" to 700, "ochocientos" to 800, "novecientos" to 900,
        "mil" to 1000,
        "medio" to 1, "media" to 1, // handled as 0.5 in FoodParser
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

        // 3. Collapse repeated vowels (polloooo → pollo)
        text = REPEATED_VOWELS.replace(text) { it.groupValues[1] }

        // 4. Strip voice fillers
        text = FILLER_PATTERN.replace(text, "")

        // 5. Strip quantity hedges (keep the number that follows)
        text = HEDGE_PATTERN.replace(text, "")

        // 6. Apply shorthand replacements
        text = applyShorthand(text)

        // 7. Apply common typos
        text = applyTypos(text)
        text = REPEATED_LETTERS_3_PLUS.replace(text) { it.groupValues[1] }

        // 8. Map English food words to Spanish
        text = applyEnglishMapping(text)

        // 9. Expand fractional patterns
        text = expandFractions(text)

        // 10. Convert number words to digits
        text = convertNumberWords(text)

        // 11. Final cleanup: collapse multiple spaces
        text = text.replace(Regex("\\s{2,}"), " ").trim()

        return text
    }

    /**
     * Normalize a food name specifically (additional to general normalization).
     * Handles diminutivos and aumentativos.
     */
    fun normalizeFoodName(name: String): String {
        var normalized = name.trim().lowercase()
            .replace(Regex("\\s+"), " ")

        // Diminutivos: -ito/-ita/-illo/-illa/-cito/-cita/-ecito/-ecita
        // Handle vowel elision: "pollito" → "pollo", "panecito" → "pan"
        val diminutiveSuffixes = listOf("ecito", "ecita", "cito", "cita", "illo", "illa", "ito", "ita")
        for (suffix in diminutiveSuffixes) {
            if (normalized.endsWith(suffix) && normalized.length > suffix.length + 2) {
                val root = normalized.dropLast(suffix.length)
                if (root.length >= 3) {
                    val candidates = listOf(root, root + "o", root + "a", root + "e")
                    normalized = candidates.firstOrNull { it in COMMON_FOOD_ROOTS } ?: root
                    break
                }
            }
        }

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

    // ─── Internal ──────────────────────────────────────────────────────────

    private fun replaceEmojis(text: String): String {
        var result = text
        for ((emoji, word) in EMOJI_MAP) {
            result = result.replace(emoji, " $word ")
        }
        // Strip remaining emojis
        result = result.replace(Regex("[\\p{So}\\p{Cn}]"), "")
        return result
    }

    private fun applyShorthand(text: String): String {
        return SHORTHAND_PATTERN.replace(text) { match ->
            val word = match.value.lowercase()
            when {
                word.matches(Regex("xq|pq|porq")) -> "porque"
                word == "q" || word == "ke" -> "que"
                word.matches(Regex("tmb|tb")) -> "tambien"
                word.matches(Regex("grs?|gramit[oa]s|gramines?|gramos?|gr")) -> "g"
                word.matches(Regex("kilit[oa]s|kls|kgs")) -> "kg"
                word.matches(Regex("mililitr[oa]s|mlts")) -> "ml"
                word.matches(Regex("cdas?|cdita|cucharadita")) -> "cucharada"
                else -> word
            }
        }
    }

    private fun applyTypos(text: String): String {
        var result = text
        for ((typo, correction) in TYPO_MAP) {
            // Only replace whole words
            result = result.replace(Regex("""\b$typo\b""", RegexOption.IGNORE_CASE), correction)
        }
        return result
    }

    private fun applyEnglishMapping(text: String): String {
        var result = text
        for ((en, es) in EN_ES_MAP) {
            result = result.replace(Regex("""\b$en\b""", RegexOption.IGNORE_CASE), es)
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
        val words = text.lowercase().split(Regex("\\s+"))
        for ((word, num) in NUMBER_WORDS) {
            if (word in words) {
                result = result.replace(Regex("""\b$word\b""", RegexOption.IGNORE_CASE), num.toString())
            }
        }
        return result
    }
}
