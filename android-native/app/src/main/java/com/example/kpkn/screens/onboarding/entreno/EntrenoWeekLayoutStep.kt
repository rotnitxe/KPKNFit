package com.example.kpkn.screens.onboarding.entreno

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.SetupLayoutSession
import com.example.kpkn.screens.onboarding.SetupRetryOperation
import com.example.kpkn.screens.onboarding.SetupWeekLayout
import com.example.kpkn.screens.onboarding.SetupWizardState
import com.example.kpkn.screens.onboarding.SetupWizardViewModel
import com.example.kpkn.screens.onboarding.TrainingLoading
import com.example.kpkn.screens.onboarding.TrainingNotice
import com.example.kpkn.screens.onboarding.TrainingNoticeTone
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardTypography

/**
 * WEEK_LAYOUT · «Así queda tu semana»: las sesiones del programa previsualizado en sus días, para moverlas o adaptar el
 * programa a otro reparto. Lo que se ve es exactamente el programa que se activará (`state.programPreview`, armado por
 * `applyLayout`).
 *
 * Control PROVISIONAL y funcional hasta que aterrice el tablero de U5 (`WeekLayoutBoard`): sustituir el cuerpo de este
 * archivo. Lee SOLO `state.weekLayout` ([SetupWeekLayout]: sesiones, asignación día → sesión, repartos y avisos) y escribe
 * SOLO con `vm.moveSession(id, día)`, `vm.adaptToSplit(id, authoredConfirmed)` y `vm.resetWeekLayout()`. Tocar una sesión
 * la «levanta» y tocar otro día la deja ahí (a un día ocupado, intercambia): es también el camino accesible sin arrastre
 * del tablero definitivo.
 */
@Composable
internal fun EntrenoWeekLayoutStep(state: SetupWizardState, vm: SetupWizardViewModel) {
    val layout = state.weekLayout
    val previewError = state.previewError
    when {
        previewError != null -> TrainingNotice(
            text = previewError,
            tone = TrainingNoticeTone.ERROR,
            actionLabel = "Reintentar",
            onAction = { vm.retryFailedOperation(SetupRetryOperation.PREVIEW) },
        )
        layout == null -> TrainingLoading("Preparando tu semana…")
        else -> WeekLayoutProvisional(layout = layout, busy = state.isPreviewLoading, vm = vm)
    }
}

@Composable
private fun WeekLayoutProvisional(layout: SetupWeekLayout, busy: Boolean, vm: SetupWizardViewModel) {
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    var chosenSplit by rememberSaveable { mutableStateOf<String?>(null) }
    var confirming by rememberSaveable { mutableStateOf(false) }
    val sessionsById = layout.sessions.associateBy { it.id }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        weekOrder(layout.weekStartDay).forEach { day ->
            val session = layout.assignment[day]?.let { sessionsById[it] }
            val name = ENTRENO_WEEKDAY_NAMES[day - 1]
            if (session == null) {
                EntrenoOptionRow(
                    label = name,
                    selected = false,
                    enabled = picked != null && !busy,
                    onClick = {
                        picked?.let { id -> vm.moveSession(id, day) }
                        picked = null
                    },
                    supporting = if (picked != null) "Descanso · Mover aquí" else "Descanso",
                    multi = false,
                    tag = "setup-layout-slot-$day",
                )
            } else {
                EntrenoOptionRow(
                    label = "$name · ${session.title}",
                    selected = picked == session.id,
                    enabled = !busy,
                    onClick = {
                        val current = picked
                        picked = when (current) {
                            null -> session.id
                            session.id -> null
                            else -> {
                                vm.moveSession(current, day)
                                null
                            }
                        }
                    },
                    supporting = sessionLine(session),
                    multi = false,
                    tag = "setup-layout-session-${session.id}",
                )
            }
        }
        if (picked != null) EntrenoCaption("Toca el día al que quieres moverla.")

        if (layout.splitOptions.isNotEmpty()) {
            EntrenoSectionLabel("Adaptar a un reparto", modifier = Modifier.padding(top = 8.dp))
            val selected = chosenSplit ?: layout.selectedSplitId
            layout.splitOptions.forEach { option ->
                EntrenoOptionRow(
                    label = option.name,
                    selected = selected == option.id,
                    enabled = !busy,
                    onClick = { chosenSplit = option.id },
                    supporting = option.summary,
                    multi = false,
                    tag = "setup-layout-split-${option.id}",
                )
            }
            if (chosenSplit != null && chosenSplit != layout.selectedSplitId) {
                TextButton(
                    onClick = { confirming = true },
                    enabled = !busy,
                    modifier = Modifier.testTag("setup-layout-adapt"),
                ) {
                    Text("Adaptar mi programa a este reparto", color = WizardColors.text, style = WizardTypography.cardTitle)
                }
            }
        }
        if (layout.canReset) {
            TextButton(
                onClick = {
                    chosenSplit = null
                    picked = null
                    vm.resetWeekLayout()
                },
                enabled = !busy,
                modifier = Modifier.testTag("setup-layout-reset"),
            ) {
                Text("Restablecer", color = WizardColors.textMuted, style = WizardTypography.cardTitle)
            }
        }
        layout.refusal?.let { refusal -> TrainingNotice(text = refusal, tone = TrainingNoticeTone.ERROR) }
        layout.notes.forEach { note -> EntrenoCaption(note) }
        EntrenoCaption("Puedes cambiar todo esto cuando quieras desde tu programa.")
    }

    val pendingId = chosenSplit
    val pendingName = layout.splitOptions.firstOrNull { it.id == pendingId }?.name
    if (confirming && pendingId != null && pendingName != null) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("¿Adaptar tu programa a «$pendingName»?", style = WizardTypography.cardTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Reubicamos los ejercicios y mantenemos tu volumen semanal.", style = WizardTypography.bodySmall)
                    if (layout.authoredStructure) {
                        Text(
                            "Este programa trae su reparto de autor. Si lo adaptas, cambia su estructura original.",
                            style = WizardTypography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        vm.adaptToSplit(pendingId, authoredConfirmed = layout.authoredStructure)
                    },
                    modifier = Modifier.testTag("setup-layout-adapt-confirm"),
                ) { Text("Adaptar") }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirming = false
                    chosenSplit = null
                }) { Text("Mantener mi reparto") }
            },
        )
    }
}

/** «60 min · 6 ejercicios · Tu sesión más fuerte · Gimnasio». */
internal fun sessionLine(session: SetupLayoutSession): String = buildList {
    add("${session.minutes} min")
    add(if (session.exerciseCount == 1) "1 ejercicio" else "${session.exerciseCount} ejercicios")
    if (session.isMain) add("Tu sesión más fuerte")
    session.place?.let { add(it.label) }
}.joinToString(" · ")

/** Los siete días de la semana en el orden que empieza en [weekStartDay] (1 = lunes). */
internal fun weekOrder(weekStartDay: Int): List<Int> =
    (0 until 7).map { offset -> Math.floorMod(weekStartDay - 1 + offset, 7) + 1 }
