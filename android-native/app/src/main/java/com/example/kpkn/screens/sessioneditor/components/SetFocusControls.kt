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
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSetFormat
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/**
 * B2 · `ed-serie-descanso`: null en el modelo hereda el descanso del ejercicio; editarlo solo afecta a esta serie. El campo es
 * «−15 · ⏱ 2:00 · +15»; tocar el tiempo abre los atajos y arrastrarlo lo cambia de 15 en 15 s.
 */
@Composable
internal fun SetRestControl(
    seconds: Int,
    inherited: Boolean,
    onChange: (Int) -> Unit,
    onReset: () -> Unit,
    /** Descanso del ejercicio: lo que restablece «Restablecer» y el último atajo. */
    exerciseSeconds: Int = seconds,
    /** Clave del estado de los atajos (una por serie), para que no se arrastre de una ficha a otra. */
    stateKey: String = "set-rest",
) {
    var presetsOpen by androidx.compose.runtime.saveable.rememberSaveable(stateKey) { androidx.compose.runtime.mutableStateOf(false) }
    EdRestSection(
        seconds = seconds,
        inherited = inherited,
        exerciseSeconds = exerciseSeconds,
        presetsOpen = presetsOpen,
        onPresetsOpenChange = { presetsOpen = it },
        onChange = onChange,
        onReset = {
            presetsOpen = false
            onReset()
        },
    )
}

/**
 * B9 · `ed-serie-aplicar`: confirmación antes de aplicar, en la hoja del editor: título, frase y dos botones (el principal y «Cancelar»).
 */
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
    EdSheet(onDismiss = onDismissRequest) {
        androidx.compose.material3.Text(title, style = edDisplay(20f, androidx.compose.ui.text.font.FontWeight.Bold, EdColors.ink, -0.4f).copy(lineHeight = 23.sp))
        androidx.compose.material3.Text(text, style = edText(13.5f, androidx.compose.ui.text.font.FontWeight.Normal, EdColors.muted, 19.6f))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EdPrimaryButton(text = confirmLabel, onClick = onConfirm)
            EdPrimaryButton(text = dismissLabel, onClick = onDismiss, ghost = true)
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
