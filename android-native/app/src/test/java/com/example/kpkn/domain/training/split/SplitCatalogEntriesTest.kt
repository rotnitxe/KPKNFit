package com.example.kpkn.domain.training.split

import com.example.kpkn.data.splits.Difficulty
import com.example.kpkn.data.splits.SPLIT_CARD_DESCRIPTIONS
import com.example.kpkn.data.splits.SplitTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/** Datos de las tarjetas del catálogo de repartos: descripción, nombre llano, frecuencia, nivel, tira, filtros y grupos. */
class SplitCatalogEntriesTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            com.example.kpkn.domain.training.CatalogCompositionTestSupport.install()
        }
    }

    private val entries = SplitCatalog.entries()

    @Test
    fun the_catalog_has_the_35_published_splits_with_the_approved_name_and_description() {
        assertEquals(35, entries.size)
        assertEquals(SplitCatalogApprovedNames.names.keys, entries.map { it.id }.toSet())
        assertEquals(SplitCatalogApprovedNames.names.keys, SPLIT_CARD_DESCRIPTIONS.keys)
        entries.forEach { entry ->
            assertEquals(entry.id, SplitCatalogApprovedNames.names.getValue(entry.id), entry.name)
            assertEquals(SPLIT_CARD_DESCRIPTIONS.getValue(entry.id), entry.description)
            assertTrue(entry.id, entry.description.length in 20..200)
        }
    }

    @Test
    fun times_per_muscle_comes_from_the_focus_of_each_day() {
        fun times(id: String) = SplitCatalog.entry(id)?.timesPerMuscle
        assertEquals(2, times("ul_x4"))
        assertEquals(3, times("fullbody_x3"))
        assertEquals(2, times("ppl_x6"))
        assertEquals(1, times("bro_split"))
        assertEquals(5, times("fullbody_x5"))
        // El powerlifting no lo muestra: sus días se definen por levantamiento.
        entries.filter { it.powerlifting }.forEach { assertNull(it.id, it.timesPerMuscle) }
        entries.filter { !it.powerlifting }.forEach { assertNotNull(it.id, it.timesPerMuscle) }
    }

    @Test
    fun the_level_comes_from_the_difficulty_of_the_template() {
        assertEquals(Difficulty.PRINCIPIANTE, SplitCatalog.entry("fullbody_x3")?.level)
        assertEquals("Principiante", SplitCatalog.entry("fullbody_x3")?.levelLabel)
        assertEquals("Avanzado", SplitCatalog.entry("ppl_x6")?.levelLabel)
        assertEquals("Intermedio", SplitCatalog.entry("ul_x4")?.levelLabel)
    }

    @Test
    fun the_strip_has_seven_cells_with_letters_roles_and_initials() {
        val strip = SplitCatalog.entry("ul_x4")!!.strip(startDay = 1)
        assertEquals(7, strip.size)
        assertEquals(listOf("L", "M", "Mi", "J", "V", "S", "D"), strip.map { it.letter })
        assertEquals(listOf(DayRole.TORSO, DayRole.LEGS, DayRole.REST, DayRole.TORSO, DayRole.LEGS, DayRole.REST, DayRole.REST), strip.map { it.mark.role })
        assertEquals(listOf("T", "Pi", "", "T", "Pi", "", ""), strip.map { it.mark.initial })
        // Desde el jueves la tira empieza en J.
        assertEquals(listOf("J", "V", "S", "D", "L", "M", "Mi"), SplitCatalog.entry("ul_x4")!!.strip(startDay = 4).map { it.letter })
    }

    @Test
    fun day_marks_follow_the_canvas_rules() {
        fun mark(label: String) = SplitDayMarks.of(label)
        assertEquals(DayMark(DayRole.PUSH, "E"), mark("Empuje + Cuádriceps"))
        assertEquals(DayMark(DayRole.PULL, "Ti"), mark("Tirón"))
        assertEquals(DayMark(DayRole.FULL_BODY, "CC"), mark("Cuerpo Completo A"))
        assertEquals(DayMark(DayRole.MIX, "An"), mark("Cadena Anterior"))
        assertEquals(DayMark(DayRole.MIX, "Po"), mark("Cadena Posterior"))
        assertEquals(DayRole.REST, mark("Descanso").role)
        // Los días «raros» toman el color del grupo que domina su foco, con la inicial de su etiqueta.
        assertEquals(DayRole.LEGS, mark("Glúteo/Isquios").role)
        assertEquals(DayRole.TORSO, mark("Pecho/Espalda").role)
        assertEquals("Pe", mark("Pecho/Espalda").initial)
        assertEquals(DayRole.MIX, mark("Sesión 1 (4x9)").role)
    }

    @Test
    fun filters_by_days_tag_and_text() {
        val four = entries.filter { SplitCatalog.matches(it, SplitCatalogFilter(days = 4)) }
        assertTrue(four.isNotEmpty() && four.all { it.trainingDays == 4 })
        val rec = entries.filter { SplitCatalog.matches(it, SplitCatalogFilter(tag = SplitTag.RECOMENDADO_KPKN)) }
        assertTrue(rec.all { it.recommended } && rec.isNotEmpty())
        val glute = entries.filter { SplitCatalog.matches(it, SplitCatalogFilter(query = "glúteos")) }
        assertTrue(glute.any { it.id == "glute_focus" })
        // Busca también en las ventajas y en el nombre original de la plantilla.
        assertTrue(SplitCatalog.matches(SplitCatalog.entry("ul_x4")!!, SplitCatalogFilter(query = "upper / lower")))
        assertTrue(SplitCatalog.matches(SplitCatalog.entry("ul_x4")!!, SplitCatalogFilter(query = "48-72h")))
        assertTrue(SplitCatalog.view(SplitCatalogFilter(query = "zzzz")).isEmpty)
    }

    @Test
    fun the_view_groups_what_fits_the_week_and_what_changes_the_days() {
        val now = CatalogNow("Personalizado", 4, SplitDayMarks.strip(listOf("Pierna A", "Empuje", "Descanso", "Tirón", "Pierna B", "Descanso", "Descanso")))
        val view = SplitCatalog.view(haveDays = 4, now = now)
        assertEquals(now, view.now)
        assertEquals(listOf("Encajan con tus 4 días", "Cambian tus días"), view.groups.map { it.title })
        assertTrue(view.groups[0].entries.all { it.trainingDays == 4 })
        assertTrue(view.groups[1].entries.all { it.trainingDays != 4 })
        assertFalse(view.groups[0].changesDays)
        assertTrue(view.groups[1].changesDays)
        assertEquals(35, view.groups.sumOf { it.entries.size })

        // Sin ninguno que encaje (6 días y filtro de 2): «Piden otro número de días».
        val none = SplitCatalog.view(SplitCatalogFilter(days = 2), haveDays = 6)
        assertEquals(listOf("Piden otro número de días"), none.groups.map { it.title })
        // Mientras se busca, «Ahora» no se muestra.
        assertNull(SplitCatalog.view(SplitCatalogFilter(query = "torso"), haveDays = 4, now = now).now)
    }

    @Test
    fun now_is_the_week_pattern_and_names_the_split_only_when_it_matches() {
        val program = MigrationTestSupport.program(MigrationTestSupport.ulWeek("w1"))
        val custom = SplitCatalog.now(program, "w1")!!
        assertEquals(SplitCatalog.PERSONALIZED, custom.label)
        assertEquals(4, custom.trainingDays)
        assertEquals(listOf("T", "Pi", "", "T", "Pi", "", ""), custom.strip.map { it.mark.initial })
        val named = SplitCatalog.now(program.copy(selectedSplitId = "ul_x4"), "w1")!!
        assertEquals("Torso y pierna, 4 días", named.label)
        val wrong = SplitCatalog.now(program.copy(selectedSplitId = "fullbody_x3"), "w1")!!
        assertEquals(SplitCatalog.PERSONALIZED, wrong.label)
    }
}
