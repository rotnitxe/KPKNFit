package com.example.kpkn.screens.sessioneditor.components.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.exercises.*
import com.example.kpkn.screens.sessioneditor.SessionEditorUiState
import com.example.kpkn.screens.sessioneditor.SessionDraftSnapshot
import com.example.kpkn.screens.sessioneditor.SheetHeader
import com.example.kpkn.screens.sessioneditor.DarkEditorSurfaceSoft
import com.example.kpkn.screens.sessioneditor.formatHistoryTimestamp

@Composable
internal fun HistorySheet(
    uiState: SessionEditorUiState,
    onRestoreSnapshot: (SessionDraftSnapshot) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SheetHeader("Historial y borradores", "Restaura snapshots locales o revisa sesiones registradas.")
        Text("Cambios recientes del borrador", style = MaterialTheme.typography.labelLarge)
        if (uiState.localDraftHistory.isEmpty()) {
            Text("Todavía no hay snapshots locales.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            uiState.localDraftHistory.asReversed().forEachIndexed { index, snapshot ->
                val title = snapshot.session.name.ifBlank { "Sesión" }
                val diffSummary = snapshot.changedFields.take(3).joinToString(" · ")
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onRestoreSnapshot(snapshot) },
                    shape = RoundedCornerShape(16.dp),
                    color = DarkEditorSurfaceSoft,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("${index + 1}. $title", fontWeight = FontWeight.Bold)
                        Text(
                            "${formatHistoryTimestamp(snapshot.savedAtMs)} · ${snapshot.reason}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "${snapshot.exerciseCount} ejercicios · ${snapshot.setCount} series · ${snapshot.partCount} grupos",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Cambios: $diffSummary",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        Text("Sesiones registradas", style = MaterialTheme.typography.labelLarge)
        if (uiState.workoutLogs.isEmpty()) {
            Text("Todavía no hay historiales de esta sesión.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                uiState.workoutLogs.forEach { log ->
                    Card {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(log.date.substringBefore("T"), fontWeight = FontWeight.Black)
                            Text("Duración ${log.durationMinutes} min · Volumen ${"%.0f".format(log.totalVolume)}", style = MaterialTheme.typography.bodySmall)
                            Text("Fatiga ${log.fatigueLevel ?: 0}/10 · Estrés ${log.sessionStressScore?.toInt() ?: 0}", style = MaterialTheme.typography.bodySmall)
                            if (!log.discomforts.isNullOrEmpty()) Text("Molestias: ${log.discomforts.joinToString()}", style = MaterialTheme.typography.bodySmall)
                            if (!log.notes.isNullOrBlank()) Text(log.notes, style = MaterialTheme.typography.bodySmall)
                            uiState.feedbackByLogId[log.id]?.let { feedback ->
                                Text("Feedback muscular: ${feedback.muscleFeedback.keys.joinToString()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}
