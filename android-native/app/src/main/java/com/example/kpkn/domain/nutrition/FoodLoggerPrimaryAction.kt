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

    fun label(hasTags: Boolean, descriptionEdited: Boolean, isSearchMode: Boolean): String {
        return when {
            descriptionEdited -> "ACTUALIZAR Y BUSCAR"
            hasTags -> "GUARDAR"
            isSearchMode && !hasTags -> "BUSCAR"
            else -> "REGISTRAR"
        }
    }
}
