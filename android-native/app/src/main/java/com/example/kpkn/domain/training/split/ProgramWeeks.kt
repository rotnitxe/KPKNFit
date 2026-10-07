package com.example.kpkn.domain.training.split

import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WeekExecutionKind

/** Posición de una semana en la jerarquía del programa (macrociclo, bloque, mesociclo, semana dentro del mesociclo). */
internal data class WeekPosition(
    val macroIndex: Int,
    val blockIndex: Int,
    val mesoIndex: Int,
    val weekIndex: Int,
)

/** Una semana del programa con su posición en la jerarquía. */
internal data class WeekRef(
    val macroIndex: Int,
    val blockIndex: Int,
    val mesoIndex: Int,
    val weekIndex: Int,
    val week: ProgramWeek,
) {
    val position: WeekPosition get() = WeekPosition(macroIndex, blockIndex, mesoIndex, weekIndex)
}

/** Recorridos de las semanas de un programa, con posiciones: no exigen ids únicos (un programa a medio editar puede no tenerlos). */
internal object ProgramWeeks {

    /** Todas las semanas en el orden del programa. */
    fun all(program: Program): List<WeekRef> = buildList {
        program.macrocycles.forEachIndexed { macroIndex, macro ->
            macro.blocks.forEachIndexed { blockIndex, block ->
                block.mesocycles.forEachIndexed { mesoIndex, meso ->
                    meso.weeks.forEachIndexed { weekIndex, week ->
                        add(WeekRef(macroIndex, blockIndex, mesoIndex, weekIndex, week))
                    }
                }
            }
        }
    }

    /** Una semana de entreno de verdad: no es una semana extra de ciclo (test, descarga programada), ni de descanso, ni está vacía. */
    fun isEffective(week: ProgramWeek): Boolean =
        !week.isLoopWeek && week.executionKind != WeekExecutionKind.REST && week.sessions.isNotEmpty()

    fun effective(program: Program): List<WeekRef> = all(program).filter { isEffective(it.week) }

    /** Reemplaza cada semana por lo que devuelva [transform]; `null` la deja como estaba. */
    fun map(program: Program, transform: (WeekRef) -> ProgramWeek?): Program = program.copy(
        macrocycles = program.macrocycles.mapIndexed { macroIndex, macro ->
            macro.copy(
                blocks = macro.blocks.mapIndexed { blockIndex, block ->
                    block.copy(
                        mesocycles = block.mesocycles.mapIndexed { mesoIndex, meso ->
                            meso.copy(
                                weeks = meso.weeks.mapIndexed { weekIndex, week ->
                                    transform(WeekRef(macroIndex, blockIndex, mesoIndex, weekIndex, week)) ?: week
                                },
                            )
                        },
                    )
                },
            )
        },
    )

    /** Días (1..7) en los que alguna semana del programa tiene una sesión; es lo que debe decir `schedulePlan.trainingDays`. */
    fun trainingDays(program: Program): Set<Int> =
        all(program).flatMapTo(sortedSetOf<Int>()) { ref -> ref.week.sessions.flatMap { session -> daysOf(session) } }

    /** Días en los que cae una sesión: el principal y los asignados (siempre dentro de 1..7). */
    fun daysOf(session: Session): List<Int> =
        (listOfNotNull(session.dayOfWeek) + session.assignedDays).filter { it in 1..7 }.distinct()
}
