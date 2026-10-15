package com.example.kpkn.screens.sessioneditor.components.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.sessioneditor.SessionDraftSnapshot
import com.example.kpkn.screens.sessioneditor.SessionEditorUiState
import com.example.kpkn.screens.sessioneditor.SheetHeader
import com.example.kpkn.screens.sessioneditor.formatHistoryTimestamp
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.KpknSheetTokens

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
            .padding(
                horizontal = KpknSheetTokens.ContentPaddingHorizontal,
                vertical = KpknSheetTokens.ContentPaddingTop,
            ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SheetHeader(
            "Versiones",
            "Versiones estructurales guardadas al completar un entreno.",
        )
        if (uiState.localDraftHistory.isEmpty()) {
            Text(
                "Todavía no hay versiones. Se guardan al terminar un entreno si la estructura cambió.",
                color = KpknSheetTokens.MutedStrong,
            )
        } else {
            uiState.localDraftHistory.asReversed().forEachIndexed { index, snapshot ->
                val title = snapshot.session.name.ifBlank { "Sesión" }
                val diffSummary = snapshot.changedFields.take(3).joinToString(" · ").ifBlank { "ajustes" }
                val isCurrentRaw = currentSession != null && currentSession == snapshot.session
                val isCurrent = currentSession != null &&
                    com.example.kpkn.screens.sessioneditor.TrainedSessionVersionStore.structuralEquals(
                        currentSession,
                        snapshot.session,
                    )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(KpknSheetTokens.PanelRadius))
                        .clickable(enabled = !isCurrent) {
                            if (uiState.hasUnsavedChanges && !isCurrent) {
                                pendingRestore = snapshot
                            } else {
                                onRestoreSnapshot(snapshot)
                            }
                        },
                    shape = RoundedCornerShape(KpknSheetTokens.PanelRadius),
                    color = if (isCurrent) Color.White.copy(alpha = 0.16f) else KpknSheetTokens.Panel,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${index + 1}. $title",
                                fontWeight = FontWeight.Bold,
                                color = KpknSheetTokens.Body,
                            )
                            if (isCurrent) {
                                Text(
                                    "Actual",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = KpknSheetTokens.Body,
                                )
                            }
                        }
                        Text(
                            "${formatHistoryTimestamp(snapshot.savedAtMs)} · ${snapshot.reason}",
                            style = MaterialTheme.typography.labelSmall,
                            color = KpknSheetTokens.MutedStrong,
                        )
                        Text(
                            "${snapshot.exerciseCount} ejercicios · ${snapshot.setCount} series · ${snapshot.partCount} grupos",
                            style = MaterialTheme.typography.labelSmall,
                            color = KpknSheetTokens.MutedStrong,
                        )
                        Text(
                            "Cambios: $diffSummary",
                            style = MaterialTheme.typography.labelSmall,
                            color = KpknSheetTokens.MutedStrong,
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
