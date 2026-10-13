package com.example.kpkn.screens.programdetail.design.split

import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.data.splits.SplitPublicationStatus
import com.example.kpkn.data.splits.SplitTag
import com.example.kpkn.data.splits.SplitTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Funciones puras de la página «Cambiar reparto» (P5): rol e inicial de cada día, orden de la semana, descripción accesible, filtro
 * del catálogo y días que cambian entre dos repartos.
 */
class SplitDayMarkTest {

    private val upperLower = listOf("Torso", "Pierna", "Descanso", "Torso", "Pierna", "Descanso", "Descanso")

    @Test
    fun descansoNoLlevaInicialNiRol() {
        for (label in listOf("Descanso", "descanso", "", "   ")) {
            assertEquals(SplitDayMark(SplitDayRole.REST, ""), splitDayMark(label))
        }
    }

    @Test
    fun empujeTironYPiernaTienenSuInicial() {
        assertEquals(SplitDayMark(SplitDayRole.PUSH, "E"), splitDayMark("Empuje"))
        assertEquals(SplitDayMark(SplitDayRole.PUSH, "E"), splitDayMark("Empuje + Cuádriceps"))
        assertEquals(SplitDayMark(SplitDayRole.PULL, "Ti"), splitDayMark("Tirón"))
        assertEquals(SplitDayMark(SplitDayRole.PULL, "Ti"), splitDayMark("tiron + isquios"))
        assertEquals(SplitDayMark(SplitDayRole.LEGS, "Pi"), splitDayMark("Pierna"))
        assertEquals(SplitDayMark(SplitDayRole.LEGS, "Pi"), splitDayMark("Piernas"))
    }

    @Test
    fun torsoYCuerpoCompletoSonDelMismoRol() {
        assertEquals(SplitDayMark(SplitDayRole.TORSO, "T"), splitDayMark("Torso"))
        // Torso antes que cuerpo completo: «Torso/Full Body» cuenta como torso.
        assertEquals(SplitDayMark(SplitDayRole.TORSO, "T"), splitDayMark("Torso/Full Body"))
        assertEquals(SplitDayMark(SplitDayRole.TORSO, "CC"), splitDayMark("Cuerpo Completo A"))
        assertEquals(SplitDayMark(SplitDayRole.TORSO, "CC"), splitDayMark("Full Body B"))
    }

    @Test
    fun piernaConFullBodyGanaPierna() {
        // «Pierna/Full Body» es un día de pierna: la regla de pierna va antes que la de cuerpo completo.
        assertEquals(SplitDayRole.LEGS, splitDayMark("Pierna/Full Body").role)
    }

    @Test
    fun elRestoTomaLaPrimeraLetraDelPatron() {
        assertEquals(SplitDayMark(SplitDayRole.OTHER, "P"), splitDayMark("Pecho/Espalda"))
        assertEquals(SplitDayMark(SplitDayRole.OTHER, "S"), splitDayMark("Sentadilla/Banca"))
        assertEquals(SplitDayMark(SplitDayRole.OTHER, "C"), splitDayMark("Cadena Anterior"))
    }

    @Test
    fun cadaRolTieneSuColorEstable() {
        assertTrue(splitDayAccent(SplitDayRole.PUSH) != null)
        assertEquals(null, splitDayAccent(SplitDayRole.REST))
        val colors = listOf(SplitDayRole.PUSH, SplitDayRole.PULL, SplitDayRole.LEGS, SplitDayRole.TORSO).map { splitDayAccent(it) }
        assertEquals(4, colors.toSet().size)
    }

    @Test
    fun laSemanaEmpiezaEnElDiaDelPrograma() {
        assertEquals(1, splitWeekdayAt(startDay = 1, position = 0))
        assertEquals(7, splitWeekdayAt(startDay = 1, position = 6))
        // Una semana que empieza en sábado (6): sábado, domingo y después lunes.
        assertEquals(6, splitWeekdayAt(startDay = 6, position = 0))
        assertEquals(1, splitWeekdayAt(startDay = 6, position = 2))
    }

    @Test
    fun descripcionAccesibleSigueElOrdenDelPrograma() {
        assertEquals(
            "Lunes: torso, Martes: pierna, Miércoles: descanso, Jueves: torso, Viernes: pierna, Sábado: descanso, Domingo: descanso",
            splitWeekDescription(upperLower, startDay = 1),
        )
        // Con el programa empezando en miércoles, el torso cae el miércoles.
        assertTrue(splitWeekDescription(upperLower, startDay = 3).startsWith("Miércoles: torso, Jueves: pierna"))
    }

    @Test
    fun patronCortoSeCompletaConDescanso() {
        assertEquals("Descanso", splitLabelAt(listOf("Torso"), 4))
    }

    @Test
    fun diasDeEntrenoYSuTexto() {
        assertEquals(4, splitTrainingDays(upperLower))
        assertEquals("1 día", splitDaysLabel(1))
        assertEquals("4 días", splitDaysLabel(4))
    }

    @Test
    fun cambiaSoloDondeNoCoincideElPatron() {
        assertEquals(emptyList<Int>(), splitChangedPositions(upperLower, upperLower))
        // Upper/Lower x4 frente a PPL + Upper/Lower: cambian el jueves, el viernes y el sábado.
        val pplUl = listOf("Torso", "Pierna", "Descanso", "Empuje", "Tirón", "Pierna", "Descanso")
        assertEquals(listOf(3, 4, 5), splitChangedPositions(upperLower, pplUl))
    }

    @Test
    fun formaComparableIgnoraTildesYMayusculas() {
        assertEquals("tiron", canonicalSplitLabel("  Tirón  "))
        assertEquals("descanso", canonicalSplitLabel(""))
    }

    @Test
    fun filtroSinBusquedaNiEtiquetaDejaElCatalogoVisible() {
        val all = filterSplitCatalog(SPLIT_TEMPLATES, query = "", tag = null)
        assertTrue(all.isNotEmpty())
        assertTrue(all.none { it.id == "custom" })
        assertTrue(all.all { it.publicationStatus != SplitPublicationStatus.HIDDEN_UNVERIFIED })
    }

    @Test
    fun filtroPorEtiquetaDejaSoloLosDeEsaEtiqueta() {
        val byTag = filterSplitCatalog(SPLIT_TEMPLATES, query = "", tag = SplitTag.ALTA_FRECUENCIA)
        assertTrue(byTag.isNotEmpty())
        assertTrue(byTag.all { SplitTag.ALTA_FRECUENCIA in it.tags })
    }

    @Test
    fun laEtiquetaPersonalizadoNoListaRepartos() {
        assertTrue(filterSplitCatalog(SPLIT_TEMPLATES, query = "", tag = SplitTag.PERSONALIZADO).isEmpty())
    }

    @Test
    fun laBusquedaMiraNombreYDescripcion() {
        val byName = filterSplitCatalog(SPLIT_TEMPLATES, query = "Upper / Lower x4", tag = null)
        assertTrue(byName.any { it.id == "ul_x4" })
        // «brutal» está en la descripción de Push / Pull x4, no en su nombre.
        val byDescription = filterSplitCatalog(SPLIT_TEMPLATES, query = "brutal", tag = null)
        assertTrue(byDescription.any { it.id == "push_pull_x4" })
        assertTrue(filterSplitCatalog(SPLIT_TEMPLATES, query = "zzz-no-existe", tag = null).isEmpty())
    }

    @Test
    fun repartoSinPatronTieneCeroDias() {
        val empty = SplitTemplate(id = "x", name = "Vacío", description = "")
        assertEquals(0, splitTrainingDays(empty.pattern))
    }
}
