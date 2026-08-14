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
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
    MobilityGlobalTimerField(
        totalMinutes = mobilityConfig?.totalMinutes ?: 0,
        accentColor = accentColor,
        onConfirm = { minutes ->
            onUpdateConfig(
                MobilityConfig(
                    mode = MobilityMode.ENFOCADO,
                    totalMinutes = minutes,
                ),
            )
        },
    )
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
                accentColor = accentColor,
                onUpdate = { transform ->
                    onUpdate(mobility.id) { current ->
                        transform(current).copy(restBetweenSeconds = 0)
                    }
                },
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
    onUpdate: ((MobilitySeries) -> MobilitySeries) -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        modifier = Modifier.width(220.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        color = accentColor.copy(alpha = 0.11f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.38f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
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
                if (!mobility.notes.isNullOrBlank()) {
                    Text(
                        mobility.notes,
                        color = Color.White.copy(alpha = 0.60f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Quitar movilidad",
                    tint = accentColor,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun MobilityGlobalTimerField(
    totalMinutes: Int,
    accentColor: Color,
    onConfirm: (Int) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val normalizedMinutes = totalMinutes.coerceAtLeast(0)
    OutlinedButton(
        onClick = { showPicker = true },
        modifier = Modifier
            .fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.34f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = accentColor.copy(alpha = 0.08f),
            contentColor = Color.White,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Icon(
            Icons.Default.Timer,
            contentDescription = "Configurar tiempo global",
            tint = accentColor,
        )
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                "Tiempo global de movilidad",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.90f),
            )
            Text(
                if (normalizedMinutes > 0) formatMobilityTimer(normalizedMinutes) else "Configurar con reloj",
                style = MaterialTheme.typography.labelSmall,
                color = accentColor.copy(alpha = 0.88f),
            )
        }
    }
    if (showPicker) {
        KpknNativeTimePickerDialog(
            title = "Tiempo global de movilidad",
            initialHour = (normalizedMinutes / 60).coerceIn(0, 23),
            initialMinute = (normalizedMinutes % 60).coerceIn(0, 59),
            hint = "Horas : minutos",
            onConfirm = { hour, minute ->
                onConfirm((hour * 60 + minute).coerceAtLeast(1))
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

private fun formatMobilityTimer(totalMinutes: Int): String {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "%d h %02d min".format(hours, minutes) else "$minutes min"
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
