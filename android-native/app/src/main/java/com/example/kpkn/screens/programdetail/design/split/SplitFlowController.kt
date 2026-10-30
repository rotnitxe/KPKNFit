package com.example.kpkn.screens.programdetail.design.split

import com.example.kpkn.data.models.Program
import com.example.kpkn.data.sessions.SESSION_TEMPLATES_SYSTEM
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.data.splits.SplitTemplate
import com.example.kpkn.domain.training.SplitApplicationEngine
import com.example.kpkn.domain.training.SplitTemporalScope
import com.example.kpkn.domain.training.split.AuthoredPlans
import com.example.kpkn.domain.training.split.ExerciseTraitResolver
import com.example.kpkn.domain.training.split.MigrationPlan
import com.example.kpkn.domain.training.split.MigrationRequest
import com.example.kpkn.domain.training.split.ProgramWeeks
import com.example.kpkn.domain.training.split.SplitCatalogRules
import com.example.kpkn.domain.training.split.SplitMigrationPlanner
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * REPARTO · estado de la vista previa (punto 23).
 *
 * Guarda la petición vigente del motor de migración (reparto elegido, días, decisiones, tiempo) y el plan que sale de ella. Cada cambio
 * (otro reparto, arrastrar una sesión, una decisión) pide el plan de nuevo fuera del hilo principal; mientras llega, [SplitFlowState.calculating]
 * pinta los esqueletos. Una semana sin sesiones no se migra: enseña la plantilla propuesta de cada día y se aplica con ejercicios de plantilla.
 */

/** Lo que la vista previa le pide al motor; separado para poder sustituirlo en las pruebas. */
internal interface SplitPlannerPort {
    fun plan(request: MigrationRequest): MigrationPlan
    fun rearrange(request: MigrationRequest, plan: MigrationPlan.Ok, from: Int, to: Int): SplitMigrationPlanner.Rearranged
    fun proposeWeekdays(program: Program, weekId: String, split: SplitTemplate, startDay: Int): List<Int>

    object Default : SplitPlannerPort {
        override fun plan(request: MigrationRequest) = SplitMigrationPlanner.plan(request)
        override fun rearrange(request: MigrationRequest, plan: MigrationPlan.Ok, from: Int, to: Int) =
            SplitMigrationPlanner.rearrange(request, plan, from, to)
        override fun proposeWeekdays(program: Program, weekId: String, split: SplitTemplate, startDay: Int) =
            SplitMigrationPlanner.proposeWeekdays(program, weekId, split, startDay)
    }
}

/** «Antes» o «Después» de la semana. */
internal enum class RpView { BEFORE, AFTER }

/** La plantilla propuesta de un día de una semana vacía. */
internal data class RpTemplateDay(val title: String, val label: String, val template: String, val count: Int, val alternatives: List<String>, val weekday: Int)

internal data class SplitFlowState(
    val split: SplitTemplate? = null,
    val calculating: Boolean = false,
    val request: MigrationRequest? = null,
    val plan: MigrationPlan.Ok? = null,
    val refusal: MigrationPlan.Refused? = null,
    val view: RpView = RpView.AFTER,
    val selectedWeekday: Int? = null,
    val dirty: Boolean = false,
    val initialWeekdays: List<Int> = emptyList(),
    val authorPrompt: Boolean = false,
    /** Semana sin sesiones: se enseñan las plantillas propuestas y no hay plan. */
    val templateDays: List<RpTemplateDay> = emptyList(),
    val emptyWeek: Boolean = false,
    /** Sube en cada cambio que re-dibuja la agenda con la entrada escalonada (`rp-anim`). */
    val revision: Int = 0,
)

internal class SplitFlowController(
    private val config: Config,
    private val scope: CoroutineScope,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val planner: SplitPlannerPort = SplitPlannerPort.Default,
) {

    /** Todo lo que no cambia mientras la página está abierta. */
    internal data class Config(
        val program: Program,
        val weekId: String,
        val resolver: ExerciseTraitResolver,
        val completedSessionIds: Set<String> = emptySet(),
        /** Hoy (1..7) si la semana abierta es la semana en curso; null si no lo es. */
        val todayWeekday: Int? = null,
        val startDay: Int = 1,
        val targetMinutes: Int? = null,
        val templates: List<SessionTemplate> = SESSION_TEMPLATES_SYSTEM,
        val nowMs: Long = 0L,
    ) {
        val emptyWeek: Boolean
            get() = ProgramWeeks.all(program).firstOrNull { it.week.id == weekId }?.week?.sessions.isNullOrEmpty()
    }

    private val _state = MutableStateFlow(SplitFlowState(emptyWeek = config.emptyWeek))
    val state: StateFlow<SplitFlowState> = _state.asStateFlow()
    private var job: Job? = null

    /** Elige [split]: propone sus días y pide el plan. */
    fun open(split: SplitTemplate) {
        val weekdays = planner.proposeWeekdays(config.program, config.weekId, split, config.startDay)
        val authored = AuthoredPlans.hasFixedRecipe(config.program)
        val request = MigrationRequest(
            program = config.program,
            split = split,
            weekdays = weekdays,
            resolver = config.resolver,
            baseWeekId = config.weekId,
            scopeWeekIds = listOf(config.weekId),
            scope = SplitTemporalScope.SELECTED_WEEKS,
            completedSessionIds = config.completedSessionIds,
            todayWeekday = config.todayWeekday,
            currentWeekId = config.weekId.takeIf { config.todayWeekday != null },
            targetMinutes = config.targetMinutes,
            fitToTime = false,
            allowAuthored = authored,
            nowMs = config.nowMs,
        )
        _state.update {
            SplitFlowState(
                split = split,
                request = request,
                initialWeekdays = weekdays,
                authorPrompt = authored && !config.emptyWeek,
                emptyWeek = config.emptyWeek,
                revision = it.revision + 1,
                view = RpView.AFTER,
            )
        }
        if (config.emptyWeek) showTemplates(split, weekdays) else recompute(request, markDirty = false)
    }

    /** Vuelve al catálogo. */
    fun close() {
        job?.cancel()
        _state.update { SplitFlowState(emptyWeek = config.emptyWeek, revision = it.revision) }
    }

    fun setView(view: RpView) = _state.update { it.copy(view = view) }

    fun dismissAuthorPrompt() = _state.update { it.copy(authorPrompt = false) }

    fun toggleSelected(weekday: Int) = _state.update { it.copy(selectedWeekday = if (it.selectedWeekday == weekday) null else weekday) }

    /** Mueve la sesión de [from] a [to] (a un día libre se muda; a uno ocupado se intercambian). */
    fun move(from: Int, to: Int) {
        val current = _state.value
        if (from == to) return
        if (current.emptyWeek) {
            moveTemplate(from, to)
            return
        }
        val request = current.request ?: return
        val plan = current.plan ?: return
        job?.cancel()
        val moved = planner.rearrange(request, plan, from, to)
        if (moved.request === request) return
        applyResult(moved.request, moved.plan, markDirty = true, selected = to)
    }

    /** Devuelve los días y las decisiones a los que propuso el reparto. */
    fun reset() {
        val current = _state.value
        val request = current.request ?: return
        val split = current.split ?: return
        if (current.emptyWeek) {
            showTemplates(split, current.initialWeekdays)
            return
        }
        recompute(request.copy(weekdays = current.initialWeekdays, placements = emptyMap(), fitToTime = false), markDirty = false)
    }

    /** Pone el ejercicio (o la superserie) [unitKey] en el día [dayIndex] del reparto. */
    fun place(unitKey: String, dayIndex: Int) {
        val request = _state.value.request ?: return
        recompute(request.copy(placements = request.placements + (unitKey to dayIndex)), markDirty = true)
    }

    /** Activa o apaga el recorte de series de accesorios por el tiempo por sesión. */
    fun setFit(on: Boolean) {
        val request = _state.value.request ?: return
        if (request.fitToTime == on) return
        recompute(request.copy(fitToTime = on), markDirty = true)
    }

    // ─── Interno ────────────────────────────────────────────────────────────────────────────────

    private fun recompute(request: MigrationRequest, markDirty: Boolean) {
        job?.cancel()
        _state.update { it.copy(calculating = true, request = request) }
        job = scope.launch {
            val result = withContext(computeDispatcher) { planner.plan(request) }
            applyResult(request, result, markDirty, selected = null)
        }
    }

    private fun applyResult(request: MigrationRequest, result: MigrationPlan, markDirty: Boolean, selected: Int?) {
        _state.update { current ->
            when (result) {
                is MigrationPlan.Ok -> current.copy(
                    calculating = false,
                    request = request,
                    plan = result,
                    refusal = null,
                    dirty = markDirty || (current.dirty && !isPristine(current, request)),
                    selectedWeekday = selected,
                    revision = current.revision + 1,
                )
                is MigrationPlan.Refused -> current.copy(
                    calculating = false,
                    request = request,
                    plan = null,
                    refusal = result,
                    selectedWeekday = null,
                    revision = current.revision + 1,
                )
            }
        }
    }

    private fun isPristine(current: SplitFlowState, request: MigrationRequest) =
        request.weekdays == current.initialWeekdays && request.placements.isEmpty() && !request.fitToTime

    // ─── Semana sin sesiones ────────────────────────────────────────────────────────────────────

    private fun showTemplates(split: SplitTemplate, weekdays: List<Int>) {
        val preview = SplitApplicationEngine.prebuiltWeekPreview(split = split, templates = config.templates)
        val labels = SplitCatalogRules.dayTitles(split)
        val training = split.pattern.filterNot { SplitCatalogRules.isRest(it) }
        val days = preview.days.mapIndexed { index, day ->
            RpTemplateDay(
                title = labels.getOrNull(index) ?: day.dayLabel,
                label = training.getOrNull(index) ?: day.dayLabel,
                template = day.templateName ?: "Sin plantilla compatible",
                count = day.exerciseCount,
                alternatives = day.alternatives.map { it.focusLabel }.distinct(),
                weekday = weekdays.getOrNull(index) ?: (index + 1),
            )
        }
        _state.update {
            it.copy(
                calculating = false,
                templateDays = days,
                dirty = false,
                selectedWeekday = null,
                revision = it.revision + 1,
                plan = null,
                refusal = null,
            )
        }
    }

    private fun moveTemplate(from: Int, to: Int) {
        _state.update { current ->
            val days = current.templateDays
            val source = days.indexOfFirst { it.weekday == from }
            if (source < 0) return@update current
            val occupant = days.indexOfFirst { it.weekday == to }
            val moved = days.toMutableList()
            moved[source] = moved[source].copy(weekday = to)
            if (occupant >= 0) moved[occupant] = moved[occupant].copy(weekday = from)
            current.copy(templateDays = moved, dirty = true, selectedWeekday = to, revision = current.revision + 1)
        }
    }
}
