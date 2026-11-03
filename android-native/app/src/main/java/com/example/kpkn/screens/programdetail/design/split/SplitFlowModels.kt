package com.example.kpkn.screens.programdetail.design.split

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.domain.text.SpanishPlurals
import com.example.kpkn.domain.training.SessionDurationEstimator
import com.example.kpkn.domain.training.split.DayKind
import com.example.kpkn.domain.training.split.DayPlan
import com.example.kpkn.domain.training.split.DayRole
import com.example.kpkn.domain.training.split.MigrationPlan
import com.example.kpkn.domain.training.split.MoveKind
import com.example.kpkn.domain.training.split.PlaceReason
import com.example.kpkn.domain.training.split.PlanDecision
import com.example.kpkn.domain.training.split.PlanWarning
import com.example.kpkn.domain.training.split.ProgramWeeks
import com.example.kpkn.domain.training.split.SplitDayMarks
import com.example.kpkn.domain.training.split.SplitMuscleGroups
import com.example.kpkn.domain.training.split.WarningKind
import com.example.kpkn.domain.training.split.WarningSeverity
import com.example.kpkn.domain.training.split.WeekPlan
import com.example.kpkn.domain.training.split.FocusSummary
import java.time.LocalDate
import java.util.Locale

/*
 * REPARTO · modelos de pantalla (punto 23 de «Pulido v1»).
 *
 * Todo lo que las pantallas de reparto pintan, ya calculado y sin Android: la agenda de siete días (antes y después), los avisos con su
 * negrita, el volumen y la frecuencia antes → después, el veredicto de la revisión y el mapa «de dónde sale cada sesión». Las pantallas solo
 * pintan estos modelos; así se prueban con el motor de verdad (`SplitFlowModelsTest`) y las hojas de render las alimentan con los datos
 * de muestra del lienzo, sin depender del motor.
 */

/** Un trozo de texto con o sin negrita (las frases del lienzo llevan `<b>`). */
internal data class RpSpan(val text: String, val bold: Boolean = false)

internal typealias RpRich = List<RpSpan>

internal fun RpRich.plain(): String = joinToString("") { it.text }

/** Texto sin negrita. */
internal fun rpPlain(text: String): RpRich = listOf(RpSpan(text))

/** Una ejercicio de una sesión con su prescripción tal cual viaja: series × repeticiones · RIR · descanso. */
internal data class RpExerciseLine(
    val name: String,
    val prescription: String,
    /** «Empuje» cuando el ejercicio viene de otra sesión; null si se queda. */
    val movedFrom: String? = null,
    /** Identificador de la superserie, o null: las superseries van juntas y se rotulan. */
    val supersetId: String? = null,
)

/** Una casilla del selector «Mover a». */
internal data class RpMoveTarget(val weekday: Int, val letter: String, val here: Boolean, val enabled: Boolean, val description: String)

/** Una sesión de la agenda. [kind] = OPEN con asa, LOCKED con check (hecha), EXTRA sin ninguno (no se toca). */
internal data class RpSession(
    val id: String,
    val weekday: Int,
    val title: String,
    val focus: String,
    val detail: String,
    val role: DayRole,
    val kind: DayKind = DayKind.OPEN,
    val exercises: List<RpExerciseLine> = emptyList(),
    val moveTargets: List<RpMoveTarget> = emptyList(),
    /** Semana vacía: la sesión es una plantilla propuesta (borde discontinuo) y [alternatives] sus otras plantillas. */
    val template: Boolean = false,
    val alternatives: List<String> = emptyList(),
    /** Vista «Antes»: solo se lee, sin asa ni detalle. */
    val readOnly: Boolean = false,
) {
    val draggable: Boolean get() = kind == DayKind.OPEN && !readOnly
}

/** Un día de la agenda: LUN 3. Puede no tener sesión (Descanso). */
internal data class RpDay(val weekday: Int, val date: Int?, val sessions: List<RpSession>) {
    val rest: Boolean get() = sessions.isEmpty()
}

internal enum class RpSeverity { WARN, OK, INFO }

/** El aviso que abre las decisiones («Ver cómo ajustarlo»). */
internal enum class RpNoticeAction { ADJUST_TIME }

internal data class RpNotice(val severity: RpSeverity, val text: RpRich, val action: RpNoticeAction? = null)

/** Una fila de «Volumen y frecuencia»: series después (barra) y antes (marca), y las veces por semana. */
internal data class RpVolumeRow(val label: String, val before: Double, val after: Double, val freqBefore: Int, val freqAfter: Int) {
    val freqUp: Boolean get() = freqAfter > freqBefore
    val freqDown: Boolean get() = freqAfter < freqBefore

    /** «2×» o «1→2×». */
    val freqText: String get() = if (freqBefore == freqAfter) "$freqAfter×" else "$freqBefore→$freqAfter×"
}

/** Cifras de la revisión: «cambian de sesión», «ejercicios», «series». */
internal data class RpReviewSummary(
    val verdict: String,
    val moved: Int,
    val exercises: Int,
    val cardio: Int,
    val sets: Int,
    val setsBefore: Int,
    val lockedCount: Int,
) {
    val movedLabel: String get() = "cambian de sesión"
    val exercisesLabel: String get() = "ejercicios" + if (cardio > 0) " y $cardio de cardio" else ""
    val setsLabel: String get() = "series" + if (sets == setsBefore) ", las mismas" else ", antes $setsBefore"
}

/** Una sesión del mapa «De dónde sale cada sesión»: cuántos ejercicios van a cada sesión nueva. */
internal data class RpMapRow(val name: String, val total: Int, val locked: Boolean, val parts: List<RpMapPart>, val caption: String)

internal data class RpMapPart(val title: String, val count: Int, val role: DayRole)

/** Una sesión nueva en la revisión «Por sesión». */
internal data class RpReviewSession(
    val title: String,
    val role: DayRole,
    /** «Lun 3 · 6 ej · 66 min». */
    val meta: String,
    val from: String,
    val locked: Boolean,
    val rows: List<RpReviewRow>,
)

/** Una fila de ejercicio de la revisión; [supersetId] agrupa las filas de una superserie. */
internal data class RpReviewRow(val name: String, val prescription: String, val tag: String?, val moved: Boolean, val locked: Boolean, val supersetId: String?)

/** Una decisión con propuesta: el ejercicio, por qué, dónde va ahora y a qué días puede ir. */
internal data class RpPlaceDecision(
    val unitKey: String,
    val title: String,
    val explanation: String,
    val current: String,
    val options: List<RpPlaceOption>,
)

internal data class RpPlaceOption(val dayIndex: Int, val title: String, val on: Boolean)

/** La decisión del tiempo por sesión. */
internal data class RpTimeDecision(val targetMinutes: Int, val explanation: String, val fit: Boolean, val boldPrefix: String? = null)

/** Todo lo que pinta la hoja «Antes de aplicar». */
internal data class RpDecisionsModel(val places: List<RpPlaceDecision>, val time: RpTimeDecision?)

/** Lo que muestra el resumen tras aplicar. */
internal data class RpAppliedSummary(
    val splitName: String,
    val weekLabel: String,
    val exercises: Int,
    val sessions: Int,
    val sets: Int,
    val setsBefore: Int,
    val moved: Int,
    val weekdays: List<Int>,
    /** «Personalizado, 4 días (lunes, martes, jueves y viernes)»: cómo estaba la semana, para el texto de «deshecho». */
    val beforeText: String = "",
)

internal object SplitFlowModels {

    /** `LUN … DOM` de la agenda. */
    val WEEKDAY_CAPS: List<String> = listOf("LUN", "MAR", "MIÉ", "JUE", "VIE", "SÁB", "DOM")

    /** `Lun … Dom` de la revisión. */
    val WEEKDAY_ABBR: List<String> = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")

    // ─── Fechas ─────────────────────────────────────────────────────────────────────────────────

    /** El número de día del mes de cada día de la semana (índice 0 = lunes), o null si la semana no tiene fechas. */
    fun weekDates(week: ProgramWeek?): List<Int?> {
        val none = List<Int?>(7) { null }
        week ?: return none
        val fromMap = week.trainingDayDates.mapNotNull { (weekday, date) ->
            runCatching { LocalDate.parse(date) }.getOrNull()?.let { weekday to it.dayOfMonth }
        }.toMap()
        val start = week.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        return (1..7).map { weekday ->
            fromMap[weekday] ?: start?.let { first ->
                val offset = (weekday - first.dayOfWeek.value + 7) % 7
                first.plusDays(offset.toLong()).dayOfMonth
            }
        }
    }

    // ─── Texto de un ejercicio ──────────────────────────────────────────────────────────────────

    /** «4 × 5–6 · RIR 2 · 3 min»; el cardio, «15 min». Lo que falta (RIR, descanso) no se escribe. */
    fun prescription(exercise: Exercise): String {
        exercise.cardioDetails?.let { cardio ->
            val minutes = cardio.targetDurationSeconds?.takeIf { it > 0 }?.let { (it + 30) / 60 }
            return if (minutes != null) "$minutes min" else "Cardio"
        }
        val sets = exercise.sets.filterNot { it.isEmptySlot }
        if (sets.isEmpty()) return "Sin series"
        val first = sets.first()
        val range = first.targetRepsRange
        val reps = when {
            range != null -> if (range.min == range.max) "${range.min}" else "${range.min}–${range.max}"
            first.targetReps != null -> "${first.targetReps}"
            first.targetDuration != null -> "${first.targetDuration} s"
            else -> null
        }
        val parts = buildList {
            add(if (reps != null) "${sets.size} × $reps" else "${sets.size} series")
            first.targetRIR?.let { add("RIR $it") }
            exercise.restTime?.takeIf { it > 0 }?.let { add(restText(it)) }
        }
        return parts.joinToString(" · ")
    }

    /** 180 → «3 min»; 150 → «2,5 min»; 45 → «45 s». */
    fun restText(seconds: Int): String =
        if (seconds >= 60) "${(seconds / 60.0).let { if (it % 1.0 == 0.0) it.toInt().toString() else String.format(Locale.ROOT, "%.1f", it).replace('.', ',') }} min" else "$seconds s"

    /** «6 ejercicios · 66 min» (los de fuerza; el cardio no cuenta como ejercicio). */
    fun detailLine(session: Session): String {
        val count = session.allExercises().count { it.cardioDetails == null }
        val minutes = SessionDurationEstimator.estimate(session).totalMinutes
        return "${SpanishPlurals.exercises(count)} · $minutes min"
    }

    private fun focusOf(session: Session, resolver: com.example.kpkn.domain.training.split.ExerciseTraitResolver): String =
        FocusSummary.of(session, resolver).ifBlank { "Sin ejercicios" }

    // ─── Agenda ─────────────────────────────────────────────────────────────────────────────────

    private fun weekOf(program: Program, weekId: String): ProgramWeek? =
        ProgramWeeks.all(program).firstOrNull { it.week.id == weekId }?.week

    /** «Antes»: la semana tal como está hoy (solo lectura). */
    fun beforeAgenda(
        program: Program,
        weekId: String,
        resolver: com.example.kpkn.domain.training.split.ExerciseTraitResolver,
    ): List<RpDay> {
        val week = weekOf(program, weekId)
        val dates = weekDates(week)
        val sessions = week?.sessions.orEmpty()
        return (1..7).map { weekday ->
            val here = sessions.filter { (it.dayOfWeek?.takeIf { d -> d in 1..7 } ?: it.assignedDays.firstOrNull { d -> d in 1..7 }) == weekday }
            RpDay(
                weekday = weekday,
                date = dates[weekday - 1],
                sessions = here.map { session ->
                    RpSession(
                        id = session.id,
                        weekday = weekday,
                        title = session.name,
                        focus = focusOf(session, resolver),
                        detail = detailLine(session),
                        role = SplitDayMarks.of(session.scheduleLabel?.takeIf { it.isNotBlank() } ?: session.name).role.let { if (it == DayRole.REST) DayRole.MIX else it },
                        readOnly = true,
                    )
                },
            )
        }
    }

    /** «Después»: la semana base del plan, con sus ejercicios y de dónde viene cada uno. */
    fun afterAgenda(plan: MigrationPlan.Ok): List<RpDay> {
        val base = plan.base
        val week = weekOf(plan.program, base.weekId)
        val dates = weekDates(week)
        val sessions = week?.sessions.orEmpty().associateBy { it.id }
        val occupied = base.days.filter { it.kind != DayKind.OPEN }.map { it.weekday }.toSet()
        val byWeekday = base.days.groupBy { it.weekday }
        return (1..7).map { weekday ->
            val here = byWeekday[weekday].orEmpty().sortedBy { it.kind.ordinal }
            RpDay(
                weekday = weekday,
                date = dates[weekday - 1],
                sessions = here.map { day ->
                    val session = sessions[day.sessionId]
                    val moves = base.moves.filter { it.toTitle == day.title }.associateBy { it.exerciseId }
                    val lines = session?.allExercises().orEmpty().map { exercise ->
                        val move = moves[exercise.id]
                        RpExerciseLine(
                            name = exercise.name,
                            prescription = prescription(exercise),
                            movedFrom = if (day.kind == DayKind.OPEN && move?.kind == MoveKind.MOVED) move.fromTitle else null,
                            supersetId = exercise.supersetId?.takeIf { it.isNotBlank() },
                        )
                    }
                    val strength = session?.allExercises()?.count { it.cardioDetails == null } ?: day.exerciseCount
                    RpSession(
                        id = day.sessionId,
                        weekday = weekday,
                        title = day.title,
                        focus = day.focus.ifBlank { "Sin ejercicios" },
                        detail = when (day.kind) {
                            DayKind.LOCKED -> "Hecha · se queda como está"
                            DayKind.EXTRA -> "Se queda como está"
                            DayKind.OPEN -> "${SpanishPlurals.exercises(strength)} · ${day.minutes} min"
                        },
                        role = day.mark.role.let { if (it == DayRole.REST) DayRole.MIX else it },
                        kind = day.kind,
                        exercises = lines,
                        moveTargets = if (day.kind == DayKind.OPEN) moveTargets(weekday, base.days, occupied) else emptyList(),
                    )
                },
            )
        }
    }

    private fun moveTargets(from: Int, days: List<DayPlan>, lockedWeekdays: Set<Int>): List<RpMoveTarget> = (1..7).map { weekday ->
        val occupant = days.firstOrNull { it.weekday == weekday && it.kind == DayKind.OPEN }
        val name = SplitDayMarks.WEEKDAY_NAMES[weekday - 1]
        RpMoveTarget(
            weekday = weekday,
            letter = SplitDayMarks.letterOf(weekday),
            here = weekday == from,
            enabled = weekday !in lockedWeekdays,
            description = if (occupant != null && weekday != from) "$name, intercambia con ${occupant.title}" else name,
        )
    }

    // ─── Avisos ─────────────────────────────────────────────────────────────────────────────────

    private fun join(items: List<String>): String =
        if (items.size < 2) items.joinToString("") else items.dropLast(1).joinToString(", ") + " y " + items.last()

    private fun daysPhrase(days: List<Int>): String = join(days.map { "el ${SplitDayMarks.WEEKDAY_NAMES[it - 1]}" })

    private fun bold(text: String) = RpSpan(text, bold = true)

    /** Las frases del motor con la negrita del lienzo; unidas, son exactamente `PlanWarning.text()`. */
    fun richText(warning: PlanWarning): RpRich {
        fun <T> param(name: String): T {
            @Suppress("UNCHECKED_CAST")
            return warning.params.getValue(name) as T
        }
        return when (warning.kind) {
            WarningKind.DAYS_CHANGED -> {
                val to = param<Int>("to")
                val from = param<Int>("from")
                val added = param<List<Int>>("added")
                val removed = param<List<Int>>("removed")
                listOf(
                    RpSpan("Este reparto usa "),
                    bold(SpanishPlurals.days(to)),
                    RpSpan(
                        " y tu semana tiene $from." +
                            (if (added.isNotEmpty()) " Añadimos ${daysPhrase(added)}." else "") +
                            (if (removed.isNotEmpty()) " Quitamos ${daysPhrase(removed)}." else "") +
                            " Arrastra una sesión si prefieres otro día.",
                    ),
                )
            }
            WarningKind.FREQ_DOWN -> listOf(
                RpSpan("Pierdes frecuencia en "),
                bold(param<String>("muscle").lowercase()),
                RpSpan(": de ${param<Int>("from")}× a ${param<Int>("to")}× por semana."),
            )
            WarningKind.FREQ_UP -> listOf(
                RpSpan("Ganas frecuencia en "),
                bold(join(param<List<String>>("muscles").map { it.lowercase() })),
                RpSpan(": cada uno se entrena más veces por semana con las mismas series."),
            )
            WarningKind.ADJACENT_HEAVY -> listOf(
                bold(param("a")),
                RpSpan(" y "),
                bold(param("b")),
                RpSpan(" caen en días seguidos y los dos cargan ${param<String>("muscle").lowercase()}: solo 24 h de descanso."),
            )
            WarningKind.SHORT_DAY -> listOf(
                bold(param("title")),
                RpSpan(" queda con ${SpanishPlurals.exercises(param("count"))}: no hay más que encajen con su foco."),
            )
            WarningKind.TIME_OVER -> listOf(
                bold(param("title")),
                RpSpan(" dura unos ${param<Int>("minutes")} min y tu tiempo por sesión es de ${param<Int>("target")} min."),
            )
            else -> rpPlain(warning.text())
        }
    }

    private fun severityOf(warning: PlanWarning): RpSeverity = when {
        warning.kind == WarningKind.FREQ_UP || warning.kind == WarningKind.LOCKED_KEPT -> RpSeverity.OK
        warning.severity == WarningSeverity.INFO -> RpSeverity.INFO
        else -> RpSeverity.WARN
    }

    /**
     * Los avisos de la vista previa. «Ya hecho» sale primero y con las palabras del lienzo («Lunes y martes ya están hechos…»);
     * el aviso de tiempo ofrece «Ver cómo ajustarlo».
     */
    fun notices(plan: MigrationPlan.Ok): List<RpNotice> {
        val base = plan.base
        val locked = base.days.filter { it.kind == DayKind.LOCKED }.sortedBy { it.weekday }
        val out = mutableListOf<RpNotice>()
        if (locked.isNotEmpty()) {
            val names = join(locked.map { SplitDayMarks.WEEKDAY_NAMES[it.weekday - 1] }).replaceFirstChar { it.titlecase(Locale.forLanguageTag("es")) }
            val open = base.days.filter { it.kind == DayKind.OPEN }.sortedBy { it.weekday }
            out += RpNotice(
                RpSeverity.OK,
                listOf(
                    bold(names),
                    RpSpan(
                        (if (locked.size > 1) " ya están hechos" else " ya está hecho") +
                            ": se quedan como están y cuentan en el volumen de la semana." +
                            if (open.isNotEmpty()) " El reparto nuevo cubre ${daysPhrase(open.map { it.weekday })}." else "",
                    ),
                ),
            )
        }
        plan.warnings.filter { it.kind != WarningKind.LOCKED_KEPT }.forEach { warning ->
            out += RpNotice(
                severity = severityOf(warning),
                text = richText(warning),
                action = if (warning.kind == WarningKind.TIME_OVER) RpNoticeAction.ADJUST_TIME else null,
            )
        }
        return out
    }

    // ─── Volumen y frecuencia ───────────────────────────────────────────────────────────────────

    /** Los once grupos de siempre y, solo si tienen series, los demás. */
    fun volumeRows(week: WeekPlan): List<RpVolumeRow> = SplitMuscleGroups.ALL.mapIndexedNotNull { index, group ->
        val volume = week.volume[group.label]
        val before = volume?.first ?: 0.0
        val after = volume?.second ?: 0.0
        val frequency = week.frequency[group.label]
        if (index >= 11 && before <= 0.0 && after <= 0.0) null
        else RpVolumeRow(group.label, before, after, frequency?.first ?: 0, frequency?.second ?: 0)
    }

    /** «9», «18,5». */
    fun number(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.ROOT, "%.1f", value).replace('.', ',')

    // ─── Revisión ───────────────────────────────────────────────────────────────────────────────

    private fun strengthCount(session: Session) = session.allExercises().count { it.cardioDetails == null }

    private fun setsOf(session: Session) = session.allExercises().filter { it.cardioDetails == null }.sumOf { e -> e.sets.count { !it.isEmptySlot } }

    fun reviewSummary(before: Program, plan: MigrationPlan.Ok, targetMinutes: Int?): RpReviewSummary {
        val base = plan.base
        val afterWeek = weekOf(plan.program, base.weekId)
        val beforeWeek = weekOf(before, base.weekId)
        val afterSessions = afterWeek?.sessions.orEmpty()
        val exercises = afterSessions.sumOf { strengthCount(it) }
        val cardio = afterSessions.sumOf { s -> s.allExercises().count { it.cardioDetails != null } }
        val sets = afterSessions.sumOf { setsOf(it) }
        val setsBefore = beforeWeek?.sessions.orEmpty().sumOf { setsOf(it) }
        val moved = base.moves.count { it.kind == MoveKind.MOVED }
        val trimmed = base.trims.sumOf { it.removedSets }
        val verdict = if (trimmed > 0) {
            "Se recortan ${SpanishPlurals.sets(trimmed)} para que quepan en ${targetMinutes ?: 0} min."
        } else {
            "No pierdes nada: ${SpanishPlurals.exercises(exercises)} y ${SpanishPlurals.sets(sets)} ${if (exercises == 1 && sets == 1) "pasa" else "pasan"} al nuevo reparto."
        }
        return RpReviewSummary(verdict, moved, exercises, cardio, sets, setsBefore, base.days.count { it.kind == DayKind.LOCKED })
    }

    /** «De dónde sale cada sesión»: por cada sesión de hoy, adónde van sus ejercicios. */
    fun reviewMap(before: Program, plan: MigrationPlan.Ok): List<RpMapRow> {
        val base = plan.base
        val roleOf = base.days.associate { it.title to it.mark.role.let { r -> if (r == DayRole.REST) DayRole.MIX else r } }
        val lockedTitles = base.days.filter { it.kind == DayKind.LOCKED }.map { it.title }.toSet()
        val originals = weekOf(before, base.weekId)?.sessions.orEmpty().map { it.name }.distinct()
        return originals.mapNotNull { name ->
            val own = base.moves.filter { it.fromTitle == name }
            if (own.isEmpty()) return@mapNotNull null
            val perDestination = own.groupBy { it.toTitle }.map { (title, list) -> RpMapPart(title, list.size, roleOf[title] ?: DayRole.MIX) }
            val same = perDestination.size == 1 && perDestination.first().title == name
            RpMapRow(
                name = name,
                total = own.size,
                locked = name in lockedTitles,
                parts = perDestination,
                caption = if (same) "Se queda como estaba" else "→ " + perDestination.joinToString(" · ") { "${it.title} ${it.count}" },
            )
        }
    }

    /** Cada sesión nueva con sus ejercicios; los que vienen de otra llevan «de Empuje». */
    fun reviewSessions(plan: MigrationPlan.Ok): List<RpReviewSession> {
        val base = plan.base
        val week = weekOf(plan.program, base.weekId)
        val dates = weekDates(week)
        val sessions = week?.sessions.orEmpty().associateBy { it.id }
        return base.days.sortedBy { it.weekday }.map { day ->
            val session = sessions[day.sessionId]
            val moves = base.moves.filter { it.toTitle == day.title }
            val byId = moves.associateBy { it.exerciseId }
            val stay = moves.count { it.kind == MoveKind.STAYS }
            val from = moves.filter { it.kind == MoveKind.MOVED }.groupBy { it.fromTitle }.map { (title, list) -> "${list.size} de $title" }
            val fromText = when {
                day.kind == DayKind.LOCKED -> "Hecha · se queda como está"
                day.kind == DayKind.EXTRA -> "Se queda como está"
                from.isNotEmpty() -> (if (stay > 0) "$stay siguen aquí · " else "") + "vienen ${join(from)}"
                else -> "Sigue como estaba"
            }
            val n = session?.let { strengthCount(it) } ?: day.exerciseCount
            val weekdayText = if (day.weekday in 1..7) {
                SplitFlowModels.WEEKDAY_ABBR[day.weekday - 1] + (dates[day.weekday - 1]?.let { " $it" } ?: "")
            } else {
                ""
            }
            RpReviewSession(
                title = day.title,
                role = day.mark.role.let { if (it == DayRole.REST) DayRole.MIX else it },
                meta = listOf(weekdayText, "$n ej", "${day.minutes} min").filter { it.isNotBlank() }.joinToString(" · "),
                from = fromText,
                locked = day.kind == DayKind.LOCKED,
                rows = session?.allExercises().orEmpty().map { exercise ->
                    val movedFrom = if (day.kind == DayKind.OPEN) byId[exercise.id]?.takeIf { it.kind == MoveKind.MOVED }?.fromTitle else null
                    RpReviewRow(
                        name = exercise.name,
                        prescription = prescription(exercise),
                        tag = when {
                            movedFrom != null -> "de $movedFrom"
                            day.kind == DayKind.LOCKED -> "Hecha"
                            else -> null
                        },
                        moved = movedFrom != null,
                        locked = day.kind == DayKind.LOCKED,
                        supersetId = exercise.supersetId?.takeIf { it.isNotBlank() },
                    )
                },
            )
        }
    }

    // ─── Decisiones ─────────────────────────────────────────────────────────────────────────────

    fun decisions(plan: MigrationPlan.Ok): RpDecisionsModel {
        val titles = plan.base.days.filter { it.kind == DayKind.OPEN }.associate { it.dayIndex to it.title }
        val places = plan.decisions.filterIsInstance<PlanDecision.PlaceUnit>().map { decision ->
            val name = decision.exerciseNames.joinToString(" + ")
            val current = titles[decision.proposedDay].orEmpty()
            RpPlaceDecision(
                unitKey = decision.unitKey,
                title = name,
                explanation = when (decision.reason) {
                    PlaceReason.CARDIO -> "El cardio no depende del músculo: va al día con menos carga."
                    PlaceReason.CORE -> "Es un remate de core: encaja en cualquier día."
                    PlaceReason.UNCLASSIFIED -> "No se pudo clasificar: va al día más corto."
                    PlaceReason.MISFIT -> "No encaja del todo con ningún día: era la única forma de llenarlos todos."
                } + " Ahora va en",
                current = current,
                options = decision.options.mapNotNull { index -> titles[index]?.let { RpPlaceOption(index, it, index == decision.proposedDay) } },
            )
        }
        val fit = plan.decisions.filterIsInstance<PlanDecision.FitTime>().firstOrNull()?.let { time ->
            val over = time.overDays.keys.toList()
            val overText = when {
                over.isEmpty() -> "Todas las sesiones caben."
                over.size == 1 -> "${over.first()} se pasa."
                else -> "${join(over)} se pasan."
            }
            RpTimeDecision(
                boldPrefix = join(over).takeIf { over.isNotEmpty() },
                targetMinutes = time.targetMinutes,
                explanation = if (time.applied) {
                    "${if (over.isEmpty()) "Todas las sesiones caben" else "${join(over)} ${if (over.size > 1) "quedan dentro de tu tiempo" else "queda dentro de tu tiempo"}"}. " +
                        "Se quitan ${SpanishPlurals.sets(time.wouldRemoveSets)} de accesorios; los ejercicios principales no se tocan."
                } else {
                    "$overText Puedes dejarlas así o quitar series de accesorios hasta que quepan."
                },
                fit = time.applied,
            )
        }
        return RpDecisionsModel(places, fit)
    }

    // ─── Resumen tras aplicar ───────────────────────────────────────────────────────────────────

    /** «Personalizado, 4 días (lunes, martes, jueves y viernes)»: la semana [weekId] de [program] tal como está. */
    fun weekDescription(program: Program, weekId: String, startDay: Int): String {
        val now = com.example.kpkn.domain.training.split.SplitCatalog.now(program, weekId, startDay)
        val days = weekOf(program, weekId)?.sessions.orEmpty()
            .mapNotNull { it.dayOfWeek?.takeIf { d -> d in 1..7 } ?: it.assignedDays.firstOrNull { d -> d in 1..7 } }.distinct().sorted()
        val label = now?.label ?: com.example.kpkn.domain.training.split.SplitCatalog.PERSONALIZED
        return "$label, ${SpanishPlurals.days(days.size)}" + if (days.isNotEmpty()) " (${join(days.map { SplitDayMarks.WEEKDAY_NAMES[it - 1] })})" else ""
    }

    fun appliedSummary(before: Program, plan: MigrationPlan.Ok, weekLabel: String, targetMinutes: Int?, startDay: Int = 1): RpAppliedSummary {
        val review = reviewSummary(before, plan, targetMinutes)
        return RpAppliedSummary(
            beforeText = weekDescription(before, plan.base.weekId, startDay),
            splitName = plan.splitName,
            weekLabel = weekLabel,
            exercises = review.exercises,
            sessions = plan.base.days.count { it.kind != DayKind.EXTRA },
            sets = review.sets,
            setsBefore = review.setsBefore,
            moved = review.moved,
            weekdays = plan.base.days.filter { it.kind != DayKind.EXTRA }.map { it.weekday }.sorted(),
        )
    }

    /** El resumen de una semana rehecha por el camino de siempre (sesiones vacías o de plantilla): sin plan, se cuenta lo que quedó. */
    fun appliedSummaryOf(before: Program, after: Program, weekId: String, splitName: String, weekLabel: String, startDay: Int = 1): RpAppliedSummary {
        val sessions = weekOf(after, weekId)?.sessions.orEmpty()
        val days = sessions.mapNotNull { it.dayOfWeek?.takeIf { d -> d in 1..7 } ?: it.assignedDays.firstOrNull { d -> d in 1..7 } }.distinct().sorted()
        return RpAppliedSummary(
            splitName = splitName,
            weekLabel = weekLabel,
            exercises = sessions.sumOf { strengthCount(it) },
            sessions = sessions.size,
            sets = sessions.sumOf { setsOf(it) },
            setsBefore = weekOf(before, weekId)?.sessions.orEmpty().sumOf { setsOf(it) },
            moved = 0,
            weekdays = days,
            beforeText = weekDescription(before, weekId, startDay),
        )
    }

    /** La hoja de resumen: tras aplicar o, con [undone], tras deshacer. */
    fun appliedModel(summary: RpAppliedSummary, undone: Boolean, canUndo: Boolean = true): RpAppliedModel =
        if (undone) {
            RpAppliedModel(
                undone = true,
                title = "Reparto deshecho",
                sub = "Tu ${summary.weekLabel.lowercase()} volvió a como estaba" + (if (summary.beforeText.isNotBlank()) ": ${summary.beforeText}" else "") + ", con las mismas series.",
                stats = emptyList(),
                note = null,
            )
        } else {
            RpAppliedModel(
                undone = false,
                title = "Reparto aplicado",
                sub = "${summary.splitName} está en tu ${summary.weekLabel.lowercase()}.",
                stats = listOf(
                    "Ejercicios repartidos" to "${summary.exercises} en ${summary.sessions} sesiones",
                    "Series" to "${summary.sets} · ${if (summary.sets == summary.setsBefore) "las mismas" else "antes ${summary.setsBefore}"}",
                    "Cambian de sesión" to "${summary.moved}",
                    "Días de entreno" to weekdaysShort(summary.weekdays),
                ),
                note = "Puedes deshacerlo desde el ⋯ de la semana hasta que edites sus sesiones.",
                canUndo = canUndo,
            )
        }

    /** «lun, mar, jue y vie». */
    fun weekdaysShort(weekdays: List<Int>): String =
        join(weekdays.map { SplitDayMarks.WEEKDAY_NAMES[it - 1].take(3) })
}
