package com.example.kpkn.screens.sessioneditor.components.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Session
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.SessionDraftSnapshot
import com.example.kpkn.screens.sessioneditor.SessionEditorUiState
import com.example.kpkn.screens.sessioneditor.TrainedSessionVersionStore
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.design.KpknEmptyState
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.components.icons.KpknLineIcons
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Hoja «Historial de versiones» (SE7). Una fila por versión, con su fecha larga y, si el modelo lo dice, lo que cambió. «Restaurar» pide
 * confirmación siempre; la versión actual no se puede restaurar y lleva la marca «Actual».
 */
@Composable
internal fun HistorySheet(
    uiState: SessionEditorUiState,
    onRestoreSnapshot: (SessionDraftSnapshot) -> Unit,
) {
    HistoryBody(
        versions = uiState.localDraftHistory.asReversed(),
        currentSession = uiState.session,
        hasUnsavedChanges = uiState.hasUnsavedChanges,
        onRestoreSnapshot = onRestoreSnapshot,
    )
}

/**
 * El contenido del historial sin la hoja. [versions] va de la más nueva a la más antigua. Sin versiones, un estado vacío; con ellas, una
 * fila por versión en un módulo.
 */
@Composable
internal fun HistoryBody(
    versions: List<SessionDraftSnapshot>,
    currentSession: Session?,
    hasUnsavedChanges: Boolean,
    onRestoreSnapshot: (SessionDraftSnapshot) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingRestore by remember { mutableStateOf<SessionDraftSnapshot?>(null) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Historial de versiones", style = KpknType.title, color = WizardColors.text)
        Text(
            text = "Versiones estructurales guardadas al completar un entreno.",
            style = KpknType.caption,
            color = WizardColors.textMuted,
        )
        if (versions.isEmpty()) {
            KpknEmptyState(
                icon = KpknLineIcons.History,
                title = "Aún no hay versiones guardadas",
                body = "Se guardan al terminar un entreno si la estructura cambió.",
            )
        } else {
            KpknModule {
                versions.forEachIndexed { index, snapshot ->
                    if (index > 0) {
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
                    }
                    val isCurrent = currentSession != null &&
                        TrainedSessionVersionStore.structuralEquals(currentSession, snapshot.session)
                    HistoryRow(
                        snapshot = snapshot,
                        isCurrent = isCurrent,
                        onRestore = { pendingRestore = snapshot },
                    )
                }
            }
        }
    }

    pendingRestore?.let { snapshot ->
        KpknAlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = "¿Restaurar esta versión?",
            text = if (hasUnsavedChanges) {
                "Hay cambios sin guardar. Al restaurar se reemplaza el borrador actual."
            } else {
                "Al restaurar se reemplaza el borrador actual por la versión del ${sessionVersionStamp(snapshot.savedAtMs)}."
            },
            confirmLabel = "Restaurar",
            onConfirm = {
                pendingRestore = null
                onRestoreSnapshot(snapshot)
            },
            dismissLabel = "Cancelar",
            onDismiss = { pendingRestore = null },
        )
    }
}

/** Una versión: la fecha larga en `bodyStrong`, las cifras y, si hay, los cambios en `caption`, y «Restaurar» a la derecha. */
@Composable
private fun HistoryRow(
    snapshot: SessionDraftSnapshot,
    isCurrent: Boolean,
    onRestore: () -> Unit,
) {
    val changes = snapshot.changedFields.take(3).joinToString(" · ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = sessionVersionStamp(snapshot.savedAtMs),
                style = KpknType.bodyStrong,
                color = WizardColors.text,
            )
            Text(
                text = "${snapshot.exerciseCount} ejercicios · ${snapshot.setCount} series · ${snapshot.partCount} partes",
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
            if (changes.isNotBlank()) {
                Text(
                    text = "Cambios: $changes",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                )
            }
        }
        if (isCurrent) {
            Text(text = "Actual", style = KpknType.caption, color = WizardColors.textMuted)
        } else {
            KpknTextAction(text = "Restaurar", onClick = onRestore)
        }
    }
}

/** «martes 13 de octubre · 18:40»: la fecha larga de una versión, en la zona horaria del móvil. */
internal fun sessionVersionStamp(savedAtMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val at = Instant.ofEpochMilli(savedAtMs).atZone(zone)
    val day = at.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es-ES")))
    return "$day · ${at.format(DateTimeFormatter.ofPattern("HH:mm"))}"
}
