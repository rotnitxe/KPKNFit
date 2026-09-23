package com.example.kpkn.domain.onboarding

import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.data.programs.CatalogSource
import com.example.kpkn.data.programs.TrainingFocus
import com.example.kpkn.data.programs.TrainingReference
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupTrainingPlannerTest {
    private fun input(
        reference: TrainingReference? = TrainingReference.POWERLIFTING,
        frequency: Int? = 4,
        equipment: Set<String> = setOf("general_gym"),
        level: CatalogLevel = CatalogLevel.INTERMEDIATE,
        focus: TrainingFocus = TrainingFocus.FULL_BODY,
        protocolOnly: Boolean = false,
        mixedTraining: Boolean = false,
    ) = SetupTrainingPlannerInput(reference, frequency, equipment, level, focus, protocolOnly, mixedTraining)

    @Test
    fun fixedRecipesRequireTheirDeclaredEquipment() {
        val withoutGym = SetupTrainingPlanner.candidates(
            input(reference = null, frequency = 3, equipment = setOf("bodyweight")),
        )
        assertTrue(withoutGym.none { it.source == CatalogSource.PROTOCOL })
        assertTrue(withoutGym.none { it.source == CatalogSource.TEMPLATE })

        val withGym = SetupTrainingPlanner.candidates(
            input(reference = null, frequency = 3, equipment = setOf("general_gym", "bodyweight")),
        )
        assertTrue(withGym.any { it.source == CatalogSource.PROTOCOL || it.source == CatalogSource.TEMPLATE })
    }

    @Test
    fun machineOnlyUsersStillSeeNativeFamily() {
        val candidates = SetupTrainingPlanner.candidates(
            input(reference = null, frequency = 3, equipment = setOf("machine")),
        )
        assertTrue(candidates.any { it.source == CatalogSource.NATIVE })
        assertTrue(candidates.none { it.source == CatalogSource.PROTOCOL })
    }

    @Test
    fun strengthReferenceKeepsOnlyRealPowerliftingDiscipline() {
        val candidates = SetupTrainingPlanner.candidates(input(reference = TrainingReference.POWERLIFTING))
        assertTrue(candidates.isNotEmpty())
        assertTrue(candidates.all { TrainingReference.POWERLIFTING in it.references })
        assertTrue(candidates.none { it.source == CatalogSource.NATIVE })
        assertTrue(candidates.none { it.id == "template:body-12-3" })
        assertTrue(candidates.any { it.id == "template:power-16-4" || it.source == CatalogSource.PROTOCOL })
    }

    @Test
    fun muscleReferenceKeepsHypertrophyAndNativeCycles() {
        val candidates = SetupTrainingPlanner.candidates(
            input(reference = TrainingReference.HYPERTROPHY, frequency = 3),
        )
        assertTrue(candidates.any { it.source == CatalogSource.NATIVE })
        assertTrue(candidates.all { TrainingReference.HYPERTROPHY in it.references })
        assertTrue(candidates.none { it.id == "template:power-12-3" })
    }

    @Test
    fun powerbuildingReferenceMatchesRealPowerbuildingMetadata() {
        val candidates = SetupTrainingPlanner.candidates(input(reference = TrainingReference.POWERBUILDING))
        assertTrue(candidates.isNotEmpty())
        assertTrue(candidates.all { TrainingReference.POWERBUILDING in it.references })
        assertTrue(candidates.any { it.id == "template:powerbuild-16-4" })
    }

    @Test
    fun incompatibleCombinationIsEmptyInsteadOfSubstitutingAnotherDiscipline() {
        val homeStrength = SetupTrainingPlanner.candidates(
            input(reference = TrainingReference.POWERLIFTING, frequency = 3, equipment = setOf("bodyweight", "band")),
        )
        assertTrue(homeStrength.isEmpty())
    }

    @Test
    fun mixedTrainingKeepsOnlyPlansThatScheduleCardio() {
        val candidates = SetupTrainingPlanner.candidates(
            input(reference = TrainingReference.POWERLIFTING, frequency = 3, mixedTraining = true),
        )
        assertTrue(candidates.isNotEmpty())
        assertTrue(candidates.all { it.schedulesCardio })
    }

    @Test
    fun protocolOnlyFiltersToProtocols() {
        val candidates = SetupTrainingPlanner.candidates(input(protocolOnly = true))
        assertTrue(candidates.isNotEmpty())
        assertTrue(candidates.all { it.source == CatalogSource.PROTOCOL })
    }

    @Test
    fun unsupportedFrequencyIsExcluded() {
        val candidates = SetupTrainingPlanner.candidates(
            input(reference = null, frequency = 7, equipment = setOf("general_gym")),
        )
        assertFalse(candidates.any { it.id == "native:one-day" })
    }
}
