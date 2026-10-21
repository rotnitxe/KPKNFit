package com.example.kpkn.domain.relator

object RelatorSelector {
    fun deterministicSeed(sessionId: String, setKey: String, idleCycle: Int): Int {
        var hash = 17
        hash = 31 * hash + sessionId.hashCode()
        hash = 31 * hash + setKey.hashCode()
        hash = 31 * hash + idleCycle
        return hash
    }

    /**
     * Elige el candidato del momento (LR1g): sin jitter y sin evitar el tema anterior. Solo cuentan la prioridad, la
     * relevancia, el enfriamiento por series y las huellas usadas, así que dentro de un mismo momento sale siempre lo mismo.
     */
    fun select(
        candidates: List<RelatorCandidate>,
        state: RelatorSelectorState,
        context: RelatorContext,
        longTerm: RelatorLongTermMemory,
        nowEpochDay: Long = java.time.LocalDate.now().toEpochDay(),
    ): RelatorCandidate? {
        if (candidates.isEmpty()) return null
        val seed = deterministicSeed(context.sessionId, context.setKey, context.idleCycle)
        val holding = candidates.firstOrNull { it.holdPrevious }
        if (holding != null) return holding

        val eligible = candidates.filter { candidate ->
            passesCooldown(candidate, state, context) &&
                passesConceptMemory(candidate, state, context, longTerm, nowEpochDay)
        }
        val pool = eligible.ifEmpty { candidates.filter { it.isReaction } }.ifEmpty { candidates }
        val unused = pool.filter { candidate ->
            fingerprint(candidate, variantIndex(candidate, seed, state)) !in state.fingerprints
        }
        return (unused.ifEmpty { pool }).maxWithOrNull { a, b ->
            val cmp = score(a, seed, state).compareTo(score(b, seed, state))
            if (cmp != 0) {
                cmp
            } else {
                fingerprint(a, variantIndex(a, seed, state)).compareTo(fingerprint(b, variantIndex(b, seed, state)))
            }
        }
    }

    fun fingerprint(candidate: RelatorCandidate, variantIndex: Int): String {
        val extra = candidate.spokenConceptId?.trim().orEmpty()
        val line = candidate.lines.getOrElse(variantIndex) { candidate.lines.firstOrNull().orEmpty() }
        val lineTag = line.hashCode()
        return if (extra.isEmpty()) {
            "${candidate.fingerprintBase}:$variantIndex:$lineTag"
        } else {
            "${candidate.fingerprintBase}:$extra:$variantIndex:$lineTag"
        }
    }

    /**
     * Variante del candidato para este momento (LR1h). Dentro de un momento sale siempre la misma: depende solo de la
     * semilla y del estado. Entre usos del tema rota por módulo: la siguiente vez sale la frase siguiente a la última
     * que dijo ese tema, así que con dos frases no se repite de inmediato. La primera vez decide la semilla.
     */
    fun variantIndex(candidate: RelatorCandidate, seed: Int, state: RelatorSelectorState): Int {
        val n = candidate.lines.size.coerceAtLeast(1)
        if (n == 1) return 0
        val last = state.lastVariantByTopic[candidate.topic]
        val start = if (last == null) {
            Math.floorMod(seed + candidate.topic.ordinal * 13, n)
        } else {
            (last + 1) % n
        }
        for (offset in 0 until n) {
            val idx = (start + offset) % n
            val fp = fingerprint(candidate, idx)
            if (fp !in state.fingerprints) return idx
        }
        return start
    }

    private fun score(candidate: RelatorCandidate, seed: Int, state: RelatorSelectorState): Double {
        val unusedBonus = if (fingerprint(candidate, variantIndex(candidate, seed, state)) !in state.fingerprints) {
            8.0
        } else {
            0.0
        }
        return candidate.priority + candidate.relevance * 10.0 + unusedBonus
    }

    private fun passesCooldown(
        candidate: RelatorCandidate,
        state: RelatorSelectorState,
        context: RelatorContext,
    ): Boolean {
        if (candidate.isReaction || candidate.cooldownSets <= 0) return true
        val last = state.lastTopicAtSet[candidate.topic] ?: return true
        return state.setsSeen - last >= candidate.cooldownSets
    }

    private fun passesConceptMemory(
        candidate: RelatorCandidate,
        state: RelatorSelectorState,
        context: RelatorContext,
        longTerm: RelatorLongTermMemory,
        nowEpochDay: Long,
    ): Boolean {
        val conceptId = candidate.spokenConceptId ?: return true
        if (conceptId in state.conceptSpokenThisSession) return false
        if ("${context.exerciseId}|$conceptId" in state.conceptSpokenForExercise) return false
        if (longTerm.conceptWasShownRecently(conceptId, nowEpochDay)) return false
        return true
    }
}
