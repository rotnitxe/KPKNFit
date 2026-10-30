package com.example.kpkn.screens.sessioneditor.components.assistant

import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverBackground
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverSpec
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Filtros, grupos y contador del catálogo de plantillas del asistente (pulido `as-pla-catalogo`, `as-pla-filtros`). */
class AsTemplateCatalogTest {

    private fun card(id: String, group: String, minutes: Int?, level: Int, types: Set<String>, routine: String = "PPL", text: String = id) = AsTemplateCard(
        id = id, name = id, line = "", minutes = minutes, exercises = 5, sets = 15, level = level, muscles = "", types = types,
        group = group, routine = routine, searchText = text.lowercase(), cover = LineCoverSpec(LineCoverStyle.RELIEVE, LineCoverBackground.CARBON, 1),
    )

    private val cards = listOf(
        card("leg-quad", "Pierna", 35, 1, setOf("Piernas"), "Upper/Lower"),
        card("legs-big", "Pierna", 50, 2, setOf("Piernas", "Glúteos"), "PPL"),
        card("push", "Torso", 35, 1, setOf("Torso", "Pecho"), "PPL"),
        card("arms", "Brazos", 76, 0, setOf("Brazos"), "Antagonistas"),
    )

    @Test
    fun duracionUsaLosCortesDeCortaMediaYLarga() {
        assertEquals(AsDuration.SHORT, AsTemplateCatalog.durationOf(card("a", "x", 45, 1, emptySet())))
        assertEquals(AsDuration.MEDIUM, AsTemplateCatalog.durationOf(card("a", "x", 46, 1, emptySet())))
        assertEquals(AsDuration.MEDIUM, AsTemplateCatalog.durationOf(card("a", "x", 75, 1, emptySet())))
        assertEquals(AsDuration.LONG, AsTemplateCatalog.durationOf(card("a", "x", 76, 1, emptySet())))
    }

    @Test
    fun filtrosSeCombinan() {
        assertEquals(listOf("leg-quad", "legs-big"), AsTemplateCatalog.filter(cards, AsTemplateFilters(type = "Piernas")).map { it.id })
        assertEquals(listOf("leg-quad"), AsTemplateCatalog.filter(cards, AsTemplateFilters(type = "Piernas", level = 1)).map { it.id })
        assertEquals(listOf("legs-big"), AsTemplateCatalog.filter(cards, AsTemplateFilters(duration = AsDuration.MEDIUM)).map { it.id })
        assertEquals(listOf("push"), AsTemplateCatalog.filter(cards, AsTemplateFilters(query = " PUSH ")).map { it.id })
        assertTrue(AsTemplateCatalog.filter(cards, AsTemplateFilters(type = "Brazos", level = 2)).isEmpty())
    }

    @Test
    fun seccionesSiguenElOrdenDeAparicionPorGrupoRutinaNivelODuracion() {
        assertEquals(listOf("Pierna", "Torso", "Brazos"), AsTemplateCatalog.sections(cards, AsGroupMode.GROUP).map { it.key })
        assertEquals(listOf("Upper/Lower", "PPL", "Antagonistas"), AsTemplateCatalog.sections(cards, AsGroupMode.ROUTINE).map { it.key })
        assertEquals(listOf("Medio", "Exigente", "Fácil"), AsTemplateCatalog.sections(cards, AsGroupMode.LEVEL).map { it.key })
        assertEquals(
            listOf("Corta (≤45 min)", "Media (46–75 min)", "Larga (≥76 min)"),
            AsTemplateCatalog.sections(cards, AsGroupMode.DURATION).map { it.key },
        )
        assertEquals(2, AsTemplateCatalog.sections(cards, AsGroupMode.GROUP).first().cards.size)
    }

    @Test
    fun elContadorDiceCuantasPlantillasOCuantosResultados() {
        assertEquals("4 plantillas", AsTemplateCatalog.resultLabel(4, 4, AsTemplateFilters()))
        assertEquals("1 resultado", AsTemplateCatalog.resultLabel(4, 1, AsTemplateFilters(type = "Brazos")))
        assertEquals("2 resultados", AsTemplateCatalog.resultLabel(4, 2, AsTemplateFilters(type = "Piernas")))
        assertEquals("", AsTemplateCatalog.resultLabel(4, 0, AsTemplateFilters(type = "Brazos", level = 2)))
    }

    @Test
    fun losFiltrosActivosCuentanSoloNivelYDuracion() {
        assertEquals(0, AsTemplateFilters(type = "Piernas", query = "x").activeCount)
        assertEquals(2, AsTemplateFilters(level = 1, duration = AsDuration.LONG).activeCount)
        assertFalse(AsTemplateFilters().anyFilter)
        assertTrue(AsTemplateFilters(query = "x").anyFilter)
    }
}
