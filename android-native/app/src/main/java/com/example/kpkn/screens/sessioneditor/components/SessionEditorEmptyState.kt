package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknOptionRow
import com.example.kpkn.ui.design.KpknType

/**
 * Sesión vacía (E5): «¿Qué quieres entrenar en esta sesión?» con dos filas, Fuerza y Cardio, en un módulo propio. Cada fila hace lo mismo
 * que antes: [onChooseStrength] da de alta el espacio de fuerza y [onChooseCardio] crea el de cardio.
 */
@Composable
internal fun SessionEditorEmptyState(
    onChooseStrength: () -> Unit,
    onChooseCardio: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KpknModule(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Text(
            text = "¿Qué quieres entrenar en esta sesión?",
            style = KpknType.title,
            color = WizardColors.text,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Elige el tipo de bloque para empezar.",
            style = KpknType.caption,
            color = WizardColors.textMuted,
        )
        Spacer(modifier = Modifier.height(8.dp))
        KpknOptionRow(
            modifier = Modifier.clickable(role = Role.Button, onClick = onChooseStrength),
            title = "Fuerza",
            subtitle = "Ejercicios, grupos y superseries",
            enabled = true,
            mark = {
                Icon(
                    imageVector = KpknLineIcons.Training,
                    contentDescription = null,
                    tint = WizardColors.text,
                    modifier = Modifier.size(20.dp),
                )
            },
        )
        KpknOptionRow(
            modifier = Modifier.clickable(role = Role.Button, onClick = onChooseCardio),
            title = "Cardio",
            subtitle = "Al inicio o al final de la sesión",
            enabled = true,
            mark = {
                Icon(
                    imageVector = KpknLineIcons.Run,
                    contentDescription = null,
                    tint = WizardColors.text,
                    modifier = Modifier.size(20.dp),
                )
            },
        )
    }
}
