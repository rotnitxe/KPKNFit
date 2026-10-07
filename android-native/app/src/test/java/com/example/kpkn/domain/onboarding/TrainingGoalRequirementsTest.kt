package com.example.kpkn.domain.onboarding

import com.example.kpkn.data.models.EquipmentAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingGoalRequirementsTest {

    private fun ok(profile: TrainingGoalProfile, vararg symbols: EquipmentSymbolId) =
        TrainingGoalRequirements.isCompatible(profile, symbols.toSet())

    @Test
    fun generalProfilesAreCompatibleWithAnyMaterialIncludingNone() {
        for (profile in TrainingGoalProfile.general) {
            assertTrue(ok(profile))
            assertTrue(ok(profile, EquipmentSymbolId.BODYWEIGHT_ONLY))
            assertTrue(ok(profile, EquipmentSymbolId.BANDS))
            assertNull(TrainingGoalRequirements.missingText(profile, emptySet()))
            assertNull(TrainingGoalRequirements.requirementText(profile))
        }
    }

    @Test
    fun powerliftingNeedsBarbellRackAndBench() {
        val p = TrainingGoalProfile.POWERLIFTING
        assertTrue(ok(p, EquipmentSymbolId.BARBELL, EquipmentSymbolId.RACK, EquipmentSymbolId.BENCH))
        assertFalse(ok(p, EquipmentSymbolId.BARBELL, EquipmentSymbolId.RACK))
        assertFalse(ok(p, EquipmentSymbolId.BARBELL, EquipmentSymbolId.BENCH))
        assertFalse(ok(p, EquipmentSymbolId.RACK, EquipmentSymbolId.BENCH))
        assertFalse(ok(p, EquipmentSymbolId.DUMBBELLS, EquipmentSymbolId.MACHINES))
        assertEquals("Necesita barra, rack y banco.", TrainingGoalRequirements.missingText(p, emptySet()))
    }

    @Test
    fun powerbuildingAcceptsTheBarbellSetOrJustDumbbells() {
        val p = TrainingGoalProfile.POWERBUILDING
        assertTrue(ok(p, EquipmentSymbolId.BARBELL, EquipmentSymbolId.RACK, EquipmentSymbolId.BENCH))
        assertTrue(ok(p, EquipmentSymbolId.DUMBBELLS))
        assertFalse(ok(p, EquipmentSymbolId.BARBELL, EquipmentSymbolId.RACK))
        assertFalse(ok(p, EquipmentSymbolId.MACHINES))
        assertEquals("Necesita barra, rack y banco, o mancuernas.", TrainingGoalRequirements.missingText(p, emptySet()))
    }

    @Test
    fun bodybuildingNeedsAtLeastOneLoadableImplement() {
        val p = TrainingGoalProfile.BODYBUILDING
        for (symbol in listOf(
            EquipmentSymbolId.BARBELL, EquipmentSymbolId.DUMBBELLS, EquipmentSymbolId.MACHINES,
            EquipmentSymbolId.CABLE, EquipmentSymbolId.SMITH,
        )) {
            assertTrue("$symbol", ok(p, symbol))
        }
        assertFalse(ok(p))
        assertFalse(ok(p, EquipmentSymbolId.BANDS, EquipmentSymbolId.PULL_UP_BAR, EquipmentSymbolId.KETTLEBELL))
        assertEquals("Necesita mancuernas, barra, poleas o máquinas.", TrainingGoalRequirements.missingText(p, emptySet()))
    }

    @Test
    fun calisthenicsNeedsAPullUpBarOrRings() {
        val p = TrainingGoalProfile.CALISTHENICS
        assertTrue(ok(p, EquipmentSymbolId.PULL_UP_BAR))
        assertTrue(ok(p, EquipmentSymbolId.RINGS))
        assertFalse(ok(p, EquipmentSymbolId.PARALLEL_BARS, EquipmentSymbolId.BANDS))
        assertEquals("Necesita barra de dominadas o anillas.", TrainingGoalRequirements.missingText(p, emptySet()))
    }

    @Test
    fun weightliftingNeedsBarbellAndRack() {
        val p = TrainingGoalProfile.WEIGHTLIFTING
        assertTrue(ok(p, EquipmentSymbolId.BARBELL, EquipmentSymbolId.RACK))
        assertFalse(ok(p, EquipmentSymbolId.BARBELL))
        assertFalse(ok(p, EquipmentSymbolId.RACK, EquipmentSymbolId.BENCH))
        assertEquals("Necesita barra y rack.", TrainingGoalRequirements.missingText(p, emptySet()))
    }

    @Test
    fun strongmanNeedsBarbellAndEitherDumbbellsOrKettlebell() {
        val p = TrainingGoalProfile.STRONGMAN
        assertTrue(ok(p, EquipmentSymbolId.BARBELL, EquipmentSymbolId.DUMBBELLS))
        assertTrue(ok(p, EquipmentSymbolId.BARBELL, EquipmentSymbolId.KETTLEBELL))
        assertFalse(ok(p, EquipmentSymbolId.BARBELL))
        assertFalse(ok(p, EquipmentSymbolId.DUMBBELLS, EquipmentSymbolId.KETTLEBELL))
        assertEquals("Necesita barra y mancuernas o kettlebell.", TrainingGoalRequirements.missingText(p, emptySet()))
    }

    @Test
    fun armwrestlingNeedsAnyOfTheGripAndPullImplements() {
        val p = TrainingGoalProfile.ARMWRESTLING
        for (symbol in listOf(
            EquipmentSymbolId.DUMBBELLS, EquipmentSymbolId.CABLE, EquipmentSymbolId.BANDS,
            EquipmentSymbolId.BARBELL, EquipmentSymbolId.KETTLEBELL,
        )) {
            assertTrue("$symbol", ok(p, symbol))
        }
        assertFalse(ok(p, EquipmentSymbolId.PULL_UP_BAR, EquipmentSymbolId.MACHINES))
        assertEquals(
            "Necesita mancuernas, poleas, bandas, barra o kettlebell.",
            TrainingGoalRequirements.missingText(p, emptySet()),
        )
    }

    @Test
    fun everySpecificProfileHasAOneLineReasonAndNoGeneralOneDoes() {
        for (profile in TrainingGoalProfile.specific) {
            val text = TrainingGoalRequirements.requirementText(profile)
            assertNotNull("$profile", text)
            assertTrue("$profile: una sola línea y sin culpar", text!!.startsWith("Necesita ") && '\n' !in text)
        }
        for (profile in TrainingGoalProfile.general) assertNull(TrainingGoalRequirements.requirementText(profile))
    }

    @Test
    fun missingTextIsNullExactlyWhenCompatible() {
        for (profile in TrainingGoalProfile.entries) {
            for (symbols in listOf(
                emptySet(),
                setOf(EquipmentSymbolId.BODYWEIGHT_ONLY),
                setOf(EquipmentSymbolId.BARBELL, EquipmentSymbolId.RACK, EquipmentSymbolId.BENCH),
                EquipmentSymbols.seedFor(setOf(TrainingPlace.GYM)),
                EquipmentSymbols.seedFor(setOf(TrainingPlace.PUBLIC)),
            )) {
                val compatible = TrainingGoalRequirements.isCompatible(profile, symbols)
                assertEquals("$profile / $symbols", compatible, TrainingGoalRequirements.missingText(profile, symbols) == null)
            }
        }
    }

    @Test
    fun readsTheDeclaredAvailabilityThroughTheSymbols() {
        val gym = EquipmentSymbols.availabilityOf(
            EquipmentSymbols.seedFor(setOf(TrainingPlace.GYM)),
            setOf(TrainingPlace.GYM),
        )
        // Con el gimnasio completo todas las disciplinas encajan.
        for (profile in TrainingGoalProfile.entries) {
            assertTrue("$profile", TrainingGoalRequirements.isCompatible(profile, gym))
        }
        val park = EquipmentSymbols.availabilityOf(
            EquipmentSymbols.seedFor(setOf(TrainingPlace.PUBLIC)),
            setOf(TrainingPlace.PUBLIC),
        )
        assertTrue(TrainingGoalRequirements.isCompatible(TrainingGoalProfile.CALISTHENICS, park))
        assertFalse(TrainingGoalRequirements.isCompatible(TrainingGoalProfile.POWERLIFTING, park))
        // Sin material declarado no se asume nada: las disciplinas no encajan y los generales sí.
        assertFalse(TrainingGoalRequirements.isCompatible(TrainingGoalProfile.BODYBUILDING, null as EquipmentAvailability?))
        assertTrue(TrainingGoalRequirements.isCompatible(TrainingGoalProfile.FUNCTIONAL_HEALTH, null as EquipmentAvailability?))
    }

    @Test
    fun compatibleProfilesFollowTheContractOrder() {
        val none = TrainingGoalRequirements.compatibleProfiles(emptySet())
        assertEquals(TrainingGoalProfile.general, none)
        val all = TrainingGoalRequirements.compatibleProfiles(EquipmentSymbols.seedFor(setOf(TrainingPlace.GYM)))
        assertEquals(TrainingGoalProfile.entries, all)
    }
}
