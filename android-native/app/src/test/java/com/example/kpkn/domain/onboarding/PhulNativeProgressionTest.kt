package com.example.kpkn.domain.onboarding

import com.example.kpkn.data.models.NativeProgressionProposalKind
import com.example.kpkn.data.models.NativeProgressionResolutionStatus
import com.example.kpkn.data.protocols.NativeProgressionStrategy
import com.example.kpkn.data.protocols.definitions.AuthoredPhulPhatRecipes
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.NativeProgressionTestSupport.completedLog
import com.example.kpkn.domain.training.NativeProgressionTestSupport.managedOf
import com.example.kpkn.domain.training.NativeProgressionTestSupport.sessionOf
import com.example.kpkn.domain.training.NativeWorkoutProgressionRuntime
import com.example.kpkn.domain.training.PlanMaterializer
import com.example.kpkn.domain.training.ProgramProgressEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * R16 (decisión del dueño): PHUL, original y adaptado, propone subidas de carga con la doble
 * progresión KPKN rotulada «Recomendación KPKN §12.4». PHAT no la lleva por ahora.
 */
class PhulNativeProgressionTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }

        private val PHUL_SET_COUNTS = listOf(18, 16, 21, 18)
    }

    @Test
    fun phulOriginalAndAdaptedCarryTheKpknDoubleProgressionAndPhatDoesNot() {
        listOf(AuthoredPhulPhatRecipes.phulOriginal, AuthoredPhulPhatRecipes.phulAdapted).forEach { recipe ->
            val spec = requireNotNull(recipe.nativeProgression) { recipe.id }
            assertEquals(NativeProgressionStrategy.REP_RANGE_THEN_LOAD, spec.strategy)
            assertEquals(2, spec.exposuresBeforeProposal)
            assertTrue(spec.note, spec.note.startsWith("Recomendación KPKN §12.4"))
        }
        assertNull(AuthoredPhulPhatRecipes.phatOriginal.nativeProgression)
        assertNull(AuthoredPhulPhatRecipes.phatAdapted.nativeProgression)
    }

    @Test
    fun phulKeepsItsPublishedSetsAndOnlyItsMainSlotsBecomeLoadManaged() {
        val program = AuthoredPlanFixtures.prepare(AuthoredPhulPhatRecipes.PHUL_ORIGINAL_ID, AuthoredPlanFixtures.fullGym)

        assertEquals("las series publicadas no cambian", PHUL_SET_COUNTS, AuthoredPlanFixtures.firstWeekSetCounts(program))
        assertEquals(12, AuthoredPlanFixtures.weeksOf(program).size)
        val managed = managedOf(program, sessionOf(program, 1, 1).id)
        assertTrue("el día 1 tiene ejercicios con carga gestionada", managed.isNotEmpty())
    }

    @Test
    fun twoTopRangeSessionsOfPhulProposeALoadIncrease() {
        val program = AuthoredPlanFixtures.prepare(AuthoredPhulPhatRecipes.PHUL_ORIGINAL_ID, AuthoredPlanFixtures.fullGym)
        val first = completedLog(program, sessionOf(program, 1, 1).id, "phul-1", dayOffset = 1)
        val second = completedLog(program, sessionOf(program, 2, 1).id, "phul-2", dayOffset = 8)

        val observed = NativeWorkoutProgressionRuntime.observeCompletedWorkout(
            program = program,
            logs = listOf(first, second),
            inventory = null,
            curatedConfigurations = emptySet(),
            completedLogId = second.id,
            nowMs = 1_000L,
        )

        assertTrue("PHUL propone al menos una subida", observed.nativeProgressionProposals.isNotEmpty())
        assertTrue(observed.nativeProgressionProposals.all { it.kind == NativeProgressionProposalKind.INCREASE_LOAD })
        assertTrue(observed.nativeProgressionProposals.all { it.identity.slotPurpose in setOf("F", "H", "I") })
    }

    @Test
    fun phulCycleCloseTalksAboutItsRealTwelveWeeksAndGivesOneNewBlockNotice() {
        val program = AuthoredPlanFixtures.prepare(AuthoredPhulPhatRecipes.PHUL_ORIGINAL_ID, AuthoredPlanFixtures.fullGym)

        val continued = ProgramProgressEngine.registerNativeContinuationOnce(
            program = program,
            cycleNumber = 2,
            weekOccurrence = 1,
            nowMs = 5_000L,
            logs = emptyList(),
        )

        val summaries = continued.effectiveWeekRecipes.flatMap { it.appliedProposals }.map { it.summary }
        assertTrue(summaries.toString(), summaries.any { it.startsWith("Continuación de 12 semanas") })
        assertFalse(summaries.toString(), summaries.any { it.contains("6 semanas") })
        val notices = continued.nativeProgressionAudit.filter { it.userFacingNotice }
        assertEquals(1, notices.size)
        assertEquals(NativeProgressionResolutionStatus.NOTICE, notices.single().status)
        assertEquals("Empiezas un nuevo bloque de 12 semanas con tus últimas cargas.", notices.single().reason)
        // Idempotente: repetir el cierre no suma un segundo aviso.
        val again = ProgramProgressEngine.registerNativeContinuationOnce(continued, 2, 1, 6_000L, emptyList())
        assertEquals(1, again.nativeProgressionAudit.count { it.userFacingNotice })
        assertTrue(PlanMaterializer.effectiveWeekRecipeFor(again, 1, 2) != null)
    }
}
