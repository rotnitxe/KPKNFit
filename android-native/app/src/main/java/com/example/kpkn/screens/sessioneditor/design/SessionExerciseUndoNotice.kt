package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/**
 * Aviso de un ejercicio que se acaba de quitar (ER1b): ocupa el sitio de su fila, de 64 dp, con «Ejercicio eliminado» y «Deshacer». Desaparece
 * solo a los 5 s, cuando el borrado se confirma; [onUndo] lo deja donde estaba.
 */
@Composable
internal fun SessionExerciseUndoNotice(
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .padding(start = 20.dp, end = 12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Ejercicio eliminado",
            style = KpknType.bodyStrong,
            color = WizardColors.textMuted,
            modifier = Modifier.weight(1f),
        )
        KpknTextAction(
            text = "Deshacer",
            onClick = onUndo,
            color = WizardColors.text,
        )
    }
}
