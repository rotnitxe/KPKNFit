package com.example.kpkn.screens.workout

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

internal fun activeAutoRegulationForSet(
    autoRegulation: SetAutoRegulation?,
    exerciseId: String,
    setIdx: Int,
): SetAutoRegulation? = autoRegulation?.takeIf {
    it.exerciseId == exerciseId && it.nextSetIdx == setIdx
}

internal fun shouldOfferSuggestedLoad(
    currentWeightText: String,
    suggestion: WeightSuggestion?,
): Boolean {
    val suggested = suggestion?.suggestedWeight?.takeIf { it > 0.0 }?.toTrimmedNumberString() ?: return false
    return currentWeightText != suggested
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorkoutLiveGuidanceCard(
    weightSuggestion: WeightSuggestion?,
    autoRegulation: SetAutoRegulation?,
    coachMessage: CoachMessage?,
    currentWeightText: String,
    onApplySuggestedLoad: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (weightSuggestion == null && autoRegulation == null && coachMessage == null) return

    val accent = coachAccentColor(coachMessage?.severity)
    val shouldShowApply = shouldOfferSuggestedLoad(currentWeightText, weightSuggestion)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = accent.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.24f)),
    ) {
        BoxWithConstraints {
            val isCompactWidth = maxWidth < 360.dp

            Column(
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (isCompactWidth) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = accent)
                            Text(
                                text = "AUGE en vivo",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        coachMessage?.let { message ->
                            AssistChip(onClick = {}, label = { Text(coachSeverityLabel(message.severity)) })
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = accent)
                            Text(
                                text = "AUGE en vivo",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        coachMessage?.let { message ->
                            AssistChip(onClick = {}, label = { Text(coachSeverityLabel(message.severity)) })
                        }
                    }
                }

                weightSuggestion?.let { suggestion ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (isCompactWidth) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = "Carga sugerida",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            text = "${suggestion.suggestedWeight.toTrimmedNumberString()} kg",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Black,
                                        )
                                    }
                                    if (shouldShowApply) {
                                        Button(onClick = { onApplySuggestedLoad(suggestion.suggestedWeight) }, modifier = Modifier.fillMaxWidth()) {
                                            Text("Usar")
                                        }
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = "Carga sugerida",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            text = "${suggestion.suggestedWeight.toTrimmedNumberString()} kg",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Black,
                                        )
                                    }
                                    if (shouldShowApply) {
                                        Button(onClick = { onApplySuggestedLoad(suggestion.suggestedWeight) }) {
                                            Text("Usar")
                                        }
                                    }
                                }
                            }
                            Text(
                                text = suggestion.reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                autoRegulation?.let { regulation ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = accent.copy(alpha = 0.08f),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = accent)
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = buildAutoRegulationHeadline(regulation),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = regulation.reason,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                coachMessage?.let { message ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                AssistChip(onClick = {}, label = { Text(coachSeverityLabel(message.severity)) })
                                message.action?.let { action ->
                                    AssistChip(onClick = {}, label = { Text(coachActionLabel(action)) })
                                }
                            }
                            Text(
                                text = message.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = accent,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = message.body,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun buildAutoRegulationHeadline(regulation: SetAutoRegulation): String {
    val deltaPercent = ((regulation.adjustmentFactor - 1.0) * 100.0).roundToInt()
    return when {
        abs(deltaPercent) <= 1 -> "Ajuste fino para esta serie"
        deltaPercent > 0 -> "AUGE empuja +${deltaPercent}% para esta serie"
        else -> "AUGE baja ${deltaPercent}% para esta serie"
    }
}

@Composable
private fun coachAccentColor(severity: CoachSeverity?): Color = when (severity) {
    CoachSeverity.SUCCESS -> Color(0xFF2E7D32)
    CoachSeverity.WARNING -> Color(0xFFEF6C00)
    CoachSeverity.DANGER -> Color(0xFFC62828)
    CoachSeverity.INFO, null -> MaterialTheme.colorScheme.primary
}

private fun coachSeverityLabel(severity: CoachSeverity): String = when (severity) {
    CoachSeverity.INFO -> "Info"
    CoachSeverity.WARNING -> "Atención"
    CoachSeverity.DANGER -> "Crítico"
    CoachSeverity.SUCCESS -> "Óptimo"
}

private fun coachActionLabel(action: CoachAction): String = when (action) {
    CoachAction.REDUCE_INTENSITY -> "Baja intensidad"
    CoachAction.SKIP_EXERCISE -> "Salta ejercicio"
    CoachAction.EXTEND_REST -> "Más descanso"
    CoachAction.STAY_THE_COURSE -> "Mantener rumbo"
}
