package com.example.kpkn.screens.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.data.models.InitialRecoveryEvidenceFactory
import com.example.kpkn.data.models.InitialRecoveryMuscleScope
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.onboarding.WizChatAnswerKind
import com.example.kpkn.domain.onboarding.WizChatCopyCatalog
import com.example.kpkn.domain.onboarding.WizChatQuestion
import com.example.kpkn.domain.onboarding.WizChatQuestionId
import com.example.kpkn.domain.onboarding.WizChatStage
import com.example.kpkn.ui.components.KpknGlass
import com.example.kpkn.ui.components.kpknDockGlassOrFallback
import com.example.kpkn.R
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch

@Composable
fun SetupWizardScreen(
    mode: SetupWizardMode,
    draftId: String? = null,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    viewModel: SetupWizardViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by ProgramRepository.getInstance().settings.collectAsStateWithLifecycle()
    val hazeState = remember { HazeState() }
    val feedback = rememberWizChatFeedbackController()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val reducedMotion = wizChatReducedMotion()
    var lastFeedbackMessageId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(mode, draftId) { viewModel.initialize(mode, draftId = draftId) }
    LaunchedEffect(state.messages.lastOrNull()?.id, state.draft.wizChat.soundEnabled, settings.soundsEnabled, reducedMotion) {
        val message = state.messages.lastOrNull()
        if (lastFeedbackMessageId == null) {
            lastFeedbackMessageId = message?.id
        } else if (message?.id != lastFeedbackMessageId) {
            lastFeedbackMessageId = message?.id
            message?.let { feedback.playOnce(it.id, state.draft.wizChat.soundEnabled && settings.soundsEnabled && !it.fromUser) }
        }
        if (state.messages.isNotEmpty()) {
            if (reducedMotion) listState.scrollToItem(state.messages.lastIndex) else listState.animateScrollToItem(state.messages.lastIndex)
        }
    }
    BackHandler { if (!state.draft.wizChat.terminal) onCancel() else onDone() }
    val question = viewModel.activeQuestion()
    val accent = question?.stage?.accent() ?: WizChatTokens.orange
    Box(Modifier.fillMaxSize().background(WizChatTokens.background)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().hazeSource(state = hazeState).padding(bottom = 190.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item { WizChatHeader(state.draft.wizChat.stage, state.draft.wizChat.completedStages, state.draft.wizChat.soundEnabled, accent, onSound = viewModel::toggleSound, onClose = onCancel) }
            item { WizChatChapterSummary(state.draft.wizChat.stage, state.draft.wizChat.stage in state.draft.wizChat.completedStages) }
            items(state.messages, key = { it.id }) { message ->
                AnimatedVisibility(
                    visible = true,
                    enter = if (reducedMotion) EnterTransition.None else fadeIn(tween(220)) + slideInVertically(initialOffsetY = { 6 }),
                    exit = if (reducedMotion) ExitTransition.None else fadeOut(tween(220)),
                ) {
                    WizChatMessageBubble(message) { it.questionId?.let(viewModel::edit) }
                }
            }
            if (question?.id == WizChatQuestionId.REVIEW) item { WizChatReview(state, viewModel.ringsPreview()) }
            if (state.machineState == com.example.kpkn.domain.onboarding.WizChatMachineState.UnsupportedDraft) item {
                Text(state.errors["draft"] ?: "Borrador no compatible", color = WizChatTokens.danger, modifier = Modifier.padding(18.dp))
            }
        }
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .kpknDockGlassOrFallback(hazeState, WizChatTokens.dockShape, withBorder = false),
            color = Color.Transparent,
            shape = WizChatTokens.dockShape,
        ) {
            question?.let {
                WizChatQuestionDock(it, state, viewModel, accent, onCommit = { scope.launch { if (viewModel.commit() != null) onDone() } })
            }
        }
    }
}

@Composable
private fun WizChatHeader(stage: WizChatStage, completed: Set<WizChatStage>, sound: Boolean, accent: Color, onSound: () -> Unit, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Close, "Salir", tint = WizChatTokens.text) }
        Image(painterResource(R.drawable.kpknicon), contentDescription = "KPKN", modifier = Modifier.size(30.dp))
        Column(Modifier.weight(1f)) {
            Text("WIZCHAT", color = WizChatTokens.text, fontWeight = androidx.compose.ui.text.font.FontWeight.Black)
            Text(WizChatCopyCatalog.progressLabel(stage), color = accent)
            Row(Modifier.fillMaxWidth().padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                WizChatStage.entries.forEach { value -> Box(Modifier.weight(1f).height(3.dp).background(if (value == stage || value in completed) value.accent() else Color.White.copy(alpha = .13f))) }
            }
        }
        IconButton(onClick = onSound, modifier = Modifier.size(48.dp)) { Icon(if (sound) Icons.Default.GraphicEq else Icons.Default.Close, "Sonido", tint = accent) }
    }
}

@Composable
private fun WizChatQuestionDock(question: WizChatQuestion, state: SetupWizardState, vm: SetupWizardViewModel, accent: Color, onCommit: () -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    var multi by remember(question.id, state.draft.wizChat.revision) { mutableStateOf(emptySet<String>()) }
    val answerAction: () -> Unit = { keyboard?.hide(); focus.clearFocus(); vm.answerAction(question.id); Unit }
    val multiAction: () -> Unit = { keyboard?.hide(); focus.clearFocus(); vm.answerMulti(question.id, multi.toList()); Unit }
    WizChatAnswerDock(
        content = {
        when (question.id) {
            WizChatQuestionId.P_NAME -> NameAnswer(vm, state.draft.name, keyboard, focus)
            WizChatQuestionId.P_AGE -> NumberAnswer(question, state.draft.ageYears?.toDouble(), 13.0..100.0, 1.0, vm, accent)
            WizChatQuestionId.P_HEIGHT -> NumberAnswer(question, state.draft.heightCm, 100.0..250.0, 1.0, vm, accent)
            WizChatQuestionId.P_WEIGHT -> NumberAnswer(question, state.draft.weightKg, 20.0..500.0, .1, vm, accent)
            WizChatQuestionId.T_DAYS -> NumberAnswer(question, state.draft.daysPerWeek?.toDouble(), 1.0..6.0, 1.0, vm, accent)
            WizChatQuestionId.T_TIME -> NumberAnswer(question, state.draft.minutesPerSession?.toDouble(), 20.0..100.0, 5.0, vm, accent)
            WizChatQuestionId.T_PLAN -> PlanAnswer(state, vm, accent)
            WizChatQuestionId.N_RESULT -> NutritionAnswer(state, vm, accent)
            WizChatQuestionId.R_RESULT -> RingsAnswer(state, vm, accent)
            WizChatQuestionId.REVIEW -> Text("La configuración queda lista para una única activación transaccional.", color = WizChatTokens.muted)
            else -> ChoiceAnswer(question, multi, { multi = it }, vm, accent)
        }
        },
        accent = accent,
        onSkip = if (question.allowSkip) ({ vm.skip(question.id); Unit }) else null,
        onAction = when (question.id) { WizChatQuestionId.REVIEW -> onCommit; else -> if (question.kind == WizChatAnswerKind.ACTION) answerAction else if (question.kind == WizChatAnswerKind.MULTI_CHOICE) multiAction else null },
        actionLabel = if (question.id == WizChatQuestionId.REVIEW) "Activar configuración" else "Continuar",
    )
}

@Composable
private fun RingsAnswer(state: SetupWizardState, vm: SetupWizardViewModel, accent: Color) {
    val mapping = vm.ringsPreview()
    val answers = state.draft.ringsAnswers ?: SetupRingsAnswers()
    val selectedScope = when (answers.muscleScope) {
        InitialRecoveryMuscleScope.FULL_BODY -> "Cuerpo entero"
        InitialRecoveryMuscleScope.SELECTED -> "Elegir músculos"
        InitialRecoveryMuscleScope.UNKNOWN -> "No especificar"
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when (mapping.completion) {
            com.example.kpkn.domain.onboarding.RingsCompletion.VALID -> mapping.evidence?.let { evidence ->
                Text("Batería muscular: ${evidence.muscularScore}/100", color = accent)
                Text("Energía: ${evidence.systemScore}/100 · Columna: ${evidence.structureScore}/100", color = WizChatTokens.text)
                Text("Estimación inicial aproximada · confianza ${evidence.confidence}/100 · vence en 14 días", color = WizChatTokens.muted)
            }
            com.example.kpkn.domain.onboarding.RingsCompletion.UNKNOWN -> Text("Sin calibrar: no hay evidencia suficiente para fabricar una carga histórica.", color = WizChatTokens.muted)
            com.example.kpkn.domain.onboarding.RingsCompletion.PRESERVE -> Text("Conservaré la estimación existente sin rejuvenecer su fecha.", color = WizChatTokens.muted)
            com.example.kpkn.domain.onboarding.RingsCompletion.OMITTED -> Text("Quitaré la estimación inicial, sin borrar un check-in real.", color = WizChatTokens.muted)
            com.example.kpkn.domain.onboarding.RingsCompletion.INCOMPLETE -> Text("Faltan las tres sensaciones para generar una estimación completa.", color = WizChatTokens.danger)
        }
        WizChatAdvancedHost(
            open = "rings" in state.draft.wizChat.advancedBranches,
            onOpen = vm::openRingsAdvanced,
        ) {
            Text("Los ajustes manuales solo se aplicarán al activar esta configuración.", color = WizChatTokens.muted)
            Text("¿Qué músculos quieres que queden disponibles para ajustar?", color = WizChatTokens.text)
            WizChatChoiceGroup(
                options = listOf("No especificar", "Cuerpo entero", "Elegir músculos"),
                selected = setOf(selectedScope),
                multi = false,
                accent = accent,
            ) { value ->
                vm.setRingsMuscleScope(
                    when (value) {
                        "Cuerpo entero" -> InitialRecoveryMuscleScope.FULL_BODY
                        "Elegir músculos" -> InitialRecoveryMuscleScope.SELECTED
                        else -> InitialRecoveryMuscleScope.UNKNOWN
                    },
                )
            }
            if (answers.muscleScope == InitialRecoveryMuscleScope.SELECTED) {
                WizChatChoiceGroup(
                    options = InitialRecoveryEvidenceFactory.INITIAL_RECOVERY_MUSCLE_PILLARS,
                    selected = answers.recentMuscles,
                    multi = true,
                    accent = accent,
                    onSelect = vm::toggleRingsMuscle,
                )
            }
            val adjustableMuscles = when (answers.muscleScope) {
                InitialRecoveryMuscleScope.FULL_BODY -> InitialRecoveryEvidenceFactory.INITIAL_RECOVERY_MUSCLE_PILLARS
                InitialRecoveryMuscleScope.SELECTED -> answers.recentMuscles.toList().sorted()
                InitialRecoveryMuscleScope.UNKNOWN -> emptyList()
            }
            adjustableMuscles.forEach { muscle ->
                Text(muscle, color = WizChatTokens.text)
                WizChatChoiceGroup(
                    options = manualLevelOptions,
                    selected = setOf(manualLevelOptions.getOrNull((state.draft.manualMuscleOverrides[muscle] ?: 0) - 1).orEmpty()),
                    multi = false,
                    accent = accent,
                ) { value -> vm.setManualMuscleOverride(muscle, manualLevelOptions.indexOf(value) + 1) }
            }
            Text("Energía", color = WizChatTokens.text)
            WizChatChoiceGroup(manualLevelOptions, setOf(manualLevelOptions.getOrNull((state.draft.manualEnergyOverride ?: 0) - 1).orEmpty()), false, accent) { value -> vm.setManualEnergyOverride(manualLevelOptions.indexOf(value) + 1) }
            Text("Columna / estructura", color = WizChatTokens.text)
            WizChatChoiceGroup(manualLevelOptions, setOf(manualLevelOptions.getOrNull((state.draft.manualStructureOverride ?: 0) - 1).orEmpty()), false, accent) { value -> vm.setManualStructureOverride(manualLevelOptions.indexOf(value) + 1) }
            androidx.compose.material3.TextButton(onClick = vm::resetManualRecoveryAdjustments) { Text("Restablecer ajustes de esta configuración", color = WizChatTokens.muted) }
        }
    }
}

private val manualLevelOptions = listOf("1 · Descansado", "2 · Bien", "3 · Intermedio", "4 · Cargado", "5 · Muy cargado")

@Composable
private fun NameAnswer(vm: SetupWizardViewModel, value: String, keyboard: androidx.compose.ui.platform.SoftwareKeyboardController?, focus: androidx.compose.ui.focus.FocusManager) {
    BasicTextField(
        value = value,
        onValueChange = vm::updateNameDraft,
        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(horizontal = 14.dp, vertical = 15.dp),
        textStyle = androidx.compose.ui.text.TextStyle(color = WizChatTokens.text),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); focus.clearFocus(); vm.answerText(value) }),
        decorationBox = { field -> if (value.isBlank()) Text("Tu nombre o cómo quieres que te llamemos", color = WizChatTokens.muted); field() },
    )
}

@Composable
private fun NumberAnswer(question: WizChatQuestion, selected: Double?, range: ClosedFloatingPointRange<Double>, step: Double, vm: SetupWizardViewModel, accent: Color) {
    val values = remember(range, step) { generateSequence(range.start) { next -> (next + step).takeIf { it <= range.endInclusive + 0.0001 } }.toList() }
    WizChatNumberWheel(values, selected, question.unit.orEmpty(), accent, onSettled = { vm.answerNumber(question.id, it) })
}

@Composable
private fun ChoiceAnswer(question: WizChatQuestion, selected: Set<String>, setSelected: (Set<String>) -> Unit, vm: SetupWizardViewModel, accent: Color) {
    val multi = question.kind == WizChatAnswerKind.MULTI_CHOICE
    WizChatChoiceGroup(question.options, selected, multi, accent) { option ->
        if (multi) setSelected(if (option in selected) selected - option else selected + option) else vm.answerChoice(question.id, option)
    }
}

@Composable
private fun PlanAnswer(state: SetupWizardState, vm: SetupWizardViewModel, accent: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.planCandidates.forEach { plan ->
            Surface(color = Color.White.copy(alpha = .08f), shape = WizChatTokens.optionShape, modifier = Modifier.fillMaxWidth().heightIn(min = 70.dp).then(Modifier.padding(0.dp))) {
                Column(Modifier.fillMaxWidth().padding(14.dp).clickable { vm.selectPlan(plan.id) }) {
                    Text(plan.title, color = WizChatTokens.text, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Text(plan.subtitle, color = accent)
                    Text(plan.description, color = WizChatTokens.muted, maxLines = 2)
                }
            }
        }
        if (state.planCandidates.isEmpty()) Text("Aún faltan frecuencia, días, equipo o volumen para listar recetas compatibles.", color = WizChatTokens.muted)
        WizChatAdvancedHost(
            open = "training" in state.draft.wizChat.advancedBranches,
            onOpen = vm::openAdvanced,
        ) {
            Text("La edición avanzada conserva la receta elegida y permite continuar con ajustes explícitos.", color = WizChatTokens.muted)
        }
    }
}

@Composable
private fun NutritionAnswer(state: SetupWizardState, vm: SetupWizardViewModel, accent: Color) {
    val draft = state.draft.nutritionDraft ?: return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Objetivo medido", color = WizChatTokens.text)
        WizChatChoiceGroup(listOf("Peso", "Grasa corporal", "Masa muscular"), setOf(when (draft.goalMetric) { com.example.kpkn.data.models.GoalMetric.WEIGHT -> "Peso"; com.example.kpkn.data.models.GoalMetric.BODY_FAT -> "Grasa corporal"; com.example.kpkn.data.models.GoalMetric.MUSCLE_MASS -> "Masa muscular" }), false, accent) { value ->
            vm.updateNutritionInput { it.copy(goalMetric = when (value) { "Grasa corporal" -> com.example.kpkn.data.models.GoalMetric.BODY_FAT; "Masa muscular" -> com.example.kpkn.data.models.GoalMetric.MUSCLE_MASS; else -> com.example.kpkn.data.models.GoalMetric.WEIGHT }) }
        }
        NutritionField("Meta (kg o %)", draft.targetValueText.ifBlank { draft.targetWeightText }) { value -> vm.updateNutritionInput { it.copy(targetValueText = value, targetWeightText = value, targetBodyFatText = if (draft.goalMetric == com.example.kpkn.data.models.GoalMetric.BODY_FAT) value else it.targetBodyFatText, targetMuscleText = if (draft.goalMetric == com.example.kpkn.data.models.GoalMetric.MUSCLE_MASS) value else it.targetMuscleText) } }
        if (draft.mode == "professional") {
            NutritionField("Calorías profesionales", draft.manualCalorieTargetText) { value -> vm.updateNutritionInput { it.copy(manualCalorieTargetText = value) } }
            NutritionField("Proteína (g)", draft.manualProteinText) { value -> vm.updateNutritionInput { it.copy(manualProteinText = value) } }
            NutritionField("Carbohidratos (g)", draft.manualCarbsText) { value -> vm.updateNutritionInput { it.copy(manualCarbsText = value) } }
            NutritionField("Grasas (g)", draft.manualFatText) { value -> vm.updateNutritionInput { it.copy(manualFatText = value) } }
        }
        state.nutritionPlanPreview?.let { Text("Referencia preparada: ${it.calorieTarget} kcal · ${it.proteinGoal}/${it.carbGoal}/${it.fatGoal} g", color = accent) }
    }
}

@Composable
private fun NutritionField(label: String, value: String, onChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, color = WizChatTokens.muted)
        BasicTextField(value, onChange, Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(12.dp), textStyle = androidx.compose.ui.text.TextStyle(color = WizChatTokens.text), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next))
    }
}
