package com.example.kpkn.services.workout

import java.text.Normalizer
import java.util.Locale

/**
 * Closed-list instant commands detectable from partial ASR results
 * only in CONFIRM_WAIT or while a rest timer is active.
 */
object WorkoutVoiceInstantCommands {

    private val CONFIRM = listOf("si", "sí", "confirmar", "confirmado", "dale", "ok", "listo", "correcto")
    private val CANCEL = listOf("no", "cancelar", "corregir", "borrar", "descartar")
    private val SKIP_REST = listOf(
        "saltar descanso", "saltar timer", "listo", "ya estoy", "continuar",
    )
    private val USE_ADAPTIVE = listOf(
        "usar sugerido", "descanso dinamico", "descanso dinámico", "usar adaptativo",
    )
    private val UNDO = listOf("corregir", "deshacer", "borra eso")
    private val STOP = listOf("para", "calla")

    fun match(
        partial: String,
        confirmWait: Boolean,
        restActive: Boolean,
    ): VoiceSessionCommand? {
        val lower = normalize(partial)
        if (lower.isBlank()) return null

        if (STOP.any { lower == normalize(it) }) {
            return VoiceSessionCommand.StopSpeaking
        }

        if (confirmWait) {
            if (matchesExactOrPhrase(lower, CONFIRM)) return VoiceSessionCommand.Confirm
            if (matchesExactOrPhrase(lower, CANCEL)) return VoiceSessionCommand.Cancel
            return null
        }

        if (restActive) {
            if (USE_ADAPTIVE.any { lower.contains(normalize(it)) }) {
                return VoiceSessionCommand.UseAdaptiveRest
            }
            if (SKIP_REST.any { lower.contains(normalize(it)) }) {
                return VoiceSessionCommand.SkipRest
            }
            if (UNDO.any { lower == normalize(it) || lower.contains(normalize(it)) }) {
                return VoiceSessionCommand.UndoLastSet
            }
        }

        return null
    }

    private fun matchesExactOrPhrase(lower: String, keywords: List<String>): Boolean {
        val tokens = lower.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.size == 1) {
            val t = tokens[0]
            return keywords.any { normalize(it) == t }
        }
        return keywords.any { kw ->
            val n = normalize(kw)
            lower == n || lower.contains(n)
        }
    }

    private fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{Mn}+"), "").trim()
    }
}
