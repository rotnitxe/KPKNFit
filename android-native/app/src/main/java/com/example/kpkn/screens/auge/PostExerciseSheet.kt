package com.example.kpkn.screens.auge

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * PostExerciseSheet — Optional per-exercise feedback during workout.
 * Captures RPE, technical quality, mood, and discomfort tags.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostExerciseSheet(
    exerciseName: String,
    onDismiss: () -> Unit,
    onSave: (PostExerciseResult) -> Unit,
) {
    var rpe by remember { mutableIntStateOf(7) }
    var technicalQuality by remember { mutableIntStateOf(8) }
    var mood by remember { mutableIntStateOf(3) }
    val selectedTags = remember { mutableStateListOf<String>() }

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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "Feedback del ejercicio",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        exerciseName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar")
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // ─── RPE ──────────────────────────────────────────────────────────

            LabeledSlider(
                label = "RPE",
                value = rpe,
                range = 1..10,
                onChange = { rpe = it },
                lowLabel = "Muy fácil",
                highLabel = "Fallo",
            )

            // ─── Technical quality ────────────────────────────────────────────

            LabeledSlider(
                label = "Calidad técnica",
                value = technicalQuality,
                range = 6..10,
                onChange = { technicalQuality = it },
                lowLabel = "Deficiente",
                highLabel = "Perfecta",
            )

            // ─── Mood ─────────────────────────────────────────────────────────

            LabeledSlider(
                label = "Estado anímico",
                value = mood,
                range = 1..5,
                onChange = { mood = it },
                lowLabel = "Bajo",
                highLabel = "Excelente",
            )

            // ─── Discomfort tags ──────────────────────────────────────────────

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Molestias (opcional)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DISCOMFORT_TAGS.forEach { tag ->
                        FilterChip(
                            selected = selectedTags.contains(tag),
                            onClick = {
                                if (selectedTags.contains(tag)) selectedTags.remove(tag)
                                else selectedTags.add(tag)
                            },
                            label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // ─── Buttons ──────────────────────────────────────────────────────

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Text("Saltar", style = MaterialTheme.typography.labelLarge)
                }
                Button(
                    onClick = {
                        onSave(PostExerciseResult(
                            rpe = rpe,
                            technicalQuality = technicalQuality,
                            mood = mood,
                            discomforts = selectedTags.toList(),
                        ))
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Text("Guardar", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ─── LabeledSlider ────────────────────────────────────────────────────────────

@Composable
private fun LabeledSlider(
    label: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    lowLabel: String,
    highLabel: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text("$value", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = range.last - range.first - 1,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(lowLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(highLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ─── Data types ───────────────────────────────────────────────────────────────

data class PostExerciseResult(
    val rpe: Int,
    val technicalQuality: Int,
    val mood: Int,
    val discomforts: List<String>,
)

private val DISCOMFORT_TAGS = listOf(
    "Dolor lumbar", "Dolor cervical", "Dolor de rodilla",
    "Dolor de hombro", "Dolor de codo", "Dolor de muñeca",
    "Fatiga muscular", "Mareo", "Sin molestias",
)
