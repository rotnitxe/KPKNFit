package com.example.kpkn.domain.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityRulesTest {

    private val gym = EquipmentSymbols.seedFor(setOf(TrainingPlace.GYM))
    private val light = setOf(EquipmentSymbolId.DUMBBELLS, EquipmentSymbolId.BANDS)

    @Test
    fun asksForGeneralProfilesAndCalisthenicsWhenTheMaterialIsLight() {
        for (profile in TrainingGoalProfile.general + TrainingGoalProfile.CALISTHENICS) {
            assertTrue("$profile", CapabilityRules.asks(profile, novice = false, symbols = light))
        }
    }

    @Test
    fun aNoviceIsAskedEvenWithAFullGym() {
        for (profile in TrainingGoalProfile.general + TrainingGoalProfile.CALISTHENICS) {
            assertTrue("$profile", CapabilityRules.asks(profile, novice = true, symbols = gym))
        }
    }

    @Test
    fun notAskedWhenBarbellWithRackOrMachinesAlreadyCoverTheWork() {
        val general = TrainingGoalProfile.STRENGTH_MUSCLE
        assertFalse(CapabilityRules.asks(general, novice = false, symbols = gym))
        assertFalse(CapabilityRules.asks(general, novice = false, symbols = setOf(EquipmentSymbolId.MACHINES)))
        assertFalse(
            CapabilityRules.asks(
                general, novice = false, symbols = setOf(EquipmentSymbolId.BARBELL, EquipmentSymbolId.RACK),
            ),
        )
        // Barra sin rack no cuenta como material pesado.
        assertTrue(CapabilityRules.asks(general, novice = false, symbols = setOf(EquipmentSymbolId.BARBELL)))
    }

    @Test
    fun neverAskedForWeightDisciplinesOrWithoutAGoal() {
        for (profile in TrainingGoalProfile.specific - TrainingGoalProfile.CALISTHENICS) {
            assertFalse("$profile", CapabilityRules.asks(profile, novice = true, symbols = emptySet()))
        }
        assertFalse(CapabilityRules.asks(null, novice = true, symbols = emptySet()))
    }

    @Test
    fun lightMaterialMeansNeitherBarbellWithRackNorMachines() {
        assertTrue(CapabilityRules.isLightMaterial(emptySet()))
        assertTrue(CapabilityRules.isLightMaterial(light))
        assertFalse(CapabilityRules.isLightMaterial(setOf(EquipmentSymbolId.MACHINES)))
        assertFalse(CapabilityRules.isLightMaterial(setOf(EquipmentSymbolId.BARBELL, EquipmentSymbolId.RACK)))
    }

    @Test
    fun skillsDependOnTheMaterial() {
        // Sin material solo se ofrecen los que no piden nada.
        assertEquals(
            listOf(CapabilitySkill.PUSH_UP, CapabilitySkill.PISTOL_SQUAT),
            CapabilityRules.skillsFor(emptySet()),
        )
        // Las dominadas solo con barra de dominadas; los fondos con paralelas o con banco.
        assertEquals(
            listOf(CapabilitySkill.PULL_UP, CapabilitySkill.PUSH_UP, CapabilitySkill.PISTOL_SQUAT),
            CapabilityRules.skillsFor(setOf(EquipmentSymbolId.PULL_UP_BAR)),
        )
        assertEquals(
            listOf(CapabilitySkill.PUSH_UP, CapabilitySkill.DIP, CapabilitySkill.PISTOL_SQUAT),
            CapabilityRules.skillsFor(setOf(EquipmentSymbolId.PARALLEL_BARS)),
        )
        assertEquals(
            listOf(CapabilitySkill.PUSH_UP, CapabilitySkill.DIP, CapabilitySkill.PISTOL_SQUAT),
            CapabilityRules.skillsFor(setOf(EquipmentSymbolId.BENCH)),
        )
        assertEquals(CapabilitySkill.entries, CapabilityRules.skillsFor(gym))
    }

    @Test
    fun thereAreAlwaysBetweenTwoAndFourSkillsToAsk() {
        for (symbols in listOf(emptySet(), light, gym)) {
            assertTrue(CapabilityRules.skillsFor(symbols).size in 2..4)
        }
    }
}
