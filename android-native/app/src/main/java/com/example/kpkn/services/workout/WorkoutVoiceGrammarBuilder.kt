package com.example.kpkn.services.workout

/**
 * Gramática JSON restringida para Vosk small-es.
 *
 * Las sustituciones viven en [WorkoutVoiceGrammarLexicon] y el parser consume sus
 * aliases. Así una frase válida para Vosk nunca deja de representar el comando
 * original por una sustitución local.
 */
object WorkoutVoiceGrammarBuilder {
    fun build(
        stage: VoicePipelineStage,
        context: VoiceCommandContext?,
        pendingClarification: Boolean = false,
    ): String {
        val tokens = linkedSetOf<String>()
        WorkoutVoiceCommandParser.grammarTokensForStage(
            stage = stage,
            includeFeedback =
                context?.showFinishSheet == true || context?.showPostExerciseSheet == true,
        ).filterNot { token ->
            context?.trackRom != true && token.lowercase() in ROM_COMPONENTS
        }.forEach { token ->
            expandForVosk(token).forEach { tokens += it }
        }
        context?.exercise?.name
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let { exerciseName ->
                expandForVosk(exerciseName.lowercase()).forEach { tokens += it }
            }
        context?.nextExerciseName
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let { nextExerciseName ->
                expandForVosk(nextExerciseName.lowercase()).forEach { tokens += it }
            }
        context?.customUnit
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let { customUnit ->
                expandForVosk(customUnit.lowercase()).forEach { tokens += it }
            }
        context?.tagNames.orEmpty().forEach { tagName ->
            tagName.trim().takeIf(String::isNotBlank)?.let { raw ->
                expandForVosk(raw.lowercase()).forEach { tokens += it }
            }
        }
        context?.exerciseAliases.orEmpty().forEach { alias ->
            alias.trim().takeIf(String::isNotBlank)?.let { raw ->
                expandForVosk(raw.lowercase()).forEach { tokens += it }
            }
        }
        context?.customIntensityPhrases.orEmpty().forEach { phrase ->
            phrase.trim().takeIf(String::isNotBlank)?.let { raw ->
                expandForVosk(raw.lowercase()).forEach { tokens += it }
            }
        }
        val phrases = linkedSetOf<String>().apply { addAll(tokens) }
        if (stage != VoicePipelineStage.CONFIRM_WAIT) {
            val numericWords = WorkoutVoiceCommandParser.defaultNumericGrammarTokens()
                .filter { token -> token !in NON_NUMERIC_COMPONENTS && ' ' !in token }
            val metricComponents = if (context?.trackRom == true) {
                METRIC_COMPONENTS
            } else {
                METRIC_COMPONENTS - ROM_COMPONENTS
            }
            numericWords.forEach { number ->
                metricComponents.forEach { metric -> phrases += "$number $metric" }
                INTENSITY_COMPONENTS.forEach { intensity -> phrases += "$intensity $number" }
            }
            context?.tagNames.orEmpty().forEach { tag ->
                expandForVosk(tag).forEach { expanded -> phrases += "etiqueta $expanded" }
            }
        }
        if (stage == VoicePipelineStage.LISTENING && pendingClarification) {
            WorkoutVoiceCommandParser.clarificationReplyGrammarTokens().forEach { token ->
                expandForVosk(token).forEach { phrases += it }
            }
        }
        phrases += "[unk]"
        return phrases
            .filter(String::isNotBlank)
            .distinct()
            .joinToString(
                prefix = "[",
                postfix = "]",
                separator = ",",
            ) { token ->
                "\"${token.replace("\"", "\\\"")}\""
            }
    }

    internal fun expandForVosk(token: String): List<String> {
        val key = token.trim().lowercase()
        if (key.isBlank()) return emptyList()
        if (key.all { it.isDigit() || it == '.' || it == ',' }) return emptyList()

        WorkoutVoiceGrammarLexicon.expandWord(key)
            .takeIf(List<String>::isNotEmpty)
            ?.let(::withAccentVariants)
            ?.let { return it }

        if (' ' in key) {
            val rewritten = key
                .split(Regex("\\s+"))
                .joinToString(" ") { word ->
                    WorkoutVoiceGrammarLexicon.expandWord(word).firstOrNull() ?: word
                }
            return withAccentVariants(listOf(rewritten))
        }
        return withAccentVariants(listOf(key))
    }

    private val METRIC_COMPONENTS = setOf(
        "repeticiones", "segundos", "minutos", "kilos", "metros", "kilómetros", "millas", "unidades", "rom",
    )
    private val ROM_COMPONENTS = setOf("rom", "rango", "recorrido")
    private val INTENSITY_COMPONENTS = setOf("esfuerzo", "intensidad", "reservas", "ritmo", "porcentaje")
    private val NON_NUMERIC_COMPONENTS = METRIC_COMPONENTS + INTENSITY_COMPONENTS + setOf(
        "punto", "coma", "como", "medio", "media", "kilo", "peso", "carga", "repeticion", "repetición",
        "segundo", "minuto", "metro", "kilometro", "milla", "unidad", "caloria", "calorías", "vuelta",
        "vueltas", "etiqueta", "rango", "recorrido", "izquierda", "izquierdo", "derecha", "derecho", "fallo", "falla", "por",
    )
    private fun withAccentVariants(values: List<String>): List<String> = buildList {
        values.forEach { value ->
            add(value)
            val withoutMarks = stripCombiningMarks(value)
            if (withoutMarks != value) add(withoutMarks)
        }
    }.distinct()

    private fun stripCombiningMarks(text: String): String =
        java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
}
