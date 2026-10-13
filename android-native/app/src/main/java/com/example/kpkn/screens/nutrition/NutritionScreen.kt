package com.example.kpkn.screens.nutrition

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.kpkn.ui.components.LocalKpknOverlayHost
import com.example.kpkn.ui.tour.TourAnchors
import com.example.kpkn.ui.tour.TourIds
import com.example.kpkn.ui.tour.TourScripts
import com.example.kpkn.ui.tour.tourAnchor
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.ui.adapt.LocalViewportAdapt
import com.example.kpkn.ui.adapt.adapt
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.nutrition.*
import com.example.kpkn.screens.nutrition.components.FoodLoggerDrawer
import com.example.kpkn.screens.nutrition.components.MealTypeSaver
import com.example.kpkn.screens.nutrition.design.NutritionBalanceModule
import com.example.kpkn.screens.nutrition.design.NutritionDayModule
import com.example.kpkn.screens.nutrition.design.NutritionHeader
import com.example.kpkn.screens.nutrition.design.NutritionSupplementsModule
import com.example.kpkn.screens.nutrition.design.NutritionTopIsland
import com.example.kpkn.screens.nutrition.design.nutritionIslandSubtitle
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.design.kpknEntrance
import com.example.kpkn.ui.design.rememberIslandVisible
import androidx.compose.foundation.lazy.rememberLazyListState
import com.example.kpkn.screens.nutrition.components.CreatineSaturationOverlay
import com.example.kpkn.screens.nutrition.design.mealIcon
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.KpknSnackbar
import com.example.kpkn.ui.components.SnackbarType
import com.example.kpkn.ui.components.showKpknSnackbar
import com.example.kpkn.ui.components.LocalHazeState
import com.example.kpkn.ui.components.kpknGlass
import com.example.kpkn.ui.components.kpknGlassOrFallback
import com.example.kpkn.ui.locale.LocaleManager
import com.example.kpkn.ui.theme.MacroColors
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Lado mínimo de una zona táctil (WP-U17): los controles de 24-36 dp se llevan a este tamaño. */
private val MIN_TOUCH_TARGET = 40.dp

private val PROTEIN_COLOR = MacroColors.protein
private val CARBS_COLOR = MacroColors.carbs
private val FATS_COLOR = MacroColors.fat
private val TEAL = Color(0xFF009688)

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
) {
    val dailyTotals by viewModel.dailyTotals.collectAsState()
    val goals by viewModel.goals.collectAsState()
    // Recorrido de Nutrición: se lanza al montar la pantalla (su ViewModel no expone señal de carga). Visto = no arranca.
    val tourHost = LocalKpknOverlayHost.current
    LaunchedEffect(Unit) { tourHost?.startTour(TourIds.NUTRITION, TourScripts.nutrition) }
    val mealGroups by viewModel.mealGroups.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val today by viewModel.today.collectAsState()
    val activePlan by viewModel.activePlan.collectAsState()
    val foodDatabase by viewModel.foodDatabase.collectAsState()
    val trendData by viewModel.trendData.collectAsState()
    val historySeries by viewModel.historySeries.collectAsState()
    val sharedDescription by viewModel.pendingSharedDescription.collectAsState()
    val sharedTab by viewModel.pendingSharedTab.collectAsState()
    val foodLoggerOpenRequest by viewModel.foodLoggerOpenRequest.collectAsState()
    val pendingUndo by viewModel.pendingUndo.collectAsState()
    val uiMessage by viewModel.uiMessage.collectAsState()
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
    // Fecha de la cabecera y de la isla («Hoy» o la fecha elegida), con el idioma efectivo de la app.
    val nutritionLocale = LocaleManager.getEffectiveLocale(nutritionContext)
    val dateLabel = remember(selectedDate, nutritionLocale) { nutritionHeroDateLabel(selectedDate, nutritionLocale) }
    // Abre el registro desde la isla con la misma regla que la píldora de abajo: sin plan, pide crear uno.
    val openFoodLogger: () -> Unit = {
        if (!isFoodLoggingAvailable(settings, activePlan != null)) {
            showPlanRequiredDialog = true
        } else {
            foodLoggerInitialDescription = null
            foodLoggerInitialTab = 0
            viewModel.requestFoodLoggerOpen(tab = 0)
        }
    }

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
                    )
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

                item {
                    QuickAddBar(
                        onMealTypeClick = { meal ->
                            selectedMealForLogger = meal
                            foodLoggerInitialDescription = null
                            foodLoggerInitialTab = 0
                            viewModel.requestFoodLoggerOpen(tab = 0, mealType = meal)
                        },
                    )
                }

                items(MealSlots.mealOrder) { mealType ->
                    val group = mealGroups.find { it.mealType == mealType }
                    MealGroupCard(
                        mealType = mealType,
                        group = group,
                        onDelete = { viewModel.deleteLog(it) },
                        onEdit = { viewModel.requestFoodLoggerOpen(tab = 0, editLogId = it) },
                        onAddFood = {
                            selectedMealForLogger = mealType
                            foodLoggerInitialDescription = null
                            foodLoggerInitialTab = 0
                            viewModel.requestFoodLoggerOpen(tab = 0, mealType = mealType)
                        },
                    )
                }

                if (onNavigateToMealHistory != null) {
                    item {
                        TextButton(
                            onClick = onNavigateToMealHistory,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp.adapt(nutritionAdapt)).tourAnchor(TourAnchors.NUTRITION_HISTORIAL),
                        ) {
                            Icon(Icons.Default.History, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Ver historial de comidas", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                item {
                    NutritionBalanceModule(
                        loaded = userStateLoaded,
                        balance = dailyEnergyBalance,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).kpknEntrance("nutrition", 3),
                    )
                }

                if (dailyTotals.calories > 0) {
                    item {
                        MacroBarsSection(dailyTotals = dailyTotals)
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

                if (trendData.isNotEmpty()) {
                    item {
                        CalorieTrendChart(
                            trendData = trendData,
                        )
                    }
                }
                if (historySeries.points.isNotEmpty()) {
                    item { NutritionHistoryCoverageCard(historySeries) }
                }
            }
        }

        // Isla superior: aparece cuando la cabecera sale de la vista.
        NutritionTopIsland(
            visible = islandVisible,
            title = if (selectedDate == today.toString()) "Hoy" else dateLabel,
            subtitle = nutritionIslandSubtitle(
                loaded = userStateLoaded,
                kcal = kotlin.math.round(dailyTotals.calories).toInt(),
                kcalGoal = (goals as? DayGoalsResult.Present)?.goals?.calorieGoal?.takeIf { it > 0 },
                estimate = dailyTotals.isEstimate,
            ),
            onAdd = openFoodLogger,
        )

        val pillShape = RoundedCornerShape(999.dp)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 106.dp)
                .kpknGlass(hazeState = nutritionHazeState, shape = pillShape)
                .tourAnchor(TourAnchors.NUTRITION_REGISTRO)
                .clickable(role = Role.Button) {
                    // En modo «solo registro» el registro de alimentos sigue
                    // disponible aunque no haya plan activo.
                    if (!isFoodLoggingAvailable(settings, activePlan != null)) {
                        showPlanRequiredDialog = true
                    } else {
                        // C12: sin comida elegida, la solicitud deja que el logger proponga la de la hora.
                        foodLoggerInitialDescription = null
                        foodLoggerInitialTab = 0
                        viewModel.requestFoodLoggerOpen(tab = 0)
                    }
                }
                .padding(horizontal = 18.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.12f),
                    modifier = Modifier.size(26.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            // El texto de al lado ya dice «Registrar comida»: así TalkBack no lo lee dos veces.
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Registrar comida",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    letterSpacing = 0.2.sp,
                )
            }
        }

        // WP-U11: el aviso de «Deshacer» vive sobre el botón de registrar, nunca debajo de él.
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 160.dp),
        ) { data -> KpknSnackbar(data) }
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
 * La fecha del encabezado: «Jueves, 2 de octubre» en español, «Thursday, October 2» en inglés y, en cualquier otro idioma,
 * la fecha larga de ese idioma. Cada idioma arma su propio orden: el «de» solo existe en español. Si [selectedDate] no es
 * una fecha válida se muestra tal cual.
 */
internal fun nutritionHeroDateLabel(selectedDate: String, locale: Locale): String =
    try {
        val formatter = when (locale.language) {
            "es" -> DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", locale)
            "en" -> DateTimeFormatter.ofPattern("EEEE, MMMM d", locale)
            else -> DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)
        }
        LocalDate.parse(selectedDate).format(formatter).replaceFirstChar { it.titlecase(locale) }
    } catch (_: Exception) {
        selectedDate
    }

/**
 * Guardar la comida abierta es una edición si lo que se guarda lleva el id de la comida que se abrió para editar. El id
 * manda, no solo que haya una edición abierta: un borrador nuevo (por ejemplo tras recuperar el proceso) nunca se confunde con
 * ella, y una edición sigue siéndolo tras rotar.
 */
internal fun isEditSave(editingLogId: String?, saved: NutritionLog): Boolean =
    editingLogId != null && saved.id == editingLogId

// ═══════════════════════════════════════════════════════════════════════
// QUICK ADD BAR
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun QuickAddBar(onMealTypeClick: (MealType) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = TEAL.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, TEAL.copy(alpha = 0.20f)),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "REGISTRO RÁPIDO",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                color = TEAL,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MealSlots.mealOrder.forEach { meal ->
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(role = Role.Button) { onMealTypeClick(meal) },
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1E1E26),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                mealIcon(meal),
                                null,
                                tint = TEAL,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                MealSlots.mealLabel(meal),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// MACRO BARS SECTION
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun MacroBarsSection(dailyTotals: DailyMacroTotals) {
    val totalCal = dailyTotals.protein * 4 + dailyTotals.carbs * 4 + dailyTotals.fats * 9
    val protPct = if (totalCal > 0) (dailyTotals.protein * 4 / totalCal) else 0.0
    val carbPct = if (totalCal > 0) (dailyTotals.carbs * 4 / totalCal) else 0.0
    val fatPct = if (totalCal > 0) (dailyTotals.fats * 9 / totalCal) else 0.0

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF16161C)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                "DISTRIBUCIÓN CALÓRICA",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))

            // Stacked bar
            if (totalCal > 0) {
                Canvas(modifier = Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp))) {
                    drawRoundRect(Color.Gray.copy(alpha = 0.08f), cornerRadius = CornerRadius(7.dp.toPx()))
                    var x = 0f
                    val segments = listOf(protPct to PROTEIN_COLOR, carbPct to CARBS_COLOR, fatPct to FATS_COLOR)
                    segments.forEach { (pct, color) ->
                        val w = (size.width * pct).toFloat()
                        if (w > 0f) {
                            drawRect(color, Offset(x, 0f), Size(w, size.height))
                            x += w
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// MEAL GROUP CARD
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun MealGroupCard(
    mealType: MealType,
    group: MealGroup?,
    onDelete: (String) -> Unit,
    onEdit: (String) -> Unit,
    onAddFood: () -> Unit,
) {
    val label = MealSlots.mealLabel(mealType)
    val icon = mealIcon(mealType)
    val logs = group?.logs ?: emptyList()
    val totals = group?.totals ?: DailyMacroTotals()
    var expanded by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF16161C)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = if (expanded) "Contraer" else "Expandir", role = Role.Button) { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = TEAL.copy(alpha = 0.10f),
                        modifier = Modifier.size(36.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(icon, null, tint = TEAL, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        if (logs.isNotEmpty()) {
                            Text(
                                "${NutritionDisplayFormat.kcalLabel(totals)} · P ${kotlin.math.round(totals.protein).toInt()}g · C ${kotlin.math.round(totals.carbs).toInt()}g · G ${kotlin.math.round(totals.fats).toInt()}g",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                "Sin registros",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (logs.isNotEmpty()) {
                        Surface(
                            shape = CircleShape,
                            color = TEAL.copy(alpha = 0.12f),
                        ) {
                            Text(
                                "${logs.size}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = TEAL,
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    IconButton(onClick = onAddFood, modifier = Modifier.size(MIN_TOUCH_TARGET)) {
                        Icon(Icons.Default.Add, "Agregar comida a $label", tint = TEAL, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Entries
            AnimatedVisibility(visible = expanded && logs.isNotEmpty()) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    logs.forEach { log ->
                        LogEntry(log = log, onDelete = onDelete, onEdit = onEdit)
                        if (log != logs.last()) Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// LOG ENTRY
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun LogEntry(log: NutritionLog, onDelete: (String) -> Unit, onEdit: (String) -> Unit) {
    val foodNames = log.foods.joinToString(", ") { it.foodName }.ifEmpty { "Comida registrada" }
    val pro = log.foods.sumOf { it.protein }
    val car = log.foods.sumOf { it.carbs }
    val fat = log.foods.sumOf { it.fats }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            // WP-U11 (C9): tocar la fila abre la comida para editarla; el icono de eliminar sigue siendo su propio botón.
            .clickable(onClickLabel = "Editar", role = Role.Button) { onEdit(log.id) },
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    foodNames,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${NutritionDisplayFormat.logKcalSummary(log)} · P${kotlin.math.round(pro).toInt()} C${kotlin.math.round(car).toInt()} G${kotlin.math.round(fat).toInt()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                log.foods.mapNotNull { it.nutritionReferenceNote }.distinct().forEach { note ->
                    Text(note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (log.notes != null) {
                    Text(
                        log.notes,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // WP-U11 (C9): zona táctil de 40 dp (eran 28) y un nombre que dice qué comida se elimina.
            IconButton(onClick = { onDelete(log.id) }, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.Close,
                    "Eliminar comida: $foodNames",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// CALORIE TREND CHART
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun CalorieTrendChart(
    trendData: List<TrendPoint>,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF16161C)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                "TENDENCIA DE CALORÍAS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Últimos ${trendData.size} días",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )

            Spacer(Modifier.height(12.dp))

            // Cada punto lleva su propia meta por fecha; un día sin meta no
            // arrastra ningún default.
            val maxCal = maxOf(
                trendData.filter { it.hasData }.maxOfOrNull { it.calories } ?: 0.0,
                trendData.mapNotNull { it.goal }.maxOrNull() ?: 0.0,
            ) * 1.15

            val surfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

            val barCount = trendData.size

            Canvas(
                modifier = Modifier.fillMaxWidth().height(120.dp)
            ) {
                if (trendData.isEmpty() || maxCal <= 0) return@Canvas

                val w = size.width
                val h = size.height
                val slotWidth = w / barCount
                val barPadding = slotWidth * 0.15f
                val barWidth = slotWidth - barPadding * 2

                // Línea de objetivo por fecha: solo para los días con meta.
                trendData.forEachIndexed { i, point ->
                    val goalKcal = point.goal ?: return@forEachIndexed
                    val slotX = i * slotWidth
                    val goalY = h - (goalKcal / maxCal * h).toFloat()
                    drawLine(
                        color = surfaceVariant.copy(alpha = 0.3f),
                        start = Offset(slotX, goalY),
                        end = Offset(slotX + slotWidth, goalY),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                            floatArrayOf(8f, 6f)
                        ),
                    )
                }

                trendData.forEachIndexed { i, point ->
                    val slotX = i * slotWidth
                    val x = slotX + barPadding
                    if (!point.hasData) {
                        drawLine(
                            color = surfaceVariant.copy(alpha = 0.35f),
                            start = androidx.compose.ui.geometry.Offset(x + barWidth / 2f, h - 3f),
                            end = androidx.compose.ui.geometry.Offset(x + barWidth / 2f, h - 14f),
                            strokeWidth = 2f,
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(3f, 3f)),
                        )
                        return@forEachIndexed
                    }
                    val barH = (point.calories / maxCal * h).toFloat().coerceAtLeast(4f)
                    val y = h - barH
                    val overGoal = point.goal?.let { point.calories > it } ?: false
                    val barColor = if (overGoal) Color(0xFFE53935).copy(alpha = 0.7f) else TEAL.copy(alpha = 0.7f)

                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barH),
                        cornerRadius = CornerRadius(4.dp.toPx()),
                    )
                }
            }

            val locale = LocaleManager.getEffectiveLocale(LocalContext.current)

            // Day labels — each gets equal slot to align with bars
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                trendData.forEach { point ->
                    val dayLabel = try {
                        java.time.LocalDate.parse(point.date)
                            .format(java.time.format.DateTimeFormatter.ofPattern("E", locale))
                            .take(2)
                    } catch (_: Exception) { "?" }
                    Text(
                        dayLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun NutritionHistoryCoverageCard(series: NutritionHistorySeries) {
    val uncertain = series.points.filter { point ->
        point.intakeCaloriesMin != null && point.intakeCaloriesMax != null &&
            point.intakeCaloriesMin != point.intakeCaloriesMax
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF16161C)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("HISTÓRICO", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            Text("Cobertura: ${series.coverage.label}", style = MaterialTheme.typography.bodySmall)
            Text(
                if (uncertain.isEmpty()) "Sin rangos inciertos en este periodo."
                else "Los días con alimentos inciertos conservan su banda mínima–máxima.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            series.averageCaloriesOnRegisteredDays?.let { average ->
                Text("Promedio registrado: ${kotlin.math.round(average).toInt()} kcal", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
