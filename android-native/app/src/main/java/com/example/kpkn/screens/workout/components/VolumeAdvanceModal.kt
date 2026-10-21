package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.MuscleAdvance
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.workout.BlockedBackHint
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknType
import kotlin.math.roundToInt

/**
 * «Adelanto de volumen» (B12, LR1c2): cada músculo es un módulo y los dos botones son píldoras. El envoltorio
 * `ProtectedWorkoutBottomSheet` no se toca; la lógica de aplicar o de omitir tampoco.
 */
@Composable
fun VolumeAdvanceModal(
    advances: List<MuscleAdvance>,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    ProtectedWorkoutBottomSheet(
        title = "Adelanto de volumen",
        onDismiss = onDismiss,
        showCloseButton = false,
    ) {
        // Atrás no cierra esta hoja (pide una decisión), pero ya no se queda muda: avisa de por qué (LV4, 15).
        BlockedBackHint()
        Text(
            text = "Realizaste más series de las planificadas.",
            style = KpknType.body,
            color = WizardColors.textMuted,
        )

        advances.forEach { advance ->
            val surplusSets = advance.deficitSets.roundToInt().coerceAtLeast(1)
            val muscle = advance.muscleName.ifBlank { advance.muscleId }
            KpknModule(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(14.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = muscle,
                        style = KpknType.bodyStrong,
                        color = WizardColors.text,
                    )
                    Text(
                        text = "Si sumamos estas a las programadas para la siguiente sesión que involucra $muscle, superas el volumen. ¿Compensas restando de esa sesión las $surplusSets series extra?",
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                    )
                    Text(
                        text = "Próxima sesión: ${advance.targetSessionName}",
                        style = KpknType.caption,
                        color = WizardColors.textFaint,
                    )
                    advance.discountProposals.forEach { proposal ->
                        val discount = proposal.discountSets.roundToInt().coerceAtLeast(1)
                        Text(
                            text = "• ${proposal.exerciseName}: −$discount ${if (discount == 1) "serie" else "series"}",
                            style = KpknType.caption,
                            color = WizardColors.text,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        KpknPrimaryPill(
            text = "Aplicar descuento",
            onClick = onAccept,
            modifier = Modifier.fillMaxWidth(),
        )
        KpknSecondaryPill(
            text = "Omitir por ahora",
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
