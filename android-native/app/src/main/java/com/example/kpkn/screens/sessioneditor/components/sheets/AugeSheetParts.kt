package com.example.kpkn.screens.sessioneditor.components.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.exercises.*
import com.example.kpkn.screens.sessioneditor.SessionEditorAugeAlert
import com.example.kpkn.screens.sessioneditor.SessionEditorAugeStatus
import com.example.kpkn.screens.sessioneditor.SessionEditorAugeCorrectionType

@Composable
internal fun AugeOverviewMetric(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = if (compact) 0.7f else 0.8f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = if (compact) 10.dp else 12.dp, vertical = if (compact) 8.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp),
        ) {
            Text(
                title,
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun AugeSectionTitle(
    title: String,
    subtitle: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun AugeAlertCard(
    alert: SessionEditorAugeAlert,
    onApplyCorrection: (String) -> Unit,
) {
    val accentColor = MaterialTheme.colorScheme.primary
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = accentColor.copy(alpha = 0.08f),
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            accentColor.copy(alpha = 0.18f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(accentColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.TipsAndUpdates,
                        contentDescription = null,
                        tint = accentColor,
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        alert.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        alert.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = accentColor.copy(alpha = 0.12f),
                ) {
                    Text(
                        "Recomendación",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        color = accentColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                    )
                }
            }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    alert.exerciseName?.let { AugeTag(it, accentColor) }
                    alert.muscle?.let { AugeTag(it, accentColor) }
                }

            if (alert.correctionType != null) {
                FilledTonalButton(onClick = { onApplyCorrection(alert.id) }) {
                    Text(augeCorrectionLabel(alert.correctionType), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
internal fun AugeTag(
    label: String,
    accentColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = accentColor.copy(alpha = 0.12f),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = accentColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

internal fun augeStatusLabel(status: SessionEditorAugeStatus): String = when (status) {
    SessionEditorAugeStatus.OPTIMAL -> "Sesión bien calibrada"
    SessionEditorAugeStatus.WARNING -> "Sesión a vigilar"
    SessionEditorAugeStatus.FATIGUING -> "Sesión fatigante"
}

internal fun augeStatusColor(
    status: SessionEditorAugeStatus,
    critical: Boolean = false,
): Color = when {
    critical -> Color(0xFFEA580C)
    status == SessionEditorAugeStatus.OPTIMAL -> Color(0xFF16A34A)
    status == SessionEditorAugeStatus.WARNING -> Color(0xFFF59E0B)
    else -> Color(0xFFEA580C)
}

// (removed source label helpers - no longer displayed)

internal fun augeCorrectionLabel(correctionType: SessionEditorAugeCorrectionType): String = when (correctionType) {
    SessionEditorAugeCorrectionType.REDUCE_SERIES -> "Aplicar recorte de series"
    SessionEditorAugeCorrectionType.REDUCE_RPE -> "Aplicar baja de intensidad"
    SessionEditorAugeCorrectionType.REDUCE_VOLUME_RPE -> "Bajar volumen e intensidad"
    SessionEditorAugeCorrectionType.ADD_SERIES -> "Agregar una serie"
}

internal data class SuggestionGroup(
    val title: String,
    val alerts: List<SessionEditorAugeAlert>,
    val correctionType: SessionEditorAugeCorrectionType?,
)

internal fun groupSuggestionsForSheet(suggestions: List<SessionEditorAugeAlert>): List<SuggestionGroup> {
    if (suggestions.isEmpty()) return emptyList()

    val grouped = suggestions
        .mapIndexed { index, alert -> index to alert }
        .groupBy { (_, alert) ->
            val normalizedTitle = alert.title
                .replace(Regex("\\s+para\\s+.+$", RegexOption.IGNORE_CASE), "")
                .trim()
            "$normalizedTitle|${alert.source}|${alert.correctionType ?: "none"}"
        }

    return grouped.values
        .sortedBy { pairs -> pairs.minOf { it.first } }
        .map { pairs ->
            val alerts = pairs.map { it.second }
            val normalizedTitle = alerts.first().title
                .replace(Regex("\\s+para\\s+.+$", RegexOption.IGNORE_CASE), "")
                .trim()
            val correctionType = alerts.mapNotNull { it.correctionType }.firstOrNull()
            SuggestionGroup(
                title = if (normalizedTitle.isBlank()) alerts.first().title else normalizedTitle,
                alerts = alerts,
                correctionType = correctionType,
            )
        }
}
