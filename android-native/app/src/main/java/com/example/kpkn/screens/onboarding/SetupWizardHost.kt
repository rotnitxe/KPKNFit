package com.example.kpkn.screens.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.domain.onboarding.SetupWizardBlock
import com.example.kpkn.domain.onboarding.WizChatMachineState
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardTypography
import kotlinx.coroutines.launch

/**
 * Host del wizard tradicional que sustituye a WizChat.
 *
 * Una pantalla por paso, sin conversación. Conserva intactas las piezas que el
 * plan manda preservar: inicialización del borrador, **atrás sin pérdida** (nunca
 * descarta), diálogos de salida diferenciados y activación conjunta al final.
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

    LaunchedEffect(mode, draftId) { viewModel.initialize(mode, draftId = draftId) }

    // Atrás nunca descarta ni borra respuestas: abre la salida explícita.
    // Si ya hay un diálogo abierto, atrás lo cierra y sigue configurando.
    BackHandler {
        when {
            state.isSavingAndExiting -> Unit
            state.dialog == SetupWizardDialog.NONE -> viewModel.requestExit()
            else -> viewModel.keepConfiguring()
        }
    }

    SetupWizardExitDialogs(state = state, viewModel = viewModel, onLeftWizard = onCancel)

    val step: SetupStepId = state.draft.stepProgress.let { progress ->
        SetupStepGraphCurrentStep.resolve(state) ?: SetupStepId.NAME
    }

    when (state.machineState) {
        WizChatMachineState.Loading -> WizardStatusScreen(
            title = "Preparando tu configuración…",
            body = "Estamos recuperando tus respuestas guardadas.",
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
            body = "Tu borrador sigue guardado. Puedes reintentarlo.",
            secondaryLabel = "Reintentar",
            onSecondary = { viewModel.initialize(mode, draftId = draftId) },
            tertiaryLabel = "Volver",
            onTertiary = onCancel,
        )

        else -> SetupStepScreen(
            step = step,
            state = state,
            vm = viewModel,
            onBack = { viewModel.goBack() },
            onExit = { viewModel.requestExit() },
            ctaLabel = if (step == SetupStepId.REVIEW_ACTIVATE) "Activar y entrar a KPKN" else "Continuar",
            ctaEnabled = !state.isSavingAndExiting,
            onCta = {
                if (step == SetupStepId.REVIEW_ACTIVATE) {
                    scope.launch {
                        if (viewModel.commit() != null && !navigatedAfterCommit) {
                            navigatedAfterCommit = true
                            onDone()
                        }
                    }
                } else {
                    viewModel.goNext()
                }
            },
        )
    }
}

/** Estados sin paso: carga, error recuperable y borrador no convertible. */
@Composable
private fun WizardStatusScreen(
    title: String,
    body: String,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    tertiaryLabel: String? = null,
    onTertiary: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text(title, style = WizardTypography.cardTitle, color = WizardColors.text) },
        text = { Text(body, style = WizardTypography.bodySmall, color = WizardColors.textMuted) },
        confirmButton = {
            if (secondaryLabel != null && onSecondary != null) {
                TextButton(onClick = onSecondary) { Text(secondaryLabel) }
            }
        },
        dismissButton = {
            if (tertiaryLabel != null && onTertiary != null) {
                TextButton(onClick = onTertiary) { Text(tertiaryLabel) }
            }
        },
    )
}

/**
 * Intenciones de salida, separadas a propósito:
 *  - Salir (atrás o la X de la cabecera) abre «Guardar y salir» / «Seguir
 *    configurando»; el wizard solo se abandona cuando el guardado terminó.
 *  - Descartar es una acción aparte, detrás de su propia confirmación, y nunca
 *    es alcanzable desde el botón Atrás.
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
                Text(
                    "Puedes guardar tus respuestas y continuar más tarde. Nada se activa todavía.",
                    style = WizardTypography.bodySmall,
                    color = WizardColors.textMuted,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !state.isSavingAndExiting,
                    onClick = {
                        scope.launch {
                            viewModel.saveAndExit()
                            onLeftWizard()
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
                TextButton(onClick = { scope.launch { viewModel.confirmDiscard(); onLeftWizard() } }) {
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

/**
 * Resolución del paso actual a partir del progreso persistido. Se mantiene aquí
 * para no exponer detalles del grafo en la capa de presentación.
 */
private object SetupStepGraphCurrentStep {
    fun resolve(state: SetupWizardState): SetupStepId? {
        val progress = state.draft.stepProgress
        val blockSteps = SetupStepId.entries.filter {
            com.example.kpkn.domain.onboarding.SetupStepGraph.blockOf(it) == progress.block
        }
        return blockSteps.getOrNull(progress.stepIndex)
            ?: SetupStepId.entries.firstOrNull { com.example.kpkn.domain.onboarding.SetupStepGraph.blockOf(it) == progress.block }
    }
}
