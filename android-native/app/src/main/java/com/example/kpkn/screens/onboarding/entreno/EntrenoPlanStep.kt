package com.example.kpkn.screens.onboarding.entreno

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.screens.onboarding.PLAN_PREPARE_FAILED_MESSAGE
import com.example.kpkn.screens.onboarding.SetupPlanReveal
import com.example.kpkn.screens.onboarding.SetupPlanSweep
import com.example.kpkn.screens.onboarding.SetupProgramRoute
import com.example.kpkn.screens.onboarding.SetupRetryOperation
import com.example.kpkn.screens.onboarding.SetupTrainingPath
import com.example.kpkn.screens.onboarding.SetupWizardState
import com.example.kpkn.screens.onboarding.SetupWizardViewModel
import com.example.kpkn.screens.onboarding.TrainingPlanStep
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanCarousel
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanCopy
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanDetailOverlay
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanPreparingOverlay
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanPreparingVariant

/**
 * PLAN · «Tu programa a medida» (perfil general) / «Elige tu programa» (disciplina): el revelado del programa.
 *
 * - Con el paso activo, cada barrido de programas (al llegar, tras «Otra versión», tras «Reintentar») abre
 *   `PlanPreparingOverlay` (GENERAL o DISCIPLINE): corre mientras el barrido trabaja (`ready` = `state.planSweep` en
 *   READY) y, al avisar `onAnimationDone`, se retira y la página revela el resultado. Un resultado ya revelado (misma
 *   huella, [revealKeyOf]) no repite el overlay al girar la pantalla.
 * - **General**: el programa «a medida» con su portada (carrusel de una sola tarjeta: blurb, «Ver detalles» y
 *   «Elegir»), «Tu programa está listo», sus razones, «Otra versión» (`vm.anotherPlanVersion()`) y la frase fija.
 * - **Disciplina**: `PlanCarousel` con las portadas de todos los programas viables (el «a medida» al frente).
 * - «Ver detalles» o tocar la portada central abre `PlanDetailOverlay` con el detalle REAL del programa ya preparado;
 *   «Elegir este programa» elige (`vm.selectPlan`) y lo cierra. Cerrar sin elegir deja el paso pendiente: «Ver detalles»
 *   y «Elegir» siguen bajo la portada para reabrirlo o elegir.
 * - «Lo haré más adelante» (`vm.deferProgramUntilLater()`) es una acción discreta: no se crea programa y WEEK_LAYOUT se
 *   salta. Aplazado, el paso ofrece volver a prepararlo (`vm.setProgramRoute(CUSTOMIZABLE)`).
 *
 * Lee `state.planSweep`, `state.planReveals` (modelos ya calculados por el ViewModel), `state.draft`, `state.errors` y
 * `state.previewError`; escribe SOLO por el ViewModel. Lo que dura más allá de una recomposición (resultado ya revelado,
 * detalle abierto) se guarda con `rememberSaveable`; el plan elegido vive en el borrador (sobrevive a girar la pantalla
 * y a guardar y salir). Los borradores sin perfil de objetivo y la ruta «desde cero» conservan la lista clásica.
 */
@Composable
internal fun EntrenoPlanStep(state: SetupWizardState, vm: SetupWizardViewModel) {
    val draft = state.draft
    val profile = draft.goalProfile
    if (draft.trainingPath == SetupTrainingPath.FROM_SCRATCH || profile == null) {
        TrainingPlanStep(state = state, vm = vm)
        return
    }
    val active = state.currentStep == SetupStepId.PLAN
    val discipline = profile.isSpecific
    val reveals = state.planReveals
    val sweep = state.planSweep
    val deferred = draft.programRoute == SetupProgramRoute.LATER
    val selectedId = draft.selectedCatalogId?.takeIf { !deferred }

    val revealKey = remember(reveals) { revealKeyOf(reveals) }
    // La huella del último resultado revelado y si, con el paso activo, se vio un barrido en curso: con un barrido visto
    // el overlay llega al final de su animación aunque el resultado nuevo coincida con el anterior.
    var revealedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var sweepSeen by rememberSaveable { mutableStateOf(false) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(active, sweep) {
        if (active && sweep == SetupPlanSweep.LOADING) sweepSeen = true
    }

    val preparing = active && !deferred && when (sweep) {
        SetupPlanSweep.LOADING -> true
        SetupPlanSweep.READY -> sweepSeen || revealedKey != revealKey
        SetupPlanSweep.IDLE, SetupPlanSweep.FAILED -> false
    }
    if (preparing) {
        PlanPreparingOverlay(
            variant = if (discipline) PlanPreparingVariant.DISCIPLINE else PlanPreparingVariant.GENERAL,
            profile = profile,
            ready = sweep == SetupPlanSweep.READY,
            onAnimationDone = {
                revealedKey = revealKey
                sweepSeen = false
            },
        )
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (deferred) {
            DeferredPlan(onResume = { vm.setProgramRoute(SetupProgramRoute.CUSTOMIZABLE) })
        } else {
            // El plan elegido (o traído de la biblioteca) que ya no encaja: su aviso con la reparación de un toque.
            EntrenoDroppedSelectionNotice(state = state, vm = vm)
            when {
                sweep == SetupPlanSweep.FAILED -> EntrenoPlanNotice(
                    text = state.errors["candidates"] ?: PLAN_PREPARE_FAILED_MESSAGE,
                    actions = listOf(
                        EntrenoPlanNoticeAction(RETRY_LABEL, RETRY_SWEEP_TAG) {
                            vm.retryFailedOperation(SetupRetryOperation.CANDIDATES)
                        },
                    ),
                )
                // Debajo del overlay la página aún no enseña el resultado: se revela cuando el overlay se retira.
                preparing || sweep != SetupPlanSweep.READY || reveals.isEmpty() -> EntrenoPlanLoadingLine(
                    if (discipline) "${PlanCopy.PREPARING_DISCIPLINE}…" else "${PlanCopy.PREPARING_GENERAL}…",
                )
                discipline -> PlanCarousel(
                    cards = cardModelsOf(reveals),
                    selectedId = selectedId,
                    onSelect = { id -> vm.selectPlan(id) },
                    onOpen = { id -> detailId = id },
                )
                else -> GeneralReveal(
                    reveal = reveals.first(),
                    selectedId = selectedId,
                    onChoose = { id -> vm.selectPlan(id) },
                    onOpen = { id -> detailId = id },
                    onAnotherVersion = { vm.anotherPlanVersion() },
                )
            }
            state.previewError?.let { error ->
                EntrenoPlanNotice(
                    text = error,
                    actions = listOf(
                        EntrenoPlanNoticeAction(RETRY_LABEL, RETRY_PREVIEW_TAG) {
                            vm.retryFailedOperation(SetupRetryOperation.PREVIEW)
                        },
                    ),
                )
            }
            TextButton(onClick = { vm.deferProgramUntilLater() }, modifier = Modifier.testTag("setup-plan-defer")) {
                Text(DEFER_LABEL, color = WizardColors.textMuted, style = WizardTypography.cardSubtitle)
            }
        }
    }

    val detailReveal = detailId?.let { id -> reveals.firstOrNull { it.planId == id } }
    // El barrido cambió y ese programa ya no está (o vuelve a correr el overlay «preparando…»): el detalle se cierra.
    LaunchedEffect(detailId, detailReveal == null, preparing, deferred) {
        if (detailId != null && (detailReveal == null || preparing || deferred)) detailId = null
    }
    if (detailReveal != null && !preparing && !deferred) {
        val id = detailReveal.planId
        val model = remember(detailReveal) { detailReveal.toDetailModel() }
        PlanDetailOverlay(
            model = model,
            selected = selectedId == id,
            onChoose = {
                vm.selectPlan(id)
                detailId = null
            },
            onClose = { detailId = null },
        )
    }
}

/**
 * El revelado del programa «a medida» de un perfil general: «Tu programa está listo», su portada (el carrusel de una
 * sola tarjeta trae el blurb, «Ver detalles» y «Elegir»), las razones del generador, «Otra versión» y la frase fija.
 * Sin cajas: texto y acciones sobre la página.
 */
@Composable
private fun GeneralReveal(
    reveal: SetupPlanReveal,
    selectedId: String?,
    onChoose: (String) -> Unit,
    onOpen: (String) -> Unit,
    onAnotherVersion: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().testTag("setup-plan-reveal"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text = READY_TITLE, style = WizardTypography.controlLabel, color = WizardColors.text)
        PlanCarousel(
            cards = listOf(reveal.toCardModel()),
            selectedId = selectedId,
            onSelect = onChoose,
            onOpen = onOpen,
        )
        reveal.reasons.take(MAX_REASONS).forEach { reason ->
            Text(text = reason, style = WizardTypography.bodySmall, color = WizardColors.textMuted)
        }
        TextButton(onClick = onAnotherVersion, modifier = Modifier.testTag("setup-plan-another-version")) {
            Text(ANOTHER_VERSION_LABEL, color = WizardColors.text, style = WizardTypography.cardTitle)
        }
        Text(text = PlanCopy.EDIT_FREELY, style = WizardTypography.note, color = WizardColors.textMuted)
    }
}

/** El programa aplazado: lo que pasará y la vuelta atrás (volver a preparar el programa con las mismas respuestas). */
@Composable
private fun DeferredPlan(onResume: () -> Unit) {
    Text(text = DEFERRED_NOTE, style = WizardTypography.bodySmall, color = WizardColors.textMuted)
    TextButton(onClick = onResume, modifier = Modifier.testTag("setup-plan-resume")) {
        Text(RESUME_LABEL, color = WizardColors.text, style = WizardTypography.cardTitle)
    }
}

/** Razones de «por qué este programa» que caben en el revelado. */
private const val MAX_REASONS = 5

/** Textos del revelado (COPY.md, «Revelado del programa»; el aplazado conserva el del resumen de revisión). */
private const val READY_TITLE = "Tu programa está listo"
private const val ANOTHER_VERSION_LABEL = "Otra versión"
private const val DEFER_LABEL = "Lo haré más adelante"
private const val DEFERRED_NOTE = "Lo armarás manualmente más adelante."
private const val RESUME_LABEL = "Preparar mi programa ahora"
private const val RETRY_LABEL = "Reintentar"

/** Marcas de prueba de «Reintentar»: el barrido de programas y la vista previa del elegido. */
internal const val RETRY_SWEEP_TAG = "setup-plan-retry"
internal const val RETRY_PREVIEW_TAG = "setup-plan-retry-preview"
