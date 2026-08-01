package com.example.kpkn.services.workout

/**
 * ASR hypothesis with optional confidence.
 *
 * [confidenceKnown] = false para Vosk (no inventar scores). La política de auto-confirmación
 * no debe tratar confianza ficticia como suficiente.
 */
data class VoiceHypothesis(
    val text: String,
    val confidence: Float = 0f,
    val confidenceKnown: Boolean = true,
    /** Hypotheses recovered from a Vosk partial when the final result was empty. */
    val fromPartial: Boolean = false,
)

/**
 * Picks the best workout ASR hypothesis using gym signal words + confidence.
 * Ports the idea of VoiceNutritionRecognizer.pickBestTranscription to the continuous path.
 */
object WorkoutVoiceHypothesisScorer {

    private val GYM_SIGNAL_WORDS = setOf(
        "kilo", "kilos", "rep", "reps", "repeticion", "repeticiones",
        "rpe", "rir", "fallo", "falla", "serie", "series",
        "peso", "carga", "descanso", "ejercicio", "confirmar",
        "cancelar", "siguiente", "anterior", "izquierda", "derecha",
        "segundo", "segundos", "minuto", "minutos",
        "saltar", "listo", "corregir", "sugerido", "adaptativo",
        "por", "con",
    )

    fun pickBest(hypotheses: List<VoiceHypothesis>): VoiceHypothesis? {
        if (hypotheses.isEmpty()) return null
        if (hypotheses.size == 1) return hypotheses.first()

        return hypotheses.maxByOrNull { score(it) }
    }

    fun pickBestText(hypotheses: List<VoiceHypothesis>): String? =
        pickBest(hypotheses)?.text?.trim()?.takeIf { it.isNotBlank() }

    fun score(hypothesis: VoiceHypothesis): Float {
        val lower = hypothesis.text.lowercase()
        val gymHits = GYM_SIGNAL_WORDS.count { lower.contains(it) }
        val confidenceBoost = hypothesis.confidence.coerceIn(0f, 1f) * 2f
        return gymHits * 3f + confidenceBoost
    }

    /** High enough ASR confidence to allow smart auto-confirm (F4). */
    const val AUTO_CONFIRM_MIN_CONFIDENCE = 0.55f
}
