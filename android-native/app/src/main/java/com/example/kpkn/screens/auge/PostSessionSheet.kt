package com.example.kpkn.screens.auge

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.*
import java.time.LocalDate

/**
 * PostSessionSheet — Post-workout recovery questionnaire.
 * Shows 2-48h after a session. Captures system freshness (1-10) and
 * per-muscle DOMS/strength feedback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostSessionSheet(
    questionnaire: PendingQuestionnaire,
    onDismiss: () -> Unit,
    onSave: (PostSessionFeedback) -> Unit,
) {
    var cnsRecovery by remember { mutableIntStateOf(7) }
    val muscleFeedback = remember {
        mutableStateMapOf<String, MuscleFeedbackState>().also { map ->
            questionnaire.muscleGroups.forEach { muscle ->
                map[muscle] = MuscleFeedbackState()
            }
        }
    }
    val unresolvedDiscomfortIds = remember {
        mutableStateListOf<String>().apply {
            addAll(questionnaire.stillPresentDiscomfortIds)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Header
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "¿Cómo te recuperaste?",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        questionnaire.sessionName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar")
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // ─── System recovery ─────────────────────────────────────────────

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Sistema / frescura general", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text("$cnsRecovery / 10", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = cnsRecovery.toFloat(),
                    onValueChange = { cnsRecovery = it.toInt() },
                    valueRange = 1f..10f,
                    steps = 8,
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Sin energía", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Totalmente recuperado", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // ─── Per-muscle feedback ──────────────────────────────────────────

            if (questionnaire.muscleGroups.isNotEmpty()) {
                Text(
                    "Retroalimentación muscular",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                )

                questionnaire.muscleGroups.forEach { muscle ->
                    val state = muscleFeedback[muscle] ?: MuscleFeedbackState()
                    MuscleFeedbackCard(
                        muscleName = muscle,
                        state = state,
                        onChange = { muscleFeedback[muscle] = it },
                    )
                }
            }

            // ─── Molestias registradas ───
            if (questionnaire.stillPresentDiscomfortIds.isNotEmpty()) {
                Text(
                    "¿Sigues sintiendo estas molestias?",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                )
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    questionnaire.stillPresentDiscomfortIds.forEach { discomfortId ->
                        val label = DISCOMFORT_CATALOG_BY_ID[discomfortId]?.label ?: discomfortId
                        val isSelected = unresolvedDiscomfortIds.contains(discomfortId)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isSelected) {
                                    unresolvedDiscomfortIds.remove(discomfortId)
                                } else {
                                    unresolvedDiscomfortIds.add(discomfortId)
                                }
                            },
                            label = {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            leadingIcon = if (isSelected) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                                selectedLabelColor = MaterialTheme.colorScheme.error,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.error,
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // ─── Save button ──────────────────────────────────────────────────

            Button(
                onClick = {
                    val fb = PostSessionFeedback(
                        logId = questionnaire.logId,
                        date = LocalDate.now().toString(),
                        cnsRecovery = cnsRecovery,
                        muscleFeedback = muscleFeedback.mapValues { (_, s) ->
                            MuscleFeedbackEntry(doms = s.doms, jointPain = s.jointPain, strengthCapacity = s.strengthCapacity)
                        },
                        unresolvedDiscomfortIds = unresolvedDiscomfortIds.toList(),
                    )
                    onSave(fb)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Text(
                    "Guardar feedback",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ─── Per-muscle feedback card ─────────────────────────────────────────────────

data class MuscleFeedbackState(
    val doms: Int = 1,
    val jointPain: Boolean = false,
    val strengthCapacity: Int = 7,
)

@Composable
private fun MuscleFeedbackCard(
    muscleName: String,
    state: MuscleFeedbackState,
    onChange: (MuscleFeedbackState) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(muscleName, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)

            // DOMS selector (1–5)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Agujetas", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..5).forEach { level ->
                        FilterChip(
                            selected = state.doms == level,
                            onClick = { onChange(state.copy(doms = level)) },
                            label = { Text("$level", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(28.dp),
                        )
                    }
                }
            }

            // Strength capacity slider (1-10)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Fuerza percibida", style = MaterialTheme.typography.bodySmall)
                Text("${state.strengthCapacity}/10", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = state.strengthCapacity.toFloat(),
                onValueChange = { onChange(state.copy(strengthCapacity = it.toInt())) },
                valueRange = 1f..10f,
                steps = 8,
            )

            // Joint pain toggle
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Dolor articular", style = MaterialTheme.typography.bodySmall)
                Switch(
                    checked = state.jointPain,
                    onCheckedChange = { onChange(state.copy(jointPain = it)) },
                )
            }
        }
    }
}
