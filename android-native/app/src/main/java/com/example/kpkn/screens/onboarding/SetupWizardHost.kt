package com.example.kpkn.screens.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
    onDone: () -> Unit,
    onCancel: () -> Unit,
    viewModel: SetupWizardViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var navigatedAfterCommit by remember { mutableStateOf(false) }

    // La clave incluye el ViewModel: si las tests recrean el VM, la
    // inicialización se vuelve a ejecutar en lugar de quedar en un no-op.
    LaunchedEffect(mode, draftId, viewModel) { viewModel.initialize(mode, draftId = draftId) }

    // Atrás nunca descarta ni borra respuestas: retrocede un paso cuando existe
    // historial y abre la salida explícita solo en el primer paso.
    BackHandler {
        when {
            state.isSavingAndExiting -> Unit
            state.dialog != SetupWizardDialog.NONE -> viewModel.keepConfiguring()
            viewModel.canGoBack() -> viewModel.goBack()
            else -> viewModel.requestExit()
        }
    }

    SetupWizardExitDialogs(state = state, viewModel = viewModel, onLeftWizard = onCancel)

    val step: SetupStepId = state.currentStep

    when (state.machineState) {
        WizChatMachineState.Loading -> WizardStatusScreen(
            title = "Preparando tu configuración…",
            body = "Estamos recuperando tus respuestas guardadas.",
            // Si la carga nunca termina, el usuario siempre puede salir.
            tertiaryLabel = "Volver",
            onTertiary = onCancel,
        )

        WizChatMachineState.UnsupportedDraft -> WizardStatusScreen(
            title = "No pude abrir este borrador",
            body = state.errors["draft"] ?: "El borrador no es convertible. Puedes conservarlo o descartarlo con confirmación.",
            secondaryLabel = "Volver a configuraciones",
            onSecondary = onCancel,
            tertiaryLabel = "Descartar este borrador",
            onTertiary = { viewModel.requestDiscard() },
        )

        WizChatMachineState.RecoverableError -> WizardStatusScreen(
            title = "No pude abrir tu configuración",
            // Mensaje honesto del fallo real, no un texto genérico.
            body = state.lastFailure
                ?: state.errors["initialize"]
                ?: "Tu borrador sigue guardado. Puedes reintentarlo.",
            secondaryLabel = "Reintentar",
            onSecondary = { viewModel.retryFailedOperation(SetupRetryOperation.LOAD) },
            tertiaryLabel = "Volver",
            onTertiary = onCancel,
        )

        else -> Box(modifier = Modifier.fillMaxSize()) {
            SetupStepScreen(
                    step = step,
                    state = state,
                    vm = viewModel,
                    onBack = { if (viewModel.canGoBack()) viewModel.goBack() else viewModel.requestExit() },
                    onExit = { viewModel.requestExit() },
                    // Un solo CTA por paso, siempre sobre el paso actual.
                    ctaLabel = if (step == SetupStepId.REVIEW_ACTIVATE) "Activar y entrar a KPKN" else "Continuar",
                    ctaEnabled = state.canConfirmStep,
                    onCta = {
                        if (step == SetupStepId.REVIEW_ACTIVATE) {
                            scope.launch {
                                if (viewModel.commit() != null && !navigatedAfterCommit) {
                                    navigatedAfterCommit = true
                                    onDone()
                                }
                            }
                        } else {
                            viewModel.submitCurrentStep(step)
                        }
                    },
            )
            // El aviso flota sobre el paso: no empuja la cabecera, la pregunta
            // ni el botón. Sigue siendo descartable y reintenta la misma operación.
            if (state.errors.isNotEmpty()) {
                WizardInlineErrors(
                    errors = state.errors,
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
        SetupWizardDialog.EXIT -> AlertDialog(
            onDismissRequest = { viewModel.keepConfiguring() },
            title = { Text("¿Quieres salir?", style = WizardTypography.cardTitle, color = WizardColors.text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Puedes guardar tus respuestas y continuar más tarde. Nada se activa todavía.",
                        style = WizardTypography.bodySmall,
                        color = WizardColors.textMuted,
                    )
                    // Tercera acción: sólo ABRE el diálogo DISCARD existente.
                    // Descartar sigue exigiendo su confirmación y la navegación
                    // sólo ocurre si el borrado se persistió (confirmDiscard).
                    TextButton(onClick = { viewModel.requestDiscard() }) {
                        Text("Descartar borrador", color = WizardColors.danger)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !state.isSavingAndExiting,
                    onClick = {
                        scope.launch {
                            if (viewModel.saveAndExit()) onLeftWizard()
                        }
                    },
                ) { Text(if (state.isSavingAndExiting) "Guardando…" else "Guardar y salir") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.keepConfiguring() }) { Text("Seguir configurando") }
            },
        )

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