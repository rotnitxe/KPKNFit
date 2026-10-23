package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSetFormat
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/** B2: null en el modelo hereda el descanso del ejercicio; editarlo solo afecta a esta serie. */
@Composable
internal fun SetRestControl(seconds: Int, inherited: Boolean, onChange: (Int) -> Unit, onReset: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Descanso de esta serie", style = KpknType.caption, color = WizardColors.textMuted)
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            KpknIconButton(icon = KpknLineIcons.Minus, contentDescription = "Quitar 15 segundos a esta serie",
                onClick = { onChange((seconds - 15).coerceAtLeast(0)) })
            Text(KpknSetFormat.doseTime(seconds), style = KpknType.metricM, color = WizardColors.text,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
            KpknIconButton(icon = KpknLineIcons.Plus, contentDescription = "Sumar 15 segundos a esta serie",
                onClick = { onChange(seconds + 15) })
        }
        if (!inherited) KpknTextAction(text = "Usar descanso del ejercicio", onClick = onReset)
    }
}

/** B9: confirmación antes de aplicar, en la misma hoja que el resto del editor. */
@Composable
internal fun EditorConfirmationSheet(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String = "Cancelar",
    onDismiss: () -> Unit,
    onDismissRequest: () -> Unit = onDismiss,
) {
    KpknSheet(onDismissRequest = onDismissRequest) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, style = KpknType.dialogTitle, color = WizardColors.text)
            Text(text, style = KpknType.body, color = WizardColors.textMuted)
            KpknPrimaryPill(text = confirmLabel, onClick = onConfirm, modifier = Modifier.fillMaxWidth())
            KpknTextAction(text = dismissLabel, onClick = onDismiss)
        }
    }
}

/** B9: adaptación de los campos existentes al marco de hoja, conservando sus acciones. */
@Composable
internal fun EditorSheetDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    dismissButton: @Composable (() -> Unit)? = null,
) {
    KpknSheet(onDismissRequest = onDismissRequest) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            title?.invoke()
            text?.invoke()
            confirmButton()
            dismissButton?.invoke()
        }
    }
}
