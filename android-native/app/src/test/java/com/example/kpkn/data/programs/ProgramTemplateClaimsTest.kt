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
    fun block_name_maps_to_composition_contract() {
        PROGRAM_TEMPLATES.filter { it.type == ProgramStructure.COMPLEX && it.recipe != null }.forEach { template ->
            val recipe = template.recipe!!
            template.blockNames.forEachIndexed { index, name ->
                val weeks = recipe.weeks.filter { it.blockIndex == index }
                val goal = weeks.first().blockGoal
                val exact = name.trim()
                when {
                    exact.equals("Peak", ignoreCase = true) || exact.equals("Pico", ignoreCase = true) -> {
                        assertEquals("${template.id} '$exact' debe ser PEAK, no $goal", BlockGoal.PEAK, goal)
                    }
                    exact.contains("Taper", ignoreCase = true) -> {
                        assertEquals("${template.id} '$exact' debe ser TAPER, no $goal", BlockGoal.TAPER, goal)
                        val last = weeks.maxBy { it.weekNumber }
                        val t1Percents = last.days.flatMap { day ->
                            day.slots.filter { it.role == SlotRole.T1_MAIN }.flatMap { slot ->
                                slot.sets.filter { !it.isWarmup && it.loadBasis == LoadBasis.PERCENT_1RM }
                                    .mapNotNull { it.percent }
                            }
                        }
                        assertTrue(
                            "${template.id} última semana de '$exact' debe ser test 90→95→100 % 1RM, obtuvo $t1Percents",
                            t1Percents.containsAll(listOf(90.0, 95.0, 100.0)),
                        )
                    }
                    exact.equals("Definición", ignoreCase = true) -> {
                        assertEquals("${template.id} 'Definición' debe ser DENSITY, no $goal", BlockGoal.DENSITY, goal)
                    }
                }
            }
        }
    }
}
