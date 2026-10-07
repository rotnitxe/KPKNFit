package com.example.kpkn.screens.onboarding.entreno

import androidx.compose.runtime.Composable
import com.example.kpkn.screens.onboarding.ProgramSessions
import com.example.kpkn.screens.onboarding.SetupRetryOperation
import com.example.kpkn.screens.onboarding.SetupWizardState
import com.example.kpkn.screens.onboarding.SetupWizardViewModel
import com.example.kpkn.screens.onboarding.TrainingLoading
import com.example.kpkn.screens.onboarding.TrainingNotice
import com.example.kpkn.screens.onboarding.TrainingNoticeTone

/**
 * WEEK_LAYOUT · «Así queda tu semana»: las sesiones del programa colocadas en sus días.
 *
 * Por ahora es solo lectura (las sesiones de la primera semana del programa preparado, por día, con su carga y su
 * error con reintento). Punto de enganche del tablero de semana: sustituir el cuerpo de este archivo. Lee
 * `state.programPreview` y los datos de la semana del borrador (`weekStartDay`, `selectedWeekdays`, `effectiveDayPlaces()`,
 * `weekLayoutOverrides`, `adaptedSplitId`); las escrituras de mover sesiones o adaptar el reparto las define el paquete
 * que lo implemente (sobre `weekLayoutOverrides` / `adaptedSplitId` / `planVariantSeed`).
 */
@Composable
internal fun EntrenoWeekLayoutStep(state: SetupWizardState, vm: SetupWizardViewModel) {
    val program = state.programPreview
    val previewError = state.previewError
    when {
        state.isPreviewLoading -> TrainingLoading("Preparando tu semana…")
        previewError != null -> TrainingNotice(
            text = previewError,
            tone = TrainingNoticeTone.ERROR,
            actionLabel = "Reintentar",
            onAction = { vm.retryFailedOperation(SetupRetryOperation.PREVIEW) },
        )
        program == null -> TrainingNotice(
            text = "Todavía no hay un programa preparado. Revisa tus respuestas y elige un programa.",
            tone = TrainingNoticeTone.ERROR,
        )
        else -> ProgramSessions(program = program)
    }
}
