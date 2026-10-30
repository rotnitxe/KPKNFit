package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.NativeWheelPicker
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.KpknSheetTokens
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import kotlin.math.roundToInt

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
                    preview,
                    style = KpknType.metricM,
                    color = accentColor,
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
