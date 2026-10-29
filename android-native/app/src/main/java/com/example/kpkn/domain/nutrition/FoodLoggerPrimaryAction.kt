package com.example.kpkn.domain.nutrition

/**
 * Canonical primary-button label for the food logger.
 * Identity is a normalized fingerprint, not raw trim() of the field.
 */
object FoodLoggerPrimaryAction {

    fun descriptionFingerprint(text: String): String =
        text.trim().replace(Regex("\\s+"), " ")

    fun isDescriptionEdited(current: String, lastAnalyzed: String, hasTags: Boolean, describeTab: Boolean): Boolean {
        if (!describeTab || !hasTags) return false
        return descriptionFingerprint(current) != descriptionFingerprint(lastAnalyzed)
    }

    /**
     * Un solo verbo (lienzo f-typed): «Analizar», «Volver a analizar» si el texto cambió después de analizar y «Guardar» con
     * alimentos. La búsqueda va sola al escribir, así que en Buscar no hay verbo propio.
     */
    @Suppress("UNUSED_PARAMETER")
    fun label(hasTags: Boolean, descriptionEdited: Boolean, isSearchMode: Boolean): String {
        return when {
            descriptionEdited -> "Volver a analizar"
            hasTags -> "Guardar"
            else -> "Analizar"
        }
    }
}
