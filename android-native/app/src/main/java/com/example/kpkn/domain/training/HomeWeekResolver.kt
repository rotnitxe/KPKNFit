package com.example.kpkn.domain.training

import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.SimpleProgramKind
import com.example.kpkn.data.models.TodaySessionItem
import com.example.kpkn.data.models.isSimpleProgram
import com.example.kpkn.domain.training.HomeSessionResolver.allWeekLocations
import java.time.LocalDate

/** Estado de un día de «Tu semana». */
enum class HomeDayKind { DONE, PLANNED, MISSED, ONGOING, REST }

/**
 * Un día de «Tu semana». [letter] es la inicial del día (L M X J V S D); [sessionName] es null si es descanso.
 */
data class HomeWeekDay(
    val date: LocalDate,
    val letter: String,
    val isToday: Boolean,
    val kind: HomeDayKind,
    val sessionName: String?,
)

/** La siguiente sesión del programa después de un día: su fecha y su nombre (lo que lee el descanso de «Tu semana»). */
data class HomeNextSession(val date: LocalDate, val sessionName: String)

/**
 * Línea del programa activo en la cabecera de Home. [weekIndex] y [weekCount] son null sin sesiones; [weekLabel] y [phase]
 * también pueden ser null cuando no se sabe.
 */
data class HomeProgramLine(
    val name: String,
    val weekLabel: String?,
    val weekIndex: Int?,
    val weekCount: Int?,
    val phase: String?,
)

/**
 * Datos de la cabecera y de «Tu semana» de Home, en puro Kotlin. Parte de las sesiones de la semana actual que ya resuelve
 * [HomeSessionResolver] y del programa activo; no lee ViewModels ni base de datos.
 */
object HomeWeekResolver {

    private val letters = listOf("L", "M", "X", "J", "V", "S", "D")

    /**
     * Los 7 días desde el último [startDay] ISO (1 = lunes) que no sea posterior a [today]; sin [startDay], desde el lunes.
     * Cada día toma la sesión de ese día ISO: la principal si hay varias, o la primera.
     */
    fun week(sessions: List<TodaySessionItem>, startDay: Int?, today: LocalDate): List<HomeWeekDay> {
        val startIso = startDay?.coerceIn(1, 7) ?: 1
        val offset = Math.floorMod(today.dayOfWeek.value - startIso, 7)
        val first = today.minusDays(offset.toLong())
        return (0 until 7).map { i ->
            val date = first.plusDays(i.toLong())
            val dayItems = sessions.filter { it.dayOfWeek == date.dayOfWeek.value }
            val item = dayItems.firstOrNull { it.session.isMainSession } ?: dayItems.firstOrNull()
            HomeWeekDay(
                date = date,
                letter = letters[date.dayOfWeek.value - 1],
                isToday = date == today,
                kind = kindOf(item, date, today),
                sessionName = item?.session?.name,
            )
        }
    }

    private fun kindOf(item: TodaySessionItem?, date: LocalDate, today: LocalDate): HomeDayKind = when {
        item == null -> HomeDayKind.REST
        item.isOngoing -> HomeDayKind.ONGOING
        item.isCompleted -> HomeDayKind.DONE
        date.isBefore(today) -> HomeDayKind.MISSED
        else -> HomeDayKind.PLANNED
    }

    /**
     * La siguiente sesión del programa después de [after] (un día de descanso, por ejemplo). Primero mira «Tu semana» ([weekDays]);
     * si no queda nada en ella, sigue por las semanas que vienen después de la de [sessions]. Cada una cae en los 7 días que siguen a
     * la anterior, con el día ISO de cada sesión; un programa calendarizado usa las fechas de su calendario. Null si no hay programa,
     * no se sabe en qué semana va o no queda ninguna sesión.
     */
    fun nextSession(
        program: Program?,
        sessions: List<TodaySessionItem>,
        weekDays: List<HomeWeekDay>,
        after: LocalDate,
    ): HomeNextSession? {
        weekDays.firstOrNull { it.date.isAfter(after) && it.sessionName != null }?.let { day ->
            return HomeNextSession(day.date, day.sessionName.orEmpty())
        }
        val windowStart = weekDays.firstOrNull()?.date ?: return null
        if (program == null) return null
        val weekId = sessions.firstOrNull()?.location?.weekId ?: return null
        val weeks = program.allWeekLocations()
        val current = weeks.indexOfFirst { it.week.id == weekId }
        if (current < 0) return null
        val calendar = ProgramCalendarEngine.project(program).takeIf { it.enabled }
        val startIso = windowStart.dayOfWeek.value
        return weeks.drop(current + 1).withIndex().flatMap { (ahead, location) ->
            // La principal va primero: si dos sesiones caen el mismo día, gana la principal.
            location.week.sessions.sortedBy { if (it.isMainSession) 0 else 1 }.mapNotNull { session ->
                val date = if (calendar?.weeks?.any { it.weekId == location.week.id || it.weekInstanceId == location.week.id } == true) {
                    calendar.scheduledDateFor(session, location.week.id)
                } else {
                    // La semana que sigue a «Tu semana» (ahead = 0) cae en los 7 días siguientes a ella.
                    (session.dayOfWeek ?: session.assignedDays.firstOrNull())?.let { iso ->
                        windowStart.plusDays(7L * (ahead + 1) + Math.floorMod(iso - startIso, 7))
                    }
                }
                date?.takeIf { it.isAfter(after) }?.let { HomeNextSession(it, session.name) }
            }
        }.minByOrNull { it.date }
    }

    /**
     * Línea del programa activo. «Semana N de M» sale de la posición, entre todas las semanas del programa, de la semana de la
     * primera sesión; con un ciclo simple de una sola semana, «Semana N» con el número de ciclo. La fase sale del objetivo del
     * bloque de esa semana. Sin sesiones solo queda el nombre. [program] nulo = sin programa activo, y devuelve null.
     * [today] no cambia la línea; va en la firma por el contrato de la cabecera.
     */
    fun programLine(
        program: Program?,
        sessions: List<TodaySessionItem>,
        @Suppress("UNUSED_PARAMETER") today: LocalDate,
    ): HomeProgramLine? {
        if (program == null) return null
        val weekId = sessions.firstOrNull()?.location?.weekId
        val weeks = program.allWeekLocations()
        val position = weekId?.let { id -> weeks.indexOfFirst { it.week.id == id } }?.takeIf { it >= 0 }
        val index = position?.plus(1)
        val count = if (index != null) weeks.size else null
        val weekLabel = when {
            index == null || count == null -> null
            count == 1 && program.isSimpleProgram && program.simpleProgramKind == SimpleProgramKind.CYCLIC ->
                program.runState?.cycleNumber?.let { "Semana $it" }
            else -> "Semana $index de $count"
        }
        val phase = weekId
            ?.let { ProgramHierarchyIndex(program).locateWeek(it)?.block?.goal }
            ?.let(::homeBlockPhase)
        return HomeProgramLine(
            name = program.name,
            weekLabel = weekLabel,
            weekIndex = index,
            weekCount = count,
            phase = phase,
        )
    }
}

/** Nombre de la fase en la cabecera. Exhaustivo: un objetivo nuevo obliga a elegir su texto en español. */
internal fun homeBlockPhase(goal: BlockGoal): String = when (goal) {
    BlockGoal.ACCUMULATION -> "Fase de acumulación"
    BlockGoal.INTENSIFICATION -> "Fase de intensificación"
    BlockGoal.SPECIFICITY -> "Fase de especificidad"
    BlockGoal.REALIZATION -> "Fase de realización"
    BlockGoal.DELOAD -> "Fase de descarga"
    BlockGoal.DENSITY -> "Fase de densidad metabólica"
    BlockGoal.PEAK -> "Fase de pico"
    BlockGoal.TAPER -> "Fase de puesta a punto"
    BlockGoal.CUSTOM -> "Fase personalizada"
}
