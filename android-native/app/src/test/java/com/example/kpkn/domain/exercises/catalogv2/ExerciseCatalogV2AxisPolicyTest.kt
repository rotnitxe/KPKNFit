package com.example.kpkn.domain.exercises.catalogv2

import com.example.kpkn.screens.sessioneditor.components.CatalogTestBuilders
import com.example.kpkn.screens.sessioneditor.components.catalogAxisChoicesComplete
import com.example.kpkn.screens.sessioneditor.components.catalogMissingApplicableAxes
import com.example.kpkn.screens.sessioneditor.components.exactCatalogConfigurationId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseCatalogV2AxisPolicyTest {

    @Test
    fun primaryAxesRemainRequiredWhileSecondaryAxesAreOptional() {
        assertTrue(ExerciseCatalogAxisPolicy.isRequiredAxis("implement"))
        assertTrue(ExerciseCatalogAxisPolicy.isRequiredAxis("pulley_height"))
        assertTrue(ExerciseCatalogAxisPolicy.isRequiredAxis("station"))

        assertTrue(ExerciseCatalogAxisPolicy.isOptionalAxis("grip_width"))
        assertTrue(ExerciseCatalogAxisPolicy.isOptionalAxis("grip_type"))
        assertTrue(ExerciseCatalogAxisPolicy.isOptionalAxis("laterality"))
        assertTrue(ExerciseCatalogAxisPolicy.isOptionalAxis("stance"))
        assertTrue(ExerciseCatalogAxisPolicy.isOptionalAxis("support_angle"))
    }

    @Test
    fun chestSupportedRowInMachineResolvesDefaultGripWithoutBlocking() = runTest {
        val definition = CatalogTestBuilders.realDefinition("chest_supported_row")
        val repository = InMemoryExerciseCatalogRepositoryV2(CatalogTestBuilders.real)
        repository.load()

        // Sin elegir implemento, implement sigue siendo obligatorio.
        assertEquals(listOf("implement"), catalogMissingApplicableAxes(definition, emptyMap()))
        assertNull(exactCatalogConfigurationId(definition, emptyMap()))
        assertFalse(catalogAxisChoicesComplete(definition, emptyMap()))

        // Al elegir «En máquina» sin seleccionar amplitud de agarre, resuelve con el agarre del default (medium).
        val machineOnly = mapOf("implement" to "machine")
        assertTrue(catalogMissingApplicableAxes(definition, machineOnly).isEmpty())
        assertEquals("chest_supported_row__machine__medium", exactCatalogConfigurationId(definition, machineOnly))
        assertTrue(catalogAxisChoicesComplete(definition, machineOnly))

        val compatibility = repository.compatibility(definition.id, machineOnly)
        assertEquals("chest_supported_row__machine__medium", compatibility.exactConfigurationId)
        val validation = repository.validate(
            ExerciseSelectionDraftV2(
                definitionId = definition.id,
                selectedOptions = machineOnly,
                catalogRevision = CatalogTestBuilders.real.catalogRevision,
            ),
        )
        assertTrue(validation is ExerciseSelectionValidationV2.Valid)
        assertEquals(
            "chest_supported_row__machine__medium",
            (validation as ExerciseSelectionValidationV2.Valid).selection.configurationId,
        )

        // En polea, pulley_height es obligatorio, pero grip_width es opcional.
        val cableOnly = mapOf("implement" to "cable")
        assertEquals(listOf("pulley_height"), catalogMissingApplicableAxes(definition, cableOnly))
        assertNull(exactCatalogConfigurationId(definition, cableOnly))

        val cableLowOnly = mapOf("implement" to "cable", "pulley_height" to "low")
        assertTrue(catalogMissingApplicableAxes(definition, cableLowOnly).isEmpty())
        assertEquals("chest_supported_row__cable__low__medium", exactCatalogConfigurationId(definition, cableLowOnly))
    }

    @Test
    fun romanianDeadliftAndLateralityDefinitionsResolveWithoutSecondaryAxis() {
        val rdl = CatalogTestBuilders.realDefinition("romanian_deadlift")
        assertNull(exactCatalogConfigurationId(rdl, emptyMap()))
        assertEquals(
            "romanian_deadlift__bilateral__barbell",
            exactCatalogConfigurationId(rdl, mapOf("implement" to "barbell")),
        )

        val beltSquat = CatalogTestBuilders.realDefinition("belt_squat")
        assertTrue(catalogMissingApplicableAxes(beltSquat, emptyMap()).isEmpty())
        assertEquals("belt_squat__bilateral", exactCatalogConfigurationId(beltSquat, emptyMap()))
        assertEquals(
            "belt_squat__unilateral",
            exactCatalogConfigurationId(beltSquat, mapOf("laterality" to "unilateral")),
        )
    }
}
