package com.example.kpkn.domain.exercises

import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.domain.exercises.catalogv2.ExerciseDefinitionV2

/**
 * Léxico compartido para detección de coincidencias de ejercicios. Normaliza
 * acentos, aplica singular/plural ligero, expande sinónimos ES/EN, detecta
 * frases conocidas y músculos mencionados, y da tolerancia a typos.
 *
 * Es puro Kotlin para poder testearse sin Android.
 */
object ExerciseMatchLexicon {

    fun normalize(value: String): String =
        value.lowercase()
            .replace("á", "a")
            .replace("é", "e")
            .replace("í", "i")
            .replace("ó", "o")
            .replace("ú", "u")
            .replace("ü", "u")
            .replace("ñ", "n")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    private val STEM_EXCEPTIONS = setOf(
        "press", "biceps", "triceps", "abs", "barbell", "dumbbell", "glutes",
    )

    /** Singular/plural ligero: "aperturas" -> "apertura", "planas" -> "plana". */
    fun stem(token: String): String {
        val t = normalize(token)
        if (t.length <= 4 || t in STEM_EXCEPTIONS) return t
        return when {
            t.endsWith("es") && t.length > 5 -> t.dropLast(2)
            t.endsWith("s") -> t.dropLast(1)
            else -> t
        }
    }

    fun stemTokens(text: String): Set<String> =
        normalize(text).split(" ").filter { it.isNotBlank() }.map(::stem).toSet()

    private val SYNONYM_CANONICAL = mapOf(
        "press" to "push",
        "empuje" to "push",
        "push" to "push",
        "sentadilla" to "squat",
        "squat" to "squat",
        "zancada" to "lunge",
        "lunge" to "lunge",
        "muerto" to "deadlift",
        "deadlift" to "deadlift",
        "rdl" to "deadlift",
        "remo" to "row",
        "row" to "row",
        "remada" to "row",
        "dominada" to "pullup",
        "dominadas" to "pullup",
        "pullup" to "pullup",
        "chinup" to "pullup",
        "jalon" to "pulldown",
        "pulldown" to "pulldown",
        "jalones" to "pulldown",
        "elevacion" to "raise",
        "raise" to "raise",
        "apertura" to "fly",
        "fly" to "fly",
        "banca" to "bench",
        "bench" to "bench",
        "mancuerna" to "dumbbell",
        "dumbbell" to "dumbbell",
        "polea" to "cable",
        "cable" to "cable",
        "maquina" to "machine",
        "machine" to "machine",
        "barra" to "barbell",
        "barbell" to "barbell",
        "sentado" to "seated",
        "seated" to "seated",
        "inclinado" to "incline",
        "incline" to "incline",
        "declinado" to "decline",
        "decline" to "decline",
        "triceps" to "triceps",
        "tricep" to "triceps",
        "biceps" to "biceps",
        "bicep" to "biceps",
        "pectoral" to "chest",
        "pectorales" to "chest",
        "pecho" to "chest",
        "chest" to "chest",
        "dorsal" to "back",
        "dorsales" to "back",
        "espalda" to "back",
        "back" to "back",
        "lat" to "back",
        "hombro" to "shoulder",
        "hombros" to "shoulder",
        "shoulder" to "shoulder",
        "gluteo" to "glutes",
        "gluteos" to "glutes",
        "glute" to "glutes",
        "glutes" to "glutes",
        "cuadriceps" to "quads",
        "quad" to "quads",
        "quads" to "quads",
        "isquiosurales" to "hamstrings",
        "isquio" to "hamstrings",
        "isquios" to "hamstrings",
        "hamstring" to "hamstrings",
        "hamstrings" to "hamstrings",
        "femoral" to "hamstrings",
        "pantorrilla" to "calves",
        "pantorrillas" to "calves",
        "calf" to "calves",
        "calves" to "calves",
        "abdominal" to "abs",
        "abdominales" to "abs",
        "abs" to "abs",
        "antebrazo" to "forearm",
        "forearm" to "forearm",
        "trapecio" to "traps",
        "trapezius" to "traps",
        "erectores" to "spine",
        "erector" to "spine",
        "core" to "core",
    )

    fun synonymKey(token: String): String =
        SYNONYM_CANONICAL[stem(token)] ?: stem(token)

    fun tokenKeys(text: String): Set<String> =
        normalize(text).split(" ").filter { it.isNotBlank() }.map(::synonymKey).toSet()

    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val prev = IntArray(b.length + 1) { it }
        val curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            prev.indices.forEach { prev[it] = curr[it] }
        }
        return curr[b.length]
    }

    /** Tolerancia a typos en tokens largos (>=5 chars). */
    fun fuzzyMatch(queryToken: String, candidateToken: String): Boolean {
        val q = stem(queryToken)
        val c = stem(candidateToken)
        if (q == c) return true
        if (q.length < 5 || c.length < 5) return false
        val maxDistance = if (q.length >= 8 || c.length >= 8) 2 else 1
        return levenshtein(q, c) <= maxDistance
    }

    /** Similaridad 0..1 entre una consulta y un texto candidato. */
    fun tokenSimilarity(query: String, candidate: String): Double {
        val qKeys = tokenKeys(query)
        val cKeys = tokenKeys(candidate)
        if (qKeys.isEmpty() || cKeys.isEmpty()) return 0.0

        val intersection = qKeys.intersect(cKeys)
        val jaccard = intersection.size.toDouble() /
            (qKeys.size + cKeys.size - intersection.size)
        val overlap = intersection.size.toDouble() / qKeys.size
        var sim = jaccard * 0.6 + overlap * 0.4

        val qRaw = stemTokens(query)
        val cRaw = stemTokens(candidate)
        val unmatchedRaw = qRaw.count { raw ->
            cRaw.none { fuzzyMatch(raw, it) }
        }
        val fuzzyHits = qRaw.size - unmatchedRaw
        if (fuzzyHits > 0 && intersection.size < qKeys.size) {
            sim += 0.15 * (fuzzyHits.toDouble() / qRaw.size)
        }
        return sim.coerceIn(0.0, 1.0)
    }

    private val KNOWN_PHRASES = listOf(
        "peso muerto",
        "peso muerto rumano",
        "press banca",
        "press militar",
        "press inclinado",
        "press declinado",
        "elevacion lateral",
        "aperturas planas",
        "aperturas inclinadas",
        "aperturas declinadas",
        "apertura de pecho",
        "face pull",
        "hip thrust",
        "prensa de pierna",
        "leg press",
        "step up",
        "sentadilla bulgara",
        "curl bayesiano",
        "curl martillo",
        "extension de triceps",
        "elevacion de piernas",
    )

    fun containsKnownPhrase(query: String, candidateText: String): Boolean {
        val nq = normalize(query)
        val nc = normalize(candidateText)
        return KNOWN_PHRASES.any { phrase -> nq.contains(phrase) && nc.contains(phrase) }
    }

    private val MUSCLE_GROUP_ALIASES = mapOf(
        "chest" to setOf("Pectorales"),
        "back" to setOf("Dorsales", "Trapecio", "Romboides"),
        "shoulder" to setOf("Deltoides"),
        "triceps" to setOf("Tríceps"),
        "biceps" to setOf("Bíceps"),
        "quads" to setOf("Cuádriceps"),
        "hamstrings" to setOf("Isquiosurales"),
        "glutes" to setOf("Glúteos"),
        "calves" to setOf("Pantorrillas"),
        "abs" to setOf("Abdomen"),
        "forearm" to setOf("Antebrazo"),
        "traps" to setOf("Trapecio"),
        "spine" to setOf("Erectores Espinales"),
    )

    /** Grupos musculares canónicos mencionados en la consulta ("pecho" -> Pectorales). */
    fun mentionedMuscleGroups(query: String): Set<String> =
        tokenKeys(query).flatMap { key -> MUSCLE_GROUP_ALIASES[key].orEmpty() }.toSet()

    /**
     * Coincidencia 100%: el nombre normalizado (sin acentos/case/puntuación)
     * es exactamente igual al nombre canónico, a un searchTerm del catálogo o
     * al nombre/alias de un ejercicio personalizado. Los plurales y variantes
     * NO cuentan como coincidencia exacta.
     */
    fun hasExactMatch(
        query: String,
        definitions: Collection<ExerciseDefinitionV2> = emptyList(),
        customExercises: Collection<ExerciseMuscleInfo> = emptyList(),
    ): Boolean {
        val q = normalize(query)
        if (q.isBlank()) return false
        fun exact(vararg values: String): Boolean =
            values.any { normalize(it) == q }
        val catalogHit = definitions.any { definition ->
            exact(definition.canonicalName) ||
                definition.searchTerms.any { normalize(it) == q }
        }
        val customHit = customExercises.any { exercise ->
            exact(exercise.name) ||
                exercise.alias.orEmpty().split(',').any { normalize(it) == q }
        }
        return catalogHit || customHit
    }
}
