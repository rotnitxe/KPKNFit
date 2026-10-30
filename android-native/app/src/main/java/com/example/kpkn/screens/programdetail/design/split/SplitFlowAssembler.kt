package com.example.kpkn.screens.programdetail.design.split

import com.example.kpkn.data.models.Program
import com.example.kpkn.data.splits.SplitTemplate
import com.example.kpkn.domain.text.SpanishPlurals
import com.example.kpkn.domain.training.split.DayKind
import com.example.kpkn.domain.training.split.DayRole
import com.example.kpkn.domain.training.split.ExerciseTraitResolver
import com.example.kpkn.domain.training.split.PlanDecision
import com.example.kpkn.domain.training.split.SplitCatalog
import com.example.kpkn.domain.training.split.SplitCatalogEntry
import com.example.kpkn.domain.training.split.SplitCatalogRules
import com.example.kpkn.domain.training.split.SplitDayMarks

/*
 * REPARTO · de lo que calcula el motor a lo que pintan las pantallas (punto 23). Funciones puras: la vista previa y la revisión salen del
 * estado del flujo ([SplitFlowState]) y de unos pocos datos de la semana abierta.
 */

/** La semana abierta: cómo se llama, qué fechas tiene y qué día es hoy dentro de ella. */
internal data class RpWeekContext(
    val program: Program,
    val weekId: String,
    /** «Semana 5». */
    val label: String,
    /** «3–9 nov»; null si la semana no tiene fechas. */
    val range: String?,
    val startDay: Int = 1,
    val todayWeekday: Int? = null,
    val targetMinutes: Int? = null,
)

internal object SplitFlowAssembler {

    private const val COMPETITION = "competición"

    /** Las etiquetas «4 días · 2× por músculo · Intermedio» de un reparto. */
    fun chips(entry: SplitCatalogEntry): List<Pair<String?, String?>> = buildList {
        add(SpanishPlurals.days(entry.trainingDays) to null)
        entry.timesPerMuscle?.let { add("$it×" to "por músculo") }
        add(null to entry.levelLabel)
    }

    /** La entrada del catálogo de un reparto; el propio de la persona se resume con sus datos. */
    fun entryFor(split: SplitTemplate): SplitCatalogEntry = SplitCatalog.entryOf(split)

    fun scopeText(emptyWeek: Boolean): String =
        if (emptyWeek) "Solo esta semana · Con plantillas" else "Solo esta semana · Conservar y reubicar"

    /** «Repartos para tu semana»: el elegido y hasta seis más (los que encajan con tus días, o los recomendados). */
    fun picker(current: SplitCatalogEntry, haveDays: Int?, startDay: Int): List<RpPickItem> {
        val all = SplitCatalog.entries()
        val pool = if (haveDays != null && haveDays > 0) all.filter { it.trainingDays == haveDays && !it.powerlifting } else all.filter { it.recommended }
        val others = pool.filter { it.id != current.id }.take(6)
        return (listOf(current) + others).map { entry ->
            RpPickItem(
                id = entry.id,
                name = entry.name,
                strip = entry.strip(startDay),
                caption = entry.timesPerMuscle?.let { "$it× por músculo" } ?: entry.levelLabel,
                on = entry.id == current.id,
            )
        }
    }

    fun preview(
        week: RpWeekContext,
        split: SplitTemplate,
        state: SplitFlowState,
        resolver: ExerciseTraitResolver,
            ): RpPreviewModel {
        val entry = entryFor(split)
        val now = SplitCatalog.now(week.program, week.weekId, week.startDay)
        val haveDays = now?.trainingDays
        val competition = state.refusal?.reason?.contains(COMPETITION, ignoreCase = true) == true
        val hoy = when {
            competition -> "con competición"
            now != null -> "hoy: ${now.label}"
            else -> "hoy: sin sesiones"
        }
        val eyebrow = listOfNotNull(week.label, week.range, hoy).joinToString(" · ")
        val dates = SplitFlowModels.weekDates(
            com.example.kpkn.domain.training.split.ProgramWeeks.all(week.program).firstOrNull { it.week.id == week.weekId }?.week,
        )
        val plan = state.plan
        val days: List<RpDay> = when {
            state.refusal != null -> SplitFlowModels.beforeAgenda(week.program, week.weekId, resolver)
            state.view == RpView.BEFORE -> SplitFlowModels.beforeAgenda(week.program, week.weekId, resolver)
            state.emptyWeek -> templateAgenda(state, dates).ifEmpty { emptyDays(dates) }
            plan != null -> SplitFlowModels.afterAgenda(plan)
            else -> SplitFlowModels.beforeAgenda(week.program, week.weekId, resolver)
        }
        val notices = when {
            state.emptyWeek -> listOf(
                RpNotice(
                    RpSeverity.INFO,
                    listOf(
                        RpSpan("Esta semana no tiene sesiones: el reparto crea las "),
                        RpSpan(SpanishPlurals.sessions(entry.trainingDays), bold = true),
                        RpSpan(" con los ejercicios de las plantillas más compatibles. No hay nada que migrar."),
                    ),
                ),
            )
            plan != null -> SplitFlowModels.notices(plan)
            else -> emptyList()
        }
        val refusal = state.refusal?.let { refused ->
            RpRefusal(
                title = refused.reason,
                body = if (competition) {
                    "No se pueden repartir los ejercicios de una semana con competición: mover sus sesiones rompería la preparación del día de competencia. El resto de tus semanas sí."
                } else {
                    null
                },
                action = if (refused.canUseCleanOrPrebuilt) "Aplicar con sesiones vacías" else null,
                note = if (competition) "Cambiar de reparto en esta semana queda desactivado con cualquier reparto, hasta que pase la competición." else null,
            )
        }
        return RpPreviewModel(
            eyebrow = eyebrow,
            title = entry.name,
            lead = entry.description,
            chips = if (refusal != null) emptyList() else chips(entry),
            view = state.view,
            days = days,
            todayWeekday = week.todayWeekday,
            selectedWeekday = state.selectedWeekday,
            dirty = state.dirty,
            scopeText = scopeText(state.emptyWeek),
            notices = notices,
            pickerTrailing = if (haveDays != null && haveDays > 0) SpanishPlurals.days(haveDays) else "recomendados",
            picker = picker(entry, haveDays, week.startDay),
            pickerTotal = SplitCatalog.entries().size,
            volume = if (state.emptyWeek || plan == null) null else SplitFlowModels.volumeRows(plan.base),
            ctaLabel = if (state.emptyWeek) "Aplicar reparto" else "Revisar cambios",
            ctaEnabled = !state.calculating && state.refusal == null && (plan != null || state.emptyWeek),
            calculating = state.calculating && plan == null && !state.emptyWeek && state.refusal == null,
            refusal = refusal,
            revision = state.revision,
            emptyWeek = state.emptyWeek,
        )
    }

    /** La agenda de una semana vacía: una sesión por plantilla propuesta. */
    fun templateAgenda(state: SplitFlowState, dates: List<Int?>): List<RpDay> = (1..7).map { weekday ->
        RpDay(
            weekday = weekday,
            date = dates.getOrNull(weekday - 1),
            sessions = state.templateDays.filter { it.weekday == weekday }.map { day ->
                RpSession(
                    id = "template-$weekday",
                    weekday = weekday,
                    title = day.title,
                    focus = day.template,
                    detail = "${day.count} ejercicios de plantilla",
                    role = SplitDayMarks.of(day.label).role.let { if (it == DayRole.REST) DayRole.MIX else it },
                    kind = DayKind.OPEN,
                    template = true,
                    alternatives = day.alternatives,
                    moveTargets = (1..7).map { target ->
                        RpMoveTarget(
                            weekday = target,
                            letter = SplitDayMarks.letterOf(target),
                            here = target == weekday,
                            enabled = true,
                            description = SplitDayMarks.WEEKDAY_NAMES[target - 1],
                        )
                    },
                )
            },
        )
    }

    private fun emptyDays(dates: List<Int?>): List<RpDay> = (1..7).map { RpDay(it, dates.getOrNull(it - 1), emptyList()) }

    // ─── Revisión ───────────────────────────────────────────────────────────────────────────────

    fun review(week: RpWeekContext, state: SplitFlowState, tab: RpReviewTab): RpReviewModel? {
        val plan = state.plan ?: return null
        val summary = SplitFlowModels.reviewSummary(week.program, plan, week.targetMinutes)
        val proposals = plan.decisions.filterIsInstance<PlanDecision.PlaceUnit>().map { decision ->
            val title = plan.base.days.firstOrNull { it.kind == DayKind.OPEN && it.dayIndex == decision.proposedDay }?.title.orEmpty()
            RpProposal(decision.exerciseNames.joinToString(" + "), " va en $title.")
        }
        val fit = plan.decisions.filterIsInstance<PlanDecision.FitTime>().firstOrNull()
        val timeWarning = fit?.takeIf { !it.applied && it.overDays.isNotEmpty() }?.let { "Alguna sesión pasa de tus ${it.targetMinutes} min." }
        val locked = summary.lockedCount
        val volume = SplitFlowModels.volumeRows(plan.base)
        val trimmed = plan.base.trims.isNotEmpty()
        return RpReviewModel(
            eyebrow = "${week.label} · ${plan.splitName}",
            summary = summary,
            lockedNote = if (locked > 0) listOf(
                RpSpan(if (locked == 1) "1 sesión hecha" else "$locked sesiones hechas", bold = true),
                RpSpan(" se quedan tal cual y cuentan en el volumen de la semana."),
            ) else null,
            proposals = proposals,
            timeWarning = timeWarning,
            tab = tab,
            map = SplitFlowModels.reviewMap(week.program, plan),
            sessions = SplitFlowModels.reviewSessions(plan),
            volume = volume,
            muscleNote = "Series semanales por músculo (principal cuenta 1 y secundario 0,5, como en el volumen de la app). " +
                if (trimmed) "Las diferencias vienen de las series recortadas para tu tiempo." else "Son las mismas porque los ejercicios se mueven enteros.",
        )
    }

    /** Los días de entreno de la semana abierta, para decidir qué repartos «encajan». */
    fun haveDays(week: RpWeekContext): Int? = SplitCatalog.now(week.program, week.weekId, week.startDay)?.trainingDays

    /** Nombre llano de un reparto del catálogo o del propio. */
    fun displayName(split: SplitTemplate): String = SplitCatalogRules.displayName(split)

    /** La tira de «Ahora» de una semana, si tiene sesiones. */
    fun nowStrip(week: RpWeekContext) = SplitCatalog.now(week.program, week.weekId, week.startDay)

    /** Letras de la semana de [startDay]. */
    fun letters(startDay: Int): List<String> = (0 until 7).map { SplitDayMarks.letterOf(((startDay - 1 + it) % 7) + 1) }
}
