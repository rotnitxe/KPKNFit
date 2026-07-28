package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.example.kpkn.ui.components.KpknSheetTokens
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.data.splits.Difficulty
import com.example.kpkn.domain.templates.RingBudgetPolicy
import com.example.kpkn.domain.templates.SessionTemplateCatalogPolicy
import com.example.kpkn.domain.templates.SessionTemplateFacets
import com.example.kpkn.domain.templates.TemplateCatalogFilterLogic
import com.example.kpkn.screens.sessioneditor.formatEditorOneDecimal

@Composable
internal fun CompactTemplateCard(
    template: SessionTemplate,
    onApply: () -> Unit,
    exerciseIndex: Map<String, ExerciseMuscleInfo>,
    glassDark: Boolean = false,
    facets: SessionTemplateFacets? = null,
) {
    var expanded by rememberSaveable(template.id) { mutableStateOf(false) }
    val titleColor = if (glassDark) Color.White else MaterialTheme.colorScheme.onSurface
    val mutedColor = if (glassDark) Color.White.copy(alpha = 0.55f) else MaterialTheme.colorScheme.onSurfaceVariant
    val rowBg = if (glassDark) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)

    val durationMin = facets?.realDurationMinutes ?: template.estimatedDurationMinutes
    val totalSets = facets?.totalSets ?: template.session.allExercises().sumOf { it.sets.size }
    val exerciseCount = template.session.allExercises().size
    val primaryMuscles = facets?.primaryMuscles?.sortedBy { it.lowercase() }.orEmpty()
    val shortCopy = template.shortDescription.ifBlank {
        template.description.take(120).ifBlank { template.muscleGroupsSummary }
    }
    val diffText = TemplateCatalogFilterLogic.difficultyLabel(template.difficulty)
    val diffColor = when (template.difficulty) {
        Difficulty.PRINCIPIANTE -> Color(0xFF66BB6A)
        Difficulty.INTERMEDIO -> Color(0xFFFFA726)
        Difficulty.AVANZADO -> Color(0xFFEF5350)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (glassDark) 10.dp else 16.dp))
            .background(rowBg)
            .then(if (expanded) Modifier.animateContentSize() else Modifier)
            .semantics {
                contentDescription = buildString {
                    append(template.name)
                    durationMin?.let { append(", $it minutos") }
                    append(", $exerciseCount ejercicios, $totalSets series, $diffText")
                    if (primaryMuscles.isNotEmpty()) {
                        append(", músculos: ${primaryMuscles.joinToString()}")
                    }
                }
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable { expanded = !expanded }
                .padding(horizontal = if (glassDark) 8.dp else 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = template.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = titleColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (shortCopy.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = shortCopy,
                        style = MaterialTheme.typography.labelSmall,
                        color = mutedColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    durationMin?.let {
                        Text(text = "$it min", style = MaterialTheme.typography.labelSmall, color = mutedColor)
                        Text("·", style = MaterialTheme.typography.labelSmall, color = mutedColor)
                    }
                    Text(
                        text = "$exerciseCount ej.",
                        style = MaterialTheme.typography.labelSmall,
                        color = mutedColor,
                    )
                    Text("·", style = MaterialTheme.typography.labelSmall, color = mutedColor)
                    Text(
                        text = "$totalSets series",
                        style = MaterialTheme.typography.labelSmall,
                        color = mutedColor,
                    )
                    Text("·", style = MaterialTheme.typography.labelSmall, color = mutedColor)
                    Text(
                        text = diffText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = diffColor,
                    )
                }
                if (primaryMuscles.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Enfoque: ${primaryMuscles.take(4).joinToString(" · ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (glassDark) Color.White.copy(alpha = 0.72f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
            if (glassDark) {
                Text(
                    text = "Aplicar",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = KpknSheetTokens.ControlLabel,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(KpknSheetTokens.ControlFill)
                        .clickable(onClick = onApply)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .semantics { contentDescription = "Aplicar plantilla ${template.name}" },
                )
            } else {
                FilledTonalButton(
                    onClick = onApply,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier
                        .heightIn(min = 36.dp)
                        .semantics { contentDescription = "Aplicar plantilla ${template.name}" },
                ) {
                    Text("Aplicar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Colapsar detalles" else "Expandir detalles",
                tint = mutedColor,
                modifier = Modifier.size(22.dp),
            )
        }
        AnimatedVisibility(visible = expanded) {
            TemplateExpandedDetails(
                template = template,
                exerciseIndex = exerciseIndex,
                glassDark = glassDark,
                facets = facets,
                advanced = true,
            )
        }
        if (glassDark) {
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        }
    }
}

@Composable
private fun AdvancedTemplateMetaRow(
    facets: SessionTemplateFacets,
    mutedColor: Color,
    glassDark: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val rpe = facets.averageTargetRpe

        if (rpe != null) {
            Text(
                text = "RPE ${formatEditorOneDecimal(rpe)}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = mutedColor,
            )
        }
        CompactFatigueMeters(
            energy = facets.drain.cns,
            muscular = facets.drain.muscular,
            spinal = facets.drain.spinal,
            glassDark = glassDark,
        )
    }
}

@Composable
internal fun CompactFatigueMeters(
    energy: Int,
    muscular: Int,
    spinal: Int,
    glassDark: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CompactFatigueChip(
            label = "Energía",
            value = energy.coerceIn(0, 100),
            color = Color(0xFF448AFF),
            glassDark = glassDark,
            modifier = Modifier.weight(1f),
        )
        CompactFatigueChip(
            label = "Muscular",
            value = muscular.coerceIn(0, 100),
            color = Color(0xFFFF5252),
            glassDark = glassDark,
            modifier = Modifier.weight(1f),
        )
        CompactFatigueChip(
            label = "Columna",
            value = spinal.coerceIn(0, 100),
            color = Color(0xFFFFAB40),
            glassDark = glassDark,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CompactFatigueChip(
    label: String,
    value: Int,
    color: Color,
    glassDark: Boolean,
    modifier: Modifier = Modifier,
) {
    val track = if (glassDark) Color.White.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val textColor = if (glassDark) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurface
    Column(
        modifier = modifier.semantics {
            contentDescription = "$label $value por ciento"
        },
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = "$label $value%",
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 10.sp,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(track),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(value / 100f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(color),
            )
        }
    }
}

@Composable
internal fun TemplateExpandedDetails(
    template: SessionTemplate,
    exerciseIndex: Map<String, ExerciseMuscleInfo>,
    glassDark: Boolean = false,
    facets: SessionTemplateFacets? = null,
    advanced: Boolean = false,
) {
    val titleColor = if (glassDark) Color.White else MaterialTheme.colorScheme.onSurface
    val mutedColor = if (glassDark) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant
    val dividerColor = if (glassDark) Color.White.copy(alpha = 0.12f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    val estimatedVol = remember(template, exerciseIndex) {
        SessionTemplateCatalogPolicy.calculateSessionMuscleVolume(template.session, exerciseIndex)
    }

    val drain = remember(facets, template, exerciseIndex) {
        val settings = com.example.kpkn.data.repository.ProgramRepository.getInstance().settings.value
        facets?.drain ?: SessionTemplateCatalogPolicy.evaluateTemplateRings(template, exerciseIndex, settings)
    }

    val warnings = remember(template, drain, advanced) {
        if (!advanced) emptyList()
        else {
            val list = mutableListOf<String>()
            val isPl = SessionTemplateCatalogPolicy.isPowerliftingTemplate(template)
            val caps = RingBudgetPolicy.sessionWarningCaps(isPl)
            if (drain.cns > caps.cns) list += "SNC elevada (${drain.cns}% > ${caps.cns}%)"
            if (drain.muscular > caps.muscular) list += "Muscular elevada (${drain.muscular}% > ${caps.muscular}%)"
            if (drain.spinal > caps.spinal) list += "Axial/espinal elevada (${drain.spinal}% > ${caps.spinal}%)"
            list
        }
    }

    HorizontalDivider(color = dividerColor)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (template.description.isNotBlank()) {
            Text(
                text = template.description,
                style = MaterialTheme.typography.bodySmall,
                color = mutedColor,
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Ejercicios incluidos",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = titleColor,
            )
            template.session.allExercises().forEachIndexed { idx, ex ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${idx + 1}. ${ex.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = mutedColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    val setsCount = ex.sets.size
                    Text(
                        text = "$setsCount ${if (setsCount == 1) "serie" else "series"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (glassDark) Color.White.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        if (estimatedVol.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Volumen estimado por músculo",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = titleColor,
                )

                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    estimatedVol.entries.sortedByDescending { it.value }.forEach { (muscle, sets) ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (glassDark) {
                                Color.White.copy(alpha = 0.12f)
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                            },
                            modifier = Modifier.height(24.dp),
                        ) {
                            Text(
                                text = "$muscle: ${formatEditorOneDecimal(sets)} series",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                color = if (glassDark) {
                                    Color.White.copy(alpha = 0.85f)
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                },
                            )
                        }
                    }
                }
            }
        }

        if (advanced) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Fatiga estimada (AUGE)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = titleColor,
                )
                CompactFatigueMeters(
                    energy = drain.cns,
                    muscular = drain.muscular,
                    spinal = drain.spinal,
                    glassDark = glassDark,
                )
                facets?.averageTargetRpe?.let { rpe ->
                    Text(
                        text = "RPE medio objetivo: ${formatEditorOneDecimal(rpe)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = mutedColor,
                    )
                }
                if (facets != null && facets.primaryMuscles.isNotEmpty()) {
                    Text(
                        text = "Enfoque: ${facets.primaryMuscles.sortedBy { it.lowercase() }.joinToString(" · ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = mutedColor,
                    )
                }

                if (warnings.isNotEmpty()) {
                    warnings.forEach { warning ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PriorityHigh,
                                    contentDescription = "Advertencia",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(12.dp),
                                )
                                Text(
                                    text = warning,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
