package com.example.kpkn.screens.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.kpkn.data.models.DailyGoalSnapshot
import com.example.kpkn.data.models.DailyMacroTotals
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.NutritionStatus
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.nutrition.DayGoalsResult
import com.example.kpkn.domain.nutrition.MealSlots
import com.example.kpkn.domain.nutrition.computeDailyTotals
import com.example.kpkn.domain.nutrition.resolveDayGoalsByDate
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** Meses por tanda: el historial enseña tres al principio y tres más cada vez que la lista llega al final. */
internal const val MEAL_HISTORY_MONTHS_PER_PAGE = 3

/** Los flujos que lee el historial: los mismos que leía la pantalla dentro de la composición. */
internal data class MealHistoryInputs(
    val logs: Flow<List<NutritionLog>>,
    val settings: Flow<Settings>,
    val plans: Flow<List<NutritionPlan>>,
    val activePlanId: Flow<String?>,
    val goalSnapshots: Flow<List<DailyGoalSnapshot>>,
)

/** Un día del historial ya resumido: lo que pinta su fila y su detalle. */
internal data class MealHistoryDay(
    /** La clave del día, «2026-10-13». */
    val dateKey: String,
    /** La fecha corta de la fila, «mar 13 oct». */
    val dateLabel: String,
    /** La fecha larga del detalle, «martes 13 de octubre». */
    val longDateLabel: String,
    /** Los registros del día, sin lo planificado y en el orden de las comidas. */
    val logs: List<NutritionLog>,
    val totals: DailyMacroTotals,
    /** Meta de kcal del día; null si el día no tiene meta. Nunca un valor inventado. */
    val kcalGoal: Int?,
    val isToday: Boolean,
)

/** Un mes del historial, con su etiqueta («octubre de 2026») y sus días, del más reciente al más antiguo. */
internal data class MealHistoryMonth(
    val month: YearMonth,
    val label: String,
    val days: List<MealHistoryDay>,
)

/** Una tanda del historial: los meses de la ventana y si quedan más por mostrar. */
internal data class MealHistoryPage(
    val months: List<MealHistoryMonth>,
    val hasMore: Boolean,
)

/**
 * Arma la tanda del historial: los [monthCount] meses más recientes, con sus días. Cada día lleva sus registros (sin lo planificado), sus
 * totales y su meta según el resolvedor de siempre: sin evidencia, la meta es null. Es pura, sin Android ni repositorios.
 */
internal fun buildMealHistoryPage(
    logs: List<NutritionLog>,
    settings: Settings,
    activePlan: NutritionPlan?,
    snapshots: List<DailyGoalSnapshot>,
    today: LocalDate,
    locale: Locale,
    monthCount: Int,
): MealHistoryPage {
    val consumed = logs.filter { it.status != NutritionStatus.PLANNED }
    val logsByKey = consumed.groupBy { it.date.take(10) }
    // Solo las fechas que se leen tienen mes: una fecha rota no entra en ningún grupo.
    val datesByMonth: Map<YearMonth, List<LocalDate>> = logsByKey.keys
        .mapNotNull { key -> runCatching { LocalDate.parse(key) }.getOrNull() }
        .groupBy { YearMonth.from(it) }
    val allMonths = datesByMonth.keys.sortedDescending()
    val shown = allMonths.take(monthCount)
    val dates = shown.flatMap { datesByMonth.getValue(it) }
    // Metas por fecha con el resolvedor canónico: un fallo deja todas las metas en ausencia, nunca un default.
    val goals: Map<LocalDate, DayGoalsResult> = runCatching {
        resolveDayGoalsByDate(dates, settings, activePlan, snapshots, today)
    }.getOrDefault(emptyMap())
    return MealHistoryPage(
        months = shown.map { month ->
            MealHistoryMonth(
                month = month,
                label = mealHistoryMonthLabel(month, locale),
                days = datesByMonth.getValue(month).sortedDescending().map { date ->
                    val dayLogs = logsByKey.getValue(date.toString())
                        .sortedBy { MealSlots.mealOrder.indexOf(it.mealType) }
                    MealHistoryDay(
                        dateKey = date.toString(),
                        dateLabel = mealHistoryDayLabel(date, locale),
                        longDateLabel = mealHistoryLongDateLabel(date, locale),
                        logs = dayLogs,
                        totals = computeDailyTotals(dayLogs),
                        kcalGoal = (goals[date] as? DayGoalsResult.Present)?.goals?.calorieGoal?.takeIf { it > 0 },
                        isToday = date == today,
                    )
                },
            )
        },
        hasMore = allMonths.size > shown.size,
    )
}

/** «octubre de 2026» en español; en otros idiomas, el nombre del mes con el año. */
internal fun mealHistoryMonthLabel(month: YearMonth, locale: Locale): String {
    val pattern = if (locale.language == "es") "MMMM 'de' yyyy" else "MMMM yyyy"
    return month.atDay(1).format(DateTimeFormatter.ofPattern(pattern, locale))
}

/** «mar 13 oct»: la fecha corta de la fila del historial. */
internal fun mealHistoryDayLabel(date: LocalDate, locale: Locale): String =
    date.format(DateTimeFormatter.ofPattern("EEE d MMM", locale))

/** «martes 13 de octubre» en español, la fecha larga del detalle; en inglés y en otros idiomas, su forma local. */
internal fun mealHistoryLongDateLabel(date: LocalDate, locale: Locale): String {
    val formatter = when (locale.language) {
        "es" -> DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", locale)
        "en" -> DateTimeFormatter.ofPattern("EEEE, MMMM d", locale)
        else -> DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)
    }
    return date.format(formatter)
}

/** Los datos del historial ya unidos: registros, ajustes, plan activo y metas guardadas por día. */
private class MealHistorySources(
    val logs: List<NutritionLog>,
    val settings: Settings,
    val activePlan: NutritionPlan?,
    val snapshots: List<DailyGoalSnapshot>,
)

/** Los flujos reales: el historial lee los mismos repositorios que la pantalla leía dentro de la composición. */
private fun repositoryInputs(): MealHistoryInputs {
    val nutrition = NutritionRepository.getInstance()
    return MealHistoryInputs(
        logs = nutrition.nutritionLogs,
        settings = ProgramRepository.getInstance().settings,
        plans = nutrition.nutritionPlans,
        activePlanId = nutrition.activeNutritionPlanId,
        goalSnapshots = nutrition.dailyGoalSnapshots,
    )
}

/**
 * Historial de comidas por meses (N5). Los días se calculan fuera de la composición, en [computeDispatcher], con las mismas funciones de
 * siempre. Muestra [MEAL_HISTORY_MONTHS_PER_PAGE] meses; [loadMore] suma otra tanda cuando la lista llega al final.
 */
internal class MealHistoryViewModel(
    inputs: MealHistoryInputs = repositoryInputs(),
    locale: Locale = Locale.forLanguageTag("es"),
    today: () -> LocalDate = { LocalDate.now() },
    computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val windowMonths = MutableStateFlow(MEAL_HISTORY_MONTHS_PER_PAGE)

    private val sources: Flow<MealHistorySources> = combine(
        inputs.logs,
        inputs.settings,
        inputs.plans,
        inputs.activePlanId,
        inputs.goalSnapshots,
    ) { logs, settings, plans, activePlanId, snapshots ->
        MealHistorySources(
            logs = logs,
            settings = settings,
            activePlan = activePlanId?.let { id -> plans.find { it.id == id } },
            snapshots = snapshots,
        )
    }

    /** La tanda actual; null hasta que llega la primera. */
    private val page: StateFlow<MealHistoryPage?> = combine(sources, windowMonths) { source, count ->
        buildMealHistoryPage(
            logs = source.logs,
            settings = source.settings,
            activePlan = source.activePlan,
            snapshots = source.snapshots,
            today = today(),
            locale = locale,
            monthCount = count,
        )
    }
        .flowOn(computeDispatcher)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Los meses de la ventana, del más reciente al más antiguo. */
    val months: StateFlow<List<MealHistoryMonth>> = page
        .map { it?.months.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** true mientras llega la primera tanda: la pantalla pinta esqueletos. */
    val isLoading: StateFlow<Boolean> = page
        .map { it == null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** true si quedan meses más allá de la ventana. */
    val hasMore: StateFlow<Boolean> = page
        .map { it?.hasMore == true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Suma una tanda de meses a la ventana, si quedan más. */
    fun loadMore() {
        if (hasMore.value) windowMonths.update { it + MEAL_HISTORY_MONTHS_PER_PAGE }
    }
}

/** Fábrica del historial, con el idioma efectivo de la app para que las fechas salgan en ese idioma. */
internal class MealHistoryViewModelFactory(private val locale: Locale) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = MealHistoryViewModel(locale = locale) as T
}
