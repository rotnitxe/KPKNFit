package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.DropSetData
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.RestPauseData
import com.example.kpkn.data.models.TechniqueType
import com.example.kpkn.screens.sessioneditor.components.DropSetPlanDefaults
import com.example.kpkn.screens.sessioneditor.components.RestPausePlanDefaults
import com.example.kpkn.screens.workout.toTrimmedNumberString

internal data class TechniqueCheckRow(
    val weightText: String = "",
    val repsText: String = RestPausePlanDefaults.Reps.toString(),
    val restText: String = RestPausePlanDefaults.PauseSeconds.toString(),
    val done: Boolean = false,
)

private const val LiveDropSetMax = 5

@Composable
internal fun SetCardTechniqueBack(
    currentSet: ExerciseSet,
    coverWeightLabel: String,
    coverValueLabel: String,
    coverIntensityLabel: String?,
    mainWeight: Double,
    mainReps: Int,
    sessionAccentColor: Color,
    isFailedSet: Boolean,
    onFailedSetChange: (Boolean) -> Unit,
    onFlipBack: () -> Unit,
    onCompleteTechniques: (dropSets: List<DropSetData>, restPauses: List<RestPauseData>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val plannedGuide = remember(currentSet) { currentSet.resolvePlannedTechniqueGuide() }
    var dropEnabled by remember(currentSet.id) {
        mutableStateOf(plannedGuide?.kind == TechniqueType.DROP_SET || currentSet.isDropSet)
    }
    var restPauseEnabled by remember(currentSet.id) {
        mutableStateOf(plannedGuide?.kind == TechniqueType.REST_PAUSE || currentSet.isRestPause)
    }
    var dropRows by remember(currentSet.id, mainWeight, mainReps) {
        val count = plannedGuide?.count?.takeIf { plannedGuide.kind == TechniqueType.DROP_SET }
            ?: DropSetPlanDefaults.DefaultDrops
        val suggestions = suggestedDropLoadsForMainSet(mainWeight, mainReps, count)
        mutableStateOf(
            suggestions.map { load ->
                TechniqueCheckRow(
                    weightText = if (load > 0) load.toTrimmedNumberString() else "",
                    repsText = RestPausePlanDefaults.Reps.toString(),
                )
            }.ifEmpty {
                listOf(TechniqueCheckRow())
            },
        )
    }
    var restPauseRows by remember(currentSet.id) {
        val count = plannedGuide?.count?.takeIf { plannedGuide.kind == TechniqueType.REST_PAUSE }
            ?: RestPausePlanDefaults.DefaultCount
        mutableStateOf(
            List(count.coerceIn(RestPausePlanDefaults.MinCount, RestPausePlanDefaults.MaxCount)) {
                TechniqueCheckRow(repsText = RestPausePlanDefaults.Reps.toString())
            },
        )
    }
    var committed by remember(currentSet.id) { mutableStateOf(false) }

    LaunchedEffect(dropEnabled, restPauseEnabled, dropRows, restPauseRows, committed) {
        if (committed) return@LaunchedEffect
        val dropsReady = !dropEnabled || shouldAutoCommitTechniqueRows(dropRows.map { it.done })
        val pausesReady = !restPauseEnabled || shouldAutoCommitTechniqueRows(restPauseRows.map { it.done })
        val hasTechnique = dropEnabled || restPauseEnabled
        if (hasTechnique && dropsReady && pausesReady &&
            ((dropEnabled && dropRows.isNotEmpty()) || (restPauseEnabled && restPauseRows.isNotEmpty()))
        ) {
            val submitted = commit(dropRows, restPauseRows, dropEnabled, restPauseEnabled, onCompleteTechniques)
            if (submitted) committed = true
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        shape = WorkoutUiTokens.CardShape,
        color = if (isFailedSet) {
            Color(0xFF8B1E1E)
        } else {
            WorkoutUiTokens.setCardColor()
        },
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onFlipBack, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver a la serie",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                    )
                }
                Text(
                    "Técnicas de intensidad",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = WorkoutUiTokens.setInnerColor(),
            ) {
                Text(
                    listOfNotNull(coverWeightLabel, coverValueLabel, coverIntensityLabel)
                        .filter { it.isNotBlank() }
                        .joinToString("  ·  "),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SoftToggleChip(
                    text = if (dropEnabled) "Drop-sets" else "Añadir drop-sets",
                    selected = dropEnabled,
                    onClick = { dropEnabled = !dropEnabled },
                    accentColor = sessionAccentColor,
                    modifier = Modifier.weight(1f),
                )
                SoftToggleChip(
                    text = if (restPauseEnabled) "Rest-pause" else "Añadir rest-pause",
                    selected = restPauseEnabled,
                    onClick = { restPauseEnabled = !restPauseEnabled },
                    accentColor = sessionAccentColor,
                    modifier = Modifier.weight(1f),
                )
            }

            if (dropEnabled) {
                TechniqueChecklistBlock(
                    title = "Drop-sets",
                    rows = dropRows,
                    showWeight = true,
                    onRowsChange = { dropRows = it },
                    onAdd = {
                        val nextIndex = dropRows.size
                        val suggested = suggestedDropLoadsForMainSet(
                            mainWeight,
                            mainReps,
                            (nextIndex + 1).coerceAtMost(DropSetPlanDefaults.MaxDrops),
                        ).getOrNull(nextIndex)
                            ?: dropRows.lastOrNull()?.weightText?.toDoubleOrNull()?.times(0.85)
                        dropRows = dropRows + TechniqueCheckRow(
                            weightText = suggested?.takeIf { it > 0 }?.toTrimmedNumberString().orEmpty(),
                        )
                    },
                    canAdd = dropRows.size < LiveDropSetMax,
                )
            }

            if (restPauseEnabled) {
                TechniqueChecklistBlock(
                    title = "Rest-pause",
                    rows = restPauseRows,
                    showWeight = false,
                    onRowsChange = { restPauseRows = it },
                    onAdd = {
                        restPauseRows = restPauseRows + TechniqueCheckRow()
                    },
                    canAdd = restPauseRows.size < RestPausePlanDefaults.MaxCount,
                )
            }

            Spacer(Modifier.height(2.dp))

            SoftToggleChip(
                text = "Error de ejecución",
                selected = isFailedSet,
                onClick = { onFailedSetChange(!isFailedSet) },
                accentColor = WorkoutUiTokens.dangerColor(),
                modifier = Modifier.fillMaxWidth(),
            )
            }
        }
    }
}

@Composable
private fun TechniqueChecklistBlock(
    title: String,
    rows: List<TechniqueCheckRow>,
    showWeight: Boolean,
    onRowsChange: (List<TechniqueCheckRow>) -> Unit,
    onAdd: () -> Unit,
    canAdd: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
        )
        rows.forEachIndexed { index, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TechniqueDoneToggle(
                    done = row.done,
                    onToggle = {
                        onRowsChange(rows.toMutableList().also { it[index] = row.copy(done = !row.done) })
                    },
                )
                Text(
                    "${index + 1}",
                    modifier = Modifier.width(12.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f),
                )
                if (showWeight) {
                    WorkoutCompactNumberField(
                        value = row.weightText,
                        onValueChange = { value ->
                            onRowsChange(rows.toMutableList().also { it[index] = row.copy(weightText = value) })
                        },
                        unit = "kg",
                        decimal = true,
                        width = 64.dp,
                    )
                }
                WorkoutCompactNumberField(
                    value = row.repsText,
                    onValueChange = { value ->
                        onRowsChange(rows.toMutableList().also { it[index] = row.copy(repsText = value) })
                    },
                    unit = "reps",
                    width = 58.dp,
                )
                if (!showWeight) {
                    WorkoutCompactNumberField(
                        value = row.restText,
                        onValueChange = { value ->
                            onRowsChange(rows.toMutableList().also { it[index] = row.copy(restText = value) })
                        },
                        unit = "s",
                        width = 48.dp,
                    )
                }
            }
        }
        if (canAdd) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                Surface(
                    onClick = onAdd,
                    shape = RoundedCornerShape(10.dp),
                    color = WorkoutUiTokens.setInnerHighestColor(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Text(
                            "Añadir",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TechniqueDoneToggle(
    done: Boolean,
    onToggle: () -> Unit,
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Surface(
            onClick = onToggle,
            modifier = Modifier.size(22.dp),
            shape = RoundedCornerShape(6.dp),
            color = if (done) {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
            } else {
                Color(0xFF2E2E2E)
            },
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (done) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Hecho",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.surface,
                    )
                }
            }
        }
    }
}

@Composable
private fun SoftToggleChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) {
            accentColor.copy(alpha = 0.18f)
        } else {
            Color(0xFF2E2E2E)
        },
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (selected) 0.92f else 0.78f),
        )
    }
}

private fun commit(
    dropRows: List<TechniqueCheckRow>,
    restPauseRows: List<TechniqueCheckRow>,
    dropEnabled: Boolean,
    restPauseEnabled: Boolean,
    onCompleteTechniques: (List<DropSetData>, List<RestPauseData>) -> Unit,
): Boolean {
    val drops = if (dropEnabled) {
        dropRows.mapNotNull { row ->
            val weight = row.weightText.toDoubleOrNull() ?: return@mapNotNull null
            val reps = row.repsText.toIntOrNull() ?: return@mapNotNull null
            if (weight <= 0.0 || reps <= 0) null else DropSetData(weight, reps)
        }
    } else {
        emptyList()
    }
    val pauses = if (restPauseEnabled) {
        restPauseRows.mapNotNull { row ->
            val reps = row.repsText.toIntOrNull() ?: return@mapNotNull null
            if (reps <= 0) null else RestPauseData(
                restTime = row.restText.toIntOrNull()?.coerceAtLeast(0) ?: RestPausePlanDefaults.PauseSeconds,
                reps = reps,
            )
        }
    } else {
        emptyList()
    }
    if (drops.isEmpty() && pauses.isEmpty()) return false
    onCompleteTechniques(drops, pauses)
    return true
}
