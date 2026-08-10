package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.data.models.MobilityConfig
import com.example.kpkn.data.models.MobilityMode
import com.example.kpkn.data.models.MobilityUnit
import com.example.kpkn.data.models.WarmupSetDefinition
import com.example.kpkn.screens.sessioneditor.EditorMiniField
import com.example.kpkn.screens.sessioneditor.formatEditableNumber
import com.example.kpkn.screens.sessioneditor.formatRestSummary
import com.example.kpkn.ui.components.KpknNativeTimePickerDialog

/**
 * Compact preparation cards used inside the folded editor blocks. The cards deliberately
 * expose only the fields that belong to their preparation type; strength techniques and
 * intensity controls remain in the effective-series carousel below.
 */
@Composable
internal fun MobilityPreparationCarousel(
    series: List<MobilitySeries>,
    mobilityConfig: MobilityConfig? = null,
    accentColor: Color,
    onUpdate: (String, (MobilitySeries) -> MobilitySeries) -> Unit,
    onUpdateConfig: (MobilityConfig) -> Unit = {},
    onRemove: (String) -> Unit,
    onAdd: () -> Unit,
) {
    val selectedMode = mobilityConfig?.mode ?: MobilityMode.ENFOCADO
    val isSurtido = selectedMode == MobilityMode.SURTIDO
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Modo",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.62f),
        )
        FilterChip(
            selected = !isSurtido,
            onClick = { onUpdateConfig((mobilityConfig ?: MobilityConfig()).copy(mode = MobilityMode.ENFOCADO)) },
            label = { Text("Enfocado") },
        )
        FilterChip(
            selected = isSurtido,
            onClick = { onUpdateConfig((mobilityConfig ?: MobilityConfig()).copy(mode = MobilityMode.SURTIDO)) },
            label = { Text("Surtido") },
        )
        if (isSurtido) {
            EditorMiniField(
                label = "Total (min)",
                value = (mobilityConfig?.totalMinutes ?: 1).coerceAtLeast(1).toString(),
                stateKey = "mobility-total-minutes-${series.firstOrNull()?.id ?: "block"}",
                keyboardType = KeyboardType.Number,
                accentColor = accentColor,
                modifier = Modifier.width(92.dp),
            ) { input ->
                input.toIntOrNull()?.let { minutes ->
                    onUpdateConfig(
                        (mobilityConfig ?: MobilityConfig(MobilityMode.SURTIDO, 1)).copy(
                            mode = MobilityMode.SURTIDO,
                            totalMinutes = minutes.coerceAtLeast(1),
                        ),
                    )
                }
            }
        }
    }
    val listState = rememberLazyListState()
    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        items(series, key = { it.id }) { mobility ->
            MobilityPreparationCard(
                mobility = mobility,
                showPerExerciseFields = !isSurtido,
                accentColor = accentColor,
                onUpdate = { transform -> onUpdate(mobility.id, transform) },
                onRemove = { onRemove(mobility.id) },
            )
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(series.size) { index ->
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(
                            color = if (index == listState.firstVisibleItemIndex) accentColor else Color.White.copy(alpha = 0.25f),
                            shape = CircleShape,
                        ),
                )
            }
        }
        TextButton(
            onClick = onAdd,
            colors = ButtonDefaults.textButtonColors(contentColor = accentColor),
        ) {
            Text("+ Agregar otra", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MobilityPreparationCard(
    mobility: MobilitySeries,
    accentColor: Color,
    showPerExerciseFields: Boolean,
    onUpdate: ((MobilitySeries) -> MobilitySeries) -> Unit,
    onRemove: () -> Unit,
) {
    var showRestPicker by remember(mobility.id) { mutableStateOf(false) }
    Surface(
        modifier = Modifier.width(268.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        color = accentColor.copy(alpha = 0.11f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.38f)),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        mobility.name,
                        color = Color.White.copy(alpha = 0.94f),
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "Movilidad",
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Quitar movilidad",
                        tint = accentColor,
                    )
                }
            }
            if (showPerExerciseFields) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                EditorMiniField(
                    label = "Series",
                    value = mobility.sets.toString(),
                    stateKey = "mobility-sets-${mobility.id}",
                    keyboardType = KeyboardType.Number,
                    accentColor = accentColor,
                    modifier = Modifier.weight(1f),
                ) { input ->
                    input.toIntOrNull()?.let { value ->
                        onUpdate { it.copy(sets = value.coerceAtLeast(1)) }
                    }
                }
                val unit = mobility.unit ?: if (mobility.durationSeconds != null) MobilityUnit.SECONDS else MobilityUnit.REPS
                if (unit == MobilityUnit.SECONDS) {
                    EditorMiniField(
                        label = "Segundos",
                        value = mobility.durationSeconds.toString(),
                        stateKey = "mobility-duration-${mobility.id}",
                        keyboardType = KeyboardType.Number,
                        accentColor = accentColor,
                        modifier = Modifier.weight(1f),
                    ) { input ->
                        input.toIntOrNull()?.let { value ->
                            onUpdate { it.copy(unit = MobilityUnit.SECONDS, durationSeconds = value.coerceAtLeast(1), reps = null) }
                        }
                    }
                } else {
                    EditorMiniField(
                        label = "Reps",
                        value = mobility.reps.orEmpty(),
                        stateKey = "mobility-reps-${mobility.id}",
                        keyboardType = KeyboardType.Text,
                        accentColor = accentColor,
                        modifier = Modifier.weight(1f),
                    ) { input ->
                        onUpdate { it.copy(unit = MobilityUnit.REPS, reps = input.trim().ifBlank { null }, durationSeconds = null) }
                    }
                }
            }
            if (showPerExerciseFields) {
                val unit = mobility.unit ?: if (mobility.durationSeconds != null) MobilityUnit.SECONDS else MobilityUnit.REPS
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    FilterChip(
                        selected = unit == MobilityUnit.SECONDS,
                        onClick = {
                            onUpdate {
                                it.copy(
                                    unit = MobilityUnit.SECONDS,
                                    durationSeconds = it.durationSeconds ?: 1,
                                    reps = null,
                                )
                            }
                        },
                        label = { Text("Segundos") },
                    )
                    FilterChip(
                        selected = unit == MobilityUnit.REPS,
                        onClick = {
                            onUpdate { it.copy(unit = MobilityUnit.REPS, durationSeconds = null) }
                        },
                        label = { Text("Repeticiones") },
                    )
                }
                TextButton(
                    onClick = { showRestPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = accentColor),
                ) {
                    Text("Descanso entre series: ${formatRestSummary(mobility.restBetweenSeconds)}")
                }
            }
        }
    }
    if (showRestPicker) {
        KpknNativeTimePickerDialog(
            title = "Descanso entre series",
            initialHour = (mobility.restBetweenSeconds / 60).coerceIn(0, 23),
            initialMinute = (mobility.restBetweenSeconds % 60).coerceIn(0, 59),
            hint = "Minutos : segundos",
            onConfirm = { minutes, seconds ->
                onUpdate { it.copy(restBetweenSeconds = (minutes * 60 + seconds).coerceAtLeast(0)) }
                showRestPicker = false
            },
            onDismiss = { showRestPicker = false },
        )
    }
}

@Composable
internal fun WarmupPreparationCarousel(
    sets: List<WarmupSetDefinition>,
    resolved1RM: Double?,
    accentColor: Color,
    onUpdate: (String, (WarmupSetDefinition) -> WarmupSetDefinition) -> Unit,
    onRemove: (String) -> Unit,
    onAdd: () -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        items(sets, key = { it.id }) { warmup ->
            WarmupPreparationCard(
                warmup = warmup,
                resolved1RM = resolved1RM,
                accentColor = accentColor,
                onUpdate = { transform -> onUpdate(warmup.id, transform) },
                onRemove = { onRemove(warmup.id) },
            )
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (sets.size == 1) "1 tarjeta de aproximación" else "${sets.size} tarjetas · desliza para verlas",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.58f),
        )
        TextButton(
            onClick = onAdd,
            colors = ButtonDefaults.textButtonColors(contentColor = accentColor),
        ) {
            Text("+ Añadir aproximación", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun WarmupPreparationCard(
    warmup: WarmupSetDefinition,
    resolved1RM: Double?,
    accentColor: Color,
    onUpdate: ((WarmupSetDefinition) -> WarmupSetDefinition) -> Unit,
    onRemove: () -> Unit,
) {
    val restSeconds = warmup.restBetween ?: 60
    var showRestPicker by remember(warmup.id) { mutableStateOf(false) }
    val normalizedPercentage = normalizeWarmupPercentage(warmup.percentageOfWorkingWeight)
    val suggestedLoad = resolved1RM?.takeIf { it > 0.0 }?.times(normalizedPercentage)
    Surface(
        modifier = Modifier.width(268.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        color = accentColor.copy(alpha = 0.11f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.38f)),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Aproximación",
                        color = Color.White.copy(alpha = 0.94f),
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        "Porcentaje de carga efectiva",
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Quitar aproximación",
                        tint = accentColor,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                EditorMiniField(
                    label = "% carga",
                    value = formatEditableNumber(normalizedPercentage * 100.0),
                    stateKey = "warmup-percent-inline-${warmup.id}",
                    keyboardType = KeyboardType.Decimal,
                    accentColor = accentColor,
                    modifier = Modifier.weight(1f),
                ) { input ->
                    input.replace(',', '.')
                        .toDoubleOrNull()
                        ?.coerceIn(10.0, 95.0)
                        ?.let { value -> onUpdate { it.copy(percentageOfWorkingWeight = value / 100.0) } }
                }
                EditorMiniField(
                    label = "Reps",
                    value = warmup.targetReps.toString(),
                    stateKey = "warmup-reps-inline-${warmup.id}",
                    keyboardType = KeyboardType.Number,
                    accentColor = accentColor,
                    modifier = Modifier.weight(1f),
                ) { input ->
                    input.toIntOrNull()?.let { value ->
                        onUpdate { it.copy(targetReps = value.coerceAtLeast(1)) }
                    }
                }
            }
            TextButton(
                onClick = { showRestPicker = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = accentColor),
            ) {
                Text("Descanso entre series: ${formatRestSummary(restSeconds)}")
            }
            Text(
                suggestedLoad?.let { "Carga sugerida: ${formatEditableNumber(it)} kg" }
                    ?: "Al ${formatEditableNumber(normalizedPercentage * 100.0)}% de la carga que usarás en tu primera serie efectiva",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.66f),
            )
        }
    }
    if (showRestPicker) {
        KpknNativeTimePickerDialog(
            title = "Descanso de aproximación",
            initialHour = (restSeconds / 60).coerceIn(0, 23),
            initialMinute = (restSeconds % 60).coerceIn(0, 59),
            hint = "Minutos : segundos",
            onConfirm = { minutes, seconds ->
                onUpdate { it.copy(restBetween = (minutes * 60 + seconds).coerceAtLeast(0)) }
                showRestPicker = false
            },
            onDismiss = { showRestPicker = false },
        )
    }
}

private fun normalizeWarmupPercentage(rawPercentage: Double): Double {
    val asFraction = if (rawPercentage > 1.0) rawPercentage / 100.0 else rawPercentage
    return asFraction.coerceIn(0.1, 0.95)
}
