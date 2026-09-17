package com.example.kpkn.domain.exercises.catalogv2

/**
 * Derivación determinista del nombre visible de un ejercicio catalogado.
 *
 * El nombre es EXACTAMENTE el canonicalName de la definición del catálogo
 * (ExerciseDefinitionV2.canonicalName), verbatim, sin sufijos, sin prefijos y
 * sin composición de técnica en el nombre. Los matices de configuración
 * (implemento, agarre, técnica de receta) se exponen como chips en
 * exerciseDisplayParts, nunca como texto del nombre.
 *
 * Restricción de scripts/catalog_v2_gate.py (legacy_consumer_gate): ningún
 * código de producto puede resolver un id a partir de un nombre visible.
 */
object CatalogDisplayNames {

    /**
     * Nombre visible para [configurationId]: el canonicalName de la definición
     * que la contiene. Devuelve null si el id no resuelve en el catálogo.
     * Nunca inventa nombres: el fallback es null.
     */
    fun configurationDisplayName(
        catalog: ExerciseCatalogV2,
        configurationId: String,
    ): String? {
        val normalized = configurationId.trim().lowercase()
        if (normalized.isBlank()) return null
        for (family in catalog.families) {
            for (definition in family.definitions) {
                val matches = definition.configurations.any {
                    it.id.trim().lowercase() == normalized
                }
                if (matches) return definition.canonicalName.trim()
            }
        }
        return null
    }

    /** Índice configurationId → canonicalName verbatim de la definición. */
    fun buildDisplayNameIndex(catalog: ExerciseCatalogV2): Map<String, String> {
        val index = LinkedHashMap<String, String>()
        for (family in catalog.families) {
            for (definition in family.definitions) {
                for (configuration in definition.configurations) {
                    index[configuration.id] = definition.canonicalName.trim()
                }
            }
        }
        return index
    }
}
