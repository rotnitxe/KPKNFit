package com.example.kpkn.screens.programs

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramRunState
import com.example.kpkn.data.models.ProgramRunStatus
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reglas de la lista de Programas sin Android: numeración, filtros, estados y la cinta del programa en curso. */
class ProgramListLogicTest {

    @Test
    fun nextBlankProgramNumber_empieza_en_1_sin_programas() {
        assertEquals(1, nextBlankProgramNumber(emptyList()))
    }

    @Test
    fun nextBlankProgramNumber_pasa_del_mayor_aunque_haya_huecos() {
        // Contar daba 3 y repetía «Nuevo programa 3»: el mayor (3) más uno es 4.
        assertEquals(4, nextBlankProgramNumber(listOf("Nuevo programa 1", "Nuevo programa 3")))
    }

    @Test
    fun nextBlankProgramNumber_ignora_los_nombres_que_no_son_del_patron() {
        val names = listOf("Mi plan", "Nuevo programa x", "Nuevo programa 1 copia", " Nuevo programa 1 ")
        assertEquals(2, nextBlankProgramNumber(names))
    }

    @Test
    fun inactiveProgramsOf_quita_el_de_en_curso_y_los_encolados() {
        val all = listOf(program("a"), program("b"), program("c"))
        val result = inactiveProgramsOf(all = all, featuredId = "a", queuedIds = listOf("c"))
        assertEquals(listOf("b"), result.map { it.id })
    }

    @Test
    fun programListStatusLabel_distingue_borrador_terminado_y_fecha() {
        val day = LocalDate.of(2026, 9, 12)
        assertEquals("Borrador", programListStatusLabel(completed = false, completedOn = day))
        assertEquals("Terminado", programListStatusLabel(completed = true, completedOn = null))
        assertEquals("Terminado · 12 sep", programListStatusLabel(completed = true, completedOn = day))
    }

    @Test
    fun programIsFinished_solo_con_el_ciclo_completado() {
        assertTrue(programIsFinished(program("x").copy(runState = ProgramRunState(runId = "r", status = ProgramRunStatus.COMPLETED))))
        assertFalse(programIsFinished(program("y").copy(runState = ProgramRunState(runId = "r", status = ProgramRunStatus.ACTIVE))))
        assertFalse(programIsFinished(program("z")))
    }

    @Test
    fun lastTrainedDatesOf_guarda_el_dia_mas_reciente_y_salta_las_fechas_ilegibles() {
        val entries = listOf(
            "p1" to "2026-09-01T10:00:00.000Z",
            "p1" to "2026-09-12T08:30:00.000Z",
            "p1" to "2026-08-30T09:00:00.000Z",
            "p2" to "no es una fecha",
        )
        val result = lastTrainedDatesOf(entries, ZoneId.of("UTC"))
        assertEquals(mapOf("p1" to LocalDate.of(2026, 9, 12)), result)
    }

    @Test
    fun summarizeActiveProgram_sin_programa_activo_no_tiene_sesion_ni_semana_y_una_celda_por_semana() {
        val summary = summarizeActiveProgram(
            program = twoWeekProgram("p"),
            active = null,
            history = emptyList(),
            ongoing = null,
            today = LocalDate.of(2026, 10, 27),
        )
        assertEquals("p", summary.programId)
        assertNull(summary.todaySessionName)
        assertNull(summary.progressLabel)
        assertEquals(2, summary.ribbon.size)
        assertTrue(summary.ribbon.none { it.current || it.done })
    }

    @Test
    fun programListMeta_dice_semanas_y_dias_como_el_lienzo() {
        // 16 semanas · 4 días: cada semana tiene sesiones en 4 días distintos (dos sesiones el mismo día cuentan un día).
        val weeks = (1..3).map { n ->
            ProgramWeek(
                id = "w$n",
                name = "Semana $n",
                sessions = listOf(
                    Session(id = "s$n-1", name = "A", dayOfWeek = 1),
                    Session(id = "s$n-2", name = "B", dayOfWeek = 2),
                    Session(id = "s$n-3", name = "C", dayOfWeek = 2),
                    Session(id = "s$n-4", name = "D", dayOfWeek = 4),
                    Session(id = "s$n-5", name = "E", dayOfWeek = 5),
                ),
            )
        }
        val program = programWith(weeks, ProgramStructure.COMPLEX)
        assertEquals(4, programTrainingDays(program))
        assertEquals("3 semanas · 4 días", programListMeta(program, ProgramStats(weeks = 3, sessions = 15)))
    }

    @Test
    fun programListMeta_en_una_rutina_de_una_semana_dice_rutina_y_los_dias() {
        val week = ProgramWeek(
            id = "w1",
            name = "Semana 1",
            sessions = listOf(
                Session(id = "a", name = "A", dayOfWeek = 1),
                Session(id = "b", name = "B", dayOfWeek = 3),
                Session(id = "c", name = "C", dayOfWeek = 5),
            ),
        )
        val routine = programWith(listOf(week), ProgramStructure.SIMPLE)
        assertEquals("Rutina · 3 días", programListMeta(routine, ProgramStats(weeks = 1, sessions = 3)))
    }

    @Test
    fun programListMeta_sin_sesiones_omite_los_dias() {
        val empty = programWith(listOf(ProgramWeek(id = "w1", name = "Semana 1"), ProgramWeek(id = "w2", name = "Semana 2")), ProgramStructure.COMPLEX)
        assertEquals(0, programTrainingDays(empty))
        assertEquals("2 semanas", programListMeta(empty, ProgramStats(weeks = 2, sessions = 0)))
    }

    @Test
    fun queueDragSteps_cuenta_una_fila_al_cruzar_su_mitad() {
        val heights = listOf(100f, 100f, 100f)
        // Cada puesto mide 100 + 10 de hueco: hasta 55 px no se mueve nada; a partir de 56, un puesto.
        assertEquals(0, queueDragSteps(offsetPx = 55f, index = 0, heightsPx = heights, spacingPx = 10f))
        assertEquals(1, queueDragSteps(offsetPx = 56f, index = 0, heightsPx = heights, spacingPx = 10f))
        assertEquals(2, queueDragSteps(offsetPx = 170f, index = 0, heightsPx = heights, spacingPx = 10f))
        // No se sale por abajo ni por arriba de la cola.
        assertEquals(2, queueDragSteps(offsetPx = 900f, index = 0, heightsPx = heights, spacingPx = 10f))
        assertEquals(-2, queueDragSteps(offsetPx = -900f, index = 2, heightsPx = heights, spacingPx = 10f))
        assertEquals(-1, queueDragSteps(offsetPx = -60f, index = 1, heightsPx = heights, spacingPx = 10f))
        assertEquals(0, queueDragSteps(offsetPx = 0f, index = 1, heightsPx = heights, spacingPx = 10f))
        assertEquals(0, queueDragSteps(offsetPx = 40f, index = 5, heightsPx = heights, spacingPx = 10f))
    }

    private fun programWith(weeks: List<ProgramWeek>, structure: ProgramStructure): Program = Program(
        id = "p",
        name = "Programa p",
        structure = structure,
        macrocycles = listOf(
            Macrocycle(
                id = "mc",
                name = "Macrociclo",
                blocks = listOf(
                    Block(
                        id = "b",
                        name = "Bloque",
                        mesocycles = listOf(Mesocycle(id = "m", name = "Mesociclo", weeks = weeks)),
                    ),
                ),
            ),
        ),
    )

    private fun program(id: String): Program = Program(id = id, name = "Programa $id")

    private fun twoWeekProgram(id: String): Program = Program(
        id = id,
        name = "Programa $id",
        structure = ProgramStructure.SIMPLE,
        macrocycles = listOf(
            Macrocycle(
                id = "$id-mc",
                name = "Macrociclo",
                blocks = listOf(
                    Block(
                        id = "$id-b",
                        name = "Bloque",
                        mesocycles = listOf(
                            Mesocycle(
                                id = "$id-m",
                                name = "Mesociclo",
                                weeks = listOf(
                                    ProgramWeek(id = "$id-w1", name = "Semana 1"),
                                    ProgramWeek(id = "$id-w2", name = "Semana 2"),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )
}
