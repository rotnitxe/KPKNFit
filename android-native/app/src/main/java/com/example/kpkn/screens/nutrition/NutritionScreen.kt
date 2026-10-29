package com.example.kpkn.screens.nutrition

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.nutrition.*
import com.example.kpkn.domain.nutrition.expenditure.DayMark
import com.example.kpkn.screens.nutrition.daystate.NutritionDayStateLine
import com.example.kpkn.screens.nutrition.daystate.NutritionDayStateSheet
import com.example.kpkn.screens.nutrition.components.CreatineSaturationOverlay
import com.example.kpkn.screens.nutrition.components.FoodLoggerDrawer
import com.example.kpkn.screens.nutrition.components.MealTypeSaver
import com.example.kpkn.screens.nutrition.design.NutritionDayModule
import com.example.kpkn.screens.nutrition.design.NutritionFoodMenu
import com.example.kpkn.screens.nutrition.design.NutritionHeader
import com.example.kpkn.screens.nutrition.design.NutritionLogIsland
import com.example.kpkn.screens.nutrition.design.NutritionMealModule
import com.example.kpkn.screens.nutrition.design.NutritionSupplementsModule
import com.example.kpkn.screens.nutrition.design.NutritionTopIsland
import com.example.kpkn.screens.nutrition.design.NutritionWeekTrendModule
import com.example.kpkn.screens.nutrition.design.buildNutritionWeekBars
import com.example.kpkn.screens.nutrition.reuse.CopyToSheet
import com.example.kpkn.screens.nutrition.reuse.PlannedMealsModule
import com.example.kpkn.screens.nutrition.reuse.RepeatYesterdayDialog
import com.example.kpkn.screens.nutrition.reuse.ReuseDayActions
import com.example.kpkn.screens.nutrition.reuse.SaveTemplateDialog
import com.example.kpkn.screens.nutrition.design.nutritionIslandSubtitle
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.KpknSnackbar
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.components.SnackbarType
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.components.showKpknSnackbar
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknStatusVeil
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.design.kpknEntrance
import com.example.kpkn.ui.design.rememberIslandVisible
import com.example.kpkn.ui.adapt.LocalViewportAdapt
import com.example.kpkn.ui.adapt.adapt
import com.example.kpkn.ui.locale.LocaleManager
import com.example.kpkn.ui.tour.TourIds
import com.example.kpkn.ui.tour.TourScripts
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** El menú de una fila abierto: el registro, dónde se midió la fila y si «Mover a…» está desplegado. */
private data class NutritionFoodMenuState(
    val log: NutritionLog,
    val anchor: Rect,
    val moveExpanded: Boolean = false,
)

/** N4: lo que pidió la hoja «Copiar a…»: una comida (menú de la fila) o todo el día mirado. */
private sealed interface NutritionCopyTarget {
    data class Meal(val log: NutritionLog) : NutritionCopyTarget
    data object Day : NutritionCopyTarget
}

/** N4: «Guardar como plantilla» abierto sobre [log]; [templateId] nace al abrir, así que guardar dos veces es una sola plantilla. */
private data class NutritionTemplateDialogState(val log: NutritionLog, val templateId: String)

// ═══════════════════════════════════════════════════════════════════════
// MAIN SCREEN
// ═══════════════════════════════════════════════════════════════════════

@Composable
fun NutritionScreen(
    viewModel: NutritionViewModel = viewModel { NutritionViewModel() },
    onNavigateToMealHistory: (() -> Unit)? = null,
    /** Editor directo del plan: creación y edición, nunca un wizard. */
    onNavigateToPlanEditor: (planId: String?) -> Unit = { _ -> },
    /** Borrador pendiente (pauta profesional a medias) → editor directo. */
    onOpenPendingDraft: (draftId: String) -> Unit = {},
    onOpenCheckin: () -> Unit = {},
) {
    val dailyTotals by viewModel.dailyTotals.collectAsState()
    val dayState by viewModel.dayState.collectAsState()
    val nutrients by viewModel.nutrients.collectAsState()
    val checkinAvailability by viewModel.checkinAvailability.collectAsState()
    val goals by viewModel.goals.collectAsState()
    // Recorrido de Nutrición: se lanza al montar la pantalla (su ViewModel no expone señal de carga). Visto = no arranca.
    val tourHost = LocalKpknOverlayHost.current
    LaunchedEffect(Unit) { tourHost?.startTour(TourIds.NUTRITION, TourScripts.nutrition) }
    val mealGroups by viewModel.mealGroups.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    var showDayState by rememberSaveable(selectedDate) { mutableStateOf(false) }
    var showNutrients by rememberSaveable(selectedDate) { mutableStateOf(false) }
    val today by viewModel.today.collectAsState()
    val activePlan by viewModel.activePlan.collectAsState()
    val foodDatabase by viewModel.foodDatabase.collectAsState()
    val trendData by viewModel.trendData.collectAsState()
    val shareMacros by viewModel.shareMacros.collectAsState()
    val shareStreak by viewModel.shareStreak.collectAsState()
    val historySeries by viewModel.historySeries.collectAsState()
    val sharedDescription by viewModel.pendingSharedDescription.collectAsState()
    val sharedTab by viewModel.pendingSharedTab.collectAsState()
    val foodLoggerOpenRequest by viewModel.foodLoggerOpenRequest.collectAsState()
    val pendingUndo by viewModel.pendingUndo.collectAsState()
    val uiMessage by viewModel.uiMessage.collectAsState()
    // Reutilización del diario (N4): comidas planificadas del día, avisos de copia y la pregunta de «Repetir ayer».
    val plannedLogs by viewModel.plannedLogs.collectAsState()
    val reuseMessage by viewModel.reuseMessage.collectAsState()
    val reuseBusy by viewModel.reuseBusy.collectAsState()
    val pendingRepeat by viewModel.pendingRepeat.collectAsState()
    val dailyEnergyBalance by viewModel.dailyEnergyBalance.collectAsState()
    val caffeineLimits by viewModel.caffeineLimits.collectAsState()
    val creatineSaturation by viewModel.creatineSaturation.collectAsState()
    val creatineToday by viewModel.creatineTodayGrams.collectAsState()
    val showCreatineOverlay by viewModel.showCreatineOverlay.collectAsState()
    val creatineDoses by viewModel.creatineProtocolDoses.collectAsState()
    val settings by remember { com.example.kpkn.data.repository.ProgramRepository.getInstance().settings }
        .collectAsState(initial = com.example.kpkn.data.models.Settings())
    val nutritionRepo = remember { com.example.kpkn.data.repository.NutritionRepository.getInstance() }
    // Raíz de Nutrición (N1): carga del estado del usuario, tira de «Tu día», racha de creatina y la isla al salir la cabecera.
    val userStateLoaded by viewModel.userStateLoaded.collectAsState()
    val weekStrip by viewModel.weekStrip.collectAsState()
    val creatineStreakDays by viewModel.creatineStreakDays.collectAsState()
    val listState = rememberLazyListState()
    val islandVisible = rememberIslandVisible(listState, afterItemIndex = 0)

    // WP-U7 (C5): si la hoja del logger estaba abierta, tras la muerte del proceso se reabre con su borrador
    // (la semilla vive en el SavedStateHandle del FoodLoggerViewModel); por eso estos tres estados se guardan.
    var showFoodLogger by rememberSaveable { mutableStateOf(false) }
    var showPlanRequiredDialog by remember { mutableStateOf(false) }
    // C12: la comida por defecto sigue a la hora, no es siempre «Almuerzo»; cada apertura la resuelve de nuevo.
    var selectedMealForLogger by rememberSaveable(stateSaver = MealTypeSaver) { mutableStateOf(defaultMealTypeNow()) }
    var foodLoggerInitialDescription by remember { mutableStateOf<String?>(sharedDescription) }
    var foodLoggerInitialTab by rememberSaveable { mutableIntStateOf(sharedTab.coerceIn(0, 1)) }
    // WP-U11 / WP-U17: la comida registrada que se abrió para editar (null = comida nueva). Se guarda su id (sobrevive a
    // rotar) y la comida se lee de la lista viva, así que guardar tras rotar sigue siendo una edición. El borrador vive en
    // el ViewModel del logger, que es quien sabe que se está editando y nunca manda confirmaciones.
    var editingLogId by rememberSaveable { mutableStateOf<String?>(null) }
    val nutritionLogs by viewModel.nutritionLogs.collectAsState()
    val editingLog = remember(editingLogId, nutritionLogs) {
        editingLogId?.let { id -> nutritionLogs.firstOrNull { it.id == id } }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingNutritionSetupDraftId by remember { mutableStateOf<String?>(null) }
    var pendingSetupRefresh by remember { mutableIntStateOf(0) }
    val nutritionContext = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner, viewModel) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                pendingSetupRefresh++
                // C1: tras pasar la noche (o cambiar de zona horaria) la app vuelve con «hoy»
                // desfasado; si el usuario miraba hoy, el día visible avanza con el calendario.
                viewModel.refreshToday()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(pendingSetupRefresh) {
        pendingNutritionSetupDraftId = runCatching {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.example.kpkn.data.onboarding.SetupDraftResolver(
                    com.example.kpkn.data.onboarding.persistenceFactory(nutritionContext).database,
                ).findPendingNutritionDraftId()
            }
        }.getOrNull()
    }

    val nutritionHazeState = remember { HazeState() }
    // Fecha de la cabecera y de la isla («Hoy» o la fecha elegida): siempre en español, sea cual sea el idioma del sistema.
    val dateLabel = remember(selectedDate) { nutritionHeroDateLabel(selectedDate) }
    // La isla «Registrar comida» y el «+» de la isla superior usan la misma regla: sin plan, pide crear uno.
    val openFoodLogger: () -> Unit = {
        if (!isFoodLoggingAvailable(settings, activePlan != null)) {
            showPlanRequiredDialog = true
        } else {
            foodLoggerInitialDescription = null
            foodLoggerInitialTab = 0
            viewModel.requestFoodLoggerOpen(tab = 0)
        }
    }
    // El «+» de una comida abre el registro en esa comida (la misma acción que tenía el acceso rápido de hoy).
    val openLoggerFor: (MealType) -> Unit = { meal ->
        selectedMealForLogger = meal
        foodLoggerInitialDescription = null
        foodLoggerInitialTab = 0
        viewModel.requestFoodLoggerOpen(tab = 0, mealType = meal)
    }
    // Los siete días de «Últimos 7 días»: la tendencia (kcal y meta) cruzada con las bandas del historial (mínimo y máximo).
    val weekBars = remember(trendData, historySeries, today) {
        buildNutritionWeekBars(trendData, historySeries, today.toString())
    }
    // El menú de una fila (N2): abierto desde la pulsación larga; «Mover a…» se despliega dentro del mismo menú.
    var foodMenu by remember { mutableStateOf<NutritionFoodMenuState?>(null) }
    var copyTarget by remember { mutableStateOf<NutritionCopyTarget?>(null) }
    var templateDialog by remember { mutableStateOf<NutritionTemplateDialogState?>(null) }

    LaunchedEffect(sharedDescription) {
        if (!sharedDescription.isNullOrBlank()) {
            foodLoggerInitialDescription = sharedDescription
            foodLoggerInitialTab = sharedTab.coerceIn(0, 1)
            selectedMealForLogger = defaultMealTypeNow()
            showFoodLogger = true
        }
    }

    LaunchedEffect(foodLoggerOpenRequest) {
        val request = foodLoggerOpenRequest ?: return@LaunchedEffect
        // WP-U11: editar abre una comida ya registrada, buscada por id; si ya no existe (se eliminó) no hay nada que abrir.
        val editTarget = request.editLogId?.let { id -> viewModel.nutritionLogs.value.firstOrNull { it.id == id } }
        if (request.editLogId != null && editTarget == null) {
            viewModel.consumeFoodLoggerOpenRequest()
            return@LaunchedEffect
        }
        editingLogId = editTarget?.id
        foodLoggerInitialDescription = request.description
        foodLoggerInitialTab = request.tab
        // C12: widget, share y deep link no eligen comida: heredan la de la hora.
        selectedMealForLogger = editTarget?.mealType ?: request.mealType ?: defaultMealTypeNow()
        showFoodLogger = true
        viewModel.consumeFoodLoggerOpenRequest()
    }

    // WP-U11 (C9): «Comida eliminada» con «Deshacer». El plazo es el del propio aviso: si termina o se cierra, la
    // eliminación queda firme; una eliminación nueva reinicia el efecto y ocupa su lugar.
    LaunchedEffect(pendingUndo) {
        if (pendingUndo == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "Comida eliminada",
            actionLabel = "Deshacer",
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete() else viewModel.dismissUndo()
    }

    LaunchedEffect(uiMessage) {
        val message = uiMessage ?: return@LaunchedEffect
        snackbarHostState.showKpknSnackbar(message, SnackbarType.DANGER)
        viewModel.consumeUiMessage()
    }

    // N4: el resultado de copiar, guardar una plantilla o pasar una comida a consumida. Los fallos duran más: nombran qué falta.
    LaunchedEffect(reuseMessage) {
        val message = reuseMessage ?: return@LaunchedEffect
        snackbarHostState.showKpknSnackbar(
            message = message.text,
            type = if (message.isError) SnackbarType.DANGER else SnackbarType.SUCCESS,
            duration = if (message.isError) SnackbarDuration.Long else SnackbarDuration.Short,
        )
        viewModel.consumeReuseMessage()
    }

    LaunchedEffect(activePlan?.id, activePlan?.calorieTarget, activePlan?.proteinGoal, activePlan?.carbGoal, activePlan?.fatGoal) {
        if (activePlan != null) {
            viewModel.syncActivePlanGoalsToSettings()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(KpknSurface.canvas)
                .hazeSource(state = nutritionHazeState),
        ) {
            val nutritionAdapt = LocalViewportAdapt.current
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                // Deja aire bajo el último módulo para que la isla «Registrar comida» no lo tape.
                contentPadding = PaddingValues(bottom = 160.dp.adapt(nutritionAdapt)),
            ) {
                item {
                    NutritionHeader(
                        dateLabel = dateLabel,
                        loaded = userStateLoaded,
                        hasActivePlan = activePlan != null,
                        onEditPlan = { onNavigateToPlanEditor(activePlan?.id) },
                        onCreatePlan = { onNavigateToPlanEditor(null) },
                        modifier = Modifier.kpknEntrance("nutrition", 0),
                    )
                }

                item {
                    NutritionDayModule(
                        loaded = userStateLoaded,
                        days = weekStrip,
                        selectedDate = selectedDate,
                        totals = dailyTotals,
                        goals = goals,
                        hasActivePlan = activePlan != null,
                        onSelectDate = { viewModel.setSelectedDate(it) },
                        onDefineGoals = { onNavigateToPlanEditor(null) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).kpknEntrance("nutrition", 1),
                        trainingBurnKcal = dailyEnergyBalance.trainingBurnKcal,
                    )
                }

                if (userStateLoaded) {
                    item {
                        KpknTextAction("Nutrientes del día", { showNutrients = true },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                    }
                    item {
                        NutritionDayStateLine(
                            state = dayState,
                            onOpen = { showDayState = true },
                            onAcceptSuggestion = { viewModel.acceptPartialDaySuggestion() },
                            onRejectSuggestion = { viewModel.rejectPartialDaySuggestion() },
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                    }
                }

                if (userStateLoaded) {
                    item(key = "reuse-day-actions") {
                        val shownDay = runCatching { LocalDate.parse(selectedDate) }.getOrDefault(today)
                        ReuseDayActions(
                            repeatLabel = MealReuse.repeatLabel(shownDay, today),
                            canCopyDay = mealGroups.any { it.logs.isNotEmpty() },
                            onRepeat = { viewModel.repeatYesterday() },
                            onCopyDay = { copyTarget = NutritionCopyTarget.Day },
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                    }
                }

                if (userStateLoaded && checkinAvailability == com.example.kpkn.domain.nutrition.checkin.CheckinAvailability.NOW) {
                    item(key = "weekly-checkin") {
                        com.example.kpkn.screens.nutrition.checkin.NutritionCheckinCard(
                            onOpenCheckin, Modifier.padding(horizontal = 16.dp, vertical = 6.dp).kpknEntrance("nutrition-checkin", 0),
                        )
                    }
                }

                // Avisos: la pauta a medias de un profesional, en un módulo teñido de energía.
                pendingNutritionSetupDraftId?.let { pendingDraftId ->
                    item {
                        KpknModule(
                            accent = SymbolPalette.energia,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).kpknEntrance("nutrition", 2),
                        ) {
                            Text(
                                "Tienes indicaciones de un profesional a medias",
                                style = KpknType.bodyStrong,
                                color = WizardColors.text,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Completa macros y calorías cuando quieras; no se borraron.",
                                style = KpknType.caption,
                                color = WizardColors.textMuted,
                            )
                            Spacer(Modifier.height(10.dp))
                            KpknTextAction(
                                text = "Completar ahora",
                                onClick = { onOpenPendingDraft(pendingDraftId) },
                                icon = KpknLineIcons.ChevronRight,
                            )
                        }
                    }
                }

                // Un módulo por comida (N2): el bloque entero cambia con la fecha, como «Tu día».
                item {
                    NutritionMealsBlock(
                        selectedDate = selectedDate,
                        mealGroups = mealGroups,
                        onAdd = openLoggerFor,
                        onOpenLog = { log -> viewModel.requestFoodLoggerOpen(tab = 0, editLogId = log.id) },
                        onLongPressLog = { log, anchor -> foodMenu = NutritionFoodMenuState(log = log, anchor = anchor) },
                    )
                }

                if (plannedLogs.isNotEmpty()) {
                    item(key = "planned-meals") {
                        PlannedMealsModule(
                            logs = plannedLogs,
                            canMarkConsumed = selectedDate <= today.toString(),
                            onMarkConsumed = { viewModel.markPlannedConsumed(it.id) },
                            onRemove = { viewModel.deleteLog(it.id) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
                }

                item {
                    NutritionSupplementsModule(
                        loaded = userStateLoaded,
                        caffeineMg = dailyTotals.caffeineMg,
                        caffeineLimits = caffeineLimits,
                        creatineTodayG = creatineToday,
                        creatineTargetG = creatineSaturation.dailyTargetGrams.coerceAtLeast(1.0),
                        creatineStreakDays = creatineStreakDays,
                        isToday = selectedDate == today.toString(),
                        onQuickAddCreatine = { viewModel.quickAddCreatine() },
                        onOpenCreatine = { viewModel.openCreatineTracker() },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).kpknEntrance("nutrition", 4),
                    )
                }

                item {
                    NutritionWeekTrendModule(
                        bars = weekBars,
                        selectedDate = selectedDate,
                        onSelectDate = { viewModel.setSelectedDate(it) },
                        onOpenHistory = onNavigateToMealHistory,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).kpknEntrance("nutrition", 9),
                        shareMacros = shareMacros,
                        shareStreak = shareStreak,
                    )
                }
            }
        }

        // Velo bajo la barra de estado: por encima de la lista y por debajo de las islas.
        KpknStatusVeil(modifier = Modifier.align(Alignment.TopCenter))

        // Isla superior: aparece cuando la cabecera sale de la vista. Sin «+»: el registro vive en la isla de abajo.
        NutritionTopIsland(
            visible = islandVisible,
            title = if (selectedDate == today.toString()) "Hoy" else dateLabel,
            subtitle = nutritionIslandSubtitle(
                loaded = userStateLoaded,
                kcal = kotlin.math.round(dailyTotals.calories).toInt(),
                kcalGoal = (goals as? DayGoalsResult.Present)?.goals?.calorieGoal?.takeIf { it > 0 },
                estimate = dailyTotals.isEstimate,
            ),
        )

        // Isla «Registrar comida» (N2): sobre la barra inferior de 64 dp, con 8 dp sobre la barra de gestos y 12 dp de aire.
        NutritionLogIsland(
            hazeState = nutritionHazeState,
            onClick = openFoodLogger,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 84.dp),
        )

        // WP-U11: el aviso de «Deshacer» vive sobre la isla de registrar, nunca debajo de ella.
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 160.dp),
        ) { data -> KpknSnackbar(data) }
    }

    if (showNutrients) {
        com.example.kpkn.screens.nutrition.nutrients.NutrientsSheet(nutrients, { showNutrients = false }, nutritionHazeState)
    }

    if (showDayState) {
        NutritionDayStateSheet(
            state = dayState,
            onSelect = { mark ->
                if (mark == DayMark.NONE) viewModel.clearDayMark() else viewModel.setDayMark(mark)
                showDayState = false
            },
            onDismiss = { showDayState = false },
            hazeState = nutritionHazeState,
        )
    }

    // El menú de una fila (N2): fuera de la pantalla, sobre todo lo demás. Duplicar, mover o eliminar cierran el menú.
    foodMenu?.let { menu ->
        NutritionFoodMenu(
            anchor = menu.anchor,
            moveExpanded = menu.moveExpanded,
            otherMeals = MealSlots.mealOrder.filter { it != menu.log.mealType },
            onDuplicate = { viewModel.duplicateLog(menu.log) },
            onToggleMove = { foodMenu = menu.copy(moveExpanded = !menu.moveExpanded) },
            onMove = { meal -> viewModel.moveLog(menu.log.id, meal) },
            onDelete = { viewModel.deleteLog(menu.log.id) },
            onDismiss = { foodMenu = null },
            onCopyTo = if (MealReuse.isCopyable(menu.log)) ({ copyTarget = NutritionCopyTarget.Meal(menu.log) }) else null,
            onSaveTemplate = if (MealReuse.isCopyable(menu.log)) {
                ({ templateDialog = NutritionTemplateDialogState(menu.log, java.util.UUID.randomUUID().toString()) })
            } else {
                null
            },
        )
    }

    // N4: «Copiar a…» / «Copiar día a…», «Guardar como plantilla» y la pregunta de «Repetir ayer».
    copyTarget?.let { target ->
        val sources = when (target) {
            is NutritionCopyTarget.Meal -> listOf(target.log)
            NutritionCopyTarget.Day -> mealGroups.flatMap { it.logs }
        }
        val originDay = when (target) {
            is NutritionCopyTarget.Meal -> runCatching { LocalDate.parse(target.log.date.take(10)) }.getOrNull()
            NutritionCopyTarget.Day -> runCatching { LocalDate.parse(selectedDate) }.getOrNull()
        }
        CopyToSheet(
            title = if (target is NutritionCopyTarget.Meal) "Copiar a…" else "Copiar día a…",
            subtitle = copySourceSubtitle(sources, originDay),
            sources = sources,
            today = today,
            blocked = setOfNotNull(originDay),
            busy = reuseBusy,
            onConfirm = { dates ->
                copyTarget = null
                viewModel.copyLogsTo(sources, dates)
            },
            onDismiss = { copyTarget = null },
            hazeState = nutritionHazeState,
        )
    }
    templateDialog?.let { state ->
        SaveTemplateDialog(
            initialName = MealReuse.defaultTemplateName(state.log),
            summary = templateDialogSummary(state.log),
            onConfirm = { name ->
                templateDialog = null
                viewModel.saveLogAsTemplate(state.log, name, state.templateId)
            },
            onDismiss = { templateDialog = null },
        )
    }
    pendingRepeat?.let { ask ->
        RepeatYesterdayDialog(
            existingMeals = ask.existingMeals,
            incomingMeals = ask.plan.copies.size,
            sourceIsYesterday = selectedDate == today.toString(),
            onAdd = { viewModel.confirmRepeat() },
            onCancel = { viewModel.cancelRepeat() },
        )
    }

    // ── Food Logger Drawer ───────────────────────────────────────────────────
    FoodLoggerDrawer(
        nutritionRepo = nutritionRepo,
        isOpen = showFoodLogger,
        onDismiss = {
            showFoodLogger = false
            viewModel.consumeSharedDescription()
            foodLoggerInitialDescription = null
            foodLoggerInitialTab = 0
            editingLogId = null
        },
        // WP-U11: editar guarda sobre la misma comida y no enseña ningún hábito (el logger tampoco manda confirmaciones).
        onSave = { log, confirmations ->
            if (isEditSave(editingLogId, log)) viewModel.updateLog(log) else viewModel.saveLog(log, confirmations)
        },
        foodDatabase = foodDatabase,
        initialDate = selectedDate,
        initialMealType = selectedMealForLogger,
        initialDescription = foodLoggerInitialDescription,
        initialTab = foodLoggerInitialTab,
        initialLog = editingLog,
    )

    CreatineSaturationOverlay(
        visible = showCreatineOverlay,
        weightKg = settings.userVitals.weight,
        doses = creatineDoses,
        onDismiss = { viewModel.dismissCreatineOverlay() },
        onConfirm = { viewModel.confirmCreatineProtocol(it) },
    )

    if (showPlanRequiredDialog) {
        KpknAlertDialog(
            onDismissRequest = { showPlanRequiredDialog = false },
            title = { Text("Crea un plan de alimentación") },
            text = { Text("Primero necesitas crear un plan de alimentación para registrar comidas o mediciones.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPlanRequiredDialog = false
                        onNavigateToPlanEditor(null)
                    },
                ) { Text("Crear plan") }
            },
            dismissButton = {
                TextButton(onClick = { showPlanRequiredDialog = false }) { Text("Cerrar") }
            },
        )
    }
}

/**
 * Los cuatro módulos de comida (N2) en el orden de [MealSlots.mealOrder]. Al cambiar de día, el bloque entero cambia con `AnimatedContent`:
 * fundido cruzado de 220 ms y 12 dp en el sentido del cambio; con movimiento reducido, solo fundido de 120 ms.
 */
@Composable
private fun NutritionMealsBlock(
    selectedDate: String,
    mealGroups: List<MealGroup>,
    onAdd: (MealType) -> Unit,
    onOpenLog: (NutritionLog) -> Unit,
    onLongPressLog: (NutritionLog, Rect) -> Unit,
) {
    val reduced = wizardReducedMotion()
    val slidePx = with(LocalDensity.current) { 12.dp.roundToPx() }
    AnimatedContent(
        targetState = selectedDate,
        modifier = Modifier.fillMaxWidth(),
        transitionSpec = {
            val forward = targetState >= initialState
            if (reduced) {
                fadeIn(animationSpec = tween(durationMillis = 120)) togetherWith
                    fadeOut(animationSpec = tween(durationMillis = 120))
            } else {
                (fadeIn(animationSpec = tween(durationMillis = 220)) +
                    slideInHorizontally(animationSpec = tween(durationMillis = 220)) { if (forward) slidePx else -slidePx }) togetherWith
                    fadeOut(animationSpec = tween(durationMillis = 220))
            }
        },
        label = "nutritionMeals",
    ) { _ ->
        Column(modifier = Modifier.fillMaxWidth()) {
            MealSlots.mealOrder.forEachIndexed { index, mealType ->
                val group = mealGroups.find { it.mealType == mealType }
                NutritionMealModule(
                    mealType = mealType,
                    logs = group?.logs.orEmpty(),
                    totals = group?.totals ?: DailyMacroTotals(),
                    onAdd = { onAdd(mealType) },
                    onOpenLog = onOpenLog,
                    onLongPressLog = onLongPressLog,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).kpknEntrance("nutrition", 5 + index),
                )
            }
        }
    }
}

/**
 * La fecha del encabezado: «Miércoles 14 de octubre», con el mismo formato que Inicio (la pantalla la pone en mayúsculas por estilo).
 * Siempre en español, con independencia del idioma del sistema. Si [selectedDate] no es una fecha válida se muestra tal cual.
 */
internal fun nutritionHeroDateLabel(selectedDate: String): String =
    try {
        LocalDate.parse(selectedDate).format(NUTRITION_DATE_FORMAT).replaceFirstChar { it.titlecase(NUTRITION_DATE_LOCALE) }
    } catch (_: Exception) {
        selectedDate
    }

private val NUTRITION_DATE_LOCALE: Locale = Locale.forLanguageTag("es")
private val NUTRITION_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", NUTRITION_DATE_LOCALE)

/**
 * Guardar la comida abierta es una edición si lo que se guarda lleva el id de la comida que se abrió para editar. El id
 * manda, no solo que haya una edición abierta: un borrador nuevo (por ejemplo tras recuperar el proceso) nunca se confunde con
 * ella, y una edición sigue siéndolo tras rotar.
 */
internal fun isEditSave(editingLogId: String?, saved: NutritionLog): Boolean =
    editingLogId != null && saved.id == editingLogId

/** N4: la línea de la hoja «Copiar a…»: «Almuerzo · 20 oct · 560 kcal» para una comida o «3 comidas · 20 oct · 1240 kcal» para el día. */
internal fun copySourceSubtitle(sources: List<NutritionLog>, originDay: LocalDate?): String {
    val kcal = kotlin.math.round(computeFoodTotals(sources.flatMap { it.foods }).calories).toInt()
    val day = originDay?.let { MealReuse.shortDateEs(it) }
    val head = if (sources.size == 1) MealSlots.mealLabel(sources.single().mealType) else "${sources.size} comidas"
    return listOfNotNull(head, day, "$kcal kcal").joinToString(" · ")
}

/** N4: el resumen de «Guardar como plantilla»: los alimentos y las kcal de la comida. */
internal fun templateDialogSummary(log: NutritionLog): String {
    val kcal = kotlin.math.round(computeFoodTotals(log.foods).calories).toInt()
    val foods = log.foods.joinToString(", ") { it.foodName }.ifBlank { "Comida" }
    return "$foods · $kcal kcal"
}
