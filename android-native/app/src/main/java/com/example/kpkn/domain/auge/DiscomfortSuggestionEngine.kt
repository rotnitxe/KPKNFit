package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.DISCOMFORT_CATALOG
import com.example.kpkn.data.models.DiscomfortCatalogEntry

/**
 * Sugiere molestias relevantes para la sesión según los músculos involucrados,
 * y ofrece búsqueda sobre el catálogo. Lógica pura y testeable.
 */
object DiscomfortSuggestionEngine {

    /**
     * Molestias del catálogo (sin "Sin molestias") relacionadas con al menos uno
     * de los pilares musculares de la sesión.
     */
    fun suggestForMuscles(sessionPillarMuscleIds: Collection<String>): List<DiscomfortCatalogEntry> {
        if (sessionPillarMuscleIds.isEmpty()) return emptyList()
        return DISCOMFORT_CATALOG
            .filter { it.id != "none" }
            .filter { entry ->
                entry.relatedMuscles.any { related ->
                    sessionPillarMuscleIds.any { sessionMuscle ->
                        matchesAugeMuscleTarget(related, sessionMuscle) ||
                            matchesAugeMuscleTarget(sessionMuscle, related)
                    }
                }
            }
    }

    /**
     * Resultados de búsqueda por label/descripción/section sobre el catálogo completo.
     * Case-insensitive; coincide con cualquiera de los términos separados por espacio.
     */
    fun search(query: String): List<DiscomfortCatalogEntry> {
        val terms = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (terms.isEmpty()) return emptyList()
        return DISCOMFORT_CATALOG
            .filter { it.id != "none" }
            .filter { entry ->
                val haystack = (entry.label + " " + entry.description + " " + entry.section.label).lowercase()
                terms.all(haystack::contains)
            }
    }
}
