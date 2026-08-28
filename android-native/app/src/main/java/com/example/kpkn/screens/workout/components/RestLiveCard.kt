package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.SetOutcomeV2
import com.example.kpkn.screens.sessioneditor.contentOn
import com.example.kpkn.screens.workout.PendingRestSuggestion
import com.example.kpkn.screens.workout.RestTimerKind
import com.example.kpkn.screens.workout.WorkoutRestModalState
import java.util.Locale

/**
 * Working-set rest as a live pager card (minimized overlay view).
 * Mirrors key context from [RestTimerOverlay] without full-screen chrome.
 */
@Composable
internal fun RestLiveCard(
    remainingSeconds: Int,
    totalSeconds: Int,
    sessionAccentColor: Color,
    restState: WorkoutRestModalState?,
    pendingRestSuggestion: PendingRestSuggestion?,
    lastSetOutcome: SetOutcomeV2?,
    lastCompletedSet: CompletedSet?,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onSkip: () -> Unit,
    onUseAdaptive: (() -> Unit)?,
    onExpand: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val safeTotal = totalSeconds.coerceAtLeast(1)
    val progress = (remainingSeconds.toFloat() / safeTotal).coerceIn(0f, 1f)
    val mm = remainingSeconds / 60
    val ss = remainingSeconds % 60
    val clock = String.format(Locale.US, "%d:%02d", mm, ss)
    val kindLabel = when (restState?.kind) {
        RestTimerKind.SUPERSET_INTRA -> "SUPERSERIE"
        RestTimerKind.SUPERSET_ROUND -> "SIGUIENTE RONDA"
        RestTimerKind.WARMUP -> "APROXIMACIÓN"
        RestTimerKind.BETWEEN_SIDES -> "ENTRE LADOS"
        RestTimerKind.STANDARD, null -> "DESCANSO"
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        shape = WorkoutUiTokens.CardShape,
        color = Color(0xFF121820),
        border = BorderStroke(1.dp, sessionAccentColor.copy(alpha = 0.35f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (onExpand != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(onClick = onExpand, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Expandir descanso",
                            tint = sessionAccentColor,
                        )
                    }
                }
            }

            Text(
                text = kindLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                color = sessionAccentColor,
            )
            if (!restState?.exerciseName.isNullOrBlank()) {
                Text(
                    text = restState!!.exerciseName,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Text(
                text = clock,
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 44.sp),
                fontWeight = FontWeight.Black,
                color = Color.White,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                shape = RoundedCornerShape(999.dp),
                color = Color.White.copy(alpha = 0.12f),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(8.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = sessionAccentColor,
                    content = {},
                )
            }

            RestLiveLastSetSummary(
                lastCompletedSet = lastCompletedSet ?: pendingRestSuggestion?.lastSet,
                lastSetOutcome = lastSetOutcome,
                sessionAccentColor = sessionAccentColor,
            )

            if (pendingRestSuggestion != null && onUseAdaptive != null) {
                OutlinedButton(
                    onClick = onUseAdaptive,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp),
                    shape = WorkoutUiTokens.InnerCardShape,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = sessionAccentColor),
                    border = BorderStroke(1.dp, sessionAccentColor.copy(alpha = 0.4f)),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = "Descanso sugerido",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Black,
                            )
                        }
                        Text(
                            text = formatRestClock(pendingRestSuggestion.adaptiveSeconds),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.72f),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onDecrease,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("−15s", fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.85f))
                }
                Button(
                    onClick = onSkip,
                    modifier = Modifier.weight(1.4f).height(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = sessionAccentColor,
                        contentColor = contentOn(sessionAccentColor),
                    ),
                ) {
                    Text("Saltar", fontWeight = FontWeight.Black)
                }
                TextButton(
                    onClick = onIncrease,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("+15s", fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.85f))
                }
            }
        }
    }
}

@Composable
private fun RestLiveLastSetSummary(
    lastCompletedSet: CompletedSet?,
    lastSetOutcome: SetOutcomeV2?,
    sessionAccentColor: Color,
) {
    val set = lastCompletedSet ?: return
    val loadStr = if (set.weight > 0.0) "${formatRestWeight(set.weight)} kg" else "Peso corporal"
    val valueStr = if (set.timeSeconds != null) "${set.timeSeconds}s" else "${set.reps} reps"
    val outcome = lastSetOutcome ?: set.setOutcomeV2

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = WorkoutUiTokens.InnerCardShape,
        color = Color.White.copy(alpha = 0.04f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Último set",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.48f),
                )
                Text(
                    text = "$loadStr × $valueStr",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.86f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (outcome?.isContextPr == true || outcome?.isGlobalPr == true) {
                RestLiveTinyBadge("Récord", Color(0xFFFFD740))
            }
            if (set.isFailure || set.isFailedSet) {
                RestLiveTinyBadge("Fallo", Color(0xFFFF5252))
            }
        }
    }
}

@Composable
private fun RestLiveTinyBadge(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.18f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f)),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            fontWeight = FontWeight.Black,
            color = color,
        )
    }
}

private fun formatRestClock(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    return String.format(Locale.US, "%d:%02d", safe / 60, safe % 60)
}

private fun formatRestWeight(weight: Double): String {
    return if (weight % 1.0 == 0.0) weight.toInt().toString() else String.format(Locale.US, "%.1f", weight)
}
