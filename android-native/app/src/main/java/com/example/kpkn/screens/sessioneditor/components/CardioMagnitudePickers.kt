package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.sessioneditor.NativeWheelPicker
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.KpknSheetTokens
import kotlin.math.roundToInt

internal enum class CardioMagnitudeKind {
    MINUTES,
    MINUTES_SECONDS,
    SECONDS,
    DISTANCE_KM,
    PACE,
    INTEGER,
    TENTHS,
}

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
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (selected) accentColor.copy(alpha = 0.22f) else KpknSheetTokens.ChipIdle,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.62f),
            fontWeight = FontWeight.Bold,
        )
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            fontWeight = FontWeight.Black,
        )
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
        preview = if (km == 0 && tenths == 0) "Sin distancia" else "$km.$tenths km",
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
        preview = "%d:%02d/km".format(minutes, seconds),
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
        preview = "$whole.$tenths $unit",
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
        title = { Text(title, fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                content()
                Text(
                    preview,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                )
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = onConfirm) { Text("Aplicar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

@Composable
internal fun rememberCardioMagnitudePicker(): CardioMagnitudePickerState {
    var kind by remember { mutableStateOf<CardioMagnitudeKind?>(null) }
    return remember {
        CardioMagnitudePickerState(
            kindProvider = { kind },
            setKind = { kind = it },
        )
    }
}

internal class CardioMagnitudePickerState(
    private val kindProvider: () -> CardioMagnitudeKind?,
    private val setKind: (CardioMagnitudeKind?) -> Unit,
) {
    val kind: CardioMagnitudeKind? get() = kindProvider()
    fun dismiss() = setKind(null)
    fun open(kind: CardioMagnitudeKind) = setKind(kind)
}
