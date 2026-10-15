package com.example.kpkn.domain.relator

object RelatorComposer {
    /**
     * Compone la frase de un candidato. Sin rótulos (LR1g): si una frase necesita contexto, va dentro de la frase.
     * La elección es determinista dentro de un momento; la variedad sale entre series, por las huellas del selector.
     */
    fun compose(
        candidate: RelatorCandidate,
        context: RelatorContext,
        state: RelatorSelectorState,
        longTerm: RelatorLongTermMemory,
        previousText: String? = null,
    ): Pair<RelatorLine, RelatorLongTermMemory> {
        if (candidate.holdPrevious && previousText != null) {
            return RelatorLine(
                text = previousText,
                holdPrevious = true,
                phaseKey = candidate.phaseKey,
                topic = candidate.topic,
                fingerprint = null,
                spokenConceptId = candidate.spokenConceptId,
                actions = candidate.actions,
            ) to longTerm
        }
        val seed = RelatorSelector.deterministicSeed(context.sessionId, context.setKey, context.idleCycle)
        val variant = RelatorSelector.variantIndex(candidate, seed, state)
        val core = candidate.lines.getOrElse(variant) { candidate.lines.firstOrNull().orEmpty() }
        val voice = RelatorVoiceWords.of(context.feminine)
        val rendered = applyVoiceAndName(core, context.shortExerciseName(), voice, candidate.actions)
        val clipped = clipToBudget(rendered, candidate.actions.map { it.span.ifBlank { it.label } })
        return RelatorLine(
            text = clipped.takeIf { it.isNotBlank() },
            holdPrevious = false,
            phaseKey = candidate.phaseKey,
            topic = candidate.topic,
            fingerprint = RelatorSelector.fingerprint(candidate, variant),
            spokenConceptId = candidate.spokenConceptId,
            actions = candidate.actions,
        ) to longTerm
    }

    private fun applyVoiceAndName(
        template: String,
        exerciseName: String,
        voice: RelatorVoiceWords,
        actions: List<RelatorActionSpec>,
    ): String {
        fun apply(name: String): String = template
            .replace("{ex}", name)
            .replace("{conservative}", voice.conservative)
            .replace("{ready}", voice.ready)
            .replace("{safe}", voice.safe)
            .replace("{slow}", voice.slow)
            .replace("{prepared}", voice.prepared)

        fun keeps(text: String): Boolean = actions.all { action ->
            val needle = action.span.ifBlank { action.label }
            needle.isBlank() || text.contains(needle, ignoreCase = true)
        }

        val full = apply(exerciseName)
        if (keeps(full)) return full
        val shortened = apply(
            if (exerciseName.length > 28) exerciseName.take(27).trimEnd() + "…" else exerciseName,
        )
        return if (keeps(shortened)) shortened else full
    }

    private fun clipToBudget(text: String, mustKeep: List<String>): String {
        if (text.length <= RELATOR_LINE_MAX_CHARS) return text
        val keep = mustKeep.filter { it.isNotBlank() }
        if (keep.any { !text.contains(it, ignoreCase = true) }) return text
        val clipped = text.take(RELATOR_LINE_MAX_CHARS - 1).trimEnd { it == ' ' || it == ',' || it == ';' } + "…"
        return if (keep.all { clipped.contains(it, ignoreCase = true) }) clipped else text
    }
}

internal data class RelatorVoiceWords(
    val conservative: String,
    val ready: String,
    val safe: String,
    val slow: String,
    val prepared: String,
) {
    companion object {
        fun of(feminine: Boolean): RelatorVoiceWords = if (feminine) {
            RelatorVoiceWords("conservadora", "lista", "segura", "lenta", "preparada")
        } else {
            RelatorVoiceWords("conservador", "listo", "seguro", "lento", "preparado")
        }
    }
}
