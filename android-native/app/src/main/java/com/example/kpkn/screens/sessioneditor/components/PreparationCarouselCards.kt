package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.MobilityConfig
import com.example.kpkn.data.models.MobilityMode
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.data.models.WarmupSetDefinition
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.EditorMiniField
import com.example.kpkn.screens.sessioneditor.formatEditableNumber
import com.example.kpkn.screens.sessioneditor.formatRestSummary
import com.example.kpkn.ui.components.KpknNativeTimePickerDialog
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknCellInput
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknNumberCell
import com.example.kpkn.ui.design.KpknSetFormat
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/**
 * Carrusel de movilidad del grupo generado (`GroupEditorCard`, ER1b). Sin cambios: el ejercicio desplegado usa
 * [MobilityPreparationRows].
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

/**
 * Aproximaciones del ejercicio desplegado (ER1c1, R1 §4.1): cada serie en una fila con su % de carga y sus reps editables en sitio,
 * la carga sugerida y su descanso en una fila propia. «+ Añadir aproximación» va al pie del bloque.
 */
@Composable
internal fun WarmupPreparationRows(
    sets: List<WarmupSetDefinition>,
    resolved1RM: Double?,
    accentColor: Color,
    onUpdate: (String, (WarmupSetDefinition) -> WarmupSetDefinition) -> Unit,
    onRemove: (String) -> Unit,
    onAdd: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        sets.forEachIndexed { index, warmup ->
            key(warmup.id) {
                WarmupPreparationRow(
                    index = index,
                    warmup = warmup,
                    resolved1RM = resolved1RM,
                    onUpdate = { transform -> onUpdate(warmup.id, transform) },
                    onRemove = { onRemove(warmup.id) },
                )
            }
        }
        KpknTextAction(
            text = "+ Añadir aproximación",
            onClick = onAdd,
            color = WizardColors.text,
        )
    }
}

@Composable
private fun WarmupPreparationRow(
    index: Int,
    warmup: WarmupSetDefinition,
    resolved1RM: Double?,
    onUpdate: ((WarmupSetDefinition) -> WarmupSetDefinition) -> Unit,
    onRemove: () -> Unit,
) {
    val restSeconds = warmup.restBetween ?: 60
    var showRestPicker by remember(warmup.id) { mutableStateOf(false) }
    val normalizedPercentage = normalizeWarmupPercentage(warmup.percentageOfWorkingWeight)
    val suggestedLoad = resolved1RM?.takeIf { it > 0.0 }?.times(normalizedPercentage)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(KpknSurface.inset, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "${index + 1}",
                style = KpknType.bodyStrong,
                color = WizardColors.textMuted,
                modifier = Modifier.width(16.dp),
            )
            KpknNumberCell(
                value = formatEditableNumber(normalizedPercentage * 100.0).replace('.', ','),
                input = KpknCellInput.Text(
                    keyboardType = KeyboardType.Decimal,
                    onCommit = { input ->
                        input.replace(',', '.')
                            .toDoubleOrNull()
                            ?.coerceIn(10.0, 95.0)
                            ?.let { value -> onUpdate { it.copy(percentageOfWorkingWeight = value / 100.0) } }
                    },
                ),
                contentDescription = "Porcentaje de carga de la aproximación ${index + 1}",
                modifier = Modifier.width(60.dp),
            )
            Text("%", style = KpknType.bodyStrong, color = WizardColors.text)
            KpknNumberCell(
                value = warmup.targetReps.toString(),
                input = KpknCellInput.Text(
                    keyboardType = KeyboardType.Number,
                    onCommit = { input ->
                        input.toIntOrNull()?.let { value ->
                            onUpdate { it.copy(targetReps = value.coerceAtLeast(1)) }
                        }
                    },
                ),
                contentDescription = "Repeticiones de la aproximación ${index + 1}",
                modifier = Modifier.width(60.dp),
            )
            Text("reps", style = KpknType.caption, color = WizardColors.textMuted)
            Box(modifier = Modifier.weight(1f))
            KpknIconButton(
                icon = KpknLineIcons.Close,
                contentDescription = "Quitar aproximación",
                onClick = onRemove,
                modifier = Modifier.size(40.dp),
            )
        }
        Text(
            suggestedLoad?.let { load -> "Carga sugerida: ${formatEditableNumber(load).replace('.', ',')} kg" }
                ?: "Al ${formatEditableNumber(normalizedPercentage * 100.0).replace('.', ',')} % de la carga que usarás en tu primera serie efectiva",
            style = KpknType.caption,
            color = WizardColors.textMuted,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clickable { showRestPicker = true },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = KpknLineIcons.Clock,
                contentDescription = null,
                tint = WizardColors.textMuted,
                modifier = Modifier.size(16.dp),
            )
            Text(
                "Descanso entre series ${formatRestSummary(restSeconds)}",
                style = KpknType.caption,
                color = WizardColors.text,
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

/**
 * Movilidad del ejercicio desplegado (ER1c1, R1 §4.1): la cabecera «Tiempo global de movilidad» con su reloj, una fila por ejercicio
 * con su tiempo o reps y su ×, y «+ Agregar otra» al pie.
 */
@Composable
internal fun MobilityPreparationRows(
    series: List<MobilitySeries>,
    mobilityConfig: MobilityConfig?,
    onUpdateConfig: (MobilityConfig) -> Unit,
    onRemove: (String) -> Unit,
    onAdd: () -> Unit,
) {
    var showTimer by remember { mutableStateOf(false) }
    val totalMinutes = (mobilityConfig?.totalMinutes ?: 0).coerceAtLeast(0)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(KpknSurface.inset, RoundedCornerShape(16.dp))
                .clickable { showTimer = true }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = KpknLineIcons.Clock,
                contentDescription = "Configurar tiempo global",
                tint = WizardColors.text,
                modifier = Modifier.size(18.dp),
            )
            Text(
                "Tiempo global de movilidad",
                style = KpknType.bodyStrong,
                color = WizardColors.text,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (totalMinutes > 0) formatMobilityTimer(totalMinutes) else "Configurar con reloj",
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
        }
        series.forEach { mobility ->
            key(mobility.id) {
                MobilityPreparationRow(mobility = mobility, onRemove = { onRemove(mobility.id) })
            }
        }
        KpknTextAction(
            text = "+ Agregar otra",
            onClick = onAdd,
            color = WizardColors.text,
        )
    }
    if (showTimer) {
        KpknNativeTimePickerDialog(
            title = "Tiempo global de movilidad",
            initialHour = (totalMinutes / 60).coerceIn(0, 23),
            initialMinute = (totalMinutes % 60).coerceIn(0, 59),
            hint = "Horas : minutos",
            onConfirm = { hour, minute ->
                onUpdateConfig(MobilityConfig(mode = MobilityMode.ENFOCADO, totalMinutes = (hour * 60 + minute).coerceAtLeast(1)))
                showTimer = false
            },
            onDismiss = { showTimer = false },
        )
    }
}

@Composable
private fun MobilityPreparationRow(
    mobility: MobilitySeries,
    onRemove: () -> Unit,
) {
    val dose = mobility.durationSeconds?.takeIf { it > 0 }?.let { KpknSetFormat.doseTime(it) }
        ?: mobility.reps?.takeIf { it.isNotBlank() }?.let { "$it reps" }
        ?: "—"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(KpknSurface.inset, RoundedCornerShape(16.dp))
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            mobility.name,
            style = KpknType.bodyStrong,
            color = WizardColors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(dose, style = KpknType.caption, color = WizardColors.textMuted, maxLines = 1)
        KpknIconButton(
            icon = KpknLineIcons.Close,
            contentDescription = "Quitar movilidad",
            onClick = onRemove,
            modifier = Modifier.size(40.dp),
        )
    }
}

private fun normalizeWarmupPercentage(rawPercentage: Double): Double {
    val asFraction = if (rawPercentage > 1.0) rawPercentage / 100.0 else rawPercentage
    return asFraction.coerceIn(0.1, 0.95)
}

/**
 * Píldora de borde punteado (filete blanco al 24 %, 40 dp) para añadir lo que falta en la preparación («+ Movilidad», «+ Aproximación»).
 * Es un objetivo táctil de 40 dp de alto, como las píldoras de la barra de opciones.
 */
@Composable
internal fun PreparationDashedPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val edge = Color.White.copy(alpha = 0.24f)
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawRoundRect(
                    color = edge,
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(20.dp.toPx()),
                    style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                )
            }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = KpknType.chip, color = WizardColors.text, maxLines = 1)
    }
}
