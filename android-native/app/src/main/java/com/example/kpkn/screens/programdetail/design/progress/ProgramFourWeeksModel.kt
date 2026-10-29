package com.example.kpkn.screens.programdetail.design.progress

import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.KeyDateType
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.NativeProgressionProposal
import com.example.kpkn.data.models.NativeProgressionProposalKind
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramRunStatus
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.models.isSimpleProgram
import com.example.kpkn.domain.time.ActivityLocalDate
import com.example.kpkn.domain.training.ExerciseProgress
import com.example.kpkn.domain.training.ExerciseProgressMetric
import com.example.kpkn.domain.training.ExerciseProgressPoint
import com.example.kpkn.domain.training.ExerciseProgressWindow
import com.example.kpkn.domain.training.ProgramDetailHelpers
import com.example.kpkn.domain.training.ProgramProgressEngine
import com.example.kpkn.domain.training.TrainingWindow
import com.example.kpkn.domain.training.TrainingWindowType
import com.example.kpkn.domain.training.VolumeCalculator
import com.example.kpkn.domain.training.VolumeCountingMode
import com.example.kpkn.domain.training.progress.LiftTrendEngine
import com.example.kpkn.domain.training.progress.LiftTrendStatus
import com.example.kpkn.domain.training.progress.WeeklyWorkEngine
import com.example.kpkn.screens.onboarding.design.formatDecimalEs
import com.example.kpkn.screens.programdetail.components.mergePersonalizedTargets
import com.example.kpkn.screens.programdetail.components.volumeMuscleRows
import com.example.kpkn.screens.programdetail.design.ProgramVolumeMuscle
import com.example.kpkn.screens.programdetail.design.programPhaseOf
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/*
 * Modelo de «Tus últimas 4 semanas» (lienzo Arreglos v1, escenas p-progress, p-4w, p-4w-mid y p-4w-end) y de su tarjeta de entrada en
 * Progreso. Sin Compose ni android.*: la prueba JVM decide cada cifra, cada cuadro del calendario y cada frase.
 *
 * Las 4 semanas son las de calendario (de lunes a domingo) que terminan en la de hoy, aunque crucen un cambio de bloque. A cada una le toca
 * una semana del programa: la de sus registros (la más repetida) o, sin registros, la que dice el orden del programa contando hacia atrás
 * desde la semana en curso. Las semanas anteriores al programa no cuentan: con menos de 4, la página cuenta las que haya y lo dice. Una
 * rutina (programa simple de una semana que se repite) cuenta igual que Progreso, por semana de calendario ([routineCalendarOf]).
 */

/** Semanas de calendario de la página. */
internal const val FOUR_WEEKS = 4

/** Ejercicios de «Fuerza». */
private const val FOUR_WEEKS_LIFTS = 4

/** Récords que se listan como mucho. */
private const val FOUR_WEEKS_RECORDS = 5

/** Molestias que se listan como mucho (las mismas que Progreso). */
private const val FOUR_WEEKS_DISCOMFORTS = 4

/** Diferencia de RIR (medio frente al pedido) que ya no cuenta como «según el plan». La misma que Progreso. */
private const val FOUR_WEEKS_RIR_GAP = 0.5

/** Las indirectas cuentan la mitad, como «Series por músculo» de Progreso. */
private val FOUR_WEEKS_VOLUME_MODE = VolumeCountingMode.SECONDARY

/** Un día del calendario: hecho (hay un entreno registrado), saltado (una sesión obligatoria que ya pasó sin registro) o nada. */
internal enum class FourWeeksDayState { REST, DONE, SKIPPED }

/** Un cuadro del calendario: su fecha, su estado y si es hoy. */
internal data class FourWeeksDay(val date: LocalDate, val state: FourWeeksDayState, val isToday: Boolean)

/**
 * Una semana del plan, lo que la página necesita de ella: su id, su posición (0 = la primera), sus sesiones obligatorias con su día
 * (1 = lunes; null = sin día), sus series y su RIR previstos y el nombre de su fase («Intensificación»), si se sabe.
 */
internal data class FourWeeksPlanWeek(
    val id: String,
    val position: Int,
    val requiredSessionIds: List<String>,
    val sessionDays: Map<String, Int?>,
    val plannedSets: Int,
    val plannedRir: Double?,
    val phaseLabel: String? = null,
)

/** Una semana de calendario de la ventana: su lunes, la semana del plan que le toca (null = ninguna) y sus registros (por fecha). */
internal data class FourWeeksSlot(val monday: LocalDate, val plan: FourWeeksPlanWeek?, val logs: List<WorkoutLog>)

/** Una columna de «Semana a semana»: «S4 · hoy», las series duras hechas y previstas, el tonelaje y los minutos. */
internal data class FourWeeksColumn(
    val label: String,
    val isCurrent: Boolean,
    val hardSets: Int,
    val plannedSets: Int,
    val tonnageKg: Double,
    val minutes: Int,
)

/** Un récord: el ejercicio, «142,5 kg × 5 · mié 22» y «Mejor serie» o «Mejor marca». */
internal data class FourWeeksRecord(val exercise: String, val detail: String, val kind: String, val date: LocalDate)

/** La etiqueta de un músculo; [low] la pinta en ámbar. */
internal enum class FourWeeksMuscleTag(val label: String, val low: Boolean) {
    IN_RANGE("En rango", false),
    JUST("Justo", false),
    ABOVE("Por encima", true),
    BELOW("Por debajo", true),
    NONE("Sin series", true),
}

/** Una fila de «Músculos»: el nombre, «15 series por semana · rango 8–22» y su etiqueta (null = sin rangos con que comparar). */
internal data class FourWeeksMuscle(val name: String, val caption: String, val tag: FourWeeksMuscleTag?)

/** Una fila de «Esfuerzo y molestias»: título, pie y etiqueta; [low] pinta la etiqueta en ámbar. */
internal data class FourWeeksNote(val title: String, val caption: String, val tag: String?, val low: Boolean)

/** Una semana de «Lo que viene»: su id (para el color de su fase), «Semana 5 · Intensificación» y «5 sesiones · 56 series · …». */
internal data class FourWeeksNext(val weekId: String, val title: String, val line: String)

/** La frase de arriba en piezas: [start] + [bold] en negrita + [rest] + [muted] en gris. */
internal data class FourWeeksLead(val start: String, val bold: String?, val rest: String, val muted: String?) {
    val text: String
        get() = start + bold.orEmpty() + rest + muted.orEmpty()
}

/** Todo lo que pintan la página y su tarjeta de entrada. */
internal data class ProgramFourWeeks(
    /** «Motor 6 · semanas 1 a 4» (la página lo pasa a mayúsculas). */
    val eyebrow: String,
    /** «6 – 29 oct · la semana 4 sigue en curso». */
    val range: String,
    val lead: FourWeeksLead,
    val sessionsDone: Int,
    val sessionsPlanned: Int,
    val hardSetsDone: Int,
    val hardSetsPlanned: Int,
    val tonnageKg: Double,
    val minutes: Int,
    /** Los 28 días de las 4 semanas de calendario, de lunes a domingo; la página enseña solo los de las semanas que cuentan. */
    val days: List<FourWeeksDay>,
    /** Semanas que cuentan (de 1 a 4): las últimas de [days]. */
    val countedWeeks: Int,
    val skipped: Int,
    val lifts: List<ProgressLiftRow>,
    val records: List<FourWeeksRecord>,
    val weeks: List<FourWeeksColumn>,
    val muscles: List<FourWeeksMuscle>,
    val effort: List<FourWeeksNote>,
    val next: List<FourWeeksNext>,
    /** La línea de la tarjeta de entrada; null si no hay nada que decir. */
    val entryLine: String?,
) {
    /** Los días de las semanas que cuentan, de lunes a domingo. */
    val shownDays: List<FourWeeksDay>
        get() = days.takeLast(countedWeeks * 7)
}

/**
 * La semana en curso del programa: la del estado de ejecución del propio programa si está activo; si no, [activeWeekId] (el estado activo
 * de la app), siempre en su id de plantilla.
 */
internal fun fourWeeksCurrentWeekId(program: Program, activeWeekId: String? = null): String? {
    val run = program.runState?.takeIf { it.status == ProgramRunStatus.ACTIVE }
    val raw = run?.weekId?.takeIf { it.isNotBlank() } ?: run?.weekInstanceId?.takeIf { it.isNotBlank() } ?: activeWeekId?.takeIf { it.isNotBlank() }
    return raw?.let(::templateWeekIdOf)
}

/**
 * Calcula la página para [program] con sus [logs] (los del programa). [currentWeekId] es la semana en curso ([fourWeeksCurrentWeekId]).
 * Con [exerciseIndex] se calculan los músculos (sin él, la lista va vacía: la tarjeta de entrada no la necesita). [proposal] es el trozo
 * de la propuesta pendiente para «Lo que viene» ([fourWeeksProposalText]).
 */
internal fun buildProgramFourWeeks(
    program: Program,
    logs: List<WorkoutLog>,
    currentWeekId: String?,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
    exerciseIndex: Map<String, ExerciseMuscleInfo>? = null,
    proposal: String? = null,
): ProgramFourWeeks {
    val baseWeeks = progressWeeksOf(program)
    val ownLogs = logs.filter { it.programId == program.id && it.weekId != null }
    val routine = if (program.isSimpleProgram && baseWeeks.size == 1) routineCalendarOf(baseWeeks.single(), ownLogs, today, zone) else null
    val planWeeks = routine?.weeks?.map { it.toPlan(null) } ?: fourWeeksPlanOf(program, baseWeeks)
    val calendarLogs = routine?.logs ?: ownLogs
    val nowWeekId = routine?.currentWeekId ?: currentWeekId
    val dated = calendarLogs.mapNotNull { log -> fourWeeksDateOf(log, zone)?.takeIf { !it.isAfter(today) }?.let { log to it } }
    val slots = fourWeeksSlotsOf(planWeeks, dated, nowWeekId, today)
    val core = fourWeeksCoreOf(slots, dated, today)
    val counted = slots.takeLast(core.countedWeeks)
    val start = counted.first().monday
    val windowIds = counted.flatMapTo(HashSet()) { slot -> slot.logs.map { it.id } }
    // Los registros originales de la ventana (en una rutina, [calendarLogs] lleva la semana cambiada a la de su fecha).
    val windowLogs = ownLogs.filter { it.id in windowIds }

    val rows = if (windowLogs.isEmpty()) {
        emptyList()
    } else {
        buildProgramProgress(
            program = program,
            logs = windowLogs,
            scope = ProgressScope.PROGRAM,
            selectedBlockId = null,
            selectedWeekId = null,
            currentWeekId = currentWeekId,
            maxLifts = Int.MAX_VALUE,
            today = today,
        ).allLifts
    }
    val baseWeekIds = baseWeeks.mapTo(HashSet()) { it.id }
    val liftLogs = if (routine != null) windowLogs else windowLogs.filter { it.weekId in baseWeekIds }
    val trendsByKey = LiftTrendEngine.trends(liftLogs).associateBy { it.exerciseKey }
    val sessionsByLift = liftSessionsOf(liftLogs)
    val lead = fourWeeksLeadOf(
        done = core.sessionsDone,
        planned = core.sessionsPlanned,
        rows = rows,
        downWeeksOf = { key -> fourWeeksDownWeeks(sessionsByLift[key].orEmpty()) },
        stalledWeeksOf = { key -> trendsByKey[key]?.let(::stalledWeeksOf) },
    )
    val lifts = rows.filter { it.currentKg != null }.take(FOUR_WEEKS_LIFTS)
    val records = fourWeeksRecordsOf(ownLogs, windowLogs, start, today, zone)

    val muscles = if (exerciseIndex == null) {
        emptyList()
    } else {
        fourWeeksMusclesOf(fourWeeksVolumeRows(program, ownLogs, counted, start, today, zone, exerciseIndex))
    }
    val rir = WeeklyWorkEngine.byWeek(windowLogs.map { it.copy(weekId = ALL_WEEKS) }, listOf(ALL_WEEKS)).single().rirMean
    val effort = listOfNotNull(fourWeeksRirNote(rir, fourWeeksPlannedRir(counted.mapNotNull { it.plan }))) +
        fourWeeksDiscomfortsOf(counted.mapIndexed { index, slot -> weekNumberOf(slot, index) to slot.logs })
    val current = slots.last().plan
    val next = if (routine == null && current != null) fourWeeksNextOf(planWeeks, current, proposal) else emptyList()
    val numbers = counted.mapNotNull { slot -> slot.plan?.let { it.position + 1 } }
    return ProgramFourWeeks(
        eyebrow = fourWeeksEyebrowOf(program.name, numbers.minOrNull(), numbers.maxOrNull()),
        range = fourWeeksRangeOf(start, today, core.ongoingWeek),
        lead = lead,
        sessionsDone = core.sessionsDone,
        sessionsPlanned = core.sessionsPlanned,
        hardSetsDone = core.columns.sumOf { it.hardSets },
        hardSetsPlanned = core.columns.sumOf { it.plannedSets },
        tonnageKg = core.columns.sumOf { it.tonnageKg },
        minutes = core.columns.sumOf { it.minutes },
        days = core.days,
        countedWeeks = core.countedWeeks,
        skipped = core.skipped,
        lifts = lifts,
        records = records,
        weeks = core.columns,
        muscles = muscles,
        effort = effort,
        next = next,
        entryLine = fourWeeksEntryLine(core.sessionsDone, core.sessionsPlanned, rows, records.size),
    )
}

/** Id con el que se agrupan todos los registros de la ventana para el RIR medio. */
private const val ALL_WEEKS = "all"

/** Las semanas del programa con el nombre de su fase, como en la cinta de fases. */
private fun fourWeeksPlanOf(program: Program, weeks: List<ProgressScopeWeek>): List<FourWeeksPlanWeek> {
    val roadmap = ProgramDetailHelpers.buildRoadmapBlocks(program)
    val phaseByWeek = HashMap<String, String?>()
    for (block in roadmap) {
        val blockWeeks = ProgramDetailHelpers.getWeeksForBlock(block.id, roadmap, program)
        val label = programPhaseOf(block.goal, blockWeeks.any { it.keyDateType == KeyDateType.COMPETITION })?.label
        blockWeeks.forEach { phaseByWeek[it.id] = label }
    }
    return weeks.map { it.toPlan(phaseByWeek[it.id]) }
}

private fun ProgressScopeWeek.toPlan(phase: String?) = FourWeeksPlanWeek(
    id = id,
    position = position,
    requiredSessionIds = requiredSessionIds,
    sessionDays = sessionDays,
    plannedSets = plannedSets,
    plannedRir = plannedRir,
    phaseLabel = phase,
)

/** El id de plantilla de una semana (las instancias de ciclo «inst_c2_<id>» llevan a su plantilla). */
private fun templateWeekIdOf(weekId: String): String = ProgramProgressEngine.templateWeekIdFromInstance(weekId) ?: weekId

/** El día de un registro: su fecha real si la tiene; si no, la del registro en [zone]. Igual que Progreso. */
internal fun fourWeeksDateOf(log: WorkoutLog, zone: ZoneId): LocalDate? =
    log.actualDate?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: ActivityLocalDate.fromInstantIsoOrDatePrefixOrNull(log.date, zone)

private fun mondayOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/**
 * Las 4 semanas de calendario que terminan en la de [today], cada una con sus registros ([dated], por fecha) y su semana del plan: la más
 * repetida entre sus registros o, sin registros, la que toca contando hacia atrás desde [currentWeekId] en [planWeeks] (null si cae antes
 * del programa o no hay semana en curso).
 */
internal fun fourWeeksSlotsOf(
    planWeeks: List<FourWeeksPlanWeek>,
    dated: List<Pair<WorkoutLog, LocalDate>>,
    currentWeekId: String?,
    today: LocalDate,
): List<FourWeeksSlot> {
    val thisMonday = mondayOf(today)
    val byId = planWeeks.associateBy { it.id }
    val currentPosition = planWeeks.indexOfFirst { it.id == currentWeekId }
    return (0 until FOUR_WEEKS).map { index ->
        val back = FOUR_WEEKS - 1 - index
        val monday = thisMonday.minusWeeks(back.toLong())
        val sunday = monday.plusDays(6)
        val weekLogs = dated.filter { (_, date) -> !date.isBefore(monday) && !date.isAfter(sunday) }
            .sortedWith(compareBy<Pair<WorkoutLog, LocalDate>> { it.second }.thenBy { it.first.id })
            .map { it.first }
        val dominant = weekLogs.mapNotNull { log -> log.weekId?.let(::templateWeekIdOf)?.takeIf { it in byId } }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
        val plan = dominant?.let { byId[it] } ?: if (currentPosition < 0) null else planWeeks.getOrNull(currentPosition - back)
        FourWeeksSlot(monday, plan, weekLogs)
    }
}

/** Lo que sale de las semanas sin mirar los ejercicios: sesiones, calendario, semana a semana y si la semana actual sigue abierta. */
internal data class FourWeeksCore(
    val countedWeeks: Int,
    val sessionsDone: Int,
    val sessionsPlanned: Int,
    val skipped: Int,
    val days: List<FourWeeksDay>,
    val columns: List<FourWeeksColumn>,
    /** Número de la semana en curso si le quedan sesiones obligatorias por registrar; null si no. */
    val ongoingWeek: Int?,
)

/**
 * Cuenta las semanas de [slots]. Cuentan desde la primera con semana del plan o con registros hasta la de hoy (al menos la de hoy). Una
 * sesión obligatoria está saltada si no tiene registro en la ventana y su día ya pasó (sin día: si su semana ya terminó); la de hoy aún no
 * cuenta. Las sesiones previstas son las hechas más las saltadas, así que «14 de 16» nunca pasa de 16 de 16.
 */
internal fun fourWeeksCoreOf(slots: List<FourWeeksSlot>, dated: List<Pair<WorkoutLog, LocalDate>>, today: LocalDate): FourWeeksCore {
    val firstIndex = slots.indexOfFirst { it.plan != null || it.logs.isNotEmpty() }.let { if (it < 0) slots.size - 1 else it }
    val counted = slots.drop(firstIndex)
    val windowLogs = counted.flatMap { it.logs }
    val logged = windowLogs.mapTo(HashSet()) { log -> log.weekId?.let(::templateWeekIdOf) to log.sessionId }
    val dateById = dated.associate { (log, date) -> log.id to date }
    val skippedBySlot = slots.map { slot -> skippedDatesOf(slot, logged, today) }
    val days = slots.flatMapIndexed { index, slot ->
        val doneDates = slot.logs.mapNotNullTo(HashSet()) { dateById[it.id] }
        val skippedDates = skippedBySlot[index].filterNotNull().toSet()
        (0L until 7L).map { offset ->
            val date = slot.monday.plusDays(offset)
            val state = when {
                date in doneDates -> FourWeeksDayState.DONE
                date in skippedDates -> FourWeeksDayState.SKIPPED
                else -> FourWeeksDayState.REST
            }
            FourWeeksDay(date, state, date == today)
        }
    }
    val countedSkipped = skippedBySlot.drop(firstIndex)
    val work = WeeklyWorkEngine.byWeek(
        logs = counted.flatMap { slot -> slot.logs.map { it.copy(weekId = slotId(slot)) } },
        weekOrder = counted.map(::slotId),
        plannedSetsByWeek = counted.associate { slotId(it) to (it.plan?.plannedSets ?: 0) },
    ).associateBy { it.weekId }
    val columns = counted.mapIndexed { index, slot ->
        val week = work[slotId(slot)]
        val current = index == counted.lastIndex
        FourWeeksColumn(
            label = "S${weekNumberOf(slot, index)}" + if (current) " · hoy" else "",
            isCurrent = current,
            hardSets = week?.hardSets ?: 0,
            plannedSets = slot.plan?.plannedSets ?: 0,
            tonnageKg = week?.tonnageKg ?: 0.0,
            minutes = week?.minutes ?: 0,
        )
    }
    val skipped = countedSkipped.sumOf { it.size }
    val done = windowLogs.size
    val thisWeek = slots.last().plan
    val ongoing = thisWeek?.takeIf { plan -> plan.requiredSessionIds.any { (plan.id to it) !in logged } }?.let { it.position + 1 }
    return FourWeeksCore(
        countedWeeks = counted.size,
        sessionsDone = done,
        sessionsPlanned = done + skipped,
        skipped = skipped,
        days = days,
        columns = columns,
        ongoingWeek = ongoing,
    )
}

/** Id con el que se agrupan los registros de una semana de calendario. */
private fun slotId(slot: FourWeeksSlot): String = "cal:${slot.monday}"

/** Número de la semana: la del plan («S3») o, sin semana del plan, su orden en la ventana. */
private fun weekNumberOf(slot: FourWeeksSlot, index: Int): Int = slot.plan?.let { it.position + 1 } ?: (index + 1)

/** Las fechas de las sesiones obligatorias saltadas de [slot] (null = sin día, no va al calendario). */
private fun skippedDatesOf(slot: FourWeeksSlot, logged: Set<Pair<String?, String>>, today: LocalDate): List<LocalDate?> {
    val plan = slot.plan ?: return emptyList()
    val sunday = slot.monday.plusDays(6)
    val result = mutableListOf<LocalDate?>()
    for (sessionId in plan.requiredSessionIds) {
        if ((plan.id to sessionId) in logged) continue
        val date = plan.sessionDays[sessionId]?.takeIf { it in 1..7 }?.let { slot.monday.plusDays(it - 1L) }
        val due = if (date != null) date.isBefore(today) else sunday.isBefore(today)
        if (due) result += date
    }
    return result
}

/** «Motor 6 · semanas 1 a 4», «Motor 6 · semana 1»; sin semanas del plan, solo el nombre. */
internal fun fourWeeksEyebrowOf(name: String, first: Int?, last: Int?): String = when {
    first == null || last == null -> name
    first == last -> "$name · semana $first"
    else -> "$name · semanas $first a $last"
}

/** «6 – 29 oct», «29 sept – 26 oct»; con [ongoingWeek], «· la semana 4 sigue en curso». */
internal fun fourWeeksRangeOf(start: LocalDate, today: LocalDate, ongoingWeek: Int?): String {
    val end = "${today.dayOfMonth} ${monthShortOf(today.monthValue)}"
    val base = when {
        start == today -> end
        start.year == today.year && start.month == today.month -> "${start.dayOfMonth} – $end"
        else -> "${start.dayOfMonth} ${monthShortOf(start.monthValue)} – $end"
    }
    return if (ongoingWeek != null) "$base · la semana $ongoingWeek sigue en curso" else base
}

private fun monthShortOf(month: Int): String =
    listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sept", "oct", "nov", "dic")[month - 1]

/**
 * La frase: «Cumpliste **14 de 16 sesiones**. Sentadilla trasera y press banca suben; _press militar baja desde hace 2 semanas._» Lo que
 * sube sale de [rows] (los ejercicios con tendencia «sube», hasta tres); lo gris, del primero que baja o, si ninguno baja, del primero
 * estancado.
 */
internal fun fourWeeksLeadOf(
    done: Int,
    planned: Int,
    rows: List<ProgressLiftRow>,
    downWeeksOf: (String) -> Int? = { null },
    stalledWeeksOf: (String) -> Int? = { null },
): FourWeeksLead {
    val ups = rows.filter { it.status == LiftTrendStatus.UP }
    val upText = ups.takeIf { it.isNotEmpty() }?.let { list ->
        val names = list.take(3).mapIndexed { index, row -> if (index == 0) row.name else lowerFirst(row.name) }
        val subject = if (list.size > 3) names.joinToString(", ") + " y ${list.size - 3} más" else spanishList(names)
        if (list.size == 1) "$subject sube" else "$subject suben"
    }
    val down = rows.firstOrNull { it.status == LiftTrendStatus.DOWN }
    val stalled = rows.firstOrNull { it.status == LiftTrendStatus.STAGNANT }
    val mutedText = when {
        down != null -> {
            val name = lowerFirst(down.name)
            when (val weeks = downWeeksOf(down.key)) {
                null -> "$name baja"
                1 -> "$name baja desde hace 1 semana"
                else -> "$name baja desde hace $weeks semanas"
            }
        }
        stalled != null -> {
            val name = lowerFirst(stalled.name)
            val weeks = stalledWeeksOf(stalled.key)
            if (weeks == null) "$name está parado" else "$name lleva $weeks ${if (weeks == 1) "semana" else "semanas"} parado"
        }
        else -> null
    }
    val (start, bold, close) = when {
        planned > 0 -> Triple("Cumpliste ", "$done de $planned ${if (planned == 1) "sesión" else "sesiones"}", ".")
        done > 0 -> Triple("Registraste ", if (done == 1) "1 sesión" else "$done sesiones", ".")
        else -> Triple("Aún no hay sesiones registradas en estas semanas.", null, "")
    }
    return when {
        upText != null && mutedText != null -> FourWeeksLead(start, bold, "$close $upText; ", "$mutedText.")
        upText != null -> FourWeeksLead(start, bold, "$close $upText.", null)
        mutedText != null -> FourWeeksLead(start, bold, "$close ", "${mutedText.replaceFirstChar { it.uppercase() }}.")
        else -> FourWeeksLead(start, bold, close, null)
    }
}

/**
 * Semanas que lleva bajando un ejercicio: desde la mejor sesión de la ventana anterior (las 3 sesiones antes de las 3 últimas) hasta la
 * última, como mínimo 1. El mismo cálculo que el pie «Baja 2 semanas» de Progreso. Null si no hay sesiones suficientes.
 */
internal fun fourWeeksDownWeeks(sessions: List<LiftSession>): Int? {
    if (sessions.size < LiftTrendEngine.MIN_SESSIONS) return null
    val peak = sessions.dropLast(3).takeLast(3).maxByOrNull { it.e1rm } ?: return null
    return (ChronoUnit.DAYS.between(peak.date, sessions.last().date) / 7).toInt().coerceAtLeast(1)
}

/** El cambio de una fila de fuerza: «+7,5 kg», «−2,5 kg» o «=» (sin cambio o sin dato). */
internal fun fourWeeksDeltaText(delta: Double?): String {
    val tenths = delta?.let { Math.round(it * 10.0) } ?: 0L
    return when {
        tenths > 0 -> "+${kgText(tenths / 10.0)} kg"
        tenths < 0 -> "−${kgText(-tenths / 10.0)} kg"
        else -> "="
    }
}

/** El pie de una fila de fuerza: «142,5 kg · 6 sesiones». */
internal fun fourWeeksLiftCaption(row: ProgressLiftRow): String =
    row.currentKg?.let { "${kgText(it)} kg · ${row.caption}" } ?: row.caption

/**
 * La línea de la tarjeta de entrada: «14 de 16 sesiones · sentadilla trasera +7,5 kg · 2 récords». Cada parte sale solo si tiene dato: el
 * ejercicio es el que más subió en la ventana. Null si no hay ninguna.
 */
internal fun fourWeeksEntryLine(done: Int, planned: Int, rows: List<ProgressLiftRow>, records: Int): String? {
    val sessions = when {
        planned > 0 -> "$done de $planned ${if (planned == 1) "sesión" else "sesiones"}"
        done > 0 -> if (done == 1) "1 sesión" else "$done sesiones"
        else -> null
    }
    val best = rows.filter { row -> row.deltaKg?.let { Math.round(it * 10.0) > 0 } == true }.maxByOrNull { it.deltaKg ?: 0.0 }
    val lift = best?.let { "${lowerFirst(it.name)} ${fourWeeksDeltaText(it.deltaKg)}" }
    val prs = when (records) {
        0 -> null
        1 -> "1 récord"
        else -> "$records récords"
    }
    return listOfNotNull(sessions, lift, prs).takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/**
 * Los récords de la ventana con la detección de [ExerciseProgress] (cada punto marca si supera a todos los anteriores de su misma
 * trayectoria): para cada ejercicio registrado en [windowLogs], su última «Mejor serie» (carga × repeticiones) o «Mejor marca» (e1RM)
 * desde [start], contra todo el historial del programa ([logs]). Una primera vez no es récord. Solo con carga externa. Del más reciente al
 * más antiguo.
 */
internal fun fourWeeksRecordsOf(
    logs: List<WorkoutLog>,
    windowLogs: List<WorkoutLog>,
    start: LocalDate,
    today: LocalDate,
    zone: ZoneId,
): List<FourWeeksRecord> {
    if (windowLogs.isEmpty()) return emptyList()
    return ExerciseProgress.choices(windowLogs).mapNotNull { choice ->
        val bestSet = lastRecordOf(logs, choice.id, ExerciseProgressMetric.BEST_SET, start, today, zone)
        val e1rm = lastRecordOf(logs, choice.id, ExerciseProgressMetric.E1RM, start, today, zone)
        when {
            bestSet != null && (e1rm == null || !e1rm.date.isAfter(bestSet.date)) -> FourWeeksRecord(
                exercise = choice.name,
                detail = "${kgText(bestSet.bestSet.weight)} kg × ${bestSet.bestSet.reps} · ${fourWeeksDayLabel(bestSet.date, today)}",
                kind = "Mejor serie",
                date = bestSet.date,
            )
            e1rm != null -> FourWeeksRecord(
                exercise = choice.name,
                detail = "e1RM ${kgText(Math.round(e1rm.value * 2.0) / 2.0)} kg · ${fourWeeksDayLabel(e1rm.date, today)}",
                kind = "Mejor marca",
                date = e1rm.date,
            )
            else -> null
        }
    }.sortedByDescending { it.date }.take(FOUR_WEEKS_RECORDS)
}

/** El último récord de [metric] de un ejercicio desde [start]; null si no hay ninguno o el ejercicio no tiene esa métrica. */
private fun lastRecordOf(
    logs: List<WorkoutLog>,
    exerciseId: String,
    metric: ExerciseProgressMetric,
    start: LocalDate,
    today: LocalDate,
    zone: ZoneId,
): ExerciseProgressPoint? {
    val report = ExerciseProgress.build(logs, exerciseId, metric, ExerciseProgressWindow.ALL, today, zone)
    if (report.metric != metric) return null
    val seen = HashSet<String>()
    var last: ExerciseProgressPoint? = null
    for (point in report.points) {
        val hadBefore = !seen.add(point.lineage)
        if (point.isPr && hadBefore && point.mode == LoadModeV2.LOAD && !point.date.isBefore(start)) last = point
    }
    return last
}

/** «mié 22», «hoy» y, fuera del mes en curso, «lun 29 sept». */
internal fun fourWeeksDayLabel(date: LocalDate, today: LocalDate): String = progressDayLabel(date, today).lowercase()

/**
 * Las filas de «Músculos» desde las de «Series por músculo» ([rows]: lo hecho de media por semana en [ProgramVolumeMuscle.real], lo
 * previsto en [ProgramVolumeMuscle.sets] y su rango útil). Por debajo del mínimo útil, «Por debajo»; por encima del máximo recuperable,
 * «Por encima»; en el tercio bajo del rango, «Justo»; si no, «En rango». Sin ninguna serie, «Sin series». La primera cifra dice «series»;
 * las demás, solo «por semana». Van en rango primero y lo que falta al final.
 */
internal fun fourWeeksMusclesOf(rows: List<ProgramVolumeMuscle>): List<FourWeeksMuscle> {
    var named = false
    val result = rows.map { row ->
        val done = Math.round(row.real ?: 0.0).toInt()
        val zones = row.zones
        val tag = when {
            done == 0 -> FourWeeksMuscleTag.NONE
            zones == null -> null
            done < zones.minEffective -> FourWeeksMuscleTag.BELOW
            done > zones.maxRecoverable -> FourWeeksMuscleTag.ABOVE
            (done - zones.minEffective) * 3 < zones.maxRecoverable - zones.minEffective -> FourWeeksMuscleTag.JUST
            else -> FourWeeksMuscleTag.IN_RANGE
        }
        val caption = if (done == 0 && row.sets <= 0.0) {
            "Sin trabajo directo en el plan"
        } else {
            val amount = if (!named) {
                named = true
                "$done ${if (done == 1) "serie" else "series"} por semana"
            } else {
                "$done por semana"
            }
            val reference = when {
                zones == null -> ""
                tag == FourWeeksMuscleTag.BELOW || tag == FourWeeksMuscleTag.NONE -> " · mínimo útil ${zones.minEffective}"
                else -> " · rango ${zones.minEffective}–${zones.maxRecoverable}"
            }
            amount + reference
        }
        FourWeeksMuscle(row.name, caption, tag)
    }
    return result.sortedBy { muscle ->
        when (muscle.tag) {
            null, FourWeeksMuscleTag.IN_RANGE -> 0
            FourWeeksMuscleTag.JUST -> 1
            FourWeeksMuscleTag.ABOVE -> 2
            FourWeeksMuscleTag.BELOW -> 3
            FourWeeksMuscleTag.NONE -> 4
        }
    }
}

/**
 * «Series por músculo» de la ventana con las mismas piezas que Progreso ([volumeMuscleRows], indirectas a la mitad): lo hecho entre el
 * lunes [start] y [today], de media por semana (los días que van divididos entre 7); lo previsto, la media de las semanas del plan de
 * [counted]; los rangos, los del perfil si el programa está calibrado.
 */
private fun fourWeeksVolumeRows(
    program: Program,
    logs: List<WorkoutLog>,
    counted: List<FourWeeksSlot>,
    start: LocalDate,
    today: LocalDate,
    zone: ZoneId,
    exerciseIndex: Map<String, ExerciseMuscleInfo>,
): List<ProgramVolumeMuscle> {
    val days = ChronoUnit.DAYS.between(start, today) + 1
    val window = TrainingWindow(start, today, zone, TrainingWindowType.LAST_DAYS, days / 7.0)
    val done = VolumeCalculator.calculateCompletedVolume(logs, exerciseIndex, window, FOUR_WEEKS_VOLUME_MODE, false, program.id)
        .muscles.mapValues { it.value.sets }
    val weekById = program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }.associateBy { it.id }
    // En una rutina la semana del plan es «<plantilla>@<lunes>».
    val sessions = counted.mapNotNull { it.plan }.flatMap { plan -> weekById[plan.id.substringBefore('@')]?.sessions.orEmpty() }
    val planned = VolumeCalculator.calculateVolume(sessions, exerciseIndex, counted.size.toDouble(), FOUR_WEEKS_VOLUME_MODE, false)
        .muscles.mapValues { it.value.sets }
    val calibrated = program.volumeRecommendations.isNotEmpty() && program.athleteProfileScore != null
    // Los mismos rangos que Progreso, con los de respaldo de Romboides, Cuello, Antebrazo, Aductores y Core.
    val targets = if (calibrated) mergePersonalizedTargets(program.volumeRecommendations) else null
    return volumeMuscleRows(planned = planned, done = done, targets = targets, priorityByMuscle = emptyMap(), hasLogs = true)
}

/** El RIR previsto de las semanas: la media de su RIR pedido, pesada por sus series. Null si ninguna lo fija. */
internal fun fourWeeksPlannedRir(plans: List<FourWeeksPlanWeek>): Double? {
    val withRir = plans.filter { it.plannedRir != null }
    if (withRir.isEmpty()) return null
    val sets = withRir.sumOf { it.plannedSets }
    val mean = if (sets > 0) withRir.sumOf { it.plannedRir!! * it.plannedSets } / sets else withRir.map { it.plannedRir!! }.average()
    return Math.round(mean * 10.0) / 10.0
}

/**
 * La fila del RIR: «RIR medio 2,1» con «El plan pedía 2: vas donde toca» y «Según el plan»; más de medio punto por debajo, más cerca del
 * fallo (en ámbar); más de medio punto por encima, más lejos. Sin RIR pedido, solo lo registrado. Null sin RIR registrado.
 */
internal fun fourWeeksRirNote(rir: Double?, plannedRir: Double?): FourWeeksNote? {
    if (rir == null) return null
    val title = "RIR medio ${formatRir(rir)}"
    if (plannedRir == null) return FourWeeksNote(title, "El plan no fija un RIR", null, false)
    val asked = "El plan pedía ${formatPlannedRir(plannedRir)}"
    return when {
        plannedRir - rir >= FOUR_WEEKS_RIR_GAP -> FourWeeksNote(title, "$asked: entrenas más cerca del fallo", "Más cerca del fallo", true)
        rir - plannedRir >= FOUR_WEEKS_RIR_GAP -> FourWeeksNote(title, "$asked: te quedas más lejos del fallo", "Más lejos del fallo", false)
        else -> FourWeeksNote(title, "$asked: vas donde toca", "Según el plan", false)
    }
}

/**
 * Las molestias registradas en las semanas ([weeks]: número de semana y sus registros): «Hombro derecho» con «2 veces, las dos en la
 * semana 3» y «Vigilar». De la más repetida a la menos.
 */
internal fun fourWeeksDiscomfortsOf(weeks: List<Pair<Int, List<WorkoutLog>>>): List<FourWeeksNote> {
    val byName = LinkedHashMap<String, MutableList<Int>>()
    for ((week, logs) in weeks) {
        for (log in logs) {
            for (name in log.discomforts.filter { it.isNotBlank() }.distinct()) byName.getOrPut(name) { mutableListOf() } += week
        }
    }
    return byName.entries
        .sortedWith(compareByDescending<Map.Entry<String, MutableList<Int>>> { it.value.size }.thenBy { it.key })
        .take(FOUR_WEEKS_DISCOMFORTS)
        .map { (name, hits) -> FourWeeksNote(name, discomfortCaption(hits), "Vigilar", true) }
}

/** «1 vez, en la semana 3», «2 veces, las dos en la semana 3», «3 veces, todas en la semana 3», «3 veces, en las semanas 2 y 3». */
private fun discomfortCaption(weeks: List<Int>): String {
    val count = weeks.size
    val distinct = weeks.distinct().sorted()
    val times = if (count == 1) "1 vez" else "$count veces"
    return when {
        distinct.size == 1 && count == 1 -> "$times, en la semana ${distinct.single()}"
        distinct.size == 1 && count == 2 -> "$times, las dos en la semana ${distinct.single()}"
        distinct.size == 1 -> "$times, todas en la semana ${distinct.single()}"
        else -> "$times, en las semanas ${spanishList(distinct.map { it.toString() })}"
    }
}

/**
 * «Lo que viene»: las dos semanas del plan que siguen a [current], con «Semana 5 · Intensificación» y «5 sesiones · 56 series». La primera
 * lleva la propuesta pendiente ([proposal]); la última del programa, «cierra el programa».
 */
internal fun fourWeeksNextOf(planWeeks: List<FourWeeksPlanWeek>, current: FourWeeksPlanWeek, proposal: String?): List<FourWeeksNext> {
    val lastPosition = planWeeks.maxOfOrNull { it.position }
    return planWeeks.filter { it.position > current.position }.sortedBy { it.position }.take(2).mapIndexed { index, week ->
        val sessions = week.requiredSessionIds.size
        val parts = listOfNotNull(
            sessions.takeIf { it > 0 }?.let { if (it == 1) "1 sesión" else "$it sesiones" },
            week.plannedSets.takeIf { it > 0 }?.let { if (it == 1) "1 serie" else "$it series" },
            proposal.takeIf { index == 0 },
            "cierra el programa".takeIf { week.position == lastPosition },
        )
        FourWeeksNext(
            weekId = week.id,
            title = "Semana ${week.position + 1}" + (week.phaseLabel?.let { " · $it" } ?: ""),
            line = parts.joinToString(" · "),
        )
    }
}

/**
 * La propuesta pendiente para «Lo que viene»: con una, «propuesta: sentadilla a 105 kg»; con varias, «3 propuestas por revisar». [nameOf]
 * da el nombre del ejercicio de cada propuesta (el de la tarjeta de progresión). Null sin propuestas.
 */
internal fun fourWeeksProposalText(proposals: List<NativeProgressionProposal>, nameOf: (String) -> String?): String? {
    if (proposals.isEmpty()) return null
    if (proposals.size > 1) return "${proposals.size} propuestas por revisar"
    val proposal = proposals.single()
    val name = nameOf(proposal.proposalId)?.trim()?.takeIf { it.isNotEmpty() }?.let(::lowerFirst) ?: "un ejercicio"
    val target = proposal.targetLoadKg?.let { "${kgText(it)} kg" }
    val text = when (proposal.kind) {
        NativeProgressionProposalKind.INCREASE_LOAD -> target?.let { "$name a $it" } ?: "más carga en $name"
        NativeProgressionProposalKind.REDUCE_LOAD -> target?.let { "$name a $it" } ?: "menos carga en $name"
        NativeProgressionProposalKind.HARDER_BODYWEIGHT_VARIANT -> "variante más difícil de $name"
        NativeProgressionProposalKind.EASIER_BODYWEIGHT_VARIANT -> "variante más fácil de $name"
    }
    return "propuesta: $text"
}

/** Tonelaje en toneladas con un decimal: «42,4». */
internal fun fourWeeksTonnes(kg: Double): String = formatDecimalEs(kg / 1000.0, 1)
