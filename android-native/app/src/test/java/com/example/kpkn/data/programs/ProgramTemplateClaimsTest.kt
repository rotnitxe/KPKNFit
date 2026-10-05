package com.example.kpkn.data.programs

import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.SlotRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramTemplateClaimsTest {
    @Test
    fun name_and_weeks_match_recipe() {
        PROGRAM_TEMPLATES.filter { it.type == ProgramStructure.COMPLEX }.forEach { template ->
            val recipe = template.recipe!!
            assertTrue("${template.id} semanas", recipe.weeks.size == template.weeks)
            val blob = (template.name + " " + template.description).lowercase()
            val dayClaim = Regex("""(\d+)\s*días""").find(blob)
            if (dayClaim != null) {
                assertTrue(
                    "${template.id} días ${dayClaim.groupValues[1]} vs ${recipe.daysPerWeek}",
                    dayClaim.groupValues[1].toInt() == recipe.daysPerWeek,
                )
            }
            assertTrue("${template.id} bloques vs receta", recipe.weeks.map { it.blockIndex }.distinct().size == template.blockNames.size)
            template.blockNames.forEachIndexed { index, name ->
                val weeks = recipe.weeks.filter { it.blockIndex == index }
                assertTrue("${template.id} bloque $index sin semanas", weeks.isNotEmpty())
                assertTrue("${template.id} bloque $index sin nombre", name.isNotBlank())
                weeks.forEach { week ->
                    assertEquals(
                        "${template.id} bloque $index semana ${week.weekNumber}: nombre de plantilla y receta",
                        name,
                        week.blockName,
                    )
                }
            }
            if (template.blockWeekCounts.isNotEmpty()) {
                template.blockWeekCounts.forEachIndexed { index, count ->
                    val actual = recipe.weeks.count { it.blockIndex == index }
                    assertTrue("${template.id} bloque $index semanas $actual vs $count", actual == count)
                }
            }
            if (template.blockGoalSemantics.isNotEmpty()) {
                val goals = recipe.weeks.groupBy { it.blockIndex }.toSortedMap().map { it.value.first().blockGoal }
                assertTrue("${template.id} blockGoalSemantics $goals vs ${template.blockGoalSemantics}", goals == template.blockGoalSemantics)
            }
        }
    }

    @Test
    fun explicit_block_semantics_define_the_composition_contract() {
        PROGRAM_TEMPLATES.filter { it.type == ProgramStructure.COMPLEX && it.recipe != null }.forEach { template ->
            val recipe = template.recipe!!
            template.blockNames.forEachIndexed { index, name ->
                val weeks = recipe.weeks.filter { it.blockIndex == index }
                val goal = weeks.first().blockGoal
                // Editorial names can change without changing the executable phase.
                // Legacy templates without explicit semantics keep their name contract.
                val expectedGoal = template.blockGoalSemantics.getOrNull(index) ?: when {
                    name.equals("Peak", ignoreCase = true) || name.equals("Pico", ignoreCase = true) -> BlockGoal.PEAK
                    name.contains("Taper", ignoreCase = true) -> BlockGoal.TAPER
                    name.equals("Definición", ignoreCase = true) -> BlockGoal.DENSITY
                    else -> null
                }
                if (expectedGoal != null) {
                    assertEquals("${template.id} bloque $index '$name': semántica $expectedGoal, receta $goal", expectedGoal, goal)
                }
                when (expectedGoal) {
                    BlockGoal.TAPER -> {
                        val last = weeks.maxBy { it.weekNumber }
                        val t1Percents = last.days.flatMap { day ->
                            day.slots.filter { it.role == SlotRole.T1_MAIN }.flatMap { slot ->
                                slot.sets.filter { !it.isWarmup && it.loadBasis == LoadBasis.PERCENT_1RM }
                                    .mapNotNull { it.percent }
                            }
                        }
                        assertTrue(
                            "${template.id} última semana de '$name' debe ser test 90→95→100 % 1RM, obtuvo $t1Percents",
                            t1Percents.containsAll(listOf(90.0, 95.0, 100.0)),
                        )
                    }
                    else -> Unit
                }
            }
        }
    }
}
