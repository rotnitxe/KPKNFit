package com.example.kpkn.domain.training.importer

import com.example.kpkn.domain.exercises.ExerciseMatchLexicon
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2

/**
 * Normalización de los nombres de las hojas para la segunda pasada del emparejador (propuesta v6, H6b): quita la
 * notación (cargas, series, códigos de bloque), expande abreviaturas y compuestos, marca cabeceras y prosa, y da el
 * stem de plural de las claves del importador. Solo actúa sobre los nombres que la primera pasada no reconoce; el
 * léxico compartido no cambia.
 */
internal object ImportNameNormalizer {

    /** Nombre limpio ([text], vacío si no queda nada) y si es una cabecera o prosa que no es un ejercicio ([isNoise]). */
    class Prepared(val text: String, val isNoise: Boolean)

    /**
     * Palabras que no se comprueban al decidir si el candidato dice todo el nombre (G1 y G3): relleno inglés y
     * notación («up», «set», «pause», «bar»…).
     */
    val NEUTRAL_WORDS: Set<String> = setOf(
        "up", "to", "with", "the", "of", "on", "a", "an", "and", "for", "in", "at", "from", "your", "my", "each",
        "set", "sets", "reps", "rep", "x", "w", "or", "v", "bar", "work", "day", "days", "pause", "pausa", "sit",
        "lateral", "side", "rear", "front", "bent", "over", "one", "arm",
    )

    /** Restos de notación de las hojas (códigos de bloque, restos de celda, ordinales) que se quitan. */
    private val DROP_WORDS = setOf(
        "ref", "value", "main", "pd", "pds", "pps", "pba", "c", "x", "at", "w", "each", "log", "or", "comp",
        "competition", "speed", "heavier", "heavy", "light", "accessory", "acc", "st", "nd", "rd", "th", "ss", "d", "of",
    )

    /** Palabras de cabecera o de prosa: si aparecen, el nombre no es un ejercicio. */
    private val NOISE_WORDS = setOf(
        "used", "none", "tier", "warmup", "warmups", "warm", "day", "days", "week", "sett", "rpe", "emom", "amrap",
        "reserve", "work", "gauging", "repeat", "assistance", "choose", "stronglifts", "madcow", "nsuns", "gzclp",
        "phul", "phat", "candito", "smolov", "set", "sets", "superset",
    )

    private val WEEKDAYS = setOf("fri", "mon", "wed", "tue", "thu", "sat", "sun")

    /** Abreviaturas y compuestos → palabras del catálogo («cg» → agarre cerrado, «pushups» → push up). */
    private val TOKEN_MAP: Map<String, List<String>> = mapOf(
        "cg" to listOf("close", "grip"),
        "wg" to listOf("wide", "grip"),
        "dbs" to listOf("db"),
        "bbs" to listOf("bb"),
        "kbs" to listOf("kb"),
        "pushups" to listOf("push", "up"),
        "pushup" to listOf("push", "up"),
        "pullups" to listOf("pull", "up"),
        "pullup" to listOf("pull", "up"),
        "chinups" to listOf("chin", "up"),
        "chinup" to listOf("chin", "up"),
    )

    /** Notación pegada o separada, en orden: pausas («2ct» → pausa), cargas, códigos («ACC1», «SS2», «A1:») y cifras. */
    private val NOTATION_RULES: List<Pair<Regex, String>> = listOf(
        Regex("""\b(\d+)\s*ct\b""") to " pause ",
        Regex("""\b\d+\s*rm\b""") to " ",
        Regex("""\b(acc|a|b)\d\b""") to " ",
        Regex("""\bss\d\b""") to " ",
        Regex("""\b\d+(st|nd|rd|th)\b""") to " ",
        Regex("""\bx\d+\b""") to " ",
        Regex("""\bt\d\b""") to " ",
        Regex("""\b\d+\b""") to " ",
        Regex("""\bpush ?ups?\b""") to "push up",
        Regex("""\bpull ?ups?\b""") to "pull up",
        Regex("""\bchin ?ups?\b""") to "chin up",
    )

    /** Mismas excepciones de stem que el léxico compartido: estas palabras no pierden la «s». */
    private val STEM_EXCEPTIONS = setOf("press", "biceps", "triceps", "abs", "barbell", "dumbbell", "glutes")

    private val IRREGULAR_PLURALS = mapOf("flyes" to "fly", "flys" to "fly", "flies" to "fly", "calves" to "calf")

    /** Valores de categoría del catálogo y su texto en español, para el vocabulario conocido. */
    private val CATEGORY_WORDS: Map<String, String> = mapOf(
        "barbell" to "barra barra libre barra recta",
        "band" to "banda banda elastica",
        "cable" to "polea cable",
        "dumbbells" to "mancuerna mancuernas",
        "machine" to "maquina máquina",
        "bodyweight" to "peso corporal",
        "plate" to "disco",
        "safety_bar" to "barra de seguridad safety bar",
        "smith_machine" to "smith maquina máquina",
        "hex_bar" to "barra hexagonal trap bar hex bar",
        "kettlebell" to "kettlebell pesa rusa",
        "standing" to "de pie parado",
        "seated" to "sentado",
        "pec_deck" to "pec deck",
        "pec_deck_reverse" to "pec deck inverso reverse pec fly",
        "supinated" to "supino",
        "pronated" to "prono",
        "neutral" to "neutro",
        "low" to "baja bajo",
        "mid" to "media medio",
        "high" to "alta alto",
        "flat" to "plano plana",
        "incline" to "inclinado inclinada",
        "decline" to "declinado declinada",
        "wide" to "amplio ancha ancho",
        "close" to "cerrado estrecha estrecho",
    )

    /**
     * Limpia la notación de un nombre y marca las cabeceras y la prosa («1 Fri Heavy», «3 day (Wednesday)», «used:
     * none»…). Un nombre sin palabras queda como cabecera.
     */
    fun prepare(raw: String): Prepared {
        var text = ExerciseMatchLexicon.normalize(raw)
        for ((regex, replacement) in NOTATION_RULES) text = regex.replace(text, replacement)
        val words = ArrayList<String>()
        for (token in text.split(' ').filter { it.isNotEmpty() }) {
            if (token in DROP_WORDS) continue
            for (word in TOKEN_MAP[token] ?: listOf(token)) {
                if (words.isEmpty() || words.last() != word) words += word
            }
        }
        if (words.isEmpty()) return Prepared("", isNoise = true)
        val noise = words.any { it in NOISE_WORDS } || words.all { it in WEEKDAYS } || words.size >= 9
        return Prepared(words.joinToString(" "), isNoise = noise)
    }

    /**
     * Stem de plural de las claves del importador: «raises» → «raise», «lunges» → «lunge», «rows» → «row», «flyes» →
     * «fly». Las reglas del español (elevaciones, abductores) se mantienen.
     */
    fun pluralStem(token: String): String {
        val t = ExerciseMatchLexicon.normalize(token)
        if (t.length <= 3 || t in STEM_EXCEPTIONS) return t
        IRREGULAR_PLURALS[t]?.let { return it }
        return when {
            t.endsWith("ises") || t.endsWith("ges") -> t.dropLast(1)
            t.length == 4 && t.endsWith("s") && !t.endsWith("ss") -> t.dropLast(1)
            t.endsWith("es") && t.length > 5 -> t.dropLast(2)
            t.endsWith("s") -> t.dropLast(1)
            else -> t
        }
    }

    /** Stem de la comprobación de vocabulario (G1): quita «es» y «s» solo en los plurales que el catálogo usa. */
    fun vocabularyStem(token: String): String {
        val t = ExerciseMatchLexicon.normalize(token)
        if (t.length <= 3 || t in STEM_EXCEPTIONS) return t
        IRREGULAR_PLURALS[t]?.let { return it }
        return when {
            t.endsWith("sses") || t.endsWith("ches") || t.endsWith("shes") || t.endsWith("xes") -> t.dropLast(2)
            t.endsWith("s") && !(t.endsWith("ss") || t.endsWith("us") || t.endsWith("is")) -> t.dropLast(1)
            else -> t
        }
    }

    /**
     * Palabras que el catálogo y el importador conocen: nombres, búsquedas, configuraciones, materiales, traducciones
     * y frases de alias ([aliasPhrases]). Un nombre con una palabra fuera de este conjunto no se reconoce solo (G1).
     */
    fun vocabulary(
        catalog: ExerciseCatalogV2,
        translations: Map<String, String>,
        aliasPhrases: List<String>,
    ): Set<String> {
        val words = HashSet<String>()
        fun add(text: String) {
            for (word in ExerciseMatchLexicon.normalize(text).split(' ').filter { it.isNotEmpty() }) {
                words += word
                words += vocabularyStem(word)
                words += ExerciseMatchLexicon.stem(word)
            }
        }
        for (family in catalog.families) {
            for (definition in family.definitions) {
                add(definition.canonicalName)
                definition.searchTerms.forEach(::add)
                for (configuration in definition.configurations) {
                    add(configuration.displaySummary)
                    for (value in configuration.selectedOptions.values) {
                        add(value)
                        add(CATEGORY_WORDS[value.lowercase()] ?: value)
                    }
                    add(configuration.profile.equipmentId)
                }
            }
        }
        for ((english, spanish) in translations) {
            add(english)
            add(spanish)
        }
        MATERIAL_WORDS.keys.forEach(::add)
        (IMPORT_STOP_WORDS + NEUTRAL_WORDS).forEach(::add)
        aliasPhrases.forEach(::add)
        return words
    }
}
