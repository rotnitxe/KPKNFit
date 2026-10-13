package com.example.kpkn.screens.nutrition.design.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.GoalMetric
import com.example.kpkn.data.models.PlanDirection
import com.example.kpkn.domain.nutrition.EerActivity
import com.example.kpkn.domain.nutrition.EerSex
import com.example.kpkn.domain.nutrition.NutritionEditorProvenance
import com.example.kpkn.domain.nutrition.NutritionPlanEditorMode
import com.example.kpkn.domain.nutrition.NutritionWeeklyDistributionMode
import com.example.kpkn.screens.nutrition.NutritionPlanEditorUiState
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknSkeletonModule
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTopIsland
import com.example.kpkn.ui.design.KpknTopRow
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.design.kpknEntrance
import com.example.kpkn.ui.design.rememberIslandVisible

/** Grupo de la entrada escalonada de los módulos del editor. */
private const val EntranceGroup = "nutrition.editor"

/** Posición del título en la lista (0 es la barra superior): la isla aparece cuando este bloque sale de la vista. */
private const val TitleIndex = 1

/** Aire bajo el último módulo para que la isla de «Guardar» no lo tape. */
private val BottomClearance = 120.dp

/** Con avisos de error, la franja de abajo es más alta: hay que poder llegar al último módulo por encima de ellos. */
private val BottomClearanceWithErrors = 200.dp

/**
 * Lo que el editor pide a su ViewModel: un lambda por acción. Sin estado: la pantalla lo arma con el ViewModel y con su estado de interfaz;
 * la prueba lo arma con lambdas que anotan las llamadas.
 */
class NutritionPlanEditorActions(
    val onBack: () -> Unit = {},
    val onDirection: (PlanDirection) -> Unit = {},
    val onGoalMetric: (GoalMetric) -> Unit = {},
    val onTargetValue: (String) -> Unit = {},
    val onOpenGoal: () -> Unit = {},
    val onRemoveGoal: () -> Unit = {},
    val onAge: (String) -> Unit = {},
    val onHeight: (String) -> Unit = {},
    val onWeight: (String) -> Unit = {},
    val onEquation: (EerSex) -> Unit = {},
    val onActivity: (EerActivity) -> Unit = {},
    val onEligibilityUnknown: (Boolean) -> Unit = {},
    val onMedicalRestriction: (Boolean) -> Unit = {},
    val onPregnant: (Boolean) -> Unit = {},
    val onLactating: (Boolean) -> Unit = {},
    val onCalories: (Int) -> Unit = {},
    val onProtein: (Double) -> Unit = {},
    val onCarbs: (Double) -> Unit = {},
    val onFat: (Double) -> Unit = {},
    val onSelectDay: (Int?) -> Unit = {},
    val onWeeklyDistribution: (NutritionWeeklyDistributionMode) -> Unit = {},
    val onProvenance: (NutritionEditorProvenance) -> Unit = {},
    val onMode: (NutritionPlanEditorMode) -> Unit = {},
    val onSave: () -> Unit = {},
)

/**
 * Raíz del editor del plan, sin estado. Sobre el negro: la barra superior con «Atrás» y el título, la lista de módulos (`LazyColumn`), la
 * barra que aparece cuando el título sale de la vista y, abajo, los avisos de error y la isla de «Guardar». Con la carga, tres esqueletos.
 * [goalOpen] y [selectedDay] son estado de interfaz que quien llama guarda; [listState] lo puede mover la prueba.
 */
@Composable
fun NutritionPlanEditorContent(
    state: NutritionPlanEditorUiState,
    actions: NutritionPlanEditorActions,
    isNew: Boolean,
    goalOpen: Boolean,
    selectedDay: Int?,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val title = if (isNew) "Nuevo plan" else "Editar plan"
    val draft = state.draft
    val base = state.base
    val showIsland = rememberIslandVisible(listState, afterItemIndex = TitleIndex)
    Box(modifier = modifier.fillMaxSize().background(KpknSurface.canvas)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = if (state.errors.isNotEmpty()) BottomClearanceWithErrors else BottomClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "top") { KpknTopRow(onBack = actions.onBack) }
            item(key = "title") {
                Text(
                    text = title,
                    style = KpknType.display,
                    color = WizardColors.text,
                    modifier = Modifier.fillMaxWidth().semantics { heading() },
                )
            }
            if (state.isLoading) {
                items(count = 3) { index ->
                    KpknSkeletonModule(
                        lines = 3,
                        modifier = Modifier.fillMaxWidth().kpknEntrance(EntranceGroup, index),
                    )
                }
            } else {
                item(key = "direction") {
                    EditorDirectionModule(
                        draft = draft,
                        goalOpen = goalOpen,
                        onDirection = actions.onDirection,
                        onGoalMetric = actions.onGoalMetric,
                        onTargetValue = actions.onTargetValue,
                        onOpenGoal = actions.onOpenGoal,
                        onRemoveGoal = actions.onRemoveGoal,
                        modifier = Modifier.fillMaxWidth().kpknEntrance(EntranceGroup, 0),
                    )
                }
                item(key = "calculation") {
                    EditorCalculationModule(
                        draft = draft,
                        onAge = actions.onAge,
                        onHeight = actions.onHeight,
                        onWeight = actions.onWeight,
                        onEquation = actions.onEquation,
                        onActivity = actions.onActivity,
                        onEligibilityUnknown = actions.onEligibilityUnknown,
                        onMedicalRestriction = actions.onMedicalRestriction,
                        onPregnant = actions.onPregnant,
                        onLactating = actions.onLactating,
                        modifier = Modifier.fillMaxWidth().kpknEntrance(EntranceGroup, 1),
                    )
                }
                item(key = "energy") {
                    EditorEnergyModule(
                        base = base,
                        eerKcal = state.recommendation?.eerKcal,
                        todayGoalFixedKcal = state.todayGoalFixedKcal,
                        onCalories = actions.onCalories,
                        onProtein = actions.onProtein,
                        onCarbs = actions.onCarbs,
                        onFat = actions.onFat,
                        modifier = Modifier.fillMaxWidth().kpknEntrance(EntranceGroup, 2),
                    )
                }
                item(key = "week") {
                    EditorWeekModule(
                        weeklyDistribution = draft.weeklyDistribution,
                        weeklyTargets = state.weeklyTargets,
                        selectedDay = selectedDay,
                        onWeeklyDistribution = actions.onWeeklyDistribution,
                        onSelectDay = actions.onSelectDay,
                        modifier = Modifier.fillMaxWidth().kpknEntrance(EntranceGroup, 3),
                    )
                }
                item(key = "provenance") {
                    EditorProvenanceModule(
                        draft = draft,
                        onProvenance = actions.onProvenance,
                        modifier = Modifier.fillMaxWidth().kpknEntrance(EntranceGroup, 4),
                    )
                }
                item(key = "status") {
                    EditorStatusModule(
                        mode = draft.mode,
                        onMode = actions.onMode,
                        modifier = Modifier.fillMaxWidth().kpknEntrance(EntranceGroup, 5),
                    )
                }
            }
        }

        KpknTopIsland(
            visible = showIsland && !state.isLoading,
            title = title,
            subtitle = base?.let { editorKcalText(it.caloriesKcal) },
            leading = {
                KpknIconButton(icon = KpknLineIcons.ChevronLeft, contentDescription = "Atrás", onClick = actions.onBack)
            },
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.errors.values.forEach { message ->
                KpknModule(
                    modifier = Modifier.fillMaxWidth(),
                    accent = WizardColors.danger,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(text = message, style = KpknType.body, color = WizardColors.text)
                }
            }
            EditorSaveIsland(
                changeText = saveChangeText(state, isNew),
                canSave = state.isDirty && !state.isSaving && !state.isLoading,
                saving = state.isSaving,
                onSave = actions.onSave,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Lo que cambia al guardar, a la vista junto a «Guardar». Mientras carga, no hay cambio que mostrar. */
private fun saveChangeText(state: NutritionPlanEditorUiState, isNew: Boolean): String =
    if (state.isLoading) {
        "Cargando…"
    } else {
        editorChangeText(
            originalKcal = state.originalBase?.caloriesKcal,
            currentKcal = state.base?.caloriesKcal,
            dirty = state.isDirty,
            isNew = isNew,
        )
    }
