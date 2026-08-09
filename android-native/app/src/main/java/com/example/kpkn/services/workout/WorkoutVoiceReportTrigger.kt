package com.example.kpkn.services.workout

/** Shared trigger vocabulary for the voice report flow and every active grammar. */
internal object WorkoutVoiceReportTrigger {
    const val KEYWORD = "caupolican"
    const val LEGACY_ALIAS = "reportar equipo"

    val grammarTokens: Set<String> = linkedSetOf(
        KEYWORD,
        "caupolican",
        "caupolica",
        "caupoli kan",
        "caupolikan",
        "capolican",
        "capolica",
        LEGACY_ALIAS,
    )

    fun matches(normalizedText: String): Boolean {
        val text = normalizedText.trim()
        return grammarTokens.any { token ->
            text == token || text.startsWith("$token ") || text.contains(" $token ")
        }
    }
}
