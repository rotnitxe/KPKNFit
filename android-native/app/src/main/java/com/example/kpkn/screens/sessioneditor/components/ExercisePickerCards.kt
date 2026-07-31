package com.example.kpkn.screens.sessioneditor.components

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
import com.example.kpkn.domain.exercises.resolvePrimaryMuscleLabel
import com.example.kpkn.ui.components.KpknSheetTokens
import com.example.kpkn.ui.components.kpknSheetWhiteTonalButtonColors

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
    onInfo: () -> Unit,
    onOpenVariantFlow: (() -> Unit)? = null,
    selectedAspects: Map<String, String> = emptyMap(),
    onAspectsChange: ((Map<String, String>) -> Unit)? = null,
) {
    val primaryMuscle = resolvePrimaryMuscleLabel(info)
    val bgAlpha = if (isSelected) 0.44f else 0.28f
    val hasAspects = !info.technicalAspects.isNullOrEmpty()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
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
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                // Legacy Tune only if group exists and no inline aspects yet
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
                IconButton(onClick = onInfo) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Ver detalle",
                        tint = KpknSheetTokens.MutedStrong,
                    )
                }
            }

            if (!info.description.isNullOrBlank() && !(isSelected && hasAspects)) {
                Text(
                    info.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = KpknSheetTokens.MutedStrong,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (isSelected && hasAspects && onAspectsChange != null) {
                ExerciseAspectChipsInline(
                    exercise = info,
                    selectedAspects = selectedAspects.ifEmpty { defaultAspectSelection(info) },
                    onAspectsChange = onAspectsChange,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
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
