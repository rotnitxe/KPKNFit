package com.example.kpkn.domain.exercises

/**
 * Detects the movement pattern behind an exercise title using a curated
 * Spanish/English keyword lexicon. It is intentionally pure Kotlin so it can
 * be unit tested without Android dependencies.
 *
 * Confidence contract:
 *  - HIGH: a distinctive keyword for the pattern was found (e.g. "peso muerto", "sentadilla").
 *  - MEDIUM: only a generic keyword was found (e.g. "press", "remo").
 *  - null: no recognizable pattern; callers should offer manual muscle input.
 */
object ExercisePatternDetector {

    enum class PatternConfidence { HIGH, MEDIUM }

    data class DetectedMovementPattern(
        val patternId: String,
        val label: String,
        val confidence: PatternConfidence,
        val matchedTerms: List<String>,
    )

    private data class PatternRule(
        val patternId: String,
        val label: String,
        val strongKeywords: List<String>,
        val weakKeywords: List<String> = emptyList(),
    )

    private val RULES = listOf(
        PatternRule(
            patternId = "knee_dominant",
            label = "Sentadilla / Dominante de rodilla",
            strongKeywords = listOf(
                "sentadilla bulgara", "sentadilla búlgara", "búlgar", "bulgarian", "sentadilla",
                "squat", "zancada", "lunge", "step up", "prensa de pierna", "leg press",
                "hack squat", "prensa", "pistol squat", "sissy squat",
            ),
        ),
        PatternRule(
            patternId = "hip_hinge",
            label = "Bisagra de cadera",
            strongKeywords = listOf(
                "peso muerto", "deadlift", "rumano", "stiff leg", "buenos dias", "buenos días",
                "good morning", "hip thrust", "empuje de cadera", "bisagra", "snatch grip deadlift",
            ),
            weakKeywords = listOf("peso muerto rumano"),
        ),
        PatternRule(
            patternId = "vertical_push",
            label = "Empuje vertical",
            strongKeywords = listOf(
                "press militar", "press de hombros", "press sobre cabeza", "overhead press",
                "press arnold", "press por encima", "empuje vertical", "handstand push up",
                "pike push up", "dips rectas",
            ),
            weakKeywords = listOf("press"),
        ),
        PatternRule(
            patternId = "horizontal_push",
            label = "Empuje horizontal",
            strongKeywords = listOf(
                "press banca", "press plano", "press inclinado", "press declinado", "press de pecho",
                "chest press", "push up", "pushup", "flexion de brazos", "lagartija", "fondos",
                "dips", "flexion", "press con mancuernas", "press mancuerna",
            ),
            weakKeywords = listOf("press"),
        ),
        PatternRule(
            patternId = "horizontal_pull",
            label = "Tirón horizontal",
            strongKeywords = listOf(
                "remo", "remada", "row", "face pull", "jalon horizontal", "pull row",
                "remo pendlay", "remo inclinado", "remo mancuerna", "remo con barra",
            ),
            weakKeywords = listOf("tiron"),
        ),
        PatternRule(
            patternId = "vertical_pull",
            label = "Tirón vertical",
            strongKeywords = listOf(
                "dominada", "dominadas", "pull up", "chin up", "jalon", "pulldown",
                "pull down", "jalones", "muscle up",
            ),
            weakKeywords = listOf("jalar"),
        ),
        PatternRule(
            patternId = "elbow_flexion",
            label = "Flexión de codo",
            strongKeywords = listOf("curl", "flexion de codo", "flexión de codo", "biceps"),
        ),
        PatternRule(
            patternId = "elbow_extension",
            label = "Extensión de codo",
            strongKeywords = listOf(
                "extension de triceps", "extensión de tríceps", "triceps extension",
                "francesa", "frances", "pushdown", "kickback", "patada de triceps",
                "skull crusher", "cráneo",
            ),
        ),
        PatternRule(
            patternId = "shoulder_abduction",
            label = "Abducción de hombro",
            strongKeywords = listOf(
                "elevacion lateral", "elevación lateral", "lateral raise", "vuelo lateral",
                "abduccion de hombro", "abducción de hombro", "abduccion hombro", "lateral deltoid",
            ),
        ),
        PatternRule(
            patternId = "horizontal_abduction",
            label = "Aperturas / Abducción horizontal",
            strongKeywords = listOf(
                "apertura", "aperturas", "fly", "pec deck", "crossover", "cruces",
                "abduccion horizontal", "abducción horizontal", "chest fly",
            ),
        ),
        PatternRule(
            patternId = "anti_extension",
            label = "Anti-extensión",
            strongKeywords = listOf(
                "plancha", "plank", "ab wheel", "rollout", "rueda abdominal", "dead bug",
                "ab rollout",
            ),
        ),
        PatternRule(
            patternId = "anti_rotation",
            label = "Anti-rotación",
            strongKeywords = listOf("pallof", "anti rotacion", "anti-rotacion", "anti rotación", "anti-rotación"),
        ),
        PatternRule(
            patternId = "core_flexion",
            label = "Flexión de tronco",
            strongKeywords = listOf(
                "crunch", "abdominal", "abdominales", "sit up", "encogimiento",
                "elevacion de piernas", "elevación de piernas", "leg raise",
            ),
        ),
        PatternRule(
            patternId = "hip_abduction",
            label = "Abducción de cadera",
            strongKeywords = listOf(
                "abduccion de cadera", "abducción de cadera", "hip abduction",
                "apertura de cadera", "abductor",
            ),
        ),
        PatternRule(
            patternId = "hip_adduction",
            label = "Aducción de cadera",
            strongKeywords = listOf("aduccion", "aducción", "adduction", "aductores", "adductor"),
        ),
    )

    fun detect(name: String): DetectedMovementPattern? {
        val normalized = normalize(name)
        if (normalized.isBlank()) return null

        var best: DetectedMovementPattern? = null
        for (rule in RULES) {
            val strongHit = rule.strongKeywords.firstOrNull { normalized.contains(it) }
            val weakHit = if (strongHit == null) rule.weakKeywords.firstOrNull { normalized.contains(it) } else null
            if (strongHit == null && weakHit == null) continue

            val confidence = if (strongHit != null) PatternConfidence.HIGH else PatternConfidence.MEDIUM
            val candidate = DetectedMovementPattern(
                patternId = rule.patternId,
                label = rule.label,
                confidence = confidence,
                matchedTerms = listOfNotNull(strongHit, weakHit),
            )
            if (best == null || confidence.ordinal < best.confidence.ordinal) {
                best = candidate
            }
        }
        return best
    }

    private fun normalize(value: String): String {
        val lower = value.lowercase()
        val sb = StringBuilder(lower.length)
        for (ch in lower) {
            sb.append(
                when (ch) {
                    'á' -> 'a'
                    'é' -> 'e'
                    'í' -> 'i'
                    'ó' -> 'o'
                    'ú' -> 'u'
                    'ü' -> 'u'
                    'ñ' -> 'n'
                    else -> ch
                },
            )
        }
        return sb.toString().replace(Regex("[^a-z0-9\\s]"), " ").replace(Regex("\\s+"), " ").trim()
    }
}
