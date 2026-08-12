package com.example.kpkn.screens.sessioneditor.components

import com.example.kpkn.data.exercises.catalogv2.toLegacySelection
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2Loader
import com.example.kpkn.domain.exercises.catalogv2.ExerciseSelectionV2
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ExerciseCatalogV2InfoTest {
    private val catalog: ExerciseCatalogV2 by lazy {
        val file = listOf(
            File("src/main/assets/exercise_catalog_v2.json"),
            File("app/src/main/assets/exercise_catalog_v2.json"),
        ).first { it.exists() }
        ExerciseCatalogV2Loader.decodeApproved(file.readText())
    }

    @Test
    fun resolves_the_exact_definition_configuration_and_involvement_for_long_press() {
        val definition = catalog.families
            .flatMap { it.definitions }
            .first { definition ->
                definition.configurations.any {
                    it.profile.jointInvolvement.isNotEmpty() && it.profile.description.isNotBlank()
                }
            }
        val configuration = definition.configurations.first {
            it.profile.jointInvolvement.isNotEmpty() && it.profile.description.isNotBlank()
        }
        val legacyInfo = catalog.toLegacySelection(
            ExerciseSelectionV2(definition.id, configuration.id, catalog.catalogRevision),
        )
        val exercise = Exercise(
            id = "exercise-occurrence",
            name = definition.canonicalName,
            catalogRevision = catalog.catalogRevision,
            catalogDefinitionId = definition.id,
            catalogConfigurationId = configuration.id,
            performanceProfileId = configuration.profile.performanceProfileId,
        )

        val resolved = resolveCatalogExerciseV2(exercise, catalog, legacyInfo)

        assertNotNull(resolved)
        assertEquals(definition.description, resolved?.definition?.description)
        assertEquals(configuration.profile.description, resolved?.configuration?.profile?.description)
        assertEquals(
            configuration.profile.jointInvolvement,
            resolved?.configuration?.profile?.jointInvolvement,
        )
        assertEquals(legacyInfo?.involvedMuscles, resolved?.legacyInfo?.involvedMuscles)
    }

    @Test
    fun rejects_a_selection_from_a_different_catalog_revision() {
        val definition = catalog.families.flatMap { it.definitions }.first()
        val configuration = definition.configurations.first()
        val exercise = Exercise(
            id = "stale-occurrence",
            name = definition.canonicalName,
            catalogRevision = "stale-revision",
            catalogDefinitionId = definition.id,
            catalogConfigurationId = configuration.id,
            performanceProfileId = configuration.profile.performanceProfileId,
        )

        assertNull(resolveCatalogExerciseV2(exercise, catalog, legacyInfo = null))
    }
}
