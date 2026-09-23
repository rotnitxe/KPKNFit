package com.example.kpkn.screens.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.example.kpkn.R
import com.example.kpkn.screens.home.RingColors
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

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
    val feedback = rememberWizChatFeedbackController()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var navigatedAfterCommit by remember { mutableStateOf(false) }
    val reducedMotion = wizChatReducedMotion()
    val messageSlidePx = with(LocalDensity.current) { 6.dp.roundToPx() }
    var lastFeedbackMessageId by remember { mutableStateOf<String?>(null) }
    var feedbackInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(mode, draftId) { viewModel.initialize(mode, draftId = draftId) }
    val acknowledgement = state.messages.lastOrNull { it.id.startsWith("ack:") }
    LaunchedEffect(acknowledgement?.id, state.isLoading) {
        if (state.isLoading) return@LaunchedEffect
        val message = acknowledgement
        if (!feedbackInitialized) {
            feedbackInitialized = true
            lastFeedbackMessageId = message?.id
        } else if (message?.id != lastFeedbackMessageId) {
            lastFeedbackMessageId = message?.id
            message?.let { feedback.playOnce(it.id, state.draft.wizChat.soundEnabled && settings.soundsEnabled) }
        }
    }
    BackHandler { onCancel() }
    val question = viewModel.activeQuestion()
    val answerInputs = rememberSaveable(question?.id, saver = WizChatAnswerInputsSaver) {
        WizChatAnswerInputs(
            name = state.draft.name,
            squat = state.draft.powerliftingProfile?.squat1RM?.toString().orEmpty(),
            bench = state.draft.powerliftingProfile?.bench1RM?.toString().orEmpty(),
            deadlift = state.draft.powerliftingProfile?.deadlift1RM?.toString().orEmpty(),
        )
    }
    val accent = WizChatTokens.stageAccent(state.draft.wizChat.stage)
    val history = state.messages.filterNot { it.id.startsWith("current:") }
    var lastHistoryId by remember { mutableStateOf<String?>(null) }
    var arrivingAnswerId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(history.lastOrNull()?.id) {
        val latest = history.lastOrNull()?.id
        if (lastHistoryId != null && latest != lastHistoryId) {
            arrivingAnswerId = history.lastOrNull { it.fromUser }?.id
            delay(240)
            arrivingAnswerId = null
        }
        lastHistoryId = latest
    }
    val stage = state.draft.wizChat.stage
    val previousAll = history.filter { it.stage != stage }
    val transitionIds = previousAll.groupBy { it.stage }
        .mapNotNull { (_, messages) -> messages.lastOrNull { it.id.startsWith("ack:") }?.id }
        .toSet()
    val previous = previousAll.filterNot { it.id in transitionIds }
    val current = history.filter { it.stage == stage || it.id in transitionIds }
    var showPrevious by remember(stage) { mutableStateOf(false) }
    var following by rememberSaveable { mutableStateOf(true) }
    var pendingUserScroll by remember { mutableStateOf(false) }
    var answerJustArrived by remember { mutableStateOf(false) }
    var lastAnswerId by remember { mutableStateOf<String?>(null) }
    var answerHistorySeeded by remember { mutableStateOf(false) }
    val latestAnswerId = history.lastOrNull { it.fromUser }?.id
    LaunchedEffect(latestAnswerId, state.isLoading) {
        if (state.isLoading) return@LaunchedEffect
        if (!answerHistorySeeded) {
            answerHistorySeeded = true
            lastAnswerId = latestAnswerId
            return@LaunchedEffect
        }
        if (latestAnswerId != null && latestAnswerId != lastAnswerId) {
            lastAnswerId = latestAnswerId
            following = true
            answerJustArrived = true
            try {
                delay(if (reducedMotion) 120L else 720L)
            } finally {
                answerJustArrived = false
            }
        }
    }
    val showTyping = (state.isSubmittingAnswer || answerJustArrived) &&
        state.machineState != com.example.kpkn.domain.onboarding.WizChatMachineState.RecoverableError
    val jumpToQuestionVisible by remember { derivedStateOf {
        listState.layoutInfo.totalItemsCount > 0 &&
            listState.layoutInfo.visibleItemsInfo.none { it.index == listState.layoutInfo.totalItemsCount - 1 }
    } }
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(dragged) {
        if (dragged) {
            pendingUserScroll = true
        } else if (pendingUserScroll) {
            pendingUserScroll = false
            snapshotFlow { listState.isScrollInProgress }.first { !it }
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            if (info.totalItemsCount == 0 || lastVisible < info.totalItemsCount - 2) following = false
        }
    }
    LaunchedEffect(question?.id, history.size, showTyping, following, state.isLoading) {
        if (!following || state.isLoading) return@LaunchedEffect
        if (state.machineState == com.example.kpkn.domain.onboarding.WizChatMachineState.UnsupportedDraft) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.viewportEndOffset }.distinctUntilChanged().collect {
            val total = listState.layoutInfo.totalItemsCount
            if (total > 0) {
                if (reducedMotion) listState.scrollToItem(total - 1)
                else listState.animateScrollToItem(total - 1)
            }
        }
    }
    Box(Modifier.fillMaxSize().background(WizChatTokens.background)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
            WizChatHeader(state.draft.wizChat.stage, state.draft.wizChat.soundEnabled, onSound = viewModel::toggleSound, onClose = onCancel)
            WizChatBusyIndicator(state, accent)
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(top = 8.dp, bottom = if (jumpToQuestionVisible) 80.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (previous.isNotEmpty()) {
                item(key = "previous-summary") {
                    Surface(color = WizChatTokens.option, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clickable { showPrevious = !showPrevious }) {
                        Text(if (showPrevious) "Ocultar respuestas anteriores" else "Ver respuestas anteriores · ${previous.count { it.fromUser }}",
                            color = WizChatTokens.muted, modifier = Modifier.padding(15.dp))
                    }
                }
                if (showPrevious) items(previous, key = { it.id }) { message ->
                    WizChatMessageBubble(message) { it.questionId?.let(viewModel::edit) }
                }
            }
            items(current, key = { it.id }) { message ->
                if (message.id == arrivingAnswerId && !reducedMotion) {
                    val transition = remember(message.id) { MutableTransitionState(false).apply { targetState = true } }
                    AnimatedVisibility(visibleState = transition,
                        enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { messageSlidePx }, exit = ExitTransition.None) {
                        WizChatMessageBubble(message) { it.questionId?.let(viewModel::edit) }
                    }
                } else WizChatMessageBubble(message) { it.questionId?.let(viewModel::edit) }
            }
            if (showTyping && question != null) {
                item(key = "typing") { WizChatTypingIndicator(accent, reducedMotion) }
            }
            when {
                showTyping && question != null -> Unit
                state.machineState == com.example.kpkn.domain.onboarding.WizChatMachineState.Loading -> item { Text("Preparando tu configuración…", color = WizChatTokens.muted, modifier = Modifier.padding(18.dp)) }
                state.machineState == com.example.kpkn.domain.onboarding.WizChatMachineState.UnsupportedDraft -> item {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(state.errors["draft"] ?: "No pude abrir este borrador", color = WizChatTokens.danger)
                        TextButton(onClick = onCancel) { Text("Volver a configuraciones", color = accent) }
                    }
                }
                state.machineState == com.example.kpkn.domain.onboarding.WizChatMachineState.RecoverableError && "initialize" in state.errors -> item {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("No pude abrir tu configuración. Tu borrador sigue guardado.", color = WizChatTokens.danger)
                        TextButton(onClick = { viewModel.initialize(mode, draftId = draftId) }) { Text("Reintentar", color = accent) }
                        TextButton(onClick = onCancel) { Text("Volver", color = WizChatTokens.muted) }
                    }
                }
                state.machineState == com.example.kpkn.domain.onboarding.WizChatMachineState.RecoverableError && question != null -> item(key = "active:${question.id.name}") {
                    WizChatInlineQuestion(question, state, viewModel, accent, answerInputs,
                        onCommit = { scope.launch { if (viewModel.commit() != null && !navigatedAfterCommit) { navigatedAfterCommit = true; onDone() } } })
                }
                question != null -> item(key = "active:${question.id.name}") {
                    val transition = remember(question.id) { MutableTransitionState(reducedMotion).apply { targetState = true } }
                    AnimatedVisibility(visibleState = transition,
                        enter = if (reducedMotion) EnterTransition.None else fadeIn(tween(220)) + slideInVertically(tween(220)) { messageSlidePx },
                        exit = ExitTransition.None) {
                        WizChatInlineQuestion(question, state, viewModel, accent, answerInputs,
                            onCommit = { scope.launch { if (viewModel.commit() != null && !navigatedAfterCommit) {
                                navigatedAfterCommit = true
                                onDone()
                            } } })
                    }
                }
            }
        }
        }
        if (jumpToQuestionVisible && question != null && !state.isLoading) {
            Surface(color = WizChatTokens.option,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp),
                modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                    .imePadding().padding(bottom = 10.dp).clickable {
                        following = true
                        scope.launch {
                            val target = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                            if (reducedMotion) listState.scrollToItem(target)
                            else listState.animateScrollToItem(target)
                        }
                    }) {
                Text("Ir a la pregunta actual ↑", color = WizChatTokens.text,
                    modifier = Modifier.padding(horizontal = 17.dp, vertical = 12.dp))
            }
        }
    }
}

@Composable
private fun WizChatHeader(stage: WizChatStage, sound: Boolean, onSound: () -> Unit, onClose: () -> Unit) {
    val accent = WizChatTokens.stageAccent(stage)
    val stageIndex = when (stage) {
        WizChatStage.PROFILE -> 0
        WizChatStage.TRAINING -> 1
        WizChatStage.NUTRITION -> 2
        WizChatStage.RINGS -> 3
        WizChatStage.REVIEW -> 4
    }
    Box(Modifier.fillMaxWidth().background(WizChatTokens.background.copy(alpha = 0.94f))) {
    Column {
    Row(Modifier.widthIn(max = 620.dp).fillMaxWidth().padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Close, "Salir", tint = WizChatTokens.text) }
        Image(painterResource(R.drawable.kpknicon), contentDescription = null, colorFilter = ColorFilter.tint(WizChatTokens.text), modifier = Modifier.size(27.dp))
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text("KPKN", color = WizChatTokens.text, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            Text(WizChatCopyCatalog.progressLabel(stage), color = accent,
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
        }
        IconButton(onClick = onSound, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.GraphicEq, if (sound) "Silenciar mensajes" else "Activar sonido", tint = if (sound) accent else WizChatTokens.muted) }
    }
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)) {
        Box(Modifier.fillMaxWidth().height(3.dp).background(WizChatTokens.option, RoundedCornerShape(999.dp)))
        Box(Modifier.fillMaxWidth((stageIndex + 1) / 5f).height(3.dp).background(accent, RoundedCornerShape(999.dp)))
    }
    }
    }
}

@Composable
private fun WizChatBusyIndicator(state: SetupWizardState, accent: Color) {
    val busy = state.machineState == com.example.kpkn.domain.onboarding.WizChatMachineState.PersistingAnswer ||
        state.machineState == com.example.kpkn.domain.onboarding.WizChatMachineState.PreparingPreview ||
        state.isCommitting ||
        state.machineState == com.example.kpkn.domain.onboarding.WizChatMachineState.Committing
    if (!busy) return
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            color = accent,
            strokeWidth = 2.dp,
        )
        Text(
            when {
                state.isCommitting || state.machineState == com.example.kpkn.domain.onboarding.WizChatMachineState.Committing -> "Guardando configuración…"
                state.machineState == com.example.kpkn.domain.onboarding.WizChatMachineState.PreparingPreview -> "Preparando vista previa…"
                else -> "Guardando respuesta…"
            },
            color = WizChatTokens.muted,
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun WizChatInlineQuestion(question: WizChatQuestion, state: SetupWizardState, vm: SetupWizardViewModel, accent: Color, answerInputs: WizChatAnswerInputs, onCommit: () -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val revision = state.draft.wizChat.revision
    val multiList = answerInputs.multiList
    val multi = multiList.toSet()
    val answerAction: () -> Unit = { keyboard?.hide(); focus.clearFocus(); vm.answerAction(question.id, revision); Unit }
    val multiAction: () -> Unit = { keyboard?.hide(); focus.clearFocus(); vm.answerMulti(question.id, multiList, revision); Unit }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    Column(Modifier.widthIn(max = 620.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(color = WizChatTokens.botBubble,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp),
            modifier = Modifier.widthIn(max = 360.dp).align(Alignment.Start)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.padding(start = 10.dp).width(3.dp).height(28.dp)
                    .background(accent, RoundedCornerShape(2.dp)))
                Column(Modifier.padding(start = 9.dp, end = 15.dp, top = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(question.prompt, color = WizChatTokens.text, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                    if (question.id == WizChatQuestionId.N_START) vm.activeNutritionPlan()?.let {
                        Text("Tu plan actual: ${it.name}", color = WizChatTokens.muted,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Surface(color = WizChatTokens.replyZone,
            shape = WizChatTokens.replyShape,
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (question.id) {
                    WizChatQuestionId.P_NAME -> NameAnswer(answerInputs.pendingName, onChange = { answerInputs.pendingName = it }, onSubmit = {
                        keyboard?.hide(); focus.clearFocus(); vm.answerText(answerInputs.pendingName, revision)
                    })
                    WizChatQuestionId.P_GENDER -> ChoiceAnswer(question, multi, { answerInputs.multiList = it.toList() }, vm, accent, revision)
                    WizChatQuestionId.P_AGE -> NumberAnswer(question, state.draft.ageYears?.toDouble(), 13.0..100.0, 1.0, vm, accent, revision)
                    WizChatQuestionId.P_HEIGHT -> NumberAnswer(question, state.draft.heightCm, 100.0..250.0, 1.0, vm, accent, revision)
                    WizChatQuestionId.P_WEIGHT -> WeightAnswer(state.draft, vm, accent, revision)
                    WizChatQuestionId.T_TIME -> NumberAnswer(question, state.draft.minutesPerSession?.toDouble(), 20.0..100.0, 5.0, vm, accent, revision)
                    WizChatQuestionId.T_PLAN -> PlanAnswer(state, vm, accent, revision)
                    WizChatQuestionId.T_REVIEW -> TrainingReview(state, vm, accent)
                    WizChatQuestionId.T_MARKS -> {
                        NutritionField("Sentadilla · 1RM en kg", answerInputs.squatMark) { answerInputs.squatMark = it }
                        NutritionField("Press banca · 1RM en kg", answerInputs.benchMark) { answerInputs.benchMark = it }
                        NutritionField("Peso muerto · 1RM en kg", answerInputs.deadliftMark) { answerInputs.deadliftMark = it }
                        Text("Si no conoces una marca, déjala vacía. Nunca calcularemos kg sin una referencia.", color = WizChatTokens.muted)
                    }
                    WizChatQuestionId.T_GOAL -> if (state.draft.trainingPath == SetupTrainingPath.FROM_SCRATCH) {
                        Text("Fuerza + cardio requiere un plan que programe el cardio; puedes elegir Recomiéndame en la pregunta anterior.", color = WizChatTokens.muted)
                        ChoiceAnswer(question, multi, { answerInputs.multiList = it.toList() }, vm, accent, revision,
                            visibleOptions = question.options.filterNot { it == "Fuerza + cardio" })
                    } else ChoiceAnswer(question, multi, { answerInputs.multiList = it.toList() }, vm, accent, revision)
                    WizChatQuestionId.N_RESULT -> NutritionAnswer(state, vm, accent)
                    WizChatQuestionId.R_RESULT -> RingsAnswer(state, vm, accent)
                    WizChatQuestionId.REVIEW -> {
                        WizChatReview(state, vm.ringsPreview())
                        if (state.ringsPreviewError != null) TextButton(onClick = vm::retryRingsPreview) {
                            Text("Reintentar vista previa de RINGS", color = accent)
                        }
                        if (state.requiresActivationConfirmation) {
                            Text("Se sustituirá la configuración que tienes activa.", color = WizChatTokens.muted)
                            WizChatChoiceGroup(listOf("Confirmo el reemplazo"), if (state.draft.confirmActivation) setOf("Confirmo el reemplazo") else emptySet(), false, accent) { vm.confirmActivation(!state.draft.confirmActivation) }
                        }
                    }
                    else -> ChoiceAnswer(question, multi, { answerInputs.multiList = it.toList() }, vm, accent,
                        expectedRevision = revision,
                        extraOptions = if (question.id == WizChatQuestionId.N_START && vm.activeNutritionPlan() != null) listOf("Conservar plan actual") else emptyList(),
                        visibleOptions = if (question.id == WizChatQuestionId.R_START)
                            if (vm.hasInitialRecoveryEvidence()) question.options.takeLast(3) else question.options.take(2)
                        else null)
                }
                val error = state.errors[question.id.name] ?: state.errors["activation"] ?: state.errors["commit"] ?: state.errors["draft"]
                if (error != null) Text(error, color = WizChatTokens.danger,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    if (question.allowSkip && question.id != WizChatQuestionId.P_GENDER) TextButton(onClick = { vm.skip(question.id, revision) }) { Text(if (question.id == WizChatQuestionId.P_NAME) "Prefiero no ponerlo" else "Omitir por ahora", color = WizChatTokens.muted) }
                    val action = when (question.id) {
                        WizChatQuestionId.REVIEW -> onCommit
                        WizChatQuestionId.P_NAME -> ({ keyboard?.hide(); focus.clearFocus(); vm.answerText(answerInputs.pendingName, revision); Unit })
                        WizChatQuestionId.T_MARKS -> ({ vm.answerTrainingMarks(answerInputs.squatMark, answerInputs.benchMark, answerInputs.deadliftMark, revision); Unit })
                        else -> if (question.kind == WizChatAnswerKind.ACTION) answerAction else if (question.kind == WizChatAnswerKind.MULTI_CHOICE) multiAction else null
                    }
                    if (action != null) Button(onClick = action, enabled = !state.isCommitting && state.machineState != com.example.kpkn.domain.onboarding.WizChatMachineState.PersistingAnswer &&
                        (question.id != WizChatQuestionId.T_REVIEW || !state.isPreviewLoading && state.programPreview != null || state.draft.programRoute == SetupProgramRoute.LATER) &&
                        (question.id != WizChatQuestionId.N_RESULT || state.nutritionErrors.isEmpty() && state.nutritionPlanPreview != null) &&
                        (question.id != WizChatQuestionId.REVIEW || !state.isPreviewLoading && !state.ringsPreviewLoading && state.ringsPreviewError == null && (!state.requiresActivationConfirmation || state.draft.confirmActivation)),
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color(0xFF061725)),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)) {
                        Text(when (question.id) {
                            WizChatQuestionId.P_NAME -> "Enviar"
                            WizChatQuestionId.T_MARKS -> "Usar mis marcas"
                            WizChatQuestionId.REVIEW -> "Entrar a KPKN"
                            WizChatQuestionId.T_HOME_EQUIPMENT -> "Usar este equipo"
                            WizChatQuestionId.T_WEEKDAYS -> "Estos son mis días"
                            WizChatQuestionId.N_ELIGIBILITY -> "Confirmar condiciones"
                            WizChatQuestionId.R_DISCOMFORT -> "Son estas molestias"
                            WizChatQuestionId.T_REVIEW -> if (state.draft.includeNutrition) "Seguir con alimentación" else if (state.mode == SetupWizardMode.TRAINING_ONLY) "Revisar configuración" else "Revisar RINGS"
                            WizChatQuestionId.N_RESULT -> if (state.mode == SetupWizardMode.NUTRITION_ONLY) "Revisar configuración" else "Seguir con RINGS"
                            WizChatQuestionId.R_RESULT -> "Revisar configuración"
                            else -> "Continuar"
                        })
                    }
                }
            }
        }
    }
    }
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
        Text("Músculos: cómo llegan tus grupos musculares · Energía: tu batería general · Columna: la carga que arrastra tu espalda.", color = WizChatTokens.muted)
        when (mapping.completion) {
            com.example.kpkn.domain.onboarding.RingsCompletion.VALID -> {
                when {
                    state.ringsBatteriesPreview != null && !state.ringsPreviewLoading -> {
                        val batteries = state.ringsBatteriesPreview
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            listOf(Triple("Músculos", batteries.muscular, RingColors[0]),
                                Triple("Energía", batteries.cnc, RingColors[1]),
                                Triple("Columna", batteries.spinal, RingColors[2])).forEach { (label, score, color) ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(Modifier.size(74.dp), contentAlignment = Alignment.Center) {
                                        Canvas(Modifier.fillMaxSize().padding(5.dp)) {
                                            drawArc(color.copy(alpha = .22f), -90f, 360f, false, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
                                            drawArc(color, -90f, 360f * score.coerceIn(0, 100) / 100f, false, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
                                        }
                                        Text("$score", color = WizChatTokens.text, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                    }
                                    Text(label, color = WizChatTokens.muted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        Text("${batteries.sourceLabel ?: "Estimación inicial"} · Aproximación calculada con tus datos disponibles, no una medición ni un diagnóstico.", color = WizChatTokens.muted)
                    }
                    state.ringsPreviewLoading -> Text("Calculando con el mismo motor que usa Inicio…", color = WizChatTokens.muted)
                    else -> {
                        Text(state.ringsPreviewError ?: "Preparando tu punto de partida…", color = WizChatTokens.muted)
                        if (state.ringsPreviewError != null) TextButton(onClick = vm::retryRingsPreview) {
                            Text("Reintentar", color = accent)
                        }
                    }
                }
            }
            com.example.kpkn.domain.onboarding.RingsCompletion.UNKNOWN -> Text("Sin calibrar: no hay evidencia suficiente para fabricar una carga histórica. Tus anillos irán estimando con lo que registres al entrenar y con tus check-ins; no inventamos sesiones.", color = WizChatTokens.muted)
            com.example.kpkn.domain.onboarding.RingsCompletion.PRESERVE -> Text("Conservaré la estimación existente sin rejuvenecer su fecha: sigue reflejando cuándo se calculó.", color = WizChatTokens.muted)
            com.example.kpkn.domain.onboarding.RingsCompletion.OMITTED -> Text("Quitaré la estimación inicial, sin borrar un check-in real.", color = WizChatTokens.muted)
            com.example.kpkn.domain.onboarding.RingsCompletion.INCOMPLETE -> {
                Text("Faltan datos para preparar la estimación inicial.", color = WizChatTokens.danger)
                TextButton(onClick = { vm.edit(WizChatQuestionId.R_START) }) { Text("Dejar sin calibrar", color = accent) }
            }
        }
        WizChatAdvancedHost(
            open = "rings" in state.draft.wizChat.advancedBranches,
            onOpen = vm::openRingsAdvanced,
        ) {
            Text("Los ajustes manuales solo se aplicarán al activar esta configuración.", color = WizChatTokens.muted)
            Text("¿Qué músculos quieres que queden disponibles para ajustar?", color = WizChatTokens.text)
            Text("Cambiar el alcance restablece los ajustes de músculos que queden fuera de la selección.", color = WizChatTokens.muted)
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
private fun NameAnswer(value: String, onChange: (String) -> Unit, onSubmit: () -> Unit) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(15.dp))
            .background(WizChatTokens.option).padding(horizontal = 14.dp, vertical = 15.dp),
        textStyle = androidx.compose.ui.text.TextStyle(color = WizChatTokens.text, fontSize = 16.sp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        decorationBox = { field -> Box { if (value.isBlank()) Text("Escribe tu nombre", color = WizChatTokens.muted); field() } },
    )
}

@Composable
private fun NumberAnswer(question: WizChatQuestion, selected: Double?, range: ClosedFloatingPointRange<Double>, step: Double, vm: SetupWizardViewModel, accent: Color, revision: Int) {
    val values = remember(range, step) { generateSequence(range.start) { next -> (next + step).takeIf { it <= range.endInclusive + 0.0001 } }.toList() }
    WizChatNumberWheel(values, selected, question.unit.orEmpty(), accent, onSettled = { vm.answerNumber(question.id, it, revision) })
}

@Composable
private fun WeightAnswer(draft: SetupWizardDraft, vm: SetupWizardViewModel, accent: Color, revision: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("kg", "lb").forEach { unit ->
            Surface(color = if (draft.weightUnit == unit) WizChatTokens.optionSelected else WizChatTokens.option,
                shape = WizChatTokens.optionShape, modifier = Modifier.heightIn(min = 48.dp).clickable { vm.setWeightUnit(unit) }) {
                Text(unit, color = WizChatTokens.text, modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp))
            }
        }
    }
    val importedWeight = draft.importedWeightKg?.takeIf { it == draft.weightKg }
    if (importedWeight != null) {
        val shown = WizChatWeightScale.toDisplay(importedWeight, draft.weightUnit)
        TextButton(onClick = { vm.acceptImportedWeight(importedWeight, revision) }) {
            Text("De tu perfil · Mantener ${WizChatWeightScale.format(WizChatWeightScale.snap(shown))} ${draft.weightUnit}", color = accent)
        }
    }
    WizChatWeightRule(
        unit = draft.weightUnit,
        selected = draft.weightKg?.let { WizChatWeightScale.toDisplay(it, draft.weightUnit) },
        accent = accent,
        onConfirm = { vm.answerWeight(it, draft.weightUnit, revision) },
    )
}

@Composable
private fun ChoiceAnswer(question: WizChatQuestion, selected: Set<String>, setSelected: (Set<String>) -> Unit, vm: SetupWizardViewModel, accent: Color, expectedRevision: Int, extraOptions: List<String> = emptyList(), visibleOptions: List<String>? = null) {
    val multi = question.kind == WizChatAnswerKind.MULTI_CHOICE
    val compact = question.id in setOf(WizChatQuestionId.P_GENDER, WizChatQuestionId.T_DAYS,
        WizChatQuestionId.N_SEX, WizChatQuestionId.R_RECENT, WizChatQuestionId.R_SESSIONS,
        WizChatQuestionId.R_RECENCY, WizChatQuestionId.R_ACTIVITY, WizChatQuestionId.R_INTENSITY,
        WizChatQuestionId.R_AXIAL)
    var search by remember(question.id) { mutableStateOf("") }
    if (question.id == WizChatQuestionId.R_DISCOMFORT) {
        BasicTextField(search, { search = it }, Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            .background(WizChatTokens.option).padding(13.dp), singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = WizChatTokens.text, fontSize = 16.sp),
            decorationBox = { field -> Box { if (search.isBlank()) Text("Buscar molestia", color = WizChatTokens.muted); field() } })
    }
    if (question.id == WizChatQuestionId.N_ACTIVITY) Text(
        "Tranquilo: sentado · Algo activo: caminas · Activo: te mueves gran parte del día · Muy activo: trabajo físico. No sumes tus entrenamientos otra vez.",
        color = WizChatTokens.muted, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
    val visible = ((visibleOptions ?: question.options) + extraOptions).filter { option -> search.isBlank() || option in setOf("Sin molestias", "Prefiero omitirlo") || option.contains(search, ignoreCase = true) }
    WizChatChoiceGroup(visible, selected, multi, accent, compact = compact) { option ->
        if (!multi) vm.answerChoice(question.id, option, expectedRevision)
        else {
            val exclusive = when (question.id) {
                WizChatQuestionId.T_HOME_EQUIPMENT -> setOf("Sin material")
                WizChatQuestionId.N_ELIGIBILITY -> setOf("Ninguna de estas", "No lo sé / prefiero no responder")
                WizChatQuestionId.R_DISCOMFORT -> setOf("Sin molestias", "Prefiero omitirlo")
                else -> emptySet()
            }
            if (option in exclusive) vm.answerMulti(question.id, listOf(option), expectedRevision)
            else setSelected(if (option in selected) selected - option else selected - exclusive + option)
        }
    }
}

@Composable
private fun PlanAnswer(state: SetupWizardState, vm: SetupWizardViewModel, accent: Color, revision: Int) {
    if (state.draft.trainingPath == SetupTrainingPath.FROM_SCRATCH) {
        val days = state.draft.selectedWeekdays.sorted()
        var editingDay by remember(days) { mutableIntStateOf(days.firstOrNull() ?: 1) }
        var search by remember { mutableStateOf("") }
        var expandedExerciseId by remember { mutableStateOf<String?>(null) }
        val weekdayNames = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Crea una sesión por cada día. No se activará hasta que todas tengan ejercicios.", color = WizChatTokens.muted)
            WizChatChoiceGroup(days.map { weekdayNames[it - 1] }, setOf(weekdayNames[editingDay - 1]), false, accent) { label -> editingDay = weekdayNames.indexOf(label) + 1 }
            val session = state.draft.sessions.firstOrNull { it.weekday == editingDay }
            var sessionTitle by remember(editingDay) { mutableStateOf(session?.title.orEmpty()) }
            BasicTextField(sessionTitle, { sessionTitle = it },
                Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(13.dp))
                    .background(WizChatTokens.option).padding(12.dp),
                singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { vm.renameSession(editingDay, sessionTitle) }),
                textStyle = androidx.compose.ui.text.TextStyle(color = WizChatTokens.text, fontSize = 16.sp),
                decorationBox = { field -> Box { if (sessionTitle.isBlank()) Text("Nombre de la sesión · ${weekdayNames[editingDay - 1]}", color = WizChatTokens.muted); field() } })
            if (sessionTitle.isNotBlank() && sessionTitle != session?.title) TextButton(onClick = { vm.renameSession(editingDay, sessionTitle) }) {
                Text("Guardar nombre", color = accent)
            }
            session?.exercises?.forEach { exercise ->
                Surface(color = WizChatTokens.option, shape = WizChatTokens.optionShape) {
                    Column(Modifier.padding(10.dp)) {
                        Text(exercise.name, color = WizChatTokens.text)
                        exercise.info?.equipment?.let { Text("Equipo indicado: $it", color = WizChatTokens.muted) }
                        Row {
                            TextButton(onClick = { vm.moveExercise(editingDay, exercise.id, -1) }) { Text("↑", color = accent) }
                            TextButton(onClick = { vm.moveExercise(editingDay, exercise.id, 1) }) { Text("↓", color = accent) }
                            TextButton(onClick = { vm.removeExercise(editingDay, exercise.id) }) { Text("Quitar", color = WizChatTokens.muted) }
                        }
                        TextButton(onClick = { expandedExerciseId = if (expandedExerciseId == exercise.id) null else exercise.id }) {
                            Text(if (expandedExerciseId == exercise.id) "Ocultar prescripción" else "Editar series y repeticiones", color = accent)
                        }
                        if (expandedExerciseId == exercise.id) {
                            Text("Series", color = WizChatTokens.muted)
                            WizChatChoiceGroup((1..5).map(Int::toString), setOf(exercise.sets?.toString().orEmpty()),
                                false, accent, compact = true) { value -> vm.changeSets(editingDay, exercise.id, value.toInt()) }
                            Text("Repeticiones por serie", color = WizChatTokens.muted)
                            WizChatChoiceGroup(listOf("6", "8", "10", "12", "15"),
                                setOf(exercise.reps?.toString().orEmpty()), false, accent, compact = true) { value ->
                                vm.changeReps(editingDay, exercise.id, value)
                            }
                            var customReps by remember(exercise.id) { mutableStateOf("") }
                            BasicTextField(customReps, { customReps = it.filter(Char::isDigit).take(2) },
                                Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(13.dp))
                                    .background(WizChatTokens.botBubble).padding(12.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    customReps.toIntOrNull()?.takeIf { it in 1..30 }?.let { vm.changeReps(editingDay, exercise.id, it.toString()) }
                                }),
                                textStyle = androidx.compose.ui.text.TextStyle(color = WizChatTokens.text, fontSize = 16.sp),
                                decorationBox = { field -> Box { if (customReps.isBlank()) Text("Otro número (1–30)", color = WizChatTokens.muted); field() } })
                            TextButton(onClick = { customReps.toIntOrNull()?.takeIf { it in 1..30 }?.let { vm.changeReps(editingDay, exercise.id, it.toString()) } },
                                enabled = customReps.toIntOrNull() in 1..30) { Text("Usar repeticiones", color = accent) }
                        }
                    }
                }
            }
            BasicTextField(value = search, onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(15.dp))
                    .background(WizChatTokens.option).padding(14.dp),
                singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(color = WizChatTokens.text, fontSize = 16.sp),
                decorationBox = { field -> Box { if (search.isBlank()) Text("Buscar ejercicio para ${weekdayNames[editingDay - 1]}", color = WizChatTokens.muted); field() } })
            LaunchedEffect(search, editingDay) { vm.searchExercises(search) }
            if (search.isNotBlank()) {
                val matches = state.exerciseSuggestions
                if (state.isExerciseSearching) Text("Buscando ejercicios…", color = WizChatTokens.muted)
                else if (state.exerciseSearchError != null) {
                    Text(state.exerciseSearchError, color = WizChatTokens.danger)
                    TextButton(onClick = { vm.searchExercises(search) }) { Text("Reintentar búsqueda", color = accent) }
                } else if (matches.isEmpty()) Text("No encontré ejercicios con ese nombre.", color = WizChatTokens.muted)
                matches.forEach { exercise ->
                    Surface(color = WizChatTokens.option, shape = WizChatTokens.optionShape,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { vm.addExercise(editingDay, exercise); search = "" }) {
                        Column(Modifier.padding(12.dp)) {
                            Text(exercise.name, color = WizChatTokens.text)
                            exercise.equipment?.let { Text("Equipo indicado: $it", color = WizChatTokens.muted) }
                        }
                    }
                }
            }
            val complete = days.isNotEmpty() && days.all { day -> state.draft.sessions.any { it.weekday == day && it.exercises.isNotEmpty() } }
            Button(onClick = { vm.selectPlan("from-scratch", revision) }, enabled = complete && !state.isPreviewLoading,
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color(0xFF061725))) {
                Text("Usar mis sesiones")
            }
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        WizChatPlanCarousel(state.planCandidates, accent) { id -> vm.selectPlan(id, revision) }
        if (state.availablePlanCandidates.size > state.planCandidates.size) {
            TextButton(onClick = vm::showMoreCandidates) { Text("Ver otras opciones", color = accent) }
        }
    if (state.planCandidates.isEmpty()) {
        if (state.previewError != null) Text(state.previewError, color = WizChatTokens.danger)
            Text(if (state.isCandidateLoading) "Comprobando planes ejecutables…" else "No hay planes compatibles a la vez con tu objetivo, tu equipo, tus días y tu tiempo.", color = WizChatTokens.muted)
            Text("Puedes ajustar esas condiciones o cambiar de objetivo; no te enseñaré como compatible un plan de otra disciplina.", color = WizChatTokens.muted)
            TextButton(onClick = { vm.edit(WizChatQuestionId.T_GOAL) }) { Text("Cambiar objetivo", color = accent) }
            TextButton(onClick = { vm.edit(WizChatQuestionId.T_EQUIPMENT) }) { Text("Cambiar equipo", color = accent) }
            TextButton(onClick = { vm.edit(WizChatQuestionId.T_DAYS) }) { Text("Cambiar días", color = accent) }
            TextButton(onClick = { vm.edit(WizChatQuestionId.T_TIME) }) { Text("Cambiar tiempo por sesión", color = accent) }
            TextButton(onClick = { vm.edit(WizChatQuestionId.T_ROUTE) }) { Text("Elegir otra forma de empezar", color = accent) }
        }
        WizChatAdvancedHost(
            open = "training" in state.draft.wizChat.advancedBranches,
            onOpen = vm::openAdvanced,
        ) {
            Text("Enfoque del entrenamiento", color = WizChatTokens.text)
            WizChatChoiceGroup(SetupFocus.entries.map { it.label }, setOf(state.draft.focus.label), false, accent) { selected ->
                vm.update { it.copy(focus = SetupFocus.entries.first { focus -> focus.label == selected }) }
            }
            val muscles = state.draft.volumeRecommendations.map { it.muscleGroup }.distinct().take(12)
            if (muscles.isNotEmpty()) {
                var priorityLimitReached by remember { mutableStateOf(false) }
                Text("Priorizar · hasta 3 grupos", color = WizChatTokens.text)
                WizChatChoiceGroup(muscles, state.draft.priorityMuscles, true, accent) { muscle ->
                    if (muscle !in state.draft.priorityMuscles && state.draft.priorityMuscles.size >= 3) priorityLimitReached = true
                    else {
                        priorityLimitReached = false
                        vm.setPriorityMuscles(if (muscle in state.draft.priorityMuscles) state.draft.priorityMuscles - muscle else state.draft.priorityMuscles + muscle)
                    }
                }
                if (priorityLimitReached) Text("Elige como máximo tres prioridades.", color = WizChatTokens.danger)
                Text("Menor énfasis", color = WizChatTokens.text)
                WizChatChoiceGroup(muscles.filterNot { it in state.draft.priorityMuscles }, state.draft.lowerEmphasisMuscles, true, accent) { muscle ->
                    vm.setLowerEmphasisMuscles(if (muscle in state.draft.lowerEmphasisMuscles) state.draft.lowerEmphasisMuscles - muscle else state.draft.lowerEmphasisMuscles + muscle)
                }
            }
        }
    }
}

@Composable
private fun TrainingReview(state: SetupWizardState, vm: SetupWizardViewModel, accent: Color) {
    val draft = state.draft
    if (!draft.includeTraining || draft.programRoute == SetupProgramRoute.LATER) {
        Text("Tu perfil de volumen queda guardado. Podrás crear un programa después.", color = WizChatTokens.muted)
        TextButton(onClick = { vm.edit(WizChatQuestionId.T_ROUTE) }) { Text("Elegir un programa ahora", color = accent) }
        return
    }
    val preview = state.programPreview
    if (state.isPreviewLoading) Text("Actualizando esta propuesta…", color = WizChatTokens.muted)
    if (preview == null) {
        Text(state.previewError ?: "Preparando un programa que puedas ejecutar…", color = if (state.previewError != null) WizChatTokens.danger else WizChatTokens.muted)
        TextButton(onClick = { vm.edit(WizChatQuestionId.T_PLAN) }) { Text("Cambiar elección", color = accent) }
        return
    }
    val weekdays = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")
    Text(preview.name, color = WizChatTokens.text, fontSize = 19.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
    Text("${draft.daysPerWeek ?: 0} días · ${draft.selectedWeekdays.sorted().joinToString(" / ") { weekdays[it - 1] }} · ${draft.minutesPerSession ?: "—"} min disponibles",
        color = WizChatTokens.muted)
    Text("Equipo: ${draft.equipment.joinToString { it.label }}", color = WizChatTokens.muted)
    if (draft.goal == SetupGoal.MIXED) Text("Cardio elegido: ${draft.cardioType?.name ?: "pendiente"} · ${draft.cardioMinutes ?: "—"} min", color = WizChatTokens.muted)
    val realDays = state.fixedTrainingDays
    val estimated = state.fixedSessionEstimateMinutes
    if (realDays != null || estimated != null) {
        val shownDays = realDays.orEmpty().sorted().joinToString(" / ") { weekdays[it - 1] }.ifBlank { "Rotación propia de la receta" }
        Text("Receta fija · días reales: $shownDays${estimated?.let { " · hasta ~$it min por sesión" }.orEmpty()}", color = WizChatTokens.muted)
        if (estimated != null && estimated > (draft.minutesPerSession ?: 100)) Text("Esta receta supera tu tiempo por sesión. Elige otra opción.", color = WizChatTokens.danger)
        else if (realDays != draft.selectedWeekdays || estimated != null && estimated > (draft.minutesPerSession ?: 100)) {
            Text("Su calendario o duración difiere de tu disponibilidad; no cambiaremos la receta para hacerla encajar.", color = WizChatTokens.muted)
            WizChatChoiceGroup(listOf("Acepto esta rotación y duración"),
                if (draft.acceptFixedRecipeDifference) setOf("Acepto esta rotación y duración") else emptySet(),
                false, accent) { vm.acceptFixedRecipeDifference(!draft.acceptFixedRecipeDifference) }
        }
    }
    var showSessions by remember(preview.id) { mutableStateOf(false) }
    TextButton(onClick = { showSessions = !showSessions }) { Text(if (showSessions) "Ocultar sesiones" else "Ver sesiones", color = accent) }
    if (showSessions) preview.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }
        .firstOrNull()?.sessions?.forEach { session ->
            Text("${session.name} · ${session.exercises.size} ejercicios", color = WizChatTokens.text)
        }
    TextButton(onClick = { vm.edit(WizChatQuestionId.T_PLAN) }) { Text("Cambiar plan", color = accent) }
}

@Composable
private fun NutritionAnswer(state: SetupWizardState, vm: SetupWizardViewModel, accent: Color) {
    val draft = state.draft.nutritionDraft ?: return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.nutritionPlanPreview?.let { plan ->
            Text("${plan.calorieTarget} kcal", color = accent, fontSize = 29.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            Text("Proteínas ${plan.proteinGoal} g  ·  Carbohidratos ${plan.carbGoal} g  ·  Grasas ${plan.fatGoal} g", color = WizChatTokens.text)
            state.nutritionPacePercentPerWeek?.let { pace ->
                Text("Ritmo inicial propuesto: ~${java.text.DecimalFormat("0.##").format(pace)} % del peso por semana", color = WizChatTokens.muted)
            }
            Text("Referencia inicial, ajustable cuando quieras.", color = WizChatTokens.muted)
        }
        if (draft.mode != "professional") {
            WizChatAdvancedHost(open = "nutrition" in state.draft.wizChat.advancedBranches, onOpen = vm::openNutritionAdvanced) {
                Text("Objetivo corporal opcional", color = WizChatTokens.text)
                if (draft.direction == com.example.kpkn.data.models.PlanDirection.DEFICIT || draft.direction == com.example.kpkn.data.models.PlanDirection.SURPLUS) {
                    Text("Ritmo inicial", color = WizChatTokens.muted)
                    val paceLabels = listOf("Lento", "Medio", "Rápido")
                    WizChatChoiceGroup(paceLabels, setOf(when (draft.pacePreset) {
                        com.example.kpkn.domain.nutrition.WizardPacePreset.SLOW -> "Lento"
                        com.example.kpkn.domain.nutrition.WizardPacePreset.MEDIUM -> "Medio"
                        com.example.kpkn.domain.nutrition.WizardPacePreset.FAST -> "Rápido"
                    }), false, accent, compact = true) { label -> vm.updateNutritionInput { it.copy(pacePreset = when (label) {
                        "Lento" -> com.example.kpkn.domain.nutrition.WizardPacePreset.SLOW
                        "Rápido" -> com.example.kpkn.domain.nutrition.WizardPacePreset.FAST
                        else -> com.example.kpkn.domain.nutrition.WizardPacePreset.MEDIUM
                    }) } }
                }
                WizChatChoiceGroup(listOf("Peso", "Grasa corporal", "Masa muscular"), setOf(when (draft.goalMetric) { com.example.kpkn.data.models.GoalMetric.WEIGHT -> "Peso"; com.example.kpkn.data.models.GoalMetric.BODY_FAT -> "Grasa corporal"; com.example.kpkn.data.models.GoalMetric.MUSCLE_MASS -> "Masa muscular" }), false, accent) { value ->
                    vm.updateNutritionInput { old ->
                        val metric = when (value) { "Grasa corporal" -> com.example.kpkn.data.models.GoalMetric.BODY_FAT; "Masa muscular" -> com.example.kpkn.data.models.GoalMetric.MUSCLE_MASS; else -> com.example.kpkn.data.models.GoalMetric.WEIGHT }
                        old.copy(goalMetric = metric, targetValueText = when (metric) {
                            com.example.kpkn.data.models.GoalMetric.WEIGHT -> old.targetWeightText
                            com.example.kpkn.data.models.GoalMetric.BODY_FAT -> old.targetBodyFatText
                            com.example.kpkn.data.models.GoalMetric.MUSCLE_MASS -> old.targetMuscleText
                        })
                    }
                }
                if (draft.goalMetric == com.example.kpkn.data.models.GoalMetric.BODY_FAT) {
                    WizChatPhysiquePicker(draft.equationSex) { target ->
                        vm.updateNutritionInput { it.copy(targetBodyFatText = target.toString(), targetValueText = target.toString()) }
                    }
                }
                val goalValue = when (draft.goalMetric) {
                    com.example.kpkn.data.models.GoalMetric.WEIGHT -> draft.targetWeightText
                    com.example.kpkn.data.models.GoalMetric.BODY_FAT -> draft.targetBodyFatText
                    com.example.kpkn.data.models.GoalMetric.MUSCLE_MASS -> draft.targetMuscleText
                }
                NutritionField(if (draft.goalMetric == com.example.kpkn.data.models.GoalMetric.WEIGHT) "Meta de peso (${state.draft.weightUnit})" else "Meta de composición (%)", goalValue) { value -> vm.updateNutritionInput {
                    when (it.goalMetric) {
                        com.example.kpkn.data.models.GoalMetric.WEIGHT -> it.copy(targetValueText = value, targetWeightText = value)
                        com.example.kpkn.data.models.GoalMetric.BODY_FAT -> it.copy(targetValueText = value, targetBodyFatText = value)
                        com.example.kpkn.data.models.GoalMetric.MUSCLE_MASS -> it.copy(targetValueText = value, targetMuscleText = value)
                    }
                } }
            }
        }
        if (draft.mode == "professional") {
            NutritionField("Calorías profesionales", draft.manualCalorieTargetText) { value -> vm.updateNutritionInput { it.copy(manualCalorieTargetText = value) } }
            NutritionField("Proteína (g)", draft.manualProteinText) { value -> vm.updateNutritionInput { it.copy(manualProteinText = value) } }
            NutritionField("Carbohidratos (g)", draft.manualCarbsText) { value -> vm.updateNutritionInput { it.copy(manualCarbsText = value) } }
            NutritionField("Grasas (g)", draft.manualFatText) { value -> vm.updateNutritionInput { it.copy(manualFatText = value) } }
        }
        state.nutritionErrors.values.forEach { Text(it, color = WizChatTokens.danger) }
        listOf("age" to (WizChatQuestionId.P_AGE to "Completar edad"),
            "height" to (WizChatQuestionId.P_HEIGHT to "Completar estatura"),
            "weight" to (WizChatQuestionId.P_WEIGHT to "Completar peso"),
            "equationSex" to (WizChatQuestionId.N_SEX to "Revisar dato de la ecuación")
        ).forEach { (errorKey, target) ->
            if (errorKey in state.nutritionErrors) TextButton(onClick = { vm.edit(target.first) }) {
                Text(target.second, color = accent)
            }
        }
        if (state.nutritionErrors.isNotEmpty()) TextButton(onClick = { vm.edit(WizChatQuestionId.N_START) }) {
            Text("Configurar después", color = accent)
        }
        if (state.nutritionErrors.isNotEmpty() && draft.mode == "professional") {
            TextButton(onClick = { vm.skip(WizChatQuestionId.N_RESULT, state.draft.wizChat.revision) }) {
                Text("Guardar indicaciones y continuar", color = accent)
            }
        }
    }
}

@Composable
private fun NutritionField(label: String, value: String, onChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, color = WizChatTokens.muted)
        BasicTextField(value, onChange, Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(12.dp), textStyle = androidx.compose.ui.text.TextStyle(color = WizChatTokens.text), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next))
    }
}

/**
 * Answer inputs live at screen level so hiding and re-showing the controls
 * (typing sequence, save errors) never loses the selection or typed text.
 */
class WizChatAnswerInputs(
    name: String = "",
    squat: String = "",
    bench: String = "",
    deadlift: String = "",
) {
    var multiList by mutableStateOf(emptyList<String>())
    var pendingName by mutableStateOf(name)
    var squatMark by mutableStateOf(squat)
    var benchMark by mutableStateOf(bench)
    var deadliftMark by mutableStateOf(deadlift)
}

private val WizChatAnswerInputsSaver = mapSaver(
    save = {
        mapOf(
            "multi" to it.multiList,
            "name" to it.pendingName,
            "sq" to it.squatMark,
            "bn" to it.benchMark,
            "dl" to it.deadliftMark,
        )
    },
    restore = { map ->
        WizChatAnswerInputs(
            name = map["name"] as? String ?: "",
            squat = map["sq"] as? String ?: "",
            bench = map["bn"] as? String ?: "",
            deadlift = map["dl"] as? String ?: "",
        ).apply { multiList = (map["multi"] as? List<*>)?.filterIsInstance<String>().orEmpty() }
    },
)
