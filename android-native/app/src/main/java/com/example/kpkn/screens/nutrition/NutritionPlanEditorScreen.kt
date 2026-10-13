package com.example.kpkn.screens.nutrition

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.data.repository.NutritionPlanCommitCoordinator
import com.example.kpkn.data.repository.NutritionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.screens.nutrition.design.editor.NutritionPlanEditorActions
import com.example.kpkn.screens.nutrition.design.editor.NutritionPlanEditorContent
import java.time.LocalDate

/**
 * Editor nutricional DIRECTO: secciones editables en cualquier orden, sin
 * pasos, sin botones «Siguiente» y sin secuencia guiada. Es la única puerta de
 * creación/edición del plan tras el onboarding.
 *
 * Aquí solo se cablea el ViewModel y el estado de interfaz (la meta abierta y
 * el día elegido); la presentación vive en `screens/nutrition/design/editor/`.
 */
@Composable
fun NutritionPlanEditorScreen(
    planId: String?,
    pendingDraftId: String?,
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: NutritionPlanEditorViewModel = rememberEditorViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(planId, pendingDraftId) { viewModel.initialize(planId, pendingDraftId) }
    LaunchedEffect(state.isSaved) { if (state.isSaved) onDone() }

    var goalOpen by rememberSaveable { mutableStateOf(false) }
    var selectedDay by rememberSaveable { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState()
    val today = remember { LocalDate.now() }
    // Mientras carga, el título sigue a la navegación (plan nuevo o existente); después, al borrador cargado.
    val isNew = if (state.isLoading) planId == null else state.draft.planId == null

    NutritionPlanEditorContent(
        state = state,
        actions = NutritionPlanEditorActions(
            onBack = onBack,
            onDirection = viewModel::setDirection,
            onGoalMetric = { metric ->
                viewModel.setGoalMetric(metric)
                // Una cifra de otra meta no vale para esta: se borra y el control queda abierto, vacío.
                if (state.draft.targetValueText.isNotBlank()) {
                    viewModel.setTargetValue("")
                    goalOpen = true
                }
            },
            onTargetValue = viewModel::setTargetValue,
            onOpenGoal = { goalOpen = true },
            onRemoveGoal = {
                viewModel.setTargetValue("")
                goalOpen = false
            },
            onAge = viewModel::setAge,
            onHeight = viewModel::setHeight,
            onWeight = viewModel::setWeight,
            onEquation = { sex -> viewModel.setEquationSex(sex) },
            onActivity = viewModel::setActivity,
            onEligibilityUnknown = viewModel::setEligibilityUnknown,
            onMedicalRestriction = viewModel::setMedicalRestriction,
            onPregnant = viewModel::setPregnant,
            onLactating = viewModel::setLactating,
            onCalories = viewModel::setCalories,
            onProtein = { grams -> viewModel.setMacro(protein = grams) },
            onCarbs = { grams -> viewModel.setMacro(carbs = grams) },
            onFat = { grams -> viewModel.setMacro(fat = grams) },
            onSelectDay = { day -> selectedDay = day },
            onWeeklyDistribution = viewModel::setWeeklyDistribution,
            onProvenance = viewModel::setProvenance,
            onMode = viewModel::setMode,
            onSave = viewModel::save,
        ),
        isNew = isNew,
        goalOpen = goalOpen,
        selectedDay = selectedDay,
        today = today,
        listState = listState,
    )
}

@Composable
private fun rememberEditorViewModel(): NutritionPlanEditorViewModel {
    val context = LocalContext.current.applicationContext
    return viewModel {
        val nutritionRepository = NutritionRepository.getInstance()
        val programRepository = ProgramRepository.getInstance()
        val factory = com.example.kpkn.data.onboarding.persistenceFactory(context)
        NutritionPlanEditorViewModel(
            nutritionRepository = nutritionRepository,
            programRepository = programRepository,
            commits = NutritionPlanCommitCoordinator(
                db = factory.database,
                nutritionRepository = nutritionRepository,
                programRepository = programRepository,
            ),
            drafts = factory.drafts,
        )
    }
}
