package com.example.kpkn.screens.sessioneditor.frame

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/**
 * Aviso fijo del guardado automático bloqueado (ER1a §5): el mensaje humano del cortafuegos o de la validación, en un módulo con
 * borde `danger`. Lo escrito no se pierde; el aviso desaparece cuando un guardado pasa. Con [onOpenExercise], un acceso lleva al ejercicio.
 */
@Composable
internal fun SessionEditorFrameBlockNotice(
    message: String,
    onOpenExercise: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(KpknSurface.module, KpknShape.module)
            .border(1.dp, WizardColors.danger, KpknShape.module)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = message,
            style = KpknType.body,
            color = WizardColors.text,
        )
        if (onOpenExercise != null) {
            KpknTextAction(text = "Ver el ejercicio", onClick = onOpenExercise)
        }
    }
}
