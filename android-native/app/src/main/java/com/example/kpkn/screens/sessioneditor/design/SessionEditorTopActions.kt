package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknType

/**
 * Acciones de la derecha de la fila superior del editor (SESSION_EDITOR_V1 §2, punto 1): el estado del guardado en `caption` y el botón
 * «⋯». [onMoreBoundsChanged] da la posición del botón en la ventana, para anclar el menú de vidrio debajo de él.
 *
 * @param saveStatus «Guardado» o «Sin guardar»; lo calcula quien lo pone, con los datos que ya tiene el VM.
 */
@Composable
fun SessionEditorTopActions(
    saveStatus: String,
    onMore: () -> Unit,
    onMoreBoundsChanged: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = saveStatus,
            style = KpknType.caption,
            color = WizardColors.textMuted,
        )
        Spacer(Modifier.width(4.dp))
        KpknIconButton(
            icon = KpknLineIcons.More,
            contentDescription = "Más opciones",
            onClick = onMore,
            modifier = Modifier.onGloballyPositioned { onMoreBoundsChanged(it.boundsInWindow()) },
        )
    }
}
