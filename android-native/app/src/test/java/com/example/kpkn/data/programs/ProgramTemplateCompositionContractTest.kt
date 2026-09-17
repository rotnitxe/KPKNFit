package com.example.kpkn.data.programs

import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.ProgramRecipeValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

class ProgramTemplateCompositionContractTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            CatalogCompositionTestSupport.install()
        }
    }

    @Test
    fun every_complex_visible_template_has_recipe_without_exemptions() {
        PROGRAM_TEMPLATES.filter { it.type == com.example.kpkn.data.models.ProgramStructure.COMPLEX }.forEach { template ->
            assertTrue("${template.id} necesita receta", template.recipe != null && template.recipe!!.weeks.isNotEmpty())
            assertTrue("${template.id} cero exenciones", template.recipe!!.exemptions.isEmpty())
            val hard = ProgramRecipeValidator.hardFindings(template.recipe!!, CatalogCompositionTestSupport.metadata)
            assertTrue("${template.id}:\n" + hard.joinToString("\n") { "${it.rule} ${it.scope}: ${it.message}" }, hard.isEmpty())
        }
    }

    @Test
    fun example_a_b_c_are_literal_cases() {
        val squat = com.example.kpkn.data.protocols.DayArchetypes.plSquat(80.0)
        assertEquals(CatalogIds.SQ_LOW, squat.slots[0].lift.configurationId)
        val work = squat.slots[0].sets.filter { !it.isWarmup }
        val warmups = squat.slots[0].sets.filter { it.isWarmup }
        assertEquals(listOf(40.0, 55.0, 65.0), warmups.map { it.percent })
        assertEquals(listOf(5, 3, 1), warmups.map { it.reps })
        assertEquals(4, work.size)
        assertTrue(work.all { it.percent == 80.0 && it.reps == 4 })
        assertEquals(240, squat.slots[0].restSeconds)
        assertEquals(CatalogIds.DL_DEF, squat.slots[1].lift.configurationId)
        assertEquals(listOf(5, 5, 5), squat.slots[1].sets.map { it.reps })
        assertTrue(squat.slots[1].sets.all { it.percent == 65.0 })
        assertEquals(180, squat.slots[1].restSeconds)
        assertEquals(CatalogIds.BP_PAUSE, squat.slots[2].lift.configurationId)
        assertEquals(null, squat.slots[2].technique)
        assertTrue(squat.slots[2].sets.all { it.percent == 70.0 && it.reps == 6 })
        assertEquals(150, squat.slots[2].restSeconds)
        assertEquals(CatalogIds.GHR, squat.slots[3].lift.configurationId)
        assertEquals(CatalogIds.PALLOF, squat.slots[4].lift.configurationId)

        val bench = com.example.kpkn.data.protocols.DayArchetypes.plBenchHeavy(80.0)
        assertEquals(CatalogIds.BP, bench.slots[0].lift.configurationId)
        assertEquals(6, bench.slots.size)
        assertEquals(CatalogIds.PENDLAY, bench.slots[2].lift.configurationId)
        assertEquals(4, bench.slots[2].sets.size)
        assertEquals(CatalogIds.PULLUP, bench.slots[3].lift.configurationId)
        assertEquals(CatalogIds.JM, bench.slots[4].lift.configurationId)
        assertEquals(CatalogIds.FACE, bench.slots[5].lift.configurationId)

        val legs = com.example.kpkn.data.protocols.DayArchetypes.bbLegs()
        assertEquals(CatalogIds.SQ_HIGH, legs.slots[0].lift.configurationId)
        assertEquals(6, legs.slots.size)
        assertEquals(180, legs.slots[0].restSeconds)
        assertEquals(CatalogIds.RDL, legs.slots[1].lift.configurationId)
        assertEquals(CatalogIds.PRESS_LEG, legs.slots[2].lift.configurationId)
        assertEquals(CatalogIds.CURL_H, legs.slots[3].lift.configurationId)
        assertEquals(CatalogIds.LEG_EXT, legs.slots[4].lift.configurationId)
        assertEquals(CatalogIds.CALF, legs.slots[5].lift.configurationId)
    }
}
