package com.example.kpkn.screens.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import com.example.kpkn.domain.onboarding.SetupStepDefinitions
import com.example.kpkn.screens.onboarding.design.WizardGlassHeader
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.domain.onboarding.SetupStepGraph
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.WizChatMachineState
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardShapes
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.WizardTypography
import kotlinx.coroutines.launch

/**
 * Host del wizard tradicional que sustituye a WizChat.
 *
 * Una pantalla por paso, sin conversación. Conserva intactas las piezas que el
 * plan manda preservar: inicialización del borrador, **atrás sin pérdida** (nunca
 * descarta), diálogos de salida diferenciados y activación conjunta al final.
 *
 * El paso actual es autoridad del estado ([SetupWizardState.currentStep]); la
 * flecha atrás de la cabecera y el botón de sistema vuelven un paso cuando hay
 * historial y abren la salida explícita en el primer paso. La navegación por
 * salida/descarte solo avanza cuando la operación de persistencia devuelve true.
 */
@Composable
fun SetupWizardScreen(
    mode: SetupWizardMode,
    draftId: String? = null,
    /** Plan de la biblioteca que la persona eligió («Configurar este plan», E-18); null = ninguno. */
    preselectedPlanId: String? = null,
    /** Abre un concepto de «Conceptos clave» desde las hojas «Cómo funciona»; null = sin enlace (sin navegación). */
    onOpenConcept: ((String) -> Unit)? = null,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    viewModel: SetupWizardViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var navigatedAfterCommit by remember { mutableStateOf(false) }

    // La clave incluye el ViewModel: si las tests recrean el VM, la
    // inicialización se vuelve a ejecutar en lugar de quedar en un no-op.
    LaunchedEffect(mode, draftId, viewModel) {
        viewModel.initialize(mode, draftId = draftId, preselectedPlanId = preselectedPlanId)
    }

    // Atrás nunca descarta ni borra respuestas: retrocede un paso cuando existe
    // historial y abre la salida explícita solo en el primer paso.
    fun leaveNow() {
        scope.launch {
            viewModel.leaveImmediately()
            onCancel()
        }
    }

    BackHandler {
        when {
            state.isSavingAndExiting -> Unit
            state.dialog == SetupWizardDialog.DISCARD -> viewModel.keepConfiguring()
            viewModel.canGoBack() -> viewModel.goBack()
            else -> leaveNow()
        }
    }

    if (state.dialog == SetupWizardDialog.DISCARD) {
        SetupWizardExitDialogs(state = state, viewModel = viewModel, onLeftWizard = onCancel)
    }

    val step: SetupStepId = state.currentStep
    val renderedStep = if (step == SetupStepId.AGE) SetupStepId.NAME else step

    CompositionLocalProvider(LocalOpenConcept provides onOpenConcept) {
        when (state.machineState) {
            WizChatMachineState.Loading -> Box(
                Modifier
                    .fillMaxSize()
                    .background(WizardColors.background),
            )

            WizChatMachineState.UnsupportedDraft -> WizardStatusScreen(
                title = "No pude abrir tu configuración",
                body = state.errors["draft"] ?: "Esta configuración no se puede continuar. Puedes volver y empezar de nuevo.",
                secondaryLabel = "Volver",
                onSecondary = onCancel,
            )

            WizChatMachineState.RecoverableError -> WizardStatusScreen(
                title = "No pude abrir tu configuración",
                // Mensaje honesto del fallo real, no un texto genérico.
                body = state.lastFailure
                    ?: state.errors["initialize"]
                    ?: "Lo que llevas sigue guardado. Puedes reintentarlo.",
                secondaryLabel = "Reintentar",
                onSecondary = { viewModel.retryFailedOperation(SetupRetryOperation.LOAD) },
                tertiaryLabel = "Volver",
                onTertiary = onCancel,
            )

            else -> Box(modifier = Modifier.fillMaxSize().background(WizardColors.background)) {
                val pages = wizardPresentationSteps(SetupStepGraph.stepIds(state.draft.stepContext()))
                val currentIndex = pages.indexOf(renderedStep).coerceAtLeast(0)
                val visible = pages.take(currentIndex + 2)
                val scroll = rememberScrollState()
                val anchors = remember { mutableStateMapOf<SetupStepId, Int>() }
                val haze = remember { HazeState() }
                val headerTitle = when (renderedStep) {
                    SetupStepId.HEIGHT -> "¿Cuánto mides y pesas?"
                    else -> SetupStepDefinitions.of(renderedStep)?.title
                        ?: SetupStepGraph.blockOf(renderedStep).headerTitle()
                }
                val ageYears = state.draft.ageYears
                val aliasReady = renderedStep != SetupStepId.NAME ||
                    (state.draft.name.isNotBlank() && ageYears != null && ageYears in 13..100)
                val measuresReady = renderedStep != SetupStepId.HEIGHT ||
                    (state.draft.heightCm != null && state.draft.weightKg != null)
                val checkEnabled = state.canConfirmStep && aliasReady && measuresReady && !state.isSubmittingAnswer
                val progress = if (pages.size <= 1) 1f else (currentIndex + 1f) / pages.size
                val headerPx = with(LocalDensity.current) { 76.dp.toPx() }.toInt()
                LaunchedEffect(renderedStep) {
                    val y = snapshotFlow { anchors[renderedStep] }.filterNotNull().first()
                    scroll.animateScrollTo((y - headerPx).coerceAtLeast(0).coerceAtMost(scroll.maxValue))
                }
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(scroll)
                        .hazeSource(state = haze)
                        .statusBarsPadding()
                        .padding(top = 64.dp, bottom = 120.dp),
                ) {
                    visible.forEach { page ->
                        val peek = pages.indexOf(page) == currentIndex + 1
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { anchors[page] = it.positionInParent().y.roundToInt() }
                                .then(if (peek && android.os.Build.VERSION.SDK_INT >= 31) Modifier.blur(16.dp) else Modifier)
                                .padding(bottom = if (peek) 12.dp else 28.dp),
                        ) {
                            SetupStepScreen(
                                step = page,
                                state = state,
                                vm = viewModel,
                                showCta = false,
                                embedded = true,
                                onBack = { if (viewModel.canGoBack()) viewModel.goBack() else leaveNow() },
                                onExit = { leaveNow() },
                                ctaLabel = if (step == SetupStepId.REVIEW_ACTIVATE) "Activar y entrar a KPKN" else "Continuar",
                                ctaEnabled = checkEnabled,
                                onCta = {},
                            )
                        }
                    }
                }
                WizardGlassHeader(
                    haze = haze,
                    title = headerTitle,
                    onBack = { if (viewModel.canGoBack()) viewModel.goBack() else leaveNow() },
                    onExit = { leaveNow() },
                    exitLabel = "Salir",
                    modifier = Modifier.align(Alignment.TopCenter).zIndex(2f).statusBarsPadding(),
                )
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 8.dp, top = 92.dp, bottom = 112.dp)
                        .width(5.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(99.dp))
                        .background(WizardColors.progressTrack.copy(alpha = 0.35f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(progress.coerceIn(0.06f, 1f))
                            .clip(RoundedCornerShape(99.dp))
                            .align(Alignment.TopCenter)
                            .background(WizardColors.progressFill.copy(alpha = 0.7f)),
                    )
                }
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .zIndex(2f)
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp)
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(if (checkEnabled) WizardColors.cta else WizardColors.ctaDisabled)
                        .clickable(enabled = checkEnabled, role = Role.Button, onClick = {
                            when {
                                step == SetupStepId.REVIEW_ACTIVATE -> scope.launch {
                                    if (viewModel.commit() != null && !navigatedAfterCommit) {
                                        navigatedAfterCommit = true
                                        onDone()
                                    }
                                }
                                renderedStep == SetupStepId.NAME -> viewModel.submitAliasAgePair()
                                renderedStep == SetupStepId.HEIGHT -> viewModel.submitAnthropometryPair()
                                else -> viewModel.submitCurrentStep(step)
                            }
                        })
                        .testTag("setup-continue")
                        .semantics { contentDescription = if (step == SetupStepId.REVIEW_ACTIVATE) "Activar y entrar a KPKN" else "Continuar" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = if (checkEnabled) WizardColors.ctaContent else WizardColors.ctaDisabledContent,
                    )
                }
                // El aviso flota sobre el paso: no empuja la cabecera, la pregunta
                // ni el botón. Sigue siendo descartable y reintenta la misma operación.
                // H6: lo que el paso ya pinta por sí mismo (la lista de planes y el preview) no se repite aquí.
                val floatingErrors = state.errors.filterKeys { key -> !stepRendersError(step, key) }
                if (floatingErrors.isNotEmpty()) {
                    WizardInlineErrors(
                        errors = floatingErrors,
                        onDismiss = viewModel::clearError,
                        retryFor = viewModel::retryOperationForError,
                        onRetry = { operation -> viewModel.retryFailedOperation(operation) },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 88.dp),
                    )
                }
            }
        }
    }
}

/**
 * H6: ¿el paso [step] ya pinta por sí mismo el error de clave [key] de `SetupWizardState.errors`? Si sí, el aviso
 * flotante ([WizardInlineErrors]) no lo repite: antes pintaba TODO `errors` encima del paso y el mismo texto salía dos
 * veces (en el paso y en el aviso, con su propio «Reintentar»).
 *
 *  - PLAN pinta `candidates` (la búsqueda que falló o «ningún plan viable») y `preview` (el error del programa de la
 *    selección, encima de las tarjetas, con su «Reintentar»).
 *  - TRAINING_REVIEW y la revisión final (REVIEW_ACTIVATE) pintan `preview` (sin programa preparado).
 *
 * Todo lo demás (guardado, activación, la clave `plan` de «Continuar»…) no lo pinta ningún paso y sigue en el aviso.
 */
internal fun stepRendersError(step: SetupStepId, key: String): Boolean = when (step) {
    SetupStepId.PLAN -> key == "candidates" || key == "preview"
    SetupStepId.TRAINING_REVIEW, SetupStepId.REVIEW_ACTIVATE -> key == "preview"
    else -> false
}

/**
 * Banner inline de errores, colocado en el flujo (por encima del paso, nunca
 * sobre la cabecera). Cada error se puede cerrar y, si pertenece a una
 * operación fallida, reintentar exactamente esa operación.
 */
@Composable
private fun WizardInlineErrors(
    errors: Map<String, String>,
    onDismiss: (String) -> Unit,
    retryFor: (String) -> SetupRetryOperation?,
    onRetry: (SetupRetryOperation) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = WizardSpacing.gutter, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        errors.forEach { (key, message) ->
            Surface(
                color = WizardColors.cardFill,
                shape = WizardShapes.card,
                border = BorderStroke(1.dp, WizardColors.danger),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = message,
                        style = WizardTypography.bodySmall,
                        color = WizardColors.text,
                        modifier = Modifier.weight(1f),
                    )
                    retryFor(key)?.let { operation ->
                        TextButton(onClick = { onRetry(operation) }) {
                            Text("Reintentar", color = WizardColors.text)
                        }
                    }
                    TextButton(onClick = { onDismiss(key) }) { Text("Cerrar", color = WizardColors.danger) }
                }
            }
        }
    }
}

/**
 * Estados sin paso: carga, error recuperable y borrador no convertible.
 *
 * Es una pantalla completa, NO un AlertDialog: una carga que no termina o un
 * error no puede quedarse tras un modal sin salida ("popup eterno"), y todas
 * las acciones siguen visibles para volver o reintentar.
 */
@Composable
private fun WizardStatusScreen(
    title: String,
    body: String,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    tertiaryLabel: String? = null,
    onTertiary: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WizardColors.background)
            .statusBarsPadding()
            .padding(horizontal = WizardSpacing.gutter),
        verticalArrangement = Arrangement.spacedBy(WizardSpacing.cardGap, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = WizardTypography.question,
            color = WizardColors.text,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = WizardTypography.bodySmall,
            color = WizardColors.textMuted,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(WizardSpacing.cardGap)) {
            if (secondaryLabel != null && onSecondary != null) {
                TextButton(onClick = onSecondary) { Text(secondaryLabel) }
            }
            if (tertiaryLabel != null && onTertiary != null) {
                TextButton(onClick = onTertiary) { Text(tertiaryLabel, color = WizardColors.danger) }
            }
        }
    }
}

/**
 * Intenciones de salida, separadas a propósito:
 *  - Salir (atrás o la X de la cabecera) abre «Guardar y salir» / «Seguir
 *    configurando»; el wizard solo se abandona cuando el guardado terminó y
 *    devolvió verdadero.
 *  - Descartar es una acción aparte, detrás de su propia confirmación, y nunca
 *    es alcanzable desde el botón Atrás. Solo navega si el descarte tuvo éxito.
 */
@Composable
private fun SetupWizardExitDialogs(
    state: SetupWizardState,
    viewModel: SetupWizardViewModel,
    onLeftWizard: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    when (state.dialog) {
        SetupWizardDialog.EXIT -> Unit

        SetupWizardDialog.DISCARD -> AlertDialog(
            onDismissRequest = { viewModel.keepConfiguring() },
            title = { Text("¿Descartar este borrador?", style = WizardTypography.cardTitle, color = WizardColors.text) },
            text = {
                Text(
                    "Se perderán las respuestas guardadas de esta configuración. Esta acción no se puede deshacer.",
                    style = WizardTypography.bodySmall,
                    color = WizardColors.textMuted,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        if (viewModel.confirmDiscard()) onLeftWizard()
                    }
                }) {
                    Text("Descartar", color = WizardColors.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.keepConfiguring() }) { Text("Conservar") }
            },
        )

        SetupWizardDialog.NONE -> Unit
    }
}