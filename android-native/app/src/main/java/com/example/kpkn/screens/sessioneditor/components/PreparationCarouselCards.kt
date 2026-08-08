package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.data.models.WarmupSetDefinition
import com.example.kpkn.screens.sessioneditor.EditorMiniField
import com.example.kpkn.screens.sessioneditor.formatEditableNumber

/**
 * Compact preparation cards used inside the folded editor blocks. The cards deliberately
 * expose only the fields that belong to their preparation type; strength techniques and
 * intensity controls remain in the effective-series carousel below.
 */
@Composable
internal fun MobilityPreparationCarousel(
    series: List<MobilitySeries>,
    accentColor: Color,
    onUpdate: (String, (MobilitySeries) -> MobilitySeries) -> Unit,
    onRemove: (String) -> Unit,
    onAdd: () -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        items(series, key = { it.id }) { mobility ->
            MobilityPreparationCard(
                mobility = mobility,
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
        Text(
            if (series.size == 1) "1 tarjeta de movilidad" else "${series.size} tarjetas · desliza para verlas",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.58f),
        )
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
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                if (mobility.durationSeconds != null) {
                    EditorMiniField(
                        label = "Segundos",
                        value = mobility.durationSeconds.toString(),
                        stateKey = "mobility-duration-${mobility.id}",
                        keyboardType = KeyboardType.Number,
                        accentColor = accentColor,
                        modifier = Modifier.weight(1f),
                    ) { input ->
                        input.toIntOrNull()?.let { value ->
                            onUpdate { it.copy(durationSeconds = value.coerceAtLeast(1)) }
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
                        onUpdate { it.copy(reps = input.trim().ifBlank { null }) }
                    }
                }
            }
            EditorMiniField(
                label = "Descanso (s)",
                value = mobility.restBetweenSeconds.toString(),
                stateKey = "mobility-rest-${mobility.id}",
                keyboardType = KeyboardType.Number,
                accentColor = accentColor,
                modifier = Modifier.fillMaxWidth(),
            ) { input ->
                input.toIntOrNull()?.let { value ->
                    onUpdate { it.copy(restBetweenSeconds = value.coerceAtLeast(0)) }
                }
            }
        }
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
            EditorMiniField(
                label = "Descanso (s)",
                value = (warmup.restBetween ?: 60).toString(),
                stateKey = "warmup-rest-inline-${warmup.id}",
                keyboardType = KeyboardType.Number,
                accentColor = accentColor,
                modifier = Modifier.fillMaxWidth(),
            ) { input ->
                input.toIntOrNull()?.let { value ->
                    onUpdate { it.copy(restBetween = value.coerceAtLeast(0)) }
                }
            }
            Text(
                suggestedLoad?.let { "Carga sugerida: ${formatEditableNumber(it)} kg" }
                    ?: "Sin 1RM configurada: el porcentaje se aplicará al iniciar",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.66f),
            )
        }
    }
}

private fun normalizeWarmupPercentage(rawPercentage: Double): Double {
    val asFraction = if (rawPercentage > 1.0) rawPercentage / 100.0 else rawPercentage
    return asFraction.coerceIn(0.1, 0.95)
}
