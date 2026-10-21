package com.example.kpkn.screens.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.data.models.NutritionCalibrationProfile
import com.example.kpkn.screens.nutrition.checkin.*
import com.example.kpkn.screens.nutrition.design.NutritionSubpageHeader
import com.example.kpkn.screens.nutrition.design.calibration.CalibrationStatusModule
import com.example.kpkn.screens.nutrition.design.calibration.CalibrationWizardModule
import com.example.kpkn.screens.nutrition.design.calibration.WEIGHING_CONVENTIONS
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/** El nombre de la convención de pesaje: «sin definir» mientras no se haya elegido; un valor desconocido se muestra tal cual. */
internal fun weighingConventionLabel(convention: String?): String =
    if (convention == null) "sin definir" else WEIGHING_CONVENTIONS.firstOrNull { it.first == convention }?.second ?: convention

/** Etiquetas conservadas para leer perfiles de calibración anteriores. */
internal fun calibrationStatusLabel(status: String): String = when (status) {
    "incomplete" -> "Faltan días de registro"
    "needs_more_weights_or_complete_days" -> "Faltan pesajes o días completos"
    "waiting_after_plan_change" -> "En espera: cambiaste el plan hace poco"
    "ready" -> "Lista para ajustar"
    else -> "En evaluación"
}

/**
 * Detalle del estimador y revisión semanal; conserva el asistente de hábitos y el enlace de calibración.
 */
@Composable
fun NutritionCalibrationScreen(
    onBack: () -> Unit,
    viewModel: NutritionCalibrationViewModel = viewModel(),
    onResumeLogging: () -> Unit = onBack,
    onWeighIn: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val checkinState by viewModel.checkinState.collectAsState()
    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.evaluate() }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner, viewModel) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) viewModel.checkin.refresh()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    NutritionCalibrationContent(
        state = state,
        onBack = onBack,
        onEvaluate = viewModel::evaluate,
        onReset = viewModel::reset,
        onAnswerWizard = viewModel::answerWizard,
        onSkipWizard = viewModel::skipWizard,
        onResumeWizard = viewModel::resumeWizard,
        checkinState = checkinState,
        checkinController = viewModel.checkin,
        onResumeLogging = onResumeLogging,
        onWeighIn = onWeighIn,
    )
}

/**
 * La página de calibración sin su ViewModel. Sin el error crudo: si algo falla, un aviso en lenguaje llano. Sin `LinearProgressIndicator`:
 * mientras carga, la píldora dice «Evaluando…» y se apaga.
 */
@Composable
internal fun NutritionCalibrationContent(
    state: NutritionCalibrationUiState,
    onBack: () -> Unit,
    onEvaluate: () -> Unit,
    onReset: () -> Unit,
    onAnswerWizard: (String) -> Unit,
    onSkipWizard: () -> Unit,
    onResumeWizard: () -> Unit,
    checkinState: NutritionCheckinUiState = NutritionCheckinUiState(loading = false),
    checkinController: NutritionCheckinController? = null,
    onResumeLogging: () -> Unit = {},
    onWeighIn: () -> Unit = {},
) {
    val wizard = state.wizard
    // La respuesta en curso se borra al cambiar de paso; así un paso nuevo nunca arrastra lo escrito en el anterior.
    var answer by remember(wizard.step, wizard.profile.lastWizardUpdatedAtEpochMs) { mutableStateOf("") }
    var askReset by remember { mutableStateOf(false) }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val pagePadding = Modifier.padding(horizontal = 16.dp)

    Box(modifier = Modifier.fillMaxSize().background(KpknSurface.canvas)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "calibration-header") {
                NutritionSubpageHeader(title = "Calibración", topInset = topInset, onBack = onBack)
            }
            item(key = "calibration-intro") {
                Text(
                    text = "Revisa tu gasto estimado, confirma los días que quieras usar y decide sobre tu objetivo. Hoy y el historial conservan sus metas.",
                    style = KpknType.body,
                    color = WizardColors.textMuted,
                    modifier = pagePadding,
                )
            }
            item(key = "calibration-status") {
                CalibrationStatusModule(
                    estimate = checkinState.plan?.estimate,
                    modifier = pagePadding,
                )
            }
            item(key = "calibration-checkin") {
                NutritionCheckinContent(
                    state = checkinState,
                    onNext = { checkinController?.next() }, onPrevious = { checkinController?.back() },
                    onAccept = { checkinController?.accept() }, onDecline = { checkinController?.decline() },
                    onPostpone = { checkinController?.postpone() }, onDismissModule = { checkinController?.dismissModule(it) },
                    onPartial = { checkinController?.markPartial(it) }, onComplete = { checkinController?.keepComplete(it) },
                    onFasting = { checkinController?.confirmFasting(it) }, onResumeLogging = onResumeLogging,
                    onWeighIn = onWeighIn, modifier = pagePadding,
                )
            }
            item(key = "calibration-checkin-day") {
                CheckinDaySetting(checkinState.checkinDay, { checkinController?.setCheckinDay(it) }, pagePadding)
            }
            item(key = "calibration-evaluate") {
                KpknPrimaryPill(
                    text = if (state.isLoading) "Evaluando…" else "Evaluar mis últimos datos",
                    onClick = onEvaluate,
                    modifier = pagePadding.fillMaxWidth(),
                    enabled = !state.isLoading,
                )
            }
            if (state.error != null) {
                item(key = "calibration-error") {
                    KpknModule(modifier = pagePadding.fillMaxWidth(), accent = WizardColors.danger) {
                        Text(
                            text = "No se pudo calibrar. Inténtalo de nuevo.",
                            style = KpknType.bodyStrong,
                            color = WizardColors.text,
                        )
                    }
                }
            }
            item(key = "calibration-wizard") {
                CalibrationWizardModule(
                    wizard = wizard,
                    answer = answer,
                    onAnswerChange = { answer = it },
                    onAnswer = onAnswerWizard,
                    onSkip = onSkipWizard,
                    onResume = onResumeWizard,
                    modifier = pagePadding,
                )
            }
            item(key = "calibration-reset") {
                KpknTextAction(
                    text = "Restablecer calibración",
                    onClick = { askReset = true },
                    modifier = pagePadding,
                    color = WizardColors.danger,
                )
            }
        }
    }

    if (askReset) {
        KpknAlertDialog(
            onDismissRequest = { askReset = false },
            title = "¿Restablecer la calibración?",
            text = "Se borra el perfil de calibración, con sus revisiones. Tus registros de comida no cambian.",
            confirmLabel = "Restablecer",
            onConfirm = {
                askReset = false
                onReset()
            },
            dismissLabel = "Cancelar",
            onDismiss = { askReset = false },
            destructive = true,
        )
    }
}
