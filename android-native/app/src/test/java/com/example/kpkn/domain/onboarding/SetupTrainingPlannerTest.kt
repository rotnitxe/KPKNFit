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
        // T-004 added a correctly-authored native strength profile; only native
        // entries with real powerlifting metadata may enter this set.
        assertTrue(candidates.any { it.id == "native:strength-foundation-v2" })
        assertTrue(candidates.none {
            it.source == CatalogSource.NATIVE && TrainingReference.POWERLIFTING !in it.references
        })
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
        // No se sustituye la disciplina: el perfil nativo SBD está etiquetado
        // como powerlifting real; los nativos históricos de hipertrofia quedan
        // fuera. El material NO se decide en el planner (metadata gruesa
        // `general_gym`): lo decide la guardia de materialización.
        assertTrue(homeStrength.any { it.id == "native:strength-foundation-v2" })
        assertTrue(homeStrength.none {
            it.source == CatalogSource.NATIVE && TrainingReference.POWERLIFTING !in it.references
        })
        assertTrue(homeStrength.all { TrainingReference.POWERLIFTING in it.references })
    }

    @Test
    fun mixedTrainingKeepsOnlyPlansThatScheduleCardio() {
        val candidates = SetupTrainingPlanner.candidates(
            input(reference = TrainingReference.POWERLIFTING, frequency = 3, mixedTraining = true),
        )
        assertTrue(candidates.isNotEmpty())
        assertTrue(candidates.all { it.schedulesCardio })
        // C.P2b: strength-cardio ya no declara disciplina (references = ∅) y sigue siendo el candidato
        // del modo mixto, porque `schedulesCardio` no depende de las referencias.
        val strengthCardio = candidates.single { it.id == "native:strength-cardio" }
        assertTrue(strengthCardio.references.isEmpty())
    }

    @Test
    fun strengthCardioIsNoLongerAMuscleCandidateOutsideTheMixedGoal() {
        (1..6).forEach { days ->
            val muscle = SetupTrainingPlanner.candidates(
                input(reference = TrainingReference.HYPERTROPHY, frequency = days, level = CatalogLevel.BEGINNER),
            )
            assertFalse("strength-cardio no es Músculo ($days días)", muscle.any { it.id == "native:strength-cardio" })
            assertTrue("el plan propio de Músculo cubre $days días", muscle.any { it.id == "native:muscle-foundation-v2" })
        }
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
        // Ningún nativo admite 7 días: los propios cubren 1..6 y los históricos llegan a 6 como mucho.
        assertTrue(candidates.none { it.source == CatalogSource.NATIVE })
    }

    /**
     * C.P2b (D2): antes `native:one-day` entraba con 1 día. Ahora un histórico oculto no es candidato
     * ni siquiera cuando su frecuencia encaja; el plan propio cubre ese caso.
     */
    @Test
    fun hiddenHistoricalsAreNotCandidatesEvenWhenTheirFrequencyMatches() {
        val oneDay = SetupTrainingPlanner.candidates(
            input(reference = TrainingReference.HYPERTROPHY, frequency = 1, level = CatalogLevel.BEGINNER),
        )
        assertFalse(oneDay.any { it.id == "native:one-day" })
        assertTrue("el propio de Músculo cubre 1 día", oneDay.any { it.id == "native:muscle-foundation-v2" })

        val hiddenByDays = mapOf(
            "native:full-body" to (2..3),
            "native:return-training" to (2..3),
            "native:home-training" to (2..4),
            "native:gym-muscle" to (3..6),
        )
        hiddenByDays.forEach { (id, days) ->
            days.forEach { frequency ->
                // Sin referencia y con la de hipertrofia (la que antes los ofrecía).
                listOf<TrainingReference?>(null, TrainingReference.HYPERTROPHY).forEach { reference ->
                    val ids = SetupTrainingPlanner.candidates(
                        input(reference = reference, frequency = frequency, level = CatalogLevel.BEGINNER),
                    ).map { it.id }
                    assertFalse("$id no es candidato con $frequency días (ref=$reference)", id in ids)
                }
            }
        }
    }
}
