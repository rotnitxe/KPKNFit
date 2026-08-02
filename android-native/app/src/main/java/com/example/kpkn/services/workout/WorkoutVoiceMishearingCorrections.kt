package com.example.kpkn.services.workout

/**
 * Corrige mishearings de Vosk dentro del dominio de entrenamiento.
 *
 * Primero frases conocidas ("metro corporal" -> "peso corporal"), luego tokens
 * con distancia de edición contra el léxico del dominio. Nunca toca números y
 * evita palabras comunes del español que no pertenecen al gimnasio.
 */
internal object WorkoutVoiceMishearingCorrections {

    private val PHRASE_CORRECTIONS = mapOf(
        "metro corporal" to "peso corporal",
        "metros corporales" to "peso corporal",
        "metro coporal" to "peso corporal",
        "carga corporal" to "peso corporal",
        "solo la varra" to "solo la barra",
        "la varra" to "la barra",
    )

    private val DOMAIN_LEXICON = setOf(
        "kilo", "kilos", "peso", "carga", "lastre", "asistencia", "contrapeso", "barra",
        "mancuerna", "mancuernas", "repeticion", "repeticiones", "segundo", "segundos",
        "minuto", "minutos", "descanso", "timer", "rir", "rpe", "fallo", "falla",
        "reservas", "reserva", "ritmo", "esfuerzo", "intensidad", "porcentaje",
        "izquierda", "izquierdo", "derecha", "derecho", "saltar", "siguiente", "anterior",
        "confirmar", "confirmado", "cancelar", "corregir", "deshacer", "dropset",
        "descendente", "rom", "rango", "recorrido", "corporal", "asistida", "asistidas",
        "ayuda", "ayudada", "ayudadas", "izq", "der",
        // Vocabulario verbal de intensidad (Fase 2) — proteger del Levenshtein:
        "equis", "quedaron", "quedaban", "recamara", "dandolo", "dándolo", "agotado",
        "agotada", "cansado", "cansada", "energia", "energía", "tope",
    )

    private val TOKEN_CORRECTIONS = mapOf(
        "rear" to "rir",
        "reir" to "rir",
        "varra" to "barra",
    )

    private val STOPWORDS = setOf(
        "pero", "para", "como", "cuando", "donde", "este", "esta", "esto", "ese", "esa",
        "eso", "tambien", "entonces", "porque", "aunque", "siempre", "nunca", "casi",
        "todo", "toda", "muy", "mas", "menos", "despues", "antes", "acerca", "medio",
        "media", "casa", "cosa", "hace", "hacer", "puede", "puedo", "quiero", "dice",
        "dijo", "diga", "voy", "vas", "va", "fue", "era", "estoy", "estas", "estan",
    )

    fun correct(normalizedText: String): String {
        if (normalizedText.isBlank()) return normalizedText
        var result = normalizedText
        for ((from, to) in PHRASE_CORRECTIONS) {
            if (result.contains(from)) {
                result = result.replace(from, to)
            }
        }
        var changed = false
        val corrected = result.split(' ').map { token ->
            val directCorrection = TOKEN_CORRECTIONS[token]
            if (directCorrection != null) {
                changed = true
                directCorrection
            } else if (token.length < 4 || token.any(Char::isDigit) || token in STOPWORDS) {
                token
            } else {
                val fixed = closestLexiconWord(token) ?: token
                if (fixed != token) changed = true
                fixed
            }
        }
        return if (changed) corrected.joinToString(" ") else result
    }

    private fun closestLexiconWord(token: String): String? {
        var best: String? = null
        var bestDistance = Int.MAX_VALUE
        for (candidate in DOMAIN_LEXICON) {
            val distance = levenshtein(token, candidate)
            val maxAllowed = if (candidate.length <= 5) 1 else 2
            if (distance <= maxAllowed && distance < bestDistance) {
                best = candidate
                bestDistance = distance
            }
        }
        return best
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val tmp = dp[j]
                dp[j] = minOf(
                    dp[j] + 1,
                    dp[j - 1] + 1,
                    prev + if (a[i - 1] == b[j - 1]) 0 else 1,
                )
                prev = tmp
            }
        }
        return dp[b.length]
    }
}
