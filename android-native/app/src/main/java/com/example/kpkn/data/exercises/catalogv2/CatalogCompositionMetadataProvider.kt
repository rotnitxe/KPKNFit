package com.example.kpkn.data.exercises.catalogv2

import com.example.kpkn.domain.exercises.catalogv2.CatalogDisplayNames
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.training.ExerciseCompositionMetadata
import com.example.kpkn.domain.training.ExerciseCompositionMetadataProvider

object CatalogCompositionMetadataProvider {
    fun fromCatalog(catalog: ExerciseCatalogV2): ExerciseCompositionMetadataProvider {
        val displayNames = CatalogDisplayNames.buildDisplayNameIndex(catalog)
        val byId = catalog.families
            .asSequence()
            .flatMap { family -> family.definitions.asSequence() }
            .flatMap { definition ->
                definition.configurations.asSequence().map { configuration ->
                    val profile = configuration.profile
                    configuration.id.lowercase() to ExerciseCompositionMetadata(
                        configurationId = configuration.id,
                        displayName = displayNames[configuration.id] ?: definition.canonicalName,
                        movementPatternId = profile.movementPatternId,
                        primaryMuscles = profile.primaryMuscles,
                        secondaryMuscles = profile.secondaryMuscles,
                        axialLoadFactor = profile.axialLoadFactor,
                        replacementGroup = profile.replacementGroup,
                        laterality = profile.laterality.name,
                        performanceProfileId = profile.performanceProfileId,
                        articulationType = profile.articulationType?.name,
                    )
                }
            }
            .toMap()
        return ExerciseCompositionMetadataProvider { configurationId ->
            byId[configurationId.trim().lowercase()]
        }
    }
}
