package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.exercises.catalogv2.canonicalMuscleKnowledgeForVolumeLabel
import com.example.kpkn.data.exercises.catalogv2.canonicalPatternKnowledge
import com.example.kpkn.data.exercises.catalogv2.decodeCatalogRichMetadata
import com.example.kpkn.domain.exercises.resolvePrimaryMuscleLabel
import com.example.kpkn.ui.components.CanonicalKnowledgeOverlay
import com.example.kpkn.ui.components.KpknSheetTokens
import com.example.kpkn.ui.components.kpknSheetWhiteTonalButtonColors

internal fun shouldShowExerciseAspectChips(
    hasAspects: Boolean,
    isSelected: Boolean,
    hasHighlightedOptions: Boolean,
    showAspects: Boolean,
): Boolean = hasAspects && (isSelected || hasHighlightedOptions || showAspects)

@Composable
internal fun ExercisePickerCompactCard(
    info: ExerciseMuscleInfo,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onInfo: () -> Unit,
    onOpenVariantFlow: (() -> Unit)? = null,
) {
    val bgAlpha = if (isSelected) 0.40f else 0.24f
    Surface(
        modifier = Modifier
            .width(220.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onSelect() },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = bgAlpha),
        contentColor = KpknSheetTokens.Body,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    info.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = KpknSheetTokens.Body,
                )
                if (onOpenVariantFlow != null && !info.variantGroupId.isNullOrBlank()) {
                    IconButton(onClick = onOpenVariantFlow, modifier = Modifier.size(26.dp)) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = "Configuración avanzada",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                if (isSelected) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Seleccionado",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(onClick = onInfo, modifier = Modifier.size(26.dp)) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Ver detalle",
                        tint = KpknSheetTokens.MutedStrong,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Text(
                listOfNotNull(resolvePrimaryMuscleLabel(info), info.equipment).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = KpknSheetTokens.MutedStrong,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun ExercisePickerDetailedCard(
    info: ExerciseMuscleInfo,
    isSelected: Boolean,
    onSelect: () -> Unit,
    isInfoExpanded: Boolean = false,
    onToggleInfo: () -> Unit,
    onOpenExerciseDetail: (() -> Unit)? = null,
    onOpenVariantFlow: (() -> Unit)? = null,
    selectedAspects: Map<String, String> = emptyMap(),
    highlightedAspectOptions: Map<String, String> = emptyMap(),
    onAspectsChange: ((Map<String, String>) -> Unit)? = null,
    showAspects: Boolean = false,
) {
    val primaryMuscle = resolvePrimaryMuscleLabel(info)
    val bgAlpha = if (isSelected) 0.44f else 0.28f
    val hasAspects = !info.catalogOptionAxes.isNullOrEmpty()
    var chipDescription by remember(info.id) { mutableStateOf<String?>(null) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = bgAlpha),
            contentColor = KpknSheetTokens.Body,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelect() }
                        .padding(vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        info.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = KpknSheetTokens.Body,
                    )
                    Text(
                        listOfNotNull(primaryMuscle, info.equipment, info.type).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = KpknSheetTokens.MutedStrong,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (onOpenVariantFlow != null && !info.variantGroupId.isNullOrBlank() && !hasAspects) {
                    IconButton(onClick = onOpenVariantFlow) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = "Configuración avanzada",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (isSelected) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Seleccionado",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
                IconButton(onClick = onToggleInfo) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = if (isInfoExpanded) {
                            "Ocultar información de " + info.name
                        } else {
                            "Mostrar información de " + info.name
                        },
                        tint = if (isInfoExpanded) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            KpknSheetTokens.MutedStrong
                        },
                    )
                }
            }

            ExercisePickerCanonicalSummary(info)

            AnimatedVisibility(
                visible = isInfoExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                ExercisePickerInlineInfo(
                    info = info,
                    selectedAspects = selectedAspects,
                    onOpenExerciseDetail = onOpenExerciseDetail,
                )
            }

            if (onAspectsChange != null && shouldShowExerciseAspectChips(
                    hasAspects = hasAspects,
                    isSelected = isSelected,
                    hasHighlightedOptions = highlightedAspectOptions.isNotEmpty(),
                    showAspects = showAspects,
                )
            ) {
                ExerciseAspectChipsInline(
                    exercise = info,
                    selectedAspects = selectedAspects.ifEmpty { defaultAspectSelection(info) },
                    onAspectsChange = onAspectsChange,
                    highlightedOptionIds = highlightedAspectOptions.values.toSet(),
                    onOptionInfo = { aspect, option ->
                        chipDescription = "Configuración exacta: ${option.name}."
                    },
                    modifier = Modifier.padding(top = 4.dp),
                )
                chipDescription?.let { description ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    ) {
                        Text(
                            description,
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = KpknSheetTokens.MutedStrong,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExercisePickerInlineInfo(
    info: ExerciseMuscleInfo,
    selectedAspects: Map<String, String>,
    onOpenExerciseDetail: (() -> Unit)?,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        contentColor = KpknSheetTokens.Body,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                "Información del ejercicio",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = KpknSheetTokens.Body,
            )
            Text(
                "El aporte muscular se expresa por cada serie planificada; pulsa un músculo arriba para ver su definición canónica cuando exista.",
                style = MaterialTheme.typography.labelSmall,
                color = KpknSheetTokens.MutedStrong,
            )

            if (onOpenExerciseDetail != null) {
                TextButton(
                    onClick = onOpenExerciseDetail,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Ficha completa")
                }
            }
        }
    }
}

@Composable
private fun ExercisePickerCanonicalSummary(info: ExerciseMuscleInfo) {
    val rich = remember(info.id, info.catalogRichMetadataJson) { info.decodeCatalogRichMetadata() }
    val contributions = remember(info) { oneSeriesVolumeContributions(info) }
    val pattern = rich?.biomechanics?.movementPatternId?.let(::canonicalPatternKnowledge)
    if (contributions.isEmpty() && pattern == null) return
    var selected by remember(info.id) { mutableStateOf<com.example.kpkn.data.exercises.catalogv2.CanonicalKnowledge?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        pattern?.let { knowledge ->
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable { selected = knowledge },
                shape = RoundedCornerShape(999.dp),
                color = Color.White.copy(alpha = 0.08f),
                contentColor = KpknSheetTokens.Body,
            ) {
                Text(
                    "Patrón · ${knowledge.name}",
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (contributions.isNotEmpty()) {
            Text(
                "Músculos · aporte por 1 serie",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = KpknSheetTokens.Body,
            )
            contributions.take(6).forEach { contribution ->
                val knowledge = canonicalMuscleKnowledgeForVolumeLabel(contribution.muscle)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .then(if (knowledge != null) Modifier.clickable { selected = knowledge } else Modifier),
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White.copy(alpha = 0.045f),
                    contentColor = KpknSheetTokens.Body,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                contribution.muscle,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                compactRoleLabel(contribution.role),
                                style = MaterialTheme.typography.labelSmall,
                                color = KpknSheetTokens.MutedStrong,
                            )
                        }
                        Text(
                            formatSeriesEquivalent(contribution.seriesEquivalent) + " · " +
                                formatVolumePercent(contribution.seriesEquivalent),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = KpknSheetTokens.MutedStrong,
                        )
                    }
                }
            }
        }
        selected?.let { knowledge ->
            CanonicalKnowledgeOverlay(
                knowledge = knowledge,
                onDismiss = { selected = null },
            )
        }
    }
}

private fun compactRoleLabel(role: MuscleRole): String = when (role) {
    MuscleRole.PRIMARY -> "Principal"
    MuscleRole.SECONDARY -> "Secundario"
    MuscleRole.STABILIZER -> "Estabilizador"
    MuscleRole.NEUTRALIZER -> "Neutralizador"
}

@Composable
internal fun ExercisePickerSelectionDock(
    selectedExercises: List<ExerciseMuscleInfo>,
    onRemove: (String) -> Unit,
    onCreateSuperset: ((List<ExerciseMuscleInfo>) -> Unit)?,
    onClearExerciseSelection: () -> Unit,
    onMultiSelect: (List<ExerciseMuscleInfo>) -> List<String>,
) {
    var showSelectedList by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = KpknSheetTokens.Panel,
        contentColor = KpknSheetTokens.Body,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showSelectedList = !showSelectedList },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${selectedExercises.size} seleccionados",
                    color = KpknSheetTokens.Body,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Icon(
                    if (showSelectedList) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    null,
                    tint = KpknSheetTokens.MutedStrong,
                )
            }
            if (showSelectedList) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    selectedExercises.forEach { info ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                info.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = KpknSheetTokens.Body,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            IconButton(
                                onClick = { onRemove(info.id) },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    "Quitar",
                                    tint = KpknSheetTokens.MutedStrong,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selectedExercises.size >= 2 && onCreateSuperset != null) {
                    Button(
                        onClick = {
                            onCreateSuperset(selectedExercises)
                            onClearExerciseSelection()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KpknSheetTokens.ControlFill,
                            contentColor = KpknSheetTokens.ControlLabel,
                        ),
                    ) {
                        Text(
                            "Crear superserie",
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
                FilledTonalButton(
                    onClick = {
                        onMultiSelect(selectedExercises)
                        onClearExerciseSelection()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = kpknSheetWhiteTonalButtonColors(),
                ) {
                    Text(
                        "Agregar ${selectedExercises.size}",
                        maxLines = 1,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }
    }
}
