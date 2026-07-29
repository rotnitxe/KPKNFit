package com.example.kpkn.services.workout

/**
 * Gramática JSON restringida para Vosk small-es.
 *
 * Las sustituciones viven en [WorkoutVoiceGrammarLexicon] y el parser consume sus
 * aliases. Así una frase válida para Vosk nunca deja de representar el comando
 * original por una sustitución local.
 */
object WorkoutVoiceGrammarBuilder {
    fun build(stage: VoicePipelineStage, context: VoiceCommandContext?): String {
        val tokens = linkedSetOf<String>()
        WorkoutVoiceCommandParser.grammarTokensForStage(
            stage = stage,
            includeFeedback =
                context?.showFinishSheet == true || context?.showPostExerciseSheet == true,
        ).forEach { token ->
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
        tokens += "[unk]"
        return tokens
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
