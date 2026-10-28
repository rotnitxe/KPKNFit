package com.example.kpkn.screens.programdetail.design.structure

import com.example.kpkn.screens.programdetail.design.ProgramPhase
import com.example.kpkn.screens.programdetail.design.ProgramPhaseWeekUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** El modelo del mapa del programa (Estructura): los tramos por bloque, la semana en curso y la fecha corta. Pura, sin Compose. */
class ProgramMapModelTest {

    /** Semanas consecutivas desde [first]; la de número [current], si se da, es la en curso. */
    private fun weeks(first: Int, count: Int, current: Int? = null): List<ProgramPhaseWeekUi> =
        (first until first + count).map { number ->
            ProgramPhaseWeekUi(id = "s$number", number = number, isCurrent = number == current, isDone = false)
        }

    /** Un bloque a mano, sin mesociclos: solo importan su fase y sus semanas. */
    private fun block(id: String, weeks: List<ProgramPhaseWeekUi>, phase: ProgramPhase?): ProgramBlockUi = ProgramBlockUi(
        id = id,
        name = id,
        macroIndex = 0,
        blockIndex = 0,
        eyebrow = phase?.label,
        phase = phase,
        mesocycles = emptyList(),
        weeks = weeks,
        sessionCount = weeks.size,
    )

    @Test
    fun cadaBloqueConSemanasEsUnTramoDeSuAncho() {
        val segments = programMapSegments(
            listOf(
                block("acumulacion", weeks(1, 3), ProgramPhase.ACCUMULATION),
                block("vacio", emptyList(), ProgramPhase.DELOAD),
                block("competicion", weeks(4, 1), ProgramPhase.COMPETITION),
            ),
        )
        assertEquals(
            listOf(
                ProgramMapSegment(id = "acumulacion", label = "Acumulación", weeks = 3, phase = ProgramPhase.ACCUMULATION),
                ProgramMapSegment(id = "competicion", label = null, weeks = 1, phase = ProgramPhase.COMPETITION),
            ),
            segments,
        )
    }

    @Test
    fun unBloqueSinFaseVaSinNombreAunqueTengaVariasSemanas() {
        val segments = programMapSegments(listOf(block("libre", weeks(1, 4), null)))
        assertEquals(listOf(ProgramMapSegment(id = "libre", label = null, weeks = 4, phase = null)), segments)
    }

    @Test
    fun laSemanaEnCursoSeNombraConSuNumeroGlobal() {
        val blocks = listOf(
            block("a", weeks(1, 3), ProgramPhase.ACCUMULATION),
            block("b", weeks(4, 4, current = 5), ProgramPhase.INTENSIFICATION),
        )
        assertEquals("Hoy · semana 5", programMapNowLabel(blocks))
    }

    @Test
    fun sinSemanaEnCursoNoHayEtiqueta() {
        assertNull(programMapNowLabel(listOf(block("a", weeks(1, 3), ProgramPhase.ACCUMULATION))))
    }

    @Test
    fun laFechaCortaVaEnEspanolSinAnioNiPuntoFinal() {
        assertEquals("6 oct", programMapDateLabel(LocalDate.of(2026, 10, 6)))
        assertNull(programMapDateLabel(null))
    }
}
