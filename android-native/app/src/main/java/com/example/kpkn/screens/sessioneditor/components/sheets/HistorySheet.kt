package com.example.kpkn.screens.sessioneditor.components.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.SessionDraftSnapshot
import com.example.kpkn.screens.sessioneditor.SessionEditorUiState
import com.example.kpkn.screens.sessioneditor.formatHistoryTimestamp
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknEmptyState
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknType

/**
 * Versiones de la sesión (ER1e): cada versión es un módulo con su fecha legible y un «Restaurar». Si hay cambios sin guardar, restaurar
 * pide confirmación, como antes.
 */
@Composable
internal fun HistorySheet(
    uiState: SessionEditorUiState,
    onRestoreSnapshot: (SessionDraftSnapshot) -> Unit,
) {
    var pendingRestore by remember { mutableStateOf<SessionDraftSnapshot?>(null) }
    val currentSession = uiState.session

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Versiones",
            style = KpknType.title,
            color = WizardColors.text,
            modifier = Modifier.fillMaxWidth(),
        )
        SheetCaption("Versiones estructurales guardadas al completar un entreno.")
        if (uiState.localDraftHistory.isEmpty()) {
            KpknEmptyState(
                icon = KpknLineIcons.History,
                title = "Todavía no hay versiones",
                body = "Se guardan al terminar un entreno si la estructura cambió.",
            )
        } else {
            uiState.localDraftHistory.asReversed().forEachIndexed { index, snapshot ->
                val title = snapshot.session.name.ifBlank { "Sesión" }
                val diffSummary = snapshot.changedFields.take(3).joinToString(" · ").ifBlank { "ajustes" }
                val isCurrent = currentSession != null &&
                    com.example.kpkn.screens.sessioneditor.TrainedSessionVersionStore.structuralEquals(
                        currentSession,
                        snapshot.session,
                    )
                KpknModule(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${index + 1}. $title",
                            style = KpknType.bodyStrong,
                            color = WizardColors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (isCurrent) {
                            Spacer(Modifier.width(8.dp))
                            SheetTag(text = "Actual", color = WizardColors.done)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    SheetCaption("${formatHistoryTimestamp(snapshot.savedAtMs)} · ${snapshot.reason}")
                    Spacer(Modifier.height(2.dp))
                    SheetCaption("${snapshot.exerciseCount} ejercicios · ${snapshot.setCount} series · ${snapshot.partCount} grupos")
                    Spacer(Modifier.height(2.dp))
                    SheetCaption("Cambios: $diffSummary")
                    if (!isCurrent) {
                        Spacer(Modifier.height(12.dp))
                        KpknSecondaryPill(
                            text = "Restaurar",
                            onClick = {
                                if (uiState.hasUnsavedChanges) {
                                    pendingRestore = snapshot
                                } else {
                                    onRestoreSnapshot(snapshot)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    pendingRestore?.let { snapshot ->
        KpknAlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = "¿Restaurar esta versión?",
            text = "Hay cambios sin guardar. Al restaurar se reemplaza el borrador actual (puedes deshacer con otra versión).",
            confirmLabel = "Restaurar",
            onConfirm = {
                onRestoreSnapshot(snapshot)
                pendingRestore = null
            },
            dismissLabel = "Cancelar",
            onDismiss = { pendingRestore = null },
        )
    }
}
