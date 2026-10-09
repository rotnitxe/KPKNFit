package com.example.kpkn.domain.training

import com.example.kpkn.data.models.KeyDateType
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramKeyDate
import com.example.kpkn.data.models.ProgramStructure
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** C4c: «Sí, sin fecha» pide añadir la fecha en Inicio hasta que el programa tenga fecha clave de competición. */
class HomeCompetitionNeedsDateTest {

    private val plan = Program(id = "p1", name = "Plan", structure = ProgramStructure.COMPLEX)

    private val planWithDate = plan.copy(
        keyDates = listOf(
            ProgramKeyDate(
                id = "kd-1",
                title = "Open nacional",
                type = KeyDateType.COMPETITION,
                startDate = "2026-11-20",
                eventDate = "2026-11-20",
            ),
        ),
    )

    @Test
    fun compite_sin_fecha_con_programa_sin_fecha_clave_pide_la_fecha() {
        assertTrue(HomeCompetitionResolver.needsCompetitionDate(plan, competesWithoutDate = true))
    }

    @Test
    fun con_fecha_clave_de_competicion_no_pide_nada() {
        assertFalse(HomeCompetitionResolver.needsCompetitionDate(planWithDate, competesWithoutDate = true))
    }

    @Test
    fun quien_no_compite_no_pide_fecha() {
        assertFalse(HomeCompetitionResolver.needsCompetitionDate(plan, competesWithoutDate = false))
    }

    @Test
    fun sin_programa_no_pide_fecha() {
        assertFalse(HomeCompetitionResolver.needsCompetitionDate(null, competesWithoutDate = true))
    }
}
