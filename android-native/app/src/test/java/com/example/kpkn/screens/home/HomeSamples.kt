package com.example.kpkn.screens.home

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramRunState
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionLocation
import com.example.kpkn.data.models.SimpleProgramKind
import com.example.kpkn.data.models.TodaySessionItem
import com.example.kpkn.domain.training.HomeWeekDay
import com.example.kpkn.domain.training.HomeWeekResolver
import com.example.kpkn.screens.home.design.HomeMastheadState
import com.example.kpkn.screens.home.design.homeDateLine
import java.time.LocalDate

/** Un caso de Home para la hoja de render: lo que pinta la cabecera y «Tu semana». */
internal data class HomeSample(
    val masthead: HomeMastheadState,
    val days: List<HomeWeekDay>,
    val selected: LocalDate,
)

/** Datos de ejemplo de Home para las pruebas y la hoja de render. Fechas fijas: martes 13 de octubre de 2026. */
internal object HomeSamples {

    /** Martes 13 de octubre de 2026: el «hoy» de todos los ejemplos. */
    val today: LocalDate = LocalDate.of(2026, 10, 13)

    fun session(id: String, name: String, dayOfWeek: Int, main: Boolean = false): Session =
        Session(id = id, name = name, dayOfWeek = dayOfWeek, isMainSession = main)

    /** Una sesión de la semana [weekId]; «hoy» se deduce de su día ISO frente a [today]. */
    fun item(
        program: Program,
        weekId: String,
        session: Session,
        today: LocalDate = this.today,
        done: Boolean = false,
        ongoing: Boolean = false,
    ): TodaySessionItem {
        val day = session.dayOfWeek ?: 1
        return TodaySessionItem(
            session = session,
            program = program,
            location = SessionLocation(macroIndex = 0, mesoIndex = 0, weekId = weekId),
            isCompleted = done,
            dayOfWeek = day,
            isOngoing = ongoing,
            isToday = day == today.dayOfWeek.value,
        )
    }

    /** Sesiones de cada semana del programa de 12: lunes (principal), martes y jueves. */
    private val weekSessions: List<Session> = listOf(
        session("torso-a", "Torso A", 1, main = true),
        session("pierna-a", "Pierna A", 2),
        session("torso-b", "Torso B", 4),
    )

    /** Programa de 12 semanas (la semana 3 es la actual) con el objetivo [goal] en su bloque. */
    fun twelveWeekProgram(
        name: String = "Camino al pico",
        goal: BlockGoal? = BlockGoal.ACCUMULATION,
    ): Program = Program(
        id = "p-12",
        name = name,
        structure = ProgramStructure.COMPLEX,
        startDay = 1,
        macrocycles = listOf(
            Macrocycle(
                id = "mac-1",
                name = "Macrociclo",
                blocks = listOf(
                    Block(
                        id = "blk-1",
                        name = "Bloque",
                        goal = goal,
                        mesocycles = listOf(
                            Mesocycle(
                                id = "meso-1",
                                name = "Mesociclo",
                                weeks = (1..12).map { n ->
                                    ProgramWeek(id = "w$n", name = "Semana $n", sessions = weekSessions)
                                },
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    /** Sesiones de la semana 3: lunes hecho, martes (hoy) pendiente y jueves pendiente. */
    fun twelveWeekItems(
        program: Program = twelveWeekProgram(),
        today: LocalDate = this.today,
    ): List<TodaySessionItem> = weekSessions.map { s ->
        item(program, "w3", s, today = today, done = s.dayOfWeek == 1)
    }

    /** Ciclo simple de una sola semana, con su número de ciclo en 5. */
    fun simpleCyclicProgram(): Program = Program(
        id = "p-cyc",
        name = "Cuerpo completo",
        structure = ProgramStructure.SIMPLE,
        simpleProgramKind = SimpleProgramKind.CYCLIC,
        startDay = 1,
        runState = ProgramRunState(runId = "run-cyc", cycleNumber = 5),
        macrocycles = listOf(
            Macrocycle(
                id = "mac-c",
                name = "Ciclo",
                blocks = listOf(
                    Block(
                        id = "blk-c",
                        name = "Ciclo",
                        mesocycles = listOf(
                            Mesocycle(
                                id = "meso-c",
                                name = "Ciclo",
                                weeks = listOf(
                                    ProgramWeek(
                                        id = "cyc-w1",
                                        name = "Semana",
                                        sessions = listOf(session("cuerpo", "Cuerpo completo", 2)),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    /** La única sesión del ciclo: martes (hoy), pendiente. */
    fun simpleCyclicItems(
        program: Program = simpleCyclicProgram(),
        today: LocalDate = this.today,
    ): List<TodaySessionItem> = listOf(item(program, "cyc-w1", session("cuerpo", "Cuerpo completo", 2), today = today))

    /** La muestra completa: cabecera (con o sin programa) y «Tu semana» si hay sesiones. */
    fun sample(
        program: Program?,
        items: List<TodaySessionItem>,
        name: String = "Alex",
        today: LocalDate = this.today,
    ): HomeSample {
        val days = if (program == null || items.isEmpty()) {
            emptyList()
        } else {
            HomeWeekResolver.week(items, program.startDay, today)
        }
        return HomeSample(
            masthead = HomeMastheadState(
                dateLine = homeDateLine(today),
                greeting = "Buenos días",
                name = name,
                initial = name.take(1).uppercase(),
                program = HomeWeekResolver.programLine(program, items, today),
            ),
            days = days,
            selected = today,
        )
    }

    /** Los cuatro casos de la hoja de render: programa de 12 semanas, ciclo simple, sin programa y nombre largo. */
    fun sheetCases(): List<Pair<String, HomeSample>> {
        val longProgram = twelveWeekProgram(name = "Powerlifting a medida con un nombre larguísimo que no cabe en una línea")
        return listOf(
            "prog12" to sample(twelveWeekProgram(), twelveWeekItems()),
            "cyclic" to sample(simpleCyclicProgram(), simpleCyclicItems()),
            "none" to sample(null, emptyList()),
            "long" to sample(longProgram, twelveWeekItems(longProgram), name = "Maximiliano Alejandro"),
        )
    }
}
