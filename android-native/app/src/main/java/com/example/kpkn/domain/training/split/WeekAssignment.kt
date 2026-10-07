package com.example.kpkn.domain.training.split

import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.resolvedSchedulePlan
import com.example.kpkn.domain.training.ProgramCalendarEngine
import com.example.kpkn.domain.training.SessionDurationEstimator

/** Una sesión de la semana tal como la dibuja el tablero: título, foco, minutos, ejercicios y día. */
data class WeekSessionInfo(
    val id: String,
    val title: String,
    val focus: String,
    val minutes: Int,
    val exerciseCount: Int,
    val isMain: Boolean,
    /** Día de la semana (1 = lunes … 7 = domingo) o `null` si la sesión no tiene día. */
    val day: Int?,
)

/**
 * El efecto real de arrastrar y soltar sesiones entre días en el tablero de la semana. La asignación es un mapa
 * `día (1 = lunes … 7 = domingo) → id de sesión`; los días sin entrada son descanso.
 *
 * - [move] decide qué pasa al soltar una sesión en un día: a un día libre, la sesión se muda y su día queda libre; a un día
 *   ocupado, las dos sesiones se **intercambian**; al mismo día o fuera de 1..7, nada.
 * - [applyAssignment] escribe el resultado en el programa: reescribe `dayOfWeek` y `assignedDays` y deja
 *   `schedulePlan.trainingDays` con los días que de verdad tienen sesión, **sin tocar ningún ejercicio**.
 *
 * Un día que no estaba entre los días de entreno (un descanso) es un destino válido: mover una sesión allí cambia los días
 * de entreno de la semana.
 */
object WeekAssignment {

    /** Asignación `día → sesión` de la semana [weekIndex]: la primera sesión de cada día (las demás no caben en el tablero). */
    fun of(program: Program, weekIndex: Int = 0): Map<Int, String> {
        val week = ProgramWeeks.effective(program).getOrNull(weekIndex)?.week ?: return emptyMap()
        val byDay = LinkedHashMap<Int, String>()
        week.sessions.forEach { session ->
            val day = session.dayOfWeek?.takeIf { it in 1..7 } ?: session.assignedDays.firstOrNull { it in 1..7 }
            if (day != null) byDay.putIfAbsent(day, session.id)
        }
        return byDay.toSortedMap().toMap()
    }

    /** Las sesiones de la semana [weekIndex] con lo que el tablero muestra de cada una. */
    fun sessionsOf(program: Program, resolver: ExerciseTraitResolver, weekIndex: Int = 0): List<WeekSessionInfo> {
        val week = ProgramWeeks.effective(program).getOrNull(weekIndex)?.week ?: return emptyList()
        return week.sessions.map { session ->
            WeekSessionInfo(
                id = session.id,
                title = session.name,
                focus = FocusSummary.of(session, resolver),
                minutes = SessionDurationEstimator.estimate(session).totalMinutes,
                exerciseCount = session.allExercises().size,
                isMain = session.isMainSession,
                day = session.dayOfWeek?.takeIf { it in 1..7 } ?: session.assignedDays.firstOrNull { it in 1..7 },
            )
        }
    }

    /**
     * Suelta [sessionId] en [toDay]. A un día libre: se muda (su día anterior queda libre). A un día ocupado: intercambio
     * con su dueño. Mismo día, sesión que no está en la asignación o [toDay] fuera de 1..7: la asignación sin cambios.
     */
    fun move(assignment: Map<Int, String>, sessionId: String, toDay: Int): Map<Int, String> {
        if (toDay !in 1..7) return assignment
        val fromDay = assignment.entries.firstOrNull { it.value == sessionId }?.key ?: return assignment
        if (fromDay == toDay) return assignment
        val occupant = assignment[toDay]
        val next = LinkedHashMap(assignment)
        if (occupant == null) {
            next.remove(fromDay)
            next[toDay] = sessionId
        } else {
            next[toDay] = sessionId
            next[fromDay] = occupant
        }
        return next.toSortedMap().toMap()
    }

    /** Problemas de una asignación respecto a la semana [weekIndex] del programa (vacío = se puede aplicar tal cual). */
    fun issues(program: Program, assignment: Map<Int, String>, weekIndex: Int = 0): List<String> {
        val week = ProgramWeeks.effective(program).getOrNull(weekIndex)?.week ?: return listOf("Tu programa no tiene esa semana.")
        val ids = week.sessions.mapTo(hashSetOf<String>()) { it.id }
        return buildList {
            assignment.keys.filter { it !in 1..7 }.forEach { add("El día $it no existe: usa de 1 a 7.") }
            assignment.values.filter { it !in ids }.forEach { add("La sesión $it no está en esa semana.") }
            assignment.values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach {
                add("La sesión $it aparece en más de un día.")
            }
        }
    }

    /**
     * Escribe [assignment] en la semana [weekIndex] del programa: cada sesión nombrada pasa a su día (`dayOfWeek`,
     * `assignedDays`) y `schedulePlan.trainingDays` queda con los días que tienen sesión en alguna semana. Los ejercicios no se
     * tocan; las sesiones que la asignación no nombra conservan su día.
     *
     * Con [propagateToOtherWeeks] las demás semanas de entreno que tienen la misma estructura (mismas sesiones en los mismos
     * días, como las de un plan propio) cambian igual, sesión a sesión por su posición; una semana distinta se deja como estaba.
     */
    fun applyAssignment(
        program: Program,
        assignment: Map<Int, String>,
        weekIndex: Int = 0,
        propagateToOtherWeeks: Boolean = true,
    ): Program {
        val effective = ProgramWeeks.effective(program)
        val baseRef = effective.getOrNull(weekIndex) ?: return program
        val dayBySession: Map<String, Int> = assignment.entries
            .filter { it.key in 1..7 }
            .associate { it.value to it.key }
        val baseSessions = baseRef.week.sessions
        val baseDays = baseSessions.map { it.dayOfWeek }

        val replacements = HashMap<WeekPosition, ProgramWeek>()
        replacements[baseRef.position] = baseRef.week.copy(
            sessions = baseSessions.map { session -> dayBySession[session.id]?.let { moved(session, it) } ?: session },
        )
        if (propagateToOtherWeeks) {
            effective.filter { it.position != baseRef.position }.forEach { other ->
                val parallel = other.week.sessions.size == baseSessions.size &&
                    other.week.sessions.map { it.dayOfWeek } == baseDays
                if (parallel) {
                    replacements[other.position] = other.week.copy(
                        sessions = other.week.sessions.mapIndexed { index, session ->
                            dayBySession[baseSessions[index].id]?.let { moved(session, it) } ?: session
                        },
                    )
                }
            }
        }

        var result = ProgramWeeks.map(program) { ref -> replacements[ref.position] }
        result = result.copy(
            schedulePlan = result.resolvedSchedulePlan().copy(trainingDays = ProgramWeeks.trainingDays(result)),
        )
        if (ProgramCalendarEngine.isCalendarized(result)) result = ProgramCalendarEngine.materializeWeekDates(result)
        return result
    }

    /** La sesión en [day]: cambia su día principal y, si tenía varios días asignados, solo el que ocupaba. */
    private fun moved(session: Session, day: Int): Session {
        val oldDay = session.dayOfWeek
        val assigned = session.assignedDays
        val nextAssigned = when {
            assigned.size <= 1 -> listOf(day)
            oldDay != null && oldDay in assigned -> assigned.map { if (it == oldDay) day else it }.distinct()
            else -> (assigned + day).distinct()
        }
        return session.copy(dayOfWeek = day, assignedDays = nextAssigned)
    }
}
