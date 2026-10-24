package com.example.kpkn.screens.nutrition.checkin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.domain.nutrition.checkin.*
import com.example.kpkn.domain.nutrition.expenditure.*
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class CheckinContext(
    val plan: NutritionPlan? = null,
    val weekStartDay: DayOfWeek = DayOfWeek.MONDAY,
    val preferences: CheckinPreferences = CheckinPreferences(),
    val days: List<DayObservation> = emptyList(),
    val weights: List<WeightObservation> = emptyList(),
    val initialKcal: Double? = null,
    val restingKcal: Double? = null,
    val goalChangeDates: List<LocalDate> = emptyList(),
)

data class CheckinDependencies(
    val context: Flow<CheckinContext>,
    val store: CheckinStore,
    val savePreferences: suspend ((CheckinPreferences) -> CheckinPreferences) -> Unit,
    val markDay: suspend (LocalDate, DayMark, DayMarkSource) -> Unit,
    val today: () -> LocalDate = { LocalDate.now() },
)

data class NutritionCheckinUiState(
    val availability: CheckinAvailability = CheckinAvailability.NONE,
    val plan: CheckinPlan? = null,
    val preferences: CheckinPreferences = CheckinPreferences(),
    val checkinDay: DayOfWeek = DayOfWeek.MONDAY,
    val stepIndex: Int = 0,
    val busy: Boolean = false,
    val loading: Boolean = true,
    val error: String? = null,
) {
    val steps: List<CheckinModule> get() = plan?.modules.orEmpty().let {
        // La confirmación final sigue disponible aunque se omitan las explicaciones de sus módulos.
        if (CheckinModule.TARGET_UPDATE in it) it else it + CheckinModule.TARGET_UPDATE
    }
    val module: CheckinModule get() = steps[stepIndex.coerceIn(0, steps.lastIndex)]
}

/** Control de presentación compartido por la pantalla real y la vista previa en memoria. */
class NutritionCheckinController(
    private val scope: CoroutineScope,
    private val dependencies: CheckinDependencies,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val _state = MutableStateFlow(NutritionCheckinUiState())
    val state: StateFlow<NutritionCheckinUiState> = _state.asStateFlow()
    private val refreshTick = MutableStateFlow(0)

    init {
        scope.launch {
            combine(dependencies.context, dependencies.store.records, refreshTick) { context, records, _ -> context to records }
                .collectLatest { (context, records) ->
                    try {
                        _state.update { it.copy(loading = true) }
                        val computed = withContext(computeDispatcher) {
                            val today = dependencies.today()
                            val day = context.preferences.dayOfWeek?.takeIf { it in 1..7 }?.let(DayOfWeek::of) ?: context.weekStartDay
                            val key = CheckinPolicy.weekKey(today, context.weekStartDay, day)
                            val through = today.minusDays(1)
                            val initial = context.initialKcal?.takeIf { it.isFinite() && it > 0.0 }
                            val series = if (initial == null) emptyList() else ExpenditureEstimator().estimateSeries(ExpenditureInput(
                                initial, CheckinPlanner.estimatorFloor(context.restingKcal), context.days.filter { it.date <= through }
                                    .ifEmpty { listOf(DayObservation(through, null)) },
                                context.weights, through, context.goalChangeDates,
                            ))
                            val plan = context.plan?.let { CheckinPlanner.build(series.lastOrNull(), series, context.days,
                                it, today, key, context.preferences) }
                            NutritionCheckinUiState(
                                availability = CheckinPolicy.state(today, context.weekStartDay, records.find { it.weekKey == key },
                                    context.plan != null, day),
                                plan = plan, preferences = context.preferences, checkinDay = day, loading = false,
                            )
                        }
                        _state.update { old -> computed.copy(stepIndex = old.stepIndex.coerceAtMost(computed.steps.lastIndex), busy = old.busy, error = old.error) }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { _state.update { it.copy(plan = null, availability = CheckinAvailability.NONE,
                        loading = false, error = "No se pudo preparar el check-in. Inténtalo de nuevo.") } }
                }
        }
    }

    fun refresh() { refreshTick.update { it + 1 } }
    fun back() { _state.update { it.copy(stepIndex = (it.stepIndex - 1).coerceAtLeast(0)) } }
    fun next() = action {
        if (_state.value.module == CheckinModule.INTRODUCTION) dependencies.savePreferences { it.copy(introductionSeen = true) }
        else _state.update { it.copy(stepIndex = (it.stepIndex + 1).coerceAtMost(it.steps.lastIndex)) }
    }
    fun dismissModule(module: CheckinModule) = action {
        dependencies.savePreferences { it.copy(dismissedModules = it.dismissedModules + module) }
    }
    fun setCheckinDay(day: DayOfWeek) = action { dependencies.savePreferences { it.copy(dayOfWeek = day.value) } }
    fun markPartial(date: LocalDate) = mark(date, DayMark.PARTIAL, DayMarkSource.REVIEW_ACCEPTED)
    fun keepComplete(date: LocalDate) = mark(date, DayMark.COMPLETE_CONFIRMED, DayMarkSource.REVIEW_REJECTED)
    fun confirmFasting(date: LocalDate) = mark(date, DayMark.FASTING, DayMarkSource.USER)
    private fun mark(date: LocalDate, mark: DayMark, source: DayMarkSource) = action { dependencies.markDay(date, mark, source) }
    fun accept() = decision { dependencies.store.apply(it) }
    fun decline() = decision { dependencies.store.decline(it.weekKey, it.proposal) }
    fun postpone() = decision { dependencies.store.postpone(it.weekKey, dependencies.today(), it.proposal) }

    private fun decision(block: suspend (CheckinPlan) -> Unit) = action {
        val state = _state.value
        check(!state.loading) { "Espera a que termine la estimación." }
        check(state.availability == CheckinAvailability.NOW) { "Este check-in ya no está disponible." }
        block(requireNotNull(state.plan))
    }

    private fun action(block: suspend () -> Unit): Job = scope.launch {
        if (_state.value.busy) return@launch
        _state.update { it.copy(busy = true, error = null) }
        try { withContext(NonCancellable) { block() } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { _state.update { it.copy(error = "No se pudo confirmar la decisión. Vuelve a intentarlo; no se aplicará dos veces.") } }
        finally { _state.update { it.copy(busy = false) } }
    }
}

class NutritionCheckinViewModel(dependencies: CheckinDependencies, dispatcher: CoroutineDispatcher = Dispatchers.Default) : ViewModel() {
    val controller = NutritionCheckinController(viewModelScope, dependencies, dispatcher)
    val state: StateFlow<NutritionCheckinUiState> = controller.state
}
