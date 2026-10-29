package com.example.kpkn.domain.competitions

import com.example.kpkn.data.models.CompetitionMovementType
import com.example.kpkn.data.models.CompetitionRecord
import com.example.kpkn.data.models.CompetitionTechnicalBlock
import com.example.kpkn.data.models.CompetitionTemplateType
import com.example.kpkn.domain.multisport.SportId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompetitionDisciplineTest {

    @Test
    fun there_are_twenty_eight_disciplines_with_unique_ids() {
        assertEquals(28, CompetitionDiscipline.entries.size)
        assertEquals(28, CompetitionDiscipline.entries.map { it.id }.toSet().size)
    }

    @Test
    fun without_programs_the_groups_keep_the_canvas_order_and_powerlifting_is_preselected() {
        val groups = CompetitionDiscipline.grouped(emptySet())
        assertEquals(listOf("Fuerza", "Resistencia", "Combate", "Más"), groups.map { it.first.label })
        assertEquals(CompetitionDiscipline.POWERLIFTING, groups.first().second.first())
        assertEquals(CompetitionDiscipline.POWERLIFTING, CompetitionDiscipline.preselected(emptySet()))
    }

    @Test
    fun the_sports_of_your_programs_go_first() {
        val groups = CompetitionDiscipline.grouped(setOf(SportId.RUNNING))
        assertEquals("Resistencia", groups.first().first.label)
        assertEquals(CompetitionDiscipline.RUNNING, groups.first().second.first())
        assertEquals(CompetitionDiscipline.RUNNING, CompetitionDiscipline.preselected(setOf(SportId.RUNNING)))
        assertEquals(28, groups.sumOf { it.second.size })
    }

    @Test
    fun of_reads_the_saved_discipline_and_falls_back_on_the_old_sport_type() {
        assertEquals(CompetitionDiscipline.HYROX, CompetitionDiscipline.of(CompetitionRecord(id = "a", title = "", discipline = "hyrox")))
        assertEquals(CompetitionDiscipline.RUNNING, CompetitionDiscipline.of(CompetitionRecord(id = "b", title = "", sportType = CompetitionTemplateType.RUNNING)))
        assertEquals(CompetitionDiscipline.OTHER, CompetitionDiscipline.of(CompetitionRecord(id = "c", title = "")))
        val sbd = CompetitionRecord(
            id = "d", title = "",
            technicalBlocks = listOf(CompetitionTechnicalBlock(id = "s", title = "Sentadilla", movementType = CompetitionMovementType.SQUAT)),
        )
        assertEquals(CompetitionDiscipline.POWERLIFTING, CompetitionDiscipline.of(sbd))
        assertEquals(CompetitionDiscipline.POWERLIFTING, CompetitionDiscipline.of(CompetitionRecord(id = "e", title = ""), linkedToPowerlifting = true))
    }

    @Test
    fun weigh_in_belongs_to_load_and_combat() {
        assertTrue(CompetitionDiscipline.POWERLIFTING.hasWeighIn)
        assertTrue(CompetitionDiscipline.JUDO.hasWeighIn)
        assertFalse(CompetitionDiscipline.RUNNING.hasWeighIn)
        assertFalse(CompetitionDiscipline.BODYBUILDING.hasWeighIn)
    }
}
