package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.*
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.domain.templates.SessionTemplateCatalogPolicy
import androidx.compose.animation.animateContentSize
import com.example.kpkn.domain.exercises.*
import com.example.kpkn.screens.sessioneditor.formatEditorOneDecimal
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

@Composable
internal fun CompactTemplateCard(
    template: SessionTemplate,
    onApply: () -> Unit,
    exerciseIndex: Map<String, ExerciseMuscleInfo>,
) {
    var expanded by rememberSaveable(template.id) { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = template.emoji.ifBlank { "💪" },
                    fontSize = 20.sp,
                    modifier = Modifier.padding(end = 10.dp)
                )
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = template.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        template.estimatedDurationMinutes?.let {
                            Text(
                                text = "~${it} min",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text("•", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        Text(
                            text = "${template.session.allExercises().size} ej.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text("•", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outlineVariant)
                        val diffText = when (template.difficulty) {
                            com.example.kpkn.data.splits.Difficulty.PRINCIPIANTE -> "Principiante"
                            com.example.kpkn.data.splits.Difficulty.INTERMEDIO -> "Intermedio"
                            com.example.kpkn.data.splits.Difficulty.AVANZADO -> "Avanzado"
                        }
                        val diffColor = when (template.difficulty) {
                            com.example.kpkn.data.splits.Difficulty.PRINCIPIANTE -> Color(0xFF66BB6A)
                            com.example.kpkn.data.splits.Difficulty.INTERMEDIO -> Color(0xFFFFA726)
                            com.example.kpkn.data.splits.Difficulty.AVANZADO -> Color(0xFFEF5350)
                        }
                        Text(
                            text = diffText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = diffColor
                        )
                    }
                }
                
                Spacer(Modifier.width(6.dp))

                FilledTonalButton(
                    onClick = onApply,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Aplicar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.width(4.dp))

                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Colapsar" else "Expandir",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(visible = expanded) {
                TemplateExpandedDetails(template, exerciseIndex)
            }
        }
    }
}

@Composable
internal fun TemplateExpandedDetails(
    template: SessionTemplate,
    exerciseIndex: Map<String, ExerciseMuscleInfo>
) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (template.description.isNotBlank()) {
            Text(
                text = template.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Ejercicios incluidos:",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            template.session.allExercises().forEachIndexed { idx, ex ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${idx + 1}. ${ex.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    val setsCount = ex.sets.size
                    SuggestionChip(
                        onClick = {},
                        label = { Text("$setsCount ${if (setsCount == 1) "serie" else "series"}", fontSize = 10.sp) },
                        modifier = Modifier.height(22.dp)
                    )
                }
            }
        }

        val estimatedVol = remember(template, exerciseIndex) {
            SessionTemplateCatalogPolicy.calculateSessionMuscleVolume(template.session, exerciseIndex)
        }

        val drain = remember(template, exerciseIndex) {
            SessionTemplateCatalogPolicy.evaluateTemplateRings(template, exerciseIndex)
        }

        val warnings = remember(template, exerciseIndex) {
            val list = mutableListOf<String>()
            val isPl = SessionTemplateCatalogPolicy.isPowerliftingTemplate(template)
            val maxCns = if (isPl) 45 else 35
            val maxMuscular = if (isPl) 50 else 45
            val maxSpinal = if (isPl) 40 else 30

            if (drain.cns > maxCns) list += "SNC elevada (${drain.cns}% > $maxCns%)"
            if (drain.muscular > maxMuscular) list += "Muscular elevada (${drain.muscular}% > $maxMuscular%)"
            if (drain.spinal > maxSpinal) list += "Axial/espinal elevada (${drain.spinal}% > $maxSpinal%)"
            list
        }

        if (estimatedVol.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Volumen estimado por músculo:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    estimatedVol.entries.sortedByDescending { it.value }.forEach { (muscle, sets) ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Text(
                                text = "$muscle: ${formatEditorOneDecimal(sets)} series",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Fatiga SNC: ${drain.cns}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Muscular: ${drain.muscular}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Axial: ${drain.spinal}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (warnings.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                warnings.forEach { warning ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PriorityHigh,
                                contentDescription = "Advertencia",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = warning,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
