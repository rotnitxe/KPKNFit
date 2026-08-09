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
        "capolican" to WorkoutVoiceReportTrigger.KEYWORD,
        "capolica" to WorkoutVoiceReportTrigger.KEYWORD,
        "caupolica" to WorkoutVoiceReportTrigger.KEYWORD,
        "caupoli kan" to WorkoutVoiceReportTrigger.KEYWORD,
        "caupolikan" to WorkoutVoiceReportTrigger.KEYWORD,
    )

    private val PAIR_CORRECTIONS = mapOf(
        "rir voz" to "rir dos",
        "rpe voz" to "rpe dos",
        "rir toca" to "rir dos",
        "rpe toca" to "rpe dos",
        "rir kilos" to "rir dos",
        "rpe kilos" to "rpe dos",
        "ritmo doce" to "rir dos",
        // RIR no puede superar ~5: "doce/ocho/diez" tras "rir" son mishearings de "dos".
        "rir doce" to "rir dos",
        "rir ocho" to "rir dos",
        "rir diez" to "rir dos",
        "rir reservas doce" to "rir dos",
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
        var result = correctedDeterministically(normalizedText)
        if (result != normalizedText) {
            // Dejar que el token map adicional corra sobre el texto ya corregido.
            result = deterministicTokenPass(result)
        }
        var changed = false
        val corrected = result.split(' ').map { token ->
            if (token.length < 4 || token.any(Char::isDigit) || token in STOPWORDS) {
                token
            } else {
                val closest = closestLexiconWord(token)
                if (closest != null && closest != token) {
                    changed = true
                    closest
                } else {
                    token
                }
            }
        }
        return if (changed) corrected.joinToString(" ") else result
    }

    /**
     * Solo correcciones deterministas (pares, frases y mapeos directos), sin
     * Levenshtein. Seguro para el clasificador de comandos, donde palabras de
     * vocabulario propio ("falta", "lado") no deben "corregirse" al léxico de series.
     */
    fun correctDeterministic(normalizedText: String): String {
        if (normalizedText.isBlank()) return normalizedText
        val result = correctedDeterministically(normalizedText)
        return deterministicTokenPass(result)
    }

    private fun correctedDeterministically(normalizedText: String): String {
        var result = normalizedText
        for ((from, to) in PAIR_CORRECTIONS) {
            if (result.contains(from)) {
                result = result.replace(from, to)
            }
        }
        for ((from, to) in PHRASE_CORRECTIONS) {
            if (result.contains(from)) {
                result = result.replace(from, to)
            }
        }
        return result
    }

    private fun deterministicTokenPass(text: String): String {
        var changed = false
        val corrected = text.split(' ').map { token ->
            val directCorrection = TOKEN_CORRECTIONS[token]
            if (directCorrection != null) {
                changed = true
                directCorrection
            } else {
                token
            }
        }
        return if (changed) corrected.joinToString(" ") else text
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
