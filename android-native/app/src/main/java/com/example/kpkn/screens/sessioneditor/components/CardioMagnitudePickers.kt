package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.NativeWheelPicker
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import kotlin.math.roundToInt

/**
 * Píldora de un valor del cardio (tiempo, distancia, ritmo, cadencia…): etiqueta en `caption` y la cifra en `metricM` tabular, con su unidad
 * en la misma línea base («30 min»). Relleno `inset` con radio 12 dp; con [selected], el acento al 22 %. Toda la píldora es un botón de 48 dp.
 * Firma fija: la sesión en vivo la usa.
 */
@Composable
internal fun CardioValuePill(
    label: String,
    value: String,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    Column(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) accentColor.copy(alpha = 0.22f) else KpknSurface.inset)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = KpknType.caption,
            color = WizardColors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // «30 min» se parte en cifra y unidad, con la misma línea base; un valor sin cifra inicial («—», «Libre») va entero.
        val parts = value.split(' ', limit = 2)
        if (parts.size == 2 && parts[0].firstOrNull()?.isDigit() == true) {
            KpknMetric(value = parts[0], unit = parts[1], style = KpknType.metricM)
        } else {
            Text(
                text = value,
                style = KpknType.metricM,
                color = WizardColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun CardioMinutesWheelDialog(
    title: String,
    initialSeconds: Int,
    accentColor: Color,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    minMinutes: Int = 0,
    maxMinutes: Int = 180,
) {
    var minutes by rememberSaveable(initialSeconds) {
        mutableIntStateOf((initialSeconds / 60).coerceIn(minMinutes, maxMinutes))
    }
    CardioWheelDialogScaffold(
        title = title,
        accentColor = accentColor,
        preview = "$minutes min",
        onDismiss = onDismiss,
        onConfirm = { onConfirm(minutes * 60) },
    ) {
        NativeWheelPicker(
            label = "Min",
            value = minutes,
            range = minMinutes..maxMinutes,
            accentColor = accentColor,
            formatValue = { it.toString() },
            modifier = Modifier.fillMaxWidth(),
        ) { minutes = it }
    }
}

@Composable
internal fun CardioMinutesSecondsWheelDialog(
    title: String,
    initialSeconds: Int,
    accentColor: Color,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    maxMinutes: Int = 59,
) {
    var minutes by rememberSaveable(initialSeconds) {
        mutableIntStateOf((initialSeconds / 60).coerceIn(0, maxMinutes))
    }
    var seconds by rememberSaveable(initialSeconds) {
        mutableIntStateOf((initialSeconds % 60).coerceIn(0, 59))
    }
    CardioWheelDialogScaffold(
        title = title,
        accentColor = accentColor,
        preview = "%d:%02d".format(minutes, seconds),
        onDismiss = onDismiss,
        onConfirm = { onConfirm(minutes * 60 + seconds) },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NativeWheelPicker(
                label = "Min",
                value = minutes,
                range = 0..maxMinutes,
                accentColor = accentColor,
                formatValue = { it.toString() },
                modifier = Modifier.weight(1f),
            ) { minutes = it }
            NativeWheelPicker(
                label = "Seg",
                value = seconds,
                range = 0..59,
                accentColor = accentColor,
                modifier = Modifier.weight(1f),
            ) { seconds = it }
        }
    }
}

@Composable
internal fun CardioSecondsWheelDialog(
    title: String,
    initialSeconds: Int,
    accentColor: Color,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    minSeconds: Int = 0,
    maxSeconds: Int = 600,
) {
    var seconds by rememberSaveable(initialSeconds) {
        mutableIntStateOf(initialSeconds.coerceIn(minSeconds, maxSeconds))
    }
    CardioWheelDialogScaffold(
        title = title,
        accentColor = accentColor,
        preview = "$seconds s",
        onDismiss = onDismiss,
        onConfirm = { onConfirm(seconds) },
    ) {
        NativeWheelPicker(
            label = "Seg",
            value = seconds,
            range = minSeconds..maxSeconds,
            accentColor = accentColor,
            formatValue = { it.toString() },
            modifier = Modifier.fillMaxWidth(),
        ) { seconds = it }
    }
}

@Composable
internal fun CardioDistanceWheelDialog(
    title: String,
    initialKm: Double?,
    accentColor: Color,
    onDismiss: () -> Unit,
    onConfirm: (Double?) -> Unit,
) {
    val seed = ((initialKm ?: 0.0) * 10).roundToInt().coerceIn(0, 500)
    var km by rememberSaveable(seed) { mutableIntStateOf(seed / 10) }
    var tenths by rememberSaveable(seed) { mutableIntStateOf(seed % 10) }
    CardioWheelDialogScaffold(
        title = title,
        accentColor = accentColor,
        preview = if (km == 0 && tenths == 0) "Sin distancia" else "$km,$tenths km",
        onDismiss = onDismiss,
        onConfirm = {
            val value = km + tenths / 10.0
            onConfirm(value.takeIf { it > 0.0 })
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NativeWheelPicker(
                label = "km",
                value = km,
                range = 0..50,
                accentColor = accentColor,
                formatValue = { it.toString() },
                modifier = Modifier.weight(1f),
            ) { km = it }
            NativeWheelPicker(
                label = "décimas",
                value = tenths,
                range = 0..9,
                accentColor = accentColor,
                formatValue = { it.toString() },
                modifier = Modifier.weight(1f),
            ) { tenths = it }
        }
    }
}

@Composable
internal fun CardioPaceWheelDialog(
    title: String,
    initialSecondsPerKm: Int?,
    accentColor: Color,
    onDismiss: () -> Unit,
    onConfirm: (Int?) -> Unit,
) {
    val seed = (initialSecondsPerKm ?: 330).coerceIn(150, 720)
    var minutes by rememberSaveable(seed) { mutableIntStateOf(seed / 60) }
    var seconds by rememberSaveable(seed) { mutableIntStateOf(seed % 60) }
    CardioWheelDialogScaffold(
        title = title,
        accentColor = accentColor,
        preview = "%d:%02d /km".format(minutes, seconds),
        onDismiss = onDismiss,
        onConfirm = { onConfirm(minutes * 60 + seconds) },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NativeWheelPicker(
                label = "Min/km",
                value = minutes,
                range = 2..12,
                accentColor = accentColor,
                formatValue = { it.toString() },
                modifier = Modifier.weight(1f),
            ) { minutes = it }
            NativeWheelPicker(
                label = "Seg",
                value = seconds,
                range = 0..59,
                accentColor = accentColor,
                modifier = Modifier.weight(1f),
            ) { seconds = it }
        }
    }
}

@Composable
internal fun CardioIntWheelDialog(
    title: String,
    initial: Int?,
    range: IntRange,
    unit: String,
    accentColor: Color,
    onDismiss: () -> Unit,
    onConfirm: (Int?) -> Unit,
    allowZeroAsNone: Boolean = true,
) {
    var value by rememberSaveable(initial) {
        mutableIntStateOf((initial ?: range.first).coerceIn(range.first, range.last))
    }
    CardioWheelDialogScaffold(
        title = title,
        accentColor = accentColor,
        preview = "$value $unit",
        onDismiss = onDismiss,
        onConfirm = {
            onConfirm(if (allowZeroAsNone && value == 0) null else value)
        },
    ) {
        NativeWheelPicker(
            label = unit,
            value = value,
            range = range,
            accentColor = accentColor,
            formatValue = { it.toString() },
            modifier = Modifier.fillMaxWidth(),
        ) { value = it }
    }
}

@Composable
internal fun CardioTenthsWheelDialog(
    title: String,
    initial: Double?,
    wholeRange: IntRange,
    unit: String,
    accentColor: Color,
    onDismiss: () -> Unit,
    onConfirm: (Double?) -> Unit,
) {
    val seed = ((initial ?: 0.0) * 10).roundToInt().coerceIn(wholeRange.first * 10, wholeRange.last * 10 + 9)
    var whole by rememberSaveable(seed) { mutableIntStateOf(seed / 10) }
    var tenths by rememberSaveable(seed) { mutableIntStateOf(seed % 10) }
    CardioWheelDialogScaffold(
        title = title,
        accentColor = accentColor,
        preview = "$whole,$tenths $unit",
        onDismiss = onDismiss,
        onConfirm = {
            val value = whole + tenths / 10.0
            onConfirm(value.takeIf { it > 0.0 })
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NativeWheelPicker(
                label = unit,
                value = whole,
                range = wholeRange,
                accentColor = accentColor,
                formatValue = { it.toString() },
                modifier = Modifier.weight(1f),
            ) { whole = it }
            NativeWheelPicker(
                label = "décimas",
                value = tenths,
                range = 0..9,
                accentColor = accentColor,
                formatValue = { it.toString() },
                modifier = Modifier.weight(1f),
            ) { tenths = it }
        }
    }
}

/** Diálogo de rueda con la vista previa en `metricM` y las acciones KPKN: «Aplicar» como píldora y «Cancelar» como acción de texto. */
@Composable
private fun CardioWheelDialogScaffold(
    title: String,
    accentColor: Color,
    preview: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit,
) {
    KpknAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = KpknType.dialogTitle, color = WizardColors.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                content()
                Text(
                    text = preview,
                    style = KpknType.metricM,
                    color = accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        confirmButton = {
            KpknPrimaryPill(text = "Aplicar", onClick = onConfirm)
        },
        dismissButton = {
            KpknTextAction(text = "Cancelar", onClick = onDismiss)
        },
    )
}

internal enum class CardioMagnitudeKind {
    MINUTES,
    MINUTES_SECONDS,
    SECONDS,
    DISTANCE_KM,
    PACE,
    INTEGER,
    TENTHS,
}
