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

    private class Index(val catalog: ExerciseCatalogV2) {
        val matcher = ExerciseNameMatcher(catalog)
        val resolver = ExerciseCatalogV2Resolver(catalog)
        val definitions = catalog.families.flatMap { it.definitions }.associateBy { it.id }
        val names = definitions.values.flatMap { definition ->
            definition.configurations.map { it.id to definition.canonicalName }
        }.toMap()
    }

    @Volatile private var cached: Index? = null

    private fun indexFor(catalog: ExerciseCatalogV2): Index =
        cached?.takeIf { it.catalog === catalog } ?: synchronized(this) {
            cached?.takeIf { it.catalog === catalog } ?: Index(catalog).also { cached = it }
        }

    /** Las tres mejores configuraciones para un nombre importado, de mayor a menor confianza. */
    fun suggestions(catalog: ExerciseCatalogV2, rawName: String): List<ExerciseNameCandidate> =
        indexFor(catalog).matcher.match(rawName).candidates

    /** Búsqueda libre: una configuración sugerida por ejercicio. La confianza queda en cero (no es un emparejamiento). */
    fun search(catalog: ExerciseCatalogV2, query: String): List<ExerciseNameCandidate> {
        val index = indexFor(catalog)
        return index.resolver.search(query).mapNotNull { hit ->
            val definition = index.definitions[hit.definitionId] ?: return@mapNotNull null
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
        val names = indexFor(catalog).names
        return configIds.mapNotNull { id -> names[id]?.let { id to it } }.toMap()
    }
}
