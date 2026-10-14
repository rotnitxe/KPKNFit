package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknEmptyState
import com.example.kpkn.ui.design.KpknOptionRow

/**
 * Sesión vacía (SESSION_EDITOR_V1 §2, punto 8): un estado vacío con «¿Qué quieres entrenar en esta sesión?» y dos filas grandes para elegir el
 * espacio, fuerza (partes y superseries) o cardio (al inicio o al final). Hace lo mismo que antes; solo cambia el aspecto.
 */
@Composable
internal fun SessionEditorEmptyState(
    onChooseStrength: () -> Unit,
    onChooseCardio: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KpknEmptyState(
        icon = KpknLineIcons.Training,
        title = "¿Qué quieres entrenar en esta sesión?",
        body = null,
        action = {
            Column(
                modifier = modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                KpknOptionRow(
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onChooseStrength,
                    ),
                    title = "Fuerza",
                    subtitle = "Ejercicios, partes y superseries",
                    enabled = true,
                    mark = {
                        Icon(
                            imageVector = KpknLineIcons.Training,
                            contentDescription = null,
                            tint = WizardColors.text,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                )
                KpknOptionRow(
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onChooseCardio,
                    ),
                    title = "Cardio",
                    subtitle = "Al inicio o al final de la sesión",
                    enabled = true,
                    mark = {
                        Icon(
                            imageVector = KpknLineIcons.Pulse,
                            contentDescription = null,
                            tint = WizardColors.text,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                )
            }
        },
    )
}
