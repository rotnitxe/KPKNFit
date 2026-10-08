package com.example.kpkn.screens.onboarding.entreno

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.SetupLayoutSession
import com.example.kpkn.screens.onboarding.SetupPlaceConflict
import com.example.kpkn.screens.onboarding.SetupRetryOperation
import com.example.kpkn.screens.onboarding.SetupSplitOption
import com.example.kpkn.screens.onboarding.SetupWeekLayout
import com.example.kpkn.screens.onboarding.SetupWizardState
import com.example.kpkn.screens.onboarding.SetupWizardViewModel
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.entreno.layout.SplitOption
import com.example.kpkn.screens.onboarding.design.entreno.layout.WEEK_LAYOUT_RESET_TAG
import com.example.kpkn.screens.onboarding.design.entreno.layout.WeekLayoutBoard
import com.example.kpkn.screens.onboarding.design.entreno.layout.WeekLayoutCopy
import com.example.kpkn.screens.onboarding.design.entreno.layout.WeekLayoutSession
import com.example.kpkn.screens.onboarding.placeConflictDayName
import com.example.kpkn.screens.onboarding.placeConflictSentence

/**
 * WEEK_LAYOUT · «Así queda tu semana»: el tablero de la semana (`WeekLayoutBoard`) con las sesiones del programa
 * previsualizado en sus días y, debajo, el carril de repartos. Lo que se ve es exactamente el programa que se activará
 * (`state.programPreview`, armado por `applyLayout`).
 *
 * Lee SOLO `state.weekLayout` ([SetupWeekLayout]: sesiones, asignación día → sesión, repartos y avisos),
 * `state.isPreviewLoading` (el tablero se atenúa mientras se re-arma) y `state.previewError`; escribe SOLO con
 * `vm.moveSession(id, día)`, `vm.adaptToSplit(id, authoredConfirmed)` y `vm.resetWeekLayout()`. El tablero solo avisa de
 * «Adaptar mi programa a este reparto»: la confirmación de COPY («¿Adaptar tu programa a «X»?») vive aquí y, en un plan
 * de autor, lleva su aviso de estructura (también como `splitNotice` del tablero); solo tras confirmarla se pide al
 * ViewModel, con `authoredConfirmed = true` si el plan es de autor.
 */
@Composable
internal fun EntrenoWeekLayoutStep(state: SetupWizardState, vm: SetupWizardViewModel) {
    val layout = state.weekLayout
    val previewError = state.previewError
    when {
        previewError != null -> EntrenoPlanNotice(
            text = previewError,
            actions = listOf(
                EntrenoPlanNoticeAction(label = "Reintentar", tag = LAYOUT_RETRY_TAG) {
                    vm.retryFailedOperation(SetupRetryOperation.PREVIEW)
                },
            ),
        )
        layout == null -> EntrenoPlanLoadingLine(PREPARING_WEEK)
        else -> WeekLayoutStepBoard(layout = layout, busy = state.isPreviewLoading, vm = vm)
    }
}

@Composable
private fun WeekLayoutStepBoard(layout: SetupWeekLayout, busy: Boolean, vm: SetupWizardViewModel) {
    var confirmingSplit by rememberSaveable { mutableStateOf<String?>(null) }
    val sessions = remember(layout.sessions) { boardSessionsOf(layout.sessions) }
    val options = remember(layout.splitOptions) { layout.splitOptions.map { it.toBoardOption() } }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WeekLayoutBoard(
            weekStartDay = layout.weekStartDay,
            sessions = sessions,
            assignment = layout.assignment,
            onMove = { sessionId, toDay -> vm.moveSession(sessionId, toDay) },
            splitOptions = options,
            selectedSplitId = layout.selectedSplitId,
            onAdaptSplit = { splitId -> confirmingSplit = splitId },
            canReset = layout.canReset,
            onReset = { vm.resetWeekLayout() },
            adapting = busy,
            splitNotice = AUTHORED_SPLIT_NOTICE.takeIf { layout.authoredStructure },
        )
        layout.refusal?.let { refusal -> EntrenoPlanNotice(text = refusal, modifier = Modifier.testTag(LAYOUT_REFUSAL_TAG)) }
        layout.notes.forEach { note -> Text(text = note, style = WizardTypography.note, color = WizardColors.textMuted) }
        // Una sesión que cae en un día cuyo lugar no tiene su material: el movimiento se permite (la persona manda) y el
        // aviso se queda mientras siga así; mover otra vez o «Restablecer» lo quita.
        layout.placeConflicts.forEach { conflict -> PlaceConflictNotice(conflict) }
        // Sin repartos que ofrecer (p. ej. sesiones en varios lugares) el tablero no pinta su carril ni, con él,
        // «Restablecer»: las sesiones movidas se restablecen desde aquí.
        if (options.isEmpty() && layout.canReset) {
            EntrenoTextAction(
                label = WeekLayoutCopy.RESET,
                onClick = { vm.resetWeekLayout() },
                modifier = Modifier.testTag(WEEK_LAYOUT_RESET_TAG),
                color = WizardColors.textMuted,
                enabled = !busy,
            )
        }
    }

    val splitId = confirmingSplit
    val name = layout.splitOptions.firstOrNull { it.id == splitId }?.name
    if (splitId != null && name != null) {
        AlertDialog(
            onDismissRequest = { confirmingSplit = null },
            title = { Text("¿Adaptar tu programa a «$name»?", style = WizardTypography.cardTitle, color = WizardColors.text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(ADAPT_EXPLANATION, style = WizardTypography.bodySmall, color = WizardColors.textMuted)
                    if (layout.authoredStructure) {
                        Text(AUTHORED_SPLIT_NOTICE, style = WizardTypography.bodySmall, color = WizardColors.textMuted)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingSplit = null
                        vm.adaptToSplit(splitId, authoredConfirmed = layout.authoredStructure)
                    },
                    modifier = Modifier.testTag(LAYOUT_ADAPT_CONFIRM_TAG),
                ) { Text("Adaptar", color = WizardColors.text) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingSplit = null }, modifier = Modifier.testTag(LAYOUT_ADAPT_KEEP_TAG)) {
                    Text("Mantener mi reparto")
                }
            },
        )
    }
}

/**
 * El aviso de una sesión que no cabe en el lugar de su día: la sesión y su día (el título lleva `heading` para TalkBack) y
 * debajo la frase de COPY. Sin caja: texto sobre la página.
 */
@Composable
private fun PlaceConflictNotice(conflict: SetupPlaceConflict) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag("$LAYOUT_PLACE_CONFLICT_TAG-${conflict.sessionId}"),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "${conflict.title} · ${placeConflictDayName(conflict.day)}",
            style = WizardTypography.controlLabel,
            color = WizardColors.text,
        )
        Text(text = placeConflictSentence(conflict), style = WizardTypography.bodySmall, color = WizardColors.danger)
    }
}

/**
 * Las sesiones como las dibuja el tablero. El foco lleva además el lugar solo cuando el programa reparte sus sesiones
 * entre varios lugares (con un único lugar sería ruido).
 */
internal fun boardSessionsOf(sessions: List<SetupLayoutSession>): List<WeekLayoutSession> {
    val severalPlaces = sessions.mapNotNull { it.place }.distinct().size > 1
    return sessions.map { session ->
        WeekLayoutSession(
            id = session.id,
            title = session.title,
            focus = listOfNotNull(
                session.focus.takeIf { it.isNotBlank() },
                session.place?.label?.takeIf { severalPlaces },
            ).joinToString(" · "),
            minutes = session.minutes,
            exerciseCount = session.exerciseCount,
            isMain = session.isMain,
        )
    }
}

/** Un reparto del carril del tablero. */
internal fun SetupSplitOption.toBoardOption(): SplitOption = SplitOption(id = id, name = name, summary = summary, dayTitles = dayTitles)

/** Textos de COPY del reparto («Reparto»). */
internal const val AUTHORED_SPLIT_NOTICE = "Este programa trae su reparto de autor. Si lo adaptas, cambia su estructura original."
private const val ADAPT_EXPLANATION = "Reubicamos los ejercicios y mantenemos tu volumen semanal."
private const val PREPARING_WEEK = "Preparando tu semana…"

/** Marcas de prueba propias del paso (las del tablero las pone el tablero: `setup-layout-board`, `-slot-<día>`…). */
internal const val LAYOUT_ADAPT_CONFIRM_TAG = "setup-layout-adapt-confirm"
internal const val LAYOUT_ADAPT_KEEP_TAG = "setup-layout-adapt-keep"
internal const val LAYOUT_REFUSAL_TAG = "setup-layout-refusal"

/** Marca del aviso de una sesión que no cabe en el lugar de su día (sigue `-<id de la sesión>`). */
internal const val LAYOUT_PLACE_CONFLICT_TAG = "setup-layout-place-conflict"
internal const val LAYOUT_RETRY_TAG = "setup-layout-retry"
