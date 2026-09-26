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
    fun fixedRecipesAreNotHiddenBehindCoarseGeneralGym() {
        // `general_gym` es metadata gruesa de la receta fija: no autoriza nada
        // por sí sola ni bloquea con inventario finito. El planner ya NO oculta
        // plantillas/protocolos por esa clave: el material real se verifica en
        // la materialización con `missingFixedRecipeEquipment` (antes del preview).
        val finiteMaterial = SetupTrainingPlanner.candidates(
            input(reference = null, frequency = 3, equipment = setOf("barbell", "bodyweight")),
        )
        assertTrue(finiteMaterial.any { it.source == CatalogSource.PROTOCOL || it.source == CatalogSource.TEMPLATE })

        val legacyGym = SetupTrainingPlanner.candidates(
            input(reference = null, frequency = 3, equipment = setOf("general_gym", "bodyweight")),
        )
        assertTrue(legacyGym.any { it.source == CatalogSource.PROTOCOL || it.source == CatalogSource.TEMPLATE })
    }

    @Test
    fun machineOnlyUsersStillSeeNativeFamilyAndFixedRecipesReachTheGuard() {
        val candidates = SetupTrainingPlanner.candidates(
            input(reference = null, frequency = 3, equipment = setOf("machine")),
        )
        assertTrue(candidates.any { it.source == CatalogSource.NATIVE })
        // Las recetas fijas tampoco se ocultan aquí: su material se comprueba en
        // la guardia de materialización (una máquina genérica no atestigua toda
        // la maquinaria ni se afirma compatible en el planner).
        assertTrue(candidates.any { it.source == CatalogSource.PROTOCOL || it.source == CatalogSource.TEMPLATE })
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
    fun disciplineMismatchNeverSubstitutesAnotherDisciplineAndMaterialIsCheckedAtGuard() {
        val homeStrength = SetupTrainingPlanner.candidates(
            input(reference = TrainingReference.POWERLIFTING, frequency = 3, equipment = setOf("bodyweight", "band")),
        )
        // La disciplina nunca se sustituye: solo powerlifting real (el nativo es
        // de hipertrofia y queda fuera). El material NO se decide en el planner
        // (metadata gruesa `general_gym`): lo decide la guardia de materialización
        // `missingFixedRecipeEquipment`, que con {bodyweight, band} rechaza una
        // receta que exige barra/cable/máquina.
        assertTrue(homeStrength.none { it.source == CatalogSource.NATIVE })
        assertTrue(homeStrength.all { TrainingReference.POWERLIFTING in it.references })
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
