package com.example.kpkn.data.exercises.catalogv2

import com.example.kpkn.domain.exercises.catalogv2.CatalogDisplayNames
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.exercises.catalogv2.JointInvolvementV2
import com.example.kpkn.domain.exercises.catalogv2.JointRoleV2
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
                        equipmentId = profile.equipmentId,
                        principalJoints = principalJointsOf(profile.jointInvolvement),
                    )
                }
            }
            .toMap()
        return ExerciseCompositionMetadataProvider { configurationId ->
            byId[configurationId.trim().lowercase()]
        }
    }

    /** Articulaciones PRIMARY y luego SECONDARY de la configuración (sin repetir), sin los estabilizadores. */
    private fun principalJointsOf(involvement: List<JointInvolvementV2>): List<String> =
        (involvement.filter { it.role == JointRoleV2.PRIMARY } + involvement.filter { it.role == JointRoleV2.SECONDARY })
            .map { it.jointId }
            .distinct()
}
