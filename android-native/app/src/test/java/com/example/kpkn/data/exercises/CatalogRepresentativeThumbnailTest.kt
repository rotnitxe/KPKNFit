package com.example.kpkn.data.exercises

import com.example.kpkn.domain.exercises.catalogv2.CatalogConfidenceV2
import com.example.kpkn.domain.exercises.catalogv2.CatalogEvidenceV2
import com.example.kpkn.domain.exercises.catalogv2.CatalogReviewStatusV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseBodyRegionV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseConfigurationV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseDefinitionKindV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseDefinitionV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseKineticChainV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseLateralityV2
import com.example.kpkn.domain.exercises.catalogv2.ResolvedExerciseProfileV2
import com.example.kpkn.screens.sessioneditor.components.CatalogTestBuilders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogRepresentativeThumbnailTest {

    private val evidence = CatalogEvidenceV2(
        reviewStatus = CatalogReviewStatusV2.APPROVED,
        confidence = CatalogConfidenceV2.HIGH,
        evidenceRefs = emptyList(),
    )

    private fun def(
        id: String,
        defaultConfigId: String,
        configs: List<ExerciseConfigurationV2>,
    ): ExerciseDefinitionV2 = ExerciseDefinitionV2(
        id = id,
        familyId = id,
        kind = ExerciseDefinitionKindV2.PARENT,
        canonicalName = id,
        description = "",
        defaultConfigurationId = defaultConfigId,
        optionAxes = listOf("implement"),
        configurations = configs,
        evidence = evidence,
    )

    private fun cfg(id: String, implement: String) = ExerciseConfigurationV2(
        id = id,
        selectedOptions = mapOf("implement" to implement),
        displaySummary = implement,
        profile = ResolvedExerciseProfileV2(
            movementPatternId = "horizontal_push",
            bodyRegion = ExerciseBodyRegionV2.UPPER,
            kineticChain = ExerciseKineticChainV2.ANTERIOR,
            laterality = ExerciseLateralityV2.BILATERAL,
            equipmentId = implement,
            loadMode = "EXTERNAL_WEIGHT",
            primaryMuscles = listOf("Pectorales"),
            technicalDifficulty = 2.0,
            resistanceProfile = "descending",
            setupCues = emptyList(),
            executionCues = emptyList(),
            performanceProfileId = "strength",
        ),
        evidence = evidence,
    )

    @Test
    fun unselectedDefinitionUsesDefaultConfigurationRepresentativeImage() {
        val definition = def(
            id = "bench_press",
            defaultConfigId = "bench_press__barbell",
            configs = listOf(
                cfg("bench_press__barbell", "barbell"),
                cfg("bench_press__dumbbells", "dumbbells"),
            ),
        )
        val resId = ExerciseTechniqueImageLookup.resolveRepresentativeImageResId(
            definition = definition,
            resolvedConfigurationId = null,
            selectedOptions = emptyMap(),
        )
        assertNotNull(resId)
    }

    @Test
    fun partialSelectionUpdatesThumbnailToSelectedImplementWithoutMutatingDefault() {
        val definition = def(
            id = "bench_press",
            defaultConfigId = "bench_press__barbell",
            configs = listOf(
                cfg("bench_press__barbell", "barbell"),
                cfg("bench_press__dumbbells", "dumbbells"),
            ),
        )
        val barbellRes = ExerciseTechniqueImageLookup.resolveRepresentativeImageResId(
            definition = definition,
            resolvedConfigurationId = null,
            selectedOptions = mapOf("implement" to "barbell"),
        )
        val dumbbellRes = ExerciseTechniqueImageLookup.resolveRepresentativeImageResId(
            definition = definition,
            resolvedConfigurationId = null,
            selectedOptions = mapOf("implement" to "dumbbells"),
        )
        assertNotNull(barbellRes)
        assertNotNull(dumbbellRes)
        assertEquals(false, barbellRes == dumbbellRes)
    }

    @Test
    fun tBarRowFallsBackToSiblingWithSameImplement() {
        val definition = def(
            id = "t_bar_row",
            defaultConfigId = "t_bar_row__t_bar",
            configs = listOf(
                cfg("t_bar_row__t_bar", "t_bar"),
                cfg("t_bar_row__t_bar__close", "t_bar"),
            ),
        )
        val resId = ExerciseTechniqueImageLookup.resolveRepresentativeImageResId(
            definition = definition,
            resolvedConfigurationId = null,
            selectedOptions = emptyMap(),
        )
        assertEquals(
            ReviewedExerciseTechniqueImages.strengthImage("t_bar_row__t_bar__close"),
            resId,
        )
    }

    @Test
    fun all228RealCatalogDefinitionsResolveRepresentativeImageWithoutSelection() {
        val definitions = CatalogTestBuilders.real.families.flatMap { it.definitions }
        assertEquals(228, definitions.size)
        val missing = definitions.filter { definition ->
            ExerciseTechniqueImageLookup.resolveRepresentativeImageResId(
                definition = definition,
                resolvedConfigurationId = null,
                selectedOptions = emptyMap(),
            ) == null
        }
        assertTrue("Expected 0 missing representative thumbnails across 228 exercises, found: ${missing.map { it.id }}", missing.isEmpty())
    }

    @Test
    fun partialSelectionWithUnpicturedImplementReturnsNullInsteadOfWrongImplementFallback() {
        val definition = def(
            id = "bench_press",
            defaultConfigId = "bench_press__barbell",
            configs = listOf(
                cfg("bench_press__barbell", "barbell"),
                cfg("bench_press__band", "band"),
            ),
        )
        val unpicturedRes = ExerciseTechniqueImageLookup.resolveRepresentativeImageResId(
            definition = definition,
            resolvedConfigurationId = null,
            selectedOptions = mapOf("implement" to "band"),
        )
        assertNull(unpicturedRes)
    }

    @Test
    fun standingHipAbductionAndAdductionDoNotLeakSeatedMachineThumbnail() {
        listOf("hip_abduction", "hip_adduction").forEach { defId ->
            val definition = CatalogTestBuilders.realDefinition(defId)
            val standingUnilateral = definition.configurations.firstOrNull {
                it.selectedOptions["station"] == "standing" &&
                    it.selectedOptions["implement"] == "machine" &&
                    it.selectedOptions["laterality"] == "unilateral"
            } ?: error("Missing standing unilateral machine config for $defId")
            val standingBilateral = definition.configurations.firstOrNull {
                it.selectedOptions["station"] == "standing" &&
                    it.selectedOptions["implement"] == "machine" &&
                    it.selectedOptions["laterality"] == "bilateral"
            } ?: error("Missing standing bilateral machine config for $defId")
            val seatedConfig = definition.configurations.firstOrNull {
                it.selectedOptions["station"] == "seated" &&
                    it.selectedOptions["implement"] == "machine"
            } ?: error("Missing seated machine config for $defId")

            val seatedImage = ExerciseTechniqueImageLookup.resolveRepresentativeImageResId(
                definition = definition,
                resolvedConfigurationId = seatedConfig.id,
                selectedOptions = seatedConfig.selectedOptions,
            )
            val standingBilateralImage = ExerciseTechniqueImageLookup.resolveRepresentativeImageResId(
                definition = definition,
                resolvedConfigurationId = standingBilateral.id,
                selectedOptions = standingBilateral.selectedOptions,
            )
            val standingUnilateralImage = ExerciseTechniqueImageLookup.resolveRepresentativeImageResId(
                definition = definition,
                resolvedConfigurationId = standingUnilateral.id,
                selectedOptions = standingUnilateral.selectedOptions,
            )
            val standingPartialImage = ExerciseTechniqueImageLookup.resolveRepresentativeImageResId(
                definition = definition,
                resolvedConfigurationId = null,
                selectedOptions = mapOf("station" to "standing", "implement" to "machine"),
            )

            assertNotNull(seatedImage)
            assertNotNull(standingBilateralImage)
            assertEquals(standingBilateralImage, standingUnilateralImage)
            assertEquals(standingBilateralImage, standingPartialImage)
            assertEquals(false, seatedImage == standingUnilateralImage)
            assertEquals(false, seatedImage == standingPartialImage)
        }
    }
}
