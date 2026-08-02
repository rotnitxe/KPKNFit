package com.example.kpkn.services.workout

import com.example.kpkn.data.models.CustomIntensityPhrase
import java.text.Normalizer
import java.util.Locale

/**
 * Reescribe frases personalizadas del usuario a tokens canónicos del parser
 * de series ("rpe N", "rir N", "porcentaje N", "al fallo").
 *
 * La normalización es la MISMA que usa el parser (lowercase, sin tildes,
 * colapso de espacios) para que la comparación sea estable.
 */
object WorkoutVoiceCustomPhraseRewriter {

    fun rewrite(
        normalizedTranscript: String,
        phrases: List<CustomIntensityPhrase>,
    ): String {
        var result = normalizedTranscript
        for (phrase in phrases) {
            val raw = phrase.phrase.trim()
            if (raw.length < 4) continue
            val target = normalize(raw)
            if (target.isBlank()) continue
            val replacement = when (phrase.kind.uppercase()) {
                "FALLO" -> "al fallo"
                "RPE" -> phrase.value?.let { "rpe ${it.toTrimmedNumberString()}" } ?: continue
                "RIR" -> phrase.value?.let { "rir ${it.toTrimmedNumberString()}" } ?: continue
                "PERCENT_RM" -> phrase.value?.let { "porcentaje ${it.toTrimmedNumberString()}" } ?: continue
                else -> continue
            }
            if (result.contains(target)) {
                result = result.replace(target, replacement)
            }
        }
        return result
    }

    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .replace(Regex("[^a-z0-9.,% ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun Double.toTrimmedNumberString(): String =
        if (this % 1.0 == 0.0) this.toInt().toString() else this.toString()
}
