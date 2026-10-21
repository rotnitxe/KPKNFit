package com.example.kpkn.domain.exercises

import kotlin.math.abs

/**
 * Lectura de un nombre escrito a mano para «Crear ejercicio» (MOT · E1, AUD1 §3.3). Separa lo que dice QUÉ ejercicio es
 * (identidad) de la variante que pide (material, estación, lateralidad, agarre), corrige erratas contra el vocabulario del
 * catálogo y reconoce alias de identidad («RDL», «facepull», «chin up»). Es puro Kotlin y comparte el léxico de la búsqueda
 * ([ExerciseMatchLexicon]): el mismo normalizado, plurales y sinónimos ES/EN.
 */
data class ParsedExerciseName(
    val raw: String,
    /** Palabras de identidad tras corregir erratas, consumir alias y quitar la variante y el relleno. */
    val coreTokens: List<String>,
    /** Variante pedida: eje v2 (`implement`, `station`, `laterality`, `grip_type`, `grip_width`) → valor. */
    val restrictions: Map<String, String>,
    /** Definiciones a las que apuntan los alias reconocidos, en orden de preferencia. */
    val aliasDefinitionIds: List<String>,
    /** Palabras de variante que se quitaron de la identidad (material, agarre…). */
    val strippedWords: Set<String>,
    /** Mayor distancia de edición de una errata corregida; 0 si no hubo. Con 2 o más no se certifica la referencia. */
    val maxTypoDistance: Int,
    /** Correcciones aplicadas: escrito → corregido. */
    val corrections: List<Pair<String, String>>,
) {
    val implement: String? get() = restrictions["implement"]
}

/** Léxico de variante y alias de identidad. Los alias se validan contra el catálogo en las pruebas. */
internal object NameLexicon {
    val STOP: Set<String> = setOf(
        "de", "del", "la", "el", "en", "con", "para", "por", "y", "a", "al", "un", "una", "los", "las",
        "ejercicio", "ejercicios", "variante", "estilo", "mi", "me",
    )

    private val IMPLEMENT: List<Pair<String, String>> = listOf(
        "barra ez" to "ez_bar", "barra z" to "ez_bar", "ez bar" to "ez_bar", "barra curl" to "ez_bar",
        "barra hexagonal" to "hex_bar", "barra hex" to "hex_bar", "trap bar" to "hex_bar", "hex bar" to "hex_bar",
        "barra t" to "t_bar", "t bar" to "t_bar",
        "maquina smith" to "smith_machine", "smith machine" to "smith_machine", "smith" to "smith_machine",
        "barra de seguridad" to "safety_bar", "safety bar" to "safety_bar",
        "peso corporal" to "bodyweight", "bodyweight" to "bodyweight", "body weight" to "bodyweight",
        "pesa rusa" to "kettlebell", "kettlebell" to "kettlebell",
        "mancuernas" to "dumbbells", "mancuerna" to "dumbbells", "dumbbells" to "dumbbells", "dumbbell" to "dumbbells",
        "polea" to "cable", "poleas" to "cable", "cable" to "cable", "cables" to "cable", "cuerda" to "cable",
        "maquina" to "machine", "machine" to "machine",
        "banda" to "band", "band" to "band", "liga" to "band",
        "disco" to "plate", "plate" to "plate",
        "barra" to "barbell", "barbell" to "barbell",
    )
    private val STATION: List<Pair<String, String>> = listOf(
        "de pie" to "standing", "standing" to "standing", "parado" to "standing",
        "sentado" to "seated", "sentada" to "seated", "seated" to "seated",
    )
    private val LATERALITY: List<Pair<String, String>> = listOf(
        "a una mano" to "unilateral", "una mano" to "unilateral", "un brazo" to "unilateral", "una pierna" to "unilateral",
        "single arm" to "unilateral", "single leg" to "unilateral", "unilateral" to "unilateral", "bilateral" to "bilateral",
    )
    private val GRIP_TYPE: List<Pair<String, String>> = listOf(
        "supinado" to "supinated", "supinada" to "supinated", "supinas" to "supinated", "supino" to "supinated",
        "supina" to "supinated", "supinated" to "supinated", "underhand" to "supinated",
        "pronado" to "pronated", "pronada" to "pronated", "pronas" to "pronated", "prono" to "pronated",
        "prona" to "pronated", "pronated" to "pronated", "overhand" to "pronated",
        "neutro" to "neutral", "neutra" to "neutral", "neutral" to "neutral",
    )
    private val GRIP_WIDTH: List<Pair<String, String>> = listOf(
        "amplio" to "wide", "ancho" to "wide", "wide" to "wide",
        "cerrado" to "close", "estrecho" to "close", "close" to "close", "narrow" to "close",
    )

    val AXES: List<Pair<String, List<Pair<String, String>>>> = listOf(
        "implement" to IMPLEMENT,
        "station" to STATION,
        "laterality" to LATERALITY,
        "grip_type" to GRIP_TYPE,
        "grip_width" to GRIP_WIDTH,
    )

    /** La estación (sentado / de pie) define la identidad de varios nombres del catálogo: se lee como restricción, pero la palabra se conserva. */
    val KEEP_WORD_AXES: Set<String> = setOf("station")

    val IMPLEMENT_PHRASES: List<Pair<String, String>> get() = IMPLEMENT

    data class Alias(val phrase: String, val definitionIds: List<String>, val implied: Map<String, String> = emptyMap())

    /** Alias de identidad: nombres de uso común que el catálogo no recoge como término de búsqueda. */
    val ALIASES: List<Alias> = listOf(
        Alias("rdl", listOf("romanian_deadlift")),
        Alias("stiff leg", listOf("stiff_leg_deadlift")),
        Alias("stiff", listOf("stiff_leg_deadlift", "romanian_deadlift")),
        Alias("facepull", listOf("deltoides_face_pull")),
        Alias("face pull", listOf("deltoides_face_pull")),
        Alias("chin up", listOf("pull_up"), mapOf("grip_type" to "supinated")),
        Alias("chin ups", listOf("pull_up"), mapOf("grip_type" to "supinated")),
        Alias("chinup", listOf("pull_up"), mapOf("grip_type" to "supinated")),
        Alias("pull up", listOf("pull_up")),
        Alias("pull ups", listOf("pull_up")),
        Alias("pullup", listOf("pull_up")),
        Alias("vuelo lateral", listOf("standing_lateral_raise", "seated_lateral_raise")),
        Alias("vuelos laterales", listOf("standing_lateral_raise", "seated_lateral_raise")),
        Alias("pajaros", listOf("rear_delt_raise", "reverse_pec_fly")),
        Alias("pajaro", listOf("rear_delt_raise", "reverse_pec_fly")),
        Alias("rompecraneos", listOf("triceps_press_frances", "skullcrusher")),
        Alias("skull crusher", listOf("triceps_press_frances", "skullcrusher")),
        Alias("skullcrushers", listOf("triceps_press_frances", "skullcrusher")),
        Alias("skullcrusher", listOf("triceps_press_frances", "skullcrusher")),
        Alias("goblet", listOf("quads_sentadilla_copa")),
        Alias("ab wheel", listOf("core_rueda_abdominal")),
        Alias("rollout", listOf("core_rueda_abdominal")),
        Alias("pallof", listOf("core_press_pallof")),
        Alias("push up", listOf("push_up")),
        Alias("pushup", listOf("push_up")),
        Alias("push ups", listOf("push_up")),
        Alias("swing", listOf("hams_swing_kettlebell_dos_manos", "hams_swing_kettlebell_unilateral")),
        Alias("trap bar deadlift", listOf("conventional_deadlift"), mapOf("implement" to "hex_bar")),
        Alias("flexion", listOf("push_up")),
        Alias("flexiones", listOf("push_up")),
    )

    /** Palabras que describen la variante y no la identidad del movimiento. */
    val RESTRICTION_STEMS: Set<String> by lazy {
        val stems = mutableSetOf<String>()
        // En los NOMBRES del catálogo solo se descuenta el material: el agarre, el ancho y la lateralidad forman parte de la identidad
        // («curl prono» es otro ejercicio que «curl»). Los de la consulta sí se leen como restricción.
        AXES.filter { (axis, _) -> axis == "implement" }.forEach { (_, phrases) ->
            phrases.forEach { (phrase, _) ->
                if (!phrase.contains(' ')) stems += ExerciseMatchLexicon.stem(phrase)
            }
        }
        listOf("ez", "hex", "trap", "smith", "safety", "pesa", "rusa", "corporal").forEach { stems += ExerciseMatchLexicon.stem(it) }
        stems -= STOP
        stems
    }
}

/** Vocabulario del catálogo (raíces y frecuencia) para corregir erratas. */
class ExerciseNameVocabulary(private val frequency: Map<String, Int>) {
    private val sorted: List<String> = frequency.keys.sorted()

    fun contains(stem: String): Boolean = stem in frequency

    /** La corrección más cercana a [stem] (distancia ≤ 1; ≤ 2 si es larga y comparte las tres primeras letras) o null. */
    fun nearest(stem: String): Pair<String, Int>? {
        var best: String? = null
        var bestDistance = Int.MAX_VALUE
        for (candidate in sorted) {
            if (abs(candidate.length - stem.length) > 2) continue
            val distance = ExerciseMatchLexicon.levenshtein(stem, candidate)
            val limit = if (stem.length >= 7 && candidate.take(3) == stem.take(3)) 2 else 1
            if (distance > limit) continue
            val better = distance < bestDistance ||
                (distance == bestDistance && (frequency[candidate] ?: 0) > (best?.let { frequency[it] } ?: 0))
            if (better) {
                best = candidate
                bestDistance = distance
            }
        }
        return best?.let { it to bestDistance }
    }

    companion object {
        fun of(phrases: Collection<String>): ExerciseNameVocabulary {
            val counts = mutableMapOf<String, Int>()
            fun add(text: String) {
                ExerciseMatchLexicon.normalize(text).split(' ').filter { it.isNotBlank() }.forEach { token ->
                    val stem = ExerciseMatchLexicon.stem(token)
                    counts[stem] = (counts[stem] ?: 0) + 1
                }
            }
            phrases.forEach(::add)
            NameLexicon.ALIASES.forEach { add(it.phrase) }
            NameLexicon.AXES.forEach { (_, list) -> list.forEach { (phrase, _) -> add(phrase) } }
            return ExerciseNameVocabulary(counts)
        }
    }
}

object ExerciseNameAnalyzer {
    /**
     * Lee [raw]. [aliasTargetWords] da, para una definición, las palabras clave de su nombre: tras reconocer un alias se quitan de
     * la identidad restante («pallof press» → la palabra «press» ya la explica el ejercicio Press Pallof).
     */
    fun parse(
        raw: String,
        vocabulary: ExerciseNameVocabulary,
        aliasTargetWords: (String) -> Set<String> = { emptySet() },
    ): ParsedExerciseName {
        var tokens = ExerciseMatchLexicon.normalize(raw).split(' ').filter { it.isNotBlank() }
        var maxTypo = 0
        val corrections = mutableListOf<Pair<String, String>>()
        tokens = tokens.map { token ->
            val stem = ExerciseMatchLexicon.stem(token)
            if (vocabulary.contains(stem) || stem.length < 4 || stem.all { it.isDigit() }) return@map token
            val fix = vocabulary.nearest(stem) ?: return@map token
            maxTypo = maxOf(maxTypo, fix.second)
            corrections += token to fix.first
            fix.first
        }

        val restrictions = linkedMapOf<String, String>()
        val aliasIds = mutableListOf<String>()
        for (alias in NameLexicon.ALIASES.sortedByDescending { it.phrase.split(' ').size }) {
            val phrase = alias.phrase.split(' ')
            val at = indexOfPhrase(tokens, phrase)
            if (at < 0) continue
            alias.definitionIds.forEach { if (it !in aliasIds) aliasIds += it }
            restrictions.putAll(alias.implied)
            tokens = tokens.subList(0, at) + tokens.subList(at + phrase.size, tokens.size)
        }

        val stripped = mutableSetOf<String>()
        for ((axis, phrases) in NameLexicon.AXES) {
            while (true) {
                val hit = extractPhrase(tokens, phrases) ?: break
                if (axis !in restrictions) restrictions[axis] = hit.value
                if (axis in NameLexicon.KEEP_WORD_AXES) break
                stripped += hit.removed.map { ExerciseMatchLexicon.stem(it) }
                tokens = hit.remaining
            }
        }

        var core = tokens.filter { it !in NameLexicon.STOP }
        if (aliasIds.isNotEmpty()) {
            val targetWords = aliasIds.flatMap { aliasTargetWords(it) }.toSet()
            core = core.filter { ExerciseMatchLexicon.synonymKey(it) !in targetWords }
        }
        return ParsedExerciseName(
            raw = raw,
            coreTokens = core,
            restrictions = restrictions,
            aliasDefinitionIds = aliasIds,
            strippedWords = stripped,
            maxTypoDistance = maxTypo,
            corrections = corrections,
        )
    }

    internal data class PhraseHit(val value: String, val remaining: List<String>, val removed: List<String>)

    /** Busca, de la frase más larga a la más corta, una frase consecutiva de [phrases] dentro de [tokens]. */
    internal fun extractPhrase(tokens: List<String>, phrases: List<Pair<String, String>>): PhraseHit? {
        for ((phrase, value) in phrases.sortedByDescending { it.first.split(' ').size }) {
            val words = phrase.split(' ')
            val at = indexOfPhrase(tokens, words)
            if (at >= 0) {
                return PhraseHit(
                    value = value,
                    remaining = tokens.subList(0, at) + tokens.subList(at + words.size, tokens.size),
                    removed = tokens.subList(at, at + words.size),
                )
            }
        }
        return null
    }

    private fun indexOfPhrase(tokens: List<String>, words: List<String>): Int {
        val n = words.size
        if (n == 0 || tokens.size < n) return -1
        val wantedStems = words.map { ExerciseMatchLexicon.stem(it) }
        for (i in 0..(tokens.size - n)) {
            if ((0 until n).all { k -> ExerciseMatchLexicon.stem(tokens[i + k]) == wantedStems[k] }) return i
        }
        return -1
    }
}
