package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.domain.sessionassistant.SeriesTechnique
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.workout.STEPPER_CHROME_SIZE_DP
import com.example.kpkn.screens.workout.SeriesTypeTarget
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType

/**
 * «Tipo de serie» (B10, LR1c2): la piel de LR1a dentro del `KpknSheet`. Las series son círculos (hechas en `ok`, elegidas en tinta, el
 * resto en `inset`), las opciones son `KpknChip` y los botones, píldoras. La selección y lo que se aplica no cambian.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SeriesTypeSheet(
    exercise: Exercise,
    target: SeriesTypeTarget,
    completedSetIndices: Set<Int>,
    onDismiss: () -> Unit,
    onApply: (SeriesTypeTarget, SeriesTechnique) -> Unit,
) {
    var selectedTechnique by remember(target) { mutableStateOf(SeriesTechnique.NORMAL) }
    val initialIndices = remember(target, exercise.sets.size) {
        val fromTarget = target.selectedSetIndices.filter { it in exercise.sets.indices }.toSet()
        if (fromTarget.isNotEmpty()) fromTarget
        else (target.fromSetIdx..target.toSetIdx).filter { it in exercise.sets.indices }.toSet()
            .ifEmpty { setOf(target.fromSetIdx.coerceIn(0, (exercise.sets.size - 1).coerceAtLeast(0))) }
    }
    var selectedIndices by remember(target) { mutableStateOf(initialIndices) }
    var multiMarkStarted by remember(target) { mutableStateOf(initialIndices.size > 1) }
    var anchorIdx by remember(target) {
        mutableStateOf(target.fromSetIdx.coerceIn(0, (exercise.sets.size - 1).coerceAtLeast(0)))
    }
    var includeAllFollowing by remember(target) {
        mutableStateOf(target.toSetIdx >= exercise.sets.lastIndex && selectedIndices.size > 1)
    }

    LaunchedEffect(target, exercise.id) {
        val first = exercise.sets.getOrNull(anchorIdx) ?: exercise.sets.getOrNull(target.fromSetIdx)
        selectedTechnique = when {
            first?.isDropSet == true -> SeriesTechnique.DROPSET
            first?.isRestPause == true -> SeriesTechnique.REST_PAUSE
            else -> SeriesTechnique.NORMAL
        }
    }

    val lastIndex = exercise.sets.lastIndex
    val fromEndSet = if (lastIndex >= 0) (anchorIdx..lastIndex).toSet() else emptySet()
    val rangeChipSelected = includeAllFollowing && selectedIndices.containsAll(fromEndSet)
    val soloChipSelected = !multiMarkStarted && selectedIndices == setOf(anchorIdx)

    fun toggleIndex(idx: Int) {
        if (idx in completedSetIndices) return
        val next = if (idx in selectedIndices) {
            val remaining = selectedIndices - idx
            if (remaining.isEmpty()) selectedIndices else remaining
        } else {
            selectedIndices + idx
        }
        selectedIndices = next
        multiMarkStarted = next.size > 1
        includeAllFollowing = false
        anchorIdx = idx
    }

    KpknSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = exercise.name,
                style = KpknType.dialogTitle,
                color = WizardColors.text,
                maxLines = 2,
            )
            Text(
                text = "Elige qué series cambiar. Las completadas no se tocan. Mantén pulsado un nodo para marcar varias.",
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                exercise.sets.forEachIndexed { idx, set ->
                    val isCompleted = idx in completedSetIndices
                    val isSelected = idx in selectedIndices
                    val label = "S${idx + 1}"
                    val fill = when {
                        isCompleted -> WizardColors.done
                        isSelected -> WizardColors.text
                        else -> KpknSurface.inset
                    }
                    val edge = when {
                        isCompleted || isSelected -> Color.Transparent
                        set.isDropSet || set.isRestPause -> WizardColors.text.copy(alpha = 0.40f)
                        else -> Color.White.copy(alpha = 0.12f)
                    }
                    Box(
                        modifier = Modifier
                            .size(STEPPER_CHROME_SIZE_DP.dp)
                            .clip(CircleShape)
                            .background(fill, CircleShape)
                            .border(1.dp, edge, CircleShape)
                            .combinedClickable(
                                enabled = !isCompleted,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    if (multiMarkStarted) {
                                        toggleIndex(idx)
                                    } else {
                                        selectedIndices = setOf(idx)
                                        anchorIdx = idx
                                        includeAllFollowing = false
                                    }
                                },
                                onLongClick = { toggleIndex(idx) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isCompleted) {
                            Icon(
                                KpknLineIcons.Check,
                                contentDescription = "Completada",
                                modifier = Modifier.size(14.dp),
                                tint = WizardColors.ctaContent,
                            )
                        } else {
                            Text(
                                text = label,
                                style = KpknType.chip,
                                color = if (isSelected) WizardColors.ctaContent else WizardColors.text.copy(alpha = 0.9f),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SeriesChoiceChip(
                    text = "Desde S${anchorIdx + 1} hasta el final",
                    selected = rangeChipSelected,
                    onClick = {
                        includeAllFollowing = true
                        multiMarkStarted = true
                        selectedIndices = fromEndSet
                    },
                    modifier = Modifier.weight(1.6f),
                )
                SeriesChoiceChip(
                    text = "Solo S${anchorIdx + 1}",
                    selected = soloChipSelected,
                    onClick = {
                        includeAllFollowing = false
                        multiMarkStarted = false
                        selectedIndices = setOf(anchorIdx)
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            Text(
                text = "Tipo de serie".uppercase(),
                style = KpknType.eyebrow,
                color = WizardColors.textMuted,
            )
            val onlyFirstSetSelected = selectedIndices.size == 1 && selectedIndices.contains(0)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SeriesChoiceChip(
                    text = "Normal",
                    selected = selectedTechnique == SeriesTechnique.NORMAL,
                    onClick = { selectedTechnique = SeriesTechnique.NORMAL },
                    modifier = Modifier.weight(1f),
                )
                SeriesChoiceChip(
                    text = "Dropset",
                    selected = selectedTechnique == SeriesTechnique.DROPSET && !onlyFirstSetSelected,
                    onClick = { selectedTechnique = SeriesTechnique.DROPSET },
                    enabled = !onlyFirstSetSelected,
                    modifier = Modifier.weight(1.05f),
                )
                SeriesChoiceChip(
                    text = "Rest-Pause",
                    selected = selectedTechnique == SeriesTechnique.REST_PAUSE && !onlyFirstSetSelected,
                    onClick = { selectedTechnique = SeriesTechnique.REST_PAUSE },
                    enabled = !onlyFirstSetSelected,
                    modifier = Modifier.weight(1.3f),
                )
            }
            Text(
                text = when {
                    onlyFirstSetSelected -> "La Serie 1 no puede ser Dropset ni Rest-Pause (requiere descanso previo completo de cambio de ejercicio)."
                    selectedTechnique == SeriesTechnique.NORMAL -> "Serie estándar. Quita dropset o rest-pause y el descanso especial."
                    selectedTechnique == SeriesTechnique.DROPSET -> "Entre las series marcadas: sin descanso y −5 kg en la siguiente."
                    selectedTechnique == SeriesTechnique.REST_PAUSE -> "Entre las series marcadas: 15 s de descanso y el mismo peso."
                    else -> ""
                },
                style = KpknType.caption,
                color = if (onlyFirstSetSelected) WizardColors.danger else WizardColors.textMuted,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                KpknSecondaryPill(
                    text = "Cancelar",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                KpknPrimaryPill(
                    text = "Aplicar",
                    onClick = {
                        val rawIndices = selectedIndices.filter { it in exercise.sets.indices }.toSet()
                        val indices = if (selectedTechnique != SeriesTechnique.NORMAL) {
                            rawIndices.filter { it > 0 }.toSet()
                        } else {
                            rawIndices
                        }
                        if (indices.isNotEmpty()) {
                            val from = indices.minOrNull() ?: anchorIdx
                            val to = indices.maxOrNull() ?: from
                            onApply(
                                SeriesTypeTarget(
                                    exerciseId = exercise.id,
                                    fromSetIdx = from,
                                    toSetIdx = to,
                                    selectedSetIndices = indices,
                                ),
                                selectedTechnique,
                            )
                        } else {
                            onDismiss()
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

/**
 * Opción de alcance o de tipo (LR1c2): la misma píldora que `KpknChip` (elegida en tinta; sin elegir, `inset` con filete al 8 %), pero con
 * el rótulo en hasta dos líneas antes que cortarlo con elipsis al 130 % de letra. Toda la píldora es tocable (48 dp) y sin ripple.
 */
@Composable
private fun SeriesChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 32.dp)
                .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
                .clip(KpknShape.pill)
                .background(if (selected) WizardColors.text else KpknSurface.inset, KpknShape.pill)
                .border(
                    1.dp,
                    if (selected) Color.Transparent else Color.White.copy(alpha = 0.08f),
                    KpknShape.pill,
                )
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = KpknType.chip,
                color = if (selected) WizardColors.ctaContent else WizardColors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}
