package com.example.kpkn.domain.training

import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.screens.home.HomeSamples
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** «Tu semana» y la línea del programa de Home: ventana de 7 días, estados de cada día, semana, ciclo y fase. */
class HomeWeekResolverTest {

    /** Martes 13 de octubre de 2026. */
    private val today = HomeSamples.today
    private val program = HomeSamples.twelveWeekProgram()

    @Test
    fun `la ventana empieza en el día de inicio del programa, un jueves`() {
        val days = HomeWeekResolver.week(emptyList(), startDay = 4, today = today)

        assertEquals(LocalDate.of(2026, 10, 8), days.first().date)
        assertEquals(LocalDate.of(2026, 10, 14), days.last().date)
        assertEquals(listOf("J", "V", "S", "D", "L", "M", "X"), days.map { it.letter })
    }

    @Test
    fun `sin día de inicio la ventana empieza en lunes`() {
        val days = HomeWeekResolver.week(emptyList(), startDay = null, today = today)

        assertEquals(LocalDate.of(2026, 10, 12), days.first().date)
        assertEquals(listOf("L", "M", "X", "J", "V", "S", "D"), days.map { it.letter })
        assertEquals(today, days.single { it.isToday }.date)
    }

    @Test
    fun `lunes hecho, hoy pendiente y miércoles de descanso`() {
        val days = HomeWeekResolver.week(HomeSamples.twelveWeekItems(program), startDay = 1, today = today)

        assertEquals(HomeDayKind.DONE, days[0].kind)
        assertEquals(HomeDayKind.PLANNED, days[1].kind)
        assertEquals(true, days[1].isToday)
        assertEquals(HomeDayKind.REST, days[2].kind)
        assertNull(days[2].sessionName)
    }

    @Test
    fun `una sesión pasada sin hacer queda como perdida`() {
        val items = listOf(
            HomeSamples.item(program, "w3", HomeSamples.session("torso-a", "Torso A", 1), today = today),
        )
        val days = HomeWeekResolver.week(items, startDay = 1, today = today)

        assertEquals(HomeDayKind.MISSED, days[0].kind)
    }

    @Test
    fun `la sesión en curso manda sobre el resto`() {
        val items = listOf(
            HomeSamples.item(
                program,
                "w3",
                HomeSamples.session("pierna-a", "Pierna A", 2),
                today = today,
                ongoing = true,
            ),
        )
        val days = HomeWeekResolver.week(items, startDay = 1, today = today)

        assertEquals(HomeDayKind.ONGOING, days[1].kind)
    }

    @Test
    fun `con varias sesiones el día muestra la principal`() {
        val items = listOf(
            HomeSamples.item(program, "w3", HomeSamples.session("a", "Accesorios", 2), today = today),
            HomeSamples.item(program, "w3", HomeSamples.session("b", "Empuje", 2, main = true), today = today),
        )
        val days = HomeWeekResolver.week(items, startDay = 1, today = today)

        assertEquals("Empuje", days[1].sessionName)
    }

    @Test
    fun `semana 3 de 12 con el nombre y la fase del bloque`() {
        val line = HomeWeekResolver.programLine(program, HomeSamples.twelveWeekItems(program), today)

        assertEquals("Camino al pico", line?.name)
        assertEquals("Semana 3 de 12", line?.weekLabel)
        assertEquals(3, line?.weekIndex)
        assertEquals(12, line?.weekCount)
        assertEquals("Fase de acumulación", line?.phase)
    }

    @Test
    fun `un ciclo simple de una semana muestra Semana N con el número de ciclo`() {
        val cyclic = HomeSamples.simpleCyclicProgram()
        val line = HomeWeekResolver.programLine(cyclic, HomeSamples.simpleCyclicItems(cyclic), today)

        assertEquals("Semana 5", line?.weekLabel)
        assertEquals(1, line?.weekCount)
        assertNull(line?.phase)
    }

    @Test
    fun `la fase sale del objetivo del bloque y no hay fase sin objetivo`() {
        val deload = HomeSamples.twelveWeekProgram(goal = BlockGoal.DELOAD)
        assertEquals(
            "Fase de descarga",
            HomeWeekResolver.programLine(deload, HomeSamples.twelveWeekItems(deload), today)?.phase,
        )

        val withoutGoal = HomeSamples.twelveWeekProgram(goal = null)
        assertNull(HomeWeekResolver.programLine(withoutGoal, HomeSamples.twelveWeekItems(withoutGoal), today)?.phase)
    }

    @Test
    fun `sin sesiones queda solo el nombre`() {
        val line = HomeWeekResolver.programLine(program, emptyList(), today)

        assertEquals("Camino al pico", line?.name)
        assertNull(line?.weekLabel)
        assertNull(line?.weekIndex)
        assertNull(line?.weekCount)
        assertNull(line?.phase)
    }

    @Test
    fun `sin programa activo no hay línea de programa`() {
        assertNull(HomeWeekResolver.programLine(null, emptyList(), today))
    }
}
