package com.example.kpkn.screens.programs.importer

import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2Resolver
import com.example.kpkn.domain.training.importer.ExerciseNameCandidate
import com.example.kpkn.domain.training.importer.ExerciseNameMatcher

/**
 * Consultas del catálogo para la importación, compartidas por el paso del alta y por la pantalla de Programas: las
 * sugerencias de un nombre importado, la búsqueda libre para «Elegir» y el nombre de cada configuración decidida.
 */
internal object ImportCatalogLookup {
    /** Ejercicios que muestra la búsqueda libre de «Elegir». */
    private const val SEARCH_LIMIT = 8

    /** Las tres mejores configuraciones para un nombre importado, de mayor a menor confianza. */
    fun suggestions(catalog: ExerciseCatalogV2, rawName: String): List<ExerciseNameCandidate> =
        ExerciseNameMatcher(catalog).match(rawName).candidates

    /** Búsqueda libre: una configuración sugerida por ejercicio. La confianza queda en cero (no es un emparejamiento). */
    fun search(catalog: ExerciseCatalogV2, query: String): List<ExerciseNameCandidate> {
        val definitions = catalog.families.flatMap { it.definitions }.associateBy { it.id }
        return ExerciseCatalogV2Resolver(catalog).search(query).mapNotNull { hit ->
            val definition = definitions[hit.definitionId] ?: return@mapNotNull null
            val configId = hit.suggestedConfigurationId ?: definition.defaultConfigurationId
            val config = definition.configurations.firstOrNull { it.id == configId } ?: return@mapNotNull null
            ExerciseNameCandidate(
                configurationId = config.id,
                displayName = definition.canonicalName,
                confidence = 0.0,
                equipmentId = config.profile.equipmentId,
            )
        }.take(SEARCH_LIMIT)
    }

    /** Nombre canónico de cada configuración de [configIds] que existe en el catálogo. */
    fun namesOf(catalog: ExerciseCatalogV2, configIds: Set<String>): Map<String, String> {
        if (configIds.isEmpty()) return emptyMap()
        return catalog.families.flatMap { it.definitions }.flatMap { definition ->
            definition.configurations
                .filter { it.id in configIds }
                .map { it.id to definition.canonicalName }
        }.toMap()
    }
}
