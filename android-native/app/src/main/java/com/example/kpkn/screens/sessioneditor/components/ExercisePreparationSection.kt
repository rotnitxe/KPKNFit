package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.WarmupSetDefinition
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknType
import java.util.UUID

/**
 * B6 · preparación del ejercicio: la movilidad (H2) y la aproximación (H1) viven dentro del propio ejercicio, cada
 * una en un bloque plegable con su contador y su control común en la cabecera. Lo que falta queda como píldora
 * punteada debajo, en el mismo sitio. Las fichas de ambos bloques son la misma pieza que las series efectivas
 * ([SessionEditorChipMetrics]).
 */
@Composable
internal fun ExercisePreparationSection(
    exercise: Exercise,
    mobilityBlockExpanded: Boolean,
    warmupBlockExpanded: Boolean,
    onToggleMobilityBlock: () -> Unit,
    onToggleWarmupBlock: () -> Unit,
    onUpdateExercise: ((Exercise) -> Exercise) -> Unit,
    onRemoveMobility: (String) -> Unit,
    onOpenMobility: () -> Unit,
    capacityKg: Double? = null,
    initiallyExpandedWarmupId: String? = null,
    initiallyExpandedMobilityId: String? = null,
) {
    val hasMobility = exercise.mobilitySeries.isNotEmpty()
    val hasWarmup = exercise.warmupSets.isNotEmpty()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("exercise-preparation"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (hasMobility) {
            PreparationBlock(
                title = "MOVILIDAD",
                count = exercise.mobilitySeries.size,
                expanded = mobilityBlockExpanded,
                onToggle = onToggleMobilityBlock,
                trailing = {
                    MobilityGlobalTimeButton(
                        mobilityConfig = exercise.mobilityConfig,
                        onUpdateConfig = { config ->
                            onUpdateExercise { current -> current.copy(mobilityConfig = config) }
                        },
                    )
                },
            ) {
                MobilityPreparationChips(
                    exerciseId = exercise.id,
                    series = exercise.mobilitySeries,
                    onUpdate = { mobilityId, transform ->
                        onUpdateExercise { current ->
                            current.copy(
                                mobilitySeries = current.mobilitySeries.map { mobility ->
                                    if (mobility.id == mobilityId) transform(mobility) else mobility
                                },
                            )
                        }
                    },
                    onMove = { mobilityId, delta ->
                        onUpdateExercise { current ->
                            val reordered = current.mobilitySeries.toMutableList()
                            val from = reordered.indexOfFirst { it.id == mobilityId }
                            if (from >= 0) {
                                val to = (from + delta).coerceIn(0, reordered.lastIndex)
                                if (to != from) reordered.add(to, reordered.removeAt(from))
                            }
                            current.copy(mobilitySeries = reordered)
                        }
                    },
                    onRemove = onRemoveMobility,
                    onAdd = onOpenMobility,
                    initiallyExpandedId = initiallyExpandedMobilityId,
                )
            }
        }
        if (hasWarmup) {
            PreparationBlock(
                title = "APROXIMACIÓN",
                count = exercise.warmupSets.size,
                expanded = warmupBlockExpanded,
                onToggle = onToggleWarmupBlock,
                trailing = {
                    WarmupCommonRestButton(
                        sets = exercise.warmupSets,
                        onUpdate = { warmupId, transform ->
                            onUpdateExercise { current ->
                                current.copy(
                                    warmupSets = current.warmupSets.map { warmup ->
                                        if (warmup.id == warmupId) transform(warmup) else warmup
                                    },
                                )
                            }
                        },
                    )
                },
            ) {
                WarmupPreparationChips(
                    exerciseId = exercise.id,
                    sets = exercise.warmupSets,
                    workingWeightKg = targetWorkingWeightKg(exercise.sets),
                    capacityKg = capacityKg,
                    effectiveSetCount = exercise.sets.size,
                    onUpdate = { warmupId, transform ->
                        onUpdateExercise { current ->
                            current.copy(
                                warmupSets = current.warmupSets.map { warmup ->
                                    if (warmup.id == warmupId) transform(warmup) else warmup
                                },
                            )
                        }
                    },
                    onRemove = { warmupId ->
                        onUpdateExercise { current ->
                            current.copy(warmupSets = current.warmupSets.filterNot { it.id == warmupId })
                        }
                    },
                    onAdd = { onAddWarmup(onUpdateExercise) },
                    initiallyExpandedId = initiallyExpandedWarmupId,
                )
            }
        }
        if (!hasMobility || !hasWarmup) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!hasMobility) {
                    PreparationDashedPill(
                        text = "+ Movilidad",
                        onClick = onOpenMobility,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (!hasWarmup) {
                    PreparationDashedPill(
                        text = "+ Aproximación",
                        onClick = { onAddWarmup(onUpdateExercise) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * Primera carga de trabajo positiva y finita del ejercicio, omitiendo ranuras vacías, como en LV-C.
 * Si no hay ninguna carga de trabajo válida, la aproximación cae a la capacidad.
 */
internal fun targetWorkingWeightKg(sets: List<ExerciseSet>): Double? =
    sets.firstNotNullOfOrNull { set -> set.weight?.takeIf { !set.isEmptySlot && it.isFinite() && it > 0.0 } }

/** Bloque plegable de la preparación (R1 §4.1): la ceja con su contador («APROXIMACIÓN · 3») y, desplegado, sus fichas. */
@Composable
internal fun PreparationBlock(
    title: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val stackTrailing = LocalDensity.current.fontScale >= 1.2f
    KpknModule(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("preparation-block-${title.lowercase()}"),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "$title · $count",
                style = KpknType.eyebrow,
                color = WizardColors.textMuted,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!stackTrailing) trailing?.invoke()
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Plegar $title" else "Desplegar $title",
                tint = WizardColors.textMuted,
                modifier = Modifier.size(18.dp),
            )
        }
        if (stackTrailing && trailing != null) {
            Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.CenterEnd) {
                trailing.invoke()
            }
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) { content() }
        }
    }
}

/** Alta de la primera aproximación (R1 §4.1): 50 %, 8 reps y 60 s. Las siguientes suben el 10 % y bajan 2 reps. */
internal fun onAddWarmup(onUpdateExercise: ((Exercise) -> Exercise) -> Unit) {
    onUpdateExercise { current ->
        val previous = current.warmupSets.lastOrNull()
        if (previous == null) {
            current.copy(
                warmupSets = listOf(
                    WarmupSetDefinition(
                        id = UUID.randomUUID().toString(),
                        percentageOfWorkingWeight = 0.5,
                        targetReps = 8,
                        restBetween = 60,
                    ),
                ),
            )
        } else {
            current.copy(
                warmupSets = current.warmupSets + WarmupSetDefinition(
                    id = UUID.randomUUID().toString(),
                    percentageOfWorkingWeight = (
                        normalizeWarmupPercentageForEditor(previous.percentageOfWorkingWeight) + 0.1
                    ).coerceAtMost(0.9),
                    // As the load moves toward the working RM, the next
                    // approximation also becomes shorter. Keep one
                    // repetition as the safe floor when the previous card
                    // is already minimal.
                    targetReps = (previous.targetReps - 2).coerceAtLeast(1),
                    restBetween = previous.restBetween ?: 60,
                ),
            )
        }
    }
}

internal fun normalizeWarmupPercentageForEditor(rawPercentage: Double): Double {
    val asFraction = if (rawPercentage > 1.0) rawPercentage / 100.0 else rawPercentage
    return asFraction.coerceIn(0.1, 0.95)
}
